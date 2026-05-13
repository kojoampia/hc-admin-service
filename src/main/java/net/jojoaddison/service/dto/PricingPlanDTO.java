package net.jojoaddison.service.dto;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;
import net.jojoaddison.domain.enumeration.BillingType;

/**
 * A DTO for the {@link net.jojoaddison.domain.PricingPlan} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PricingPlanDTO implements Serializable {

    private String id;

    @NotNull
    private String name;

    @NotNull
    private String description;

    @NotNull
    private BigDecimal price;

    @NotNull
    private String features;

    @NotNull
    private BillingType billingCycle;

    @NotNull
    private Boolean active;

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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getFeatures() {
        return features;
    }

    public void setFeatures(String features) {
        this.features = features;
    }

    public BillingType getBillingCycle() {
        return billingCycle;
    }

    public void setBillingCycle(BillingType billingCycle) {
        this.billingCycle = billingCycle;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PricingPlanDTO)) {
            return false;
        }

        PricingPlanDTO pricingPlanDTO = (PricingPlanDTO) o;
        if (this.id == null) {
            return false;
        }
        return Objects.equals(this.id, pricingPlanDTO.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PricingPlanDTO{" +
            "id='" + getId() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", price=" + getPrice() +
            ", features='" + getFeatures() + "'" +
            ", billingCycle='" + getBillingCycle() + "'" +
            ", active='" + getActive() + "'" +
            "}";
    }
}
