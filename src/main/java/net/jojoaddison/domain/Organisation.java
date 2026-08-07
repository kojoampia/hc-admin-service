package net.jojoaddison.domain;

import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * PDF: Organisation. One row — Abofonsa BridgeCare itself.
 */
@Document(collection = "organisation")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Organisation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Size(max = 80)
    @Field("name")
    private String name;

    @NotNull
    @Size(max = 120)
    @Field("legal_name")
    private String legalName;

    @Size(max = 400)
    @Field("description")
    private String description;

    @Size(max = 40)
    @Field("registration_number")
    private String registrationNumber;

    @Size(max = 40)
    @Field("tin")
    private String tin;

    @Field("founded_on")
    private LocalDate foundedOn;

    @Size(max = 24)
    @Field("switchboard")
    private String switchboard;

    @Size(max = 120)
    @Field("email")
    private String email;

    @Size(max = 80)
    @Field("desk_hours")
    private String deskHours;

    @DBRef
    @Field("address")
    private Address address;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Organisation id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public Organisation name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLegalName() {
        return this.legalName;
    }

    public Organisation legalName(String legalName) {
        this.setLegalName(legalName);
        return this;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getDescription() {
        return this.description;
    }

    public Organisation description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getRegistrationNumber() {
        return this.registrationNumber;
    }

    public Organisation registrationNumber(String registrationNumber) {
        this.setRegistrationNumber(registrationNumber);
        return this;
    }

    public void setRegistrationNumber(String registrationNumber) {
        this.registrationNumber = registrationNumber;
    }

    public String getTin() {
        return this.tin;
    }

    public Organisation tin(String tin) {
        this.setTin(tin);
        return this;
    }

    public void setTin(String tin) {
        this.tin = tin;
    }

    public LocalDate getFoundedOn() {
        return this.foundedOn;
    }

    public Organisation foundedOn(LocalDate foundedOn) {
        this.setFoundedOn(foundedOn);
        return this;
    }

    public void setFoundedOn(LocalDate foundedOn) {
        this.foundedOn = foundedOn;
    }

    public String getSwitchboard() {
        return this.switchboard;
    }

    public Organisation switchboard(String switchboard) {
        this.setSwitchboard(switchboard);
        return this;
    }

    public void setSwitchboard(String switchboard) {
        this.switchboard = switchboard;
    }

    public String getEmail() {
        return this.email;
    }

    public Organisation email(String email) {
        this.setEmail(email);
        return this;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDeskHours() {
        return this.deskHours;
    }

    public Organisation deskHours(String deskHours) {
        this.setDeskHours(deskHours);
        return this;
    }

    public void setDeskHours(String deskHours) {
        this.deskHours = deskHours;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Organisation address(Address address) {
        this.setAddress(address);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Organisation)) {
            return false;
        }
        return getId() != null && getId().equals(((Organisation) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Organisation{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", legalName='" + getLegalName() + "'" +
            ", description='" + getDescription() + "'" +
            ", registrationNumber='" + getRegistrationNumber() + "'" +
            ", tin='" + getTin() + "'" +
            ", foundedOn='" + getFoundedOn() + "'" +
            ", switchboard='" + getSwitchboard() + "'" +
            ", email='" + getEmail() + "'" +
            ", deskHours='" + getDeskHours() + "'" +
            "}";
    }
}
