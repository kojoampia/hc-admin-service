package net.jojoaddison.domain;

import java.util.UUID;

public class HProfessionalTestSamples {

    public static HProfessional getHProfessionalSample1() {
        return new HProfessional()
            .id("id1")
            .name("name1")
            .organisation("organisation1")
            .roster("roster1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1")
            .profile("profile1");
    }

    public static HProfessional getHProfessionalSample2() {
        return new HProfessional()
            .id("id2")
            .name("name2")
            .organisation("organisation2")
            .roster("roster2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2")
            .profile("profile2");
    }

    public static HProfessional getHProfessionalRandomSampleGenerator() {
        return new HProfessional()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .organisation(UUID.randomUUID().toString())
            .roster(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString())
            .profile(UUID.randomUUID().toString());
    }
}
