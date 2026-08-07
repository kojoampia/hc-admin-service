package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A PlanFeature.
 */
@Document(collection = "plan_feature")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PlanFeature implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Size(max = 120)
    @Field("label")
    private String label;

    @NotNull
    @Min(value = 0)
    @Field("position")
    private Integer position;

    @DBRef
    @Field("plan")
    @JsonIgnoreProperties(value = { "features" }, allowSetters = true)
    private ServicePlan plan;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public PlanFeature id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return this.label;
    }

    public PlanFeature label(String label) {
        this.setLabel(label);
        return this;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getPosition() {
        return this.position;
    }

    public PlanFeature position(Integer position) {
        this.setPosition(position);
        return this;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public ServicePlan getPlan() {
        return this.plan;
    }

    public void setPlan(ServicePlan servicePlan) {
        this.plan = servicePlan;
    }

    public PlanFeature plan(ServicePlan servicePlan) {
        this.setPlan(servicePlan);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlanFeature)) {
            return false;
        }
        return getId() != null && getId().equals(((PlanFeature) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PlanFeature{" +
            "id=" + getId() +
            ", label='" + getLabel() + "'" +
            ", position=" + getPosition() +
            "}";
    }
}
