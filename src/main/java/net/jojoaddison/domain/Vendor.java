package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.domain.enumeration.AccountStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A Vendor.
 */
// Fully qualified: importing the annotation would shadow net.jojoaddison.domain.Document, which the
// documents relationship below refers to by simple name. See the note on the Document entity.
@org.springframework.data.mongodb.core.mapping.Document(collection = "vendor")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Vendor implements Serializable {

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
    @Field("category")
    private String category;

    @Size(max = 200)
    @Field("service_summary")
    private String serviceSummary;

    @Size(max = 80)
    @Field("contact_name")
    private String contactName;

    @Size(max = 24)
    @Field("phone")
    private String phone;

    @Size(max = 120)
    @Field("email")
    private String email;

    @Size(max = 60)
    @Field("city")
    private String city;

    @NotNull
    @Field("status")
    private AccountStatus status;

    @Size(max = 80)
    @Field("contract_note")
    private String contractNote;

    @Field("contract_renews_on")
    private LocalDate contractRenewsOn;

    @Min(value = 0)
    @Field("order_count")
    private Integer orderCount;

    @DecimalMin(value = "0")
    @Field("spend_to_date")
    private BigDecimal spendToDate;

    @DecimalMin(value = "0")
    @DecimalMax(value = "5")
    @Field("rating")
    private BigDecimal rating;

    @DBRef
    @Field("document")
    @JsonIgnoreProperties(value = { "patient", "vendor" }, allowSetters = true)
    private Set<Document> documents = new HashSet<>();

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Vendor id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public Vendor name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return this.category;
    }

    public Vendor category(String category) {
        this.setCategory(category);
        return this;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getServiceSummary() {
        return this.serviceSummary;
    }

    public Vendor serviceSummary(String serviceSummary) {
        this.setServiceSummary(serviceSummary);
        return this;
    }

    public void setServiceSummary(String serviceSummary) {
        this.serviceSummary = serviceSummary;
    }

    public String getContactName() {
        return this.contactName;
    }

    public Vendor contactName(String contactName) {
        this.setContactName(contactName);
        return this;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getPhone() {
        return this.phone;
    }

    public Vendor phone(String phone) {
        this.setPhone(phone);
        return this;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return this.email;
    }

    public Vendor email(String email) {
        this.setEmail(email);
        return this;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCity() {
        return this.city;
    }

    public Vendor city(String city) {
        this.setCity(city);
        return this;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public AccountStatus getStatus() {
        return this.status;
    }

    public Vendor status(AccountStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public String getContractNote() {
        return this.contractNote;
    }

    public Vendor contractNote(String contractNote) {
        this.setContractNote(contractNote);
        return this;
    }

    public void setContractNote(String contractNote) {
        this.contractNote = contractNote;
    }

    public LocalDate getContractRenewsOn() {
        return this.contractRenewsOn;
    }

    public Vendor contractRenewsOn(LocalDate contractRenewsOn) {
        this.setContractRenewsOn(contractRenewsOn);
        return this;
    }

    public void setContractRenewsOn(LocalDate contractRenewsOn) {
        this.contractRenewsOn = contractRenewsOn;
    }

    public Integer getOrderCount() {
        return this.orderCount;
    }

    public Vendor orderCount(Integer orderCount) {
        this.setOrderCount(orderCount);
        return this;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }

    public BigDecimal getSpendToDate() {
        return this.spendToDate;
    }

    public Vendor spendToDate(BigDecimal spendToDate) {
        this.setSpendToDate(spendToDate);
        return this;
    }

    public void setSpendToDate(BigDecimal spendToDate) {
        this.spendToDate = spendToDate;
    }

    public BigDecimal getRating() {
        return this.rating;
    }

    public Vendor rating(BigDecimal rating) {
        this.setRating(rating);
        return this;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public Set<Document> getDocuments() {
        return this.documents;
    }

    public void setDocuments(Set<Document> documents) {
        if (this.documents != null) {
            this.documents.forEach(i -> i.setVendor(null));
        }
        if (documents != null) {
            documents.forEach(i -> i.setVendor(this));
        }
        this.documents = documents;
    }

    public Vendor documents(Set<Document> documents) {
        this.setDocuments(documents);
        return this;
    }

    public Vendor addDocument(Document document) {
        this.documents.add(document);
        document.setVendor(this);
        return this;
    }

    public Vendor removeDocument(Document document) {
        this.documents.remove(document);
        document.setVendor(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Vendor)) {
            return false;
        }
        return getId() != null && getId().equals(((Vendor) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Vendor{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", category='" + getCategory() + "'" +
            ", serviceSummary='" + getServiceSummary() + "'" +
            ", contactName='" + getContactName() + "'" +
            ", phone='" + getPhone() + "'" +
            ", email='" + getEmail() + "'" +
            ", city='" + getCity() + "'" +
            ", status='" + getStatus() + "'" +
            ", contractNote='" + getContractNote() + "'" +
            ", contractRenewsOn='" + getContractRenewsOn() + "'" +
            ", orderCount=" + getOrderCount() +
            ", spendToDate=" + getSpendToDate() +
            ", rating=" + getRating() +
            "}";
    }
}
