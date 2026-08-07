package net.jojoaddison.domain;

import static net.jojoaddison.domain.AddressTestSamples.*;
import static net.jojoaddison.domain.PatientTestSamples.*;
import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static net.jojoaddison.domain.ProfileTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ProfileTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Profile.class);
        Profile profile1 = getProfileSample1();
        Profile profile2 = new Profile();
        assertThat(profile1).isNotEqualTo(profile2);

        profile2.setId(profile1.getId());
        assertThat(profile1).isEqualTo(profile2);

        profile2 = getProfileSample2();
        assertThat(profile1).isNotEqualTo(profile2);
    }

    @Test
    void addressTest() {
        Profile profile = getProfileRandomSampleGenerator();
        Address addressBack = getAddressRandomSampleGenerator();

        profile.setAddress(addressBack);
        assertThat(profile.getAddress()).isEqualTo(addressBack);

        profile.address(null);
        assertThat(profile.getAddress()).isNull();
    }

    @Test
    void patientTest() {
        Profile profile = getProfileRandomSampleGenerator();
        Patient patientBack = getPatientRandomSampleGenerator();

        profile.setPatient(patientBack);
        assertThat(profile.getPatient()).isEqualTo(patientBack);
        assertThat(patientBack.getProfile()).isEqualTo(profile);

        profile.patient(null);
        assertThat(profile.getPatient()).isNull();
        assertThat(patientBack.getProfile()).isNull();
    }

    @Test
    void professionalTest() {
        Profile profile = getProfileRandomSampleGenerator();
        Professional professionalBack = getProfessionalRandomSampleGenerator();

        profile.setProfessional(professionalBack);
        assertThat(profile.getProfessional()).isEqualTo(professionalBack);
        assertThat(professionalBack.getProfile()).isEqualTo(profile);

        profile.professional(null);
        assertThat(profile.getProfessional()).isNull();
        assertThat(professionalBack.getProfile()).isNull();
    }
}
