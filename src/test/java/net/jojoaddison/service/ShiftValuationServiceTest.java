package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.ShiftAssignment;
import net.jojoaddison.domain.WageRate;
import net.jojoaddison.domain.enumeration.EarningsGranularity;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.ShiftType;
import net.jojoaddison.service.dto.ProfessionalEarningsDTO;
import net.jojoaddison.service.dto.ProfessionalShiftDTO;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * What {@link ShiftValuationService} does today, pinned before anything moves it.
 *
 * <p><b>This is a characterization test, and that is a different thing from a specification.</b> It
 * records behaviour as it currently is so that a later change has to declare itself: a rewrite that
 * preserves these answers is provably behaviour-preserving, and one that does not shows up as a
 * failing assertion somebody has to look at and either fix or deliberately update. It is not a
 * claim that every answer below is the right one.
 *
 * <p>It exists because this class had <b>no unit test at all</b>, which is a poor position for the
 * code that turns a roster into money. {@link ShiftValuationService#periodsBetween} was even made
 * package-private for a test that was never written; this is that test.
 *
 * <p>A unit test rather than an IT. The interesting part is arithmetic over dates and rates —
 * payable windows, period boundaries, and the difference between a shift priced at zero and one
 * nobody had priced. A Mongo round trip says nothing about any of it, and needing a container to
 * ask would be why the test kept not getting written. {@code ProfessionalEarningsIT} and
 * {@code WageRateEffectiveDatingIT} already cover the wiring.
 *
 * <p><b>The clock is fixed and that is load-bearing.</b> Every boundary here is relative to "today",
 * and the service takes an injected {@link Clock} for exactly this reason. Today is
 * <b>2026-09-01</b> throughout, so the last payable date is <b>2026-08-31</b>.
 */
class ShiftValuationServiceTest {

    /** Today. Every expectation below is stated relative to this date, never to the real one. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-01T12:00:00Z"), ZoneOffset.UTC);

    private static final ProfessionalRole ROLE = ProfessionalRole.NURSE;

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final WageRateService wageRates = mock(WageRateService.class);
    private final ShiftValuationService service = new ShiftValuationService(mongo, wageRates, CLOCK);

    // --- fixtures -------------------------------------------------------------------------------

    private static Professional professional() {
        return new Professional().id("prof-1").role(ROLE).licenceNumber("LIC-1").isArchived(Boolean.FALSE);
    }

    private static ShiftAssignment shift(LocalDate date, ShiftType type) {
        return new ShiftAssignment().shiftDate(date).shift(type);
    }

    private static WageRate rate(ShiftType shiftType, String amount, LocalDate validFrom) {
        return new WageRate().role(ROLE).shiftType(shiftType).amount(new BigDecimal(amount)).currency("GHS").validFrom(validFrom);
    }

    /** Installs a table keyed the way {@code rateTableUpTo} builds one: role, then shift type. */
    private void install(Map<ShiftType, List<WageRate>> byShift) {
        when(wageRates.rateTableUpTo(any())).thenReturn(new WageRateService.RateTable(Map.of(ROLE, byShift)));
    }

    /**
     * One rate for {@link #ROLE} at <b>every</b> shift type, in force from well before any date used
     * here.
     *
     * <p>Flat across shift types on purpose. Since 2026-09-04 a rate is keyed on
     * {@code (role, shiftType, date)}, and most of this class is about the payable boundary and the
     * bucketing rather than about pricing — a fixture that priced the shift types differently would
     * make every arithmetic expectation below depend on which shift type each fixture row happened
     * to carry. The shift dimension gets its own tests at the end of the class, where the rates
     * differ and the difference is the assertion.
     */
    private void ratedAt(String amount) {
        Map<ShiftType, List<WageRate>> byShift = new EnumMap<>(ShiftType.class);
        for (ShiftType shiftType : ShiftType.values()) {
            byShift.put(shiftType, List.of(rate(shiftType, amount, LocalDate.of(2020, 1, 1))));
        }
        install(byShift);
    }

    /**
     * A rate superseded mid-window: {@code amount} from 2020, {@code laterAmount} from {@code rise},
     * at every shift type.
     *
     * <p>Newest first, because that is the order {@code rateTableUpTo} builds and what
     * {@code RateTable.rateOn}'s {@code findFirst()} depends on — a table sorted the other way
     * answers every date with the oldest rate.
     */
    private void ratedAt(String amount, String laterAmount, LocalDate rise) {
        Map<ShiftType, List<WageRate>> byShift = new EnumMap<>(ShiftType.class);
        for (ShiftType shiftType : ShiftType.values()) {
            byShift.put(shiftType, List.of(rate(shiftType, laterAmount, rise), rate(shiftType, amount, LocalDate.of(2020, 1, 1))));
        }
        install(byShift);
    }

    /** No rate has ever been configured — distinct from a rate of zero, and the service says so. */
    private void unpriced() {
        when(wageRates.rateTableUpTo(any())).thenReturn(new WageRateService.RateTable(Map.of()));
    }

    private void rosterReturns(ShiftAssignment... shifts) {
        when(mongo.find(any(), any())).thenReturn(List.of(shifts));
    }

    // --- the payable boundary -------------------------------------------------------------------

    /**
     * A shift dated today has not been worked yet — the day is still running — so the payable window
     * ends yesterday. This is the rule the whole class turns on.
     */
    @Test
    void lastPayableDateIsYesterdayNotToday() {
        assertThat(service.lastPayableDate()).isEqualTo(TODAY.minusDays(1)).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    /**
     * {@code shiftsFor} answers "what is my schedule", so it keeps OFF days and future dates and
     * flags each row instead of dropping it. Two independent reasons make a row unpayable, and both
     * are pinned here: the type is OFF, or the date has not passed.
     *
     * <p><b>The third row sits exactly on the cutoff and that is why it is here.</b> Without it
     * every row is a day or more clear of the boundary, and {@code !date.isAfter(cutoff)} can be
     * weakened to {@code date.isBefore(cutoff)} with the whole assertion still passing — a
     * clinician's most recently worked day would silently stop reading as payable on their own
     * roster screen. {@code ProfessionalSelfIT} does not cover it either; its fixture is two days
     * clear as well.
     */
    @Test
    void marksAShiftPayableOnlyWhenItIsPastAndNotAnOffDay() {
        rosterReturns(
            shift(TODAY.minusDays(3), ShiftType.DAY), // worked
            shift(TODAY.minusDays(2), ShiftType.OFF), // past, but a rest day
            shift(TODAY.minusDays(1), ShiftType.NIGHT), // the last payable date itself
            shift(TODAY, ShiftType.NIGHT), // today is not yet worked
            shift(TODAY.plusDays(3), ShiftType.EVENING) // rostered ahead
        );

        List<ProfessionalShiftDTO> shifts = service.shiftsFor(professional(), TODAY.minusDays(7), TODAY.plusDays(7));

        assertThat(shifts).extracting(ProfessionalShiftDTO::payable).containsExactly(true, false, true, false, false);
    }

    /**
     * <b>The exclusion of OFF days and future dates lives in the query, not in the loop</b> — so it
     * has to be asserted on the query.
     *
     * <p>This matters more than it looks. {@code earningsFor}'s bucket loop never re-checks the
     * shift type: it counts whatever {@code payableShifts} returned. Against a mocked
     * {@code MongoTemplate} the criteria are never evaluated, so a test that stubs an OFF shift and
     * then asserts a total is asserting the stub, not the service — and would in fact see that OFF
     * shift counted and priced. An earlier version of this test did exactly that under a name
     * promising otherwise, and would have stayed green with {@code .ne(ShiftType.OFF)} deleted from
     * the source.
     *
     * <p>So capture the {@link Query} and read its criteria. Three things are pinned, and the third
     * is the one the service's own javadoc calls out as silently breakable:
     *
     * <ul>
     *   <li>{@code shift $ne OFF} — a rest day is never paid;
     *   <li>{@code shiftDate $gte/$lte} — the window bounds, which are what exclude the future once
     *       the end has been clipped to the last payable date;
     *   <li>the professional matched as an <b>entity</b>, not an id. {@code professional} is a
     *       {@code @DBRef}, and the id-shaped query forms return an empty list without erroring —
     *       indistinguishable from a professional who has not worked.
     * </ul>
     *
     * <p>{@code ProfessionalEarningsIT.offDaysAreNeitherCountedNorPaid} exercises the same exclusion
     * against real Mongo. This pins that the criteria are still being <em>built</em>, which is the
     * part a refactor can drop.
     *
     * <p><b>A second OFF test was deleted from this class on 2026-09-04 rather than repaired</b>, and
     * it is worth saying where it went. {@code neverValuesARestDayEvenWhenOneHasBeenPriced} claimed
     * that a priced {@code OFF} rate contributes nothing — but with {@code MongoTemplate} mocked the
     * criteria never execute, so its body stubbed an empty roster and asserted three zeros, which is
     * true of any service at all. Its own comment admitted it encoded the assumption rather than
     * checking it. Repairing it would have meant asserting the built {@code Query}, which is this
     * test; so the coverage moved instead of doubling. {@code ProfessionalEarningsIT} prices every
     * shift type including {@code OFF} and asserts the count, the total and {@code unpricedShifts}
     * across two rostered rest days, which is the claim the deleted name was making.
     *
     * <p>The general rule it came from is worth more than the case: <b>a test whose name promises
     * what its body cannot check inflates a count this estate already reads sceptically.</b> Deleting
     * one and saying so beats leaving it green.
     */
    @Test
    void excludesOffDaysAndFutureDatesInTheQueryItself() {
        ratedAt("100");
        rosterReturns();

        service.earningsFor(professional(), EarningsGranularity.MONTHLY, TODAY.minusMonths(1), TODAY);

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(captor.capture(), eq(ShiftAssignment.class));
        Document criteria = captor.getValue().getQueryObject();

        assertThat(criteria).containsKeys("professional", "shiftDate", "shift");
        assertThat(criteria.get("shift").toString()).contains("$ne").contains("OFF");
        assertThat(criteria.get("shiftDate").toString()).contains("$gte").contains("$lte");
        assertThat(criteria.get("professional")).isInstanceOf(Professional.class);
    }

    /**
     * Asking about a window that runs past today does not accrue tomorrow's shifts: the requested
     * end is clipped to the last payable date, and the DTO reports the clipped value rather than
     * what was asked for. A caller that echoes {@code to} back to a user is showing them a real
     * boundary, not their own input.
     */
    @Test
    void clipsARequestedEndDateToTheLastPayableDate() {
        ratedAt("100");
        rosterReturns();

        ProfessionalEarningsDTO earnings = service.earningsFor(
            professional(),
            EarningsGranularity.DAILY,
            TODAY.minusDays(3),
            TODAY.plusMonths(6)
        );

        assertThat(earnings.to()).isEqualTo(LocalDate.of(2026, 8, 31));
    }

    /**
     * A window wholly in the future inverts the loop bounds, and the service returns a zeroed
     * result rather than iterating backwards. Note {@code from} is echoed unclipped while {@code to}
     * is clipped, so the DTO legitimately carries {@code to} before {@code from} — that is the
     * signal, not a bug.
     */
    @Test
    void returnsAnEmptyResultForAWindowEntirelyInTheFuture() {
        ratedAt("100");
        rosterReturns();

        ProfessionalEarningsDTO earnings = service.earningsFor(
            professional(),
            EarningsGranularity.DAILY,
            TODAY.plusDays(10),
            TODAY.plusDays(20)
        );

        assertThat(earnings.buckets()).isEmpty();
        assertThat(earnings.shiftsCompleted()).isZero();
        assertThat(earnings.totalAccrued()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(earnings.unpricedShifts()).isZero();
        assertThat(earnings.to()).isBefore(earnings.from());
    }

    // --- pricing --------------------------------------------------------------------------------

    /** Two worked shifts at 100 come to 200, and both count. The base case, stated once. */
    @Test
    void valuesEachPayableShiftAtTheRoleRate() {
        ratedAt("100");
        rosterReturns(shift(TODAY.minusDays(3), ShiftType.DAY), shift(TODAY.minusDays(2), ShiftType.NIGHT));

        ProfessionalEarningsDTO earnings = service.earningsFor(professional(), EarningsGranularity.MONTHLY, TODAY.minusDays(5), TODAY);

        assertThat(earnings.shiftsCompleted()).isEqualTo(2);
        assertThat(earnings.totalAccrued()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(earnings.unpricedShifts()).isZero();
        assertThat(earnings.currency()).isEqualTo("GHS");
    }

    /**
     * <b>Each shift is valued at the rate in force on its own date.</b> This is the rule the whole
     * model turns on and the one this file did not pin until 2026-09-01.
     *
     * <p>With a single rate installed — which is all {@link #ratedAt(String)} gives you — every
     * other pricing assertion here passes whether the service reads {@code rateOn(role, date)} or
     * {@code rateOn(role, windowEnd)}. That second form values a whole wage bill at today's rate
     * and restates money already paid, which is precisely what effective dating exists to prevent,
     * and nothing in this file would have gone red. Two rates and two shifts either side of the
     * rise make the difference observable: 100 + 150, not 300 and not 200.
     *
     * <p>Two further things are only reachable with a populated table. {@code RateTable.rateOn}
     * filters on {@code !validFrom.isAfter(date)}, so the shift dated <b>on</b> the rise takes the
     * new rate — with {@code Map.of()} that stream never runs and the inclusive boundary cannot be
     * broken by a test. And the table is asked for {@code windowEnd}: ask for {@code windowStart}
     * instead and a mid-window rise is missing from it entirely, which the {@code any()} matcher
     * everywhere else in this file cannot see.
     */
    @Test
    void valuesEachShiftAtTheRateInForceOnItsOwnDate() {
        LocalDate rise = TODAY.minusDays(3);
        ratedAt("100", "150", rise);
        rosterReturns(shift(rise.minusDays(1), ShiftType.DAY), shift(rise, ShiftType.NIGHT));

        ProfessionalEarningsDTO earnings = service.earningsFor(professional(), EarningsGranularity.MONTHLY, TODAY.minusDays(10), TODAY);

        assertThat(earnings.shiftsCompleted()).isEqualTo(2);
        assertThat(earnings.totalAccrued()).isEqualByComparingTo(new BigDecimal("250"));
        assertThat(earnings.unpricedShifts()).isZero();
        verify(wageRates).rateTableUpTo(service.lastPayableDate());
    }

    /**
     * <b>Unpriced is not zero, and the distinction is the point.</b> A shift worked before any rate
     * existed is counted as worked and reported separately, so the console can say "we could not
     * price these" instead of showing a total that looks like nothing was earned.
     */
    @Test
    void countsShiftsWorkedBeforeAnyRateExistedAsUnpricedRatherThanFree() {
        unpriced();
        rosterReturns(shift(TODAY.minusDays(3), ShiftType.DAY), shift(TODAY.minusDays(2), ShiftType.DAY));

        ProfessionalEarningsDTO earnings = service.earningsFor(professional(), EarningsGranularity.MONTHLY, TODAY.minusDays(5), TODAY);

        assertThat(earnings.shiftsCompleted()).isEqualTo(2);
        assertThat(earnings.unpricedShifts()).isEqualTo(2);
        assertThat(earnings.totalAccrued()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // --- period arithmetic ----------------------------------------------------------------------

    /**
     * The reason {@code periodsBetween} is package-private. Every case is inclusive of both ends —
     * a single day is one period, not zero.
     *
     * <p><b>It has no caller in {@code src/main}.</b> Bucket tiling is done by the loop in
     * {@code earningsFor} over {@code nextPeriod}/{@code endOfPeriod}, not by this helper, so
     * nothing here constrains the number of buckets a caller actually sees — that is
     * {@link #emitsABucketPerPeriodIncludingTheEmptyOnes()} and
     * {@link #tilesDailyAndWeeklyWindowsToo()}. This pins the helper's arithmetic only, and the
     * honest options are to delete it as dead code or to use it in the loop it duplicates. It is
     * kept for now because it is the one piece of period arithmetic stated in one place.
     */
    @Test
    void countsPeriodsInclusivelyAtBothEnds() {
        LocalDate day = LocalDate.of(2026, 3, 10);

        assertThat(ShiftValuationService.periodsBetween(day, day, EarningsGranularity.DAILY)).isEqualTo(1);
        assertThat(ShiftValuationService.periodsBetween(day, day.plusDays(6), EarningsGranularity.DAILY)).isEqualTo(7);
        assertThat(ShiftValuationService.periodsBetween(day, day.plusWeeks(3), EarningsGranularity.WEEKLY)).isEqualTo(4);
        assertThat(ShiftValuationService.periodsBetween(day, day.plusMonths(11), EarningsGranularity.MONTHLY)).isEqualTo(12);
    }

    /**
     * {@code WEEKLY} and {@code MONTHLY} both round the window start <b>outwards</b> to a period
     * boundary — Monday, and the first of the month — so a bucket is never a partial period. The
     * requested {@code from} is not what comes back.
     */
    @Test
    void snapsTheWindowStartOutwardsToAPeriodBoundary() {
        ratedAt("100");
        rosterReturns();

        // 2026-08-12 is a Wednesday.
        LocalDate wednesday = LocalDate.of(2026, 8, 12);

        assertThat(service.earningsFor(professional(), EarningsGranularity.WEEKLY, wednesday, TODAY).from())
            .isEqualTo(LocalDate.of(2026, 8, 10)); // the Monday of that week

        assertThat(service.earningsFor(professional(), EarningsGranularity.MONTHLY, wednesday, TODAY).from())
            .isEqualTo(LocalDate.of(2026, 8, 1));

        assertThat(service.earningsFor(professional(), EarningsGranularity.DAILY, wednesday, TODAY).from()).isEqualTo(wednesday);
    }

    /**
     * With no {@code from}, each granularity reaches back a fixed number of periods chosen to draw a
     * readable series — 30 days, 12 weeks, 12 months, each counted inclusively from the clipped end.
     * These are the numbers the earnings chart's default view depends on.
     */
    @Test
    void defaultsToAWindowLongEnoughToDraw() {
        ratedAt("100");
        rosterReturns();

        Professional prof = professional();
        LocalDate end = LocalDate.of(2026, 8, 31); // clipped: yesterday

        assertThat(service.earningsFor(prof, EarningsGranularity.DAILY, null, null).from()).isEqualTo(end.minusDays(29));
        assertThat(service.earningsFor(prof, EarningsGranularity.WEEKLY, null, null).from()).isEqualTo(LocalDate.of(2026, 6, 15)); // Monday, 12 weeks back
        assertThat(service.earningsFor(prof, EarningsGranularity.MONTHLY, null, null).from()).isEqualTo(LocalDate.of(2025, 9, 1));
    }

    /**
     * The weekly default <b>snaps back to Monday</b> after counting twelve weeks, and this is the
     * case that proves it.
     *
     * <p>The assertion above cannot: today is a Tuesday, so the clipped end 2026-08-31 is itself a
     * Monday, twelve weeks back from it is a Monday too, and removing the snap from
     * {@code defaultStart}'s WEEKLY branch would leave that expectation passing unchanged. Here the
     * end is an explicit Wednesday, so the unsnapped answer (2026-05-27) and the snapped one differ
     * and only the snapped one passes.
     */
    @Test
    void snapsTheWeeklyDefaultStartBackToMonday() {
        ratedAt("100");
        rosterReturns();

        LocalDate wednesday = LocalDate.of(2026, 8, 12);

        assertThat(service.earningsFor(professional(), EarningsGranularity.WEEKLY, null, wednesday).from())
            .isEqualTo(
                LocalDate.of(2026, 5, 25) // Monday; 2026-05-27 would be the unsnapped answer
            );
    }

    /**
     * DAILY and WEEKLY tile their windows too, not just MONTHLY.
     *
     * <p>Covered explicitly because the bucket loop is the only thing that produces bucket counts —
     * {@link ShiftValuationService#periodsBetween} is <b>not</b> in that path (see the note on
     * {@link #countsPeriodsInclusivelyAtBothEnds()}), so a rewrite could change DAILY or WEEKLY
     * tiling with the helper's own test still green.
     */
    @Test
    void tilesDailyAndWeeklyWindowsToo() {
        ratedAt("100");
        rosterReturns();

        assertThat(service.earningsFor(professional(), EarningsGranularity.DAILY, TODAY.minusDays(4), TODAY.minusDays(2)).buckets())
            .hasSize(3);

        // Mon 2026-08-10 through Sun 2026-08-23 — two whole weeks.
        assertThat(
            service.earningsFor(professional(), EarningsGranularity.WEEKLY, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 23)).buckets()
        )
            .hasSize(2);
    }

    /** A null granularity means monthly, rather than a null-pointer or an empty result. */
    @Test
    void treatsAMissingGranularityAsMonthly() {
        ratedAt("100");
        rosterReturns();

        assertThat(service.earningsFor(professional(), null, TODAY.minusMonths(2), TODAY).granularity())
            .isEqualTo(EarningsGranularity.MONTHLY);
    }

    /**
     * Buckets tile the window continuously — one per period, present even when nothing was worked in
     * it. A chart that skipped empty periods would compress a quiet month into the same width as a
     * busy one.
     */
    @Test
    void emitsABucketPerPeriodIncludingTheEmptyOnes() {
        ratedAt("100");
        rosterReturns(shift(LocalDate.of(2026, 8, 5), ShiftType.DAY));

        ProfessionalEarningsDTO earnings = service.earningsFor(
            professional(),
            EarningsGranularity.MONTHLY,
            LocalDate.of(2026, 6, 1),
            TODAY
        );

        assertThat(earnings.buckets()).hasSize(3); // June, July, August
        assertThat(earnings.buckets()).extracting(b -> b.periodStart().getMonthValue()).containsExactly(6, 7, 8);
        assertThat(earnings.buckets().get(0).shifts()).isZero();
        assertThat(earnings.buckets().get(2).shifts()).isEqualTo(1);
    }

    /** An archived professional still values normally; the flag is reported, not applied. */
    @Test
    void reportsArchivedWithoutSuppressingEarnings() {
        ratedAt("100");
        rosterReturns(shift(TODAY.minusDays(2), ShiftType.DAY));

        ProfessionalEarningsDTO earnings = service.earningsFor(
            professional().isArchived(Boolean.TRUE),
            EarningsGranularity.MONTHLY,
            TODAY.minusMonths(1),
            TODAY
        );

        assertThat(earnings.archived()).isTrue();
        assertThat(earnings.shiftsCompleted()).isEqualTo(1);
    }

    /** With no profile attached, the licence number is the display name rather than null. */
    @Test
    void fallsBackToTheLicenceNumberWhenThereIsNoProfile() {
        ratedAt("100");
        rosterReturns();

        assertThat(service.earningsFor(professional(), EarningsGranularity.MONTHLY, TODAY.minusMonths(1), TODAY).professionalName())
            .isEqualTo("LIC-1");
    }

    // --- the shift dimension (2026-09-04) --------------------------------------------------------

    /**
     * A night is valued at the night rate, not at the role's rate.
     *
     * <p>The one assertion in this class that fails if the lookup ever goes back to
     * {@code (role, date)}. Every other pricing assertion here is deliberately flat across shift
     * types — see {@link #ratedAt(String)} — so all of them pass either way, which is exactly why
     * this one has to exist rather than being assumed covered.
     *
     * <p>Two shifts, both payable, both in the same bucket: 100 for the day and 150 for the night.
     * A shift-blind lookup finds whichever row the flattened list happened to hold first and reports
     * 200 or 300, both of which are plausible totals nobody would query.
     */
    @Test
    void valuesEachShiftAtItsOwnShiftTypesRate() {
        Map<ShiftType, List<WageRate>> byShift = new EnumMap<>(ShiftType.class);
        byShift.put(ShiftType.DAY, List.of(rate(ShiftType.DAY, "100", LocalDate.of(2020, 1, 1))));
        byShift.put(ShiftType.NIGHT, List.of(rate(ShiftType.NIGHT, "150", LocalDate.of(2020, 1, 1))));
        install(byShift);
        rosterReturns(shift(TODAY.minusDays(3), ShiftType.DAY), shift(TODAY.minusDays(2), ShiftType.NIGHT));

        ProfessionalEarningsDTO earnings = service.earningsFor(professional(), EarningsGranularity.MONTHLY, TODAY.minusMonths(1), TODAY);

        assertThat(earnings.totalAccrued()).isEqualByComparingTo("250");
        assertThat(earnings.shiftsCompleted()).isEqualTo(2);
        assertThat(earnings.unpricedShifts()).isZero();
    }

    /**
     * An unpriced shift type is unpriced, not valued at a sibling's rate.
     *
     * <p>There is no fallback from {@code (role, EVENING)} to {@code (role, DAY)}, so an evening
     * worked before anybody priced evenings counts toward {@code shiftsCompleted} and contributes
     * nothing to {@code totalAccrued} — the same treatment as a shift worked before any rate existed
     * at all. The console shows a non-zero {@code unpricedShifts} as "nobody set a price", which is
     * the honest answer and is not the same as "earned nothing".
     */
    @Test
    void reportsAnUnpricedShiftTypeAsUnpricedRatherThanBorrowingAnotherRate() {
        Map<ShiftType, List<WageRate>> byShift = new EnumMap<>(ShiftType.class);
        byShift.put(ShiftType.DAY, List.of(rate(ShiftType.DAY, "100", LocalDate.of(2020, 1, 1))));
        install(byShift);
        rosterReturns(shift(TODAY.minusDays(3), ShiftType.DAY), shift(TODAY.minusDays(2), ShiftType.EVENING));

        ProfessionalEarningsDTO earnings = service.earningsFor(professional(), EarningsGranularity.MONTHLY, TODAY.minusMonths(1), TODAY);

        assertThat(earnings.totalAccrued()).isEqualByComparingTo("100");
        assertThat(earnings.shiftsCompleted()).isEqualTo(2);
        assertThat(earnings.unpricedShifts()).isEqualTo(1);
    }

    /**
     * {@code FLEXIBLE} is payable here, and this is the first thing in this repo that could say so.
     *
     * <p>The value arrived with the superset enum on 2026-09-04. It is hc-professional's negotiated
     * 2–4 hour block, and this service could not previously receive, store or price one — so the
     * earnings contract it serves back to that stack could not express a shift that stack routinely
     * rosters. Payability is keyed on {@code shift != OFF}, so {@code FLEXIBLE} accrues like any
     * worked shift, at its own rate.
     */
    @Test
    void valuesAFlexibleShiftLikeAnyOtherWorkedOne() {
        Map<ShiftType, List<WageRate>> byShift = new EnumMap<>(ShiftType.class);
        byShift.put(ShiftType.FLEXIBLE, List.of(rate(ShiftType.FLEXIBLE, "60", LocalDate.of(2020, 1, 1))));
        install(byShift);
        rosterReturns(shift(TODAY.minusDays(2), ShiftType.FLEXIBLE));

        ProfessionalEarningsDTO earnings = service.earningsFor(professional(), EarningsGranularity.MONTHLY, TODAY.minusMonths(1), TODAY);

        assertThat(earnings.totalAccrued()).isEqualByComparingTo("60");
        assertThat(earnings.shiftsCompleted()).isEqualTo(1);
    }
}
