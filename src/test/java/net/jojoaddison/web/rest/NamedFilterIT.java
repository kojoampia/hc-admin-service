package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.RosterWeek;
import net.jojoaddison.domain.ShiftAssignment;
import net.jojoaddison.domain.Task;
import net.jojoaddison.domain.Vendor;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.MessageChannel;
import net.jojoaddison.domain.enumeration.MessageStatus;
import net.jojoaddison.domain.enumeration.Priority;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.ShiftType;
import net.jojoaddison.domain.enumeration.TaskState;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import net.jojoaddison.repository.MessageRepository;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.RosterWeekRepository;
import net.jojoaddison.repository.ShiftAssignmentRepository;
import net.jojoaddison.repository.TaskRepository;
import net.jojoaddison.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The named list filters, and the count that goes with them.
 *
 * <p><b>Every one of these was already being sent by the console and silently ignored.</b> Spring
 * drops an undeclared {@code @RequestParam} without complaint, so the request looked correct, the
 * response looked healthy, and the endpoint returned the whole collection — which is why each case
 * below asserts {@code X-Total-Count} rather than the body alone. The directory tiles and the desk's
 * status counters render that header from a one-row request; a count that ignores the filter is the
 * actual defect, and a body-only assertion would pass straight through it.
 *
 * <p>Each case seeds two documents that differ only in the filtered field, so a filter that does
 * nothing returns 2 and fails. The collections are cleared first because the count is the assertion.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class NamedFilterIT {

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Autowired
    private RosterWeekRepository rosterWeekRepository;

    @BeforeEach
    void clear() {
        professionalRepository.deleteAll();
        vendorRepository.deleteAll();
        patientRepository.deleteAll();
        taskRepository.deleteAll();
        messageRepository.deleteAll();
        shiftAssignmentRepository.deleteAll();
        rosterWeekRepository.deleteAll();
    }

    private Professional professional(ProfessionalRole role, AccountStatus status) {
        Professional professional = new Professional()
            .role(role)
            .status(status)
            .licenceNumber("GH-" + role + "-" + status)
            .verification(VerificationStatus.VERIFIED)
            .joinedOn(LocalDate.of(2026, 1, 1));
        return professionalRepository.save(professional);
    }

    @Test
    void shouldFilterProfessionalsByStatus() throws Exception {
        professional(ProfessionalRole.NURSE, AccountStatus.ACTIVE);
        professional(ProfessionalRole.NURSE, AccountStatus.SUSPENDED);

        restMockMvc
            .perform(get("/api/professionals?status.equals=ACTIVE&size=1"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$.[0].status").value("ACTIVE"));
    }

    @Test
    void shouldFilterProfessionalsByRole() throws Exception {
        professional(ProfessionalRole.DOCTOR, AccountStatus.ACTIVE);
        professional(ProfessionalRole.CAREGIVER, AccountStatus.ACTIVE);

        restMockMvc
            .perform(get("/api/professionals?role.equals=DOCTOR"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$.[0].role").value("DOCTOR"));
    }

    /** Two filters at once have to intersect, which is the whole reason for a builder. */
    @Test
    void shouldCombineStatusAndRole() throws Exception {
        professional(ProfessionalRole.DOCTOR, AccountStatus.ACTIVE);
        professional(ProfessionalRole.DOCTOR, AccountStatus.ON_LEAVE);
        professional(ProfessionalRole.NURSE, AccountStatus.ACTIVE);

        restMockMvc
            .perform(get("/api/professionals?role.equals=DOCTOR&status.equals=ACTIVE"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"));
    }

    /**
     * The archived filter predates these and was served by its own repository method. It has to keep
     * working, and has to intersect with the new ones rather than replace them.
     */
    @Test
    void shouldCombineStatusWithTheArchivedFilter() throws Exception {
        professional(ProfessionalRole.NURSE, AccountStatus.ACTIVE);
        Professional archived = professional(ProfessionalRole.NURSE, AccountStatus.ACTIVE);
        professionalRepository.save(archived.isArchived(true));

        restMockMvc
            .perform(get("/api/professionals?status.equals=ACTIVE&isArchived.notEquals=true"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"));
    }

    /**
     * A document written before {@code is_archived} existed does not carry the field at all, so the
     * not-archived filter has to be {@code $ne: true} and not {@code is(false)} — the latter matches
     * none of them and empties the directory.
     */
    @Test
    void shouldTreatAMissingArchivedFieldAsNotArchived() throws Exception {
        professional(ProfessionalRole.NURSE, AccountStatus.ACTIVE);

        restMockMvc
            .perform(get("/api/professionals?isArchived.notEquals=true"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"));
    }

    /** An unknown enum value is the client's mistake, not a server error. */
    @Test
    void shouldRejectAnUnknownStatus() throws Exception {
        restMockMvc.perform(get("/api/professionals?status.equals=NOT_A_STATUS")).andExpect(status().isBadRequest());
    }

    @Test
    void shouldFilterVendorsByStatus() throws Exception {
        vendorRepository.save(new Vendor().name("Kaneshie Medical").category("Supplies").status(AccountStatus.ACTIVE));
        vendorRepository.save(new Vendor().name("Under review co").category("Supplies").status(AccountStatus.UNDER_REVIEW));

        restMockMvc
            .perform(get("/api/vendors?status.equals=UNDER_REVIEW"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$.[0].name").value("Under review co"));
    }

    @Test
    void shouldFilterPatientsByStatus() throws Exception {
        patientRepository.save(new Patient().status(AccountStatus.ACTIVE).joinedOn(LocalDate.of(2026, 1, 1)));
        patientRepository.save(new Patient().status(AccountStatus.PENDING).joinedOn(LocalDate.of(2026, 1, 1)));

        restMockMvc
            .perform(get("/api/patients?status.equals=PENDING"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$.[0].status").value("PENDING"));
    }

    /**
     * The board asks for two of the three states at once, which is why this one is {@code in} and not
     * {@code equals}. The shell's open-task badge sends the same thing and was counting every task
     * ever created, closed ones included.
     */
    @Test
    void shouldFilterTasksByState() throws Exception {
        taskRepository.save(new Task().title("Open one").state(TaskState.TODO).priority(Priority.NORMAL));
        taskRepository.save(new Task().title("Open two").state(TaskState.DOING).priority(Priority.NORMAL));
        taskRepository.save(new Task().title("Closed").state(TaskState.DONE).priority(Priority.NORMAL));

        restMockMvc
            .perform(get("/api/tasks?state.in=TODO&state.in=DOING"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "2"));
    }

    private Message message(String subject, MessageStatus status, Priority priority) {
        return message(subject, status, priority, MessageChannel.EMAIL);
    }

    private Message message(String subject, MessageStatus status, Priority priority, MessageChannel channel) {
        return messageRepository.save(
            new Message()
                .subject(subject)
                .fromAddress("sender@abofonsa.care")
                .senderName("A sender")
                .sentAt(Instant.parse("2026-08-01T09:00:00Z"))
                .channel(channel)
                .status(status)
                .priority(priority)
        );
    }

    @Test
    void shouldFilterMessagesByStatus() throws Exception {
        message("Unread", MessageStatus.NEW, Priority.NORMAL);
        message("Answered", MessageStatus.REPLIED, Priority.NORMAL);

        restMockMvc
            .perform(get("/api/messages?status.equals=NEW"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$.[0].subject").value("Unread"));
    }

    @Test
    void shouldFilterMessagesByPriority() throws Exception {
        message("Urgent", MessageStatus.NEW, Priority.HIGH);
        message("Routine", MessageStatus.NEW, Priority.LOW);

        restMockMvc
            .perform(get("/api/messages?priority.equals=HIGH"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$.[0].subject").value("Urgent"));
    }

    /**
     * Item 19: the desk's Filter control offers channel, and {@code channel} is a column it shows.
     *
     * <p>Sent without the handler declaring it, this would return the whole collection and read as a
     * filter that found everything — the failure the three parameters above were added to end.
     */
    @Test
    void shouldFilterMessagesByChannel() throws Exception {
        message("Emailed", MessageStatus.NEW, Priority.NORMAL, MessageChannel.EMAIL);
        message("From the patient app", MessageStatus.NEW, Priority.NORMAL, MessageChannel.PATIENT_APP);

        restMockMvc
            .perform(get("/api/messages?channel.equals=PATIENT_APP"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$.[0].subject").value("From the patient app"));
    }

    /** Two named filters at once are one query, not the first one applied and the second dropped. */
    @Test
    void shouldCombineTheChannelAndStatusFilters() throws Exception {
        message("Patient app, unread", MessageStatus.NEW, Priority.NORMAL, MessageChannel.PATIENT_APP);
        message("Patient app, answered", MessageStatus.REPLIED, Priority.NORMAL, MessageChannel.PATIENT_APP);
        message("Emailed and unread", MessageStatus.NEW, Priority.NORMAL, MessageChannel.EMAIL);

        restMockMvc
            .perform(get("/api/messages?channel.equals=PATIENT_APP&status.equals=NEW"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$.[0].subject").value("Patient app, unread"));
    }

    /** A channel the enum does not hold is a bad request, not an empty page that looks like none. */
    @Test
    void shouldRejectAChannelThatIsNotOne() throws Exception {
        restMockMvc.perform(get("/api/messages?channel.equals=CARRIER_PIGEON")).andExpect(status().isBadRequest());
    }

    /** The desk's search box. Case-insensitive, because nobody types a subject line back exactly. */
    @Test
    void shouldFilterMessagesBySubjectSubstring() throws Exception {
        message("Invoice query for August", MessageStatus.NEW, Priority.NORMAL);
        message("Roster change", MessageStatus.NEW, Priority.NORMAL);

        restMockMvc
            .perform(get("/api/messages?subject.contains=invoice"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(jsonPath("$.[0].subject").value("Invoice query for August"));
    }

    /**
     * A subject search is a substring, not a pattern. Unquoted, a search containing regex characters
     * either matches the wrong rows or throws from inside the driver.
     */
    @Test
    void shouldTreatASubjectSearchAsLiteralText() throws Exception {
        message("Invoice query", MessageStatus.NEW, Priority.NORMAL);

        restMockMvc
            .perform(get("/api/messages?subject.contains=In.oice"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "0"));
    }

    /**
     * A DBRef is stored as {@code { $ref, $id }}, so the criteria path is {@code week.$id}. Matching
     * on {@code week} alone matches nothing at all — and would look exactly like an empty roster.
     */
    @Test
    void shouldFilterShiftAssignmentsByWeekAndProfessional() throws Exception {
        RosterWeek thisWeek = rosterWeekRepository.save(
            new RosterWeek().label("2026-W33").startDate(LocalDate.of(2026, 8, 10)).published(true)
        );
        RosterWeek nextWeek = rosterWeekRepository.save(
            new RosterWeek().label("2026-W34").startDate(LocalDate.of(2026, 8, 17)).published(true)
        );
        Professional nurse = professional(ProfessionalRole.NURSE, AccountStatus.ACTIVE);
        Professional doctor = professional(ProfessionalRole.DOCTOR, AccountStatus.ACTIVE);

        shiftAssignmentRepository.save(shift(thisWeek, nurse));
        shiftAssignmentRepository.save(shift(thisWeek, doctor));
        shiftAssignmentRepository.save(shift(nextWeek, nurse));

        restMockMvc
            .perform(get("/api/shift-assignments?weekId.equals=" + thisWeek.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "2"));

        restMockMvc
            .perform(get("/api/shift-assignments?professionalId.equals=" + nurse.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "2"));

        restMockMvc
            .perform(get("/api/shift-assignments?weekId.equals=" + thisWeek.getId() + "&professionalId.equals=" + nurse.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"));
    }

    private ShiftAssignment shift(RosterWeek week, Professional professional) {
        return new ShiftAssignment()
            .dayIndex(0)
            .shiftDate(LocalDate.of(2026, 8, 10))
            .shift(ShiftType.DAY)
            .week(week)
            .professional(professional);
    }

    /** With nothing supplied, every endpoint still returns the whole (paged) collection. */
    @Test
    void shouldReturnEverythingWhenNoFilterIsSupplied() throws Exception {
        professional(ProfessionalRole.NURSE, AccountStatus.ACTIVE);
        professional(ProfessionalRole.DOCTOR, AccountStatus.SUSPENDED);

        restMockMvc.perform(get("/api/professionals")).andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "2"));
    }
}
