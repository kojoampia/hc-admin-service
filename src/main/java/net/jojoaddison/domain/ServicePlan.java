package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One membership tier, as this console holds it.
 *
 * <h2>Abofonsa publishes the catalogue; this service keeps a copy of part of it</h2>
 *
 * <p>Until 2026-09-08 this collection was a price list of its own — {@code Bridge Essential} /
 * {@code Bridge Plus} / {@code Bridge Family} at GHS 320 / 680 / 1,240 — while
 * {@code web.abofonsa.com} and hc-patient both showed {@code PEAR} / {@code PAWPAW} / {@code MELON}
 * at GHS 3,000 / 5,000 / 8,000. Different names, different tier codes, and prices differing by
 * roughly a factor of ten. hc-patient's {@code membership-plan.service.ts} carries the reason that
 * matters: <em>"the subsystem has a documented history of two products quoting different numbers for
 * the same tier, and the way that stays fixed is by never restating a price"</em> — and this service
 * was the third product doing exactly that, in production. Backlog item 51.
 *
 * <p>{@link #code} is now the content API's code and is the join key;
 * {@link net.jojoaddison.service.ServicePlanCatalogueSyncService} keeps {@code code}, {@link #name}
 * and {@link #displayOrder} in step with what Abofonsa publishes. Nothing else on this document is
 * synced, and nothing here is ever deleted by the sync — see that class.
 *
 * <h2>{@link #monthlyPrice} is still a second copy, and it is the field item 51 was opened about</h2>
 *
 * <p>This is not a closed drift and should not be described as one. Abofonsa publishes
 * {@code priceAmount} as a string already formatted for the requesting locale — {@code "8,000"} —
 * and deliberately publishes no machine-readable price at all. This console needs a number, because
 * {@link net.jojoaddison.service.ServicePlanSummaryService} computes the dashboard's monthly revenue
 * as {@code monthlyPrice × subscribers}. So hc-admin owns a {@link BigDecimal} of its own, an
 * administrator sets it, and it can disagree with the published figure with nothing failing.
 *
 * <p><b>Do not "fix" that by parsing {@code priceAmount}.</b> It is a display string in an unstated
 * locale format; parsing it is precisely what hc-patient's comment forbids, and it would break
 * silently the first time a locale groups or separates differently. If a machine-readable price is
 * wanted, it has to be asked of Abofonsa and published by them.
 *
 * <p>A plan the sync created has <b>no</b> {@code monthlyPrice} until somebody sets one, which is
 * why the field is nullable — see the note on it.
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

    /**
     * Abofonsa's stable tier code — {@code PEAR}, {@code PAWPAW}, {@code MELON} — and the join key.
     *
     * <p>Optional rather than required, and the two reasons are different. A plan an administrator
     * created by hand before the catalogue was reconciled has none, and refusing to load it would
     * turn a data question into a startup failure; and item 48's inbound plan-choice event will name
     * a plan by this code, so a plan with no code is one such an event cannot resolve — which is a
     * fact worth being able to see rather than one to forbid.
     *
     * <p>This replaced a {@code PlanTier} enum of {@code ESSENTIAL} / {@code PLUS} / {@code FAMILY},
     * which was the second half of item 51's report — the tier codes differed as well as the names.
     * <b>A {@link String} rather than an enum on purpose</b>: Abofonsa can publish a fourth tier
     * whenever it likes, and an enum would refuse to store the one value the sync most needs to
     * carry across.
     */
    @Size(max = 40)
    @Field("code")
    private String code;

    @Size(max = 40)
    @Field("tier_label")
    private String tierLabel;

    /**
     * The order the published catalogue draws the tiers in, cheapest first.
     *
     * <p>Synced. It replaces the card order that used to be the {@code PlanTier} ordinal, so the
     * console orders plans the way the public site does rather than the way an enum happened to be
     * declared.
     */
    @Field("display_order")
    private Integer displayOrder;

    /**
     * What one month of this plan is worth, in {@link #currency} — this service's own figure.
     *
     * <p><b>Nullable, and null is not zero.</b> A plan the catalogue sync created has no price until
     * an administrator sets one, and that is a gap a reader must be able to see: zero is a price, and
     * a revenue line computed from a zero reads as "nobody is paying" rather than "nobody has said".
     * The same distinction is why {@code WageRate}'s seed deliberately leaves two cells unpriced.
     * {@link net.jojoaddison.service.ServicePlanSummaryService#revenue} already treats a missing
     * price as earning nothing.
     *
     * <p>It carried {@code @NotNull} until 2026-09-08. Dropping it is what lets the sync create a
     * plan at all — {@code DatabaseConfiguration} registers a {@code ValidatingMongoEventListener},
     * so a constraint here is enforced on every save and not only on the REST surface.
     */
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

    public String getCode() {
        return this.code;
    }

    public ServicePlan code(String code) {
        this.setCode(code);
        return this;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Integer getDisplayOrder() {
        return this.displayOrder;
    }

    public ServicePlan displayOrder(Integer displayOrder) {
        this.setDisplayOrder(displayOrder);
        return this;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
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
            ", code='" + getCode() + "'" +
            ", tierLabel='" + getTierLabel() + "'" +
            ", displayOrder=" + getDisplayOrder() +
            ", monthlyPrice=" + getMonthlyPrice() +
            ", currency='" + getCurrency() + "'" +
            ", summary='" + getSummary() + "'" +
            ", featured='" + getFeatured() + "'" +
            "}";
    }
}
