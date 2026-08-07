package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * PDF: Category (Name, Description).
 */
@Schema(description = "PDF: Category (Name, Description).")
@Document(collection = "category")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Category implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Size(max = 60)
    @Field("name")
    private String name;

    @Size(max = 200)
    @Field("description")
    private String description;

    @Size(max = 30)
    @Field("icon_key")
    private String iconKey;

    @DBRef
    @Field("activity")
    @JsonIgnoreProperties(value = { "category" }, allowSetters = true)
    private Set<ServiceActivity> activities = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Category id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public Category name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public Category description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIconKey() {
        return this.iconKey;
    }

    public Category iconKey(String iconKey) {
        this.setIconKey(iconKey);
        return this;
    }

    public void setIconKey(String iconKey) {
        this.iconKey = iconKey;
    }

    public Set<ServiceActivity> getActivities() {
        return this.activities;
    }

    public void setActivities(Set<ServiceActivity> serviceActivities) {
        if (this.activities != null) {
            this.activities.forEach(i -> i.setCategory(null));
        }
        if (serviceActivities != null) {
            serviceActivities.forEach(i -> i.setCategory(this));
        }
        this.activities = serviceActivities;
    }

    public Category activities(Set<ServiceActivity> serviceActivities) {
        this.setActivities(serviceActivities);
        return this;
    }

    public Category addActivity(ServiceActivity serviceActivity) {
        this.activities.add(serviceActivity);
        serviceActivity.setCategory(this);
        return this;
    }

    public Category removeActivity(ServiceActivity serviceActivity) {
        this.activities.remove(serviceActivity);
        serviceActivity.setCategory(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Category)) {
            return false;
        }
        return getId() != null && getId().equals(((Category) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Category{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", iconKey='" + getIconKey() + "'" +
            "}";
    }
}
