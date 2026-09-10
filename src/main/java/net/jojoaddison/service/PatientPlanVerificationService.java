package net.jojoaddison.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import net.jojoaddison.broker.OutboundEventPublisher;
import net.jojoaddison.broker.PlanVerificationEvent;
import net.jojoaddison.config.Constants;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Tells hc-patient that an administrator has verified the membership tier a patient chose.
 *
 * <p>Backlog item 54, and the dequeue for item 48: hc-patient publishes {@code PlanChosen}, the four
 * fields land on the {@code directory_link}, the console lists them as "plan choices awaiting a
 * decision", and this is what empties that list.
 *
 * <h2>This service stores nothing, and that is the decision rather than a shortcut</h2>
 *
 * <p>hc-patient owns {@code Membership.status}. This console acts on what item 48 delivered and
 * <b>the published event is the record</b> — so there is no new collection, no field on
 * {@code DirectoryLink}, and nothing beside {@code Patient.plan}. Three consequences follow and each
 * one looks like a bug to somebody who arrives without this paragraph:
 *
 * <ul>
 *   <li><b>The link goes on reading {@code PENDING} after a verification.</b> That field is what
 *       hc-patient reported when the membership was <em>created</em>, and their
 *       {@code MembershipResource} publishes on {@code POST} alone, so nothing announces the state
 *       moving. The row leaves the console's panel when their next {@code PlanChosen} says so, not
 *       when this method returns. Do not "fix" it by writing {@code VERIFIED} onto the link — that
 *       is this service inventing a status for a field whose vocabulary belongs to another product,
 *       and the next event would silently put it back.</li>
 *   <li><b>Pressing the button twice publishes twice.</b> Deliberate: the echo is the
 *       acknowledgement, and republishing unconditionally is what makes a lost frame recoverable by
 *       repeating the act. There is no local state to make it idempotent against, and hc-patient
 *       verifying an already-verified membership is a no-op on their side.</li>
 *   <li><b>{@code AuditLogCallback} writes nothing.</b> It fires on save and delete, and there is no
 *       save here — so the {@code INFO} line in {@link #record} is the <em>only</em> local trace that
 *       an administrator took this decision. That is a gap rather than a property: the payload is one
 *       field and carries no actor, so neither product can answer "who verified this, and when".
 *       It is recorded in backlog item 54's report rather than closed here, because closing it means
 *       a collection and this item was specified to add none.</li>
 * </ul>
 *
 * <h2>The subject is lowercased here, and the lowercasing is load-bearing</h2>
 *
 * <p>See {@link #subjectOf}. This has cost the estate once already.
 */
@Service
public class PatientPlanVerificationService {

    private static final Logger LOG = LoggerFactory.getLogger(PatientPlanVerificationService.class);

    /** The plan-verification topic hc-patient consumes. Bound to `patient-events-plan`. */
    private static final String PLAN_VERIFICATION_BINDING = "plan-verification-out-0";

    private final OutboundEventPublisher eventPublisher;

    private final ObjectMapper objectMapper;

    /** Injected so a test can publish at a known instant rather than at "now", as ProfessionalVerificationService does. */
    private final Clock clock;

    public PatientPlanVerificationService(OutboundEventPublisher eventPublisher, ObjectMapper objectMapper, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Publishes one verification and returns what went on the wire.
     *
     * <p><b>Nothing is written to this database.</b> See the class javadoc — the event is the record.
     *
     * <p>The publish is queued and never waits: {@link OutboundEventPublisher} hands it to a
     * single-threaded bounded executor, so an unreachable broker cannot make this request hang, and a
     * failure to reach one is a {@code WARN} and nothing else. The caller is therefore told that the
     * decision was <em>accepted</em>, which is why the resource answers {@code 202} rather than
     * {@code 200} — this method cannot promise delivery and must not appear to.
     *
     * @param link the patient's directory link, carrying the plan choice being verified. The caller
     *             has already established that it is an {@code HC_PATIENT} {@code PATIENT} link with a
     *             tier named on it.
     * @return the event as published.
     */
    public PlanVerificationEvent record(DirectoryLink link) {
        String plan = link.getPlanCode();
        if (plan == null || plan.isBlank()) {
            // Unreachable through PatientPlanVerificationResource, which refuses this with a 400
            // before calling. Loud rather than silent because the alternative is publishing
            // `{"plan":null}`, which reads as a well-formed verification of nothing and defeats the
            // consistency check that is this payload's only reason to carry a field at all —
            // hc-patient would refuse it NO_PLAN_NAMED and dead-letter it.
            throw new IllegalStateException("A plan verification was asked for a link naming no tier");
        }

        String subject = subjectOf(link);
        if (subject == null) {
            throw new IllegalStateException("A plan verification was asked for a link with no subject key");
        }

        // Fresh per emission and never derived from the link, because it is hc-patient's idempotency
        // key: it becomes the `_id` of their PlanVerification ledger row, so two presses of the button
        // must be two ids or the second is silently ignored as a redelivery. That is the opposite of
        // what "republish unconditionally, the echo is the acknowledgement" asks for — a repeat is a
        // deliberate act by an administrator and must reach them as one.
        PlanVerificationEvent event = new PlanVerificationEvent(
            UUID.randomUUID().toString(),
            Instant.now(clock).truncatedTo(ChronoUnit.MILLIS),
            subject,
            plan
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException | RuntimeException e) {
            // Serialising one string cannot fail in practice; matched to the shape
            // ProfessionalVerificationService.announce uses so a defect here reports against the
            // decision rather than surfacing later on a publisher thread with nothing to name.
            LOG.warn("A plan verification for {} could not be serialised", LogPseudonym.subject(subject), e);
            throw new IllegalStateException("A plan verification could not be serialised", e);
        }

        // THE ONLY LOCAL TRACE OF THIS DECISION — see the class javadoc's third bullet. The actor is
        // a login, which is what AuditLog.userId already holds and is safe to write; the patient is a
        // digest, never the address, because item 43 established that these lines reach an
        // unauthenticated estate-wide Loki. Never render LogPseudonym on a screen.
        LOG.info(
            "{} verified the {} plan choice for {}; announcing on {}",
            SecurityUtils.getCurrentUserLogin().orElse(Constants.SYSTEM),
            plan,
            LogPseudonym.subject(subject),
            PLAN_VERIFICATION_BINDING
        );

        // The fourth argument is the partition key, and it is the whole reason this call is not the
        // three-argument form every other publisher in this service uses. See
        // OutboundEventPublisher.publish(String, String, String, String).
        eventPublisher.publish(PLAN_VERIFICATION_BINDING, payload, "Plan verification for " + LogPseudonym.subject(subject), subject);
        return event;
    }

    /**
     * The patient's address, lowercased — the key hc-patient joins this event on.
     *
     * <p><b>The lowercasing is not defensive tidying and the estate has already paid for skipping
     * it.</b> hc-patient's join is an equality test on a lowercased address, so a frame keyed
     * {@code Ama.Mensah@Example.COM} matches nothing there: it is accepted, committed, and silently
     * applied to no membership. That is item 26's wrong join key exactly — a healthy producer, a
     * healthy consumer, no lag, no dead letter, and a screen that never changes.
     *
     * <p>It is normalised <b>here</b> rather than trusted from the document, and the difference is
     * real rather than theoretical. {@code SiblingEventParser.normaliseKey} lowercases every key that
     * arrives over the broker, so a link learned from an event is already safe — but a link can also
     * come from {@code hc-admin-ms-data.json}, which is hand-written JSON that reaches the collection
     * through {@code saveAll} without passing the parser at all. Normalising at the publish point is
     * what makes the guarantee a property of what this service <em>sends</em>, rather than of where
     * its rows happened to come from.
     *
     * <p>{@code externalKey} rather than {@code email}: for an {@code HC_PATIENT} link the two hold
     * the same address, and the external key is the one the exchange is defined on — it is what the
     * inbound {@code PlanChosen} was keyed by, so keying the answer on anything else would make the
     * round trip join on two different fields.
     */
    private String subjectOf(DirectoryLink link) {
        String key = link.getExternalKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        return key.trim().toLowerCase(Locale.ROOT);
    }
}
