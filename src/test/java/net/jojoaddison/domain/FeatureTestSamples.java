package net.jojoaddison.domain;

import java.util.UUID;

public class FeatureTestSamples {

    public static Feature getFeatureSample1() {
        return new Feature().id("id1").name("name1").description("description1");
    }

    public static Feature getFeatureSample2() {
        return new Feature().id("id2").name("name2").description("description2");
    }

    public static Feature getFeatureRandomSampleGenerator() {
        return new Feature().id(UUID.randomUUID().toString()).name(UUID.randomUUID().toString()).description(UUID.randomUUID().toString());
    }
}
