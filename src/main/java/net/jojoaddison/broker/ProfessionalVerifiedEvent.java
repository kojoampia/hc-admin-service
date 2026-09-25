package net.jojoaddison.broker;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * A clinician verification decision, as it travels on {@code admin.event}. Backlog item 145.
 *
 * <h2>This leg is a construction, not a migration, and that is the difference worth knowing</h2>
 *
 * <p>hc-patient's return leg has an envelope, a live consumer and a working contract; moving it is a
 * destination change. This one had <b>none of those</b>. {@link VerificationEvent} is a bare payload —
 * {@code verificationId}, {@code professionalId}, {@code licenceNumber}, {@code status},
 * {@code recordedAt}, {@code recordedBy} — with no {@code eventId}, no {@code type}, no
 * {@code occurredAt} and no {@code source}, because nothing ever consumed it and so nothing ever made
 * it be an envelope. <b>{@code professional-verification} has been published into silence since it
 * shipped.</b> This class gives the decision an envelope for the first time.
 *
 * <p>{@link VerificationEvent} is <b>unchanged and still published</b> to
 * {@code professional-verification} and to the SSE fan-out. Item 145 step 1 is additive; the old topic
 * goes only after hc-professional's item 144 consumes this channel and its lag reaches zero.
 *
 * <p>⚠ <b>Nothing reads this type yet.</b> hc-professional 144 is unbuilt, so this frame is
 * <b>unverified against any consumer</b> — accepted knowingly by the architect (item 145 D2) rather
 * than discovered later. It is not silence of the old kind, because {@code admin.event} has live
 * readers for {@link AdminEntityEvent#TYPE}, but nothing confirms this frame is <em>usable</em> until
 * 144 lands.
 *
 * <h2>⛔ {@code licenceNumber} is deliberately NOT here</h2>
 *
 * <p>Dropped by the architect on 2026-09-25 (item 145, D3). {@code professional-verification} reached
 * nobody; {@code admin.event} is read by three products, so carrying it would be a new exposure rather
 * than a continued one. hc-professional <b>owns</b> the licence number, so sending theirs back to them
 * carries no information they lack, and it is a clinician's professional identifier — the class item 43
 * took out of logs.
 *
 * <p>The 2026-09-18 rule is that <b>a command carries the value it decides</b>. Here that value is
 * {@code status}. A licence number is an attribute of the subject, not the decision, so the rule does
 * not reach it. <b>The legacy frame keeps the field</b>: the drop is a property of this shape only, so
 * nothing that ships today changes.
 *
 * <h2>{@code subject} is the verification row</h2>
 *
 * <p>Unlike {@link PlanVerifiedEvent}, this decision <b>does</b> write a record —
 * {@code ProfessionalVerification} is a collection, and the history is what the console reads. So the
 * subject is that row and {@code entityId} is its id, with no interpretation needed.
 *
 * <p>⚠ <b>Two frames will therefore describe one row</b>, and that is intended rather than duplicate:
 * {@code EntityChangeAnnouncer} announces the save as {@link AdminEntityEvent#TYPE}, and this announces
 * the decision. They say different things — "a row changed" and "here is what was decided" — and
 * because both key on {@code ProfessionalVerification/<id>} (see {@link AdminChannel}) they cannot
 * arrive out of order. A consumer that wants only one dispatches on {@code type}.
 *
 * <p>It holds no domain types, for the ArchUnit reason {@link AdminEntityEvent} and
 * {@link VerificationEvent} both give: {@code ..broker..} is not a declared layer, so the conversion
 * from a saved row lives in the service.
 */
public class ProfessionalVerifiedEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** The envelope version, and ours — this frame rides this product's own channel. */
    public static final int VERSION = 1;

    /** What this service calls itself, matching {@link AdminEntityEvent#SOURCE}. */
    public static final String SOURCE = "hcAdminService";

    /** The discriminator a consumer dispatches on. */
    public static final String TYPE = "ProfessionalVerified";

    /**
     * The entity type named in {@code subject}, as a domain class's simple name.
     *
     * <p>A literal rather than {@code ProfessionalVerification.class.getSimpleName()} because this
     * package may not name a domain type.
     */
    public static final String SUBJECT_ENTITY_TYPE = "ProfessionalVerification";

    private String eventId;
    private String type;
    private int version;
    private String occurredAt;
    private String source;
    private AdminEntityEvent.Subject subject;
    private VerificationData data;

    public ProfessionalVerifiedEvent() {}

    public ProfessionalVerifiedEvent(
        String eventId,
        Instant occurredAt,
        String verificationId,
        String professionalId,
        String status,
        String actorAccountId
    ) {
        this.eventId = eventId;
        this.type = TYPE;
        this.version = VERSION;
        this.occurredAt = occurredAt == null ? null : occurredAt.toString();
        this.source = SOURCE;
        this.subject = new AdminEntityEvent.Subject(SUBJECT_ENTITY_TYPE, verificationId);
        this.data = new VerificationData(status, professionalId, actorAccountId);
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

    public VerificationData getData() {
        return data;
    }

    public void setData(VerificationData data) {
        this.data = data;
    }

    /**
     * The decided value, the clinician it is about, and who decided it.
     *
     * <p>{@code actorAccountId} is the gateway {@code User.id} and <b>never a login</b>, matching
     * {@link AdminEntityEvent.ChangeData}. ⚠ Note this differs from {@link VerificationEvent}'s
     * {@code recordedBy}, which holds the <b>login</b> because it mirrors a local column that has always
     * held one. The two fields are not the same value and must not be copied across.
     *
     * <p>Absent when unknown rather than filled with a placeholder — the estate rule since item 129.
     */
    public record VerificationData(
        String status,
        String professionalId,
        @JsonInclude(JsonInclude.Include.NON_NULL) String actorAccountId
    ) implements Serializable {}
}
