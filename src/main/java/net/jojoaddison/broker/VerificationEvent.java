package net.jojoaddison.broker;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * Announced when a professional's verification is decided.
 *
 * <p>Published to the {@code professional-verification} topic, which exists so hc-professional can
 * consume these when it grows its own credentialing view. That is the reason hc-admin records the
 * decision rather than consuming one: nothing there publishes today, so a console that waited would
 * read zero on arrival — indistinguishable from broken. The event is what turns the eventual
 * reconciliation into a subscription rather than a migration.
 *
 * <p><b>It carries the decision, not the professional.</b> A consumer that needs the person fetches
 * them by {@code professionalId}; putting a snapshot of the record on the bus would publish a copy
 * that is stale from the moment it is sent, and this service is not the system of record for
 * anything about a professional except this.
 *
 * <p>{@code licenceNumber} is the exception, and is here because it is the identifier the other
 * stacks key on — a consumer holding only an id from a service it does not share a database with
 * cannot resolve it, which is the shape of the cross-service 404 CLAUDE.md records for
 * {@code Profile.accountId}.
 *
 * <p><b>It holds no domain types, and that is enforced.</b> {@code TechnicalStructureTest} does not
 * list {@code ..broker..} as a layer, so anything here that reaches into {@code ..domain..} fails
 * the ArchUnit rule — which is what {@code RosterEvent} beside it is also obeying, and why the
 * conversion from a saved row lives in {@code ProfessionalVerificationService} rather than in a
 * static factory here. The practical effect is the right one anyway: an event is a wire format, and
 * a wire format that knows the shape of a document changes whenever the document does.
 */
public class VerificationEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String verificationId;
    private String professionalId;
    private String licenceNumber;
    private String status;
    private Instant recordedAt;
    private String recordedBy;

    public VerificationEvent() {}

    public VerificationEvent(
        String verificationId,
        String professionalId,
        String licenceNumber,
        String status,
        Instant recordedAt,
        String recordedBy
    ) {
        this.verificationId = verificationId;
        this.professionalId = professionalId;
        this.licenceNumber = licenceNumber;
        this.status = status;
        this.recordedAt = recordedAt;
        this.recordedBy = recordedBy;
    }

    public String getVerificationId() {
        return verificationId;
    }

    public void setVerificationId(String verificationId) {
        this.verificationId = verificationId;
    }

    public String getProfessionalId() {
        return professionalId;
    }

    public void setProfessionalId(String professionalId) {
        this.professionalId = professionalId;
    }

    public String getLicenceNumber() {
        return licenceNumber;
    }

    public void setLicenceNumber(String licenceNumber) {
        this.licenceNumber = licenceNumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
    }

    @Override
    public String toString() {
        return (
            "VerificationEvent{" +
            "verificationId='" +
            verificationId +
            "', professionalId='" +
            professionalId +
            "', licenceNumber='" +
            licenceNumber +
            "', status='" +
            status +
            "', recordedAt='" +
            recordedAt +
            "', recordedBy='" +
            recordedBy +
            "'}"
        );
    }
}
