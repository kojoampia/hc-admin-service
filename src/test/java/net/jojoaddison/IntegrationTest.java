package net.jojoaddison;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.jojoaddison.config.AsyncSyncConfiguration;
import net.jojoaddison.config.EmbeddedMongo;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Base composite annotation for integration tests.
 *
 * <h2>Why there is no {@code @EmbeddedKafka} here</h2>
 *
 * <p>There was, from the generator's first commit, and it started a {@code confluentinc/cp-kafka}
 * container for <b>every</b> integration test in this repository — seventy-two of them —
 * because {@code KafkaTestContainersSpringContextCustomizerFactory} resolves the annotation with
 * {@code findMergedAnnotation}, which sees meta-annotations. Nothing said so; the customizer logs
 * "Warming up the kafka broker" on each context and reads as a per-class decision.
 *
 * <p><b>Not one test in this repository needed it.</b> The three classes that drive a binding —
 * {@code DirectoryEventConsumptionIT}, {@code HcAdminServiceKafkaResourceIT} and
 * {@code VerificationEventIT} — each import {@link TestChannelBinderConfiguration} and say in their
 * own javadoc that they exercise the binding names and payloads without a broker, because Kafka's
 * delivery is not this service's to test. The other sixty-nine bound five consumer groups against a
 * real broker per context and asserted nothing about any of them. Measured on a baseline run on
 * 2026-09-09: the container took 11.4 seconds to start and every context paid for its bindings on
 * top, for no assertion anywhere.
 *
 * <p>So the binder every integration test gets is the in-memory one, declared here rather than
 * repeated on the three classes that already had it. Bindings, destinations, groups and payload
 * conversion are all still exercised — {@code ConfigurationBindingTest} covers the half this cannot,
 * the topic names in the file that ships — and nothing starts a broker. See docs/backlog.md item 17.
 *
 * <p>{@code @EmbeddedKafka} itself still works and still starts a container for a class that asks for
 * it; {@code BrokerOptInArchTest} asserts both that the mechanism is intact and that nothing currently
 * uses it, so reintroducing the cost is a decision somebody has to take deliberately.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(classes = { HcAdminServiceApp.class, AsyncSyncConfiguration.class })
@EmbeddedMongo
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ImportAutoConfiguration(TestChannelBinderConfiguration.class)
public @interface IntegrationTest {
}
