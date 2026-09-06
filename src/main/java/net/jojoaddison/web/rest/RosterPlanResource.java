package net.jojoaddison.web.rest;

import jakarta.validation.Valid;
import net.jojoaddison.service.RoundPlanningService;
import net.jojoaddison.service.dto.RoundPlanDtos.PlanReport;
import net.jojoaddison.service.dto.RoundPlanDtos.PlanRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Planning: staff a day's rounds and file them with the roster of record.
 *
 * <p>The one write in this service that leaves it. hc-professional owns the roster since the
 * 2026-09 migration, so planning is a cross-stack call rather than a local save — which is the whole
 * reason {@link PlanReport#rosterServiceReachable()} exists and why the console has an outage state
 * on the roster screen.
 *
 * <p><b>{@code POST} and not {@code GET}, although a caller might reasonably want to see who
 * <em>would</em> be chosen.</b> There is no dry run, deliberately: a preview that ranked candidates
 * without filing would be a second code path making the same decision, and the two would diverge the
 * first time either changed. If a preview is wanted it should return the same report this does, from
 * this method, with the write suppressed — not from a second one.
 *
 * <p><b>No collection, no {@code GET} by id, no {@code DELETE}.</b> A planning run is not a stored
 * resource here; the round it produces is stored by {@code professionalservice} and is read back
 * from there. {@code PaginationIT} sweeps single-segment {@code /api} paths for a {@code Page} and
 * this path has no list handler for it to find, which is correct rather than an omission.
 *
 * <p><b>Admin only, and stated twice on purpose — the only {@code @PreAuthorize} in this
 * service.</b> The blanket {@code POST /api/**} rule in {@code SecurityConfiguration} already
 * decides this, and the house convention is that authorization lives there and nowhere else, so the
 * duplication is a deliberate exception rather than an oversight. Two reasons for it, and only the
 * second is about defence in depth.
 *
 * <p>First, this endpoint spends the <em>caller's own token</em> on another stack: a reader working
 * out what a compromised account here can reach next door should not have to open a second file.
 * Second, every {@code *ResourceIT} in this package runs with {@code addFilters = false}, which
 * takes the whole filter chain out of the request path — method security is the only rule such a
 * test cannot bypass, and this is the one endpoint where that is worth having.
 *
 * <p>The cost is that the two can drift apart, and the mitigation is that they cannot drift
 * <em>quietly</em>: {@code ApiAuthorizationIT.planningIsAdminOnly} exercises the matcher and
 * {@code RosterPlanResourceIT} only passes because its mock user holds {@code ROLE_ADMIN}. Relaxing
 * either one alone turns the other red.
 */
@RestController
@RequestMapping("/api/roster-plans")
public class RosterPlanResource {

    private static final Logger LOG = LoggerFactory.getLogger(RosterPlanResource.class);

    private final RoundPlanningService roundPlanningService;

    public RosterPlanResource(RoundPlanningService roundPlanningService) {
        this.roundPlanningService = roundPlanningService;
    }

    /**
     * {@code POST /api/roster-plans} : staff the requested rounds and file the ones that could be
     * staffed.
     *
     * <p><b>Always {@code 200}, even when the roster service is unreachable</b>, and the flag in the
     * body carries that. A {@code 502} would be the tidier-looking answer and it would throw away
     * the record of the rounds that <em>were</em> filed before the failure — leaving an
     * administrator to guess whether to run it again, which is how a round gets filed twice.
     *
     * @param request the date and the rounds wanted on it.
     * @return {@code 200 (OK)} with one outcome per requested round.
     */
    @PostMapping("")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<PlanReport> plan(@Valid @RequestBody PlanRequest request) {
        // Counts and a date. The request carries customer ids, and this service holds no patient
        // data by design — putting them in a log line would be the one place it did.
        LOG.debug("REST request to plan {} round(s) for {}", request.rounds().size(), request.date());
        return ResponseEntity.ok(roundPlanningService.plan(request));
    }
}
