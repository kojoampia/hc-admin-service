package net.jojoaddison.config;

/**
 * Builds an {@link ApplicationProperties} for a test that constructs a sibling client by hand.
 *
 * <p>Backlog item 58 made the clients take this object instead of three or four
 * {@code @Value} placeholders, which moved the work of naming a base URL and a timeout out of each
 * constructor call and into an object. Six test call sites needed that object; a five-line builder
 * pasted into six files is a small version of the duplication the item exists to close, so it is
 * written once here.
 *
 * <p><b>Every factory below starts from a real {@code new ApplicationProperties()}</b> and overwrites
 * only what the caller names, so a field the caller does not mention holds the shipped default rather
 * than a fixture's idea of one. That is deliberate: a test that quietly ran on {@code timeoutSeconds
 * = 0} because the fixture forgot the field would be a test whose subject is not the production
 * object.
 */
public final class ApplicationPropertiesFixture {

    private ApplicationPropertiesFixture() {}

    /** {@code application.patientservice.*} — {@code PatientServiceClient}'s three settings. */
    public static ApplicationProperties patientservice(String baseUrl, boolean enabled, int timeoutSeconds) {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.Patientservice patientservice = properties.getPatientservice();
        patientservice.setBaseUrl(baseUrl);
        patientservice.setEnabled(enabled);
        patientservice.setTimeoutSeconds(timeoutSeconds);
        return properties;
    }

    /**
     * {@code application.patientservice.resolve-budget-ms} — the only key
     * {@code DirectoryNameResolutionService} reads.
     */
    public static ApplicationProperties resolveBudget(long resolveBudgetMs) {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getPatientservice().setResolveBudgetMs(resolveBudgetMs);
        return properties;
    }

    /** {@code application.professionalservice.*} — {@code ProfessionalServiceClient}'s three. */
    public static ApplicationProperties professionalservice(String baseUrl, boolean enabled, int timeoutSeconds) {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.Professionalservice professionalservice = properties.getProfessionalservice();
        professionalservice.setBaseUrl(baseUrl);
        professionalservice.setEnabled(enabled);
        professionalservice.setTimeoutSeconds(timeoutSeconds);
        return properties;
    }

    /** {@code application.abofonsa-content.*} — {@code AbofonsaContentClient}'s four. */
    public static ApplicationProperties abofonsaContent(String baseUrl, boolean enabled, String locale, int timeoutSeconds) {
        ApplicationProperties properties = new ApplicationProperties();
        ApplicationProperties.AbofonsaContent abofonsaContent = properties.getAbofonsaContent();
        abofonsaContent.setBaseUrl(baseUrl);
        abofonsaContent.setEnabled(enabled);
        abofonsaContent.setLocale(locale);
        abofonsaContent.setTimeoutSeconds(timeoutSeconds);
        return properties;
    }
}
