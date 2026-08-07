package net.jojoaddison.domain;

import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.MessageChannel;
import net.jojoaddison.domain.enumeration.MessageStatus;
import net.jojoaddison.domain.enumeration.Priority;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A Message.
 */
@Document(collection = "message")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Message implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Field("sent_at")
    private Instant sentAt;

    @NotNull
    @Size(max = 120)
    @Field("from_address")
    private String fromAddress;

    @NotNull
    @Size(max = 80)
    @Field("sender_name")
    private String senderName;

    @NotNull
    @Size(max = 160)
    @Field("subject")
    private String subject;

    @Field("body")
    private String body;

    @NotNull
    @Field("channel")
    private MessageChannel channel;

    @NotNull
    @Field("status")
    private MessageStatus status;

    @NotNull
    @Field("priority")
    private Priority priority;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Message id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getSentAt() {
        return this.sentAt;
    }

    public Message sentAt(Instant sentAt) {
        this.setSentAt(sentAt);
        return this;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public String getFromAddress() {
        return this.fromAddress;
    }

    public Message fromAddress(String fromAddress) {
        this.setFromAddress(fromAddress);
        return this;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public String getSenderName() {
        return this.senderName;
    }

    public Message senderName(String senderName) {
        this.setSenderName(senderName);
        return this;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getSubject() {
        return this.subject;
    }

    public Message subject(String subject) {
        this.setSubject(subject);
        return this;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return this.body;
    }

    public Message body(String body) {
        this.setBody(body);
        return this;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public MessageChannel getChannel() {
        return this.channel;
    }

    public Message channel(MessageChannel channel) {
        this.setChannel(channel);
        return this;
    }

    public void setChannel(MessageChannel channel) {
        this.channel = channel;
    }

    public MessageStatus getStatus() {
        return this.status;
    }

    public Message status(MessageStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public Priority getPriority() {
        return this.priority;
    }

    public Message priority(Priority priority) {
        this.setPriority(priority);
        return this;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Message)) {
            return false;
        }
        return getId() != null && getId().equals(((Message) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Message{" +
            "id=" + getId() +
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
