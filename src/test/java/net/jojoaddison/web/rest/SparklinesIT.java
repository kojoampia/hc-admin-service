package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.repository.ProfessionalRepository;
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
 * The trend line inside each KPI tile — item 9 of admin-gaps.md.
 *
 * <p>{@code sparklines} was an empty map, so all four tiles drew flat. The component and its
 * binding had been there the whole time; there was simply nothing to draw.
 *
 * <p><b>The assertion that matters is the last point.</b> A sparkline here means the tile's own
 * number over the last six months, and the prototype's literal series each end at their tile's
 * value. A line whose final point disagrees with the figure printed above it is worse than no line:
 * it is a second, quieter answer to the same question, which is the defect this dashboard has
 * already had twice.
 *
 * <p>The clock is pinned because the buckets are months relative to today. Two of the three patients
 * below join inside the window and one predates it, so the series has to both start above zero and
 * rise — a fixture where everyone joined in the window would pass against an implementation that
 * forgot the running total and counted per-month intake instead.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
@Import(SparklinesIT.FixedClockConfiguration.class)
class SparklinesIT {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    private static final String ENDPOINT = "/api/dashboard/metrics";

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
    private PatientRepository patientRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @BeforeEach
    void seed() {
        patientRepository.deleteAll();
        professionalRepository.deleteAll();

        // March, June and August 2026 — the window runs March..August, so the first is already on
        // file when it opens and the other two arrive inside it.
        patientRepository.save(patient(LocalDate.of(2026, 3, 2)));
        patientRepository.save(patient(LocalDate.of(2026, 6, 14)));
        patientRepository.save(patient(TODAY.minusDays(3)));

        professionalRepository.save(professional(LocalDate.of(2021, 6, 11)));
        professionalRepository.save(professional(LocalDate.of(2026, 7, 28)));
    }

    /** Six points, because six is what the tile is drawn to hold and what the volume chart uses. */
    @Test
    void drawsSixMonthsForEveryTileThatHasASeries() throws Exception {
        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sparklines.patients.length()").value(6))
            .andExpect(jsonPath("$.sparklines.professionals.length()").value(6));
    }

    /**
     * A running total, not a per-month intake.
     *
     * <p>The two shapes are indistinguishable on a fixture where everybody arrived inside the
     * window — both would draw a rising line — which is why one patient here predates it. Counting
     * intake would open this series at 0 and lose that patient entirely.
     */
    @Test
    void eachPointIsTheRunningTotalAtThatMonthEnd() throws Exception {
        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            // Mar, Apr, May, Jun, Jul, Aug
            .andExpect(jsonPath("$.sparklines.patients").value(org.hamcrest.Matchers.contains(1, 1, 1, 2, 2, 3)))
            .andExpect(jsonPath("$.sparklines.professionals").value(org.hamcrest.Matchers.contains(1, 1, 1, 1, 2, 2)));
    }

    /**
     * <b>The line has to end where the number above it is.</b>
     *
     * <p>Everything, archived included, because the tiles render {@code network} and not
     * {@code loaded} — an archived-excluding series would sit a point below the figure it
     * illustrates, and nothing on the screen would say which of the two to believe.
     */
    @Test
    void theLastPointIsTheCountTheTileRenders() throws Exception {
        patientRepository.save(patient(TODAY.minusDays(1)).isArchived(true));

        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.network.patients").value(4))
            .andExpect(jsonPath("$.sparklines.patients[5]").value(4))
            .andExpect(jsonPath("$.network.professionals").value(2))
            .andExpect(jsonPath("$.sparklines.professionals[5]").value(2));
    }

    /**
     * <b>Unread messages and open tasks are deliberately absent, and this pins that.</b>
     *
     * <p>Both tiles count a backlog rather than a total, so a past month's value needs to know when
     * each one stopped being open — and nothing records it: no read time on a message, no closed
     * time on a task, and the seed populates neither {@code created_at} nor {@code modified_date}.
     *
     * <p>Inflow could be counted and would draw a plausible line under both. That is exactly what
     * this test exists to prevent: "messages that arrived" is not "messages still unread", the
     * screen already charts the former beside these tiles, and a wrong trend under a right number is
     * the fabricated-figure failure the in-browser mock was deleted for. Giving these two a real
     * series means giving the domain a read time and a closed time first — at which point this test
     * should be changed deliberately, not deleted in passing.
     */
    @Test
    void theTwoBacklogTilesCarryNoSeriesRatherThanAPlausibleOne() throws Exception {
        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sparklines.messages").doesNotExist())
            .andExpect(jsonPath("$.sparklines.tasks").doesNotExist());
    }

    private static Patient patient(LocalDate joinedOn) {
        return new Patient().joinedOn(joinedOn).status(AccountStatus.ACTIVE);
    }

    private static Professional professional(LocalDate joinedOn) {
        return new Professional()
            .role(ProfessionalRole.NURSE)
            .licenceNumber("NMC/RN/" + joinedOn)
            .verification(VerificationStatus.VERIFIED)
            .status(AccountStatus.ACTIVE)
            .joinedOn(joinedOn);
    }
}
