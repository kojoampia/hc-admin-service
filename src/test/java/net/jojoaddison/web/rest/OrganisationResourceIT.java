package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Organisation;
import net.jojoaddison.repository.OrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link OrganisationResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class OrganisationResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_PROFILE = "AAAAAAAAAA";
    private static final String UPDATED_PROFILE = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_MODIFIED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_MODIFIED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/organisations";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private MockMvc restOrganisationMockMvc;

    private Organisation organisation;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Organisation createEntity() {
        Organisation organisation = new Organisation()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .profile(DEFAULT_PROFILE)
            .createdDate(DEFAULT_CREATED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .modifiedBy(DEFAULT_MODIFIED_BY);
        return organisation;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Organisation createUpdatedEntity() {
        Organisation organisation = new Organisation()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .profile(UPDATED_PROFILE)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY);
        return organisation;
    }

    @BeforeEach
    public void initTest() {
        organisationRepository.deleteAll();
        organisation = createEntity();
    }

    @Test
    void createOrganisation() throws Exception {
        int databaseSizeBeforeCreate = organisationRepository.findAll().size();
        // Create the Organisation
        restOrganisationMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(organisation)))
            .andExpect(status().isCreated());

        // Validate the Organisation in the database
        List<Organisation> organisationList = organisationRepository.findAll();
        assertThat(organisationList).hasSize(databaseSizeBeforeCreate + 1);
        Organisation testOrganisation = organisationList.get(organisationList.size() - 1);
        assertThat(testOrganisation.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(testOrganisation.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(testOrganisation.getProfile()).isEqualTo(DEFAULT_PROFILE);
        assertThat(testOrganisation.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testOrganisation.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testOrganisation.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testOrganisation.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void createOrganisationWithExistingId() throws Exception {
        // Create the Organisation with an existing ID
        organisation.setId("existing_id");

        int databaseSizeBeforeCreate = organisationRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restOrganisationMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(organisation)))
            .andExpect(status().isBadRequest());

        // Validate the Organisation in the database
        List<Organisation> organisationList = organisationRepository.findAll();
        assertThat(organisationList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllOrganisations() throws Exception {
        // Initialize the database
        organisationRepository.save(organisation);

        // Get all the organisationList
        restOrganisationMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(organisation.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].profile").value(hasItem(DEFAULT_PROFILE)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getOrganisation() throws Exception {
        // Initialize the database
        organisationRepository.save(organisation);

        // Get the organisation
        restOrganisationMockMvc
            .perform(get(ENTITY_API_URL_ID, organisation.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(organisation.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.profile").value(DEFAULT_PROFILE))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY));
    }

    @Test
    void getNonExistingOrganisation() throws Exception {
        // Get the organisation
        restOrganisationMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingOrganisation() throws Exception {
        // Initialize the database
        organisationRepository.save(organisation);

        int databaseSizeBeforeUpdate = organisationRepository.findAll().size();

        // Update the organisation
        Organisation updatedOrganisation = organisationRepository.findById(organisation.getId()).orElseThrow();
        updatedOrganisation
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .profile(UPDATED_PROFILE)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restOrganisationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedOrganisation.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedOrganisation))
            )
            .andExpect(status().isOk());

        // Validate the Organisation in the database
        List<Organisation> organisationList = organisationRepository.findAll();
        assertThat(organisationList).hasSize(databaseSizeBeforeUpdate);
        Organisation testOrganisation = organisationList.get(organisationList.size() - 1);
        assertThat(testOrganisation.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testOrganisation.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testOrganisation.getProfile()).isEqualTo(UPDATED_PROFILE);
        assertThat(testOrganisation.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testOrganisation.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testOrganisation.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testOrganisation.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void putNonExistingOrganisation() throws Exception {
        int databaseSizeBeforeUpdate = organisationRepository.findAll().size();
        organisation.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restOrganisationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, organisation.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(organisation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Organisation in the database
        List<Organisation> organisationList = organisationRepository.findAll();
        assertThat(organisationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchOrganisation() throws Exception {
        int databaseSizeBeforeUpdate = organisationRepository.findAll().size();
        organisation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restOrganisationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(organisation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Organisation in the database
        List<Organisation> organisationList = organisationRepository.findAll();
        assertThat(organisationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamOrganisation() throws Exception {
        int databaseSizeBeforeUpdate = organisationRepository.findAll().size();
        organisation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restOrganisationMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(organisation)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Organisation in the database
        List<Organisation> organisationList = organisationRepository.findAll();
        assertThat(organisationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateOrganisationWithPatch() throws Exception {
        // Initialize the database
        organisationRepository.save(organisation);

        int databaseSizeBeforeUpdate = organisationRepository.findAll().size();

        // Update the organisation using partial update
        Organisation partialUpdatedOrganisation = new Organisation();
        partialUpdatedOrganisation.setId(organisation.getId());

        partialUpdatedOrganisation
            .description(UPDATED_DESCRIPTION)
            .profile(UPDATED_PROFILE)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restOrganisationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedOrganisation.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedOrganisation))
            )
            .andExpect(status().isOk());

        // Validate the Organisation in the database
        List<Organisation> organisationList = organisationRepository.findAll();
        assertThat(organisationList).hasSize(databaseSizeBeforeUpdate);
        Organisation testOrganisation = organisationList.get(organisationList.size() - 1);
        assertThat(testOrganisation.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(testOrganisation.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testOrganisation.getProfile()).isEqualTo(UPDATED_PROFILE);
        assertThat(testOrganisation.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testOrganisation.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testOrganisation.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testOrganisation.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void fullUpdateOrganisationWithPatch() throws Exception {
        // Initialize the database
        organisationRepository.save(organisation);

        int databaseSizeBeforeUpdate = organisationRepository.findAll().size();

        // Update the organisation using partial update
        Organisation partialUpdatedOrganisation = new Organisation();
        partialUpdatedOrganisation.setId(organisation.getId());

        partialUpdatedOrganisation
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .profile(UPDATED_PROFILE)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restOrganisationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedOrganisation.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedOrganisation))
            )
            .andExpect(status().isOk());

        // Validate the Organisation in the database
        List<Organisation> organisationList = organisationRepository.findAll();
        assertThat(organisationList).hasSize(databaseSizeBeforeUpdate);
        Organisation testOrganisation = organisationList.get(organisationList.size() - 1);
        assertThat(testOrganisation.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testOrganisation.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testOrganisation.getProfile()).isEqualTo(UPDATED_PROFILE);
        assertThat(testOrganisation.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testOrganisation.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testOrganisation.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testOrganisation.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void patchNonExistingOrganisation() throws Exception {
        int databaseSizeBeforeUpdate = organisationRepository.findAll().size();
        organisation.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restOrganisationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, organisation.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(organisation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Organisation in the database
        List<Organisation> organisationList = organisationRepository.findAll();
        assertThat(organisationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchOrganisation() throws Exception {
        int databaseSizeBeforeUpdate = organisationRepository.findAll().size();
        organisation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restOrganisationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(organisation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Organisation in the database
        List<Organisation> organisationList = organisationRepository.findAll();
        assertThat(organisationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamOrganisation() throws Exception {
        int databaseSizeBeforeUpdate = organisationRepository.findAll().size();
        organisation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restOrganisationMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(organisation))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the Organisation in the database
        List<Organisation> organisationList = organisationRepository.findAll();
        assertThat(organisationList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteOrganisation() throws Exception {
        // Initialize the database
        organisationRepository.save(organisation);

        int databaseSizeBeforeDelete = organisationRepository.findAll().size();

        // Delete the organisation
        restOrganisationMockMvc
            .perform(delete(ENTITY_API_URL_ID, organisation.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<Organisation> organisationList = organisationRepository.findAll();
        assertThat(organisationList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
