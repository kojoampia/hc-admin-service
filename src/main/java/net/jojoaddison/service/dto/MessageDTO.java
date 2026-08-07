package net.jojoaddison.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.MessageChannel;
import net.jojoaddison.domain.enumeration.MessageStatus;
import net.jojoaddison.domain.enumeration.Priority;

/**
 * A DTO for the {@link net.jojoaddison.domain.Message} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class MessageDTO implements Serializable {

    private String id;

    @NotNull
    private Instant sentAt;

    @NotNull
    @Size(max = 120)
    private String fromAddress;

    @NotNull
    @Size(max = 80)
    private String senderName;

    @NotNull
    @Size(max = 160)
    private String subject;

    private String body;

    @NotNull
    private MessageChannel channel;

    @NotNull
    private MessageStatus status;

    @NotNull
    private Priority priority;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public MessageChannel getChannel() {
        return channel;
    }

    public void setChannel(MessageChannel channel) {
        this.channel = channel;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MessageDTO)) {
            return false;
        }

        MessageDTO messageDTO = (MessageDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, messageDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "MessageDTO{" +
            "id='" + getId() + "'" +
            ", sentAt='" + getSentAt() + "'" +
            ", fromAddress='" + getFromAddress() + "'" +
            ", senderName='" + getSenderName() + "'" +
            ", subject='" + getSubject() + "'" +
            ", body='" + getBody() + "'" +
            ", channel='" + getChannel() + "'" +
            ", status='" + getStatus() + "'" +
            ", priority='" + getPriority() + "'" +
            "}";
    }
}
