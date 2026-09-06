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
 * <p><b>Adding a value rewrote no stored document — but the release it came in did, and
 * {@code ShiftTypeMigration} beside this enum is what does the rewriting.</b> The first half still
 * holds: {@code FLEXIBLE} retired no value and renamed none, so every stored {@code shift} string
 * still parses and the roster collections needed nothing. The second half is why the class must not
 * be deleted — the same change gave {@code WageRate} a <b>required</b> {@code shift_type}, and the
 * migration is the only thing that backfills a row written before it. Without it, one such row makes
 * {@code WageRateService.rateTableUpTo} build an {@code EnumMap} on a null key and every wage-rates
 * and earnings screen answers 500.
 *
 * <p>This paragraph called the migration "deliberately a no-op" until 2026-09-04 — written before the
 * backfill landed, left standing after it, and directly contradicted by the migration's own Javadoc.
 * The stale half was the dangerous one: "a no-op … read its Javadoc before deleting it" reads as
 * permission to delete the only backfill for a required money field. Read that Javadoc; do not
 * delete the class.
 *
 * <p>This enum is a <b>cross-repo invariant</b>, and the list below is the whole of it. In this
 * repository: {@code jdl/hc-admin-console.jdl}'s {@code enum ShiftType} <b>and</b> its
 * {@code entity WageRate}, plus {@code .jhipster/ShiftAssignment.json} (there was a
 * {@code .jhipster/DutyRoster.json} too, deleted with that entity on 2026-09-04). In
 * {@code app/}: {@code hc-admin.jdl}, {@code .jhipster/ShiftAssignment.json},
 * {@code shift-type.model.ts}, {@code SHIFT_CYCLE} and {@code i18n/en/operations-shiftType.json}.
 * Across the estate: hc-professional's {@code ShiftType}, its web and mobile unions and their eight
 * catalogues. Change it here and all of them move in the same change.
 *
 * <p><b>The two {@code app/} generator inputs were missing from this list until 2026-09-04, and a
 * reader following it missed them exactly as the change that wrote it did.</b> They are the
 * dangerous pair rather than an omission of detail: regenerating {@code ShiftAssignment} in
 * {@code app/} rewrites {@code shift-type.model.ts} and {@code operations-shiftType.json}
 * <em>together</em>, so both sides agree at four values and {@code enum-coverage.spec.ts} stays
 * green while the console loses {@code FLEXIBLE}. {@code JhipsterEnumFieldValuesTest} here and
 * {@code generator-inputs.spec.ts} in {@code app/} now hold both repositories' inputs to the code,
 * so the list is a reading aid and no longer the only thing looking.
 */
public enum ShiftType {
    DAY,
    EVENING,
    NIGHT,
    OFF,
    FLEXIBLE,
}
