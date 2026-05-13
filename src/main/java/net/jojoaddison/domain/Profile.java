package net.jojoaddison.domain;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A Profile.
 */
@Document(collection = "profile")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Profile implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Field("person_id")
    private String personId;

    @NotNull
    @Field("photo_id")
    private String photoId;

    @NotNull
    @Field("contact_id")
    private String contactId;

    @NotNull
    @Field("address_list")
    private String addressList;

    @Field("roles")
    private String roles;

    @NotNull
    @Field("status")
    private Boolean status;

    @NotNull
    @Field("organisation_id")
    private String organisationId;

    @NotNull
    @Field("team_id")
    private String teamId;

    @NotNull
    @Field("document_items")
    private String documentItems;

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

    public Profile id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPersonId() {
        return this.personId;
    }

    public Profile personId(String personId) {
        this.setPersonId(personId);
        return this;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }

    public String getPhotoId() {
        return this.photoId;
    }

    public Profile photoId(String photoId) {
        this.setPhotoId(photoId);
        return this;
    }

    public void setPhotoId(String photoId) {
        this.photoId = photoId;
    }

    public String getContactId() {
        return this.contactId;
    }

    public Profile contactId(String contactId) {
        this.setContactId(contactId);
        return this;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }

    public String getAddressList() {
        return this.addressList;
    }

    public Profile addressList(String addressList) {
        this.setAddressList(addressList);
        return this;
    }

    public void setAddressList(String addressList) {
        this.addressList = addressList;
    }

    public String getRoles() {
        return this.roles;
    }

    public Profile roles(String roles) {
        this.setRoles(roles);
        return this;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public Boolean getStatus() {
        return this.status;
    }

    public Profile status(Boolean status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getOrganisationId() {
        return this.organisationId;
    }

    public Profile organisationId(String organisationId) {
        this.setOrganisationId(organisationId);
        return this;
    }

    public void setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
    }

    public String getTeamId() {
        return this.teamId;
    }

    public Profile teamId(String teamId) {
        this.setTeamId(teamId);
        return this;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public String getDocumentItems() {
        return this.documentItems;
    }

    public Profile documentItems(String documentItems) {
        this.setDocumentItems(documentItems);
        return this;
    }

    public void setDocumentItems(String documentItems) {
        this.documentItems = documentItems;
    }

    public String getCreatedBy() {
        return this.createdBy;
    }

    public Profile createdBy(String createdBy) {
        this.setCreatedBy(createdBy);
        return this;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedDate() {
        return this.createdDate;
    }

    public Profile createdDate(Instant createdDate) {
        this.setCreatedDate(createdDate);
        return this;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getModifiedBy() {
        return this.modifiedBy;
    }

    public Profile modifiedBy(String modifiedBy) {
        this.setModifiedBy(modifiedBy);
        return this;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public Instant getModifiedDate() {
        return this.modifiedDate;
    }

    public Profile modifiedDate(Instant modifiedDate) {
        this.setModifiedDate(modifiedDate);
        return this;
    }

    public void setModifiedDate(Instant modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Profile)) {
            return false;
        }
        return getId() != null && getId().equals(((Profile) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Profile{" +
            "id=" + getId() +
            ", personId='" + getPersonId() + "'" +
            ", photoId='" + getPhotoId() + "'" +
            ", contactId='" + getContactId() + "'" +
            ", addressList='" + getAddressList() + "'" +
            ", roles='" + getRoles() + "'" +
            ", status='" + getStatus() + "'" +
            ", organisationId='" + getOrganisationId() + "'" +
            ", teamId='" + getTeamId() + "'" +
            ", documentItems='" + getDocumentItems() + "'" +
            ", createdBy='" + getCreatedBy() + "'" +
            ", createdDate='" + getCreatedDate() + "'" +
            ", modifiedBy='" + getModifiedBy() + "'" +
            ", modifiedDate='" + getModifiedDate() + "'" +
            "}";
    }
}
