package net.jojoaddison.domain;

import static net.jojoaddison.domain.DutyRosterTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DutyRosterTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DutyRoster.class);
        DutyRoster dutyRoster1 = getDutyRosterSample1();
        DutyRoster dutyRoster2 = new DutyRoster();
        assertThat(dutyRoster1).isNotEqualTo(dutyRoster2);

        dutyRoster2.setId(dutyRoster1.getId());
        assertThat(dutyRoster1).isEqualTo(dutyRoster2);

        dutyRoster2 = getDutyRosterSample2();
        assertThat(dutyRoster1).isNotEqualTo(dutyRoster2);
    }
}
