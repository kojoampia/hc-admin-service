package net.jojoaddison.domain;

import static net.jojoaddison.domain.RosterWeekTestSamples.*;
import static net.jojoaddison.domain.ShiftAssignmentTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
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

    @Test
    void assignmentTest() {
        RosterWeek rosterWeek = getRosterWeekRandomSampleGenerator();
        ShiftAssignment shiftAssignmentBack = getShiftAssignmentRandomSampleGenerator();

        rosterWeek.addAssignment(shiftAssignmentBack);
        assertThat(rosterWeek.getAssignments()).containsOnly(shiftAssignmentBack);
        assertThat(shiftAssignmentBack.getWeek()).isEqualTo(rosterWeek);

        rosterWeek.removeAssignment(shiftAssignmentBack);
        assertThat(rosterWeek.getAssignments()).doesNotContain(shiftAssignmentBack);
        assertThat(shiftAssignmentBack.getWeek()).isNull();

        rosterWeek.assignments(new HashSet<>(Set.of(shiftAssignmentBack)));
        assertThat(rosterWeek.getAssignments()).containsOnly(shiftAssignmentBack);
        assertThat(shiftAssignmentBack.getWeek()).isEqualTo(rosterWeek);

        rosterWeek.setAssignments(new HashSet<>());
        assertThat(rosterWeek.getAssignments()).doesNotContain(shiftAssignmentBack);
        assertThat(shiftAssignmentBack.getWeek()).isNull();
    }
}
