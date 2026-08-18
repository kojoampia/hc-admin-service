package net.jojoaddison.web.rest;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.enumeration.EarningsGranularity;
import net.jojoaddison.service.CurrentProfessionalService;
import net.jojoaddison.service.ShiftValuationService;
import net.jojoaddison.service.dto.ProfessionalEarningsDTO;
import net.jojoaddison.service.dto.ProfessionalShiftDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a professional can see about their own work: their roster, and what it has come to.
 *
 * <p><b>Why this is a separate surface from {@link ProfessionalEarningsResource} rather than a
 * relaxed permission on it.</b> That resource takes the professional's id from the path, which is
 * correct for an administrator looking at somebody: choosing whose figures to read is the whole
 * point of the screen. Opening the same endpoint to clinicians would have made the id a way to read
 * a colleague's pay by editing a URL — the authority check would pass, because the caller is
 * entitled to <em>an</em> earnings figure, just not that one. Here there is no id to edit. The
 * subject of the read is the authenticated caller and there is no parameter that can change it.
 *
 * <p>This is deliberately not the shape of {@code GET /api/duty-rosters/patient/{patientId}}, which
 * takes the subject from the path and checks only that the caller holds {@code ROLE_PATIENT}.
 *
 * <p><b>Read-only, and structurally so.</b> Wage rates are the administrator's to set — see {@code
 * WageRateResource}, which stays admin-gated. Nothing here exposes a rate as a rate: a professional
 * sees the money their own shifts came to, not the table that priced them. There is no write
 * mapping on this class for the same reason.
 *
 * <p>Path note: {@code /me/...} is two literal segments, which outranks {@code /{id}/earnings} in
 * path-pattern matching, so these mappings do not compete with that resource's despite sharing a
 * prefix. A professional whose id was literally {@code me} would still be reachable there by
 * administrators, and unreachable here by anyone but themselves.
 */
@RestController
@RequestMapping("/api/professionals/me")
public class ProfessionalSelfResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProfessionalSelfResource.class);

    /**
     * How far either side of today the roster reaches when the caller names no window. Backwards far
     * enough to cover the month just paid, forwards far enough to cover the roster already
     * published.
     */
    private static final int DEFAULT_ROSTER_DAYS_BACK = 30;
    private static final int DEFAULT_ROSTER_DAYS_FORWARD = 30;

    private final CurrentProfessionalService currentProfessionalService;

    private final ShiftValuationService shiftValuationService;

    private final Clock clock;

    public ProfessionalSelfResource(
        CurrentProfessionalService currentProfessionalService,
        ShiftValuationService shiftValuationService,
        Clock clock
    ) {
        this.currentProfessionalService = currentProfessionalService;
        this.shiftValuationService = shiftValuationService;
        this.clock = clock;
    }

    /**
     * {@code GET  /professionals/me/earnings} : what the caller has earned, and the series behind it.
     *
     * @param granularity {@code DAILY}, {@code WEEKLY} or {@code MONTHLY}; defaults to monthly.
     * @param from start of the window; defaults to a span that fills the series.
     * @param to end of the window; defaults to today, and is clipped to the last date that can have
     *     been worked. The response reports where the window actually ended.
     * @return {@code 200 (OK)} with the caller's earnings, or {@code 404 (Not Found)} when the
     *     caller has no professional record — an account exists, the clinical record it would point
     *     at does not.
     */
    @GetMapping("/earnings")
    public ResponseEntity<ProfessionalEarningsDTO> getOwnEarnings(
        @RequestParam(name = "granularity", required = false) EarningsGranularity granularity,
        @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LOG.debug("REST request for the caller's own earnings by {} from {} to {}", granularity, from, to);
        return currentProfessionalService
            .currentProfessional()
            .map(professional -> ResponseEntity.ok(shiftValuationService.earningsFor(professional, granularity, from, to)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * {@code GET  /professionals/me/shifts} : the caller's own roster over a window.
     *
     * <p>Includes off days and shifts still to come, each flagged with whether it counts toward
     * earnings. A schedule that hid the unpaid rows would not be a schedule.
     *
     * @param from start of the window; defaults to 30 days back.
     * @param to end of the window; defaults to 30 days ahead. Not clipped to the payable cutoff —
     *     future shifts are the point of looking.
     * @return {@code 200 (OK)} with the caller's shifts oldest first, or {@code 404 (Not Found)}
     *     when the caller has no professional record.
     */
    @GetMapping("/shifts")
    public ResponseEntity<List<ProfessionalShiftDTO>> getOwnShifts(
        @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        LOG.debug("REST request for the caller's own shifts from {} to {}", from, to);
        LocalDate today = LocalDate.now(clock);
        LocalDate windowStart = from == null ? today.minusDays(DEFAULT_ROSTER_DAYS_BACK) : from;
        LocalDate windowEnd = to == null ? today.plusDays(DEFAULT_ROSTER_DAYS_FORWARD) : to;

        return currentProfessionalService
            .currentProfessional()
            .map(professional -> ResponseEntity.ok(shiftsOf(professional, windowStart, windowEnd)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private List<ProfessionalShiftDTO> shiftsOf(Professional professional, LocalDate from, LocalDate to) {
        // An inverted window is a client bug, not a server error: report nothing rather than let the
        // range query decide what a backwards span means.
        return to.isBefore(from) ? List.of() : shiftValuationService.shiftsFor(professional, from, to);
    }
}
