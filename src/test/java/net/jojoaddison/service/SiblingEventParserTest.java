package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.service.dto.SiblingDomainEvent;
import net.jojoaddison.service.dto.SiblingDomainEvent.Disposition;
import org.junit.jupiter.api.Test;

/**
 * The two sibling envelopes, read off the wire.
 *
 * <p>The payloads below are written out as literal JSON rather than built from a shared type,
 * because <b>there is no shared type</b> — the whole risk this class covers is that hc-patient or
 * hc-professional changes a field name and nothing in either build fails. A fixture assembled from
 * this repository's own classes would agree with itself for ever. These strings were copied from the
 * two publishers, and if the parser stops reading them the test names which field went missing.
 */
class SiblingEventParserTest {

    private static final String PATIENT_EMAIL = "ama.mensah@example.com";

    private final SiblingEventParser parser = new SiblingEventParser(new ObjectMapper());

    @Test
    void readsAPatientAccountCreatedEvent() {
        SiblingDomainEvent event = parser
            .parsePatientEvent(bytes(accountCreated(PATIENT_EMAIL, "2026-09-01T08:00:00Z", false)), null)
            .orElseThrow();

        assertThat(event.source()).isEqualTo(DirectorySource.HC_PATIENT);
        assertThat(event.type()).isEqualTo("AccountCreated");
        assertThat(event.subjectKey()).isEqualTo(PATIENT_EMAIL);
        assertThat(event.login()).isEqualTo("amensah");
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-09-01T08:00:00Z"));
        assertThat(event.activated()).as("this account has not been activated yet").isFalse();
    }

    /**
     * The header wins over the payload, because it is what the broker partitioned on.
     *
     * <p>They are the same value in practice. Asserting the precedence anyway pins which one is
     * authoritative, so a future reader does not "simplify" the fallback into the primary.
     */
    @Test
    void prefersThePartitionKeyHeaderAndFallsBackToThePayload() {
        String frame = accountCreated(PATIENT_EMAIL, "2026-09-01T08:00:00Z", false);

        assertThat(parser.parsePatientEvent(bytes(frame), "  OTHER@Example.COM ").orElseThrow().subjectKey())
            .as("the header is the partition key and is trimmed and lowercased like the producer does")
            .isEqualTo("other@example.com");
        assertThat(parser.parsePatientEvent(bytes(frame), "   ").orElseThrow().subjectKey())
            .as("a blank header falls back to subject.email")
            .isEqualTo(PATIENT_EMAIL);
    }

    @Test
    void treatsAnActivationAsEvidenceTheAccountCanSignIn() {
        assertThat(parser.parsePatientEvent(bytes(accountActivated(PATIENT_EMAIL)), null).orElseThrow().activated()).isTrue();
        assertThat(
            parser.parsePatientEvent(bytes(accountCreated(PATIENT_EMAIL, "2026-09-01T08:00:00Z", true)), null).orElseThrow().activated()
        )
            .as("an account created already activated says so in its data")
            .isTrue();
    }

    /**
     * <b>The care-angel trap.</b> hc-patient publishes {@code AccountCreated} for a nomination too,
     * keyed on the angel's own address, with {@code ROLE_ANGEL} among the authorities and
     * {@code reason: careAngelNomination} — and then an {@code AccountActivated} straight after it,
     * because an angel's account is created already activated.
     *
     * <p>Until 2026-09-05 the parser named two types and read them only for {@code activated}, so
     * both frames went down the creation path and every nomination became an ACTIVE patient: on the
     * count, in the directory, on the account-mix chart, on the weekly-joins tile. An angel is not a
     * patient here — hc-admin has a separate {@code Angel} entity — so the nomination is a link and
     * nothing else, and the activation that follows it may only update.
     */
    @Test
    void aCareAngelNominationIsALinkAndNeverAPatient() {
        SiblingDomainEvent nomination = parser.parsePatientEvent(bytes(careAngelNominated(PATIENT_EMAIL)), null).orElseThrow();

        assertThat(nomination.disposition())
            .as("a nomination may not open a patient record; it is a link and nothing more")
            .isEqualTo(Disposition.LINK_ONLY);
        assertThat(nomination.subjectKind()).isEqualTo(DirectorySubjectKind.CARE_ANGEL);
        assertThat(nomination.activated()).as("an angel's account being activated is not a patient becoming active").isFalse();

        // The second frame the same call site emits. It carries no ROLE_ANGEL and no reason — only
        // an activatedAt — so nothing in it says who it is about. UPDATE_ONLY is what stops it.
        assertThat(parser.parsePatientEvent(bytes(accountActivated(PATIENT_EMAIL)), null).orElseThrow().disposition())
            .as("the nomination's activation frame carries no marker at all, so it must not create either")
            .isEqualTo(Disposition.UPDATE_ONLY);
    }

    /** Either marker alone is enough; they are set at one call site and one could outlive the other. */
    @Test
    void recognisesANominationFromEitherMarkerAlone() {
        String byAuthority = accountCreated(PATIENT_EMAIL, "2026-09-01T08:00:00Z", true)
            .replace("\"authorities\":\"ROLE_USER\"", "\"authorities\":\"ROLE_USER,ROLE_ANGEL\"");
        String byReason = accountCreated(PATIENT_EMAIL, "2026-09-01T08:00:00Z", true)
            .replace("\"langKey\":\"en\"", "\"reason\":\"careAngelNomination\"");

        assertThat(parser.parsePatientEvent(bytes(byAuthority), null).orElseThrow().disposition()).isEqualTo(Disposition.LINK_ONLY);
        assertThat(parser.parsePatientEvent(bytes(byReason), null).orElseThrow().disposition()).isEqualTo(Disposition.LINK_ONLY);
    }

    /**
     * <b>All seven types hc-patient publishes, and what each may do.</b>
     *
     * <p>Enumerated from {@code PatientEventType} in that repository rather than from the frames that
     * happen to be in a topic. The entry that closed this work claimed the sweep had been done and it
     * had been done for hc-professional only: the parser named two of these seven and there was no
     * type filter on the creation path at all, so the other five — and every type either product adds
     * next — opened a {@code Patient}.
     */
    @Test
    void modelsEveryTypeHcPatientPublishes() {
        assertThat(dispositionOf("AccountCreated")).isEqualTo(Disposition.CREATE);
        assertThat(dispositionOf("OnboardingStarted"))
            .as("the one event that proves a profile exists on the far side, and the only one carrying the patientId")
            .isEqualTo(Disposition.CREATE);
        assertThat(dispositionOf("AccountActivated")).isEqualTo(Disposition.UPDATE_ONLY);
        assertThat(dispositionOf("OnboardingStepCompleted")).isEqualTo(Disposition.UPDATE_ONLY);
        assertThat(dispositionOf("OnboardingCompleted")).isEqualTo(Disposition.UPDATE_ONLY);
        assertThat(dispositionOf("CareDelegationChanged"))
            .as("keyed on the PATIENT's address, with the angel's in data — it changes a subject already known")
            .isEqualTo(Disposition.UPDATE_ONLY);
        assertThat(dispositionOf("DeletionRequestChanged")).isEqualTo(Disposition.UPDATE_ONLY);
    }

    /**
     * The sharpest case on the stream, and the one an untyped creation path got exactly backwards.
     *
     * <p>{@code DeletionRequestChanged/COMPLETED} is published <em>after</em> the profile has been
     * erased — hc-patient's own javadoc says the email has to be read off the stored request because
     * the profile is already gone and "a consumer must not try to resolve the patient". So the event
     * whose entire meaning is "erase this person" was, for a subject with no link, the thing that made
     * this service start storing their address and open a nameless directory row for them.
     */
    @Test
    void marksTheCompletedErasureAndNeverOpensARecordForIt() {
        SiblingDomainEvent completed = parser.parsePatientEvent(bytes(deletionRequest(PATIENT_EMAIL, "COMPLETED")), null).orElseThrow();

        assertThat(completed.disposition()).isEqualTo(Disposition.UPDATE_ONLY);
        assertThat(completed.erased()).as("the far side has already deleted this person's profile").isTrue();

        // The other three changes are ordinary lifecycle news about somebody who still exists.
        for (String change : new String[] { "RAISED", "CANCELLED", "REJECTED" }) {
            assertThat(parser.parsePatientEvent(bytes(deletionRequest(PATIENT_EMAIL, change)), null).orElseThrow().erased())
                .as("%s is a request, not an erasure", change)
                .isFalse();
        }
    }

    /**
     * A type neither producer has published is ignored, which is the contract both of them state in
     * their own javadoc: <em>a consumer meeting something it does not recognise must ignore it.</em>
     */
    @Test
    void ignoresATypeItDoesNotModel() {
        assertThat(parser.parsePatientEvent(bytes(typed("SomethingAddedNextYear", PATIENT_EMAIL)), null))
            .as("hc-patient may add a type without reference to this consumer, and it must not become a patient")
            .isEmpty();
        assertThat(parser.parseProfessionalEvent(bytes(professionalTyped("compliance.alert"))))
            .as("hc-professional owns its topic and may add to it too")
            .isEmpty();
    }

    /** Both clinician types are links and nothing more; no {@code Professional} is ever invented. */
    @Test
    void keepsNoLocalRecordForEitherProfessionalType() {
        assertThat(parser.parseProfessionalEvent(bytes(registrationCreated("acc-1"))).orElseThrow().disposition())
            .isEqualTo(Disposition.LINK_ONLY);
        assertThat(parser.parseProfessionalEvent(bytes(onboardingState("acc-1", "COMPLETED"))).orElseThrow().disposition())
            .isEqualTo(Disposition.LINK_ONLY);
        assertThat(parser.parseProfessionalEvent(bytes(registrationCreated("acc-1"))).orElseThrow().subjectKind())
            .isEqualTo(DirectorySubjectKind.PROFESSIONAL);
    }

    private Disposition dispositionOf(String type) {
        return parser.parsePatientEvent(bytes(typed(type, PATIENT_EMAIL)), null).orElseThrow().disposition();
    }

    /**
     * {@code OnboardingStarted} is the one event that binds an email to a patient id, and the parser
     * has to pick it up from {@code subject.patientId} — hc-patient's envelope says in its own
     * javadoc that this is the only place the mapping is published.
     */
    @Test
    void picksUpThePatientIdWhereOnboardingPublishesIt() {
        SiblingDomainEvent event = parser.parsePatientEvent(bytes(onboardingStarted(PATIENT_EMAIL, "p-1234")), null).orElseThrow();

        assertThat(event.externalId()).isEqualTo("p-1234");
        assertThat(event.activated()).as("onboarding says nothing about whether an account can sign in").isFalse();
    }

    @Test
    void readsAProfessionalRegistration() {
        SiblingDomainEvent event = parser.parseProfessionalEvent(bytes(registrationCreated("acc-1"))).orElseThrow();

        assertThat(event.source()).isEqualTo(DirectorySource.HC_PROFESSIONAL);
        assertThat(event.type()).isEqualTo("registration.created");
        assertThat(event.subjectKey()).as("this stream is keyed on accountId throughout, never on the email").isEqualTo("acc-1");
        assertThat(event.email()).isEqualTo("k.boateng@example.com");
        assertThat(event.login()).isEqualTo("kboateng");
        assertThat(event.activated()).isTrue();
    }

    /**
     * {@code onboarding.state} carries the state in the payload rather than in the type, which is a
     * deliberate choice on the producer's side so a consumer switches on one field. It also carries
     * no email, which is the reason the subject key cannot be the address.
     */
    @Test
    void readsAnOnboardingStateChange() {
        SiblingDomainEvent event = parser.parseProfessionalEvent(bytes(onboardingState("acc-1", "COMPLETED"))).orElseThrow();

        assertThat(event.state()).isEqualTo("COMPLETED");
        assertThat(event.subjectKey()).isEqualTo("acc-1");
        assertThat(event.email()).isNull();
        assertThat(event.activated()).as("progressing through onboarding is not an account status change").isFalse();
    }

    /**
     * A numeric {@code occurredAt} is read rather than ignored.
     *
     * <p>hc-patient serialises a real {@code Instant}, and whether that comes out as an ISO string or
     * an epoch number is a Jackson setting in a repository this one does not own. Reading only the
     * string form would not fail — it would fall back to "now", pushing the watermark to the present
     * so that every genuinely later event was then discarded as stale. The consumer would appear to
     * have stopped, with nothing in the log.
     */
    @Test
    void readsAnEpochTimestampAsWellAsAnIsoOne() {
        String numeric = accountCreated(PATIENT_EMAIL, null, false).replace("\"occurredAt\":null", "\"occurredAt\":1788249600.000000000");

        assertThat(parser.parsePatientEvent(bytes(numeric), null).orElseThrow().occurredAt()).isEqualTo(Instant.ofEpochSecond(1788249600L));
    }

    /**
     * A frame with no readable {@code occurredAt} is ignored rather than stamped "now".
     *
     * <p><b>Watermark poisoning, and it is the failure the old fallback caused rather than avoided.</b>
     * {@code occurredAt} is what {@code DirectoryLink.lastEventAt} is set from, and anything strictly
     * older than that is discarded. Stamping an unreadable frame with the present therefore freezes
     * its subject against every event older than the moment it arrived — and these consumer groups
     * read from the earliest offset, so during the backfill "now" is later than every frame in the
     * topic. One such frame would have discarded the rest of that person's history, silently, in the
     * middle of the read that history exists for.
     */
    @Test
    void ignoresAFrameWhoseTimestampItCannotRead() {
        assertThat(parser.parsePatientEvent(bytes(accountCreated(PATIENT_EMAIL, null, false)), null))
            .as("no occurredAt means no watermark to apply the event against")
            .isEmpty();
        assertThat(parser.parsePatientEvent(bytes(accountCreated(PATIENT_EMAIL, "the day before yesterday", false)), null))
            .as("an unparseable one is the same case, and used to be silently read as the present")
            .isEmpty();
        assertThat(parser.parseProfessionalEvent(bytes(registrationCreated("acc-1").replace("\"2026-09-01T08:00:00Z\"", "null"))))
            .as("the same rule on the other stream")
            .isEmpty();
    }

    /**
     * Anything unusable is ignored, never thrown.
     *
     * <p>An exception out of a Spring Cloud Stream consumer is retried and stalls the partition it
     * arrived on, so one bad frame would freeze every subject whose key hashes there. These topics
     * are other products' and carry types added without reference to this consumer.
     */
    @Test
    void ignoresEverythingItCannotUseInsteadOfThrowing() {
        assertThat(parser.parsePatientEvent(bytes("this is not json at all"), null)).isEmpty();
        assertThat(parser.parsePatientEvent(bytes("[1,2,3]"), null)).as("a JSON array is not an envelope").isEmpty();
        assertThat(parser.parsePatientEvent(new byte[0], null)).isEmpty();
        assertThat(parser.parsePatientEvent(bytes("{\"type\":\"AccountCreated\",\"subject\":{}}"), null))
            .as("no subject key means nothing to apply it to")
            .isEmpty();
        assertThat(parser.parsePatientEvent(bytes("{\"subject\":{\"email\":\"a@b.co\"}}"), null)).as("no type").isEmpty();

        assertThat(parser.parseProfessionalEvent(bytes("{}"))).isEmpty();
        assertThat(parser.parseProfessionalEvent(bytes("{\"eventType\":\"registration.created\",\"payload\":{}}")))
            .as("no accountId means nothing to key on")
            .isEmpty();
    }

    // --- the wire formats, copied from the two publishers -----------------------------------------

    private static String accountCreated(String email, String occurredAt, boolean activated) {
        return (
            "{\"eventId\":\"evt-1\",\"type\":\"AccountCreated\",\"version\":1," +
            "\"occurredAt\":" +
            (occurredAt == null ? "null" : "\"" + occurredAt + "\"") +
            ",\"source\":\"patientGateway\"," +
            "\"subject\":{\"email\":\"" +
            email +
            "\",\"login\":\"amensah\",\"patientId\":null}," +
            "\"data\":{\"authorities\":\"ROLE_USER\",\"langKey\":\"en\",\"activated\":" +
            activated +
            "}}"
        );
    }

    /** hc-patient's {@code CareAngelResource.nominate}, first frame, verbatim from that call site. */
    private static String careAngelNominated(String email) {
        return (
            "{\"eventId\":\"evt-angel\",\"type\":\"AccountCreated\",\"version\":1," +
            "\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"patientGateway\"," +
            "\"subject\":{\"email\":\"" +
            email +
            "\",\"login\":\"aangel\",\"patientId\":null}," +
            "\"data\":{\"authorities\":\"ROLE_USER,ROLE_ANGEL\",\"activated\":true,\"reason\":\"careAngelNomination\"}}"
        );
    }

    /** {@code DeletionRequestService.announce} — one type, a {@code change} discriminator. */
    private static String deletionRequest(String email, String change) {
        return (
            "{\"eventId\":\"evt-deletion\",\"type\":\"DeletionRequestChanged\",\"version\":1," +
            "\"occurredAt\":\"2026-09-01T12:00:00Z\",\"source\":\"hcPatientService\"," +
            "\"subject\":{\"email\":\"" +
            email +
            "\",\"login\":\"amensah\",\"patientId\":\"p-1234\"}," +
            "\"data\":{\"requestId\":\"req-1\",\"change\":\"" +
            change +
            "\"}}"
        );
    }

    /** A minimal well-formed envelope of an arbitrary type, for sweeping the disposition table. */
    private static String typed(String type, String email) {
        return (
            "{\"eventId\":\"evt-typed\",\"type\":\"" +
            type +
            "\",\"version\":1,\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"hcPatientService\"," +
            "\"subject\":{\"email\":\"" +
            email +
            "\",\"login\":\"amensah\",\"patientId\":null},\"data\":{}}"
        );
    }

    private static String professionalTyped(String eventType) {
        return (
            "{\"eventId\":\"evt-other\",\"eventType\":\"" +
            eventType +
            "\",\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"hc-professional-service\",\"actor\":\"system\"," +
            "\"payload\":{\"accountId\":\"acc-1\",\"alertType\":\"LICENCE_EXPIRING\"}}"
        );
    }

    private static String accountActivated(String email) {
        return (
            "{\"eventId\":\"evt-2\",\"type\":\"AccountActivated\",\"version\":1," +
            "\"occurredAt\":\"2026-09-01T09:00:00Z\",\"source\":\"patientGateway\"," +
            "\"subject\":{\"email\":\"" +
            email +
            "\",\"login\":\"amensah\",\"patientId\":null}," +
            "\"data\":{\"activatedAt\":\"2026-09-01T09:00:00Z\"}}"
        );
    }

    private static String onboardingStarted(String email, String patientId) {
        return (
            "{\"eventId\":\"evt-3\",\"type\":\"OnboardingStarted\",\"version\":1," +
            "\"occurredAt\":\"2026-09-01T10:00:00Z\",\"source\":\"hcPatientService\"," +
            "\"subject\":{\"email\":\"" +
            email +
            "\",\"login\":null,\"patientId\":\"" +
            patientId +
            "\"}," +
            "\"data\":{\"startedAt\":\"2026-09-01T10:00:00Z\"}}"
        );
    }

    private static String registrationCreated(String accountId) {
        return (
            "{\"eventId\":\"evt-4\",\"eventType\":\"registration.created\"," +
            "\"occurredAt\":\"2026-09-01T08:00:00Z\",\"source\":\"hc-professional-gateway\",\"actor\":\"anonymous\"," +
            "\"payload\":{\"accountId\":\"" +
            accountId +
            "\",\"login\":\"kboateng\",\"email\":\"k.boateng@example.com\"," +
            "\"langKey\":\"en\",\"origin\":\"self-service\"}}"
        );
    }

    private static String onboardingState(String accountId, String state) {
        return (
            "{\"eventId\":\"evt-5\",\"eventType\":\"onboarding.state\"," +
            "\"occurredAt\":\"2026-09-01T11:00:00Z\",\"source\":\"hc-professional-service\",\"actor\":\"admin\"," +
            "\"payload\":{\"accountId\":\"" +
            accountId +
            "\",\"state\":\"" +
            state +
            "\",\"applicationId\":\"app-1\",\"requestedRole\":\"NURSE\"}}"
        );
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
