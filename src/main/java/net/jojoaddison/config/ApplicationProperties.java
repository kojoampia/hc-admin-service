package net.jojoaddison.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Hc Admin Service.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    /**
     * Where the roster of record lives, and whether to write to it.
     *
     * <p><b>This class had no properties at all, and {@code ignoreUnknownFields = false} is why it
     * has these.</b> Any key under {@code application.*} that no field here declares fails the
     * context at startup with "were left unbound" — so a {@code @Value("${application.…}")} read
     * without a declaration here is not a shortcut, it is a service that will not boot. Found the
     * honest way, by adding the key to the test config first.
     *
     * <p>Values are read through {@code @Value} in {@code ProfessionalServiceClient} rather than by
     * injecting this class, matching how hc-professional's {@code PatientServiceClient} reads the
     * mirror-image settings; the declarations below exist to make the prefix legal and to be the one
     * place the defaults are written down.
     */
    private final Professionalservice professionalservice = new Professionalservice();

    /**
     * Where Abofonsa publishes the membership catalogue, and whether to read it.
     *
     * <p>Same shape and same reason as the block above, and the trap it documents caught this one
     * too: adding {@code application.abofonsa-content.enabled} to the test config with no field here
     * failed every integration test with "were left unbound", 1033 errors from one missing
     * declaration. The comment above was right and is worth reading before adding a third.
     */
    private final AbofonsaContent abofonsaContent = new AbofonsaContent();

    /**
     * Where the patient app lives, and whether to ask it to name a patient learned from an event.
     *
     * <p>Third block, same shape and same trap as the two above — {@code ignoreUnknownFields = false}
     * means a compose file setting {@code APPLICATION_PATIENTSERVICE_BASE_URL} against a class that
     * does not declare it fails the whole context at startup. Backlog item 50.
     */
    private final Patientservice patientservice = new Patientservice();

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
     * <p><b>The default is the production container name, and every other environment must set it.</b>
     * hc-patient-service sits on {@code infranet} and this service is already on it, so production
     * needs no line at all — but the quality stack's sibling is called
     * {@code hc-patient-quality-service}, and the {@code deploy/e2e} stack has no hc-patient in it.
     * Wrong or absent, the console shows the address item 45 put on the row plus a note saying the
     * name could not be checked, which is honest and is not the feature working.
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
     * <p>Read through {@code @Value} in {@link net.jojoaddison.service.AbofonsaContentClient} and
     * {@link net.jojoaddison.service.ServicePlanCatalogueSyncService}, matching how the block above
     * is read; the declarations here make the prefix legal and are the one place the defaults are
     * written down.
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

        /** How long after startup the first refresh runs. Off the startup path deliberately. */
        private long initialDelayMs = 120_000;

        /** How often to refresh afterwards. Six hours: a published price list moves a few times a year. */
        private long refreshMs = 21_600_000;

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
    // jhipster-needle-application-properties-property-class
}
