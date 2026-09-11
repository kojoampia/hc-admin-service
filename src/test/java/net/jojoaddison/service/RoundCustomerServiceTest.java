package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.repository.DirectoryLinkRepository;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.service.dto.RoundCustomerDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

/**
 * The planner's patient picker: who is offered, and — the whole of backlog item 22 — <b>which id is
 * sent</b>.
 *
 * <h2>The one assertion this class exists for</h2>
 *
 * <p>Item 22 says: "Do not close this by making the field a dropdown of hc-admin patients", because
 * such a dropdown "would compose a round against an id that means nobody, file it successfully, and
 * produce a day plan the patient cannot see". The id it is talking about is {@code Patient.id}.
 * {@link RoundCustomerService} sends {@code DirectoryLink.externalId} instead — hc-patient's own
 * {@code patientId}, which is exactly what hc-professional keys a visit on — so the picker is the
 * control the entry warns about carrying the opposite value.
 *
 * <p><b>{@link #theCustomerIdIsTheSiblingsIdAndNeverTheLocalOne} is the guard, and it was watched
 * failing.</b> Changing the service to emit {@code patient.getId()} turns it red with a message
 * naming the confusion, which is the only way a reader who has just been told "no dropdown" can tell
 * this dropdown from the forbidden one.
 *
 * <h2>Why a unit test and not only an integration one</h2>
 *
 * <p>The rule is decided in one service over two repositories, so asserting it needs neither Mongo
 * nor MockMvc — and this repository's integration tests need a Mongo replica set that misses its
 * start window on a loaded workstation (backlog item 17). {@code PatientNamingTiersTest} makes the
 * same argument one file along and it applies here for the same reason: <b>an assertion that can
 * only be watched in CI is one the next author will not watch at all</b>, and this one exists to be
 * watched by somebody who has just changed which id goes on the wire.
 * {@code RoundCustomerResourceIT} proves the same things through the real endpoint over stored
 * documents.
 */
class RoundCustomerServiceTest {

    /**
     * hc-patient's own id for the person — what a visit must be addressed with.
     *
     * <p>Deliberately not shaped like this service's ids. {@code Patient} documents here are keyed
     * {@code a1}, {@code a2}, …, and the fixture's real external ids read {@code hcp-8814},
     * {@code patient-kojo}; two literals that could be confused for each other would let a case pass
     * while the service returned the wrong one.
     */
    private static final String THE_SIBLINGS_ID = "hcp-6120";

    /** This service's own id for the same person, and the value item 22 forbids sending. */
    private static final String THE_LOCAL_ID = "a6";

    private static final String THE_ADDRESS = "k.darkwa@mail.gh";

    private final DirectoryLinkRepository directoryLinkRepository = mock(DirectoryLinkRepository.class);
    private final PatientRepository patientRepository = mock(PatientRepository.class);
    private final RoundCustomerService service = new RoundCustomerService(directoryLinkRepository, patientRepository);

    private final List<DirectoryLink> links = new ArrayList<>();
    private final List<Patient> patients = new ArrayList<>();

    @BeforeEach
    void aDirectoryHoldingOneLinkedPatient() {
        links.add(patientLink(THE_LOCAL_ID, THE_SIBLINGS_ID, THE_ADDRESS));
        patients.add(patient(THE_LOCAL_ID, "Kwame", "Darkwa"));

        when(directoryLinkRepository.findBySource(DirectorySource.HC_PATIENT)).thenReturn(links);
        // Answers the ids it is asked for rather than everything, because the service joins on what
        // comes back: a mock returning the whole list regardless would make every "is not offered"
        // case below pass for the wrong reason, or fail on a record the service never asked about.
        when(patientRepository.findAllById(any())).thenAnswer(invocation -> {
            Set<String> wanted = new HashSet<>();
            ((Iterable<?>) invocation.getArgument(0)).forEach(id -> wanted.add(String.valueOf(id)));
            return patients
                .stream()
                .filter(candidate -> wanted.contains(candidate.getId()))
                .toList();
        });
    }

    @Test
    void aLinkedPatientIsOfferedByName() {
        assertThat(offered())
            .singleElement()
            .satisfies(customer -> {
                assertThat(customer.name()).isEqualTo("Kwame Darkwa");
                assertThat(customer.customerId()).isEqualTo(THE_SIBLINGS_ID);
            });
    }

    /**
     * <b>The id on the wire is hc-patient's, never this service's.</b>
     *
     * <p>Backlog item 22 in one assertion. Both halves are stated: what the value <em>is</em>, and
     * what it must not be. The second is not redundant — the two literals are different strings, so
     * an implementation returning the local id fails the first with a diff a reader has to interpret
     * and fails the second with a sentence saying what went wrong.
     */
    @Test
    void theCustomerIdIsTheSiblingsIdAndNeverTheLocalOne() {
        RoundCustomerDTO customer = offered().get(0);

        assertThat(customer.customerId())
            .as("a visit's customerId is a patientservice Profile.patientId — DirectoryLink.externalId, not Patient.id")
            .isEqualTo(THE_SIBLINGS_ID);
        assertThat(customer.customerId())
            .as(
                "sending Patient.id is the failure backlog item 22 exists to prevent: hc-professional would " +
                    "accept the round, file it, and produce a day plan for nobody"
            )
            .isNotEqualTo(THE_LOCAL_ID);
    }

    /**
     * A patient this service knows and hc-patient's stack has never named to it.
     *
     * <p>The common case in production and nine of the fifteen rows in the {@code test} fixture. It
     * is absent rather than offered with a blank or with its local id, which is the decision the
     * console then has to explain on screen.
     */
    @Test
    void aPatientWithNoLinkIsNotOffered() {
        patients.add(patient("a14", "Afua", "Boateng"));

        assertThat(offered()).extracting(RoundCustomerDTO::name).doesNotContain("Afua Boateng");
        assertThat(offered()).hasSize(1);
    }

    /**
     * A care angel is an account on hc-patient's stack and is not a patient.
     *
     * <p>Their nomination publishes {@code AccountCreated} keyed on the <em>angel's</em> address,
     * which carries no patient id and opens no local record — so the link exists and has neither an
     * {@code externalId} nor a {@code localId}. Asserted rather than assumed, because "a link
     * exists" was the tempting filter and would have offered this row with nothing to send.
     */
    @Test
    void aCareAngelIsNotOffered() {
        DirectoryLink angel = new DirectoryLink();
        angel.setId("dl-angel");
        angel.setSource(DirectorySource.HC_PATIENT);
        angel.setSubjectKind(DirectorySubjectKind.CARE_ANGEL);
        angel.setExternalKey("kojo.sarsah@mail.gh");
        angel.setEmail("kojo.sarsah@mail.gh");
        links.add(angel);

        assertThat(offered()).hasSize(1);
        assertThat(offered()).extracting(RoundCustomerDTO::name).doesNotContain("kojo.sarsah@mail.gh");
    }

    /**
     * Somebody hc-patient has told this service it erased.
     *
     * <p>The row is filtered on its {@code erasedAt} and would also be filtered on its missing
     * {@code externalId} — {@code DirectoryProjectionService} unsets that on erasure. Both are
     * exercised here: the link is given an {@code externalId} the erasure would have removed, so
     * this case fails if the {@code erasedAt} guard is dropped as redundant. Offering a clinician a
     * home visit to an erased subject is the harm that guard is for.
     */
    @Test
    void anErasedSubjectIsNotOffered() {
        DirectoryLink erased = patientLink("a99", "hcp-0000", "former.patient@mail.gh");
        erased.setId("dl-erased");
        erased.setErasedAt(Instant.parse("2026-09-04T09:30:00Z"));
        links.add(erased);
        patients.add(patient("a99", "Former", "Patient"));

        assertThat(offered()).extracting(RoundCustomerDTO::customerId).doesNotContain("hcp-0000");
        assertThat(offered()).hasSize(1);
    }

    /**
     * A patient learned from a domain event: a link, an address, and no {@code Profile} ever.
     *
     * <p>Nothing on {@code patient-events} carries a name, so this is permanent rather than pending.
     * The address is what the directory screen and the CSV export both show for such a row, and this
     * picker is the third surface to take that answer.
     */
    @Test
    void aLinkedPatientWithNoProfileIsNamedByTheAddressOnTheLink() {
        links.add(patientLink("a13", "hcp-8814", "naa.adjeley@mail.gh"));
        patients.add(patientWithNoProfile("a13"));

        assertThat(offered())
            .filteredOn(customer -> "hcp-8814".equals(customer.customerId()))
            .singleElement()
            .extracting(RoundCustomerDTO::name)
            .isEqualTo("naa.adjeley@mail.gh");
    }

    /**
     * A row nothing here can name carries {@code null} — <b>never the record's id</b>.
     *
     * <p>Item 45's rule, broken three times in this product (items 45, 53 and 62) and each time in a
     * new surface that nobody had swept. This is a new surface, so it is pinned here at birth rather
     * than after somebody reports a 24-character ObjectId where a name should be.
     */
    @Test
    void aRowNothingCanNameCarriesNullRatherThanAnId() {
        DirectoryLink anonymous = patientLink("68b4f2a19c3d5e7f81a02c15", "hcp-7777", null);
        links.add(anonymous);
        patients.add(patientWithNoProfile("68b4f2a19c3d5e7f81a02c15"));

        RoundCustomerDTO row = offered()
            .stream()
            .filter(customer -> "hcp-7777".equals(customer.customerId()))
            .findFirst()
            .orElseThrow();

        assertThat(row.name()).isNull();
        assertThat(row.name()).isNotEqualTo("68b4f2a19c3d5e7f81a02c15");
    }

    /** An archived patient has been taken out of the directory and is not a candidate for a round. */
    @Test
    void anArchivedPatientIsNotOffered() {
        links.add(patientLink("a9", "hcp-9999", "archived@mail.gh"));
        Patient archived = patient("a9", "Archived", "Person");
        archived.setIsArchived(true);
        patients.add(archived);

        assertThat(offered()).extracting(RoundCustomerDTO::customerId).doesNotContain("hcp-9999");
    }

    /**
     * A link whose local record is missing offers nothing.
     *
     * <p>The state {@link DirectoryLink} documents: the record and the claim are two writes with no
     * transaction around them, so a crash between them leaves a link naming a {@code Patient} that
     * was never saved. The picker is over this directory's patients, and there is no row to show.
     */
    @Test
    void aLinkNamingNoStoredRecordIsNotOffered() {
        links.add(patientLink("a404", "hcp-4040", "ghost@mail.gh"));

        assertThat(offered()).extracting(RoundCustomerDTO::customerId).doesNotContain("hcp-4040");
        assertThat(offered()).hasSize(1);
    }

    /**
     * Named rows first, and a row that cannot be named sorts last rather than to the top.
     *
     * <p>An empty label at the head of a dropdown reads as the placeholder, which is the one option
     * in that control that means "nothing chosen".
     */
    @Test
    void aRowWithNoNameSortsBelowTheNamedOnes() {
        links.add(patientLink("a13", "hcp-8814", null));
        patients.add(patientWithNoProfile("a13"));

        assertThat(offered()).extracting(RoundCustomerDTO::name).containsExactly("Kwame Darkwa", null);
    }

    /** The whole set, taken through the paged call the endpoint makes. */
    private List<RoundCustomerDTO> offered() {
        return service.plannableCustomers(PageRequest.of(0, 50)).getContent();
    }

    private static DirectoryLink patientLink(String localId, String externalId, String email) {
        DirectoryLink link = new DirectoryLink();
        link.setId("dl-" + localId);
        link.setSource(DirectorySource.HC_PATIENT);
        link.setSubjectKind(DirectorySubjectKind.PATIENT);
        link.setExternalKey(email == null ? externalId : email);
        link.setExternalId(externalId);
        link.setLocalId(localId);
        link.setEmail(email);
        return link;
    }

    private static Patient patient(String id, String firstName, String lastName) {
        Patient patient = patientWithNoProfile(id);
        Profile profile = new Profile();
        profile.setId("profile-" + id);
        profile.setFirstName(firstName);
        profile.setLastName(lastName);
        patient.setProfile(profile);
        return patient;
    }

    private static Patient patientWithNoProfile(String id) {
        Patient patient = new Patient().status(AccountStatus.ACTIVE).joinedOn(LocalDate.of(2026, 3, 1));
        patient.setId(id);
        return patient;
    }
}
