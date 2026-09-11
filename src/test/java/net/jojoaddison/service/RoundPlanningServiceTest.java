package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.domain.GeographicSpace;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.ShiftAssignment;
import net.jojoaddison.domain.Team;
import net.jojoaddison.domain.UnavailabilityPeriod;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.ShiftType;
import net.jojoaddison.repository.GeographicSpaceRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.TeamRepository;
import net.jojoaddison.service.dto.RoundPlanDtos.Outcome;
import net.jojoaddison.service.dto.RoundPlanDtos.PlanReport;
import net.jojoaddison.service.dto.RoundPlanDtos.PlanRequest;
import net.jojoaddison.service.dto.RoundPlanDtos.Reason;
import net.jojoaddison.service.dto.RoundPlanDtos.RoundRequest;
import net.jojoaddison.service.dto.RoundPlanDtos.VisitRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * The planner, checked against the scheduler it replaced.
 *
 * <p><b>This is the successor to {@code DutyRosterAutoScheduleTest}</b>, which was written as a
 * characterization test in front of this rewrite because {@code autoScheduleShifts} had no
 * regression net at all. That class pinned eleven behaviours of the old code, two of them
 * deliberately <em>wrong</em> and labelled as such, and said in its own javadoc what should happen
 * to each when the rewrite landed. This class is the answer, case for case:
 *
 * <ul>
 *   <li><b>Carried unchanged</b> — team→space coverage, availability including the open-ended
 *       period, fairness by fewest shifts in the Monday-to-Sunday week of the round. The cases below
 *       are the old ones with the fixture translated from {@code HCProfile} to {@link Professional};
 *       the assertions did not move.
 *   <li><b>Carried, against a different record</b> — double-booking. The old rule read this
 *       service's own roster collection, which no longer exists; it now reads the staffing grid for
 *       a rostered {@code OFF} day and keeps its own set for one planning run. See
 *       {@link #leavesARoundUnplannedWhenTheOnlyCandidateIsRosteredOff} and
 *       {@link #doesNotGiveOnePersonTwoRoundsInTheSameRun}, and the honest limit in
 *       {@code RoundPlanningService.alreadyCommitted}.
 *   <li><b>Deliberately inverted</b> — {@code doesNotNarrowCandidatesByDutyRole} pinned a bug: both
 *       branches of a ternary returned the same value, so every duty drew the same pool. Its
 *       instruction was "when the role filter is made real, replace this test — do not adjust it to
 *       keep passing". {@link #narrowsCandidatesByRole} is that replacement.
 *   <li><b>Deliberately inverted</b> — {@code issuesTheFairnessQueryRepeatedlyFromInsideTheComparator}
 *       pinned a query issued from inside a sort comparator, and asked to be replaced by one
 *       asserting exactly one query per candidate. {@link #issuesExactlyOneFairnessQueryPerCandidate}
 *       is that replacement.
 *   <li><b>Deleted with the behaviour</b> — {@code publishesToAnUndeclaredBindingNamedRosterEvents}
 *       pinned a {@code StreamBridge} send to {@code roster-events}, a binding declared in no config
 *       and read by nothing. Its instruction was "if the rewrite drops the publish entirely, this
 *       assertion should go with it rather than be made to pass against a new name nobody reads
 *       either." The write is a synchronous HTTP call now, and
 *       {@link #sendsTheRoundToProfessionalserviceWithTheFieldsItRequires} is what took its place.
 *   <li><b>New</b> — proximity ranking, the role translation across the stack boundary, and the two
 *       failure states the old code could not have: an unreachable roster service and a refused
 *       round.
 * </ul>
 *
 * <p>A unit test over mocked repositories, as its predecessor was and for the same reason: the logic
 * is a filter chain, a walk and a sort, and needing a container is why there was no test here.
 */
class RoundPlanningServiceTest {

    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);
    private static final String SPACE = "space-osu";

    private final TeamRepository teams = mock(TeamRepository.class);
    private final ProfessionalRepository professionals = mock(ProfessionalRepository.class);
    private final GeographicSpaceRepository spaces = mock(GeographicSpaceRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ProfessionalServiceClient client = mock(ProfessionalServiceClient.class);

    private final RoundPlanningService service = new RoundPlanningService(teams, professionals, spaces, mongoTemplate, client);

    /** Ids passed to the fairness count, in the order the service asked for them. */
    private final List<String> fairnessQueries = new ArrayList<>();

    private final Map<String, Integer> load = new HashMap<>();
    private final Map<String, Boolean> rosteredOff = new HashMap<>();

    // --- fixtures -------------------------------------------------------------------------------

    private static RoundRequest round(ProfessionalRole role) {
        return new RoundRequest(role, ShiftType.DAY, SPACE, "Morning round", "Routine calls", List.of());
    }

    private static PlanRequest planFor(RoundRequest... rounds) {
        return new PlanRequest(WEDNESDAY, List.of(rounds));
    }

    private static Professional candidate(String id) {
        return new Professional()
            .id(id)
            .role(ProfessionalRole.NURSE)
            .status(AccountStatus.ACTIVE)
            .profile(new Profile().firstName("Ama").lastName("Boateng"));
    }

    private void oneTeamCoversTheSpace() {
        when(teams.findByGeographicSpaceIdsContaining(SPACE)).thenReturn(List.of(new Team().id("team-1")));
    }

    private void candidatesAre(Professional... found) {
        when(professionals.findByTeamIn(anyCollection())).thenReturn(List.of(found));
    }

    /**
     * Nobody is rostered off and nobody has worked this week, unless a test says otherwise.
     *
     * <p>Both stubs read the {@code professional} out of the {@link Query} the service built, which
     * is also how the per-candidate assertions below can tell the calls apart. {@code Criteria.is}
     * stores the entity unconverted, so it comes back out of {@code getQueryObject()} as itself.
     */
    private void gridStubs() {
        when(mongoTemplate.exists(any(Query.class), eq(ShiftAssignment.class))).thenAnswer(invocation ->
            rosteredOff.getOrDefault(queriedProfessional(invocation.getArgument(0)), Boolean.FALSE)
        );
        when(mongoTemplate.count(any(Query.class), eq(ShiftAssignment.class))).thenAnswer(invocation -> {
            String id = queriedProfessional(invocation.getArgument(0));
            fairnessQueries.add(id);
            return (long) load.getOrDefault(id, 0);
        });
    }

    private static String queriedProfessional(Query query) {
        return ((Professional) query.getQueryObject().get("professional")).getId();
    }

    private void filedAs(String roundId) {
        when(client.fileRound(any())).thenReturn(roundId);
    }

    private static void assertPlanned(PlanReport report, String professionalId) {
        assertThat(report.rounds()).hasSize(1);
        assertThat(report.rounds().get(0).outcome()).isEqualTo(Outcome.PLANNED);
        assertThat(report.rounds().get(0).professionalId()).isEqualTo(professionalId);
    }

    // --- the happy path -------------------------------------------------------------------------

    @Test
    void staffsAnEligibleProfessionalAndFilesTheRound() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-1"));
        gridStubs();
        filedAs("round-77");

        PlanReport report = service.plan(planFor(round(ProfessionalRole.NURSE)));

        assertPlanned(report, "prof-1");
        assertThat(report.rounds().get(0).roundId()).isEqualTo("round-77");
        assertThat(report.rounds().get(0).professionalName()).isEqualTo("Ama Boateng");
        assertThat(report.rosterServiceReachable()).isTrue();
    }

    /**
     * What the far service is actually sent.
     *
     * <p>The successor to the old {@code roster-events} assertion. Every key is checked because the
     * body is hand-built rather than serialised from a shared type — deliberately, so that hc-admin
     * does not keep a fourth copy of a roster shape — and a hand-built map is exactly the thing a
     * rename on the other side breaks in silence.
     *
     * <p><b>{@code id} must be absent.</b> hc-professional refuses a round that already carries one,
     * so including it would turn every plan into a 400 that reads as a validation problem.
     */
    @Test
    @SuppressWarnings("unchecked")
    void sendsTheRoundToProfessionalserviceWithTheFieldsItRequires() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-1"));
        gridStubs();
        filedAs("round-1");

        service.plan(
            new PlanRequest(
                WEDNESDAY,
                List.of(
                    new RoundRequest(
                        ProfessionalRole.NURSE,
                        ShiftType.EVENING,
                        SPACE,
                        "Evening round — Osu",
                        "Two calls",
                        List.of(new VisitRequest("cust-9", LocalTime.of(16, 0), LocalTime.of(17, 0)))
                    )
                )
            )
        );

        ArgumentCaptor<Map<String, Object>> body = ArgumentCaptor.forClass(Map.class);
        verify(client).fileRound(body.capture());
        Map<String, Object> sent = body.getValue();
        assertThat(sent).doesNotContainKey("id");
        assertThat(sent.get("date")).isEqualTo("2026-08-12");
        assertThat(sent.get("duty")).isEqualTo("NURSE");
        assertThat(sent.get("professionalId")).isEqualTo("prof-1");
        assertThat(sent.get("shift")).isEqualTo("EVENING");
        assertThat(sent.get("name")).isEqualTo("Evening round — Osu");
        assertThat(sent.get("geographicSpaceId")).isEqualTo(SPACE);
        assertThat((List<Map<String, Object>>) sent.get("visits"))
            .singleElement()
            .satisfies(visit -> {
                assertThat(visit.get("customerId")).isEqualTo("cust-9");
                assertThat(visit.get("startTime")).isEqualTo("16:00");
                assertThat(visit.get("endTime")).isEqualTo("17:00");
            });
    }

    /**
     * hc-admin's roles translated into the duty names hc-professional declares.
     *
     * <p>Four of the five names match and one does not — {@code CAREGIVER} is {@code CARER} over
     * there — which is the near-identity {@code duty-roster-resolution.md} § 6.4 calls more
     * dangerous than clean difference. Every value is asserted, so adding a {@link ProfessionalRole}
     * fails here as well as failing to compile.
     */
    @Test
    void translatesEveryRoleToADutyHcProfessionalDeclares() {
        assertThat(RoundPlanningService.duty(ProfessionalRole.DOCTOR)).isEqualTo("DOCTOR");
        assertThat(RoundPlanningService.duty(ProfessionalRole.NURSE)).isEqualTo("NURSE");
        assertThat(RoundPlanningService.duty(ProfessionalRole.PARAMEDIC)).isEqualTo("PARAMEDIC");
        assertThat(RoundPlanningService.duty(ProfessionalRole.THERAPIST)).isEqualTo("THERAPIST");
        assertThat(RoundPlanningService.duty(ProfessionalRole.CAREGIVER)).isEqualTo("CARER");
        assertThat(ProfessionalRole.values()).hasSize(5);
    }

    // --- hard constraints -----------------------------------------------------------------------

    /**
     * No team covers the round's space, so it is left unplanned rather than filled from anywhere.
     *
     * <p><b>A willing candidate is stubbed on purpose</b>, exactly as in the predecessor. Without
     * one this passes for the wrong reason: remove the geography guard and the unstubbed candidate
     * query returns an empty list by default, so the round is unplanned anyway and the test stays
     * green. Proven by mutation there; the same stub keeps it honest here.
     *
     * <p>Geography short-circuits <b>before</b> the candidate query, so that query is never reached.
     */
    @Test
    void leavesARoundUnplannedWhenNoTeamCoversItsGeographicSpace() {
        when(teams.findByGeographicSpaceIdsContaining(SPACE)).thenReturn(List.of());
        candidatesAre(candidate("prof-willing"));
        gridStubs();

        PlanReport report = service.plan(planFor(round(ProfessionalRole.NURSE)));

        assertThat(report.rounds().get(0).outcome()).isEqualTo(Outcome.UNPLANNED);
        assertThat(report.rounds().get(0).reason()).isEqualTo(Reason.NO_TEAM_COVERS_THE_SPACE);
        verify(professionals, never()).findByTeamIn(anyCollection());
        verify(client, never()).fileRound(any());
    }

    /** A covering team with no eligible member is the same outcome: unplanned, not force-filled. */
    @Test
    void leavesARoundUnplannedWhenNobodyIsEligible() {
        oneTeamCoversTheSpace();
        candidatesAre();
        gridStubs();

        PlanReport report = service.plan(planFor(round(ProfessionalRole.NURSE)));

        assertThat(report.rounds().get(0).outcome()).isEqualTo(Outcome.UNPLANNED);
        verify(client, never()).fileRound(any());
    }

    /**
     * <b>The role filter narrows.</b> The replacement for the pinned bug: the old selection read
     * {@code duty.name().equals("DOCTOR") ? PROFESSIONAL : PROFESSIONAL}, so a doctor's round and a
     * technician's drew from an identical pool.
     *
     * <p>Two candidates on the same team, differing only in role, and the doctor's round must not go
     * to the nurse — who is also the one fairness would prefer, so this cannot pass on ordering.
     */
    @Test
    void narrowsCandidatesByRole() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-nurse"), candidate("prof-doctor").role(ProfessionalRole.DOCTOR));
        load.put("prof-nurse", 0);
        load.put("prof-doctor", 9);
        gridStubs();
        filedAs("round-1");

        assertPlanned(service.plan(planFor(round(ProfessionalRole.DOCTOR))), "prof-doctor");
    }

    /** Nobody holds the role: a different answer from "nobody is free", and the screen says so. */
    @Test
    void distinguishesNobodyWithTheRoleFromNobodyAvailable() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-nurse"));
        gridStubs();

        PlanReport report = service.plan(planFor(round(ProfessionalRole.THERAPIST)));

        assertThat(report.rounds().get(0).reason()).isEqualTo(Reason.NO_CANDIDATE_HOLDS_THE_ROLE);
    }

    /**
     * Availability is applied in memory by {@link Professional#isAvailable(LocalDate)} — a candidate
     * inside an unavailability period is dropped even though the repository returned them.
     */
    @Test
    void excludesACandidateInsideAnUnavailabilityPeriod() {
        oneTeamCoversTheSpace();
        Professional onLeave = candidate("prof-away").unavailabilityPeriods(
            List.of(new UnavailabilityPeriod().fromDate(WEDNESDAY.minusDays(2)).toDate(WEDNESDAY.plusDays(2)))
        );
        candidatesAre(onLeave, candidate("prof-free"));
        // The excluded candidate is also the one fairness would prefer, so this cannot pass merely
        // because the sort is stable and it happened to be listed first.
        load.put("prof-away", 0);
        load.put("prof-free", 9);
        gridStubs();
        filedAs("round-1");

        assertPlanned(service.plan(planFor(round(ProfessionalRole.NURSE))), "prof-free");
    }

    /** An open-ended unavailability period (no end date) excludes every date from its start on. */
    @Test
    void treatsAnOpenEndedUnavailabilityPeriodAsIndefinite() {
        oneTeamCoversTheSpace();
        Professional gone = candidate("prof-gone").unavailabilityPeriods(
            List.of(new UnavailabilityPeriod().fromDate(WEDNESDAY.minusMonths(1)))
        );
        candidatesAre(gone, candidate("prof-here"));
        gridStubs();
        filedAs("round-1");

        assertPlanned(service.plan(planFor(round(ProfessionalRole.NURSE))), "prof-here");
    }

    /**
     * A suspended account is not available, whatever their diary says.
     *
     * <p>The status half of the rule, which changed shape in the move: {@code HCProfile.status} was
     * a {@code Boolean} and {@link Professional}'s is an {@link AccountStatus}. Only {@code ACTIVE}
     * may be planned.
     */
    @Test
    void excludesACandidateWhoIsNotActive() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-suspended").status(AccountStatus.SUSPENDED), candidate("prof-active"));
        load.put("prof-suspended", 0);
        load.put("prof-active", 9);
        gridStubs();
        filedAs("round-1");

        assertPlanned(service.plan(planFor(round(ProfessionalRole.NURSE))), "prof-active");
    }

    /**
     * A rostered rest day is a commitment not to work — the replacement for the old
     * "already has a roster row on this date" check, against the record this service still owns.
     */
    @Test
    void leavesARoundUnplannedWhenTheOnlyCandidateIsRosteredOff() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-off"));
        rosteredOff.put("prof-off", true);
        gridStubs();

        PlanReport report = service.plan(planFor(round(ProfessionalRole.NURSE)));

        assertThat(report.rounds().get(0).outcome()).isEqualTo(Outcome.UNPLANNED);
        assertThat(report.rounds().get(0).reason()).isEqualTo(Reason.NO_CANDIDATE_IS_AVAILABLE);
        verify(client, never()).fileRound(any());
    }

    /**
     * One person does not get two rounds in one run.
     *
     * <p>Without this, three rounds in one space all go to the same nearest, least-loaded person —
     * and the grid check cannot catch it, because nothing has been written to the grid.
     */
    @Test
    void doesNotGiveOnePersonTwoRoundsInTheSameRun() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-1"), candidate("prof-2"));
        load.put("prof-1", 0);
        load.put("prof-2", 5);
        gridStubs();
        filedAs("round-x");

        PlanReport report = service.plan(planFor(round(ProfessionalRole.NURSE), round(ProfessionalRole.NURSE)));

        assertThat(report.rounds()).extracting("professionalId").containsExactly("prof-1", "prof-2");
    }

    /**
     * <b>And a second run gives the same person a second visit-less round on the same date, on
     * purpose.</b>
     *
     * <p>Backlog item 23, decided 2026-09-06 and pinned here rather than left as a paragraph. The
     * rule above is scoped to one call; nothing here or in hc-professional refuses the second round
     * when it carries no visits ({@code validateRound} returns on an empty visit list before it
     * reaches the overlap check, deliberately — ward cover and on-call are real shifts). The
     * decision is that this is legal and that this service will not guess otherwise: it knows only
     * what it filed, so a local uniqueness check would refuse or allow the same round depending on
     * which surface filed the first, and a wrongly refused round comes back as
     * {@code NO_CANDIDATE_IS_AVAILABLE} — indistinguishable from leave. The reasoning is in
     * {@code RoundPlanningService.alreadyCommitted}'s javadoc, with the one change that would
     * reverse it.
     *
     * <p><b>Inverted, which is why it is worth its lines.</b> It asserts the absence of a rule, so
     * it goes red the moment somebody adds the partial guard the entry exists to prevent — and the
     * javadoc it names is where they will find out why it is red. A test that only asserted the
     * present behaviour of the same code would pass either way.
     */
    @Test
    void filesASecondVisitlessRoundForTheSamePersonAndDate() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-1"));
        gridStubs();
        filedAs("round-1");

        // The same request, twice, exactly as two planning runs on the same day would arrive.
        PlanReport first = service.plan(planFor(round(ProfessionalRole.NURSE)));
        PlanReport second = service.plan(planFor(round(ProfessionalRole.NURSE)));

        assertPlanned(first, "prof-1");
        assertPlanned(second, "prof-1");
        // Filed both times: not merely reported as planned, actually written to the roster of record.
        verify(client, times(2)).fileRound(any());
    }

    // --- ranking --------------------------------------------------------------------------------

    /**
     * Among equally near candidates the one with the fewest shifts this week wins.
     *
     * <p>The predecessor's own note said this assertion should keep passing if fairness remained the
     * tie-break and fail if it were dropped altogether. Proximity now ranks above it, and neither
     * candidate here has a home space, so they are equally near and fairness decides — which is the
     * case that note described.
     */
    @Test
    void prefersTheCandidateWithFewestShiftsThisWeek() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-loaded"), candidate("prof-light"));
        load.put("prof-loaded", 4);
        load.put("prof-light", 1);
        gridStubs();
        filedAs("round-1");

        assertPlanned(service.plan(planFor(round(ProfessionalRole.NURSE))), "prof-light");
    }

    /**
     * Fairness is counted over the <b>Monday-to-Sunday week containing the round</b>, not a rolling
     * seven days and not the calendar month. Unchanged from the scheduler, and pinned again because
     * the rewrite had to keep some window and this is the one in force.
     */
    @Test
    void countsFairnessOverTheMondayToSundayWeekOfTheRound() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-1"));
        gridStubs();
        filedAs("round-1");

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        service.plan(planFor(round(ProfessionalRole.NURSE)));

        verify(mongoTemplate).count(query.capture(), eq(ShiftAssignment.class));
        // Read out of the criteria document rather than its JSON: Criteria.gte stores the LocalDate
        // unconverted, and toJson() on a document holding one throws for want of a codec — a green
        // assertion here would be an exception nobody meant to catch.
        org.bson.Document criteria = query.getValue().getQueryObject();
        org.bson.Document window = (org.bson.Document) criteria.get("shiftDate");
        assertThat(window.get("$gte")).isEqualTo(LocalDate.of(2026, 8, 10)); // Monday
        assertThat(window.get("$lte")).isEqualTo(LocalDate.of(2026, 8, 16)); // Sunday
        // OFF is excluded — a rest day is planned but not worked, which is the rule
        // ShiftValuationService pays by, so the planner's busy week and the payroll's agree.
        assertThat(((org.bson.Document) criteria.get("shift")).get("$ne")).isEqualTo(ShiftType.OFF);
    }

    /**
     * <b>One query per candidate.</b> The replacement for the pinned defect: the old comparator
     * issued the count on every invocation, which is O(n log n) round trips where n would do,
     * repeated for every round on the date. Its own javadoc asked for exactly this assertion.
     *
     * <p>Five candidates, so a comparator-side query would show as far more than five.
     */
    @Test
    void issuesExactlyOneFairnessQueryPerCandidate() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("p1"), candidate("p2"), candidate("p3"), candidate("p4"), candidate("p5"));
        gridStubs();
        filedAs("round-1");

        service.plan(planFor(round(ProfessionalRole.NURSE)));

        assertThat(fairnessQueries).containsExactlyInAnyOrder("p1", "p2", "p3", "p4", "p5");
    }

    /**
     * <b>Proximity outranks fairness.</b> The nearer candidate wins even carrying nine shifts to the
     * other's none — which is the ordering the plan specified and the reason it is worth a test of
     * its own: with the two ranks the other way round this assertion inverts rather than merely
     * weakening.
     */
    @Test
    void prefersTheNearerCandidateOverTheLessLoadedOne() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-near").homeSpaceId(SPACE), candidate("prof-far").homeSpaceId("space-elsewhere"));
        tree();
        load.put("prof-near", 9);
        load.put("prof-far", 0);
        gridStubs();
        filedAs("round-1");

        assertPlanned(service.plan(planFor(round(ProfessionalRole.NURSE))), "prof-near");
    }

    /**
     * The four bands, in order: same space, same parent, same ancestor, anywhere the team covers.
     *
     * <p>Run as four plans over the same tree rather than four assertions on a ranking function,
     * because the ordering is what the caller sees and a private comparator is not. Each run offers
     * the winner of the last run plus one nearer candidate, so a ranking that collapsed two bands
     * into one would fail here rather than pass on a coincidence.
     */
    @Test
    void ranksSameSpaceThenSameParentThenSameAncestorThenAnywhereCovered() {
        oneTeamCoversTheSpace();
        tree();
        gridStubs();
        filedAs("round-1");

        Professional unrelated = candidate("prof-unrelated").homeSpaceId("space-far-region");
        Professional ancestor = candidate("prof-ancestor").homeSpaceId("space-labadi");
        Professional sibling = candidate("prof-sibling").homeSpaceId("space-cantonments");
        Professional same = candidate("prof-same").homeSpaceId(SPACE);

        candidatesAre(unrelated);
        assertPlanned(service.plan(planFor(round(ProfessionalRole.NURSE))), "prof-unrelated");

        candidatesAre(unrelated, ancestor);
        assertPlanned(service.plan(planFor(round(ProfessionalRole.NURSE))), "prof-ancestor");

        candidatesAre(unrelated, ancestor, sibling);
        assertPlanned(service.plan(planFor(round(ProfessionalRole.NURSE))), "prof-sibling");

        candidatesAre(unrelated, ancestor, sibling, same);
        assertPlanned(service.plan(planFor(round(ProfessionalRole.NURSE))), "prof-same");
    }

    /**
     * A candidate with no home space stays eligible and ranks last.
     *
     * <p>Being on a team that covers the space is what makes somebody a candidate; proximity only
     * orders them. Dropping the unplaced would make an unset field a silent disqualification, and
     * {@code homeSpaceId} is absent on every {@link Professional} written before it existed.
     */
    @Test
    void ranksACandidateWithNoHomeSpaceLastRatherThanExcludingThem() {
        oneTeamCoversTheSpace();
        tree();
        gridStubs();
        filedAs("round-1");

        candidatesAre(candidate("prof-unplaced"));
        assertPlanned(service.plan(planFor(round(ProfessionalRole.NURSE))), "prof-unplaced");

        candidatesAre(candidate("prof-unplaced"), candidate("prof-placed").homeSpaceId(SPACE));
        assertPlanned(service.plan(planFor(round(ProfessionalRole.NURSE))), "prof-placed");
    }

    /**
     * A cycle in the tree gives up rather than looping.
     *
     * <p>{@code GeographicSpaceCycleGuard} refuses to store one and says in its own javadoc that a
     * reader must not rely on that, because it is check-then-act with no lock. The cost of trusting
     * it is a request that never answers, arriving nowhere near the write that caused it.
     */
    @Test
    void survivesACycleInTheSpaceTree() {
        oneTeamCoversTheSpace();
        when(spaces.findById(SPACE)).thenReturn(Optional.of(new GeographicSpace().id(SPACE).parentId("space-loop")));
        when(spaces.findById("space-loop")).thenReturn(Optional.of(new GeographicSpace().id("space-loop").parentId(SPACE)));
        candidatesAre(candidate("prof-1").homeSpaceId("space-loop"));
        gridStubs();
        filedAs("round-1");

        assertPlanned(service.plan(planFor(round(ProfessionalRole.NURSE))), "prof-1");
    }

    /**
     * An exact tie is broken by id, so a planning run is reproducible.
     *
     * <p>The old scheduler left it to whatever order the repository returned, which is not an order
     * Mongo promises across queries — the same lesson hc-professional's {@code DutyRosterEstateOrderIT}
     * records for paging. The candidates are offered in reverse id order deliberately: listed
     * ascending, this assertion would pass with no tiebreaker at all.
     */
    @Test
    void breaksAnExactTieOnTheIdSoARunIsReproducible() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-b"), candidate("prof-a"));
        gridStubs();
        filedAs("round-1");

        assertPlanned(service.plan(planFor(round(ProfessionalRole.NURSE))), "prof-a");
    }

    // --- the three failures the old code could not have ------------------------------------------

    /**
     * <b>An unreachable roster service is FAILED and an outage, never UNPLANNED.</b>
     *
     * <p>Decision 10 of {@code duty-roster-resolution.md} § 9.1, at the layer that decides it. The
     * two states mean opposite things to whoever is looking: unplanned is a decision they can act on
     * by widening the request, and an outage is the estate telling them nothing is known.
     */
    @Test
    void reportsAnUnreachableRosterServiceAsAnOutageRatherThanAsUnplanned() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-1"));
        gridStubs();
        when(client.fileRound(any())).thenThrow(
            new ProfessionalServiceClient.RosterServiceUnavailableException("down", new ResourceAccessException("connect timed out"))
        );

        PlanReport report = service.plan(planFor(round(ProfessionalRole.NURSE)));

        assertThat(report.rosterServiceReachable()).isFalse();
        assertThat(report.rounds().get(0).outcome()).isEqualTo(Outcome.FAILED);
        assertThat(report.rounds().get(0).reason()).isEqualTo(Reason.ROSTER_SERVICE_UNREACHABLE);
        // Who it would have gone to is still reported: the administrator can retry the same round.
        assertThat(report.rounds().get(0).professionalId()).isEqualTo("prof-1");
        assertThat(report.rounds().get(0).roundId()).isNull();
    }

    /**
     * A round the far service <b>refuses</b> is a failure and not an outage.
     *
     * <p>hc-professional validates a round against its own rules — an {@code OFF} round carrying
     * visits, a time outside the shift window, an overlap with a round it already holds — and
     * answers 400. Nothing is wrong with the estate, so the console must not show one; the request
     * is wrong, and saying "the roster service is unreachable" would send somebody to look at
     * infrastructure.
     */
    @Test
    void reportsARefusedRoundWithoutClaimingAnOutage() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-1"));
        gridStubs();
        when(client.fileRound(any())).thenThrow(
            new ProfessionalServiceClient.RosterServiceUnavailableException(
                "refused",
                HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null, null, null)
            )
        );

        PlanReport report = service.plan(planFor(round(ProfessionalRole.NURSE)));

        assertThat(report.rosterServiceReachable()).isTrue();
        assertThat(report.rounds().get(0).outcome()).isEqualTo(Outcome.FAILED);
        assertThat(report.rounds().get(0).reason()).isEqualTo(Reason.ROSTER_SERVICE_REFUSED_THE_ROUND);
    }

    /**
     * <b>A local misconfiguration is not an outage, and does not claim one.</b>
     *
     * <p>Backlog item 24. {@code enabled=false} and a request carrying no caller token both stop the
     * write on this side, before a socket is opened — so nothing was learned about hc-professional,
     * and reporting them as unreachable sends a reader to another stack, or to the network, for a
     * missing environment variable in a compose file. The pair with the case above is the assertion
     * that matters: the flag has to separate "we dialled and got nothing" from "we dialled nothing",
     * and until this test existed one reason carried both.
     *
     * <p>The thrown exception deliberately carries <b>no cause</b>, which is the shape the real one
     * has and the reason the branch order in the service matters: tested for a refusal first, it
     * would fall through to the outage it is here to be told apart from.
     */
    @Test
    void reportsADeploymentThatCannotDialAtAllAsMisconfiguredRatherThanAsAnOutage() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-1"));
        gridStubs();
        when(client.fileRound(any())).thenThrow(
            new ProfessionalServiceClient.RosterServiceNotConfiguredException("professionalservice is disabled")
        );

        PlanReport report = service.plan(planFor(round(ProfessionalRole.NURSE)));

        assertThat(report.rosterServiceReachable()).isTrue();
        assertThat(report.rounds().get(0).outcome()).isEqualTo(Outcome.FAILED);
        assertThat(report.rounds().get(0).reason()).isEqualTo(Reason.ROSTER_SERVICE_NOT_CONFIGURED);
        // Still a failure, still not filed, and still naming who it would have gone to.
        assertThat(report.plannedCount()).isZero();
        assertThat(report.rounds().get(0).professionalId()).isEqualTo("prof-1");
        assertThat(report.rounds().get(0).roundId()).isNull();
    }

    /**
     * One failed write does not discard the rounds that landed.
     *
     * <p>Which is the whole argument for a per-round report rather than a 502: an administrator who
     * cannot tell what was filed either runs it again and double-books, or does not and leaves
     * customers unvisited.
     */
    @Test
    void keepsTheRoundsItFiledWhenALaterOneFails() {
        oneTeamCoversTheSpace();
        candidatesAre(candidate("prof-1"), candidate("prof-2"));
        load.put("prof-1", 0);
        load.put("prof-2", 5);
        gridStubs();
        when(client.fileRound(any()))
            .thenReturn("round-1")
            .thenThrow(
                new ProfessionalServiceClient.RosterServiceUnavailableException("down", new ResourceAccessException("connect timed out"))
            );

        PlanReport report = service.plan(planFor(round(ProfessionalRole.NURSE), round(ProfessionalRole.NURSE)));

        assertThat(report.plannedCount()).isEqualTo(1);
        assertThat(report.rounds().get(0).roundId()).isEqualTo("round-1");
        assertThat(report.rounds().get(1).outcome()).isEqualTo(Outcome.FAILED);
        assertThat(report.rosterServiceReachable()).isFalse();
    }

    /**
     * Accra as far as the planner needs it: two districts under one city, one city under another.
     *
     * <p>{@code space-osu} and {@code space-cantonments} share {@code space-accra}, which sits under
     * {@code space-greater-accra}; {@code space-labadi} hangs off the region directly, so it is a
     * band further out than a sibling district; and {@code space-far-region} is a root of its own.
     */
    private void tree() {
        when(spaces.findById(SPACE)).thenReturn(Optional.of(new GeographicSpace().id(SPACE).parentId("space-accra")));
        when(spaces.findById("space-cantonments")).thenReturn(
            Optional.of(new GeographicSpace().id("space-cantonments").parentId("space-accra"))
        );
        when(spaces.findById("space-accra")).thenReturn(
            Optional.of(new GeographicSpace().id("space-accra").parentId("space-greater-accra"))
        );
        when(spaces.findById("space-labadi")).thenReturn(
            Optional.of(new GeographicSpace().id("space-labadi").parentId("space-greater-accra"))
        );
        when(spaces.findById("space-greater-accra")).thenReturn(Optional.of(new GeographicSpace().id("space-greater-accra")));
        when(spaces.findById("space-far-region")).thenReturn(Optional.of(new GeographicSpace().id("space-far-region")));
        when(spaces.findById("space-elsewhere")).thenReturn(Optional.of(new GeographicSpace().id("space-elsewhere")));
    }
}
