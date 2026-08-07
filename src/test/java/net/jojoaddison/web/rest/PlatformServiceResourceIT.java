package net.jojoaddison.web.rest;

import static net.jojoaddison.domain.PlatformServiceAsserts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.PlatformService;
import net.jojoaddison.domain.enumeration.ServiceHealth;
import net.jojoaddison.repository.PlatformServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link PlatformServiceResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class PlatformServiceResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_HOST = "AAAAAAAAAA";
    private static final String UPDATED_HOST = "BBBBBBBBBB";

    private static final Integer DEFAULT_PORT = 1;
    private static final Integer UPDATED_PORT = 2;

    private static final String DEFAULT_PLANE = "AAAAAAAAAA";
    private static final String UPDATED_PLANE = "BBBBBBBBBB";

    private static final ServiceHealth DEFAULT_HEALTH = ServiceHealth.HEALTHY;
    private static final ServiceHealth UPDATED_HEALTH = ServiceHealth.DEGRADED;

    private static final Integer DEFAULT_RESPONSE_MS = 0;
    private static final Integer UPDATED_RESPONSE_MS = 1;

    private static final String ENTITY_API_URL = "/api/platform-services";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    @Autowired
    private ObjectMapper om;

    @Autowired
    private PlatformServiceRepository platformServiceRepository;

    @Autowired
    private MockMvc restPlatformServiceMockMvc;

    private PlatformService platformService;

    private PlatformService insertedPlatformService;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static PlatformService createEntity() {
        return new PlatformService()
            .name(DEFAULT_NAME)
            .host(DEFAULT_HOST)
            .port(DEFAULT_PORT)
            .plane(DEFAULT_PLANE)
            .health(DEFAULT_HEALTH)
            .responseMs(DEFAULT_RESPONSE_MS);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static PlatformService createUpdatedEntity() {
        return new PlatformService()
            .name(UPDATED_NAME)
            .host(UPDATED_HOST)
            .port(UPDATED_PORT)
            .plane(UPDATED_PLANE)
            .health(UPDATED_HEALTH)
            .responseMs(UPDATED_RESPONSE_MS);
    }

    @BeforeEach
    void initTest() {
        platformService = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedPlatformService != null) {
            platformServiceRepository.delete(insertedPlatformService);
            insertedPlatformService = null;
        }
    }

    @Test
    void getAllPlatformServices() throws Exception {
        // Initialize the database
        insertedPlatformService = platformServiceRepository.save(platformService);

        // Get all the platformServiceList
        restPlatformServiceMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(platformService.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].host").value(hasItem(DEFAULT_HOST)))
            .andExpect(jsonPath("$.[*].port").value(hasItem(DEFAULT_PORT)))
            .andExpect(jsonPath("$.[*].plane").value(hasItem(DEFAULT_PLANE)))
            .andExpect(jsonPath("$.[*].health").value(hasItem(DEFAULT_HEALTH.toString())))
            .andExpect(jsonPath("$.[*].responseMs").value(hasItem(DEFAULT_RESPONSE_MS)));
    }

    @Test
    void getPlatformService() throws Exception {
        // Initialize the database
        insertedPlatformService = platformServiceRepository.save(platformService);

        // Get the platformService
        restPlatformServiceMockMvc
            .perform(get(ENTITY_API_URL_ID, platformService.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(platformService.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.host").value(DEFAULT_HOST))
            .andExpect(jsonPath("$.port").value(DEFAULT_PORT))
            .andExpect(jsonPath("$.plane").value(DEFAULT_PLANE))
            .andExpect(jsonPath("$.health").value(DEFAULT_HEALTH.toString()))
            .andExpect(jsonPath("$.responseMs").value(DEFAULT_RESPONSE_MS));
    }

    @Test
    void getNonExistingPlatformService() throws Exception {
        // Get the platformService
        restPlatformServiceMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    protected long getRepositoryCount() {
        return platformServiceRepository.count();
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

    protected PlatformService getPersistedPlatformService(PlatformService platformService) {
        return platformServiceRepository.findById(platformService.getId()).orElseThrow();
    }

    protected void assertPersistedPlatformServiceToMatchAllProperties(PlatformService expectedPlatformService) {
        assertPlatformServiceAllPropertiesEquals(expectedPlatformService, getPersistedPlatformService(expectedPlatformService));
    }

    protected void assertPersistedPlatformServiceToMatchUpdatableProperties(PlatformService expectedPlatformService) {
        assertPlatformServiceAllUpdatablePropertiesEquals(expectedPlatformService, getPersistedPlatformService(expectedPlatformService));
    }
}
