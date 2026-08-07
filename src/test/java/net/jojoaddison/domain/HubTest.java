package net.jojoaddison.domain;

import static net.jojoaddison.domain.AddressTestSamples.*;
import static net.jojoaddison.domain.HubTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class HubTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Hub.class);
        Hub hub1 = getHubSample1();
        Hub hub2 = new Hub();
        assertThat(hub1).isNotEqualTo(hub2);

        hub2.setId(hub1.getId());
        assertThat(hub1).isEqualTo(hub2);

        hub2 = getHubSample2();
        assertThat(hub1).isNotEqualTo(hub2);
    }

    @Test
    void addressTest() {
        Hub hub = getHubRandomSampleGenerator();
        Address addressBack = getAddressRandomSampleGenerator();

        hub.setAddress(addressBack);
        assertThat(hub.getAddress()).isEqualTo(addressBack);

        hub.address(null);
        assertThat(hub.getAddress()).isNull();
    }
}
