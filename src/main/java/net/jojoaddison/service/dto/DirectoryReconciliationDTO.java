package net.jojoaddison.service.dto;

/**
 * What a run of {@code DirectoryProjectionService.reconcile()} did.
 *
 * <p>Three numbers rather than a boolean, because the useful answer is almost always "nothing" and a
 * caller has to be able to tell "nothing to do" from "nothing happened". {@code examined} is how many
 * subjects this service knows about from the patient stream, so a zero there says the consumer has
 * never received anything — a different problem entirely from links whose records are all present.
 *
 * @param examined links inspected.
 * @param created local records that were missing and have been re-derived.
 * @param alreadyPresent links whose local record was already there.
 */
public record DirectoryReconciliationDTO(int examined, int created, int alreadyPresent) {}
