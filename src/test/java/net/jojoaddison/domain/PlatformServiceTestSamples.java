package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class PlatformServiceTestSamples {

    private static final Random random = new Random();
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static PlatformService getPlatformServiceSample1() {
        return new PlatformService().id("id1").name("name1").host("host1").port(1).plane("plane1").responseMs(1);
    }

    public static PlatformService getPlatformServiceSample2() {
        return new PlatformService().id("id2").name("name2").host("host2").port(2).plane("plane2").responseMs(2);
    }

    public static PlatformService getPlatformServiceRandomSampleGenerator() {
        return new PlatformService()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .host(UUID.randomUUID().toString())
            .port(intCount.incrementAndGet())
            .plane(UUID.randomUUID().toString())
            .responseMs(intCount.incrementAndGet());
    }
}
