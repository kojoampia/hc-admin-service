package net.jojoaddison.web.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.security.AuthoritiesConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The authorization matrix for {@code /api/**}.
 *
 * <p>Every other {@code *ResourceIT} in this package runs with {@code addFilters = false}, which
 * takes the security filter chain out of the request path entirely — deliberately, so those tests
 * exercise the controller contract rather than re-testing authentication. The consequence is that
 * <em>none of them would notice</em> if the chain were opened up, which is how
 * {@code /api/** -> authenticated()} survived: any principal holding any authority, including a
 * self-registered {@code ROLE_USER}, could read and write the whole admin surface.
 *
 * <p>This class is the one that keeps filters on. It asserts the split enforced in
 * {@link net.jojoaddison.config.SecurityConfiguration}: admins write, operators read, a bare
 * {@code ROLE_USER} reaches nothing.
 *
 * <p>Authorities come from the {@code jwt()} post-processor rather than {@code @WithMockUser}. This
 * service is an OAuth2 resource server with {@code SessionCreationPolicy.STATELESS}, so Spring
 * Security installs a null {@code SecurityContextRepository} — which loads an empty context over
 * whatever {@code TestSecurityContextHolder} placed there, and every request comes back 401. The
 * post-processor puts the authorities on the request itself, where the bearer-token filter reads
 * them.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ApiAuthorizationIT {

    /** One representative path per shape of rule, not per resource — the rules are path-wide. */
    private static final String ENTITY_PATH = "/api/teams";

    @Autowired
    private MockMvc mvc;

    private static JwtRequestPostProcessor as(String... authorities) {
        return jwt().authorities(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toArray(GrantedAuthority[]::new));
    }

    // --- anonymous -------------------------------------------------------------------------------

    @Test
    void anonymousCannotReadTheAdminSurface() throws Exception {
        mvc.perform(get(ENTITY_PATH)).andExpect(status().isUnauthorized());
    }

    /**
     * 401 rather than 403: anonymous means "no credentials presented", and the bearer-token entry
     * point has to be what answers. A 403 here would mean an anonymous principal was being treated
     * as authenticated-but-unauthorized, which is how a permitAll rule hides in plain sight.
     *
     * <p>The permitAll actuator paths are not asserted here — the test context does not set
     * {@code management.endpoints.web.exposure.include}, so they 404 for reasons that have nothing
     * to do with this filter chain.
     */
    @Test
    void anonymousIsChallenged() throws Exception {
        mvc.perform(get(ENTITY_PATH)).andExpect(status().isUnauthorized());
        mvc.perform(post(ENTITY_PATH).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }

    // --- ROLE_USER: authenticated, and that is deliberately not enough -----------------------------

    @ParameterizedTest
    @ValueSource(
        strings = {
            "/api/teams",
            "/api/organisations",
            "/api/pricing-plans",
            "/api/audit-logs",
            "/api/hc-subscriptions",
            "/api/facilities",
            "/api/messages",
            "/api/notifications",
        }
    )
    void plainUserIsRefusedEverywhere(String path) throws Exception {
        mvc.perform(get(path).with(as(AuthoritiesConstants.USER))).andExpect(status().isForbidden());
    }

    @Test
    void plainUserCannotWrite() throws Exception {
        mvc
            .perform(post(ENTITY_PATH).with(as(AuthoritiesConstants.USER)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete(ENTITY_PATH + "/any-id").with(as(AuthoritiesConstants.USER))).andExpect(status().isForbidden());
    }

    // --- ROLE_OPERATOR: reads, never writes -------------------------------------------------------

    @Test
    void operatorCanRead() throws Exception {
        mvc.perform(get(ENTITY_PATH).with(as(AuthoritiesConstants.OPERATOR))).andExpect(status().isOk());
    }

    @Test
    void operatorCannotWrite() throws Exception {
        mvc
            .perform(post(ENTITY_PATH).with(as(AuthoritiesConstants.OPERATOR)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(delete(ENTITY_PATH + "/any-id").with(as(AuthoritiesConstants.OPERATOR))).andExpect(status().isForbidden());
    }

    /**
     * The operator account seeded by the gateway also holds {@code ROLE_USER} as a baseline. Asserted
     * because {@code hasAnyAuthority} is order-independent but a future {@code hasAuthority} chain
     * might not be, and the seeded account is the one that would break.
     */
    @Test
    void theBaselineUserAuthorityDoesNotDemoteAnOperator() throws Exception {
        mvc.perform(get(ENTITY_PATH).with(as(AuthoritiesConstants.OPERATOR, AuthoritiesConstants.USER))).andExpect(status().isOk());
    }

    /**
     * The probe endpoint, which is a write on a screen that is otherwise all reads.
     *
     * <p>Asserted separately from the CRUD paths above because it does not look like a write: it is
     * a button on a monitoring page called "re-run", and the argument for letting an operator press
     * it is a good one. It is a POST that stores health, response time and a timestamp, so the
     * read/write split covers it through the blanket rule and the answer is 403 — a 404 here would
     * mean the path had stopped existing and the assertion had stopped meaning anything.
     */
    @Test
    void operatorCannotProbeAPlatformService() throws Exception {
        mvc.perform(post("/api/platform-services/any-id/probe").with(as(AuthoritiesConstants.OPERATOR))).andExpect(status().isForbidden());
    }

    // --- ROLE_ADMIN: everything -------------------------------------------------------------------

    @Test
    void adminCanRead() throws Exception {
        mvc.perform(get(ENTITY_PATH).with(as(AuthoritiesConstants.ADMIN))).andExpect(status().isOk());
    }

    /**
     * Not asserting 2xx: an empty body is a 400 from the controller. The point is that it got past
     * the filter chain at all — a 401 or 403 here would mean admins cannot write.
     */
    @Test
    void adminReachesTheWriteHandlers() throws Exception {
        mvc
            .perform(post(ENTITY_PATH).with(as(AuthoritiesConstants.ADMIN)).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status == 401 || status == 403) {
                    throw new AssertionError("admin was refused at the filter chain, status " + status);
                }
            });
    }

    // --- the patient carve-out --------------------------------------------------------------------

    /**
     * {@code ROLE_PATIENT} is never issued by this stack's gateway; it arrives on tokens from
     * hc-patient-ms, which shares the signing key. It is honoured on exactly one path, and the rule
     * has to sit above the blanket ones or the chain rejects it before
     * {@code DutyRosterResource}'s narrower {@code @PreAuthorize} ever runs.
     */
    @Test
    void patientCanReadTheirOwnDailyPlan() throws Exception {
        mvc
            .perform(get("/api/duty-rosters/patient/some-profile-id").param("date", "2026-08-05").with(as(AuthoritiesConstants.PATIENT)))
            .andExpect(status().isOk());
    }

    @Test
    void patientReachesNothingElse() throws Exception {
        mvc.perform(get(ENTITY_PATH).with(as(AuthoritiesConstants.PATIENT))).andExpect(status().isForbidden());
        mvc.perform(get("/api/duty-rosters").with(as(AuthoritiesConstants.PATIENT))).andExpect(status().isForbidden());
    }
}
