package net.jojoaddison.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.*;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.domain.enumeration.AccountStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A Patient.
 */
// Fully qualified: importing the annotation would shadow net.jojoaddison.domain.Document, which the
// documents relationship below refers to by simple name. See the note on the Document entity.
@org.springframework.data.mongodb.core.mapping.Document(collection = "patient")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Patient implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    /**
     * The identity link to the subject's gateway Account — the same identifier {@link Profile#getAccountId()}
     * carries, on the record hc-admin owns about a patient.
     *
     * <p>A plain String, not a relationship: the account lives in a gateway's own database — for a patient,
     * <b>hc-patient's</b>, since patients register there and this stack's gateway never issues
     * {@code ROLE_PATIENT} — and this service cannot join across that boundary.
     *
     * <p><b>The value is the account's {@code User.id}, never the login and never hc-patient's
     * {@code patientId}</b> — the estate decision of 2026-09-17 ({@code account.id = profile.accountId}),
     * applied to {@code Patient} by backlog item 115. The three are all opaque strings and a join on the
     * wrong one matches nothing silently, which is why this paragraph names the value space instead of
     * trusting the field name. It is required for the same reason {@code Profile.accountId} is: a patient
     * with no account id is a record that cannot be joined to the identity that owns it, on any screen or
     * any stream.
     *
     * <p>Where the value comes from, in order of arrival: hc-patient's publishers put
     * {@code subject.accountId} on every frame since their item 72 refactor (their {@code b6894dc},
     * 2026-09-24), item 130 reads it onto {@code directory_link.account_id}, and
     * {@code DirectoryProjectionService} stamps it onto the record it creates.
     * {@code PatientAccountIdBackfillMigration} resolves rows written before this field existed from that
     * same link column — and <b>reports what it cannot resolve rather than defaulting it</b>, because a
     * fabricated account id is a join key that silently matches nothing (item 26's defect with a new name).
     *
     * <p>⚠ <b>Seeded values are fixture identifiers, deliberately.</b> hc-patient's gateway mints its
     * account ids at seed time ({@code UUID.randomUUID()}-style, no pinned-id seeding exists there), so no
     * fixture on any stack holds an id this seed could reference — the same transitional state
     * {@code Profile.accountId}'s javadoc records for the nine clinician rows. The seed says so by shape
     * ({@code fixture-account-*}) and {@code DevelopmentDataInitializerTest} pins it, so a reader never
     * mistakes a seeded value for one that resolves on a sibling stack.
     */
    @NotNull
    @Size(max = 60)
    @Field("account_id")
    private String accountId;

    @NotNull
    @Field("status")
    private AccountStatus status;

    @NotNull
    @Field("joined_on")
    private LocalDate joinedOn;

    @Field("last_active_on")
    private LocalDate lastActiveOn;

    @Min(value = 0)
    @Field("case_count")
    private Integer caseCount;

    @DBRef
    @Field("profile")
    private Profile profile;

    @DBRef
    @Field("angel")
    private Angel angel;

    @DBRef
    @Field("document")
    @JsonIgnoreProperties(value = { "patient", "vendor" }, allowSetters = true)
    private Set<Document> documents = new HashSet<>();

    @DBRef
    @Field("careActivity")
    @JsonIgnoreProperties(value = { "patient" }, allowSetters = true)
    private Set<CareActivity> careActivities = new HashSet<>();

    @DBRef
    @Field("plan")
    @JsonIgnoreProperties(value = { "features" }, allowSetters = true)
    private ServicePlan plan;

    @DBRef
    @Field("clinicalLead")
    @JsonIgnoreProperties(value = { "profile", "assignments", "team", "hub" }, allowSetters = true)
    private Professional clinicalLead;

    @DBRef
    @Field("hub")
    @JsonIgnoreProperties(value = { "address" }, allowSetters = true)
    private Hub hub;

    /**
     * Hidden from the console's directory listings, but not deleted.
     *
     * <p>Nullable on purpose, and absent on every document written before this field existed. The
     * list endpoint's "not archived" filter is therefore {@code $ne: true} rather than
     * {@code false} — a document with no value at all has to read as not archived, or the whole
     * directory disappears the day this ships.
     */
    @Field("is_archived")
    private Boolean isArchived;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Patient id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAccountId() {
        return this.accountId;
    }

    public Patient accountId(String accountId) {
        this.setAccountId(accountId);
        return this;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public AccountStatus getStatus() {
        return this.status;
    }

    public Patient status(AccountStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public LocalDate getJoinedOn() {
        return this.joinedOn;
    }

    public Patient joinedOn(LocalDate joinedOn) {
        this.setJoinedOn(joinedOn);
        return this;
    }

    public void setJoinedOn(LocalDate joinedOn) {
        this.joinedOn = joinedOn;
    }

    public LocalDate getLastActiveOn() {
        return this.lastActiveOn;
    }

    public Patient lastActiveOn(LocalDate lastActiveOn) {
        this.setLastActiveOn(lastActiveOn);
        return this;
    }

    public void setLastActiveOn(LocalDate lastActiveOn) {
        this.lastActiveOn = lastActiveOn;
    }

    public Integer getCaseCount() {
        return this.caseCount;
    }

    public Patient caseCount(Integer caseCount) {
        this.setCaseCount(caseCount);
        return this;
    }

    public void setCaseCount(Integer caseCount) {
        this.caseCount = caseCount;
    }

    public Profile getProfile() {
        return this.profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public Patient profile(Profile profile) {
        this.setProfile(profile);
        return this;
    }

    public Angel getAngel() {
        return this.angel;
    }

    public void setAngel(Angel angel) {
        this.angel = angel;
    }

    public Patient angel(Angel angel) {
        this.setAngel(angel);
        return this;
    }

    public Set<Document> getDocuments() {
        return this.documents;
    }

    public void setDocuments(Set<Document> documents) {
        if (this.documents != null) {
            this.documents.forEach(i -> i.setPatient(null));
        }
        if (documents != null) {
            documents.forEach(i -> i.setPatient(this));
        }
        this.documents = documents;
    }

    public Patient documents(Set<Document> documents) {
        this.setDocuments(documents);
        return this;
    }

    public Patient addDocument(Document document) {
        this.documents.add(document);
        document.setPatient(this);
        return this;
    }

    public Patient removeDocument(Document document) {
        this.documents.remove(document);
        document.setPatient(null);
        return this;
    }

    public Set<CareActivity> getCareActivities() {
        return this.careActivities;
    }

    public void setCareActivities(Set<CareActivity> careActivities) {
        if (this.careActivities != null) {
            this.careActivities.forEach(i -> i.setPatient(null));
        }
        if (careActivities != null) {
            careActivities.forEach(i -> i.setPatient(this));
        }
        this.careActivities = careActivities;
    }

    public Patient careActivities(Set<CareActivity> careActivities) {
        this.setCareActivities(careActivities);
        return this;
    }

    public Patient addCareActivity(CareActivity careActivity) {
        this.careActivities.add(careActivity);
        careActivity.setPatient(this);
        return this;
    }

    public Patient removeCareActivity(CareActivity careActivity) {
        this.careActivities.remove(careActivity);
        careActivity.setPatient(null);
        return this;
    }

    public ServicePlan getPlan() {
        return this.plan;
    }

    public void setPlan(ServicePlan servicePlan) {
        this.plan = servicePlan;
    }

    public Patient plan(ServicePlan servicePlan) {
        this.setPlan(servicePlan);
        return this;
    }

    public Professional getClinicalLead() {
        return this.clinicalLead;
    }

    public void setClinicalLead(Professional professional) {
        this.clinicalLead = professional;
    }

    public Patient clinicalLead(Professional professional) {
        this.setClinicalLead(professional);
        return this;
    }

    public Hub getHub() {
        return this.hub;
    }

    public void setHub(Hub hub) {
        this.hub = hub;
    }

    public Patient hub(Hub hub) {
        this.setHub(hub);
        return this;
    }

    public Boolean getIsArchived() {
        return this.isArchived;
    }

    public Patient isArchived(Boolean isArchived) {
        this.setIsArchived(isArchived);
        return this;
    }

    public void setIsArchived(Boolean isArchived) {
        this.isArchived = isArchived;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Patient)) {
            return false;
        }
        return getId() != null && getId().equals(((Patient) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Patient{" +
            "id=" + getId() +
            ", accountId='" + getAccountId() + "'" +
            ", status='" + getStatus() + "'" +
            ", joinedOn='" + getJoinedOn() + "'" +
            ", lastActiveOn='" + getLastActiveOn() + "'" +
            ", caseCount=" + getCaseCount() +
            ", isArchived='" + getIsArchived() + "'" +
            "}";
    }
}
