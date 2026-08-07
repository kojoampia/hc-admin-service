package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class HubTestSamples {

    private static final Random random = new Random();
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static Hub getHubSample1() {
        return new Hub().id("id1").name("name1").staffCount(1);
    }

    public static Hub getHubSample2() {
        return new Hub().id("id2").name("name2").staffCount(2);
    }

    public static Hub getHubRandomSampleGenerator() {
        return new Hub().id(UUID.randomUUID().toString()).name(UUID.randomUUID().toString()).staffCount(intCount.incrementAndGet());
    }
}
