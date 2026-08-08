package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.RosterWeekAsserts.*;
import static net.jojoaddison.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.RosterWeek;
import net.jojoaddison.repository.RosterWeekRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link RosterWeekResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class RosterWeekResourceIT {

    private static final String DEFAULT_LABEL = "AAAAAAAAAA";
    private static final String UPDATED_LABEL = "BBBBBBBBBB";

    private static final LocalDate DEFAULT_START_DATE = LocalDate.ofEpochDay(0L);
    private static final LocalDate UPDATED_START_DATE = LocalDate.parse("2024-03-26");

    private static final Boolean DEFAULT_PUBLISHED = false;
    private static final Boolean UPDATED_PUBLISHED = true;

    private static final Instant DEFAULT_PUBLISHED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_PUBLISHED_AT = Instant.ofEpochMilli(1711489506648L);

    private static final String ENTITY_API_URL = "/api/roster-weeks";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private RosterWeekRepository rosterWeekRepository;

    @Autowired
    private MockMvc restRosterWeekMockMvc;

    private RosterWeek rosterWeek;

    private RosterWeek insertedRosterWeek;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static RosterWeek createEntity() {
        return new RosterWeek()
            .label(DEFAULT_LABEL)
            .startDate(DEFAULT_START_DATE)
            .published(DEFAULT_PUBLISHED)
            .publishedAt(DEFAULT_PUBLISHED_AT);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static RosterWeek createUpdatedEntity() {
        return new RosterWeek()
            .label(UPDATED_LABEL)
            .startDate(UPDATED_START_DATE)
            .published(UPDATED_PUBLISHED)
            .publishedAt(UPDATED_PUBLISHED_AT);
    }

    @BeforeEach
    void initTest() {
        rosterWeek = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedRosterWeek != null) {
            rosterWeekRepository.delete(insertedRosterWeek);
            insertedRosterWeek = null;
        }
    }

    @Test
    void createRosterWeek() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the RosterWeek
        var returnedRosterWeek = om.readValue(
            restRosterWeekMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(rosterWeek)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            RosterWeek.class
        );

        // Validate the RosterWeek in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        assertRosterWeekUpdatableFieldsEquals(returnedRosterWeek, getPersistedRosterWeek(returnedRosterWeek));

        insertedRosterWeek = returnedRosterWeek;
    }

    @Test
    void createRosterWeekWithExistingId() throws Exception {
        // Create the RosterWeek with an existing ID
        rosterWeek.setId("existing_id");

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restRosterWeekMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(rosterWeek)))
            .andExpect(status().isBadRequest());

        // Validate the RosterWeek in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    void checkLabelIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        rosterWeek.setLabel(null);

        // Create the RosterWeek, which fails.

        restRosterWeekMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(rosterWeek)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkStartDateIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        rosterWeek.setStartDate(null);

        // Create the RosterWeek, which fails.

        restRosterWeekMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(rosterWeek)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void checkPublishedIsRequired() throws Exception {
        long databaseSizeBeforeTest = getRepositoryCount();
        // set the field null
        rosterWeek.setPublished(null);

        // Create the RosterWeek, which fails.

        restRosterWeekMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(rosterWeek)))
            .andExpect(status().isBadRequest());

        assertSameRepositoryCount(databaseSizeBeforeTest);
    }

    @Test
    void getAllRosterWeeks() throws Exception {
        // Initialize the database
        insertedRosterWeek = rosterWeekRepository.save(rosterWeek);

        // Get all the rosterWeekList
        restRosterWeekMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(rosterWeek.getId())))
            .andExpect(jsonPath("$.[*].label").value(hasItem(DEFAULT_LABEL)))
            .andExpect(jsonPath("$.[*].startDate").value(hasItem(DEFAULT_START_DATE.toString())))
            .andExpect(jsonPath("$.[*].published").value(hasItem(DEFAULT_PUBLISHED)))
            .andExpect(jsonPath("$.[*].publishedAt").value(hasItem(DEFAULT_PUBLISHED_AT.toString())));
    }

    @Test
    void getRosterWeek() throws Exception {
        // Initialize the database
        insertedRosterWeek = rosterWeekRepository.save(rosterWeek);

        // Get the rosterWeek
        restRosterWeekMockMvc
            .perform(get(ENTITY_API_URL_ID, rosterWeek.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(rosterWeek.getId()))
            .andExpect(jsonPath("$.label").value(DEFAULT_LABEL))
            .andExpect(jsonPath("$.startDate").value(DEFAULT_START_DATE.toString()))
            .andExpect(jsonPath("$.published").value(DEFAULT_PUBLISHED))
            .andExpect(jsonPath("$.publishedAt").value(DEFAULT_PUBLISHED_AT.toString()));
    }

    @Test
    void getNonExistingRosterWeek() throws Exception {
        // Get the rosterWeek
        restRosterWeekMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void putExistingRosterWeek() throws Exception {
        // Initialize the database
        insertedRosterWeek = rosterWeekRepository.save(rosterWeek);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the rosterWeek
        RosterWeek updatedRosterWeek = rosterWeekRepository.findById(rosterWeek.getId()).orElseThrow();
        updatedRosterWeek.label(UPDATED_LABEL).startDate(UPDATED_START_DATE).published(UPDATED_PUBLISHED).publishedAt(UPDATED_PUBLISHED_AT);

        restRosterWeekMockMvc
            .perform(
                put(ENTITY_API_URL_ID, updatedRosterWeek.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(updatedRosterWeek))
            )
            .andExpect(status().isOk());

        // Validate the RosterWeek in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedRosterWeekToMatchAllProperties(updatedRosterWeek);
    }

    @Test
    void putNonExistingRosterWeek() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        rosterWeek.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restRosterWeekMockMvc
            .perform(
                put(ENTITY_API_URL_ID, rosterWeek.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(rosterWeek))
            )
            .andExpect(status().isBadRequest());

        // Validate the RosterWeek in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithIdMismatchRosterWeek() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        rosterWeek.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRosterWeekMockMvc
            .perform(
                put(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(rosterWeek))
            )
            .andExpect(status().isBadRequest());

        // Validate the RosterWeek in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void putWithMissingIdPathParamRosterWeek() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        rosterWeek.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRosterWeekMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(rosterWeek)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the RosterWeek in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void partialUpdateRosterWeekWithPatch() throws Exception {
        // Initialize the database
        insertedRosterWeek = rosterWeekRepository.save(rosterWeek);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the rosterWeek using partial update
        RosterWeek partialUpdatedRosterWeek = new RosterWeek();
        partialUpdatedRosterWeek.setId(rosterWeek.getId());

        partialUpdatedRosterWeek.startDate(UPDATED_START_DATE).published(UPDATED_PUBLISHED).publishedAt(UPDATED_PUBLISHED_AT);

        restRosterWeekMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedRosterWeek.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedRosterWeek))
            )
            .andExpect(status().isOk());

        // Validate the RosterWeek in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertRosterWeekUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedRosterWeek, rosterWeek),
            getPersistedRosterWeek(rosterWeek)
        );
    }

    @Test
    void fullUpdateRosterWeekWithPatch() throws Exception {
        // Initialize the database
        insertedRosterWeek = rosterWeekRepository.save(rosterWeek);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the rosterWeek using partial update
        RosterWeek partialUpdatedRosterWeek = new RosterWeek();
        partialUpdatedRosterWeek.setId(rosterWeek.getId());

        partialUpdatedRosterWeek
            .label(UPDATED_LABEL)
            .startDate(UPDATED_START_DATE)
            .published(UPDATED_PUBLISHED)
            .publishedAt(UPDATED_PUBLISHED_AT);

        restRosterWeekMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedRosterWeek.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedRosterWeek))
            )
            .andExpect(status().isOk());

        // Validate the RosterWeek in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertRosterWeekUpdatableFieldsEquals(partialUpdatedRosterWeek, getPersistedRosterWeek(partialUpdatedRosterWeek));
    }

    @Test
    void patchNonExistingRosterWeek() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        rosterWeek.setId(UUID.randomUUID().toString());

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restRosterWeekMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, rosterWeek.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(rosterWeek))
            )
            .andExpect(status().isBadRequest());

        // Validate the RosterWeek in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithIdMismatchRosterWeek() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        rosterWeek.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRosterWeekMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, UUID.randomUUID().toString())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(rosterWeek))
            )
            .andExpect(status().isBadRequest());

        // Validate the RosterWeek in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void patchWithMissingIdPathParamRosterWeek() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        rosterWeek.setId(UUID.randomUUID().toString());

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restRosterWeekMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(rosterWeek)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the RosterWeek in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    void deleteRosterWeek() throws Exception {
        // Initialize the database
        insertedRosterWeek = rosterWeekRepository.save(rosterWeek);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the rosterWeek
        restRosterWeekMockMvc
            .perform(delete(ENTITY_API_URL_ID, rosterWeek.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return rosterWeekRepository.count();
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

    protected RosterWeek getPersistedRosterWeek(RosterWeek rosterWeek) {
        return rosterWeekRepository.findById(rosterWeek.getId()).orElseThrow();
    }

    protected void assertPersistedRosterWeekToMatchAllProperties(RosterWeek expectedRosterWeek) {
        assertRosterWeekAllPropertiesEquals(expectedRosterWeek, getPersistedRosterWeek(expectedRosterWeek));
    }

    protected void assertPersistedRosterWeekToMatchUpdatableProperties(RosterWeek expectedRosterWeek) {
        assertRosterWeekAllUpdatablePropertiesEquals(expectedRosterWeek, getPersistedRosterWeek(expectedRosterWeek));
    }
}
