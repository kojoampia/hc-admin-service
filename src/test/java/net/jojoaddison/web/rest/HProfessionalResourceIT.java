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
import net.jojoaddison.domain.HProfessional;
import net.jojoaddison.repository.HProfessionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link HProfessionalResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class HProfessionalResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_ORGANISATION = "AAAAAAAAAA";
    private static final String UPDATED_ORGANISATION = "BBBBBBBBBB";

    private static final String DEFAULT_ROSTER = "AAAAAAAAAA";
    private static final String UPDATED_ROSTER = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_MODIFIED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_MODIFIED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String DEFAULT_PROFILE = "AAAAAAAAAA";
    private static final String UPDATED_PROFILE = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/h-professionals";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private HProfessionalRepository hProfessionalRepository;

    @Autowired
    private MockMvc restHProfessionalMockMvc;

    private HProfessional hProfessional;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static HProfessional createEntity() {
        HProfessional hProfessional = new HProfessional()
            .name(DEFAULT_NAME)
            .organisation(DEFAULT_ORGANISATION)
            .roster(DEFAULT_ROSTER)
            .createdDate(DEFAULT_CREATED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .modifiedBy(DEFAULT_MODIFIED_BY)
            .profile(DEFAULT_PROFILE);
        return hProfessional;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static HProfessional createUpdatedEntity() {
        HProfessional hProfessional = new HProfessional()
            .name(UPDATED_NAME)
            .organisation(UPDATED_ORGANISATION)
            .roster(UPDATED_ROSTER)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY)
            .profile(UPDATED_PROFILE);
        return hProfessional;
    }

    @BeforeEach
    public void initTest() {
        hProfessionalRepository.deleteAll();
        hProfessional = createEntity();
    }

    @Test
    void createHProfessional() throws Exception {
        int databaseSizeBeforeCreate = hProfessionalRepository.findAll().size();
        // Create the HProfessional
        restHProfessionalMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(hProfessional)))
            .andExpect(status().isCreated());

        // Validate the HProfessional in the database
        List<HProfessional> hProfessionalList = hProfessionalRepository.findAll();
        assertThat(hProfessionalList).hasSize(databaseSizeBeforeCreate + 1);
        HProfessional testHProfessional = hProfessionalList.get(hProfessionalList.size() - 1);
        assertThat(testHProfessional.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(testHProfessional.getOrganisation()).isEqualTo(DEFAULT_ORGANISATION);
        assertThat(testHProfessional.getRoster()).isEqualTo(DEFAULT_ROSTER);
        assertThat(testHProfessional.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testHProfessional.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testHProfessional.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testHProfessional.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
        assertThat(testHProfessional.getProfile()).isEqualTo(DEFAULT_PROFILE);
    }

    @Test
    void createHProfessionalWithExistingId() throws Exception {
        // Create the HProfessional with an existing ID
        hProfessional.setId("existing_id");

        int databaseSizeBeforeCreate = hProfessionalRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restHProfessionalMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(hProfessional)))
            .andExpect(status().isBadRequest());

        // Validate the HProfessional in the database
        List<HProfessional> hProfessionalList = hProfessionalRepository.findAll();
        assertThat(hProfessionalList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllHProfessionals() throws Exception {
        // Initialize the database
        hProfessionalRepository.save(hProfessional);

        // Get all the hProfessionalList
        restHProfessionalMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(hProfessional.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].organisation").value(hasItem(DEFAULT_ORGANISATION)))
            .andExpect(jsonPath("$.[*].roster").value(hasItem(DEFAULT_ROSTER)))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)))
            .andExpect(jsonPath("$.[*].profile").value(hasItem(DEFAULT_PROFILE)));
    }

    @Test
    void getHProfessional() throws Exception {
        // Initialize the database
        hProfessionalRepository.save(hProfessional);

        // Get the hProfessional
        restHProfessionalMockMvc
            .perform(get(ENTITY_API_URL_ID, hProfessional.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(hProfessional.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.organisation").value(DEFAULT_ORGANISATION))
            .andExpect(jsonPath("$.roster").value(DEFAULT_ROSTER))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY))
            .andExpect(jsonPath("$.profile").value(DEFAULT_PROFILE));
    }

    @Test
    void getNonExistingHProfessional() throws Exception {
        // Get the hProfessional
        restHProfessionalMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingHProfessional() throws Exception {
        // Initialize the database
        hProfessionalRepository.save(hProfessional);

        int databaseSizeBeforeUpdate = hProfessionalRepository.findAll().size();

        // Update the hProfessional
        HProfessional updatedHProfessional = hProfessionalRepository.findById(hProfessional.getId()).orElseThrow();
        updatedHProfessional
            .name(UPDATED_NAME)
            .organisation(UPDATED_ORGANISATION)
            .roster(UPDATED_ROSTER)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY)
            .profile(UPDATED_PROFILE);

        restHProfessionalMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedHProfessional.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedHProfessional))
            )
            .andExpect(status().isOk());

        // Validate the HProfessional in the database
        List<HProfessional> hProfessionalList = hProfessionalRepository.findAll();
        assertThat(hProfessionalList).hasSize(databaseSizeBeforeUpdate);
        HProfessional testHProfessional = hProfessionalList.get(hProfessionalList.size() - 1);
        assertThat(testHProfessional.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testHProfessional.getOrganisation()).isEqualTo(UPDATED_ORGANISATION);
        assertThat(testHProfessional.getRoster()).isEqualTo(UPDATED_ROSTER);
        assertThat(testHProfessional.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testHProfessional.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testHProfessional.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testHProfessional.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
        assertThat(testHProfessional.getProfile()).isEqualTo(UPDATED_PROFILE);
    }

    @Test
    void putNonExistingHProfessional() throws Exception {
        int databaseSizeBeforeUpdate = hProfessionalRepository.findAll().size();
        hProfessional.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restHProfessionalMockMvc
            .perform(
                put(ENTITY_API_URL_ID, hProfessional.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(hProfessional))
            )
            .andExpect(status().isBadRequest());

        // Validate the HProfessional in the database
        List<HProfessional> hProfessionalList = hProfessionalRepository.findAll();
        assertThat(hProfessionalList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchHProfessional() throws Exception {
        int databaseSizeBeforeUpdate = hProfessionalRepository.findAll().size();
        hProfessional.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHProfessionalMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(hProfessional))
            )
            .andExpect(status().isBadRequest());

        // Validate the HProfessional in the database
        List<HProfessional> hProfessionalList = hProfessionalRepository.findAll();
        assertThat(hProfessionalList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamHProfessional() throws Exception {
        int databaseSizeBeforeUpdate = hProfessionalRepository.findAll().size();
        hProfessional.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHProfessionalMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(hProfessional)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the HProfessional in the database
        List<HProfessional> hProfessionalList = hProfessionalRepository.findAll();
        assertThat(hProfessionalList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateHProfessionalWithPatch() throws Exception {
        // Initialize the database
        hProfessionalRepository.save(hProfessional);

        int databaseSizeBeforeUpdate = hProfessionalRepository.findAll().size();

        // Update the hProfessional using partial update
        HProfessional partialUpdatedHProfessional = new HProfessional();
        partialUpdatedHProfessional.setId(hProfessional.getId());

        partialUpdatedHProfessional
            .name(UPDATED_NAME)
            .organisation(UPDATED_ORGANISATION)
            .createdDate(UPDATED_CREATED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY)
            .profile(UPDATED_PROFILE);

        restHProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedHProfessional.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedHProfessional))
            )
            .andExpect(status().isOk());

        // Validate the HProfessional in the database
        List<HProfessional> hProfessionalList = hProfessionalRepository.findAll();
        assertThat(hProfessionalList).hasSize(databaseSizeBeforeUpdate);
        HProfessional testHProfessional = hProfessionalList.get(hProfessionalList.size() - 1);
        assertThat(testHProfessional.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testHProfessional.getOrganisation()).isEqualTo(UPDATED_ORGANISATION);
        assertThat(testHProfessional.getRoster()).isEqualTo(DEFAULT_ROSTER);
        assertThat(testHProfessional.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testHProfessional.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testHProfessional.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testHProfessional.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
        assertThat(testHProfessional.getProfile()).isEqualTo(UPDATED_PROFILE);
    }

    @Test
    void fullUpdateHProfessionalWithPatch() throws Exception {
        // Initialize the database
        hProfessionalRepository.save(hProfessional);

        int databaseSizeBeforeUpdate = hProfessionalRepository.findAll().size();

        // Update the hProfessional using partial update
        HProfessional partialUpdatedHProfessional = new HProfessional();
        partialUpdatedHProfessional.setId(hProfessional.getId());

        partialUpdatedHProfessional
            .name(UPDATED_NAME)
            .organisation(UPDATED_ORGANISATION)
            .roster(UPDATED_ROSTER)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY)
            .profile(UPDATED_PROFILE);

        restHProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedHProfessional.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedHProfessional))
            )
            .andExpect(status().isOk());

        // Validate the HProfessional in the database
        List<HProfessional> hProfessionalList = hProfessionalRepository.findAll();
        assertThat(hProfessionalList).hasSize(databaseSizeBeforeUpdate);
        HProfessional testHProfessional = hProfessionalList.get(hProfessionalList.size() - 1);
        assertThat(testHProfessional.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testHProfessional.getOrganisation()).isEqualTo(UPDATED_ORGANISATION);
        assertThat(testHProfessional.getRoster()).isEqualTo(UPDATED_ROSTER);
        assertThat(testHProfessional.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testHProfessional.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testHProfessional.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testHProfessional.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
        assertThat(testHProfessional.getProfile()).isEqualTo(UPDATED_PROFILE);
    }

    @Test
    void patchNonExistingHProfessional() throws Exception {
        int databaseSizeBeforeUpdate = hProfessionalRepository.findAll().size();
        hProfessional.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restHProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, hProfessional.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(hProfessional))
            )
            .andExpect(status().isBadRequest());

        // Validate the HProfessional in the database
        List<HProfessional> hProfessionalList = hProfessionalRepository.findAll();
        assertThat(hProfessionalList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchHProfessional() throws Exception {
        int databaseSizeBeforeUpdate = hProfessionalRepository.findAll().size();
        hProfessional.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(hProfessional))
            )
            .andExpect(status().isBadRequest());

        // Validate the HProfessional in the database
        List<HProfessional> hProfessionalList = hProfessionalRepository.findAll();
        assertThat(hProfessionalList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamHProfessional() throws Exception {
        int databaseSizeBeforeUpdate = hProfessionalRepository.findAll().size();
        hProfessional.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHProfessionalMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(hProfessional))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the HProfessional in the database
        List<HProfessional> hProfessionalList = hProfessionalRepository.findAll();
        assertThat(hProfessionalList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteHProfessional() throws Exception {
        // Initialize the database
        hProfessionalRepository.save(hProfessional);

        int databaseSizeBeforeDelete = hProfessionalRepository.findAll().size();

        // Delete the hProfessional
        restHProfessionalMockMvc
            .perform(delete(ENTITY_API_URL_ID, hProfessional.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<HProfessional> hProfessionalList = hProfessionalRepository.findAll();
        assertThat(hProfessionalList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
