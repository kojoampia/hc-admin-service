package net.jojoaddison.config;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.enumeration.MessageStatus;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.stereotype.Component;

/**
 * Stamps {@code readAt} when a message stops being unread, and clears it if one goes back.
 *
 * <h2>Why the server owns this field</h2>
 *
 * <p>The desk marks a message read by changing its {@code status}. If {@code readAt} were settable
 * by the client too, the two could disagree — READ with no read time, or unread with one — and the
 * dashboard's backlog history would be reconstructed from whichever the caller happened to send.
 * Derived here, they cannot drift: the status is the fact, the timestamp is a consequence of it.
 * {@code MessageDTO} therefore does not carry it, and nothing on the wire can set it.
 *
 * <h2>The stored value has to be read back</h2>
 *
 * <p>This looks like it could be stateless — "non-NEW and unstamped gets stamped now" — and that is
 * wrong in a way that only shows up later. Updates go through {@code MessageMapper.toEntity}, which
 * builds a fresh entity from the DTO, so {@code readAt} arrives null on <em>every</em> update of an
 * already-read message. Stamping "now" there would move the read time forward on each save, and the
 * backlog series would quietly report messages as having been read on the day somebody last edited
 * them. So an update reads the stored value first and keeps it — the same reason
 * {@link AuditingEntityCallback} re-reads {@code created_*} rather than trusting the payload.
 *
 * <h2>What it does not do</h2>
 *
 * <p>It does not backfill. A message that was already READ before this field existed keeps a null
 * {@code readAt}, so the backlog series counts it as never having left — wrong, but honestly wrong
 * rather than invented. The seed carries real values for exactly that reason, and production holds
 * no messages yet.
 */
@Component
public class MessageLifecycleCallback implements BeforeConvertCallback<Message> {

    private static final String READ_AT = "read_at";

    private final Clock clock;
    private final MongoTemplate mongoTemplate;

    /** Lazy: this callback is published by the very template it reads from. */
    public MessageLifecycleCallback(Clock clock, @Lazy MongoTemplate mongoTemplate) {
        this.clock = clock;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Message onBeforeConvert(Message message, String collection) {
        if (message.getStatus() == null) {
            return message;
        }
        if (message.getStatus() == MessageStatus.NEW) {
            // Unread again has no read time. A stale one would put the message back in the backlog
            // carrying a date that says it had left.
            message.setReadAt(null);
            return message;
        }
        if (message.getReadAt() == null) {
            message.setReadAt(storedReadAt(message.getId(), collection));
        }
        return message;
    }

    /**
     * The {@code read_at} already on the stored document, or now if there is none.
     *
     * <p>Read as a raw field rather than by loading the entity: this runs inside the conversion
     * callback, and mapping the document back to its class here re-enters machinery that is
     * mid-flight. Truncated to milliseconds, which is BSON's resolution — a nanosecond stamp leaves
     * the returned object disagreeing with the row just written.
     */
    private Instant storedReadAt(String id, String collection) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        if (id == null) {
            return now;
        }
        org.bson.Document stored = mongoTemplate
            .getCollection(collection)
            .find(new org.bson.Document("_id", storedIdFor(id)))
            .projection(new org.bson.Document(READ_AT, 1))
            .first();
        Date storedReadAt = stored == null ? null : stored.getDate(READ_AT);
        return storedReadAt == null ? now : storedReadAt.toInstant();
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
