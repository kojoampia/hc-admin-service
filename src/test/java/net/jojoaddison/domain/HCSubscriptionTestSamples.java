package net.jojoaddison.domain;

import java.util.UUID;

public class HCSubscriptionTestSamples {

    public static HCSubscription getHCSubscriptionSample1() {
        return new HCSubscription()
            .id("id1")
            .serviceId("serviceId1")
            .patientId("patientId1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static HCSubscription getHCSubscriptionSample2() {
        return new HCSubscription()
            .id("id2")
            .serviceId("serviceId2")
            .patientId("patientId2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static HCSubscription getHCSubscriptionRandomSampleGenerator() {
        return new HCSubscription()
            .id(UUID.randomUUID().toString())
            .serviceId(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
