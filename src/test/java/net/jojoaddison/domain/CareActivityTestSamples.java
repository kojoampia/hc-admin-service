package net.jojoaddison.domain;

import java.util.UUID;

public class CareActivityTestSamples {

    public static CareActivity getCareActivitySample1() {
        return new CareActivity().id("id1").name("name1").description("description1");
    }

    public static CareActivity getCareActivitySample2() {
        return new CareActivity().id("id2").name("name2").description("description2");
    }

    public static CareActivity getCareActivityRandomSampleGenerator() {
        return new CareActivity()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString());
    }
}
