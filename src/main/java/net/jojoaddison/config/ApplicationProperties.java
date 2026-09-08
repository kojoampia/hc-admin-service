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

    // jhipster-needle-application-properties-property

    public Professionalservice getProfessionalservice() {
        return professionalservice;
    }

    public AbofonsaContent getAbofonsaContent() {
        return abofonsaContent;
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
