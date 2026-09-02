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
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/**
 * A Professional.
 */
@Document(collection = "professional")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Professional implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    private String id;

    @NotNull
    @Field("role")
    private ProfessionalRole role;

    @Size(max = 80)
    @Field("speciality")
    private String speciality;

    @NotNull
    @Size(max = 40)
    @Field("licence_number")
    private String licenceNumber;

    @NotNull
    @Field("verification")
    private VerificationStatus verification;

    @NotNull
    @Field("status")
    private AccountStatus status;

    @Min(value = 0)
    @Field("patient_count")
    private Integer patientCount;

    @Min(value = 0)
    @Field("case_count")
    private Integer caseCount;

    @Min(value = 0)
    @Field("visit_count")
    private Integer visitCount;

    @DecimalMin(value = "0")
    @DecimalMax(value = "5")
    @Field("rating")
    private BigDecimal rating;

    @NotNull
    @Field("joined_on")
    private LocalDate joinedOn;

    @DBRef
    @Field("profile")
    private Profile profile;

    @DBRef
    @Field("assignment")
    @JsonIgnoreProperties(value = { "week", "professional" }, allowSetters = true)
    private Set<ShiftAssignment> assignments = new HashSet<>();

    @DBRef
    @Field("team")
    @JsonIgnoreProperties(value = { "supervisor" }, allowSetters = true)
    private Team team;

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

    /**
     * Where this professional is based, as a {@code GeographicSpace} id.
     *
     * <p><b>Proximity has nothing to measure against without it.</b> A shift knows the space it is
     * in; ranking candidates by how near they are needs the other end of that comparison, and no
     * field on this document supplied one — {@code hub} is a building, and {@code team} carries the
     * spaces the team covers rather than the spaces its members live in.
     *
     * <p>An opaque id, not a {@code @DBRef}, and that is the one thing here that reads as
     * inconsistent: {@code profile}, {@code team} and {@code hub} are all {@code @DBRef}. Three
     * reasons it is not. {@code GeographicSpace} is not a JHipster entity — it appears in no
     * {@code .jhipster/} config and in no {@code jdl/} file — so a relationship to it cannot be
     * expressed in {@code .jhipster/Professional.json}, and a regeneration would drop it in silence.
     * Every other reference to this collection in the service is already an opaque id
     * ({@code Team.geographicSpaceIds}, {@code DutyRoster.geographicSpaceId}). And
     * {@code ProfessionalResource} serialises this entity directly with no DTO, so a {@code @DBRef}
     * would put a whole space — and, once the tree is walked for display, its ancestry — into every
     * row of the directory listing.
     *
     * <p>The console does not send it, so {@code ProfessionalResource.updateProfessional} restores
     * the stored value when a {@code PUT} arrives without one. See the note there: this is the same
     * silent erasure {@code TeamService.restoreGeographicSpaceIds} exists to prevent.
     */
    @Field("home_space_id")
    private String homeSpaceId;

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public String getId() {
        return this.id;
    }

    public Professional id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ProfessionalRole getRole() {
        return this.role;
    }

    public Professional role(ProfessionalRole role) {
        this.setRole(role);
        return this;
    }

    public void setRole(ProfessionalRole role) {
        this.role = role;
    }

    public String getSpeciality() {
        return this.speciality;
    }

    public Professional speciality(String speciality) {
        this.setSpeciality(speciality);
        return this;
    }

    public void setSpeciality(String speciality) {
        this.speciality = speciality;
    }

    public String getLicenceNumber() {
        return this.licenceNumber;
    }

    public Professional licenceNumber(String licenceNumber) {
        this.setLicenceNumber(licenceNumber);
        return this;
    }

    public void setLicenceNumber(String licenceNumber) {
        this.licenceNumber = licenceNumber;
    }

    public VerificationStatus getVerification() {
        return this.verification;
    }

    public Professional verification(VerificationStatus verification) {
        this.setVerification(verification);
        return this;
    }

    public void setVerification(VerificationStatus verification) {
        this.verification = verification;
    }

    public AccountStatus getStatus() {
        return this.status;
    }

    public Professional status(AccountStatus status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public Integer getPatientCount() {
        return this.patientCount;
    }

    public Professional patientCount(Integer patientCount) {
        this.setPatientCount(patientCount);
        return this;
    }

    public void setPatientCount(Integer patientCount) {
        this.patientCount = patientCount;
    }

    public Integer getCaseCount() {
        return this.caseCount;
    }

    public Professional caseCount(Integer caseCount) {
        this.setCaseCount(caseCount);
        return this;
    }

    public void setCaseCount(Integer caseCount) {
        this.caseCount = caseCount;
    }

    public Integer getVisitCount() {
        return this.visitCount;
    }

    public Professional visitCount(Integer visitCount) {
        this.setVisitCount(visitCount);
        return this;
    }

    public void setVisitCount(Integer visitCount) {
        this.visitCount = visitCount;
    }

    public BigDecimal getRating() {
        return this.rating;
    }

    public Professional rating(BigDecimal rating) {
        this.setRating(rating);
        return this;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public LocalDate getJoinedOn() {
        return this.joinedOn;
    }

    public Professional joinedOn(LocalDate joinedOn) {
        this.setJoinedOn(joinedOn);
        return this;
    }

    public void setJoinedOn(LocalDate joinedOn) {
        this.joinedOn = joinedOn;
    }

    public Profile getProfile() {
        return this.profile;
    }

    public void setProfile(Profile profile) {
        this.profile = profile;
    }

    public Professional profile(Profile profile) {
        this.setProfile(profile);
        return this;
    }

    public Set<ShiftAssignment> getAssignments() {
        return this.assignments;
    }

    public void setAssignments(Set<ShiftAssignment> shiftAssignments) {
        if (this.assignments != null) {
            this.assignments.forEach(i -> i.setProfessional(null));
        }
        if (shiftAssignments != null) {
            shiftAssignments.forEach(i -> i.setProfessional(this));
        }
        this.assignments = shiftAssignments;
    }

    public Professional assignments(Set<ShiftAssignment> shiftAssignments) {
        this.setAssignments(shiftAssignments);
        return this;
    }

    public Professional addAssignment(ShiftAssignment shiftAssignment) {
        this.assignments.add(shiftAssignment);
        shiftAssignment.setProfessional(this);
        return this;
    }

    public Professional removeAssignment(ShiftAssignment shiftAssignment) {
        this.assignments.remove(shiftAssignment);
        shiftAssignment.setProfessional(null);
        return this;
    }

    public Team getTeam() {
        return this.team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

    public Professional team(Team team) {
        this.setTeam(team);
        return this;
    }

    public Hub getHub() {
        return this.hub;
    }

    public void setHub(Hub hub) {
        this.hub = hub;
    }

    public Professional hub(Hub hub) {
        this.setHub(hub);
        return this;
    }

    public Boolean getIsArchived() {
        return this.isArchived;
    }

    public Professional isArchived(Boolean isArchived) {
        this.setIsArchived(isArchived);
        return this;
    }

    public void setIsArchived(Boolean isArchived) {
        this.isArchived = isArchived;
    }

    public String getHomeSpaceId() {
        return this.homeSpaceId;
    }

    public Professional homeSpaceId(String homeSpaceId) {
        this.setHomeSpaceId(homeSpaceId);
        return this;
    }

    public void setHomeSpaceId(String homeSpaceId) {
        this.homeSpaceId = homeSpaceId;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Professional)) {
            return false;
        }
        return getId() != null && getId().equals(((Professional) o).getId());
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Professional{" +
            "id=" + getId() +
            ", role='" + getRole() + "'" +
            ", speciality='" + getSpeciality() + "'" +
            ", licenceNumber='" + getLicenceNumber() + "'" +
            ", verification='" + getVerification() + "'" +
            ", status='" + getStatus() + "'" +
            ", patientCount=" + getPatientCount() +
            ", caseCount=" + getCaseCount() +
            ", visitCount=" + getVisitCount() +
            ", rating=" + getRating() +
            ", joinedOn='" + getJoinedOn() + "'" +
            ", isArchived='" + getIsArchived() + "'" +
            ", homeSpaceId='" + getHomeSpaceId() + "'" +
            "}";
    }
}
