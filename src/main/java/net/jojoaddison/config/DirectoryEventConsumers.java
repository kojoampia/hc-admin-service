package net.jojoaddison.config;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.jojoaddison.service.DirectoryProjectionService;
import net.jojoaddison.service.SiblingEventParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;

/**
 * The subscriptions that let this service's directories learn anything.
 *
 * <h2>Three functions, not a widened one</h2>
 *
 * <p>This service already has {@code kafkaConsumer}, which reads {@code sse-topic} and fans strings
 * out to browsers. It was tempting to route domain events through it and it would have been wrong:
 * {@code sse-topic} is a {@code text/plain} UI fan-out channel, and the argument was already made in
 * this repository in the other direction — {@code verification-out-0} was given its own destination
 * rather than sharing that one, on the grounds that a consumer of domain events should not have to
 * filter out UI traffic and a browser should not receive domain envelopes it cannot read. The same
 * reasoning gives each inbound stream its own function here.
 *
 * <p><b>One function per topic, because a Spring Cloud Stream function has one destination.</b>
 * The three topics have different envelopes, different subject keys and different owners, and folding
 * any two together would mean sniffing the payload to decide which parser to use — a guess, where the
 * binding already knows the answer. The third arrived with backlog item 47: the two phases of the
 * professional contract are published by two applications, each to the topic it already owns, so
 * subscribing to both is the cost of that decision rather than an oversight in it.
 *
 * <h2>Groups, and why each one is written down</h2>
 *
 * <p>Both bindings name their own group in {@code application.yml}, and neither may be left to
 * inherit one. Two things make that load-bearing rather than tidy:
 *
 * <ul>
 *   <li>Without a group, Spring Cloud Stream generates an anonymous one per instance: no committed
 *       offsets, so a restarted container resumes at the end of the log and everything published
 *       while it was down is gone. The directory would then be missing exactly the accounts created
 *       during a deploy, and nothing would report it.</li>
 *   <li>The quality stack sets {@code SPRING_CLOUD_STREAM_DEFAULT_GROUP=hc-admin-service}, which
 *       applies to every binding that does not name one. Inheriting it would put these two consumers
 *       and the SSE consumer in one group on a shared broker, where they would compete for
 *       partitions and each see part of the traffic — the failure hc-professional's own config
 *       warns about at length, and it is silent.</li>
 * </ul>
 *
 * <h2>A bad message is refused by the parser; a failed write is retried and dead-lettered</h2>
 *
 * <p>These two are not the same thing and were treated as one until 2026-09-05, when this method
 * caught every {@code RuntimeException}, logged a {@code warn}, and let the offset advance. The
 * justification was the right one for a bad message and wrong for everything else:
 * {@code projection.apply} makes three Mongo writes, and <b>any {@code DataAccessException} — a
 * failover, a connection reset, memory pressure — is a {@code RuntimeException}</b>. During a Mongo
 * restart every event in that window was dropped with a {@code WARN}, the offset committed, and
 * {@code /reconcile} could not recover it either, because no link had been written to reconcile
 * against. The event was gone for good.
 *
 * <p>The split now is:
 *
 * <ul>
 *   <li><b>A message this service cannot use never reaches an exception at all.</b>
 *       {@code SiblingEventParser} answers {@code Optional.empty()} for an unreadable envelope, a
 *       missing subject key, an unreadable timestamp and a type neither producer has published, and
 *       that is where the leniency belongs — at the point the frame is being read, not wrapped around
 *       everything downstream of it.</li>
 *   <li><b>Anything that does throw is rethrown</b>, so the binder retries it and, when the retries
 *       are exhausted, dead-letters it. There is no list of exceptions to keep up to date here, and
 *       nothing is judged at the moment it is least knowable.</li>
 * </ul>
 *
 * <p>The retry is bounded and the DLQ is configured, in {@code config/application.yml}:
 * {@code maxAttempts: 5} over an exponential back-off from two seconds to thirty, so a partition
 * pauses for at most about a minute and a half rather than for ever, and {@code enableDlq: true} with
 * a {@code dlqName} of this service's own so the frames that still fail are recoverable rather than a
 * line in a log that rotates. Without a DLQ the binder default is three fast attempts and then
 * log-and-skip, which buys about two seconds of tolerance and pays permanent loss for it.
 *
 * <h2>Why this is not in {@code ..broker..}, which is where it obviously belongs</h2>
 *
 * <p>That package is documented as "Spring cloud consumers and providers" and holds
 * {@code KafkaConsumer} and {@code KafkaProducer}, so this class reads as misfiled. It is not:
 * {@code TechnicalStructureTest} declares {@code ..broker..} in no layer, so a class there may reach
 * neither {@code ..service..} nor {@code ..domain..} — and a consumer that writes to a directory has
 * to reach both.
 *
 * <p>The existing residents are not counter-examples. {@code KafkaConsumer} appears to reference
 * {@code service.dto.MessageSentEvent} and does not: it reads only that class's {@code static final
 * String} constant, which javac inlines, so no reference survives into the bytecode ArchUnit reads.
 * The package's freedom is an accident of its emptiness.
 *
 * <p><b>Moving this back is a failing build, not a style disagreement</b> — and the failure names ten
 * dependencies rather than the move. {@code SiblingEventParser} was relocated from there to
 * {@code ..service..} for the same reason.
 */
@Configuration
public class DirectoryEventConsumers {

    /**
     * The header hc-patient sets as the Kafka partition key.
     *
     * <p>Deliberately the same literal as {@code PatientEventPublisher.KEY_HEADER} in that
     * repository, and it cannot be imported: the two services share no code, only a topic. If it
     * ever changes there, the fallback to the payload's {@code subject.email} carries this consumer
     * across — the two are the same value, which is why the fallback is correct rather than merely
     * defensive.
     */
    static final String PATIENT_KEY_HEADER = "patientKey";

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryEventConsumers.class);

    private final SiblingEventParser parser;
    private final DirectoryProjectionService projection;

    public DirectoryEventConsumers(SiblingEventParser parser, DirectoryProjectionService projection) {
        this.parser = parser;
        this.projection = projection;
    }

    /**
     * {@code patient-events} — hc-patient's gateway and api, one topic, keyed on the lowercased email.
     *
     * <p>Bound as {@code patientDirectoryConsumer-in-0}. <b>The bean name is the binding name, so
     * renaming this method silently unsubscribes the service</b>: the function named in
     * {@code spring.cloud.function.definition} no longer resolves to anything, the binding has nothing
     * to attach to, and the application starts and serves as though nothing were wrong.
     *
     * <p>The guard for that is {@code DirectoryEventConsumptionIT}, which sends a frame and finds no
     * channel — it translates the binder's own {@code NullPointerException} into a message naming the
     * topic. {@code ConfigurationBindingTest} does <em>not</em> catch a rename: it compares the
     * binding name in the YAML against the function list in the same YAML, so renaming this method
     * leaves both sides agreeing with each other and disagreeing with the code. It guards the other
     * half — a binding with no destination, no group, or no entry in the function list.
     */
    @Bean
    public Consumer<Message<byte[]>> patientDirectoryConsumer() {
        return message ->
            handle("patient-events", () ->
                parser.parsePatientEvent(message.getPayload(), headerText(message, PATIENT_KEY_HEADER)).map(projection::apply)
            );
    }

    /**
     * {@code hc.professional.registration} — <b>phase 1</b>, plus onboarding state, keyed on accountId.
     *
     * <p>Bound as {@code professionalDirectoryConsumer-in-0}.
     *
     * <p>This is the gateway's topic and the gateway is what registers an account, so it is where
     * {@code AccountStatus} belongs: {@code accountId}, {@code login}, {@code email},
     * {@code activated}, {@code createdDate}, {@code modifiedDate}. Backlog item 47.
     */
    @Bean
    public Consumer<Message<byte[]>> professionalDirectoryConsumer() {
        return message ->
            handle("hc.professional.registration", () -> parser.parseProfessionalEvent(message.getPayload()).map(projection::apply));
    }

    /**
     * {@code hc.professional.entity} — <b>phase 2</b>, the profile status, keyed on the same accountId.
     *
     * <p>Bound as {@code professionalProfileConsumer-in-0}.
     *
     * <h2>Why there is a third subscription rather than a wider second one</h2>
     *
     * <p>A clinician is accepted in two phases, and the two are published by two <em>applications</em>:
     * hc-professional's gateway registers the account and its api owns the profile. The architect's
     * decision of 2026-09-07 is that each publishes to the topic it already owns — phase 1 on
     * {@code hc.professional.registration}, phase 2 on {@code hc.professional.entity} — which keeps
     * the producer boundary clean and costs this service a subscription. A Spring Cloud Stream
     * function has one destination, so a second topic is a second function; there was never an option
     * to widen the one above.
     *
     * <p><b>This entry used to say the opposite, and the reversal is the point of reading it.</b>
     * Until 2026-09-07 the class javadoc for {@code professionalDirectoryConsumer} recorded that
     * {@code hc.professional.entity} was <em>deliberately not consumed</em>: it carried
     * {@code entity.created} for entity types this service has no collection for,
     * {@code message.created}, and {@code compliance.alert}, none of which hc-admin had a surface for.
     * That reasoning was correct about the topic's contents at the time and is superseded by the
     * contract putting {@code ProfileStatus} on it, not by anybody deciding the old argument was
     * wrong.
     *
     * <h2>The filter is in the parser, before anything is written</h2>
     *
     * <p>Item 35 measured this topic at <b>82% {@code Task}</b>. {@code SiblingEventParser} refuses
     * everything that is not a profile — by type, then by {@code entityType} — so a backfill from the
     * earliest offset is a read and a discard rather than thousands of no-op writes.
     *
     * <h2>Its own group, its own dead-letter queue, its own offsets</h2>
     *
     * <p>{@code hc-admin-directory-professional-profile}, and it may not share
     * {@code hc-admin-directory-professional}: two consumers in one group split the partitions and
     * each sees part of the traffic. The cost the decision carries is that following one clinician
     * through both phases now means reading two {@code --describe} outputs and two DLQs — which is
     * easy to half-do, so {@code ConfigurationBindingTest} names the third topic, group and dlq
     * explicitly rather than sweeping for them.
     *
     * <p><b>And the function name has to reach every deployment that overrides the definition list.</b>
     * {@code SPRING_CLOUD_FUNCTION_DEFINITION} <em>replaces</em> the shipped value rather than adding
     * to it, so a function missing from {@code quality/compose.yml}'s copy is a bean that exists, a
     * binding that is configured, and no message ever delivered, with nothing logged at any level.
     * That is item 25's original defect exactly, and it is the reason this subscription is the easiest
     * one in the estate to ship broken.
     */
    @Bean
    public Consumer<Message<byte[]>> professionalProfileConsumer() {
        return message ->
            handle("hc.professional.entity", () ->
                parser.parseProfessionalProfileEvent(message.getPayload()).map(projection::applyProfileStatus)
            );
    }

    /**
     * Runs one delivery, says what it did, then — if it failed — <b>lets the failure out</b>.
     *
     * <p>The rethrow is the point of this method, not an oversight in it. See the class javadoc: a
     * frame this service cannot use has already been refused by the parser and never gets here, so
     * anything reaching this catch is a write that did not happen — and swallowing it commits the
     * offset over an event nothing can then recover. The log line exists because the binder's own
     * message names the binding rather than the topic.
     *
     * <p>The empty answer is the parser declining a frame — an unreadable envelope, no subject key, no
     * readable timestamp, or a type neither producer has published. Normal on somebody else's topic,
     * and already logged where the decision was taken.
     *
     * <p><b>The outcome is read here, and it used to be discarded.</b> {@code apply} has always
     * returned one and nothing looked at it, so the enum described behaviour rather than reporting it —
     * and "a care angel was linked and no record made" is exactly the kind of thing that has to be
     * visible in a log when somebody asks why a registration did not appear.
     *
     * <p><b>This line stays at {@code debug} and is deliberately not the answer to that question.</b>
     * It names the topic, which is what the binder's own message does not; what happened to the
     * subject is announced by {@code DirectoryProjectionService.announce}, at {@code INFO}, where the
     * outcome is decided and where the subject can be named as a digest. Two {@code INFO} lines per
     * frame, one of them without a subject on it, would be a worse log than one — and the reason the
     * production case was invisible was an absent statement, not a quiet one. Backlog item 46.
     */
    private void handle(String topic, Supplier<Optional<DirectoryProjectionService.Outcome>> body) {
        try {
            body.get().ifPresent(outcome -> LOG.debug("A frame on {} was applied — {}", topic, outcome));
        } catch (RuntimeException e) {
            LOG.warn("A frame on {} could not be applied to the directory — retrying, then dead-lettering it", topic, e);
            throw e;
        }
    }

    /** Reads a header the binder may hand over as a String or as raw bytes. */
    private String headerText(Message<byte[]> message, String name) {
        Object value = message.getHeaders().get(name);
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value == null ? null : value.toString();
    }
}
