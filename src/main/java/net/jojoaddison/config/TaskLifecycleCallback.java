package net.jojoaddison.config;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import net.jojoaddison.domain.Task;
import net.jojoaddison.domain.enumeration.TaskState;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Stamps {@code closedAt} when a task reaches {@code DONE}, and clears it when one is re-opened.
 *
 * <p>The same shape and the same reasoning as {@link MessageLifecycleCallback}, over the other
 * backlog the dashboard counts — including reading the stored value back on an update, because the
 * board's dialog saves through the mapper and a fresh entity carries no closing time.
 *
 * <p>The board moves cards with a segmented control, so a task can reach DONE and come back.
 * Clearing on the way out matters as much as stamping on the way in: a re-opened task holding its
 * old closing time would be counted as closed in every past month it was actually open.
 */
@Component
public class TaskLifecycleCallback implements BeforeConvertCallback<Task> {

    private static final String CLOSED_AT = "closed_at";

    private final Clock clock;
    private final MongoTemplate mongoTemplate;

    /** Lazy: this callback is published by the very template it reads from. */
    public TaskLifecycleCallback(Clock clock, @Lazy MongoTemplate mongoTemplate) {
        this.clock = clock;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Task onBeforeConvert(Task task, String collection) {
        if (task.getState() != TaskState.DONE) {
            task.setClosedAt(null);
            return task;
        }
        if (task.getClosedAt() == null) {
            task.setClosedAt(storedClosedAt(task.getId(), collection));
        }
        return task;
    }

    /** The {@code closed_at} already stored, or now. See {@code MessageLifecycleCallback}. */
    private Instant storedClosedAt(String id, String collection) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        if (id == null) {
            return now;
        }
        org.bson.Document stored = mongoTemplate
            .getCollection(collection)
            .find(new org.bson.Document("_id", storedIdFor(id)))
            .projection(new org.bson.Document(CLOSED_AT, 1))
            .first();
        Date storedClosedAt = stored == null ? null : stored.getDate(CLOSED_AT);
        return storedClosedAt == null ? now : storedClosedAt.toInstant();
    }

    /**
     * Mongo stores a generated String id as an {@code ObjectId}, and a seeded one ({@code "m1"}) as
     * a String. Querying {@code _id} with the wrong type matches nothing — and "nothing" here is
     * indistinguishable from "no stamp yet", so the field would be silently restamped on every
     * update. {@code AuditingEntityCallback} carries the same conversion for the same reason.
     */
    private static Object storedIdFor(String id) {
        return org.bson.types.ObjectId.isValid(id) ? new org.bson.types.ObjectId(id) : id;
    }
}
