package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.mongock.api.annotations.ChangeUnit;
import io.mongock.runner.springboot.EnableMongock;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
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

    /**
     * It stays an {@link ApplicationRunner} and does not become a Mongock change unit — backlog item 84.
     *
     * <p><b>This pins a decision, not an implementation detail.</b> Until 2026-09-11 the class javadoc
     * said it took this shape "because neither repo has Liquibase or Mongock and an
     * {@link ApplicationRunner} is the only migration seam either of them has", which was false about
     * both repos and had been since the generator's first commit — the scan package was simply empty
     * here until backlog item 56 put a change unit in it, and an empty scan target looks exactly like
     * no scan target.
     *
     * <p>Correcting the reason makes converting this class look like the obvious tidy-up, and it is
     * the wrong one. Half of what this class does is not a migration: it <b>reports</b>, on every
     * start, any stored {@code shift} value the enum can no longer parse. A change unit records itself
     * as executed and never repeats, so that report would be made once against the database as it
     * stood on the day it first ran and never again. And this has already run against production on
     * every start since it deployed, so Mongock would hold no record of those runs and its changelog
     * would assert a first execution that is not the first.
     *
     * <p>So the case is the absence of an annotation, deliberately — the same shape as the patient
     * screen asserting the Create button it must not regrow.
     */
    @Test
    void isARunnerAndNotAChangeUnit() {
        assertThat(ApplicationRunner.class).isAssignableFrom(ShiftTypeMigration.class);
        assertThat(ShiftTypeMigration.class.getAnnotation(ChangeUnit.class)).isNull();
    }

    /**
     * The Mongock seam this service really has, asserted so that the corrected claim cannot go stale
     * the way the false one did.
     *
     * <p>The old comment's "neither repo has Mongock" survived for a week because nothing anywhere
     * read the configuration that contradicts it. Both halves are checked here: the annotation that
     * switches Mongock on, and the scan package that gives it somewhere to look. Either being removed
     * would silently strand item 56's change unit — a class that is never discovered, never executed
     * and never reported, which is the failure mode of every migration in this repository's history.
     *
     * <p>The scan package is read from the shipped YAML as text rather than from a bound context: the
     * point is what {@code src/main/resources/config/application.yml} says, and a property resolved
     * through a test context is a property some test profile could be overriding.
     */
    @Test
    void mongockIsConfiguredAndPointedAtAPackageThatExists() throws IOException {
        assertThat(DatabaseConfiguration.class.getAnnotation(EnableMongock.class))
            .as("@EnableMongock on DatabaseConfiguration")
            .isNotNull();

        String applicationYml = Files.readString(Path.of("src/main/resources/config/application.yml"));
        assertThat(applicationYml).contains("migration-scan-package");
        assertThat(applicationYml).contains("net.jojoaddison.config.dbmigrations");
        assertThat(Path.of("src/main/java/net/jojoaddison/config/dbmigrations")).exists();
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
