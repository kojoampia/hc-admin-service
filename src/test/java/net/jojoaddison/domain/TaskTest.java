package net.jojoaddison.domain;

import static net.jojoaddison.domain.MessageTestSamples.*;
import static net.jojoaddison.domain.ProfessionalTestSamples.*;
import static net.jojoaddison.domain.TaskTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import net.jojoaddison.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class TaskTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Task.class);
        Task task1 = getTaskSample1();
        Task task2 = new Task();
        assertThat(task1).isNotEqualTo(task2);

        task2.setId(task1.getId());
        assertThat(task1).isEqualTo(task2);

        task2 = getTaskSample2();
        assertThat(task1).isNotEqualTo(task2);
    }

    @Test
    void ownerTest() {
        Task task = getTaskRandomSampleGenerator();
        Professional professionalBack = getProfessionalRandomSampleGenerator();

        task.setOwner(professionalBack);
        assertThat(task.getOwner()).isEqualTo(professionalBack);

        task.owner(null);
        assertThat(task.getOwner()).isNull();
    }

    @Test
    void sourceMessageTest() {
        Task task = getTaskRandomSampleGenerator();
        Message messageBack = getMessageRandomSampleGenerator();

        task.setSourceMessage(messageBack);
        assertThat(task.getSourceMessage()).isEqualTo(messageBack);

        task.sourceMessage(null);
        assertThat(task.getSourceMessage()).isNull();
    }
}
