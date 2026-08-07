package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class PlanFeatureTestSamples {

    private static final Random random = new Random();
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static PlanFeature getPlanFeatureSample1() {
        return new PlanFeature().id("id1").label("label1").position(1);
    }

    public static PlanFeature getPlanFeatureSample2() {
        return new PlanFeature().id("id2").label("label2").position(2);
    }

    public static PlanFeature getPlanFeatureRandomSampleGenerator() {
        return new PlanFeature().id(UUID.randomUUID().toString()).label(UUID.randomUUID().toString()).position(intCount.incrementAndGet());
    }
}
