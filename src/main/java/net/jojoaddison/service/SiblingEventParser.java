package net.jojoaddison.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.service.dto.SiblingDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads the two sibling envelopes off the wire and answers with a {@link SiblingDomainEvent}.
 *
 * <h2>Nothing here throws</h2>
 *
 * <p>Every method answers {@link Optional#empty()} for anything it cannot make sense of, and the
 * consumers treat empty as "not for us" rather than as an error. That is the whole reason this is a
 * separate class with its own tests: <b>an exception thrown out of a Spring Cloud Stream consumer is
 * redelivered, and by default redelivered three times and then dropped</b> — so one message this
 * service cannot parse would stall the partition it arrived on and, with it, every subject whose key
 * hashes there. These topics are shared and neither producer has promised this service a schema;
 * both say in their own javadoc that a consumer meeting something it does not recognise must ignore
 * it. Refusing loudly is the right behaviour for a producer validating its own payload and the wrong
 * behaviour for a consumer reading somebody else's.
 *
 * <p>The counterpart of that leniency is that a message which is genuinely for this service and
 * genuinely malformed is dropped with a {@code warn} and nothing else. That is a deliberate trade
 * and the log line names the topic and the first hundred characters, because "the directory did not
 * update" has no other evidence behind it.
 *
 * <h2>Timestamps are parsed from both forms on purpose</h2>
 *
 * <p>hc-professional's gateway writes {@code Instant.now().toString()} into a {@code Map}, so its
 * {@code occurredAt} is always an ISO-8601 string. hc-patient's is a real {@code Instant} on a
 * record, serialised by whatever that application's ObjectMapper is configured to do — which is a
 * setting in a repository this one does not own and can change without anything failing here. Both
 * forms are read rather than one being assumed, because guessing wrong would not be a parse error:
 * a numeric epoch read as a missing field would fall back to "now", the watermark would jump to the
 * present, and every genuinely later event would then be discarded as stale. Silent, and it would
 * look like the consumer had stopped.
 *
 * <h2>Why this is in {@code ..service..} rather than {@code ..broker..}</h2>
 *
 * <p>It reads wire formats, so {@code ..broker..} is where it looks as though it should live. That
 * package is in no layer as far as {@code TechnicalStructureTest} is concerned, which means a class
 * in it may reference neither {@code ..service..} nor {@code ..domain..} — and this one returns a
 * {@code service.dto} type carrying a {@code domain.enumeration} value. See
 * {@link net.jojoaddison.config.DirectoryEventConsumers} for the fuller note, including why
 * {@code KafkaConsumer} appears to get away with it and does not.
 */
@Component
public class SiblingEventParser {

    /** hc-patient's two account events, the only ones that say an account can sign in. */
    private static final String PATIENT_ACCOUNT_CREATED = "AccountCreated";
    private static final String PATIENT_ACCOUNT_ACTIVATED = "AccountActivated";

    /** hc-professional's registration event, which is the arrival of a clinician's account. */
    private static final String PROFESSIONAL_REGISTRATION_CREATED = "registration.created";

    /** How much of an unreadable frame to put in the log. Enough to identify it, not enough to copy it. */
    private static final int LOG_EXCERPT = 100;

    private static final Logger LOG = LoggerFactory.getLogger(SiblingEventParser.class);

    private final ObjectMapper objectMapper;

    public SiblingEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * A frame from {@code patient-events}.
     *
     * @param headerKey the {@code patientKey} header hc-patient sets as the Kafka partition key, or
     *                  null. Preferred over the payload's own copy because it is what the broker
     *                  actually partitioned on, so keying on it is keying on the thing that
     *                  guarantees this subject's events arrive in order on one thread. The payload
     *                  field is the same value and is the fallback for a frame whose headers were
     *                  not carried across — a header is metadata and can be dropped by a bridge, a
     *                  mirror or a test harness, where the payload cannot.
     */
    public Optional<SiblingDomainEvent> parsePatientEvent(byte[] payload, String headerKey) {
        JsonNode node = read(payload, "patient-events");
        if (node == null) {
            return Optional.empty();
        }
        JsonNode subject = node.path("subject");

        String type = text(node, "type");
        String key = normaliseKey(headerKey != null && !headerKey.isBlank() ? headerKey : text(subject, "email"));
        if (type == null || key == null) {
            LOG.warn("Ignoring a patient-events frame with no {}", type == null ? "type" : "subject key");
            return Optional.empty();
        }

        boolean activated =
            PATIENT_ACCOUNT_ACTIVATED.equals(type) ||
            (PATIENT_ACCOUNT_CREATED.equals(type) && node.path("data").path("activated").asBoolean(false));

        return Optional.of(
            new SiblingDomainEvent(
                DirectorySource.HC_PATIENT,
                text(node, "eventId"),
                type,
                occurredAt(node),
                key,
                text(subject, "email"),
                text(subject, "login"),
                text(subject, "patientId"),
                type,
                activated
            )
        );
    }

    /**
     * A frame from {@code hc.professional.registration}, carrying either {@code registration.created}
     * or {@code onboarding.state}.
     *
     * <p>The subject key is {@code payload.accountId} rather than the email: that is what both
     * producers key the topic on, and {@code onboarding.state} carries no email at all. Taking the
     * email where it is available and the accountId where it is not would give one clinician two
     * links.
     *
     * <p>The header is not read here. hc-professional sets {@code KafkaHeaders.KEY} directly, which
     * the binder consumes as the record key rather than exposing under a name of its own — the
     * payload is the reliable copy on this stream, unlike hc-patient's.
     */
    public Optional<SiblingDomainEvent> parseProfessionalEvent(byte[] payload) {
        JsonNode node = read(payload, "hc.professional.registration");
        if (node == null) {
            return Optional.empty();
        }
        JsonNode content = node.path("payload");

        String type = text(node, "eventType");
        String accountId = text(content, "accountId");
        if (type == null || accountId == null) {
            LOG.warn("Ignoring an hc.professional.registration frame with no {}", type == null ? "eventType" : "accountId");
            return Optional.empty();
        }

        // `state` where the event carries one (onboarding.state does, registration.created does
        // not), otherwise the type itself — so the link always records something a reader can act
        // on rather than sometimes recording null.
        String state = text(content, "state");

        return Optional.of(
            new SiblingDomainEvent(
                DirectorySource.HC_PROFESSIONAL,
                text(node, "eventId"),
                type,
                occurredAt(node),
                accountId,
                text(content, "email"),
                text(content, "login"),
                accountId,
                state != null ? state : type,
                // A registration is an account that exists and can sign in; hc-professional has no
                // separate activation event on this topic. Onboarding state changes say nothing
                // about sign-in and must not move a status.
                PROFESSIONAL_REGISTRATION_CREATED.equals(type)
            )
        );
    }

    /** Lowercased and trimmed, matching what both hc-patient publishers do to the key before sending. */
    private String normaliseKey(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** The envelope, or null for anything that is not one. Null rather than an Optional because
     * every caller immediately branches on it and an Optional would only be unwrapped. */
    private JsonNode read(byte[] payload, String topic) {
        if (payload == null || payload.length == 0) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(payload);
            if (!node.isObject()) {
                LOG.warn("Ignoring a non-object frame on {}", topic);
                return null;
            }
            return node;
        } catch (Exception e) {
            // The exception's message, not the exception. A topic this service does not own can
            // carry frames it will never parse, and one stack trace per message would bury the log
            // in the shape of a stack trace that is not a failure.
            LOG.warn("Ignoring an unreadable frame on {} ({}): {}", topic, e.toString(), excerpt(payload));
            return null;
        }
    }

    /**
     * {@code occurredAt} as an instant, from a string or a numeric epoch, falling back to now.
     *
     * <p>The fallback is safe in the direction that matters and unsafe in the other, so it is worth
     * being explicit: an event stamped "now" is never discarded as stale, so nothing is lost, but it
     * does advance the watermark past events that really are later. That only happens for a frame
     * whose timestamp this could not read at all, which is a schema change rather than a normal
     * message, and dropping such a frame entirely would be the worse of the two.
     */
    private Instant occurredAt(JsonNode node) {
        JsonNode value = node.path("occurredAt");
        if (value.isNumber()) {
            // Seconds with a fractional part is what Jackson writes for an Instant with
            // WRITE_DATES_AS_TIMESTAMPS enabled and JavaTimeModule registered.
            double seconds = value.asDouble();
            return Instant.ofEpochMilli(Math.round(seconds * 1000));
        }
        String text = value.asText(null);
        if (text != null && !text.isBlank()) {
            try {
                return Instant.parse(text);
            } catch (DateTimeParseException e) {
                LOG.warn("Unreadable occurredAt '{}' — treating the event as current", text);
            }
        }
        return Instant.now();
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private String excerpt(byte[] payload) {
        String text = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
        return text.length() <= LOG_EXCERPT ? text : text.substring(0, LOG_EXCERPT) + "…";
    }
}
