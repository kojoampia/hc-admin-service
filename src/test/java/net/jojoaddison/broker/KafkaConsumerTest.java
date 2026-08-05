package net.jojoaddison.broker;

import static org.assertj.core.api.Assertions.assertThat;

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
        KafkaConsumer consumer = new KafkaConsumer();

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
        KafkaConsumer consumer = new KafkaConsumer();
        consumer.register("admin");
        consumer.register("admin");

        consumer.unregister("admin");

        assertThat(consumer.connectionCount("admin")).isZero();
    }

    @Test
    void unregisteringAnUnknownPrincipalIsANoOp() {
        KafkaConsumer consumer = new KafkaConsumer();

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
        KafkaConsumer consumer = new KafkaConsumer();
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
}
