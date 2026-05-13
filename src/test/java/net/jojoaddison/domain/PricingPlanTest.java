package net.jojoaddison.domain;

import static net.jojoaddison.domain.PricingPlanTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PricingPlanTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(PricingPlan.class);
        PricingPlan pricingPlan1 = getPricingPlanSample1();
        PricingPlan pricingPlan2 = new PricingPlan();
        assertThat(pricingPlan1).isNotEqualTo(pricingPlan2);

        pricingPlan2.setId(pricingPlan1.getId());
        assertThat(pricingPlan1).isEqualTo(pricingPlan2);

        pricingPlan2 = getPricingPlanSample2();
        assertThat(pricingPlan1).isNotEqualTo(pricingPlan2);
    }
}
