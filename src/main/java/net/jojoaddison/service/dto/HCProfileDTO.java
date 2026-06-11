package net.jojoaddison.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import net.jojoaddison.domain.UnavailabilityPeriod;
import net.jojoaddison.domain.enumeration.RoleType;

/**
 * A DTO for the {@link net.jojoaddison.domain.HCProfile} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class HCProfileDTO implements Serializable {

    private String id;

    @NotNull
    private String userId;

    @NotNull
    private String personId;

    @NotNull
    private String photoId;

    @NotNull
    private String contactId;

    @NotNull
    private String addressList;

    private String roles;

    @NotNull
    private Boolean status;

    @NotNull
    private String organisationId;

    @NotNull
    private String teamId;

    private RoleType roleType;

    private List<UnavailabilityPeriod> unavailabilityPeriods;

    @NotNull
    private String documentItems;

    @NotNull
    private String createdBy;

    @NotNull
    private Instant createdDate;

    @NotNull
    private String modifiedBy;

    @NotNull
    private Instant modifiedDate;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
    }

    public String getPhotoId() {
        return photoId;
    }

    public void setPhotoId(String photoId) {
        this.photoId = photoId;
    }

    public String getContactId() {
        return contactId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }

    public String getAddressList() {
        return addressList;
    }

    public void setAddressList(String addressList) {
        this.addressList = addressList;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public String getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(String organisationId) {
        this.organisationId = organisationId;
    }

    public String getTeamId() {
        return teamId;
    }

    public void setTeamId(String teamId) {
        this.teamId = teamId;
    }

    public RoleType getRoleType() {
        return roleType;
    }

    public void setRoleType(RoleType roleType) {
        this.roleType = roleType;
    }

    public List<UnavailabilityPeriod> getUnavailabilityPeriods() {
        return unavailabilityPeriods;
    }

    public void setUnavailabilityPeriods(List<UnavailabilityPeriod> unavailabilityPeriods) {
        this.unavailabilityPeriods = unavailabilityPeriods;
    }

    public String getDocumentItems() {
        return documentItems;
    }

    public void setDocumentItems(String documentItems) {
        this.documentItems = documentItems;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public Instant getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(Instant modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof HCProfileDTO)) {
            return false;
        }

        HCProfileDTO profileDTO = (HCProfileDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, profileDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ProfileDTO{" +
            "id='" + getId() + "'" +
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
