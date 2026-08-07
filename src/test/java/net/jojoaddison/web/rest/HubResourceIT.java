package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.HubAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Hub;
import net.jojoaddison.repository.HubRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link HubResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class HubResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final Integer DEFAULT_STAFF_COUNT = 0;
    private static final Integer UPDATED_STAFF_COUNT = 1;

    private static final String ENTITY_API_URL = "/api/hubs";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private MockMvc restHubMockMvc;

    private Hub hub;

    private Hub insertedHub;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Hub createEntity() {
        return new Hub().name(DEFAULT_NAME).staffCount(DEFAULT_STAFF_COUNT);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Hub createUpdatedEntity() {
        return new Hub().name(UPDATED_NAME).staffCount(UPDATED_STAFF_COUNT);
    }

    @BeforeEach
    void initTest() {
        hub = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedHub != null) {
            hubRepository.delete(insertedHub);
            insertedHub = null;
        }
    }

    @Test
    void createHub() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Hub
        var returnedHub = om.readValue(
            restHubMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(hub)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Hub.class
        );

        // Validate the Hub in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertHubUpdatableFieldsEquals(returnedHub, getPersistedHub(returnedHub));

        insertedHub = returnedHub;
    }

    @Test
    void createHubWithExistingId() throws Exception {
        // Create the Hub with an existing ID
        hub.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restHubMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(hub)))
            .andExpect(status().isBadRequest());

        // Validate the Hub in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        hub.setName(null);

        // Create the Hub, which fails.

        restHubMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(hub)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllHubs() throws Exception {
        // Initialize the database
        insertedHub = hubRepository.save(hub);

        // Get all the hubList
        restHubMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(hub.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].staffCount").value(hasItem(DEFAULT_STAFF_COUNT)));
    }

    @Test
    void getHub() throws Exception {
        // Initialize the database
        insertedHub = hubRepository.save(hub);

        // Get the hub
        restHubMockMvc
            .perform(get(ENTITY_API_URL_ID, hub.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(hub.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.staffCount").value(DEFAULT_STAFF_COUNT));
    }

    @Test
    void getNonExistingHub() throws Exception {
        // Get the hub
        restHubMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingHub() throws Exception {
        // Initialize the database
        insertedHub = hubRepository.save(hub);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the hub
        Hub updatedHub = hubRepository.findById(hub.getId()).orElseThrow();
        updatedHub.name(UPDATED_NAME).staffCount(UPDATED_STAFF_COUNT);

        restHubMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedHub.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(updatedHub))
            )
            .andExpect(status().isOk());

        // Validate the Hub in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedHubToMatchAllProperties(updatedHub);
    }

    @Test
    void putNonExistingHub() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        hub.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restHubMockMvc
            .perform(put(ENTITY_API_URL_ID, hub.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(hub)))
            .andExpect(status().isBadRequest());

        // Validate the Hub in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchHub() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        hub.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHubMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(hub))
            )
            .andExpect(status().isBadRequest());

        // Validate the Hub in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamHub() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        hub.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHubMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(hub)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Hub in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateHubWithPatch() throws Exception {
        // Initialize the database
        insertedHub = hubRepository.save(hub);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the hub using partial update
        Hub partialUpdatedHub = new Hub();
        partialUpdatedHub.setId(hub.getId());

        partialUpdatedHub.name(UPDATED_NAME);

        restHubMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedHub.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedHub))
            )
            .andExpect(status().isOk());

        // Validate the Hub in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertHubUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedHub, hub), getPersistedHub(hub));
    }

    @Test
    void fullUpdateHubWithPatch() throws Exception {
        // Initialize the database
        insertedHub = hubRepository.save(hub);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the hub using partial update
        Hub partialUpdatedHub = new Hub();
        partialUpdatedHub.setId(hub.getId());

        partialUpdatedHub.name(UPDATED_NAME).staffCount(UPDATED_STAFF_COUNT);

        restHubMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedHub.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedHub))
            )
            .andExpect(status().isOk());

        // Validate the Hub in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertHubUpdatableFieldsEquals(partialUpdatedHub, getPersistedHub(partialUpdatedHub));
    }

    @Test
    void patchNonExistingHub() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        hub.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restHubMockMvc
            .perform(patch(ENTITY_API_URL_ID, hub.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(hub)))
            .andExpect(status().isBadRequest());

        // Validate the Hub in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchHub() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        hub.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHubMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(hub))
            )
            .andExpect(status().isBadRequest());

        // Validate the Hub in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamHub() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        hub.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHubMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(hub)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Hub in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteHub() throws Exception {
        // Initialize the database
        insertedHub = hubRepository.save(hub);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the hub
        restHubMockMvc.perform(delete(ENTITY_API_URL_ID, hub.getId()).accept(MediaType.APPLICATION_JSON)).andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return hubRepository.count();
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

    protected Hub getPersistedHub(Hub hub) {
        return hubRepository.findById(hub.getId()).orElseThrow();
    }

    protected void assertPersistedHubToMatchAllProperties(Hub expectedHub) {
        assertHubAllPropertiesEquals(expectedHub, getPersistedHub(expectedHub));
    }

    protected void assertPersistedHubToMatchUpdatableProperties(Hub expectedHub) {
        assertHubAllUpdatablePropertiesEquals(expectedHub, getPersistedHub(expectedHub));
    }
}
