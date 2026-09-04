package net.jojoaddison.service.dto;

/**
 * What a run of {@code DirectoryProjectionService.reconcile()} did.
 *
 * <p>Numbers rather than a boolean, because the useful answer is almost always "nothing" and a
 * caller has to be able to tell "nothing to do" from "nothing happened". {@code examined} is how many
 * subjects this service knows about from the patient stream, so a zero there says the consumer has
 * never received anything — a different problem entirely from links whose records are all present.
 *
 * <p>The four add up: {@code examined == created + alreadyPresent + skipped}. {@code skipped} is
 * reported rather than folded into {@code alreadyPresent} because those links have no local record
 * and are <em>supposed</em> not to — a care angel, or somebody hc-patient has erased — and counting
 * them as present would say the opposite of what is true.
 *
 * @param examined links inspected.
 * @param created local records that were missing and have been re-derived.
 * @param alreadyPresent links whose local record was already there.
 * @param skipped links that deliberately keep no local record.
 */
public record DirectoryReconciliationDTO(int examined, int created, int alreadyPresent, int skipped) {}
