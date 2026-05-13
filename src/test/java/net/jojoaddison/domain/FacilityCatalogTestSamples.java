package net.jojoaddison.domain;

import java.util.UUID;

public class FacilityCatalogTestSamples {

    public static FacilityCatalog getFacilityCatalogSample1() {
        return new FacilityCatalog().id("id1").name("name1").description("description1").facilities("facilities1");
    }

    public static FacilityCatalog getFacilityCatalogSample2() {
        return new FacilityCatalog().id("id2").name("name2").description("description2").facilities("facilities2");
    }

    public static FacilityCatalog getFacilityCatalogRandomSampleGenerator() {
        return new FacilityCatalog()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .facilities(UUID.randomUUID().toString());
    }
}
