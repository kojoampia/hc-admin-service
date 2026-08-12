package net.jojoaddison.service;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Reads the observability stack, for the figures the domain database cannot answer.
 *
 * <p>Uptime, whether Kafka is carrying traffic, and whether the databases are healthy are all facts
 * about the running platform rather than about any document in Mongo. Mimir already holds them —
 * every Health Connect service pushes OpenTelemetry metrics into it — so this asks Mimir instead of
 * inventing a second, disagreeing source of truth.
 *
 * <p><strong>Every method fails soft, and that is the whole design.</strong> Dev, test and CI have no
 * Mimir and no Grafana. An unreachable metrics store must degrade the dashboard, never break it: a
 * failed query returns empty, the caller reports the capability as unknown, and the screen shows
 * less rather than nothing. A metrics store outage taking down the admin console with it would be a
 * worse bug than the blank panel this replaced.
 *
 * <p>Disabled unless {@code observability.mimir.url} is set, so the default posture — including every
 * test in this repository — is "no observability stack, and that is fine".
 */
@Service
public class ObservabilityClient {

    private static final Logger LOG = LoggerFactory.getLogger(ObservabilityClient.class);

    /** Short: this sits in the request path of a dashboard someone is waiting on. */
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final RestClient mimir;
    private final RestClient grafana;

    public ObservabilityClient(
        @Value("${observability.mimir.url:}") String mimirUrl,
        @Value("${observability.grafana.url:}") String grafanaUrl
    ) {
        this.mimir = mimirUrl.isBlank() ? null : client(mimirUrl);
        this.grafana = grafanaUrl.isBlank() ? null : client(grafanaUrl);
        if (this.mimir == null) {
            LOG.info("observability.mimir.url is not set — platform figures will be reported as unknown");
        }
    }

    private static RestClient client(String baseUrl) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) TIMEOUT.toMillis());
        factory.setReadTimeout((int) TIMEOUT.toMillis());
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public boolean isEnabled() {
        return mimir != null;
    }

    /**
     * Runs an instant PromQL query and returns the first sample's value.
     *
     * <p>An empty result is {@link Optional#empty()} rather than zero, and the distinction matters
     * everywhere this is used: PromQL returns nothing for a series that does not exist, which is not
     * the same as a series whose value is 0. Reporting "no data" as "zero connections" would turn a
     * missing exporter into a false outage.
     */
    public Optional<Double> instant(String promql) {
        if (mimir == null) {
            return Optional.empty();
        }
        try {
            JsonNode body = mimir
                .get()
                .uri(uriBuilder -> uriBuilder.path("/prometheus/api/v1/query").queryParam("query", promql).build())
                .retrieve()
                .body(JsonNode.class);

            JsonNode result = body == null ? null : body.path("data").path("result");
            if (result == null || !result.isArray() || result.isEmpty()) {
                return Optional.empty();
            }
            JsonNode value = result.get(0).path("value");
            if (!value.isArray() || value.size() < 2) {
                return Optional.empty();
            }
            String raw = value.get(1).asString();
            if ("NaN".equals(raw)) {
                return Optional.empty();
            }
            return Optional.of(Double.parseDouble(raw));
        } catch (Exception e) {
            // Debug, not warn. This is expected in every environment without a metrics store, and a
            // dashboard request that logs a warning per query would bury real problems.
            LOG.debug("Mimir query failed [{}]: {}", promql, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Whether Grafana reports itself ready.
     *
     * <p>A direct probe rather than a Mimir query, because Grafana is not a scrape target — nothing
     * in the metrics store knows anything about it. Its {@code /api/health} answers
     * {@code {"database":"ok"}} when it can serve, which is exactly the question being asked.
     */
    public Optional<Boolean> grafanaReady() {
        if (grafana == null) {
            return Optional.empty();
        }
        try {
            JsonNode body = grafana.get().uri("/api/health").retrieve().body(JsonNode.class);
            return Optional.of(body != null && "ok".equalsIgnoreCase(body.path("database").asString("")));
        } catch (Exception e) {
            // Reachable-but-unhealthy and unreachable are different answers: a refused connection
            // means Grafana is down, which is a real "false", not an absence of information.
            LOG.debug("Grafana health probe failed: {}", e.getMessage());
            return Optional.of(false);
        }
    }
}
