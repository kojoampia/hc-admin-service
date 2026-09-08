package net.jojoaddison.service.dto;

import java.util.List;

/**
 * What one run of the plan catalogue sync did.
 *
 * <p>Modelled on {@code DirectoryReconciliationDTO}: counts, plus the codes behind each count, so a
 * reader of {@code POST /api/service-plans/sync} can tell an idle run from a run that never dialled
 * without going to the log. A bare {@code 204} would make "Abofonsa is down" and "Abofonsa published
 * exactly what we already hold" the same answer, and those want opposite responses.
 *
 * @param reached whether the published catalogue was read at all. {@code false} means the local copy
 *     is untouched — every other field is zero or empty and says nothing about the catalogue.
 * @param configured whether this deployment is set up to read Abofonsa at all. Read it together with
 *     {@code reached}: {@code reached: false, configured: false} means nothing was dialled, and
 *     {@code reached: false, configured: true} means Abofonsa was dialled and did not answer. This
 *     repo has already paid for collapsing those two — backlog item 24, where the console told an
 *     administrator the roster service was down whenever a local switch was off, which sends the
 *     reader to the wrong machine.
 * @param published how many tiers Abofonsa published.
 * @param created plans this service did not hold and now does, priced by nobody yet.
 * @param updated plans whose name or ordering moved to match the published catalogue.
 * @param unchanged plans that already agreed.
 * @param unpublishedCodes codes this service holds that Abofonsa no longer publishes. Left alone —
 *     patients reference plans, so withdrawing one is an administrator's decision and never a
 *     consequence of a third party editing a page.
 * @param unpricedCodes plans with no {@code monthlyPrice}, which is the state every created plan is
 *     in. The dashboard reports them as earning nothing, which is true and is not the same as "this
 *     plan is free".
 * @param refusedCodes published tiers this service would not store — a blank name, or one longer
 *     than {@code ServicePlan.name} allows. Reported rather than thrown: one malformed tier must not
 *     abort the run and leave the tiers after it in the list unreconciled.
 */
public record PlanCatalogueSyncDTO(
    boolean reached,
    boolean configured,
    int published,
    int created,
    int updated,
    int unchanged,
    List<String> unpublishedCodes,
    List<String> unpricedCodes,
    List<String> refusedCodes
) {
    /**
     * The answer when nothing was written, with {@code configured} carrying which of the two reasons
     * it was.
     */
    public static PlanCatalogueSyncDTO notReached(boolean configured) {
        return new PlanCatalogueSyncDTO(false, configured, 0, 0, 0, 0, List.of(), List.of(), List.of());
    }
}
