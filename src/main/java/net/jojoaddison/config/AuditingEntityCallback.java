package net.jojoaddison.config;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.jojoaddison.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.Ordered;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Stamps {@code createdBy}/{@code createdDate}/{@code modifiedBy}/{@code modifiedDate} from the
 * authenticated principal, on every write, for every document that declares them.
 *
 * <h2>The fields were never missing — they were client-supplied</h2>
 *
 * <p>Fourteen domain classes declare these four fields, and until now whatever the caller sent was
 * stored verbatim. Any client could {@code PUT "modifiedBy": "somebody-else"} and have it persisted
 * as authoritative. An attribution trail the attributed party controls is worse than none, because
 * it reads as evidence.
 *
 * <h2>Why this took a JWT claim to do correctly</h2>
 *
 * <p>A first attempt stamped {@link SecurityUtils#getCurrentUserLogin()} and had to be reverted: it
 * wrote the wrong <em>kind</em> of identifier. These fields hold gateway user <b>ids</b> — the seed
 * data puts {@code a0eebc99-…-a11} in {@code createdBy}, and CLAUDE.md names those ids a
 * cross-service contract — while the JWT subject is a login. Writing logins here would have put two
 * identifier spaces in one column and broken every seeded reference, and this service cannot convert
 * between them: it runs with {@code skipUserManagement: true} and has no route to the gateway's user
 * collection.
 *
 * <p>The gateway now mints the id as a {@code uid} claim (see its {@code Account}), so the right
 * identifier arrives on the request and no lookup is needed.
 *
 * <h2>Behaviour</h2>
 *
 * <ul>
 *   <li>On insert, both {@code created*} and {@code modified*} are stamped.
 *   <li>On update, {@code created*} are re-read from the stored document and restored. Merely
 *       leaving the payload alone is not enough — {@code PUT} sends a whole document, so a caller
 *       who supplies a different {@code createdBy} would have it written straight through.
 *   <li>With no {@code uid} claim — seed data loading, a service-to-service call — the auditor is
 *       {@link Constants#SYSTEM}, never a guess.
 * </ul>
 */
@Component
public class AuditingEntityCallback implements BeforeConvertCallback<Object>, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(AuditingEntityCallback.class);

    private static final String CREATED_BY = "CreatedBy";
    private static final String CREATED_DATE = "CreatedDate";
    private static final String MODIFIED_BY = "ModifiedBy";
    private static final String MODIFIED_DATE = "ModifiedDate";

    /** Reflection lookups are cached per class: this runs on every write. */
    private final Map<Class<?>, Accessors> accessorCache = new ConcurrentHashMap<>();

    private final MongoTemplate mongoTemplate;

    /** Lazy: this callback is published by the very template it reads from. */
    public AuditingEntityCallback(@Lazy MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Object onBeforeConvert(Object entity, String collection) {
        Accessors accessors = accessorCache.computeIfAbsent(entity.getClass(), Accessors::of);
        if (accessors.isEmpty()) {
            return entity;
        }

        String auditor = SecurityUtils.getCurrentUserId().orElse(Constants.SYSTEM);
        // Truncated to milliseconds because that is BSON's date resolution. Stamping nanosecond
        // precision leaves the in-memory object disagreeing with the row that was just written, so
        // save() returns something that never equals what a subsequent read gives back.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        try {
            if (accessors.isNew(entity)) {
                accessors.set(CREATED_BY, entity, auditor);
                accessors.set(CREATED_DATE, entity, now);
            } else {
                restoreProvenance(entity, collection, accessors, auditor);
            }
            accessors.set(MODIFIED_BY, entity, auditor);
            accessors.set(MODIFIED_DATE, entity, now);
        } catch (ReflectiveOperationException e) {
            // Never fail the write over bookkeeping — but say so loudly, because a silent gap here
            // is exactly the state this class was written to end.
            LOG.error("could not stamp audit fields on {}", entity.getClass().getSimpleName(), e);
        }
        return entity;
    }

    /**
     * Reads {@code created_by} and {@code created_date} straight from the stored document. Raw field
     * names rather than a mapped entity load: this runs inside the conversion callback, so mapping
     * the document back to its class here would re-enter the machinery that is mid-flight.
     */
    private void restoreProvenance(Object entity, String collection, Accessors accessors, String auditor)
        throws ReflectiveOperationException {
        Object id = accessors.getId().invoke(entity);
        org.bson.Document stored = mongoTemplate
            .getCollection(collection)
            .find(new org.bson.Document("_id", storedIdFor(id)))
            .projection(new org.bson.Document("created_by", 1).append("created_date", 1))
            .first();

        if (stored == null) {
            // An id that is not in the collection: an upsert with a client-chosen id. Treat it as a
            // creation rather than leaving the caller's values in place.
            accessors.set(CREATED_BY, entity, auditor);
            accessors.set(CREATED_DATE, entity, Instant.now().truncatedTo(ChronoUnit.MILLIS));
            return;
        }

        accessors.set(CREATED_BY, entity, stored.getString("created_by"));
        Object createdDate = stored.get("created_date");
        if (createdDate instanceof java.util.Date date) {
            accessors.set(CREATED_DATE, entity, date.toInstant());
        } else if (createdDate instanceof Instant instant) {
            accessors.set(CREATED_DATE, entity, instant);
        }
    }

    /**
     * The value to match {@code _id} against.
     *
     * <p>Domain ids here are declared {@code String}, but Spring Data converts one that is a valid
     * 24-character ObjectId hex into a BSON {@link org.bson.types.ObjectId} on the way in. Querying
     * the raw collection with the {@code String} then matches nothing — which is not an error, it
     * just returns no document, and the caller concludes the entity is new. That is exactly how a
     * "restore the stored value" step turns into "stamp a fresh one" and the bug it was written to
     * fix survives with a passing test somewhere else.
     */
    private Object storedIdFor(Object id) {
        if (id instanceof String text && org.bson.types.ObjectId.isValid(text)) {
            return new org.bson.types.ObjectId(text);
        }
        return id;
    }

    /**
     * Ahead of Spring Data's own auditing callback, which would otherwise run first and be
     * overwritten anyway. Nothing depends on the ordering today; it is pinned so that adding an
     * entity that does extend AbstractAuditingEntity does not produce two different answers
     * depending on bean discovery order.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    /** The setters a given class actually has, resolved once. */
    private record Accessors(Method getId, Method createdBy, Method createdDate, Method modifiedBy, Method modifiedDate) {
        static Accessors of(Class<?> type) {
            return new Accessors(
                find(type, "getId"),
                find(type, "setCreatedBy", String.class),
                find(type, "setCreatedDate", Instant.class),
                find(type, "setModifiedBy", String.class),
                find(type, "setModifiedDate", Instant.class)
            );
        }

        private static Method find(Class<?> type, String name, Class<?>... parameters) {
            try {
                return type.getMethod(name, parameters);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        boolean isEmpty() {
            return createdBy == null && createdDate == null && modifiedBy == null && modifiedDate == null;
        }

        boolean isNew(Object entity) throws ReflectiveOperationException {
            return getId == null || getId.invoke(entity) == null;
        }

        void set(String which, Object entity, Object value) throws ReflectiveOperationException {
            Method setter =
                switch (which) {
                    case CREATED_BY -> createdBy;
                    case CREATED_DATE -> createdDate;
                    case MODIFIED_BY -> modifiedBy;
                    default -> modifiedDate;
                };
            if (setter != null) {
                setter.invoke(entity, value);
            }
        }
    }
}
