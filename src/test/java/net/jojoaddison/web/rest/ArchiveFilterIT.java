package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Stream;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Vendor;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.VendorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The archived/unarchived split on the three directory list endpoints.
 *
 * <p>The entity ITs cover that {@code isArchived} round-trips. This covers the thing that can be
 * wrong while every one of those still passes: which records come back. A filter that returns the
 * whole collection, or none of it, is a 200 with a plausible body either way — the console would
 * show an intact directory that quietly ignores archiving, or an empty one that looks like no data.
 *
 * <p>The third case is the one worth the file on its own. A document written before this field
 * existed has no {@code is_archived} key at all, and {@code {is_archived: false}} does not match a
 * missing key in MongoDB — so an equality filter would have hidden every pre-existing record the
 * day this shipped. The repositories use {@code $ne: true} instead, and
 * {@code legacyRecordsWithNoFieldCountAsActive} is what holds them to it: it writes a document with
 * the field genuinely absent rather than set to null, which is the only way to reproduce it.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class ArchiveFilterIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private VendorRepository vendorRepository;

    private Patient activePatient;
    private Patient archivedPatient;
    private Professional activeProfessional;
    private Professional archivedProfessional;
    private Vendor activeVendor;
    private Vendor archivedVendor;

    static Stream<Arguments> endpoints() {
        return Stream.of(
            Arguments.of("/api/patients", "activePatient", "archivedPatient"),
            Arguments.of("/api/professionals", "activeProfessional", "archivedProfessional"),
            Arguments.of("/api/vendors", "activeVendor", "archivedVendor")
        );
    }

    @BeforeEach
    void seed() {
        activePatient = patientRepository.save(PatientResourceIT.createEntity().isArchived(false));
        archivedPatient = patientRepository.save(PatientResourceIT.createEntity().isArchived(true));
        activeProfessional = professionalRepository.save(ProfessionalResourceIT.createEntity().isArchived(false));
        archivedProfessional = professionalRepository.save(ProfessionalResourceIT.createEntity().isArchived(true));
        activeVendor = vendorRepository.save(VendorResourceIT.createEntity().isArchived(false));
        archivedVendor = vendorRepository.save(VendorResourceIT.createEntity().isArchived(true));
    }

    @AfterEach
    void cleanup() {
        patientRepository.deleteAll();
        professionalRepository.deleteAll();
        vendorRepository.deleteAll();
    }

    private String idOf(String field) {
        return switch (field) {
            case "activePatient" -> activePatient.getId();
            case "archivedPatient" -> archivedPatient.getId();
            case "activeProfessional" -> activeProfessional.getId();
            case "archivedProfessional" -> archivedProfessional.getId();
            case "activeVendor" -> activeVendor.getId();
            case "archivedVendor" -> archivedVendor.getId();
            default -> throw new IllegalArgumentException(field);
        };
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void notEqualsTrueReturnsOnlyTheUnarchived(String path, String active, String archived) throws Exception {
        mvc
            .perform(get(path).param("isArchived.notEquals", "true").param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(idOf(active))).exists())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(idOf(archived))).doesNotExist());
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void equalsTrueReturnsOnlyTheArchived(String path, String active, String archived) throws Exception {
        mvc
            .perform(get(path).param("isArchived.equals", "true").param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(idOf(archived))).exists())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(idOf(active))).doesNotExist());
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void noFilterReturnsBothHalves(String path, String active, String archived) throws Exception {
        mvc
            .perform(get(path).param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(idOf(active))).exists())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(idOf(archived))).exists());
    }

    /**
     * The regression this whole arrangement exists to avoid.
     *
     * <p>{@code MongoTemplate.remove} on the field, rather than saving {@code null}, because those
     * are different documents: Spring Data writes an explicit null, and the point is a key that is
     * not there. Only the second reproduces a record written before the field existed.
     */
    @Test
    void legacyRecordsWithNoFieldCountAsActive() throws Exception {
        Patient legacy = patientRepository.save(PatientResourceIT.createEntity().isArchived(null));
        mongoTemplate
            .getCollection("patient")
            .updateOne(
                new org.bson.Document("_id", new org.bson.types.ObjectId(legacy.getId())),
                new org.bson.Document("$unset", new org.bson.Document("is_archived", ""))
            );

        org.bson.Document stored = mongoTemplate
            .getCollection("patient")
            .find(new org.bson.Document("_id", new org.bson.types.ObjectId(legacy.getId())))
            .first();
        assertThat(stored).isNotNull();
        assertThat(stored.containsKey("is_archived")).as("the field must genuinely be absent, not null").isFalse();

        mvc
            .perform(get("/api/patients").param("isArchived.notEquals", "true").param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(legacy.getId())).exists());

        mvc
            .perform(get("/api/patients").param("isArchived.equals", "true").param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(legacy.getId())).doesNotExist());
    }

    /** X-Total-Count has to describe the filtered set, or page 2 of the directory has holes. */
    @Test
    void theTotalCountDescribesTheFilteredSetNotTheCollection() throws Exception {
        List<String> totals = List.of(
            mvc
                .perform(get("/api/patients").param("isArchived.notEquals", "true").param("size", "100"))
                .andReturn()
                .getResponse()
                .getHeader("X-Total-Count"),
            mvc
                .perform(get("/api/patients").param("isArchived.equals", "true").param("size", "100"))
                .andReturn()
                .getResponse()
                .getHeader("X-Total-Count"),
            mvc.perform(get("/api/patients").param("size", "100")).andReturn().getResponse().getHeader("X-Total-Count")
        );

        assertThat(Integer.parseInt(totals.get(0)) + Integer.parseInt(totals.get(1))).isEqualTo(Integer.parseInt(totals.get(2)));
    }
}
