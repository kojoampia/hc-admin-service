package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.jojoaddison.domain.enumeration.NameResolution;
import net.jojoaddison.security.SecurityUtils;
import net.jojoaddison.service.PatientServiceClient.ResolvedName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

/**
 * What {@link PatientServiceClient} does with each answer hc-patient can give — backlog item 50.
 *
 * <p><b>A loopback HTTP server rather than {@code MockRestServiceServer}</b>, for the reason
 * {@link AbofonsaContentClientTest} records: this client installs its own request factory after
 * receiving the builder, in order to bound both timeouts, so a stub bound to the builder is
 * replaced and the request goes to the real host. The server here is on {@code 127.0.0.1} on an
 * ephemeral port and depends on nobody's deploy.
 *
 * <p>The body is a <b>capture</b>, taken on 2026-09-09 from
 * {@code GET /api/profiles/email/kojo@jac.net} against the hc-patient quality stack with an
 * hc-admin administrator's token, trimmed of the address block and otherwise as they sent it. It
 * keeps the clinical fields deliberately — {@code bloodGroup}, {@code cardNumber},
 * {@code birthDate}, {@code careAngelPhone} — because "reads two fields out of a 27-field medical
 * profile and drops the rest" is the property item 50's consequence (a) turns on, and a fixture
 * carrying only the two names could not fail if the client started returning the document.
 */
class PatientServiceClientTest {

    private static final String CAPTURED =
        """
        {
          "id": "patient-kojo",
          "patientId": "patient-kojo",
          "firstName": "Kojo",
          "middleNames": "Kwame",
          "lastName": "Ampia-Addison",
          "membership": "PEAR",
          "birthDate": "1979-04-11",
          "sex": "MALE",
          "bloodGroup": "O+",
          "mobilePhone": "+233201110000",
          "phoneNumber": "+233302110000",
          "email": "kojo@jac.net",
          "cardType": "NHIS",
          "cardNumber": "GHA-000111222-3",
          "careAngelName": "Ama Ampia-Addison",
          "careAngelPhone": "+233209990000",
          "careAngelEmail": "ama@jac.net",
          "onboardingStatus": "COMPLETED",
          "onboardingStep": 6
        }
        """;

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>(CAPTURED);
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> requestedPath = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
            "/",
            exchange -> {
                requests.incrementAndGet();
                requestedPath.set(URLDecoder.decode(exchange.getRequestURI().toString(), StandardCharsets.UTF_8));
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] payload = body.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status.get(), payload.length);
                exchange.getResponseBody().write(payload);
                exchange.close();
            }
        );
        server.start();
    }

    @AfterEach
    void stopServerAndClearTheContext() {
        server.stop(0);
        SecurityContextHolder.clearContext();
    }

    /**
     * The name comes back, and <b>nothing else does</b>.
     *
     * <p>The second half is the assertion that matters and it is made on the value that crosses the
     * boundary rather than on a screen: {@link ResolvedName} has exactly two components, so there is
     * no field on it for a blood group to travel in. A test that only checked the name would pass
     * just as well against a client that returned the whole document beside it.
     *
     * <p>The middle name is dropped on purpose — see {@code PatientServiceClient.nameOf}. A patient
     * with a local profile is rendered first-plus-last by the console, and a learned patient
     * gaining a middle name would be two renderings of one column.
     */
    @Test
    void resolvesTheNameAndCarriesNoClinicalFieldWithIt() {
        authenticateWithAToken();

        ResolvedName resolved = clientAt(server.getAddress().getPort(), true).resolveName("kojo@jac.net");

        assertThat(resolved.outcome()).isEqualTo(NameResolution.RESOLVED);
        assertThat(resolved.name()).isEqualTo("Kojo Ampia-Addison");
        assertThat(resolved.hasName()).isTrue();
        assertThat(ResolvedName.class.getRecordComponents()).hasSize(2);
        assertThat(resolved.toString()).doesNotContain("O+", "GHA-000111222-3", "1979-04-11", "+233209990000");
    }

    /**
     * It asks the address it was given, and it relays <b>the caller's</b> token.
     *
     * <p>Both halves are the security model rather than plumbing. The token is why nothing had to be
     * built on hc-patient's side — their guard admits {@code ROLE_ADMIN}, which is on the token this
     * service received — and a service identity here would hand every operator the read that guard
     * exists to deny them.
     */
    @Test
    void asksHcPatientsOwnPathWithTheCallersToken() {
        authenticateWithAToken();

        clientAt(server.getAddress().getPort(), true).resolveName("kojo@jac.net");

        assertThat(requestedPath.get()).isEqualTo("/api/profiles/email/kojo@jac.net");
        assertThat(authorization.get()).isEqualTo("Bearer a.relayed.token");
    }

    /**
     * <b>And it relays the token a REAL request carries, which is not the same object at all.</b>
     *
     * <p>The case above authenticates with a {@code UsernamePasswordAuthenticationToken}, whose
     * credentials are the {@code String} the fixture put there. <b>No request to this service ever
     * produces one.</b> {@code SecurityConfiguration} configures
     * {@code oauth2ResourceServer(oauth2 -> oauth2.jwt(...))}, so an authenticated request arrives as
     * a {@link JwtAuthenticationToken} — and its base class's two-argument constructor passes the
     * token as token, principal <em>and</em> credentials (verified in the bytecode of Spring Security
     * 7.1.1's {@code AbstractOAuth2TokenAuthenticationToken}: {@code aload_1, aload_1, aload_1}). So
     * {@code getCredentials()} answers a {@code Jwt}, never a {@code String}.
     *
     * <p>{@code SecurityUtils.getCurrentUserJWT()} filters on {@code instanceof String}. Against the
     * only authentication this service actually issues it is therefore <b>empty on every request</b>,
     * the client takes its no-token branch, and every candidate row reports as unresolvable on every
     * stack, for ever. The fixture above could not see it, and its own javadoc named the trap without
     * asking which token a real request produces — which is the more useful half of this finding: a
     * comment that knows about a hazard is not a test for it.
     *
     * <p>{@link SecurityUtils#getCurrentRequestJwt()} is the reading that covers both shapes.
     */
    @Test
    void relaysTheTokenThatARealResourceServerRequestCarries() {
        authenticateAsTheResourceServerDoes();

        clientAt(server.getAddress().getPort(), true).resolveName("kojo@jac.net");

        assertThat(requests.get()).as("a real authenticated request must reach the far service at all").isOne();
        assertThat(authorization.get()).isEqualTo("Bearer a.real.bearer.token");
    }

    /**
     * A 404 is an answer, not a failure.
     *
     * <p>It is <b>both</b> "hc-patient holds no profile for this address" and "this caller may not
     * see the one they hold" — their {@code ProfileResource} returns the same status for the guard
     * and for the miss — which is why {@link NameResolution} has no fourth value and why nothing
     * here tries to say which it was.
     */
    @Test
    void anUnknownAddressIsNotFoundRatherThanUnavailable() {
        authenticateWithAToken();
        status.set(404);
        body.set("{}");

        assertThat(clientAt(server.getAddress().getPort(), true).resolveName("nobody@nowhere.test").outcome())
            .isEqualTo(NameResolution.NOT_FOUND);
    }

    /**
     * A transport failure is {@code UNAVAILABLE}, and this is the case that must not collapse into
     * the one above.
     *
     * <p>Item 46's lesson one stack along: reporting "nobody asked them" as "they do not know this
     * person" is a confident wrong answer, and the console renders a note for exactly this outcome.
     * A closed loopback port, so it is refused immediately and no timeout is spent.
     */
    @Test
    void aStackThatCannotBeReachedIsUnavailable() throws IOException {
        authenticateWithAToken();

        assertThat(clientAt(aPortNothingIsListeningOn(), true).resolveName("kojo@jac.net").outcome()).isEqualTo(NameResolution.UNAVAILABLE);
    }

    /** And so is an answer this client cannot make sense of. A 500 is not "there is no such patient". */
    @Test
    void anErrorStatusIsUnavailableToo() {
        authenticateWithAToken();
        status.set(503);
        body.set("{}");

        assertThat(clientAt(server.getAddress().getPort(), true).resolveName("kojo@jac.net").outcome())
            .isEqualTo(NameResolution.UNAVAILABLE);
    }

    /**
     * It throws nothing, ever — the inversion of {@link ProfessionalServiceClient}.
     *
     * <p>That client writes to the roster of record and must never report a round as filed when it
     * was not. This one decorates a directory row, and a directory that will not load because a
     * sibling stack is down is a far worse screen than one showing yesterday's address. Asserted as
     * its own case because the outcome assertions above would pass just as well if the throw were
     * one branch away.
     */
    @Test
    void neverThrows() throws IOException {
        authenticateWithAToken();
        PatientServiceClient unreachable = clientAt(aPortNothingIsListeningOn(), true);

        assertThat(unreachable.resolveName("kojo@jac.net")).isNotNull();
        assertThat(unreachable.resolveName(null)).isNotNull();
        assertThat(unreachable.resolveName("  ")).isNotNull();
    }

    /**
     * Disabled and no-token both answer {@code UNAVAILABLE} <b>without opening a socket</b>, and the
     * request count is the assertion.
     *
     * <p>Disabled is the state every integration test in this repository runs in, so it is also the
     * state a reader is most likely to be looking at a report from. Neither is a fourth outcome:
     * "never dialled" is a distinction the api's log makes and a directory row cannot act on.
     */
    @Test
    void aDisabledClientAndAnUnauthenticatedRequestBothAnswerUnavailableWithoutAsking() {
        int port = server.getAddress().getPort();

        authenticateWithAToken();
        assertThat(clientAt(port, false).resolveName("kojo@jac.net").outcome()).isEqualTo(NameResolution.UNAVAILABLE);

        SecurityContextHolder.clearContext();
        assertThat(clientAt(port, true).resolveName("kojo@jac.net").outcome()).isEqualTo(NameResolution.UNAVAILABLE);

        assertThat(requests.get()).as("neither case may reach the far service").isZero();
    }

    /**
     * A profile with no name is {@code RESOLVED} with nothing on it, and that is deliberate.
     *
     * <p>hc-patient's {@code Profile} carries no {@code @NotNull} on {@code firstName} or
     * {@code lastName}, so the state is storable there and this client will meet it. It is not a
     * fourth outcome: the question was asked and answered, and the console falls through to the
     * address exactly as it does for a 404. The name is {@code null} rather than {@code ""} so that
     * a blank cannot be printed as somebody's name.
     */
    @Test
    void aProfileWithNoNameResolvesToNoName() {
        authenticateWithAToken();
        body.set("{ \"id\": \"patient-nameless\", \"firstName\": \"  \", \"email\": \"nameless@mail.gh\" }");

        ResolvedName resolved = clientAt(server.getAddress().getPort(), true).resolveName("nameless@mail.gh");

        assertThat(resolved.outcome()).isEqualTo(NameResolution.RESOLVED);
        assertThat(resolved.name()).isNull();
        assertThat(resolved.hasName()).isFalse();
    }

    /**
     * {@code SecurityUtils.getCurrentUserJWT()} reads the authentication's <em>credentials</em> and
     * requires a {@code String}, so this is a plain token-bearing authentication rather than a
     * {@code JwtAuthenticationToken} — whose credentials are the decoded {@code Jwt} and which would
     * be filtered out as "no token", passing the wrong case. The same fixture
     * {@link ProfessionalServiceClientTest} uses, and for the same reason.
     */
    private static void authenticateWithAToken() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken("admin", "a.relayed.token", List.of()));
    }

    /**
     * The authentication a real request to this service produces: a {@link JwtAuthenticationToken}
     * from the resource-server filter chain, holding the decoded {@code Jwt}.
     *
     * <p>Built the way {@code JwtAuthenticationProvider} builds it — {@code new
     * JwtAuthenticationToken(jwt, authorities)} — so the credentials are the {@code Jwt} and the raw
     * token is reachable only through {@code getToken().getTokenValue()}. The header value below is
     * not a well-formed JWS and does not need to be: nothing here decodes it, and the point of the
     * case is which field the relay reads.
     */
    private static void authenticateAsTheResourceServerDoes() {
        Jwt jwt = Jwt
            .withTokenValue("a.real.bearer.token")
            .header("alg", "HS512")
            .subject("admin")
            .claim("auth", "ROLE_ADMIN")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();
        SecurityContextHolder
            .getContext()
            .setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    /** Bound and released, so the number is one the kernel says is free rather than one guessed. */
    private static int aPortNothingIsListeningOn() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static PatientServiceClient clientAt(int port, boolean enabled) {
        return new PatientServiceClient(RestClient.builder(), "http://127.0.0.1:" + port, enabled, 1);
    }
}
