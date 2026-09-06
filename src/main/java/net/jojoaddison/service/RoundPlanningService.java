package net.jojoaddison.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.jojoaddison.domain.GeographicSpace;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.ShiftAssignment;
import net.jojoaddison.domain.Team;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.ShiftType;
import net.jojoaddison.repository.GeographicSpaceRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.TeamRepository;
import net.jojoaddison.service.dto.RoundPlanDtos.PlanReport;
import net.jojoaddison.service.dto.RoundPlanDtos.PlanRequest;
import net.jojoaddison.service.dto.RoundPlanDtos.Reason;
import net.jojoaddison.service.dto.RoundPlanDtos.RoundOutcome;
import net.jojoaddison.service.dto.RoundPlanDtos.RoundRequest;
import net.jojoaddison.service.dto.RoundPlanDtos.VisitRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Staffs rounds and files them with {@code professionalservice}, which owns the roster of record.
 *
 * <h2>What this replaced, and what carried over</h2>
 *
 * <p>{@code DutyRosterService.autoScheduleShifts} filled unassigned rows in <em>this</em> service's
 * own {@code DutyRoster} collection — a collection nothing could reach, seeded in {@code dev} with a
 * single row and absent from the console's own dataset entirely. It was deleted on 2026-09-04 with
 * the rest of that surface. Its four constraints were not deleted, and
 * {@code DutyRosterAutoScheduleTest} existed so that carrying them over could be checked rather than
 * asserted; {@code RoundPlanningServiceTest} is its successor, case for case, and records against
 * each one whether it is the same rule or a deliberately changed one.
 *
 * <ul>
 *   <li><b>Team → space coverage.</b> Unchanged. Only teams that list the round's space in
 *       {@code geographicSpaceIds} may staff it, and a space no team covers leaves the round alone
 *       rather than filling it from anywhere.
 *   <li><b>Availability.</b> Same rule, moved to the document the planner now ranks — see
 *       {@link Professional#isAvailable(LocalDate)}. The old one read {@code HCProfile}, of which
 *       the console dataset holds none.
 *   <li><b>No double-booking.</b> Same rule, against what this service can actually see: a rostered
 *       {@code OFF} cell on the date, and any round this run has already given the same person.
 *       See {@link #alreadyCommitted}, which states plainly what it cannot check — and, since
 *       2026-09-06, records the decision not to guess at the one case nothing in the estate covers.
 *   <li><b>Fairness, fewest shifts this week.</b> Same rule and the same Monday-to-Sunday window,
 *       counted from the staffing grid rather than from the deleted roster — and computed
 *       <b>once per candidate</b> rather than inside a comparator. That was a pinned defect.
 * </ul>
 *
 * <h2>The two fixes that were required in the same change</h2>
 *
 * <p><b>The role filter now narrows.</b> The old one read
 * {@code shift.getDuty().name().equals("DOCTOR") ? RoleType.PROFESSIONAL : RoleType.PROFESSIONAL} —
 * both branches of the ternary the same value — so a doctor's round and a technician's round drew
 * from an identical pool while the comment above them called role a hard constraint. A request now
 * names a {@link ProfessionalRole} and only professionals holding it are candidates.
 *
 * <p><b>Proximity ranks ahead of fairness.</b> Nearest first — same space, then same parent, then
 * same ancestor, then any covered space — with fairness as the tie-break within a proximity band
 * and the id as the final key so that a run is reproducible. See {@link #proximity}.
 *
 * <h2>Planning can now fail in a way local writing could not</h2>
 *
 * <p>The round is written to another service over HTTP. A failure there is reported as
 * {@code FAILED} with {@code rosterServiceReachable=false}, never as {@code UNPLANNED} and never as
 * silence: an empty roster and an unreachable roster service must not look alike, which is decision
 * 10 of {@code duty-roster-resolution.md} § 9.1. The run continues to the next round rather than
 * aborting, so that one unreachable moment does not discard the work already done.
 */
@Service
public class RoundPlanningService {

    private static final Logger LOG = LoggerFactory.getLogger(RoundPlanningService.class);

    /**
     * How far a walk up {@code parentId} may climb before giving up.
     *
     * <p>{@code GeographicSpaceCycleGuard} refuses to store a cycle and says in its own class doc
     * that readers must not trust it: it is check-then-act with no lock, so two concurrent
     * reparentings can still produce one. A reader that loops on corrupt data is a hung request
     * that blames the reader. The visited set below is the real guard and this is the second one.
     */
    private static final int MAX_ANCESTRY_DEPTH = 32;

    /** Ranked last: eligible because a team covers the space, near nothing in particular. */
    private static final int PROXIMITY_UNRELATED = Integer.MAX_VALUE;

    private final TeamRepository teamRepository;
    private final ProfessionalRepository professionalRepository;
    private final GeographicSpaceRepository geographicSpaceRepository;
    private final MongoTemplate mongoTemplate;
    private final ProfessionalServiceClient professionalServiceClient;

    public RoundPlanningService(
        TeamRepository teamRepository,
        ProfessionalRepository professionalRepository,
        GeographicSpaceRepository geographicSpaceRepository,
        MongoTemplate mongoTemplate,
        ProfessionalServiceClient professionalServiceClient
    ) {
        this.teamRepository = teamRepository;
        this.professionalRepository = professionalRepository;
        this.geographicSpaceRepository = geographicSpaceRepository;
        this.mongoTemplate = mongoTemplate;
        this.professionalServiceClient = professionalServiceClient;
    }

    /**
     * Staff every requested round and file the ones that could be staffed.
     *
     * @param request the date and the rounds wanted on it
     * @return one outcome per requested round, in the order they were asked for
     */
    public PlanReport plan(PlanRequest request) {
        LOG.debug("Planning {} round(s) for {}", request.rounds().size(), request.date());

        LocalDate date = request.date();
        // Committed within this run. The far service is the only place a round exists, and asking it
        // per candidate would be a cross-stack read inside a ranking loop; what this set does is stop
        // one planning run from giving the same person two rounds on one date.
        Set<String> committedInThisRun = new HashSet<>();
        Map<String, Optional<GeographicSpace>> spaces = new HashMap<>();
        List<RoundOutcome> outcomes = new ArrayList<>();
        boolean reachable = true;

        for (int index = 0; index < request.rounds().size(); index++) {
            RoundRequest round = request.rounds().get(index);
            List<Team> coveringTeams = teamRepository.findByGeographicSpaceIdsContaining(round.geographicSpaceId());
            if (coveringTeams.isEmpty()) {
                outcomes.add(RoundOutcome.unplanned(index, Reason.NO_TEAM_COVERS_THE_SPACE));
                continue;
            }

            List<Professional> withTheRole = professionalRepository
                .findByTeamIn(coveringTeams)
                .stream()
                .filter(candidate -> candidate.getRole() == round.role())
                .toList();
            if (withTheRole.isEmpty()) {
                outcomes.add(RoundOutcome.unplanned(index, Reason.NO_CANDIDATE_HOLDS_THE_ROLE));
                continue;
            }

            List<Professional> eligible = withTheRole
                .stream()
                .filter(candidate -> candidate.isAvailable(date))
                .filter(candidate -> !alreadyCommitted(candidate, date, committedInThisRun))
                .toList();
            if (eligible.isEmpty()) {
                outcomes.add(RoundOutcome.unplanned(index, Reason.NO_CANDIDATE_IS_AVAILABLE));
                continue;
            }

            Professional chosen = rank(eligible, round.geographicSpaceId(), date, spaces);
            String name = displayName(chosen);
            try {
                String roundId = professionalServiceClient.fileRound(body(round, chosen, date));
                committedInThisRun.add(chosen.getId());
                outcomes.add(RoundOutcome.planned(index, chosen.getId(), name, roundId));
            } catch (ProfessionalServiceClient.RosterServiceUnavailableException e) {
                // A 4xx is the far service reading the round and refusing it — an OFF round carrying
                // visits, a time outside the shift window, an overlap with a round it already holds.
                // That is a different fact from "the roster service is down" and must not put the
                // console into an outage state: nothing is wrong with the estate, the request is
                // wrong. Only the second sets `reachable` false.
                boolean refused = e.getCause() instanceof HttpClientErrorException;
                reachable = reachable && refused;
                outcomes.add(
                    RoundOutcome.failed(
                        index,
                        refused ? Reason.ROSTER_SERVICE_REFUSED_THE_ROUND : Reason.ROSTER_SERVICE_UNREACHABLE,
                        chosen.getId(),
                        name
                    )
                );
            }
        }
        return new PlanReport(date, reachable, outcomes);
    }

    /**
     * The round as {@code professionalservice} expects it.
     *
     * <p><b>This method is the contract.</b> The far service's {@code DutyRoster} is its type and
     * changes on its schedule; there is deliberately no mirrored DTO in this repository, because
     * three copies of a roster shape is what the migration was for. What lives here is the meaning
     * of each key, once:
     *
     * <ul>
     *   <li>{@code duty} is hc-professional's {@code DutyRole}, translated by {@link #duty} from the
     *       {@link ProfessionalRole} this service holds.
     *   <li>{@code shift} is a {@code ShiftType} name, and the two enums are the same five values in
     *       the same order since the superset change of 2026-09-04 — so the name travels unmapped
     *       and a new value on either side is caught by {@code JhipsterEnumFieldValuesTest} on that
     *       side rather than here.
     *   <li>{@code geographicSpaceId} is stored opaquely over there. hc-admin owns the tree and
     *       serves {@code GET /api/geographic-spaces/{id}} so names can be resolved for display
     *       without either service modelling the other's data.
     *   <li>{@code visits[].customerId} passes through untouched — see {@code RoundPlanDtos}.
     * </ul>
     *
     * <p>No {@code id}: hc-professional refuses a round that already has one, which is the JHipster
     * create contract and is what makes this a create rather than an overwrite of somebody else's
     * row.
     */
    static Map<String, Object> body(RoundRequest round, Professional chosen, LocalDate date) {
        List<Map<String, Object>> visits = new ArrayList<>();
        for (VisitRequest visit : round.visits()) {
            Map<String, Object> call = new LinkedHashMap<>();
            call.put("customerId", visit.customerId());
            call.put("startTime", visit.startTime().toString());
            call.put("endTime", visit.endTime().toString());
            visits.add(call);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("date", date.toString());
        body.put("duty", duty(round.role()));
        body.put("professionalId", chosen.getId());
        body.put("shift", round.shift().name());
        body.put("name", round.name());
        body.put("description", round.description());
        body.put("geographicSpaceId", round.geographicSpaceId());
        body.put("visits", visits);
        return body;
    }

    /**
     * hc-admin's {@link ProfessionalRole} as hc-professional's {@code DutyRole} name.
     *
     * <p><b>A cross-repo mirror, and the only one this service keeps.</b> Four of the five names are
     * identical on both sides and one is not — hc-admin says {@code CAREGIVER}, hc-professional says
     * {@code CARER} — which is precisely the near-identity that {@code duty-roster-resolution.md}
     * § 6.4 calls more dangerous than clean difference. Written as an exhaustive switch so that a
     * new {@code ProfessionalRole} is a compile error here rather than a 400 from another stack.
     *
     * <p>hc-admin's own {@code DutyRole} enum — {@code CARE, VENDOR, MEDIC, ADMINISTRATOR} among
     * them — was deleted with {@code DutyRoster}: it was a third vocabulary, it disagreed with
     * hc-professional's on four values, and nothing but the deleted entity ever read it.
     *
     * <p>MIRROR LIST — if hc-professional's {@code DutyRole} is renamed, this switch and
     * {@code RoundPlanningServiceTest.translatesEveryRoleToADutyHcProfessionalDeclares} move with
     * it.
     */
    static String duty(ProfessionalRole role) {
        return switch (role) {
            case DOCTOR -> "DOCTOR";
            case NURSE -> "NURSE";
            case PARAMEDIC -> "PARAMEDIC";
            case THERAPIST -> "THERAPIST";
            case CAREGIVER -> "CARER";
        };
    }

    /**
     * Already spoken for on the date.
     *
     * <p>Two facts, and the honest limit is worth stating in the same breath as the rule.
     *
     * <ul>
     *   <li>A rostered {@code OFF} cell for that date is a commitment <em>not</em> to work, and the
     *       grid is where this service records it. The old rule read its own roster collection for
     *       "is there already a row for this person on this date"; that collection is gone, and the
     *       grid is the nearest thing this service still owns.
     *   <li>A round already given out in this run. Without it, one planning call with three rounds
     *       in one space hands all three to the same nearest, least-loaded person.
     * </ul>
     *
     * <p><b>What it cannot see: rounds already filed with {@code professionalservice}.</b> Those
     * live over there, and the only read that would answer the question is the whole-estate list.
     * Issuing that inside a ranking loop would make a planning run's cost a function of the estate's
     * history.
     *
     * <p><b>The far service is the backstop for rounds that carry visits, and for those only.</b>
     * {@code DutyRosterService.validateRound} builds each visit's interval, compares it against the
     * target's existing roster for the date, and refuses an overlap with a 400 — which arrives here
     * as {@code ROSTER_SERVICE_REFUSED_THE_ROUND} and not as an outage. That is a real check and it
     * is the one this method is deliberately not duplicating.
     *
     * <p><b>A round with no visits is checked by nothing, here or there.</b> {@code validateRound}
     * returns on an empty visit list before it reaches the overlap comparison, and it does so on
     * purpose: ward cover and on-call time are real shifts with no visits in them. This method's own
     * two facts do not close the gap either — an {@code OFF} cell is a different statement from a
     * filed round, and {@code committedInThisRun} is scoped to one call. So two planning runs can
     * each file a visit-less round for the same professional, on the same date, in the same space,
     * and no rule anywhere will notice.
     *
     * <p><b>DECIDED 2026-09-06, and decided as "legal": this service will not refuse the second
     * one</b> (backlog item 23, which was filed to stop the stating above being mistaken for a
     * decision). A second visit-less round on a date is not known to be a mistake — ward cover and
     * on-call are exactly the shifts a clinician can genuinely hold two of — so the choice is not
     * between a rule and no rule, it is between no rule and a <em>guess</em>.
     *
     * <p>Three things settle it, and the third is the one that would not have been obvious from the
     * gap alone.
     *
     * <ul>
     *   <li><b>The rule belongs to the owner of the roster.</b> hc-professional holds every round in
     *       the estate and is the only party that can answer "does this person already have one".
     *       Whatever it decides about a second visit-less shift applies to rounds filed on its own
     *       admin surface too; whatever this service decides applies to the subset that came through
     *       here.
     *   <li><b>A check on this side could only be built on a partial record.</b> This service knows
     *       what <em>it</em> filed, not what the estate holds — so the same second round would be
     *       refused or allowed depending on which surface filed the first. A guard that enforces a
     *       rule unevenly teaches that the rule does not exist while still refusing legitimate work,
     *       which is worse than the gap it closes.
     *   <li><b>The failure it would prevent is visible and recoverable; the one it would cause is
     *       not.</b> A duplicate on-call block is on the roster where a person can see it and remove
     *       it. A round wrongly refused reports {@code NO_CANDIDATE_IS_AVAILABLE} — the same answer
     *       as leave and as a rostered {@code OFF} — and nothing distinguishes it from a genuinely
     *       unstaffable round.
     * </ul>
     *
     * <p><b>What would reverse it:</b> hc-professional growing the rule in {@code validateRound},
     * where an empty visit list currently returns before {@code rejectOverlaps}. Then it applies to
     * the whole estate, the refusal arrives here as a 400 and is reported as
     * {@code ROSTER_SERVICE_REFUSED_THE_ROUND} like every other rule of theirs, and this method still
     * does not need to change. That is the shape any future work on this should take —
     * <b>not</b> a local uniqueness check bolted on here.
     *
     * <p>{@code RoundPlanningServiceTest.filesASecondVisitlessRoundForTheSamePersonAndDate} pins the
     * decision as behaviour, inverted: it goes red if somebody adds the partial guard, which is what
     * makes this a decision the repository holds rather than a paragraph.
     */
    private boolean alreadyCommitted(Professional candidate, LocalDate date, Set<String> committedInThisRun) {
        if (committedInThisRun.contains(candidate.getId())) {
            return true;
        }
        // Matched on the entity, not on its id: ShiftAssignment.professional is a @DBRef and only
        // Criteria.is(entity) writes the reference the way the converter stored it. Three shapes
        // that look equivalent all match nothing silently — see ShiftValuationService.payableShifts,
        // where the same trap is recorded with the three that were tried.
        Query query = new Query(Criteria.where("professional").is(candidate).and("shiftDate").is(date).and("shift").is(ShiftType.OFF));
        return mongoTemplate.exists(query, ShiftAssignment.class);
    }

    /**
     * The best candidate: nearest, then least loaded, then lowest id.
     *
     * <p><b>Proximity ahead of fairness</b>, which is the ordering the plan asked for and the
     * opposite of what the old scheduler did (it had no proximity at all). Fairness still decides
     * within a band, so two candidates equally near are separated by workload exactly as before.
     *
     * <p><b>The id is a real key, not decoration.</b> Two candidates alike on both measures used to
     * be separated by whatever order the repository happened to return, which is not an order Mongo
     * promises across queries — the same lesson hc-professional's {@code DutyRosterEstateOrderIT}
     * records for paging. A planning run that is not reproducible cannot be argued with.
     *
     * <p>The fairness count is computed <b>once per candidate</b> into a map before sorting. The old
     * one issued the query inside the comparator, which is O(n log n) round trips where n would do,
     * repeated per round — a pinned defect with its own characterization test.
     */
    private Professional rank(List<Professional> eligible, String spaceId, LocalDate date, Map<String, Optional<GeographicSpace>> spaces) {
        Map<String, Integer> proximity = new HashMap<>();
        Map<String, Integer> load = new HashMap<>();
        for (Professional candidate : eligible) {
            proximity.put(candidate.getId(), proximity(candidate.getHomeSpaceId(), spaceId, spaces));
            load.put(candidate.getId(), shiftsThisWeek(candidate, date));
        }
        return eligible
            .stream()
            .min(
                Comparator
                    .comparingInt((Professional candidate) -> proximity.get(candidate.getId()))
                    .thenComparingInt(candidate -> load.get(candidate.getId()))
                    .thenComparing(Professional::getId, Comparator.nullsLast(Comparator.naturalOrder()))
            )
            .orElseThrow();
    }

    /**
     * How near a professional's home space is to the round's, as a number to sort on.
     *
     * <p>{@code 0} is the same space; {@code 1} means they share a parent — which includes the case
     * where the professional is based in the round's parent itself; {@code 2} and up climb the tree
     * a level at a time. A candidate whose home space is unrelated, unknown or unset ranks last but
     * stays eligible: a team covering the space is what makes somebody a candidate, and proximity
     * only orders them.
     *
     * <p>Measured by climbing from the <b>round's</b> space and asking, at each step, whether that
     * ancestor is also an ancestor-or-self of the home space. Climbing from the home space instead
     * would answer a different question — "how far has this person come" rather than "who is
     * nearest to the work" — and the two disagree wherever the tree is not balanced, which every
     * real geography is not.
     *
     * <p>The walk carries a visited set and a depth bound: {@code GeographicSpaceCycleGuard} refuses
     * to store a cycle and says in its own javadoc that a reader must not rely on that, because it
     * is check-then-act under concurrency. Giving up produces a candidate ranked last; looping
     * produces a request that never answers.
     */
    private int proximity(String homeSpaceId, String spaceId, Map<String, Optional<GeographicSpace>> spaces) {
        if (homeSpaceId == null || homeSpaceId.isBlank() || spaceId == null) {
            return PROXIMITY_UNRELATED;
        }
        if (homeSpaceId.equals(spaceId)) {
            return 0;
        }
        Set<String> homeAncestry = ancestry(homeSpaceId, spaces);
        Set<String> seen = new HashSet<>();
        String cursor = spaceId;
        for (int step = 0; step < MAX_ANCESTRY_DEPTH; step++) {
            if (!seen.add(cursor)) {
                return PROXIMITY_UNRELATED;
            }
            String parentId = spaces
                .computeIfAbsent(cursor, geographicSpaceRepository::findById)
                .map(GeographicSpace::getParentId)
                .orElse(null);
            if (parentId == null || parentId.isBlank()) {
                return PROXIMITY_UNRELATED;
            }
            if (homeAncestry.contains(parentId)) {
                return step + 1;
            }
            cursor = parentId;
        }
        return PROXIMITY_UNRELATED;
    }

    /** A space and every ancestor above it, bounded and cycle-safe for the reasons above. */
    private Set<String> ancestry(String spaceId, Map<String, Optional<GeographicSpace>> spaces) {
        Set<String> ancestry = new HashSet<>();
        String cursor = spaceId;
        for (int step = 0; step < MAX_ANCESTRY_DEPTH && cursor != null && !cursor.isBlank(); step++) {
            if (!ancestry.add(cursor)) {
                return ancestry;
            }
            cursor = spaces.computeIfAbsent(cursor, geographicSpaceRepository::findById).map(GeographicSpace::getParentId).orElse(null);
        }
        return ancestry;
    }

    /**
     * Shifts worked in the Monday-to-Sunday week containing the date — the fairness measure.
     *
     * <p>The window is the old one, deliberately: a rolling seven days and a calendar month were
     * both available and the rewrite is not the place to change what fairness means. {@code OFF} is
     * excluded, because a rest day is not work — the same {@code shift != OFF} rule
     * {@code ShiftValuationService} pays by, so the planner's idea of a busy week and the payroll's
     * agree.
     */
    private int shiftsThisWeek(Professional candidate, LocalDate date) {
        LocalDate startOfWeek = date.with(DayOfWeek.MONDAY);
        LocalDate endOfWeek = date.with(DayOfWeek.SUNDAY);
        Query query = new Query(
            Criteria.where("professional").is(candidate).and("shiftDate").gte(startOfWeek).lte(endOfWeek).and("shift").ne(ShiftType.OFF)
        );
        return (int) mongoTemplate.count(query, ShiftAssignment.class);
    }

    /** Who was chosen, for the console to render. Falls back to the id rather than to a blank. */
    private static String displayName(Professional chosen) {
        if (chosen.getProfile() == null) {
            return chosen.getId();
        }
        String name =
            ((chosen.getProfile().getFirstName() == null ? "" : chosen.getProfile().getFirstName()) +
                " " +
                (chosen.getProfile().getLastName() == null ? "" : chosen.getProfile().getLastName())).trim();
        return name.isEmpty() ? chosen.getId() : name;
    }
}
