package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Integration tests for {@link ProfessionalSelfResource}.
 *
 * <p>The valuation rules themselves are covered by {@link ProfessionalEarningsIT} and are not
 * repeated. What is tested here is the one thing this resource adds and the other does not: that
 * the subject of the read is the caller, and that nothing in the request can change it.
 *
 * <p><b>Security filters are left ON</b>, unlike the sibling suite. The whole point of these
 * endpoints is who is allowed to reach them, so a suite that disabled the filter chain would assert
 * the interesting half of the behaviour away — {@code /api/professionals/me/**} sits in front of a
 * blanket rule that rejects clinicians, and with {@code addFilters = false} that ordering is never
 * exercised.
 *
 * <p>The clock is pinned for the same reason as the sibling suite: payability is a comparison
 * against today, so a floating date makes the assertions decay.
 *
 * <p><b>Callers are authenticated with {@code jwt()} rather than {@code @WithMockUser}.</b> This
 * service is an OAuth2 resource server with {@code SessionCreationPolicy.STATELESS}, so the context
 * {@code @WithMockUser} populates is replaced by an empty one before the request reaches a
 * controller — every call comes back 401 regardless of the annotation, which reads as a broken
 * security rule rather than as a broken test. It also matters that the principal is a real {@code
 * Jwt}: {@code SecurityUtils.extractPrincipal} takes the login from {@code jwt.getSubject()} for a
 * Jwt principal and from {@code getUsername()} for a {@code UserDetails} one, so a mock user would
 * exercise a branch production never takes.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(ProfessionalSelfIT.FixedClockConfiguration.class)
class ProfessionalSelfIT {

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

    private Professional ama;
    private Professional kwesi;

    @BeforeEach
    void seed() {
        shiftAssignmentRepository.deleteAll();
        professionalRepository.deleteAll();
        profileRepository.deleteAll();
        wageRateRepository.deleteAll();

        wageRateRepository.saveAll(
            List.of(
                rate(ProfessionalRole.DOCTOR, 500, LocalDate.of(2026, 1, 1)),
                rate(ProfessionalRole.NURSE, 300, LocalDate.of(2026, 1, 1))
            )
        );

        ama = professional(ProfessionalRole.DOCTOR, "MDC/RN/23-4471", profile("ama", "Ama", "Boateng"));
        kwesi = professional(ProfessionalRole.NURSE, "NMC/RN/19-2210", profile("kwesi", "Kwesi", "Mensah"));

        // Two shifts for Ama, five for Kwesi — deliberately different totals, so a response that
        // read the wrong professional could not coincidentally match.
        assign(ama, TODAY.minusDays(3), ShiftType.DAY);
        assign(ama, TODAY.minusDays(4), ShiftType.NIGHT);
        for (int day = 3; day <= 7; day++) {
            assign(kwesi, TODAY.minusDays(day), ShiftType.DAY);
        }
    }

    private Profile profile(String login, String firstName, String lastName) {
        Profile profile = new Profile();
        // The identity link: account_id carries the gateway login, which is the JWT subject.
        profile.setAccountId(login);
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        profile.setDateOfBirth(LocalDate.of(1985, 3, 2));
        profile.setMobilePhone("0200000000");
        profile.setEmail(login + "@example.com");
        profile.setIdNumber("GHA-" + login);
        profile.setSex(Sex.FEMALE);
        profile.setIdType(IdType.GHANA_CARD);
        return profileRepository.save(profile);
    }

    /**
     * Links exactly as production and the seeded dataset do: {@code Professional.profile} is set,
     * and {@code Profile.professional} is deliberately left null.
     *
     * <p><b>This fixture used to set both sides, and that is what let a broken feature ship.</b> The
     * resolver read the {@code Profile.professional} back-reference; nothing anywhere populates it,
     * so every caller got 404 in production while this suite stayed green — the test had encoded the
     * assumption instead of checking it. Do not "fix" a failure here by setting the back-reference
     * again: if a test needs it set, the resolver is reading the wrong direction.
     */
    private Professional professional(ProfessionalRole role, String licence, Profile profile) {
        Professional professional = new Professional()
            .role(role)
            .licenceNumber(licence)
            .verification(VerificationStatus.VERIFIED)
            .status(AccountStatus.ACTIVE)
            .joinedOn(LocalDate.of(2021, 6, 11));
        professional.setProfile(profile);
        return professionalRepository.save(professional);
    }

    private static WageRate rate(ProfessionalRole role, int amount, LocalDate validFrom) {
        return new WageRate().role(role).amount(new BigDecimal(amount)).currency("GHS").validFrom(validFrom);
    }

    private void assign(Professional professional, LocalDate date, ShiftType shift) {
        ShiftAssignment assignment = new ShiftAssignment().dayIndex(date.getDayOfWeek().getValue() - 1).shiftDate(date).shift(shift);
        assignment.setProfessional(professional);
        shiftAssignmentRepository.save(assignment);
    }

    /**
     * A signed-in clinician, as the gateway presents one: the login in {@code sub}, the clinical
     * role as an authority.
     */
    private static RequestPostProcessor clinician(String login, String role) {
        return jwt().jwt(builder -> builder.subject(login)).authorities(new SimpleGrantedAuthority(role));
    }

    /** The ordinary case: a clinician sees their own figures, resolved from the token. */
    @Test
    void aProfessionalSeesTheirOwnEarnings() throws Exception {
        restMockMvc
            .perform(get("/api/professionals/me/earnings?granularity=MONTHLY").with(clinician("ama", "ROLE_DOCTOR")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.professionalId").value(ama.getId()))
            .andExpect(jsonPath("$.professionalName").value("Ama Boateng"))
            .andExpect(jsonPath("$.shiftsCompleted").value(2))
            .andExpect(jsonPath("$.totalAccrued").value(1000))
            .andExpect(jsonPath("$.currency").value("GHS"));
    }

    /**
     * The reason this resource exists. Two clinicians hit one URL with no id in it and each gets
     * their own answer — there is no request the first could make that would return the second's.
     */
    @Test
    void theSameUrlAnswersDifferentlyPerCaller() throws Exception {
        restMockMvc
            .perform(get("/api/professionals/me/earnings?granularity=MONTHLY").with(clinician("kwesi", "ROLE_NURSE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.professionalId").value(kwesi.getId()))
            .andExpect(jsonPath("$.shiftsCompleted").value(5))
            .andExpect(jsonPath("$.totalAccrued").value(1500));
    }

    /**
     * The id-addressed endpoint is the one a clinician must not reach. Widening the authority on it
     * instead of adding this resource would have made a colleague's pay a URL edit away, and the
     * authority check would have passed.
     */
    @Test
    void aProfessionalCannotReadAColleaguesEarningsById() throws Exception {
        restMockMvc
            .perform(get("/api/professionals/{id}/earnings", kwesi.getId()).with(clinician("ama", "ROLE_DOCTOR")))
            .andExpect(status().isForbidden());
    }

    /**
     * Nor the wage bill, which is every professional's pay on one page.
     */
    @Test
    void aProfessionalCannotReadTheWageBill() throws Exception {
        restMockMvc.perform(get("/api/professionals/earnings").with(clinician("ama", "ROLE_DOCTOR"))).andExpect(status().isForbidden());
    }

    /**
     * Wage rates stay the administrator's. A professional views what they accrued, never the table
     * that priced it, and never a way to change it.
     */
    @Test
    void aProfessionalCannotReadTheWageRates() throws Exception {
        restMockMvc.perform(get("/api/wage-rates").with(clinician("ama", "ROLE_DOCTOR"))).andExpect(status().isForbidden());
    }

    /**
     * An account with no clinical record behind it — an applicant mid-onboarding, or any non-clinical
     * user. 404, and specifically not somebody else's figures.
     */
    @Test
    void anUnlinkedAccountGetsNotFound() throws Exception {
        restMockMvc.perform(get("/api/professionals/me/earnings").with(clinician("nobody", "ROLE_USER"))).andExpect(status().isNotFound());
        restMockMvc.perform(get("/api/professionals/me/shifts").with(clinician("nobody", "ROLE_USER"))).andExpect(status().isNotFound());
    }

    /** No token at all. The resource server rejects this before any of the above is reached. */
    @Test
    void anonymousIsRejected() throws Exception {
        restMockMvc.perform(get("/api/professionals/me/earnings")).andExpect(status().isUnauthorized());
        restMockMvc.perform(get("/api/professionals/me/shifts")).andExpect(status().isUnauthorized());
    }

    /**
     * The roster keeps what the payslip drops. Off days and future shifts are part of a schedule,
     * and each row says whether it counts toward earnings so no client has to re-derive the rule.
     */
    @Test
    void theRosterIncludesOffDaysAndFutureShiftsAndFlagsWhatIsPayable() throws Exception {
        assign(ama, TODAY.minusDays(2), ShiftType.OFF);
        assign(ama, TODAY.plusDays(2), ShiftType.NIGHT);

        restMockMvc
            .perform(get("/api/professionals/me/shifts?from=2026-08-01&to=2026-08-31").with(clinician("ama", "ROLE_DOCTOR")))
            .andExpect(status().isOk())
            // Oldest first: 14th, 15th, 16th (OFF), 20th (future).
            .andExpect(jsonPath("$.length()").value(4))
            .andExpect(jsonPath("$[0].date").value("2026-08-14"))
            .andExpect(jsonPath("$[0].payable").value(true))
            .andExpect(jsonPath("$[2].shift").value("OFF"))
            .andExpect(jsonPath("$[2].payable").value(false))
            .andExpect(jsonPath("$[3].date").value("2026-08-20"))
            .andExpect(jsonPath("$[3].payable").value(false));
    }

    /**
     * A regression guard for the direction of the identity link, asserted on the data rather than
     * through the API, because the API cannot tell the two apart: reading the unpopulated
     * back-reference produces exactly the same 404 as a caller who genuinely has no record.
     */
    @Test
    void theIdentityLinkIsStoredOnTheProfessionalAndNotOnTheProfile() {
        Profile stored = profileRepository.findByAccount("ama").orElseThrow();

        assertThat(stored.getProfessional()).isNull();
        assertThat(professionalRepository.findByProfile(stored)).map(Professional::getId).hasValue(ama.getId());
    }

    /** A roster read must not leak either, and there is likewise no id on it to try. */
    @Test
    void theRosterIsAlsoScopedToTheCaller() throws Exception {
        restMockMvc
            .perform(get("/api/professionals/me/shifts?from=2026-08-01&to=2026-08-31").with(clinician("kwesi", "ROLE_NURSE")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(5));
    }
}
