package net.jojoaddison.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.ShiftAssignment;
import net.jojoaddison.domain.WageRate;
import net.jojoaddison.domain.enumeration.EarningsGranularity;
import net.jojoaddison.domain.enumeration.ShiftType;
import net.jojoaddison.service.dto.EarningsBucketDTO;
import net.jojoaddison.service.dto.ProfessionalEarningsDTO;
import net.jojoaddison.service.dto.ProfessionalShiftDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

/**
 * Turns a professional's roster into money.
 *
 * <p>Two rules decide everything here, and both were settled deliberately rather than inferred:
 *
 * <ol>
 *   <li><b>A shift is payable once it is in the past and is not an off day.</b> {@code
 *       ShiftAssignment} carries no completion status — the roster grid <em>is</em> the record of
 *       work — so "completed" means the date has passed. A shift scheduled for next week is real,
 *       and is deliberately not counted: accruing it would report money as earned before it was.
 *   <li><b>Each shift is valued at the rate in force on its own date</b>, never at today's rate.
 *       This is what makes a rate rise non-retroactive; see {@link WageRateService}.
 * </ol>
 */
@Service
public class ShiftValuationService {

    private static final Logger LOG = LoggerFactory.getLogger(ShiftValuationService.class);

    /** How far back the default window reaches, per bucket size. Twelve points make a readable series. */
    private static final int DEFAULT_DAILY_DAYS = 30;
    private static final int DEFAULT_WEEKLY_WEEKS = 12;
    private static final int DEFAULT_MONTHLY_MONTHS = 12;

    private final MongoTemplate mongoTemplate;

    private final WageRateService wageRateService;

    private final Clock clock;

    public ShiftValuationService(MongoTemplate mongoTemplate, WageRateService wageRateService, Clock clock) {
        this.mongoTemplate = mongoTemplate;
        this.wageRateService = wageRateService;
        this.clock = clock;
    }

    /**
     * The shifts a professional is paid for in a window: worked, and not an off day.
     *
     * <p><b>Matched on the entity, not on its id.</b> {@code ShiftAssignment.professional} is a
     * {@code @DBRef}, and only {@code Criteria.is(entity)} produces a query that matches one — it
     * goes through the mapping context, which knows the property is an association and writes the
     * reference the same way the converter stored it. Three shapes that look obviously equivalent
     * all silently match nothing, and were each tried against a real document before this was
     * written: {@code 'professional.$id': <id>} as a string query, the same path through the driver
     * directly, and an explicit {@code DBRef} value. None errors. They return an empty list, which
     * reads exactly like a professional who has not worked.
     *
     * <p>The projection is not a micro-optimisation. Without it every row resolves its
     * {@code professional} reference on load — an extra round trip per shift, to fetch the document
     * the caller already passed in.
     */
    private List<ShiftAssignment> payableShifts(Professional professional, LocalDate from, LocalDate to) {
        Query query = new Query(
            Criteria.where("professional").is(professional).and("shiftDate").gte(from).lte(to).and("shift").ne(ShiftType.OFF)
        );
        query.fields().include("shiftDate").include("shift");
        return mongoTemplate.find(query, ShiftAssignment.class);
    }

    /**
     * The last date that can have been worked. A shift dated today has not been completed yet — the
     * day is still running — so the payable window ends yesterday.
     */
    public LocalDate lastPayableDate() {
        return LocalDate.now(clock).minusDays(1);
    }

    /**
     * Every rostered row for a professional in a window, payable or not, oldest first.
     *
     * <p>The counterpart to {@link #payableShifts}, and deliberately not a variant of it: this one
     * keeps the {@code OFF} days and the future dates, because it answers "what is my schedule"
     * rather than "what am I owed". Each row carries the payable flag so that the single rule
     * deciding it stays here — see {@link ProfessionalShiftDTO#payable()}.
     *
     * <p>Matched on the entity for the same reason as {@link #payableShifts}: {@code professional}
     * is a {@code @DBRef}, and the query shapes that address it by raw id return an empty list
     * rather than failing.
     */
    public List<ProfessionalShiftDTO> shiftsFor(Professional professional, LocalDate from, LocalDate to) {
        Query query = new Query(Criteria.where("professional").is(professional).and("shiftDate").gte(from).lte(to));
        query.fields().include("shiftDate").include("shift");
        query.with(Sort.by(Sort.Direction.ASC, "shiftDate"));

        LocalDate cutoff = lastPayableDate();
        return mongoTemplate
            .find(query, ShiftAssignment.class)
            .stream()
            .map(shift ->
                new ProfessionalShiftDTO(
                    shift.getShiftDate(),
                    shift.getShift(),
                    shift.getShift() != ShiftType.OFF && !shift.getShiftDate().isAfter(cutoff)
                )
            )
            .toList();
    }

    /**
     * Earnings for one professional over a window, bucketed for the chart.
     *
     * @param professional the professional to value; its {@code role} selects the rate.
     * @param granularity the bucket size, defaulting to {@code MONTHLY}.
     * @param from the start of the window, defaulting to a span that fills the series.
     * @param to the end of the window, defaulting to today. Clipped to {@link #lastPayableDate()}.
     */
    public ProfessionalEarningsDTO earningsFor(Professional professional, EarningsGranularity granularity, LocalDate from, LocalDate to) {
        EarningsGranularity bucketing = granularity == null ? EarningsGranularity.MONTHLY : granularity;
        LocalDate windowEnd = clipToPayable(to == null ? LocalDate.now(clock) : to);
        LocalDate windowStart = from == null ? defaultStart(windowEnd, bucketing) : startOfPeriod(from, bucketing);

        LOG.debug("Valuing {} from {} to {} by {}", professional.getId(), windowStart, windowEnd, bucketing);

        // An entirely empty window — asked about a period wholly in the future, or before the
        // service existed. Report it as such rather than inverting the loop bounds below.
        if (windowEnd.isBefore(windowStart)) {
            return new ProfessionalEarningsDTO(
                professional.getId(),
                nameOf(professional),
                professional.getRole(),
                bucketing,
                windowStart,
                windowEnd,
                0L,
                BigDecimal.ZERO,
                0L,
                currentCurrencyFor(professional),
                Boolean.TRUE.equals(professional.getIsArchived()),
                List.of()
            );
        }

        List<ShiftAssignment> payable = payableShifts(professional, windowStart, windowEnd);
        WageRateService.RateTable rates = wageRateService.rateTableUpTo(windowEnd);

        List<EarningsBucketDTO> buckets = new ArrayList<>();
        long totalShifts = 0;
        long unpriced = 0;
        BigDecimal total = BigDecimal.ZERO;
        String currency = null;

        for (LocalDate periodStart = windowStart; !periodStart.isAfter(windowEnd); periodStart = nextPeriod(periodStart, bucketing)) {
            LocalDate periodEnd = endOfPeriod(periodStart, bucketing);
            long shifts = 0;
            BigDecimal amount = BigDecimal.ZERO;

            for (ShiftAssignment shift : payable) {
                LocalDate date = shift.getShiftDate();
                if (date.isBefore(periodStart) || date.isAfter(periodEnd)) {
                    continue;
                }
                shifts++;
                WageRate rate = rates.rateOn(professional.getRole(), date).orElse(null);
                if (rate == null) {
                    unpriced++;
                } else {
                    amount = amount.add(rate.getAmount());
                    currency = rate.getCurrency();
                }
            }

            buckets.add(new EarningsBucketDTO(periodStart, min(periodEnd, windowEnd), shifts, amount));
            totalShifts += shifts;
            total = total.add(amount);
        }

        return new ProfessionalEarningsDTO(
            professional.getId(),
            nameOf(professional),
            professional.getRole(),
            bucketing,
            windowStart,
            windowEnd,
            totalShifts,
            total,
            unpriced,
            currency == null ? currentCurrencyFor(professional) : currency,
            Boolean.TRUE.equals(professional.getIsArchived()),
            buckets
        );
    }

    /**
     * The currency to label a figure of zero with. Nothing was priced in the window, so there is no
     * rate to read it off — fall back to what the role is priced in today, which is what the console
     * would show beside it anyway.
     */
    private String currentCurrencyFor(Professional professional) {
        return wageRateService.rateOn(professional.getRole(), LocalDate.now(clock)).map(WageRate::getCurrency).orElse(null);
    }

    private LocalDate clipToPayable(LocalDate requested) {
        LocalDate cutoff = lastPayableDate();
        return requested.isAfter(cutoff) ? cutoff : requested;
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? b : a;
    }

    private LocalDate defaultStart(LocalDate end, EarningsGranularity granularity) {
        return switch (granularity) {
            case DAILY -> end.minusDays(DEFAULT_DAILY_DAYS - 1L);
            case WEEKLY -> startOfPeriod(end.minusWeeks(DEFAULT_WEEKLY_WEEKS - 1L), granularity);
            case MONTHLY -> startOfPeriod(end.minusMonths(DEFAULT_MONTHLY_MONTHS - 1L), granularity);
        };
    }

    /**
     * Snap a date to the start of the period containing it, so buckets line up with calendar weeks
     * and months rather than with whatever date the caller happened to pass. A weekly series that
     * starts mid-week reports a short first week and shifts every boundary after it.
     */
    private static LocalDate startOfPeriod(LocalDate date, EarningsGranularity granularity) {
        return switch (granularity) {
            case DAILY -> date;
            case WEEKLY -> date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
            case MONTHLY -> date.withDayOfMonth(1);
        };
    }

    private static LocalDate endOfPeriod(LocalDate periodStart, EarningsGranularity granularity) {
        return switch (granularity) {
            case DAILY -> periodStart;
            case WEEKLY -> periodStart.plusDays(6);
            case MONTHLY -> periodStart.with(TemporalAdjusters.lastDayOfMonth());
        };
    }

    private static LocalDate nextPeriod(LocalDate periodStart, EarningsGranularity granularity) {
        return switch (granularity) {
            case DAILY -> periodStart.plusDays(1);
            case WEEKLY -> periodStart.plusWeeks(1);
            case MONTHLY -> periodStart.plusMonths(1);
        };
    }

    /** Best available display name. A professional with no profile still has to be nameable. */
    private static String nameOf(Professional professional) {
        Profile profile = professional.getProfile();
        if (profile == null) {
            return professional.getLicenceNumber();
        }
        String name =
            ((profile.getFirstName() == null ? "" : profile.getFirstName()) +
                " " +
                (profile.getLastName() == null ? "" : profile.getLastName())).trim();
        return name.isEmpty() ? professional.getLicenceNumber() : name;
    }

    /** Exposed for the tests that assert bucket boundaries without going through the repository. */
    static long periodsBetween(LocalDate start, LocalDate end, EarningsGranularity granularity) {
        return switch (granularity) {
            case DAILY -> ChronoUnit.DAYS.between(start, end) + 1;
            case WEEKLY -> ChronoUnit.WEEKS.between(start, end) + 1;
            case MONTHLY -> ChronoUnit.MONTHS.between(start, end) + 1;
        };
    }
}
