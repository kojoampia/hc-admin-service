package net.jojoaddison.domain;

import static net.jojoaddison.domain.HCServiceTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class HCServiceTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(HCService.class);
        HCService hCService1 = getHCServiceSample1();
        HCService hCService2 = new HCService();
        assertThat(hCService1).isNotEqualTo(hCService2);

        hCService2.setId(hCService1.getId());
        assertThat(hCService1).isEqualTo(hCService2);

        hCService2 = getHCServiceSample2();
        assertThat(hCService1).isNotEqualTo(hCService2);
    }
}
