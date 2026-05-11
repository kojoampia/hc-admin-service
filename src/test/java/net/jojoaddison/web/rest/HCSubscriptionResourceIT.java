package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.HCSubscription;
import net.jojoaddison.repository.HCSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link HCSubscriptionResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class HCSubscriptionResourceIT {

    private static final String DEFAULT_SERVICE_ID = "AAAAAAAAAA";
    private static final String UPDATED_SERVICE_ID = "BBBBBBBBBB";

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final Boolean DEFAULT_IS_ACTIVE = false;
    private static final Boolean UPDATED_IS_ACTIVE = true;

    private static final Instant DEFAULT_CREATED_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_MODIFIED_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_MODIFIED_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/hc-subscriptions";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private HCSubscriptionRepository hCSubscriptionRepository;

    @Autowired
    private MockMvc restHCSubscriptionMockMvc;

    private HCSubscription hCSubscription;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static HCSubscription createEntity() {
        HCSubscription hCSubscription = new HCSubscription()
            .serviceId(DEFAULT_SERVICE_ID)
            .patientId(DEFAULT_PATIENT_ID)
            .isActive(DEFAULT_IS_ACTIVE)
            .createdDate(DEFAULT_CREATED_DATE)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedBy(DEFAULT_MODIFIED_BY);
        return hCSubscription;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static HCSubscription createUpdatedEntity() {
        HCSubscription hCSubscription = new HCSubscription()
            .serviceId(UPDATED_SERVICE_ID)
            .patientId(UPDATED_PATIENT_ID)
            .isActive(UPDATED_IS_ACTIVE)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
        return hCSubscription;
    }

    @BeforeEach
    public void initTest() {
        hCSubscriptionRepository.deleteAll();
        hCSubscription = createEntity();
    }

    @Test
    void createHCSubscription() throws Exception {
        int databaseSizeBeforeCreate = hCSubscriptionRepository.findAll().size();
        // Create the HCSubscription
        restHCSubscriptionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(hCSubscription))
            )
            .andExpect(status().isCreated());

        // Validate the HCSubscription in the database
        List<HCSubscription> hCSubscriptionList = hCSubscriptionRepository.findAll();
        assertThat(hCSubscriptionList).hasSize(databaseSizeBeforeCreate + 1);
        HCSubscription testHCSubscription = hCSubscriptionList.get(hCSubscriptionList.size() - 1);
        assertThat(testHCSubscription.getServiceId()).isEqualTo(DEFAULT_SERVICE_ID);
        assertThat(testHCSubscription.getPatientId()).isEqualTo(DEFAULT_PATIENT_ID);
        assertThat(testHCSubscription.getIsActive()).isEqualTo(DEFAULT_IS_ACTIVE);
        assertThat(testHCSubscription.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testHCSubscription.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testHCSubscription.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testHCSubscription.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void createHCSubscriptionWithExistingId() throws Exception {
        // Create the HCSubscription with an existing ID
        hCSubscription.setId("existing_id");

        int databaseSizeBeforeCreate = hCSubscriptionRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restHCSubscriptionMockMvc
            .perform(
                post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(hCSubscription))
            )
            .andExpect(status().isBadRequest());

        // Validate the HCSubscription in the database
        List<HCSubscription> hCSubscriptionList = hCSubscriptionRepository.findAll();
        assertThat(hCSubscriptionList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllHCSubscriptions() throws Exception {
        // Initialize the database
        hCSubscriptionRepository.save(hCSubscription);

        // Get all the hCSubscriptionList
        restHCSubscriptionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(hCSubscription.getId())))
            .andExpect(jsonPath("$.[*].serviceId").value(hasItem(DEFAULT_SERVICE_ID)))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)))
            .andExpect(jsonPath("$.[*].isActive").value(hasItem(DEFAULT_IS_ACTIVE.booleanValue())))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getHCSubscription() throws Exception {
        // Initialize the database
        hCSubscriptionRepository.save(hCSubscription);

        // Get the hCSubscription
        restHCSubscriptionMockMvc
            .perform(get(ENTITY_API_URL_ID, hCSubscription.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(hCSubscription.getId()))
            .andExpect(jsonPath("$.serviceId").value(DEFAULT_SERVICE_ID))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID))
            .andExpect(jsonPath("$.isActive").value(DEFAULT_IS_ACTIVE.booleanValue()))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY));
    }

    @Test
    void getNonExistingHCSubscription() throws Exception {
        // Get the hCSubscription
        restHCSubscriptionMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingHCSubscription() throws Exception {
        // Initialize the database
        hCSubscriptionRepository.save(hCSubscription);

        int databaseSizeBeforeUpdate = hCSubscriptionRepository.findAll().size();

        // Update the hCSubscription
        HCSubscription updatedHCSubscription = hCSubscriptionRepository.findById(hCSubscription.getId()).orElseThrow();
        updatedHCSubscription
            .serviceId(UPDATED_SERVICE_ID)
            .patientId(UPDATED_PATIENT_ID)
            .isActive(UPDATED_IS_ACTIVE)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restHCSubscriptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedHCSubscription.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedHCSubscription))
            )
            .andExpect(status().isOk());

        // Validate the HCSubscription in the database
        List<HCSubscription> hCSubscriptionList = hCSubscriptionRepository.findAll();
        assertThat(hCSubscriptionList).hasSize(databaseSizeBeforeUpdate);
        HCSubscription testHCSubscription = hCSubscriptionList.get(hCSubscriptionList.size() - 1);
        assertThat(testHCSubscription.getServiceId()).isEqualTo(UPDATED_SERVICE_ID);
        assertThat(testHCSubscription.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testHCSubscription.getIsActive()).isEqualTo(UPDATED_IS_ACTIVE);
        assertThat(testHCSubscription.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testHCSubscription.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testHCSubscription.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testHCSubscription.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void putNonExistingHCSubscription() throws Exception {
        int databaseSizeBeforeUpdate = hCSubscriptionRepository.findAll().size();
        hCSubscription.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restHCSubscriptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, hCSubscription.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(hCSubscription))
            )
            .andExpect(status().isBadRequest());

        // Validate the HCSubscription in the database
        List<HCSubscription> hCSubscriptionList = hCSubscriptionRepository.findAll();
        assertThat(hCSubscriptionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchHCSubscription() throws Exception {
        int databaseSizeBeforeUpdate = hCSubscriptionRepository.findAll().size();
        hCSubscription.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHCSubscriptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(hCSubscription))
            )
            .andExpect(status().isBadRequest());

        // Validate the HCSubscription in the database
        List<HCSubscription> hCSubscriptionList = hCSubscriptionRepository.findAll();
        assertThat(hCSubscriptionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamHCSubscription() throws Exception {
        int databaseSizeBeforeUpdate = hCSubscriptionRepository.findAll().size();
        hCSubscription.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHCSubscriptionMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(hCSubscription)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the HCSubscription in the database
        List<HCSubscription> hCSubscriptionList = hCSubscriptionRepository.findAll();
        assertThat(hCSubscriptionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateHCSubscriptionWithPatch() throws Exception {
        // Initialize the database
        hCSubscriptionRepository.save(hCSubscription);

        int databaseSizeBeforeUpdate = hCSubscriptionRepository.findAll().size();

        // Update the hCSubscription using partial update
        HCSubscription partialUpdatedHCSubscription = new HCSubscription();
        partialUpdatedHCSubscription.setId(hCSubscription.getId());

        partialUpdatedHCSubscription
            .serviceId(UPDATED_SERVICE_ID)
            .patientId(UPDATED_PATIENT_ID)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY);

        restHCSubscriptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedHCSubscription.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedHCSubscription))
            )
            .andExpect(status().isOk());

        // Validate the HCSubscription in the database
        List<HCSubscription> hCSubscriptionList = hCSubscriptionRepository.findAll();
        assertThat(hCSubscriptionList).hasSize(databaseSizeBeforeUpdate);
        HCSubscription testHCSubscription = hCSubscriptionList.get(hCSubscriptionList.size() - 1);
        assertThat(testHCSubscription.getServiceId()).isEqualTo(UPDATED_SERVICE_ID);
        assertThat(testHCSubscription.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testHCSubscription.getIsActive()).isEqualTo(DEFAULT_IS_ACTIVE);
        assertThat(testHCSubscription.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testHCSubscription.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testHCSubscription.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testHCSubscription.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void fullUpdateHCSubscriptionWithPatch() throws Exception {
        // Initialize the database
        hCSubscriptionRepository.save(hCSubscription);

        int databaseSizeBeforeUpdate = hCSubscriptionRepository.findAll().size();

        // Update the hCSubscription using partial update
        HCSubscription partialUpdatedHCSubscription = new HCSubscription();
        partialUpdatedHCSubscription.setId(hCSubscription.getId());

        partialUpdatedHCSubscription
            .serviceId(UPDATED_SERVICE_ID)
            .patientId(UPDATED_PATIENT_ID)
            .isActive(UPDATED_IS_ACTIVE)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restHCSubscriptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedHCSubscription.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedHCSubscription))
            )
            .andExpect(status().isOk());

        // Validate the HCSubscription in the database
        List<HCSubscription> hCSubscriptionList = hCSubscriptionRepository.findAll();
        assertThat(hCSubscriptionList).hasSize(databaseSizeBeforeUpdate);
        HCSubscription testHCSubscription = hCSubscriptionList.get(hCSubscriptionList.size() - 1);
        assertThat(testHCSubscription.getServiceId()).isEqualTo(UPDATED_SERVICE_ID);
        assertThat(testHCSubscription.getPatientId()).isEqualTo(UPDATED_PATIENT_ID);
        assertThat(testHCSubscription.getIsActive()).isEqualTo(UPDATED_IS_ACTIVE);
        assertThat(testHCSubscription.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testHCSubscription.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testHCSubscription.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testHCSubscription.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void patchNonExistingHCSubscription() throws Exception {
        int databaseSizeBeforeUpdate = hCSubscriptionRepository.findAll().size();
        hCSubscription.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restHCSubscriptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, hCSubscription.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(hCSubscription))
            )
            .andExpect(status().isBadRequest());

        // Validate the HCSubscription in the database
        List<HCSubscription> hCSubscriptionList = hCSubscriptionRepository.findAll();
        assertThat(hCSubscriptionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchHCSubscription() throws Exception {
        int databaseSizeBeforeUpdate = hCSubscriptionRepository.findAll().size();
        hCSubscription.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHCSubscriptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(hCSubscription))
            )
            .andExpect(status().isBadRequest());

        // Validate the HCSubscription in the database
        List<HCSubscription> hCSubscriptionList = hCSubscriptionRepository.findAll();
        assertThat(hCSubscriptionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamHCSubscription() throws Exception {
        int databaseSizeBeforeUpdate = hCSubscriptionRepository.findAll().size();
        hCSubscription.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHCSubscriptionMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(hCSubscription))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the HCSubscription in the database
        List<HCSubscription> hCSubscriptionList = hCSubscriptionRepository.findAll();
        assertThat(hCSubscriptionList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteHCSubscription() throws Exception {
        // Initialize the database
        hCSubscriptionRepository.save(hCSubscription);

        int databaseSizeBeforeDelete = hCSubscriptionRepository.findAll().size();

        // Delete the hCSubscription
        restHCSubscriptionMockMvc
            .perform(delete(ENTITY_API_URL_ID, hCSubscription.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<HCSubscription> hCSubscriptionList = hCSubscriptionRepository.findAll();
        assertThat(hCSubscriptionList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
