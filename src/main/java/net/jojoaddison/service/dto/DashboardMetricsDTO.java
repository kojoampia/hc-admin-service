package net.jojoaddison.service.dto;

import java.io.Serializable;
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
    List<PlatformCapability> capabilities
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
     * Derived from the current ISO week's shift assignments.
     *
     * <p>{@code coverPercent} is assigned slots over total slots for the week, rounded. With no
     * assignments at all it is 0 rather than 100 — an empty roster is uncovered, not perfectly
     * covered, and the rounding convention should not be the thing that decides which.
     */
    public record RosterSummary(int coverPercent, long unassignedSlots, long rosteredStaff, long shiftsThisWeek) implements Serializable {}

    /** A platform service whose recorded health is not {@code HEALTHY}. */
    public record DegradedService(String id, String name, String host, Integer port) implements Serializable {}

    public record PlatformServiceTotals(long total, long healthy) implements Serializable {}

    public record MonthCount(String month, long count) implements Serializable {}

    public record KeyCount(String key, long value) implements Serializable {}

    public record CaseLoadRow(String id, String name, Integer cases, Integer visits) implements Serializable {}

    /**
     * A declared platform capability and its status.
     *
     * <p>These are <em>declared</em>, not probed. They describe what the platform offers, which is a
     * property of the product rather than of any running process, and the prototype
     * ({@code app/admin-demo.html}) carried them as four constants for the same reason. The status
     * is a release stage — "Live", "Beta" — and must not be read as a health check. Runtime health
     * lives in {@link DegradedService} and {@link PlatformServiceTotals}, which are counted from
     * real records.
     */
    public record PlatformCapability(String name, String icon, String status) implements Serializable {}
}
