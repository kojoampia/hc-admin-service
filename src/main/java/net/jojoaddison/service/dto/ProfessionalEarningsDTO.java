package net.jojoaddison.service.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.domain.enumeration.EarningsGranularity;
import net.jojoaddison.domain.enumeration.ProfessionalRole;

/**
 * What one professional has earned over a window, and the series behind it.
 *
 * @param from the first date counted.
 * @param to the last date counted. This is <em>not</em> necessarily the {@code to} that was asked
 *     for: a shift is payable only once it is in the past, so the window is clipped at yesterday
 *     and this field reports where it actually ended. A client that echoes back its own request
 *     would otherwise label a chart with a range it does not contain.
 * @param shiftsCompleted payable shifts in the window — worked, and not an off day.
 * @param totalAccrued the sum of the buckets.
 * @param unpricedShifts shifts no rate covered: the {@code (role, shiftType)} cell had no rate in
 *     force on the day the shift was worked. They are counted in {@code shiftsCompleted} and
 *     contribute nothing to {@code totalAccrued}, so a non-zero value here is the difference between
 *     "earned nothing" and "we never set a price" — the console needs to say which.
 *     <p><b>Two states produce it and this count does not distinguish them.</b> Until 2026-09-04
 *     there was only one — a shift worked before any rate existed for the role — and this
 *     description said so. A rate is keyed on the shift type now, so the second is a role priced for
 *     days and not for nights, which makes every night worked unpriced however old the day rate is.
 *     Describing only the first sends an administrator to look at {@code validFrom} when the answer
 *     is an empty cell in the pricing grid, and tells a clinician something false about dates.
 *     Anything rendering this must say "no rate", not "before a rate".
 * @param currency the currency the rates were denominated in, or null when nothing was priced.
 * @param archived whether the professional has been archived out of the directory. Reported rather
 *     than filtered on: archiving removes somebody from the lists you browse, and says nothing
 *     about whether work already done was done. The wage bill has to include them and the screen
 *     has to be able to mark the row.
 */
public record ProfessionalEarningsDTO(
    String professionalId,
    String professionalName,
    ProfessionalRole role,
    EarningsGranularity granularity,
    LocalDate from,
    LocalDate to,
    long shiftsCompleted,
    BigDecimal totalAccrued,
    long unpricedShifts,
    String currency,
    boolean archived,
    List<EarningsBucketDTO> buckets
) implements Serializable {}
