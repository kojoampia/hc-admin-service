package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every list endpoint is paginated, and says so in its headers.
 *
 * <p>Written as one sweep rather than a case per resource, because the failure this guards is
 * uniform and easy to reintroduce: a {@code List<T>} return type over a bare {@code findAll()} reads
 * perfectly well and silently returns the entire collection. Nine endpoints did exactly that until
 * 2026-08-05, including the two that grow without bound.
 *
 * <p><b>The list of paths is discovered from the application, not written down here.</b> It used to
 * be a literal array of 23, and that is how eight more unpaginated endpoints reached production
 * unnoticed: {@code angels}, {@code categories}, {@code hubs}, {@code plan-features},
 * {@code platform-services}, {@code roster-weeks}, {@code service-plans} and {@code user-options}
 * were all generated with the console entities <em>after</em> the sweep was written, so the guard
 * for this exact regression could not see them. A test whose coverage has to be extended by hand is
 * a test that silently stops covering things — the next entity would have been the ninth.
 *
 * <p>Asserting the headers rather than the body size is deliberate: an empty test database returns
 * an empty page either way, so only {@code X-Total-Count} and {@code Link} tell a paginated endpoint
 * from an unpaginated one.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PaginationIT {

    @Autowired
    private MockMvc mvc;

    // Qualified by name: actuator contributes a second RequestMappingHandlerMapping
    // (controllerEndpointHandlerMapping) and by type alone this is ambiguous.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    /**
     * Every {@code GET /api/<collection>} handled by a {@code *Resource} in this application.
     *
     * <p>Matched on the mapping rather than the method name: a handler is a collection endpoint if
     * its path is a single segment under {@code /api} with no path variable. That is the shape a
     * generated resource produces, and it is what a client pages through — {@code /api/x/{id}} and
     * the nested reads underneath it are excluded by the pattern itself, so nothing has to be
     * remembered when a resource grows another sub-path.
     */
    Stream<String> listEndpoints() {
        return handlerMapping
            .getHandlerMethods()
            .entrySet()
            .stream()
            .filter(entry -> entry.getValue().getBeanType().getName().startsWith("net.jojoaddison.web.rest."))
            .filter(entry -> entry.getKey().getMethodsCondition().getMethods().stream().anyMatch(m -> m.name().equals("GET")))
            .flatMap(entry -> patternsOf(entry).stream())
            .filter(path -> path.matches("/api/[a-z0-9-]+"))
            .distinct()
            .sorted();
    }

    private List<String> patternsOf(Map.Entry<org.springframework.web.servlet.mvc.method.RequestMappingInfo, HandlerMethod> entry) {
        var patterns = entry.getKey().getPathPatternsCondition();
        return patterns == null ? List.of() : patterns.getPatternValues().stream().toList();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("listEndpoints")
    void listEndpointsArePaginated(String path) throws Exception {
        mvc
            .perform(get(path).param("page", "0").param("size", "1"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Total-Count"))
            .andExpect(header().exists("Link"))
            .andExpect(jsonPath("$").isArray());
    }

    /**
     * The discovery itself, because a sweep that finds nothing passes.
     *
     * <p>A {@code @MethodSource} returning an empty stream is an error in JUnit, but a stream of two
     * is not — and a filter that quietly stopped matching would leave this looking green while
     * covering almost nothing. The floor is the count at the time of writing.
     */
    @Test
    void theSweepFindsEveryListEndpoint() {
        List<String> found = listEndpoints().toList();

        assertThat(found).hasSizeGreaterThanOrEqualTo(37);
        // The eight that the hand-written list missed. Named explicitly: they are the reason this
        // test discovers rather than enumerates, and pinning them keeps that concrete.
        assertThat(found)
            .contains(
                "/api/angels",
                "/api/categories",
                "/api/hubs",
                "/api/plan-features",
                "/api/platform-services",
                "/api/roster-weeks",
                "/api/service-plans",
                "/api/user-options"
            );
    }
}
