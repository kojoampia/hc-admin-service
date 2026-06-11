package net.jojoaddison.domain;

import static net.jojoaddison.domain.HCProfileTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class HCProfileTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(HCProfile.class);
        HCProfile profile1 = getProfileSample1();
        HCProfile profile2 = new HCProfile();
        assertThat(profile1).isNotEqualTo(profile2);

        profile2.setId(profile1.getId());
        assertThat(profile1).isEqualTo(profile2);

        profile2 = getProfileSample2();
        assertThat(profile1).isNotEqualTo(profile2);
    }
}
