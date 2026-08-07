package net.jojoaddison.domain;

import java.util.UUID;

public class MessageTestSamples {

    public static Message getMessageSample1() {
        return new Message().id("id1").fromAddress("fromAddress1").senderName("senderName1").subject("subject1");
    }

    public static Message getMessageSample2() {
        return new Message().id("id2").fromAddress("fromAddress2").senderName("senderName2").subject("subject2");
    }

    public static Message getMessageRandomSampleGenerator() {
        return new Message()
            .id(UUID.randomUUID().toString())
            .fromAddress(UUID.randomUUID().toString())
            .senderName(UUID.randomUUID().toString())
            .subject(UUID.randomUUID().toString());
    }
}
