package net.jojoaddison.domain;

import java.util.UUID;

public class DashboardTestSamples {

    public static Dashboard getDashboardSample1() {
        return new Dashboard().id("id1").name("name1").description("description1").elements("elements1");
    }

    public static Dashboard getDashboardSample2() {
        return new Dashboard().id("id2").name("name2").description("description2").elements("elements2");
    }

    public static Dashboard getDashboardRandomSampleGenerator() {
        return new Dashboard()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .elements(UUID.randomUUID().toString());
    }
}
