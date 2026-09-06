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

    // jhipster-needle-application-properties-property

    public Professionalservice getProfessionalservice() {
        return professionalservice;
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
    // jhipster-needle-application-properties-property-class
}
