package net.jojoaddison.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.ProfessionalVerification;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ProfessionalVerificationRepository;
import net.jojoaddison.service.PatientPlanVerificationService;
import net.jojoaddison.service.ProfessionalVerificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Both return legs, on {@code admin.event}, without either old topic losing a frame. Backlog item 145.
 *
 * <h2>What this file is for, and why it is one file rather than two</h2>
 *
 * <p>Item 145 step 1 makes a single claim — <b>the two return legs are now also on this product's own
 * channel, and nothing that shipped before has changed</b> — and that claim spans two services. Split
 * across their two test classes it would be two half-claims, and the half most likely to be broken
 * silently is the one this file exists to hold: <b>additivity</b>. A refactor that "moves" a leg
 * instead of duplicating it leaves every per-service assertion passing.
 *
 * <p>Assertions are on the <b>serialised JSON</b>, not on the objects, for the reason
 * {@code PatientPlanVerificationServiceTest} gives at length: three other products bind these bytes,
 * and an object-level assertion passes while a field name or a nesting level makes the frame
 * unreadable at the far end. That is item 47's recorded failure — a reader written from prose,
 * agreeing with its own tests, matching nothing the producer sent.
 *
 * <p>The mapper is deliberately <b>bare</b>, matching {@code WebConfigurer.objectMapper()}, so no
 * Jackson setting can be what makes these pass or stop passing.
 */
class ReturnLegsOnAdminEventTest {

    private static final String ADMIN_EVENT = "admin-event-out-0";
    private static final String PLAN_TOPIC = "plan-verification-out-0";
    private static final String VERIFICATION_TOPIC = "verification-out-0";
    private static final String SSE = "binding-out-0";

    private static final Instant AT = Instant.parse("2026-09-25T09:15:30.500Z");

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final OutboundEventPublisher publisher = mock(OutboundEventPublisher.class);
    private final Clock clock = Clock.fixed(AT, ZoneOffset.UTC);

    // --- hc-patient's leg — a migration: the frame, the envelope and the consumer all exist ---------

    /**
     * ⭐ <b>The additivity assertion, and the one this file exists for.</b>
     *
     * <p>{@code patient-events-plan} has a live consumer today. Removing it before hc-patient's item 47
     * reads this channel and their lag reaches zero is an outage in their product caused by a commit in
     * ours — so this asserts the old send is <b>still made</b>, not merely that the new one is.
     */
    @Test
    void thePlanLegStillPublishesToTheOldTopicAsWellAsTheNewChannel() {
        planService().record(link());

        verify(publisher).publish(org.mockito.ArgumentMatchers.eq(PLAN_TOPIC), anyString(), anyString(), anyString());
        verify(publisher).publish(
            org.mockito.ArgumentMatchers.eq(ADMIN_EVENT),
            anyString(),
            anyString(),
            anyString(),
            org.mockito.ArgumentMatchers.isNull()
        );
    }

    /**
     * The envelope {@code admin.event}'s readers dispatch on — architect's D1, 2026-09-25.
     *
     * <p>{@code subject} is {@code (entityType, entityId)} exactly as an {@link AdminEntityEvent} is,
     * which is the whole point of the decision: one channel, one meaning for {@code subject}. The
     * alternative — publishing hc-patient's shape here — would have made {@code subject} mean two
     * things under two {@code type}s.
     */
    @Test
    void thePlanFrameCarriesTheChannelsOwnEnvelopeAndNamesTheLink() throws Exception {
        planService().record(link());

        JsonNode frame = objectMapper.readTree(adminEventPayload());

        assertThat(frame.path("type").asText()).isEqualTo("PlanVerified");
        assertThat(frame.path("source").asText()).isEqualTo("hcAdminService");
        assertThat(frame.path("version").asInt()).isEqualTo(1);
        assertThat(frame.path("occurredAt").asText()).isEqualTo("2026-09-25T09:15:30.500Z");
        assertThat(frame.path("eventId").asText()).isNotBlank();

        assertThat(frame.path("subject").path("entityType").asText()).isEqualTo("DirectoryLink");
        assertThat(frame.path("subject").path("entityId").asText()).isEqualTo("dl-p12");

        assertThat(frame.path("data").path("plan").asText()).isEqualTo("PREMIUM");
        assertThat(frame.path("data").path("subjectKey").asText()).isEqualTo("kojo@jac.net");
    }

    /**
     * The subject key is lowercased on this frame too, and that is not incidental.
     *
     * <p>hc-patient joins on a lowercased address. A frame keyed {@code Kojo@JAC.net} is accepted,
     * committed, and applied to no membership — item 26's wrong join key, with a healthy producer, a
     * healthy consumer, no lag and no dead letter. The old frame already guards this; the new one is a
     * second place it can be got wrong.
     */
    @Test
    void thePlanFramesSubjectKeyIsLowercased() throws Exception {
        DirectoryLink shouty = link();
        shouty.setExternalKey("  Kojo@JAC.net  ");

        planService().record(shouty);

        assertThat(objectMapper.readTree(adminEventPayload()).path("data").path("subjectKey").asText()).isEqualTo("kojo@jac.net");
    }

    /**
     * ⚠ <b>Two channels, two identities.</b>
     *
     * <p>The old frame's {@code eventId} is hc-patient's idempotency key — it becomes the {@code _id}
     * of their ledger row. Reusing it here would hand a future consumer of <em>this</em> channel a key
     * that is already spoken for by another product. The decision the two describe is joined by
     * {@code subjectKey}, not by sharing an id.
     */
    @Test
    void theTwoPlanFramesDoNotShareAnEventId() throws Exception {
        planService().record(link());

        String onAdminEvent = objectMapper.readTree(adminEventPayload()).path("eventId").asText();
        String onPlanTopic = objectMapper.readTree(planTopicPayload()).path("eventId").asText();

        assertThat(onAdminEvent).isNotBlank();
        assertThat(onPlanTopic).isNotBlank();
        assertThat(onAdminEvent).isNotEqualTo(onPlanTopic);
    }

    // --- hc-professional's leg — a construction: it had no envelope at all -------------------------

    /**
     * ⭐ <b>The D3 pin, and the assertion most likely to be undone by someone being helpful.</b>
     *
     * <p>{@code licenceNumber} is on the legacy frame and must <b>not</b> be on this one.
     * {@code professional-verification} reached nobody; {@code admin.event} is read by three products,
     * so carrying it would be a new exposure rather than a continued one. hc-professional owns the
     * licence number — sending theirs back carries no information they lack.
     *
     * <p>Asserted over the <b>whole serialised frame</b> rather than a named path, because a future
     * refactor could reintroduce it at a different nesting level and a path assertion would not see it.
     */
    @Test
    void theProfessionalFrameDoesNotCarryTheLicenceNumber() {
        professionalService().record(verification());

        assertThat(adminEventPayload()).doesNotContain("licence").doesNotContain("Licence").doesNotContain("GMC-12345");
    }

    /** The same additivity claim as the plan leg: the old topic and the SSE fan-out both still fire. */
    @Test
    void theProfessionalLegStillPublishesToTheOldTopicAndTheSseFanOut() {
        professionalService().record(verification());

        verify(publisher).publish(org.mockito.ArgumentMatchers.eq(VERIFICATION_TOPIC), anyString(), anyString());
        verify(publisher).publish(org.mockito.ArgumentMatchers.eq(SSE), anyString(), anyString());
    }

    /**
     * The envelope this leg never had.
     *
     * <p>{@code VerificationEvent} carries no {@code eventId}, {@code type}, {@code occurredAt} or
     * {@code source} — it never needed them, because nothing consumed it. These assertions are the
     * difference between a migration and a construction.
     */
    @Test
    void theProfessionalFrameHasAnEnvelopeForTheFirstTime() throws Exception {
        professionalService().record(verification());

        JsonNode frame = objectMapper.readTree(adminEventPayload());

        assertThat(frame.path("type").asText()).isEqualTo("ProfessionalVerified");
        assertThat(frame.path("source").asText()).isEqualTo("hcAdminService");
        assertThat(frame.path("version").asInt()).isEqualTo(1);
        assertThat(frame.path("occurredAt").asText()).isEqualTo("2026-09-25T09:15:30.500Z");
        assertThat(frame.path("eventId").asText()).isNotBlank();

        assertThat(frame.path("subject").path("entityType").asText()).isEqualTo("ProfessionalVerification");
        assertThat(frame.path("subject").path("entityId").asText()).isEqualTo("pv-1");

        assertThat(frame.path("data").path("status").asText()).isEqualTo("VERIFIED");
        assertThat(frame.path("data").path("professionalId").asText()).isEqualTo("p1");
    }

    // --- the channel's own rules, which both legs must obey ---------------------------------------

    /**
     * The partition key is {@code <EntityType>/<id>} for a command exactly as for a notification.
     *
     * <p>This is the property that decided D1. Two presses for one link stay ordered, <b>and</b> an
     * {@code EntityChanged} frame for that same record lands on the same partition — so a consumer
     * cannot see the record's change and the decision taken against it out of order.
     */
    @Test
    void bothFramesAreKeyedOnTheSubjectRecordLikeEveryOtherFrameOnTheChannel() {
        planService().record(link());
        assertThat(adminEventKey()).isEqualTo("DirectoryLink/dl-p12");

        org.mockito.Mockito.reset(publisher);

        professionalService().record(verification());
        assertThat(adminEventKey()).isEqualTo("ProfessionalVerification/pv-1");
    }

    /**
     * ⚠ No {@code patientKey} header on this channel, ever.
     *
     * <p>{@code OutboundEventPublisher} states the rule: a frame on this service's own channel is not
     * about a patient, and stamping hc-patient's convention on it would read as a defect to whoever
     * found it — including on the professional leg, where it would be simply false. The subject travels
     * in the payload, where a consumer reads it.
     */
    @Test
    void neitherFrameStampsAPatientKeyHeader() {
        planService().record(link());
        assertThat(adminEventHeaderName()).isNull();

        org.mockito.Mockito.reset(publisher);

        professionalService().record(verification());
        assertThat(adminEventHeaderName()).isNull();
    }

    // --- fixtures and captors ---------------------------------------------------------------------

    private PatientPlanVerificationService planService() {
        return new PatientPlanVerificationService(publisher, objectMapper, clock);
    }

    private ProfessionalVerificationService professionalService() {
        ProfessionalVerificationRepository verifications = mock(ProfessionalVerificationRepository.class);
        ProfessionalRepository professionals = mock(ProfessionalRepository.class);
        when(verifications.save(any(ProfessionalVerification.class))).thenAnswer(i -> i.getArgument(0));
        when(professionals.findById(anyString())).thenReturn(Optional.empty());
        return new ProfessionalVerificationService(verifications, professionals, clock, publisher, objectMapper);
    }

    private DirectoryLink link() {
        DirectoryLink link = new DirectoryLink();
        link.setId("dl-p12");
        link.setSource(DirectorySource.HC_PATIENT);
        link.setSubjectKind(DirectorySubjectKind.PATIENT);
        link.setExternalKey("kojo@jac.net");
        link.setPlanCode("PREMIUM");
        return link;
    }

    private ProfessionalVerification verification() {
        Professional professional = new Professional();
        professional.setId("p1");
        professional.setLicenceNumber("GMC-12345");

        ProfessionalVerification verification = new ProfessionalVerification();
        verification.setId("pv-1");
        verification.setStatus(VerificationStatus.VERIFIED);
        verification.setProfessional(professional);
        return verification;
    }

    /** The five-argument publish is the {@code admin.event} one; the legacy sends use three or four. */
    private ArgumentCaptor<String> captureAdminEvent(int argumentIndex) {
        ArgumentCaptor<String> binding = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> header = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(binding.capture(), payload.capture(), subject.capture(), key.capture(), header.capture());
        assertThat(binding.getValue()).isEqualTo(ADMIN_EVENT);
        return switch (argumentIndex) {
            case 1 -> payload;
            case 3 -> key;
            case 4 -> header;
            default -> throw new IllegalArgumentException("no captor for argument " + argumentIndex);
        };
    }

    private String adminEventPayload() {
        return captureAdminEvent(1).getValue();
    }

    private String adminEventKey() {
        return captureAdminEvent(3).getValue();
    }

    private String adminEventHeaderName() {
        return captureAdminEvent(4).getValue();
    }

    private String planTopicPayload() {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(org.mockito.ArgumentMatchers.eq(PLAN_TOPIC), payload.capture(), anyString(), anyString());
        return payload.getValue();
    }
}
