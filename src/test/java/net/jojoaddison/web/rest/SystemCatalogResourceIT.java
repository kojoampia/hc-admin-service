package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.SystemCatalogAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.SystemCatalog;
import net.jojoaddison.domain.enumeration.CatalogType;
import net.jojoaddison.repository.SystemCatalogRepository;
import net.jojoaddison.service.dto.SystemCatalogDTO;
import net.jojoaddison.service.mapper.SystemCatalogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link SystemCatalogResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class SystemCatalogResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final CatalogType DEFAULT_TYPE = CatalogType.SERVICE;
    private static final CatalogType UPDATED_TYPE = CatalogType.PRODUCT;

    private static final String DEFAULT_CONTENT = "AAAAAAAAAA";
    private static final String UPDATED_CONTENT = "BBBBBBBBBB";

    private static final Instant DEFAULT_CREATED_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final Instant DEFAULT_MODIFIED_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_MODIFIED_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/system-catalogs";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private final ObjectMapper om = TestUtil.createObjectMapper();

    @Autowired
    private SystemCatalogRepository systemCatalogRepository;

    @Autowired
    private SystemCatalogMapper systemCatalogMapper;

    @Autowired
    private MockMvc restSystemCatalogMockMvc;

    private SystemCatalog systemCatalog;

    private SystemCatalog insertedSystemCatalog;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static SystemCatalog createEntity() {
        return new SystemCatalog()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .type(DEFAULT_TYPE)
            .content(DEFAULT_CONTENT)
            .createdDate(DEFAULT_CREATED_DATE)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedBy(DEFAULT_MODIFIED_BY);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static SystemCatalog createUpdatedEntity() {
        return new SystemCatalog()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .type(UPDATED_TYPE)
            .content(UPDATED_CONTENT)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
    }

    @BeforeEach
    void initTest() {
        systemCatalog = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedSystemCatalog != null) {
            systemCatalogRepository.delete(insertedSystemCatalog);
            insertedSystemCatalog = null;
        }
    }

    @Test
    void createSystemCatalog() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the SystemCatalog
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);
        var returnedSystemCatalogDTO = om.readValue(
            restSystemCatalogMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(systemCatalogDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            SystemCatalogDTO.class
        );

        // Validate the SystemCatalog in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedSystemCatalog = systemCatalogMapper.toEntity(returnedSystemCatalogDTO);
        assertSystemCatalogUpdatableFieldsEquals(returnedSystemCatalog, getPersistedSystemCatalog(returnedSystemCatalog));

        insertedSystemCatalog = returnedSystemCatalog;
    }

    @Test
    void createSystemCatalogWithExistingId() throws Exception {
        // Create the SystemCatalog with an existing ID
        systemCatalog.setId("existing_id");
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restSystemCatalogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(systemCatalogDTO)))
            .andExpect(status().isBadRequest());

        // Validate the SystemCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        systemCatalog.setName(null);

        // Create the SystemCatalog, which fails.
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        restSystemCatalogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(systemCatalogDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkDescriptionIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        systemCatalog.setDescription(null);

        // Create the SystemCatalog, which fails.
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        restSystemCatalogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(systemCatalogDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        systemCatalog.setType(null);

        // Create the SystemCatalog, which fails.
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        restSystemCatalogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(systemCatalogDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkCreatedDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        systemCatalog.setCreatedDate(null);

        // Create the SystemCatalog, which fails.
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        restSystemCatalogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(systemCatalogDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkModifiedDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        systemCatalog.setModifiedDate(null);

        // Create the SystemCatalog, which fails.
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        restSystemCatalogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(systemCatalogDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkCreatedByIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        systemCatalog.setCreatedBy(null);

        // Create the SystemCatalog, which fails.
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        restSystemCatalogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(systemCatalogDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkModifiedByIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        systemCatalog.setModifiedBy(null);

        // Create the SystemCatalog, which fails.
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        restSystemCatalogMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(systemCatalogDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllSystemCatalogs() throws Exception {
        // Initialize the database
        insertedSystemCatalog = systemCatalogRepository.save(systemCatalog);

        // Get all the systemCatalogList
        restSystemCatalogMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(systemCatalog.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE.toString())))
            .andExpect(jsonPath("$.[*].content").value(hasItem(DEFAULT_CONTENT)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getSystemCatalog() throws Exception {
        // Initialize the database
        insertedSystemCatalog = systemCatalogRepository.save(systemCatalog);

        // Get the systemCatalog
        restSystemCatalogMockMvc
            .perform(get(ENTITY_API_URL_ID, systemCatalog.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(systemCatalog.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE.toString()))
            .andExpect(jsonPath("$.content").value(DEFAULT_CONTENT))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY));
    }

    @Test
    void getNonExistingSystemCatalog() throws Exception {
        // Get the systemCatalog
        restSystemCatalogMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingSystemCatalog() throws Exception {
        // Initialize the database
        insertedSystemCatalog = systemCatalogRepository.save(systemCatalog);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the systemCatalog
        SystemCatalog updatedSystemCatalog = systemCatalogRepository.findById(systemCatalog.getId()).orElseThrow();
        updatedSystemCatalog
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .type(UPDATED_TYPE)
            .content(UPDATED_CONTENT)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(updatedSystemCatalog);

        restSystemCatalogMockMvc
            .perform(
                put(ENTITY_API_URL_ID, systemCatalogDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(systemCatalogDTO))
            )
            .andExpect(status().isOk());

        // Validate the SystemCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedSystemCatalogToMatchAllProperties(updatedSystemCatalog);
    }

    @Test
    void putNonExistingSystemCatalog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        systemCatalog.setId(UUID.randomUUID().toString());

        // Create the SystemCatalog
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restSystemCatalogMockMvc
            .perform(
                put(ENTITY_API_URL_ID, systemCatalogDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(systemCatalogDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SystemCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchSystemCatalog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        systemCatalog.setId(UUID.randomUUID().toString());

        // Create the SystemCatalog
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSystemCatalogMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(systemCatalogDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SystemCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamSystemCatalog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        systemCatalog.setId(UUID.randomUUID().toString());

        // Create the SystemCatalog
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSystemCatalogMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(systemCatalogDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the SystemCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateSystemCatalogWithPatch() throws Exception {
        // Initialize the database
        insertedSystemCatalog = systemCatalogRepository.save(systemCatalog);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the systemCatalog using partial update
        SystemCatalog partialUpdatedSystemCatalog = new SystemCatalog();
        partialUpdatedSystemCatalog.setId(systemCatalog.getId());

        partialUpdatedSystemCatalog
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restSystemCatalogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedSystemCatalog.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedSystemCatalog))
            )
            .andExpect(status().isOk());

        // Validate the SystemCatalog in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertSystemCatalogUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedSystemCatalog, systemCatalog),
            getPersistedSystemCatalog(systemCatalog)
        );
    }

    @Test
    void fullUpdateSystemCatalogWithPatch() throws Exception {
        // Initialize the database
        insertedSystemCatalog = systemCatalogRepository.save(systemCatalog);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the systemCatalog using partial update
        SystemCatalog partialUpdatedSystemCatalog = new SystemCatalog();
        partialUpdatedSystemCatalog.setId(systemCatalog.getId());

        partialUpdatedSystemCatalog
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .type(UPDATED_TYPE)
            .content(UPDATED_CONTENT)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restSystemCatalogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedSystemCatalog.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedSystemCatalog))
            )
            .andExpect(status().isOk());

        // Validate the SystemCatalog in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertSystemCatalogUpdatableFieldsEquals(partialUpdatedSystemCatalog, getPersistedSystemCatalog(partialUpdatedSystemCatalog));
    }

    @Test
    void patchNonExistingSystemCatalog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        systemCatalog.setId(UUID.randomUUID().toString());

        // Create the SystemCatalog
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restSystemCatalogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, systemCatalogDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(systemCatalogDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SystemCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchSystemCatalog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        systemCatalog.setId(UUID.randomUUID().toString());

        // Create the SystemCatalog
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSystemCatalogMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(systemCatalogDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the SystemCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamSystemCatalog() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        systemCatalog.setId(UUID.randomUUID().toString());

        // Create the SystemCatalog
        SystemCatalogDTO systemCatalogDTO = systemCatalogMapper.toDto(systemCatalog);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restSystemCatalogMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(systemCatalogDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the SystemCatalog in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteSystemCatalog() throws Exception {
        // Initialize the database
        insertedSystemCatalog = systemCatalogRepository.save(systemCatalog);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the systemCatalog
        restSystemCatalogMockMvc
            .perform(delete(ENTITY_API_URL_ID, systemCatalog.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return systemCatalogRepository.count();
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

    protected SystemCatalog getPersistedSystemCatalog(SystemCatalog systemCatalog) {
        return systemCatalogRepository.findById(systemCatalog.getId()).orElseThrow();
    }

    protected void assertPersistedSystemCatalogToMatchAllProperties(SystemCatalog expectedSystemCatalog) {
        assertSystemCatalogAllPropertiesEquals(expectedSystemCatalog, getPersistedSystemCatalog(expectedSystemCatalog));
    }

    protected void assertPersistedSystemCatalogToMatchUpdatableProperties(SystemCatalog expectedSystemCatalog) {
        assertSystemCatalogAllUpdatablePropertiesEquals(expectedSystemCatalog, getPersistedSystemCatalog(expectedSystemCatalog));
    }
}
