package net.jojoaddison.domain;

import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.IdType;
import net.jojoaddison.domain.enumeration.Sex;
import net.jojoaddison.domain.enumeration.Title;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * The shared person record behind both patients and professionals.
 *
 * `accountId` is the identity link to the gateway's Account. It is a plain
 * String, not a relationship: Account lives in a gateway's own database and
 * this service cannot join across that boundary. A Profile with no accountId
 * is a person no login can reach, so it is required.
 *
 * <p><b>The value is the account's {@code User.id}, never the login</b> — the estate decision of
 * 2026-09-17 ({@code account.id = profile.accountId}), applied here by backlog item 123. The two
 * are both opaque strings and a join on the wrong one matches nothing silently, which is why this
 * paragraph names the value space instead of trusting the field name. The account may live on any
 * of the estate's gateways: the administrator's and the twelve office profiles' accounts are
 * hc-admin-gateway's (ids committed in that repo's {@code hc-admin-gw-data.json} — a cross-service
 * contract), a clinician's is hc-professional-gateway's.
 *
 * <p>⚠ <b>Transitional, deliberate, and reported rather than papered over:</b> the nine seeded
 * clinician rows ({@code profile-p1}–{@code p9}) still carry hc-professional <em>logins</em>,
 * because that gateway mints its account ids at creation time ({@code UUID.randomUUID()} in their
 * {@code InitialSetupMigration}, and their quality loader for the named five) — no fixture on any
 * stack holds an id this seed could reference. Their translation belongs to hc-professional's
 * quality loader ({@code link_hc_admin}, which already overwrites these rows on every quality
 * roll and owns the login → {@code User.id} resolution via its {@code account_uid} helper).
 * {@code DevelopmentDataInitializerTest} pins which rows hold which value space.
 */
@Document(collection = "profile")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Profile implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Size(max = 60)
    @Field("account_id")
    private String accountId;

    @Field("title")
    private Title title;

    @NotNull
    @Size(max = 50)
    @Field("first_name")
    private String firstName;

    @Size(max = 50)
    @Field("middle_name")
    private String middleName;

    @NotNull
    @Size(max = 50)
    @Field("last_name")
    private String lastName;

    @NotNull
    @Field("date_of_birth")
    private LocalDate dateOfBirth;

    @NotNull
    @Field("sex")
    private Sex sex;

    @NotNull
    @Size(max = 24)
    @Field("mobile_phone")
    private String mobilePhone;

    @NotNull
    @Size(max = 120)
    @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    @Field("email")
    private String email;

    @NotNull
    @Field("id_type")
    private IdType idType;

    @NotNull
    @Size(max = 40)
    @Field("id_number")
    private String idNumber;

    @DBRef
    @Field("address")
    private Address address;

    @DBRef
    private Patient patient;

    @DBRef
    private Professional professional;

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

    public String getAccountId() {
        return this.accountId;
    }

    public Profile accountId(String accountId) {
        this.setAccountId(accountId);
        return this;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public Title getTitle() {
        return this.title;
    }

    public Profile title(Title title) {
        this.setTitle(title);
        return this;
    }

    public void setTitle(Title title) {
        this.title = title;
    }

    public String getFirstName() {
        return this.firstName;
    }

    public Profile firstName(String firstName) {
        this.setFirstName(firstName);
        return this;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return this.middleName;
    }

    public Profile middleName(String middleName) {
        this.setMiddleName(middleName);
        return this;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return this.lastName;
    }

    public Profile lastName(String lastName) {
        this.setLastName(lastName);
        return this;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDate getDateOfBirth() {
        return this.dateOfBirth;
    }

    public Profile dateOfBirth(LocalDate dateOfBirth) {
        this.setDateOfBirth(dateOfBirth);
        return this;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Sex getSex() {
        return this.sex;
    }

    public Profile sex(Sex sex) {
        this.setSex(sex);
        return this;
    }

    public void setSex(Sex sex) {
        this.sex = sex;
    }

    public String getMobilePhone() {
        return this.mobilePhone;
    }

    public Profile mobilePhone(String mobilePhone) {
        this.setMobilePhone(mobilePhone);
        return this;
    }

    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
    }

    public String getEmail() {
        return this.email;
    }

    public Profile email(String email) {
        this.setEmail(email);
        return this;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public IdType getIdType() {
        return this.idType;
    }

    public Profile idType(IdType idType) {
        this.setIdType(idType);
        return this;
    }

    public void setIdType(IdType idType) {
        this.idType = idType;
    }

    public String getIdNumber() {
        return this.idNumber;
    }

    public Profile idNumber(String idNumber) {
        this.setIdNumber(idNumber);
        return this;
    }

    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }

    public Address getAddress() {
        return this.address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Profile address(Address address) {
        this.setAddress(address);
        return this;
    }

    public Patient getPatient() {
        return this.patient;
    }

    public void setPatient(Patient patient) {
        if (this.patient != null) {
            this.patient.setProfile(null);
        }
        if (patient != null) {
            patient.setProfile(this);
        }
        this.patient = patient;
    }

    public Profile patient(Patient patient) {
        this.setPatient(patient);
        return this;
    }

    public Professional getProfessional() {
        return this.professional;
    }

    public void setProfessional(Professional professional) {
        if (this.professional != null) {
            this.professional.setProfile(null);
        }
        if (professional != null) {
            professional.setProfile(this);
        }
        this.professional = professional;
    }

    public Profile professional(Professional professional) {
        this.setProfessional(professional);
        return this;
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
            ", accountId='" + getAccountId() + "'" +
            ", title='" + getTitle() + "'" +
            ", firstName='" + getFirstName() + "'" +
            ", middleName='" + getMiddleName() + "'" +
            ", lastName='" + getLastName() + "'" +
            ", dateOfBirth='" + getDateOfBirth() + "'" +
            ", sex='" + getSex() + "'" +
            ", mobilePhone='" + getMobilePhone() + "'" +
            ", email='" + getEmail() + "'" +
            ", idType='" + getIdType() + "'" +
            ", idNumber='" + getIdNumber() + "'" +
            "}";
    }
}
