package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.service.dto.SiblingDomainEvent;
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
            .as("a care-angel account is created already activated, and says so in its data")
            .isTrue();
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
