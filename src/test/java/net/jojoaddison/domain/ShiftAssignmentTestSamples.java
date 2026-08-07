package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ShiftAssignmentTestSamples {

    private static final Random random = new Random();
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static ShiftAssignment getShiftAssignmentSample1() {
        return new ShiftAssignment().id("id1").dayIndex(1);
    }

    public static ShiftAssignment getShiftAssignmentSample2() {
        return new ShiftAssignment().id("id2").dayIndex(2);
    }

    public static ShiftAssignment getShiftAssignmentRandomSampleGenerator() {
        return new ShiftAssignment().id(UUID.randomUUID().toString()).dayIndex(intCount.incrementAndGet());
    }
}
