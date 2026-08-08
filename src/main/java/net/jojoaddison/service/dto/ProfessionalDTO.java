package net.jojoaddison.service.dto;

import java.io.Serializable;
import java.util.Objects;

/**
 * A DTO for the {@link net.jojoaddison.domain.Professional} entity, carrying only what a
 * relationship display field needs.
 *
 * <p>Professional has no DTO layer of its own — {@code ProfessionalResource} exposes the domain
 * entity directly, which CLAUDE.md records as the deliberate convention here alongside
 * {@code OrganisationResource} and {@code PersonResource}. This class exists solely because
 * {@code TeamMapper.toDtoProfessionalLicenceNumber} needs a type to map the supervisor into, and it
 * maps exactly two properties: {@code @BeanMapping(ignoreByDefault = true)} plus {@code id} and
 * {@code licenceNumber}.
 *
 * <p>Deliberately not the full entity shape. Professional relates to Profile, Team and Hub, none of
 * which have DTOs, so a faithful full DTO would pull three more into existence for fields no caller
 * of {@code /api/teams} reads.
 *
 * <p>licenceNumber rather than a name: a JDL display field must be a direct field of the target
 * entity, and Professional reaches firstName/lastName through Profile. The console screens resolve
 * the display name themselves — see the note above the ManyToOne block in
 * {@code jdl/hc-admin-console.jdl}.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ProfessionalDTO implements Serializable {

    private String id;

    private String licenceNumber;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLicenceNumber() {
        return licenceNumber;
    }

    public void setLicenceNumber(String licenceNumber) {
        this.licenceNumber = licenceNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProfessionalDTO)) {
            return false;
        }

        ProfessionalDTO professionalDTO = (ProfessionalDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, professionalDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ProfessionalDTO{" +
            "id='" + getId() + "'" +
            ", licenceNumber='" + getLicenceNumber() + "'" +
            "}";
    }
}
