package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.ServicePlanAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static net.jojoaddison.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.ServicePlan;
import net.jojoaddison.domain.enumeration.PlanTier;
import net.jojoaddison.repository.ServicePlanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link ServicePlanResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class ServicePlanResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final PlanTier DEFAULT_TIER = PlanTier.ESSENTIAL;
    private static final PlanTier UPDATED_TIER = PlanTier.PLUS;

    private static final String DEFAULT_TIER_LABEL = "AAAAAAAAAA";
    private static final String UPDATED_TIER_LABEL = "BBBBBBBBBB";

    private static final BigDecimal DEFAULT_MONTHLY_PRICE = new BigDecimal(0);
    private static final BigDecimal UPDATED_MONTHLY_PRICE = new BigDecimal(1);

    private static final String DEFAULT_CURRENCY = "AAA";
    private static final String UPDATED_CURRENCY = "BBB";

    private static final String DEFAULT_SUMMARY = "AAAAAAAAAA";
    private static final String UPDATED_SUMMARY = "BBBBBBBBBB";

    private static final Boolean DEFAULT_FEATURED = false;
    private static final Boolean UPDATED_FEATURED = true;

    private static final Integer DEFAULT_SUBSCRIBER_COUNT = 0;
    private static final Integer UPDATED_SUBSCRIBER_COUNT = 1;

    private static final String ENTITY_API_URL = "/api/service-plans";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ServicePlanRepository servicePlanRepository;

    @Autowired
    private MockMvc restServicePlanMockMvc;

    private ServicePlan servicePlan;

    private ServicePlan insertedServicePlan;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ServicePlan createEntity() {
        return new ServicePlan()
            .name(DEFAULT_NAME)
            .tier(DEFAULT_TIER)
            .tierLabel(DEFAULT_TIER_LABEL)
            .monthlyPrice(DEFAULT_MONTHLY_PRICE)
            .currency(DEFAULT_CURRENCY)
            .summary(DEFAULT_SUMMARY)
            .featured(DEFAULT_FEATURED)
            .subscriberCount(DEFAULT_SUBSCRIBER_COUNT);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ServicePlan createUpdatedEntity() {
        return new ServicePlan()
            .name(UPDATED_NAME)
            .tier(UPDATED_TIER)
            .tierLabel(UPDATED_TIER_LABEL)
            .monthlyPrice(UPDATED_MONTHLY_PRICE)
            .currency(UPDATED_CURRENCY)
            .summary(UPDATED_SUMMARY)
            .featured(UPDATED_FEATURED)
            .subscriberCount(UPDATED_SUBSCRIBER_COUNT);
    }

    @BeforeEach
    void initTest() {
        servicePlan = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedServicePlan != null) {
            servicePlanRepository.delete(insertedServicePlan);
            insertedServicePlan = null;
        }
    }

    @Test
    void createServicePlan() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ServicePlan
        var returnedServicePlan = om.readValue(
            restServicePlanMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(servicePlan)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ServicePlan.class
        );

        // Validate the ServicePlan in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertServicePlanUpdatableFieldsEquals(returnedServicePlan, getPersistedServicePlan(returnedServicePlan));

        insertedServicePlan = returnedServicePlan;
    }

    @Test
    void createServicePlanWithExistingId() throws Exception {
        // Create the ServicePlan with an existing ID
        servicePlan.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restServicePlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(servicePlan)))
            .andExpect(status().isBadRequest());

        // Validate the ServicePlan in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        servicePlan.setName(null);

        // Create the ServicePlan, which fails.

        restServicePlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(servicePlan)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkTierIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        servicePlan.setTier(null);

        // Create the ServicePlan, which fails.

        restServicePlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(servicePlan)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkMonthlyPriceIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        servicePlan.setMonthlyPrice(null);

        // Create the ServicePlan, which fails.

        restServicePlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(servicePlan)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkCurrencyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        servicePlan.setCurrency(null);

        // Create the ServicePlan, which fails.

        restServicePlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(servicePlan)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkFeaturedIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        servicePlan.setFeatured(null);

        // Create the ServicePlan, which fails.

        restServicePlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(servicePlan)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllServicePlans() throws Exception {
        // Initialize the database
        insertedServicePlan = servicePlanRepository.save(servicePlan);

        // Get all the servicePlanList
        restServicePlanMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(servicePlan.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].tier").value(hasItem(DEFAULT_TIER.toString())))
            .andExpect(jsonPath("$.[*].tierLabel").value(hasItem(DEFAULT_TIER_LABEL)))
            .andExpect(jsonPath("$.[*].monthlyPrice").value(hasItem(sameNumber(DEFAULT_MONTHLY_PRICE))))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY)))
            .andExpect(jsonPath("$.[*].summary").value(hasItem(DEFAULT_SUMMARY)))
            .andExpect(jsonPath("$.[*].featured").value(hasItem(DEFAULT_FEATURED)))
            .andExpect(jsonPath("$.[*].subscriberCount").value(hasItem(DEFAULT_SUBSCRIBER_COUNT)));
    }

    @Test
    void getServicePlan() throws Exception {
        // Initialize the database
        insertedServicePlan = servicePlanRepository.save(servicePlan);

        // Get the servicePlan
        restServicePlanMockMvc
            .perform(get(ENTITY_API_URL_ID, servicePlan.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(servicePlan.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.tier").value(DEFAULT_TIER.toString()))
            .andExpect(jsonPath("$.tierLabel").value(DEFAULT_TIER_LABEL))
            .andExpect(jsonPath("$.monthlyPrice").value(sameNumber(DEFAULT_MONTHLY_PRICE)))
            .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY))
            .andExpect(jsonPath("$.summary").value(DEFAULT_SUMMARY))
            .andExpect(jsonPath("$.featured").value(DEFAULT_FEATURED))
            .andExpect(jsonPath("$.subscriberCount").value(DEFAULT_SUBSCRIBER_COUNT));
    }

    @Test
    void getNonExistingServicePlan() throws Exception {
        // Get the servicePlan
        restServicePlanMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingServicePlan() throws Exception {
        // Initialize the database
        insertedServicePlan = servicePlanRepository.save(servicePlan);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the servicePlan
        ServicePlan updatedServicePlan = servicePlanRepository.findById(servicePlan.getId()).orElseThrow();
        updatedServicePlan
            .name(UPDATED_NAME)
            .tier(UPDATED_TIER)
            .tierLabel(UPDATED_TIER_LABEL)
            .monthlyPrice(UPDATED_MONTHLY_PRICE)
            .currency(UPDATED_CURRENCY)
            .summary(UPDATED_SUMMARY)
            .featured(UPDATED_FEATURED)
            .subscriberCount(UPDATED_SUBSCRIBER_COUNT);

        restServicePlanMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedServicePlan.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedServicePlan))
            )
            .andExpect(status().isOk());

        // Validate the ServicePlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedServicePlanToMatchAllProperties(updatedServicePlan);
    }

    @Test
    void putNonExistingServicePlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        servicePlan.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restServicePlanMockMvc
            .perform(
                put(ENTITY_API_URL_ID, servicePlan.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(servicePlan))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServicePlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchServicePlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        servicePlan.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServicePlanMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(servicePlan))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServicePlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamServicePlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        servicePlan.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServicePlanMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(servicePlan)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ServicePlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateServicePlanWithPatch() throws Exception {
        // Initialize the database
        insertedServicePlan = servicePlanRepository.save(servicePlan);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the servicePlan using partial update
        ServicePlan partialUpdatedServicePlan = new ServicePlan();
        partialUpdatedServicePlan.setId(servicePlan.getId());

        partialUpdatedServicePlan.summary(UPDATED_SUMMARY).featured(UPDATED_FEATURED);

        restServicePlanMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedServicePlan.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedServicePlan))
            )
            .andExpect(status().isOk());

        // Validate the ServicePlan in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertServicePlanUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedServicePlan, servicePlan),
            getPersistedServicePlan(servicePlan)
        );
    }

    @Test
    void fullUpdateServicePlanWithPatch() throws Exception {
        // Initialize the database
        insertedServicePlan = servicePlanRepository.save(servicePlan);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the servicePlan using partial update
        ServicePlan partialUpdatedServicePlan = new ServicePlan();
        partialUpdatedServicePlan.setId(servicePlan.getId());

        partialUpdatedServicePlan
            .name(UPDATED_NAME)
            .tier(UPDATED_TIER)
            .tierLabel(UPDATED_TIER_LABEL)
            .monthlyPrice(UPDATED_MONTHLY_PRICE)
            .currency(UPDATED_CURRENCY)
            .summary(UPDATED_SUMMARY)
            .featured(UPDATED_FEATURED)
            .subscriberCount(UPDATED_SUBSCRIBER_COUNT);

        restServicePlanMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedServicePlan.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedServicePlan))
            )
            .andExpect(status().isOk());

        // Validate the ServicePlan in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertServicePlanUpdatableFieldsEquals(partialUpdatedServicePlan, getPersistedServicePlan(partialUpdatedServicePlan));
    }

    @Test
    void patchNonExistingServicePlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        servicePlan.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restServicePlanMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, servicePlan.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(servicePlan))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServicePlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchServicePlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        servicePlan.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServicePlanMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(servicePlan))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServicePlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamServicePlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        servicePlan.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServicePlanMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(servicePlan)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ServicePlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteServicePlan() throws Exception {
        // Initialize the database
        insertedServicePlan = servicePlanRepository.save(servicePlan);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the servicePlan
        restServicePlanMockMvc
            .perform(delete(ENTITY_API_URL_ID, servicePlan.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return servicePlanRepository.count();
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

    protected ServicePlan getPersistedServicePlan(ServicePlan servicePlan) {
        return servicePlanRepository.findById(servicePlan.getId()).orElseThrow();
    }

    protected void assertPersistedServicePlanToMatchAllProperties(ServicePlan expectedServicePlan) {
        assertServicePlanAllPropertiesEquals(expectedServicePlan, getPersistedServicePlan(expectedServicePlan));
    }

    protected void assertPersistedServicePlanToMatchUpdatableProperties(ServicePlan expectedServicePlan) {
        assertServicePlanAllUpdatablePropertiesEquals(expectedServicePlan, getPersistedServicePlan(expectedServicePlan));
    }
}
