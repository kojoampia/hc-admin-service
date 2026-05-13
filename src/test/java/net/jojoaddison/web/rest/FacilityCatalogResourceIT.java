package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.FacilityCatalogAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.FacilityCatalog;
import net.jojoaddison.repository.FacilityCatalogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link FacilityCatalogResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class FacilityCatalogResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_FACILITIES = "AAAAAAAAAA";
    private static final String UPDATED_FACILITIES = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/facility-catalogs";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private final ObjectMapper om = TestUtil.createObjectMapper();

    @Autowired
    private FacilityCatalogRepository facilityCatalogRepository;

    @Autowired
    private MockMvc restFacilityCatalogMockMvc;

    private FacilityCatalog facilityCatalog;

    private FacilityCatalog insertedFacilityCatalog;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static FacilityCatalog createEntity() {
        return new FacilityCatalog().name(DEFAULT_NAME).description(DEFAULT_DESCRIPTION).facilities(DEFAULT_FACILITIES);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static FacilityCatalog createUpdatedEntity() {
        return new FacilityCatalog().name(UPDATED_NAME).description(UPDATED_DESCRIPTION).facilities(UPDATED_FACILITIES);
    }

    @BeforeEach
    void initTest() {
        facilityCatalog = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedFacilityCatalog != null) {
            facilityCatalogRepository.delete(insertedFacilityCatalog);
            insertedFacilityCatalog = null;
        }
    }

    @Test
    void createFacilityCatalog() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the FacilityCatalog
        var returnedFacilityCatalog = om.readValue(
            restFacilityCatalogMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(facilityCatalog)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            FacilityCatalog.class
        );

        // Validate the FacilityCatalog in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertFacilityCatalogUpdatableFieldsEquals(returnedFacilityCatalog, getPersistedFacilityCatalog(returnedFacilityCatalog));

        insertedFacilityCatalog = returnedFacilityCatalog;
    }

    @Test
    void createFacilityCatalogWithExistingId() throws Exception {
        // Create the FacilityCatalog with an existing ID
        facilityCatalog.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restFacilityCatalogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(facilityCatalog)))
            .andExpect(status().isBadRequest());

        // Validate the FacilityCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        facilityCatalog.setName(null);

        // Create the FacilityCatalog, which fails.

        restFacilityCatalogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(facilityCatalog)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkDescriptionIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        facilityCatalog.setDescription(null);

        // Create the FacilityCatalog, which fails.

        restFacilityCatalogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(facilityCatalog)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkFacilitiesIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        facilityCatalog.setFacilities(null);

        // Create the FacilityCatalog, which fails.

        restFacilityCatalogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(facilityCatalog)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllFacilityCatalogs() throws Exception {
        // Initialize the database
        insertedFacilityCatalog = facilityCatalogRepository.save(facilityCatalog);

        // Get all the facilityCatalogList
        restFacilityCatalogMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(facilityCatalog.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].facilities").value(hasItem(DEFAULT_FACILITIES)));
    }

    @Test
    void getFacilityCatalog() throws Exception {
        // Initialize the database
        insertedFacilityCatalog = facilityCatalogRepository.save(facilityCatalog);

        // Get the facilityCatalog
        restFacilityCatalogMockMvc
            .perform(get(ENTITY_API_URL_ID, facilityCatalog.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(facilityCatalog.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.facilities").value(DEFAULT_FACILITIES));
    }

    @Test
    void getNonExistingFacilityCatalog() throws Exception {
        // Get the facilityCatalog
        restFacilityCatalogMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingFacilityCatalog() throws Exception {
        // Initialize the database
        insertedFacilityCatalog = facilityCatalogRepository.save(facilityCatalog);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the facilityCatalog
        FacilityCatalog updatedFacilityCatalog = facilityCatalogRepository.findById(facilityCatalog.getId()).orElseThrow();
        updatedFacilityCatalog.name(UPDATED_NAME).description(UPDATED_DESCRIPTION).facilities(UPDATED_FACILITIES);

        restFacilityCatalogMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedFacilityCatalog.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedFacilityCatalog))
            )
            .andExpect(status().isOk());

        // Validate the FacilityCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedFacilityCatalogToMatchAllProperties(updatedFacilityCatalog);
    }

    @Test
    void putNonExistingFacilityCatalog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        facilityCatalog.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restFacilityCatalogMockMvc
            .perform(
                put(ENTITY_API_URL_ID, facilityCatalog.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(facilityCatalog))
            )
            .andExpect(status().isBadRequest());

        // Validate the FacilityCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchFacilityCatalog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        facilityCatalog.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFacilityCatalogMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(facilityCatalog))
            )
            .andExpect(status().isBadRequest());

        // Validate the FacilityCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamFacilityCatalog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        facilityCatalog.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFacilityCatalogMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(facilityCatalog)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the FacilityCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateFacilityCatalogWithPatch() throws Exception {
        // Initialize the database
        insertedFacilityCatalog = facilityCatalogRepository.save(facilityCatalog);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the facilityCatalog using partial update
        FacilityCatalog partialUpdatedFacilityCatalog = new FacilityCatalog();
        partialUpdatedFacilityCatalog.setId(facilityCatalog.getId());

        partialUpdatedFacilityCatalog.name(UPDATED_NAME).facilities(UPDATED_FACILITIES);

        restFacilityCatalogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedFacilityCatalog.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedFacilityCatalog))
            )
            .andExpect(status().isOk());

        // Validate the FacilityCatalog in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertFacilityCatalogUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedFacilityCatalog, facilityCatalog),
            getPersistedFacilityCatalog(facilityCatalog)
        );
    }

    @Test
    void fullUpdateFacilityCatalogWithPatch() throws Exception {
        // Initialize the database
        insertedFacilityCatalog = facilityCatalogRepository.save(facilityCatalog);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the facilityCatalog using partial update
        FacilityCatalog partialUpdatedFacilityCatalog = new FacilityCatalog();
        partialUpdatedFacilityCatalog.setId(facilityCatalog.getId());

        partialUpdatedFacilityCatalog.name(UPDATED_NAME).description(UPDATED_DESCRIPTION).facilities(UPDATED_FACILITIES);

        restFacilityCatalogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedFacilityCatalog.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedFacilityCatalog))
            )
            .andExpect(status().isOk());

        // Validate the FacilityCatalog in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertFacilityCatalogUpdatableFieldsEquals(
            partialUpdatedFacilityCatalog,
            getPersistedFacilityCatalog(partialUpdatedFacilityCatalog)
        );
    }

    @Test
    void patchNonExistingFacilityCatalog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        facilityCatalog.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restFacilityCatalogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, facilityCatalog.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(facilityCatalog))
            )
            .andExpect(status().isBadRequest());

        // Validate the FacilityCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchFacilityCatalog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        facilityCatalog.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFacilityCatalogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(facilityCatalog))
            )
            .andExpect(status().isBadRequest());

        // Validate the FacilityCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamFacilityCatalog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        facilityCatalog.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restFacilityCatalogMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(facilityCatalog)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the FacilityCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteFacilityCatalog() throws Exception {
        // Initialize the database
        insertedFacilityCatalog = facilityCatalogRepository.save(facilityCatalog);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the facilityCatalog
        restFacilityCatalogMockMvc
            .perform(delete(ENTITY_API_URL_ID, facilityCatalog.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return facilityCatalogRepository.count();
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

    protected FacilityCatalog getPersistedFacilityCatalog(FacilityCatalog facilityCatalog) {
        return facilityCatalogRepository.findById(facilityCatalog.getId()).orElseThrow();
    }

    protected void assertPersistedFacilityCatalogToMatchAllProperties(FacilityCatalog expectedFacilityCatalog) {
        assertFacilityCatalogAllPropertiesEquals(expectedFacilityCatalog, getPersistedFacilityCatalog(expectedFacilityCatalog));
    }

    protected void assertPersistedFacilityCatalogToMatchUpdatableProperties(FacilityCatalog expectedFacilityCatalog) {
        assertFacilityCatalogAllUpdatablePropertiesEquals(expectedFacilityCatalog, getPersistedFacilityCatalog(expectedFacilityCatalog));
    }
}
