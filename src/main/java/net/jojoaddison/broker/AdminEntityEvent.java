package net.jojoaddison.broker;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * One entity of this service was written or removed, announced on {@code admin.event}.
 *
 * <p>Backlog item 112, which extends item 110's one-channel-per-product rule to hc-admin: each product
 * publishes <b>every</b> entity change on a single channel, and a new consumer subscribes rather than
 * negotiating a topic. hc-patient, hc-professional and hc-vendor each have a filed item to consume this
 * one — it is the channel carrying every return leg in the estate.
 *
 * <h2>⚠ This frame carries identifiers and metadata. It never carries what changed.</h2>
 *
 * <p>That is item 110's rule and it has two reasons, both of which apply here: identifying content must
 * not be on the wire, and a channel carrying entity contents rebuilds exactly the local mirrors item 107
 * is removing. So a save of a {@code Patient} announces <em>that patient a13 was saved, by admin, at
 * this instant</em> and nothing else — no name, no address, no plan, no before-and-after.
 *
 * <p><b>The one exception is stated as a rule so the next command-shaped event does not re-litigate
 * it</b>, and it was decided by the architect on 2026-09-18:
 *
 * <blockquote>A notification carries identifiers and metadata. A command carries the value it decides.</blockquote>
 *
 * <p>This class is the <b>notification</b> half and is therefore identifiers-only, with no room in its
 * shape for a value. The command half is {@link PlanVerificationEvent}, which carries the plan an
 * administrator verified because hc-patient cannot ask what it was — they have no route to this service,
 * measured 2026-09-17, so "publish the id and let them ask" is not available. <b>It still publishes to
 * {@code patient-events-plan} and this item does not move it</b>: the retirement half of item 112 goes
 * consumer-side first, when hc-patient's item 47 reads this channel and their lag on that topic reaches
 * zero. Do not fold the two together here.
 *
 * <h2>The shape was taken from the siblings' built producers, not from the backlog entry</h2>
 *
 * <p>Read on 2026-09-18, because "read the producer, not the specification" is the rule this estate
 * paid for twice — hc-professional's phase-2 reader was written from item 47's prose, agreed with its
 * own twelve tests, and refused every real frame while the binding bound, the group committed and lag
 * stayed at zero. The other three channels all exist already:
 *
 * <ul>
 *   <li><b>{@code hc-vendor/api .../broker/VendorEvent.java}</b> — {@code subject} is
 *       {@code (entityType, entityId)}, the record that changed; {@code data} is
 *       {@code (actorAccountId)}; {@code entityType} is the domain class's <em>simple name</em>,
 *       explicitly not a storage or package name, so a consumer is not coupled to their layout.</li>
 *   <li><b>{@code hc-professional/api .../service/EntityChangeAnnouncer.java}</b> — the same
 *       {@code AbstractMongoEventListener} mechanism as this one, arrived at independently, publishing
 *       {@code type.getSimpleName()}, a reflective id, and
 *       {@code SecurityUtils.getCurrentAccountId().orElse(null)}.</li>
 *   <li><b>{@code hc-patient/api .../service/event/EntityEvent.java}</b> — same seven-component
 *       envelope, but {@code subject} is {@code (accountId)}, the <em>actor</em>, and
 *       {@code entityType}/{@code entityId}/{@code action} are in {@code data}.</li>
 * </ul>
 *
 * <p>⚠ <b>So hc-patient disagrees with the other two about what {@code subject} means, under the same
 * {@code type} string.</b> A consumer reading two of these channels through one code path — which is
 * the whole point of one channel per product — finds {@code subject.accountId} on one and
 * {@code subject.entityType} on the other. That divergence is recorded in this item's report rather
 * than repaired here, because it is another product's code.
 *
 * <p><b>This class follows hc-vendor and hc-professional</b>: the subject of an entity-change frame is
 * the entity, their {@code Subject} is then identical to this one, and keying the partition on the
 * record rather than the actor is what makes two changes to one row arrive in order.
 *
 * <h2>What the fields mean, because a consumer will be written against them and not against this</h2>
 *
 * <p>{@code EntityChangeAnnouncer} is the producer, and it is a Mongo event listener rather than
 * anything a resource calls — the same mechanism that already writes the audit trail, so the stream and
 * {@code AuditLog} agree by construction rather than by two people remembering.
 *
 * <ul>
 *   <li><b>{@code subject.entityType} is the domain class's simple name</b> — {@code Patient},
 *       {@code ServicePlan}, {@code WageRate} — matching hc-vendor's rule that a consumer must not be
 *       coupled to this product's storage layout. It is <em>not</em> the Mongo collection name, which
 *       an earlier draft of this class used: {@code service_plan} names a collection this service is
 *       free to rename, and {@code AuditLog.metadata} recording it is an argument about a local column,
 *       not about four products' wire.</li>
 *   <li><b>{@code data.action} is {@link #SAVED} or {@link #DELETED}</b>, where hc-patient and
 *       hc-professional both split {@code CREATED} from {@code UPDATED}. See {@link #SAVED}: the split
 *       is <em>achievable</em> on this stack and was declined on evidence, rather than being impossible
 *       as an earlier draft of this javadoc asserted. {@code DELETED} is spelled to match theirs
 *       exactly. The local {@code AuditLog.actionType} keeps its own {@code SAVE}/{@code DELETE}
 *       spelling, because a column this service owns and a four-product wire answer to different
 *       conventions.</li>
 *   <li><b>{@code data.actorAccountId} is the gateway {@code User.id}, never a login</b>, taken from
 *       the {@code uid} claim via {@code SecurityUtils.getCurrentUserId()}. <b>An earlier draft of this
 *       class put the login here and that was a breach of this product's own rule</b>: item 43
 *       established that the login space is small and enumerable, so a login is identifying content,
 *       and hc-vendor cites that item by number when ruling one out. It is the estate's join key under
 *       item 107 D1, which is the second reason. All three siblings put an account id here.</li>
 *   <li><b>{@code data.actorAccountId} is absent when unknown, and never filled in with something
 *       else.</b> There are real writes with no authenticated caller — the {@code dev}/{@code test}
 *       seeder runs as an {@code ApplicationRunner} at boot — and real tokens carrying no such claim,
 *       because a sibling gateway sharing this estate's signing key spells it differently. An absent
 *       actor says "this service does not know"; a {@code system} placeholder would say "this service
 *       knows, and the answer is a name".</li>
 * </ul>
 *
 * <h2>It holds no domain types, and that is enforced</h2>
 *
 * <p>{@code TechnicalStructureTest} does not list {@code ..broker..} as a layer, so anything here
 * reaching into {@code ..domain..} fails the ArchUnit rule. That is why the conversion from a Mongo
 * event lives in {@code EntityChangeAnnouncer}, under {@code ..config..}, and not in a factory here —
 * the same reason {@link VerificationEvent} and {@link PlanVerificationEvent} both give.
 */
public class AdminEntityEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The envelope version, and <b>ours</b> — unlike {@link PlanVerificationEvent#VERSION}, which is a
     * fact about hc-patient's schema because that frame rides their topic. {@code admin.event} is this
     * product's channel, so this number moves when this shape does.
     */
    public static final int VERSION = 1;

    /** What this service calls itself, matching {@link PlanVerificationEvent#SOURCE}. */
    public static final String SOURCE = "hcAdminService";

    /**
     * The event type string, and the one a consumer dispatches on.
     *
     * <p>Deliberately one type for every collection rather than a type per entity. A consumer filters
     * on {@code subject.entityType}, which is data; a type per entity would make every new entity in
     * this service a new string in three other repositories, which is the negotiation item 110's one
     * channel exists to end.
     *
     * <p>It is a sibling of the command types that will share this channel — {@code PlanVerified} today
     * on {@code patient-events-plan} — so a reader dispatching on {@code type} can tell a notification
     * from a decision without looking at anything else.
     */
    public static final String TYPE = "EntityChanged";

    /**
     * A document was written — created or updated, and this channel deliberately does not say which.
     *
     * <h2>⚠ Not because it cannot be done. Because here it would usually be wrong.</h2>
     *
     * <p>{@code AfterSaveEvent} carries no create-versus-update distinction, which is where the easy
     * answer stops — and it is the wrong answer, because <b>hc-professional's
     * {@code EntityChangeAnnouncer} does make the split on this same Spring Data MongoDB stack</b>: a
     * {@code BeforeConvertEvent} notes entities arriving with a null id in a {@code ThreadLocal}, and
     * the save reads the note back. So "Mongo cannot" is false, and this javadoc said it until the
     * sibling's code was read.
     *
     * <p><b>What their own {@code EntityChangeAction} records is the reason not to copy it</b>, in their
     * words: an entity <i>"already carrying one is reported {@code UPDATED}. Nothing in the save path
     * distinguishes that."</i> The test is "did this arrive with an id", not "did a row exist".
     *
     * <p>In hc-professional that is an edge. <b>In this service it is the common case.</b> Records here
     * carry explicit client-assigned ids almost everywhere — {@code DevelopmentDataInitializer} writes
     * the whole {@code test} fixture with ids in the JSON, and the seeded account ids
     * ({@code a0eebc99-…-a11}) are a documented cross-service contract. Every one of those inserts would
     * be announced {@code CREATED}-as-{@code UPDATED}. Two values that are frequently wrong are worse
     * than one value that is always right, and a consumer counting creations from this channel would be
     * quietly counting almost none of them.
     *
     * <p>So {@code SAVED} spans their {@code CREATED} and {@code UPDATED}. A consumer needing the
     * difference must read the record. <b>If the split is ever wanted here it needs a real insert
     * signal</b> — not the id heuristic — and that is a decision with a follow-up behind it, not an
     * omission.
     */
    public static final String SAVED = "SAVED";

    /** A document was removed. Spelled to match hc-patient's {@code EntityChangeAction.DELETED}. */
    public static final String DELETED = "DELETED";

    private String eventId;
    private String type;
    private int version;
    private String occurredAt;
    private String source;
    private Subject subject;
    private ChangeData data;

    public AdminEntityEvent() {}

    public AdminEntityEvent(String eventId, Instant occurredAt, String entityType, String entityId, String action, String actorAccountId) {
        this.eventId = eventId;
        this.type = TYPE;
        this.version = VERSION;
        this.occurredAt = occurredAt == null ? null : occurredAt.toString();
        this.source = SOURCE;
        this.subject = new Subject(entityType, entityId);
        this.data = new ChangeData(action, actorAccountId);
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
     * When the change happened, as an ISO-8601 instant — {@code 2026-09-18T09:15:30.500Z}.
     *
     * <h2>⚠ A {@code String} on purpose, and making it an {@code Instant} is a silent break</h2>
     *
     * <p>This is {@link PlanVerificationEvent#getOccurredAt()}'s finding and it is live on this stack
     * rather than defensive. A typed {@code Instant} is written by whatever {@code ObjectMapper} is
     * injected, and here that is {@code WebConfigurer.objectMapper()}:
     *
     * <pre>{@code return new ObjectMapper().registerModule(new JavaTimeModule());}</pre>
     *
     * <p><b>Bare.</b> An explicit {@code @Bean}, so Spring Boot's builder — which would have disabled
     * {@code WRITE_DATES_AS_TIMESTAMPS} — backs off and never runs, the feature is on, and a typed
     * {@code Instant} emits {@code 1.7890317305E9}. That binds back to an {@code Instant} at the far end
     * without complaint, so the casualty is not delivery: it is every timestamp a consumer stores or
     * shows, in scientific notation, for ever. Pinning the format in the field removes the dependency on
     * that bean altogether.
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

    public ChangeData getData() {
        return data;
    }

    public void setData(ChangeData data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return (
            "AdminEntityEvent{eventId='" +
            eventId +
            "', type='" +
            type +
            "', version=" +
            version +
            ", occurredAt='" +
            occurredAt +
            "', source='" +
            source +
            "', entityType='" +
            (subject == null ? null : subject.entityType()) +
            "', entityId='" +
            (subject == null ? null : subject.entityId()) +
            "', action='" +
            (data == null ? null : data.action()) +
            "'}"
        );
    }

    /**
     * Which record changed — the whole of what this frame identifies.
     *
     * <p><b>Two fields, and adding a third is how this becomes a mirror.</b> A name, an address or a
     * status here would be the changed content arriving under a heading that reads like an identifier,
     * which is the shape item 110's rule is written to refuse. {@code AdminEntityEventIT} asserts the
     * field set rather than the individual fields, so a third one fails rather than passes quietly.
     *
     * @param entityType the domain class's simple name — {@code Patient}, {@code ServicePlan}. Never the
     *                   fully-qualified name and never the collection: a repackaging or a collection
     *                   rename here must not read as a new entity type in three other products.
     * @param entityId the document id, or {@code null} for a delete that matched on something other than
     *                 {@code _id}, where there is nothing left to report after the fact
     */
    public record Subject(String entityType, String entityId) implements Serializable {}

    /**
     * What happened, and who did it. Metadata about the change, never the change.
     *
     * @param action {@link #SAVED} or {@link #DELETED}
     * @param actorAccountId the gateway {@code User.id} from the {@code uid} claim, or {@code null} when
     *                       there is no authenticated caller or the token carries no such claim. Never a
     *                       login — see the class javadoc.
     */
    public record ChangeData(String action, String actorAccountId) implements Serializable {}
}
