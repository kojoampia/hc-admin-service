package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.Priority;
import net.jojoaddison.domain.enumeration.TaskState;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A Task.
 */
@Document(collection = "task")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Task implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Size(max = 200)
    @Field("title")
    private String title;

    @NotNull
    @Field("state")
    private TaskState state;

    @NotNull
    @Field("priority")
    private Priority priority;

    @Field("due_on")
    private LocalDate dueOn;

    @Size(max = 40)
    @Field("tag")
    private String tag;

    @Field("created_at")
    private Instant createdAt;

    /**
     * When this task stopped being open, or {@code null} while it still is.
     *
     * <p>Stamped by {@code TaskLifecycleCallback} the first time {@code state} becomes {@code DONE},
     * and cleared when a task is re-opened. Same reasoning as {@code Message.readAt}: the open-task
     * tile counts a backlog, and a backlog has no history unless something records the leaving.
     */
    @Field("closed_at")
    private Instant closedAt;

    @DBRef
    @Field("owner")
    @JsonIgnoreProperties(value = { "profile", "assignments", "team", "hub" }, allowSetters = true)
    private Professional owner;

    @DBRef
    @Field("sourceMessage")
    private Message sourceMessage;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Task id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return this.title;
    }

    public Task title(String title) {
        this.setTitle(title);
        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public TaskState getState() {
        return this.state;
    }

    public Task state(TaskState state) {
        this.setState(state);
        return this;
    }

    public void setState(TaskState state) {
        this.state = state;
    }

    public Priority getPriority() {
        return this.priority;
    }

    public Task priority(Priority priority) {
        this.setPriority(priority);
        return this;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public LocalDate getDueOn() {
        return this.dueOn;
    }

    public Task dueOn(LocalDate dueOn) {
        this.setDueOn(dueOn);
        return this;
    }

    public void setDueOn(LocalDate dueOn) {
        this.dueOn = dueOn;
    }

    public String getTag() {
        return this.tag;
    }

    public Task tag(String tag) {
        this.setTag(tag);
        return this;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public Instant getCreatedAt() {
        return this.createdAt;
    }

    public Task createdAt(Instant createdAt) {
        this.setCreatedAt(createdAt);
        return this;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getClosedAt() {
        return this.closedAt;
    }

    public Task closedAt(Instant closedAt) {
        this.setClosedAt(closedAt);
        return this;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Professional getOwner() {
        return this.owner;
    }

    public void setOwner(Professional professional) {
        this.owner = professional;
    }

    public Task owner(Professional professional) {
        this.setOwner(professional);
        return this;
    }

    public Message getSourceMessage() {
        return this.sourceMessage;
    }

    public void setSourceMessage(Message message) {
        this.sourceMessage = message;
    }

    public Task sourceMessage(Message message) {
        this.setSourceMessage(message);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Task)) {
            return false;
        }
        return getId() != null && getId().equals(((Task) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Task{" +
            "id=" + getId() +
            ", title='" + getTitle() + "'" +
            ", state='" + getState() + "'" +
            ", priority='" + getPriority() + "'" +
            ", dueOn='" + getDueOn() + "'" +
            ", tag='" + getTag() + "'" +
            ", createdAt='" + getCreatedAt() + "'" +
            "}";
    }
}
