package net.jojoaddison.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.PlatformService;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.ShiftAssignment;
import net.jojoaddison.domain.enumeration.ServiceHealth;
import net.jojoaddison.service.dto.DashboardMetricsDTO;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Counts the console's dashboard figures.
 *
 * <p>Written with {@code count()} and narrow queries rather than {@code findAll()} and streams. The
 * collections are small today, which is exactly when an unbounded read looks harmless — nine list
 * endpoints in this service were written that way and had to be paginated later. The only documents
 * loaded whole are the ones actually rendered: degraded services, and the case-load rows.
 *
 * <p>Where the domain has no source for a figure, the figure is empty. See
 * {@link DashboardMetricsDTO} for why that is deliberate.
 */
@Service
public class DashboardMetricsService {

    /**
     * The platform services whose reporting defines uptime. Same six the sync script catalogues in
     * hc-admin-ci; kept as a pattern rather than a list because PromQL wants one.
     */
    private static final String PLATFORM_SERVICES = "hc-(admin|patient|professional)-(gateway|service)";

    private static final int PLATFORM_SERVICE_COUNT = 6;

    /**
     * Days of uptime to report.
     *
     * <p>Not thirty. Mimir keeps 15 days, so a 30-day window would be computed from the days that
     * exist and captioned as if it covered the rest. Seven sits inside retention with room for the
     * store to have been restarted recently.
     */
    private static final int UPTIME_WINDOW_DAYS = 7;

    /** hc-admin, hc-patient, hc-professional. Fewer reporting means the answer is not known. */
    private static final int EXPECTED_DATABASES = 3;

    /** Statuses. Deliberately the prototype's vocabulary, so the panel reads the same. */
    private static final String LIVE = "Live";
    private static final String OFFLINE = "Offline";
    private static final String HEALTHY = "Healthy";
    private static final String UNAVAILABLE = "Unavailable";
    private static final String UNKNOWN = "Unknown";
    private static final String BETA = "Beta";

    /** How many months of message volume the chart shows. */
    private static final int VOLUME_MONTHS = 6;

    /** How many professionals the case-load table lists, busiest first. */
    private static final int CASE_LOAD_ROWS = 8;

    private final MongoTemplate mongoTemplate;
    private final ObservabilityClient observability;

    public DashboardMetricsService(MongoTemplate mongoTemplate, ObservabilityClient observability) {
        this.mongoTemplate = mongoTemplate;
        this.observability = observability;
    }

    public DashboardMetricsDTO metrics() {
        return new DashboardMetricsDTO(
            networkTotals(false),
            networkTotals(true),
            count(Message.class, Criteria.where("status").is("NEW")),
            count(net.jojoaddison.domain.Task.class, Criteria.where("state").in("TODO", "DOING")),
            count(Professional.class, Criteria.where("verification").is("PENDING")),
            roster(),
            degradedServices(),
            platformServiceTotals(),
            messageVolume(),
            accountMix(),
            caseLoad(),
            Map.of(),
            capabilities(),
            uptime()
        );
    }

    /**
     * @param activeOnly when true, exclude archived records.
     *     <p>Archived is stored as {@code is_archived: true}, and legacy documents predate the field
     *     entirely — so "active" must be {@code $ne: true}, not {@code false}, or every record
     *     written before the field existed silently disappears from the totals. Same reasoning as
     *     the repositories' {@code findNotArchived}.
     */
    private DashboardMetricsDTO.NetworkTotals networkTotals(boolean activeOnly) {
        Criteria criteria = activeOnly ? Criteria.where("is_archived").ne(true) : null;
        return new DashboardMetricsDTO.NetworkTotals(
            count(net.jojoaddison.domain.Patient.class, criteria),
            count(Professional.class, criteria),
            count(net.jojoaddison.domain.Vendor.class, criteria)
        );
    }

    private DashboardMetricsDTO.RosterSummary roster() {
        LocalDate monday = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);
        Criteria week = Criteria.where("shift_date").gte(monday).lte(sunday);

        List<ShiftAssignment> assignments = mongoTemplate.find(new Query(week), ShiftAssignment.class);
        long total = assignments.size();
        long unassigned = assignments.stream().filter(a -> a.getProfessional() == null).count();
        long staff = assignments
            .stream()
            .map(ShiftAssignment::getProfessional)
            .filter(Objects::nonNull)
            .map(Professional::getId)
            .filter(Objects::nonNull)
            .distinct()
            .count();

        // 0% for an empty week. An uncovered roster is not a fully covered one, and dividing by zero
        // should not be resolved by whichever default reads better on a card.
        int coverPercent = total == 0 ? 0 : (int) Math.round(((double) (total - unassigned) / total) * 100);
        return new DashboardMetricsDTO.RosterSummary(coverPercent, unassigned, staff, total);
    }

    private List<DashboardMetricsDTO.DegradedService> degradedServices() {
        Query query = new Query(Criteria.where("health").ne(ServiceHealth.HEALTHY.name()));
        return mongoTemplate
            .find(query, PlatformService.class)
            .stream()
            .map(s -> new DashboardMetricsDTO.DegradedService(s.getId(), s.getName(), s.getHost(), s.getPort()))
            .toList();
    }

    private DashboardMetricsDTO.PlatformServiceTotals platformServiceTotals() {
        return new DashboardMetricsDTO.PlatformServiceTotals(
            count(PlatformService.class, null),
            count(PlatformService.class, Criteria.where("health").is(ServiceHealth.HEALTHY.name()))
        );
    }

    /**
     * Message counts for the last {@value #VOLUME_MONTHS} months, oldest first.
     *
     * <p>Months with no messages are present with a count of zero. A chart that omits empty months
     * compresses the gap and draws a trend that did not happen.
     */
    private List<DashboardMetricsDTO.MonthCount> messageVolume() {
        YearMonth thisMonth = YearMonth.now(ZoneOffset.UTC);
        List<DashboardMetricsDTO.MonthCount> volume = new ArrayList<>(VOLUME_MONTHS);
        for (int back = VOLUME_MONTHS - 1; back >= 0; back--) {
            YearMonth month = thisMonth.minusMonths(back);
            Criteria window = Criteria
                .where("sent_at")
                .gte(month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant())
                .lt(month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant());
            volume.add(new DashboardMetricsDTO.MonthCount(month.toString(), count(Message.class, window)));
        }
        return volume;
    }

    /** Professionals by role. Roles with nobody in them are omitted rather than shown as zero slices. */
    private List<DashboardMetricsDTO.KeyCount> accountMix() {
        return mongoTemplate
            .find(new Query(Criteria.where("role").exists(true)), Professional.class)
            .stream()
            .filter(p -> p.getRole() != null)
            .collect(Collectors.groupingBy(p -> p.getRole().name(), Collectors.counting()))
            .entrySet()
            .stream()
            .map(e -> new DashboardMetricsDTO.KeyCount(e.getKey(), e.getValue()))
            .sorted(Comparator.comparingLong(DashboardMetricsDTO.KeyCount::value).reversed())
            .toList();
    }

    /**
     * The busiest professionals by recorded case count.
     *
     * <p>{@code caseCount} and {@code visitCount} are stored on the professional rather than counted
     * from cases, because this service holds no case collection — they arrive from
     * hc-professional-service. They are reported as found; a null means not reported, not zero.
     */
    private List<DashboardMetricsDTO.CaseLoadRow> caseLoad() {
        return mongoTemplate
            .find(new Query(Criteria.where("case_count").exists(true)), Professional.class)
            .stream()
            .filter(p -> p.getCaseCount() != null)
            .sorted(Comparator.comparingInt(Professional::getCaseCount).reversed())
            .limit(CASE_LOAD_ROWS)
            .map(p -> new DashboardMetricsDTO.CaseLoadRow(p.getId(), displayName(p), p.getCaseCount(), p.getVisitCount()))
            .toList();
    }

    /** A professional's name comes from the linked profile; falls back to the licence number, then the id. */
    private static String displayName(Professional professional) {
        if (professional.getProfile() != null) {
            String first = professional.getProfile().getFirstName();
            String last = professional.getProfile().getLastName();
            String name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
            if (!name.isEmpty()) {
                return name;
            }
        }
        return professional.getLicenceNumber() != null ? professional.getLicenceNumber() : professional.getId();
    }

    private long count(Class<?> type, Criteria criteria) {
        return mongoTemplate.count(criteria == null ? new Query() : new Query(criteria), type);
    }

    /**
     * Portion of the window in which every catalogued service was reporting.
     *
     * <p>{@code or vector(0)} is the load-bearing part. A service that stops reporting produces no
     * samples at all, and {@code avg_over_time} skips absent points rather than treating them as
     * zero — so without it an outage would be averaged out of existence and a dead platform could
     * report 100%. The fallback makes every gap count as nothing reporting, which is what an outage
     * is.
     *
     * <p>{@code count by (job)} before {@code count} matters too: each service emits dozens of
     * {@code jvm_memory_used_bytes} series, and counting those instead of jobs gives a number many
     * times the service count. The first draft of this query returned 767%.
     */
    private DashboardMetricsDTO.Uptime uptime() {
        String promql =
            "avg_over_time((count(count by (job) (jvm_memory_used_bytes{job=~\"" +
            PLATFORM_SERVICES +
            "\"})) or vector(0))[" +
            UPTIME_WINDOW_DAYS +
            "d:5m]) / " +
            PLATFORM_SERVICE_COUNT +
            " * 100";
        Double percent = observability.instant(promql).map(v -> Math.round(v * 100) / 100.0).orElse(null);
        return new DashboardMetricsDTO.Uptime(percent, UPTIME_WINDOW_DAYS);
    }

    /**
     * Three capabilities read from the running platform, one that has no signal.
     *
     * <p>Each returns Unknown rather than Live when the metrics store cannot be reached. That is the
     * point of the change: a capability panel that claims "Live" while nothing is checked is
     * decoration, and decoration on an operations screen is worse than an empty space.
     */
    private List<DashboardMetricsDTO.PlatformCapability> capabilities() {
        return List.of(
            new DashboardMetricsDTO.PlatformCapability("Realtime message notification", "bell", kafkaStatus()),
            new DashboardMetricsDTO.PlatformCapability("Long term persistence storage", "save", databaseStatus()),
            new DashboardMetricsDTO.PlatformCapability("Metric visualization", "report", grafanaStatus()),
            // No signal exists for this one. Saying so is the honest option; the alternative is a
            // badge that means nothing sitting beside three that mean something.
            new DashboardMetricsDTO.PlatformCapability("AI & ML analysis", "star", BETA)
        );
    }

    /**
     * Kafka, via its clients rather than the broker.
     *
     * <p>Kafka is not a scrape target here, but every service that uses it reports
     * {@code kafka_consumer_connection_count}. Connections open means a broker is accepting them,
     * which is the question "is realtime notification working" actually asks — a broker that is
     * running but unreachable from the services is not a working capability.
     */
    private String kafkaStatus() {
        return observability
            .instant("sum(kafka_consumer_connection_count)")
            .map(connections -> connections > 0 ? LIVE : OFFLINE)
            .orElse(UNKNOWN);
    }

    /**
     * Every Health Connect database, or the capability is not healthy.
     *
     * <p>{@code min(up)} over the three stores: one exporter reporting 0 drops the whole capability,
     * which is the intent — "long term persistence" is not partially true. Counted as well as
     * min-ed, because a query matching zero series would otherwise look identical to one matching
     * three healthy ones.
     */
    private String databaseStatus() {
        // orElse(0) rather than isEmpty()/get(): absent and "fewer than expected" lead to the same
        // answer here, so collapsing them keeps the two cases from drifting apart later.
        double counted = observability.instant("count(up{job=\"mongodb\", database=~\"hc-.*\"})").orElse(0d);
        if (counted < EXPECTED_DATABASES) {
            return UNKNOWN;
        }
        return observability
            .instant("min(up{job=\"mongodb\", database=~\"hc-.*\"})")
            .map(min -> min >= 1 ? HEALTHY : UNAVAILABLE)
            .orElse(UNKNOWN);
    }

    private String grafanaStatus() {
        return observability.grafanaReady().map(ready -> ready ? LIVE : OFFLINE).orElse(UNKNOWN);
    }
}
