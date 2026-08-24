package net.jojoaddison.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.VerificationStatus;

/**
 * What a caller may say when recording a verification.
 *
 * <p><strong>{@code recordedAt} and {@code recordedBy} are deliberately absent.</strong> They are
 * stamped by {@code ProfessionalVerificationService} from the clock and the token, and keeping them
 * off this shape is what makes it impossible to send them — stronger than overwriting them,
 * because there is nothing on the wire to overwrite. It is the rule CLAUDE.md records for
 * {@code Message.readAt} and {@code Task.closedAt}: written only by the server, carried on no DTO,
 * so no client can make them disagree with the state they describe.
 *
 * <p>The reason it matters more here than for a read receipt: a verification history whose times and
 * authors the caller supplies is not evidence that anybody verified anything, and evidence is the
 * only thing this collection is for.
 *
 * <p>This is the one request shape in the directory that is not the domain object. The others expose
 * their entities directly and are none the worse for it — but none of them has a field that must not
 * be settable.
 *
 * @param professionalId the professional this decision is about
 * @param status the decision
 * @param method how it was checked — "Licence register", "Document review"
 * @param reference the register entry, ticket or document it rests on
 * @param note free text for whoever reads the history later
 * @param expiresOn when this verification lapses, if it does
 */
public record ProfessionalVerificationRequest(
    @NotNull @Size(max = 100) String professionalId,
    @NotNull VerificationStatus status,
    @Size(max = 60) String method,
    @Size(max = 120) String reference,
    @Size(max = 500) String note,
    LocalDate expiresOn
) {}
