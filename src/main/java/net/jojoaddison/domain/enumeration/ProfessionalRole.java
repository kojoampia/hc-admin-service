package net.jojoaddison.domain.enumeration;

/**
 * The ProfessionalRole enumeration.
 *
 * <p><b>{@code CAREGIVER} was renamed to {@code CARER} on 2026-09-16</b> — backlog item 35, decision
 * D3 — so that this service and hc-professional say the same word for the same discipline. It was
 * the one name of the five that differed, and {@code duty-roster-resolution.md} § 6.4 calls that
 * near-identity more dangerous than a clean difference: {@code RoundPlanningService.duty} existed
 * partly to translate it, and a reader of either stack had no way to tell whether the two names meant
 * two things.
 *
 * <p><b>This enum is persisted</b>, in {@code professional.role} and {@code wage_rate.role}, so the
 * rename is a data change and not only a source change. {@code ProfessionalRoleRenameMigration}
 * rewrites the stored values; without it a stored {@code "CAREGIVER"} fails to map on the read that
 * happens to touch it first. Do not add a value to this enum and assume nothing is stored under the
 * old one.
 *
 * <p><b>⚠ FIVE OF THESE EIGHT ARE PAYABLE AND THREE ARE NOT, AND THAT IS DELIBERATE</b> — backlog
 * item 35, decision D2. This enum is not a directory label: it is the <b>wage-grid key</b>, read by
 * {@code WageRate}, {@code WageRateService}, {@code ShiftValuationService}, {@code RoundPlanningService}
 * and {@code DashboardMetricsService}.
 *
 * <table>
 *   <caption>What each value is for</caption>
 *   <tr><th>values</th><th>rostered and paid?</th></tr>
 *   <tr><td>{@code CARER PARAMEDIC THERAPIST NURSE DOCTOR}</td><td><b>yes</b> — the 5×5 wage grid</td></tr>
 *   <tr><td>{@code PHARMACIST CHEMIST TECHNICIAN}</td><td><b>no</b> — directory only</td></tr>
 * </table>
 *
 * <p>The last three exist because hc-professional recognises <b>eight</b> disciplines and this service
 * recognised five, so a clinician of one of those three could not be described at all. hc-admin does
 * not roster or pay them, so <b>the wage grid stays five roles by five shift types and must not be
 * extended to cover them</b>.
 *
 * <p><b>The consequence is intended and will look like a bug.</b>
 * {@code ShiftValuationService} resolves {@code (role, shiftType, date)} by <b>exact match with no
 * fallback</b>, so a shift worked by a {@code PHARMACIST}, {@code CHEMIST} or {@code TECHNICIAN}
 * reports as <b>unpriced</b> — for ever, by design, not until somebody gets round to pricing it.
 * <b>Do not add a fallback and do not add wage rates for them</b>; a fallback would value a shift at
 * another discipline's rate, which is a wrong number that reads as a right one, and that is the exact
 * failure the effective-dated grid exists to prevent.
 *
 * <p>Anyone adding a value here should decide the same question first: is it payable? If yes it needs
 * wage rates and the grid grows; if no, add it here and say so in this table.
 */
public enum ProfessionalRole {
    // Payable — the 5×5 wage grid is keyed on these five.
    CARER,
    PARAMEDIC,
    THERAPIST,
    NURSE,
    DOCTOR,
    // Directory only — hc-professional recognises these, hc-admin neither rosters nor pays them.
    // Shifts worked by them report unpriced, deliberately. See the javadoc above.
    PHARMACIST,
    CHEMIST,
    TECHNICIAN;

    /**
     * The roles hc-admin rosters and pays — the wage grid is keyed on exactly these.
     *
     * <p><b>This exists so that "three of the eight are directory only" is executable rather than a
     * comment.</b> Before it, every wage-grid assertion derived from {@link #values()}, which quietly
     * encoded "every role is payable"; adding a directory-only role broke four tests that were right
     * about their intent and wrong about their source. A test that derives from the wrong set is not
     * more robust than one that enumerates — it is enumeration with extra steps.
     *
     * <p>So derive grid expectations from <b>this</b>, never from {@code values()}. A new payable role
     * lands here and the grid grows with it; a new directory-only role does not, and nothing has to be
     * remembered.
     */
    public static final java.util.Set<ProfessionalRole> PAYABLE = java.util.Collections.unmodifiableSet(
        java.util.EnumSet.of(CARER, PARAMEDIC, THERAPIST, NURSE, DOCTOR)
    );

    /** Whether hc-admin rosters and pays this discipline. See {@link #PAYABLE}. */
    public boolean isPayable() {
        return PAYABLE.contains(this);
    }
}
