package net.jojoaddison.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import net.jojoaddison.broker.AdminChannel;
import net.jojoaddison.broker.AdminEntityEvent;
import net.jojoaddison.broker.OutboundEventPublisher;
import net.jojoaddison.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.stereotype.Component;

/**
 * Announces every domain mutation on {@code admin.event} — backlog item 112.
 *
 * <p>Item 110 gave each product one channel carrying all of its entity CRUD. hc-admin is the fourth,
 * and this class is its producer half. hc-patient, hc-professional and hc-vendor each have a filed item
 * to consume it, so the shape of {@link AdminEntityEvent} is a contract with three repositories that
 * import nothing from this one.
 *
 * <h2>Why this is a Mongo event listener and not something the resources call</h2>
 *
 * <p>Because a channel that carries "all entity CRUD" cannot be maintained by hand. There are forty-odd
 * generated resources here and more arrive with each regeneration; a publish added per resource is a
 * publish somebody forgets, and the forgetting is silent — the record is written, the response is 201,
 * and a sibling product simply never hears about that one collection. This is the same argument
 * {@code PaginationIT} makes about deriving its paths from the handler mapping rather than listing them,
 * and the same mechanism {@link AuditLogCallback} already uses to catch every save and delete across
 * every collection. The two now agree by construction: one change produces one audit row and one frame,
 * from the same event, so neither can describe a write the other missed.
 *
 * <p><b>They do not agree on spelling, and that is deliberate.</b> {@code AuditLog} records the
 * collection ({@code service_plan}) and {@code SAVE}/{@code DELETE}; the frame carries the domain class
 * ({@code ServicePlan}) and {@link AdminEntityEvent#SAVED}/{@link AdminEntityEvent#DELETED}. The column
 * is this service's own and the frame is a four-product contract, so they answer to different
 * conventions — see {@link AdminEntityEvent}, which takes both from the siblings' built producers.
 *
 * <p>It is a second listener rather than three more lines inside {@link AuditLogCallback}, matching the
 * one-callback-per-concern shape the rest of this package already has
 * ({@link AuditingEntityCallback}, {@link MessageLifecycleCallback}, {@link TaskLifecycleCallback},
 * {@link RosterWeekLifecycleCallback}). The practical half of that is failure isolation: an audit write
 * that cannot reach Mongo must not also cost the announcement, and an announcement that cannot be
 * serialised must not cost the audit row.
 *
 * <h2>What it inherits from that mechanism, stated so it is not read as a defect</h2>
 *
 * <ul>
 *   <li><b>{@code updateFirst}, {@code findAndModify} and bulk operations publish nothing</b>, because
 *       they fire no save event. The audit trail has the same blind spot and always has; this class does
 *       not widen or narrow it.</li>
 *   <li><b>A delete reports the id its query matched</b>, not the document it removed — after the fact
 *       there is nothing else left to report. A delete on a criterion other than {@code _id} therefore
 *       announces a null id, which is honest rather than wrong.</li>
 *   <li><b>{@link AdminEntityEvent#SAVED} covers create and update alike.</b> Mongo's save is an
 *       upsert and the event carries no such distinction, which is why this channel cannot offer
 *       hc-patient's {@code CREATED}/{@code UPDATED} split. See {@link AdminEntityEvent}.</li>
 *   <li><b>{@code data.actorAccountId} is absent for a write with no authenticated caller</b> — the
 *       seeder and the inbound directory consumers are both such writers. It is never replaced by a
 *       placeholder; {@link AdminEntityEvent} argues why. Absent as in <b>omitted from the frame</b>,
 *       not present-with-null — the estate rule since item 129 (2026-09-24), implemented by the
 *       {@code @JsonInclude} on {@link AdminEntityEvent.ChangeData}.</li>
 *   <li><b>The {@code dev} and {@code test} seed announces every row it writes.</b>
 *       {@code DevelopmentDataInitializer} calls {@code saveAll} on every start, so a stack running
 *       those profiles republishes its whole fixture on each boot. That is the honest reading of "every
 *       entity CRUD" — the seed really does write those entities — and it costs nothing in production,
 *       where neither profile is active. It is recorded here rather than suppressed because suppressing
 *       it means a rule about <em>which</em> writes count, and no such rule was decided.</li>
 * </ul>
 *
 * <h2>The one exclusion, and it is the same one the audit trail has</h2>
 *
 * <p>{@code audit_log} announces nothing. Every change already writes a row there, so announcing that
 * row would double every frame on the channel and pair each real one with a frame about bookkeeping —
 * and a consumer cannot tell the two apart without knowing this service's internals, which is precisely
 * what a cross-product channel must not require.
 *
 * <h2>Publishing never touches the caller's thread</h2>
 *
 * <p>Through {@link OutboundEventPublisher}, which is the only class in this service allowed to hold a
 * {@code StreamBridge} — {@code OutboundPublishingArchTest} enforces that, and the reason is a
 * sixty-second wait against an unreachable broker (backlog item 39a). It matters more here than
 * anywhere else in the service: this listener runs inside <em>every</em> write, so an inline publish
 * would put that wait in front of every save in the application rather than in front of one endpoint.
 */
@Component
public class EntityChangeAnnouncer extends AbstractMongoEventListener<Object> {

    /** The channel binding as {@code config/application.yml} declares it. Bound to {@code admin.event}. */
    static final String ADMIN_EVENT_BINDING = "admin-event-out-0";

    private static final Logger LOG = LoggerFactory.getLogger(EntityChangeAnnouncer.class);

    private static final String AUDIT_LOG_COLLECTION = "audit_log";

    private final OutboundEventPublisher eventPublisher;

    private final ObjectMapper objectMapper;

    /** Injected rather than read as "now" so a test can announce at a known instant, as the lifecycle callbacks do. */
    private final Clock clock;

    public EntityChangeAnnouncer(OutboundEventPublisher eventPublisher, ObjectMapper objectMapper, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void onAfterSave(AfterSaveEvent<Object> event) {
        Object entity = event.getSource();
        Class<?> type = entity == null ? null : entity.getClass();
        announce(event.getCollectionName(), type, AdminEntityEvent.SAVED, idOf(entity));
    }

    @Override
    public void onAfterDelete(AfterDeleteEvent<Object> event) {
        Object id = event.getDocument() == null ? null : event.getDocument().get("_id");
        announce(event.getCollectionName(), event.getType(), AdminEntityEvent.DELETED, id == null ? null : id.toString());
    }

    /**
     * Builds one frame and queues it. Never throws: this runs inside somebody else's write, and a
     * broker — or a serialiser — must not be able to fail an operation that has already succeeded.
     */
    private void announce(String collection, Class<?> type, String action, String entityId) {
        if (AUDIT_LOG_COLLECTION.equals(collection)) {
            return;
        }

        try {
            String entityType = entityTypeOf(type, collection);
            AdminEntityEvent event = new AdminEntityEvent(
                UUID.randomUUID().toString(),
                Instant.now(clock).truncatedTo(ChronoUnit.MILLIS),
                entityType,
                entityId,
                action,
                // Absent when unknown, and never replaced with a login or a placeholder. See
                // AdminEntityEvent's javadoc: item 43 makes a login identifying content, and a
                // `system` stand-in would claim this service knows something it does not.
                //
                // NOTE this deliberately declines getCurrentUserId's own advice to "fall back to
                // Constants.SYSTEM". That guidance is written for AuditingEntityCallback, where the
                // value lands in a local `createdBy` column that must not be empty. Here it goes on a
                // four-product wire, where an absent field and a fabricated one read very differently
                // — and hc-vendor's VendorEvent.Data states the same rule for their half of it.
                SecurityUtils.getCurrentUserId().orElse(null)
            );

            eventPublisher.publish(
                ADMIN_EVENT_BINDING,
                objectMapper.writeValueAsString(event),
                action + " on " + entityType + (entityId == null ? "" : " [" + entityId + "]"),
                partitionKey(entityType, entityId),
                // No redundant string header. `patientKey` is hc-patient's convention for their own
                // topic and would be a lie on a frame about a wage rate; the Kafka key is the whole of
                // the ordering guarantee and the subject travels in the payload where a consumer reads it.
                null
            );
        } catch (JsonProcessingException | RuntimeException e) {
            // Deliberately not rethrown and deliberately loud. The change is already committed, so
            // there is nothing to unwind — but a channel three products subscribe to going quiet is
            // exactly the failure this estate keeps discovering months late.
            LOG.error("could not announce {} on {} to {}", action, collection, ADMIN_EVENT_BINDING, e);
        }
    }

    /**
     * The domain class's simple name, which is what a consumer keys on.
     *
     * <p>Not the collection name, and the difference is a contract rather than a preference: hc-vendor's
     * {@code VendorEvent.Subject} states the rule for this channel family — a consumer must not be
     * coupled to the producing product's storage layout, so a collection rename here must not read as a
     * new entity type in three other products.
     *
     * <p><b>The fallback is reachable only through a raw collection-name delete</b> —
     * {@code mongoTemplate.remove(query, "some_collection")}, which passes no type and which this
     * service does not do anywhere today (checked, not assumed). Every repository delete goes through
     * {@code SimpleMongoRepository}, which supplies the entity class. When it is ever reached, the
     * collection name is the only thing left to say, and saying it is better than announcing a null
     * entity type that no consumer can route.
     */
    private String entityTypeOf(Class<?> type, String collection) {
        return type == null || type == Object.class ? collection : type.getSimpleName();
    }

    /**
     * The partition key: the entity, not the collection and not the id alone.
     *
     * <p><b>Two changes to one record must not overtake each other</b>, and without a key Kafka's
     * partitioner scatters a producer's frames across partitions, after which the order they are
     * consumed in is not the order they were produced in. A delete arriving before the save it follows
     * leaves a consumer holding a record this service has removed — and the failure is invisible from
     * here, because the producer is healthy, the group is not lagging and nothing is dead-lettered.
     *
     * <p>Qualified by the entity type because ids here are not globally unique: the seed uses short
     * literals like {@code a13}, so keying on the id alone would put unrelated records from different
     * collections on one partition and, worse, make that look deliberate.
     *
     * <p>hc-vendor's {@code VendorEventPublisher} keys the same pair for the same stated reason, which
     * is convergence rather than copying — neither producer was written from the other.
     *
     * <p>Choosing this <em>later</em> is not free — changing a topic's partitioning changes the
     * ordering guarantee for frames already in flight — which is why it is decided now, while the
     * channel has no consumer reading it in anger.
     */
    private String partitionKey(String entityType, String entityId) {
        // Delegated since item 145: this class stopped being the channel's only publisher, and three
        // copies of "the same" key is three places for them to stop being the same — invisibly, because
        // every producer stays healthy while ordering quietly stops holding. The reasoning moved with it.
        return AdminChannel.partitionKey(entityType, entityId);
    }

    /**
     * The entity id, and nothing else off the entity.
     *
     * <p>Reflective for the reason {@code AuditLogCallback.idOf} is: the listener is declared over
     * {@code Object} so that it catches every collection, which is the whole point, and there is no
     * common supertype to call {@code getId()} through. Anything without one announces a null id rather
     * than failing a write.
     */
    private String idOf(Object entity) {
        if (entity == null) {
            return null;
        }
        try {
            Method getId = entity.getClass().getMethod("getId");
            Object id = getId.invoke(entity);
            return id == null ? null : id.toString();
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
