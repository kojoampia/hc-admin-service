package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.MessageChannel;
import net.jojoaddison.domain.enumeration.MessageStatus;
import net.jojoaddison.domain.enumeration.Priority;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
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

    /**
     * Where an outbound message went. Null on everything that arrived at the desk.
     *
     * <p>Every field above assumes a message arriving: `fromAddress` and `senderName` describe the
     * patient, professional or vendor who wrote in. Composing one reverses that, and rather than
     * overload the same two fields with a direction flag, the outbound pair sits beside the inbound
     * one. A message is outbound exactly when this is set — no separate enum to keep consistent
     * with the addresses it would be describing.
     */
    @Size(max = 120)
    @Field("to_address")
    private String toAddress;

    @Size(max = 80)
    @Field("recipient_name")
    private String recipientName;

    /**
     * The message this one answers, for a reply.
     *
     * <p>A reply is its own message rather than an edit of the one it answers: it has its own body,
     * its own time and its own author, and the desk showed none of that while "Send reply" merely
     * flipped a status and discarded the text. Null for anything that starts a conversation.
     */
    @DBRef
    @Field("parent")
    @JsonIgnoreProperties(value = { "parent", "vendor", "patient", "professional" }, allowSetters = true)
    private Message parent;

    /**
     * Who it went to, when that is somebody this service knows.
     *
     * <p>Optional and mutually exclusive in practice: {@code toAddress} is what delivery and the
     * Kafka event use, and these exist so the desk can link a message back to the record it concerns.
     * An address with no record behind it stays perfectly representable, which is the reason the
     * address is the authoritative field rather than the reference.
     */
    @DBRef
    @Field("vendor")
    @JsonIgnoreProperties(value = { "documents", "facilities" }, allowSetters = true)
    private Vendor vendor;

    @DBRef
    @Field("patient")
    @JsonIgnoreProperties(value = { "profile", "angel", "plan", "clinicalLead", "hub", "documents", "careActivities" }, allowSetters = true)
    private Patient patient;

    @DBRef
    @Field("professional")
    @JsonIgnoreProperties(value = { "profile", "team", "hub", "assignments" }, allowSetters = true)
    private Professional professional;

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

    public String getToAddress() {
        return this.toAddress;
    }

    public Message toAddress(String toAddress) {
        this.setToAddress(toAddress);
        return this;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }

    public String getRecipientName() {
        return this.recipientName;
    }

    public Message recipientName(String recipientName) {
        this.setRecipientName(recipientName);
        return this;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public Message getParent() {
        return this.parent;
    }

    public void setParent(Message parent) {
        this.parent = parent;
    }

    public Message parent(Message parent) {
        this.setParent(parent);
        return this;
    }

    public Vendor getVendor() {
        return this.vendor;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    public Message vendor(Vendor vendor) {
        this.setVendor(vendor);
        return this;
    }

    public Patient getPatient() {
        return this.patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Message patient(Patient patient) {
        this.setPatient(patient);
        return this;
    }

    public Professional getProfessional() {
        return this.professional;
    }

    public void setProfessional(Professional professional) {
        this.professional = professional;
    }

    public Message professional(Professional professional) {
        this.setProfessional(professional);
        return this;
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
