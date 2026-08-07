package net.jojoaddison.domain;

import static net.jojoaddison.domain.DocumentTestSamples.*;
import static net.jojoaddison.domain.PatientTestSamples.*;
import static net.jojoaddison.domain.VendorTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DocumentTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Document.class);
        Document document1 = getDocumentSample1();
        Document document2 = new Document();
        assertThat(document1).isNotEqualTo(document2);

        document2.setId(document1.getId());
        assertThat(document1).isEqualTo(document2);

        document2 = getDocumentSample2();
        assertThat(document1).isNotEqualTo(document2);
    }

    @Test
    void patientTest() {
        Document document = getDocumentRandomSampleGenerator();
        Patient patientBack = getPatientRandomSampleGenerator();

        document.setPatient(patientBack);
        assertThat(document.getPatient()).isEqualTo(patientBack);

        document.patient(null);
        assertThat(document.getPatient()).isNull();
    }

    @Test
    void vendorTest() {
        Document document = getDocumentRandomSampleGenerator();
        Vendor vendorBack = getVendorRandomSampleGenerator();

        document.setVendor(vendorBack);
        assertThat(document.getVendor()).isEqualTo(vendorBack);

        document.vendor(null);
        assertThat(document.getVendor()).isNull();
    }
}
