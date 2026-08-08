package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.UserOptionAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.UserOption;
import net.jojoaddison.repository.UserOptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link UserOptionResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class UserOptionResourceIT {

    private static final String DEFAULT_CATEGORY = "AAAAAAAAAA";
    private static final String UPDATED_CATEGORY = "BBBBBBBBBB";

    private static final String DEFAULT_USER_REF = "AAAAAAAAAA";
    private static final String UPDATED_USER_REF = "BBBBBBBBBB";

    private static final String DEFAULT_METADATA = "AAAAAAAAAA";
    private static final String UPDATED_METADATA = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/user-options";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private UserOptionRepository userOptionRepository;

    @Autowired
    private MockMvc restUserOptionMockMvc;

    private UserOption userOption;

    private UserOption insertedUserOption;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static UserOption createEntity() {
        return new UserOption().category(DEFAULT_CATEGORY).userRef(DEFAULT_USER_REF).metadata(DEFAULT_METADATA);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static UserOption createUpdatedEntity() {
        return new UserOption().category(UPDATED_CATEGORY).userRef(UPDATED_USER_REF).metadata(UPDATED_METADATA);
    }

    @BeforeEach
    void initTest() {
        userOption = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedUserOption != null) {
            userOptionRepository.delete(insertedUserOption);
            insertedUserOption = null;
        }
    }

    @Test
    void createUserOption() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the UserOption
        var returnedUserOption = om.readValue(
            restUserOptionMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userOption)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            UserOption.class
        );

        // Validate the UserOption in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertUserOptionUpdatableFieldsEquals(returnedUserOption, getPersistedUserOption(returnedUserOption));

        insertedUserOption = returnedUserOption;
    }

    @Test
    void createUserOptionWithExistingId() throws Exception {
        // Create the UserOption with an existing ID
        userOption.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restUserOptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userOption)))
            .andExpect(status().isBadRequest());

        // Validate the UserOption in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkCategoryIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        userOption.setCategory(null);

        // Create the UserOption, which fails.

        restUserOptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userOption)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkUserRefIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        userOption.setUserRef(null);

        // Create the UserOption, which fails.

        restUserOptionMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userOption)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllUserOptions() throws Exception {
        // Initialize the database
        insertedUserOption = userOptionRepository.save(userOption);

        // Get all the userOptionList
        restUserOptionMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(userOption.getId())))
            .andExpect(jsonPath("$.[*].category").value(hasItem(DEFAULT_CATEGORY)))
            .andExpect(jsonPath("$.[*].userRef").value(hasItem(DEFAULT_USER_REF)))
            .andExpect(jsonPath("$.[*].metadata").value(hasItem(DEFAULT_METADATA)));
    }

    @Test
    void getUserOption() throws Exception {
        // Initialize the database
        insertedUserOption = userOptionRepository.save(userOption);

        // Get the userOption
        restUserOptionMockMvc
            .perform(get(ENTITY_API_URL_ID, userOption.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(userOption.getId()))
            .andExpect(jsonPath("$.category").value(DEFAULT_CATEGORY))
            .andExpect(jsonPath("$.userRef").value(DEFAULT_USER_REF))
            .andExpect(jsonPath("$.metadata").value(DEFAULT_METADATA));
    }

    @Test
    void getNonExistingUserOption() throws Exception {
        // Get the userOption
        restUserOptionMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingUserOption() throws Exception {
        // Initialize the database
        insertedUserOption = userOptionRepository.save(userOption);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the userOption
        UserOption updatedUserOption = userOptionRepository.findById(userOption.getId()).orElseThrow();
        updatedUserOption.category(UPDATED_CATEGORY).userRef(UPDATED_USER_REF).metadata(UPDATED_METADATA);

        restUserOptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedUserOption.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedUserOption))
            )
            .andExpect(status().isOk());

        // Validate the UserOption in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedUserOptionToMatchAllProperties(updatedUserOption);
    }

    @Test
    void putNonExistingUserOption() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userOption.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restUserOptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, userOption.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userOption))
            )
            .andExpect(status().isBadRequest());

        // Validate the UserOption in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchUserOption() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userOption.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUserOptionMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(userOption))
            )
            .andExpect(status().isBadRequest());

        // Validate the UserOption in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamUserOption() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userOption.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUserOptionMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(userOption)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the UserOption in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateUserOptionWithPatch() throws Exception {
        // Initialize the database
        insertedUserOption = userOptionRepository.save(userOption);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the userOption using partial update
        UserOption partialUpdatedUserOption = new UserOption();
        partialUpdatedUserOption.setId(userOption.getId());

        partialUpdatedUserOption.userRef(UPDATED_USER_REF).metadata(UPDATED_METADATA);

        restUserOptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedUserOption.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedUserOption))
            )
            .andExpect(status().isOk());

        // Validate the UserOption in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertUserOptionUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedUserOption, userOption),
            getPersistedUserOption(userOption)
        );
    }

    @Test
    void fullUpdateUserOptionWithPatch() throws Exception {
        // Initialize the database
        insertedUserOption = userOptionRepository.save(userOption);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the userOption using partial update
        UserOption partialUpdatedUserOption = new UserOption();
        partialUpdatedUserOption.setId(userOption.getId());

        partialUpdatedUserOption.category(UPDATED_CATEGORY).userRef(UPDATED_USER_REF).metadata(UPDATED_METADATA);

        restUserOptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedUserOption.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedUserOption))
            )
            .andExpect(status().isOk());

        // Validate the UserOption in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertUserOptionUpdatableFieldsEquals(partialUpdatedUserOption, getPersistedUserOption(partialUpdatedUserOption));
    }

    @Test
    void patchNonExistingUserOption() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userOption.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restUserOptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, userOption.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(userOption))
            )
            .andExpect(status().isBadRequest());

        // Validate the UserOption in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchUserOption() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userOption.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUserOptionMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(userOption))
            )
            .andExpect(status().isBadRequest());

        // Validate the UserOption in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamUserOption() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        userOption.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restUserOptionMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(userOption)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the UserOption in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteUserOption() throws Exception {
        // Initialize the database
        insertedUserOption = userOptionRepository.save(userOption);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the userOption
        restUserOptionMockMvc
            .perform(delete(ENTITY_API_URL_ID, userOption.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return userOptionRepository.count();
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

    protected UserOption getPersistedUserOption(UserOption userOption) {
        return userOptionRepository.findById(userOption.getId()).orElseThrow();
    }

    protected void assertPersistedUserOptionToMatchAllProperties(UserOption expectedUserOption) {
        assertUserOptionAllPropertiesEquals(expectedUserOption, getPersistedUserOption(expectedUserOption));
    }

    protected void assertPersistedUserOptionToMatchUpdatableProperties(UserOption expectedUserOption) {
        assertUserOptionAllUpdatablePropertiesEquals(expectedUserOption, getPersistedUserOption(expectedUserOption));
    }
}
