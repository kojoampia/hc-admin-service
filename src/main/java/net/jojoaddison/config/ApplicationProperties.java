package net.jojoaddison.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Hc Admin Service.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 *
 * <h2>Every default under {@code application.*} is written here, once — backlog item 58</h2>
 *
 * <p>This class had no properties at all until the sibling clients arrived, and until 2026-09-09 it
 * was <b>not what ran</b>: each client took its configuration from inline
 * {@code @Value("${application.…:default}")} placeholders on its constructor, so every value was
 * written twice — once here and once where it was read — with nothing that would notice if the two
 * stopped agreeing. Editing a default in this file changed nothing at runtime, the build stayed
 * green, and it had already misled one reader: a {@code quality/compose.yml} comment asserted that
 * <em>"{@code ApplicationProperties} defaults the address"</em> — plausible, wrong, and caught only
 * because a review traced the injection by hand.
 *
 * <p><b>The rule now, and it is enforced by {@code ApplicationPropertiesSingleSourceTest} rather
 * than by this paragraph:</b> a reader injects this class and the default is the field initializer
 * below. No class may read an {@code application.*} key through {@code @Value}. The single
 * exception is {@code @Scheduled}, whose {@code initialDelayString} / {@code fixedDelayString} are
 * annotation attributes and must therefore be compile-time constant expressions — a placeholder or
 * SpEL, never an injected value. For those two keys the placeholder carries the default and the
 * field here deliberately carries none. {@link AbofonsaContent#initialDelayMs} argues it in full,
 * including why the lifecycle reason this paragraph gave first is wrong.
 *
 * <h2>{@code ignoreUnknownFields = false} — what it does and does not police</h2>
 *
 * <p>Any key under {@code application.*} that no field here declares fails the context at startup
 * with "were left unbound" — <b>if it comes from a configuration file.</b> Adding
 * {@code application.abofonsa-content.enabled} to the test config with no field here failed every
 * integration test, 1033 errors from one missing declaration, which is how this was found.
 *
 * <p><b>It does not police the environment, and believing otherwise is how these declarations came
 * to be described as load-bearing for the wrong reason.</b> Spring Boot excludes
 * {@code SystemEnvironmentPropertySource} from the unbound check, because the environment is a
 * namespace nobody owns. So {@code APPLICATION_PATIENTSERVICE_BASE_URL} in a compose file against a
 * class that declared nothing would have started perfectly happily — measured 2026-09-09, and
 * corroborated by hc-professional, whose {@code ApplicationProperties} declares no
 * {@code patientservice} at all while their quality compose sets exactly that variable. What makes
 * these declarations mandatory is {@code src/test/resources/config/application.yml}, which sets
 * three {@code enabled} keys from a <em>file</em>. Both facts are pinned as tests, because this is
 * the second time a claim about them has been reasoned rather than measured.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    /**
     * Where the roster of record lives, and whether to write to it.
     *
     * <p>Injected by {@code ProfessionalServiceClient}. Until item 58 it was not: that client read
     * the same three keys through {@code @Value} and this block was a second, unread copy.
     */
    private final Professionalservice professionalservice = new Professionalservice();

    /**
     * Where Abofonsa publishes the membership catalogue, and whether to read it.
     *
     * <p>Injected by {@code AbofonsaContentClient}, except for the two schedule fields, which
     * {@code ServicePlanCatalogueSyncService} reads through {@code @Scheduled} and which therefore
     * carry no default here — see their own comments.
     */
    private final AbofonsaContent abofonsaContent = new AbofonsaContent();

    /**
     * Where the patient app lives, and whether to ask it to name a patient learned from an event.
     *
     * <p>Injected by {@code PatientServiceClient} and by {@code DirectoryNameResolutionService},
     * which reads {@link Patientservice#getResolveBudgetMs()} and nothing else. Backlog item 50.
     */
    private final Patientservice patientservice = new Patientservice();

    /**
     * Whether item 56's one-time plan-code backfill may run at all.
     *
     * <p>Injected by {@link net.jojoaddison.config.dbmigrations.ServicePlanCodeBackfillMigration}, and
     * the only block here that a Mongock change unit reads rather than a Spring service.
     */
    private final ServicePlanCodeBackfill servicePlanCodeBackfill = new ServicePlanCodeBackfill();

    // jhipster-needle-application-properties-property

    public Professionalservice getProfessionalservice() {
        return professionalservice;
    }

    public AbofonsaContent getAbofonsaContent() {
        return abofonsaContent;
    }

    public Patientservice getPatientservice() {
        return patientservice;
    }

    public ServicePlanCodeBackfill getServicePlanCodeBackfill() {
        return servicePlanCodeBackfill;
    }

    // jhipster-needle-application-properties-property-getter

    /**
     * {@code application.professionalservice.*}.
     *
     * <p>One word, not {@code professional-service}: relaxed binding maps a hyphenated key onto a
     * camel-cased field, so both spellings would bind and the two would read as different settings
     * in a compose file. The service is called {@code professionalservice} everywhere else in the
     * estate — the gateway prefix, the Consul name — and this agrees with that.
     */
    public static class Professionalservice {

        /** Container-network address of hc-professional-service. */
        private String baseUrl = "http://hc-professional-service:8081";

        /**
         * Off means "do not pretend to file rounds".
         *
         * <p>Disabled, a planning run reports every round as {@code FAILED} with the roster service
         * unreachable, which is what an environment with no sibling stack should say. It must never
         * degrade to reporting a round as planned.
         */
        private boolean enabled = true;

        /** Both the connect and the read timeout, in seconds. */
        private int timeoutSeconds = 5;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }

    /**
     * {@code application.patientservice.*} — hc-patient's microservice, which owns patient identity.
     *
     * <p>One word, matching {@code professionalservice} above and for the same reason: relaxed
     * binding would accept {@code patient-service} too, and two spellings in two compose files read
     * as two settings. The gateway prefix over there is {@code hcpatientservice} and the container is
     * {@code hc-patient-service}; this name follows the sibling block in this file, which is the one
     * a reader is comparing it against.
     *
     * <p>This block carried a warning until 2026-09-09 that <em>nothing injects this class, so the
     * values below are not what runs</em>. That was true and is the defect backlog item 58 closed:
     * {@link net.jojoaddison.service.PatientServiceClient} and
     * {@link net.jojoaddison.service.DirectoryNameResolutionService} now take this object, and these
     * are the values in force. <b>Editing a default here does change what runs.</b>
     *
     * <p><b>The default is the production container name, and every other environment must set it.</b>
     * hc-patient-service sits on {@code infranet} and this service is already on it, so production
     * needs no line at all — but the quality stack's sibling is called
     * {@code hc-patient-quality-service}, and the {@code deploy/e2e} stack has no hc-patient in it.
     * Wrong or absent, the console shows the address item 45 put on the row plus a note saying a
     * name could not be looked up, which is honest and is not the feature working. That note names
     * no stack precisely because this is one of its causes.
     */
    public static class Patientservice {

        /** Container-network address of hc-patient-service. Port 8081, not 8080 — theirs, not ours. */
        private String baseUrl = "http://hc-patient-service:8081";

        /**
         * Off means "do not ask hc-patient to name anybody".
         *
         * <p>Disabled, every lookup answers {@code UNAVAILABLE} and the directory reads exactly as it
         * did before backlog item 50. It never degrades to inventing a name, and it never fails a
         * request: this read decorates a screen, and a screen that will not load is worse than one
         * showing an address.
         */
        private boolean enabled = true;

        /**
         * Both the connect and the read timeout, in seconds.
         *
         * <p>Two rather than the roster client's five, because this one is on the path of a screen
         * somebody is waiting for and it runs once per nameless row.
         */
        private int timeoutSeconds = 2;

        /**
         * How long one request may spend resolving names before the rest of the page is reported
         * unavailable without being asked.
         *
         * <p>The bound on the worst case: a page of twenty nameless rows against a sibling that
         * accepts connections and never answers is twenty read timeouts in series without it. See
         * {@code DirectoryNameResolutionService}.
         */
        private long resolveBudgetMs = 4000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public long getResolveBudgetMs() {
            return resolveBudgetMs;
        }

        public void setResolveBudgetMs(long resolveBudgetMs) {
            this.resolveBudgetMs = resolveBudgetMs;
        }
    }

    /**
     * {@code application.abofonsa-content.*} — the public content API of {@code web.abofonsa.com}.
     *
     * <p>Hyphenated, unlike {@code professionalservice} one block up, because there is no
     * single-word name for it in the estate to agree with. Relaxed binding accepts
     * {@code abofonsacontent} too; use the hyphenated spelling everywhere so a compose file cannot
     * end up carrying both and reading as two settings.
     *
     * <p><b>This block is read two ways, and it is the only one that is.</b> The four settings a
     * client needs are injected into {@link net.jojoaddison.service.AbofonsaContentClient}, like
     * every other block here. The two schedule fields are read by
     * {@link net.jojoaddison.service.ServicePlanCatalogueSyncService}'s {@code @Scheduled}, whose
     * attributes must be compile-time constants and so cannot be handed an injected value at all —
     * those two carry no default here and {@link AbofonsaContent#initialDelayMs} says why. That
     * split is item 58's one exception, and {@code ApplicationPropertiesSingleSourceTest} is what
     * keeps it from spreading.
     */
    public static class AbofonsaContent {

        /** The publisher. Public — {@code /api/v1/content/**} is permitAll on their side. */
        private String baseUrl = "https://web.abofonsa.com";

        /**
         * Off means "do not read the published catalogue".
         *
         * <p>Disabled, the sync writes nothing and reports {@code configured: false} — which is a
         * different answer from Abofonsa being unreachable, and the two must not be collapsed. See
         * {@code PlanCatalogueSyncDTO}.
         */
        private boolean enabled = true;

        /** The language to price and describe the tiers in. */
        private String locale = "en";

        /** Both the connect and the read timeout, in seconds. */
        private int timeoutSeconds = 5;

        /**
         * How long after startup the first refresh runs. Off the startup path deliberately.
         *
         * <p><b>No default here, and that is item 58's rule rather than an omission.</b>
         * {@code ServicePlanCatalogueSyncService} reads this key through
         * {@code @Scheduled(initialDelayString = "${…:120000}")}, and <b>an annotation attribute must
         * be a compile-time constant expression</b> (JLS 9.7.1). So the only things it can carry are
         * a literal, an Environment placeholder or SpEL — injection has no path into it at all. The
         * placeholder therefore carries the default and this field carries none: <b>one place per
         * value, and the place is the annotation.</b>
         *
         * <p><b>The reason is the language, not the lifecycle, and the first version of this comment
         * got that wrong.</b> It said the string is resolved "before there is an object to ask",
         * which is false: {@code ScheduledAnnotationBeanPostProcessor} resolves these during each
         * bean's post-initialization, and this very commit made {@code ApplicationProperties} a
         * transitive constructor dependency of the bean carrying the annotation
         * ({@code ServicePlanCatalogueSyncService} ← {@code AbofonsaContentClient} ←
         * {@code ApplicationProperties}), so the object provably exists by then. A reason that a
         * change in wiring can falsify is not the reason; the constant-expression rule cannot be.
         *
         * <p><b>SpEL is the alternative, and it is worse than "stringly typed" — it does not work.</b>
         * {@code #{@applicationProperties.abofonsaContent.refreshMs}} fails bean resolution outright:
         * a {@code @ConfigurationProperties} bean is named for its prefix and class, so this one is
         * {@code application-net.jojoaddison.config.ApplicationProperties} — the name is visible in
         * the expected-failure log of
         * {@code ApplicationPropertiesSingleSourceTest.aKeyNoFieldDeclaresFailsTheContext…}. And even
         * aliased it would evaluate to {@code 0}, because this field deliberately holds no default.
         * Written down because it is the first thing the next reader will reach for.
         *
         * <p>The field itself still has to exist. Nothing sets this key today, but if anything ever
         * sets it in a configuration <em>file</em>, {@code ignoreUnknownFields = false} fails the
         * whole context — and deleting the field to "finish the tidy-up" is what would cause that.
         * Bound or not, the value that takes effect is the one {@code @Scheduled} resolves, so a
         * value arriving here is read by the scheduler too and nothing disagrees.
         */
        private long initialDelayMs;

        /**
         * How often to refresh afterwards. Six hours: a published price list moves a few times a
         * year.
         *
         * <p>Same shape and same reason as {@link #initialDelayMs} above — the default lives in
         * {@code ServicePlanCatalogueSyncService}'s {@code fixedDelayString}, which is the only
         * reader.
         */
        private long refreshMs;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getLocale() {
            return locale;
        }

        public void setLocale(String locale) {
            this.locale = locale;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public long getRefreshMs() {
            return refreshMs;
        }

        public void setRefreshMs(long refreshMs) {
            this.refreshMs = refreshMs;
        }
    }

    /**
     * {@code application.service-plan-code-backfill.*} — the switch on backlog item 56's migration.
     *
     * <p>Hyphenated, like {@code abofonsa-content} and for the same reason: there is no single-word
     * name for it elsewhere in the estate to agree with. Relaxed binding would accept
     * {@code serviceplancodebackfill} too, so use this spelling everywhere and a compose file cannot
     * end up carrying both.
     *
     * <p><b>⚠ As an environment variable that is {@code APPLICATION_SERVICEPLANCODEBACKFILL_ENABLED},
     * not {@code APPLICATION_SERVICE_PLAN_CODE_BACKFILL_ENABLED}.</b> Spring uppercases, maps
     * {@code .} to {@code _} and <em>removes</em> hyphens rather than converting them — the exact trap
     * item 56 records against {@code APPLICATION_ABOFONSACONTENT_ENABLED}, one setting along. The
     * wrong spelling binds a path nothing reads and leaves the migration off while a compose file
     * appears to have turned it on, which for this switch is the harmless direction and will not stay
     * harmless if the default is ever inverted.
     */
    public static class ServicePlanCodeBackfill {

        /**
         * Off, and it is the only default here that ships off because the work is not ready to run.
         *
         * <p>The other {@code enabled} flags in this file default {@code true} and are turned off per
         * environment. This one is the reverse: {@link net.jojoaddison.config.dbmigrations.ServicePlanCodeBackfillMigration}
         * stamps a published tier code onto plans it recognises, its matching rule was written without
         * sight of production's rows, and a wrong stamp silently re-points every patient on that plan.
         * So it stays inert until somebody has compared the rule against the real collection and
         * deliberately switched it on. That class's javadoc is the argument; this is only the switch.
         */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    // jhipster-needle-application-properties-property-class
}
