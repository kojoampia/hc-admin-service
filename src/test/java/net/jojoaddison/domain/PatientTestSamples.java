package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class PatientTestSamples {

    private static final Random random = new Random();
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static Patient getPatientSample1() {
        return new Patient().id("id1").caseCount(1);
    }

    public static Patient getPatientSample2() {
        return new Patient().id("id2").caseCount(2);
    }

    public static Patient getPatientRandomSampleGenerator() {
        return new Patient().id(UUID.randomUUID().toString()).caseCount(intCount.incrementAndGet());
    }
}
