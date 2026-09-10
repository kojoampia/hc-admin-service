package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.repository.DirectoryLinkRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifying a patient's plan choice: what it answers, what it refuses, and what it leaves behind.
 *
 * <p>Backlog item 54. The wire format hc-patient receives is asserted in
 * {@code PatientPlanVerificationServiceTest}, which reads the serialised bytes; this covers the
 * surface and the storage decision.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "admin")
class PatientPlanVerificationResourceIT {

    private static final String PATH = "/api/patient-plan-verifications";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DirectoryLinkRepository directoryLinkRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @BeforeEach
    @AfterEach
    void clean() {
        directoryLinkRepository.deleteAll();
    }

    /**
     * {@code 202}, not {@code 201} and not {@code 200} — see the resource's javadoc. Nothing was
     * created and the publish has not happened yet when this returns, deliberately.
     */
    @Test
    void acceptsTheDecisionAndEchoesWhatWasAnnounced() throws Exception {
        DirectoryLink link = directoryLinkRepository.save(patientLink("ama@mail.gh", "MELON"));

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(link.getId())))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.plan").value("MELON"))
            // On the response and deliberately NOT on the wire: it is the support handle a person
            // quotes to hc-patient, and putting it in the payload is the MembershipID field item 54
            // removed.
            .andExpect(jsonPath("$.membershipId").value("mem-0041"));
    }

    /**
     * <b>A verification stores nothing at all</b>, which is the decision this item was built on:
     * hc-patient owns {@code Membership.status}, and the published event is the record.
     *
     * <p>Asserted two ways, because either alone is weak. The link is re-read field by field — a
     * verification must not write {@code VERIFIED} onto {@code planStatus}, which is hc-patient's
     * vocabulary describing a moment this service did not witness. And the collection names are
     * compared before and after, which is what catches a future change storing the decision somewhere
     * new rather than somewhere known.
     */
    @Test
    void writesNothingToThisDatabase() throws Exception {
        DirectoryLink link = directoryLinkRepository.save(patientLink("ama@mail.gh", "MELON"));
        Set<String> before = mongoTemplate.getCollectionNames();

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(link.getId()))).andExpect(status().isAccepted());

        DirectoryLink after = directoryLinkRepository.findById(link.getId()).orElseThrow();
        assertThat(after.getPlanStatus()).as("PENDING is what hc-patient reported; this service may not restate it").isEqualTo("PENDING");
        assertThat(after.getPlanCode()).isEqualTo("MELON");
        assertThat(after.getPlanMembershipId()).isEqualTo("mem-0041");
        assertThat(mongoTemplate.getCollectionNames())
            .as("a verification created a collection — hc-admin was specified to store no plan state at all")
            .isEqualTo(before);
    }

    /** The echo is the acknowledgement: pressing twice republishes rather than refusing. */
    @Test
    void acceptsTheSameDecisionTwice() throws Exception {
        DirectoryLink link = directoryLinkRepository.save(patientLink("ama@mail.gh", "PEAR"));

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(link.getId()))).andExpect(status().isAccepted());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(link.getId())))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.plan").value("PEAR"));
    }

    /**
     * {@code 400} rather than {@code 404}: the link is a field of the decision being recorded, not the
     * resource being addressed. Same reading {@link ProfessionalVerificationResource} takes.
     */
    @Test
    void refusesALinkThatDoesNotExist() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("no-such-link"))).andExpect(status().isBadRequest());
    }

    /**
     * <b>Not every subject on {@code patient-events} is a patient.</b> A care-angel nomination arrives
     * as {@code AccountCreated} keyed on the angel's own address, and is stored as a link with no
     * local record. Verifying a plan for one would ask hc-patient to activate a membership for
     * somebody who has never chosen a tier — they would refuse it {@code NO_PENDING_MEMBERSHIP}, so
     * this is caught on the near side where the diagnosis is cheap.
     */
    @Test
    void refusesACareAngelLink() throws Exception {
        DirectoryLink angel = patientLink("angel@mail.gh", "MELON");
        angel.setSubjectKind(DirectorySubjectKind.CARE_ANGEL);
        DirectoryLink saved = directoryLinkRepository.save(angel);

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(saved.getId()))).andExpect(status().isBadRequest());
    }

    /** A clinician has no membership, and their link is keyed on an {@code accountId} rather than an address. */
    @Test
    void refusesAProfessionalLink() throws Exception {
        DirectoryLink professional = patientLink("clinician@abofonsa.care", "MELON");
        professional.setSource(DirectorySource.HC_PROFESSIONAL);
        professional.setSubjectKind(DirectorySubjectKind.PROFESSIONAL);
        DirectoryLink saved = directoryLinkRepository.save(professional);

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(saved.getId()))).andExpect(status().isBadRequest());
    }

    /**
     * <b>A membership can name no tier, and that is a real stored state.</b> hc-patient's
     * {@code Membership.plan} and {@code .name} carry no {@code @NotNull} and their administrative
     * path can create one with neither — {@code dl-plan-a5} in the {@code test} fixture is exactly
     * this row.
     *
     * <p>Refused rather than published as {@code {"plan":null}}, which would be a well-formed
     * verification of nothing: their {@code assertPlanAgrees} is the whole reason that field exists,
     * and a null defeats it. They would refuse it {@code NO_PLAN_NAMED} and dead-letter the frame.
     */
    @Test
    void refusesAPlanChoiceNamingNoTier() throws Exception {
        DirectoryLink untiered = patientLink("yaa@mail.gh", null);
        DirectoryLink saved = directoryLinkRepository.save(untiered);

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(saved.getId()))).andExpect(status().isBadRequest());
    }

    /** The exchange is keyed on the address, so a link without one cannot be answered at all. */
    @Test
    void refusesALinkWithNoSubjectKey() throws Exception {
        DirectoryLink anonymous = patientLink(null, "PEAR");
        DirectoryLink saved = directoryLinkRepository.save(anonymous);

        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body(saved.getId()))).andExpect(status().isBadRequest());
    }

    /** {@code linkId} is {@code @NotNull}: a body naming nothing is a bad body. */
    @Test
    void refusesABodyNamingNoLink() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    /**
     * <b>There is no way to un-verify, and no way to edit a decision.</b> A decision is recorded, not
     * toggled — the same contract {@link ProfessionalVerificationResource} states, and the reason this
     * console removed its status toggle on 2026-08-24. Correcting one means recording the correcting
     * decision, which is a control that does not exist yet because no refusal path has been specified.
     *
     * <p><b>The two status codes differ and the difference is the point</b>, which is worth stating
     * because the first draft of this test expected {@code 405} for both and was wrong about the
     * second. {@code PUT} on the collection is {@code 405}: the path is mapped and the method is not.
     * Anything under {@code /{id}} is {@code 404}, because <b>there is no id-addressed resource here
     * at all</b> — nothing is stored, so there is no decision to address, edit or delete. A
     * {@code 405} there would imply a resource that exists and declines the verb.
     */
    @Test
    void offersNoWayToUndoADecision() throws Exception {
        DirectoryLink link = directoryLinkRepository.save(patientLink("ama@mail.gh", "MELON"));

        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(link.getId()))
        ).andExpect(status().isMethodNotAllowed());
        mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(link.getId()))
        ).andExpect(status().isMethodNotAllowed());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(PATH + "/" + link.getId())).andExpect(
            status().isNotFound()
        );
    }

    private static String body(String linkId) {
        return "{\"linkId\":\"" + linkId + "\"}";
    }

    private static DirectoryLink patientLink(String externalKey, String planCode) {
        DirectoryLink link = new DirectoryLink();
        link.setSource(DirectorySource.HC_PATIENT);
        link.setSubjectKind(DirectorySubjectKind.PATIENT);
        link.setExternalKey(externalKey);
        link.setEmail(externalKey);
        link.setPlanMembershipId("mem-0041");
        link.setPlanCode(planCode);
        link.setPlanName(planCode == null ? null : planCode + " Plan");
        link.setPlanStatus("PENDING");
        return link;
    }
}
