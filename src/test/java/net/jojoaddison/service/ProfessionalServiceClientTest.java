package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import net.jojoaddison.security.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

/**
 * Which of the three ways {@link ProfessionalServiceClient#fileRound} fails is which.
 *
 * <p>Backlog item 24, at the layer that decides it. {@code enabled=false} and a request carrying no
 * caller token are configuration facts about the machine this api is running on; a
 * {@link ProfessionalServiceClient.RosterServiceUnavailableException} that is <em>not</em> the
 * not-configured subtype means a call was made and did not come back, which is the estate's problem
 * and a different screen. Nothing further up can tell them apart if they arrive as one type, and for
 * as long as they did the console reported all three as the roster service being down.
 *
 * <p>The three local refusals — disabled, no authentication at all, and an anonymous one — open no
 * socket, which is itself the property being asserted: they throw before the {@link RestClient} is
 * reached. The outage case opens one deliberately, to a port nothing is listening on, because "a
 * call was attempted and failed" cannot be demonstrated without attempting one. It is a closed
 * loopback port rather than a remote address, so it is refused immediately and no timeout is spent;
 * the path where a sibling really answers belongs to the quality stack, for the reason
 * {@code src/test/resources/config/application.yml} records.
 *
 * <p><b>This file is also how backlog item 57 survived, and it is red against the code it was
 * written for.</b> Its fixture authenticated with the one authentication type whose credentials are
 * a {@code String}, which is the one type this service's chain never produces — see
 * {@link #authenticateWithAToken()}. Two of the cases below now fail if the token lookup in
 * {@code fileRound} is reverted to {@code SecurityUtils.getCurrentUserJWT()}. The end-to-end form of
 * the same question, through the real filter chain and a real signed token, is {@code RoundRelayIT};
 * neither replaces the other, because this one keeps working when there is no context to boot and
 * that one keeps working when this fixture drifts from what the chain does.
 */
class ProfessionalServiceClientTest {

    private static final Map<String, Object> ANY_ROUND = Map.of("name", "Morning round");

    /** Syntactically valid and never reached — the local refusals happen before any connect. */
    private static final int NEVER_DIALLED_PORT = 1;

    /**
     * Not a real JWS and it does not need to be: nothing here decodes it. What matters is that it is
     * the {@code tokenValue} of the {@code Jwt} in the context, so a relay that passed the caller's
     * token on would send exactly this — which is the property {@code RoundRelayIT} asserts against
     * a socket, with a token the application's own decoder accepted.
     */
    private static final String RELAYED_TOKEN = "a.relayed.token";

    @AfterEach
    void clearTheContext() {
        SecurityContextHolder.clearContext();
    }

    /**
     * Disabled is not an outage.
     *
     * <p>This is the state every integration test in the repository runs in, so it is also the state
     * a reader is most likely to be looking at a report from.
     */
    @Test
    void aDisabledClientRefusesAsMisconfiguredRatherThanAsUnavailable() {
        ProfessionalServiceClient client = clientWith(false);

        assertThatExceptionOfType(ProfessionalServiceClient.RosterServiceNotConfiguredException.class)
            .isThrownBy(() -> client.fileRound(ANY_ROUND))
            .withMessageContaining("disabled");
    }

    /**
     * A request with no token to relay is the same kind of fact.
     *
     * <p>The client spends the caller's own JWT — three gateways share one signing key — so a
     * planning call arriving without one cannot be completed by anything the far service does. It is
     * reachable only through a misconfigured chain in front of this service, which is why it is a
     * deployment fault and not an outage.
     */
    @Test
    void aRequestWithNoCallerTokenRefusesAsMisconfiguredToo() {
        ProfessionalServiceClient client = clientWith(true);

        assertThatExceptionOfType(ProfessionalServiceClient.RosterServiceNotConfiguredException.class)
            .isThrownBy(() -> client.fileRound(ANY_ROUND))
            .withMessageContaining("No caller token");
    }

    /**
     * And neither does an anonymous one, whose credentials are the empty string.
     *
     * <p>A separate case from the one above because it is a separate branch, and because the two
     * were <em>not</em> equivalent before item 57. {@code AnonymousAuthenticationToken.getCredentials()}
     * is {@code ""} — verified in the bytecode, it is a bare {@code ldc} of the empty constant — so
     * the old {@code instanceof String} filter matched it and answered {@code Optional.of("")}. This
     * client would have sent hc-professional a literal {@code "Bearer "} with nothing after it and
     * reported the {@code 401} that came back as the roster service being unreachable: an outage
     * panel for a caller who was never signed in.
     *
     * <p>{@link SecurityUtils#getCurrentRequestJwt()} rejects a blank token rather than relaying it,
     * so this is the honest refusal — nothing dialled, and the local reason. It is asserted on the
     * client rather than trusted from the utility's own test because this is the class that would
     * open the socket.
     */
    @Test
    void anAnonymousRequestRefusesAsMisconfiguredRatherThanRelayingAnEmptyBearer() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))
            );
        ProfessionalServiceClient client = clientWith(true);

        assertThatExceptionOfType(ProfessionalServiceClient.RosterServiceNotConfiguredException.class)
            .isThrownBy(() -> client.fileRound(ANY_ROUND))
            .withMessageContaining("No caller token");
    }

    /**
     * And a call that was actually made and failed is <b>not</b> the misconfigured type.
     *
     * <p>The half of the discrimination that the two cases above cannot establish on their own: a
     * subtype nothing ever avoids would classify every failure as a local one, which is the same
     * defect in the other direction.
     */
    @Test
    void aCallThatWasMadeAndFailedIsTheOutageTypeAndNotTheMisconfiguredOne() throws IOException {
        authenticateWithAToken();
        ProfessionalServiceClient client = clientAt(aPortNothingIsListeningOn(), true);

        assertThatExceptionOfType(ProfessionalServiceClient.RosterServiceUnavailableException.class)
            .isThrownBy(() -> client.fileRound(ANY_ROUND))
            .satisfies(thrown -> assertThat(thrown).isNotInstanceOf(ProfessionalServiceClient.RosterServiceNotConfiguredException.class));
    }

    /**
     * The subtype is still an unavailable exception, so the round still fails.
     *
     * <p>Every existing catch of the supertype — {@code RoundPlanningService}'s among them — has to
     * keep reporting the round as failed rather than filed. Widening the reason must not have
     * narrowed the refusal, and this is the assertion that would go red if the new type were ever
     * made a sibling instead of a subtype.
     */
    @Test
    void andTheSubtypeStillFailsTheRoundLikeAnyOtherRefusalToWrite() {
        ProfessionalServiceClient client = clientWith(false);

        assertThatExceptionOfType(ProfessionalServiceClient.RosterServiceUnavailableException.class)
            .isThrownBy(() -> client.fileRound(ANY_ROUND));
    }

    /**
     * <b>The authentication a real request produces, which is what this fixture was not until
     * 2026-09-09.</b>
     *
     * <p>It authenticated with a {@code UsernamePasswordAuthenticationToken} — and said so, in a
     * javadoc that named the trap and then walked into it: <em>"{@code getCurrentUserJWT()} reads
     * the credentials and requires a {@code String}, so the fixture is a plain token-bearing
     * authentication rather than a {@code JwtAuthenticationToken}, whose credentials are the decoded
     * {@code Jwt} and which would be filtered out as 'no token' and pass the wrong case."</em> Every
     * clause of that is true. It reads as a note about a test fixture and it is a description of a
     * production defect: the shape it went out of its way to avoid is the <b>only</b> shape this
     * service's chain ever puts in the context, so the case below was the one this client took on
     * every real call, and the outage case it is here to distinguish was unreachable. Backlog item
     * 57, and the same trap {@code PatientServiceClientTest} had, found by item 50's review.
     *
     * <p>So the fixture is a {@code JwtAuthenticationToken} now, built the way
     * {@code JwtAuthenticationProvider} builds one. {@link SecurityUtils#getCurrentRequestJwt()}
     * reads it through {@code getToken().getTokenValue()}; the older method still cannot, which is
     * what makes this case fail if the adoption is ever reverted.
     */
    private static void authenticateWithAToken() {
        Jwt jwt = Jwt.withTokenValue(RELAYED_TOKEN).header("alg", "HS512").subject("admin").claim("auth", "ROLE_ADMIN").build();
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

    /**
     * The base url is never dialled by the cases that use this, so the port is a placeholder rather
     * than a promise — {@link #aPortNothingIsListeningOn()} is what the one case that does dial
     * uses.
     */
    private static ProfessionalServiceClient clientWith(boolean enabled) {
        return clientAt(NEVER_DIALLED_PORT, enabled);
    }

    private static ProfessionalServiceClient clientAt(int port, boolean enabled) {
        return new ProfessionalServiceClient(RestClient.builder(), "http://localhost:" + port, enabled, 1);
    }
}
