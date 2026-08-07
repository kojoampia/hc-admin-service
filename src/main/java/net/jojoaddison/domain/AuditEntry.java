package net.jojoaddison.domain;

import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.AuditLevel;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A AuditEntry.
 */
@Document(collection = "audit_entry")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AuditEntry implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Field("occurred_at")
    private Instant occurredAt;

    @NotNull
    @Size(max = 80)
    @Field("actor")
    private String actor;

    @NotNull
    @Size(max = 120)
    @Field("action")
    private String action;

    @Size(max = 160)
    @Field("target")
    private String target;

    @NotNull
    @Field("level")
    private AuditLevel level;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public AuditEntry id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Instant getOccurredAt() {
        return this.occurredAt;
    }

    public AuditEntry occurredAt(Instant occurredAt) {
        this.setOccurredAt(occurredAt);
        return this;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getActor() {
        return this.actor;
    }

    public AuditEntry actor(String actor) {
        this.setActor(actor);
        return this;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return this.action;
    }

    public AuditEntry action(String action) {
        this.setAction(action);
        return this;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTarget() {
        return this.target;
    }

    public AuditEntry target(String target) {
        this.setTarget(target);
        return this;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public AuditLevel getLevel() {
        return this.level;
    }

    public AuditEntry level(AuditLevel level) {
        this.setLevel(level);
        return this;
    }

    public void setLevel(AuditLevel level) {
        this.level = level;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuditEntry)) {
            return false;
        }
        return getId() != null && getId().equals(((AuditEntry) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AuditEntry{" +
            "id=" + getId() +
            ", occurredAt='" + getOccurredAt() + "'" +
            ", actor='" + getActor() + "'" +
            ", action='" + getAction() + "'" +
            ", target='" + getTarget() + "'" +
            ", level='" + getLevel() + "'" +
            "}";
    }
}
