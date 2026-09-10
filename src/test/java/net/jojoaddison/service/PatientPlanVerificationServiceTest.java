package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import net.jojoaddison.broker.OutboundEventPublisher;
import net.jojoaddison.broker.PlanVerificationEvent;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * What a real hc-patient receives when an administrator verifies a plan choice.
 *
 * <h2>Why this asserts the serialised string rather than the object</h2>
 *
 * <p>hc-patient does not receive a {@link PlanVerificationEvent} — they receive bytes, and bind them
 * to <i>their</i> {@code PatientEvent}. So every assertion here is on the JSON, because that is the
 * only thing the two repositories actually share. An assertion on the object would pass while a
 * field name, a nesting level or a date format made the frame unbindable at the far end, which is
 * precisely the failure item 47 recorded: a reader written from the backlog entry, agreeing with its
 * own tests, and matching nothing the producer sent.
 *
 * <p>The shape here was taken from hc-patient's {@code PlanVerificationConsumer} and
 * {@code PatientEvent}, read on 2026-09-10, and each assertion below names the refusal it prevents.
 */
class PatientPlanVerificationServiceTest {

    private static final String BINDING = "plan-verification-out-0";

    private static final Instant AT = Instant.parse("2026-09-10T09:15:30.500Z");

    private final OutboundEventPublisher publisher = mock(OutboundEventPublisher.class);

    /**
     * A <b>bare</b> mapper — which is not a simplification, it is <b>exactly the application's</b>.
     *
     * <p>{@code WebConfigurer.objectMapper()} is an explicit {@code @Bean} returning
     * {@code new ObjectMapper().registerModule(new JavaTimeModule())}, so Boot's builder backs off and
     * {@code WRITE_DATES_AS_TIMESTAMPS} stays <b>on</b>. This line started as an attempt to mirror
     * Boot's configuration, and finding that there is no Boot configuration to mirror is what produced
     * {@code PlanVerificationEvent.occurredAt}'s preformatted {@code String}: as a typed
     * {@code Instant} this stack emits {@code 1.7890317305E9} today, not in some future the settings
     * might drift into.
     *
     * <p>It stays bare <b>as the proof</b>: the assertions below pass under the least-configured
     * Jackson there is, so no mapper setting can be what makes them pass — and none can be what makes
     * them stop.
     */
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final PatientPlanVerificationService service = new PatientPlanVerificationService(
        publisher,
        objectMapper,
        Clock.fixed(AT, ZoneOffset.UTC)
    );

    /**
     * <b>The subject is lowercased, which is the assertion most likely to ship silently.</b>
     *
     * <p>hc-patient joins an acknowledgement to a patient by their address, and every frame in the
     * estate is keyed on the lowercased form. A frame keyed {@code Ama.Mensah@Example.COM} is
     * accepted, committed and applied to nobody — a healthy producer, a healthy consumer, no lag and
     * no dead letter. That is item 26's wrong join key, and it has cost this estate once already.
     *
     * <p>Fed through a link whose stored key is mixed case on purpose. That is a reachable state
     * rather than a contrived one: {@code SiblingEventParser.normaliseKey} lowercases everything that
     * arrives over the broker, but {@code hc-admin-ms-data.json} reaches the collection through
     * {@code saveAll} without passing the parser at all.
     */
    @Test
    void lowercasesTheSubjectInBothThePayloadAndThePartitionKey() throws Exception {
        service.record(link("Ama.Mensah@Example.COM", "MELON"));

        assertThat(payload().path("subject").path("email").asText())
            .as("hc-patient lowercases before looking up; a mixed-case subject names nobody there")
            .isEqualTo("ama.mensah@example.com");
        assertThat(partitionKey())
            .as("the partition key must be the same lowercased value, or two frames about one person split partitions")
            .isEqualTo("ama.mensah@example.com");
    }

    /** Surrounding whitespace is normalised with the case, for the same reason and by the same call. */
    @Test
    void trimsTheSubjectAsWellAsLoweringIt() throws Exception {
        service.record(link("  Kofi@Mail.GH  ", "PEAR"));

        assertThat(payload().path("subject").path("email").asText()).isEqualTo("kofi@mail.gh");
    }

    /**
     * <b>The payload is exactly one field, and it is the tier hc-patient sent.</b>
     *
     * <p>{@code isVerified} and {@code MembershipID} were both specified and both dropped. Asserted
     * as the {@code data} object's whole field set rather than as "contains plan", because the
     * failure worth catching is a field <em>added</em> — a helpful {@code membershipId} would be
     * accepted by their reader, ignored, and quietly make the contract two things.
     */
    @Test
    void sendsThePlanCodeAndNothingElseInTheData() throws Exception {
        service.record(link("kofi@mail.gh", "PAWPAW"));

        JsonNode data = payload().path("data");
        assertThat(data.properties().stream().map(java.util.Map.Entry::getKey))
            .as("item 54 settled the payload at { Plan }; a second field makes it a different contract")
            .containsExactly("plan");
        assertThat(data.path("plan").asText())
            .as("the raw code hc-patient sent, never this catalogue's own name for the tier")
            .isEqualTo("PAWPAW");
    }

    /**
     * The key spelling their reader is to be collapsed onto.
     *
     * <p>{@code PlanVerificationConsumer.planCodeIn} accepts {@code plan} or {@code planCode}, and a
     * string or an object with a {@code code}, explicitly as a temporary tolerance until this side
     * landed. This pins which of the four it should keep.
     */
    @Test
    void namesThePlanAsAFlatStringUnderPlanRatherThanPlanCode() throws Exception {
        service.record(link("kofi@mail.gh", "MELON"));

        assertThat(payload().path("data").path("plan").isTextual()).as("a flat string, not an object carrying a code").isTrue();
        assertThat(payload().path("data").has("planCode")).isFalse();
    }

    /**
     * <b>The envelope their consumer refuses without.</b>
     *
     * <p>Three of these are checked by {@code PlanVerificationConsumer} before it ever reads the
     * plan: a blank {@code eventId} is {@code NO_EVENT_ID}, a missing {@code subject.email} is
     * {@code NO_SUBJECT_KEY}. Item 54 describes the payload as {@code { Plan }} and says nothing
     * about any of this, which is why it is asserted here rather than assumed.
     */
    @Test
    void carriesTheEnvelopeHcPatientBindsTo() throws Exception {
        service.record(link("kofi@mail.gh", "PEAR"));

        JsonNode frame = payload();
        assertThat(frame.path("eventId").asText()).as("their idempotency key and the _id of their ledger row").isNotBlank();
        assertThat(frame.path("type").asText()).isEqualTo("PlanVerified");
        assertThat(frame.path("version").asInt()).isEqualTo(1);
        assertThat(frame.path("source").asText()).isEqualTo("hcAdminService");
        assertThat(frame.path("occurredAt").asText())
            .as("an ISO instant; their Jackson reads a number too, but only one form has been measured")
            .isEqualTo("2026-09-10T09:15:30.500Z");
    }

    /**
     * <b>No status is sent</b>, and this is not an oversight.
     *
     * <p>Their {@code assertActivating} refuses any status that is not {@code ACTIVE}, and their
     * vocabulary has five values rather than the six item 54 records — {@code VERIFIED} was ruled out
     * on their side on 2026-09-08. Sending {@code VERIFIED} because this console's screen says
     * "verify" would be refused {@code STATUS_NOT_IN_THIS_SERVICES_VOCABULARY} on every frame.
     */
    @Test
    void namesNoStatus() throws Exception {
        service.record(link("kofi@mail.gh", "PEAR"));

        assertThat(payload().path("data").has("status")).isFalse();
        assertThat(payload().has("status")).isFalse();
    }

    /** Two presses are two events, because their ledger keys on the id and would ignore a repeat. */
    @Test
    void givesEachEmissionItsOwnEventId() throws Exception {
        service.record(link("kofi@mail.gh", "PEAR"));
        service.record(link("kofi@mail.gh", "PEAR"));

        ArgumentCaptor<String> payloads = ArgumentCaptor.forClass(String.class);
        verify(publisher, org.mockito.Mockito.times(2)).publish(anyString(), payloads.capture(), anyString(), anyString());

        String first = objectMapper.readTree(payloads.getAllValues().get(0)).path("eventId").asText();
        String second = objectMapper.readTree(payloads.getAllValues().get(1)).path("eventId").asText();
        assertThat(first).as("a reused id is discarded by hc-patient as a redelivery it has already applied").isNotEqualTo(second);
    }

    /** The declared binding, whose {@code destination:} is what puts the frame on hc-patient's topic. */
    @Test
    void publishesOnThePlanVerificationBinding() {
        service.record(link("kofi@mail.gh", "MELON"));

        ArgumentCaptor<String> binding = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(binding.capture(), anyString(), anyString(), anyString());
        assertThat(binding.getValue()).isEqualTo(BINDING);
    }

    /**
     * <b>Nothing is stored, and this is the structural proof of it.</b>
     *
     * <p>hc-admin holds no plan state: hc-patient owns {@code Membership.status} and the published
     * event is the record. The service is constructed here with a publisher, a mapper and a clock —
     * <b>no repository and no {@code MongoTemplate}</b> — so there is nothing it could write to. A
     * test that counted rows would pass just as well against a service that wrote to a collection the
     * test did not know about; this cannot.
     *
     * <p>The behavioural half is in {@code PatientPlanVerificationResourceIT}, which verifies through
     * a real database that a verification leaves the link untouched.
     */
    @Test
    void holdsNothingItCouldWriteTo() {
        assertThat(PatientPlanVerificationService.class.getDeclaredFields())
            .as("a repository here would mean this service had somewhere to store plan state, which it must not")
            .noneMatch(field -> field.getType().getName().contains("Repository") || field.getType().getName().contains("MongoTemplate"));
    }

    /** The subject the publisher logs is a digest, never the address — item 43's rule. */
    @Test
    void namesThePatientToTheLogAsADigestRatherThanAnAddress() {
        service.record(link("ama.mensah@example.com", "MELON"));

        ArgumentCaptor<String> logSubject = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(anyString(), anyString(), logSubject.capture(), anyString());
        assertThat(logSubject.getValue()).doesNotContain("ama.mensah@example.com").contains("subj-");
    }

    private JsonNode payload() throws Exception {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(anyString(), payload.capture(), anyString(), anyString());
        return objectMapper.readTree(payload.getValue());
    }

    private String partitionKey() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(anyString(), anyString(), anyString(), key.capture());
        return key.getValue();
    }

    private static DirectoryLink link(String externalKey, String planCode) {
        DirectoryLink link = new DirectoryLink();
        link.setId("dl-test");
        link.setSource(DirectorySource.HC_PATIENT);
        link.setSubjectKind(DirectorySubjectKind.PATIENT);
        link.setExternalKey(externalKey);
        link.setPlanCode(planCode);
        link.setPlanStatus("PENDING");
        return link;
    }
}
