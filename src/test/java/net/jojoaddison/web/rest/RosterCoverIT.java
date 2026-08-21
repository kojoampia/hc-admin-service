package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.RosterWeek;
import net.jojoaddison.domain.ShiftAssignment;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.ShiftType;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.RosterWeekRepository;
import net.jojoaddison.repository.ShiftAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Roster cover, which had two answers.
 *
 * <p>The dashboard hero said "roster cover at 0% for the week" while the duty-roster screen, one
 * click away, said 80% over the same data. Neither screen was obviously broken and no test
 * disagreed with either, because each was internally consistent — they simply did not mean the same
 * thing by "cover".
 *
 * <p><b>Two independent defects were behind that, and this suite exists for both.</b>
 *
 * <p>The first is the arithmetic. An unassigned slot is the <em>absence</em> of a ShiftAssignment —
 * cycling a grid cell past OFF deletes the document — so the server's old formula, which counted
 * assignments carrying a null professional, was counting a state the data model cannot produce. It
 * found none, every time, and divided by the documents it did find: <b>0% and 100% were the only two
 * values it could return</b>, and the 0% in the gap analysis was not missing data but the other one.
 * {@link #coverIsTheGridsFraction()} is written with a deliberately awkward fraction for that
 * reason: 9 of 14 is a number neither the old formula nor a rounding accident can reach.
 *
 * <p>The second is the week. The dashboard derived a Monday-to-Sunday window from {@code
 * shift_date}; the grid asked for the most recent {@code RosterWeek}. On seeded data those coincide,
 * which is the worst case — they agree until somebody drafts a week ahead, and then they disagree
 * with no error anywhere. Both now read {@code /api/roster-weeks/current}.
 *
 * <p>The clock is pinned because every assertion here is relative to "today", and a floating date
 * would make this suite decay into a test of what week it happens to be.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
@Import(RosterCoverIT.FixedClockConfiguration.class)
class RosterCoverIT {

    /** A Wednesday, so "the week that has started" is a strictly earlier Monday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private static final LocalDate THIS_MONDAY = LocalDate.of(2026, 8, 17);
    private static final LocalDate LAST_MONDAY = LocalDate.of(2026, 8, 10);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 8, 24);

    private static final String METRICS = "/api/dashboard/metrics";
    private static final String CURRENT_WEEK = "/api/roster-weeks/current";

    @TestConfiguration
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private RosterWeekRepository rosterWeekRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ShiftAssignmentRepository shiftAssignmentRepository;

    private RosterWeek thisWeek;

    @BeforeEach
    void seed() {
        shiftAssignmentRepository.deleteAll();
        professionalRepository.deleteAll();
        rosterWeekRepository.deleteAll();

        rosterWeekRepository.save(week("Week of 10 August 2026", LAST_MONDAY));
        thisWeek = rosterWeekRepository.save(week("Week of 17 August 2026", THIS_MONDAY));
        rosterWeekRepository.save(week("Week of 24 August 2026", NEXT_MONDAY));
    }

    /**
     * The week in force is the latest one that has <em>started</em> — the drafted week ahead is not
     * it.
     *
     * <p>"The latest week" would have been the simpler rule and is what the grid used to do alone.
     * It is wrong for a figure captioned "the week": drafting next month's roster would silently
     * move what the dashboard is describing, and nothing on either screen would say so.
     */
    @Test
    void theWeekInForceIsTheLatestOneThatHasStarted() throws Exception {
        restMockMvc
            .perform(get(CURRENT_WEEK))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("Week of 17 August 2026"))
            .andExpect(jsonPath("$.startDate").value(THIS_MONDAY.toString()));
    }

    /**
     * With every week still ahead — a fresh deployment whose first roster has not begun — the
     * earliest is returned rather than nothing. A grid has to render something, and "the roster you
     * are about to work" beats an empty screen.
     */
    @Test
    void fallsBackToTheEarliestWeekWhenNoneHasStarted() throws Exception {
        rosterWeekRepository.deleteAll();
        rosterWeekRepository.save(week("Week of 24 August 2026", NEXT_MONDAY));
        rosterWeekRepository.save(week("Week of 31 August 2026", NEXT_MONDAY.plusWeeks(1)));

        restMockMvc.perform(get(CURRENT_WEEK)).andExpect(status().isOk()).andExpect(jsonPath("$.label").value("Week of 24 August 2026"));
    }

    /** No roster at all is production's normal state, and a 404 the console reads as "no roster". */
    @Test
    void answersNotFoundWhenThereIsNoRosterAtAll() throws Exception {
        rosterWeekRepository.deleteAll();

        restMockMvc.perform(get(CURRENT_WEEK)).andExpect(status().isNotFound());
    }

    /**
     * {@code current} must not be swallowed by the {@code /{id}} mapping beside it — both match a
     * single path segment and only ordering keeps them apart. Reshuffled, this becomes a lookup for
     * a week whose id is the literal string "current", and the symptom is a 404 for a record that
     * exists. The same trap has already been met on {@code /api/profiles/by-account}.
     */
    @Test
    void isNotShadowedByTheIdRoute() throws Exception {
        restMockMvc.perform(get("/api/roster-weeks/" + thisWeek.getId())).andExpect(status().isOk());
        restMockMvc.perform(get(CURRENT_WEEK)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(thisWeek.getId()));
    }

    /**
     * <b>The whole finding, as one assertion.</b>
     *
     * <p>Two rosterable professionals give the grid 14 slots; nine are planned, so cover is 64%.
     * That fraction is chosen to be unreachable by the formula this replaced, which could only ever
     * answer 0 or 100 — a test asserting 100% against a full week would have passed against the bug.
     */
    @Test
    void coverIsTheGridsFraction() throws Exception {
        Professional ama = professionalRepository.save(professional(AccountStatus.ACTIVE, "MDC/RN/23-4471"));
        Professional kwesi = professionalRepository.save(professional(AccountStatus.ON_LEAVE, "NMC/RN/19-2210"));

        // Ama: five planned days, one of them OFF. Kwesi: four, all worked.
        assign(ama, 0, ShiftType.DAY);
        assign(ama, 1, ShiftType.DAY);
        assign(ama, 2, ShiftType.OFF);
        assign(ama, 3, ShiftType.NIGHT);
        assign(ama, 4, ShiftType.EVENING);
        assign(kwesi, 0, ShiftType.NIGHT);
        assign(kwesi, 1, ShiftType.NIGHT);
        assign(kwesi, 2, ShiftType.DAY);
        assign(kwesi, 3, ShiftType.DAY);

        restMockMvc
            .perform(get(METRICS))
            .andExpect(status().isOk())
            // 9 planned of 2 x 7 = 64%, and five slots nobody has been put in.
            .andExpect(jsonPath("$.roster.coverPercent").value(64))
            .andExpect(jsonPath("$.roster.unassignedSlots").value(5))
            .andExpect(jsonPath("$.roster.rosteredStaff").value(2))
            // Eight, not nine: OFF is planned, but it is not a shift.
            .andExpect(jsonPath("$.roster.shiftsThisWeek").value(8))
            // And the figure says which week it is about, so it cannot be read against another one.
            .andExpect(jsonPath("$.roster.weekLabel").value("Week of 17 August 2026"))
            .andExpect(jsonPath("$.roster.weekStartDate").value(THIS_MONDAY.toString()));
    }

    /**
     * A pending applicant is not rostered, so they are neither a row in the grid nor a slot to fill —
     * and the assignments the generated seed gives them are not planning.
     *
     * <p>Counting them is not a rounding difference: it puts documents in the numerator whose row is
     * not in the denominator, so a fully planned week reports <em>more</em> than 100% cover. The
     * fixture is a full grid plus one pending applicant's whole week, which is exactly that shape.
     */
    @Test
    void pendingApplicantsAreNeitherCapacityNorCover() throws Exception {
        Professional ama = professionalRepository.save(professional(AccountStatus.ACTIVE, "MDC/RN/23-4471"));
        Professional applicant = professionalRepository.save(professional(AccountStatus.PENDING, "APP/2026/0007"));

        for (int day = 0; day < 7; day++) {
            assign(ama, day, ShiftType.DAY);
            assign(applicant, day, ShiftType.DAY);
        }

        restMockMvc
            .perform(get(METRICS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roster.coverPercent").value(100))
            .andExpect(jsonPath("$.roster.unassignedSlots").value(0))
            .andExpect(jsonPath("$.roster.rosteredStaff").value(1))
            .andExpect(jsonPath("$.roster.shiftsThisWeek").value(7));
    }

    /**
     * Somebody with an entirely empty week is still a row.
     *
     * <p>They are precisely the person the screen exists to surface, and counting only professionals
     * who already hold a shift would drop them from {@code rosteredStaff} and shrink the capacity
     * their gaps are measured against — a roster with a hole in it would report itself fully covered.
     */
    @Test
    void anUnrosteredProfessionalStillCountsAsAGridRow() throws Exception {
        Professional ama = professionalRepository.save(professional(AccountStatus.ACTIVE, "MDC/RN/23-4471"));
        professionalRepository.save(professional(AccountStatus.ACTIVE, "NMC/RN/19-2210"));

        for (int day = 0; day < 7; day++) {
            assign(ama, day, ShiftType.DAY);
        }

        restMockMvc
            .perform(get(METRICS))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.roster.rosteredStaff").value(2))
            .andExpect(jsonPath("$.roster.coverPercent").value(50))
            .andExpect(jsonPath("$.roster.unassignedSlots").value(7));
    }

    /**
     * Assignments belonging to another week are not this week's cover.
     *
     * <p>The link is the {@code RosterWeek} DBRef, not the shift date — which is what the dashboard
     * used to filter on. A week's worth of last week's shifts would otherwise land in this week's
     * numerator whenever the two windows overlapped.
     */
    @Test
    void anotherWeeksAssignmentsDoNotCount() throws Exception {
        Professional ama = professionalRepository.save(professional(AccountStatus.ACTIVE, "MDC/RN/23-4471"));
        RosterWeek lastWeek = rosterWeekRepository.findFirstByOrderByStartDateAsc().orElseThrow();

        assign(ama, 0, ShiftType.DAY);
        ShiftAssignment stale = new ShiftAssignment().dayIndex(1).shiftDate(LAST_MONDAY.plusDays(1)).shift(ShiftType.DAY);
        stale.setProfessional(ama);
        stale.setWeek(lastWeek);
        shiftAssignmentRepository.save(stale);

        restMockMvc
            .perform(get(METRICS))
            .andExpect(status().isOk())
            // One of seven, not two.
            .andExpect(jsonPath("$.roster.coverPercent").value(14))
            .andExpect(jsonPath("$.roster.shiftsThisWeek").value(1));
    }

    private static RosterWeek week(String label, LocalDate startDate) {
        return new RosterWeek().label(label).startDate(startDate).published(true);
    }

    private static Professional professional(AccountStatus status, String licence) {
        return new Professional()
            .role(ProfessionalRole.NURSE)
            .licenceNumber(licence)
            .verification(VerificationStatus.VERIFIED)
            .status(status)
            .joinedOn(LocalDate.of(2021, 6, 11));
    }

    private void assign(Professional professional, int dayIndex, ShiftType shift) {
        ShiftAssignment assignment = new ShiftAssignment().dayIndex(dayIndex).shiftDate(THIS_MONDAY.plusDays(dayIndex)).shift(shift);
        assignment.setProfessional(professional);
        assignment.setWeek(thisWeek);
        shiftAssignmentRepository.save(assignment);
    }
}
