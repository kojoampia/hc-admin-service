package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Task;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.MessageChannel;
import net.jojoaddison.domain.enumeration.MessageStatus;
import net.jojoaddison.domain.enumeration.Priority;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.TaskState;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import net.jojoaddison.repository.MessageRepository;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.TaskRepository;
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

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private TaskRepository taskRepository;

    @BeforeEach
    void seed() {
        patientRepository.deleteAll();
        professionalRepository.deleteAll();
        messageRepository.deleteAll();
        taskRepository.deleteAll();

        // March, June and August 2026 — the window runs March..August, so the first is already on
        // file when it opens and the other two arrive inside it.
        patientRepository.save(patient(LocalDate.of(2026, 3, 2)));
        patientRepository.save(patient(LocalDate.of(2026, 6, 14)));
        patientRepository.save(patient(TODAY.minusDays(3)));

        professionalRepository.save(professional(LocalDate.of(2021, 6, 11)));
        professionalRepository.save(professional(LocalDate.of(2026, 7, 28)));

        // Five messages arrive in June; four are read during July. A backlog that rises and then
        // falls is the shape an inflow count cannot produce.
        for (int day = 10; day < 15; day++) {
            messageRepository.save(message(LocalDate.of(2026, 6, day), day == 14 ? null : LocalDate.of(2026, 7, 20)));
        }

        // Two tasks opened in June, one closed in July. The closed one is deliberately due in
        // August: a series built on due_on rather than closed_at would keep it open a month too
        // long, and this is the row that catches it.
        taskRepository.save(task(LocalDate.of(2026, 6, 5), LocalDate.of(2026, 7, 12), LocalDate.of(2026, 8, 30)));
        taskRepository.save(task(LocalDate.of(2026, 6, 20), null, LocalDate.of(2026, 9, 1)));
    }

    private static Instant noonOn(LocalDate date) {
        return date.atTime(12, 0).toInstant(ZoneOffset.UTC);
    }

    /** An arrival, and when it stopped being unread — the status follows from the read time. */
    private static Message message(LocalDate sentOn, LocalDate readOn) {
        return new Message()
            .subject("Fixture " + sentOn)
            .fromAddress("sender@abofonsa.care")
            .senderName("A sender")
            .body("...")
            .sentAt(noonOn(sentOn))
            .channel(MessageChannel.EMAIL)
            .priority(Priority.NORMAL)
            .status(readOn == null ? MessageStatus.NEW : MessageStatus.READ)
            .readAt(readOn == null ? null : noonOn(readOn));
    }

    private static Task task(LocalDate createdOn, LocalDate closedOn, LocalDate dueOn) {
        return new Task()
            .title("Fixture " + createdOn)
            .state(closedOn == null ? TaskState.TODO : TaskState.DONE)
            .priority(Priority.NORMAL)
            .dueOn(dueOn)
            .createdAt(noonOn(createdOn))
            .closedAt(closedOn == null ? null : noonOn(closedOn));
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
     * <b>The two backlog tiles, which carried no series at all until item 14.</b>
     *
     * <p>They count a backlog rather than a total, so a past month's value needs to know when each
     * item stopped being open — and nothing recorded it. The previous version of this test pinned
     * their <em>absence</em>, and said that giving them a real series meant giving the domain a read
     * time and a closed time first, at which point it should be changed deliberately rather than
     * deleted in passing. This is that change.
     *
     * <p>What it now asserts is the difference between a backlog and a total: this line <b>falls</b>.
     * The fixture opens five messages in June and reads four of them in July, so June ends with five
     * outstanding and July with one. An implementation that counted inflow — the plausible wrong
     * answer, and the one the screen already charts beside these tiles — would report five and then
     * five, and would pass any assertion that only checked the last point.
     */
    @Test
    void theBacklogSeriesFallsWhenTheBacklogWasCleared() throws Exception {
        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sparklines.messages.length()").value(6))
            // March, April, May, June, July, August — nothing before June, five open at June's end,
            // four of them read during July.
            .andExpect(jsonPath("$.sparklines.messages[2]").value(0))
            .andExpect(jsonPath("$.sparklines.messages[3]").value(5))
            .andExpect(jsonPath("$.sparklines.messages[4]").value(1))
            .andExpect(jsonPath("$.sparklines.messages[5]").value(1))
            .andExpect(jsonPath("$.unreadMessages").value(1));
    }

    /** A task that was closed leaves the backlog on the month it closed, not on the month it was due. */
    @Test
    void theOpenTaskSeriesFollowsClosedAtRatherThanDueOn() throws Exception {
        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sparklines.tasks.length()").value(6))
            .andExpect(jsonPath("$.sparklines.tasks[3]").value(2))
            .andExpect(jsonPath("$.sparklines.tasks[4]").value(1))
            .andExpect(jsonPath("$.sparklines.tasks[5]").value(1))
            .andExpect(jsonPath("$.openTasks").value(1));
    }

    /**
     * The KPI notes, which were i18n literals until item 14 — "+3 this week" under a count of 12.
     *
     * <p>Each is the measurement its own template names. The professionals one is the interesting
     * case: the demo says "+2 verified" and nothing records when a professional was verified, so
     * this counts who <em>joined</em> and the copy says so. Inventing a verification date to match a
     * caption would be the same fabrication in a different field.
     */
    @Test
    void everyKpiNoteIsAMeasurementOverTheLastSevenDays() throws Exception {
        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            // One patient joined three days ago; the other two joined in March and June.
            .andExpect(jsonPath("$.deltas.patients").value(1))
            // Both professionals predate the window — 2021 and 28 July, against a pinned 19 August.
            .andExpect(jsonPath("$.deltas.professionals").value(0))
            // Every message in the fixture arrived in June, well outside the last seven days.
            .andExpect(jsonPath("$.deltas.messages").value(0))
            // The closed task closed on 12 July, also outside it.
            .andExpect(jsonPath("$.deltas.tasks").value(0));
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
