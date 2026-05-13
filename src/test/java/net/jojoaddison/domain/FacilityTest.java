package net.jojoaddison.domain;

import static net.jojoaddison.domain.FacilityTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class FacilityTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Facility.class);
        Facility facility1 = getFacilitySample1();
        Facility facility2 = new Facility();
        assertThat(facility1).isNotEqualTo(facility2);

        facility2.setId(facility1.getId());
        assertThat(facility1).isEqualTo(facility2);

        facility2 = getFacilitySample2();
        assertThat(facility1).isNotEqualTo(facility2);
    }
}
