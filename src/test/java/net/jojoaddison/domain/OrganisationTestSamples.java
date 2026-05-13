package net.jojoaddison.domain;

import java.util.UUID;

public class OrganisationTestSamples {

    public static Organisation getOrganisationSample1() {
        return new Organisation()
            .id("id1")
            .name("name1")
            .description("description1")
            .addressId("addressId1")
            .contactId("contactId1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Organisation getOrganisationSample2() {
        return new Organisation()
            .id("id2")
            .name("name2")
            .description("description2")
            .addressId("addressId2")
            .contactId("contactId2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Organisation getOrganisationRandomSampleGenerator() {
        return new Organisation()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .addressId(UUID.randomUUID().toString())
            .contactId(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
