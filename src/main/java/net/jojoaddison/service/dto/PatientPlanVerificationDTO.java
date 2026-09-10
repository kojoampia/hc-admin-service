package net.jojoaddison.service.dto;

/**
 * What was announced to hc-patient, echoed back to the console that asked for it.
 *
 * <p><b>It describes the event, not a state.</b> Nothing was stored — see
 * {@link net.jojoaddison.service.PatientPlanVerificationService} — so there is no record to return
 * and this is deliberately not a resource representation: it has no id, and {@code GET} of it does
 * not exist because there is nothing to get.
 *
 * <p>{@code membershipId} is on here and is <b>not</b> on the wire to hc-patient. It is the support
 * handle {@code DirectoryLink.planMembershipId} holds — the thing a person quotes when asking
 * hc-patient about one subscription — and the console shows it beside the confirmation so an
 * administrator can say which membership they just acted on. Adding it to the published payload is
 * the {@code MembershipID} field item 54 removed; keeping it in this response is not that.
 *
 * @param plan the tier code that was published, as hc-patient sent it
 * @param membershipId hc-patient's own id for the membership this decision concerned, when they gave
 *                     one. Null is a real state: their {@code Membership} carries no {@code @NotNull}
 *                     on the fields that reach us.
 */
public record PatientPlanVerificationDTO(String plan, String membershipId) {}
