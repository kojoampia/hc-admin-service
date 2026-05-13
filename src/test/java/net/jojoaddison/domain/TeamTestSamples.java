package net.jojoaddison.domain;

import java.util.UUID;

public class TeamTestSamples {

    public static Team getTeamSample1() {
        return new Team()
            .id("id1")
            .name("name1")
            .description("description1")
            .members("members1")
            .supervisorId("supervisorId1")
            .organisationId("organisationId1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static Team getTeamSample2() {
        return new Team()
            .id("id2")
            .name("name2")
            .description("description2")
            .members("members2")
            .supervisorId("supervisorId2")
            .organisationId("organisationId2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static Team getTeamRandomSampleGenerator() {
        return new Team()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .members(UUID.randomUUID().toString())
            .supervisorId(UUID.randomUUID().toString())
            .organisationId(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
