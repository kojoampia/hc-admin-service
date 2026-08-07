package net.jojoaddison.domain;

import java.util.UUID;

public class ServiceActivityTestSamples {

    public static ServiceActivity getServiceActivitySample1() {
        return new ServiceActivity().id("id1").name("name1").unit("unit1").duration("duration1");
    }

    public static ServiceActivity getServiceActivitySample2() {
        return new ServiceActivity().id("id2").name("name2").unit("unit2").duration("duration2");
    }

    public static ServiceActivity getServiceActivityRandomSampleGenerator() {
        return new ServiceActivity()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .unit(UUID.randomUUID().toString())
            .duration(UUID.randomUUID().toString());
    }
}
