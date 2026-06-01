package net.jojoaddison.domain;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.DutyRole;
import net.jojoaddison.domain.enumeration.ShiftStatus;
import net.jojoaddison.domain.enumeration.ShiftType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A DutyRoster.
 */
@Document(collection = "duty_roster")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DutyRoster implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Field("date")
    private LocalDate date;

    @NotNull
    @Field("duty")
    private DutyRole duty;

    @NotNull
    @Field("professional_id")
    private String professionalId;

    @NotNull
    @Field("shift")
    private ShiftType shift;

    @NotNull
    @Field("name")
    private String name;

    @Field("description")
    private String description;

    @NotNull
    @Field("patient_id")
    private String patientId;

    @Field("geographic_space_id")
    private String geographicSpaceId;

    @Field("status")
    private ShiftStatus status;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public DutyRoster id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return this.date;
    }

    public DutyRoster date(LocalDate date) {
        this.setDate(date);
        return this;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public DutyRole getDuty() {
        return this.duty;
    }

    public DutyRoster duty(DutyRole duty) {
        this.setDuty(duty);
        return this;
    }

    public void setDuty(DutyRole duty) {
        this.duty = duty;
    }

    public String getProfessionalId() {
        return this.professionalId;
    }

    public DutyRoster professionalId(String professionalId) {
        this.setProfessionalId(professionalId);
        return this;
    }

    public void setProfessionalId(String professionalId) {
        this.professionalId = professionalId;
    }

    public ShiftType getShift() {
        return this.shift;
    }

    public DutyRoster shift(ShiftType shift) {
        this.setShift(shift);
        return this;
    }

    public void setShift(ShiftType shift) {
        this.shift = shift;
    }

    public String getName() {
        return this.name;
    }

    public DutyRoster name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public DutyRoster description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPatientId() {
        return this.patientId;
    }

    public DutyRoster patientId(String patientId) {
        this.setPatientId(patientId);
        return this;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getGeographicSpaceId() {
        return this.geographicSpaceId;
    }

    public DutyRoster geographicSpaceId(String geographicSpaceId) {
        this.setGeographicSpaceId(geographicSpaceId);
        return this;
    }

    public void setGeographicSpaceId(String geographicSpaceId) {
        this.geographicSpaceId = geographicSpaceId;
    }

    public ShiftStatus getStatus() {
        return this.status;
    }

    public DutyRoster status(ShiftStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(ShiftStatus status) {
        this.status = status;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DutyRoster)) {
            return false;
        }
        return getId() != null && getId().equals(((DutyRoster) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DutyRoster{" +
            "id=" + getId() +
            ", date='" + getDate() + "'" +
            ", duty='" + getDuty() + "'" +
            ", professionalId='" + getProfessionalId() + "'" +
            ", shift='" + getShift() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", patientId='" + getPatientId() + "'" +
            ", geographicSpaceId='" + getGeographicSpaceId() + "'" +
            ", status='" + getStatus() + "'" +
            "}";
    }
}
