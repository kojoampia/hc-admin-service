package net.jojoaddison.domain;

import java.util.UUID;

public class ProfileTestSamples {

    public static Profile getProfileSample1() {
        return new Profile()
            .id("id1")
            .accountId("accountId1")
            .firstName("firstName1")
            .middleName("middleName1")
            .lastName("lastName1")
            .mobilePhone("mobilePhone1")
            .email("email1")
            .idNumber("idNumber1");
    }

    public static Profile getProfileSample2() {
        return new Profile()
            .id("id2")
            .accountId("accountId2")
            .firstName("firstName2")
            .middleName("middleName2")
            .lastName("lastName2")
            .mobilePhone("mobilePhone2")
            .email("email2")
            .idNumber("idNumber2");
    }

    public static Profile getProfileRandomSampleGenerator() {
        return new Profile()
            .id(UUID.randomUUID().toString())
            .accountId(UUID.randomUUID().toString())
            .firstName(UUID.randomUUID().toString())
            .middleName(UUID.randomUUID().toString())
            .lastName(UUID.randomUUID().toString())
            .mobilePhone(UUID.randomUUID().toString())
            .email(UUID.randomUUID().toString())
            .idNumber(UUID.randomUUID().toString());
    }
}
