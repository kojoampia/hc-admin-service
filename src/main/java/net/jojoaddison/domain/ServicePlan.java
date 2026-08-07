package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.domain.enumeration.PlanTier;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A ServicePlan.
 */
@Document(collection = "service_plan")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ServicePlan implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Size(max = 60)
    @Field("name")
    private String name;

    @NotNull
    @Field("tier")
    private PlanTier tier;

    @Size(max = 40)
    @Field("tier_label")
    private String tierLabel;

    @NotNull
    @DecimalMin(value = "0")
    @Field("monthly_price")
    private BigDecimal monthlyPrice;

    @NotNull
    @Size(max = 3)
    @Field("currency")
    private String currency;

    @Size(max = 240)
    @Field("summary")
    private String summary;

    @NotNull
    @Field("featured")
    private Boolean featured;

    @Min(value = 0)
    @Field("subscriber_count")
    private Integer subscriberCount;

    @DBRef
    @Field("feature")
    @JsonIgnoreProperties(value = { "plan" }, allowSetters = true)
    private Set<PlanFeature> features = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public ServicePlan id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public ServicePlan name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PlanTier getTier() {
        return this.tier;
    }

    public ServicePlan tier(PlanTier tier) {
        this.setTier(tier);
        return this;
    }

    public void setTier(PlanTier tier) {
        this.tier = tier;
    }

    public String getTierLabel() {
        return this.tierLabel;
    }

    public ServicePlan tierLabel(String tierLabel) {
        this.setTierLabel(tierLabel);
        return this;
    }

    public void setTierLabel(String tierLabel) {
        this.tierLabel = tierLabel;
    }

    public BigDecimal getMonthlyPrice() {
        return this.monthlyPrice;
    }

    public ServicePlan monthlyPrice(BigDecimal monthlyPrice) {
        this.setMonthlyPrice(monthlyPrice);
        return this;
    }

    public void setMonthlyPrice(BigDecimal monthlyPrice) {
        this.monthlyPrice = monthlyPrice;
    }

    public String getCurrency() {
        return this.currency;
    }

    public ServicePlan currency(String currency) {
        this.setCurrency(currency);
        return this;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getSummary() {
        return this.summary;
    }

    public ServicePlan summary(String summary) {
        this.setSummary(summary);
        return this;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Boolean getFeatured() {
        return this.featured;
    }

    public ServicePlan featured(Boolean featured) {
        this.setFeatured(featured);
        return this;
    }

    public void setFeatured(Boolean featured) {
        this.featured = featured;
    }

    public Integer getSubscriberCount() {
        return this.subscriberCount;
    }

    public ServicePlan subscriberCount(Integer subscriberCount) {
        this.setSubscriberCount(subscriberCount);
        return this;
    }

    public void setSubscriberCount(Integer subscriberCount) {
        this.subscriberCount = subscriberCount;
    }

    public Set<PlanFeature> getFeatures() {
        return this.features;
    }

    public void setFeatures(Set<PlanFeature> planFeatures) {
        if (this.features != null) {
            this.features.forEach(i -> i.setPlan(null));
        }
        if (planFeatures != null) {
            planFeatures.forEach(i -> i.setPlan(this));
        }
        this.features = planFeatures;
    }

    public ServicePlan features(Set<PlanFeature> planFeatures) {
        this.setFeatures(planFeatures);
        return this;
    }

    public ServicePlan addFeature(PlanFeature planFeature) {
        this.features.add(planFeature);
        planFeature.setPlan(this);
        return this;
    }

    public ServicePlan removeFeature(PlanFeature planFeature) {
        this.features.remove(planFeature);
        planFeature.setPlan(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServicePlan)) {
            return false;
        }
        return getId() != null && getId().equals(((ServicePlan) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ServicePlan{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", tier='" + getTier() + "'" +
            ", tierLabel='" + getTierLabel() + "'" +
            ", monthlyPrice=" + getMonthlyPrice() +
            ", currency='" + getCurrency() + "'" +
            ", summary='" + getSummary() + "'" +
            ", featured='" + getFeatured() + "'" +
            ", subscriberCount=" + getSubscriberCount() +
            "}";
    }
}
