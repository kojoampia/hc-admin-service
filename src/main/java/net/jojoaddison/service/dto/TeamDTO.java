package net.jojoaddison.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * A DTO for the {@link net.jojoaddison.domain.Team} entity.
 */
@Schema(description = "PDF: Team (Name, Description, Supervisor).")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TeamDTO implements Serializable {

    private String id;

    @NotNull
    @Size(max = 60)
    private String name;

    @Size(max = 200)
    private String description;

    private ProfessionalDTO supervisor;

    /** See {@link net.jojoaddison.domain.Team#getGeographicSpaceIds()} — api-side, not console. */
    private List<String> geographicSpaceIds;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ProfessionalDTO getSupervisor() {
        return supervisor;
    }

    public void setSupervisor(ProfessionalDTO supervisor) {
        this.supervisor = supervisor;
    }

    public List<String> getGeographicSpaceIds() {
        return geographicSpaceIds;
    }

    public void setGeographicSpaceIds(List<String> geographicSpaceIds) {
        this.geographicSpaceIds = geographicSpaceIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TeamDTO)) {
            return false;
        }

        TeamDTO teamDTO = (TeamDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, teamDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TeamDTO{" +
            "id='" + getId() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", supervisor=" + getSupervisor() +
            ", geographicSpaceIds='" + getGeographicSpaceIds() + "'" +
            "}";
    }
}
