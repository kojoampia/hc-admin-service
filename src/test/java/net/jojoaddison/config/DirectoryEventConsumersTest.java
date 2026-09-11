package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import net.jojoaddison.service.DirectoryProjectionService;
import net.jojoaddison.service.SiblingEventParser;
import net.jojoaddison.service.dto.SiblingDomainEvent;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * What the two consumers do with a failure, which is not the same as what they do with a bad message.
 *
 * <h2>Why this is a unit test and not a case in {@code DirectoryEventConsumptionIT}</h2>
 *
 * <p>The behaviour under test is "the exception leaves the consumer". Driven through the binder, that
 * exception is caught by the binder's retry and error handling, so what an integration test could
 * observe is the <em>consequence</em> — a redelivery, or a dead letter — and neither exists without a
 * real broker. Calling the bean directly is the only place the rethrow itself is visible, and the
 * rethrow is the change. The configuration that decides what the binder then does with it is asserted
 * in {@code ConfigurationBindingTest} against the file that ships.
 */
class DirectoryEventConsumersTest {

    private static final String FRAME =
        "{\"eventId\":\"evt-1\",\"type\":\"AccountCreated\",\"version\":1," +
        "\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"patientGateway\"," +
        "\"subject\":{\"email\":\"ama.mensah@example.com\",\"login\":\"amensah\",\"patientId\":null}," +
        "\"data\":{\"authorities\":\"ROLE_USER\",\"activated\":false}}";

    private final SiblingEventParser parser = new SiblingEventParser(new ObjectMapper());
    private final DirectoryProjectionService projection = mock(DirectoryProjectionService.class);
    private final DirectoryEventConsumers consumers = new DirectoryEventConsumers(parser, projection);

    /**
     * <b>A Mongo write that did not happen is not a message this service cannot use.</b>
     *
     * <p>Until 2026-09-05 both handlers caught every {@code RuntimeException}, warned, and returned —
     * so a {@code DataAccessException} from a failover, a connection reset or memory pressure was
     * swallowed, the offset advanced, and the event was gone. Not recoverable by {@code /reconcile}
     * either: that walks links, and the link is one of the writes that failed.
     */
    @Test
    void aFailedWriteIsRethrownSoTheBinderCanRetryAndDeadLetterIt() {
        when(projection.apply(any(SiblingDomainEvent.class))).thenThrow(
            new DataAccessResourceFailureException("Timed out while waiting for a server that matches ReadPreference")
        );

        assertThatExceptionOfType(DataAccessResourceFailureException.class)
            .as("swallowing this commits the offset over an event nothing can then recover")
            .isThrownBy(() -> consumers.patientDirectoryConsumer().accept(patientFrame(FRAME)));
    }

    /** The same rule on the other stream — one handler, so the guard has to cover both beans. */
    @Test
    void theProfessionalConsumerRethrowsToo() {
        when(projection.apply(any(SiblingDomainEvent.class))).thenThrow(new DataAccessResourceFailureException("no primary"));

        String frame =
            "{\"eventId\":\"evt-2\",\"eventType\":\"registration.created\"," +
            "\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"hc-professional-gateway\",\"actor\":\"anonymous\"," +
            "\"payload\":{\"accountId\":\"acc-1\",\"login\":\"kboateng\",\"email\":\"k.boateng@example.com\"}}";

        assertThatExceptionOfType(DataAccessResourceFailureException.class).isThrownBy(() ->
            consumers.professionalDirectoryConsumer().accept(MessageBuilder.withPayload(bytes(frame)).build())
        );
    }

    /**
     * The other half, and the reason the rethrow is safe: a frame this service cannot use never
     * reaches the projection at all.
     *
     * <p>The leniency lives in {@code SiblingEventParser}, which answers an empty Optional for an
     * unreadable envelope, a missing subject key, an unreadable timestamp and a type neither producer
     * has published. That is where it belongs — at the point the frame is read — rather than wrapped
     * around everything downstream of it, which is what made a database outage look like a bad
     * message.
     */
    @Test
    void aFrameThisServiceCannotUseNeverReachesTheProjection() {
        assertThatCode(() -> {
            consumers.patientDirectoryConsumer().accept(patientFrame("this is not an envelope"));
            consumers.patientDirectoryConsumer().accept(patientFrame("{\"type\":\"SomethingAddedNextYear\",\"subject\":{}}"));
            consumers.professionalDirectoryConsumer().accept(MessageBuilder.withPayload(bytes("{}")).build());
        })
            .as("an unusable frame is refused by the parser and throws nothing")
            .doesNotThrowAnyException();

        verify(projection, never()).apply(any());
    }

    private Message<byte[]> patientFrame(String json) {
        return MessageBuilder.withPayload(bytes(json))
            .setHeader(DirectoryEventConsumers.PATIENT_KEY_HEADER, "ama.mensah@example.com")
            .build();
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
