package net.jojoaddison.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.jojoaddison.broker.OutboundEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/**
 * The thread {@link OutboundEventPublisher} publishes on, and why it is its own.
 *
 * <p>Not {@code taskExecutor} from {@link AsyncConfiguration}. That pool is what every {@code @Async}
 * method in the service shares, and the work here is not ordinary background work: creating an output
 * binding against an unreachable broker blocks for up to a minute (see the class javadoc on
 * {@code OutboundEventPublisher}). Borrowing the shared pool would let one absent broker occupy its
 * threads and stall everything else that had nothing to do with the broker at all.
 *
 * <p>Three properties are deliberate and each one is asserted by
 * {@code OutboundPublishingConfigurationTest}, because each is a plausible-looking edit that would
 * quietly undo the fix:
 *
 * <ul>
 *   <li><b>One thread.</b> Events reach the broker in the order they were produced. Two threads would
 *       let two decisions about one professional overtake each other on
 *       {@code professional-verification}, and would buy nothing — a second thread would only queue on
 *       the same {@code StreamBridge} lock as the first.
 *   <li><b>A bounded queue.</b> An unbounded one turns a broker outage into heap growth that nothing
 *       reports. 256 is far more than the handful of events this console produces in the sixty seconds
 *       a binding creation can take, and small enough that a broker that never answers cannot
 *       accumulate work indefinitely.
 *   <li><b>Abort, not caller-runs.</b> {@link ThreadPoolExecutor.CallerRunsPolicy} is the usual choice
 *       for a bounded queue and here it is exactly wrong: it hands the blocking send back to the
 *       request thread, which is the defect this whole arrangement exists to remove — and it would do
 *       so only under the load that makes it hardest to diagnose.
 * </ul>
 */
@Configuration
public class OutboundPublishingConfiguration {

    /** See the bullet above; changing it is a decision, not tuning. */
    static final int QUEUE_CAPACITY = 256;

    /**
     * {@code shutdownNow}, not {@code shutdown} or {@code close}. On shutdown the thread may be a
     * minute into an AdminClient call against a broker that is not there, and Java 19+ makes
     * {@code close()} the inferred destroy method for an {@link ExecutorService} — which waits. These
     * events are best-effort by construction, so interrupting them is right and waiting for them would
     * add a minute to every stop during exactly the incident when restarts need to be quick.
     */
    @Bean(name = OutboundEventPublisher.EXECUTOR_BEAN, destroyMethod = "shutdownNow")
    public ExecutorService outboundEventExecutor() {
        return new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            new CustomizableThreadFactory("hc-admin-publish-"),
            new ThreadPoolExecutor.AbortPolicy()
        );
    }
}
