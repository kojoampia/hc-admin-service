package net.jojoaddison.domain;

import java.util.UUID;

public class DocumentTestSamples {

    public static Document getDocumentSample1() {
        return new Document().id("id1").name("name1").description("description1").url("url1");
    }

    public static Document getDocumentSample2() {
        return new Document().id("id2").name("name2").description("description2").url("url2");
    }

    public static Document getDocumentRandomSampleGenerator() {
        return new Document()
            .id(UUID.randomUUID().toString())
            .name(UUID.randomUUID().toString())
            .description(UUID.randomUUID().toString())
            .url(UUID.randomUUID().toString());
    }
}
