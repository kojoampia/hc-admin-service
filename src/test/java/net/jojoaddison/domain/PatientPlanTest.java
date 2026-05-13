package net.jojoaddison.domain;

import static net.jojoaddison.domain.PatientPlanTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PatientPlanTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(PatientPlan.class);
        PatientPlan patientPlan1 = getPatientPlanSample1();
        PatientPlan patientPlan2 = new PatientPlan();
        assertThat(patientPlan1).isNotEqualTo(patientPlan2);

        patientPlan2.setId(patientPlan1.getId());
        assertThat(patientPlan1).isEqualTo(patientPlan2);

        patientPlan2 = getPatientPlanSample2();
        assertThat(patientPlan1).isNotEqualTo(patientPlan2);
    }
}
