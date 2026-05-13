package net.jojoaddison.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.FeatureType;

/**
 * A DTO for the {@link net.jojoaddison.domain.Feature} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class FeatureDTO implements Serializable {

    private String id;

    private String name;

    private String description;

    @NotNull
    private FeatureType type;

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

    public FeatureType getType() {
        return type;
    }

    public void setType(FeatureType type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FeatureDTO)) {
            return false;
        }

        FeatureDTO featureDTO = (FeatureDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, featureDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "FeatureDTO{" +
            "id='" + getId() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", type='" + getType() + "'" +
            "}";
    }
}
