package net.jojoaddison.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.service.dto.SiblingDomainEvent;
import net.jojoaddison.service.dto.SiblingDomainEvent.Disposition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Reads the two sibling envelopes off the wire and answers with a {@link SiblingDomainEvent}.
 *
 * <h2>Every type each producer publishes is named here, and anything else is ignored</h2>
 *
 * <p>Both producers say the same thing in their own javadoc — <em>a consumer meeting a type it does
 * not recognise must ignore it</em> — and this class is where that is kept. The constants below are
 * the complete published set on both streams, read from
 * {@code hc-patient/api/.../service/event/PatientEventType.java} and from hc-professional's two
 * publishers rather than inferred from the frames that happen to be in a topic:
 *
 * <table>
 *   <caption>The nine types on the two subscribed topics, and what each one may do here</caption>
 *   <tr><th>Topic</th><th>Type</th><th>Disposition</th></tr>
 *   <tr><td>{@code patient-events}</td><td>{@code AccountCreated}</td>
 *       <td>{@link Disposition#CREATE}, or {@link Disposition#LINK_ONLY} for a care angel</td></tr>
 *   <tr><td></td><td>{@code AccountActivated}</td><td>{@link Disposition#UPDATE_ONLY}</td></tr>
 *   <tr><td></td><td>{@code OnboardingStarted}</td><td>{@link Disposition#CREATE}</td></tr>
 *   <tr><td></td><td>{@code OnboardingStepCompleted}</td><td>{@link Disposition#UPDATE_ONLY}</td></tr>
 *   <tr><td></td><td>{@code OnboardingCompleted}</td><td>{@link Disposition#UPDATE_ONLY}</td></tr>
 *   <tr><td></td><td>{@code CareDelegationChanged}</td><td>{@link Disposition#UPDATE_ONLY}</td></tr>
 *   <tr><td></td><td>{@code DeletionRequestChanged}</td><td>{@link Disposition#UPDATE_ONLY}, and
 *       {@code change=COMPLETED} marks the link erased</td></tr>
 *   <tr><td>{@code hc.professional.registration}</td><td>{@code registration.created}</td>
 *       <td>{@link Disposition#LINK_ONLY}</td></tr>
 *   <tr><td></td><td>{@code onboarding.state}</td><td>{@link Disposition#LINK_ONLY}</td></tr>
 * </table>
 *
 * <p><b>Two of those rows were the whole of a defect.</b> Until 2026-09-05 this class named two types
 * and used them only to decide {@code activated}, and there was no type filter on the creation path
 * at all — so every one of the other five, and every type either product adds next, opened a
 * {@code Patient}. The two that mattered: a care-angel nomination publishes {@code AccountCreated}
 * keyed on the <em>angel's</em> address, which made every nomination a patient; and
 * {@code DeletionRequestChanged/COMPLETED} is published <em>after</em> the profile has been erased,
 * so the event whose entire meaning is "erase this person" was what made this service start storing
 * their address.
 *
 * <h2>Nothing here throws</h2>
 *
 * <p>Every method answers {@link Optional#empty()} for anything it cannot make sense of, and the
 * consumers treat empty as "not for us" rather than as an error. That is the whole reason this is a
 * separate class with its own tests: <b>an exception thrown out of a Spring Cloud Stream consumer is
 * redelivered, and while that is happening the partition it arrived on makes no progress</b> — so
 * one message this service cannot parse would hold up every subject whose key hashes there. These
 * topics are shared and neither producer has promised this service a schema. Refusing loudly is the
 * right behaviour for a producer validating its own payload and the wrong behaviour for a consumer
 * reading somebody else's.
 *
 * <p>That leniency stops at the parse. A failure <em>downstream</em> of here — a Mongo write that
 * could not be made — is not a bad message and is deliberately not swallowed; see
 * {@link net.jojoaddison.config.DirectoryEventConsumers}.
 *
 * <p>The counterpart of the leniency is that a message which is genuinely for this service and
 * genuinely malformed is dropped with a {@code warn} and nothing else. That is a deliberate trade,
 * because "the directory did not update" has no other evidence behind it. An unrecognised
 * <em>type</em> is logged at {@code debug} instead: it is the normal, expected condition on somebody
 * else's topic, and a warn per frame would fill the log during a backfill with something nobody
 * should act on.
 *
 * <h3>That warn names a fingerprint of the frame, and no longer an excerpt of it</h3>
 *
 * <p>It logged the first hundred characters of the payload until 2026-09-07, and that was <b>the one
 * statement in this service whose content nobody on this side had vetted</b>: the frame is by
 * definition one this parser could not read, on a topic this service does not own, so what those
 * hundred characters hold is whatever a sibling — or anything else with write access to the topic —
 * put there. Everything else in the item 43 pass is about keeping a <em>known</em> identifier out of
 * an estate-wide log store; leaving open a channel that copies <em>unknown</em> content into the
 * same place would have been the same defect with the blame moved.
 *
 * <p><b>It is not a loss of diagnosability, and on the question actually asked it is a gain.</b>
 * What this line has to answer is "is this one frame being redelivered, or many different ones?" —
 * and two frames that differ after the hundredth character are indistinguishable under truncation
 * and distinct under a digest. The topic and the parser's own complaint are still named, so the
 * remaining question is what the bytes say; that is answered deliberately, by reading the frame off
 * the broker with a console consumer, rather than by mirroring every such frame into a fourteen-day
 * queryable store shared with five other products on the chance that somebody looks.
 *
 * <p>One thing this does <b>not</b> control and should not be assumed to: {@code e.toString()} is
 * the parser's own exception message, and Jackson historically quoted the offending input in it.
 * {@code StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION} has defaulted to disabled since Jackson 2.16
 * and this service is well past that, so the message reads {@code REDACTED} where the source used to
 * be — but that is a library default rather than something this code asserts, so
 * {@code SiblingEventParserTest} pins the whole line rather than the argument, and would fail if the
 * default were ever turned back on.
 *
 * <h2>Timestamps are read from both forms, and a frame without one is not an event</h2>
 *
 * <p>Both products put a real {@code Instant} on a record in one place and an
 * {@code Instant.now().toString()} into a {@code Map} in another — hc-professional's api publishes
 * the {@code DomainEventEnvelope} record while its gateway builds a {@code LinkedHashMap}, and
 * hc-patient publishes the {@code PatientEvent} record from both of its applications. So
 * {@code occurredAt} arrives as an ISO-8601 string from some producers and as whatever that
 * application's ObjectMapper makes of an {@code Instant} — a string, or a numeric epoch — from
 * others, and that is a setting in repositories this one does not own. Both forms are read rather
 * than one being assumed.
 *
 * <p><b>A frame whose {@code occurredAt} cannot be read at all is ignored, and until 2026-09-05 it
 * fell back to {@code Instant.now()}.</b> That fallback read as the cautious choice and was the
 * opposite of one. {@code occurredAt} is the watermark, so stamping an unreadable frame with the
 * present <b>freezes its subject against every event older than the moment it arrived</b> — and
 * these consumer groups start at the earliest offset, so on the first run "now" is later than every
 * frame in the topic. One frame with an unreadable timestamp would therefore discard the rest of
 * that subject's history as stale, during exactly the backfill that history is being read for.
 * Silent, and indistinguishable from the consumer having stopped.
 *
 * <p>Ignoring the frame loses one event. The fallback lost every event about that person that had
 * not yet been applied, which is the strictly worse of the two, and it is a case neither producer can
 * reach today: both always set the field.
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

    // --- hc-patient's published set, all seven of it ----------------------------------------------

    /** Registration on hc-patient's gateway — <b>and</b> a care-angel nomination, which is the trap. */
    private static final String PATIENT_ACCOUNT_CREATED = "AccountCreated";

    /** Activation, and the second of the two frames a care-angel nomination emits. */
    private static final String PATIENT_ACCOUNT_ACTIVATED = "AccountActivated";

    /** The patient's record now exists on the far side. The one event binding an email to a patientId. */
    private static final String PATIENT_ONBOARDING_STARTED = "OnboardingStarted";

    private static final String PATIENT_ONBOARDING_STEP_COMPLETED = "OnboardingStepCompleted";
    private static final String PATIENT_ONBOARDING_COMPLETED = "OnboardingCompleted";

    /** Keyed on the <b>patient's</b> address, with the angel's carried in {@code data.angelEmail}. */
    private static final String PATIENT_CARE_DELEGATION_CHANGED = "CareDelegationChanged";

    /** {@code RAISED}, {@code CANCELLED}, {@code REJECTED} — or {@code COMPLETED}, after the erasure. */
    private static final String PATIENT_DELETION_REQUEST_CHANGED = "DeletionRequestChanged";

    /** The {@code data} discriminator that says the far side has already erased the subject. */
    private static final String DELETION_COMPLETED = "COMPLETED";

    /**
     * What a care-angel nomination puts in {@code data.authorities}, and the reason it gives.
     *
     * <p>Either alone is enough. The authority is the fact; {@code reason} is hc-patient's own label
     * for why the account was made, and is checked as well because the two are set at one call site
     * and a change to that site is more likely to keep one than both.
     */
    private static final String CARE_ANGEL_AUTHORITY = "ROLE_ANGEL";
    private static final String CARE_ANGEL_REASON = "careAngelNomination";

    // --- hc-professional's published set on the subscribed topic ---------------------------------

    private static final String PROFESSIONAL_REGISTRATION_CREATED = "registration.created";
    private static final String PROFESSIONAL_ONBOARDING_STATE = "onboarding.state";

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
        JsonNode data = node.path("data");

        String type = text(node, "type");
        String key = normaliseKey(headerKey != null && !headerKey.isBlank() ? headerKey : text(subject, "email"));
        if (type == null || key == null) {
            LOG.warn("Ignoring a patient-events frame with no {}", type == null ? "type" : "subject key");
            return Optional.empty();
        }

        Instant occurredAt = occurredAt(node, type);
        if (occurredAt == null) {
            return Optional.empty();
        }

        boolean careAngel = isCareAngelNomination(data);
        Disposition disposition = patientDisposition(type, careAngel);
        if (disposition == null) {
            LOG.debug("Ignoring a patient-events frame of type {}, which this service does not model", type);
            return Optional.empty();
        }

        // Only the two account events say anything about signing in, and a nomination's do not count:
        // an angel's account being activated is not a patient becoming active. It cannot reach a
        // Patient anyway — the merge rule only ever reads this for a subject that has a local record,
        // and an angel has none — but leaving it true would put a fact about the wrong person one
        // refactor away from being acted on.
        boolean activated =
            !careAngel &&
            (PATIENT_ACCOUNT_ACTIVATED.equals(type) || (PATIENT_ACCOUNT_CREATED.equals(type) && data.path("activated").asBoolean(false)));

        return Optional.of(
            new SiblingDomainEvent(
                DirectorySource.HC_PATIENT,
                text(node, "eventId"),
                type,
                occurredAt,
                key,
                text(subject, "email"),
                text(subject, "login"),
                text(subject, "patientId"),
                type,
                activated,
                disposition,
                subjectKindFor(disposition, careAngel),
                PATIENT_DELETION_REQUEST_CHANGED.equals(type) && DELETION_COMPLETED.equals(text(data, "change"))
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
     * <p>Both types are {@link Disposition#LINK_ONLY}. No local row is created for a clinician at
     * all — {@code Professional} requires a {@code role} and a {@code licenceNumber}, and neither is
     * on the wire — so the distinction {@code CREATE} draws does not arise on this stream.
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
        if (!PROFESSIONAL_REGISTRATION_CREATED.equals(type) && !PROFESSIONAL_ONBOARDING_STATE.equals(type)) {
            // The topic also carries nothing else today, but hc-professional owns it and may add to
            // it, and hc.professional.entity's three types show what that looks like when it happens.
            LOG.debug("Ignoring an hc.professional.registration frame of type {}, which this service does not model", type);
            return Optional.empty();
        }

        Instant occurredAt = occurredAt(node, type);
        if (occurredAt == null) {
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
                occurredAt,
                accountId,
                text(content, "email"),
                text(content, "login"),
                accountId,
                state != null ? state : type,
                // A registration is an account that exists and can sign in; hc-professional has no
                // separate activation event on this topic. Onboarding state changes say nothing
                // about sign-in and must not move a status.
                PROFESSIONAL_REGISTRATION_CREATED.equals(type),
                Disposition.LINK_ONLY,
                DirectorySubjectKind.PROFESSIONAL,
                false
            )
        );
    }

    /**
     * What an hc-patient event of this type may do here, or null for a type this service does not
     * model.
     *
     * <p>Exhaustive over {@code PatientEventType} by design, and the {@code default} answers null
     * rather than guessing. The two lines that carry the weight are the first — a nomination is a
     * link and never a patient — and the last, where an erasure may update somebody already known
     * and may not introduce them.
     */
    private Disposition patientDisposition(String type, boolean careAngel) {
        return switch (type) {
            case PATIENT_ACCOUNT_CREATED -> careAngel ? Disposition.LINK_ONLY : Disposition.CREATE;
            // The one event that proves a Profile exists on the far side, and the only one carrying
            // the patientId. It creates as well as AccountCreated does, which is also what lets a
            // care angel who later registers as a patient in their own right become one here: their
            // account already exists, so hc-patient publishes no second AccountCreated for them.
            case PATIENT_ONBOARDING_STARTED -> Disposition.CREATE;
            case PATIENT_ACCOUNT_ACTIVATED,
                PATIENT_ONBOARDING_STEP_COMPLETED,
                PATIENT_ONBOARDING_COMPLETED,
                PATIENT_CARE_DELEGATION_CHANGED,
                PATIENT_DELETION_REQUEST_CHANGED -> Disposition.UPDATE_ONLY;
            default -> null;
        };
    }

    /** What this event says the subject is, or null when it says nothing and must not overwrite. */
    private DirectorySubjectKind subjectKindFor(Disposition disposition, boolean careAngel) {
        if (disposition == Disposition.CREATE) {
            return DirectorySubjectKind.PATIENT;
        }
        return careAngel ? DirectorySubjectKind.CARE_ANGEL : null;
    }

    /**
     * Whether an {@code AccountCreated} is a care-angel nomination rather than a registration.
     *
     * <p>{@code data.authorities} is a comma-joined string on both call sites, so this is a substring
     * test bounded by the separator rather than an exact match on the whole field. A role named as a
     * prefix of another would be a false positive and there is none — but the split is done properly
     * anyway, because "no such role today" is not a property this file can keep true.
     */
    private boolean isCareAngelNomination(JsonNode data) {
        if (CARE_ANGEL_REASON.equals(text(data, "reason"))) {
            return true;
        }
        String authorities = text(data, "authorities");
        if (authorities == null) {
            return false;
        }
        for (String authority : authorities.split(",")) {
            if (CARE_ANGEL_AUTHORITY.equals(authority.trim())) {
                return true;
            }
        }
        return false;
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
            //
            // A FINGERPRINT, not an excerpt of the bytes — see the class javadoc for the argument.
            LOG.warn("Ignoring an unreadable frame on {} ({}): {}", topic, e.toString(), LogPseudonym.frame(payload));
            return null;
        }
    }

    /**
     * {@code occurredAt} as an instant, from a string or a numeric epoch — or null, which discards
     * the frame.
     *
     * <p>Null rather than {@code Instant.now()}, and the class javadoc gives the reason at length:
     * this value is the watermark, and stamping an unreadable frame with the present freezes its
     * subject against every event older than the moment it arrived. On a group reading from the
     * earliest offset that is the rest of that subject's history.
     */
    private Instant occurredAt(JsonNode node, String type) {
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
                LOG.warn("Ignoring a {} frame: occurredAt '{}' is not a timestamp this service can read", type, text);
                return null;
            }
        }
        LOG.warn("Ignoring a {} frame with no occurredAt — there is no watermark to apply it against", type);
        return null;
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }
}
