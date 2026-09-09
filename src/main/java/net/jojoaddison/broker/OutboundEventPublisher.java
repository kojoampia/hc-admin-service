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
 * quieter. That cost was accepted rather than overlooked, and backlog item 40c is where it was
 * argued out.
 *
 * <p><strong>The decision, 2026-09-09: outbound publishing stays log-only, and the log is a detector
 * rather than a shrug.</strong> The two {@code WARN} lines below are the whole of the visibility, and
 * they are queried:
 *
 * <pre>{@code {service_name="hc-admin-service"} |~ `could not be published to|outbound publish queue is full` }</pre>
 *
 * <p>That path is <em>proven</em>, which is the reason it beat the alternatives rather than merely
 * being cheaper. Item 43 read a line of this service's back out of Loki on the production host by two
 * independent routes — the OTel agent's OTLP log export and Alloy's {@code job="docker"} scrape — with
 * {@code multitenancy_enabled: false} and {@code retention_period: 14d}. <b>So the strings in
 * {@link #publish} and {@link #send} are a contract with
 * {@code deploy/observability/alert-rules.yml}, and {@code OutboundEventPublisherTest} pins both.</b>
 * Reword one and that test fails; move the query with it rather than relaxing the test.
 *
 * <p><strong>Why not a metric, which is the obvious answer.</strong> It would be registered against
 * nothing and read by nothing. {@code management.prometheus.metrics.export.enabled} is {@code false}
 * in {@code application-prod.yml} and {@code micrometer-registry-prometheus} is the only registry on
 * this classpath, so in production the composite has no delegate and a {@code Counter} records into a
 * no-op; and this host has no application scrape targets at all — {@code alert-rules.yml} was once
 * written on Micrometer names and matched nothing for ever, which is also the evidence that the
 * agent's Micrometer bridge is not exporting here. That file is emphatic that a rule which cannot
 * fire is worse than no rule, because it looks like coverage.
 *
 * <p>The binder health indicator is not the answer either, and is the one candidate that must not be
 * "fixed". {@code MANAGEMENT_HEALTH_BINDERS_ENABLED=false} in all three compose files is deliberate:
 * it contributes to {@code /management/health}, which is what the container healthcheck reads, so
 * switching it on makes an unreachable broker restart the container.
 *
 * <p><strong>And why no badge on the platform-health screen.</strong> The dashboard already answers
 * the neighbouring question from measured data — {@code DashboardMetricsService} reads
 * {@code sum(kafka_consumer_connection_count)} out of Mimir and reports "Realtime message
 * notification" as Live, Offline or Unknown. A second badge fed by the counter below would be a
 * figure about ourselves beside a figure about the platform, and it would claim more than it can
 * prove: the {@code WARN} fires when a send <em>throws</em>, which is binding creation against an
 * absent broker. No {@code sync: true} and no producer error channel is configured on either binding,
 * so once a binding exists the send hands the record to the producer's accumulator and returns — a
 * broker that dies <em>after</em> the bind loses records without this class ever being told.
 *
 * <p><strong>What would reopen this:</strong> an application metric proven present in Mimir (queried,
 * not inferred), a Loki ruler on the host so the query above can page instead of being run by hand,
 * or a publish becoming load-bearing — which today it is not, since every caller's javadoc records
 * that the record survives without it.
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
