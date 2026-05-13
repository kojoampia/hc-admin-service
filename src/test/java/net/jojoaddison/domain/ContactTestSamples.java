package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ContactTestSamples {

    private static final Random random = new Random();
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + (2 * Short.MAX_VALUE));

    public static Contact getContactSample1() {
        return new Contact().id("id1").personId("personId1").email("email1").phoneNumber("phoneNumber1").countryCode(1);
    }

    public static Contact getContactSample2() {
        return new Contact().id("id2").personId("personId2").email("email2").phoneNumber("phoneNumber2").countryCode(2);
    }

    public static Contact getContactRandomSampleGenerator() {
        return new Contact()
            .id(UUID.randomUUID().toString())
            .personId(UUID.randomUUID().toString())
            .email(UUID.randomUUID().toString())
            .phoneNumber(UUID.randomUUID().toString())
            .countryCode(intCount.incrementAndGet());
    }
}
