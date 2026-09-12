package net.jojoaddison.web.rest.errors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests {@link ExceptionTranslator} controller advice.
 */
@WithMockUser
@AutoConfigureMockMvc(addFilters = false)
@IntegrationTest
class ExceptionTranslatorIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testConcurrencyFailure() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/concurrency-failure"))
            .andExpect(status().isConflict())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_CONCURRENCY_FAILURE));
    }

    @Test
    void testMethodArgumentNotValid() throws Exception {
        mockMvc
            .perform(post("/api/exception-translator-test/method-argument").content("{}").contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_VALIDATION))
            .andExpect(jsonPath("$.fieldErrors.[0].objectName").value("test"))
            .andExpect(jsonPath("$.fieldErrors.[0].field").value("test"))
            .andExpect(jsonPath("$.fieldErrors.[0].message").value("must not be null"));
    }

    @Test
    void testMissingServletRequestPartException() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/missing-servlet-request-part"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    void testMissingServletRequestParameterException() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/missing-servlet-request-parameter"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    void testAccessDenied() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/access-denied"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.403"))
            .andExpect(jsonPath("$.detail").value("test access denied!"));
    }

    @Test
    void testUnauthorized() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/unauthorized"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.401"))
            .andExpect(jsonPath("$.path").value("/api/exception-translator-test/unauthorized"))
            .andExpect(jsonPath("$.detail").value("test authentication failed!"));
    }

    @Test
    void testMethodNotSupported() throws Exception {
        mockMvc
            .perform(post("/api/exception-translator-test/access-denied"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.405"))
            .andExpect(jsonPath("$.detail").value("Request method 'POST' is not supported"));
    }

    @Test
    void testExceptionWithResponseStatus() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/response-status"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.400"))
            .andExpect(jsonPath("$.title").value("test response status"));
    }

    @Test
    void testInternalServerError() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/internal-server-error"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.500"))
            .andExpect(jsonPath("$.title").value("Internal Server Error"));
    }

    /**
     * The default path — 409, 500, and everything else the console's {@code handleDefaultError}
     * takes — must put a parameter <b>map</b> on the wire. That branch hands {@code error.params}
     * straight to ngx-translate, and a bare string interpolates nothing.
     *
     * <p><b>Asserted on the serialised body, deliberately.</b> The distinction does not exist before
     * then: a {@code ProblemDetail} property is an {@code Object} whether it holds a String or a
     * Map, so an assertion on the translator would pass on exactly the value that renders
     * {@code {{ entityName }}} literally in the browser.
     */
    @Test
    void testDefaultPathCarriesParamsAsAMap() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/ambiguous-account"))
            .andExpect(status().isConflict())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.accountidambiguous"))
            .andExpect(jsonPath("$.params").isMap())
            .andExpect(jsonPath("$.params.entityName").value("directoryVendor"));
    }

    /**
     * A default-path error naming no params gains none. The map is built from what the exception
     * supplied, never invented — an empty object would tell a reader a placeholder was available.
     */
    @Test
    void testDefaultPathWithoutParamsGainsNone() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/concurrency-failure"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.params").doesNotExist());
    }

    /**
     * The 400 branch is unchanged, and this is the regression most likely to slip through: the
     * obvious way to write {@code ExceptionTranslator.buildInterpolationParams} normalises every
     * status, and 400 is the one branch the console has its own answer for. Watched red against
     * exactly that over-reach — with the status guard removed this case fails
     * {@code JSON path "$.params" expected:<directoryVendor> but was:<null>}, {@code null} rather
     * than the wrapped object because the scalar match reads through an object and finds nothing.
     *
     * <p><b>The headers are deliberately not asserted, in either direction.</b> This response
     * carries no {@code X-<app>-error} and no {@code X-<app>-params} — measured, and a defect in its
     * own right, because {@code ExceptionTranslator.buildHeaders} never runs for an
     * {@code ErrorResponseException} — but pinning an absence here would turn fixing that into a red
     * build on a file that has nothing to do with it. The body is what this change could move, so
     * the body is what is pinned.
     */
    @Test
    void testBadRequestPathIsUnchanged() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/bad-request-alert"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.idexists"))
            .andExpect(jsonPath("$.params").value("directoryVendor"));
    }
}
