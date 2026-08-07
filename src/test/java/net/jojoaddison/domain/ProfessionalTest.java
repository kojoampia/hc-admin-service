package net.jojoaddison.domain;

import static net.jojoaddison.domain.HubTestSamples.*;
import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static net.jojoaddison.domain.ProfileTestSamples.*;
import static net.jojoaddison.domain.ShiftAssignmentTestSamples.*;
import static net.jojoaddison.domain.TeamTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ProfessionalTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Professional.class);
        Professional professional1 = getProfessionalSample1();
        Professional professional2 = new Professional();
        assertThat(professional1).isNotEqualTo(professional2);

        professional2.setId(professional1.getId());
        assertThat(professional1).isEqualTo(professional2);

        professional2 = getProfessionalSample2();
        assertThat(professional1).isNotEqualTo(professional2);
    }

    @Test
    void profileTest() {
        Professional professional = getProfessionalRandomSampleGenerator();
        Profile profileBack = getProfileRandomSampleGenerator();

        professional.setProfile(profileBack);
        assertThat(professional.getProfile()).isEqualTo(profileBack);

        professional.profile(null);
        assertThat(professional.getProfile()).isNull();
    }

    @Test
    void assignmentTest() {
        Professional professional = getProfessionalRandomSampleGenerator();
        ShiftAssignment shiftAssignmentBack = getShiftAssignmentRandomSampleGenerator();

        professional.addAssignment(shiftAssignmentBack);
        assertThat(professional.getAssignments()).containsOnly(shiftAssignmentBack);
        assertThat(shiftAssignmentBack.getProfessional()).isEqualTo(professional);

        professional.removeAssignment(shiftAssignmentBack);
        assertThat(professional.getAssignments()).doesNotContain(shiftAssignmentBack);
        assertThat(shiftAssignmentBack.getProfessional()).isNull();

        professional.assignments(new HashSet<>(Set.of(shiftAssignmentBack)));
        assertThat(professional.getAssignments()).containsOnly(shiftAssignmentBack);
        assertThat(shiftAssignmentBack.getProfessional()).isEqualTo(professional);

        professional.setAssignments(new HashSet<>());
        assertThat(professional.getAssignments()).doesNotContain(shiftAssignmentBack);
        assertThat(shiftAssignmentBack.getProfessional()).isNull();
    }

    @Test
    void teamTest() {
        Professional professional = getProfessionalRandomSampleGenerator();
        Team teamBack = getTeamRandomSampleGenerator();

        professional.setTeam(teamBack);
        assertThat(professional.getTeam()).isEqualTo(teamBack);

        professional.team(null);
        assertThat(professional.getTeam()).isNull();
    }

    @Test
    void hubTest() {
        Professional professional = getProfessionalRandomSampleGenerator();
        Hub hubBack = getHubRandomSampleGenerator();

        professional.setHub(hubBack);
        assertThat(professional.getHub()).isEqualTo(hubBack);

        professional.hub(null);
        assertThat(professional.getHub()).isNull();
    }
}
