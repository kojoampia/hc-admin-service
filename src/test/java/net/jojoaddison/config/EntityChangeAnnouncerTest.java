package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import net.jojoaddison.broker.AdminEntityEvent;
import net.jojoaddison.broker.OutboundEventPublisher;
import net.jojoaddison.domain.ServicePlan;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import net.jojoaddison.security.SecurityUtils;
import org.springframework.data.mongodb.core.mapping.event.AfterDeleteEvent;
import org.springframework.data.mongodb.core.mapping.event.AfterSaveEvent;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The shape of the {@code admin.event} frame, asserted at the producer — backlog item 112.
 *
 * <p>{@code AdminEntityEventIT} drives the same class through a real Mongo write and reads the frame
 * off the binder; this one reads what the class hands to {@link OutboundEventPublisher} and is where
 * the cases that are awkward to provoke through a database live: a delete that matched on something
 * other than {@code _id}, the {@code audit_log} exclusion, and a publisher that throws.
 *
 * <p><b>The mapper here is deliberately bare</b> — {@code new ObjectMapper().registerModule(new
 * JavaTimeModule())} — because that is exactly what {@code WebConfigurer.objectMapper()} is, and the
 * {@code occurredAt}-as-a-String finding in {@code AdminEntityEvent} only reproduces against a mapper
 * configured that way. A Spring-built mapper would disable {@code WRITE_DATES_AS_TIMESTAMPS} and hide
 * it. The same reasoning is in {@code PatientPlanVerificationServiceTest}.
 *
 * <p>The security context is set by hand rather than with {@code @WithMockUser}: that annotation is
 * applied by a Spring test execution listener, and nothing here boots a context — it would silently do
 * nothing, and the actor assertion would pass for the wrong reason.
 */
class EntityChangeAnnouncerTest {

    private static final Instant WHEN = Instant.parse("2026-09-18T09:15:30.500Z");

    private static final String BINDING = "admin-event-out-0";

    private final OutboundEventPublisher publisher = mock(OutboundEventPublisher.class);

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final EntityChangeAnnouncer announcer = new EntityChangeAnnouncer(
        publisher,
        objectMapper,
        Clock.fixed(WHEN, ZoneOffset.UTC)
    );

    @BeforeEach
    @AfterEach
    void clearTheContext() {
        // Cleared on both sides so a context left by another test cannot name the actor here, and this
        // one cannot name it somewhere else.
        SecurityContextHolder.clearContext();
    }

    /**
     * A save announces the entity, the action, the instant and the actor — and the binding it goes to.
     *
     * <p>The binding is a string, so a rename in {@code application.yml} is not a compile error: it is
     * a frame published to a binding nothing declares, which gets a dynamic destination named after
     * itself and is read by nobody. That is the failure {@code binding-out-0} had for the life of this
     * repository, and it is why the name is asserted rather than assumed.
     */
    @Test
    void announcesASaveWithTheEntityTheActionTheInstantAndTheActor() {
        announcer.onAfterSave(saveOf(plan("sp-1"), "service_plan"));

        JsonNode frame = frameSentToTheChannel();
        assertThat(frame.get("type").asText()).isEqualTo(AdminEntityEvent.TYPE);
        assertThat(frame.get("version").asInt()).isEqualTo(AdminEntityEvent.VERSION);
        assertThat(frame.get("source").asText()).isEqualTo(AdminEntityEvent.SOURCE);
        assertThat(frame.get("eventId").asText()).isNotBlank();
        assertThat(frame.get("subject").get("entityType").asText())
            .as("the domain class's simple name, not the collection — a consumer must not be coupled to this product's storage")
            .isEqualTo("ServicePlan");
        assertThat(frame.get("subject").get("entityId").asText()).isEqualTo("sp-1");
        assertThat(frame.get("data").get("action").asText()).isEqualTo("SAVED");
        assertThat(frame.get("data").get("actorAccountId").isNull())
            .as("no authenticated caller, so the actor is absent rather than a `system` placeholder")
            .isTrue();

        assertThat(frame.get("occurredAt").asText())
            .as("an ISO-8601 instant, not a number — see AdminEntityEvent.getOccurredAt; this reproduces on the shipped mapper")
            .isEqualTo("2026-09-18T09:15:30.500Z");
    }

    /**
     * <b>A delete announces too, and this is the half that is easy to lose.</b>
     *
     * <p>Spring Data MongoDB has a save callback and no delete callback, so a listener written against
     * callbacks rather than events silently covers only half of "entity CRUD" — and a consumer that
     * never hears about removals holds records this service no longer has, with nothing anywhere
     * reporting it.
     */
    @Test
    void announcesADeleteAsWell() {
        announcer.onAfterDelete(deleteOf(new Document("_id", "sp-1"), ServicePlan.class, "service_plan"));

        JsonNode frame = frameSentToTheChannel();
        assertThat(frame.get("data").get("action").asText()).isEqualTo("DELETED");
        assertThat(frame.get("subject").get("entityType").asText()).isEqualTo("ServicePlan");
        assertThat(frame.get("subject").get("entityId").asText()).isEqualTo("sp-1");
    }

    /**
     * A delete that carries no entity type falls back to the collection name rather than announcing a
     * null one.
     *
     * <p>Only a raw {@code mongoTemplate.remove(query, "collection")} produces this, and this service
     * does not do that anywhere today — every repository delete goes through
     * {@code SimpleMongoRepository}, which supplies the class. The branch is covered because an
     * uncovered fallback is a fallback nobody has run, and a null {@code entityType} is a frame no
     * consumer can route.
     *
     * <p>{@code Object.class} takes the same branch, and it is the more insidious of the two: this
     * listener is declared over {@code Object}, so a resolution that handed back the erased type would
     * announce every delete as an entity of type {@code "Object"} — routable, plausible, and wrong in
     * all three other products at once. A null at least looks broken.
     */
    @Test
    void aDeleteThatNamesNoUsefulTypeFallsBackToTheCollection() {
        announcer.onAfterDelete(deleteOf(new Document("_id", "sp-1"), null, "service_plan"));
        assertThat(frameSentToTheChannel().get("subject").get("entityType").asText()).isEqualTo("service_plan");

        reset(publisher);
        announcer.onAfterDelete(deleteOf(new Document("_id", "sp-1"), Object.class, "service_plan"));
        assertThat(frameSentToTheChannel().get("subject").get("entityType").asText()).isEqualTo("service_plan");
    }

    /**
     * A delete that matched on something other than {@code _id} announces a null id rather than nothing.
     *
     * <p>After the fact there is no document left to read, so the id genuinely is not knowable — and
     * saying so is better than suppressing the frame, which would make a removal indistinguishable from
     * a broker that dropped one.
     */
    @Test
    void announcesADeleteByCriteriaWithNoId() {
        announcer.onAfterDelete(deleteOf(new Document("code", "MELON"), ServicePlan.class, "service_plan"));

        JsonNode frame = frameSentToTheChannel();
        assertThat(frame.get("data").get("action").asText()).isEqualTo("DELETED");
        assertThat(frame.get("subject").get("entityId").isNull()).isTrue();
    }

    /**
     * <b>The frame carries identifiers and metadata and nothing else — item 110's rule.</b>
     *
     * <p>Asserted as the <em>field set</em> rather than as the absence of particular fields, because
     * the way this rule is lost is by somebody adding one useful-looking field to help a consumer. A
     * test naming the fields it does not want passes for every field nobody thought of; this one fails
     * on any addition, which is the point.
     *
     * <p>The negative below it is the concrete half: a plan whose name and code are on the saved
     * document, and neither string anywhere in the bytes.
     */
    @Test
    void theFrameCarriesNoChangedFieldValues() {
        ServicePlan plan = plan("sp-1");
        plan.setName("Mangosteen tier");
        plan.setCode("MANGOSTEEN");

        announcer.onAfterSave(saveOf(plan, "service_plan"));

        String payload = payloadSentToTheChannel();
        assertThat(payload)
            .as("a channel carrying entity contents rebuilds the local mirrors item 107 is removing")
            .doesNotContain("Mangosteen tier")
            .doesNotContain("MANGOSTEEN");

        JsonNode frame = readTree(payload);
        assertThat(keysOf(frame))
            .as("the seven-component envelope hc-patient's and hc-vendor's producers already carry")
            .containsExactlyInAnyOrder("eventId", "type", "version", "occurredAt", "source", "subject", "data");
        assertThat(keysOf(frame.get("subject"))).containsExactlyInAnyOrder("entityType", "entityId");
        assertThat(keysOf(frame.get("data"))).containsExactlyInAnyOrder("action", "actorAccountId");
    }

    /**
     * The partition key is the entity, qualified by its collection.
     *
     * <p>Without a key Kafka scatters a producer's frames across partitions and the order they are
     * consumed in is not the order they were produced in — so a delete can reach a consumer before the
     * save it follows, leaving them holding a record this service has removed. Qualified by the
     * collection because ids here are not globally unique: the seed uses literals like {@code a13}.
     *
     * <p><b>And the fifth argument is null on purpose.</b> {@code patientKey} is hc-patient's spelling
     * for their own topic; repeating it on a frame about a service plan would claim the subject is a
     * patient. {@code OutboundEventPublisherTest} pins that the four-argument form still sends it, so
     * {@code patient-events-plan} is unchanged.
     */
    @Test
    void keysTheFrameOnTheEntityAndSendsNoPatientKeyHeader() {
        announcer.onAfterSave(saveOf(plan("sp-1"), "service_plan"));

        verify(publisher).publish(eq(BINDING), anyString(), anyString(), eq("ServicePlan/sp-1"), eq((String) null));
    }

    /**
     * {@code audit_log} announces nothing — the one exclusion, and the same one the audit trail has.
     *
     * <p>Every change already writes a row there, so announcing it would double every frame on the
     * channel and pair each real one with a frame about this service's own bookkeeping, which a
     * consumer cannot tell apart without knowing our internals.
     */
    @Test
    void theAuditTrailsOwnWritesAreNotAnnounced() {
        announcer.onAfterSave(saveOf(plan("al-1"), "audit_log"));
        announcer.onAfterDelete(deleteOf(new Document("_id", "al-1"), ServicePlan.class, "audit_log"));

        verifyNoInteractions(publisher);
    }

    /**
     * A frame that cannot be announced must not fail the write that provoked it.
     *
     * <p>This listener runs inside <em>every</em> save in the application, so an exception escaping it
     * is not one failed announcement — it is a failed business operation, for a reason that has nothing
     * to do with the operation. The change is already committed by the time this runs; there is nothing
     * to unwind and nobody to tell but the log.
     */
    @Test
    void aFailureToAnnounceNeverFailsTheWrite() {
        OutboundEventPublisher throwing = mock(OutboundEventPublisher.class);
        doThrow(new IllegalStateException("no publisher")).when(throwing).publish(anyString(), anyString(), anyString(), any(), any());
        EntityChangeAnnouncer overABrokenPublisher = new EntityChangeAnnouncer(
            throwing,
            objectMapper,
            Clock.fixed(WHEN, ZoneOffset.UTC)
        );

        assertThatCode(() -> overABrokenPublisher.onAfterSave(saveOf(plan("sp-1"), "service_plan"))).doesNotThrowAnyException();
    }

    /**
     * <b>The actor is the gateway account id from the {@code uid} claim, and never the login.</b>
     *
     * <p>This is the assertion that would have caught the first draft of this class, which put
     * {@code SecurityUtils.getCurrentUserLogin()} on the wire. Item 43 established that the login space
     * here is small and enumerable, so a login is identifying content; both sibling producers cite that
     * item by number when ruling one out, and item 107 D1 makes {@code accountId} the estate's join key.
     *
     * <p>Driven through a real {@code JwtAuthenticationToken} rather than a
     * {@code UsernamePasswordAuthenticationToken}, because the claim is only reachable from the former —
     * and because a test that authenticates with the latter is what let
     * {@code ProfessionalServiceClient}'s token relay pass while failing on every real request.
     */
    @Test
    void namesTheCallersAccountIdAndNeverTheirLogin() {
        Jwt token = Jwt.withTokenValue("t")
            .header("alg", "none")
            .subject("operator")
            .claim(SecurityUtils.USER_ID_KEY, "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12")
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token, List.of()));

        announcer.onAfterSave(saveOf(plan("sp-1"), "service_plan"));

        JsonNode data = frameSentToTheChannel().get("data");
        assertThat(data.get("actorAccountId").asText()).isEqualTo("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12");
        assertThat(keysOf(data)).as("the login must not appear under any key").doesNotContain("actor", "login");
    }

    /**
     * A token carrying no {@code uid} claim leaves the actor absent rather than falling back to the
     * login it does carry.
     *
     * <p>Reachable in production: the three gateways share one signing key, so a token minted by a
     * sibling gateway that spells the claim differently arrives here authenticated and without it. The
     * fallback that must not exist is the tempting one — {@code sub} is right there and holds a login.
     */
    @Test
    void aTokenWithNoAccountIdClaimLeavesTheActorAbsent() {
        Jwt token = Jwt.withTokenValue("t").header("alg", "none").subject("operator").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token, List.of()));

        announcer.onAfterSave(saveOf(plan("sp-1"), "service_plan"));

        String payload = payloadSentToTheChannel();
        assertThat(readTree(payload).get("data").get("actorAccountId").isNull()).isTrue();
        assertThat(payload).as("the subject claim is a login and must not be substituted for the account id").doesNotContain("operator");
    }

    private String payloadSentToTheChannel() {
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(publisher).publish(eq(BINDING), payload.capture(), anyString(), any(), any());
        return payload.getValue();
    }

    private JsonNode frameSentToTheChannel() {
        return readTree(payloadSentToTheChannel());
    }

    private List<String> keysOf(JsonNode node) {
        return node.properties().stream().map(Map.Entry::getKey).toList();
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("the announced frame is not JSON: " + json, e);
        }
    }

    private ServicePlan plan(String id) {
        ServicePlan plan = new ServicePlan();
        plan.setId(id);
        return plan;
    }

    private AfterSaveEvent<Object> saveOf(Object entity, String collection) {
        return new AfterSaveEvent<>(entity, new Document(), collection);
    }

    /**
     * {@code AfterDeleteEvent}'s type parameter is tied to its {@code Class<T>} argument, so a wildcard
     * cannot be inferred to {@code Object}. The cast is confined here: the listener under test is
     * declared over {@code Object} precisely so that it catches every collection, and the only thing it
     * reads off this argument is the simple name.
     */
    @SuppressWarnings("unchecked")
    private AfterDeleteEvent<Object> deleteOf(Document query, Class<?> type, String collection) {
        return new AfterDeleteEvent<>(query, (Class<Object>) type, collection);
    }
}
