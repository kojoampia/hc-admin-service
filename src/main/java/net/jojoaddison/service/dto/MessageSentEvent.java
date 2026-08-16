package net.jojoaddison.service.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * What goes on the wire when a message is sent.
 *
 * <p><b>Metadata only, and deliberately so.</b> The body never leaves the database on this path: a
 * Kafka topic is retained, replicated and readable by every service on the bus, and a message on
 * this desk can carry a patient's clinical question. The recipient is told that something arrived
 * and what it is about; opening the notification fetches the message itself, authenticated, from
 * the service that owns it.
 *
 * <p>{@code toAddress} is the routing key. The consumer delivers to that principal's connections
 * when it names somebody connected here, and broadcasts otherwise — see {@code KafkaConsumer}.
 */
public record MessageSentEvent(
    String eventType,
    String id,
    String subject,
    String fromAddress,
    String senderName,
    String toAddress,
    String recipientName,
    Instant sentAt,
    String channel,
    String priority,
    String parentId
)
    implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The only value {@link #eventType} ever takes. Named on the event so a consumer can switch. */
    public static final String TYPE = "messageSentEvent";

    public static MessageSentEvent of(MessageDTO message) {
        return new MessageSentEvent(
            TYPE,
            message.getId(),
            message.getSubject(),
            message.getFromAddress(),
            message.getSenderName(),
            message.getToAddress(),
            message.getRecipientName(),
            message.getSentAt(),
            message.getChannel() == null ? null : message.getChannel().name(),
            message.getPriority() == null ? null : message.getPriority().name(),
            message.getParentId()
        );
    }
}
