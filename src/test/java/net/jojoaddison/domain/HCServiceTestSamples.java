package net.jojoaddison.domain;

import java.util.UUID;

public class HCServiceTestSamples {

    public static HCService getHCServiceSample1() {
        return new HCService()
            .id("id1")
            .name("name1")
            .description("description1")
            .serviceItems("serviceItems1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static HCService getHCServiceSample2() {
        return new HCService()
            .id("id2")
            .name("name2")
            .description("description2")
            .serviceItems("serviceItems2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static HCService getHCServiceRandomSampleGenerator() {
        return new HCService()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .serviceItems(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
