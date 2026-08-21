package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jojoaddison.service.dto.DashboardMetricsDTO;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * The capability panel reads the running platform, and says so when it cannot.
 *
 * <p>These four badges were hardcoded to "Live"/"Healthy"/"Beta" — decoration on an operations
 * screen, true whether or not anything was running. They are derived now, and the case that matters
 * most is the one where the metrics store is unreachable: the answer is <em>Unknown</em>, never
 * "Live". A panel that claims a capability works when nothing has been checked is worse than a blank
 * one, because someone will believe it.
 *
 * <p>Driven through a stubbed {@link ObservabilityClient} rather than a live Mimir, so the mapping
 * from signal to badge is pinned independently of whether a metrics stack exists.
 */
class DashboardMetricsCapabilityTest {

    private static DashboardMetricsDTO metricsWith(ObservabilityClient observability) {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.count(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.<Class<?>>any())).thenReturn(0L);
        when(mongo.find(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.<Class<?>>any())).thenReturn(List.of());
        // No roster week, which is production's state and keeps this test about capabilities.
        CurrentRosterWeekService rosterWeek = mock(CurrentRosterWeekService.class);
        when(rosterWeek.inForce()).thenReturn(Optional.empty());
        return new DashboardMetricsService(mongo, observability, rosterWeek, Clock.systemUTC()).metrics();
    }

    private static String statusOf(DashboardMetricsDTO metrics, String capability) {
        return metrics
            .capabilities()
            .stream()
            .filter(c -> c.name().equals(capability))
            .map(DashboardMetricsDTO.PlatformCapability::status)
            .findFirst()
            .orElseThrow();
    }

    private static ObservabilityClient stub(Map<String, Double> answers, Optional<Boolean> grafana) {
        ObservabilityClient client = mock(ObservabilityClient.class);
        when(client.instant(anyString())).thenReturn(Optional.empty());
        answers.forEach((fragment, value) -> when(client.instant(contains(fragment))).thenReturn(Optional.of(value)));
        when(client.grafanaReady()).thenReturn(grafana);
        return client;
    }

    // --- the signal is present ---------------------------------------------------------------------

    @Test
    void kafkaWithOpenConnectionsIsLive() {
        var metrics = metricsWith(stub(Map.of("kafka_consumer_connection_count", 24.0), Optional.of(true)));
        assertThat(statusOf(metrics, "Realtime message notification")).isEqualTo("Live");
    }

    @Test
    void kafkaWithNoConnectionsIsOffline() {
        var metrics = metricsWith(stub(Map.of("kafka_consumer_connection_count", 0.0), Optional.of(true)));
        assertThat(statusOf(metrics, "Realtime message notification")).isEqualTo("Offline");
    }

    @Test
    void everyDatabaseUpIsHealthy() {
        var metrics = metricsWith(stub(Map.of("count(up{job=\"mongodb\"", 3.0, "min(up{job=\"mongodb\"", 1.0), Optional.of(true)));
        assertThat(statusOf(metrics, "Long term persistence storage")).isEqualTo("Healthy");
    }

    /**
     * One store down takes the whole capability down. "Long term persistence" is not partially true.
     */
    @Test
    void oneDatabaseDownMakesPersistenceUnavailable() {
        var metrics = metricsWith(stub(Map.of("count(up{job=\"mongodb\"", 3.0, "min(up{job=\"mongodb\"", 0.0), Optional.of(true)));
        assertThat(statusOf(metrics, "Long term persistence storage")).isEqualTo("Unavailable");
    }

    /**
     * Fewer databases than expected is not the same as all of them being healthy — a query matching
     * nothing would otherwise be indistinguishable from three healthy stores.
     */
    @Test
    void fewerDatabasesThanExpectedIsUnknown() {
        var metrics = metricsWith(stub(Map.of("count(up{job=\"mongodb\"", 1.0, "min(up{job=\"mongodb\"", 1.0), Optional.of(true)));
        assertThat(statusOf(metrics, "Long term persistence storage")).isEqualTo("Unknown");
    }

    @Test
    void grafanaAnsweringItsHealthEndpointIsLive() {
        var metrics = metricsWith(stub(Map.of(), Optional.of(true)));
        assertThat(statusOf(metrics, "Metric visualization")).isEqualTo("Live");
    }

    @Test
    void grafanaRefusingConnectionsIsOffline() {
        var metrics = metricsWith(stub(Map.of(), Optional.of(false)));
        assertThat(statusOf(metrics, "Metric visualization")).isEqualTo("Offline");
    }

    // --- no observability stack at all -------------------------------------------------------------

    /**
     * The case this whole design turns on. Dev, CI and any deployment without a metrics stack land
     * here, and none of the three signals may claim to be Live.
     */
    @Test
    void withoutAMetricsStackNothingClaimsToBeLive() {
        var metrics = metricsWith(stub(Map.of(), Optional.empty()));

        assertThat(statusOf(metrics, "Realtime message notification")).isEqualTo("Unknown");
        assertThat(statusOf(metrics, "Long term persistence storage")).isEqualTo("Unknown");
        assertThat(statusOf(metrics, "Metric visualization")).isEqualTo("Unknown");
    }

    @Test
    void uptimeIsAbsentRatherThanZeroWithoutAMetricsStack() {
        var metrics = metricsWith(stub(Map.of(), Optional.empty()));

        // Zero would render as "0% uptime", which reads as a total outage rather than "not measured".
        assertThat(metrics.uptime().percent()).isNull();
        assertThat(metrics.uptime().windowDays()).isEqualTo(7);
    }

    /**
     * The window travels with the figure so a caption cannot claim a period nobody measured — which
     * is what "Uptime, 30 days" over a hardcoded 99.94% did in the prototype.
     */
    @Test
    void uptimeCarriesTheWindowItWasMeasuredOver() {
        var metrics = metricsWith(stub(Map.of("avg_over_time", 99.88), Optional.of(true)));

        assertThat(metrics.uptime().percent()).isEqualTo(99.88);
        assertThat(metrics.uptime().windowDays()).isEqualTo(7);
    }

    /** AI &amp; ML has no signal anywhere, and the badge says Beta rather than pretending. */
    @Test
    void theCapabilityWithNoSignalDoesNotClaimToBeLive() {
        var metrics = metricsWith(stub(Map.of(), Optional.of(true)));
        assertThat(statusOf(metrics, "AI & ML analysis")).isEqualTo("Beta");
    }
}
