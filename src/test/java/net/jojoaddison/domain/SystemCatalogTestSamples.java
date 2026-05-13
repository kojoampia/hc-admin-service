package net.jojoaddison.domain;

import java.util.UUID;

public class SystemCatalogTestSamples {

    public static SystemCatalog getSystemCatalogSample1() {
        return new SystemCatalog().id("id1").name("name1").description("description1").createdBy("createdBy1").modifiedBy("modifiedBy1");
    }

    public static SystemCatalog getSystemCatalogSample2() {
        return new SystemCatalog().id("id2").name("name2").description("description2").createdBy("createdBy2").modifiedBy("modifiedBy2");
    }

    public static SystemCatalog getSystemCatalogRandomSampleGenerator() {
        return new SystemCatalog()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
