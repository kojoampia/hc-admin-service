package net.jojoaddison.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.DutyRole;
import net.jojoaddison.domain.enumeration.ShiftType;

/**
 * A DTO for the {@link net.jojoaddison.domain.DutyRoster} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DutyRosterDTO implements Serializable {

    private String id;

    @NotNull
    private LocalDate date;

    @NotNull
    private DutyRole duty;

    @NotNull
    private String professionalId;

    @NotNull
    private ShiftType shift;

    @NotNull
    private String name;

    private String description;

    @NotNull
    private String patientId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public DutyRole getDuty() {
        return duty;
    }

    public void setDuty(DutyRole duty) {
        this.duty = duty;
    }

    public String getProfessionalId() {
        return professionalId;
    }

    public void setProfessionalId(String professionalId) {
        this.professionalId = professionalId;
    }

    public ShiftType getShift() {
        return shift;
    }

    public void setShift(ShiftType shift) {
        this.shift = shift;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DutyRosterDTO)) {
            return false;
        }

        DutyRosterDTO dutyRosterDTO = (DutyRosterDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, dutyRosterDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "DutyRosterDTO{" +
            "id='" + getId() + "'" +
            ", date='" + getDate() + "'" +
            ", duty='" + getDuty() + "'" +
            ", professionalId='" + getProfessionalId() + "'" +
            ", shift='" + getShift() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", patientId='" + getPatientId() + "'" +
            "}";
    }
}
