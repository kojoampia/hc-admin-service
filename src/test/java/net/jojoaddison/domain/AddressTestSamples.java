package net.jojoaddison.domain;

import java.util.UUID;

public class AddressTestSamples {

    public static Address getAddressSample1() {
        return new Address()
            .id("id1")
            .street("street1")
            .district("district1")
            .town("town1")
            .city("city1")
            .region("region1")
            .code("code1")
            .country("country1");
    }

    public static Address getAddressSample2() {
        return new Address()
            .id("id2")
            .street("street2")
            .district("district2")
            .town("town2")
            .city("city2")
            .region("region2")
            .code("code2")
            .country("country2");
    }

    public static Address getAddressRandomSampleGenerator() {
        return new Address()
            .id(UUID.randomUUID().toString())
            .street(UUID.randomUUID().toString())
            .district(UUID.randomUUID().toString())
            .town(UUID.randomUUID().toString())
            .city(UUID.randomUUID().toString())
            .region(UUID.randomUUID().toString())
            .code(UUID.randomUUID().toString())
            .country(UUID.randomUUID().toString());
    }
}
