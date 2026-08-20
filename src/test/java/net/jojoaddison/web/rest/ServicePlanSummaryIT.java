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
import net.jojoaddison.domain.enumeration.PlanTier;
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
 * <p><strong>Subscribers are counted from {@code Patient.plan}.</strong> The seeded
 * {@code ServicePlan.subscriberCount} disagrees with the patient directory — 41/52/23 against twelve
 * patients — and this test deliberately sets that field to a wrong number on every plan, so a
 * regression that reads the stored counter fails here rather than shipping figures nobody can
 * reconcile.
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

    private ServicePlan essential;
    private ServicePlan plus;
    private ServicePlan family;

    @BeforeEach
    void seed() {
        planFeatureRepository.deleteAll();
        patientRepository.deleteAll();
        servicePlanRepository.deleteAll();

        // subscriberCount is set to a figure that matches nothing on purpose. Nothing may read it.
        essential = servicePlanRepository.save(plan("Bridge Essential", PlanTier.ESSENTIAL, "320", 41));
        plus = servicePlanRepository.save(plan("Bridge Plus", PlanTier.PLUS, "680", 52));
        family = servicePlanRepository.save(plan("Bridge Family", PlanTier.FAMILY, "1240", 23));

        patientRepository.saveAll(
            List.of(
                patient(essential, false),
                patient(essential, false),
                patient(plus, false),
                patient(plus, false),
                patient(plus, false),
                patient(family, false),
                // Archived: out of every figure, because the directory below does not show it.
                patient(family, true),
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
     * Six subscribers: two Essential, three Plus, one Family. Not 116, which is what the stored
     * counters add up to.
     */
    @Test
    void subscribersAreCountedFromThePatientDirectory() throws Exception {
        mvc
            .perform(get("/api/service-plans/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalSubscribers").value(6))
            // Ordered by tier, which is also ascending price and the order the cards are drawn in.
            .andExpect(jsonPath("$.mix[0].planId").value(essential.getId()))
            .andExpect(jsonPath("$.mix[0].subscribers").value(2))
            .andExpect(jsonPath("$.mix[1].planId").value(plus.getId()))
            .andExpect(jsonPath("$.mix[1].subscribers").value(3))
            .andExpect(jsonPath("$.mix[2].planId").value(family.getId()))
            .andExpect(jsonPath("$.mix[2].subscribers").value(1));
    }

    /**
     * Revenue is price times subscribers, per plan.
     *
     * <p>320×2 = 640, 680×3 = 2040, 1240×1 = 1240. Read as doubles so 640 and 640.00 both pass —
     * the scale depends on how the BigDecimal was stored, which is not what this asserts.
     */
    @Test
    void revenueIsPriceTimesSubscribers() throws Exception {
        mvc
            .perform(get("/api/service-plans/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mix[0].monthlyRevenue").value(new BigDecimal("640").doubleValue()))
            .andExpect(jsonPath("$.mix[1].monthlyRevenue").value(new BigDecimal("2040").doubleValue()))
            .andExpect(jsonPath("$.mix[2].monthlyRevenue").value(new BigDecimal("1240").doubleValue()));
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
                feature("1 home visit per month", 0, essential),
                feature("Full digital health record", 1, essential),
                feature("Fortnightly nursing visits", 0, plus)
            )
        );

        mvc
            .perform(get("/api/plan-features?planId.equals=" + essential.getId()))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Total-Count", "2"))
            .andExpect(jsonPath("$[*].label", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Fortnightly nursing visits"))));

        mvc
            .perform(get("/api/plan-features?planId.equals=" + family.getId()))
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

    private static ServicePlan plan(String name, PlanTier tier, String price, int wrongStoredCount) {
        ServicePlan plan = new ServicePlan().name(name).tier(tier).monthlyPrice(new BigDecimal(price)).currency("GHS").featured(false);
        plan.setSubscriberCount(wrongStoredCount);
        return plan;
    }

    private static PlanFeature feature(String label, int position, ServicePlan plan) {
        return new PlanFeature().label(label).position(position).plan(plan);
    }

    private static Patient patient(ServicePlan plan, boolean archived) {
        return new Patient().status(AccountStatus.ACTIVE).joinedOn(LocalDate.of(2026, 1, 1)).plan(plan).isArchived(archived);
    }
}
