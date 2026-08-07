package net.jojoaddison.domain;

import static net.jojoaddison.domain.CareActivityTestSamples.*;
import static net.jojoaddison.domain.PatientTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class CareActivityTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(CareActivity.class);
        CareActivity careActivity1 = getCareActivitySample1();
        CareActivity careActivity2 = new CareActivity();
        assertThat(careActivity1).isNotEqualTo(careActivity2);

        careActivity2.setId(careActivity1.getId());
        assertThat(careActivity1).isEqualTo(careActivity2);

        careActivity2 = getCareActivitySample2();
        assertThat(careActivity1).isNotEqualTo(careActivity2);
    }

    @Test
    void patientTest() {
        CareActivity careActivity = getCareActivityRandomSampleGenerator();
        Patient patientBack = getPatientRandomSampleGenerator();

        careActivity.setPatient(patientBack);
        assertThat(careActivity.getPatient()).isEqualTo(patientBack);

        careActivity.patient(null);
        assertThat(careActivity.getPatient()).isNull();
    }
}
