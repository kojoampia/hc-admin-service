package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ServicePlanTestSamples {

    private static final Random random = new Random();
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static ServicePlan getServicePlanSample1() {
        return new ServicePlan()
            .id("id1")
            .name("name1")
            .tierLabel("tierLabel1")
            .currency("currency1")
            .summary("summary1")
            .subscriberCount(1);
    }

    public static ServicePlan getServicePlanSample2() {
        return new ServicePlan()
            .id("id2")
            .name("name2")
            .tierLabel("tierLabel2")
            .currency("currency2")
            .summary("summary2")
            .subscriberCount(2);
    }

    public static ServicePlan getServicePlanRandomSampleGenerator() {
        return new ServicePlan()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .tierLabel(UUID.randomUUID().toString())
            .currency(UUID.randomUUID().toString())
            .summary(UUID.randomUUID().toString())
            .subscriberCount(intCount.incrementAndGet());
    }
}
