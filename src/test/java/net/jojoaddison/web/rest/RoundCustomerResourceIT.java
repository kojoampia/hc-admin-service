package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.domain.enumeration.IdType;
import net.jojoaddison.domain.enumeration.Sex;
import net.jojoaddison.repository.DirectoryLinkRepository;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.repository.ProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/round-customers} over stored documents — backlog item 22.
 *
 * <h2>What this proves that {@code RoundCustomerServiceTest} cannot</h2>
 *
 * <p>The rule is asserted there, in two seconds, with mocked repositories — and this repository's
 * integration tests need a Mongo replica set that misses its start window on a loaded workstation
 * (backlog item 17), so the assertion a reader will actually watch belongs in the unit test. What
 * needs a database is the part the unit test stubs: that {@code findBySource} and
 * {@code findAllById} really return what the join expects, that a {@code Patient}'s {@code @DBRef}
 * profile comes back attached, and that the projection reaches the wire as two fields.
 *
 * <p>The endpoint itself is also swept by {@code PaginationIT}, which discovers single-segment
 * {@code /api} paths from the handler mapping — so the {@code X-Total-Count} and {@code Link}
 * headers are covered there the moment this class exists. They are asserted here as well, because
 * this one knows how many rows it seeded and that sweep deliberately does not.
 *
 * <h2>⚠ This is a picker, and backlog item 22 forbids one — read which</h2>
 *
 * <p>The entry's words are "do not close this by making the field a dropdown of hc-admin patients",
 * and the failure it describes is sending {@code Patient.id}: an id that means nobody on
 * hc-professional's stack, filed successfully, producing a day plan the patient cannot see.
 * {@link #theCustomerIdIsNeverTheLocalPatientId} is what stops that, on the wire rather than in a
 * service, and it was watched failing on exactly that mutation.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "admin", authorities = { "ROLE_ADMIN" })
class RoundCustomerResourceIT {

    /**
     * hc-patient's id for the linked patient, and deliberately unlike this service's ids.
     *
     * <p>{@code Patient} documents here are Mongo-assigned, so the local id is a 24-character
     * ObjectId and this is not — two values that could be mistaken for each other would let a case
     * pass while the endpoint returned the wrong one.
     */
    private static final String THE_SIBLINGS_ID = "round-customer-it-hcp-6120";

    /**
     * Removed by id in the teardown rather than with {@code deleteAll()}.
     *
     * <p>{@code directory_link} is the identity map two consumers upsert into, so emptying it would
     * delete rows another test depends on, and the damage would read as a directory that had
     * forgotten who it learned about rather than as a failure here.
     */
    private static final String LINKED_ID = "round-customer-it-linked";
    private static final String ANGEL_ID = "round-customer-it-angel";
    private static final String ERASED_ID = "round-customer-it-erased";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private DirectoryLinkRepository directoryLinkRepository;

    /** The Mongo id of the one patient this stack can address on hc-patient's. */
    private String linkedRecordId;

    /** The Mongo id of a patient with no link at all — the seed's {@code a14} state. */
    private String unlinkedRecordId;

    private String profileId;

    @BeforeEach
    void seedOneAddressablePatientAndThreeThatAreNot() {
        patientRepository.deleteAll();

        Profile profile = profileRepository.save(named("Kwame", "Darkwa"));
        profileId = profile.getId();

        Patient linked = patientRepository.save(patient().profile(profile));
        linkedRecordId = linked.getId();
        directoryLinkRepository.save(patientLink(LINKED_ID, linkedRecordId, THE_SIBLINGS_ID, "k.darkwa@mail.gh"));

        // No link: a real and permanent state, not a pending one. Nothing can address them.
        unlinkedRecordId = patientRepository.save(patient().profile(profile)).getId();

        // An account on hc-patient's stack that is not a patient: their nomination publishes
        // AccountCreated keyed on the ANGEL's address, which carries no patient id and opens no record.
        DirectoryLink angel = patientLink(ANGEL_ID, null, null, "kojo.sarsah@mail.gh");
        angel.setSubjectKind(DirectorySubjectKind.CARE_ANGEL);
        directoryLinkRepository.save(angel);

        // Somebody the far side has erased. It names a record of its own rather than sharing the
        // linked one, so which row the map keeps is not decided by iteration order — and it is given
        // an externalId the erasure would have unset, so this row is excluded by the erasedAt guard
        // alone. Drop that guard as redundant and this case goes red. See RoundCustomerService.
        String erasedRecordId = patientRepository.save(patient()).getId();
        DirectoryLink erased = patientLink(ERASED_ID, erasedRecordId, "round-customer-it-hcp-0000", "former.patient@mail.gh");
        erased.setErasedAt(Instant.parse("2026-09-04T09:30:00Z"));
        directoryLinkRepository.save(erased);
    }

    @AfterEach
    void tearDown() {
        patientRepository.deleteAll();
        profileRepository.deleteById(profileId);
        List.of(LINKED_ID, ANGEL_ID, ERASED_ID).forEach(directoryLinkRepository::deleteById);
    }

    /** The linked patient is offered, named from their profile and addressed by hc-patient's id. */
    @Test
    void aLinkedPatientIsOfferedByName() throws Exception {
        String body = customers();

        assertThat(customerIds(body)).contains(THE_SIBLINGS_ID);
        assertThat(nameOf(body, THE_SIBLINGS_ID)).isEqualTo("Kwame Darkwa");
    }

    /**
     * <b>The id on the wire is hc-patient's, never this service's.</b>
     *
     * <p>Backlog item 22's whole point, asserted at the boundary. The second assertion is not
     * redundant with the first: an endpoint emitting the local id fails the first with a diff a
     * reader has to interpret, and fails this one with a sentence naming the mistake.
     */
    @Test
    void theCustomerIdIsNeverTheLocalPatientId() throws Exception {
        List<String> ids = customerIds(customers());

        assertThat(ids).contains(THE_SIBLINGS_ID);
        assertThat(ids)
            .as(
                "a visit's customerId is a patientservice Profile.patientId — DirectoryLink.externalId. Sending " +
                    "Patient.id is the failure backlog item 22 exists to prevent: hc-professional accepts the round, " +
                    "files it, and produces a day plan for nobody."
            )
            .doesNotContain(linkedRecordId, unlinkedRecordId);
    }

    /** A patient this stack cannot address is absent, rather than offered with a wrong id. */
    @Test
    void aPatientWithNoLinkIsNotOffered() throws Exception {
        String body = customers();

        assertThat(customerIds(body)).hasSize(1);
        assertThat(customerIds(body)).doesNotContain(unlinkedRecordId);
    }

    /** A care angel has no patient id on any event and keeps no local record — never a visit. */
    @Test
    void aCareAngelIsNotOffered() throws Exception {
        String body = customers();

        assertThat(names(body)).doesNotContain("kojo.sarsah@mail.gh");
        assertThat(customerIds(body)).hasSize(1);
    }

    /** Somebody hc-patient has erased is never offered a home visit. */
    @Test
    void anErasedSubjectIsNotOffered() throws Exception {
        assertThat(customerIds(customers())).doesNotContain("round-customer-it-hcp-0000");
    }

    /**
     * Paginated, with the headers a pager is drawn from.
     *
     * <p>{@code PaginationIT} asserts their presence across every list endpoint; this asserts the
     * count is the whole set rather than the page, which is the half a sweep with one seeded row
     * cannot tell apart.
     */
    @Test
    void theListIsPagedAndCountsEveryPlannablePatient() throws Exception {
        mvc.perform(get("/api/round-customers").param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "1"))
            .andExpect(header().exists("Link"));
    }

    private String customers() throws Exception {
        return mvc
            .perform(get("/api/round-customers").param("size", "100"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    }

    private static List<String> customerIds(String body) {
        return JsonPath.read(body, "$[*].customerId");
    }

    private static List<String> names(String body) {
        return JsonPath.read(body, "$[*].name");
    }

    private static String nameOf(String body, String customerId) {
        List<String> found = JsonPath.read(body, "$[?(@.customerId == '" + customerId + "')].name");
        return found.isEmpty() ? null : found.get(0);
    }

    private static Patient patient() {
        return new Patient().status(AccountStatus.ACTIVE).joinedOn(LocalDate.of(2026, 3, 1));
    }

    /**
     * A valid {@code Profile} with the name this test cares about on it.
     *
     * <p>Seven of its fields carry {@code @NotNull} and none of them is read here — a
     * {@code ValidatingMongoEventListener} refuses the save without them, which is what the first
     * run of this class found. The values are {@code ProfileResourceIT}'s defaults rather than new
     * ones, so nothing here implies that any of them matters to the picker.
     */
    private static Profile named(String firstName, String lastName) {
        return new Profile()
            .accountId("round-customer-it")
            .firstName(firstName)
            .lastName(lastName)
            .dateOfBirth(LocalDate.ofEpochDay(0L))
            .sex(Sex.MALE)
            .mobilePhone("AAAAAAAAAA")
            .email("aaaaa@example.com")
            .idType(IdType.GHANA_CARD)
            .idNumber("AAAAAAAAAA");
    }

    private static DirectoryLink patientLink(String id, String localId, String externalId, String email) {
        DirectoryLink link = new DirectoryLink();
        link.setId(id);
        link.setSource(DirectorySource.HC_PATIENT);
        link.setSubjectKind(DirectorySubjectKind.PATIENT);
        link.setExternalKey(email);
        link.setExternalId(externalId);
        link.setLocalId(localId);
        link.setEmail(email);
        return link;
    }
}
