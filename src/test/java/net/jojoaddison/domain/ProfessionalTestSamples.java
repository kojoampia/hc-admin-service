package net.jojoaddison.domain;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class ProfessionalTestSamples {

    private static final Random random = new Random();
    private static final AtomicInteger intCount = new AtomicInteger(random.nextInt() + 2 * Short.MAX_VALUE);

    public static Professional getProfessionalSample1() {
        return new Professional()
            .id("id1")
            .speciality("speciality1")
            .licenceNumber("licenceNumber1")
            .patientCount(1)
            .caseCount(1)
            .visitCount(1);
    }

    public static Professional getProfessionalSample2() {
        return new Professional()
            .id("id2")
            .speciality("speciality2")
            .licenceNumber("licenceNumber2")
            .patientCount(2)
            .caseCount(2)
            .visitCount(2);
    }

    public static Professional getProfessionalRandomSampleGenerator() {
        return new Professional()
            .id(UUID.randomUUID().toString())
            .speciality(UUID.randomUUID().toString())
            .licenceNumber(UUID.randomUUID().toString())
            .patientCount(intCount.incrementAndGet())
            .caseCount(intCount.incrementAndGet())
            .visitCount(intCount.incrementAndGet());
    }
}
