package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.PlatformService;
import net.jojoaddison.domain.enumeration.ServiceHealth;
import net.jojoaddison.repository.PlatformServiceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * Platform services can be written, not just read.
 *
 * <p>This resource was read-only, which made the console's platform-health map unfillable outside a
 * seed profile — and production seeds nothing, so it rendered an empty grid. The records now come
 * from {@code prod-server/sync-platform-services.sh} in hc-admin-ci, which reads the observability
 * stack every six hours and upserts through these two endpoints.
 *
 * <p>The tests are about the CRUD contract rather than the fields, because the contract is the part
 * a sync script depends on and the part that is easy to get subtly wrong: POST must refuse a body
 * that already carries an id, and PUT must refuse a mismatched or unknown one. A script that
 * silently created a duplicate on every run instead of updating would look like it worked.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class PlatformServiceWriteIT {

    private static final String ENDPOINT = "/api/platform-services";

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private PlatformServiceRepository platformServiceRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        platformServiceRepository.deleteAll();
    }

    private static PlatformService sample() {
        return new PlatformService()
            .name("Admin Gateway")
            .host("hc-admin-gateway")
            .port(5503)
            .plane("Admin")
            .health(ServiceHealth.HEALTHY)
            .responseMs(41);
    }

    @Test
    void createsAService() throws Exception {
        restMockMvc
            .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(sample())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.host").value("hc-admin-gateway"))
            .andExpect(jsonPath("$.health").value("HEALTHY"));

        assertThat(platformServiceRepository.findAll()).hasSize(1);
    }

    @Test
    void refusesToCreateSomethingThatAlreadyHasAnId() throws Exception {
        PlatformService withId = sample();
        withId.setId("svc-already-here");

        restMockMvc
            .perform(post(ENDPOINT).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(withId)))
            .andExpect(status().isBadRequest());

        assertThat(platformServiceRepository.findAll()).isEmpty();
    }

    /**
     * The one the sync script leans on: the second run must update, not accumulate.
     */
    @Test
    void updatesInPlaceRatherThanAccumulating() throws Exception {
        PlatformService stored = platformServiceRepository.save(sample());

        stored.setHealth(ServiceHealth.DEGRADED);
        stored.setResponseMs(870);

        restMockMvc
            .perform(
                put(ENDPOINT + "/" + stored.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(stored))
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.health").value("DEGRADED"))
            .andExpect(jsonPath("$.responseMs").value(870));

        assertThat(platformServiceRepository.findAll()).hasSize(1);
        assertThat(platformServiceRepository.findAll().getFirst().getHealth()).isEqualTo(ServiceHealth.DEGRADED);
    }

    @Test
    void refusesAnUpdateWhoseIdsDisagree() throws Exception {
        PlatformService stored = platformServiceRepository.save(sample());

        restMockMvc
            .perform(
                put(ENDPOINT + "/a-different-id").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(stored))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAnUpdateToSomethingThatDoesNotExist() throws Exception {
        PlatformService unknown = sample();
        unknown.setId("svc-never-stored");

        restMockMvc
            .perform(
                put(ENDPOINT + "/svc-never-stored").contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsBytes(unknown))
            )
            .andExpect(status().isBadRequest());
    }
}
