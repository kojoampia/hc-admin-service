package net.jojoaddison.service.dto;

import java.io.Serializable;
import java.time.LocalDate;
import net.jojoaddison.domain.enumeration.ShiftType;

/**
 * One row of a professional's own roster.
 *
 * <p>Unlike the earnings series, this deliberately includes shifts that are <em>not</em> payable —
 * both the ones still in the future and the {@code OFF} days. A roster a professional looks at is a
 * schedule, not a payslip: "what am I working next week" and "what have I been paid for" are
 * different questions asked of the same rows, and filtering here would answer only the second.
 *
 * @param date the day worked.
 * @param shift the shift type; {@code OFF} is a real row in the roster grid, not an absence of one.
 * @param payable whether this row counts toward earnings — in the past, and not an off day. Carried
 *     explicitly rather than left for the client to derive, because the rule that decides it
 *     (a shift becomes payable only once the day has finished) lives in {@link
 *     net.jojoaddison.service.ShiftValuationService} and must not be reimplemented per client. Two
 *     clients deriving it from a date comparison would disagree the moment one of them used its own
 *     timezone's "today".
 */
public record ProfessionalShiftDTO(LocalDate date, ShiftType shift, boolean payable) implements Serializable {}
