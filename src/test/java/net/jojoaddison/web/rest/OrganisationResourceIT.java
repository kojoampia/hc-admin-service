package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.OrganisationAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Organisation;
import net.jojoaddison.repository.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
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

    private static final String DEFAULT_LEGAL_NAME = "AAAAAAAAAA";
    private static final String UPDATED_LEGAL_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_REGISTRATION_NUMBER = "AAAAAAAAAA";
    private static final String UPDATED_REGISTRATION_NUMBER = "BBBBBBBBBB";

    private static final String DEFAULT_TIN = "AAAAAAAAAA";
    private static final String UPDATED_TIN = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_FOUNDED_ON = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_FOUNDED_ON = LocalDate.now();

    private static final String DEFAULT_SWITCHBOARD = "AAAAAAAAAA";
    private static final String UPDATED_SWITCHBOARD = "BBBBBBBBBB";

    private static final String DEFAULT_EMAIL = "AAAAAAAAAA";
    private static final String UPDATED_EMAIL = "BBBBBBBBBB";

    private static final String DEFAULT_DESK_HOURS = "AAAAAAAAAA";
    private static final String UPDATED_DESK_HOURS = "BBBBBBBBBB";
    private static final String ENTITY_API_URL = "/api/organisations";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private OrganisationRepository organisationRepository;

    @Autowired
    private MockMvc restOrganisationMockMvc;

    private Organisation organisation;

    private Organisation insertedOrganisation;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Organisation createEntity() {
        return new Organisation()
            .name(DEFAULT_NAME)
            .legalName(DEFAULT_LEGAL_NAME)
            .description(DEFAULT_DESCRIPTION)
            .registrationNumber(DEFAULT_REGISTRATION_NUMBER)
            .tin(DEFAULT_TIN)
            .foundedOn(DEFAULT_FOUNDED_ON)
            .switchboard(DEFAULT_SWITCHBOARD)
            .email(DEFAULT_EMAIL)
            .deskHours(DEFAULT_DESK_HOURS);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Organisation createUpdatedEntity() {
        return new Organisation()
            .name(UPDATED_NAME)
            .legalName(UPDATED_LEGAL_NAME)
            .description(UPDATED_DESCRIPTION)
            .registrationNumber(UPDATED_REGISTRATION_NUMBER)
            .tin(UPDATED_TIN)
            .foundedOn(UPDATED_FOUNDED_ON)
            .switchboard(UPDATED_SWITCHBOARD)
            .email(UPDATED_EMAIL)
            .deskHours(UPDATED_DESK_HOURS);
    }

    @BeforeEach
    void initTest() {
        organisation = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedOrganisation != null) {
            organisationRepository.delete(insertedOrganisation);
            insertedOrganisation = null;
        }
    }

    @Test
    void createOrganisation() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Organisation
        var returnedOrganisation = om.readValue(
            restOrganisationMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(organisation)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            Organisation.class
        );

        // Validate the Organisation in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertOrganisationUpdatableFieldsEquals(returnedOrganisation, getPersistedOrganisation(returnedOrganisation));

        insertedOrganisation = returnedOrganisation;
    }

    @Test
    void createOrganisationWithExistingId() throws Exception {
        // Create the Organisation with an existing ID
        organisation.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restOrganisationMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(organisation)))
            .andExpect(status().isBadRequest());

        // Validate the Organisation in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        organisation.setName(null);

        // Create the Organisation, which fails.

        restOrganisationMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(organisation)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkLegalNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        organisation.setLegalName(null);

        // Create the Organisation, which fails.

        restOrganisationMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(organisation)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllOrganisations() throws Exception {
        // Initialize the database
        insertedOrganisation = organisationRepository.save(organisation);

        // Get all the organisationList
        restOrganisationMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(organisation.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].legalName").value(hasItem(DEFAULT_LEGAL_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].registrationNumber").value(hasItem(DEFAULT_REGISTRATION_NUMBER)))
            .andExpect(jsonPath("$.[*].tin").value(hasItem(DEFAULT_TIN)))
            .andExpect(jsonPath("$.[*].foundedOn").value(hasItem(DEFAULT_FOUNDED_ON.toString())))
            .andExpect(jsonPath("$.[*].switchboard").value(hasItem(DEFAULT_SWITCHBOARD)))
            .andExpect(jsonPath("$.[*].email").value(hasItem(DEFAULT_EMAIL)))
            .andExpect(jsonPath("$.[*].deskHours").value(hasItem(DEFAULT_DESK_HOURS)));
    }

    @Test
    void getOrganisation() throws Exception {
        // Initialize the database
        insertedOrganisation = organisationRepository.save(organisation);

        // Get the organisation
        restOrganisationMockMvc
            .perform(get(ENTITY_API_URL_ID, organisation.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(organisation.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.legalName").value(DEFAULT_LEGAL_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.registrationNumber").value(DEFAULT_REGISTRATION_NUMBER))
            .andExpect(jsonPath("$.tin").value(DEFAULT_TIN))
            .andExpect(jsonPath("$.foundedOn").value(DEFAULT_FOUNDED_ON.toString()))
            .andExpect(jsonPath("$.switchboard").value(DEFAULT_SWITCHBOARD))
            .andExpect(jsonPath("$.email").value(DEFAULT_EMAIL))
            .andExpect(jsonPath("$.deskHours").value(DEFAULT_DESK_HOURS));
    }

    @Test
    void getNonExistingOrganisation() throws Exception {
        // Get the organisation
        restOrganisationMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingOrganisation() throws Exception {
        // Initialize the database
        insertedOrganisation = organisationRepository.save(organisation);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the organisation
        Organisation updatedOrganisation = organisationRepository.findById(organisation.getId()).orElseThrow();
        updatedOrganisation
            .name(UPDATED_NAME)
            .legalName(UPDATED_LEGAL_NAME)
            .description(UPDATED_DESCRIPTION)
            .registrationNumber(UPDATED_REGISTRATION_NUMBER)
            .tin(UPDATED_TIN)
            .foundedOn(UPDATED_FOUNDED_ON)
            .switchboard(UPDATED_SWITCHBOARD)
            .email(UPDATED_EMAIL)
            .deskHours(UPDATED_DESK_HOURS);

        restOrganisationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedOrganisation.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedOrganisation))
            )
            .andExpect(status().isOk());

        // Validate the Organisation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedOrganisationToMatchAllProperties(updatedOrganisation);
    }

    @Test
    void putNonExistingOrganisation() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        organisation.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restOrganisationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, organisation.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(organisation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Organisation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchOrganisation() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        organisation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restOrganisationMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(organisation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Organisation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamOrganisation() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        organisation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restOrganisationMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(organisation)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Organisation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateOrganisationWithPatch() throws Exception {
        // Initialize the database
        insertedOrganisation = organisationRepository.save(organisation);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the organisation using partial update
        Organisation partialUpdatedOrganisation = new Organisation();
        partialUpdatedOrganisation.setId(organisation.getId());

        partialUpdatedOrganisation
            .name(UPDATED_NAME)
            .legalName(UPDATED_LEGAL_NAME)
            .description(UPDATED_DESCRIPTION)
            .registrationNumber(UPDATED_REGISTRATION_NUMBER);

        restOrganisationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedOrganisation.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedOrganisation))
            )
            .andExpect(status().isOk());

        // Validate the Organisation in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertOrganisationUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedOrganisation, organisation),
            getPersistedOrganisation(organisation)
        );
    }

    @Test
    void fullUpdateOrganisationWithPatch() throws Exception {
        // Initialize the database
        insertedOrganisation = organisationRepository.save(organisation);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the organisation using partial update
        Organisation partialUpdatedOrganisation = new Organisation();
        partialUpdatedOrganisation.setId(organisation.getId());

        partialUpdatedOrganisation
            .name(UPDATED_NAME)
            .legalName(UPDATED_LEGAL_NAME)
            .description(UPDATED_DESCRIPTION)
            .registrationNumber(UPDATED_REGISTRATION_NUMBER)
            .tin(UPDATED_TIN)
            .foundedOn(UPDATED_FOUNDED_ON)
            .switchboard(UPDATED_SWITCHBOARD)
            .email(UPDATED_EMAIL)
            .deskHours(UPDATED_DESK_HOURS);

        restOrganisationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedOrganisation.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedOrganisation))
            )
            .andExpect(status().isOk());

        // Validate the Organisation in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertOrganisationUpdatableFieldsEquals(partialUpdatedOrganisation, getPersistedOrganisation(partialUpdatedOrganisation));
    }

    @Test
    void patchNonExistingOrganisation() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        organisation.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restOrganisationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, organisation.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(organisation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Organisation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchOrganisation() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        organisation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restOrganisationMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(organisation))
            )
            .andExpect(status().isBadRequest());

        // Validate the Organisation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamOrganisation() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        organisation.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restOrganisationMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(organisation)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Organisation in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteOrganisation() throws Exception {
        // Initialize the database
        insertedOrganisation = organisationRepository.save(organisation);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the organisation
        restOrganisationMockMvc
            .perform(delete(ENTITY_API_URL_ID, organisation.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return organisationRepository.count();
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

    protected Organisation getPersistedOrganisation(Organisation organisation) {
        return organisationRepository.findById(organisation.getId()).orElseThrow();
    }

    protected void assertPersistedOrganisationToMatchAllProperties(Organisation expectedOrganisation) {
        assertOrganisationAllPropertiesEquals(expectedOrganisation, getPersistedOrganisation(expectedOrganisation));
    }

    protected void assertPersistedOrganisationToMatchUpdatableProperties(Organisation expectedOrganisation) {
        assertOrganisationAllUpdatablePropertiesEquals(expectedOrganisation, getPersistedOrganisation(expectedOrganisation));
    }
}
