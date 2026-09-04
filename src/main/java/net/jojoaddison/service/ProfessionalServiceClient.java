package net.jojoaddison.service;

import java.time.Duration;
import java.util.Map;
import net.jojoaddison.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Writes rounds to {@code professionalservice}, which owns the roster of record.
 *
 * <p>hc-professional's {@code DutyRoster} is the estate's roster since the 2026-09 migration; this
 * service kept an unreachable copy of it and deleted it. What hc-admin still owns is the
 * <em>planning</em> — who should do a round, given geography, availability, workload and the pay
 * model — so the planner composes a round here and files it there. See
 * {@link RoundPlanningService}.
 *
 * <h2>The write does not fail soft, deliberately</h2>
 *
 * <p>{@code ObservabilityClient} one file over answers empty when Mimir is down, because a
 * dashboard missing a figure is better than a dashboard that will not load. The opposite is true
 * here. A planner that swallowed a failed write would tell an administrator their round was filed
 * when the roster of record has never heard of it, and the clinician would simply not turn up. So a
 * failure raises {@link RosterServiceUnavailableException}, the planner reports the round as
 * <em>failed</em> rather than planned, and the console shows an outage state — which is decision 10
 * of {@code duty-roster-resolution.md} § 9.1, and the whole reason that decision was asked for.
 *
 * <h2>The caller's own token</h2>
 *
 * <p>Relayed rather than a service account, exactly as hc-professional's {@code PatientServiceClient}
 * relays a clinician's. Three gateways share one signing key, so the token this service received is
 * accepted over there; {@code POST /api/duty-roster} is {@code ROLE_ADMIN}, and the administrator
 * pressing <em>plan</em> holds it. Nothing new has to be provisioned, and the audit trail on the far
 * side names the person rather than a robot.
 *
 * <p><b>No token is a configuration failure, not an empty result.</b> A planning call always arrives
 * on an authenticated request; if the token is missing the write cannot be attempted and must not be
 * reported as anything but a failure.
 */
@Service
public class ProfessionalServiceClient {

    private static final Logger LOG = LoggerFactory.getLogger(ProfessionalServiceClient.class);

    /** hc-professional's write path. Singular — see that repo's {@code DutyRosterResource}. */
    private static final String ROUNDS = "/api/duty-roster";

    private final RestClient restClient;
    private final boolean enabled;

    public ProfessionalServiceClient(
        RestClient.Builder builder,
        @Value("${application.professionalservice.base-url:http://hc-professional-service:8081}") String baseUrl,
        @Value("${application.professionalservice.enabled:true}") boolean enabled,
        @Value("${application.professionalservice.timeout-seconds:5}") int timeoutSeconds
    ) {
        this.enabled = enabled;
        // JdkClientHttpRequestFactory and both timeouts, for the reasons hc-professional's
        // PatientServiceClient records: the connect timeout lives on the HttpClient and the read
        // timeout on the factory, and setting only the second leaves the connect side unbounded —
        // a sibling that accepts nothing hangs a request thread as effectively as one that answers
        // nothing.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build()
        );
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
        LOG.info("professionalservice client -> {} (enabled={}, timeout={}s)", baseUrl, enabled, timeoutSeconds);
    }

    /** Raised when the roster of record could not be written to. The console renders an outage. */
    public static class RosterServiceUnavailableException extends RuntimeException {

        public RosterServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        public RosterServiceUnavailableException(String message) {
            super(message);
        }
    }

    /**
     * Files one round and returns the id {@code professionalservice} gave it.
     *
     * <p>The body is a plain map rather than a mirrored DTO. hc-professional's {@code DutyRoster} is
     * that service's type and changes on its schedule; copying it here would be a fourth place in
     * the estate where a roster shape is declared, and the three that already exist are what the
     * migration was for. What this service does own is the <em>meaning</em> of each key, which
     * {@link RoundPlanningService#body} states in one place.
     *
     * <p>Only the {@code id} is read back. The round as stored carries customer snapshots the far
     * service fetches for itself, and hc-admin has no use for them — reading them into this process
     * would put patient names into a stack that deliberately holds none.
     *
     * <p><b>Two of the three ways this throws are local misconfiguration, and the console cannot
     * tell.</b> {@code enabled=false} and a missing caller token both raise
     * {@link RosterServiceUnavailableException}, which {@code RoundPlanningService} reports as
     * {@code ROSTER_SERVICE_UNREACHABLE} — so the screen says the roster service is down when
     * nothing was ever dialled. The messages here distinguish them and the log line above names the
     * far service only when one was actually contacted; a reader who has this endpoint's log has the
     * answer, and a reader who has only the screen does not. Worth a distinct reason on the wire if
     * this is ever mistaken for an outage.
     */
    public String fileRound(Map<String, Object> round) {
        if (!enabled) {
            throw new RosterServiceUnavailableException("professionalservice is disabled; refusing to report a round as filed");
        }
        String token = SecurityUtils
            .getCurrentUserJWT()
            .orElseThrow(() -> new RosterServiceUnavailableException("No caller token available; cannot file a round"));
        try {
            JsonNode created = restClient
                .post()
                .uri(ROUNDS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(round)
                .retrieve()
                .body(JsonNode.class);
            // A 2xx with no id is not a success we can report: the round cannot be read back, which
            // is exactly the verification step this whole path exists to make possible.
            if (created == null || created.get("id") == null || created.get("id").isNull()) {
                throw new RosterServiceUnavailableException("professionalservice accepted the round but returned no id");
            }
            return created.get("id").asString();
        } catch (RestClientException e) {
            // Identifiers only. The round carries customer ids and this service must not log them
            // beside a message that will be read out of a support ticket.
            LOG.warn("professionalservice refused or could not be reached while filing a round: {}", e.getMessage());
            throw new RosterServiceUnavailableException("Could not file the round with professionalservice", e);
        }
    }
}
