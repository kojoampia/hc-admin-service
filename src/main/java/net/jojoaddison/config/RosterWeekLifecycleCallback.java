package net.jojoaddison.config;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import net.jojoaddison.domain.RosterWeek;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Stamps {@code publishedAt} when a roster week is published, and clears it if one is withdrawn.
 *
 * <h2>Why the server owns this field</h2>
 *
 * <p>Decision 8 of {@code duty-roster-resolution.md} § 9.1. It joins {@code Message.readAt},
 * {@code Task.closedAt} and {@code PlatformService.lastProbedAt} — fields whose value is a
 * <em>consequence</em> of a state the client does set, and which can therefore be made to disagree
 * with it if both travel on the wire. Before this, the console sent {@code publishedAt: dayjs()}
 * beside {@code published: true} and was simply believed: a client with a wrong clock, or a
 * deliberate one, could date a publication to any moment it liked, and a week could be stored
 * published with no publication time at all.
 *
 * <p>Now {@code published} is the fact and this is derived from it. {@code RosterWeekResource}
 * refuses the value on the way in — see the notes there on {@code POST}, {@code PUT} and
 * {@code PATCH} — and this callback sits underneath every writer, including any that arrives later.
 *
 * <h2>The stored value has to be read back</h2>
 *
 * <p>The same trap the three fields above carry, arriving here by a different route. They are
 * rebuilt by MapStruct from a DTO with no field for the timestamp; {@code RosterWeek} has no DTO at
 * all — it is serialised directly — and the resource strips the incoming value instead, which puts
 * this callback in the same position on the {@code PUT} path: the entity saved is the one built from
 * the request body, so an edit to an already-published week arrives with {@code publishedAt} null.
 * Stamping "now" on finding it empty would walk the publication date forward on every such edit, so
 * a week published in May would report as published on whichever afternoon somebody last corrected
 * its label.
 *
 * <p><b>{@code PATCH} does not exercise this and it is worth knowing which does.</b> That handler
 * loads the stored document and mutates it, so the field is already populated by the time this runs
 * — the re-read is a no-op there. Removing the re-read therefore leaves the {@code PATCH} cases
 * green and fails only {@code RosterWeekPublishedAtIT}'s {@code PUT} ones, which is how that was
 * established: the mutation was run. A test suite covering only the console's current call (a
 * {@code PATCH}) would have been satisfied by a callback with this defect in it.
 *
 * <p>The lookup converts the id, and that is not defensive tidiness. Mongo stores a generated String
 * id as an {@code ObjectId} and a seeded one ({@code "week-2026-05-11"}) as a String; querying
 * {@code _id} with the wrong type matches nothing, and <b>nothing is indistinguishable from "never
 * stamped"</b> — which is the silent restamp again, one layer down.
 *
 * <h2>What it does not do</h2>
 *
 * <p>It does not backfill, and it does not overwrite a value that arrives non-null. That is what
 * lets the {@code dev}/{@code test} seed carry thirteen real publication dates: the initializer
 * writes straight to the repository, so its values are already there when this runs and are kept.
 * The wire is closed at the resource, not here, precisely so that the seed does not have to be.
 */
@Component
public class RosterWeekLifecycleCallback implements BeforeConvertCallback<RosterWeek> {

    private static final String PUBLISHED_AT = "published_at";

    private final Clock clock;
    private final MongoTemplate mongoTemplate;

    /** Lazy: this callback is published by the very template it reads from. */
    public RosterWeekLifecycleCallback(Clock clock, @Lazy MongoTemplate mongoTemplate) {
        this.clock = clock;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public RosterWeek onBeforeConvert(RosterWeek week, String collection) {
        if (!Boolean.TRUE.equals(week.getPublished())) {
            // Withdrawn, or never published. A stale timestamp beside published=false says the week
            // is out when it is not, and the grid's publish button reads that state back.
            week.setPublishedAt(null);
            return week;
        }
        if (week.getPublishedAt() == null) {
            week.setPublishedAt(storedPublishedAt(week.getId(), collection));
        }
        return week;
    }

    /**
     * The {@code published_at} already on the stored document, or now if there is none.
     *
     * <p>Read as a raw field rather than by loading the entity: this runs inside the conversion
     * callback, and mapping the document back to its class here re-enters machinery that is
     * mid-flight. Truncated to milliseconds, which is BSON's resolution — a nanosecond stamp leaves
     * the returned object disagreeing with the row just written.
     */
    private Instant storedPublishedAt(String id, String collection) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        if (id == null) {
            return now;
        }
        org.bson.Document stored = mongoTemplate
            .getCollection(collection)
            .find(new org.bson.Document("_id", storedIdFor(id)))
            .projection(new org.bson.Document(PUBLISHED_AT, 1))
            .first();
        Date storedPublishedAt = stored == null ? null : stored.getDate(PUBLISHED_AT);
        return storedPublishedAt == null ? now : storedPublishedAt.toInstant();
    }

    /** See {@link MessageLifecycleCallback}, which carries the same conversion for the same reason. */
    private static Object storedIdFor(String id) {
        return org.bson.types.ObjectId.isValid(id) ? new org.bson.types.ObjectId(id) : id;
    }
}
