package net.jojoaddison.domain;

import java.util.UUID;

public class DocumentItemTestSamples {

    public static DocumentItem getDocumentItemSample1() {
        return new DocumentItem()
            .id("id1")
            .name("name1")
            .description("description1")
            .url("url1")
            .createdBy("createdBy1")
            .modifiedBy("modifiedBy1");
    }

    public static DocumentItem getDocumentItemSample2() {
        return new DocumentItem()
            .id("id2")
            .name("name2")
            .description("description2")
            .url("url2")
            .createdBy("createdBy2")
            .modifiedBy("modifiedBy2");
    }

    public static DocumentItem getDocumentItemRandomSampleGenerator() {
        return new DocumentItem()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .url(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString())
            .modifiedBy(UUID.randomUUID().toString());
    }
}
