package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Every default under {@code application.*} is written in exactly one place — backlog item 58.
 *
 * <h2>The defect</h2>
 *
 * <p>{@link ApplicationProperties} declared nested {@code Professionalservice}, {@code Patientservice}
 * and {@code AbofonsaContent} classes with defaults on every field, and <b>nothing called their
 * getters</b>. The three clients read the same thirteen keys through inline
 * {@code @Value("${application.…:default}")} placeholders instead, so each default was written twice:
 * once where it was read and once where it was not. Editing the properties class changed nothing at
 * runtime, no test failed, and the shape had already misled one reader into writing the opposite into
 * {@code quality/compose.yml}. Measured on {@code main} before the fix: all thirteen keys carried two
 * copies, and — by luck rather than by anything — the two copies still agreed, which is why the fix
 * could not change a value in force.
 *
 * <h2>The rule this file enforces</h2>
 *
 * <p>A reader injects {@link ApplicationProperties} and the default is the field initializer. The one
 * exception is {@code @Scheduled}, whose {@code initialDelayString} and {@code fixedDelayString} are
 * annotation attributes: the language requires a compile-time constant expression there, so a
 * placeholder or SpEL are the only things they can hold and injection has no path into them. (Not a
 * lifecycle argument — this file claimed one until the review, and it was false; see
 * {@code ApplicationProperties.AbofonsaContent.initialDelayMs}.) For
 * those two keys the placeholder carries the default and the field deliberately carries none. The
 * exception is not a list of names here: {@link #everyApplicationDefaultIsWrittenInExactlyOnePlace()}
 * counts copies per key, so a third reader in the same shape is covered on the commit that creates it
 * and a fourth {@code @Value} is refused outright by
 * {@link #noClassReadsAnApplicationKeyThroughValue()}.
 *
 * <h2>Why this reads annotations rather than source text</h2>
 *
 * <p>{@code LogPseudonymTest} sweeps {@code .java} files because the property it guards is about
 * statements, and it records at length how easily a formatter silently removes such a sweep's reach —
 * item 57's discriminator was defeated by a Prettier line break. Nothing here needs source text:
 * {@code @Value} and {@code @Scheduled} are both {@code RUNTIME}-retained, so the placeholders can be
 * read off the compiled classes. A line break cannot change them, and neither can a javadoc quoting
 * the historical {@code @Value} — which several of the changed files now do, and which a source sweep
 * would have counted as a live second copy.
 *
 * <h2>The two blind spots, named rather than left implied</h2>
 *
 * <p><b>It reads annotations and never method bodies</b>, so it enforces the {@code @Value} /
 * {@code @Scheduled} half of the rule and not the whole of it. Three shapes would write a second
 * default with the suite green, and are held by review alone:
 * {@code environment.getProperty("application.x.y", "default")}; a second
 * {@code @ConfigurationProperties} class on an {@code application.x} subprefix carrying its own field
 * defaults; and {@code @ConditionalOnProperty(prefix = "application.x", name = "enabled",
 * matchIfMissing = true)}, whose default is a <em>boolean</em> attribute that
 * {@link #placeholderSites()} skips because it collects only Strings. None exists today. They are
 * accepted limits rather than oversights — but a guard that names one of its limits and not the
 * other reads as though it has only one, which is the failure this whole file is about.
 *
 * <p><b>"Does this field carry a default"</b> is answered by comparing a fresh instance's value
 * against the
 * JVM default, so a field whose <em>intended</em> default is {@code false}, {@code 0} or {@code null}
 * reads as carrying none and would be required to have a placeholder default instead. None of the
 * thirteen keys is in that position today — the three {@code enabled} flags default to {@code true} —
 * and {@link #onlyTheTwoScheduleKeysCarryNoDefaultInThePropertiesClass()} names the two that
 * deliberately hold nothing, so a third joining them is a visible change rather than a quiet one.
 */
class ApplicationPropertiesSingleSourceTest {

    /**
     * A {@code ${application.<key>}} placeholder, with the default it carries if it has one.
     *
     * <p>Anchored on {@code ${application.} rather than on {@code application.}, which is not
     * pedantry: {@code LoggingConfiguration} reads {@code ${spring.application.name}}, and a pattern
     * matching that would make this rule complain about a key that is Spring's and not ours.
     * {@link #theSweepReadsPlaceholdersAndTellsOursFromSprings()} pins both halves.
     */
    private static final Pattern APPLICATION_PLACEHOLDER = Pattern.compile("\\$\\{(application\\.[A-Za-z0-9.\\-]+)(?::([^}]*))?}");

    /** Any placeholder at all — used only to prove the sweep is reading annotation values. */
    private static final Pattern ANY_PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9.\\-]+)(?::([^}]*))?}");

    @Configuration
    @EnableConfigurationProperties(ApplicationProperties.class)
    static class BindOnly {}

    // ---------------------------------------------------------------------------------------------
    // The premise: what ignoreUnknownFields = false actually polices.
    //
    // Item 58 offered "delete the nested classes" as an equally good answer, and the reasoning that
    // ruled it out was that quality/compose.yml sets APPLICATION_PATIENTSERVICE_BASE_URL. That
    // reasoning is wrong, and the two tests below are here because it was believed and written down.
    // What really rules the option out is the third one: this repository's own test config sets three
    // of these keys from a FILE, so deleting the declarations fails every context-booting test here.
    // ---------------------------------------------------------------------------------------------

    /**
     * A key no field declares, arriving from a configuration file, fails the context.
     *
     * <p>This is the fact {@link ApplicationProperties}'s javadoc has recorded in prose since item 50
     * — found by adding {@code application.abofonsa-content.enabled} to the test config and watching
     * 1033 integration tests error with "were left unbound" — and it had never been a test. It is the
     * load-bearing half of the premise, so it is measured here rather than remembered.
     */
    @Test
    void aKeyNoFieldDeclaresFailsTheContextWhenItComesFromAConfigurationFile() {
        new ApplicationContextRunner()
            .withUserConfiguration(BindOnly.class)
            .withPropertyValues("application.zzz-not-declared.enabled=true")
            .run(context ->
                assertThat(context)
                    .as(
                        "ignoreUnknownFields = false must reject an application.* key that no field declares. " +
                            "If this ever passes, the nested classes in ApplicationProperties stop being mandatory " +
                            "and item 58's rejected 'delete it' option becomes available again — which is a decision, " +
                            "not a refactor."
                    )
                    .hasFailed()
            );
    }

    /**
     * The same key arriving from the environment does <b>not</b> fail the context, and that is the
     * half everybody gets wrong.
     *
     * <p>Spring Boot excludes {@code SystemEnvironmentPropertySource} from the unbound-elements check,
     * because the environment is a namespace nobody owns. So the two {@code APPLICATION_*_BASE_URL}
     * variables in {@code quality/compose.yml} would <em>not</em> have stopped that stack booting
     * against a class declaring nothing — which is what the argument for keeping these declarations
     * rested on. hc-professional is the running proof of it: their {@code ApplicationProperties}
     * declares no {@code patientservice} at all while their quality compose sets exactly that
     * variable, and that stack runs.
     *
     * <p>Recorded as a test because it is a fact about a framework that no amount of reading this
     * repository reveals, and because it has now been reasoned wrongly twice.
     */
    @Test
    void aKeyNoFieldDeclaresFromTheEnvironmentDoesNotFailTheContext() {
        new ApplicationContextRunner()
            .withUserConfiguration(BindOnly.class)
            .withInitializer(context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(
                        new SystemEnvironmentPropertySource("systemEnvironment", Map.of("APPLICATION_ZZZNOTDECLARED_ENABLED", "true"))
                    )
            )
            .run(context ->
                assertThat(context)
                    .as("an undeclared key from the environment is ignored, not rejected — see this method's javadoc")
                    .hasNotFailed()
            );
    }

    /**
     * The test config really does set three of these keys from a file, which is what makes the
     * declarations mandatory.
     *
     * <p>Asserted against the file rather than described, because this is the whole reason "delete the
     * nested classes" is not an option and the reason recorded in the backlog was a different one.
     */
    @Test
    void theTestConfigSetsThreeOfTheseKeysFromAFile() throws IOException {
        String testConfig = Files.readString(Path.of("src/test/resources/config/application.yml"));

        assertThat(testConfig)
            .as("the three sibling clients are switched off for the whole suite here; a key set in a FILE is bind-checked")
            .contains("professionalservice:")
            .contains("abofonsa-content:")
            .contains("patientservice:");
    }

    /**
     * The quality stack's override still reaches the client after the shape change.
     *
     * <p>{@code quality/compose.yml} sets {@code APPLICATION_PATIENTSERVICE_BASE_URL} and
     * {@code APPLICATION_PROFESSIONALSERVICE_BASE_URL}, and that used to be resolved by a
     * {@code @Value} placeholder. It is now relaxed binding onto a field, which is a different
     * mechanism reading the same variable — so it is proven rather than assumed. Getting this wrong
     * would point both clients at the production container names on the quality box: a stack that
     * comes up, looks healthy, and reports the far service unreachable.
     */
    @Test
    void theQualityStacksEnvironmentOverrideStillReachesTheProperties() {
        new ApplicationContextRunner()
            .withUserConfiguration(BindOnly.class)
            .withInitializer(context ->
                context
                    .getEnvironment()
                    .getPropertySources()
                    .addFirst(
                        new SystemEnvironmentPropertySource(
                            "systemEnvironment",
                            Map.of(
                                "APPLICATION_PATIENTSERVICE_BASE_URL",
                                "http://hc-patient-quality-service:8081",
                                "APPLICATION_PROFESSIONALSERVICE_BASE_URL",
                                "http://hc-professional-quality-service:8081"
                            )
                        )
                    )
            )
            .run(context -> {
                ApplicationProperties properties = context.getBean(ApplicationProperties.class);
                assertThat(properties.getPatientservice().getBaseUrl()).isEqualTo("http://hc-patient-quality-service:8081");
                assertThat(properties.getProfessionalservice().getBaseUrl()).isEqualTo("http://hc-professional-quality-service:8081");
            });
    }

    // ---------------------------------------------------------------------------------------------
    // The rule itself.
    // ---------------------------------------------------------------------------------------------

    /**
     * <b>No default is written twice.</b> For every key {@link ApplicationProperties} declares, the
     * number of places its default is written is exactly one: either the field initializer, or a
     * placeholder default on an annotation, never both and never neither.
     *
     * <p>This is the assertion that goes red if somebody restores an inline
     * {@code @Value("${application.…:default}")} beside a field that already carries the value, which
     * is the state item 58 closed and the state a new sibling client is most likely to arrive in.
     */
    @Test
    void everyApplicationDefaultIsWrittenInExactlyOnePlace() {
        Map<String, Boolean> declared = declaredKeysAndWhetherTheFieldCarriesADefault();
        Map<String, Set<String>> placeholders = applicationPlaceholderDefaults();

        assertThat(declared).as("ApplicationProperties should declare some keys, or this whole file is vacuous").isNotEmpty();

        List<String> written = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : declared.entrySet()) {
            String key = entry.getKey();
            int places = (entry.getValue() ? 1 : 0) + placeholders.getOrDefault(key, Set.of()).size();
            if (places != 1) {
                written.add(
                    key +
                        " has its default written in " +
                        places +
                        " place(s) — field initializer: " +
                        entry.getValue() +
                        ", placeholder defaults: " +
                        placeholders.getOrDefault(key, Set.of())
                );
            }
        }

        assertThat(written)
            .as(
                "Every application.* default must be written exactly once (backlog item 58). Two copies drift " +
                    "silently: editing the properties class changes nothing at runtime and nothing fails. Zero copies " +
                    "means the value is a JVM default nobody chose. Fix by deleting the placeholder default and " +
                    "injecting ApplicationProperties, which is what every reader but @Scheduled does."
            )
            .isEmpty();
    }

    /**
     * Nothing reads an {@code application.*} key through {@code @Value}.
     *
     * <p>Narrower than the rule above and stated on the mechanism rather than on an outcome, so that a
     * {@code @Value} carrying <em>no</em> default — which the counting rule above would happily accept
     * — is still refused. Two ways into the same setting is how the thirteen defaults came to be
     * written twice in the first place, and a placeholder with no default is one edit away from
     * carrying one.
     */
    @Test
    void noClassReadsAnApplicationKeyThroughValue() {
        List<String> offenders = new ArrayList<>();
        for (PlaceholderSite site : placeholderSites()) {
            if (!site.annotation().equals("Value")) {
                continue;
            }
            Matcher matcher = APPLICATION_PLACEHOLDER.matcher(site.text());
            while (matcher.find()) {
                offenders.add(site.owner() + " reads " + matcher.group(1) + " through @Value");
            }
        }

        assertThat(offenders)
            .as(
                "Inject ApplicationProperties instead. All four of these clients did it this way until " +
                    "2026-09-09 and every value they read was declared twice as a result (backlog item 58). " +
                    "@Scheduled is the one mechanism that genuinely cannot inject; @Value always can."
            )
            .isEmpty();
    }

    /**
     * The two schedule keys are the only ones whose default lives outside the properties class.
     *
     * <p>Named rather than counted, for the reason {@code DevelopmentDataInitializerTest} gives about
     * its fixture: "two keys carry no default" goes on passing when a different two become the ones
     * that do. It is also what keeps the blind spot in this file's javadoc honest — a field whose
     * intended default is {@code false} or {@code 0} would land in this set and has to be argued
     * rather than absorbed.
     */
    @Test
    void onlyTheTwoScheduleKeysCarryNoDefaultInThePropertiesClass() {
        List<String> withoutAFieldDefault = declaredKeysAndWhetherTheFieldCarriesADefault()
            .entrySet()
            .stream()
            .filter(entry -> !entry.getValue())
            .map(Map.Entry::getKey)
            .toList();

        assertThat(withoutAFieldDefault)
            .as(
                "@Scheduled's initialDelayString and fixedDelayString are annotation attributes, so the " +
                    "language requires a compile-time constant there and injection has no path into them — " +
                    "ServicePlanCatalogueSyncService's placeholders carry these two defaults and the fields " +
                    "deliberately do not. Anything else appearing here is either a new " +
                    "reader that should be injecting, or a field whose intended default is false/0/null — which " +
                    "this file's javadoc names as its blind spot and which needs a decision, not a green build."
            )
            .containsExactlyInAnyOrder("application.abofonsa-content.initial-delay-ms", "application.abofonsa-content.refresh-ms");
    }

    /**
     * The values themselves, pinned.
     *
     * <p>These are the thirteen defaults as they stood on {@code main} before item 58, verified there
     * to be identical in both copies — which is what makes the change value-preserving by
     * construction rather than by assertion. This method is the regression guard on them afterwards:
     * nothing else in the suite would notice a base URL or a timeout quietly changing, and two of
     * these decide whether a sibling stack is reachable at all.
     */
    @Test
    void theDefaultsAreTheOnesThatWereInForceBeforeItem58() {
        ApplicationProperties properties = new ApplicationProperties();

        assertThat(properties.getProfessionalservice().getBaseUrl()).isEqualTo("http://hc-professional-service:8081");
        assertThat(properties.getProfessionalservice().isEnabled()).isTrue();
        assertThat(properties.getProfessionalservice().getTimeoutSeconds()).isEqualTo(5);

        assertThat(properties.getPatientservice().getBaseUrl()).isEqualTo("http://hc-patient-service:8081");
        assertThat(properties.getPatientservice().isEnabled()).isTrue();
        assertThat(properties.getPatientservice().getTimeoutSeconds()).isEqualTo(2);
        assertThat(properties.getPatientservice().getResolveBudgetMs()).isEqualTo(4000L);

        assertThat(properties.getAbofonsaContent().getBaseUrl()).isEqualTo("https://web.abofonsa.com");
        assertThat(properties.getAbofonsaContent().isEnabled()).isTrue();
        assertThat(properties.getAbofonsaContent().getLocale()).isEqualTo("en");
        assertThat(properties.getAbofonsaContent().getTimeoutSeconds()).isEqualTo(5);

        // The remaining two live on @Scheduled rather than here, so they are read back from the
        // annotation — asserting 0 on the fields would pin the absence and not the value.
        Map<String, Set<String>> placeholders = applicationPlaceholderDefaults();
        assertThat(placeholders.get("application.abofonsa-content.initial-delay-ms")).containsExactly("120000");
        assertThat(placeholders.get("application.abofonsa-content.refresh-ms")).containsExactly("21600000");
    }

    /**
     * The sweep is reading annotation values, and it tells our keys from Spring's.
     *
     * <p>Every rule above passes vacuously if {@link #placeholderSites()} returns nothing, which is
     * the failure mode {@code LogPseudonymTest} exists to record: a discriminator that has stopped
     * matching goes green. So this asserts the machinery finds a placeholder that is <em>not</em>
     * ours — {@code LoggingConfiguration}'s {@code ${spring.application.name}} — and that
     * {@link #APPLICATION_PLACEHOLDER} correctly declines to claim it. That string contains
     * {@code application.} and is the near miss a looser pattern would swallow.
     */
    @Test
    void theSweepReadsPlaceholdersAndTellsOursFromSprings() {
        List<String> allKeys = new ArrayList<>();
        for (PlaceholderSite site : placeholderSites()) {
            Matcher matcher = ANY_PLACEHOLDER.matcher(site.text());
            while (matcher.find()) {
                allKeys.add(matcher.group(1));
            }
        }

        assertThat(allKeys)
            .as("the annotation sweep found no placeholders at all, so every rule in this file is passing vacuously")
            .contains("spring.application.name");
        assertThat(APPLICATION_PLACEHOLDER.matcher("${spring.application.name}").find())
            .as("spring.application.name is Spring's key, not ours — a pattern that claims it would fail this rule on it")
            .isFalse();
        assertThat(applicationPlaceholderDefaults())
            .as("the two @Scheduled keys are the known population of application.* placeholders")
            .containsOnlyKeys("application.abofonsa-content.initial-delay-ms", "application.abofonsa-content.refresh-ms");
    }

    // ---------------------------------------------------------------------------------------------
    // Machinery.
    // ---------------------------------------------------------------------------------------------

    /** One annotation attribute somewhere in the application, and who carries it. */
    private record PlaceholderSite(String owner, String annotation, String text) {}

    /**
     * Every {@code application.*} placeholder that carries a default, keyed by property name.
     *
     * <p>A {@link Set} rather than a count, so two readers writing the <em>same</em> default still
     * register as one place. That is deliberate: the failure this guards is a value that can disagree
     * with itself, and two identical literals cannot — but two readers writing two different defaults
     * is exactly the drift item 58 describes, and it registers as two.
     */
    private static Map<String, Set<String>> applicationPlaceholderDefaults() {
        Map<String, Set<String>> defaults = new TreeMap<>();
        for (PlaceholderSite site : placeholderSites()) {
            Matcher matcher = APPLICATION_PLACEHOLDER.matcher(site.text());
            while (matcher.find()) {
                if (matcher.group(2) != null) {
                    defaults.computeIfAbsent(matcher.group(1), key -> new LinkedHashSet<>()).add(matcher.group(2));
                }
            }
        }
        return defaults;
    }

    /**
     * Every String-valued annotation attribute on every compiled application class.
     *
     * <p>Read off {@code target/classes} rather than {@code src/main/java}: {@code @Value} and
     * {@code @Scheduled} are {@code RUNTIME}-retained, so the compiled form carries everything this
     * needs and carries nothing a javadoc says. Classes are loaded with {@code initialize = false} —
     * nothing here runs a static initializer to read an annotation.
     */
    private static List<PlaceholderSite> placeholderSites() {
        List<PlaceholderSite> sites = new ArrayList<>();
        for (Class<?> type : applicationClasses()) {
            collect(sites, type.getSimpleName(), type);
            for (Field field : type.getDeclaredFields()) {
                collect(sites, type.getSimpleName() + "." + field.getName(), field);
            }
            for (Method method : type.getDeclaredMethods()) {
                collect(sites, type.getSimpleName() + "." + method.getName() + "()", method);
                collectParameters(sites, type.getSimpleName() + "." + method.getName() + "()", method);
            }
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                collect(sites, type.getSimpleName() + "'s constructor", constructor);
                collectParameters(sites, type.getSimpleName() + "'s constructor", constructor);
            }
        }
        return sites;
    }

    private static void collect(List<PlaceholderSite> sites, String owner, AnnotatedElement element) {
        for (Annotation annotation : element.getDeclaredAnnotations()) {
            collect(sites, owner, annotation);
        }
    }

    private static void collectParameters(List<PlaceholderSite> sites, String owner, Executable executable) {
        for (Annotation[] onOneParameter : executable.getParameterAnnotations()) {
            for (Annotation annotation : onOneParameter) {
                collect(sites, owner, annotation);
            }
        }
    }

    private static void collect(List<PlaceholderSite> sites, String owner, Annotation annotation) {
        String name = annotation.annotationType().getSimpleName();
        for (Method attribute : annotation.annotationType().getDeclaredMethods()) {
            if (attribute.getParameterCount() != 0) {
                continue;
            }
            Object value;
            try {
                value = attribute.invoke(annotation);
            } catch (ReflectiveOperationException | RuntimeException e) {
                continue;
            }
            if (value instanceof String text) {
                sites.add(new PlaceholderSite(owner, name, text));
            } else if (value instanceof String[] texts) {
                for (String text : texts) {
                    sites.add(new PlaceholderSite(owner, name, text));
                }
            }
        }
    }

    /**
     * Every class in this application's own compiled output.
     *
     * <p>Discovered from the code source of {@link ApplicationProperties} rather than from a hard-coded
     * {@code target/classes}, so nothing here depends on the build layout, and derived rather than
     * enumerated for the reason {@code PaginationIT} records — a sweep whose coverage is extended by
     * hand silently stops covering things.
     */
    private static List<Class<?>> applicationClasses() {
        URI codeSource;
        try {
            codeSource = ApplicationProperties.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (Exception e) {
            throw new IllegalStateException("Could not locate the compiled classes to sweep", e);
        }
        Path root = Path.of(codeSource).resolve("net").resolve("jojoaddison");
        List<Class<?>> classes = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".class")).toList()) {
                String name = Path.of(codeSource)
                    .relativize(file)
                    .toString()
                    .replace(java.io.File.separatorChar, '.')
                    .replaceAll("\\.class$", "");
                try {
                    classes.add(Class.forName(name, false, ApplicationPropertiesSingleSourceTest.class.getClassLoader()));
                } catch (Throwable ignored) {
                    // A class that will not resolve cannot carry a placeholder anything reads either.
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not walk " + root, e);
        }
        assertThat(classes)
            .as("the class sweep found nothing under net.jojoaddison, so every rule here is vacuous")
            .hasSizeGreaterThan(100);
        return classes;
    }

    /**
     * Every key {@link ApplicationProperties} declares, mapped to whether its field carries a default.
     *
     * <p>"Carries a default" is "differs from the JVM default on a fresh instance". The limit that
     * follows from that is named in this class's javadoc and pinned by
     * {@link #onlyTheTwoScheduleKeysCarryNoDefaultInThePropertiesClass()}.
     */
    private static Map<String, Boolean> declaredKeysAndWhetherTheFieldCarriesADefault() {
        ApplicationProperties fresh = new ApplicationProperties();
        Map<String, Boolean> keys = new LinkedHashMap<>();
        for (Field group : ApplicationProperties.class.getDeclaredFields()) {
            if (Modifier.isStatic(group.getModifiers())) {
                continue;
            }
            Object nested = read(group, fresh);
            if (nested == null) {
                continue;
            }
            String prefix = "application." + relaxed(group.getName()) + ".";
            for (Field setting : nested.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(setting.getModifiers())) {
                    continue;
                }
                keys.put(prefix + relaxed(setting.getName()), carriesADefault(setting, nested));
            }
        }
        return keys;
    }

    private static boolean carriesADefault(Field field, Object owner) {
        Object value = read(field, owner);
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.longValue() != 0L;
        }
        return true;
    }

    private static Object read(Field field, Object owner) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read " + field, e);
        }
    }

    /**
     * A field name as relaxed binding writes it — {@code abofonsaContent} to {@code abofonsa-content},
     * {@code resolveBudgetMs} to {@code resolve-budget-ms}. {@code patientservice} is one word and
     * stays one, which is the deliberate spelling {@link ApplicationProperties.Patientservice}'s
     * javadoc argues for.
     */
    private static String relaxed(String fieldName) {
        return fieldName.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
    }
}
