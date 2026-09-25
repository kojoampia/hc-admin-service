package net.jojoaddison.broker;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * An administrator's plan verification, as it travels on {@code admin.event}. Backlog item 145.
 *
 * <p><b>This is a second frame for a decision that already has one.</b> {@link PlanVerificationEvent}
 * carries the same decision on {@code patient-events-plan} in hc-patient's own {@code PatientEvent}
 * shape, and it is <b>unchanged and still published</b>. Item 145 step 1 is additive: both
 * destinations stay live until hc-patient's item 47 reads this channel and their lag on the old topic
 * reaches zero, at which point — and only then — the old binding is removed. <b>Remove the consumer
 * before the producer</b>: a producer writing where nobody reads is harmless, a consumer reading where
 * nobody writes is silence that looks like health, which is exactly how
 * {@code professional-verification} went unnoticed for months.
 *
 * <h2>Why this is not {@link PlanVerificationEvent} with a different destination</h2>
 *
 * <p>Decided by the architect on 2026-09-25 (item 145, D1). {@code admin.event} has one envelope and
 * one meaning for {@code subject} — {@code (entityType, entityId)}, which all four products now agree
 * on. Publishing hc-patient's shape here would have put <b>two envelope contracts on one channel</b>
 * and made {@code subject} mean something different depending on {@code type}, which is the divergence
 * item 110 exists to end, reintroduced on the channel built to end it.
 *
 * <h2>{@code subject} is the {@code DirectoryLink}, and it is not a fake id</h2>
 *
 * <p>A plan verification <b>writes nothing to this database</b> — {@code PatientPlanVerificationService}
 * says so in as many words, "the event is the record" — so there is no <em>changed</em> row to name.
 * The {@code DirectoryLink} is nonetheless a real record this service owns, it carries the plan choice
 * being verified, and it is what the administrator pressed the button on. The estate rule is that
 * subject means <b>the thing this event is about</b>, which it is; the narrower reading, "the row that
 * just changed", is what would have forced an invented id.
 *
 * <p>The payoff is ordering: the key is {@code DirectoryLink/<id>}, so two presses for one link arrive
 * in order <b>and</b> an {@link AdminEntityEvent} for that same link lands on the same partition. See
 * {@link AdminChannel}.
 *
 * <h2>{@code data} carries the decided value, and that is the stated exception</h2>
 *
 * <p>Item 110's rule is identifiers and metadata only. The architect's ruling of 2026-09-18, stated as
 * a rule rather than an exception so the next command-shaped event does not re-litigate it:
 * <b>a notification carries identifiers and metadata; a command carries the value it decides.</b> Here
 * that value is {@code plan}. Neither of item 110's two reasons is touched by it — a plan name is not
 * identifying content, and it mirrors no sibling's entity.
 *
 * <p>{@code subjectKey} is the addressee, and it is on the frame because <b>hc-patient cannot ask</b>:
 * they have no route to this service, measured 2026-09-17, so "publish the id and let them ask" was not
 * available. It is the same lowercased, trimmed {@code DirectoryLink.externalKey} that
 * {@code patient-events-plan} keys on today, so the value a consumer joins on does not change when the
 * destination does — <b>one migration at a time</b>, and item 26 is what a wrong join key costs.
 *
 * <p>⚠ <b>It is deliberately in {@code data} and not in {@code subject}.</b> Putting it in the subject
 * is the option D1 rejected; putting it in both would give a consumer two places to read one value and
 * a way for them to disagree.
 *
 * <p>It holds no domain types — {@code TechnicalStructureTest} does not list {@code ..broker..} as a
 * layer, so anything here reaching into {@code ..domain..} fails the ArchUnit rule. The conversion from
 * a {@code DirectoryLink} therefore lives in the service, exactly as it does for
 * {@link AdminEntityEvent} and {@link VerificationEvent}.
 */
public class PlanVerifiedEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The envelope version, and <b>ours</b> — unlike {@link PlanVerificationEvent#VERSION}, which is a
     * fact about hc-patient's schema because that frame rides their topic. This one rides this
     * product's channel, so the number moves when this shape does.
     */
    public static final int VERSION = 1;

    /** What this service calls itself, matching {@link AdminEntityEvent#SOURCE}. */
    public static final String SOURCE = "hcAdminService";

    /**
     * The discriminator a consumer dispatches on.
     *
     * <p>Distinct from {@link PlanVerificationEvent#TYPE} ({@code "PlanVerified"}) only by accident of
     * them describing the same decision — <b>do not merge the two constants</b>. That one is a fact
     * about hc-patient's schema and moves if they rename it; this one is a fact about this channel.
     */
    public static final String TYPE = "PlanVerified";

    private String eventId;
    private String type;
    private int version;
    private String occurredAt;
    private String source;
    private AdminEntityEvent.Subject subject;
    private PlanData data;

    public PlanVerifiedEvent() {}

    public PlanVerifiedEvent(String eventId, Instant occurredAt, String linkId, String plan, String subjectKey) {
        this.eventId = eventId;
        this.type = TYPE;
        this.version = VERSION;
        this.occurredAt = occurredAt == null ? null : occurredAt.toString();
        this.source = SOURCE;
        this.subject = new AdminEntityEvent.Subject(SUBJECT_ENTITY_TYPE, linkId);
        this.data = new PlanData(plan, subjectKey);
    }

    /**
     * The entity type named in {@code subject}, as a domain class's simple name.
     *
     * <p>A literal rather than {@code DirectoryLink.class.getSimpleName()} for the ArchUnit reason
     * above: this package may not name a domain type. {@code EntityChangedEnvelopeTest}'s sibling
     * guard is what keeps the literal honest.
     */
    public static final String SUBJECT_ENTITY_TYPE = "DirectoryLink";

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

    public AdminEntityEvent.Subject getSubject() {
        return subject;
    }

    public void setSubject(AdminEntityEvent.Subject subject) {
        this.subject = subject;
    }

    public PlanData getData() {
        return data;
    }

    public void setData(PlanData data) {
        this.data = data;
    }

    /**
     * The decided value, and the addressee.
     *
     * <p>{@code subjectKey} is {@code NON_NULL} for the reason {@link AdminEntityEvent.ChangeData}'s
     * {@code actorAccountId} is: an absent key says "this service does not know", where an explicit
     * null says "this service knows, and the answer is nothing". That is the estate rule since item 129.
     * In practice it is never absent here — {@code PatientPlanVerificationService} refuses to publish a
     * frame with no subject key — so the annotation is a statement of the rule rather than a live branch.
     */
    public record PlanData(String plan, @JsonInclude(JsonInclude.Include.NON_NULL) String subjectKey) implements Serializable {}
}
