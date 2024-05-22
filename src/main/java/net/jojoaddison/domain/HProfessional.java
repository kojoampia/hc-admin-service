package net.jojoaddison.domain;

import java.io.Serializable;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A HProfessional.
 */
@Document(collection = "hprofessional")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class HProfessional implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @Field("name")
    private String name;

    @Field("organisation")
    private String organisation;

    @Field("roster")
    private String roster;

    @Field("created_date")
    private LocalDate createdDate;

    @Field("created_by")
    private String createdBy;

    @Field("modified_date")
    private LocalDate modifiedDate;

    @Field("modified_by")
    private String modifiedBy;

    @Field("profile")
    private String profile;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public HProfessional id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public HProfessional name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrganisation() {
        return this.organisation;
    }

    public HProfessional organisation(String organisation) {
        this.setOrganisation(organisation);
        return this;
    }

    public void setOrganisation(String organisation) {
        this.organisation = organisation;
    }

    public String getRoster() {
        return this.roster;
    }

    public HProfessional roster(String roster) {
        this.setRoster(roster);
        return this;
    }

    public void setRoster(String roster) {
        this.roster = roster;
    }

    public LocalDate getCreatedDate() {
        return this.createdDate;
    }

    public HProfessional createdDate(LocalDate createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(LocalDate createdDate) {
        this.createdDate = createdDate;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public HProfessional createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDate getModifiedDate() {
        return this.modifiedDate;
    }

    public HProfessional modifiedDate(LocalDate modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(LocalDate modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public HProfessional modifiedBy(String modifiedBy) {
        this.setModifiedBy(modifiedBy);
        return this;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public String getProfile() {
        return this.profile;
    }

    public HProfessional profile(String profile) {
        this.setProfile(profile);
        return this;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HProfessional)) {
            return false;
        }
        return getId() != null && getId().equals(((HProfessional) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "HProfessional{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", organisation='" + getOrganisation() + "'" +
            ", roster='" + getRoster() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            ", profile='" + getProfile() + "'" +
            "}";
    }
}
