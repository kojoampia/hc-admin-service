package net.jojoaddison.domain;

import static net.jojoaddison.domain.AngelTestSamples.*;
import static net.jojoaddison.domain.CareActivityTestSamples.*;
import static net.jojoaddison.domain.DocumentTestSamples.*;
import static net.jojoaddison.domain.HubTestSamples.*;
import static net.jojoaddison.domain.PatientTestSamples.*;
import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static net.jojoaddison.domain.ProfileTestSamples.*;
import static net.jojoaddison.domain.ServicePlanTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PatientTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Patient.class);
        Patient patient1 = getPatientSample1();
        Patient patient2 = new Patient();
        assertThat(patient1).isNotEqualTo(patient2);

        patient2.setId(patient1.getId());
        assertThat(patient1).isEqualTo(patient2);

        patient2 = getPatientSample2();
        assertThat(patient1).isNotEqualTo(patient2);
    }

    @Test
    void profileTest() {
        Patient patient = getPatientRandomSampleGenerator();
        Profile profileBack = getProfileRandomSampleGenerator();

        patient.setProfile(profileBack);
        assertThat(patient.getProfile()).isEqualTo(profileBack);

        patient.profile(null);
        assertThat(patient.getProfile()).isNull();
    }

    @Test
    void angelTest() {
        Patient patient = getPatientRandomSampleGenerator();
        Angel angelBack = getAngelRandomSampleGenerator();

        patient.setAngel(angelBack);
        assertThat(patient.getAngel()).isEqualTo(angelBack);

        patient.angel(null);
        assertThat(patient.getAngel()).isNull();
    }

    @Test
    void documentTest() {
        Patient patient = getPatientRandomSampleGenerator();
        Document documentBack = getDocumentRandomSampleGenerator();

        patient.addDocument(documentBack);
        assertThat(patient.getDocuments()).containsOnly(documentBack);
        assertThat(documentBack.getPatient()).isEqualTo(patient);

        patient.removeDocument(documentBack);
        assertThat(patient.getDocuments()).doesNotContain(documentBack);
        assertThat(documentBack.getPatient()).isNull();

        patient.documents(new HashSet<>(Set.of(documentBack)));
        assertThat(patient.getDocuments()).containsOnly(documentBack);
        assertThat(documentBack.getPatient()).isEqualTo(patient);

        patient.setDocuments(new HashSet<>());
        assertThat(patient.getDocuments()).doesNotContain(documentBack);
        assertThat(documentBack.getPatient()).isNull();
    }

    @Test
    void careActivityTest() {
        Patient patient = getPatientRandomSampleGenerator();
        CareActivity careActivityBack = getCareActivityRandomSampleGenerator();

        patient.addCareActivity(careActivityBack);
        assertThat(patient.getCareActivities()).containsOnly(careActivityBack);
        assertThat(careActivityBack.getPatient()).isEqualTo(patient);

        patient.removeCareActivity(careActivityBack);
        assertThat(patient.getCareActivities()).doesNotContain(careActivityBack);
        assertThat(careActivityBack.getPatient()).isNull();

        patient.careActivities(new HashSet<>(Set.of(careActivityBack)));
        assertThat(patient.getCareActivities()).containsOnly(careActivityBack);
        assertThat(careActivityBack.getPatient()).isEqualTo(patient);

        patient.setCareActivities(new HashSet<>());
        assertThat(patient.getCareActivities()).doesNotContain(careActivityBack);
        assertThat(careActivityBack.getPatient()).isNull();
    }

    @Test
    void planTest() {
        Patient patient = getPatientRandomSampleGenerator();
        ServicePlan servicePlanBack = getServicePlanRandomSampleGenerator();

        patient.setPlan(servicePlanBack);
        assertThat(patient.getPlan()).isEqualTo(servicePlanBack);

        patient.plan(null);
        assertThat(patient.getPlan()).isNull();
    }

    @Test
    void clinicalLeadTest() {
        Patient patient = getPatientRandomSampleGenerator();
        Professional professionalBack = getProfessionalRandomSampleGenerator();

        patient.setClinicalLead(professionalBack);
        assertThat(patient.getClinicalLead()).isEqualTo(professionalBack);

        patient.clinicalLead(null);
        assertThat(patient.getClinicalLead()).isNull();
    }

    @Test
    void hubTest() {
        Patient patient = getPatientRandomSampleGenerator();
        Hub hubBack = getHubRandomSampleGenerator();

        patient.setHub(hubBack);
        assertThat(patient.getHub()).isEqualTo(hubBack);

        patient.hub(null);
        assertThat(patient.getHub()).isNull();
    }
}
