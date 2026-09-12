package net.jojoaddison.web.rest.errors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
     * The 400 <b>body</b> is unchanged, and this is the regression most likely to slip through: the
     * obvious way to write {@code ExceptionTranslator.buildInterpolationParams} normalises every
     * status, and 400 is the one branch the console has its own answer for. Watched red against
     * exactly that over-reach — with the status guard removed this case fails
     * {@code JSON path "$.params" expected:<directoryVendor> but was:<null>}, {@code null} rather
     * than the wrapped object because the scalar match reads through an object and finds nothing.
     *
     * <p><b>This case was called {@code testBadRequestPathIsUnchanged} until backlog item 91, and that
     * name became a lie the moment item 91 landed</b> — the 400 path <em>did</em> change, it gained
     * the failure-alert headers it should always have carried. The assertions did not need to move,
     * because what they were really pinning is narrower than the old name claimed: that
     * {@code buildInterpolationParams} leaves the 400 <b>body</b> alone. The name says that now.
     *
     * <p>The headers are asserted by {@link #testBadRequestAlertCarriesItsFailureAlertHeaders}, which
     * is where the old javadoc's "measured, and a defect in its own right" note went.
     */
    @Test
    void testBadRequestBodyParamsStaysBare() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/bad-request-alert"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.idexists"))
            .andExpect(jsonPath("$.params").value("directoryVendor"));
    }

    /**
     * A refused write reaches the console <b>with its failure-alert headers</b>. This is backlog item
     * 91, and it is asserted here rather than on the body because the body has been correct since the
     * class was generated — a body assertion would have passed against the defect.
     *
     * <p><b>What was broken.</b> {@link BadRequestAlertException} extends
     * {@link org.springframework.web.ErrorResponseException}, and
     * {@link org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler}
     * declares a handler for that which is <em>more specific</em> than this advice's
     * {@code @ExceptionHandler(Throwable)}. Spring dispatched there, answered with the exception's own
     * empty headers, and {@code ExceptionTranslator.handleAnyException} — and therefore
     * {@code buildHeaders} — was never entered. Measured on the quality stack on 2026-09-12: a real
     * {@code POST /services/hcadminservice/api/hubs} carrying an id came back {@code 400} with
     * {@code Content-Type: application/problem+json} and no {@code X-}-prefixed alert header at all,
     * through the gateway and straight at the api alike.
     *
     * <p><b>Watched red before the fix</b>, with exactly this case:
     * {@code Response header 'X-hcAdminServiceApp-error' expected:<error.idexists> but was:<null>}.
     *
     * <p><b>⚠ The header name is the api's, and the console does not read it.</b>
     * {@code jhipster.clientApp.name} here is {@code hcAdminServiceApp} — derived from this repo's
     * {@code baseName}, {@code hcAdminService} — so {@code HeaderUtil} emits
     * {@code X-hcAdminServiceApp-error}. {@code app/}'s {@code shared/jhipster/constants.ts} reads
     * {@code x-hcadminapp-error}, derived from <em>its</em> {@code baseName}, {@code hcAdmin}. The two
     * have never agreed, nothing anywhere pins either, and until that is settled restoring these
     * headers does not by itself make {@code error.idexists}'s {@code {{ entityName }}} resolve on
     * screen. Reported rather than fixed here: renaming either side is a cross-repo decision, and this
     * assertion deliberately pins <b>what this api emits</b> so that whichever side moves, the move is
     * a visible diff rather than a silent one.
     */
    @Test
    void testBadRequestAlertCarriesItsFailureAlertHeaders() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/bad-request-alert"))
            .andExpect(status().isBadRequest())
            .andExpect(header().string("X-hcAdminServiceApp-error", "error.idexists"))
            .andExpect(header().string("X-hcAdminServiceApp-params", "directoryVendor"));
    }
}
