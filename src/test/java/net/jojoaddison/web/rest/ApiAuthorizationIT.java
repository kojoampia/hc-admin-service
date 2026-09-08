package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.security.AuthoritiesConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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
 *
 * <p>The corollary is how an anonymous case is written: <em>no</em> post-processor at all, so the
 * request carries no {@code Authorization} header. {@code jwt()} with an empty authority set is not
 * the same thing — it is an authenticated principal holding nothing, which satisfies
 * {@code .authenticated()} and would pass a {@code permitAll} identically, asserting neither.
 */
@IntegrationTest
@AutoConfigureMockMvc
class ApiAuthorizationIT {

    /** One representative path per shape of rule, not per resource — the rules are path-wide. */
    private static final String ENTITY_PATH = "/api/teams";

    @Autowired
    private MockMvc mvc;

    // Qualified by name: actuator contributes a second RequestMappingHandlerMapping
    // (controllerEndpointHandlerMapping) and by type alone this is ambiguous.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

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

    /**
     * The plan catalogue sync, which is the same shape one field along.
     *
     * <p>Asserted separately for the probe's reason: it does not look like a write either. It reads a
     * public catalogue somebody else publishes and takes no body, so "let an operator refresh it" is
     * an easy argument to make — and it rewrites the name, code and ordering of every plan the
     * patient directory, the CSV export and the dashboard's plan mix render. The blanket non-GET rule
     * covers it and the answer is 403.
     *
     * <p>403 and not 404, so this goes red if the path is ever removed rather than quietly asserting
     * nothing.
     */
    @Test
    void operatorCannotSyncThePlanCatalogue() throws Exception {
        mvc.perform(post("/api/service-plans/sync").with(as(AuthoritiesConstants.OPERATOR))).andExpect(status().isForbidden());
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

    // --- directory links: the endpoint that serves the correlation key -----------------------------

    /**
     * <b>{@code /api/directory-links} serves a patient's email address, and this is the only place
     * the rule that gates it is executed.</b>
     *
     * <p>The endpoint is covered by the blanket read/write split rather than a matcher of its own,
     * which is correct — but "covered by a blanket rule" and "asserted" are different things, and
     * every case in {@code DirectoryLinkResourceIT} runs {@code addFilters = false}, so none of them
     * would notice the chain being opened. The class javadoc on {@code DirectoryLinkResource} argues
     * at length that this endpoint may return the correlation key <em>because the reader is named and
     * authorised</em> — item 43 having taken the same value out of every log line — and that argument
     * is only as good as the authority behind it. So the authority is asserted here, where the filter
     * chain is on, rather than left implied by the sentence that depends on it.
     *
     * <p>Both halves: the read reaches an operator, and the reconciliation — which writes to the
     * patient directory — does not.
     */
    @Test
    void anOperatorReadsTheDirectoryLinksAndAnAnonymousCallerDoesNot() throws Exception {
        mvc.perform(get("/api/directory-links").with(as(AuthoritiesConstants.OPERATOR))).andExpect(status().isOk());
        mvc.perform(get("/api/directory-links").with(as(AuthoritiesConstants.ADMIN))).andExpect(status().isOk());
        mvc.perform(get("/api/directory-links")).andExpect(status().isUnauthorized());
    }

    /**
     * Authentication alone is not enough to read somebody's address here.
     *
     * <p>{@code ROLE_USER} is the authority every account on this gateway holds as a baseline, and
     * {@code ROLE_PATIENT} arrives on tokens hc-patient issues against the shared signing key — so
     * this is the case where a patient could otherwise read the whole directory of addresses.
     */
    @Test
    void neitherAPlainUserNorAPatientReachesTheDirectoryLinks() throws Exception {
        mvc.perform(get("/api/directory-links").with(as(AuthoritiesConstants.USER))).andExpect(status().isForbidden());
        mvc.perform(get("/api/directory-links").with(as("ROLE_PATIENT"))).andExpect(status().isForbidden());
    }

    /**
     * The reconciliation writes {@code Patient} rows, so it is the administrator's alone.
     *
     * <p>The admin half is asserted too, so that "an operator is refused" cannot be satisfied by the
     * endpoint being unreachable for everybody — which is the same trap the export cases above name.
     */
    @Test
    void onlyAnAdminReconcilesTheDirectory() throws Exception {
        mvc.perform(post("/api/directory-links/reconcile").with(as(AuthoritiesConstants.OPERATOR))).andExpect(status().isForbidden());
        mvc.perform(post("/api/directory-links/reconcile").with(as(AuthoritiesConstants.ADMIN))).andExpect(status().isOk());
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
     * <b>And the floor has a ceiling: signed in is still required.</b>
     *
     * <p>Written after a review found that {@link #anyAuthenticatedCallerCanResolveAGeographicSpace}
     * pins only how far the carve-out reaches <em>down</em>. Change either matcher in
     * {@code SecurityConfiguration} from {@code .authenticated()} to {@code .permitAll()} and the
     * whole suite stayed green: the {@code ROLE_USER} cases go on passing, the write case goes on
     * passing because the blanket rules answer writes, and no case sent this path with no token at
     * all. This is the identical gap that let {@code /api/** -> authenticated()} survive — this class
     * is the only one running with the filter chain on, and a question it does not ask is a question
     * nothing else in the suite can ask.
     *
     * <p>No {@code .with(as(...))} on purpose: the request carries no {@code Authorization} header
     * whatsoever, which is what "anonymous" has to mean here. A token holding an empty authority set
     * would still be an authenticated principal and would pass a {@code permitAll} and an
     * {@code authenticated()} alike, asserting nothing.
     *
     * <p>401 and not 403, for the reason {@link #anonymousIsChallenged} gives: a 403 would mean an
     * anonymous principal was reaching the chain as authenticated-but-unauthorized.
     */
    @Test
    void anonymousCannotResolveAGeographicSpace() throws Exception {
        mvc.perform(get("/api/geographic-spaces")).andExpect(status().isUnauthorized());
        // The unknown-id case too: under permitAll this answers 404, which is a pass for the
        // ROLE_USER case above and would be a silent pass here as well if it were not asserted.
        mvc.perform(get("/api/geographic-spaces/no-such-space")).andExpect(status().isUnauthorized());
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
     *
     * <p>Two segments, and it can only ever be two: see
     * {@link #noLiteralSiblingHidesBehindTheGeographicSpaceIdMatcher} for the one-segment case, which
     * this cannot reach.
     */
    @Test
    void theGeographicSpaceCarveOutDoesNotExtendToSubPaths() throws Exception {
        mvc
            .perform(get("/api/geographic-spaces/any-id/professionals").with(as(AuthoritiesConstants.USER)))
            .andExpect(status().isForbidden());
    }

    /**
     * The one-segment sibling, which no request can test and which the matchers do not stop.
     *
     * <p>{@code /api/geographic-spaces/{id}} is a <em>single-segment</em> wildcard. The sub-path case
     * above is safe because it is two segments deep; a literal one segment deep — {@code /export},
     * say — matches {@code {id}}, is admitted on authentication alone, and is then routed by MVC to
     * its own handler. There is no request that demonstrates this today, because the sibling does not
     * exist; by the time one does, the damage is already written and green.
     *
     * <p>So this asserts the shape of the application instead: exactly the two patterns the two
     * matchers name are mapped under this path. It fails on the commit that adds a third, which is
     * precisely when somebody needs to read the rule in {@code SecurityConfiguration}. The precedent
     * is in the same file twelve lines below — {@code /api/patients/export} is this exact shape and
     * needed an admin-only matcher of its own, above the read rule rather than below it.
     *
     * <p>Discovered from the handler mapping rather than enumerated, for {@code PaginationIT}'s
     * reason: a list that has to be extended by hand stops covering things without saying so.
     */
    @Test
    void noLiteralSiblingHidesBehindTheGeographicSpaceIdMatcher() {
        List<String> mapped = handlerMapping
            .getHandlerMethods()
            .keySet()
            .stream()
            .map(RequestMappingInfo::getPathPatternsCondition)
            .filter(Objects::nonNull)
            .flatMap(condition -> condition.getPatternValues().stream())
            .filter(pattern -> pattern.startsWith("/api/geographic-spaces"))
            .distinct()
            .sorted()
            .toList();

        assertThat(mapped)
            .as(
                "A new path under /api/geographic-spaces/ needs its own matcher ABOVE the two in " +
                "SecurityConfiguration, gated on what it discloses — one segment deep it matches " +
                "{id} and is open to every authenticated caller on three stacks. Add the matcher, " +
                "then add the pattern here."
            )
            .containsExactlyInAnyOrder("/api/geographic-spaces", "/api/geographic-spaces/{id}");
    }

    // --- the patient carve-out, and its removal ---------------------------------------------------

    /**
     * <b>There is no patient carve-out any more, as of 2026-09-04.</b>
     *
     * <p>{@code ROLE_PATIENT} is never issued by this stack's gateway; it arrives on tokens from
     * hc-patient-ms, which shares the signing key. It used to be honoured on exactly one path —
     * {@code GET /api/duty-rosters/patient/{patientId}} — by a matcher sitting above the blanket
     * rules, and this class asserted it reached that and nothing else. The endpoint moved to
     * {@code professionalservice} with the roster of record, and the matcher went with it.
     *
     * <p>The old assertion is <b>replaced rather than deleted</b>, for the reason the carve-out
     * existed at all: a matcher above the blanket rules is invisible to every other test in this
     * repository, so a re-added one would be caught by nothing. This asserts the whole surface,
     * including the path the exception used to be on.
     */
    @Test
    void patientReachesNothingAtAll() throws Exception {
        mvc.perform(get(ENTITY_PATH).with(as(AuthoritiesConstants.PATIENT))).andExpect(status().isForbidden());
        mvc.perform(get("/api/duty-rosters").with(as(AuthoritiesConstants.PATIENT))).andExpect(status().isForbidden());
        mvc
            .perform(get("/api/duty-rosters/patient/some-profile-id").param("date", "2026-08-05").with(as(AuthoritiesConstants.PATIENT)))
            .andExpect(status().isForbidden());
    }

    /**
     * The duty-roster surface is gone from this service, not merely closed off.
     *
     * <p>An admin gets {@code 404} rather than {@code 200}: the rules still admit them, and there is
     * no handler behind any of it. Asserted with the authority that <em>could</em> have reached it,
     * because a 403 for everybody would look identical whether the resource existed or not — and
     * "deleted" and "locked" are different claims. Planning is {@code POST /api/roster-plans} now,
     * and rounds are read back from {@code professionalservice} directly.
     */
    @Test
    void theDutyRosterSurfaceNoLongerExists() throws Exception {
        mvc.perform(get("/api/duty-rosters").with(as(AuthoritiesConstants.ADMIN))).andExpect(status().isNotFound());
        mvc.perform(get("/api/duty-rosters/anything").with(as(AuthoritiesConstants.ADMIN))).andExpect(status().isNotFound());
        mvc
            .perform(post("/api/duty-rosters/auto-schedule").param("date", "2026-08-05").with(as(AuthoritiesConstants.ADMIN)))
            .andExpect(status().isNotFound());
    }

    /** Planning is a write, so it is admin-only and an operator's read authority is not enough. */
    @Test
    void planningIsAdminOnly() throws Exception {
        mvc
            .perform(
                post("/api/roster-plans")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"date\":\"2026-08-12\",\"rounds\":[]}")
                    .with(as(AuthoritiesConstants.OPERATOR))
            )
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/roster-plans").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }
}
