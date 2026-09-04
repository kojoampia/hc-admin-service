package net.jojoaddison.domain.enumeration;

/**
 * What kind of account a {@link net.jojoaddison.domain.DirectoryLink} stands for, and therefore
 * whether this service keeps a local record for it.
 *
 * <p><b>This exists because a stream carries more than one kind of person.</b> hc-patient's
 * {@code patient-events} announces a patient registering <em>and</em> a care angel being nominated,
 * on the same topic, with the same {@code AccountCreated} type and the same envelope — the only
 * difference is in {@code data}, where the nomination carries {@code ROLE_ANGEL} among the
 * authorities and {@code reason: careAngelNomination}. A consumer that reads only the type turns
 * every nomination into a patient, which is what happened here: the count, the directory, the
 * account-mix chart and the weekly-joins tile all moved for somebody who is not a patient.
 *
 * <p>It is persisted on the link rather than re-derived, for one concrete reason: the reconciliation
 * endpoint walks links whose local record is missing and rebuilds it, and it has no event in front
 * of it to re-read. Without the kind stored, the reconciliation would recreate exactly the patients
 * this enum exists to prevent.
 */
public enum DirectorySubjectKind {
    /**
     * Somebody who registered on hc-patient, or who began onboarding there. This service keeps a
     * {@code Patient} row for them.
     */
    PATIENT,

    /**
     * Somebody a patient nominated as their care angel on hc-patient. <b>Not a patient here.</b>
     * hc-admin models an angel as its own {@code Angel} entity, joined to the patient who nominated
     * them, and nothing about that record can be derived from an account event — so the link is
     * recorded and no local row is invented, exactly as for {@link #PROFESSIONAL}.
     */
    CARE_ANGEL,

    /**
     * A clinician who registered on hc-professional. No local row either: {@code Professional}
     * requires a {@code role} and a {@code licenceNumber}, and neither is on the wire.
     */
    PROFESSIONAL,
}
