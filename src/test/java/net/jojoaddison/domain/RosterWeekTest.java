package net.jojoaddison.domain;

import static net.jojoaddison.domain.RosterWeekTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class RosterWeekTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(RosterWeek.class);
        RosterWeek rosterWeek1 = getRosterWeekSample1();
        RosterWeek rosterWeek2 = new RosterWeek();
        assertThat(rosterWeek1).isNotEqualTo(rosterWeek2);

        rosterWeek2.setId(rosterWeek1.getId());
        assertThat(rosterWeek1).isEqualTo(rosterWeek2);

        rosterWeek2 = getRosterWeekSample2();
        assertThat(rosterWeek1).isNotEqualTo(rosterWeek2);
    }
}
