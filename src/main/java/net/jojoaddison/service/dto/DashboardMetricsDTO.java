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
 * plausible — that was the mock's failure mode and it is not worth repeating.
 *
 * <p>{@code sparklines} is the worked example, and it is now <em>partly</em> populated rather than
 * an empty map, which is the more useful shape of the same rule. Patients and professionals carry
 * {@code joined_on}, so their running total at any past month end is a fact and each gets a series.
 * Unread messages and open tasks are backlogs whose history nothing records — no read time on a
 * message, no closed time on a task — so those two keys are absent and the client draws no line for
 * them. Two real series and two honest gaps, rather than four lines of which half are guesses. See
 * {@code DashboardMetricsService.sparklines()}.
 */
public record DashboardMetricsDTO(
    NetworkTotals network,
    NetworkTotals loaded,
    long unreadMessages,
    long openTasks,
    long pendingApprovals,
    /**
     * Clinicians this service knows about and holds no {@code Professional} for.
     *
     * <p><b>A figure of its own, deliberately, rather than added into {@code network.professionals}
     * — and the alternative was considered rather than skipped.</b> A clinician who registers on
     * hc-professional arrives here as a {@code DirectoryLink} with {@code local_id: null} and no
     * local record at all: both event types on that topic are {@code LINK_ONLY}, because
     * {@code Professional} requires a {@code role} and a {@code licenceNumber} and neither is on the
     * wire in any event, in any version. So the professionals tile could not move when somebody
     * registered, which is what an administrator reported from production (backlog item 46).
     *
     * <p>Folding them into the tile would have made it move, and would have changed what it means
     * without saying so, in four places at once. {@code network.professionals} is a count of
     * <em>records</em>, and three other figures are derived from that same collection and would then
     * disagree with it: the account-mix chart is a breakdown of these totals, the professionals
     * sparkline is a running total over {@code joined_on} whose last point {@code SparklinesIT}
     * requires to equal the tile, and {@code loaded} excludes archived records — a property a link
     * does not have. A tile and a chart beside it answering one question differently is the defect
     * this dashboard has already had twice, with roster cover and with the account mix.
     *
     * <p>So the tile keeps its meaning — <b>clinicians on file</b> — and this says what is known and
     * not on file. The console renders it under that tile as its own line, and the professional
     * directory lists the same rows from
     * {@code GET /api/directory-links?source=HC_PROFESSIONAL&unlinked=true}, so the number is one
     * click from the people it counts.
     *
     * <p>It is not a backlog that clears itself: nothing on the topic can ever complete these
     * records, which is backlog item 35 and needs a read hc-professional does not yet expose (item
     * 36). Zero is the normal answer on a stack that has never consumed a registration.
     */
    long professionalsAwaitingRecord,
    RosterSummary roster,
    List<DegradedService> degradedServices,
    PlatformServiceTotals platformServices,
    List<MonthCount> messageVolume,
    List<KeyCount> accountMix,
    List<CaseLoadRow> caseLoad,
    Map<String, List<Integer>> sparklines,
    /**
     * What each KPI tile's note says, as numbers rather than as copy.
     *
     * <p>Item 14: the notes were i18n literals — "▲ +3 this week" under a patient count of 12,
     * "+2 verified" under 9 professionals, on the demo and on the console alike, and neither could
     * ever change. The strings are format templates now and these are what fills them.
     *
     * <p>Keyed the same as {@link #sparklines()}, and each one is the measurement its template
     * names — not a generic "delta" whose meaning the copy is free to reinterpret. See
     * {@code DashboardMetricsService.deltas()} for what each counts.
     */
    Map<String, Long> deltas,
    List<PlatformCapability> capabilities,
    Uptime uptime
)
    implements Serializable {
    /**
     * {@code network} counts every document; {@code loaded} counts the ones not archived.
     *
     * <p>The distinction matters on screen: the directories list active records, so a total that
     * included archived rows would disagree with the table underneath it.
     *
     * <p><b>All three count documents in this service, and {@code professionals} is the one where
     * that is a narrower claim than it sounds.</b> A clinician who has registered on hc-professional
     * but has no record here is not in it and deliberately is not — see
     * {@link DashboardMetricsDTO#professionalsAwaitingRecord()}, which counts exactly those and is
     * kept separate so that the account-mix chart and the sparklines, both derived from these
     * numbers, keep agreeing with them.
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
