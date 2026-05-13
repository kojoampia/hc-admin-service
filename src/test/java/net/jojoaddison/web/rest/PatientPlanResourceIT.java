package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.PatientPlanAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.PatientPlan;
import net.jojoaddison.repository.PatientPlanRepository;
import net.jojoaddison.service.dto.PatientPlanDTO;
import net.jojoaddison.service.mapper.PatientPlanMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link PatientPlanResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class PatientPlanResourceIT {

    private static final String DEFAULT_PLAN_ID = "AAAAAAAAAA";
    private static final String UPDATED_PLAN_ID = "BBBBBBBBBB";

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_START_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_START_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final LocalDate DEFAULT_END_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_END_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final Instant DEFAULT_CREATED_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/patient-plans";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private final ObjectMapper om = TestUtil.createObjectMapper();

    @Autowired
    private PatientPlanRepository patientPlanRepository;

    @Autowired
    private PatientPlanMapper patientPlanMapper;

    @Autowired
    private MockMvc restPatientPlanMockMvc;

    private PatientPlan patientPlan;

    private PatientPlan insertedPatientPlan;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static PatientPlan createEntity() {
        return new PatientPlan()
            .planId(DEFAULT_PLAN_ID)
            .patientId(DEFAULT_PATIENT_ID)
            .startDate(DEFAULT_START_DATE)
            .endDate(DEFAULT_END_DATE)
            .createdDate(DEFAULT_CREATED_DATE)
            .createdBy(DEFAULT_CREATED_BY);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static PatientPlan createUpdatedEntity() {
        return new PatientPlan()
            .planId(UPDATED_PLAN_ID)
            .patientId(UPDATED_PATIENT_ID)
            .startDate(UPDATED_START_DATE)
            .endDate(UPDATED_END_DATE)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY);
    }

    @BeforeEach
    void initTest() {
        patientPlan = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedPatientPlan != null) {
            patientPlanRepository.delete(insertedPatientPlan);
            insertedPatientPlan = null;
        }
    }

    @Test
    void createPatientPlan() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the PatientPlan
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);
        var returnedPatientPlanDTO = om.readValue(
            restPatientPlanMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patientPlanDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            PatientPlanDTO.class
        );

        // Validate the PatientPlan in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedPatientPlan = patientPlanMapper.toEntity(returnedPatientPlanDTO);
        assertPatientPlanUpdatableFieldsEquals(returnedPatientPlan, getPersistedPatientPlan(returnedPatientPlan));

        insertedPatientPlan = returnedPatientPlan;
    }

    @Test
    void createPatientPlanWithExistingId() throws Exception {
        // Create the PatientPlan with an existing ID
        patientPlan.setId("existing_id");
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restPatientPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patientPlanDTO)))
            .andExpect(status().isBadRequest());

        // Validate the PatientPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkPlanIdIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        patientPlan.setPlanId(null);

        // Create the PatientPlan, which fails.
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);

        restPatientPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patientPlanDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkPatientIdIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        patientPlan.setPatientId(null);

        // Create the PatientPlan, which fails.
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);

        restPatientPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patientPlanDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkStartDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        patientPlan.setStartDate(null);

        // Create the PatientPlan, which fails.
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);

        restPatientPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patientPlanDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkEndDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        patientPlan.setEndDate(null);

        // Create the PatientPlan, which fails.
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);

        restPatientPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patientPlanDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkCreatedDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        patientPlan.setCreatedDate(null);

        // Create the PatientPlan, which fails.
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);

        restPatientPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patientPlanDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkCreatedByIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        patientPlan.setCreatedBy(null);

        // Create the PatientPlan, which fails.
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);

        restPatientPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patientPlanDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllPatientPlans() throws Exception {
        // Initialize the database
        insertedPatientPlan = patientPlanRepository.save(patientPlan);

        // Get all the patientPlanList
        restPatientPlanMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(patientPlan.getId())))
            .andExpect(jsonPath("$.[*].planId").value(hasItem(DEFAULT_PLAN_ID)))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].startDate").value(hasItem(DEFAULT_START_DATE.toString())))
            .andExpect(jsonPath("$.[*].endDate").value(hasItem(DEFAULT_END_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)));
    }

    @Test
    void getPatientPlan() throws Exception {
        // Initialize the database
        insertedPatientPlan = patientPlanRepository.save(patientPlan);

        // Get the patientPlan
        restPatientPlanMockMvc
            .perform(get(ENTITY_API_URL_ID, patientPlan.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(patientPlan.getId()))
            .andExpect(jsonPath("$.planId").value(DEFAULT_PLAN_ID))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.startDate").value(DEFAULT_START_DATE.toString()))
            .andExpect(jsonPath("$.endDate").value(DEFAULT_END_DATE.toString()))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY));
    }

    @Test
    void getNonExistingPatientPlan() throws Exception {
        // Get the patientPlan
        restPatientPlanMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingPatientPlan() throws Exception {
        // Initialize the database
        insertedPatientPlan = patientPlanRepository.save(patientPlan);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the patientPlan
        PatientPlan updatedPatientPlan = patientPlanRepository.findById(patientPlan.getId()).orElseThrow();
        updatedPatientPlan
            .planId(UPDATED_PLAN_ID)
            .patientId(UPDATED_PATIENT_ID)
            .startDate(UPDATED_START_DATE)
            .endDate(UPDATED_END_DATE)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY);
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(updatedPatientPlan);

        restPatientPlanMockMvc
            .perform(
                put(ENTITY_API_URL_ID, patientPlanDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(patientPlanDTO))
            )
            .andExpect(status().isOk());

        // Validate the PatientPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedPatientPlanToMatchAllProperties(updatedPatientPlan);
    }

    @Test
    void putNonExistingPatientPlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        patientPlan.setId(UUID.randomUUID().toString());

        // Create the PatientPlan
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPatientPlanMockMvc
            .perform(
                put(ENTITY_API_URL_ID, patientPlanDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(patientPlanDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the PatientPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchPatientPlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        patientPlan.setId(UUID.randomUUID().toString());

        // Create the PatientPlan
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPatientPlanMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(patientPlanDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the PatientPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamPatientPlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        patientPlan.setId(UUID.randomUUID().toString());

        // Create the PatientPlan
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPatientPlanMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(patientPlanDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the PatientPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdatePatientPlanWithPatch() throws Exception {
        // Initialize the database
        insertedPatientPlan = patientPlanRepository.save(patientPlan);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the patientPlan using partial update
        PatientPlan partialUpdatedPatientPlan = new PatientPlan();
        partialUpdatedPatientPlan.setId(patientPlan.getId());

        partialUpdatedPatientPlan.planId(UPDATED_PLAN_ID).startDate(UPDATED_START_DATE).endDate(UPDATED_END_DATE);

        restPatientPlanMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPatientPlan.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPatientPlan))
            )
            .andExpect(status().isOk());

        // Validate the PatientPlan in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPatientPlanUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedPatientPlan, patientPlan),
            getPersistedPatientPlan(patientPlan)
        );
    }

    @Test
    void fullUpdatePatientPlanWithPatch() throws Exception {
        // Initialize the database
        insertedPatientPlan = patientPlanRepository.save(patientPlan);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the patientPlan using partial update
        PatientPlan partialUpdatedPatientPlan = new PatientPlan();
        partialUpdatedPatientPlan.setId(patientPlan.getId());

        partialUpdatedPatientPlan
            .planId(UPDATED_PLAN_ID)
            .patientId(UPDATED_PATIENT_ID)
            .startDate(UPDATED_START_DATE)
            .endDate(UPDATED_END_DATE)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY);

        restPatientPlanMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPatientPlan.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPatientPlan))
            )
            .andExpect(status().isOk());

        // Validate the PatientPlan in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPatientPlanUpdatableFieldsEquals(partialUpdatedPatientPlan, getPersistedPatientPlan(partialUpdatedPatientPlan));
    }

    @Test
    void patchNonExistingPatientPlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        patientPlan.setId(UUID.randomUUID().toString());

        // Create the PatientPlan
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPatientPlanMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, patientPlanDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patientPlanDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the PatientPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchPatientPlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        patientPlan.setId(UUID.randomUUID().toString());

        // Create the PatientPlan
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPatientPlanMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(patientPlanDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the PatientPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamPatientPlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        patientPlan.setId(UUID.randomUUID().toString());

        // Create the PatientPlan
        PatientPlanDTO patientPlanDTO = patientPlanMapper.toDto(patientPlan);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPatientPlanMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(patientPlanDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the PatientPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deletePatientPlan() throws Exception {
        // Initialize the database
        insertedPatientPlan = patientPlanRepository.save(patientPlan);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the patientPlan
        restPatientPlanMockMvc
            .perform(delete(ENTITY_API_URL_ID, patientPlan.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return patientPlanRepository.count();
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

    protected PatientPlan getPersistedPatientPlan(PatientPlan patientPlan) {
        return patientPlanRepository.findById(patientPlan.getId()).orElseThrow();
    }

    protected void assertPersistedPatientPlanToMatchAllProperties(PatientPlan expectedPatientPlan) {
        assertPatientPlanAllPropertiesEquals(expectedPatientPlan, getPersistedPatientPlan(expectedPatientPlan));
    }

    protected void assertPersistedPatientPlanToMatchUpdatableProperties(PatientPlan expectedPatientPlan) {
        assertPatientPlanAllUpdatablePropertiesEquals(expectedPatientPlan, getPersistedPatientPlan(expectedPatientPlan));
    }
}
