package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * PDF: Team (Name, Description, Supervisor).
 */
@Document(collection = "team")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Team implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Size(max = 60)
    @Field("name")
    private String name;

    @Size(max = 200)
    @Field("description")
    private String description;

    @DBRef
    @Field("supervisor")
    @JsonIgnoreProperties(value = { "profile", "assignments", "team", "hub" }, allowSetters = true)
    private Professional supervisor;

    /**
     * The geographic spaces this team covers.
     *
     * <p>Not part of the console model — it is carried here because it is the hard geographic
     * constraint in {@code DutyRosterService.autoScheduleShifts}, reached through
     * {@code TeamRepository.findByGeographicSpaceIdsContaining}. Dropping it does not fail the
     * build: Spring Data derives that query from the property name, so a missing field is a startup
     * failure, not a compile error.
     *
     * <p>The console client does not send it. {@code TeamService.update} therefore restores the
     * stored value when an incoming payload omits it — a full PUT would otherwise wipe the
     * scheduler's only geography.
     */
    @Field("geographic_space_ids")
    private List<String> geographicSpaceIds;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Team id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public Team name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public Team description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Professional getSupervisor() {
        return this.supervisor;
    }

    public void setSupervisor(Professional professional) {
        this.supervisor = professional;
    }

    public Team supervisor(Professional professional) {
        this.setSupervisor(professional);
        return this;
    }

    public List<String> getGeographicSpaceIds() {
        return this.geographicSpaceIds;
    }

    public void setGeographicSpaceIds(List<String> geographicSpaceIds) {
        this.geographicSpaceIds = geographicSpaceIds;
    }

    public Team geographicSpaceIds(List<String> geographicSpaceIds) {
        this.setGeographicSpaceIds(geographicSpaceIds);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Team)) {
            return false;
        }
        return getId() != null && getId().equals(((Team) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Team{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            "}";
    }
}
