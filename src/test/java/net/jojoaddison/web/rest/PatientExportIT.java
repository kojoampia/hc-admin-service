package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Address;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.IdType;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.Sex;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import net.jojoaddison.repository.AddressRepository;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ProfileRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/patients/export} — the patient directory as CSV.
 *
 * <p>Its own class rather than cases in {@code PatientResourceIT}, because every assertion here is
 * about the whole matched set rather than one record, and that only means anything if this test owns
 * what is in the collection.
 *
 * <p><strong>What this guards is agreement with the list.</strong> The export takes the same filters
 * as {@code GET /api/patients} and exists to hand somebody the rows they were looking at; a file
 * that quietly holds more than the screen showed is the failure mode, and it is invisible from
 * inside the file. So the filter cases below are the point of the class, not padding around the
 * header row.
 *
 * <p>Authorization is asserted in {@link ApiAuthorizationIT}, which is the only test here that runs
 * with the filter chain on. This one runs with {@code addFilters = false} like every other
 * {@code *IT}, so it says nothing about who may call it.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class PatientExportIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    /**
     * Removed by id rather than by {@code deleteAll()}, because the professional collection is not
     * this class's to empty — every other case here owns only patients, profiles and addresses.
     */
    private static final String UNNAMEABLE_LEAD_ID = "patient-export-it-lead";

    @BeforeEach
    void seed() {
        patientRepository.deleteAll();
        profileRepository.deleteAll();
        addressRepository.deleteAll();

        patientRepository.save(patient(AccountStatus.ACTIVE, false, profile("Ama", "Boateng", "Osu", "Accra")));
        patientRepository.save(patient(AccountStatus.SUSPENDED, false, profile("Kwesi", "Owusu", "Asylum Down", "Accra")));
        // Archived, so it is out of the directory's default view and must be out of the file too.
        patientRepository.save(patient(AccountStatus.ACTIVE, true, profile("Efua", "Nyarko", "Tema", "Greater Accra")));
    }

    @AfterEach
    void tearDown() {
        patientRepository.deleteAll();
        profileRepository.deleteAll();
        addressRepository.deleteAll();
        professionalRepository.deleteById(UNNAMEABLE_LEAD_ID);
    }

    /**
     * A clinical lead with nothing to name it by exports an empty cell, and its id reaches the file
     * nowhere.
     *
     * <p><strong>This case exists to be run against a real database, not because the shaping needs
     * it.</strong> {@code PatientCsvExporterTest} asserts the same rule on a hand-built object, and
     * that proves the branch. What it cannot prove is that the branch is reachable — and the whole
     * argument for fixing this was that {@code ""} is storable, because {@code licenceNumber} is
     * {@code @NotNull} with no {@code @NotBlank} anywhere in this service. So the professional below
     * goes in through the repository, past the {@code ValidatingMongoEventListener} that
     * {@code DatabaseConfiguration} registers, and is read back before anything is asserted about the
     * file. If a {@code @NotBlank} is ever added, this fails at the save and says so, instead of
     * quietly becoming a test of an unreachable branch.
     *
     * <p>It also reads the bytes the endpoint actually returns rather than an exporter's output. No
     * test in this repository opened the clinical-lead cell of a real response before this one, which
     * is why an id sat in it through four passes over the rule that forbids it.
     */
    @Test
    void aLeadWithABlankLicenceIsStorableAndItsIdNeverReachesTheFile() throws Exception {
        Professional lead = new Professional()
            .role(ProfessionalRole.NURSE)
            .licenceNumber("")
            .verification(VerificationStatus.PENDING)
            .status(AccountStatus.ACTIVE)
            .joinedOn(LocalDate.of(2026, 2, 1));
        lead.setId(UNNAMEABLE_LEAD_ID);
        Professional stored = professionalRepository.save(lead);

        // The reachability claim, asserted rather than reasoned from the annotations.
        assertThat(professionalRepository.findById(stored.getId()))
            .get()
            .satisfies(found -> assertThat(found.getLicenceNumber()).isEmpty());
        assertThat(stored.getProfile()).isNull();

        Patient patient = patient(AccountStatus.ACTIVE, false, profile("Adjoa", "Mensah", "Madina", "Accra"));
        patient.setClinicalLead(stored);
        patientRepository.save(patient);

        List<String> lines = export();
        String row = lines.stream().filter(line -> line.startsWith("\"Adjoa Mensah\"")).findFirst().orElseThrow();

        assertThat(cells(row).get(8)).isEmpty();
        assertThat(body()).doesNotContain(UNNAMEABLE_LEAD_ID);
    }

    /** Splits a fully-quoted row back into its cells. */
    private static List<String> cells(String line) {
        String inner = line.substring(1, line.length() - 1);
        return List.of(inner.split("\",\"", -1));
    }

    @Test
    void exportsAHeaderRowAndOneRowPerPatient() throws Exception {
        List<String> lines = export();

        assertThat(lines).hasSize(4);
        assertThat(lines.getFirst()).startsWith("\"Patient\",\"Id number\",\"Age\",\"Sex\",\"Location\"");
        assertThat(lines).anyMatch(line -> line.startsWith("\"Ama Boateng\""));
        assertThat(lines).anyMatch(line -> line.startsWith("\"Kwesi Owusu\""));
    }

    /**
     * The archived filter, which is the one the directory applies by default.
     *
     * <p>{@code notEquals=true} rather than {@code equals=false}, because that is what the console
     * sends and because a document written before {@code isArchived} existed carries no value at
     * all — {@code equals=false} matches none of them, and the export would come back empty against
     * a real database while passing against a freshly-seeded one.
     */
    @Test
    void honoursTheArchivedFilter() throws Exception {
        List<String> visible = export("isArchived.notEquals", "true");
        assertThat(visible).hasSize(3);
        assertThat(visible).noneMatch(line -> line.startsWith("\"Efua Nyarko\""));

        List<String> archived = export("isArchived.equals", "true");
        assertThat(archived).hasSize(2);
        assertThat(archived.get(1)).startsWith("\"Efua Nyarko\"");
    }

    /** The status tiles filter the list; the same parameter has to narrow the file. */
    @Test
    void honoursTheStatusFilter() throws Exception {
        List<String> lines = export("status.equals", "SUSPENDED");

        assertThat(lines).hasSize(2);
        assertThat(lines.get(1)).startsWith("\"Kwesi Owusu\"");
    }

    /**
     * An unfiltered export and an unfiltered list see the same set.
     *
     * <p>Counted against the list's own {@code X-Total-Count} rather than against a literal, so the
     * two cannot drift apart without this failing — which is the property the whole endpoint rests
     * on and the one a hard-coded 3 would stop checking the moment the fixture changed.
     */
    @Test
    void theFileHoldsExactlyWhatTheListWouldPage() throws Exception {
        String total = mvc
            .perform(get("/api/patients").param("page", "0").param("size", "1").param("status.equals", "ACTIVE"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("X-Total-Count");

        List<String> lines = export("status.equals", "ACTIVE");

        assertThat(lines.size() - 1).isEqualTo(Integer.parseInt(total));
    }

    /** Named so a browser saves something recognisable rather than "export". */
    @Test
    void arrivesAsADatedAttachment() throws Exception {
        String disposition = mvc
            .perform(get("/api/patients/export"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Content-Disposition");

        assertThat(disposition).isEqualTo("attachment; filename=\"patients-" + LocalDate.now() + ".csv\"");
    }

    /**
     * The body opens with a UTF-8 BOM.
     *
     * <p>Asserted because it is invisible: without it Excel reads the file in the system codepage
     * and every name carrying a diacritic arrives mangled, which nothing in a test that only checks
     * the text would catch, and which nobody notices until a real directory is exported.
     */
    @Test
    void opensWithAByteOrderMarkSoExcelReadsItAsUtf8() throws Exception {
        assertThat(body()).startsWith("﻿");
    }

    /** Lines of the response, BOM stripped. */
    private List<String> export(String... params) throws Exception {
        return List.of(body(params).replace("﻿", "").split("\r\n"));
    }

    /**
     * The body, after the async dispatch a streaming response needs.
     *
     * <p>{@code StreamingResponseBody} returns from the handler before anything is written, so
     * MockMvc's first {@code perform} completes with a 200 and an empty body. Reading that response
     * passes every status assertion and checks nothing — which is exactly what happened the first
     * time this class ran, reporting an export of two patients as one line of nothing.
     * {@code asyncStarted()} is asserted for that reason: if the handler is ever changed to return
     * its body directly, this has to fail loudly rather than dispatch an empty result.
     */
    private String body(String... params) throws Exception {
        var request = get("/api/patients/export");
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        var started = mvc.perform(request).andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn();
        return mvc.perform(asyncDispatch(started)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /**
     * A complete profile.
     *
     * <p>{@code Profile} and {@code Address} both require most of their fields, so everything below
     * is filled even where this class does not read it. The half-filled records — no name, no date
     * of birth, no address — are covered in {@code PatientCsvExporterTest}, where they can be built
     * without going past a validator that would refuse to store them.
     */
    private static Profile profile(String first, String last, String town, String city) {
        Address address = new Address()
            .digitalAddress("GA-000-0000")
            .streetAddress("1 Independence Ave")
            .townDistrict(town)
            .cityState(city)
            .region("Greater Accra")
            .country("Ghana");
        return new Profile()
            .accountId(first.toLowerCase() + last.toLowerCase())
            .firstName(first)
            .lastName(last)
            .dateOfBirth(LocalDate.of(1986, 3, 4))
            .sex(Sex.FEMALE)
            .mobilePhone("+233200000000")
            .email(first.toLowerCase() + "@example.com")
            .idType(IdType.GHANA_CARD)
            .idNumber("GHA-000000-0")
            .address(address);
    }

    private Patient patient(AccountStatus status, boolean archived, Profile profile) {
        Profile saved = profileRepository.save(profileWithSavedAddress(profile));
        return new Patient().status(status).joinedOn(LocalDate.of(2026, 1, 1)).isArchived(archived).profile(saved);
    }

    private Profile profileWithSavedAddress(Profile profile) {
        if (profile.getAddress() != null) {
            profile.setAddress(addressRepository.save(profile.getAddress()));
        }
        return profile;
    }
}
