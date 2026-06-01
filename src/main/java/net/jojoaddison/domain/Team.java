package net.jojoaddison.domain;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A Team.
 */
@Document(collection = "team")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Team implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Field("name")
    private String name;

    @NotNull
    @Field("description")
    private String description;

    @Field("members")
    private String members;

    @Field("supervisor_id")
    private String supervisorId;

    @Field("organisation_id")
    private String organisationId;

    @Field("geographic_space_ids")
    private List<String> geographicSpaceIds;

    @NotNull
    @Field("created_by")
    private String createdBy;

    @NotNull
    @Field("created_date")
    private Instant createdDate;

    @NotNull
    @Field("modified_by")
    private String modifiedBy;

    @NotNull
    @Field("modified_date")
    private Instant modifiedDate;

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

    public String getMembers() {
        return this.members;
    }

    public Team members(String members) {
        this.setMembers(members);
        return this;
    }

    public void setMembers(String members) {
        this.members = members;
    }

    public String getSupervisorId() {
        return this.supervisorId;
    }

    public Team supervisorId(String supervisorId) {
        this.setSupervisorId(supervisorId);
        return this;
    }

    public void setSupervisorId(String supervisorId) {
        this.supervisorId = supervisorId;
    }

    public String getOrganisationId() {
        return this.organisationId;
    }

    public Team organisationId(String organisationId) {
        this.setOrganisationId(organisationId);
        return this;
    }

    public void setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public Team createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedDate() {
        return this.createdDate;
    }

    public Team createdDate(Instant createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public Team modifiedBy(String modifiedBy) {
        this.setModifiedBy(modifiedBy);
        return this;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public Instant getModifiedDate() {
        return this.modifiedDate;
    }

    public Team modifiedDate(Instant modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(Instant modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public List<String> getGeographicSpaceIds() {
        return this.geographicSpaceIds;
    }

    public Team geographicSpaceIds(List<String> geographicSpaceIds) {
        this.setGeographicSpaceIds(geographicSpaceIds);
        return this;
    }

    public void setGeographicSpaceIds(List<String> geographicSpaceIds) {
        this.geographicSpaceIds = geographicSpaceIds;
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
            ", members='" + getMembers() + "'" +
            ", supervisorId='" + getSupervisorId() + "'" +
            ", organisationId='" + getOrganisationId() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            "}";
    }
}
