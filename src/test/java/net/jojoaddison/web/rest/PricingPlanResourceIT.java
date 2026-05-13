package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.PricingPlanAsserts.*;
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
import net.jojoaddison.domain.PricingPlan;
import net.jojoaddison.domain.enumeration.BillingType;
import net.jojoaddison.repository.PricingPlanRepository;
import net.jojoaddison.service.dto.PricingPlanDTO;
import net.jojoaddison.service.mapper.PricingPlanMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link PricingPlanResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class PricingPlanResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final BigDecimal DEFAULT_PRICE = new BigDecimal(1);
    private static final BigDecimal UPDATED_PRICE = new BigDecimal(2);

    private static final String DEFAULT_FEATURES = "AAAAAAAAAA";
    private static final String UPDATED_FEATURES = "BBBBBBBBBB";

    private static final BillingType DEFAULT_BILLING_CYCLE = BillingType.DAILY;
    private static final BillingType UPDATED_BILLING_CYCLE = BillingType.WEEKLY;

    private static final Boolean DEFAULT_ACTIVE = false;
    private static final Boolean UPDATED_ACTIVE = true;

    private static final String ENTITY_API_URL = "/api/pricing-plans";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private final ObjectMapper om = TestUtil.createObjectMapper();

    @Autowired
    private PricingPlanRepository pricingPlanRepository;

    @Autowired
    private PricingPlanMapper pricingPlanMapper;

    @Autowired
    private MockMvc restPricingPlanMockMvc;

    private PricingPlan pricingPlan;

    private PricingPlan insertedPricingPlan;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static PricingPlan createEntity() {
        return new PricingPlan()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .price(DEFAULT_PRICE)
            .features(DEFAULT_FEATURES)
            .billingCycle(DEFAULT_BILLING_CYCLE)
            .active(DEFAULT_ACTIVE);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static PricingPlan createUpdatedEntity() {
        return new PricingPlan()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .price(UPDATED_PRICE)
            .features(UPDATED_FEATURES)
            .billingCycle(UPDATED_BILLING_CYCLE)
            .active(UPDATED_ACTIVE);
    }

    @BeforeEach
    void initTest() {
        pricingPlan = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedPricingPlan != null) {
            pricingPlanRepository.delete(insertedPricingPlan);
            insertedPricingPlan = null;
        }
    }

    @Test
    void createPricingPlan() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the PricingPlan
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);
        var returnedPricingPlanDTO = om.readValue(
            restPricingPlanMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(pricingPlanDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            PricingPlanDTO.class
        );

        // Validate the PricingPlan in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedPricingPlan = pricingPlanMapper.toEntity(returnedPricingPlanDTO);
        assertPricingPlanUpdatableFieldsEquals(returnedPricingPlan, getPersistedPricingPlan(returnedPricingPlan));

        insertedPricingPlan = returnedPricingPlan;
    }

    @Test
    void createPricingPlanWithExistingId() throws Exception {
        // Create the PricingPlan with an existing ID
        pricingPlan.setId("existing_id");
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restPricingPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(pricingPlanDTO)))
            .andExpect(status().isBadRequest());

        // Validate the PricingPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        pricingPlan.setName(null);

        // Create the PricingPlan, which fails.
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);

        restPricingPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(pricingPlanDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkDescriptionIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        pricingPlan.setDescription(null);

        // Create the PricingPlan, which fails.
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);

        restPricingPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(pricingPlanDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkPriceIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        pricingPlan.setPrice(null);

        // Create the PricingPlan, which fails.
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);

        restPricingPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(pricingPlanDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkFeaturesIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        pricingPlan.setFeatures(null);

        // Create the PricingPlan, which fails.
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);

        restPricingPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(pricingPlanDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkBillingCycleIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        pricingPlan.setBillingCycle(null);

        // Create the PricingPlan, which fails.
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);

        restPricingPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(pricingPlanDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkActiveIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        pricingPlan.setActive(null);

        // Create the PricingPlan, which fails.
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);

        restPricingPlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(pricingPlanDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllPricingPlans() throws Exception {
        // Initialize the database
        insertedPricingPlan = pricingPlanRepository.save(pricingPlan);

        // Get all the pricingPlanList
        restPricingPlanMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(pricingPlan.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].price").value(hasItem(sameNumber(DEFAULT_PRICE))))
            .andExpect(jsonPath("$.[*].features").value(hasItem(DEFAULT_FEATURES)))
            .andExpect(jsonPath("$.[*].billingCycle").value(hasItem(DEFAULT_BILLING_CYCLE.toString())))
            .andExpect(jsonPath("$.[*].active").value(hasItem(DEFAULT_ACTIVE)));
    }

    @Test
    void getPricingPlan() throws Exception {
        // Initialize the database
        insertedPricingPlan = pricingPlanRepository.save(pricingPlan);

        // Get the pricingPlan
        restPricingPlanMockMvc
            .perform(get(ENTITY_API_URL_ID, pricingPlan.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(pricingPlan.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.price").value(sameNumber(DEFAULT_PRICE)))
            .andExpect(jsonPath("$.features").value(DEFAULT_FEATURES))
            .andExpect(jsonPath("$.billingCycle").value(DEFAULT_BILLING_CYCLE.toString()))
            .andExpect(jsonPath("$.active").value(DEFAULT_ACTIVE));
    }

    @Test
    void getNonExistingPricingPlan() throws Exception {
        // Get the pricingPlan
        restPricingPlanMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingPricingPlan() throws Exception {
        // Initialize the database
        insertedPricingPlan = pricingPlanRepository.save(pricingPlan);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the pricingPlan
        PricingPlan updatedPricingPlan = pricingPlanRepository.findById(pricingPlan.getId()).orElseThrow();
        updatedPricingPlan
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .price(UPDATED_PRICE)
            .features(UPDATED_FEATURES)
            .billingCycle(UPDATED_BILLING_CYCLE)
            .active(UPDATED_ACTIVE);
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(updatedPricingPlan);

        restPricingPlanMockMvc
            .perform(
                put(ENTITY_API_URL_ID, pricingPlanDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(pricingPlanDTO))
            )
            .andExpect(status().isOk());

        // Validate the PricingPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedPricingPlanToMatchAllProperties(updatedPricingPlan);
    }

    @Test
    void putNonExistingPricingPlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        pricingPlan.setId(UUID.randomUUID().toString());

        // Create the PricingPlan
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPricingPlanMockMvc
            .perform(
                put(ENTITY_API_URL_ID, pricingPlanDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(pricingPlanDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the PricingPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchPricingPlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        pricingPlan.setId(UUID.randomUUID().toString());

        // Create the PricingPlan
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPricingPlanMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(pricingPlanDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the PricingPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamPricingPlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        pricingPlan.setId(UUID.randomUUID().toString());

        // Create the PricingPlan
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPricingPlanMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(pricingPlanDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the PricingPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdatePricingPlanWithPatch() throws Exception {
        // Initialize the database
        insertedPricingPlan = pricingPlanRepository.save(pricingPlan);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the pricingPlan using partial update
        PricingPlan partialUpdatedPricingPlan = new PricingPlan();
        partialUpdatedPricingPlan.setId(pricingPlan.getId());

        partialUpdatedPricingPlan.name(UPDATED_NAME).features(UPDATED_FEATURES).billingCycle(UPDATED_BILLING_CYCLE).active(UPDATED_ACTIVE);

        restPricingPlanMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPricingPlan.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPricingPlan))
            )
            .andExpect(status().isOk());

        // Validate the PricingPlan in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPricingPlanUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedPricingPlan, pricingPlan),
            getPersistedPricingPlan(pricingPlan)
        );
    }

    @Test
    void fullUpdatePricingPlanWithPatch() throws Exception {
        // Initialize the database
        insertedPricingPlan = pricingPlanRepository.save(pricingPlan);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the pricingPlan using partial update
        PricingPlan partialUpdatedPricingPlan = new PricingPlan();
        partialUpdatedPricingPlan.setId(pricingPlan.getId());

        partialUpdatedPricingPlan
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .price(UPDATED_PRICE)
            .features(UPDATED_FEATURES)
            .billingCycle(UPDATED_BILLING_CYCLE)
            .active(UPDATED_ACTIVE);

        restPricingPlanMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedPricingPlan.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedPricingPlan))
            )
            .andExpect(status().isOk());

        // Validate the PricingPlan in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPricingPlanUpdatableFieldsEquals(partialUpdatedPricingPlan, getPersistedPricingPlan(partialUpdatedPricingPlan));
    }

    @Test
    void patchNonExistingPricingPlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        pricingPlan.setId(UUID.randomUUID().toString());

        // Create the PricingPlan
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restPricingPlanMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, pricingPlanDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(pricingPlanDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the PricingPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchPricingPlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        pricingPlan.setId(UUID.randomUUID().toString());

        // Create the PricingPlan
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPricingPlanMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(pricingPlanDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the PricingPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamPricingPlan() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        pricingPlan.setId(UUID.randomUUID().toString());

        // Create the PricingPlan
        PricingPlanDTO pricingPlanDTO = pricingPlanMapper.toDto(pricingPlan);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restPricingPlanMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(pricingPlanDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the PricingPlan in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deletePricingPlan() throws Exception {
        // Initialize the database
        insertedPricingPlan = pricingPlanRepository.save(pricingPlan);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the pricingPlan
        restPricingPlanMockMvc
            .perform(delete(ENTITY_API_URL_ID, pricingPlan.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return pricingPlanRepository.count();
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

    protected PricingPlan getPersistedPricingPlan(PricingPlan pricingPlan) {
        return pricingPlanRepository.findById(pricingPlan.getId()).orElseThrow();
    }

    protected void assertPersistedPricingPlanToMatchAllProperties(PricingPlan expectedPricingPlan) {
        assertPricingPlanAllPropertiesEquals(expectedPricingPlan, getPersistedPricingPlan(expectedPricingPlan));
    }

    protected void assertPersistedPricingPlanToMatchUpdatableProperties(PricingPlan expectedPricingPlan) {
        assertPricingPlanAllUpdatablePropertiesEquals(expectedPricingPlan, getPersistedPricingPlan(expectedPricingPlan));
    }
}
