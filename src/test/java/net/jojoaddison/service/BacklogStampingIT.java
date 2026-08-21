package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.Task;
import net.jojoaddison.domain.enumeration.MessageChannel;
import net.jojoaddison.domain.enumeration.MessageStatus;
import net.jojoaddison.domain.enumeration.Priority;
import net.jojoaddison.domain.enumeration.TaskState;
import net.jojoaddison.repository.MessageRepository;
import net.jojoaddison.repository.TaskRepository;
import net.jojoaddison.service.dto.MessageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * When a message stops being unread and a task stops being open — the two timestamps item 14 added.
 *
 * <p>They exist so the dashboard can say what a backlog <em>was</em>, and they are only worth having
 * if they are right. Two ways they could quietly not be, and both are asserted here.
 *
 * <p><b>An update must not move the stamp.</b> Updates go through the mapper, which builds a fresh
 * entity from a DTO that does not carry the field, so a naive callback stamping "now" whenever it
 * finds the field empty would push a message's read time forward on every edit — and the backlog
 * series would report messages as read on the day somebody last touched them, with nothing on the
 * screen looking wrong.
 *
 * <p><b>Re-opening must clear it.</b> A task moved back out of DONE keeping its old closing time
 * would count as closed in every past month it was actually open.
 */
@IntegrationTest
class BacklogStampingIT {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private MessageService messageService;

    @BeforeEach
    void clear() {
        messageRepository.deleteAll();
        taskRepository.deleteAll();
    }

    private static Message message(MessageStatus status) {
        return new Message()
            .subject("Fixture")
            .fromAddress("sender@abofonsa.care")
            .senderName("A sender")
            .body("...")
            .sentAt(Instant.parse("2026-08-01T09:00:00Z"))
            .channel(MessageChannel.EMAIL)
            .priority(Priority.NORMAL)
            .status(status);
    }

    private static Task task(TaskState state) {
        return new Task().title("Fixture").state(state).priority(Priority.NORMAL);
    }

    @Test
    void anUnreadMessageHasNoReadTime() {
        Message saved = messageRepository.save(message(MessageStatus.NEW));

        assertThat(saved.getReadAt()).isNull();
    }

    @Test
    void readingAMessageStampsIt() {
        Message saved = messageRepository.save(message(MessageStatus.READ));

        assertThat(saved.getReadAt()).isNotNull();
    }

    /** REPLIED is not unread either. The tile counts NEW, so anything else has left the backlog. */
    @Test
    void replyingStampsItToo() {
        assertThat(messageRepository.save(message(MessageStatus.REPLIED)).getReadAt()).isNotNull();
    }

    /**
     * The one that matters: an edit through the service, which is how every update reaches the
     * database, must leave the original read time exactly where it was.
     */
    @Test
    void anUpdateKeepsTheOriginalReadTime() {
        Message read = messageRepository.save(message(MessageStatus.READ).readAt(Instant.parse("2026-07-04T10:30:00Z")));

        MessageDTO dto = messageService.findOne(read.getId()).orElseThrow();
        dto.setSubject("Edited later");
        messageService.update(dto);

        assertThat(messageRepository.findById(read.getId()).orElseThrow().getReadAt())
            .isEqualTo(Instant.parse("2026-07-04T10:30:00Z").truncatedTo(ChronoUnit.MILLIS));
    }

    /** Marked unread again, it is in the backlog again — and a backlog entry has no read time. */
    @Test
    void markingAMessageUnreadClearsTheReadTime() {
        Message read = messageRepository.save(message(MessageStatus.READ));
        read.setStatus(MessageStatus.NEW);

        assertThat(messageRepository.save(read).getReadAt()).isNull();
    }

    @Test
    void anOpenTaskHasNoClosingTime() {
        assertThat(taskRepository.save(task(TaskState.DOING)).getClosedAt()).isNull();
    }

    @Test
    void finishingATaskStampsIt() {
        assertThat(taskRepository.save(task(TaskState.DONE)).getClosedAt()).isNotNull();
    }

    @Test
    void reopeningATaskClearsTheClosingTime() {
        Task done = taskRepository.save(task(TaskState.DONE));
        done.setState(TaskState.DOING);

        assertThat(taskRepository.save(done).getClosedAt()).isNull();
    }

    /** Moving a finished task around must not restamp it as finished today. */
    @Test
    void savingAFinishedTaskAgainKeepsItsClosingTime() {
        Task done = taskRepository.save(task(TaskState.DONE).closedAt(Instant.parse("2026-07-12T12:00:00Z")));
        done.setTitle("Edited later");

        assertThat(taskRepository.save(done).getClosedAt()).isEqualTo(Instant.parse("2026-07-12T12:00:00Z"));
    }
}
