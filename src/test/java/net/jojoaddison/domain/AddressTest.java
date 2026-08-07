package net.jojoaddison.domain;

import static net.jojoaddison.domain.AddressTestSamples.*;
import static net.jojoaddison.domain.HubTestSamples.*;
import static net.jojoaddison.domain.OrganisationTestSamples.*;
import static net.jojoaddison.domain.ProfileTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class AddressTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Address.class);
        Address address1 = getAddressSample1();
        Address address2 = new Address();
        assertThat(address1).isNotEqualTo(address2);

        address2.setId(address1.getId());
        assertThat(address1).isEqualTo(address2);

        address2 = getAddressSample2();
        assertThat(address1).isNotEqualTo(address2);
    }

    @Test
    void profileTest() {
        Address address = getAddressRandomSampleGenerator();
        Profile profileBack = getProfileRandomSampleGenerator();

        address.setProfile(profileBack);
        assertThat(address.getProfile()).isEqualTo(profileBack);
        assertThat(profileBack.getAddress()).isEqualTo(address);

        address.profile(null);
        assertThat(address.getProfile()).isNull();
        assertThat(profileBack.getAddress()).isNull();
    }

    @Test
    void hubTest() {
        Address address = getAddressRandomSampleGenerator();
        Hub hubBack = getHubRandomSampleGenerator();

        address.setHub(hubBack);
        assertThat(address.getHub()).isEqualTo(hubBack);
        assertThat(hubBack.getAddress()).isEqualTo(address);

        address.hub(null);
        assertThat(address.getHub()).isNull();
        assertThat(hubBack.getAddress()).isNull();
    }

    @Test
    void organisationTest() {
        Address address = getAddressRandomSampleGenerator();
        Organisation organisationBack = getOrganisationRandomSampleGenerator();

        address.setOrganisation(organisationBack);
        assertThat(address.getOrganisation()).isEqualTo(organisationBack);
        assertThat(organisationBack.getAddress()).isEqualTo(address);

        address.organisation(null);
        assertThat(address.getOrganisation()).isNull();
        assertThat(organisationBack.getAddress()).isNull();
    }
}
