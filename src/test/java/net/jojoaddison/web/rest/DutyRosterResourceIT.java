package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.DutyRosterAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.DutyRoster;
import net.jojoaddison.domain.enumeration.DutyRole;
import net.jojoaddison.domain.enumeration.ShiftType;
import net.jojoaddison.repository.DutyRosterRepository;
import net.jojoaddison.service.dto.DutyRosterDTO;
import net.jojoaddison.service.mapper.DutyRosterMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link DutyRosterResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class DutyRosterResourceIT {

    private static final LocalDate DEFAULT_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_DATE = LocalDate.now(ZoneId.systemDefault());

    private static final DutyRole DEFAULT_DUTY = DutyRole.CARE;
    private static final DutyRole UPDATED_DUTY = DutyRole.VENDOR;

    private static final String DEFAULT_PROFESSIONAL_ID = "AAAAAAAAAA";
    private static final String UPDATED_PROFESSIONAL_ID = "BBBBBBBBBB";

    private static final ShiftType DEFAULT_SHIFT = ShiftType.MORNING;
    private static final ShiftType UPDATED_SHIFT = ShiftType.AFTERNOON;

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_PATIENT_ID = "AAAAAAAAAA";
    private static final String UPDATED_PATIENT_ID = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/duty-rosters";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private final ObjectMapper om = TestUtil.createObjectMapper();

    @Autowired
    private DutyRosterRepository dutyRosterRepository;

    @Autowired
    private DutyRosterMapper dutyRosterMapper;

    @Autowired
    private MockMvc restDutyRosterMockMvc;

    private DutyRoster dutyRoster;

    private DutyRoster insertedDutyRoster;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static DutyRoster createEntity() {
        return new DutyRoster()
            .date(DEFAULT_DATE)
            .duty(DEFAULT_DUTY)
            .professionalId(DEFAULT_PROFESSIONAL_ID)
            .shift(DEFAULT_SHIFT)
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .patientId(DEFAULT_PATIENT_ID);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static DutyRoster createUpdatedEntity() {
        return new DutyRoster()
            .date(UPDATED_DATE)
            .duty(UPDATED_DUTY)
            .professionalId(UPDATED_PROFESSIONAL_ID)
            .shift(UPDATED_SHIFT)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .patientId(UPDATED_PATIENT_ID);
    }

    @BeforeEach
    void initTest() {
        dutyRoster = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedDutyRoster != null) {
            dutyRosterRepository.delete(insertedDutyRoster);
            insertedDutyRoster = null;
        }
    }

    @Test
    void createDutyRoster() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the DutyRoster
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);
        var returnedDutyRosterDTO = om.readValue(
            restDutyRosterMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dutyRosterDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            DutyRosterDTO.class
        );

        // Validate the DutyRoster in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedDutyRoster = dutyRosterMapper.toEntity(returnedDutyRosterDTO);
        assertDutyRosterUpdatableFieldsEquals(returnedDutyRoster, getPersistedDutyRoster(returnedDutyRoster));

        insertedDutyRoster = returnedDutyRoster;
    }

    @Test
    void createDutyRosterWithExistingId() throws Exception {
        // Create the DutyRoster with an existing ID
        dutyRoster.setId("existing_id");
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restDutyRosterMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dutyRosterDTO)))
            .andExpect(status().isBadRequest());

        // Validate the DutyRoster in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        dutyRoster.setDate(null);

        // Create the DutyRoster, which fails.
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);

        restDutyRosterMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dutyRosterDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkDutyIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        dutyRoster.setDuty(null);

        // Create the DutyRoster, which fails.
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);

        restDutyRosterMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dutyRosterDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkProfessionalIdIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        dutyRoster.setProfessionalId(null);

        // Create the DutyRoster, which fails.
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);

        restDutyRosterMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dutyRosterDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkShiftIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        dutyRoster.setShift(null);

        // Create the DutyRoster, which fails.
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);

        restDutyRosterMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dutyRosterDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkNameIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        dutyRoster.setName(null);

        // Create the DutyRoster, which fails.
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);

        restDutyRosterMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dutyRosterDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkPatientIdIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        dutyRoster.setPatientId(null);

        // Create the DutyRoster, which fails.
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);

        restDutyRosterMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dutyRosterDTO)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllDutyRosters() throws Exception {
        // Initialize the database
        insertedDutyRoster = dutyRosterRepository.save(dutyRoster);

        // Get all the dutyRosterList
        restDutyRosterMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dutyRoster.getId())))
            .andExpect(jsonPath("$.[*].date").value(hasItem(DEFAULT_DATE.toString())))
            .andExpect(jsonPath("$.[*].duty").value(hasItem(DEFAULT_DUTY.toString())))
            .andExpect(jsonPath("$.[*].professionalId").value(hasItem(DEFAULT_PROFESSIONAL_ID)))
            .andExpect(jsonPath("$.[*].shift").value(hasItem(DEFAULT_SHIFT.toString())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].patientId").value(hasItem(DEFAULT_PATIENT_ID)));
    }

    @Test
    void getDutyRoster() throws Exception {
        // Initialize the database
        insertedDutyRoster = dutyRosterRepository.save(dutyRoster);

        // Get the dutyRoster
        restDutyRosterMockMvc
            .perform(get(ENTITY_API_URL_ID, dutyRoster.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(dutyRoster.getId()))
            .andExpect(jsonPath("$.date").value(DEFAULT_DATE.toString()))
            .andExpect(jsonPath("$.duty").value(DEFAULT_DUTY.toString()))
            .andExpect(jsonPath("$.professionalId").value(DEFAULT_PROFESSIONAL_ID))
            .andExpect(jsonPath("$.shift").value(DEFAULT_SHIFT.toString()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.patientId").value(DEFAULT_PATIENT_ID));
    }

    @Test
    void getNonExistingDutyRoster() throws Exception {
        // Get the dutyRoster
        restDutyRosterMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingDutyRoster() throws Exception {
        // Initialize the database
        insertedDutyRoster = dutyRosterRepository.save(dutyRoster);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dutyRoster
        DutyRoster updatedDutyRoster = dutyRosterRepository.findById(dutyRoster.getId()).orElseThrow();
        updatedDutyRoster
            .date(UPDATED_DATE)
            .duty(UPDATED_DUTY)
            .professionalId(UPDATED_PROFESSIONAL_ID)
            .shift(UPDATED_SHIFT)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .patientId(UPDATED_PATIENT_ID);
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(updatedDutyRoster);

        restDutyRosterMockMvc
            .perform(
                put(ENTITY_API_URL_ID, dutyRosterDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(dutyRosterDTO))
            )
            .andExpect(status().isOk());

        // Validate the DutyRoster in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedDutyRosterToMatchAllProperties(updatedDutyRoster);
    }

    @Test
    void putNonExistingDutyRoster() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dutyRoster.setId(UUID.randomUUID().toString());

        // Create the DutyRoster
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restDutyRosterMockMvc
            .perform(
                put(ENTITY_API_URL_ID, dutyRosterDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(dutyRosterDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DutyRoster in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchDutyRoster() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dutyRoster.setId(UUID.randomUUID().toString());

        // Create the DutyRoster
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDutyRosterMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(dutyRosterDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DutyRoster in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamDutyRoster() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dutyRoster.setId(UUID.randomUUID().toString());

        // Create the DutyRoster
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDutyRosterMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dutyRosterDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the DutyRoster in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateDutyRosterWithPatch() throws Exception {
        // Initialize the database
        insertedDutyRoster = dutyRosterRepository.save(dutyRoster);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dutyRoster using partial update
        DutyRoster partialUpdatedDutyRoster = new DutyRoster();
        partialUpdatedDutyRoster.setId(dutyRoster.getId());

        partialUpdatedDutyRoster.shift(UPDATED_SHIFT).name(UPDATED_NAME).description(UPDATED_DESCRIPTION).patientId(UPDATED_PATIENT_ID);

        restDutyRosterMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDutyRoster.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedDutyRoster))
            )
            .andExpect(status().isOk());

        // Validate the DutyRoster in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertDutyRosterUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedDutyRoster, dutyRoster),
            getPersistedDutyRoster(dutyRoster)
        );
    }

    @Test
    void fullUpdateDutyRosterWithPatch() throws Exception {
        // Initialize the database
        insertedDutyRoster = dutyRosterRepository.save(dutyRoster);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dutyRoster using partial update
        DutyRoster partialUpdatedDutyRoster = new DutyRoster();
        partialUpdatedDutyRoster.setId(dutyRoster.getId());

        partialUpdatedDutyRoster
            .date(UPDATED_DATE)
            .duty(UPDATED_DUTY)
            .professionalId(UPDATED_PROFESSIONAL_ID)
            .shift(UPDATED_SHIFT)
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .patientId(UPDATED_PATIENT_ID);

        restDutyRosterMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDutyRoster.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedDutyRoster))
            )
            .andExpect(status().isOk());

        // Validate the DutyRoster in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertDutyRosterUpdatableFieldsEquals(partialUpdatedDutyRoster, getPersistedDutyRoster(partialUpdatedDutyRoster));
    }

    @Test
    void patchNonExistingDutyRoster() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dutyRoster.setId(UUID.randomUUID().toString());

        // Create the DutyRoster
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restDutyRosterMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, dutyRosterDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(dutyRosterDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DutyRoster in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchDutyRoster() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dutyRoster.setId(UUID.randomUUID().toString());

        // Create the DutyRoster
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDutyRosterMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(dutyRosterDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DutyRoster in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamDutyRoster() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dutyRoster.setId(UUID.randomUUID().toString());

        // Create the DutyRoster
        DutyRosterDTO dutyRosterDTO = dutyRosterMapper.toDto(dutyRoster);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDutyRosterMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(dutyRosterDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the DutyRoster in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteDutyRoster() throws Exception {
        // Initialize the database
        insertedDutyRoster = dutyRosterRepository.save(dutyRoster);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the dutyRoster
        restDutyRosterMockMvc
            .perform(delete(ENTITY_API_URL_ID, dutyRoster.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return dutyRosterRepository.count();
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

    protected DutyRoster getPersistedDutyRoster(DutyRoster dutyRoster) {
        return dutyRosterRepository.findById(dutyRoster.getId()).orElseThrow();
    }

    protected void assertPersistedDutyRosterToMatchAllProperties(DutyRoster expectedDutyRoster) {
        assertDutyRosterAllPropertiesEquals(expectedDutyRoster, getPersistedDutyRoster(expectedDutyRoster));
    }

    protected void assertPersistedDutyRosterToMatchUpdatableProperties(DutyRoster expectedDutyRoster) {
        assertDutyRosterAllUpdatablePropertiesEquals(expectedDutyRoster, getPersistedDutyRoster(expectedDutyRoster));
    }
}
