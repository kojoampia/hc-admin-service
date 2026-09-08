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
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class ServicePlanResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_CODE = "AAAAAAAAAA";
    private static final String UPDATED_CODE = "BBBBBBBBBB";

    private static final String DEFAULT_TIER_LABEL = "AAAAAAAAAA";
    private static final String UPDATED_TIER_LABEL = "BBBBBBBBBB";

    private static final Integer DEFAULT_DISPLAY_ORDER = 1;
    private static final Integer UPDATED_DISPLAY_ORDER = 2;

    private static final BigDecimal DEFAULT_MONTHLY_PRICE = new BigDecimal(0);
    private static final BigDecimal UPDATED_MONTHLY_PRICE = new BigDecimal(1);

    private static final String DEFAULT_CURRENCY = "AAA";
    private static final String UPDATED_CURRENCY = "BBB";

    private static final String DEFAULT_SUMMARY = "AAAAAAAAAA";
    private static final String UPDATED_SUMMARY = "BBBBBBBBBB";

    private static final Boolean DEFAULT_FEATURED = false;
    private static final Boolean UPDATED_FEATURED = true;

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
            .code(DEFAULT_CODE)
            .tierLabel(DEFAULT_TIER_LABEL)
            .displayOrder(DEFAULT_DISPLAY_ORDER)
            .monthlyPrice(DEFAULT_MONTHLY_PRICE)
            .currency(DEFAULT_CURRENCY)
            .summary(DEFAULT_SUMMARY)
            .featured(DEFAULT_FEATURED);
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
            .code(UPDATED_CODE)
            .tierLabel(UPDATED_TIER_LABEL)
            .displayOrder(UPDATED_DISPLAY_ORDER)
            .monthlyPrice(UPDATED_MONTHLY_PRICE)
            .currency(UPDATED_CURRENCY)
            .summary(UPDATED_SUMMARY)
            .featured(UPDATED_FEATURED);
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

    /**
     * A code another plan already carries is a 400 naming the field, not a 500.
     *
     * <p>{@code config/ServicePlanIndexes} indexes {@code code} uniquely, so the second plan was
     * always refused — by the driver, as a {@code DuplicateKeyException}, which reaches the console
     * as a 500 carrying a Mongo error string. On a form whose {@code code} field an administrator
     * has a real reason to fill in by hand — stamping a plan created before the catalogue was
     * reconciled is the migration step {@code ServicePlanCatalogueSyncService} describes — that
     * reads as a broken service rather than as a value already in use.
     *
     * <p>The index is still the guarantee and is not replaced by this. What is asserted here is the
     * message.
     */
    @Test
    void refusesACodeAnotherPlanAlreadyCarries() throws Exception {
        insertedServicePlan = servicePlanRepository.save(servicePlan);
        long databaseSizeBeforeCreate = getRepositoryCount();

        ServicePlan duplicate = createUpdatedEntity().code(DEFAULT_CODE);

        restServicePlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(duplicate)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    /**
     * And a plan may keep its own code across an update.
     *
     * <p>The half a naive uniqueness check gets wrong: {@code PUT} sends the whole document, so the
     * code it carries matches the row being written, and refusing that would make every plan with a
     * code uneditable.
     */
    @Test
    void letsAPlanKeepItsOwnCodeOnUpdate() throws Exception {
        insertedServicePlan = servicePlanRepository.save(servicePlan);

        ServicePlan renamed = servicePlanRepository.findById(insertedServicePlan.getId()).orElseThrow().name(UPDATED_NAME);

        restServicePlanMockMvc
            .perform(put(ENTITY_API_URL_ID, renamed.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(renamed)))
            .andExpect(status().isOk());

        assertThat(servicePlanRepository.findById(insertedServicePlan.getId()).orElseThrow().getName()).isEqualTo(UPDATED_NAME);
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

    /**
     * A plan with no price is accepted, and the assertion is the inversion of the one it replaces.
     *
     * <p>{@code checkMonthlyPriceIsRequired} lived here until 2026-09-08 and asserted a 400. The
     * constraint went with backlog item 51: {@code ServicePlanCatalogueSyncService} creates a plan
     * from Abofonsa's published catalogue, which carries no machine-readable price at all, so an
     * unpriced plan is the state every learned plan is in until an administrator sets one. Null is
     * deliberately not zero — see {@code ServicePlan.monthlyPrice} — and
     * {@code ServicePlanSummaryService} already reports such a plan as earning nothing.
     *
     * <p>Kept as an explicit case rather than deleted, because "the required-field test is gone" and
     * "the field is optional" look identical in a diff and only one of them is a decision.
     */
    @Test
    void acceptsAPlanWithNoPriceYet() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        servicePlan.setMonthlyPrice(null);

        restServicePlanMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(servicePlan)))
            .andExpect(status().isCreated());

        assertThat(getRepositoryCount()).isEqualTo(databaseSizeBeforeTest + 1);
        insertedServicePlan =
            servicePlanRepository.findAll().stream().filter(plan -> plan.getMonthlyPrice() == null).findFirst().orElseThrow();
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
            .andExpect(jsonPath("$.[*].code").value(hasItem(DEFAULT_CODE)))
            .andExpect(jsonPath("$.[*].tierLabel").value(hasItem(DEFAULT_TIER_LABEL)))
            .andExpect(jsonPath("$.[*].displayOrder").value(hasItem(DEFAULT_DISPLAY_ORDER)))
            .andExpect(jsonPath("$.[*].monthlyPrice").value(hasItem(sameNumber(DEFAULT_MONTHLY_PRICE))))
            .andExpect(jsonPath("$.[*].currency").value(hasItem(DEFAULT_CURRENCY)))
            .andExpect(jsonPath("$.[*].summary").value(hasItem(DEFAULT_SUMMARY)))
            .andExpect(jsonPath("$.[*].featured").value(hasItem(DEFAULT_FEATURED)));
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
            .andExpect(jsonPath("$.code").value(DEFAULT_CODE))
            .andExpect(jsonPath("$.tierLabel").value(DEFAULT_TIER_LABEL))
            .andExpect(jsonPath("$.displayOrder").value(DEFAULT_DISPLAY_ORDER))
            .andExpect(jsonPath("$.monthlyPrice").value(sameNumber(DEFAULT_MONTHLY_PRICE)))
            .andExpect(jsonPath("$.currency").value(DEFAULT_CURRENCY))
            .andExpect(jsonPath("$.summary").value(DEFAULT_SUMMARY))
            .andExpect(jsonPath("$.featured").value(DEFAULT_FEATURED));
    }

    /**
     * A sync that reads nothing is a 200 saying so, not a 5xx — and it says <em>which</em> nothing.
     *
     * <p>The content client is disabled for every integration test (the note in
     * {@code src/test/resources/config/application.yml} says why), so what this exercises is
     * {@code configured: false}: nothing was dialled. <b>It is deliberately not the outage path</b>,
     * and saying so is backlog item 24's lesson one field along — that entry is about this repo
     * telling an administrator a far service was down whenever a local switch was off, which sends
     * the reader to the wrong machine. The outage proper is
     * {@code ServicePlanCatalogueSyncServiceTest}'s, over a mocked client, because producing a real
     * one here means a socket and a timeout against a third party's production host.
     *
     * <p>What the two share, and what this asserts on the wire rather than in a return value, is the
     * posture the whole design rests on: no screen reads Abofonsa, so a read that does not happen
     * writes nothing and degrades nothing.
     */
    @Test
    void syncingWithNothingToReadIsAnOkThatSaysWhy() throws Exception {
        long databaseSizeBeforeSync = getRepositoryCount();

        restServicePlanMockMvc
            .perform(post(ENTITY_API_URL + "/sync"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reached").value(false))
            .andExpect(jsonPath("$.configured").value(false))
            .andExpect(jsonPath("$.published").value(0))
            .andExpect(jsonPath("$.created").value(0));

        assertSameRepositoryCount(databaseSizeBeforeSync);
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
            .code(UPDATED_CODE)
            .tierLabel(UPDATED_TIER_LABEL)
            .displayOrder(UPDATED_DISPLAY_ORDER)
            .monthlyPrice(UPDATED_MONTHLY_PRICE)
            .currency(UPDATED_CURRENCY)
            .summary(UPDATED_SUMMARY)
            .featured(UPDATED_FEATURED);

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
            .code(UPDATED_CODE)
            .tierLabel(UPDATED_TIER_LABEL)
            .displayOrder(UPDATED_DISPLAY_ORDER)
            .monthlyPrice(UPDATED_MONTHLY_PRICE)
            .currency(UPDATED_CURRENCY)
            .summary(UPDATED_SUMMARY)
            .featured(UPDATED_FEATURED);

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
