package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.WageRate;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
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
                rate(ProfessionalRole.DOCTOR, 500, LocalDate.of(2026, 1, 1)),
                rate(ProfessionalRole.DOCTOR, 550, LocalDate.of(2026, 9, 1)),
                rate(ProfessionalRole.NURSE, 300, LocalDate.of(2026, 1, 1)),
                rate(ProfessionalRole.CAREGIVER, 200, LocalDate.of(2026, 1, 1))
            )
        );
    }

    private static WageRate rate(ProfessionalRole role, int amount, LocalDate validFrom) {
        return new WageRate().role(role).amount(new BigDecimal(amount)).currency("GHS").validFrom(validFrom);
    }

    @Test
    void aShiftIsValuedAtTheRateInForceOnItsOwnDate() {
        assertThat(amountOn(ProfessionalRole.DOCTOR, LocalDate.of(2026, 8, 14))).isEqualByComparingTo("500");
        assertThat(amountOn(ProfessionalRole.DOCTOR, LocalDate.of(2026, 9, 3))).isEqualByComparingTo("550");
    }

    @Test
    void theRateAppliesFromItsValidFromDateInclusive() {
        assertThat(amountOn(ProfessionalRole.DOCTOR, LocalDate.of(2026, 8, 31))).isEqualByComparingTo("500");
        assertThat(amountOn(ProfessionalRole.DOCTOR, LocalDate.of(2026, 9, 1))).isEqualByComparingTo("550");
    }

    /**
     * The point of the whole design: adding a rate for September leaves August alone.
     */
    @Test
    void aLaterRateDoesNotRestateAnEarlierShift() {
        BigDecimal augustBefore = amountOn(ProfessionalRole.NURSE, LocalDate.of(2026, 8, 14));

        wageRateRepository.save(rate(ProfessionalRole.NURSE, 400, LocalDate.of(2026, 10, 1)));

        assertThat(amountOn(ProfessionalRole.NURSE, LocalDate.of(2026, 8, 14))).isEqualByComparingTo(augustBefore);
        assertThat(amountOn(ProfessionalRole.NURSE, LocalDate.of(2026, 10, 2))).isEqualByComparingTo("400");
    }

    @Test
    void aShiftBeforeAnyConfiguredRateHasNoRate() {
        assertThat(wageRateService.rateOn(ProfessionalRole.DOCTOR, LocalDate.of(2025, 12, 31))).isEmpty();
    }

    @Test
    void anUnpricedRoleHasNoRate() {
        assertThat(wageRateService.rateOn(ProfessionalRole.THERAPIST, LocalDate.of(2026, 8, 14))).isEmpty();
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
            for (LocalDate date : List.of(LocalDate.of(2026, 3, 5), LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 2))) {
                assertThat(table.rateOn(role, date).map(WageRate::getAmount))
                    .as("%s on %s", role, date)
                    .isEqualTo(wageRateService.rateOn(role, date).map(WageRate::getAmount));
            }
        }
    }

    private BigDecimal amountOn(ProfessionalRole role, LocalDate date) {
        return wageRateService.rateOn(role, date).map(WageRate::getAmount).orElseThrow();
    }
}
