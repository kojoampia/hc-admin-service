package net.jojoaddison.domain;

import java.util.UUID;

public class FacilityTestSamples {

    public static Facility getFacilitySample1() {
        return new Facility()
            .id("id1")
            .name("name1")
            .description("description1")
            .addressId("addressId1")
            .contactId("contactId1")
            .photos("photos1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Facility getFacilitySample2() {
        return new Facility()
            .id("id2")
            .name("name2")
            .description("description2")
            .addressId("addressId2")
            .contactId("contactId2")
            .photos("photos2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Facility getFacilityRandomSampleGenerator() {
        return new Facility()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .addressId(UUID.randomUUID().toString())
            .contactId(UUID.randomUUID().toString())
            .photos(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
