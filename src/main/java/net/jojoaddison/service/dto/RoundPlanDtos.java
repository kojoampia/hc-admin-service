package net.jojoaddison.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.ShiftType;

/**
 * What the planner is asked for, and what it reports back.
 *
 * <p>Records rather than JHipster DTOs with MapStruct mappers, because none of these is a projection
 * of a stored document — there is no {@code RoundPlan} collection and there must not be one. The
 * round itself is stored by {@code professionalservice}; what hc-admin keeps of a planning run is
 * the {@code AuditLog} row every write already produces and nothing else.
 *
 * <p><b>The vocabulary is hc-admin's, not hc-professional's.</b> A request names a
 * {@link ProfessionalRole} — the role this service's directory carries, prices in {@code WageRate}
 * and reports earnings by — and {@link net.jojoaddison.service.RoundPlanningService} translates it
 * into the {@code duty} the far service expects. The alternative was to mirror hc-professional's
 * ten-value {@code DutyRole} here, which is a fourth copy of a vocabulary in an estate that has just
 * finished reconciling three.
 */
public final class RoundPlanDtos {

    private RoundPlanDtos() {}

    /**
     * One call inside a round.
     *
     * <p>{@code customerId} is <b>opaque to this service</b>, exactly as {@code geographicSpaceId} is
     * opaque to hc-professional. It is a {@code patientservice} {@code Profile.patientId}, and
     * hc-admin holds no mapping from its own {@code Patient} documents to that id — see backlog
     * item 22. Passing it through unexamined is honest; inventing a join here would be a guess
     * stored as a fact in another service's roster.
     *
     * <p>Times are clock times. Which calendar day they land on is hc-professional's to decide from
     * the round's date and shift window, and {@code NIGHT} genuinely lands some of them on the next
     * day — so this service must not resolve them, and does not try.
     */
    public record VisitRequest(@NotNull @Size(max = 100) String customerId, @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}

    /**
     * One round to staff and file.
     *
     * <p>A round with no visits is valid — ward cover, on call, administrative time — which is
     * hc-professional's rule and is repeated here so that {@code visits} being empty is never read
     * as a client that forgot something.
     */
    public record RoundRequest(
        @NotNull ProfessionalRole role,
        @NotNull ShiftType shift,
        @NotNull @Size(max = 100) String geographicSpaceId,
        @NotNull @Size(max = 120) String name,
        @Size(max = 500) String description,
        @Valid List<VisitRequest> visits
    ) {
        public List<VisitRequest> visits() {
            return visits == null ? List.of() : visits;
        }
    }

    /** A day's planning run. One date, because the constraints are all per-date. */
    public record PlanRequest(@NotNull LocalDate date, @NotNull @NotEmpty @Valid List<RoundRequest> rounds) {}

    /**
     * How one round ended.
     *
     * <p>Three states and not two. {@code UNPLANNED} is a decision — nobody eligible — and the
     * screen can act on it by widening the request. {@code FAILED} is not a decision at all: the
     * roster of record could not be written to, and nothing is known about whether the round should
     * exist. Collapsing the two is the mistake decision 10 was taken to prevent.
     */
    public enum Outcome {
        PLANNED,
        UNPLANNED,
        FAILED,
    }

    /**
     * Why a round was not planned, as a code the console can translate.
     *
     * <p>A code rather than a sentence: the console owns the wording, in four i18n catalogues, and a
     * server-composed message would be the one string on that screen that never translates.
     */
    public enum Reason {
        /** No {@code Team} lists the round's space in {@code geographicSpaceIds}. */
        NO_TEAM_COVERS_THE_SPACE,
        /** Teams cover it, but nobody on them holds the role the round asks for. */
        NO_CANDIDATE_HOLDS_THE_ROLE,
        /** Somebody holds the role and none of them can work that date — leave, or already committed. */
        NO_CANDIDATE_IS_AVAILABLE,
        /** The round was staffed, {@code professionalservice} was dialled, and it did not answer. */
        ROSTER_SERVICE_UNREACHABLE,
        /** The round was staffed and {@code professionalservice} refused it as invalid. */
        ROSTER_SERVICE_REFUSED_THE_ROUND,
        /**
         * The round was staffed and <b>nothing was dialled</b>: this deployment is not configured to
         * reach {@code professionalservice}.
         *
         * <p>Distinct from {@link #ROSTER_SERVICE_UNREACHABLE}, and the distinction is the whole
         * point of the value. {@code application.professionalservice.enabled=false} and a request
         * carrying no caller token both stop the write here, on this side, before any socket is
         * opened — and reporting them as "unreachable" sends a reader to the other stack, or to the
         * network, for a missing environment variable in a compose file. An outage panel is a
         * designed, plausible screen, which is exactly what makes it indistinguishable from the
         * feature working badly.
         *
         * <p><b>It does not lower {@code rosterServiceReachable}</b> — see {@link PlanReport}.
         */
        ROSTER_SERVICE_NOT_CONFIGURED,
    }

    /**
     * The outcome of one requested round, addressed by its position in the request.
     *
     * <p>By index rather than by a client-supplied key, so that a caller cannot make two rounds
     * indistinguishable by sending the same name twice.
     */
    public record RoundOutcome(int index, Outcome outcome, Reason reason, String professionalId, String professionalName, String roundId) {
        public static RoundOutcome planned(int index, String professionalId, String professionalName, String roundId) {
            return new RoundOutcome(index, Outcome.PLANNED, null, professionalId, professionalName, roundId);
        }

        public static RoundOutcome unplanned(int index, Reason reason) {
            return new RoundOutcome(index, Outcome.UNPLANNED, reason, null, null, null);
        }

        public static RoundOutcome failed(int index, Reason reason, String professionalId, String professionalName) {
            return new RoundOutcome(index, Outcome.FAILED, reason, professionalId, professionalName, null);
        }
    }

    /**
     * The report for a planning run.
     *
     * <p><b>{@code rosterServiceReachable} is the outage state, and it is a field rather than an
     * HTTP status on purpose.</b> A run in which round 1 was filed and round 2's write failed has
     * genuinely done half of what was asked; answering {@code 502} would throw away the record of
     * the half that landed, and answering {@code 200} with no flag would let a client render a
     * partial failure as success. The console reads this field, not the status code, and shows a
     * standing panel rather than a toast.
     *
     * <p><b>It is an observation, so only a call that was actually made can lower it.</b> A round
     * that failed with {@link Reason#ROSTER_SERVICE_NOT_CONFIGURED} leaves it {@code true}: nothing
     * was dialled, so nothing was learned about the far service, and claiming an outage on that
     * evidence is what item 24 of the backlog was filed about. The console distinguishes the two
     * from the round's reason and shows a different standing panel — "check this deployment" rather
     * than "check the estate".
     */
    public record PlanReport(LocalDate date, boolean rosterServiceReachable, List<RoundOutcome> rounds) {
        public long plannedCount() {
            return rounds
                .stream()
                .filter(round -> round.outcome() == Outcome.PLANNED)
                .count();
        }
    }
}
