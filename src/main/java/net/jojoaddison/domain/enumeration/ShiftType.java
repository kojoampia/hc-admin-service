package net.jojoaddison.domain.enumeration;

/**
 * What one rostered cell of the duty-roster grid is: three worked windows, a rest day, and a
 * negotiated block.
 *
 * <ul>
 *   <li>{@link #DAY}, {@link #EVENING}, {@link #NIGHT} — worked, and payable once the date is past.
 *       The console labels them 07:00–15:00, 15:00–23:00 and 23:00–07:00, but <b>this service
 *       enforces no window anywhere</b>: it has no visit times, and {@code ShiftValuationService}
 *       contains no hour arithmetic. The hours are a display string in {@code console.json} and
 *       nothing reads them back. hc-professional is where a shift has a window with rules attached.
 *   <li>{@link #OFF} — <b>planned but not worked.</b> A rostered rest day, which is not the same
 *       thing as an empty cell: the grid needs three states (no row / rostered rest / worked), and
 *       {@code unassignedSlots} stays a <em>subtraction</em> from the week's capacity rather than a
 *       count of {@code OFF} rows. {@code ShiftValuationService} keys payability on
 *       {@code shift != OFF}.
 *   <li>{@link #FLEXIBLE} — hc-professional's negotiated 2–4 hour block on the assignment date,
 *       added here on 2026-09-04.
 * </ul>
 *
 * <p><b>{@code FLEXIBLE} exists here because the estate settled on one shift vocabulary.</b> This
 * enum and hc-professional's held four values each and differed by one at either end — this one had
 * {@code OFF} and no {@code FLEXIBLE}, that one the reverse — while the other three matched by name.
 * The alternative was a translation table at that boundary, and it was rejected because
 * <b>near-identity is more dangerous than clean difference</b>: three matching names invite every
 * reader, and the author of every new call site, to assume the fourth matches too. Both sides now
 * declare {@code DAY, EVENING, NIGHT, OFF, FLEXIBLE} in that order.
 *
 * <p>This service never <em>creates</em> a {@code FLEXIBLE} row today — the console's cell cycle
 * offers it, and hc-professional's roster is where such a shift is really negotiated — but it can
 * now receive, store, price and value one, which it could not before. That is the point: the earnings
 * contract this service serves to hc-professional could not previously express a shift that stack
 * routinely rosters.
 *
 * <p><b>Adding a value rewrote no stored document</b>, so {@code ShiftTypeMigration} beside this
 * enum is deliberately a no-op. Read its Javadoc before deleting it.
 *
 * <p>This enum is a <b>cross-repo invariant</b>. Its mirrors are {@code jdl/hc-admin-console.jdl},
 * {@code .jhipster/{DutyRoster,ShiftAssignment}.json}, the console's {@code shift-type.model.ts},
 * {@code SHIFT_CYCLE} and {@code i18n/en/operations-shiftType.json}, and — across the estate —
 * hc-professional's {@code ShiftType}, its web and mobile unions and their eight catalogues. Change
 * it here and all of them move in the same change.
 */
public enum ShiftType {
    DAY,
    EVENING,
    NIGHT,
    OFF,
    FLEXIBLE,
}
