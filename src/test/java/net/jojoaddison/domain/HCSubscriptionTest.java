package net.jojoaddison.domain;

import static net.jojoaddison.domain.HCSubscriptionTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class HCSubscriptionTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(HCSubscription.class);
        HCSubscription hCSubscription1 = getHCSubscriptionSample1();
        HCSubscription hCSubscription2 = new HCSubscription();
        assertThat(hCSubscription1).isNotEqualTo(hCSubscription2);

        hCSubscription2.setId(hCSubscription1.getId());
        assertThat(hCSubscription1).isEqualTo(hCSubscription2);

        hCSubscription2 = getHCSubscriptionSample2();
        assertThat(hCSubscription1).isNotEqualTo(hCSubscription2);
    }
}
