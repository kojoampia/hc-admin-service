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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * <p>The first two cases open no socket at all, which is itself the property being asserted — they
 * throw before the {@link RestClient} is reached. The third opens one deliberately, to a port
 * nothing is listening on, because "a call was attempted and failed" cannot be demonstrated without
 * attempting one. It is a closed loopback port rather than a remote address, so it is refused
 * immediately and no timeout is spent; the path where a sibling really answers belongs to the
 * quality stack, for the reason {@code src/test/resources/config/application.yml} records.
 */
class ProfessionalServiceClientTest {

    private static final Map<String, Object> ANY_ROUND = Map.of("name", "Morning round");

    /** Syntactically valid and never reached — the two local refusals happen before any connect. */
    private static final int NEVER_DIALLED_PORT = 1;

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
     * {@link SecurityUtils#getCurrentUserJWT()} reads the authentication's <em>credentials</em> and
     * requires them to be a {@code String} — so the fixture is a plain token-bearing authentication
     * rather than a {@code JwtAuthenticationToken}, whose credentials are the decoded {@code Jwt}
     * and which would be filtered out as "no token" and pass the wrong case.
     */
    private static void authenticateWithAToken() {
        SecurityContextHolder
            .getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken("admin", "a.relayed.token", List.of()));
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
