package net.jojoaddison.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.util.MimeTypeUtils;

/**
 * What can actually be asserted about backlog item 39a, and what cannot.
 *
 * <p>The defect was a <b>duration</b>, not a wrong answer: everything returned 201, the row was
 * written, and the only difference between healthy and broken was sixty seconds. Asserting "fast" is
 * flaky by construction and asserting "slow" needs an absent broker, which no test in this suite has.
 *
 * <p>So the property under test is the one the fix actually establishes and which is true or false
 * without reference to a clock: <b>the send does not happen on the calling thread.</b> An executor
 * that records its tasks and never runs them makes that a plain equality — if the publish were still
 * inline, {@code streamBridge} would have been touched before the executor ran anything, and
 * {@code verifyNoInteractions} says so.
 *
 * <p><b>What this cannot catch</b>, and neither can anything else here: the sixty seconds itself;
 * that the first publish after a real broker outage is quick for the caller; a future publisher that
 * reaches the broker through {@code KafkaTemplate} rather than {@code StreamBridge}; and whether 256
 * queued events is the right bound. {@code OutboundPublishingArchTest} covers the one of those that
 * can be covered — no <i>other</i> class holding a {@code StreamBridge} — and the rest need a stack
 * with the broker taken away.
 */
class OutboundEventPublisherTest {

    private final StreamBridge streamBridge = mock(StreamBridge.class);

    /** Records what was handed over without running it, so "was it inline?" is directly observable. */
    private final List<Runnable> queued = new ArrayList<>();

    private final OutboundEventPublisher publisher = new OutboundEventPublisher(streamBridge, queued::add);

    @Test
    void handsTheSendToTheExecutorRatherThanRunningItOnTheCaller() {
        publisher.publish("binding-out-0", "{\"id\":\"m1\"}", "Message m1");

        // The assertion that fails if the publish goes back onto the request thread. Nothing about
        // it is timing-dependent: the executor here has run nothing at all yet.
        verifyNoInteractions(streamBridge);
        assertThat(queued).hasSize(1);
    }

    @Test
    void sendsTheDeclaredBindingAndPayloadOnceTheExecutorRunsIt() {
        when(streamBridge.send(anyString(), anyString())).thenReturn(true);

        publisher.publish("verification-out-0", "{\"id\":\"v3\"}", "Verification v3");
        queued.forEach(Runnable::run);

        // The binding is a string, so a rename in application.yml is not a compile error — it is an
        // event published to a binding nothing consumes, with a successful response to the caller.
        verify(streamBridge).send("verification-out-0", "{\"id\":\"v3\"}");
    }

    /**
     * A broker failure now happens after the response has gone, so there is no caller left to tell and
     * nothing to unwind. It must not escape onto the publisher thread either, where it would kill
     * nothing but would be reported by an uncaught-exception handler rather than named.
     */
    @Test
    void aFailedSendIsSwallowedOnThePublisherThread() {
        when(streamBridge.send(anyString(), anyString())).thenThrow(new IllegalStateException("no broker"));

        publisher.publish("binding-out-0", "payload", "Message m1");

        assertThatCode(() -> queued.forEach(Runnable::run)).doesNotThrowAnyException();
    }

    /**
     * <b>The wording of these two lines is a contract, not prose — backlog item 40c.</b>
     *
     * <p>A failed publish is visible in the log and nowhere else, and that was decided rather than
     * settled for: a Micrometer counter is registered against a composite with no delegate in
     * production ({@code management.prometheus.metrics.export.enabled: false}) and nothing on the host
     * scrapes this application, where the log demonstrably reaches Loki — proven in item 43 by reading
     * a line back out, by two independent paths, with fourteen days of retention. So the detector is a
     * LogQL query, recorded in {@code deploy/observability/alert-rules.yml}:
     *
     * <pre>{@code {service_name="hc-admin-service"} |~ `could not be published to|outbound publish queue is full` }</pre>
     *
     * <p>Which makes the message text load-bearing in a way no compiler can see. A reword is a silent
     * break of the only detector this failure has, so these two cases pin the substrings that query
     * matches. <b>If one of them fails, the fix is to move the query with the text</b> — in that file
     * and in this javadoc — not to relax the assertion.
     *
     * <p>They assert substrings rather than whole formatted messages on purpose: the subject and the
     * binding name are already covered by the cases above, and pinning the full line would fail on a
     * change to the parts the query does not read.
     */
    @Test
    void theFailedSendWarningKeepsTheTextTheLokiQueryMatches() {
        when(streamBridge.send(anyString(), anyString())).thenThrow(new IllegalStateException("no broker"));

        List<ILoggingEvent> logged = capturingLogsOf(() -> {
            publisher.publish("binding-out-0", "payload", "Message m1");
            queued.forEach(Runnable::run);
        });

        assertThat(logged)
            .as("the only trace a dropped event leaves; deploy/observability/alert-rules.yml queries this string")
            .anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("could not be published to");
            });
    }

    /** The second half of the same contract — see the case above. */
    @Test
    void theFullQueueWarningKeepsTheTextTheLokiQueryMatches() {
        Executor full = task -> {
            throw new RejectedExecutionException("queue full");
        };
        OutboundEventPublisher publisherOnAFullQueue = new OutboundEventPublisher(streamBridge, full);

        List<ILoggingEvent> logged = capturingLogsOf(() -> publisherOnAFullQueue.publish("binding-out-0", "payload", "Message m1"));

        assertThat(logged)
            .as("a queue full enough to drop events is the louder failure of the two and must be queryable with it")
            .anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("outbound publish queue is full");
            });
    }

    /** Detaches in a {@code finally}, so a failing assertion cannot leave the appender on the logger. */
    private static List<ILoggingEvent> capturingLogsOf(Runnable work) {
        Logger logger = (Logger) LoggerFactory.getLogger(OutboundEventPublisher.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            work.run();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        return List.copyOf(appender.list);
    }

    /**
     * The queue is bounded and the rejection policy aborts, so a broker that has been unreachable long
     * enough to fill it throws {@link RejectedExecutionException} back here. That must not reach the
     * caller: the request has nothing to do with the broker and a dropped notification is the
     * documented cost.
     */
    @Test
    void aRejectedTaskIsDroppedRatherThanThrownAtTheCaller() {
        Executor full = task -> {
            throw new RejectedExecutionException("queue full");
        };
        OutboundEventPublisher publisherOnAFullQueue = new OutboundEventPublisher(streamBridge, full);

        assertThatCode(() -> publisherOnAFullQueue.publish("binding-out-0", "payload", "Message m1")).doesNotThrowAnyException();
        verifyNoInteractions(streamBridge);
    }

    /**
     * <b>The keyed overload puts the partition key, the content type and the raw bytes on the
     * message</b> — backlog item 54, and nothing asserted any of it until the review said so.
     *
     * <p>The gap was precise and worth recording. {@code PatientPlanVerificationServiceTest} pins
     * that the lowercased address reaches this class as an argument; <b>no test read what this class
     * then built</b>. So dropping {@link OutboundEventPublisher} back to the unkeyed
     * {@code send(binding, payload)} — which is the whole of the round trip's partitioning — passed
     * the entire gate, on both repositories, in green.
     *
     * <p>The key is asserted as <b>bytes</b> rather than as a String, and that is the assertion doing
     * the work rather than pedantry: the binder's key serialiser is a {@code ByteArraySerializer} by
     * default, so handing it a {@code String} is a {@code ClassCastException} <em>inside the producer
     * at send time</em> — no compile error here, nothing failing until a real broker is in front of
     * it, which is the one place this repository cannot reach.
     */
    @Test
    void aKeyedSendCarriesThePartitionKeyTheContentTypeAndTheBytes() {
        publisher.publish("plan-verification-out-0", "{\"data\":{\"plan\":\"MELON\"}}", "Plan verification for subj-abc", "ama@mail.gh");
        queued.forEach(Runnable::run);

        ArgumentCaptor<Message<byte[]>> sent = ArgumentCaptor.forClass(Message.class);
        verify(streamBridge).send(eq("plan-verification-out-0"), sent.capture());
        Message<byte[]> message = sent.getValue();

        assertThat(new String(message.getPayload(), StandardCharsets.UTF_8))
            .as("the frame must go out as the bytes the caller serialised, not re-encoded")
            .isEqualTo("{\"data\":{\"plan\":\"MELON\"}}");
        assertThat(message.getHeaders().get(MessageHeaders.CONTENT_TYPE))
            .as("hc-patient binds this topic as application/json; text/plain would hand their function a String")
            .hasToString(MimeTypeUtils.APPLICATION_JSON_VALUE);
        assertThat((byte[]) message.getHeaders().get(KafkaHeaders.KEY))
            .as("the Kafka key must be BYTES — a String is a ClassCastException inside the producer, at send time")
            .isEqualTo("ama@mail.gh".getBytes(StandardCharsets.UTF_8));
        assertThat(message.getHeaders().get("patientKey"))
            .as("the spelling hc-patient's own publishers use, so either convention finds the subject")
            .isEqualTo("ama@mail.gh");
    }

    /**
     * And the three-argument form still sends a bare payload.
     *
     * <p>The keyed overload was added beneath the existing one, so every current caller now routes
     * through a method that can build a {@code Message}. This pins that they do not: {@code sse-topic}
     * and {@code professional-verification} are {@code text/plain} bindings, and a consumer of either
     * reading a JSON-typed message rather than a String is a change nobody asked for.
     */
    @Test
    void anUnkeyedSendIsStillAPlainPayload() {
        publisher.publish("binding-out-0", "{\"id\":\"v3\"}", "Verification v3");
        queued.forEach(Runnable::run);

        verify(streamBridge).send("binding-out-0", "{\"id\":\"v3\"}");
    }
}
