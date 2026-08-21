package net.jojoaddison.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.jojoaddison.domain.PlatformService;
import net.jojoaddison.domain.enumeration.ServiceHealth;
import net.jojoaddison.repository.PlatformServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Re-checks one platform service, on demand, from where this service is standing.
 *
 * <p>Item 22: the platform-health screen was read-only, so a service that recovered could not be
 * re-checked from the console — the row kept whatever `health` it was last written with until
 * somebody edited the document.
 *
 * <p><b>A TCP connect, not an HTTP health call.</b> The thirteen rows are not all HTTP: `hc-kafka`
 * on 9092 speaks its own protocol, and the gateways answer `/management/health` on paths this
 * service has no business knowing. What every row does have is a host and a port, and what the
 * screen claims about each is reachability and response time. Connecting is exactly that claim and
 * nothing more.
 *
 * <p><b>It reports what it found, including when that is bad news.</b> A host that does not resolve
 * is DOWN from here, and the answer is stamped with {@link PlatformService#getLastProbedAt()} so a
 * measurement is never confused with the seed. On a workstation or the quality stack most of these
 * hostnames belong to containers that are not running, so probing there will honestly return DOWN
 * for rows that were seeded HEALTHY. That is the probe working, not failing — and the timestamp is
 * what lets a reader tell the difference.
 */
@Service
public class PlatformProbeService {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformProbeService.class);

    /**
     * How long to wait for a connection before calling it down.
     *
     * <p>Short on purpose: this runs inside a request an operator is watching, and thirteen rows
     * probed one after another at a generous timeout is a screen that appears to hang. A service
     * that cannot accept a connection in two seconds is not healthy by this screen's definition.
     */
    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

    /**
     * Above this, a reachable service is DEGRADED rather than HEALTHY.
     *
     * <p>The seeded outlier — the vendor gateway at 212ms against twelve rows between 12 and 92 — is
     * the case this threshold is drawn around, and it is the row the fixture already marks DEGRADED.
     */
    static final int DEGRADED_ABOVE_MS = 150;

    private final PlatformServiceRepository platformServiceRepository;

    public PlatformProbeService(PlatformServiceRepository platformServiceRepository) {
        this.platformServiceRepository = platformServiceRepository;
    }

    /**
     * Probes the service with this id and stores what came back.
     *
     * @return the updated service, or empty when no service has that id.
     */
    public Optional<PlatformService> probe(String id) {
        return platformServiceRepository.findById(id).map(service -> platformServiceRepository.save(measure(service)));
    }

    /** Separated from the save so the timing rules can be tested without a database. */
    PlatformService measure(PlatformService service) {
        long startedAt = System.nanoTime();
        boolean reachable = connects(service.getHost(), service.getPort());
        int elapsedMs = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedAt) / 1_000_000);

        service.setLastProbedAt(Instant.now());
        if (!reachable) {
            // The elapsed time of a failure is how long the timeout took, which says nothing about
            // the service. Leaving the previous figure would be worse: a DOWN row reading 37ms.
            service.setResponseMs(null);
            service.setHealth(ServiceHealth.DOWN);
            return service;
        }
        service.setResponseMs(elapsedMs);
        service.setHealth(elapsedMs > DEGRADED_ABOVE_MS ? ServiceHealth.DEGRADED : ServiceHealth.HEALTHY);
        return service;
    }

    private boolean connects(String host, Integer port) {
        if (host == null || port == null) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) CONNECT_TIMEOUT.toMillis());
            return true;
        } catch (IOException | RuntimeException e) {
            // Every failure is the same answer to this screen's question — unresolvable host,
            // refused connection, timeout. Logged at debug because an operator probing a service
            // they know is down should not fill the log with warnings.
            LOG.debug("Probe of {}:{} did not connect: {}", host, port, e.toString());
            return false;
        }
    }
}
