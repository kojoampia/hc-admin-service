package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.domain.DutyRoster;
import net.jojoaddison.domain.HCProfile;
import net.jojoaddison.domain.Team;
import net.jojoaddison.domain.UnavailabilityPeriod;
import net.jojoaddison.domain.enumeration.DutyRole;
import net.jojoaddison.domain.enumeration.RoleType;
import net.jojoaddison.domain.enumeration.ShiftStatus;
import net.jojoaddison.repository.DutyRosterRepository;
import net.jojoaddison.repository.HCProfileRepository;
import net.jojoaddison.repository.TeamRepository;
import net.jojoaddison.service.mapper.DutyRosterMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cloud.stream.function.StreamBridge;

/**
 * What {@code DutyRosterService.autoScheduleShifts} does today, pinned before it is rewritten.
 *
 * <p><b>A characterization test, not a specification.</b> Every assertion below records current
 * behaviour so that the planned rewrite has to declare itself. Two of them deliberately pin
 * behaviour that is <b>wrong</b>, and say so — see {@link #doesNotNarrowCandidatesByDutyRole()} and
 * the note on fairness. Deleting or inverting those assertions is the correct way to fix the code;
 * silently changing the behaviour under them is not.
 *
 * <p><b>Why it exists at all, given the code is scheduled for deletion.</b> The plan moves the duty
 * roster to hc-professional and rewrites this as a planning service that writes across the stack
 * boundary. The constraints it applies — geography, availability, no double-booking, fairness —
 * are meant to be carried over, and until now nothing recorded what they actually do. This is the
 * list the new planner has to satisfy, written down while the old one still runs.
 *
 * <p>A unit test over mocked repositories. The logic is a filter chain and a sort; a database round
 * trip adds nothing to it, and needing a container is why there was no test here.
 */
class DutyRosterAutoScheduleTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);
    private static final String SPACE = "space-accra-north";

    private final DutyRosterRepository rosters = mock(DutyRosterRepository.class);
    private final HCProfileRepository profiles = mock(HCProfileRepository.class);
    private final TeamRepository teams = mock(TeamRepository.class);
    private final StreamBridge streamBridge = mock(StreamBridge.class);
    private final DutyRosterMapper mapper = mock(DutyRosterMapper.class);

    private final DutyRosterService service = new DutyRosterService(rosters, mapper, profiles, teams, streamBridge);

    // --- fixtures -------------------------------------------------------------------------------

    private static DutyRoster unassigned(DutyRole duty) {
        return new DutyRoster().id("shift-1").date(WEDNESDAY).duty(duty).geographicSpaceId(SPACE).status(ShiftStatus.UNASSIGNED);
    }

    private static HCProfile candidate(String id) {
        return new HCProfile().id(id).roleType(RoleType.PROFESSIONAL).status(Boolean.TRUE).teamId("team-1");
    }

    private void oneTeamCoversTheSpace() {
        when(teams.findByGeographicSpaceIdsContaining(SPACE)).thenReturn(List.of(new Team().id("team-1")));
    }

    private void candidatesAre(HCProfile... found) {
        when(profiles.findByRoleTypeAndTeamIdInAndStatusTrue(any(), anyList())).thenReturn(List.of(found));
    }

    /** Nobody is already booked and nobody has worked this week, unless a test says otherwise. */
    private void noExistingCommitments() {
        when(rosters.existsByProfessionalIdAndDate(anyString(), any())).thenReturn(false);
        when(rosters.countByProfessionalIdAndDateBetween(anyString(), any(), any())).thenReturn(0);
    }

    private void pending(DutyRoster... shifts) {
        when(rosters.findByDateAndStatus(WEDNESDAY, ShiftStatus.UNASSIGNED)).thenReturn(List.of(shifts));
    }

    private DutyRoster saved() {
        ArgumentCaptor<DutyRoster> captor = ArgumentCaptor.forClass(DutyRoster.class);
        verify(rosters).save(captor.capture());
        return captor.getValue();
    }

    // --- the happy path -------------------------------------------------------------------------

    /** An eligible candidate is assigned, the row is marked ASSIGNED, and an event is published. */
    @Test
    void assignsAnEligibleProfessionalAndMarksTheShiftAssigned() {
        pending(unassigned(DutyRole.NURSE));
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-1"));
        noExistingCommitments();

        service.autoScheduleShifts(WEDNESDAY);

        DutyRoster result = saved();
        assertThat(result.getProfessionalId()).isEqualTo("prof-1");
        assertThat(result.getStatus()).isEqualTo(ShiftStatus.ASSIGNED);
        verify(streamBridge).send(eq("roster-events"), any());
    }

    /**
     * The binding name is pinned because it is <b>not declared anywhere</b>: no {@code destination:}
     * in any {@code .yml} in any of the three products matches it, and nothing consumes it. If the
     * rewrite drops the publish entirely, this assertion should go with it rather than be made to
     * pass against a new name nobody reads either.
     */
    @Test
    void publishesToAnUndeclaredBindingNamedRosterEvents() {
        pending(unassigned(DutyRole.NURSE));
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-1"));
        noExistingCommitments();

        service.autoScheduleShifts(WEDNESDAY);

        verify(streamBridge).send(eq("roster-events"), any());
    }

    // --- hard constraints -----------------------------------------------------------------------

    /**
     * No team covers the shift's space, so it is left alone rather than filled from anywhere.
     *
     * <p><b>A willing candidate is stubbed on purpose.</b> Without one this test passes for the
     * wrong reason: remove the geography guard and the unstubbed profile query returns an empty list
     * by default, so the shift is skipped anyway and the test stays green. Proven by mutation —
     * deleting the guard produced zero failures until this candidate was added. With them present,
     * bypassing geography assigns somebody and the test fails, which is the whole point.
     *
     * <p>Geography short-circuits <b>before</b> the candidate query, so that query is never reached.
     */
    @Test
    void leavesAShiftUnassignedWhenNoTeamCoversItsGeographicSpace() {
        pending(unassigned(DutyRole.NURSE));
        when(teams.findByGeographicSpaceIdsContaining(SPACE)).thenReturn(List.of());
        candidatesAre(candidate("prof-willing"));
        noExistingCommitments();

        service.autoScheduleShifts(WEDNESDAY);

        verify(rosters, never()).save(any());
        verify(streamBridge, never()).send(anyString(), any());
        verify(profiles, never()).findByRoleTypeAndTeamIdInAndStatusTrue(any(), anyList());
    }

    /** A covering team with no eligible member is the same outcome: untouched, not force-filled. */
    @Test
    void leavesAShiftUnassignedWhenNobodyIsEligible() {
        pending(unassigned(DutyRole.NURSE));
        oneTeamCoversTheSpace();
        candidatesAre();
        noExistingCommitments();

        service.autoScheduleShifts(WEDNESDAY);

        verify(rosters, never()).save(any());
    }

    /**
     * Availability is applied in memory by {@code HCProfile.isAvailable}, on top of the query's own
     * {@code StatusTrue} filter — a candidate inside an unavailability period is dropped even though
     * the repository returned them.
     */
    @Test
    void excludesACandidateInsideAnUnavailabilityPeriod() {
        pending(unassigned(DutyRole.NURSE));
        oneTeamCoversTheSpace();
        HCProfile onLeave = candidate("prof-away")
            .unavailabilityPeriods(List.of(new UnavailabilityPeriod().fromDate(WEDNESDAY.minusDays(2)).toDate(WEDNESDAY.plusDays(2))));
        candidatesAre(onLeave, candidate("prof-free"));
        // The excluded candidate is also the one fairness would prefer, so this cannot pass merely
        // because the sort is stable and it happened to be listed first.
        when(rosters.existsByProfessionalIdAndDate(anyString(), any())).thenReturn(false);
        when(rosters.countByProfessionalIdAndDateBetween(eq("prof-away"), any(), any())).thenReturn(0);
        when(rosters.countByProfessionalIdAndDateBetween(eq("prof-free"), any(), any())).thenReturn(9);

        service.autoScheduleShifts(WEDNESDAY);

        assertThat(saved().getProfessionalId()).isEqualTo("prof-free");
    }

    /** An open-ended unavailability period (no end date) excludes every date from its start on. */
    @Test
    void treatsAnOpenEndedUnavailabilityPeriodAsIndefinite() {
        pending(unassigned(DutyRole.NURSE));
        oneTeamCoversTheSpace();
        HCProfile gone = candidate("prof-gone")
            .unavailabilityPeriods(List.of(new UnavailabilityPeriod().fromDate(WEDNESDAY.minusMonths(1))));
        candidatesAre(gone, candidate("prof-here"));
        noExistingCommitments();

        service.autoScheduleShifts(WEDNESDAY);

        assertThat(saved().getProfessionalId()).isEqualTo("prof-here");
    }

    /** Nobody is booked twice on one date. */
    @Test
    void excludesACandidateAlreadyBookedThatDay() {
        pending(unassigned(DutyRole.NURSE));
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-busy"), candidate("prof-free"));
        when(rosters.existsByProfessionalIdAndDate("prof-busy", WEDNESDAY)).thenReturn(true);
        when(rosters.existsByProfessionalIdAndDate("prof-free", WEDNESDAY)).thenReturn(false);
        // Booked AND least-loaded, so fairness would choose it if the double-booking filter were
        // removed — the test then fails, rather than passing on stable-sort ordering.
        when(rosters.countByProfessionalIdAndDateBetween(eq("prof-busy"), any(), any())).thenReturn(0);
        when(rosters.countByProfessionalIdAndDateBetween(eq("prof-free"), any(), any())).thenReturn(9);

        service.autoScheduleShifts(WEDNESDAY);

        assertThat(saved().getProfessionalId()).isEqualTo("prof-free");
    }

    // --- the soft constraint --------------------------------------------------------------------

    /**
     * Among eligible candidates the one with the fewest shifts this week wins.
     *
     * <p><b>This is the only ranking there is</b>, and the plan replaces it: proximity to the
     * professional's home space is meant to rank ahead of fairness. When that lands this assertion
     * should change deliberately — the two candidates here differ only in load, so it will keep
     * passing if fairness remains the tie-break, and fail if fairness is dropped altogether.
     */
    @Test
    void prefersTheCandidateWithFewestShiftsThisWeek() {
        pending(unassigned(DutyRole.NURSE));
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-loaded"), candidate("prof-light"));
        when(rosters.existsByProfessionalIdAndDate(anyString(), any())).thenReturn(false);
        when(rosters.countByProfessionalIdAndDateBetween(eq("prof-loaded"), any(), any())).thenReturn(4);
        when(rosters.countByProfessionalIdAndDateBetween(eq("prof-light"), any(), any())).thenReturn(1);

        service.autoScheduleShifts(WEDNESDAY);

        assertThat(saved().getProfessionalId()).isEqualTo("prof-light");
    }

    /**
     * Fairness is counted over the <b>Monday-to-Sunday week containing the shift</b>, not a rolling
     * seven days and not the calendar month. Pinned because the rewrite has to keep some window and
     * this is the one currently in force.
     *
     * <p><b>Two candidates, and that is not incidental.</b> The count is computed inside the sort
     * comparator, and {@code List.sort} short-circuits on a single-element list — so with one
     * eligible professional the fairness query is never issued at all. Written first with one
     * candidate, this test failed with "wanted but not invoked", which is how that was found.
     */
    @Test
    void countsFairnessOverTheMondayToSundayWeekOfTheShift() {
        pending(unassigned(DutyRole.NURSE));
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-1"), candidate("prof-2"));
        noExistingCommitments();

        service.autoScheduleShifts(WEDNESDAY);

        verify(rosters, org.mockito.Mockito.atLeastOnce())
            .countByProfessionalIdAndDateBetween(
                "prof-1",
                LocalDate.of(2026, 8, 10), // Monday
                LocalDate.of(2026, 8, 16) // Sunday
            );
    }

    /**
     * <b>PINNED DEFECT: the fairness count is a database query inside a sort comparator.</b>
     *
     * <p>{@code eligibleProfessionals.sort(Comparator.comparingInt(p -> repository.count...))}
     * re-issues the query every time the comparator is invoked, rather than computing one count per
     * candidate and sorting on that. For n candidates that is O(n log n) round trips where n would
     * do, repeated for every unassigned shift on the date.
     *
     * <p>It is invisible at the scale the seed data exercises and grows with the size of a team.
     * Asserted loosely — more calls than candidates — because the exact count depends on the sort
     * implementation, and pinning that would make this fail on a JDK upgrade for no reason.
     *
     * <p>The fix is {@code Comparator.comparingInt} over a pre-computed map. When it lands, this
     * test should be replaced by one asserting exactly one query per candidate.
     */
    @Test
    void issuesTheFairnessQueryRepeatedlyFromInsideTheComparator() {
        pending(unassigned(DutyRole.NURSE));
        oneTeamCoversTheSpace();
        candidatesAre(candidate("p1"), candidate("p2"), candidate("p3"), candidate("p4"), candidate("p5"));
        noExistingCommitments();

        service.autoScheduleShifts(WEDNESDAY);

        verify(rosters, org.mockito.Mockito.atLeast(6)) // more than the five candidates
            .countByProfessionalIdAndDateBetween(anyString(), any(), any());
    }

    // --- the defect -----------------------------------------------------------------------------

    /**
     * <b>PINNED BUG. The duty role does not narrow the candidate pool at all.</b>
     *
     * <p>The selection reads
     * {@code shift.getDuty().name().equals("DOCTOR") ? RoleType.PROFESSIONAL : RoleType.PROFESSIONAL}
     * — both branches of the ternary return the same value — so a {@code DOCTOR} shift and a
     * {@code TECHNICIAN} shift query for exactly the same candidates, despite the surrounding
     * comment calling role a hard constraint.
     *
     * <p>This test asserts the broken behaviour on purpose, so that fixing it is a visible,
     * intentional change rather than something that quietly alters who gets scheduled. <b>When the
     * role filter is made real, replace this test</b> with one asserting that a DOCTOR duty selects
     * only doctors — do not adjust it to keep passing.
     */
    @Test
    void doesNotNarrowCandidatesByDutyRole() {
        ArgumentCaptor<RoleType> queried = ArgumentCaptor.forClass(RoleType.class);
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-1"));
        noExistingCommitments();

        pending(unassigned(DutyRole.DOCTOR));
        service.autoScheduleShifts(WEDNESDAY);

        pending(unassigned(DutyRole.TECHNICIAN));
        service.autoScheduleShifts(WEDNESDAY);

        verify(profiles, org.mockito.Mockito.atLeast(2)).findByRoleTypeAndTeamIdInAndStatusTrue(queried.capture(), anyList());
        assertThat(queried.getAllValues()).allMatch(role -> role == RoleType.PROFESSIONAL);
    }
}
