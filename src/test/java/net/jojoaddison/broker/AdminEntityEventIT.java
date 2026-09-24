package net.jojoaddison.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.ServicePlan;
import net.jojoaddison.repository.ServicePlanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * An ordinary entity change reaches {@code admin.event} — backlog item 112, end to end.
 *
 * <p>{@code EntityChangeAnnouncerTest} asserts what the producer hands to
 * {@link OutboundEventPublisher}. This class asserts the half that one cannot: that an administrator
 * pressing Save on a screen this service already had, through a resource nobody modified for this item,
 * <b>puts a frame on the channel</b> — because the whole design of item 112 is that no resource is
 * instrumented by hand and therefore no resource can be forgotten.
 *
 * <p>{@code ServicePlan} is the entity under test for no reason other than that it is cheap to create
 * and carries two obvious strings to look for the absence of. Nothing here is specific to it; the
 * producer is a Mongo event listener over {@code Object}.
 *
 * <p><b>What this class can and cannot show</b>, as {@code VerificationEventIT} records for the
 * neighbouring channel. {@code OutputDestination.receive(timeout, name)} resolves by destination when
 * the binding declares one, so receiving on {@code admin.event} here proves the frame is produced and
 * carries the right payload — the test profile declares that destination itself. The destination that
 * actually <em>ships</em> is pinned in {@code ConfigurationBindingTest}, which reads
 * {@code config/application.yml} off disk. {@code TestChannelBinderConfiguration} replaces the Kafka
 * binder with an in-memory one, so none of this needs a broker.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "admin")
@ImportAutoConfiguration(TestChannelBinderConfiguration.class)
class AdminEntityEventIT {

    private static final String CHANNEL = "admin.event";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private OutputDestination output;

    @Autowired
    private ServicePlanRepository servicePlanRepository;

    @BeforeEach
    void drainWhatThisTestDidNotProduce() {
        servicePlanRepository.deleteAll();
        output.clear();
        // Every write in this application now announces, including the delete just above and anything
        // the context published while starting — and it announces on a publisher thread, so clear()
        // alone races them. Drained until quiet instead, because a stale frame here does not fail a
        // test: it passes one, against the wrong message.
        while (output.receive(200, CHANNEL) != null) {
            // discard
        }
    }

    @AfterEach
    void tearDown() {
        servicePlanRepository.deleteAll();
    }

    /**
     * Creating a record through its own resource announces the entity and the action.
     *
     * <p><b>{@code actorAccountId} is absent here and that is the truth about this test rather than a
     * gap.</b> {@code @WithMockUser} builds a {@code UsernamePasswordAuthenticationToken}, whose
     * principal is a {@code User} and not a {@code Jwt}, so {@code SecurityUtils.getCurrentUserId()}
     * finds no {@code uid} claim — exactly as it would for a real token from a sibling gateway that
     * spells the claim differently. The populated case is covered in {@code EntityChangeAnnouncerTest}
     * against a real {@code Jwt}.
     *
     * <p>That split is deliberate and is this repository's own lesson: {@code PatientServiceClient}'s
     * token relay passed a test built on a {@code UsernamePasswordAuthenticationToken} while failing on
     * every real request, because the mock's credentials were a {@code String} and a resource server's
     * are a decoded {@code Jwt}. A test that mocks the wrong authentication type proves the wrong thing.
     */
    @Test
    void aCreatedEntityIsAnnouncedOnTheChannel() throws Exception {
        String id = createAPlan();

        JsonNode frame = frameFromTheChannel();
        assertThat(frame.get("type").asString()).isEqualTo(AdminEntityEvent.TYPE);
        assertThat(frame.get("version").asInt()).isEqualTo(AdminEntityEvent.VERSION);
        assertThat(frame.get("source").asString()).isEqualTo(AdminEntityEvent.SOURCE);
        assertThat(frame.get("eventId").asString()).isNotBlank();
        assertThat(frame.get("occurredAt").asString())
            .as("an ISO-8601 instant, never the scientific-notation double a bare ObjectMapper writes for a typed Instant")
            .endsWith("Z");
        assertThat(frame.get("subject").get("entityType").asString())
            .as("the domain class's simple name, not the `service_plan` collection this happens to live in")
            .isEqualTo("ServicePlan");
        assertThat(frame.get("subject").get("entityId").asString()).isEqualTo(id);
        assertThat(frame.get("data").get("action").asString()).isEqualTo(AdminEntityEvent.SAVED);
        // The key set, not the value: Jackson's isNull() on a missing node cannot tell an absent key
        // from a present-with-null one, and that distinction is the whole of item 129. The decided
        // shape is ABSENT — asserted by listing the keys `data` does carry.
        assertThat(keysOf(frame.get("data")))
            .as("see this method's javadoc — @WithMockUser carries no uid claim, so the actor key is omitted (item 129)")
            .containsExactlyInAnyOrder("action");
    }

    /**
     * <b>And so does deleting one.</b>
     *
     * <p>A consumer that hears about creations and not removals accumulates records this service no
     * longer holds, and nothing on either side reports it — the producer is healthy, the group is not
     * lagging, and the far end simply has more rows than we do.
     */
    @Test
    void aDeletedEntityIsAnnouncedOnTheChannelToo() throws Exception {
        String id = createAPlan();
        // The create's own frame, taken off the channel rather than cleared: clear() runs on this
        // thread and the announcement runs on the publisher's, so clearing here can discard nothing
        // and leave the SAVE to be read below as the DELETE.
        published();

        mvc.perform(delete("/api/service-plans/{id}", id)).andExpect(status().isNoContent());

        JsonNode frame = frameFromTheChannel();
        assertThat(frame.get("data").get("action").asString()).isEqualTo(AdminEntityEvent.DELETED);
        assertThat(frame.get("subject").get("entityType").asString()).isEqualTo("ServicePlan");
        assertThat(frame.get("subject").get("entityId").asString()).isEqualTo(id);
    }

    /**
     * <b>The frame carries no changed field values, asserted on the wire.</b>
     *
     * <p>This is the rule most easily lost later, so it is asserted twice and from two directions: the
     * saved plan's name and tier code appear nowhere in the bytes, and the field set is exactly the
     * seven envelope keys with two under {@code subject} and — this write being actorless, see
     * {@link #aCreatedEntityIsAnnouncedOnTheChannel} — only {@code action} under {@code data}, because
     * item 129 has an actorless frame omit {@code actorAccountId} rather than carry it as null. The
     * populated-actor key set is pinned in {@code EntityChangeAnnouncerTest}. The second assertion is
     * the one that survives somebody adding a field — a test that names what it does not want passes
     * for everything nobody thought of.
     */
    @Test
    void theFrameCarriesIdentifiersAndMetadataAndNothingElse() throws Exception {
        createAPlan();

        Message<byte[]> published = published();
        String payload = new String(published.getPayload(), StandardCharsets.UTF_8);

        assertThat(payload)
            .as("identifying content must not be on the wire, and a channel carrying entity contents rebuilds the mirrors item 107 removes")
            .doesNotContain("Mangosteen tier")
            .doesNotContain("MANGOSTEEN");

        JsonNode frame = om.readTree(payload);
        assertThat(keysOf(frame)).containsExactlyInAnyOrder("eventId", "type", "version", "occurredAt", "source", "subject", "data");
        assertThat(keysOf(frame.get("subject"))).containsExactlyInAnyOrder("entityType", "entityId");
        assertThat(keysOf(frame.get("data")))
            .as("no actor behind this write, so the key is absent — not null — per item 129")
            .containsExactlyInAnyOrder("action");
    }

    /**
     * The frame is keyed on the entity, and carries no header belonging to another product's topic.
     *
     * <p>The key is asserted as <b>bytes</b> for the reason {@code OutboundEventPublisherTest} gives:
     * the binder's key serialiser is a {@code ByteArraySerializer} by default, so a {@code String}
     * there is a {@code ClassCastException} inside the producer at send time, which no test without a
     * real broker would otherwise see.
     */
    @Test
    void theFrameIsKeyedOnTheEntityAndCarriesNoPatientKey() throws Exception {
        String id = createAPlan();

        Message<byte[]> published = published();
        assertThat((byte[]) published.getHeaders().get(KafkaHeaders.KEY))
            .as("two changes to one record must not overtake each other on the way to a consumer")
            .isEqualTo(("ServicePlan/" + id).getBytes(StandardCharsets.UTF_8));
        assertThat(published.getHeaders().get(OutboundEventPublisher.PATIENT_KEY_HEADER))
            .as("patientKey is hc-patient's convention for their own topic; on a frame about a service plan it would be a lie")
            .isNull();
    }

    /**
     * <b>The audit trail's own rows are not announced, which is what keeps this one change one frame.</b>
     *
     * <p>{@code AuditLogCallback} writes an {@code AuditLog} for every save, so without the exclusion a
     * single create would put two frames on the channel — one about the plan and one about a row that
     * exists only because of it. Asserted by draining: after the plan's own frame there is nothing
     * else waiting.
     */
    @Test
    void oneChangeIsExactlyOneFrame() throws Exception {
        createAPlan();

        assertThat(published()).as("the plan's own frame").isNotNull();
        assertThat(output.receive(1000, CHANNEL))
            .as("a second frame means the audit row was announced too, and a consumer cannot tell the two apart")
            .isNull();
    }

    private String createAPlan() throws Exception {
        // `currency` and `featured` are @NotNull on ServicePlan and the resource rejects the payload
        // without them — a 400 that this helper's five callers all reported as "nothing arrived on
        // admin.event". The two distinctive strings are what the absence assertions look for.
        ServicePlan plan = new ServicePlan().name("Mangosteen tier").code("MANGOSTEEN").currency("GHS").featured(false);

        String body = mvc
            .perform(post("/api/service-plans").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(plan)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        return om.readTree(body).get("id").asString();
    }

    private Message<byte[]> published() {
        Message<byte[]> published = output.receive(2000, CHANNEL);
        assertThat(published).as("nothing arrived on %s", CHANNEL).isNotNull();
        return published;
    }

    private JsonNode frameFromTheChannel() {
        return om.readTree(new String(published().getPayload(), StandardCharsets.UTF_8));
    }

    private List<String> keysOf(JsonNode node) {
        return node.properties().stream().map(Map.Entry::getKey).toList();
    }
}
