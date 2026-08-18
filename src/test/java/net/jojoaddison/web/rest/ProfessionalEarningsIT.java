package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.ShiftAssignment;
import net.jojoaddison.domain.WageRate;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.IdType;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.Sex;
import net.jojoaddison.domain.enumeration.ShiftType;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.repository.ShiftAssignmentRepository;
import net.jojoaddison.repository.WageRateRepository;
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
 * Integration tests for {@link ProfessionalEarningsResource}.
 *
 * <p><b>The clock is pinned.</b> Whether a shift has been worked is a comparison against today, so
 * without a fixed date these assertions would decay: "yesterday" relative to a hardcoded seed date
 * is a moving target, and the suite would start failing on a date nobody chose. Every date below is
 * relative to {@link #TODAY}.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
@Import(ProfessionalEarningsIT.FixedClockConfiguration.class)
class ProfessionalEarningsIT {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 18);

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
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired
    private WageRateRepository wageRateRepository;

    private Professional doctor;

    @BeforeEach
    void seed() {
        shiftAssignmentRepository.deleteAll();
        professionalRepository.deleteAll();
        wageRateRepository.deleteAll();

        wageRateRepository.saveAll(
            List.of(
                rate(ProfessionalRole.DOCTOR, 500, LocalDate.of(2026, 1, 1)),
                rate(ProfessionalRole.DOCTOR, 550, LocalDate.of(2026, 8, 1)),
                rate(ProfessionalRole.NURSE, 300, LocalDate.of(2026, 1, 1))
            )
        );

        Profile profile = new Profile();
        profile.setAccountId("acct-doc");
        profile.setFirstName("Ama");
        profile.setLastName("Boateng");
        profile.setDateOfBirth(LocalDate.of(1985, 3, 2));
        profile.setMobilePhone("0200000000");
        profile.setEmail("ama@example.com");
        profile.setIdNumber("GHA-1");
        profile.setSex(Sex.FEMALE);
        profile.setIdType(IdType.GHANA_CARD);
        profileRepository.save(profile);

        doctor =
            new Professional()
                .role(ProfessionalRole.DOCTOR)
                .licenceNumber("MDC/RN/23-4471")
                .verification(VerificationStatus.VERIFIED)
                .status(AccountStatus.ACTIVE)
                .joinedOn(LocalDate.of(2021, 6, 11));
        doctor.setProfile(profile);
        professionalRepository.save(doctor);
    }

    private static WageRate rate(ProfessionalRole role, int amount, LocalDate validFrom) {
        return new WageRate().role(role).amount(new BigDecimal(amount)).currency("GHS").validFrom(validFrom);
    }

    private void assign(LocalDate date, ShiftType shift) {
        ShiftAssignment assignment = new ShiftAssignment().dayIndex(date.getDayOfWeek().getValue() - 1).shiftDate(date).shift(shift);
        assignment.setProfessional(doctor);
        shiftAssignmentRepository.save(assignment);
    }

    @Test
    void countsWorkedShiftsAndValuesThemAtTheRateInForce() throws Exception {
        assign(TODAY.minusDays(3), ShiftType.DAY); // 15 Aug, 550
        assign(TODAY.minusDays(4), ShiftType.NIGHT); // 14 Aug, 550

        restMockMvc
            .perform(get("/api/professionals/{id}/earnings?granularity=MONTHLY", doctor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shiftsCompleted").value(2))
            .andExpect(jsonPath("$.totalAccrued").value(1100))
            .andExpect(jsonPath("$.currency").value("GHS"))
            .andExpect(jsonPath("$.role").value("DOCTOR"))
            .andExpect(jsonPath("$.professionalName").value("Ama Boateng"));
    }

    /**
     * An OFF cell is a real row in the roster grid. Counting it would inflate both the shift count
     * and the wage bill, and nothing about the record says "unpaid" except its type.
     */
    @Test
    void offDaysAreNeitherCountedNorPaid() throws Exception {
        assign(TODAY.minusDays(3), ShiftType.DAY);
        assign(TODAY.minusDays(2), ShiftType.OFF);
        assign(TODAY.minusDays(4), ShiftType.OFF);

        restMockMvc
            .perform(get("/api/professionals/{id}/earnings?granularity=MONTHLY", doctor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shiftsCompleted").value(1))
            .andExpect(jsonPath("$.totalAccrued").value(550));
    }

    /**
     * A shift scheduled for next week is real and rostered, and accruing it would report money as
     * earned before the work was done.
     */
    @Test
    void futureShiftsDoNotAccrue() throws Exception {
        assign(TODAY.minusDays(1), ShiftType.DAY);
        assign(TODAY.plusDays(1), ShiftType.DAY);
        assign(TODAY.plusDays(6), ShiftType.NIGHT);

        restMockMvc
            .perform(get("/api/professionals/{id}/earnings?granularity=MONTHLY", doctor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shiftsCompleted").value(1))
            .andExpect(jsonPath("$.totalAccrued").value(550));
    }

    /**
     * Today's shift has not been worked yet — the day is still running. The window ends yesterday,
     * and the response says so rather than echoing the requested end date.
     */
    @Test
    void todaysShiftIsNotYetPayableAndTheWindowSaysSo() throws Exception {
        assign(TODAY, ShiftType.DAY);

        restMockMvc
            .perform(get("/api/professionals/{id}/earnings?granularity=DAILY&to=2026-12-31", doctor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shiftsCompleted").value(0))
            .andExpect(jsonPath("$.to").value(TODAY.minusDays(1).toString()));
    }

    /**
     * The whole point of dating the rates: shifts either side of a rise are valued differently, and
     * the earlier ones keep the earlier price.
     */
    @Test
    void shiftsStraddlingARateChangeAreValuedAtTheirOwnDatesRate() throws Exception {
        assign(LocalDate.of(2026, 7, 15), ShiftType.DAY); // before the rise: 500
        assign(LocalDate.of(2026, 7, 16), ShiftType.DAY); // 500
        assign(LocalDate.of(2026, 8, 5), ShiftType.DAY); // after the rise: 550

        restMockMvc
            .perform(get("/api/professionals/{id}/earnings?granularity=MONTHLY&from=2026-07-01", doctor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shiftsCompleted").value(3))
            .andExpect(jsonPath("$.totalAccrued").value(1550))
            .andExpect(jsonPath("$.buckets[?(@.periodStart == '2026-07-01')].amount").value(org.hamcrest.Matchers.hasItem(1000)))
            .andExpect(jsonPath("$.buckets[?(@.periodStart == '2026-08-01')].amount").value(org.hamcrest.Matchers.hasItem(550)));
    }

    /**
     * A shift worked before any rate existed is counted but unpriced. Reporting it as zero earned
     * would be indistinguishable from a professional who did not work.
     */
    @Test
    void shiftsBeforeAnyConfiguredRateAreCountedButUnpriced() throws Exception {
        assign(LocalDate.of(2025, 11, 10), ShiftType.DAY);
        assign(LocalDate.of(2026, 8, 5), ShiftType.DAY);

        restMockMvc
            .perform(get("/api/professionals/{id}/earnings?granularity=MONTHLY&from=2025-11-01", doctor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shiftsCompleted").value(2))
            .andExpect(jsonPath("$.unpricedShifts").value(1))
            .andExpect(jsonPath("$.totalAccrued").value(550));
    }

    /**
     * Empty periods are emitted, not skipped. A line chart draws straight through a missing point,
     * so a fortnight off would read as steady earnings rather than as two weeks at zero.
     */
    @Test
    void emptyPeriodsAreEmittedAsZeroBuckets() throws Exception {
        assign(TODAY.minusDays(1), ShiftType.DAY);

        restMockMvc
            .perform(get("/api/professionals/{id}/earnings?granularity=WEEKLY&from=2026-07-06", doctor.getId()))
            .andExpect(status().isOk())
            // 6 Jul is a Monday; weeks from then to the week of 17 Aug inclusive
            .andExpect(jsonPath("$.buckets.length()").value(7))
            .andExpect(jsonPath("$.buckets[0].shifts").value(0))
            .andExpect(jsonPath("$.buckets[0].amount").value(0));
    }

    /** Weekly buckets snap to Monday, whatever date the window was asked to start on. */
    @Test
    void weeklyBucketsSnapToTheStartOfTheWeek() throws Exception {
        restMockMvc
            .perform(get("/api/professionals/{id}/earnings?granularity=WEEKLY&from=2026-08-05", doctor.getId()))
            .andExpect(status().isOk())
            // 5 Aug 2026 is a Wednesday; the series starts on the Monday of that week
            .andExpect(jsonPath("$.from").value("2026-08-03"))
            .andExpect(jsonPath("$.buckets[0].periodStart").value("2026-08-03"));
    }

    /** Monthly buckets snap to the first of the month for the same reason. */
    @Test
    void monthlyBucketsSnapToTheStartOfTheMonth() throws Exception {
        restMockMvc
            .perform(get("/api/professionals/{id}/earnings?granularity=MONTHLY&from=2026-06-17", doctor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.from").value("2026-06-01"))
            .andExpect(jsonPath("$.buckets[0].periodStart").value("2026-06-01"));
    }

    @Test
    void anotherProfessionalsShiftsAreNotCounted() throws Exception {
        Professional nurse = new Professional()
            .role(ProfessionalRole.NURSE)
            .licenceNumber("NMC/RN/24-0001")
            .verification(VerificationStatus.VERIFIED)
            .status(AccountStatus.ACTIVE)
            .joinedOn(LocalDate.of(2022, 1, 5));
        professionalRepository.save(nurse);

        assign(TODAY.minusDays(2), ShiftType.DAY);

        ShiftAssignment nurseShift = new ShiftAssignment().dayIndex(0).shiftDate(TODAY.minusDays(2)).shift(ShiftType.DAY);
        nurseShift.setProfessional(nurse);
        shiftAssignmentRepository.save(nurseShift);

        restMockMvc
            .perform(get("/api/professionals/{id}/earnings?granularity=MONTHLY", doctor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shiftsCompleted").value(1))
            .andExpect(jsonPath("$.totalAccrued").value(550));

        restMockMvc
            .perform(get("/api/professionals/{id}/earnings?granularity=MONTHLY", nurse.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shiftsCompleted").value(1))
            .andExpect(jsonPath("$.totalAccrued").value(300));
    }

    @Test
    void earningsForAnUnknownProfessionalIsNotFound() throws Exception {
        restMockMvc.perform(get("/api/professionals/{id}/earnings", "no-such-id")).andExpect(status().isNotFound());
    }

    /**
     * The wage-bill list is paginated. It costs a query per row to value, so an unbounded version
     * would issue one per professional in the network.
     */
    @Test
    void theWageBillListIsPaginated() throws Exception {
        assign(TODAY.minusDays(2), ShiftType.DAY);

        restMockMvc
            .perform(get("/api/professionals/earnings?granularity=MONTHLY"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(header().exists("Link"))
            .andExpect(
                jsonPath("$[?(@.professionalId == '" + doctor.getId() + "')].totalAccrued").value(org.hamcrest.Matchers.hasItem(550))
            );
    }

    /**
     * {@code /earnings} is a literal path segment and {@code /{id}} is a template. The list endpoint
     * has to win, or it resolves as a professional whose id is the word "earnings" and 404s.
     */
    @Test
    void theListPathIsNotSwallowedByTheIdPattern() throws Exception {
        restMockMvc.perform(get("/api/professionals/earnings")).andExpect(status().isOk()).andExpect(header().exists("X-Total-Count"));
    }

    @Test
    void aProfessionalWithNoShiftsAccruesNothing() throws Exception {
        restMockMvc
            .perform(get("/api/professionals/{id}/earnings?granularity=MONTHLY", doctor.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shiftsCompleted").value(0))
            .andExpect(jsonPath("$.totalAccrued").value(0))
            // still labelled, so the console can render "0 GHS" rather than "0"
            .andExpect(jsonPath("$.currency").value("GHS"));
    }
}
