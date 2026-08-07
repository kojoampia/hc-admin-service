package net.jojoaddison.domain;

import static net.jojoaddison.domain.PlanFeatureTestSamples.*;
import static net.jojoaddison.domain.ServicePlanTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PlanFeatureTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(PlanFeature.class);
        PlanFeature planFeature1 = getPlanFeatureSample1();
        PlanFeature planFeature2 = new PlanFeature();
        assertThat(planFeature1).isNotEqualTo(planFeature2);

        planFeature2.setId(planFeature1.getId());
        assertThat(planFeature1).isEqualTo(planFeature2);

        planFeature2 = getPlanFeatureSample2();
        assertThat(planFeature1).isNotEqualTo(planFeature2);
    }

    @Test
    void planTest() {
        PlanFeature planFeature = getPlanFeatureRandomSampleGenerator();
        ServicePlan servicePlanBack = getServicePlanRandomSampleGenerator();

        planFeature.setPlan(servicePlanBack);
        assertThat(planFeature.getPlan()).isEqualTo(servicePlanBack);

        planFeature.plan(null);
        assertThat(planFeature.getPlan()).isNull();
    }
}
