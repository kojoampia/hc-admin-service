package net.jojoaddison.domain;

import java.util.UUID;

public class PersonTestSamples {

    public static Person getPersonSample1() {
        return new Person()
            .id("id1")
            .firstName("firstName1")
            .middleName("middleName1")
            .lastName("lastName1")
            .maritalStatus("maritalStatus1")
            .nationality("nationality1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Person getPersonSample2() {
        return new Person()
            .id("id2")
            .firstName("firstName2")
            .middleName("middleName2")
            .lastName("lastName2")
            .maritalStatus("maritalStatus2")
            .nationality("nationality2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Person getPersonRandomSampleGenerator() {
        return new Person()
            .id(UUID.randomUUID().toString())
            .firstName(UUID.randomUUID().toString())
            .middleName(UUID.randomUUID().toString())
            .lastName(UUID.randomUUID().toString())
            .maritalStatus(UUID.randomUUID().toString())
            .nationality(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
