package net.jojoaddison.domain;

import java.util.UUID;

public class NotificationTestSamples {

    public static Notification getNotificationSample1() {
        return new Notification()
            .id("id1")
            .content("content1")
            .recipientId("recipientId1")
            .senderId("senderId1")
            .messageType("messageType1");
    }

    public static Notification getNotificationSample2() {
        return new Notification()
            .id("id2")
            .content("content2")
            .recipientId("recipientId2")
            .senderId("senderId2")
            .messageType("messageType2");
    }

    public static Notification getNotificationRandomSampleGenerator() {
        return new Notification()
            .id(UUID.randomUUID().toString())
            .content(UUID.randomUUID().toString())
            .recipientId(UUID.randomUUID().toString())
            .senderId(UUID.randomUUID().toString())
            .messageType(UUID.randomUUID().toString());
    }
}
