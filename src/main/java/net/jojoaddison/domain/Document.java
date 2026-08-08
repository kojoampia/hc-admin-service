package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * PDF: Document (Name, Description, URL, Timestamp).
 */
@Schema(description = "PDF: Document (Name, Description, URL, Timestamp).")
// Fully qualified, and the matching import is deliberately absent: a compilation unit cannot import
// a type whose simple name it declares, and this entity is called Document. Same in Patient and
// Vendor, which hold Set<Document> — there the import would also shadow the domain class.
@org.springframework.data.mongodb.core.mapping.Document(collection = "jhi_document")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Document implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Size(max = 120)
    @Field("name")
    private String name;

    @Size(max = 300)
    @Field("description")
    private String description;

    @NotNull
    @Size(max = 400)
    @Field("url")
    private String url;

    @NotNull
    @Field("uploaded_at")
    private Instant uploadedAt;

    @DBRef
    @Field("patient")
    @JsonIgnoreProperties(value = { "profile", "angel", "documents", "careActivities", "plan", "clinicalLead", "hub" }, allowSetters = true)
    private Patient patient;

    @DBRef
    @Field("vendor")
    @JsonIgnoreProperties(value = { "documents" }, allowSetters = true)
    private Vendor vendor;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Document id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public Document name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public Document description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return this.url;
    }

    public Document url(String url) {
        this.setUrl(url);
        return this;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Instant getUploadedAt() {
        return this.uploadedAt;
    }

    public Document uploadedAt(Instant uploadedAt) {
        this.setUploadedAt(uploadedAt);
        return this;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Patient getPatient() {
        return this.patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public Document patient(Patient patient) {
        this.setPatient(patient);
        return this;
    }

    public Vendor getVendor() {
        return this.vendor;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    public Document vendor(Vendor vendor) {
        this.setVendor(vendor);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Document)) {
            return false;
        }
        return getId() != null && getId().equals(((Document) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Document{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", url='" + getUrl() + "'" +
            ", uploadedAt='" + getUploadedAt() + "'" +
            "}";
    }
}
