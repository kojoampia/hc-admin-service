package net.jojoaddison.domain;

import java.util.UUID;

public class MessageTestSamples {

    public static Message getMessageSample1() {
        return new Message().id("id1").content("content1").senderId("senderId1").recipientId("recipientId1");
    }

    public static Message getMessageSample2() {
        return new Message().id("id2").content("content2").senderId("senderId2").recipientId("recipientId2");
    }

    public static Message getMessageRandomSampleGenerator() {
        return new Message()
            .id(UUID.randomUUID().toString())
            .content(UUID.randomUUID().toString())
            .senderId(UUID.randomUUID().toString())
            .recipientId(UUID.randomUUID().toString());
    }
}
