package net.jojoaddison.domain;

import java.util.UUID;

public class DutyRosterTestSamples {

    public static DutyRoster getDutyRosterSample1() {
        return new DutyRoster()
            .id("id1")
            .professionalId("professionalId1")
            .name("name1")
            .description("description1")
            .patientId("patientId1");
    }

    public static DutyRoster getDutyRosterSample2() {
        return new DutyRoster()
            .id("id2")
            .professionalId("professionalId2")
            .name("name2")
            .description("description2")
            .patientId("patientId2");
    }

    public static DutyRoster getDutyRosterRandomSampleGenerator() {
        return new DutyRoster()
            .id(UUID.randomUUID().toString())
            .professionalId(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString());
    }
}
