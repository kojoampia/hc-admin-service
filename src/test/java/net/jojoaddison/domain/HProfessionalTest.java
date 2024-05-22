package net.jojoaddison.domain;

import static net.jojoaddison.domain.HProfessionalTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class HProfessionalTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(HProfessional.class);
        HProfessional hProfessional1 = getHProfessionalSample1();
        HProfessional hProfessional2 = new HProfessional();
        assertThat(hProfessional1).isNotEqualTo(hProfessional2);

        hProfessional2.setId(hProfessional1.getId());
        assertThat(hProfessional1).isEqualTo(hProfessional2);

        hProfessional2 = getHProfessionalSample2();
        assertThat(hProfessional1).isNotEqualTo(hProfessional2);
    }
}
