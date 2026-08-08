package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every list endpoint is paginated, and says so in its headers.
 *
 * <p>Written as one parameterised sweep rather than a case per resource, because the failure this
 * guards is uniform and easy to reintroduce: a `List<T>` return type and a bare
 * `repository.findAll()` reads perfectly well and silently returns the entire collection. Nine of
 * these endpoints did exactly that, including the two that grow without bound.
 *
 * <p>Asserting the headers rather than the body size is deliberate — an empty test database returns
 * an empty page either way, so only `X-Total-Count` and `Link` distinguish a paginated endpoint
 * from an unpaginated one.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class PaginationIT {

    @ParameterizedTest
    @ValueSource(
        strings = {
            "/api/addresses",
            "/api/audit-logs",
            "/api/contacts",
            "/api/dashboards",
            "/api/document-items",
            "/api/duty-rosters",
            "/api/facilities",
            "/api/facility-catalogs",
            "/api/features",
            "/api/profiles",
            "/api/hc-profiles",
            "/api/hc-services",
            "/api/hc-subscriptions",
            "/api/h-professionals",
            "/api/messages",
            "/api/notifications",
            "/api/organisations",
            "/api/patient-plans",
            "/api/people",
            "/api/photos",
            "/api/pricing-plans",
            "/api/system-catalogs",
            "/api/teams",
        }
    )
    void listEndpointsArePaginated(String path) throws Exception {
        mvc
            .perform(get(path).param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(header().exists("Link"))
            .andExpect(jsonPath("$").isArray());
    }

    @Autowired
    private MockMvc mvc;
}
