package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.ProfessionalVerification;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ProfessionalVerificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code /api/professional-verifications} — the append-only verification history.
 *
 * <p>Three properties are worth a test here and the rest is ordinary CRUD:
 *
 * <ul>
 *   <li><b>Nothing can be edited or deleted.</b> Asserted by calling the verbs and requiring 405,
 *       rather than by reading the source — a generated resource grows those handlers back without
 *       anyone deciding to add them, and the absence is the contract.
 *   <li><b>The time and the author are the server's.</b> A caller who could set them could write a
 *       history that never happened, which is the only thing a history is for.
 *   <li><b>The decision projects onto the professional.</b> That is the single write path to
 *       {@code Professional.verification}, and the badge and the dashboard both read it.
 * </ul>
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "admin")
class ProfessionalVerificationResourceIT {

    private static final String ENTITY_API_URL = "/api/professional-verifications";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ProfessionalVerificationRepository verificationRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    private Professional professional;

    @BeforeEach
    void seed() {
        verificationRepository.deleteAll();
        professionalRepository.deleteAll();

        professional =
            professionalRepository.save(
                new Professional()
                    .role(ProfessionalRole.NURSE)
                    .licenceNumber("NMC/GH/26-0001")
                    .verification(VerificationStatus.PENDING)
                    .status(AccountStatus.ACTIVE)
                    .joinedOn(LocalDate.of(2026, 1, 5))
            );
    }

    @AfterEach
    void tearDown() {
        verificationRepository.deleteAll();
        professionalRepository.deleteAll();
    }

    // --- recording a decision ---------------------------------------------------------------------

    @Test
    void recordsADecisionAndReturnsIt() throws Exception {
        mvc
            .perform(
                post(ENTITY_API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(verification(VerificationStatus.VERIFIED)))
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("VERIFIED"))
            .andExpect(jsonPath("$.method").value("Licence register"));

        assertThat(verificationRepository.findAll()).hasSize(1);
    }

    /**
     * The projection: the professional's own field follows the newest decision.
     *
     * <p>This is what the directory badge and the dashboard's pending count read, so without it the
     * history would be a collection nothing looks at while the screens went on showing the old
     * state — the two-disagreeing-sources failure the design exists to avoid.
     */
    @Test
    void projectsTheDecisionOntoTheProfessional() throws Exception {
        record(VerificationStatus.VERIFIED);

        assertThat(professionalRepository.findById(professional.getId()).orElseThrow().getVerification())
            .isEqualTo(VerificationStatus.VERIFIED);
    }

    /** And a later decision supersedes the earlier one, rather than the first one sticking. */
    @Test
    void theNewestDecisionWins() throws Exception {
        record(VerificationStatus.VERIFIED);
        record(VerificationStatus.REVOKED);

        assertThat(professionalRepository.findById(professional.getId()).orElseThrow().getVerification())
            .isEqualTo(VerificationStatus.REVOKED);
        // Both rows survive. Superseding is not overwriting — the revocation does not erase the
        // fact that this person was once verified, which is the whole reason for a history.
        assertThat(verificationRepository.findAll()).hasSize(2);
    }

    /**
     * The time and the author come from the server, whatever the payload said.
     *
     * <p>Sent here as a date years off and an author who is not the caller: if either survived, a
     * client could compose a verification history and the collection would be worthless as
     * evidence. Same rule {@code AuditingEntityCallback} applies to {@code createdBy}.
     */
    @Test
    void ignoresACallerSuppliedTimeAndAuthor() throws Exception {
        Map<String, Object> forged = verification(VerificationStatus.VERIFIED);
        forged.put("recordedAt", "2019-01-01T00:00:00Z");
        forged.put("recordedBy", "somebody-else");

        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS).minusSeconds(5);
        mvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(forged)))
            .andExpect(status().isCreated());

        ProfessionalVerification stored = verificationRepository.findAll().getFirst();
        assertThat(stored.getRecordedBy()).isEqualTo("admin");
        assertThat(stored.getRecordedAt()).isAfter(before);
    }

    /**
     * An id in the body is not honoured — the request shape has no id to carry it.
     *
     * <p>The JHipster contract says POST rejects a body with an id. Here it cannot have one, so the
     * assertion is that the server minted its own rather than that it refused: a structural
     * guarantee is worth more than a check, and this pins that it really is structural.
     */
    @Test
    void doesNotLetTheClientChooseTheId() throws Exception {
        Map<String, Object> withId = verification(VerificationStatus.VERIFIED);
        withId.put("id", "given-by-the-client");

        mvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(withId)))
            .andExpect(status().isCreated());

        assertThat(verificationRepository.findById("given-by-the-client")).isEmpty();
        assertThat(verificationRepository.findAll()).hasSize(1);
    }

    /** A decision about nobody is not a decision. */
    @Test
    void rejectsAVerificationNamingNoProfessional() throws Exception {
        Map<String, Object> orphan = new LinkedHashMap<>();
        orphan.put("status", "VERIFIED");

        mvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(orphan)))
            .andExpect(status().isBadRequest());
    }

    /** And a decision about a professional who does not exist is a bad body, not a missing page. */
    @Test
    void rejectsAVerificationNamingAProfessionalThatDoesNotExist() throws Exception {
        Map<String, Object> unknown = verification(VerificationStatus.VERIFIED);
        unknown.put("professionalId", "no-such-professional");

        mvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(unknown)))
            .andExpect(status().isBadRequest());
    }

    // --- append-only -------------------------------------------------------------------------------

    /**
     * No edit and no delete, asserted against the running application.
     *
     * <p>405 because the path exists and the method does not, which is the difference worth pinning:
     * a 404 would also pass a "cannot edit" assertion and would mean the resource had moved.
     */
    @Test
    void offersNoWayToEditOrDeleteARecordedDecision() throws Exception {
        String id = record(VerificationStatus.VERIFIED);
        String body = om.writeValueAsString(verification(VerificationStatus.REJECTED));

        mvc
            .perform(put(ENTITY_API_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isMethodNotAllowed());
        mvc
            .perform(patch(ENTITY_API_URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(ENTITY_API_URL + "/" + id)).andExpect(status().isMethodNotAllowed());

        assertThat(verificationRepository.findById(id))
            .get()
            .extracting(ProfessionalVerification::getStatus)
            .isEqualTo(VerificationStatus.VERIFIED);
    }

    // --- reading ------------------------------------------------------------------------------------

    @Test
    void listsThePagedHistory() throws Exception {
        record(VerificationStatus.VERIFIED);

        mvc
            .perform(get(ENTITY_API_URL + "?page=0&size=1"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(jsonPath("$").isArray());
    }

    /** The record panel's read: one professional, newest first. */
    @Test
    void returnsOneProfessionalsHistoryNewestFirst() throws Exception {
        record(VerificationStatus.VERIFIED);
        record(VerificationStatus.REVOKED);
        record(VerificationStatus.VERIFIED);

        mvc
            .perform(get("/api/professionals/" + professional.getId() + "/verifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)))
            .andExpect(jsonPath("$[0].status").value("VERIFIED"))
            .andExpect(jsonPath("$[1].status").value("REVOKED"));
    }

    /**
     * A professional nobody has verified yet has an empty history, not a 404.
     *
     * <p>Mid-onboarding is a real state and every applicant is in it. A 404 would make the record
     * panel render an error over a professional whose record is perfectly fine.
     */
    @Test
    void anUnverifiedProfessionalHasAnEmptyHistoryRatherThanA404() throws Exception {
        mvc
            .perform(get("/api/professionals/" + professional.getId() + "/verifications"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    // --- the field is no longer client-writable ------------------------------------------------------

    /**
     * {@code PATCH /api/professionals/:id} can no longer move the verification.
     *
     * <p>It could until 2026-08-24, and the record screen's "Send for re-verification" used exactly
     * that path. Both halves of the change are asserted: the field is untouched, and the rest of the
     * patch still applies — a rule that silently swallowed the whole request would also pass the
     * first assertion.
     */
    @Test
    void patchingAProfessionalCannotMoveTheVerification() throws Exception {
        record(VerificationStatus.VERIFIED);

        String body = om.writeValueAsString(
            new Professional().id(professional.getId()).verification(VerificationStatus.REJECTED).speciality("Palliative")
        );
        mvc
            .perform(patch("/api/professionals/" + professional.getId()).contentType("application/merge-patch+json").content(body))
            .andExpect(status().isOk());

        Professional stored = professionalRepository.findById(professional.getId()).orElseThrow();
        assertThat(stored.getVerification()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(stored.getSpeciality()).isEqualTo("Palliative");
    }

    /**
     * Nor can {@code PUT}, which is the harder half.
     *
     * <p>PUT sends a whole document, so ignoring the payload is not the same as protecting the
     * field: the stored value has to be read back and restored, exactly as the audit callback does
     * for {@code createdBy}. Without that the console's own edit form would still be able to set it.
     */
    @Test
    void puttingAProfessionalCannotMoveTheVerification() throws Exception {
        record(VerificationStatus.VERIFIED);

        Professional whole = professionalRepository.findById(professional.getId()).orElseThrow();
        whole.setVerification(VerificationStatus.REJECTED);
        whole.setSpeciality("Palliative");

        mvc
            .perform(
                put("/api/professionals/" + professional.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(whole))
            )
            .andExpect(status().isOk());

        Professional stored = professionalRepository.findById(professional.getId()).orElseThrow();
        assertThat(stored.getVerification()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(stored.getSpeciality()).isEqualTo("Palliative");
    }

    /**
     * A newly created professional is {@code PENDING}, whatever the payload asked for.
     *
     * <p>The third and last door onto the field. Server-written on update and client-written on
     * create is not a rule — it is a rule with a doorway beside it, and creating a record is the
     * easiest doorway to walk through: post a professional as {@code VERIFIED} and the badge says
     * verified with no decision behind it.
     */
    @Test
    void creatingAProfessionalCannotChooseTheVerification() throws Exception {
        Professional intake = new Professional()
            .role(ProfessionalRole.DOCTOR)
            .licenceNumber("MDC/RN/26-9999")
            .verification(VerificationStatus.VERIFIED)
            .status(AccountStatus.ACTIVE)
            .joinedOn(LocalDate.of(2026, 8, 24));

        String response = mvc
            .perform(post("/api/professionals").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(intake)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.verification").value("PENDING"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String id = om.readTree(response).get("id").asString();
        assertThat(professionalRepository.findById(id).orElseThrow().getVerification()).isEqualTo(VerificationStatus.PENDING);
    }

    // --- helpers -------------------------------------------------------------------------------------

    /** The request body, as a map so a test can add fields the record does not declare. */
    private Map<String, Object> verification(VerificationStatus status) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("professionalId", professional.getId());
        request.put("status", status.name());
        request.put("method", "Licence register");
        request.put("reference", professional.getLicenceNumber());
        request.put("note", "Checked against the register.");
        return request;
    }

    /** Records a decision through the API and returns its id. */
    private String record(VerificationStatus status) throws Exception {
        String response = mvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(verification(status))))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return om.readTree(response).get("id").asString();
    }
}
