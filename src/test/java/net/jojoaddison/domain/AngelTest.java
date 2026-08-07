package net.jojoaddison.domain;

import static net.jojoaddison.domain.AngelTestSamples.*;
import static net.jojoaddison.domain.PatientTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class AngelTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Angel.class);
        Angel angel1 = getAngelSample1();
        Angel angel2 = new Angel();
        assertThat(angel1).isNotEqualTo(angel2);

        angel2.setId(angel1.getId());
        assertThat(angel1).isEqualTo(angel2);

        angel2 = getAngelSample2();
        assertThat(angel1).isNotEqualTo(angel2);
    }

    @Test
    void patientTest() {
        Angel angel = getAngelRandomSampleGenerator();
        Patient patientBack = getPatientRandomSampleGenerator();

        angel.setPatient(patientBack);
        assertThat(angel.getPatient()).isEqualTo(patientBack);
        assertThat(patientBack.getAngel()).isEqualTo(angel);

        angel.patient(null);
        assertThat(angel.getPatient()).isNull();
        assertThat(patientBack.getAngel()).isNull();
    }
}
