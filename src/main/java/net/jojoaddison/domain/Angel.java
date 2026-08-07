package net.jojoaddison.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * Next of kin / diaspora sponsor. Called \"Angel\" throughout the
 * Health Connect product language — keep the name.
 */
@Schema(description = "Next of kin / diaspora sponsor. Called \"Angel\" throughout the\nHealth Connect product language — keep the name.")
@Document(collection = "angel")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Angel implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Size(max = 80)
    @Field("name")
    private String name;

    @NotNull
    @Size(max = 60)
    @Field("relationship")
    private String relationship;

    @NotNull
    @Size(max = 24)
    @Field("phone")
    private String phone;

    @Size(max = 120)
    @Field("email")
    private String email;

    @Size(max = 60)
    @Field("country")
    private String country;

    @DBRef
    private Patient patient;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Angel id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public Angel name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRelationship() {
        return this.relationship;
    }

    public Angel relationship(String relationship) {
        this.setRelationship(relationship);
        return this;
    }

    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    public String getPhone() {
        return this.phone;
    }

    public Angel phone(String phone) {
        this.setPhone(phone);
        return this;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return this.email;
    }

    public Angel email(String email) {
        this.setEmail(email);
        return this;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCountry() {
        return this.country;
    }

    public Angel country(String country) {
        this.setCountry(country);
        return this;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public Patient getPatient() {
        return this.patient;
    }

    public void setPatient(Patient patient) {
        if (this.patient != null) {
            this.patient.setAngel(null);
        }
        if (patient != null) {
            patient.setAngel(this);
        }
        this.patient = patient;
    }

    public Angel patient(Patient patient) {
        this.setPatient(patient);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Angel)) {
            return false;
        }
        return getId() != null && getId().equals(((Angel) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Angel{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", relationship='" + getRelationship() + "'" +
            ", phone='" + getPhone() + "'" +
            ", email='" + getEmail() + "'" +
            ", country='" + getCountry() + "'" +
            "}";
    }
}
