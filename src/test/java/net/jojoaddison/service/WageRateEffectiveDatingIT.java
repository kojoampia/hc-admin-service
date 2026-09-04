package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.WageRate;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.ShiftType;
import net.jojoaddison.repository.WageRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Rates are effective-dated, and this is the property that decision buys: <b>raising a rate must not
 * restate a total that has already been reported.</b>
 *
 * <p>It is worth a test of its own because the failure is silent and arrives late. Nothing errors
 * when a shift is valued at the wrong rate — last month's payroll figure simply reads differently
 * than it did last month, and by then the money has usually been paid. The obvious simplification
 * (one editable row per role) reintroduces it in a single commit, and no CRUD test would notice.
 *
 * <p><b>Since 2026-09-04 the key carries a shift type too</b>, and the second half of this class is
 * about that. The dimension was added while the collection was empty in production for exactly the
 * reason above: adding it later would have meant deciding what every already-valued shift was worth
 * under the new key, which is a restatement of a wage bill that has already been paid.
 */
@IntegrationTest
class WageRateEffectiveDatingIT {

    @Autowired
    private WageRateRepository wageRateRepository;

    @Autowired
    private WageRateService wageRateService;

    @BeforeEach
    void seedRates() {
        wageRateRepository.deleteAll();
        wageRateRepository.saveAll(
            List.of(
                rate(ProfessionalRole.DOCTOR, ShiftType.DAY, 500, LocalDate.of(2026, 1, 1)),
                rate(ProfessionalRole.DOCTOR, ShiftType.DAY, 550, LocalDate.of(2026, 9, 1)),
                // A night premium on the same role, unsuperseded — so a lookup that ignored the
                // shift type would still find the DAY rows and read plausibly.
                rate(ProfessionalRole.DOCTOR, ShiftType.NIGHT, 750, LocalDate.of(2026, 1, 1)),
                rate(ProfessionalRole.NURSE, ShiftType.DAY, 300, LocalDate.of(2026, 1, 1)),
                rate(ProfessionalRole.CAREGIVER, ShiftType.DAY, 200, LocalDate.of(2026, 1, 1))
            )
        );
    }

    private static WageRate rate(ProfessionalRole role, ShiftType shiftType, int amount, LocalDate validFrom) {
        return new WageRate().role(role).shiftType(shiftType).amount(new BigDecimal(amount)).currency("GHS").validFrom(validFrom);
    }

    @Test
    void aShiftIsValuedAtTheRateInForceOnItsOwnDate() {
        assertThat(amountOn(ProfessionalRole.DOCTOR, ShiftType.DAY, LocalDate.of(2026, 8, 14))).isEqualByComparingTo("500");
        assertThat(amountOn(ProfessionalRole.DOCTOR, ShiftType.DAY, LocalDate.of(2026, 9, 3))).isEqualByComparingTo("550");
    }

    @Test
    void theRateAppliesFromItsValidFromDateInclusive() {
        assertThat(amountOn(ProfessionalRole.DOCTOR, ShiftType.DAY, LocalDate.of(2026, 8, 31))).isEqualByComparingTo("500");
        assertThat(amountOn(ProfessionalRole.DOCTOR, ShiftType.DAY, LocalDate.of(2026, 9, 1))).isEqualByComparingTo("550");
    }

    /**
     * The point of the whole design: adding a rate for September leaves August alone.
     */
    @Test
    void aLaterRateDoesNotRestateAnEarlierShift() {
        BigDecimal augustBefore = amountOn(ProfessionalRole.NURSE, ShiftType.DAY, LocalDate.of(2026, 8, 14));

        wageRateRepository.save(rate(ProfessionalRole.NURSE, ShiftType.DAY, 400, LocalDate.of(2026, 10, 1)));

        assertThat(amountOn(ProfessionalRole.NURSE, ShiftType.DAY, LocalDate.of(2026, 8, 14))).isEqualByComparingTo(augustBefore);
        assertThat(amountOn(ProfessionalRole.NURSE, ShiftType.DAY, LocalDate.of(2026, 10, 2))).isEqualByComparingTo("400");
    }

    @Test
    void aShiftBeforeAnyConfiguredRateHasNoRate() {
        assertThat(wageRateService.rateOn(ProfessionalRole.DOCTOR, ShiftType.DAY, LocalDate.of(2025, 12, 31))).isEmpty();
    }

    @Test
    void anUnpricedRoleHasNoRate() {
        assertThat(wageRateService.rateOn(ProfessionalRole.THERAPIST, ShiftType.DAY, LocalDate.of(2026, 8, 14))).isEmpty();
    }

    // ------------------------------------------------------ the shift dimension

    /**
     * The rate follows the shift, not only the role.
     *
     * <p>A DOCTOR night pays 750 where a DOCTOR day pays 500 on the same date. This is the assertion
     * that fails if {@code ShiftValuationService} or {@code RateTable} ever resolves on
     * {@code (role, date)} again — which reads perfectly, values every shift at the day rate, and
     * under-reports a clinician's earnings by whatever the night premium is.
     */
    @Test
    void twoShiftTypesOfTheSameRoleOnTheSameDayAreDifferentRates() {
        LocalDate date = LocalDate.of(2026, 8, 14);

        assertThat(amountOn(ProfessionalRole.DOCTOR, ShiftType.DAY, date)).isEqualByComparingTo("500");
        assertThat(amountOn(ProfessionalRole.DOCTOR, ShiftType.NIGHT, date)).isEqualByComparingTo("750");
    }

    /**
     * Supersession is per cell, not per role.
     *
     * <p>DOCTOR DAY rises to 550 on 1 September and DOCTOR NIGHT does not move. A resolver that
     * ordered by {@code validFrom} across the whole role would hand the September 550 back for a
     * September night, quietly cutting the night rate by 200 rather than leaving it alone.
     */
    @Test
    void repricingOneShiftTypeLeavesTheOthersOnTheirOwnRates() {
        LocalDate september = LocalDate.of(2026, 9, 3);

        assertThat(amountOn(ProfessionalRole.DOCTOR, ShiftType.DAY, september)).isEqualByComparingTo("550");
        assertThat(amountOn(ProfessionalRole.DOCTOR, ShiftType.NIGHT, september)).isEqualByComparingTo("750");
    }

    /**
     * There is no fallback from an unpriced cell to the role's other rates, and that is deliberate.
     *
     * <p>DOCTOR is priced for DAY and NIGHT and not for EVENING. An evening shift is <b>unpriced</b>
     * rather than valued at either of the others: the console reports unpriced shifts distinctly
     * from a rate of zero, so an admin sees a gap in the grid instead of a number nobody chose.
     */
    @Test
    void anUnpricedShiftTypeDoesNotFallBackToThePricedOnes() {
        assertThat(wageRateService.rateOn(ProfessionalRole.DOCTOR, ShiftType.EVENING, LocalDate.of(2026, 8, 14))).isEmpty();
    }

    /**
     * The batch resolver has to agree with the single lookup — it exists only to avoid a query per
     * shift, and a divergence between them would value a list differently from a detail page.
     */
    @Test
    void theBatchRateTableAgreesWithTheSingleLookup() {
        LocalDate asOf = LocalDate.of(2026, 9, 30);
        WageRateService.RateTable table = wageRateService.rateTableUpTo(asOf);

        for (ProfessionalRole role : ProfessionalRole.values()) {
            for (ShiftType shiftType : ShiftType.values()) {
                for (LocalDate date : List.of(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 2))) {
                    assertThat(table.rateOn(role, shiftType, date).map(WageRate::getAmount))
                        .as("%s %s on %s", role, shiftType, date)
                        .isEqualTo(wageRateService.rateOn(role, shiftType, date).map(WageRate::getAmount));
                }
            }
        }
    }

    private BigDecimal amountOn(ProfessionalRole role, ShiftType shiftType, LocalDate date) {
        return wageRateService.rateOn(role, shiftType, date).map(WageRate::getAmount).orElseThrow();
    }
}
