package net.jojoaddison.broker;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The SSE registry's bookkeeping. These are the guarantees that were missing while nothing consumed
 * the bridge — see {@link KafkaConsumer}'s class comment for what each one replaces.
 */
class KafkaConsumerTest {

    @Test
    void aSecondConnectionForTheSamePrincipalDoesNotEvictTheFirst() {
        KafkaConsumer consumer = new KafkaConsumer(new ObjectMapper());

        SseEmitter first = consumer.register("admin");
        SseEmitter second = consumer.register("admin");

        assertThat(first).isNotSameAs(second);
        // Two browser tabs, two live connections. The previous one-emitter-per-key map returned 1
        // here and dropped the first emitter on the floor without completing it.
        assertThat(consumer.connectionCount("admin")).isEqualTo(2);
    }

    // There is deliberately no test that `emitter.complete()` removes one connection. SseEmitter
    // only runs its onCompletion callback once Spring MVC has attached it to an async request, so
    // outside a servlet container completing an emitter is a no-op — a test asserting otherwise
    // would be asserting the container's behaviour, not this class's. That path is why unregister()
    // removes the key itself rather than relying on those callbacks.

    @Test
    void unregisteringDropsThePrincipalEntirely() {
        KafkaConsumer consumer = new KafkaConsumer(new ObjectMapper());
        consumer.register("admin");
        consumer.register("admin");

        consumer.unregister("admin");

        assertThat(consumer.connectionCount("admin")).isZero();
    }

    @Test
    void unregisteringAnUnknownPrincipalIsANoOp() {
        KafkaConsumer consumer = new KafkaConsumer(new ObjectMapper());

        consumer.unregister("nobody");

        assertThat(consumer.connectionCount("nobody")).isZero();
    }

    /**
     * Registrations arrive on request threads while {@link KafkaConsumer#accept} runs on the Kafka
     * consumer thread. With the previous {@code HashMap} this interleaving could resize the map
     * mid-read; the assertion is simply that every registration survives.
     */
    @Test
    void concurrentRegistrationAndBroadcastDoNotLoseClients() throws Exception {
        KafkaConsumer consumer = new KafkaConsumer(new ObjectMapper());
        int clients = 200;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(clients);

        try {
            for (int i = 0; i < clients; i++) {
                String key = "client-" + i;
                pool.submit(() -> {
                    try {
                        start.await();
                        consumer.register(key);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 50; i++) {
                        consumer.accept("{\"type\":\"Security\"}");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        for (int i = 0; i < clients; i++) {
            assertThat(consumer.connectionCount("client-" + i)).isEqualTo(1);
        }
    }

    /**
     * A sent event routes to the address it names; everything else routes to everybody.
     *
     * <p>Broadcast was the only behaviour here, and for the audit trail it is correct — every
     * operator watches the same stream. A message is not that: delivering one operator's private
     * reply to every connected console is a disclosure, not a notification.
     *
     * <p>What an emitter was actually sent cannot be observed from a test — Spring's
     * {@code ResponseBodyEmitter.Handler} is package-private — so the assertion is on the decision
     * the fan-out makes, which is the whole of the new behaviour.
     */
    @Test
    void shouldRouteASentEventToItsRecipient() {
        KafkaConsumer consumer = new KafkaConsumer(new ObjectMapper());

        assertThat(consumer.recipientOf("{\"eventType\":\"messageSentEvent\",\"id\":\"m99\",\"toAddress\":\"desk@abofonsa.care\"}"))
            .isEqualTo("desk@abofonsa.care");
    }

    /**
     * The audit trail publishes to this same topic and names no recipient. If routing swallowed it
     * the live trail would go quiet and nothing would report why, so anything unrecognisable has to
     * fall back to the broadcast this always did.
     */
    @Test
    void shouldNotRouteAnythingThatIsNotASentEvent() {
        KafkaConsumer consumer = new KafkaConsumer(new ObjectMapper());

        assertThat(consumer.recipientOf("{\"type\":\"Security\",\"message\":\"an audit row\"}")).isNull();
        assertThat(consumer.recipientOf("not json at all")).isNull();
        assertThat(consumer.recipientOf("[1,2,3]")).isNull();
        // A sent event with no recipient must broadcast rather than route to the empty string.
        assertThat(consumer.recipientOf("{\"eventType\":\"messageSentEvent\",\"toAddress\":\"\"}")).isNull();
        assertThat(consumer.recipientOf("{\"eventType\":\"messageSentEvent\"}")).isNull();
    }

    /** A recipient nobody is connected as must not throw, and must leave connections intact. */
    @Test
    void shouldSurviveASentEventForSomebodyConnectedElsewhere() {
        KafkaConsumer consumer = new KafkaConsumer(new ObjectMapper());
        consumer.register("operator");

        consumer.accept("{\"eventType\":\"messageSentEvent\",\"id\":\"m1\",\"toAddress\":\"orders@kaneshiemed.gh\"}");

        assertThat(consumer.connectionCount("operator")).isEqualTo(1);
    }
}
