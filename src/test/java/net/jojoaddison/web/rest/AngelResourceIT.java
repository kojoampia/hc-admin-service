package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.AngelAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Angel;
import net.jojoaddison.repository.AngelRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link AngelResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class AngelResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_RELATIONSHIP = "AAAAAAAAAA";
    private static final String UPDATED_RELATIONSHIP = "BBBBBBBBBB";

    private static final String DEFAULT_PHONE = "AAAAAAAAAA";
    private static final String UPDATED_PHONE = "BBBBBBBBBB";

    private static final String DEFAULT_EMAIL = "AAAAAAAAAA";
    private static final String UPDATED_EMAIL = "BBBBBBBBBB";

    private static final String DEFAULT_COUNTRY = "AAAAAAAAAA";
    private static final String UPDATED_COUNTRY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/angels";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private AngelRepository angelRepository;

    @Autowired
    private MockMvc restAngelMockMvc;

    private Angel angel;

    private Angel insertedAngel;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Angel createEntity() {
        return new Angel()
            .name(DEFAULT_NAME)
            .relationship(DEFAULT_RELATIONSHIP)
            .phone(DEFAULT_PHONE)
            .email(DEFAULT_EMAIL)
            .country(DEFAULT_COUNTRY);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Angel createUpdatedEntity() {
        return new Angel()
            .name(UPDATED_NAME)
            .relationship(UPDATED_RELATIONSHIP)
            .phone(UPDATED_PHONE)
            .email(UPDATED_EMAIL)
            .country(UPDATED_COUNTRY);
    }

    @BeforeEach
    void initTest() {
        angel = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedAngel != null) {
            angelRepository.delete(insertedAngel);
            insertedAngel = null;
        }
    }

    @Test
    void createAngel() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Angel
        var returnedAngel = om.readValue(
            restAngelMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(angel)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Angel.class
        );

        // Validate the Angel in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertAngelUpdatableFieldsEquals(returnedAngel, getPersistedAngel(returnedAngel));

        insertedAngel = returnedAngel;
    }

    @Test
    void createAngelWithExistingId() throws Exception {
        // Create the Angel with an existing ID
        angel.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restAngelMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(angel)))
            .andExpect(status().isBadRequest());

        // Validate the Angel in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        angel.setName(null);

        // Create the Angel, which fails.

        restAngelMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(angel)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkRelationshipIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        angel.setRelationship(null);

        // Create the Angel, which fails.

        restAngelMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(angel)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkPhoneIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        angel.setPhone(null);

        // Create the Angel, which fails.

        restAngelMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(angel)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllAngels() throws Exception {
        // Initialize the database
        insertedAngel = angelRepository.save(angel);

        // Get all the angelList
        restAngelMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(angel.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].relationship").value(hasItem(DEFAULT_RELATIONSHIP)))
            .andExpect(jsonPath("$.[*].phone").value(hasItem(DEFAULT_PHONE)))
            .andExpect(jsonPath("$.[*].email").value(hasItem(DEFAULT_EMAIL)))
            .andExpect(jsonPath("$.[*].country").value(hasItem(DEFAULT_COUNTRY)));
    }

    @Test
    void getAngel() throws Exception {
        // Initialize the database
        insertedAngel = angelRepository.save(angel);

        // Get the angel
        restAngelMockMvc
            .perform(get(ENTITY_API_URL_ID, angel.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(angel.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.relationship").value(DEFAULT_RELATIONSHIP))
            .andExpect(jsonPath("$.phone").value(DEFAULT_PHONE))
            .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
            .andExpect(jsonPath("$.country").value(DEFAULT_COUNTRY));
    }

    @Test
    void getNonExistingAngel() throws Exception {
        // Get the angel
        restAngelMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingAngel() throws Exception {
        // Initialize the database
        insertedAngel = angelRepository.save(angel);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the angel
        Angel updatedAngel = angelRepository.findById(angel.getId()).orElseThrow();
        updatedAngel
            .name(UPDATED_NAME)
            .relationship(UPDATED_RELATIONSHIP)
            .phone(UPDATED_PHONE)
            .email(UPDATED_EMAIL)
            .country(UPDATED_COUNTRY);

        restAngelMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedAngel.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedAngel))
            )
            .andExpect(status().isOk());

        // Validate the Angel in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedAngelToMatchAllProperties(updatedAngel);
    }

    @Test
    void putNonExistingAngel() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        angel.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restAngelMockMvc
            .perform(put(ENTITY_API_URL_ID, angel.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(angel)))
            .andExpect(status().isBadRequest());

        // Validate the Angel in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchAngel() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        angel.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAngelMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(angel))
            )
            .andExpect(status().isBadRequest());

        // Validate the Angel in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamAngel() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        angel.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAngelMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(angel)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Angel in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateAngelWithPatch() throws Exception {
        // Initialize the database
        insertedAngel = angelRepository.save(angel);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the angel using partial update
        Angel partialUpdatedAngel = new Angel();
        partialUpdatedAngel.setId(angel.getId());

        partialUpdatedAngel.name(UPDATED_NAME).relationship(UPDATED_RELATIONSHIP).email(UPDATED_EMAIL);

        restAngelMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedAngel.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedAngel))
            )
            .andExpect(status().isOk());

        // Validate the Angel in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertAngelUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedAngel, angel), getPersistedAngel(angel));
    }

    @Test
    void fullUpdateAngelWithPatch() throws Exception {
        // Initialize the database
        insertedAngel = angelRepository.save(angel);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the angel using partial update
        Angel partialUpdatedAngel = new Angel();
        partialUpdatedAngel.setId(angel.getId());

        partialUpdatedAngel
            .name(UPDATED_NAME)
            .relationship(UPDATED_RELATIONSHIP)
            .phone(UPDATED_PHONE)
            .email(UPDATED_EMAIL)
            .country(UPDATED_COUNTRY);

        restAngelMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedAngel.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedAngel))
            )
            .andExpect(status().isOk());

        // Validate the Angel in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertAngelUpdatableFieldsEquals(partialUpdatedAngel, getPersistedAngel(partialUpdatedAngel));
    }

    @Test
    void patchNonExistingAngel() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        angel.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restAngelMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, angel.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(angel))
            )
            .andExpect(status().isBadRequest());

        // Validate the Angel in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchAngel() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        angel.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAngelMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(angel))
            )
            .andExpect(status().isBadRequest());

        // Validate the Angel in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamAngel() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        angel.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restAngelMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(angel)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Angel in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteAngel() throws Exception {
        // Initialize the database
        insertedAngel = angelRepository.save(angel);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the angel
        restAngelMockMvc
            .perform(delete(ENTITY_API_URL_ID, angel.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return angelRepository.count();
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

    protected Angel getPersistedAngel(Angel angel) {
        return angelRepository.findById(angel.getId()).orElseThrow();
    }

    protected void assertPersistedAngelToMatchAllProperties(Angel expectedAngel) {
        assertAngelAllPropertiesEquals(expectedAngel, getPersistedAngel(expectedAngel));
    }

    protected void assertPersistedAngelToMatchUpdatableProperties(Angel expectedAngel) {
        assertAngelAllUpdatablePropertiesEquals(expectedAngel, getPersistedAngel(expectedAngel));
    }
}
