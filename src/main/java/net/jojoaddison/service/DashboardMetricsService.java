package net.jojoaddison.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.PlatformService;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.RosterWeek;
import net.jojoaddison.domain.ShiftAssignment;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.ServiceHealth;
import net.jojoaddison.domain.enumeration.ShiftType;
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

    /** A roster week is seven columns wide, on both sides of this contract. */
    private static final int DAYS_IN_WEEK = 7;

    /** How many months of message volume the chart shows. */
    private static final int VOLUME_MONTHS = 6;

    /** How many professionals the case-load table lists, busiest first. */
    private static final int CASE_LOAD_ROWS = 8;

    private final MongoTemplate mongoTemplate;
    private final ObservabilityClient observability;
    private final CurrentRosterWeekService currentRosterWeek;
    private final Clock clock;

    public DashboardMetricsService(
        MongoTemplate mongoTemplate,
        ObservabilityClient observability,
        CurrentRosterWeekService currentRosterWeek,
        Clock clock
    ) {
        this.mongoTemplate = mongoTemplate;
        this.observability = observability;
        this.currentRosterWeek = currentRosterWeek;
        this.clock = clock;
    }

    public DashboardMetricsDTO metrics() {
        // Counted once and passed to both the payload and the account-mix chart. The chart is a
        // breakdown of exactly this number, so deriving it from the same object is what stops the
        // two disagreeing — the tiles say 12 patients and a chart beside them saying 10 is the
        // failure this dashboard has already had with roster cover.
        DashboardMetricsDTO.NetworkTotals network = networkTotals(false);
        return new DashboardMetricsDTO(
            network,
            networkTotals(true),
            count(Message.class, Criteria.where("status").is("NEW")),
            count(net.jojoaddison.domain.Task.class, Criteria.where("state").in("TODO", "DOING")),
            count(Professional.class, Criteria.where("verification").is("PENDING")),
            roster(),
            degradedServices(),
            platformServiceTotals(),
            messageVolume(),
            accountMix(network),
            caseLoad(),
            sparklines(),
            deltas(),
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

    /**
     * The roster figures, computed the way the duty-roster grid computes them.
     *
     * <p><b>This has to match {@code console/duty-roster} exactly, because the two sit one click
     * apart</b> and the hero sentence quotes one of these numbers. It did not: the hero said "roster
     * cover at 0% for the week" while the grid said 80%, and the reason is the definition of an
     * unassigned slot.
     *
     * <p><b>An unassigned slot is the absence of a ShiftAssignment, not a document with no
     * professional on it.</b> Cycling a grid cell past OFF deletes the assignment — that is what
     * makes the count a subtraction — so nothing in this collection ever carries a null professional.
     * The old formula counted those, found none by construction, and divided by the documents it did
     * find: it could return 100% for any week with an assignment in it and 0% for a week with none,
     * and no third value existed. The 0% in the gap analysis was not missing data, it was the only
     * other number the arithmetic could produce.
     *
     * <p>So capacity comes from the grid's own shape — a row per rosterable professional, seven days
     * — and coverage is how much of that grid has been planned:
     *
     * <ul>
     *   <li><b>capacity</b> = rosterable professionals × 7. Rosterable excludes {@code PENDING}
     *       applicants, which is what the grid excludes; the generated seed rosters them anyway, so
     *       counting their assignments would push a full week past 100%.</li>
     *   <li><b>unassigned</b> = capacity − planned. A subtraction, for the reason above.</li>
     *   <li><b>rosteredStaff</b> = the grid's row count, not "professionals who happen to have a
     *       shift". Somebody with a completely empty week is precisely who needs rostering, and
     *       counting only the assigned would hide them.</li>
     *   <li><b>shiftsThisWeek</b> = planned minus OFF. OFF is planned, but it is not a shift — the
     *       same rule that decides what a shift is worth in {@code ShiftValuationService}.</li>
     * </ul>
     *
     * <p>The week is {@link CurrentRosterWeekService#inForce()}, which is also what the grid asks
     * for, rather than the Monday-to-Sunday window this used to derive from {@code shift_date}. Two
     * independently computed weeks is the same class of defect one level up.
     */
    private DashboardMetricsDTO.RosterSummary roster() {
        RosterWeek inForce = currentRosterWeek.inForce().orElse(null);
        if (inForce == null) {
            // No roster at all — production's normal state. Zero, and no week to name.
            return new DashboardMetricsDTO.RosterSummary(0, 0L, 0L, 0L, null, null);
        }

        Set<String> rosterable = mongoTemplate
            .find(new Query(Criteria.where("status").ne(AccountStatus.PENDING.name())), Professional.class)
            .stream()
            .map(Professional::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        // `week.id`, not `week.$id`: a DBRef stores { $ref, $id }, so `$id` is the real field, but
        // writing it literally bypasses the query mapper that converts the String to what is stored
        // and matches nothing. Same note as ShiftAssignmentResource's filter, and the same symptom —
        // an empty roster that reads exactly like a quiet week.
        List<ShiftAssignment> assignments = mongoTemplate.find(
            new Query(Criteria.where("week.id").is(inForce.getId())),
            ShiftAssignment.class
        );

        // <b>By cell, not by document.</b> Nothing stops two assignments existing for one
        // professional on one day, and the quality stack had exactly that — a stray `DAY` beside the
        // seeded `EVENING` for p1 on Monday, left by a click on the grid. The grid resolves a cell
        // with `find`, so the first match wins and it renders 49 filled of 49; counting documents
        // gave 50 of 49 and put <b>102%</b> on the dashboard. Two readings of one roster is the
        // defect this method exists to close, so the numerator has to be what the grid draws.
        //
        // Keyed on (professional, dayIndex) rather than deduplicated by id: it is one cell that has
        // been filled twice, and a Set of cells cannot exceed the capacity computed from the same
        // two dimensions. Over-100% stops being possible rather than being clamped away.
        Map<String, ShiftAssignment> planned = new LinkedHashMap<>();
        for (ShiftAssignment assignment : assignments) {
            Professional professional = assignment.getProfessional();
            if (professional == null || !rosterable.contains(professional.getId()) || assignment.getDayIndex() == null) {
                continue;
            }
            planned.putIfAbsent(professional.getId() + "|" + assignment.getDayIndex(), assignment);
        }

        long capacity = (long) rosterable.size() * DAYS_IN_WEEK;
        long unassigned = Math.max(0, capacity - planned.size());
        long worked = planned.values().stream().filter(a -> a.getShift() != ShiftType.OFF).count();

        // 0% for an empty grid. An uncovered roster is not a fully covered one, and dividing by zero
        // should not be resolved by whichever default reads better on a card.
        int coverPercent = capacity == 0 ? 0 : (int) Math.round(((double) planned.size() / capacity) * 100);
        return new DashboardMetricsDTO.RosterSummary(
            coverPercent,
            unassigned,
            (long) rosterable.size(),
            worked,
            inForce.getLabel(),
            inForce.getStartDate()
        );
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
        YearMonth thisMonth = YearMonth.now(clock);
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

    /**
     * The trend line inside each KPI tile.
     *
     * <p>A sparkline here means one thing: <b>the tile's own number, over the last six months,
     * ending at the number printed above it.</b> The prototype's four series are literals that each
     * end at their tile's value — {@code [98,102,105,109,112,116]} under a count of 116 — and a line
     * whose last point disagreed with the figure beside it would be worse than no line at all. The
     * final point is therefore the same count the tile renders, and {@code SparklinesIT} asserts it.
     *
     * <p><b>Two of them are totals and two are backlogs, and they are counted differently.</b>
     * Patients and professionals both carry {@code joined_on}, so their running total at any past
     * month end is a fact — a line that only rises. Unread messages and open tasks are backlogs:
     * what was outstanding <em>at</em> that month end, which rises and falls.
     *
     * <p>The two backlogs had no series at all until item 14, and the reason is worth keeping.
     * Reconstructing a backlog needs to know when each item stopped being one, and nothing recorded
     * it — no read time on a message, no closed time on a task. Inflow could have been counted and
     * would have drawn a plausible line, but "messages that arrived" is a different quantity from
     * "messages still unread", and a wrong trend under a right number is the fabricated-figure
     * failure the in-browser mock was deleted for. So the fix was a domain change first:
     * {@code Message.readAt} and {@code Task.closedAt}, stamped server-side by their lifecycle
     * callbacks. <b>These two series are only as honest as those fields</b> — anything written
     * before they existed carries null and counts as never having left the backlog.
     */
    private Map<String, List<Integer>> sparklines() {
        return Map.of(
            "patients",
            cumulativeByMonth(net.jojoaddison.domain.Patient.class),
            "professionals",
            cumulativeByMonth(Professional.class),
            "messages",
            backlogByMonth(Message.class, "sent_at", "read_at"),
            "tasks",
            backlogByMonth(net.jojoaddison.domain.Task.class, "created_at", "closed_at")
        );
    }

    /**
     * What was still outstanding at the end of each of the last six months.
     *
     * <p>Open at a month end means: it had arrived by then, and it had not yet left — either it has
     * still not left, or it left afterwards. Both halves matter. Counting only "no closing time"
     * would draw today's backlog as a flat line across six months; counting only what arrived would
     * be the inflow chart that already sits beside these tiles.
     *
     * <p>The final bucket ends after today, so its value is the current backlog — the same number
     * the tile above prints, which is the property {@code SparklinesIT} asserts for every series.
     *
     * @param openedField when it entered the backlog ({@code sent_at}, {@code created_at})
     * @param closedField when it left, null while it has not ({@code read_at}, {@code closed_at})
     */
    private List<Integer> backlogByMonth(Class<?> collection, String openedField, String closedField) {
        YearMonth thisMonth = YearMonth.now(clock);
        List<Integer> series = new ArrayList<>(VOLUME_MONTHS);
        for (int back = VOLUME_MONTHS - 1; back >= 0; back--) {
            Instant monthEnd = thisMonth.minusMonths(back).atEndOfMonth().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            Criteria stillOpenThen = new Criteria()
                .andOperator(
                    Criteria.where(openedField).lt(monthEnd),
                    new Criteria().orOperator(Criteria.where(closedField).is(null), Criteria.where(closedField).gte(monthEnd))
                );
            series.add((int) count(collection, stillOpenThen));
        }
        return series;
    }

    /**
     * The figure under each KPI tile: item 14's "notes are copy, not measurements".
     *
     * <p>Every one is a count over the last seven days, and each is <b>the measurement its own
     * template names</b> rather than a generic delta the copy is free to reinterpret. The demo says
     * "+2 verified" under professionals; nothing here records when a professional was verified, so
     * this counts who joined instead and the string says so. Inventing a verification date to match
     * a caption would be the same failure in a different field.
     *
     * <ul>
     *   <li>{@code patients}, {@code professionals} — joined in the last seven days.
     *   <li>{@code messages} — arrived in the last seven days. Inflow, and labelled as inflow: the
     *       tile counts unread, and "3 arrived" beside "12 unread" is two honest numbers.
     *   <li>{@code tasks} — closed in the last seven days, which needs {@code closed_at} and is the
     *       second thing that field bought.
     * </ul>
     */
    private Map<String, Long> deltas() {
        LocalDate weekAgoDate = LocalDate.now(clock).minusDays(7);
        Instant weekAgo = weekAgoDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        return Map.of(
            "patients",
            count(net.jojoaddison.domain.Patient.class, Criteria.where("joined_on").gte(weekAgoDate)),
            "professionals",
            count(Professional.class, Criteria.where("joined_on").gte(weekAgoDate)),
            "messages",
            count(Message.class, Criteria.where("sent_at").gte(weekAgo)),
            "tasks",
            count(net.jojoaddison.domain.Task.class, Criteria.where("closed_at").gte(weekAgo))
        );
    }

    /**
     * How a collection's total reached its current size, at the end of each of the last six months.
     *
     * <p>Counted with a query per month rather than by loading the collection and bucketing it here:
     * six counts is cheaper than every document, and it keeps this method the same shape as
     * {@link #messageVolume()} beside it.
     *
     * <p>Everything is counted, archived included, because the tiles above these lines render
     * {@code network} and not {@code loaded}. Excluding archived records here would leave the last
     * point below the number it sits under.
     */
    private List<Integer> cumulativeByMonth(Class<?> collection) {
        YearMonth thisMonth = YearMonth.now(clock);
        List<Integer> series = new ArrayList<>(VOLUME_MONTHS);
        for (int back = VOLUME_MONTHS - 1; back >= 0; back--) {
            // Everything joined on or before the last day of that month — so the series is a running
            // total, not a per-month intake. The final bucket ends after today, which is what makes
            // the last point the current count.
            LocalDate monthEnd = thisMonth.minusMonths(back).atEndOfMonth();
            series.add((int) count(collection, Criteria.where("joined_on").lte(monthEnd)));
        }
        return series;
    }

    /**
     * Who holds an account on the platform: patients, professionals, vendors.
     *
     * <p>This grouped on {@code ProfessionalRole} until 2026-08-21 — nurses, paramedics, caregivers
     * — which is a breakdown of one of the three tiles above it rather than of the network, and it
     * answered a question the caption was not asking. Item 10 of {@code admin-gaps.md}; the roles
     * chart comes off the dashboard rather than moving, because the professional directory already
     * carries role tiles that filter, which is the better home for that question.
     *
     * <p><b>Derived from the same {@code NetworkTotals} the payload carries</b>, not counted again.
     * The chart is a breakdown of that number and a second count could drift from it — the same
     * mistake, one field apart, as the roster cover that disagreed with the roster.
     *
     * <p>Fixed order and all three always present, including zeros. The old shape sorted by size
     * descending and dropped empty groups, which reads well and quietly moves a segment's colour to
     * a different meaning the day two counts cross; and a platform with no vendors yet should show
     * "Vendors · 0" rather than a chart that has silently become two-segment.
     */
    private List<DashboardMetricsDTO.KeyCount> accountMix(DashboardMetricsDTO.NetworkTotals network) {
        return List.of(
            new DashboardMetricsDTO.KeyCount("patients", network.patients()),
            new DashboardMetricsDTO.KeyCount("professionals", network.professionals()),
            new DashboardMetricsDTO.KeyCount("vendors", network.vendors())
        );
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
