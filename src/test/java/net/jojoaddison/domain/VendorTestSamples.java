package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class VendorTestSamples {

    private static final Random random = new Random();
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static Vendor getVendorSample1() {
        return new Vendor()
            .id("id1")
            .name("name1")
            .category("category1")
            .serviceSummary("serviceSummary1")
            .contactName("contactName1")
            .phone("phone1")
            .email("email1")
            .city("city1")
            .contractNote("contractNote1")
            .orderCount(1);
    }

    public static Vendor getVendorSample2() {
        return new Vendor()
            .id("id2")
            .name("name2")
            .category("category2")
            .serviceSummary("serviceSummary2")
            .contactName("contactName2")
            .phone("phone2")
            .email("email2")
            .city("city2")
            .contractNote("contractNote2")
            .orderCount(2);
    }

    public static Vendor getVendorRandomSampleGenerator() {
        return new Vendor()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .category(UUID.randomUUID().toString())
            .serviceSummary(UUID.randomUUID().toString())
            .contactName(UUID.randomUUID().toString())
            .phone(UUID.randomUUID().toString())
            .email(UUID.randomUUID().toString())
            .city(UUID.randomUUID().toString())
            .contractNote(UUID.randomUUID().toString())
            .orderCount(intCount.incrementAndGet());
    }
}
