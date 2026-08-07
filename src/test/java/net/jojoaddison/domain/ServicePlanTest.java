package net.jojoaddison.domain;

import static net.jojoaddison.domain.PlanFeatureTestSamples.*;
import static net.jojoaddison.domain.ServicePlanTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ServicePlanTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ServicePlan.class);
        ServicePlan servicePlan1 = getServicePlanSample1();
        ServicePlan servicePlan2 = new ServicePlan();
        assertThat(servicePlan1).isNotEqualTo(servicePlan2);

        servicePlan2.setId(servicePlan1.getId());
        assertThat(servicePlan1).isEqualTo(servicePlan2);

        servicePlan2 = getServicePlanSample2();
        assertThat(servicePlan1).isNotEqualTo(servicePlan2);
    }

    @Test
    void featureTest() {
        ServicePlan servicePlan = getServicePlanRandomSampleGenerator();
        PlanFeature planFeatureBack = getPlanFeatureRandomSampleGenerator();

        servicePlan.addFeature(planFeatureBack);
        assertThat(servicePlan.getFeatures()).containsOnly(planFeatureBack);
        assertThat(planFeatureBack.getPlan()).isEqualTo(servicePlan);

        servicePlan.removeFeature(planFeatureBack);
        assertThat(servicePlan.getFeatures()).doesNotContain(planFeatureBack);
        assertThat(planFeatureBack.getPlan()).isNull();

        servicePlan.features(new HashSet<>(Set.of(planFeatureBack)));
        assertThat(servicePlan.getFeatures()).containsOnly(planFeatureBack);
        assertThat(planFeatureBack.getPlan()).isEqualTo(servicePlan);

        servicePlan.setFeatures(new HashSet<>());
        assertThat(servicePlan.getFeatures()).doesNotContain(planFeatureBack);
        assertThat(planFeatureBack.getPlan()).isNull();
    }
}
