package net.jojoaddison.domain;

import static net.jojoaddison.domain.DocumentTestSamples.*;
import static net.jojoaddison.domain.VendorTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class VendorTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Vendor.class);
        Vendor vendor1 = getVendorSample1();
        Vendor vendor2 = new Vendor();
        assertThat(vendor1).isNotEqualTo(vendor2);

        vendor2.setId(vendor1.getId());
        assertThat(vendor1).isEqualTo(vendor2);

        vendor2 = getVendorSample2();
        assertThat(vendor1).isNotEqualTo(vendor2);
    }

    @Test
    void documentTest() {
        Vendor vendor = getVendorRandomSampleGenerator();
        Document documentBack = getDocumentRandomSampleGenerator();

        vendor.addDocument(documentBack);
        assertThat(vendor.getDocuments()).containsOnly(documentBack);
        assertThat(documentBack.getVendor()).isEqualTo(vendor);

        vendor.removeDocument(documentBack);
        assertThat(vendor.getDocuments()).doesNotContain(documentBack);
        assertThat(documentBack.getVendor()).isNull();

        vendor.documents(new HashSet<>(Set.of(documentBack)));
        assertThat(vendor.getDocuments()).containsOnly(documentBack);
        assertThat(documentBack.getVendor()).isEqualTo(vendor);

        vendor.setDocuments(new HashSet<>());
        assertThat(vendor.getDocuments()).doesNotContain(documentBack);
        assertThat(documentBack.getVendor()).isNull();
    }
}
