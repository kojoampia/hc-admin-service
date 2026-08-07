package net.jojoaddison.domain;

import java.util.UUID;

public class AngelTestSamples {

    public static Angel getAngelSample1() {
        return new Angel().id("id1").name("name1").relationship("relationship1").phone("phone1").email("email1").country("country1");
    }

    public static Angel getAngelSample2() {
        return new Angel().id("id2").name("name2").relationship("relationship2").phone("phone2").email("email2").country("country2");
    }

    public static Angel getAngelRandomSampleGenerator() {
        return new Angel()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .relationship(UUID.randomUUID().toString())
            .phone(UUID.randomUUID().toString())
            .email(UUID.randomUUID().toString())
            .country(UUID.randomUUID().toString());
    }
}
