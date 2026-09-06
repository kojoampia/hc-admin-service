package net.jojoaddison.web.rest;

import java.security.Principal;
import net.jojoaddison.broker.KafkaConsumer;
import net.jojoaddison.broker.OutboundEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

@RestController
@RequestMapping("/api/hc-admin-service-kafka")
public class HcAdminServiceKafkaResource {

    private static final String PRODUCER_BINDING_NAME = "binding-out-0";

    private final Logger log = LoggerFactory.getLogger(HcAdminServiceKafkaResource.class);
    private final KafkaConsumer kafkaConsumer;
    private final OutboundEventPublisher eventPublisher;

    public HcAdminServiceKafkaResource(OutboundEventPublisher eventPublisher, KafkaConsumer kafkaConsumer) {
        this.eventPublisher = eventPublisher;
        this.kafkaConsumer = kafkaConsumer;
    }

    /**
     * Publishes on the same binding and through the same publisher as everything else, which is the
     * point: this handler had the shape backlog item 39a is about — {@code streamBridge.send} straight
     * on the request thread, and without even the {@code try/catch} the two services had.
     *
     * <p>It gives up nothing observable by no longer waiting. {@code StreamBridge.send} returns true
     * once the message is handed to the producer's accumulator, so its result never said the broker
     * had received anything; whether the bridge works is answered by {@code /register} and by what
     * arrives there, not by this response.
     */
    @PostMapping("/publish")
    public void publish(@RequestParam("message") String message) {
        log.debug("REST request the message : {} to send to Kafka topic ", message);
        eventPublisher.publish(PRODUCER_BINDING_NAME, message, "The message posted to /publish");
    }

    @GetMapping("/register")
    public ResponseBodyEmitter register(Principal principal) {
        return kafkaConsumer.register(principal.getName());
    }

    @GetMapping("/unregister")
    public void unregister(Principal principal) {
        kafkaConsumer.unregister(principal.getName());
    }
}
