package net.jojoaddison.domain;

import java.util.UUID;

public class OrganisationTestSamples {

    public static Organisation getOrganisationSample1() {
        return new Organisation()
            .id("id1")
            .name("name1")
            .description("description1")
            .profile("profile1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Organisation getOrganisationSample2() {
        return new Organisation()
            .id("id2")
            .name("name2")
            .description("description2")
            .profile("profile2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Organisation getOrganisationRandomSampleGenerator() {
        return new Organisation()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .profile(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
