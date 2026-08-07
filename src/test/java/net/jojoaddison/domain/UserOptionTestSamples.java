package net.jojoaddison.domain;

import java.util.UUID;

public class UserOptionTestSamples {

    public static UserOption getUserOptionSample1() {
        return new UserOption().id("id1").category("category1").userRef("userRef1").metadata("metadata1");
    }

    public static UserOption getUserOptionSample2() {
        return new UserOption().id("id2").category("category2").userRef("userRef2").metadata("metadata2");
    }

    public static UserOption getUserOptionRandomSampleGenerator() {
        return new UserOption()
            .id(UUID.randomUUID().toString())
            .category(UUID.randomUUID().toString())
            .userRef(UUID.randomUUID().toString())
            .metadata(UUID.randomUUID().toString());
    }
}
