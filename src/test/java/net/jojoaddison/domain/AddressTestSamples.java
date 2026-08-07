package net.jojoaddison.domain;

import java.util.UUID;

public class AddressTestSamples {

    public static Address getAddressSample1() {
        return new Address()
            .id("id1")
            .digitalAddress("digitalAddress1")
            .streetAddress("streetAddress1")
            .townDistrict("townDistrict1")
            .cityState("cityState1")
            .region("region1")
            .country("country1");
    }

    public static Address getAddressSample2() {
        return new Address()
            .id("id2")
            .digitalAddress("digitalAddress2")
            .streetAddress("streetAddress2")
            .townDistrict("townDistrict2")
            .cityState("cityState2")
            .region("region2")
            .country("country2");
    }

    public static Address getAddressRandomSampleGenerator() {
        return new Address()
            .id(UUID.randomUUID().toString())
            .digitalAddress(UUID.randomUUID().toString())
            .streetAddress(UUID.randomUUID().toString())
            .townDistrict(UUID.randomUUID().toString())
            .cityState(UUID.randomUUID().toString())
            .region(UUID.randomUUID().toString())
            .country(UUID.randomUUID().toString());
    }
}
