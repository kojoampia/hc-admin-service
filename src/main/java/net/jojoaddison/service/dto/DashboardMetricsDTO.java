package net.jojoaddison.service.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Everything the console's dashboard, platform-health and sign-in screens read, in one response.
 *
 * <p>The shape is not new — it is what {@code ConsoleMetricsService} in hc-admin-app has always
 * expected. What is new is that something serves it. The client called
 * {@code api/dashboard/metrics} from the day it was written and nothing has ever answered: the
 * in-browser mock did, and when that was removed in hc-admin-app#11 the call became a 404 that no
 * screen reported. The dashboard, platform-health and the sign-in panel's network figures were all
 * quietly empty in production until this existed.
 *
 * <p><strong>Nothing here is invented.</strong> Every number is counted from a collection, and the
 * fields with no source in the domain model are returned empty rather than filled with something
 * plausible — that was the mock's failure mode and it is not worth repeating. {@code sparklines} is
 * the honest example: there is no time series anywhere in this service, so it is an empty map, and
 * the client already treats a missing key as "no trend line".
 */
public record DashboardMetricsDTO(
    NetworkTotals network,
    NetworkTotals loaded,
    long unreadMessages,
    long openTasks,
    long pendingApprovals,
    RosterSummary roster,
    List<DegradedService> degradedServices,
    PlatformServiceTotals platformServices,
    List<MonthCount> messageVolume,
    List<KeyCount> accountMix,
    List<CaseLoadRow> caseLoad,
    Map<String, List<Integer>> sparklines,
    List<PlatformCapability> capabilities,
    Uptime uptime
)
    implements Serializable {
    /**
     * {@code network} counts every document; {@code loaded} counts the ones not archived.
     *
     * <p>The distinction matters on screen: the directories list active records, so a total that
     * included archived rows would disagree with the table underneath it.
     */
    public record NetworkTotals(long patients, long professionals, long vendors) implements Serializable {}

    /**
     * The roster week in force, counted the way the duty-roster grid counts it.
     *
     * <p>{@code coverPercent} is planned slots over the grid's capacity — rosterable professionals
     * times seven — rounded. With nothing planned it is 0 rather than 100: an empty roster is
     * uncovered, not perfectly covered, and the rounding convention should not be the thing that
     * decides which. {@code shiftsThisWeek} excludes OFF, which is planned but is not a shift. See
     * {@code DashboardMetricsService.roster()} for the full contract and for what these meant before.
     *
     * <p><b>{@code weekLabel} and {@code weekStartDate} say which week, and exist so that nothing
     * downstream has to assume.</b> The hero sentence read "for the week" with no week named, which
     * is exactly how a figure for one week can sit beside a grid showing another and look
     * reconciled. Both are null when there is no roster week at all — production's normal state.
     */
    public record RosterSummary(
        int coverPercent,
        long unassignedSlots,
        long rosteredStaff,
        long shiftsThisWeek,
        String weekLabel,
        LocalDate weekStartDate
    )
        implements Serializable {}

    /** A platform service whose recorded health is not {@code HEALTHY}. */
    public record DegradedService(String id, String name, String host, Integer port) implements Serializable {}

    public record PlatformServiceTotals(long total, long healthy) implements Serializable {}

    public record MonthCount(String month, long count) implements Serializable {}

    public record KeyCount(String key, long value) implements Serializable {}

    public record CaseLoadRow(String id, String name, Integer cases, Integer visits) implements Serializable {}

    /**
     * Availability over a window, and the window it was measured over.
     *
     * <p>The two travel together on purpose. The prototype's card read "Uptime, 30 days" above a
     * hardcoded 99.94%, and the console's replacement read "Services mapped" above a number that was
     * just the service count again — a label and a figure that had never been derived from the same
     * thing. Carrying the window with the percentage means the caption cannot drift from what was
     * measured.
     *
     * <p>Thirty days is not available: Mimir's {@code compactor_blocks_retention_period} is 15 days.
     * Asking for a window longer than retention returns a number computed from the days that exist
     * and presented as if it covered the rest, which is the same failure with extra steps.
     *
     * @param percent portion of the window in which every catalogued service was reporting.
     * @param windowDays the window actually measured — render this, do not assume it.
     */
    public record Uptime(Double percent, int windowDays) implements Serializable {}

    /**
     * A platform capability and its status.
     *
     * <p>These were four constants, copied from the prototype. They are now derived from the
     * observability stack, because a panel that says "Live" whether or not the thing is running is
     * decoration: realtime notification is Live only while Kafka is carrying connections, metric
     * visualization only while Grafana answers its health endpoint, and persistence is Healthy only
     * while every Health Connect database is up.
     *
     * <p>{@code status} is therefore a live reading for three of the four, and the fourth says so.
     * "Unknown" is a real value here — it is what a capability reports when the metrics store cannot
     * be reached, and it is deliberately not "Live".
     */
    public record PlatformCapability(String name, String icon, String status) implements Serializable {}
}
