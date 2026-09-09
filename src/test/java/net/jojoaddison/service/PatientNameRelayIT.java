package net.jojoaddison.service;

import static net.jojoaddison.security.jwt.JwtAuthenticationTestUtils.BEARER;
import static net.jojoaddison.security.jwt.JwtAuthenticationTestUtils.createValidTokenForUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import tech.jhipster.config.JHipsterProperties;

/**
 * <b>A request authenticated the way production authenticates one ends with the caller's own token
 * on the outbound call</b> — backlog item 50, and the check whose absence let the relay ship inert.
 *
 * <h2>What went wrong, and why every other test was blind to it</h2>
 *
 * <p>{@code SecurityUtils.getCurrentUserJWT()} reads the authentication's credentials and requires a
 * {@code String}. This service authenticates with
 * {@code oauth2ResourceServer(oauth2 -> oauth2.jwt(...))}, which produces a
 * {@code JwtAuthenticationToken} whose credentials are the decoded {@code Jwt} — so it answered
 * empty on <b>every</b> request, the client took its no-token branch, and every candidate row
 * reported as unresolvable on every stack. The feature had never worked.
 *
 * <p>Nothing caught it because every test authenticated with a fixture rather than with the chain.
 * {@code PatientServiceClientTest} used a {@code UsernamePasswordAuthenticationToken}, whose
 * credentials really are a {@code String}; {@code DirectoryLinkNameResolutionIT} mocks the client
 * away entirely; every other {@code *ResourceIT} runs {@code addFilters = false}, so no filter chain
 * runs at all. <b>Each of those is right for what it asserts, and together they leave the one
 * question this file asks unasked</b>: which object is in the {@code SecurityContext} when a real
 * request reaches a handler.
 *
 * <h2>How it is asserted, and why it is not the real resource</h2>
 *
 * <p>The whole path is real except the two ends: a token <b>signed with the test configuration's own
 * key</b> and decoded by the application's own {@code JwtDecoder}, the real
 * {@link SecurityConfiguration} filter chain, the real {@link PatientServiceClient}, and a loopback
 * HTTP server standing in for hc-patient. The assertion is that the stub saw
 * {@code Bearer <the exact string that was sent in>} — byte for byte, which also establishes that
 * the relay passes the caller's token on rather than minting a new one.
 *
 * <p>The handler behind the chain is a probe rather than {@code DirectoryLinkResource}, because that
 * resource needs Mongo and this slice deliberately boots no container: it is the same
 * security-only slice {@code TokenAuthenticationIT} uses, and it runs in seconds on a loaded
 * machine where a Testcontainers context does not (backlog item 17). What the probe stands in for is
 * covered elsewhere — {@code DirectoryLinkNameResolutionIT} asserts the resource's wire shape over a
 * mocked client. The seam between them is exactly this file's subject and is now asserted by it.
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
        PatientNameRelayIT.RelayHarness.class,
    }
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PatientNameRelayIT {

    /**
     * The stub, started when this class is loaded.
     *
     * <p>Static and eager on purpose: the port has to be known before the context is built, because
     * {@link RelayHarness} bakes it into the client's base url. A {@code @BeforeAll} would run in
     * time today and stops doing so the moment somebody makes the harness read a property.
     */
    private static final HttpServer STUB = startStub();

    private static final AtomicReference<String> AUTHORIZATION_SEEN = new AtomicReference<>();
    private static final AtomicInteger REQUESTS = new AtomicInteger();

    @Autowired
    private MockMvc mvc;

    @Value("${jhipster.security.authentication.jwt.base64-secret}")
    private String jwtKey;

    @BeforeEach
    void resetTheStub() {
        AUTHORIZATION_SEEN.set(null);
        REQUESTS.set(0);
    }

    @AfterAll
    static void stopTheStub() {
        STUB.stop(0);
    }

    /**
     * The token that went in is the token that goes out.
     *
     * <p>Three assertions and none is redundant. The <b>200</b> says the chain accepted the token, so
     * the handler ran as an authenticated caller rather than as nobody. The <b>request count</b> says
     * the far service was dialled at all — this is the assertion that was red before the fix, because
     * the relay found no token and refused before opening a socket. The <b>header</b> says which
     * token, which is the security model: hc-patient's guard admits this caller because the three
     * gateways share one signing key, and a token belonging to anybody else would be a different
     * feature with different authority.
     */
    @Test
    void relaysTheCallersOwnTokenOnARequestThatWentThroughTheRealFilterChain() throws Exception {
        String token = createValidTokenForUser(jwtKey, "admin");

        mvc
            .perform(get("/api/relay-probe").header(AUTHORIZATION, BEARER + token))
            .andExpect(status().isOk())
            .andExpect(content().string("RESOLVED"));

        assertThat(REQUESTS.get()).as("the far service must actually be dialled").isOne();
        assertThat(AUTHORIZATION_SEEN.get()).isEqualTo(BEARER + token);
    }

    /**
     * And an unauthenticated request never gets that far.
     *
     * <p>The complement, so the case above cannot be satisfied by a chain that lets everything
     * through: no token in means 401 out and nothing dialled. It also pins the one thing the relay
     * must not do — invent an identity for a caller who has none.
     */
    @Test
    void anUnauthenticatedRequestDialsNobody() throws Exception {
        mvc.perform(get("/api/relay-probe")).andExpect(status().isUnauthorized());

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
                    byte[] payload = "{\"firstName\":\"Kojo\",\"lastName\":\"Ampia-Addison\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, payload.length);
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
     */
    @Configuration(proxyBeanMethods = false)
    static class RelayHarness {

        @Bean
        PatientServiceClient patientServiceClient() {
            return new PatientServiceClient(RestClient.builder(), "http://127.0.0.1:" + STUB.getAddress().getPort(), true, 2);
        }

        @Bean
        RelayProbeResource relayProbeResource(PatientServiceClient patientServiceClient) {
            return new RelayProbeResource(patientServiceClient);
        }
    }

    /**
     * Somewhere behind the filter chain to call the client from.
     *
     * <p>Under {@code /api/**} deliberately, so it is governed by the same authority rules every real
     * endpoint is: a probe on a permitted path would prove the relay works for a caller the chain
     * never authenticated.
     */
    @RestController
    static class RelayProbeResource {

        private final PatientServiceClient patientServiceClient;

        RelayProbeResource(PatientServiceClient patientServiceClient) {
            this.patientServiceClient = patientServiceClient;
        }

        @GetMapping("/api/relay-probe")
        String probe() {
            return patientServiceClient.resolveName("kojo@jac.net").outcome().name();
        }
    }
}
