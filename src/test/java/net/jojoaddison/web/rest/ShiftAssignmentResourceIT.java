package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.ShiftAssignmentAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.RosterWeek;
import net.jojoaddison.domain.ShiftAssignment;
import net.jojoaddison.domain.enumeration.ShiftType;
import net.jojoaddison.repository.ShiftAssignmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link ShiftAssignmentResource} REST controller.
 */
@IntegrationTest
@ExtendWith(MockitoExtension.class)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class ShiftAssignmentResourceIT {

    private static final Integer DEFAULT_DAY_INDEX = 0;
    private static final Integer UPDATED_DAY_INDEX = 1;

    private static final LocalDate DEFAULT_SHIFT_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_SHIFT_DATE = LocalDate.parse("2024-03-26");

    private static final ShiftType DEFAULT_SHIFT = ShiftType.DAY;
    private static final ShiftType UPDATED_SHIFT = ShiftType.EVENING;

    private static final String ENTITY_API_URL = "/api/shift-assignments";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ShiftAssignmentRepository shiftAssignmentRepository;

    @Mock
    private ShiftAssignmentRepository shiftAssignmentRepositoryMock;

    @Autowired
    private MockMvc restShiftAssignmentMockMvc;

    private ShiftAssignment shiftAssignment;

    private ShiftAssignment insertedShiftAssignment;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ShiftAssignment createEntity() {
        ShiftAssignment shiftAssignment = new ShiftAssignment()
            .dayIndex(DEFAULT_DAY_INDEX)
            .shiftDate(DEFAULT_SHIFT_DATE)
            .shift(DEFAULT_SHIFT);
        // Add required entity
        RosterWeek rosterWeek;
        rosterWeek = RosterWeekResourceIT.createEntity();
        rosterWeek.setId("fixed-id-for-tests");
        shiftAssignment.setWeek(rosterWeek);
        // Add required entity
        Professional professional;
        professional = ProfessionalResourceIT.createEntity();
        professional.setId("fixed-id-for-tests");
        shiftAssignment.setProfessional(professional);
        return shiftAssignment;
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ShiftAssignment createUpdatedEntity() {
        ShiftAssignment updatedShiftAssignment = new ShiftAssignment()
            .dayIndex(UPDATED_DAY_INDEX)
            .shiftDate(UPDATED_SHIFT_DATE)
            .shift(UPDATED_SHIFT);
        // Add required entity
        RosterWeek rosterWeek;
        rosterWeek = RosterWeekResourceIT.createUpdatedEntity();
        rosterWeek.setId("fixed-id-for-tests");
        updatedShiftAssignment.setWeek(rosterWeek);
        // Add required entity
        Professional professional;
        professional = ProfessionalResourceIT.createUpdatedEntity();
        professional.setId("fixed-id-for-tests");
        updatedShiftAssignment.setProfessional(professional);
        return updatedShiftAssignment;
    }

    @BeforeEach
    void initTest() {
        shiftAssignment = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedShiftAssignment != null) {
            shiftAssignmentRepository.delete(insertedShiftAssignment);
            insertedShiftAssignment = null;
        }
    }

    @Test
    void createShiftAssignment() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ShiftAssignment
        var returnedShiftAssignment = om.readValue(
            restShiftAssignmentMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(shiftAssignment)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ShiftAssignment.class
        );

        // Validate the ShiftAssignment in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertShiftAssignmentUpdatableFieldsEquals(returnedShiftAssignment, getPersistedShiftAssignment(returnedShiftAssignment));

        insertedShiftAssignment = returnedShiftAssignment;
    }

    @Test
    void createShiftAssignmentWithExistingId() throws Exception {
        // Create the ShiftAssignment with an existing ID
        shiftAssignment.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restShiftAssignmentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(shiftAssignment)))
            .andExpect(status().isBadRequest());

        // Validate the ShiftAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkDayIndexIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        shiftAssignment.setDayIndex(null);

        // Create the ShiftAssignment, which fails.

        restShiftAssignmentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(shiftAssignment)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkShiftDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        shiftAssignment.setShiftDate(null);

        // Create the ShiftAssignment, which fails.

        restShiftAssignmentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(shiftAssignment)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkShiftIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        shiftAssignment.setShift(null);

        // Create the ShiftAssignment, which fails.

        restShiftAssignmentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(shiftAssignment)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllShiftAssignments() throws Exception {
        // Initialize the database
        insertedShiftAssignment = shiftAssignmentRepository.save(shiftAssignment);

        // Get all the shiftAssignmentList
        restShiftAssignmentMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(shiftAssignment.getId())))
            .andExpect(jsonPath("$.[*].dayIndex").value(hasItem(DEFAULT_DAY_INDEX)))
            .andExpect(jsonPath("$.[*].shiftDate").value(hasItem(DEFAULT_SHIFT_DATE.toString())))
            .andExpect(jsonPath("$.[*].shift").value(hasItem(DEFAULT_SHIFT.toString())));
    }

    @SuppressWarnings({ "unchecked" })
    void getAllShiftAssignmentsWithEagerRelationshipsIsEnabled() throws Exception {
        when(shiftAssignmentRepositoryMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restShiftAssignmentMockMvc.perform(get(ENTITY_API_URL + "?eagerload=true")).andExpect(status().isOk());

        verify(shiftAssignmentRepositoryMock, times(1)).findAllWithEagerRelationships(any());
    }

    @SuppressWarnings({ "unchecked" })
    void getAllShiftAssignmentsWithEagerRelationshipsIsNotEnabled() throws Exception {
        when(shiftAssignmentRepositoryMock.findAllWithEagerRelationships(any())).thenReturn(new PageImpl(new ArrayList<>()));

        restShiftAssignmentMockMvc.perform(get(ENTITY_API_URL + "?eagerload=false")).andExpect(status().isOk());
        verify(shiftAssignmentRepositoryMock, times(1)).findAll(any(Pageable.class));
    }

    @Test
    void getShiftAssignment() throws Exception {
        // Initialize the database
        insertedShiftAssignment = shiftAssignmentRepository.save(shiftAssignment);

        // Get the shiftAssignment
        restShiftAssignmentMockMvc
            .perform(get(ENTITY_API_URL_ID, shiftAssignment.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(shiftAssignment.getId()))
            .andExpect(jsonPath("$.dayIndex").value(DEFAULT_DAY_INDEX))
            .andExpect(jsonPath("$.shiftDate").value(DEFAULT_SHIFT_DATE.toString()))
            .andExpect(jsonPath("$.shift").value(DEFAULT_SHIFT.toString()));
    }

    @Test
    void getNonExistingShiftAssignment() throws Exception {
        // Get the shiftAssignment
        restShiftAssignmentMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingShiftAssignment() throws Exception {
        // Initialize the database
        insertedShiftAssignment = shiftAssignmentRepository.save(shiftAssignment);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the shiftAssignment
        ShiftAssignment updatedShiftAssignment = shiftAssignmentRepository.findById(shiftAssignment.getId()).orElseThrow();
        updatedShiftAssignment.dayIndex(UPDATED_DAY_INDEX).shiftDate(UPDATED_SHIFT_DATE).shift(UPDATED_SHIFT);

        restShiftAssignmentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedShiftAssignment.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedShiftAssignment))
            )
            .andExpect(status().isOk());

        // Validate the ShiftAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedShiftAssignmentToMatchAllProperties(updatedShiftAssignment);
    }

    @Test
    void putNonExistingShiftAssignment() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        shiftAssignment.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restShiftAssignmentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, shiftAssignment.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(shiftAssignment))
            )
            .andExpect(status().isBadRequest());

        // Validate the ShiftAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchShiftAssignment() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        shiftAssignment.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restShiftAssignmentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(shiftAssignment))
            )
            .andExpect(status().isBadRequest());

        // Validate the ShiftAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamShiftAssignment() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        shiftAssignment.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restShiftAssignmentMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(shiftAssignment)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ShiftAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateShiftAssignmentWithPatch() throws Exception {
        // Initialize the database
        insertedShiftAssignment = shiftAssignmentRepository.save(shiftAssignment);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the shiftAssignment using partial update
        ShiftAssignment partialUpdatedShiftAssignment = new ShiftAssignment();
        partialUpdatedShiftAssignment.setId(shiftAssignment.getId());

        partialUpdatedShiftAssignment.dayIndex(UPDATED_DAY_INDEX);

        restShiftAssignmentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedShiftAssignment.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedShiftAssignment))
            )
            .andExpect(status().isOk());

        // Validate the ShiftAssignment in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertShiftAssignmentUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedShiftAssignment, shiftAssignment),
            getPersistedShiftAssignment(shiftAssignment)
        );
    }

    @Test
    void fullUpdateShiftAssignmentWithPatch() throws Exception {
        // Initialize the database
        insertedShiftAssignment = shiftAssignmentRepository.save(shiftAssignment);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the shiftAssignment using partial update
        ShiftAssignment partialUpdatedShiftAssignment = new ShiftAssignment();
        partialUpdatedShiftAssignment.setId(shiftAssignment.getId());

        partialUpdatedShiftAssignment.dayIndex(UPDATED_DAY_INDEX).shiftDate(UPDATED_SHIFT_DATE).shift(UPDATED_SHIFT);

        restShiftAssignmentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedShiftAssignment.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedShiftAssignment))
            )
            .andExpect(status().isOk());

        // Validate the ShiftAssignment in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertShiftAssignmentUpdatableFieldsEquals(
            partialUpdatedShiftAssignment,
            getPersistedShiftAssignment(partialUpdatedShiftAssignment)
        );
    }

    @Test
    void patchNonExistingShiftAssignment() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        shiftAssignment.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restShiftAssignmentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, shiftAssignment.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(shiftAssignment))
            )
            .andExpect(status().isBadRequest());

        // Validate the ShiftAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchShiftAssignment() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        shiftAssignment.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restShiftAssignmentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(shiftAssignment))
            )
            .andExpect(status().isBadRequest());

        // Validate the ShiftAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamShiftAssignment() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        shiftAssignment.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restShiftAssignmentMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(shiftAssignment)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ShiftAssignment in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteShiftAssignment() throws Exception {
        // Initialize the database
        insertedShiftAssignment = shiftAssignmentRepository.save(shiftAssignment);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the shiftAssignment
        restShiftAssignmentMockMvc
            .perform(delete(ENTITY_API_URL_ID, shiftAssignment.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return shiftAssignmentRepository.count();
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

    protected ShiftAssignment getPersistedShiftAssignment(ShiftAssignment shiftAssignment) {
        return shiftAssignmentRepository.findById(shiftAssignment.getId()).orElseThrow();
    }

    protected void assertPersistedShiftAssignmentToMatchAllProperties(ShiftAssignment expectedShiftAssignment) {
        assertShiftAssignmentAllPropertiesEquals(expectedShiftAssignment, getPersistedShiftAssignment(expectedShiftAssignment));
    }

    protected void assertPersistedShiftAssignmentToMatchUpdatableProperties(ShiftAssignment expectedShiftAssignment) {
        assertShiftAssignmentAllUpdatablePropertiesEquals(expectedShiftAssignment, getPersistedShiftAssignment(expectedShiftAssignment));
    }
}
