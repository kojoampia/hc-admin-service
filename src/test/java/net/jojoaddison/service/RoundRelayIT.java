package net.jojoaddison.service;

import static net.jojoaddison.config.ApplicationPropertiesFixture.professionalservice;
import static net.jojoaddison.security.jwt.JwtAuthenticationTestUtils.BEARER;
import static net.jojoaddison.security.jwt.JwtAuthenticationTestUtils.createValidTokenForUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.jojoaddison.config.SecurityConfiguration;
import net.jojoaddison.config.SecurityJwtConfiguration;
import net.jojoaddison.config.WebConfigurer;
import net.jojoaddison.management.SecurityMetersService;
import net.jojoaddison.security.jwt.JwtAuthenticationTestUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tech.jhipster.config.JHipsterProperties;

/**
 * <b>A round filed on a request authenticated the way production authenticates one carries the
 * caller's own token</b> — backlog item 57, and the check whose absence let a cross-stack
 * <em>write</em> ship inert for as long as it has existed.
 *
 * <h2>What was wrong, and why it was worse here than in the sibling client</h2>
 *
 * <p>{@link ProfessionalServiceClient#fileRound} read the token with
 * {@code SecurityUtils.getCurrentUserJWT()}, which filters the authentication's credentials on
 * {@code instanceof String}. This service authenticates with
 * {@code oauth2ResourceServer(oauth2 -> oauth2.jwt(...))} — {@code SecurityConfiguration:141} — so a
 * real request arrives as a {@code JwtAuthenticationToken} whose credentials are the decoded
 * {@code Jwt}. The lookup answered empty on <b>every</b> request, the client took its
 * {@code orElseThrow} branch, and every round ever planned failed before a socket was opened.
 *
 * <p>The same mechanism as item 50's, one severity up. There it cost a patient's name on a screen;
 * here it cost the write itself, and the console rendered
 * {@code ROSTER_SERVICE_NOT_CONFIGURED} — a designed, plausible panel pointing at this deployment's
 * own compose file — for a fault in a line of Java. Item 24's wrong-machine lesson at the layer
 * underneath the one it was learned at.
 *
 * <h2>Why nothing caught it</h2>
 *
 * <p>{@code ProfessionalServiceClientTest} authenticated with a
 * {@code UsernamePasswordAuthenticationToken}, whose credentials really are a {@code String} — the
 * one shape the broken lookup accepts and the one shape no request to this service produces. Its
 * helper's javadoc named the trap and then took it, exactly as {@code PatientServiceClientTest}'s
 * did. That helper now authenticates the way the chain does, so the unit test asks the same question
 * this file does without booting a context; <b>the two are deliberate duplicates at different
 * levels</b>, because the unit form would go on passing if the resource-server configuration
 * changed and this one would not.
 *
 * <p>{@code RosterPlanResourceIT} cannot see it either, and would not have if it had been written a
 * hundred times over: {@code src/test/resources/config/application.yml} sets
 * {@code application.professionalservice.enabled: false} for the whole suite, so {@code fileRound}
 * refuses one line above the token lookup and never reaches it.
 *
 * <h2>How it is asserted, and what is real</h2>
 *
 * <p>The shape {@code PatientNameRelayIT} established, which is why this file reads like it: the
 * whole path is real except the two ends. A token <b>signed with the test configuration's own
 * key</b> and decoded by the application's own {@code JwtDecoder}, the real
 * {@link SecurityConfiguration} filter chain, the real {@link ProfessionalServiceClient} with
 * {@code enabled=true}, and a loopback HTTP server standing in for hc-professional. It boots no
 * container and runs in seconds.
 *
 * <p>The assertion is that the stub saw {@code Bearer <the exact string that was sent in>} — byte
 * for byte, which is what establishes that the token is <em>relayed</em> rather than reminted. A
 * re-encode would need this service to hold a signing key it deliberately does not use, and would
 * put a robot in hc-professional's audit trail where the administrator who pressed <em>plan</em>
 * belongs.
 */
@AutoConfigureMockMvc
@SpringBootTest(
    classes = {
        JHipsterProperties.class,
        WebConfigurer.class,
        SecurityConfiguration.class,
        SecurityJwtConfiguration.class,
        SecurityMetersService.class,
        JwtAuthenticationTestUtils.class,
        RoundRelayIT.RelayHarness.class,
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RoundRelayIT {

    /** What the stub says hc-professional called the round. Read back and asserted on. */
    private static final String FILED_ROUND_ID = "round-4f2c";

    /**
     * The stub, started when this class is loaded.
     *
     * <p>Static and eager for the reason {@code PatientNameRelayIT} records: the port has to be known
     * before the context is built, because {@link RelayHarness} bakes it into the client's base url.
     */
    private static final HttpServer STUB = startStub();

    private static final AtomicReference<String> AUTHORIZATION_SEEN = new AtomicReference<>();

    /**
     * Method and path, as hc-professional would see them.
     *
     * <p>Captured because {@code ProfessionalServiceClient.ROUNDS} is {@code "/api/duty-roster"} —
     * <b>singular</b>, which its own comment flags as deliberate and easy to "correct" — and until
     * this file existed <b>nothing in the estate exercised that constant</b>. Every integration test
     * runs with the client disabled, no Cypress spec drives the planner, and the seeded {@code test}
     * teams carry no {@code geographicSpaceIds}, so the planner never reaches a candidate and never
     * files. A typo there would have failed in production, against the one service that could not
     * tell anybody why.
     */
    private static final AtomicReference<String> REQUEST_LINE_SEEN = new AtomicReference<>();

    private static final AtomicInteger REQUESTS = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Value("${jhipster.security.authentication.jwt.base64-secret}")
    private String jwtKey;

    @BeforeEach
    void resetTheStub() {
        AUTHORIZATION_SEEN.set(null);
        REQUEST_LINE_SEEN.set(null);
        REQUESTS.set(0);
    }

    @AfterAll
    static void stopTheStub() {
        STUB.stop(0);
    }

    /**
     * The token that went in is the token that goes out, and the round comes back filed.
     *
     * <p>Four assertions and none is redundant. The <b>200</b> says the chain accepted the token and
     * the handler ran as an authenticated administrator. The <b>body</b> says {@code fileRound}
     * returned an id rather than throwing, which is the outcome {@code RoundPlanningService} turns
     * into {@code PLANNED} and the console into <em>Filed with the roster service</em>. The
     * <b>request count</b> says a socket was opened at all — this is the assertion that is red
     * against {@code getCurrentUserJWT()}, because the client refused before dialling. The
     * <b>header</b> says which token, which is the whole security model: hc-professional's
     * {@code POST /api/duty-roster} is {@code ROLE_ADMIN}, the three gateways share one signing key,
     * and the audit trail over there names the person rather than a service account.
     */
    @Test
    void relaysTheCallersOwnTokenOnARequestThatWentThroughTheRealFilterChain() throws Exception {
        String token = createValidTokenForUser(jwtKey, "admin");

        mvc
            .perform(post("/api/relay-probe-round").header(AUTHORIZATION, BEARER + token))
            .andExpect(status().isOk())
            .andExpect(content().string(FILED_ROUND_ID));

        assertThat(REQUESTS.get()).as("hc-professional must actually be dialled").isOne();
        assertThat(AUTHORIZATION_SEEN.get()).isEqualTo(BEARER + token);
        assertThat(REQUEST_LINE_SEEN.get())
            .as("hc-professional's write path is POST /api/duty-roster — singular, and nothing else exercises it")
            .isEqualTo("POST /api/duty-roster");
    }

    /**
     * And an unauthenticated request never gets that far.
     *
     * <p>The complement, so the case above cannot be satisfied by a chain that lets everything
     * through — and, since 2026-09-09, the measurement behind a claim in
     * {@link ProfessionalServiceClient#fileRound}'s javadoc: the no-token branch is unreachable
     * through the REST surface, because {@code POST /api/**} is {@code hasAuthority(ADMIN)} and the
     * chain answers {@code 401} before any resource is entered. It is kept as a guard against an
     * in-process caller with no {@code SecurityContext}, of which there is none today.
     *
     * <p>It also pins the one thing a relay must never do: invent an identity for a caller who has
     * none. Nothing is dialled, so nothing is filed, so nobody is rostered.
     */
    @Test
    void anUnauthenticatedRequestDialsNobodyAndFilesNothing() throws Exception {
        mvc.perform(post("/api/relay-probe-round")).andExpect(status().isUnauthorized());

        assertThat(REQUESTS.get()).isZero();
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(
                "/",
                exchange -> {
                    REQUESTS.incrementAndGet();
                    AUTHORIZATION_SEEN.set(exchange.getRequestHeaders().getFirst("Authorization"));
                    REQUEST_LINE_SEEN.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
                    byte[] payload = ("{\"id\":\"" + FILED_ROUND_ID + "\"}").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(201, payload.length);
                    exchange.getResponseBody().write(payload);
                    exchange.close();
                }
            );
            server.start();
            return server;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The client under test, pointed at the stub, and a handler to reach it through.
     *
     * <p>Built by hand rather than component-scanned because this slice has no auto-configuration and
     * therefore no {@code RestClient.Builder} bean — and because the base url has to be the stub's
     * ephemeral port.
     *
     * <p><b>{@code enabled=true} is the point of building it here.</b> The shared test configuration
     * disables this client for every other test in the repository, deliberately and for good reason,
     * and that is precisely why no other test can reach the token lookup.
     */
    @Configuration(proxyBeanMethods = false)
    static class RelayHarness {

        @Bean
        ProfessionalServiceClient professionalServiceClient() {
            return new ProfessionalServiceClient(
                RestClient.builder(),
                professionalservice("http://127.0.0.1:" + STUB.getAddress().getPort(), true, 2)
            );
        }

        @Bean
        RoundRelayProbeResource roundRelayProbeResource(ProfessionalServiceClient professionalServiceClient) {
            return new RoundRelayProbeResource(professionalServiceClient);
        }
    }

    /**
     * Somewhere behind the filter chain to file a round from.
     *
     * <p>A {@code POST} under {@code /api/**} deliberately, so it is governed by the same rule the
     * real planner is — {@code hasAuthority(ADMIN)}, the write half of the read/write split. A probe
     * on a permitted path, or a {@code GET}, would prove the relay works for a caller the chain
     * admitted under weaker terms than the one this feature actually has.
     *
     * <p>It stands in for {@code RosterPlanResource}, which needs Mongo, three repositories and a
     * seeded team; what that resource does around this call is {@code RosterPlanResourceIT}'s and
     * {@code RoundPlanningServiceTest}'s. The seam between the security context and the outbound
     * header is this file's, and was nobody's.
     */
    @RestController
    static class RoundRelayProbeResource {

        private final ProfessionalServiceClient professionalServiceClient;

        RoundRelayProbeResource(ProfessionalServiceClient professionalServiceClient) {
            this.professionalServiceClient = professionalServiceClient;
        }

        @PostMapping("/api/relay-probe-round")
        String probe() {
            return professionalServiceClient.fileRound(Map.of("name", "Morning round"));
        }
    }
}
