package net.jojoaddison.service.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * What a caller may say when verifying a patient's plan choice.
 *
 * <p>One field, and everything else about the event is read from the stored link. That is the point:
 * the tier that goes on the wire is the one hc-patient told us about, so a caller cannot verify a
 * patient onto a plan they did not choose — which is precisely what the payload's {@code plan} field
 * exists to let hc-patient detect, and it would be an odd contract that let this end fabricate the
 * value being checked.
 *
 * <p><b>There is no status on this shape either</b>, for the reason
 * {@link net.jojoaddison.broker.PlanVerificationEvent} gives: there is no refusal path, so a decision
 * field could hold only one value. When one is built it lands here.
 *
 * @param linkId the {@code directory_link} carrying the plan choice being verified. The link rather
 *               than the patient, because a patient does not hold the choice — item 22 keeps it on
 *               the link beside {@code Patient} rather than on it, so the link is the thing that has
 *               a tier, a membership id and the address the event is keyed on.
 */
public record PatientPlanVerificationRequest(@NotNull @Size(max = 100) String linkId) {}
