package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.DocumentItemAsserts.*;
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
import net.jojoaddison.domain.DocumentItem;
import net.jojoaddison.domain.enumeration.DocumentType;
import net.jojoaddison.repository.DocumentItemRepository;
import net.jojoaddison.service.dto.DocumentItemDTO;
import net.jojoaddison.service.mapper.DocumentItemMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link DocumentItemResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class DocumentItemResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final DocumentType DEFAULT_DOCUMENT_TYPE = DocumentType.PASSPORT;
    private static final DocumentType UPDATED_DOCUMENT_TYPE = DocumentType.ID_CARD;

    private static final String DEFAULT_URL = "AAAAAAAAAA";
    private static final String UPDATED_URL = "BBBBBBBBBB";

    private static final Instant DEFAULT_CREATED_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_CREATED_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final Instant DEFAULT_MODIFIED_DATE = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_MODIFIED_DATE = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/document-items";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private final ObjectMapper om = TestUtil.createObjectMapper();

    @Autowired
    private DocumentItemRepository documentItemRepository;

    @Autowired
    private DocumentItemMapper documentItemMapper;

    @Autowired
    private MockMvc restDocumentItemMockMvc;

    private DocumentItem documentItem;

    private DocumentItem insertedDocumentItem;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static DocumentItem createEntity() {
        return new DocumentItem()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .documentType(DEFAULT_DOCUMENT_TYPE)
            .url(DEFAULT_URL)
            .createdDate(DEFAULT_CREATED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .modifiedBy(DEFAULT_MODIFIED_BY);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static DocumentItem createUpdatedEntity() {
        return new DocumentItem()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .documentType(UPDATED_DOCUMENT_TYPE)
            .url(UPDATED_URL)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY);
    }

    @BeforeEach
    void initTest() {
        documentItem = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedDocumentItem != null) {
            documentItemRepository.delete(insertedDocumentItem);
            insertedDocumentItem = null;
        }
    }

    @Test
    void createDocumentItem() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the DocumentItem
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);
        var returnedDocumentItemDTO = om.readValue(
            restDocumentItemMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(documentItemDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            DocumentItemDTO.class
        );

        // Validate the DocumentItem in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedDocumentItem = documentItemMapper.toEntity(returnedDocumentItemDTO);
        assertDocumentItemUpdatableFieldsEquals(returnedDocumentItem, getPersistedDocumentItem(returnedDocumentItem));

        insertedDocumentItem = returnedDocumentItem;
    }

    @Test
    void createDocumentItemWithExistingId() throws Exception {
        // Create the DocumentItem with an existing ID
        documentItem.setId("existing_id");
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restDocumentItemMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(documentItemDTO)))
            .andExpect(status().isBadRequest());

        // Validate the DocumentItem in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        documentItem.setName(null);

        // Create the DocumentItem, which fails.
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        restDocumentItemMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(documentItemDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkDescriptionIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        documentItem.setDescription(null);

        // Create the DocumentItem, which fails.
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        restDocumentItemMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(documentItemDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkDocumentTypeIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        documentItem.setDocumentType(null);

        // Create the DocumentItem, which fails.
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        restDocumentItemMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(documentItemDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkUrlIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        documentItem.setUrl(null);

        // Create the DocumentItem, which fails.
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        restDocumentItemMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(documentItemDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkCreatedDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        documentItem.setCreatedDate(null);

        // Create the DocumentItem, which fails.
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        restDocumentItemMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(documentItemDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkCreatedByIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        documentItem.setCreatedBy(null);

        // Create the DocumentItem, which fails.
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        restDocumentItemMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(documentItemDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkModifiedDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        documentItem.setModifiedDate(null);

        // Create the DocumentItem, which fails.
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        restDocumentItemMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(documentItemDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkModifiedByIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        documentItem.setModifiedBy(null);

        // Create the DocumentItem, which fails.
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        restDocumentItemMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(documentItemDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllDocumentItems() throws Exception {
        // Initialize the database
        insertedDocumentItem = documentItemRepository.save(documentItem);

        // Get all the documentItemList
        restDocumentItemMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(documentItem.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].documentType").value(hasItem(DEFAULT_DOCUMENT_TYPE.toString())))
            .andExpect(jsonPath("$.[*].url").value(hasItem(DEFAULT_URL)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getDocumentItem() throws Exception {
        // Initialize the database
        insertedDocumentItem = documentItemRepository.save(documentItem);

        // Get the documentItem
        restDocumentItemMockMvc
            .perform(get(ENTITY_API_URL_ID, documentItem.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(documentItem.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.documentType").value(DEFAULT_DOCUMENT_TYPE.toString()))
            .andExpect(jsonPath("$.url").value(DEFAULT_URL))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY));
    }

    @Test
    void getNonExistingDocumentItem() throws Exception {
        // Get the documentItem
        restDocumentItemMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingDocumentItem() throws Exception {
        // Initialize the database
        insertedDocumentItem = documentItemRepository.save(documentItem);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the documentItem
        DocumentItem updatedDocumentItem = documentItemRepository.findById(documentItem.getId()).orElseThrow();
        updatedDocumentItem
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .documentType(UPDATED_DOCUMENT_TYPE)
            .url(UPDATED_URL)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY);
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(updatedDocumentItem);

        restDocumentItemMockMvc
            .perform(
                put(ENTITY_API_URL_ID, documentItemDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(documentItemDTO))
            )
            .andExpect(status().isOk());

        // Validate the DocumentItem in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedDocumentItemToMatchAllProperties(updatedDocumentItem);
    }

    @Test
    void putNonExistingDocumentItem() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        documentItem.setId(UUID.randomUUID().toString());

        // Create the DocumentItem
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restDocumentItemMockMvc
            .perform(
                put(ENTITY_API_URL_ID, documentItemDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(documentItemDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DocumentItem in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchDocumentItem() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        documentItem.setId(UUID.randomUUID().toString());

        // Create the DocumentItem
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDocumentItemMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(documentItemDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DocumentItem in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamDocumentItem() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        documentItem.setId(UUID.randomUUID().toString());

        // Create the DocumentItem
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDocumentItemMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(documentItemDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the DocumentItem in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateDocumentItemWithPatch() throws Exception {
        // Initialize the database
        insertedDocumentItem = documentItemRepository.save(documentItem);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the documentItem using partial update
        DocumentItem partialUpdatedDocumentItem = new DocumentItem();
        partialUpdatedDocumentItem.setId(documentItem.getId());

        partialUpdatedDocumentItem
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .documentType(UPDATED_DOCUMENT_TYPE)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restDocumentItemMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDocumentItem.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedDocumentItem))
            )
            .andExpect(status().isOk());

        // Validate the DocumentItem in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertDocumentItemUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedDocumentItem, documentItem),
            getPersistedDocumentItem(documentItem)
        );
    }

    @Test
    void fullUpdateDocumentItemWithPatch() throws Exception {
        // Initialize the database
        insertedDocumentItem = documentItemRepository.save(documentItem);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the documentItem using partial update
        DocumentItem partialUpdatedDocumentItem = new DocumentItem();
        partialUpdatedDocumentItem.setId(documentItem.getId());

        partialUpdatedDocumentItem
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .documentType(UPDATED_DOCUMENT_TYPE)
            .url(UPDATED_URL)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restDocumentItemMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDocumentItem.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedDocumentItem))
            )
            .andExpect(status().isOk());

        // Validate the DocumentItem in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertDocumentItemUpdatableFieldsEquals(partialUpdatedDocumentItem, getPersistedDocumentItem(partialUpdatedDocumentItem));
    }

    @Test
    void patchNonExistingDocumentItem() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        documentItem.setId(UUID.randomUUID().toString());

        // Create the DocumentItem
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restDocumentItemMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, documentItemDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(documentItemDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DocumentItem in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchDocumentItem() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        documentItem.setId(UUID.randomUUID().toString());

        // Create the DocumentItem
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDocumentItemMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(documentItemDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DocumentItem in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamDocumentItem() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        documentItem.setId(UUID.randomUUID().toString());

        // Create the DocumentItem
        DocumentItemDTO documentItemDTO = documentItemMapper.toDto(documentItem);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDocumentItemMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(documentItemDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the DocumentItem in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteDocumentItem() throws Exception {
        // Initialize the database
        insertedDocumentItem = documentItemRepository.save(documentItem);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the documentItem
        restDocumentItemMockMvc
            .perform(delete(ENTITY_API_URL_ID, documentItem.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return documentItemRepository.count();
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

    protected DocumentItem getPersistedDocumentItem(DocumentItem documentItem) {
        return documentItemRepository.findById(documentItem.getId()).orElseThrow();
    }

    protected void assertPersistedDocumentItemToMatchAllProperties(DocumentItem expectedDocumentItem) {
        assertDocumentItemAllPropertiesEquals(expectedDocumentItem, getPersistedDocumentItem(expectedDocumentItem));
    }

    protected void assertPersistedDocumentItemToMatchUpdatableProperties(DocumentItem expectedDocumentItem) {
        assertDocumentItemAllUpdatablePropertiesEquals(expectedDocumentItem, getPersistedDocumentItem(expectedDocumentItem));
    }
}
