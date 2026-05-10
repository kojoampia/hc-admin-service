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
import net.jojoaddison.domain.HCService;
import net.jojoaddison.repository.HCServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link HCServiceResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class HCServiceResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_SERVICE_ITEMS = "AAAAAAAAAA";
    private static final String UPDATED_SERVICE_ITEMS = "BBBBBBBBBB";

    private static final Double DEFAULT_AMOUNT = 1D;
    private static final Double UPDATED_AMOUNT = 2D;

    private static final LocalDate DEFAULT_CREATED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_CREATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_CREATED_BY = "AAAAAAAAAA";
    private static final String UPDATED_CREATED_BY = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_MODIFIED_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_MODIFIED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final String DEFAULT_MODIFIED_BY = "AAAAAAAAAA";
    private static final String UPDATED_MODIFIED_BY = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/hc-services";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private HCServiceRepository hCServiceRepository;

    @Autowired
    private MockMvc restHCServiceMockMvc;

    private HCService hCService;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static HCService createEntity() {
        HCService hCService = new HCService()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .serviceItems(DEFAULT_SERVICE_ITEMS)
            .amount(DEFAULT_AMOUNT)
            .createdDate(DEFAULT_CREATED_DATE)
            .createdBy(DEFAULT_CREATED_BY)
            .modifiedDate(DEFAULT_MODIFIED_DATE)
            .modifiedBy(DEFAULT_MODIFIED_BY);
        return hCService;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static HCService createUpdatedEntity() {
        HCService hCService = new HCService()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .serviceItems(UPDATED_SERVICE_ITEMS)
            .amount(UPDATED_AMOUNT)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY);
        return hCService;
    }

    @BeforeEach
    public void initTest() {
        hCServiceRepository.deleteAll();
        hCService = createEntity();
    }

    @Test
    void createHCService() throws Exception {
        int databaseSizeBeforeCreate = hCServiceRepository.findAll().size();
        // Create the HCService
        restHCServiceMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(hCService)))
            .andExpect(status().isCreated());

        // Validate the HCService in the database
        List<HCService> hCServiceList = hCServiceRepository.findAll();
        assertThat(hCServiceList).hasSize(databaseSizeBeforeCreate + 1);
        HCService testHCService = hCServiceList.get(hCServiceList.size() - 1);
        assertThat(testHCService.getName()).isEqualTo(DEFAULT_NAME);
        assertThat(testHCService.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(testHCService.getServiceItems()).isEqualTo(DEFAULT_SERVICE_ITEMS);
        assertThat(testHCService.getAmount()).isEqualTo(DEFAULT_AMOUNT);
        assertThat(testHCService.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testHCService.getCreatedBy()).isEqualTo(DEFAULT_CREATED_BY);
        assertThat(testHCService.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testHCService.getModifiedBy()).isEqualTo(DEFAULT_MODIFIED_BY);
    }

    @Test
    void createHCServiceWithExistingId() throws Exception {
        // Create the HCService with an existing ID
        hCService.setId("existing_id");

        int databaseSizeBeforeCreate = hCServiceRepository.findAll().size();

        // An entity with an existing ID cannot be created, so this API call must fail
        restHCServiceMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(hCService)))
            .andExpect(status().isBadRequest());

        // Validate the HCService in the database
        List<HCService> hCServiceList = hCServiceRepository.findAll();
        assertThat(hCServiceList).hasSize(databaseSizeBeforeCreate);
    }

    @Test
    void getAllHCServices() throws Exception {
        // Initialize the database
        hCServiceRepository.save(hCService);

        // Get all the hCServiceList
        restHCServiceMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(hCService.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].serviceItems").value(hasItem(DEFAULT_SERVICE_ITEMS)))
            .andExpect(jsonPath("$.[*].amount").value(hasItem(DEFAULT_AMOUNT.doubleValue())))
            .andExpect(jsonPath("$.[*].createdDate").value(hasItem(DEFAULT_CREATED_DATE.toString())))
            .andExpect(jsonPath("$.[*].createdBy").value(hasItem(DEFAULT_CREATED_BY)))
            .andExpect(jsonPath("$.[*].modifiedDate").value(hasItem(DEFAULT_MODIFIED_DATE.toString())))
            .andExpect(jsonPath("$.[*].modifiedBy").value(hasItem(DEFAULT_MODIFIED_BY)));
    }

    @Test
    void getHCService() throws Exception {
        // Initialize the database
        hCServiceRepository.save(hCService);

        // Get the hCService
        restHCServiceMockMvc
            .perform(get(ENTITY_API_URL_ID, hCService.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(hCService.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.serviceItems").value(DEFAULT_SERVICE_ITEMS))
            .andExpect(jsonPath("$.amount").value(DEFAULT_AMOUNT.doubleValue()))
            .andExpect(jsonPath("$.createdDate").value(DEFAULT_CREATED_DATE.toString()))
            .andExpect(jsonPath("$.createdBy").value(DEFAULT_CREATED_BY))
            .andExpect(jsonPath("$.modifiedDate").value(DEFAULT_MODIFIED_DATE.toString()))
            .andExpect(jsonPath("$.modifiedBy").value(DEFAULT_MODIFIED_BY));
    }

    @Test
    void getNonExistingHCService() throws Exception {
        // Get the hCService
        restHCServiceMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingHCService() throws Exception {
        // Initialize the database
        hCServiceRepository.save(hCService);

        int databaseSizeBeforeUpdate = hCServiceRepository.findAll().size();

        // Update the hCService
        HCService updatedHCService = hCServiceRepository.findById(hCService.getId()).orElseThrow();
        updatedHCService
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .serviceItems(UPDATED_SERVICE_ITEMS)
            .amount(UPDATED_AMOUNT)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restHCServiceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedHCService.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(updatedHCService))
            )
            .andExpect(status().isOk());

        // Validate the HCService in the database
        List<HCService> hCServiceList = hCServiceRepository.findAll();
        assertThat(hCServiceList).hasSize(databaseSizeBeforeUpdate);
        HCService testHCService = hCServiceList.get(hCServiceList.size() - 1);
        assertThat(testHCService.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testHCService.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testHCService.getServiceItems()).isEqualTo(UPDATED_SERVICE_ITEMS);
        assertThat(testHCService.getAmount()).isEqualTo(UPDATED_AMOUNT);
        assertThat(testHCService.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testHCService.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testHCService.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testHCService.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void putNonExistingHCService() throws Exception {
        int databaseSizeBeforeUpdate = hCServiceRepository.findAll().size();
        hCService.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restHCServiceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, hCService.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(hCService))
            )
            .andExpect(status().isBadRequest());

        // Validate the HCService in the database
        List<HCService> hCServiceList = hCServiceRepository.findAll();
        assertThat(hCServiceList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchHCService() throws Exception {
        int databaseSizeBeforeUpdate = hCServiceRepository.findAll().size();
        hCService.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHCServiceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TestUtil.convertObjectToJsonBytes(hCService))
            )
            .andExpect(status().isBadRequest());

        // Validate the HCService in the database
        List<HCService> hCServiceList = hCServiceRepository.findAll();
        assertThat(hCServiceList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamHCService() throws Exception {
        int databaseSizeBeforeUpdate = hCServiceRepository.findAll().size();
        hCService.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHCServiceMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(TestUtil.convertObjectToJsonBytes(hCService)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the HCService in the database
        List<HCService> hCServiceList = hCServiceRepository.findAll();
        assertThat(hCServiceList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateHCServiceWithPatch() throws Exception {
        // Initialize the database
        hCServiceRepository.save(hCService);

        int databaseSizeBeforeUpdate = hCServiceRepository.findAll().size();

        // Update the hCService using partial update
        HCService partialUpdatedHCService = new HCService();
        partialUpdatedHCService.setId(hCService.getId());

        partialUpdatedHCService.name(UPDATED_NAME).amount(UPDATED_AMOUNT).createdBy(UPDATED_CREATED_BY).modifiedBy(UPDATED_MODIFIED_BY);

        restHCServiceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedHCService.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedHCService))
            )
            .andExpect(status().isOk());

        // Validate the HCService in the database
        List<HCService> hCServiceList = hCServiceRepository.findAll();
        assertThat(hCServiceList).hasSize(databaseSizeBeforeUpdate);
        HCService testHCService = hCServiceList.get(hCServiceList.size() - 1);
        assertThat(testHCService.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testHCService.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(testHCService.getServiceItems()).isEqualTo(DEFAULT_SERVICE_ITEMS);
        assertThat(testHCService.getAmount()).isEqualTo(UPDATED_AMOUNT);
        assertThat(testHCService.getCreatedDate()).isEqualTo(DEFAULT_CREATED_DATE);
        assertThat(testHCService.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testHCService.getModifiedDate()).isEqualTo(DEFAULT_MODIFIED_DATE);
        assertThat(testHCService.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void fullUpdateHCServiceWithPatch() throws Exception {
        // Initialize the database
        hCServiceRepository.save(hCService);

        int databaseSizeBeforeUpdate = hCServiceRepository.findAll().size();

        // Update the hCService using partial update
        HCService partialUpdatedHCService = new HCService();
        partialUpdatedHCService.setId(hCService.getId());

        partialUpdatedHCService
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .serviceItems(UPDATED_SERVICE_ITEMS)
            .amount(UPDATED_AMOUNT)
            .createdDate(UPDATED_CREATED_DATE)
            .createdBy(UPDATED_CREATED_BY)
            .modifiedDate(UPDATED_MODIFIED_DATE)
            .modifiedBy(UPDATED_MODIFIED_BY);

        restHCServiceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedHCService.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(partialUpdatedHCService))
            )
            .andExpect(status().isOk());

        // Validate the HCService in the database
        List<HCService> hCServiceList = hCServiceRepository.findAll();
        assertThat(hCServiceList).hasSize(databaseSizeBeforeUpdate);
        HCService testHCService = hCServiceList.get(hCServiceList.size() - 1);
        assertThat(testHCService.getName()).isEqualTo(UPDATED_NAME);
        assertThat(testHCService.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testHCService.getServiceItems()).isEqualTo(UPDATED_SERVICE_ITEMS);
        assertThat(testHCService.getAmount()).isEqualTo(UPDATED_AMOUNT);
        assertThat(testHCService.getCreatedDate()).isEqualTo(UPDATED_CREATED_DATE);
        assertThat(testHCService.getCreatedBy()).isEqualTo(UPDATED_CREATED_BY);
        assertThat(testHCService.getModifiedDate()).isEqualTo(UPDATED_MODIFIED_DATE);
        assertThat(testHCService.getModifiedBy()).isEqualTo(UPDATED_MODIFIED_BY);
    }

    @Test
    void patchNonExistingHCService() throws Exception {
        int databaseSizeBeforeUpdate = hCServiceRepository.findAll().size();
        hCService.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restHCServiceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, hCService.getId())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(hCService))
            )
            .andExpect(status().isBadRequest());

        // Validate the HCService in the database
        List<HCService> hCServiceList = hCServiceRepository.findAll();
        assertThat(hCServiceList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchHCService() throws Exception {
        int databaseSizeBeforeUpdate = hCServiceRepository.findAll().size();
        hCService.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHCServiceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(TestUtil.convertObjectToJsonBytes(hCService))
            )
            .andExpect(status().isBadRequest());

        // Validate the HCService in the database
        List<HCService> hCServiceList = hCServiceRepository.findAll();
        assertThat(hCServiceList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamHCService() throws Exception {
        int databaseSizeBeforeUpdate = hCServiceRepository.findAll().size();
        hCService.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restHCServiceMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(TestUtil.convertObjectToJsonBytes(hCService))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the HCService in the database
        List<HCService> hCServiceList = hCServiceRepository.findAll();
        assertThat(hCServiceList).hasSize(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteHCService() throws Exception {
        // Initialize the database
        hCServiceRepository.save(hCService);

        int databaseSizeBeforeDelete = hCServiceRepository.findAll().size();

        // Delete the hCService
        restHCServiceMockMvc
            .perform(delete(ENTITY_API_URL_ID, hCService.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        List<HCService> hCServiceList = hCServiceRepository.findAll();
        assertThat(hCServiceList).hasSize(databaseSizeBeforeDelete - 1);
    }
}
