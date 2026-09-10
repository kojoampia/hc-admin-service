package net.jojoaddison.broker;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Announced when an administrator verifies the membership tier a patient chose.
 *
 * <p>Published to {@code patient-events-plan}, keyed on the patient's <b>lowercased email address</b>,
 * and consumed by hc-patient to move the {@code Membership} out of {@code PENDING}. Backlog item 54,
 * and the dequeue for item 48.
 *
 * <h2>⚠ This is hc-patient's `PatientEvent` envelope, and the backlog entry does not say so</h2>
 *
 * <p>Item 54 specifies the payload as <code>{ Plan }</code>, which reads as the whole frame. <b>It is
 * not.</b> Their consumer — {@code PlanVerificationConsumer} in hc-patient's api — is a
 * {@code Consumer<PatientEvent>}, and it refuses a frame before looking at the plan at all:
 *
 * <ul>
 *   <li>a blank {@code eventId} is refused {@code NO_EVENT_ID} — it is their idempotency key, the
 *       {@code _id} of the {@code PlanVerification} ledger row that makes a redelivery a no-op;</li>
 *   <li>a missing {@code subject.email} is refused {@code NO_SUBJECT_KEY} — <b>the subject travels in
 *       the payload</b>, not only as a message key, and that is how they read it;</li>
 *   <li>the plan is read from {@code data}, so <code>{ Plan }</code> is the <b>{@code data} map</b>
 *       and not the envelope.</li>
 * </ul>
 *
 * <p>So a literal <code>{"plan":"MELON"}</code> on this topic is dead-lettered on arrival, every time,
 * with their group committing and their DLQ filling — a healthy producer, a healthy consumer, and a
 * loop that never closes. <b>This is item 47's lesson arriving from the other side</b>: that reader
 * was written from a backlog entry rather than from the producer and matched nothing real. Here the
 * roles are reversed and the rule is the same — <b>read the far end's code, not the specification.</b>
 * Their file was read on 2026-09-10 to build this class.
 *
 * <p>The one-field decision is honoured where it actually lives: {@link #data} carries {@code plan}
 * and nothing else. {@code isVerified} and {@code MembershipID} were both specified and both dropped
 * — the first because it could hold only one value and the event type already means "verified", the
 * second because the exchange is keyed on the address and there is nothing to echo. Do not restore
 * either.
 *
 * <h2>Why {@code plan} stays, when hc-patient wrote it themselves</h2>
 *
 * <p><b>It is a consistency check, not an echo</b>, and their code is emphatic about it in
 * {@code assertPlanAgrees}: <i>"This is the whole reason plan is in a one-field payload… its use is
 * exactly this refusal, which turns a silent mismatch into a stop."</i> A patient who changed tier
 * between choosing and being verified is refused rather than silently activated onto the wrong plan.
 * Being the only field it reads as redundant, which is why both ends write it down.
 *
 * <p>The value is {@code DirectoryLink.planCode} — Abofonsa's tier code as hc-patient sent it, never
 * this catalogue's own name for it. Their check is {@code equalsIgnoreCase} on the membership's own
 * {@code plan}, so re-deriving the value through this service's {@code ServicePlan} would put a
 * translation in the middle of a comparison whose entire purpose is that both sides hold the same
 * string.
 *
 * <h2>{@code status} is deliberately absent from {@code data}</h2>
 *
 * <p>Their {@code assertActivating} tolerates one if it is present and refuses anything that is not
 * {@code ACTIVE}. Sending none is the contract and the common case. <b>Note that their vocabulary has
 * five values and not the six item 54 records: {@code VERIFIED} was ruled out on their side on
 * 2026-09-08 and an approval sets {@code ACTIVE} directly.</b> Naming a status here would be this
 * service asserting a transition that is theirs to choose.
 *
 * <h2>It holds no domain types, and that is enforced</h2>
 *
 * <p>{@code TechnicalStructureTest} does not list {@code ..broker..} as a layer, so anything here
 * reaching into {@code ..domain..} fails the ArchUnit rule — which is why the conversion from a stored
 * link lives in {@code PatientPlanVerificationService} rather than in a factory here. The same reason
 * {@link VerificationEvent} gives, and this is the second example that rule now has.
 */
public class PlanVerificationEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The envelope version hc-patient's {@code PatientEvent.VERSION} declares.
     *
     * <p>Theirs, not ours — this event rides their envelope on their topic, so the number is a fact
     * about their schema. Bump it only when they do.
     */
    public static final int VERSION = 1;

    /**
     * What this service calls itself on their stream, matching the shape of their own
     * {@code PatientEventPublisher.SOURCE} ({@code "hcPatientService"}).
     */
    public static final String SOURCE = "hcAdminService";

    /**
     * The event type string.
     *
     * <p><b>Their consumer does not dispatch on it today, and it is still a cross-repo contract.</b>
     * {@code PlanVerificationConsumer} applies every frame on the topic on purpose — their words:
     * <i>"the type string was never agreed and hc-admin has not written their half"</i>, and guessing
     * a literal would have rebuilt the half-loop that cost them a fortnight. They record it on their
     * ledger so the question can later be answered from data. <b>This is that answer</b>: the value
     * is {@code PlanVerified}, it is what they should pin when they add dispatch, and it follows the
     * naming of their own {@code PatientEventType.PLAN_CHOSEN} ({@code "PlanChosen"}).
     */
    public static final String TYPE = "PlanVerified";

    private String eventId;
    private String type;
    private int version;
    private String occurredAt;
    private String source;
    private Subject subject;
    private PlanData data;

    public PlanVerificationEvent() {}

    public PlanVerificationEvent(String eventId, Instant occurredAt, String email, String plan) {
        this.eventId = eventId;
        this.type = TYPE;
        this.version = VERSION;
        // Formatted here rather than left as an Instant for the field to explain.
        this.occurredAt = occurredAt == null ? null : occurredAt.toString();
        this.source = SOURCE;
        this.subject = new Subject(email);
        this.data = new PlanData(plan);
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * When this decision was taken, as an ISO-8601 instant — {@code 2026-09-10T09:15:30.500Z}.
     *
     * <h2>⚠ A {@code String} on purpose, and reverting it to an {@code Instant} is a silent break</h2>
     *
     * <p>Their {@code PatientEvent.occurredAt} is an {@code Instant}, so the obvious modelling here is
     * one too — and it was, until the test that reads this frame's bytes measured what came out.
     * A typed {@code Instant} is written by <b>whatever {@code ObjectMapper} is injected</b>, and in
     * this service that is {@code WebConfigurer.objectMapper()}:
     *
     * <pre>{@code return new ObjectMapper().registerModule(new JavaTimeModule());}</pre>
     *
     * <p><b>Bare.</b> An explicit {@code @Bean}, so Spring Boot's builder — which would have disabled
     * {@code WRITE_DATES_AS_TIMESTAMPS} — backs off and never runs. The feature is therefore <b>on</b>,
     * and a typed {@code Instant} emits {@code 1.7890317305E9}, a scientific-notation double, <b>on
     * this stack today</b>. So this is a live fix rather than future-proofing, which is also why
     * {@code PatientPlanVerificationServiceTest}'s deliberately bare mapper mirrors production exactly
     * instead of merely being unconfigured.
     *
     * <p><b>What the casualty would have been, stated precisely, because the obvious guess is wrong.</b>
     * Not delivery: their Jackson binds the numeric form to an {@code Instant} without complaint —
     * measured, not assumed. The loss is downstream of it. That value is written to their
     * {@code PlanVerification} ledger and is what an operator reads when asking when a decision was
     * taken, so the frame would apply correctly and leave a row whose timestamp no human can read at a
     * glance. A silent degradation in the record rather than a refusal, which is the harder kind to
     * notice.
     *
     * <p>Pinning the format in the field removes the dependency on that bean altogether — so a future
     * item switching this service to Boot's builder, or configuring the mapper either way, cannot
     * change what another product receives.
     *
     * <p>{@code Instant.toString()} is {@code DateTimeFormatter.ISO_INSTANT}, which their Jackson
     * reads into an {@code Instant} without configuration.
     */
    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }

    public PlanData getData() {
        return data;
    }

    public void setData(PlanData data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return (
            "PlanVerificationEvent{eventId='" +
            eventId +
            "', type='" +
            type +
            "', version=" +
            version +
            ", occurredAt='" +
            occurredAt +
            "', source='" +
            source +
            "', plan='" +
            (data == null ? null : data.plan()) +
            "'}"
        );
    }

    /**
     * Who the verification is about.
     *
     * <p>Their {@code PatientEvent.Subject} carries {@code email}, {@code login} and {@code patientId}
     * as well. <b>Only the address is sent</b>, and the other two are omitted rather than nulled:
     * this service does not hold hc-patient's login or their {@code patientId} — the
     * {@code directory_link} stores their address as the correlation key and nothing else that names
     * them there — so sending {@code null}s would be this end asserting it looked and found nothing,
     * when it never had the values at all. Jackson omits absent record components from the object
     * either way; their reader touches only {@code email}.
     *
     * @param email the patient's address, <b>lowercased</b> — see
     *              {@code PatientPlanVerificationService.subjectOf} for why that is load-bearing.
     */
    public record Subject(String email) implements Serializable {}

    /**
     * The payload, which is the whole of item 54's <code>{ Plan }</code>.
     *
     * <p>A typed record rather than a {@code Map} so that "one field" is a property of the class and
     * not of whatever a caller happened to put in a map. It serialises to exactly
     * <code>{"plan":"MELON"}</code>, which is what their {@code planCodeIn} reads.
     *
     * <p>Their reader currently accepts {@code plan} <i>or</i> {@code planCode}, and a string
     * <i>or</i> an object with a {@code code}. That tolerance is explicitly temporary — their javadoc
     * says <i>"collapse this to one spelling when their item 54 lands, and record which they
     * chose"</i>. <b>This is the choice: the key is {@code plan} and the value is a flat string.</b>
     *
     * @param plan the tier code, as hc-patient sent it — {@code PEAR}, {@code PAWPAW}, {@code MELON}
     */
    public record PlanData(String plan) implements Serializable {}
}
