package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;
import tech.jhipster.config.JHipsterProperties;

/**
 * Binds the shipped configuration files to the classes that read them.
 *
 * <h2>Why this exists</h2>
 *
 * <p>A bare {@code security:} key sat under {@code jhipster:} in {@code config/application.yml}
 * from the original 2024 generation, with nothing beneath it. Spring Boot 4.0.6 ignored it. Boot
 * 4.1 binds it as the empty String and fails, because {@code JHipsterProperties.security} is an
 * object:
 *
 * <pre>
 *   Failed to bind properties under 'jhipster' to tech.jhipster.config.JHipsterProperties:
 *     Property: jhipster.security
 *     Value: ""
 *     Reason: No setter found for property: security
 * </pre>
 *
 * <p>It took the service down on the first production deploy after that upgrade, and <em>nothing in
 * this repository could have caught it</em>. The entire suite runs under the {@code test} profile
 * against {@code src/test/resources/config/application.yml}; the file that ships inside the jar is
 * only ever read by a running application. `./mvnw verify` was green throughout.
 *
 * <p>So this test reads the real files off the classpath and binds them, without starting a context
 * — no Mongo, no Consul, no Kafka. It is the cheapest thing that would have failed.
 */
class ConfigurationBindingTest {

    /**
     * The one binding exempt from the destination rule, and it is exempt because it is not ours.
     *
     * <p>{@code kafkaProducer-out-0} is JHipster's generated demo {@code Supplier<String>} — it
     * returns the literal {@code "kakfa_producer"} (the typo is the generator's) once a second, and
     * nothing consumes it. It has no destination, so it publishes into a topic named after itself,
     * which is the very shape this test exists to catch. Giving it one would be inventing a topic
     * for a constant; deleting the supplier is the real answer and is a decision about generated
     * scaffolding rather than about configuration, so it is named here rather than quietly matched.
     *
     * <p><b>If it is ever deleted, delete this line with it</b> — an exemption for something that no
     * longer exists is how an exemption list starts growing.
     */
    private static final String GENERATED_DEMO_SUPPLIER = "kafkaProducer-out-0";

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    /**
     * Every profile's file, including the ones no test ever activates. {@code prod} is the whole
     * point: it is the one that runs in production and the one no test profile touches.
     */
    @ParameterizedTest
    @ValueSource(strings = { "config/application.yml", "config/application-dev.yml", "config/application-prod.yml" })
    void bindsToJHipsterProperties(String resource) throws IOException {
        Binder binder = binderFor(resource);

        assertThatCode(() -> binder.bind("jhipster", JHipsterProperties.class))
            .as("%s must bind to JHipsterProperties — an empty key whose target is an object fails here", resource)
            .doesNotThrowAnyException();
    }

    /**
     * The specific shape that caused the outage: a key present but empty, whose target is not a
     * String. Asserted directly so a regression names the cause rather than a binder stack trace.
     */
    @Test
    void jhipsterSecurityIsNeverAnEmptyValue() throws IOException {
        for (String resource : List.of("config/application.yml", "config/application-dev.yml", "config/application-prod.yml")) {
            Binder binder = binderFor(resource);
            binder
                .bind("jhipster.security", String.class)
                .ifBound(value -> {
                    throw new AssertionError(
                        resource +
                        " binds jhipster.security to the String \"" +
                        value +
                        "\". It is an object: an empty `security:` key here fails the whole application at startup."
                    );
                });
        }
    }

    /**
     * <b>Every outbound Spring Cloud Stream binding in the shipped config declares a destination.</b>
     *
     * <p>Without one, Spring publishes to a topic named after the binding. The send succeeds, the
     * topic is created, and no consumer is subscribed to it — the application starts, serves, and
     * reports healthy while everything it produces goes nowhere. {@code binding-out-0} did exactly
     * that in this service: every message the desk announced went into a topic called
     * "binding-out-0" while {@code kafkaConsumer-in-0} read {@code sse-topic}, and nothing at any
     * log level said so.
     *
     * <p><b>Discovered from the file, not enumerated here.</b> A list of binding names would have to
     * be extended by hand for each new one, and a guard whose coverage is maintained by hand stops
     * covering things — the lesson {@code PaginationIT} records after eight endpoints slipped past
     * a hard-coded list. Any {@code *-out-*} binding added later is covered the moment it is added.
     *
     * <p>It cannot be asserted from a running test context: every test runs under the {@code test}
     * profile, whose {@code application.yml} declares its own bindings and overrides these. This
     * file is the one that ships inside the jar.
     */
    @Test
    void everyOutboundBindingDeclaresADestination() throws IOException {
        // Read as flattened property names rather than bound into a nested Map: the keys arrive as
        // spring.cloud.stream.bindings.<binding>.<setting>, which is all this needs and avoids a
        // Bindable of a Map of Maps.
        Map<String, Object> properties = propertiesOf("config/application.yml");
        String prefix = "spring.cloud.stream.bindings.";

        List<String> outbound = properties
            .keySet()
            .stream()
            .filter(key -> key.startsWith(prefix))
            .map(key -> key.substring(prefix.length()).split("\\.")[0])
            .filter(name -> name.contains("-out-"))
            .filter(name -> !GENERATED_DEMO_SUPPLIER.equals(name))
            .distinct()
            .sorted()
            .toList();

        assertThat(outbound).as("the shipped config should declare outbound bindings").isNotEmpty();

        for (String name : outbound) {
            assertThat(properties.get(prefix + name + ".destination"))
                .as(
                    "binding %s declares no destination — it publishes to a topic called \"%s\" that nothing subscribes to, silently",
                    name,
                    name
                )
                .isNotNull();
        }
    }

    /**
     * <b>Every inbound binding declares a destination, a group, and a function that exists.</b>
     *
     * <p>The mirror of the rule above, and it guards a worse failure. A missing {@code destination}
     * on a consumer subscribes it to a topic named after the binding — which nothing publishes to,
     * so the service reads an empty stream for ever. A missing {@code group} is quieter still:
     * Spring Cloud Stream generates an anonymous one per instance, which commits no offsets, so a
     * restarted container resumes at the end of the log and everything published while it was down
     * is gone. Neither logs anything at any level, and both look exactly like "the other stack is
     * not publishing".
     *
     * <p>The third assertion is the one that would have caught the defect this whole subscription was
     * added for. A consumer function absent from {@code spring.cloud.function.definition} is not
     * bound at all: the bean exists, the binding is configured, and no message is ever delivered to
     * it. Renaming the bean method is enough to do it, and nothing fails.
     *
     * <p>Discovered from the file rather than enumerated, for the reason the outbound sweep gives.
     */
    @Test
    void everyInboundBindingIsSubscribedProperly() throws IOException {
        Map<String, Object> properties = propertiesOf("config/application.yml");
        String prefix = "spring.cloud.stream.bindings.";
        String definition = String.valueOf(properties.get("spring.cloud.function.definition"));

        List<String> inbound = properties
            .keySet()
            .stream()
            .filter(key -> key.startsWith(prefix))
            .map(key -> key.substring(prefix.length()).split("\\.")[0])
            .filter(name -> name.contains("-in-"))
            .distinct()
            .sorted()
            .toList();

        assertThat(inbound).as("the shipped config should declare inbound bindings").isNotEmpty();

        for (String name : inbound) {
            assertThat(properties.get(prefix + name + ".destination"))
                .as("binding %s declares no destination — it subscribes to a topic called \"%s\" that nobody publishes to", name, name)
                .isNotNull();
            assertThat(properties.get(prefix + name + ".group"))
                .as(
                    "binding %s declares no group — it commits no offsets, so a restart silently skips everything published meanwhile",
                    name
                )
                .isNotNull();

            // Split on the separator rather than asking whether the string contains the name.
            // `.contains("patientDirectoryConsumer")` is satisfied by `patientDirectoryConsumerXYZ`,
            // which is a different bean and binds nothing — the exact failure this assertion is for.
            String function = name.substring(0, name.indexOf("-in-"));
            assertThat(Arrays.stream(definition.split(";")).map(String::trim).toList())
                .as(
                    "%s is not in spring.cloud.function.definition (%s), so nothing is bound to it and no message is ever delivered",
                    function,
                    definition
                )
                .contains(function);
        }
    }

    /**
     * The two sibling topics, by name, and the groups that read them.
     *
     * <p>Named literally here rather than discovered, which is the opposite of the rule the sweeps
     * follow, and deliberately: a topic name is a <b>contract with another product</b>, not a local
     * convention. {@code patient-events} is hc-patient's, {@code hc.professional.registration} is
     * hc-professional's, and neither repository imports anything from this one — the only thing
     * holding the two ends together is that these strings match. A typo here is a service that
     * starts, reports healthy, creates its own empty topic and learns nothing, which is precisely
     * the state this subscription was added to end.
     *
     * <p>The groups are asserted distinct because two consumers sharing one compete for partitions
     * and each sees part of the traffic — the failure hc-professional's own configuration warns
     * about at length, and it presents as a feature that works about half the time.
     */
    @Test
    void theDirectorySubscriptionsNameTheSiblingTopics() throws IOException {
        Map<String, Object> properties = propertiesOf("config/application.yml");
        String prefix = "spring.cloud.stream.bindings.";

        assertThat(properties.get(prefix + "patientDirectoryConsumer-in-0.destination"))
            .as("hc-patient publishes the patient journey to patient-events; nothing else reaches this service")
            .isEqualTo("patient-events");
        assertThat(properties.get(prefix + "professionalDirectoryConsumer-in-0.destination"))
            .as("hc-professional publishes registration.created and onboarding.state to hc.professional.registration")
            .isEqualTo("hc.professional.registration");

        Object patientGroup = properties.get(prefix + "patientDirectoryConsumer-in-0.group");
        Object professionalGroup = properties.get(prefix + "professionalDirectoryConsumer-in-0.group");
        assertThat(patientGroup).as("the patient subscription needs a durable group of its own").isNotNull();
        assertThat(professionalGroup).as("the professional subscription needs a durable group of its own").isNotNull();
        assertThat(patientGroup)
            .as("two subscriptions in one group split the partitions and each sees half")
            .isNotEqualTo(professionalGroup);
        assertThat(patientGroup)
            .as("the directory consumers must not share the SSE consumer's group either")
            .isNotEqualTo(properties.get(prefix + "kafkaConsumer-in-0.group"));
    }

    /**
     * Both directory subscriptions read the topic from the beginning the first time, and only then.
     *
     * <p>{@code startOffset: earliest} is the backfill: without it a brand-new consumer group starts
     * at the end of the log and every account registered before this shipped stays invisible, which
     * is exactly the state being fixed. {@code resetOffsets: false} is what keeps it a first-run
     * behaviour rather than a per-boot replay.
     */
    @Test
    void theDirectorySubscriptionsStartAtTheBeginningOfTheTopic() throws IOException {
        Map<String, Object> properties = propertiesOf("config/application.yml");
        String prefix = "spring.cloud.stream.kafka.bindings.";

        for (String binding : List.of("patientDirectoryConsumer-in-0", "professionalDirectoryConsumer-in-0")) {
            // The literal key as written in the file: this reads raw property names off the YAML
            // loader, where relaxed binding has not happened yet and `start-offset` would not match.
            assertThat(properties.get(prefix + binding + ".consumer.startOffset"))
                .as("%s does not start at the earliest offset, so it never sees what was published before it existed", binding)
                .isEqualTo("earliest");
            assertThat(properties.get(prefix + binding + ".consumer.resetOffsets"))
                .as("%s would replay the whole topic on every restart", binding)
                .isEqualTo(false);
        }
    }

    /**
     * A write that fails is retried and then dead-lettered, rather than logged and lost.
     *
     * <p>{@code DirectoryEventConsumers} rethrows anything that fails downstream of the parser, which
     * is only ever a Mongo write — a bad message is refused inside the parser without an exception.
     * These settings are what that rethrow turns into: without them the binder makes three attempts
     * about a second apart and then logs and skips, which is shorter than any database restart, and
     * every event in the window is lost with the offset committed behind it. {@code /reconcile}
     * cannot recover those, because no link was written to reconcile against.
     *
     * <p>Asserted here rather than in an integration test because the test profile deliberately sets
     * {@code maxAttempts: 1} — a consumer that throws has to fail a test immediately rather than
     * after a minute of real back-off — so nothing that boots a context can see these values.
     */
    @Test
    void theDirectorySubscriptionsRetryAndThenDeadLetter() throws IOException {
        Map<String, Object> properties = propertiesOf("config/application.yml");

        for (String binding : List.of("patientDirectoryConsumer-in-0", "professionalDirectoryConsumer-in-0")) {
            Object attempts = properties.get("spring.cloud.stream.bindings." + binding + ".consumer.maxAttempts");
            assertThat(attempts)
                .as("%s leaves maxAttempts at the binder default of 3 — about two seconds, shorter than any restart", binding)
                .isNotNull();
            assertThat(Integer.parseInt(String.valueOf(attempts)))
                .as("%s must retry more than the default before giving up on a database that is coming back", binding)
                .isGreaterThan(3);

            assertThat(properties.get("spring.cloud.stream.kafka.bindings." + binding + ".consumer.enableDlq"))
                .as("%s has no dead-letter queue, so an event that cannot be written is lost for ever", binding)
                .isEqualTo(true);
            assertThat(String.valueOf(properties.get("spring.cloud.stream.kafka.bindings." + binding + ".consumer.dlqName")))
                .as("%s dead-letters onto a topic that does not name this service — these topics are shared", binding)
                .contains("hc-admin");
        }
    }

    /**
     * Read from {@code src/main/resources} on disk, NOT from the classpath.
     *
     * <p>This is the difference between a guard and a decoration. Under surefire,
     * {@code src/test/resources} precedes {@code src/main/resources}, so
     * {@code new ClassPathResource("config/application.yml")} resolves to the <em>test</em> file —
     * and an earlier version of this test did exactly that. It bound the test config, passed
     * happily, and would never have seen the shipped file that took production down. The test
     * config is already exercised by every other test in the suite; this one exists solely for the
     * file that is not.
     */
    /** Every property in a shipped file, flattened — {@code a.b.c} style, as Spring sees them. */
    private Map<String, Object> propertiesOf(String resource) throws IOException {
        FileSystemResource file = new FileSystemResource("src/main/resources/" + resource);
        Map<String, Object> flattened = new LinkedHashMap<>();
        for (PropertySource<?> source : loader.load(resource, file)) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    flattened.put(name, enumerable.getProperty(name));
                }
            }
        }
        return flattened;
    }

    private Binder binderFor(String resource) throws IOException {
        FileSystemResource file = new FileSystemResource("src/main/resources/" + resource);
        assertThat(file.exists()).as("%s should exist under src/main/resources", resource).isTrue();

        List<PropertySource<?>> sources = loader.load(resource, file);
        assertThat(sources).as("%s should parse", resource).isNotEmpty();

        StandardEnvironment environment = new StandardEnvironment();
        for (PropertySource<?> source : sources) {
            environment.getPropertySources().addFirst(source);
        }
        return Binder.get(environment);
    }
}
