package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.ServiceActivityAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static net.jojoaddison.web.rest.TestUtil.sameNumber;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Category;
import net.jojoaddison.domain.ServiceActivity;
import net.jojoaddison.repository.ServiceActivityRepository;
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
 * Integration tests for the {@link ServiceActivityResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class ServiceActivityResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_UNIT = "AAAAAAAAAA";
    private static final String UPDATED_UNIT = "BBBBBBBBBB";

    private static final BigDecimal DEFAULT_UNIT_PRICE = new BigDecimal(0);
    private static final BigDecimal UPDATED_UNIT_PRICE = new BigDecimal(1);

    private static final String DEFAULT_DURATION = "AAAAAAAAAA";
    private static final String UPDATED_DURATION = "BBBBBBBBBB";

    private static final Boolean DEFAULT_PUBLISHED = false;
    private static final Boolean UPDATED_PUBLISHED = true;

    private static final String ENTITY_API_URL = "/api/service-activities";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ServiceActivityRepository serviceActivityRepository;

    @Mock
    private ServiceActivityRepository serviceActivityRepositoryMock;

    @Autowired
    private MockMvc restServiceActivityMockMvc;

    private ServiceActivity serviceActivity;

    private ServiceActivity insertedServiceActivity;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ServiceActivity createEntity() {
        ServiceActivity serviceActivity = new ServiceActivity()
            .name(DEFAULT_NAME)
            .unit(DEFAULT_UNIT)
            .unitPrice(DEFAULT_UNIT_PRICE)
            .duration(DEFAULT_DURATION)
            .published(DEFAULT_PUBLISHED);
        // Add required entity
        Category category;
        category = CategoryResourceIT.createEntity();
        category.setId("fixed-id-for-tests");
        serviceActivity.setCategory(category);
        return serviceActivity;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ServiceActivity createUpdatedEntity() {
        ServiceActivity updatedServiceActivity = new ServiceActivity()
            .name(UPDATED_NAME)
            .unit(UPDATED_UNIT)
            .unitPrice(UPDATED_UNIT_PRICE)
            .duration(UPDATED_DURATION)
            .published(UPDATED_PUBLISHED);
        // Add required entity
        Category category;
        category = CategoryResourceIT.createUpdatedEntity();
        category.setId("fixed-id-for-tests");
        updatedServiceActivity.setCategory(category);
        return updatedServiceActivity;
    }

    @BeforeEach
    void initTest() {
        serviceActivity = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedServiceActivity != null) {
            serviceActivityRepository.delete(insertedServiceActivity);
            insertedServiceActivity = null;
        }
    }

    @Test
    void createServiceActivity() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ServiceActivity
        var returnedServiceActivity = om.readValue(
            restServiceActivityMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceActivity)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ServiceActivity.class
        );

        // Validate the ServiceActivity in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertServiceActivityUpdatableFieldsEquals(returnedServiceActivity, getPersistedServiceActivity(returnedServiceActivity));

        insertedServiceActivity = returnedServiceActivity;
    }

    @Test
    void createServiceActivityWithExistingId() throws Exception {
        // Create the ServiceActivity with an existing ID
        serviceActivity.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restServiceActivityMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceActivity)))
            .andExpect(status().isBadRequest());

        // Validate the ServiceActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        serviceActivity.setName(null);

        // Create the ServiceActivity, which fails.

        restServiceActivityMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceActivity)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkUnitIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        serviceActivity.setUnit(null);

        // Create the ServiceActivity, which fails.

        restServiceActivityMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceActivity)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkUnitPriceIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        serviceActivity.setUnitPrice(null);

        // Create the ServiceActivity, which fails.

        restServiceActivityMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceActivity)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkPublishedIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        serviceActivity.setPublished(null);

        // Create the ServiceActivity, which fails.

        restServiceActivityMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceActivity)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllServiceActivities() throws Exception {
        // Initialize the database
        insertedServiceActivity = serviceActivityRepository.save(serviceActivity);

        // Get all the serviceActivityList
        restServiceActivityMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(serviceActivity.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].unit").value(hasItem(DEFAULT_UNIT)))
            .andExpect(jsonPath("$.[*].unitPrice").value(hasItem(sameNumber(DEFAULT_UNIT_PRICE))))
            .andExpect(jsonPath("$.[*].duration").value(hasItem(DEFAULT_DURATION)))
            .andExpect(jsonPath("$.[*].published").value(hasItem(DEFAULT_PUBLISHED)));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllServiceActivitiesWithEagerRelationshipsIsEnabled() throws Exception {
        when(serviceActivityRepositoryMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restServiceActivityMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(serviceActivityRepositoryMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllServiceActivitiesWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(serviceActivityRepositoryMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restServiceActivityMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(serviceActivityRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    void getServiceActivity() throws Exception {
        // Initialize the database
        insertedServiceActivity = serviceActivityRepository.save(serviceActivity);

        // Get the serviceActivity
        restServiceActivityMockMvc
            .perform(get(ENTITY_API_URL_ID, serviceActivity.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(serviceActivity.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.unit").value(DEFAULT_UNIT))
            .andExpect(jsonPath("$.unitPrice").value(sameNumber(DEFAULT_UNIT_PRICE)))
            .andExpect(jsonPath("$.duration").value(DEFAULT_DURATION))
            .andExpect(jsonPath("$.published").value(DEFAULT_PUBLISHED));
    }

    @Test
    void getNonExistingServiceActivity() throws Exception {
        // Get the serviceActivity
        restServiceActivityMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingServiceActivity() throws Exception {
        // Initialize the database
        insertedServiceActivity = serviceActivityRepository.save(serviceActivity);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the serviceActivity
        ServiceActivity updatedServiceActivity = serviceActivityRepository.findById(serviceActivity.getId()).orElseThrow();
        updatedServiceActivity
            .name(UPDATED_NAME)
            .unit(UPDATED_UNIT)
            .unitPrice(UPDATED_UNIT_PRICE)
            .duration(UPDATED_DURATION)
            .published(UPDATED_PUBLISHED);

        restServiceActivityMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedServiceActivity.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedServiceActivity))
            )
            .andExpect(status().isOk());

        // Validate the ServiceActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedServiceActivityToMatchAllProperties(updatedServiceActivity);
    }

    @Test
    void putNonExistingServiceActivity() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceActivity.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restServiceActivityMockMvc
            .perform(
                put(ENTITY_API_URL_ID, serviceActivity.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(serviceActivity))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServiceActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchServiceActivity() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceActivity.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServiceActivityMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(serviceActivity))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServiceActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamServiceActivity() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceActivity.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServiceActivityMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceActivity)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ServiceActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateServiceActivityWithPatch() throws Exception {
        // Initialize the database
        insertedServiceActivity = serviceActivityRepository.save(serviceActivity);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the serviceActivity using partial update
        ServiceActivity partialUpdatedServiceActivity = new ServiceActivity();
        partialUpdatedServiceActivity.setId(serviceActivity.getId());

        partialUpdatedServiceActivity
            .unit(UPDATED_UNIT)
            .unitPrice(UPDATED_UNIT_PRICE)
            .duration(UPDATED_DURATION)
            .published(UPDATED_PUBLISHED);

        restServiceActivityMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedServiceActivity.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedServiceActivity))
            )
            .andExpect(status().isOk());

        // Validate the ServiceActivity in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertServiceActivityUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedServiceActivity, serviceActivity),
            getPersistedServiceActivity(serviceActivity)
        );
    }

    @Test
    void fullUpdateServiceActivityWithPatch() throws Exception {
        // Initialize the database
        insertedServiceActivity = serviceActivityRepository.save(serviceActivity);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the serviceActivity using partial update
        ServiceActivity partialUpdatedServiceActivity = new ServiceActivity();
        partialUpdatedServiceActivity.setId(serviceActivity.getId());

        partialUpdatedServiceActivity
            .name(UPDATED_NAME)
            .unit(UPDATED_UNIT)
            .unitPrice(UPDATED_UNIT_PRICE)
            .duration(UPDATED_DURATION)
            .published(UPDATED_PUBLISHED);

        restServiceActivityMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedServiceActivity.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedServiceActivity))
            )
            .andExpect(status().isOk());

        // Validate the ServiceActivity in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertServiceActivityUpdatableFieldsEquals(
            partialUpdatedServiceActivity,
            getPersistedServiceActivity(partialUpdatedServiceActivity)
        );
    }

    @Test
    void patchNonExistingServiceActivity() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceActivity.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restServiceActivityMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, serviceActivity.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(serviceActivity))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServiceActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchServiceActivity() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceActivity.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServiceActivityMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(serviceActivity))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServiceActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamServiceActivity() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceActivity.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServiceActivityMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(serviceActivity)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ServiceActivity in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteServiceActivity() throws Exception {
        // Initialize the database
        insertedServiceActivity = serviceActivityRepository.save(serviceActivity);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the serviceActivity
        restServiceActivityMockMvc
            .perform(delete(ENTITY_API_URL_ID, serviceActivity.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return serviceActivityRepository.count();
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

    protected ServiceActivity getPersistedServiceActivity(ServiceActivity serviceActivity) {
        return serviceActivityRepository.findById(serviceActivity.getId()).orElseThrow();
    }

    protected void assertPersistedServiceActivityToMatchAllProperties(ServiceActivity expectedServiceActivity) {
        assertServiceActivityAllPropertiesEquals(expectedServiceActivity, getPersistedServiceActivity(expectedServiceActivity));
    }

    protected void assertPersistedServiceActivityToMatchUpdatableProperties(ServiceActivity expectedServiceActivity) {
        assertServiceActivityAllUpdatablePropertiesEquals(expectedServiceActivity, getPersistedServiceActivity(expectedServiceActivity));
    }
}
