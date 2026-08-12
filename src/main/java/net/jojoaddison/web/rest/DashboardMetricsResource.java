package net.jojoaddison.web.rest;

import net.jojoaddison.service.DashboardMetricsService;
import net.jojoaddison.service.dto.DashboardMetricsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/dashboard/metrics} — the console's dashboard figures.
 *
 * <p>Singular {@code dashboard}, and deliberately so: {@link DashboardResource} owns
 * {@code /api/dashboards}, the CRUD surface for saved dashboard documents. This is a different
 * thing — one computed summary, no entity behind it — and the client has addressed it at this path
 * since it was written.
 *
 * <p>Read-only, so no {@code POST}/{@code PUT} shape to keep. Authorisation is the blanket rule in
 * {@code SecurityConfiguration}: {@code GET /api/**} is admin-or-operator, which is right for a
 * screen operators are expected to watch.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardMetricsResource {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardMetricsResource.class);

    private final DashboardMetricsService dashboardMetricsService;

    public DashboardMetricsResource(DashboardMetricsService dashboardMetricsService) {
        this.dashboardMetricsService = dashboardMetricsService;
    }

    /**
     * {@code GET /metrics} : the whole dashboard in one response.
     *
     * <p>One call rather than a dozen: the dashboard renders as a unit, and a screen that fires
     * fourteen requests on load is fourteen chances to half-render.
     *
     * @return {@code 200 OK} with the metrics.
     */
    @GetMapping("/metrics")
    public ResponseEntity<DashboardMetricsDTO> getMetrics() {
        LOG.debug("REST request to get dashboard metrics");
        return ResponseEntity.ok(dashboardMetricsService.metrics());
    }
}
