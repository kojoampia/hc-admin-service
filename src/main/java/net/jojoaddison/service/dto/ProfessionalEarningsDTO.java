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
 * @param unpricedShifts shifts that fell before any rate was configured for the role. They are
 *     counted in {@code shiftsCompleted} and contribute nothing to {@code totalAccrued}, so a
 *     non-zero value here is the difference between "earned nothing" and "we never set a price" —
 *     the console needs to say which.
 * @param currency the currency the rates were denominated in, or null when nothing was priced.
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
    List<EarningsBucketDTO> buckets
)
    implements Serializable {}
