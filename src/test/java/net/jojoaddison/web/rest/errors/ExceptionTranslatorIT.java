package net.jojoaddison.web.rest.errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Locale;
import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * Integration tests {@link ExceptionTranslator} controller advice.
 *
 * <p>One case here is not about the advice at all —
 * {@link #testSuccessAlertCarriesTheHeaderTheConsoleReads} drives a <em>successful</em> response.
 * It sits here because the alert headers on a success and on a refusal are named from one property,
 * so backlog item 95's two halves fail together and are worth reading together.
 */
@WithMockUser
@AutoConfigureMockMvc(addFilters = false)
@IntegrationTest
class ExceptionTranslatorIT {

    /**
     * The three alert headers this api emits, spelled out rather than derived — backlog item 95.
     *
     * <p>They are the exact strings {@code app/src/main/webapp/app/shared/jhipster/constants.ts}
     * reads as {@code MESSAGE_ALERT_HEADER_NAME}, {@code MESSAGE_ERROR_HEADER_NAME} and
     * {@code MESSAGE_PARAM_HEADER_NAME}, lower-cased there because HTTP header names are
     * case-insensitive and the browser hands them over that way.
     *
     * <p><b>Do not replace these with {@code "X-" + applicationName + "-error"}.</b> That is the
     * obvious tidy-up and it removes the entire guard: a test that names the header from the same
     * property the production code names it from passes under every value the property could hold,
     * including the one that broke this for the whole life of the repository.
     */
    private static final String SUCCESS_ALERT_HEADER = "X-hcAdminApp-alert";

    private static final String FAILURE_ERROR_HEADER = "X-hcAdminApp-error";

    private static final String FAILURE_PARAMS_HEADER = "X-hcAdminApp-params";

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
     * <p><b>The name changed under it in backlog item 95, and the assertions moved with it.</b> When
     * this case was written the api emitted {@code X-hcAdminServiceApp-*}, the gateway
     * {@code X-AdminGatewayApp-*} and the console read {@code x-hcadminapp-*} — three names, no two
     * matching, so these headers were restored and still reached nobody. Both services are
     * {@code hcAdminApp} now. This case's job is unchanged and is the reason item 95 could be a
     * visible diff rather than a silent one: it pins <b>what this api emits</b>.
     */
    @Test
    void testBadRequestAlertCarriesItsFailureAlertHeaders() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/bad-request-alert"))
            .andExpect(status().isBadRequest())
            .andExpect(headerIs(FAILURE_ERROR_HEADER, "error.idexists"))
            .andExpect(headerIs(FAILURE_PARAMS_HEADER, "directoryVendor"));
    }

    /**
     * A <b>successful</b> write's alert header carries the name the console reads too — the wider
     * half of backlog item 95, and the half that had nothing asserting it anywhere.
     *
     * <p>{@code notificationInterceptor} reads {@code MESSAGE_ALERT_HEADER_NAME} from the same three
     * constants as the error path, so for as long as the names disagreed <b>no create, update or
     * delete confirmation this api has ever sent was displayed by the console</b> — an operator
     * saving a record saw nothing at all. That is a larger surface than the error path and was
     * invisible from either side: a 201 with an unread header looks exactly like a 201.
     *
     * <p><b>Both the name and the value are pinned, because both are decided by the same string.</b>
     * {@code HeaderUtil.createEntityCreationAlert} puts {@code <clientApp.name>.<entity>.created} in
     * the header as a translation key, so {@code hcAdminApp} settles the message as well as the
     * header it arrives on: {@code app/src/main/webapp/i18n/en/platformHub.json} is rooted at
     * {@code hcAdminApp} — all 38 of its bundles are — and really does hold
     * {@code hcAdminApp.platformHub.created}. A header that arrived under a matching name carrying a
     * key from a different root would render as the raw key, which is item 86's defect one field
     * along.
     *
     * <p>The response comes from {@link ExceptionTranslatorTestController#successAlert()}, which
     * reproduces a generated resource's 201 rather than calling {@code HeaderUtil} from the test —
     * see its javadoc for why this fixture and not a {@code *ResourceIT}.
     */
    @Test
    void testSuccessAlertCarriesTheHeaderTheConsoleReads() throws Exception {
        mockMvc
            .perform(get("/api/exception-translator-test/success-alert"))
            .andExpect(status().isOk())
            .andExpect(headerIs(SUCCESS_ALERT_HEADER, "hcAdminApp.platformHub.created"));
    }

    /**
     * Asserts one header by its literal name, and says on failure what the console reads.
     *
     * <p><b>Literal, never derived.</b> Reading {@code jhipster.clientApp.name} here and building the
     * expected name from it would pass whatever that property said — the tautology this backlog keeps
     * closing. The names below are typed out so that changing the property turns these cases red.
     *
     * <p>The description is the point of the helper. The failure a future reader will see is a
     * regeneration having reverted {@code jhipster.clientApp.name} to {@code hcAdminServiceApp} from
     * {@code .yo-rc.json}'s {@code baseName}, and a bare "expected X-hcAdminApp-error but was null"
     * tells them a string changed without telling them which of the two sides is authoritative. It
     * is the console's, and it is named.
     */
    private static ResultMatcher headerIs(String name, String expected) {
        return result ->
            assertThat(result.getResponse().getHeader(name))
                .as(
                    "%s is the header app/src/main/webapp/app/shared/jhipster/constants.ts reads as %s. It is named from " +
                        "jhipster.clientApp.name in src/main/resources/config/application.yml, which must stay 'hcAdminApp' — " +
                        "a JHipster regeneration reverts it to 'hcAdminServiceApp' from .yo-rc.json's baseName and silently " +
                        "re-breaks every alert this api sends (backlog item 95). Re-point the property; do not edit this literal",
                    name,
                    name.toLowerCase(Locale.ROOT)
                )
                .isEqualTo(expected);
    }
}
