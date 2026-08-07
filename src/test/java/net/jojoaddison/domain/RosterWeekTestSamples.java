package net.jojoaddison.domain;

import java.util.UUID;

public class RosterWeekTestSamples {

    public static RosterWeek getRosterWeekSample1() {
        return new RosterWeek().id("id1").label("label1");
    }

    public static RosterWeek getRosterWeekSample2() {
        return new RosterWeek().id("id2").label("label2");
    }

    public static RosterWeek getRosterWeekRandomSampleGenerator() {
        return new RosterWeek().id(UUID.randomUUID().toString()).label(UUID.randomUUID().toString());
    }
}
