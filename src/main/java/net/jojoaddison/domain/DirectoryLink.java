package net.jojoaddison.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.DirectorySource;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One account on a sibling stack, and what this directory has learned about it from the broker.
 *
 * <h2>Why this is a collection of its own rather than three fields on {@code Patient}</h2>
 *
 * <p>The obvious shape is to hang the sibling's identifiers off the local record. It was rejected
 * for two reasons and both are worth keeping.
 *
 * <p>The first is that <b>how hc-admin should model a cross-stack patient identity is an open
 * decision</b> — backlog item 22, which weighs a {@code patientId} field, a read across at use time,
 * and not naming patients here at all. A field added here as a side effect of fixing a consumer
 * would be populated only for accounts registered after this shipped, would look like the answer,
 * and would be exactly the "plausible wrong id" failure that entry exists to prevent. This
 * collection is deliberately <em>beside</em> the domain record, so item 22 is still free to decide.
 *
 * <p>The second is that <b>an event carries far less than a record.</b> {@code Patient} is an
 * administrator's document — plan, hub, clinical lead, case count — and none of that is on the wire.
 * Mixing the two on one document invites the next reader to write a merge that overwrites an edit.
 * The rule instead lives in one place, {@link net.jojoaddison.service.DirectoryProjectionService},
 * and this document is only ever the identity map and the watermark.
 *
 * <h2>Idempotency without a unique index</h2>
 *
 * <p>{@code (source, externalKey)} is the natural key, and nothing in this service creates indexes:
 * there is no {@code @Indexed} anywhere and {@code auto-index-creation} is off, so a unique index
 * declared here would be a comment rather than a constraint. The write is made atomic instead —
 * {@code findAndModify} with {@code upsert}, which MongoDB serialises per document — so a redelivery
 * and the reconciliation endpoint racing each other still produce one link. See
 * {@code DirectoryProjectionService.upsertLink}.
 */
@Document(collection = "directory_link")
public class DirectoryLink implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("source")
    private DirectorySource source;

    /**
     * The correlation key, and the half of the natural key that varies.
     *
     * <p>Lowercased email for {@link DirectorySource#HC_PATIENT} — hc-patient's own
     * {@code PatientEvent} javadoc explains why it cannot be a patient id: there is no patient until
     * onboarding step 1, and the two account events happen before that. It is also the value
     * hc-patient sets as the Kafka partition key, so every event about one person arrives here in
     * order, on one thread.
     *
     * <p>{@code accountId} for {@link DirectorySource#HC_PROFESSIONAL}, which is what that stream is
     * keyed on throughout — registration and onboarding state alike.
     */
    @Field("external_key")
    private String externalKey;

    /**
     * The sibling's own identifier for the subject, when the stream has published one.
     *
     * <p>For a patient this is the {@code patientId}, and it arrives only with
     * {@code OnboardingStarted} — the one event that binds an email to a patient id. Null before
     * that, which is a real state and not a defect. For a professional it is the {@code accountId},
     * so it equals {@code externalKey}; carrying it anyway keeps a reader from having to know which
     * source stores identity where.
     */
    @Field("external_id")
    private String externalId;

    @Field("login")
    private String login;

    @Field("email")
    private String email;

    /**
     * The last lifecycle state this subject was reported in — an event type for hc-patient, the
     * {@code state} payload field for hc-professional's {@code onboarding.state}.
     *
     * <p>A free string on purpose. Both producers say plainly that a consumer meeting a type it does
     * not know must ignore it, so binding this to an enum here would turn "they added an event"
     * into "this service refuses a message".
     */
    @Field("state")
    private String state;

    /**
     * The local document this subject produced, when it produced one.
     *
     * <p>Null for every {@link DirectorySource#HC_PROFESSIONAL} link today, and that is stated
     * rather than pending: {@code Professional} requires a {@code role} and a {@code licenceNumber},
     * neither of which is on a registration event nor could be — they are what credentialing
     * collects. A row invented with a fabricated licence number in a directory whose whole purpose
     * is verification is worse than no row.
     */
    @Field("local_id")
    private String localId;

    @Field("first_seen_at")
    private Instant firstSeenAt;

    /**
     * The watermark: {@code occurredAt} of the newest event applied to this subject.
     *
     * <p>What makes replay safe in the direction that matters. Delivery is at least once and a
     * consumer group reading from the earliest offset re-reads the whole topic, so an older event
     * arriving after a newer one is normal. Anything strictly older than this is not applied — which
     * also protects an administrator's edit from being undone by a message from before they made it.
     */
    @Field("last_event_at")
    private Instant lastEventAt;

    @Field("last_event_id")
    private String lastEventId;

    @Field("last_event_type")
    private String lastEventType;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DirectorySource getSource() {
        return source;
    }

    public void setSource(DirectorySource source) {
        this.source = source;
    }

    public String getExternalKey() {
        return externalKey;
    }

    public void setExternalKey(String externalKey) {
        this.externalKey = externalKey;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getLocalId() {
        return localId;
    }

    public void setLocalId(String localId) {
        this.localId = localId;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public void setFirstSeenAt(Instant firstSeenAt) {
        this.firstSeenAt = firstSeenAt;
    }

    public Instant getLastEventAt() {
        return lastEventAt;
    }

    public void setLastEventAt(Instant lastEventAt) {
        this.lastEventAt = lastEventAt;
    }

    public String getLastEventId() {
        return lastEventId;
    }

    public void setLastEventId(String lastEventId) {
        this.lastEventId = lastEventId;
    }

    public String getLastEventType() {
        return lastEventType;
    }

    public void setLastEventType(String lastEventType) {
        this.lastEventType = lastEventType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DirectoryLink)) {
            return false;
        }
        return getId() != null && getId().equals(((DirectoryLink) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DirectoryLink{" +
            "id=" + getId() +
            ", source='" + getSource() + "'" +
            ", externalKey='" + getExternalKey() + "'" +
            ", externalId='" + getExternalId() + "'" +
            ", login='" + getLogin() + "'" +
            ", state='" + getState() + "'" +
            ", localId='" + getLocalId() + "'" +
            ", lastEventAt='" + getLastEventAt() + "'" +
            "}";
    }
}
