package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * {@link ShiftTypeMigration} registers on the profiles it has to run on, and not on the ones a build
 * uses.
 *
 * <p><b>This test exists because a migration that cannot run on a deployed profile is invisible from
 * every green build.</b> The annotation read {@code !test} until 2026-09-04 — an expression that
 * excluded nothing from {@code ./mvnw verify}, because {@code pom.xml} activates {@code testdev} or
 * {@code testprod} for an integration-test run and never {@code test}, and that did exclude the one
 * deployment the migration was written for: hc-admin's quality stack runs {@code dev,test}, so twelve
 * {@code wage_rate} rows kept a null {@code shift_type} through every restart and the wage-rates and
 * earnings screens answered 500 until the stack was reseeded. Nothing failed anywhere.
 *
 * <p>So the assertions are a pair and both halves matter. {@code dev,test} is the deployment; the two
 * {@code test*} profiles are the build. Restoring {@code !test} fails the first case and not the
 * second, which is the shape of the original defect.
 *
 * <p>It is a plain context rather than a {@code @SpringBootTest}, because booting the application
 * under four profile sets to read one bean name would cost four contexts to answer a question about
 * an annotation. The {@link MongoTemplate} is a mock for the same reason — the constructor needs one
 * and nothing here calls it.
 */
class ShiftTypeMigrationProfileTest {

    /**
     * The quality stack, and any {@code dev,test} deployment. <b>This is the case that was broken.</b>
     */
    @Test
    void registersOnADevTestDeployment() {
        assertThat(registersUnder("dev", "test")).isTrue();
    }

    @Test
    void registersOnDevAndOnProd() {
        assertThat(registersUnder("dev")).isTrue();
        assertThat(registersUnder("prod")).isTrue();
    }

    /**
     * The two profiles an integration-test run actually activates, from {@code profile.test} in
     * {@code pom.xml}.
     *
     * <p>Excluding them is belt-and-braces rather than the reason the annotation exists: Spring Boot
     * does not invoke {@link org.springframework.boot.ApplicationRunner} beans under
     * {@code @SpringBootTest} at all, so the migration could not have fired against an IT's fixtures
     * even when it registered in every one of their contexts — which, under {@code !test}, it did.
     */
    @Test
    void doesNotRegisterUnderTheProfilesAnIntegrationTestRunActivates() {
        assertThat(registersUnder("testdev")).isFalse();
        assertThat(registersUnder("testprod")).isFalse();
    }

    private boolean registersUnder(String... profiles) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles(profiles);
            context.registerBean(MongoTemplate.class, () -> mock(MongoTemplate.class));
            context.register(ShiftTypeMigration.class);
            context.refresh();
            return context.getBeanNamesForType(ShiftTypeMigration.class).length == 1;
        }
    }
}
