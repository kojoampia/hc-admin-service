package net.jojoaddison.config;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.jojoaddison.IntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * Nothing here starts a Kafka broker, and the mechanism that would still works.
 *
 * <p>Two assertions, and they guard opposite mistakes. Backlog item 17.
 *
 * <h2>Nothing opts in</h2>
 *
 * <p>{@code @EmbeddedKafka} sat on {@code @IntegrationTest} from the generator's first commit and
 * started a {@code confluentinc/cp-kafka} container for all seventy-two integration tests, while the
 * only three classes that drive a binding use {@link
 * org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration} and need no broker at
 * all. Putting it back on any class — directly or through a composite — is a decision about what this
 * suite is for, not a tidy-up, so the rule fails and says so rather than the cost reappearing on a
 * green build. Stated on the meta-annotation because that is exactly how it hid the first time.
 *
 * <h2>The opt-in still resolves</h2>
 *
 * <p>A rule that forbids the annotation would go on passing if the wiring behind it quietly died, and
 * whoever next needs a real broker would find a fixture that silently gives them none. So the
 * resolution itself is asserted, including through a composite annotation, which is the case
 * {@code findMergedAnnotation} handles and a reader does not expect. This does not start a container
 * and deliberately does not: exercising the container end to end means paying for it on every run,
 * which is the cost this item removed. That gap is real and is named here rather than left implied —
 * the end-to-end path was verified by hand once, on 2026-09-09, by restoring {@code @EmbeddedKafka} to
 * one class and watching the container start for that class and no other. <b>That proved the container
 * starts, not that anything used it</b> — on an {@code @IntegrationTest} class nothing does, and the
 * difference is the subject of {@code IntegrationTest}'s javadoc.
 */
@AnalyzeClasses(packagesOf = IntegrationTest.class)
class BrokerOptInArchTest {

    // prettier-ignore
    @ArchTest
    static final ArchRule nothingAsksForARealBroker = noClasses()
        .that()
        // The fixtures below are the second assertion's subject and are meant to carry it.
        .doNotBelongToAnyOf(BrokerOptInArchTest.class)
        .should()
        .beMetaAnnotatedWith(EmbeddedKafka.class)
        .because(
            "@EmbeddedKafka starts a Kafka container for the whole test JVM and no test in this repository "
            + "asserts anything about a real broker — the three that drive a binding use the in-memory "
            + "TestChannelBinderConfiguration. If you genuinely need one, say why here and accept that every "
            + "integration test in the run pays for it (backlog item 17). And note that adding this annotation "
            + "to an @IntegrationTest class is NOT enough to reach a real broker: that composite imports the "
            + "in-memory binder, which goes on servicing every binding while the container sits unused. Read "
            + "IntegrationTest's javadoc before assuming a passing test proved anything about Kafka"
        );

    @Test
    void theOptInStillResolvesDirectlyAndThroughAComposite() {
        assertThat(KafkaTestContainersSpringContextCustomizerFactory.wantsBroker(AsksDirectly.class)).isTrue();
        assertThat(KafkaTestContainersSpringContextCustomizerFactory.wantsBroker(AsksThroughAComposite.class)).isTrue();
        assertThat(KafkaTestContainersSpringContextCustomizerFactory.wantsBroker(AsksForNothing.class)).isFalse();
    }

    @Test
    void theIntegrationTestCompositeDoesNotAskForOne() {
        assertThat(KafkaTestContainersSpringContextCustomizerFactory.wantsBroker(AnOrdinaryIntegrationTest.class)).isFalse();
    }

    @EmbeddedKafka
    private static final class AsksDirectly {}

    @ComposedWithEmbeddedKafka
    private static final class AsksThroughAComposite {}

    private static final class AsksForNothing {}

    @IntegrationTest
    private static final class AnOrdinaryIntegrationTest {}

    /**
     * Stands in for {@code @IntegrationTest} as it was until 2026-09-09: a composite that carries
     * {@code @EmbeddedKafka} without saying so at the point of use.
     */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @EmbeddedKafka
    private @interface ComposedWithEmbeddedKafka {}
}
