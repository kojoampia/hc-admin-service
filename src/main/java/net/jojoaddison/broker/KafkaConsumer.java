package net.jojoaddison.broker;

import static org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fans inbound Kafka messages out to browser clients over SSE.
 *
 * <p>This had no callers until the dashboard's live audit trail was moved onto it — the frontend was
 * talking STOMP to a {@code /websocket} endpoint nothing implemented. Being on the live path now,
 * three things that did not matter while it was dead do:
 *
 * <ul>
 *   <li>the registry was a plain {@link java.util.HashMap}, written from request threads and read
 *       from the Kafka consumer thread — an unsynchronised map resized concurrently can drop entries
 *       or spin;
 *   <li>it was keyed one-emitter-per-principal, so a second browser tab silently evicted the first
 *       without completing it, leaking that connection until the client gave up;
 *   <li>emitters registered only {@code onCompletion}, so a timed-out or errored connection stayed
 *       in the map and every later broadcast threw into it.
 * </ul>
 */
@Component
public class KafkaConsumer implements Consumer<String> {

    /**
     * Long enough that reconnects are rare, short enough that a half-open connection is reclaimed.
     * The browser client treats a clean close as its normal cue to reconnect.
     */
    private static final Duration EMITTER_TIMEOUT = Duration.ofMinutes(30);

    private final Logger log = LoggerFactory.getLogger(KafkaConsumer.class);

    /** One entry per principal, holding every connection that principal currently has open. */
    private final Map<String, Collection<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String key) {
        log.debug("Registering sse client for {}", key);
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT.toMillis());

        // All three terminal callbacks remove this specific emitter, not the key.
        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            remove(key, emitter);
        });
        emitter.onError(throwable -> remove(key, emitter));

        emitters.computeIfAbsent(key, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        return emitter;
    }

    /**
     * Completes every connection this principal holds, and drops the key.
     *
     * <p>The removal is explicit rather than left to the {@code onCompletion} callbacks, because
     * those only fire once Spring MVC has attached the emitter to an async request — completing one
     * that never got that far removes nothing, and the entry would leak.
     */
    public void unregister(String key) {
        log.debug("Unregistering sse emitters for: {}", key);
        Collection<SseEmitter> registered = emitters.remove(key);
        if (registered != null) {
            List.copyOf(registered).forEach(SseEmitter::complete);
        }
    }

    @Override
    public void accept(String input) {
        log.debug("Got message from kafka stream: {}", input);
        emitters.forEach((key, registered) ->
            registered.forEach(emitter -> {
                try {
                    emitter.send(event().data(input, MediaType.APPLICATION_JSON));
                } catch (IOException | IllegalStateException e) {
                    // The client is gone, or the response is already committed. Drop it rather than
                    // retry — leaving it registered means throwing on every future message too.
                    log.debug("dropping unreachable sse client for {}", key);
                    remove(key, emitter);
                }
            })
        );
    }

    /** Visible for tests: how many connections this principal currently holds. */
    int connectionCount(String key) {
        return emitters.getOrDefault(key, List.of()).size();
    }

    private void remove(String key, SseEmitter emitter) {
        emitters.computeIfPresent(
            key,
            (ignored, registered) -> {
                registered.remove(emitter);
                // Returning null drops the key, so a principal who disconnects leaves nothing behind.
                return registered.isEmpty() ? null : registered;
            }
        );
    }
}
