package net.jojoaddison.domain;

import java.util.UUID;

public class OrganisationTestSamples {

    public static Organisation getOrganisationSample1() {
        return new Organisation()
            .id("id1")
            .name("name1")
            .legalName("legalName1")
            .description("description1")
            .registrationNumber("registrationNumber1")
            .tin("tin1")
            .switchboard("switchboard1")
            .email("email1")
            .deskHours("deskHours1");
    }

    public static Organisation getOrganisationSample2() {
        return new Organisation()
            .id("id2")
            .name("name2")
            .legalName("legalName2")
            .description("description2")
            .registrationNumber("registrationNumber2")
            .tin("tin2")
            .switchboard("switchboard2")
            .email("email2")
            .deskHours("deskHours2");
    }

    public static Organisation getOrganisationRandomSampleGenerator() {
        return new Organisation()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .legalName(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .registrationNumber(UUID.randomUUID().toString())
            .tin(UUID.randomUUID().toString())
            .switchboard(UUID.randomUUID().toString())
            .email(UUID.randomUUID().toString())
            .deskHours(UUID.randomUUID().toString());
    }
}
