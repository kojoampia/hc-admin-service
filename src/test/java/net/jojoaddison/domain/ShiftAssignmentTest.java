package net.jojoaddison.domain;

import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static net.jojoaddison.domain.RosterWeekTestSamples.*;
import static net.jojoaddison.domain.ShiftAssignmentTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ShiftAssignmentTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ShiftAssignment.class);
        ShiftAssignment shiftAssignment1 = getShiftAssignmentSample1();
        ShiftAssignment shiftAssignment2 = new ShiftAssignment();
        assertThat(shiftAssignment1).isNotEqualTo(shiftAssignment2);

        shiftAssignment2.setId(shiftAssignment1.getId());
        assertThat(shiftAssignment1).isEqualTo(shiftAssignment2);

        shiftAssignment2 = getShiftAssignmentSample2();
        assertThat(shiftAssignment1).isNotEqualTo(shiftAssignment2);
    }

    @Test
    void weekTest() {
        ShiftAssignment shiftAssignment = getShiftAssignmentRandomSampleGenerator();
        RosterWeek rosterWeekBack = getRosterWeekRandomSampleGenerator();

        shiftAssignment.setWeek(rosterWeekBack);
        assertThat(shiftAssignment.getWeek()).isEqualTo(rosterWeekBack);

        shiftAssignment.week(null);
        assertThat(shiftAssignment.getWeek()).isNull();
    }

    @Test
    void professionalTest() {
        ShiftAssignment shiftAssignment = getShiftAssignmentRandomSampleGenerator();
        Professional professionalBack = getProfessionalRandomSampleGenerator();

        shiftAssignment.setProfessional(professionalBack);
        assertThat(shiftAssignment.getProfessional()).isEqualTo(professionalBack);

        shiftAssignment.professional(null);
        assertThat(shiftAssignment.getProfessional()).isNull();
    }
}
