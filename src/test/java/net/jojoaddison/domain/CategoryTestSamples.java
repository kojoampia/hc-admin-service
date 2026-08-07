package net.jojoaddison.domain;

import java.util.UUID;

public class CategoryTestSamples {

    public static Category getCategorySample1() {
        return new Category().id("id1").name("name1").description("description1").iconKey("iconKey1");
    }

    public static Category getCategorySample2() {
        return new Category().id("id2").name("name2").description("description2").iconKey("iconKey2");
    }

    public static Category getCategoryRandomSampleGenerator() {
        return new Category()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .iconKey(UUID.randomUUID().toString());
    }
}
