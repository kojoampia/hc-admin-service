package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PatientPlanDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(PatientPlanDTO.class);
        PatientPlanDTO patientPlanDTO1 = new PatientPlanDTO();
        patientPlanDTO1.setId("id1");
        PatientPlanDTO patientPlanDTO2 = new PatientPlanDTO();
        assertThat(patientPlanDTO1).isNotEqualTo(patientPlanDTO2);
        patientPlanDTO2.setId(patientPlanDTO1.getId());
        assertThat(patientPlanDTO1).isEqualTo(patientPlanDTO2);
        patientPlanDTO2.setId("id2");
        assertThat(patientPlanDTO1).isNotEqualTo(patientPlanDTO2);
        patientPlanDTO1.setId(null);
        assertThat(patientPlanDTO1).isNotEqualTo(patientPlanDTO2);
    }
}
