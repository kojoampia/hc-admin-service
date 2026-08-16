package net.jojoaddison.broker;

import static org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.jojoaddison.service.dto.MessageSentEvent;
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

    private final ObjectMapper objectMapper;

    public KafkaConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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

    /**
     * Deliver an inbound event, to one recipient when it names one and to everybody otherwise.
     *
     * <p>Broadcast was the only behaviour this had, and it was right for what used it: the audit
     * trail is the same stream for every operator watching. A sent message is not — it names a
     * recipient, and delivering somebody's private reply to every connected console is not a
     * notification, it is a disclosure.
     *
     * <p>The fallback is deliberate rather than defensive. Anything that is not a recognisable
     * {@code messageSentEvent}, or that names a recipient nobody here is connected as, keeps the
     * old behaviour — so the audit trail continues to work and a vendor's notification, whose
     * recipient is a user of another service entirely, does not silently vanish from this one.
     */
    @Override
    public void accept(String input) {
        log.debug("Got message from kafka stream: {}", input);
        String recipient = recipientOf(input);
        if (recipient != null && emitters.containsKey(recipient)) {
            deliver(recipient, input);
            return;
        }
        emitters.keySet().forEach(key -> deliver(key, input));
    }

    /**
     * The {@code toAddress} of a {@code messageSentEvent}, or null for anything else.
     *
     * <p>Parsed rather than pattern-matched, and failure is not an error: this stream carries the
     * audit trail and whatever else is published to the topic, most of which is not JSON at all.
     *
     * <p>Package-private so the routing decision can be tested directly. Spring's
     * {@code ResponseBodyEmitter.Handler} is not public, so what an emitter was actually sent cannot
     * be observed from a test — the decision is the part worth asserting, and it is all of the new
     * behaviour.
     */
    String recipientOf(String input) {
        try {
            JsonNode node = objectMapper.readTree(input);
            if (!node.isObject() || !MessageSentEvent.TYPE.equals(node.path("eventType").asText(null))) {
                return null;
            }
            String toAddress = node.path("toAddress").asText(null);
            return toAddress == null || toAddress.isBlank() ? null : toAddress;
        } catch (JsonProcessingException | RuntimeException e) {
            return null;
        }
    }

    private void deliver(String key, String input) {
        Collection<SseEmitter> registered = emitters.get(key);
        if (registered == null) {
            return;
        }
        registered.forEach(emitter -> {
            try {
                emitter.send(event().data(input, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                // The client is gone, or the response is already committed. Drop it rather than
                // retry — leaving it registered means throwing on every future message too.
                log.debug("dropping unreachable sse client for {}", key);
                remove(key, emitter);
            }
        });
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
