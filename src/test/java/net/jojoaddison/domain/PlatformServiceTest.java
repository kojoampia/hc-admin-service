package net.jojoaddison.domain;

import static net.jojoaddison.domain.PlatformServiceTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PlatformServiceTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(PlatformService.class);
        PlatformService platformService1 = getPlatformServiceSample1();
        PlatformService platformService2 = new PlatformService();
        assertThat(platformService1).isNotEqualTo(platformService2);

        platformService2.setId(platformService1.getId());
        assertThat(platformService1).isEqualTo(platformService2);

        platformService2 = getPlatformServiceSample2();
        assertThat(platformService1).isNotEqualTo(platformService2);
    }
}
