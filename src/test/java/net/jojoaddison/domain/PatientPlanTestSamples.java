package net.jojoaddison.domain;

import java.util.UUID;

public class PatientPlanTestSamples {

    public static PatientPlan getPatientPlanSample1() {
        return new PatientPlan().id("id1").planId("planId1").patientId("patientId1").createdBy("createdBy1");
    }

    public static PatientPlan getPatientPlanSample2() {
        return new PatientPlan().id("id2").planId("planId2").patientId("patientId2").createdBy("createdBy2");
    }

    public static PatientPlan getPatientPlanRandomSampleGenerator() {
        return new PatientPlan()
            .id(UUID.randomUUID().toString())
            .planId(UUID.randomUUID().toString())
            .patientId(UUID.randomUUID().toString())
            .createdBy(UUID.randomUUID().toString());
    }
}
