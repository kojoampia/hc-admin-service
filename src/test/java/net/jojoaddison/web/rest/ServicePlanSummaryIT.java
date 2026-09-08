package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.PlanFeature;
import net.jojoaddison.domain.ServicePlan;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.repository.PlanFeatureRepository;
import net.jojoaddison.repository.ServicePlanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/service-plans/summary} — the plan mix beneath the console's plan board.
 *
 * <p>Its own class rather than another case in {@code ServicePlanResourceIT}, for the reason
 * {@link VendorSummaryIT} gives: every assertion here is a proportion of the whole collection, and
 * that only means anything if this test owns what is in it.
 *
 * <p><strong>Subscribers are counted from {@code Patient.plan}.</strong> This test used to set a
 * deliberately wrong {@code ServicePlan.subscriberCount} on every plan, so that a regression reading
 * the stored counter failed here rather than shipping figures nobody could reconcile. That field was
 * deleted on 2026-08-24 and the guard is now structural — there is no counter to read back, and a
 * reintroduced one would have to get past {@link net.jojoaddison.service.dto.ServicePlanSummaryDTO}'s
 * javadoc first. What remains asserted here is the positive half, and it is the half that matters:
 * every figure reconciles to the patients an operator can actually open.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class ServicePlanSummaryIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ServicePlanRepository servicePlanRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private PlanFeatureRepository planFeatureRepository;

    private ServicePlan pear;
    private ServicePlan pawpaw;
    private ServicePlan melon;

    @BeforeEach
    void seed() {
        planFeatureRepository.deleteAll();
        patientRepository.deleteAll();
        servicePlanRepository.deleteAll();

        // Abofonsa's published tiers and prices, deliberately. This fixture held the retired
        // Bridge catalogue at 320 / 680 / 1,240 until 2026-09-08, which is the price list backlog
        // item 51 was opened about; a test that keeps it alive is one more place a reader learns the
        // wrong numbers from.
        //
        // Saved out of order on purpose. displayOrder says PEAR, PAWPAW, MELON; insertion order says
        // MELON, PEAR, PAWPAW; name order says MELON, PAWPAW, PEAR. No two agree, so the ordering
        // assertion below cannot be satisfied by the summary having sorted on something else, or on
        // nothing at all.
        melon = servicePlanRepository.save(plan("MELON Plan", "MELON", 3, "8000"));
        pear = servicePlanRepository.save(plan("PEAR Plan", "PEAR", 1, "3000"));
        pawpaw = servicePlanRepository.save(plan("PAWPAW Plan", "PAWPAW", 2, "5000"));

        patientRepository.saveAll(
            List.of(
                patient(pear, false),
                patient(pear, false),
                patient(pawpaw, false),
                patient(pawpaw, false),
                patient(pawpaw, false),
                patient(melon, false),
                // Archived: out of every figure, because the directory below does not show it.
                patient(melon, true),
                // No plan: not a subscriber to anything, so out of the denominator too. Were it
                // counted, the shares would sum to less than 100 and nothing would say why.
                patient(null, false)
            )
        );
    }

    @AfterEach
    void tearDown() {
        planFeatureRepository.deleteAll();
        patientRepository.deleteAll();
        servicePlanRepository.deleteAll();
    }

    /**
     * Six subscribers: two PEAR, three PAWPAW, one MELON — every one of them a patient this test
     * put in the directory, which is the whole property being asserted.
     */
    @Test
    void subscribersAreCountedFromThePatientDirectory() throws Exception {
        mvc
            .perform(get("/api/service-plans/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSubscribers").value(6))
            // Ordered by displayOrder — the order Abofonsa publishes the tiers in, and so the order
            // the public site draws them in. It was the plan tier's ordinal until 2026-09-08; see
            // the insertion order in seed() for why this assertion is not satisfiable by accident.
            .andExpect(jsonPath("$.mix[0].planId").value(pear.getId()))
            .andExpect(jsonPath("$.mix[0].subscribers").value(2))
            .andExpect(jsonPath("$.mix[1].planId").value(pawpaw.getId()))
            .andExpect(jsonPath("$.mix[1].subscribers").value(3))
            .andExpect(jsonPath("$.mix[2].planId").value(melon.getId()))
            .andExpect(jsonPath("$.mix[2].subscribers").value(1));
    }

    /**
     * Revenue is price times subscribers, per plan.
     *
     * <p>3000×2 = 6000, 5000×3 = 15000, 8000×1 = 8000. Read as doubles so 6000 and 6000.00 both
     * pass — the scale depends on how the BigDecimal was stored, which is not what this asserts.
     */
    @Test
    void revenueIsPriceTimesSubscribers() throws Exception {
        mvc
            .perform(get("/api/service-plans/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mix[0].monthlyRevenue").value(new BigDecimal("6000").doubleValue()))
            .andExpect(jsonPath("$.mix[1].monthlyRevenue").value(new BigDecimal("15000").doubleValue()))
            .andExpect(jsonPath("$.mix[2].monthlyRevenue").value(new BigDecimal("8000").doubleValue()));
    }

    /**
     * The shares total exactly 100, which independent rounding does not guarantee.
     *
     * <p>2/6, 3/6, 1/6 is 33.3, 50.0, 16.7 — and 33.333… and 16.666… both round away from a clean
     * sum. Truncated they give 33.3 + 50.0 + 16.6 = 99.9, and the largest-remainder pass hands the
     * missing tenth to the row with the biggest discarded fraction, which is Family. A column of
     * percentages that does not add up invites a reader to distrust the whole table.
     */
    @Test
    void sharesSumToExactlyOneHundred() throws Exception {
        mvc
            .perform(get("/api/service-plans/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mix[0].share").value(33.3))
            .andExpect(jsonPath("$.mix[1].share").value(50.0))
            .andExpect(jsonPath("$.mix[2].share").value(16.7));
    }

    /**
     * With nobody subscribed, every share is <strong>null</strong> and every revenue is zero.
     *
     * <p>This is the case production is in, and the distinction is load-bearing. A share of an empty
     * directory is undefined, and the console renders null as an em dash; zero would be the console
     * stating that each plan holds none of a market, which is a claim rather than an absence.
     * Revenue genuinely is nought — no subscribers at any price earns nothing.
     */
    @Test
    void anEmptyDirectoryHasNoShareRatherThanZeroShare() throws Exception {
        patientRepository.deleteAll();

        mvc
            .perform(get("/api/service-plans/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSubscribers").value(0))
            .andExpect(jsonPath("$.mix[0].share").doesNotExist())
            .andExpect(jsonPath("$.mix[1].share").doesNotExist())
            .andExpect(jsonPath("$.mix[2].share").doesNotExist())
            .andExpect(jsonPath("$.mix[0].monthlyRevenue").value(0))
            .andExpect(jsonPath("$.mix[1].monthlyRevenue").value(0))
            .andExpect(jsonPath("$.mix[2].monthlyRevenue").value(0));
    }

    /**
     * {@code planId.equals} filters the feature list server-side, and the count follows the filter.
     *
     * <p>The plan board draws a feature list per card. Reading them unfiltered happens to work while
     * the whole catalogue of features fits inside one page — eighteen against a default of twenty in
     * the seed — and starts dropping bullets off cards silently at the nineteenth. The header is
     * asserted as well as the body because an unknown request parameter is silently ignored by
     * Spring, so a body-only check passes against a filter that does nothing.
     */
    @Test
    void featuresFilterByPlan() throws Exception {
        planFeatureRepository.saveAll(
            List.of(
                feature("1 home visit per month", 0, pear),
                feature("Full digital health record", 1, pear),
                feature("Fortnightly nursing visits", 0, pawpaw)
            )
        );

        mvc
            .perform(get("/api/plan-features?planId.equals=" + pear.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "2"))
            .andExpect(jsonPath("$[*].label", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Fortnightly nursing visits"))));

        mvc
            .perform(get("/api/plan-features?planId.equals=" + melon.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "0"));

        // Unfiltered still returns everything, so the filter did not narrow the endpoint for callers
        // that do not send it.
        mvc.perform(get("/api/plan-features")).andExpect(status().isOk()).andExpect(header().string("X-Total-Count", "3"));
    }

    /**
     * The path is a literal segment, not a plan whose id is "summary".
     *
     * <p>{@code /{id}} is declared on the same controller. PathPattern prefers the literal, but the
     * two orderings are indistinguishable in a passing test unless something asserts which handler
     * answered — a 404 here would be {@code getServicePlan("summary")} winning.
     */
    @Test
    void summaryIsNotReadAsAPlanId() throws Exception {
        mvc.perform(get("/api/service-plans/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.totalSubscribers").exists());
    }

    private static ServicePlan plan(String name, String code, int displayOrder, String price) {
        return new ServicePlan()
            .name(name)
            .code(code)
            .displayOrder(displayOrder)
            .monthlyPrice(new BigDecimal(price))
            .currency("GHS")
            .featured(false);
    }

    private static PlanFeature feature(String label, int position, ServicePlan plan) {
        return new PlanFeature().label(label).position(position).plan(plan);
    }

    private static Patient patient(ServicePlan plan, boolean archived) {
        return new Patient().status(AccountStatus.ACTIVE).joinedOn(LocalDate.of(2026, 1, 1)).plan(plan).isArchived(archived);
    }
}
