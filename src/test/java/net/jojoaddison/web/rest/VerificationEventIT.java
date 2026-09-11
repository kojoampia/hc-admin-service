package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ProfessionalVerificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/**
 * A recorded verification reaches the {@code professional-verification} topic.
 *
 * <p><b>This test exists because the failure it guards is completely silent.</b> A Spring Cloud
 * Stream binding with no {@code destination} publishes to a topic named after the binding: the send
 * succeeds, the topic is created, and nothing ever reads it. {@code binding-out-0} did exactly that
 * in this service until somebody went looking — every message the desk sent was announced into a
 * topic no consumer subscribed to, and nothing anywhere reported a problem.
 *
 * <p><b>What this class can and cannot show.</b> {@code OutputDestination.receive(timeout, name)}
 * resolves by <em>destination</em> when the binding declares one and by <em>binding name</em> when
 * it does not, so receiving here proves the event is produced and carries the right payload — but on
 * its own it could not prove the shipped topic name, because the test profile overrides the bindings
 * with its own. The destination that actually ships is guarded in {@code ConfigurationBindingTest},
 * which reads {@code config/application.yml} off the classpath; that is the file a running service
 * uses and the only one where a missing destination has consequences.
 *
 * <p>{@code TestChannelBinderConfiguration} replaces the Kafka binder with an in-memory one, so this
 * exercises the binding names and the payload without a broker.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(username = "admin")
@ImportAutoConfiguration(TestChannelBinderConfiguration.class)
class VerificationEventIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    @Autowired
    private OutputDestination output;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ProfessionalVerificationRepository verificationRepository;

    private Professional professional;

    @BeforeEach
    void seed() {
        verificationRepository.deleteAll();
        professionalRepository.deleteAll();
        professional = professionalRepository.save(
            new Professional()
                .role(ProfessionalRole.NURSE)
                .licenceNumber("NMC/GH/26-0002")
                .verification(VerificationStatus.PENDING)
                .status(AccountStatus.ACTIVE)
                .joinedOn(LocalDate.of(2026, 2, 2))
        );
        // Anything the container published while starting up, so a stale frame cannot be mistaken
        // for this test's own.
        output.clear();
    }

    @AfterEach
    void tearDown() {
        verificationRepository.deleteAll();
        professionalRepository.deleteAll();
    }

    /**
     * The domain topic gets the decision, with the fields a consumer in another stack needs.
     *
     * <p>{@code licenceNumber} is asserted alongside the id because it is the identifier the other
     * stacks key on — an event carrying only an id from a service they share no database with is an
     * event they cannot act on.
     */
    @Test
    void publishesTheDecisionToTheVerificationTopic() throws Exception {
        recordVerified();

        Message<byte[]> published = output.receive(2000, "professional-verification");
        assertThat(published).as("nothing arrived on professional-verification").isNotNull();

        var event = om.readTree(new String(published.getPayload()));
        assertThat(event.get("status").asString()).isEqualTo("VERIFIED");
        assertThat(event.get("professionalId").asString()).isEqualTo(professional.getId());
        assertThat(event.get("licenceNumber").asString()).isEqualTo("NMC/GH/26-0002");
        assertThat(event.get("recordedBy").asString()).isEqualTo("admin");
        assertThat(event.get("verificationId").asString()).isNotBlank();
    }

    /**
     * And the browser fan-out gets it too, so an open console updates without a reload.
     *
     * <p>Two sends rather than one shared binding, asserted separately: the domain topic and the SSE
     * channel have different audiences, and a consumer of one should not have to filter out the
     * other.
     */
    @Test
    void relaysTheDecisionToTheBrowserChannel() throws Exception {
        recordVerified();

        Message<byte[]> relayed = output.receive(2000, "binding-out-0");
        assertThat(relayed).as("nothing arrived on binding-out-0").isNotNull();
        assertThat(new String(relayed.getPayload())).contains("\"status\":\"VERIFIED\"");
    }

    /**
     * A verification is recorded even when publishing cannot happen.
     *
     * <p>Exercised by recording a second decision after the binder has been drained — the row and
     * the projection must land regardless. Unwinding a recorded verification because a broker was
     * unreachable would lose the one thing that cannot be reconstructed to protect the one thing
     * that can.
     */
    @Test
    void recordsTheDecisionEvenIfNobodyIsListening() throws Exception {
        recordVerified();
        output.clear();

        assertThat(verificationRepository.findAll()).hasSize(1);
        assertThat(professionalRepository.findById(professional.getId()).orElseThrow().getVerification()).isEqualTo(
            VerificationStatus.VERIFIED
        );
    }

    private void recordVerified() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("professionalId", professional.getId());
        request.put("status", "VERIFIED");
        request.put("method", "Licence register");

        mvc.perform(
            post("/api/professional-verifications").contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(request))
        ).andExpect(status().isCreated());
    }
}
