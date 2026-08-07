package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A bookable, priced catalogue line. Named ServiceActivity to keep it
 * distinct from CareActivity below — the PDF uses \"Activity\" for both
 * senses and JDL has no namespaces.
 */
@Schema(
    description = "A bookable, priced catalogue line. Named ServiceActivity to keep it\ndistinct from CareActivity below — the PDF uses \"Activity\" for both\nsenses and JDL has no namespaces."
)
@Document(collection = "service_activity")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ServiceActivity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Size(max = 100)
    @Field("name")
    private String name;

    @NotNull
    @Size(max = 40)
    @Field("unit")
    private String unit;

    @NotNull
    @DecimalMin(value = "0")
    @Field("unit_price")
    private BigDecimal unitPrice;

    @Size(max = 40)
    @Field("duration")
    private String duration;

    @NotNull
    @Field("published")
    private Boolean published;

    @DBRef
    @Field("category")
    @JsonIgnoreProperties(value = { "activities" }, allowSetters = true)
    private Category category;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public ServiceActivity id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public ServiceActivity name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUnit() {
        return this.unit;
    }

    public ServiceActivity unit(String unit) {
        this.setUnit(unit);
        return this;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getUnitPrice() {
        return this.unitPrice;
    }

    public ServiceActivity unitPrice(BigDecimal unitPrice) {
        this.setUnitPrice(unitPrice);
        return this;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getDuration() {
        return this.duration;
    }

    public ServiceActivity duration(String duration) {
        this.setDuration(duration);
        return this;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public Boolean getPublished() {
        return this.published;
    }

    public ServiceActivity published(Boolean published) {
        this.setPublished(published);
        return this;
    }

    public void setPublished(Boolean published) {
        this.published = published;
    }

    public Category getCategory() {
        return this.category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public ServiceActivity category(Category category) {
        this.setCategory(category);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ServiceActivity)) {
            return false;
        }
        return getId() != null && getId().equals(((ServiceActivity) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ServiceActivity{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", unit='" + getUnit() + "'" +
            ", unitPrice=" + getUnitPrice() +
            ", duration='" + getDuration() + "'" +
            ", published='" + getPublished() + "'" +
            "}";
    }
}
