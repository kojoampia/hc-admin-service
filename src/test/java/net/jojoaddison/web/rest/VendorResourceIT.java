package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.VendorAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static net.jojoaddison.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Vendor;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.repository.VendorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link VendorResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class VendorResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_CATEGORY = "AAAAAAAAAA";
    private static final String UPDATED_CATEGORY = "BBBBBBBBBB";

    private static final String DEFAULT_SERVICE_SUMMARY = "AAAAAAAAAA";
    private static final String UPDATED_SERVICE_SUMMARY = "BBBBBBBBBB";

    private static final String DEFAULT_CONTACT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_CONTACT_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_PHONE = "AAAAAAAAAA";
    private static final String UPDATED_PHONE = "BBBBBBBBBB";

    private static final String DEFAULT_EMAIL = "AAAAAAAAAA";
    private static final String UPDATED_EMAIL = "BBBBBBBBBB";

    private static final String DEFAULT_CITY = "AAAAAAAAAA";
    private static final String UPDATED_CITY = "BBBBBBBBBB";

    private static final AccountStatus DEFAULT_STATUS = AccountStatus.ACTIVE;
    private static final AccountStatus UPDATED_STATUS = AccountStatus.PENDING;

    private static final String DEFAULT_CONTRACT_NOTE = "AAAAAAAAAA";
    private static final String UPDATED_CONTRACT_NOTE = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_CONTRACT_RENEWS_ON = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CONTRACT_RENEWS_ON = LocalDate.parse("2024-03-26");

    private static final Integer DEFAULT_ORDER_COUNT = 0;
    private static final Integer UPDATED_ORDER_COUNT = 1;

    private static final BigDecimal DEFAULT_SPEND_TO_DATE = new BigDecimal(0);
    private static final BigDecimal UPDATED_SPEND_TO_DATE = new BigDecimal(1);

    private static final BigDecimal DEFAULT_RATING = new BigDecimal(0);
    private static final BigDecimal UPDATED_RATING = new BigDecimal(1);

    private static final String ENTITY_API_URL = "/api/vendors";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private MockMvc restVendorMockMvc;

    private Vendor vendor;

    private Vendor insertedVendor;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Vendor createEntity() {
        return new Vendor()
            .name(DEFAULT_NAME)
            .category(DEFAULT_CATEGORY)
            .serviceSummary(DEFAULT_SERVICE_SUMMARY)
            .contactName(DEFAULT_CONTACT_NAME)
            .phone(DEFAULT_PHONE)
            .email(DEFAULT_EMAIL)
            .city(DEFAULT_CITY)
            .status(DEFAULT_STATUS)
            .contractNote(DEFAULT_CONTRACT_NOTE)
            .contractRenewsOn(DEFAULT_CONTRACT_RENEWS_ON)
            .orderCount(DEFAULT_ORDER_COUNT)
            .spendToDate(DEFAULT_SPEND_TO_DATE)
            .rating(DEFAULT_RATING);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Vendor createUpdatedEntity() {
        return new Vendor()
            .name(UPDATED_NAME)
            .category(UPDATED_CATEGORY)
            .serviceSummary(UPDATED_SERVICE_SUMMARY)
            .contactName(UPDATED_CONTACT_NAME)
            .phone(UPDATED_PHONE)
            .email(UPDATED_EMAIL)
            .city(UPDATED_CITY)
            .status(UPDATED_STATUS)
            .contractNote(UPDATED_CONTRACT_NOTE)
            .contractRenewsOn(UPDATED_CONTRACT_RENEWS_ON)
            .orderCount(UPDATED_ORDER_COUNT)
            .spendToDate(UPDATED_SPEND_TO_DATE)
            .rating(UPDATED_RATING);
    }

    @BeforeEach
    void initTest() {
        vendor = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedVendor != null) {
            vendorRepository.delete(insertedVendor);
            insertedVendor = null;
        }
    }

    @Test
    void createVendor() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Vendor
        var returnedVendor = om.readValue(
            restVendorMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(vendor)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Vendor.class
        );

        // Validate the Vendor in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertVendorUpdatableFieldsEquals(returnedVendor, getPersistedVendor(returnedVendor));

        insertedVendor = returnedVendor;
    }

    @Test
    void createVendorWithExistingId() throws Exception {
        // Create the Vendor with an existing ID
        vendor.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restVendorMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(vendor)))
            .andExpect(status().isBadRequest());

        // Validate the Vendor in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        vendor.setName(null);

        // Create the Vendor, which fails.

        restVendorMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(vendor)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkCategoryIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        vendor.setCategory(null);

        // Create the Vendor, which fails.

        restVendorMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(vendor)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkStatusIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        vendor.setStatus(null);

        // Create the Vendor, which fails.

        restVendorMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(vendor)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllVendors() throws Exception {
        // Initialize the database
        insertedVendor = vendorRepository.save(vendor);

        // Get all the vendorList
        restVendorMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(vendor.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].category").value(hasItem(DEFAULT_CATEGORY)))
            .andExpect(jsonPath("$.[*].serviceSummary").value(hasItem(DEFAULT_SERVICE_SUMMARY)))
            .andExpect(jsonPath("$.[*].contactName").value(hasItem(DEFAULT_CONTACT_NAME)))
            .andExpect(jsonPath("$.[*].phone").value(hasItem(DEFAULT_PHONE)))
            .andExpect(jsonPath("$.[*].email").value(hasItem(DEFAULT_EMAIL)))
            .andExpect(jsonPath("$.[*].city").value(hasItem(DEFAULT_CITY)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS.toString())))
            .andExpect(jsonPath("$.[*].contractNote").value(hasItem(DEFAULT_CONTRACT_NOTE)))
            .andExpect(jsonPath("$.[*].contractRenewsOn").value(hasItem(DEFAULT_CONTRACT_RENEWS_ON.toString())))
            .andExpect(jsonPath("$.[*].orderCount").value(hasItem(DEFAULT_ORDER_COUNT)))
            .andExpect(jsonPath("$.[*].spendToDate").value(hasItem(sameNumber(DEFAULT_SPEND_TO_DATE))))
            .andExpect(jsonPath("$.[*].rating").value(hasItem(sameNumber(DEFAULT_RATING))));
    }

    @Test
    void getVendor() throws Exception {
        // Initialize the database
        insertedVendor = vendorRepository.save(vendor);

        // Get the vendor
        restVendorMockMvc
            .perform(get(ENTITY_API_URL_ID, vendor.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(vendor.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.category").value(DEFAULT_CATEGORY))
            .andExpect(jsonPath("$.serviceSummary").value(DEFAULT_SERVICE_SUMMARY))
            .andExpect(jsonPath("$.contactName").value(DEFAULT_CONTACT_NAME))
            .andExpect(jsonPath("$.phone").value(DEFAULT_PHONE))
            .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
            .andExpect(jsonPath("$.city").value(DEFAULT_CITY))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS.toString()))
            .andExpect(jsonPath("$.contractNote").value(DEFAULT_CONTRACT_NOTE))
            .andExpect(jsonPath("$.contractRenewsOn").value(DEFAULT_CONTRACT_RENEWS_ON.toString()))
            .andExpect(jsonPath("$.orderCount").value(DEFAULT_ORDER_COUNT))
            .andExpect(jsonPath("$.spendToDate").value(sameNumber(DEFAULT_SPEND_TO_DATE)))
            .andExpect(jsonPath("$.rating").value(sameNumber(DEFAULT_RATING)));
    }

    @Test
    void getNonExistingVendor() throws Exception {
        // Get the vendor
        restVendorMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingVendor() throws Exception {
        // Initialize the database
        insertedVendor = vendorRepository.save(vendor);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the vendor
        Vendor updatedVendor = vendorRepository.findById(vendor.getId()).orElseThrow();
        updatedVendor
            .name(UPDATED_NAME)
            .category(UPDATED_CATEGORY)
            .serviceSummary(UPDATED_SERVICE_SUMMARY)
            .contactName(UPDATED_CONTACT_NAME)
            .phone(UPDATED_PHONE)
            .email(UPDATED_EMAIL)
            .city(UPDATED_CITY)
            .status(UPDATED_STATUS)
            .contractNote(UPDATED_CONTRACT_NOTE)
            .contractRenewsOn(UPDATED_CONTRACT_RENEWS_ON)
            .orderCount(UPDATED_ORDER_COUNT)
            .spendToDate(UPDATED_SPEND_TO_DATE)
            .rating(UPDATED_RATING);

        restVendorMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedVendor.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedVendor))
            )
            .andExpect(status().isOk());

        // Validate the Vendor in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedVendorToMatchAllProperties(updatedVendor);
    }

    @Test
    void putNonExistingVendor() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        vendor.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restVendorMockMvc
            .perform(put(ENTITY_API_URL_ID, vendor.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(vendor)))
            .andExpect(status().isBadRequest());

        // Validate the Vendor in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchVendor() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        vendor.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restVendorMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(vendor))
            )
            .andExpect(status().isBadRequest());

        // Validate the Vendor in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamVendor() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        vendor.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restVendorMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(vendor)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Vendor in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateVendorWithPatch() throws Exception {
        // Initialize the database
        insertedVendor = vendorRepository.save(vendor);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the vendor using partial update
        Vendor partialUpdatedVendor = new Vendor();
        partialUpdatedVendor.setId(vendor.getId());

        partialUpdatedVendor
            .category(UPDATED_CATEGORY)
            .serviceSummary(UPDATED_SERVICE_SUMMARY)
            .contactName(UPDATED_CONTACT_NAME)
            .status(UPDATED_STATUS)
            .contractNote(UPDATED_CONTRACT_NOTE)
            .contractRenewsOn(UPDATED_CONTRACT_RENEWS_ON)
            .orderCount(UPDATED_ORDER_COUNT);

        restVendorMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedVendor.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedVendor))
            )
            .andExpect(status().isOk());

        // Validate the Vendor in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertVendorUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedVendor, vendor), getPersistedVendor(vendor));
    }

    @Test
    void fullUpdateVendorWithPatch() throws Exception {
        // Initialize the database
        insertedVendor = vendorRepository.save(vendor);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the vendor using partial update
        Vendor partialUpdatedVendor = new Vendor();
        partialUpdatedVendor.setId(vendor.getId());

        partialUpdatedVendor
            .name(UPDATED_NAME)
            .category(UPDATED_CATEGORY)
            .serviceSummary(UPDATED_SERVICE_SUMMARY)
            .contactName(UPDATED_CONTACT_NAME)
            .phone(UPDATED_PHONE)
            .email(UPDATED_EMAIL)
            .city(UPDATED_CITY)
            .status(UPDATED_STATUS)
            .contractNote(UPDATED_CONTRACT_NOTE)
            .contractRenewsOn(UPDATED_CONTRACT_RENEWS_ON)
            .orderCount(UPDATED_ORDER_COUNT)
            .spendToDate(UPDATED_SPEND_TO_DATE)
            .rating(UPDATED_RATING);

        restVendorMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedVendor.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedVendor))
            )
            .andExpect(status().isOk());

        // Validate the Vendor in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertVendorUpdatableFieldsEquals(partialUpdatedVendor, getPersistedVendor(partialUpdatedVendor));
    }

    @Test
    void patchNonExistingVendor() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        vendor.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restVendorMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, vendor.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(vendor))
            )
            .andExpect(status().isBadRequest());

        // Validate the Vendor in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchVendor() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        vendor.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restVendorMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(vendor))
            )
            .andExpect(status().isBadRequest());

        // Validate the Vendor in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamVendor() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        vendor.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restVendorMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(vendor)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Vendor in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteVendor() throws Exception {
        // Initialize the database
        insertedVendor = vendorRepository.save(vendor);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the vendor
        restVendorMockMvc
            .perform(delete(ENTITY_API_URL_ID, vendor.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return vendorRepository.count();
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

    protected Vendor getPersistedVendor(Vendor vendor) {
        return vendorRepository.findById(vendor.getId()).orElseThrow();
    }

    protected void assertPersistedVendorToMatchAllProperties(Vendor expectedVendor) {
        assertVendorAllPropertiesEquals(expectedVendor, getPersistedVendor(expectedVendor));
    }

    protected void assertPersistedVendorToMatchUpdatableProperties(Vendor expectedVendor) {
        assertVendorAllUpdatablePropertiesEquals(expectedVendor, getPersistedVendor(expectedVendor));
    }
}
