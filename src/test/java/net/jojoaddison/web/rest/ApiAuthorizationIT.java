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

    // --- bulk export: the one place an operator's read stops ---------------------------------------

    /**
     * An operator may page through the patient directory and may not download it.
     *
     * <p>This is the only {@code GET} under {@code /api} an operator is refused, so it is the one
     * assertion standing between the decision and a matcher that gets moved below the blanket read
     * rule during some later tidy-up — at which point every operator silently gains the ability to
     * extract the whole directory, with nothing failing.
     *
     * <p>403 and not 404: a 404 would mean the path had stopped existing, and this test would go on
     * passing while asserting nothing at all.
     */
    @Test
    void operatorCannotExportThePatientDirectory() throws Exception {
        mvc.perform(get("/api/patients/export").with(as(AuthoritiesConstants.OPERATOR))).andExpect(status().isForbidden());
    }

    @Test
    void plainUserCannotExportThePatientDirectory() throws Exception {
        mvc.perform(get("/api/patients/export").with(as(AuthoritiesConstants.USER))).andExpect(status().isForbidden());
    }

    /**
     * And the admin does reach it — asserted so that "operator is refused" cannot be satisfied by
     * the endpoint being unreachable for everyone.
     */
    @Test
    void adminCanExportThePatientDirectory() throws Exception {
        mvc.perform(get("/api/patients/export").with(as(AuthoritiesConstants.ADMIN))).andExpect(status().isOk());
    }

    /**
     * The list is untouched by the export rule: an operator still reads the directory a page at a
     * time. Without this, narrowing {@code /api/patients/**} to admins by mistake would look
     * exactly like the intended change.
     */
    @Test
    void operatorStillReadsThePatientList() throws Exception {
        mvc.perform(get("/api/patients").with(as(AuthoritiesConstants.OPERATOR))).andExpect(status().isOk());
    }

    /**
     * Recording a verification is a write, so it is the administrator's.
     *
     * <p>Covered by the blanket rule rather than a matcher of its own, and asserted anyway because
     * it does not look like a write from the console: it is a button on a record screen an operator
     * can otherwise read in full. Deciding that somebody is credentialed to work on patients is not
     * a read.
     */
    @Test
    void operatorCannotRecordAVerification() throws Exception {
        mvc
            .perform(
                post("/api/professional-verifications")
                    .with(as(AuthoritiesConstants.OPERATOR))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isForbidden());
    }

    /** They can still read the history, like everything else under GET. */
    @Test
    void operatorCanReadTheVerificationHistory() throws Exception {
        mvc.perform(get("/api/professional-verifications").with(as(AuthoritiesConstants.OPERATOR))).andExpect(status().isOk());
    }

    // --- geographic spaces: reference data, readable by anybody who is signed in --------------------

    /**
     * <b>This is the assertion the endpoint exists for.</b>
     *
     * <p>hc-professional stores a geographic space id on a roster round and has to render a name
     * beside it. Its callers hold hc-professional's clinical authorities, which this service does not
     * know and deliberately does not enumerate — so the gate is authentication, and the only way to
     * say that in a test is to present a token holding an authority that reaches nothing else here.
     * {@code ROLE_USER} is exactly that token: {@code plainUserIsRefusedEverywhere} above pins that
     * it is refused across the entity surface, so a pass here cannot be the blanket rule leaking.
     *
     * <p>Written as its own case rather than added to that sweep for the same reason: the sweep says
     * "nothing", this says "this one thing", and a change that merged them would delete the
     * distinction the decision turns on.
     */
    @Test
    void anyAuthenticatedCallerCanResolveAGeographicSpace() throws Exception {
        mvc.perform(get("/api/geographic-spaces").with(as(AuthoritiesConstants.USER))).andExpect(status().isOk());
        // 404 and not 403: the id is unknown, which is a fact about geography. The point is that the
        // chain admitted the caller and the handler answered.
        mvc.perform(get("/api/geographic-spaces/no-such-space").with(as(AuthoritiesConstants.USER))).andExpect(status().isNotFound());
    }

    /**
     * The carve-out is on {@code GET} only.
     *
     * <p>There is no write mapping on {@code GeographicSpaceReferenceResource} today, so this asserts
     * that the blanket {@code /api/** -> ROLE_ADMIN} rule is what would answer if one were added —
     * 403 rather than 404. Without it, a later CRUD resource on this path would arrive with its
     * writes already open to every authenticated caller in the network and nothing would fail.
     */
    @Test
    void theGeographicSpaceCarveOutDoesNotExtendToWrites() throws Exception {
        mvc
            .perform(
                post("/api/geographic-spaces").with(as(AuthoritiesConstants.USER)).contentType(MediaType.APPLICATION_JSON).content("{}")
            )
            .andExpect(status().isForbidden());
        mvc.perform(delete("/api/geographic-spaces/any-id").with(as(AuthoritiesConstants.USER))).andExpect(status().isForbidden());
    }

    /**
     * And it does not extend to a sub-path either.
     *
     * <p>The matchers name {@code /api/geographic-spaces} and {@code /api/geographic-spaces/{id}}
     * exactly rather than {@code /api/geographic-spaces/**}, so anything deeper — the professionals
     * based in a space, say, which is a list of people and not a place name — falls to the blanket
     * read rule and is refused. This asserts the failing direction is the safe one; the path itself
     * does not exist, and a 403 rather than a 404 is what says the chain decided before the handler
     * lookup did.
     */
    @Test
    void theGeographicSpaceCarveOutDoesNotExtendToSubPaths() throws Exception {
        mvc
            .perform(get("/api/geographic-spaces/any-id/professionals").with(as(AuthoritiesConstants.USER)))
            .andExpect(status().isForbidden());
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
