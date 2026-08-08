package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.AuditEntryAsserts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.AuditEntry;
import net.jojoaddison.domain.enumeration.AuditLevel;
import net.jojoaddison.repository.AuditEntryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link AuditEntryResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class AuditEntryResourceIT {

    private static final Instant DEFAULT_OCCURRED_AT = Instant.ofEpochMilli(0L);
    private static final Instant UPDATED_OCCURRED_AT = Instant.ofEpochMilli(1711489506648L);

    private static final String DEFAULT_ACTOR = "AAAAAAAAAA";
    private static final String UPDATED_ACTOR = "BBBBBBBBBB";

    private static final String DEFAULT_ACTION = "AAAAAAAAAA";
    private static final String UPDATED_ACTION = "BBBBBBBBBB";

    private static final String DEFAULT_TARGET = "AAAAAAAAAA";
    private static final String UPDATED_TARGET = "BBBBBBBBBB";

    private static final AuditLevel DEFAULT_LEVEL = AuditLevel.INFO;
    private static final AuditLevel UPDATED_LEVEL = AuditLevel.WARN;

    private static final String ENTITY_API_URL = "/api/audit-entries";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private AuditEntryRepository auditEntryRepository;

    @Autowired
    private MockMvc restAuditEntryMockMvc;

    private AuditEntry auditEntry;

    private AuditEntry insertedAuditEntry;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static AuditEntry createEntity() {
        return new AuditEntry()
            .occurredAt(DEFAULT_OCCURRED_AT)
            .actor(DEFAULT_ACTOR)
            .action(DEFAULT_ACTION)
            .target(DEFAULT_TARGET)
            .level(DEFAULT_LEVEL);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static AuditEntry createUpdatedEntity() {
        return new AuditEntry()
            .occurredAt(UPDATED_OCCURRED_AT)
            .actor(UPDATED_ACTOR)
            .action(UPDATED_ACTION)
            .target(UPDATED_TARGET)
            .level(UPDATED_LEVEL);
    }

    @BeforeEach
    void initTest() {
        auditEntry = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedAuditEntry != null) {
            auditEntryRepository.delete(insertedAuditEntry);
            insertedAuditEntry = null;
        }
    }

    @Test
    void getAllAuditEntries() throws Exception {
        // Initialize the database
        insertedAuditEntry = auditEntryRepository.save(auditEntry);

        // Get all the auditEntryList
        restAuditEntryMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(auditEntry.getId())))
            .andExpect(jsonPath("$.[*].occurredAt").value(hasItem(DEFAULT_OCCURRED_AT.toString())))
            .andExpect(jsonPath("$.[*].actor").value(hasItem(DEFAULT_ACTOR)))
            .andExpect(jsonPath("$.[*].action").value(hasItem(DEFAULT_ACTION)))
            .andExpect(jsonPath("$.[*].target").value(hasItem(DEFAULT_TARGET)))
            .andExpect(jsonPath("$.[*].level").value(hasItem(DEFAULT_LEVEL.toString())));
    }

    @Test
    void getAuditEntry() throws Exception {
        // Initialize the database
        insertedAuditEntry = auditEntryRepository.save(auditEntry);

        // Get the auditEntry
        restAuditEntryMockMvc
            .perform(get(ENTITY_API_URL_ID, auditEntry.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(auditEntry.getId()))
            .andExpect(jsonPath("$.occurredAt").value(DEFAULT_OCCURRED_AT.toString()))
            .andExpect(jsonPath("$.actor").value(DEFAULT_ACTOR))
            .andExpect(jsonPath("$.action").value(DEFAULT_ACTION))
            .andExpect(jsonPath("$.target").value(DEFAULT_TARGET))
            .andExpect(jsonPath("$.level").value(DEFAULT_LEVEL.toString()));
    }

    @Test
    void getNonExistingAuditEntry() throws Exception {
        // Get the auditEntry
        restAuditEntryMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    protected long getRepositoryCount() {
        return auditEntryRepository.count();
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

    protected AuditEntry getPersistedAuditEntry(AuditEntry auditEntry) {
        return auditEntryRepository.findById(auditEntry.getId()).orElseThrow();
    }

    protected void assertPersistedAuditEntryToMatchAllProperties(AuditEntry expectedAuditEntry) {
        assertAuditEntryAllPropertiesEquals(expectedAuditEntry, getPersistedAuditEntry(expectedAuditEntry));
    }

    protected void assertPersistedAuditEntryToMatchUpdatableProperties(AuditEntry expectedAuditEntry) {
        assertAuditEntryAllUpdatablePropertiesEquals(expectedAuditEntry, getPersistedAuditEntry(expectedAuditEntry));
    }
}
