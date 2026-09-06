package net.jojoaddison.broker;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

/**
 * The one place this service publishes to the broker, and the only class allowed to hold a
 * {@link StreamBridge}. {@code OutboundPublishingArchTest} enforces that second half.
 *
 * <p><strong>Why it exists: a caller was waiting sixty seconds for a broker that was not there.</strong>
 * {@code MessageService.send} and {@code ProfessionalVerificationService.record} both called
 * {@code streamBridge.send(...)} on the request thread. Measured on {@code deploy/e2e/compose.yml},
 * which deliberately runs no broker: the first {@code POST /api/messages/send} took <b>60.6s</b> and
 * every one after it took 15ms. Nothing failed — the row was written, a 201 came back, and the only
 * trace was a {@code WARN} naming the message id and not the wait (backlog item 39a).
 *
 * <p>The sixty seconds is <b>not</b> the publish. Once an output binding exists, the Kafka producer
 * buffers and returns; it is the <em>creation</em> of the binding that is slow, and
 * {@code StreamBridge} creates one lazily inside the first {@code send()} for that destination:
 * {@code resolveDestination} calls {@code BindingService.bindProducer}, the Kafka provisioner opens an
 * AdminClient, and the describe/create future is bounded by the client's {@code default.api.timeout.ms},
 * whose default is 60000. So the cost is one-off, paid by whoever publishes first after a start, and
 * paid again after every restart for as long as the broker is unreachable.
 *
 * <p>It is also <b>shared</b>, which the measurement above hides. {@code StreamBridge} guards both
 * {@code resolveDestination} and the conversion in {@code send} with one {@code ReentrantLock}, so
 * while one thread is stuck creating a binding, every other thread publishing <em>anything</em> queues
 * behind it. One unlucky request does not pay alone.
 *
 * <p><strong>What this class does about it.</strong> It hands the send to a dedicated single-threaded
 * executor and returns. A caller never touches the broker, never takes that lock, and never waits —
 * whatever the state of the broker, and whether or not the binding has been created yet.
 *
 * <p><strong>This changes no contract.</strong> Every caller was already best-effort and says so in
 * its own javadoc: the message is saved and readable on the desk, the verification is recorded and
 * projected, and the event is a notification that a consumer can reconstruct from the record. None of
 * them ever failed an operation because a publish failed, and none of them ever reported one. The
 * implementation now matches what those comments already promised.
 *
 * <p><strong>What it costs, stated plainly.</strong> The sixty-second request was the only externally
 * visible symptom of an unreachable broker, and this removes it — an already-silent failure gets
 * quieter. Nothing replaces it, because there is nowhere in this estate for it to go:
 * {@code MANAGEMENT_HEALTH_BINDERS_ENABLED=false} in all three compose files (deliberately — the
 * indicator feeds the container healthcheck), and {@code management.prometheus.metrics.export.enabled}
 * is {@code false} in {@code application-prod.yml}, so a Micrometer counter would reach no registry
 * anything reads. {@code deploy/observability/alert-rules.yml} records what that costs: a rule that
 * cannot fire is worse than no rule, because the file looks like coverage. The {@code WARN} below is
 * therefore the whole of the visibility, exactly as it was before this change — see backlog item 40.
 */
@Component
public class OutboundEventPublisher {

    /**
     * The executor bean this class runs on. Named here rather than in the configuration because the
     * dependency is the other way round: {@code ..config..} may reach anything, nothing may reach it.
     */
    public static final String EXECUTOR_BEAN = "outboundEventExecutor";

    private static final Logger LOG = LoggerFactory.getLogger(OutboundEventPublisher.class);

    private final StreamBridge streamBridge;

    private final Executor executor;

    public OutboundEventPublisher(StreamBridge streamBridge, @Qualifier(EXECUTOR_BEAN) Executor executor) {
        this.streamBridge = streamBridge;
        this.executor = executor;
    }

    /**
     * Queues one already-serialised event and returns immediately.
     *
     * <p>Serialisation stays with the caller on purpose. A payload that cannot be written is a defect
     * in this service, not a broker outage, and it should be reported against the record it describes
     * rather than surfacing later on a publisher thread with nothing to name.
     *
     * <p>The executor keeps one thread and a bounded queue, so events reach the broker in the order
     * they were produced — which matters for {@code professional-verification}, where two decisions
     * about one professional must not overtake each other — and a broker that never answers cannot
     * accumulate work without limit.
     *
     * @param bindingName the binding as {@code application.yml} declares it, e.g. {@code binding-out-0}
     * @param payload the wire form, already serialised
     * @param subject what to name in the log if this never reaches the broker, e.g. {@code Message m1}
     */
    public void publish(String bindingName, String payload, String subject) {
        try {
            executor.execute(() -> send(bindingName, payload, subject));
        } catch (RejectedExecutionException e) {
            // The queue is full, which takes a broker that has been unreachable long enough for the
            // one thread to still be stuck creating the binding. Dropping is the honest answer: the
            // alternative policies either run this on the caller — the defect this class exists to
            // remove — or grow without bound.
            LOG.warn("Dropped the event for {} on {}: the outbound publish queue is full", subject, bindingName);
        }
    }

    /**
     * Runs on the publisher thread. Never rethrows: there is no caller left to tell.
     *
     * <p><b>A missing broker is silent</b> — the app starts, serves and reports healthy while
     * everything produced goes nowhere. This log line is the only thing that says so.
     */
    private void send(String bindingName, String payload, String subject) {
        try {
            streamBridge.send(bindingName, payload);
        } catch (RuntimeException e) {
            LOG.warn("{} was recorded but its event could not be published to {}", subject, bindingName, e);
        }
    }
}
