package net.jojoaddison.domain;

import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import net.jojoaddison.domain.enumeration.BillingType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A PricingPlan.
 */
@Document(collection = "pricing_plan")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PricingPlan implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Field("name")
    private String name;

    @NotNull
    @Field("description")
    private String description;

    @NotNull
    @Field("price")
    private BigDecimal price;

    @NotNull
    @Field("features")
    private String features;

    @NotNull
    @Field("billing_cycle")
    private BillingType billingCycle;

    @NotNull
    @Field("active")
    private Boolean active;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public PricingPlan id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public PricingPlan name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public PricingPlan description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return this.price;
    }

    public PricingPlan price(BigDecimal price) {
        this.setPrice(price);
        return this;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getFeatures() {
        return this.features;
    }

    public PricingPlan features(String features) {
        this.setFeatures(features);
        return this;
    }

    public void setFeatures(String features) {
        this.features = features;
    }

    public BillingType getBillingCycle() {
        return this.billingCycle;
    }

    public PricingPlan billingCycle(BillingType billingCycle) {
        this.setBillingCycle(billingCycle);
        return this;
    }

    public void setBillingCycle(BillingType billingCycle) {
        this.billingCycle = billingCycle;
    }

    public Boolean getActive() {
        return this.active;
    }

    public PricingPlan active(Boolean active) {
        this.setActive(active);
        return this;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PricingPlan)) {
            return false;
        }
        return getId() != null && getId().equals(((PricingPlan) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PricingPlan{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", price=" + getPrice() +
            ", features='" + getFeatures() + "'" +
            ", billingCycle='" + getBillingCycle() + "'" +
            ", active='" + getActive() + "'" +
            "}";
    }
}
