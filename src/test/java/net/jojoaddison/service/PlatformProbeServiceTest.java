package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import net.jojoaddison.domain.PlatformService;
import net.jojoaddison.domain.enumeration.ServiceHealth;
import org.junit.jupiter.api.Test;

/**
 * What the probe concludes, against a socket that is really there and one that is not.
 *
 * <p>A unit test rather than an IT, because the interesting part is the mapping from "did it
 * connect, and how long did it take" onto {@code health} and {@code responseMs} — the repository
 * round trip adds nothing to that and would need a database to say it.
 *
 * <p>The listening case opens a real {@link ServerSocket} on an ephemeral port. Mocking the socket
 * would leave the one thing worth checking untested: that a connect to something that exists
 * succeeds, and to something that does not fails inside the timeout rather than hanging the request
 * an operator is watching.
 */
class PlatformProbeServiceTest {

    private final PlatformProbeService service = new PlatformProbeService(null);

    private static PlatformService platformService(String host, int port) {
        return new PlatformService().name("Under test").host(host).port(port).plane("ADMIN").health(ServiceHealth.DOWN).responseMs(999);
    }

    @Test
    void shouldReportAListeningServiceAsHealthy() throws IOException {
        try (ServerSocket listening = new ServerSocket(0)) {
            PlatformService probed = service.measure(platformService("127.0.0.1", listening.getLocalPort()));

            assertThat(probed.getHealth()).isEqualTo(ServiceHealth.HEALTHY);
            assertThat(probed.getResponseMs()).isNotNull().isLessThanOrEqualTo(PlatformProbeService.DEGRADED_ABOVE_MS);
            assertThat(probed.getLastProbedAt()).isNotNull();
        }
    }

    /**
     * A host that does not resolve is DOWN from here, which is the answer the screen asks for.
     *
     * <p>`.invalid` is reserved by RFC 2606 precisely so a name is guaranteed not to resolve — a
     * made-up hostname could be answered by a wildcard DNS somewhere and pass.
     */
    @Test
    void shouldReportAnUnreachableServiceAsDown() {
        PlatformService probed = service.measure(platformService("nothing-here.invalid", 5507));

        assertThat(probed.getHealth()).isEqualTo(ServiceHealth.DOWN);
        assertThat(probed.getLastProbedAt()).isNotNull();
    }

    /**
     * A DOWN row must not keep the response time it had when it was up.
     *
     * <p>Left in place, the screen shows "DOWN · 999 ms" — a number that reads as a measurement of
     * something that did not answer, and that feeds the median on the same page.
     */
    @Test
    void shouldClearTheResponseTimeOfSomethingThatDidNotAnswer() {
        PlatformService probed = service.measure(platformService("nothing-here.invalid", 5507));

        assertThat(probed.getResponseMs()).isNull();
    }

    /** A missing host is not an exception; it is a service that cannot be reached. */
    @Test
    void shouldTreatAMissingHostAsDown() {
        PlatformService probed = service.measure(new PlatformService().name("Half a record").port(1234));

        assertThat(probed.getHealth()).isEqualTo(ServiceHealth.DOWN);
    }
}
