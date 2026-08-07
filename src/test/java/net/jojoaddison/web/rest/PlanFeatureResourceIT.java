package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.PlanFeatureAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.PlanFeature;
import net.jojoaddison.domain.ServicePlan;
import net.jojoaddison.repository.PlanFeatureRepository;
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
 * Integration tests for the {@link PlanFeatureResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc
@WithMockUser
class PlanFeatureResourceIT {

    private static final String DEFAULT_LABEL = "AAAAAAAAAA";
    private static final String UPDATED_LABEL = "BBBBBBBBBB";

    private static final Integer DEFAULT_POSITION = 0;
    private static final Integer UPDATED_POSITION = 1;

    private static final String ENTITY_API_URL = "/api/plan-features";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private PlanFeatureRepository planFeatureRepository;

    @Mock
    private PlanFeatureRepository planFeatureRepositoryMock;

    @Autowired
    private MockMvc restPlanFeatureMockMvc;

    private PlanFeature planFeature;

    private PlanFeature insertedPlanFeature;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static PlanFeature createEntity() {
        PlanFeature planFeature = new PlanFeature().label(DEFAULT_LABEL).position(DEFAULT_POSITION);
        // Add required entity
        ServicePlan servicePlan;
        servicePlan = ServicePlanResourceIT.createEntity();
        servicePlan.setId("fixed-id-for-tests");
        planFeature.setPlan(servicePlan);
        return planFeature;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static PlanFeature createUpdatedEntity() {
        PlanFeature updatedPlanFeature = new PlanFeature().label(UPDATED_LABEL).position(UPDATED_POSITION);
        // Add required entity
        ServicePlan servicePlan;
        servicePlan = ServicePlanResourceIT.createUpdatedEntity();
        servicePlan.setId("fixed-id-for-tests");
        updatedPlanFeature.setPlan(servicePlan);
        return updatedPlanFeature;
    }

    @BeforeEach
    void initTest() {
        planFeature = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedPlanFeature != null) {
            planFeatureRepository.delete(insertedPlanFeature);
            insertedPlanFeature = null;
        }
    }

    @Test
    void createPlanFeature() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the PlanFeature
        var returnedPlanFeature = om.readValue(
            restPlanFeatureMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(planFeature)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            PlanFeature.class
        );

        // Validate the PlanFeature in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertPlanFeatureUpdatableFieldsEquals(returnedPlanFeature, getPersistedPlanFeature(returnedPlanFeature));

        insertedPlanFeature = returnedPlanFeature;
    }

    @Test
    void createPlanFeatureWithExistingId() throws Exception {
        // Create the PlanFeature with an existing ID
        planFeature.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restPlanFeatureMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(planFeature)))
            .andExpect(status().isBadRequest());

        // Validate the PlanFeature in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkLabelIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        planFeature.setLabel(null);

        // Create the PlanFeature, which fails.

        restPlanFeatureMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(planFeature)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkPositionIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        planFeature.setPosition(null);

        // Create the PlanFeature, which fails.

        restPlanFeatureMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(planFeature)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllPlanFeatures() throws Exception {
        // Initialize the database
        insertedPlanFeature = planFeatureRepository.save(planFeature);

        // Get all the planFeatureList
        restPlanFeatureMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(planFeature.getId())))
            .andExpect(jsonPath("$.[*].label").value(hasItem(DEFAULT_LABEL)))
            .andExpect(jsonPath("$.[*].position").value(hasItem(DEFAULT_POSITION)));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllPlanFeaturesWithEagerRelationshipsIsEnabled() throws Exception {
        when(planFeatureRepositoryMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restPlanFeatureMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(planFeatureRepositoryMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllPlanFeaturesWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(planFeatureRepositoryMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restPlanFeatureMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(planFeatureRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    void getPlanFeature() throws Exception {
        // Initialize the database
        insertedPlanFeature = planFeatureRepository.save(planFeature);

        // Get the planFeature
        restPlanFeatureMockMvc
            .perform(get(ENTITY_API_URL_ID, planFeature.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(planFeature.getId()))
            .andExpect(jsonPath("$.label").value(DEFAULT_LABEL))
            .andExpect(jsonPath("$.position").value(DEFAULT_POSITION));
    }

    @Test
    void getNonExistingPlanFeature() throws Exception {
        // Get the planFeature
        restPlanFeatureMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingPlanFeature() throws Exception {
        // Initialize the database
        insertedPlanFeature = planFeatureRepository.save(planFeature);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the planFeature
        PlanFeature updatedPlanFeature = planFeatureRepository.findById(planFeature.getId()).orElseThrow();
        updatedPlanFeature.label(UPDATED_LABEL).position(UPDATED_POSITION);

        restPlanFeatureMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedPlanFeature.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedPlanFeature))
            )
            .andExpect(status().isOk());

        // Validate the PlanFeature in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedPlanFeatureToMatchAllProperties(updatedPlanFeature);
    }

    @Test
    void putNonExistingPlanFeature() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        planFeature.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPlanFeatureMockMvc
            .perform(
                put(ENTITY_API_URL_ID, planFeature.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(planFeature))
            )
            .andExpect(status().isBadRequest());

        // Validate the PlanFeature in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchPlanFeature() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        planFeature.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPlanFeatureMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(planFeature))
            )
            .andExpect(status().isBadRequest());

        // Validate the PlanFeature in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamPlanFeature() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        planFeature.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPlanFeatureMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(planFeature)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the PlanFeature in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdatePlanFeatureWithPatch() throws Exception {
        // Initialize the database
        insertedPlanFeature = planFeatureRepository.save(planFeature);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the planFeature using partial update
        PlanFeature partialUpdatedPlanFeature = new PlanFeature();
        partialUpdatedPlanFeature.setId(planFeature.getId());

        partialUpdatedPlanFeature.label(UPDATED_LABEL).position(UPDATED_POSITION);

        restPlanFeatureMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPlanFeature.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPlanFeature))
            )
            .andExpect(status().isOk());

        // Validate the PlanFeature in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPlanFeatureUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedPlanFeature, planFeature),
            getPersistedPlanFeature(planFeature)
        );
    }

    @Test
    void fullUpdatePlanFeatureWithPatch() throws Exception {
        // Initialize the database
        insertedPlanFeature = planFeatureRepository.save(planFeature);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the planFeature using partial update
        PlanFeature partialUpdatedPlanFeature = new PlanFeature();
        partialUpdatedPlanFeature.setId(planFeature.getId());

        partialUpdatedPlanFeature.label(UPDATED_LABEL).position(UPDATED_POSITION);

        restPlanFeatureMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPlanFeature.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPlanFeature))
            )
            .andExpect(status().isOk());

        // Validate the PlanFeature in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPlanFeatureUpdatableFieldsEquals(partialUpdatedPlanFeature, getPersistedPlanFeature(partialUpdatedPlanFeature));
    }

    @Test
    void patchNonExistingPlanFeature() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        planFeature.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPlanFeatureMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, planFeature.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(planFeature))
            )
            .andExpect(status().isBadRequest());

        // Validate the PlanFeature in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchPlanFeature() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        planFeature.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPlanFeatureMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(planFeature))
            )
            .andExpect(status().isBadRequest());

        // Validate the PlanFeature in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamPlanFeature() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        planFeature.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPlanFeatureMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(planFeature)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the PlanFeature in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deletePlanFeature() throws Exception {
        // Initialize the database
        insertedPlanFeature = planFeatureRepository.save(planFeature);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the planFeature
        restPlanFeatureMockMvc
            .perform(delete(ENTITY_API_URL_ID, planFeature.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return planFeatureRepository.count();
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

    protected PlanFeature getPersistedPlanFeature(PlanFeature planFeature) {
        return planFeatureRepository.findById(planFeature.getId()).orElseThrow();
    }

    protected void assertPersistedPlanFeatureToMatchAllProperties(PlanFeature expectedPlanFeature) {
        assertPlanFeatureAllPropertiesEquals(expectedPlanFeature, getPersistedPlanFeature(expectedPlanFeature));
    }

    protected void assertPersistedPlanFeatureToMatchUpdatableProperties(PlanFeature expectedPlanFeature) {
        assertPlanFeatureAllUpdatablePropertiesEquals(expectedPlanFeature, getPersistedPlanFeature(expectedPlanFeature));
    }
}
