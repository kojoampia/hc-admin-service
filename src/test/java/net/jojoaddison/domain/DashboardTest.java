package net.jojoaddison.domain;

import static net.jojoaddison.domain.DashboardTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DashboardTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Dashboard.class);
        Dashboard dashboard1 = getDashboardSample1();
        Dashboard dashboard2 = new Dashboard();
        assertThat(dashboard1).isNotEqualTo(dashboard2);

        dashboard2.setId(dashboard1.getId());
        assertThat(dashboard1).isEqualTo(dashboard2);

        dashboard2 = getDashboardSample2();
        assertThat(dashboard1).isNotEqualTo(dashboard2);
    }
}
