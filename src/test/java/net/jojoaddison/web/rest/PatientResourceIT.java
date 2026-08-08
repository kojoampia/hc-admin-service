package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.PatientAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.repository.PatientRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link PatientResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class PatientResourceIT {

    private static final AccountStatus DEFAULT_STATUS = AccountStatus.ACTIVE;
    private static final AccountStatus UPDATED_STATUS = AccountStatus.PENDING;

    private static final LocalDate DEFAULT_JOINED_ON = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_JOINED_ON = LocalDate.parse("2024-03-26");

    private static final LocalDate DEFAULT_LAST_ACTIVE_ON = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_LAST_ACTIVE_ON = LocalDate.parse("2024-03-26");

    private static final Integer DEFAULT_CASE_COUNT = 0;
    private static final Integer UPDATED_CASE_COUNT = 1;

    private static final String ENTITY_API_URL = "/api/patients";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private PatientRepository patientRepository;

    @Mock
    private PatientRepository patientRepositoryMock;

    @Autowired
    private MockMvc restPatientMockMvc;

    private Patient patient;

    private Patient insertedPatient;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Patient createEntity() {
        Patient patient = new Patient()
            .status(DEFAULT_STATUS)
            .joinedOn(DEFAULT_JOINED_ON)
            .lastActiveOn(DEFAULT_LAST_ACTIVE_ON)
            .caseCount(DEFAULT_CASE_COUNT);
        // Add required entity
        Profile profile;
        profile = ProfileResourceIT.createEntity();
        profile.setId("fixed-id-for-tests");
        patient.setProfile(profile);
        return patient;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Patient createUpdatedEntity() {
        Patient updatedPatient = new Patient()
            .status(UPDATED_STATUS)
            .joinedOn(UPDATED_JOINED_ON)
            .lastActiveOn(UPDATED_LAST_ACTIVE_ON)
            .caseCount(UPDATED_CASE_COUNT);
        // Add required entity
        Profile profile;
        profile = ProfileResourceIT.createUpdatedEntity();
        profile.setId("fixed-id-for-tests");
        updatedPatient.setProfile(profile);
        return updatedPatient;
    }

    @BeforeEach
    void initTest() {
        patient = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedPatient != null) {
            patientRepository.delete(insertedPatient);
            insertedPatient = null;
        }
    }

    @Test
    void createPatient() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Patient
        var returnedPatient = om.readValue(
            restPatientMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patient)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Patient.class
        );

        // Validate the Patient in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertPatientUpdatableFieldsEquals(returnedPatient, getPersistedPatient(returnedPatient));

        insertedPatient = returnedPatient;
    }

    @Test
    void createPatientWithExistingId() throws Exception {
        // Create the Patient with an existing ID
        patient.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restPatientMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patient)))
            .andExpect(status().isBadRequest());

        // Validate the Patient in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        patient.setStatus(null);

        // Create the Patient, which fails.

        restPatientMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patient)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkJoinedOnIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        patient.setJoinedOn(null);

        // Create the Patient, which fails.

        restPatientMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patient)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllPatients() throws Exception {
        // Initialize the database
        insertedPatient = patientRepository.save(patient);

        // Get all the patientList
        restPatientMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(patient.getId())))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].joinedOn").value(hasItem(DEFAULT_JOINED_ON.toString())))
            .andExpect(jsonPath("$.[*].lastActiveOn").value(hasItem(DEFAULT_LAST_ACTIVE_ON.toString())))
            .andExpect(jsonPath("$.[*].caseCount").value(hasItem(DEFAULT_CASE_COUNT)));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllPatientsWithEagerRelationshipsIsEnabled() throws Exception {
        when(patientRepositoryMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restPatientMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(patientRepositoryMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllPatientsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(patientRepositoryMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restPatientMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(patientRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    void getPatient() throws Exception {
        // Initialize the database
        insertedPatient = patientRepository.save(patient);

        // Get the patient
        restPatientMockMvc
            .perform(get(ENTITY_API_URL_ID, patient.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(patient.getId()))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.joinedOn").value(DEFAULT_JOINED_ON.toString()))
            .andExpect(jsonPath("$.lastActiveOn").value(DEFAULT_LAST_ACTIVE_ON.toString()))
            .andExpect(jsonPath("$.caseCount").value(DEFAULT_CASE_COUNT));
    }

    @Test
    void getNonExistingPatient() throws Exception {
        // Get the patient
        restPatientMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingPatient() throws Exception {
        // Initialize the database
        insertedPatient = patientRepository.save(patient);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the patient
        Patient updatedPatient = patientRepository.findById(patient.getId()).orElseThrow();
        updatedPatient
            .status(UPDATED_STATUS)
            .joinedOn(UPDATED_JOINED_ON)
            .lastActiveOn(UPDATED_LAST_ACTIVE_ON)
            .caseCount(UPDATED_CASE_COUNT);

        restPatientMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedPatient.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedPatient))
            )
            .andExpect(status().isOk());

        // Validate the Patient in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedPatientToMatchAllProperties(updatedPatient);
    }

    @Test
    void putNonExistingPatient() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        patient.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPatientMockMvc
            .perform(put(ENTITY_API_URL_ID, patient.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patient)))
            .andExpect(status().isBadRequest());

        // Validate the Patient in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchPatient() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        patient.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPatientMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(patient))
            )
            .andExpect(status().isBadRequest());

        // Validate the Patient in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamPatient() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        patient.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPatientMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patient)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Patient in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdatePatientWithPatch() throws Exception {
        // Initialize the database
        insertedPatient = patientRepository.save(patient);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the patient using partial update
        Patient partialUpdatedPatient = new Patient();
        partialUpdatedPatient.setId(patient.getId());

        partialUpdatedPatient.status(UPDATED_STATUS).joinedOn(UPDATED_JOINED_ON);

        restPatientMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPatient.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPatient))
            )
            .andExpect(status().isOk());

        // Validate the Patient in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPatientUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedPatient, patient), getPersistedPatient(patient));
    }

    @Test
    void fullUpdatePatientWithPatch() throws Exception {
        // Initialize the database
        insertedPatient = patientRepository.save(patient);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the patient using partial update
        Patient partialUpdatedPatient = new Patient();
        partialUpdatedPatient.setId(patient.getId());

        partialUpdatedPatient
            .status(UPDATED_STATUS)
            .joinedOn(UPDATED_JOINED_ON)
            .lastActiveOn(UPDATED_LAST_ACTIVE_ON)
            .caseCount(UPDATED_CASE_COUNT);

        restPatientMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPatient.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPatient))
            )
            .andExpect(status().isOk());

        // Validate the Patient in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPatientUpdatableFieldsEquals(partialUpdatedPatient, getPersistedPatient(partialUpdatedPatient));
    }

    @Test
    void patchNonExistingPatient() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        patient.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPatientMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, patient.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(patient))
            )
            .andExpect(status().isBadRequest());

        // Validate the Patient in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchPatient() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        patient.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPatientMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patient))
            )
            .andExpect(status().isBadRequest());

        // Validate the Patient in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamPatient() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        patient.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPatientMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(patient)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Patient in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deletePatient() throws Exception {
        // Initialize the database
        insertedPatient = patientRepository.save(patient);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the patient
        restPatientMockMvc
            .perform(delete(ENTITY_API_URL_ID, patient.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return patientRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected Patient getPersistedPatient(Patient patient) {
        return patientRepository.findById(patient.getId()).orElseThrow();
    }

    protected void assertPersistedPatientToMatchAllProperties(Patient expectedPatient) {
        assertPatientAllPropertiesEquals(expectedPatient, getPersistedPatient(expectedPatient));
    }

    protected void assertPersistedPatientToMatchUpdatableProperties(Patient expectedPatient) {
        assertPatientAllUpdatablePropertiesEquals(expectedPatient, getPersistedPatient(expectedPatient));
    }
}
