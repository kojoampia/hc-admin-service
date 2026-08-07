package net.jojoaddison.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A DTO for the {@link net.jojoaddison.domain.Organisation} entity.
 */
@Schema(description = "PDF: Organisation. One row — Abofonsa BridgeCare itself.")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class OrganisationDTO implements Serializable {

    private String id;

    @NotNull
    @Size(max = 80)
    private String name;

    @NotNull
    @Size(max = 120)
    private String legalName;

    @Size(max = 400)
    private String description;

    @Size(max = 40)
    private String registrationNumber;

    @Size(max = 40)
    private String tin;

    private LocalDate foundedOn;

    @Size(max = 24)
    private String switchboard;

    @Size(max = 120)
    private String email;

    @Size(max = 80)
    private String deskHours;

    private AddressDTO address;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getTin() {
        return tin;
    }

    public void setTin(String tin) {
        this.tin = tin;
    }

    public LocalDate getFoundedOn() {
        return foundedOn;
    }

    public void setFoundedOn(LocalDate foundedOn) {
        this.foundedOn = foundedOn;
    }

    public String getSwitchboard() {
        return switchboard;
    }

    public void setSwitchboard(String switchboard) {
        this.switchboard = switchboard;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDeskHours() {
        return deskHours;
    }

    public void setDeskHours(String deskHours) {
        this.deskHours = deskHours;
    }

    public AddressDTO getAddress() {
        return address;
    }

    public void setAddress(AddressDTO address) {
        this.address = address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OrganisationDTO)) {
            return false;
        }

        OrganisationDTO organisationDTO = (OrganisationDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, organisationDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "OrganisationDTO{" +
            "id='" + getId() + "'" +
            ", name='" + getName() + "'" +
            ", legalName='" + getLegalName() + "'" +
            ", description='" + getDescription() + "'" +
            ", registrationNumber='" + getRegistrationNumber() + "'" +
            ", tin='" + getTin() + "'" +
            ", foundedOn='" + getFoundedOn() + "'" +
            ", switchboard='" + getSwitchboard() + "'" +
            ", email='" + getEmail() + "'" +
            ", deskHours='" + getDeskHours() + "'" +
            ", address=" + getAddress() +
            "}";
    }
}
