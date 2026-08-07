package net.jojoaddison.domain;

import java.util.UUID;

public class TaskTestSamples {

    public static Task getTaskSample1() {
        return new Task().id("id1").title("title1").tag("tag1");
    }

    public static Task getTaskSample2() {
        return new Task().id("id2").title("title2").tag("tag2");
    }

    public static Task getTaskRandomSampleGenerator() {
        return new Task().id(UUID.randomUUID().toString()).title(UUID.randomUUID().toString()).tag(UUID.randomUUID().toString());
    }
}
