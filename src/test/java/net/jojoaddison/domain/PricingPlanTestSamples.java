package net.jojoaddison.domain;

import java.util.UUID;

public class PricingPlanTestSamples {

    public static PricingPlan getPricingPlanSample1() {
        return new PricingPlan().id("id1").name("name1").description("description1").features("features1");
    }

    public static PricingPlan getPricingPlanSample2() {
        return new PricingPlan().id("id2").name("name2").description("description2").features("features2");
    }

    public static PricingPlan getPricingPlanRandomSampleGenerator() {
        return new PricingPlan()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .features(UUID.randomUUID().toString());
    }
}
