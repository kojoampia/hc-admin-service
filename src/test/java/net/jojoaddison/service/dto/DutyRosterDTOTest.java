package net.jojoaddison.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DutyRosterDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(DutyRosterDTO.class);
        DutyRosterDTO dutyRosterDTO1 = new DutyRosterDTO();
        dutyRosterDTO1.setId("id1");
        DutyRosterDTO dutyRosterDTO2 = new DutyRosterDTO();
        assertThat(dutyRosterDTO1).isNotEqualTo(dutyRosterDTO2);
        dutyRosterDTO2.setId(dutyRosterDTO1.getId());
        assertThat(dutyRosterDTO1).isEqualTo(dutyRosterDTO2);
        dutyRosterDTO2.setId("id2");
        assertThat(dutyRosterDTO1).isNotEqualTo(dutyRosterDTO2);
        dutyRosterDTO1.setId(null);
        assertThat(dutyRosterDTO1).isNotEqualTo(dutyRosterDTO2);
    }
}
