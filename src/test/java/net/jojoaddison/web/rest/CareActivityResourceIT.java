package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.CareActivityAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.CareActivity;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.repository.CareActivityRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link CareActivityResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class CareActivityResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_OCCURRED_ON = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_OCCURRED_ON = LocalDate.parse("2024-03-26");

    private static final String ENTITY_API_URL = "/api/care-activities";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private CareActivityRepository careActivityRepository;

    @Autowired
    private MockMvc restCareActivityMockMvc;

    private CareActivity careActivity;

    private CareActivity insertedCareActivity;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static CareActivity createEntity() {
        CareActivity careActivity = new CareActivity().name(DEFAULT_NAME).description(DEFAULT_DESCRIPTION).occurredOn(DEFAULT_OCCURRED_ON);
        // Add required entity
        Patient patient;
        patient = PatientResourceIT.createEntity();
        patient.setId("fixed-id-for-tests");
        careActivity.setPatient(patient);
        return careActivity;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static CareActivity createUpdatedEntity() {
        CareActivity updatedCareActivity = new CareActivity()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .occurredOn(UPDATED_OCCURRED_ON);
        // Add required entity
        Patient patient;
        patient = PatientResourceIT.createUpdatedEntity();
        patient.setId("fixed-id-for-tests");
        updatedCareActivity.setPatient(patient);
        return updatedCareActivity;
    }

    @BeforeEach
    void initTest() {
        careActivity = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedCareActivity != null) {
            careActivityRepository.delete(insertedCareActivity);
            insertedCareActivity = null;
        }
    }

    @Test
    void createCareActivity() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the CareActivity
        var returnedCareActivity = om.readValue(
            restCareActivityMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(careActivity)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            CareActivity.class
        );

        // Validate the CareActivity in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertCareActivityUpdatableFieldsEquals(returnedCareActivity, getPersistedCareActivity(returnedCareActivity));

        insertedCareActivity = returnedCareActivity;
    }

    @Test
    void createCareActivityWithExistingId() throws Exception {
        // Create the CareActivity with an existing ID
        careActivity.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restCareActivityMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(careActivity)))
            .andExpect(status().isBadRequest());

        // Validate the CareActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        careActivity.setName(null);

        // Create the CareActivity, which fails.

        restCareActivityMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(careActivity)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkOccurredOnIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        careActivity.setOccurredOn(null);

        // Create the CareActivity, which fails.

        restCareActivityMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(careActivity)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllCareActivities() throws Exception {
        // Initialize the database
        insertedCareActivity = careActivityRepository.save(careActivity);

        // Get all the careActivityList
        restCareActivityMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(careActivity.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].occurredOn").value(hasItem(DEFAULT_OCCURRED_ON.toString())));
    }

    @Test
    void getCareActivity() throws Exception {
        // Initialize the database
        insertedCareActivity = careActivityRepository.save(careActivity);

        // Get the careActivity
        restCareActivityMockMvc
            .perform(get(ENTITY_API_URL_ID, careActivity.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(careActivity.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.occurredOn").value(DEFAULT_OCCURRED_ON.toString()));
    }

    @Test
    void getNonExistingCareActivity() throws Exception {
        // Get the careActivity
        restCareActivityMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingCareActivity() throws Exception {
        // Initialize the database
        insertedCareActivity = careActivityRepository.save(careActivity);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the careActivity
        CareActivity updatedCareActivity = careActivityRepository.findById(careActivity.getId()).orElseThrow();
        updatedCareActivity.name(UPDATED_NAME).description(UPDATED_DESCRIPTION).occurredOn(UPDATED_OCCURRED_ON);

        restCareActivityMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedCareActivity.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedCareActivity))
            )
            .andExpect(status().isOk());

        // Validate the CareActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedCareActivityToMatchAllProperties(updatedCareActivity);
    }

    @Test
    void putNonExistingCareActivity() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        careActivity.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restCareActivityMockMvc
            .perform(
                put(ENTITY_API_URL_ID, careActivity.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(careActivity))
            )
            .andExpect(status().isBadRequest());

        // Validate the CareActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchCareActivity() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        careActivity.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCareActivityMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(careActivity))
            )
            .andExpect(status().isBadRequest());

        // Validate the CareActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamCareActivity() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        careActivity.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCareActivityMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(careActivity)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the CareActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateCareActivityWithPatch() throws Exception {
        // Initialize the database
        insertedCareActivity = careActivityRepository.save(careActivity);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the careActivity using partial update
        CareActivity partialUpdatedCareActivity = new CareActivity();
        partialUpdatedCareActivity.setId(careActivity.getId());

        partialUpdatedCareActivity.name(UPDATED_NAME).occurredOn(UPDATED_OCCURRED_ON);

        restCareActivityMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedCareActivity.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedCareActivity))
            )
            .andExpect(status().isOk());

        // Validate the CareActivity in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertCareActivityUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedCareActivity, careActivity),
            getPersistedCareActivity(careActivity)
        );
    }

    @Test
    void fullUpdateCareActivityWithPatch() throws Exception {
        // Initialize the database
        insertedCareActivity = careActivityRepository.save(careActivity);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the careActivity using partial update
        CareActivity partialUpdatedCareActivity = new CareActivity();
        partialUpdatedCareActivity.setId(careActivity.getId());

        partialUpdatedCareActivity.name(UPDATED_NAME).description(UPDATED_DESCRIPTION).occurredOn(UPDATED_OCCURRED_ON);

        restCareActivityMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedCareActivity.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedCareActivity))
            )
            .andExpect(status().isOk());

        // Validate the CareActivity in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertCareActivityUpdatableFieldsEquals(partialUpdatedCareActivity, getPersistedCareActivity(partialUpdatedCareActivity));
    }

    @Test
    void patchNonExistingCareActivity() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        careActivity.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restCareActivityMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, careActivity.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(careActivity))
            )
            .andExpect(status().isBadRequest());

        // Validate the CareActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchCareActivity() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        careActivity.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCareActivityMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(careActivity))
            )
            .andExpect(status().isBadRequest());

        // Validate the CareActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamCareActivity() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        careActivity.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restCareActivityMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(careActivity)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the CareActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteCareActivity() throws Exception {
        // Initialize the database
        insertedCareActivity = careActivityRepository.save(careActivity);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the careActivity
        restCareActivityMockMvc
            .perform(delete(ENTITY_API_URL_ID, careActivity.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return careActivityRepository.count();
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

    protected CareActivity getPersistedCareActivity(CareActivity careActivity) {
        return careActivityRepository.findById(careActivity.getId()).orElseThrow();
    }

    protected void assertPersistedCareActivityToMatchAllProperties(CareActivity expectedCareActivity) {
        assertCareActivityAllPropertiesEquals(expectedCareActivity, getPersistedCareActivity(expectedCareActivity));
    }

    protected void assertPersistedCareActivityToMatchUpdatableProperties(CareActivity expectedCareActivity) {
        assertCareActivityAllUpdatablePropertiesEquals(expectedCareActivity, getPersistedCareActivity(expectedCareActivity));
    }
}
