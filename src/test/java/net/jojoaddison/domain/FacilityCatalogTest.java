package net.jojoaddison.domain;

import static net.jojoaddison.domain.FacilityCatalogTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class FacilityCatalogTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(FacilityCatalog.class);
        FacilityCatalog facilityCatalog1 = getFacilityCatalogSample1();
        FacilityCatalog facilityCatalog2 = new FacilityCatalog();
        assertThat(facilityCatalog1).isNotEqualTo(facilityCatalog2);

        facilityCatalog2.setId(facilityCatalog1.getId());
        assertThat(facilityCatalog1).isEqualTo(facilityCatalog2);

        facilityCatalog2 = getFacilityCatalogSample2();
        assertThat(facilityCatalog1).isNotEqualTo(facilityCatalog2);
    }
}
