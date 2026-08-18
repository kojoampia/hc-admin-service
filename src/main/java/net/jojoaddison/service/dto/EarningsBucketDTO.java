package net.jojoaddison.service.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One point on the earnings series: what a professional worked in a period and what it came to.
 *
 * <p>Buckets are emitted for every period in the requested window, including empty ones. A gap the
 * client has to infer is a gap a line chart draws through — a fortnight off would otherwise look
 * like a straight line between the weeks either side of it rather than two weeks at zero.
 */
public record EarningsBucketDTO(LocalDate periodStart, LocalDate periodEnd, long shifts, BigDecimal amount) implements Serializable {}
