package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
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
