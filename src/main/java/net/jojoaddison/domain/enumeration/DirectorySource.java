package net.jojoaddison.domain.enumeration;

/**
 * Which sibling subsystem a {@link net.jojoaddison.domain.DirectoryLink} was learned from.
 *
 * <p><b>The subsystem, not the emitting service.</b> hc-patient publishes onto one topic from two
 * applications — its gateway announces the account, its api announces onboarding — and both use the
 * same correlation key. Keying links on the {@code source} field the envelopes carry
 * ({@code patientGateway}, {@code hcPatientService}) would split one person into two links and
 * count them twice on every dashboard tile. There is one value per stream, and that is the point.
 */
public enum DirectorySource {
    /** The {@code patient-events} topic, published by hc-patient's gateway and api. */
    HC_PATIENT,

    /** The {@code hc.professional.registration} topic, published by hc-professional's gateway and api. */
    HC_PROFESSIONAL,
}
