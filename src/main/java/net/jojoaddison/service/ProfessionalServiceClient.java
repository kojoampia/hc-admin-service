package net.jojoaddison.service;

import java.time.Duration;
import java.util.Map;
import net.jojoaddison.config.ApplicationProperties;
import net.jojoaddison.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p><b>⚠ Read the token with {@link SecurityUtils#getCurrentRequestJwt()} and never with
 * {@code getCurrentUserJWT()}</b> — backlog item 57. This class called the latter from the day it
 * was written until 2026-09-09, and it filters the authentication's credentials on
 * {@code instanceof String}. This api authenticates as an OAuth2 resource server
 * ({@code SecurityConfiguration}'s {@code oauth2ResourceServer(oauth2 -> oauth2.jwt(...))}), so a
 * real request arrives as a {@code JwtAuthenticationToken} whose credentials are the decoded
 * {@code Jwt} — verified in the bytecode of {@code AbstractOAuth2TokenAuthenticationToken}, whose
 * two-argument constructor stores the token as token, principal <em>and</em> credentials. So the
 * lookup answered empty on <b>every</b> request, {@link #fileRound} took its no-token branch, and
 * <b>every round this service has ever filed has failed</b> — reported to the console as a fact
 * about hc-professional, which had not been asked anything.
 *
 * <p>The {@code String} branch that made it look reachable is the one a
 * {@code UsernamePasswordAuthenticationToken} takes, which is what
 * {@code ProfessionalServiceClientTest} authenticated with. <b>A relay is only proven by a test with
 * the filter chain on</b> — {@link net.jojoaddison.service.RoundRelayIT}, the shape
 * {@code PatientNameRelayIT} established for the sibling client.
 */
@Service
public class ProfessionalServiceClient {

    private static final Logger LOG = LoggerFactory.getLogger(ProfessionalServiceClient.class);

    /** hc-professional's write path. Singular — see that repo's {@code DutyRosterResource}. */
    private static final String ROUNDS = "/api/duty-roster";

    private final RestClient restClient;
    private final boolean enabled;

    /**
     * Takes the properties object rather than three placeholders — backlog item 58.
     *
     * <p>{@link ApplicationProperties.Professionalservice} declared these three defaults and nothing
     * read them, while this constructor declared the same three again and everything did. The
     * properties class is now the one place each is written.
     */
    public ProfessionalServiceClient(RestClient.Builder builder, ApplicationProperties properties) {
        ApplicationProperties.Professionalservice config = properties.getProfessionalservice();
        String baseUrl = config.getBaseUrl();
        int timeoutSeconds = config.getTimeoutSeconds();
        this.enabled = config.isEnabled();
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
     * Raised when the write was not attempted because <em>this</em> deployment is not configured for
     * it — {@code enabled=false}, or a request with no caller token to relay.
     *
     * <p>A subtype rather than a sibling, so that every existing catch of the supertype keeps
     * behaving: the round still fails, still is not reported as filed, and still is not silently
     * swallowed. What the subtype adds is the one fact the console could not previously have — that
     * nothing was dialled, so the far service is not the thing to go and look at. See
     * {@code RoundPlanDtos.Reason.ROSTER_SERVICE_NOT_CONFIGURED}.
     */
    public static class RosterServiceNotConfiguredException extends RosterServiceUnavailableException {

        public RosterServiceNotConfiguredException(String message) {
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
     * <p><b>Two of the three ways this throws are local misconfiguration, and they are typed as
     * such.</b> {@code enabled=false} and a missing caller token stop the write here, before a
     * socket is opened, and raise {@link RosterServiceNotConfiguredException}; only a
     * {@link RestClientException} from a call that was actually made raises the plain supertype.
     * {@code RoundPlanningService} reads that distinction and reports
     * {@code ROSTER_SERVICE_NOT_CONFIGURED} rather than {@code ROSTER_SERVICE_UNREACHABLE}, so the
     * screen stops saying the roster service is down when nothing was ever dialled (backlog item
     * 24). It had been distinguishable in the log and nowhere else — the {@code warn} below names
     * the far service only when one was contacted — which meant a reader with the api's log had the
     * answer and a reader with only the screen did not.
     *
     * <p><b>The no-token branch is kept, and what changed on 2026-09-09 is that it stopped being the
     * only one taken.</b> Before item 57 it fired on every call; after it, the only caller is
     * {@code RosterPlanResource}, {@code POST /api/roster-plans} matches
     * {@code SecurityConfiguration}'s {@code /api/** -> hasAuthority(ADMIN)}, and the chain answers
     * {@code 401} to an unauthenticated request before the resource is entered — so no request that
     * reaches here can lack a token. It is therefore a guard against an <em>in-process</em> caller
     * with no {@code SecurityContext}: a scheduled planner, a consumer thread, a startup runner.
     * There is no such caller today, and if one is added it must not be allowed to report a round as
     * filed. It stays a throw for the reason the class exists — a swallowed failed write tells an
     * administrator a clinician was rostered when nobody was — and it stays the
     * <em>not-configured</em> subtype, because a caller with no token dialled nothing and so learned
     * nothing about hc-professional.
     */
    public String fileRound(Map<String, Object> round) {
        if (!enabled) {
            throw new RosterServiceNotConfiguredException("professionalservice is disabled; refusing to report a round as filed");
        }
        // getCurrentRequestJwt, never getCurrentUserJWT — see the class javadoc and backlog item 57.
        // The older method reads credentials as a String and this service's chain puts a decoded Jwt
        // there, so it answered empty on every request and this branch was the only one ever taken.
        String token = SecurityUtils.getCurrentRequestJwt().orElseThrow(() ->
            new RosterServiceNotConfiguredException("No caller token available; cannot file a round")
        );
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
            String roundId = created.get("id").asString();
            // SUCCESS IS ANNOUNCED, AND UNTIL 2026-09-09 THIS PATH LOGGED NOTHING AT ALL.
            //
            // Only failure had a line, which was affordable while every call failed and is not now.
            // The first successful file in this service's history happens on the deploy that lands
            // item 57, and without this an operator watching that deploy cannot tell "the relay
            // works" from "nobody has pressed the button" — the same two states item 57's own entry
            // had to write a "measured but not observed" paragraph about, and the same ambiguity
            // item 46 removed from DirectoryProjectionService by announcing the outcome rather than
            // one branch of it. Absence of use and presence of success are different observations
            // and this line is what makes them different in the log.
            //
            // INFO rather than debug: planning is a deliberate human act at human volume, not a
            // per-row loop. The round id is hc-professional's own generated identifier — the round
            // NAME is free text an administrator typed and the body carries customer ids, and
            // neither goes near this line, for the reason the warn below records.
            LOG.info("professionalservice filed a round; it returned id {}", roundId);
            return roundId;
        } catch (RestClientException e) {
            // TYPES, NEVER THE MESSAGE — and this line carried the message until item 57.
            //
            // The comment that used to sit here said "identifiers only: the round carries customer
            // ids and this service must not log them beside a message that will be read out of a
            // support ticket". That was true of what the statement INTERPOLATED and false of what
            // getMessage() CARRIES, which is the more dangerous half and is exactly the shape item
            // 43 was about — a comment reassuring a reader about the precise thing it was getting
            // wrong.
            //
            // Two library-built strings reach that message and neither is ours. A transport failure
            // is a ResourceAccessException quoting the request URL. A refusal goes through
            // RestClient's default error handler, whose getErrorMessage(int, String, byte[], Charset)
            // appends the RESPONSE BODY — so a validation refusal from hc-professional that echoes a
            // rejected value puts that value here, and the values in a round body are customer ids,
            // which are hcpatientservice patient ids.
            //
            // ITEM 57 IS WHAT MADE THIS REACHABLE. Until the token relay was fixed this client threw
            // before opening a socket on every call, so no RestClientException here had ever held a
            // response from hc-professional. The fix arms it, which is why it is repaired on the
            // same commit rather than filed.
            //
            // The two class names lose nothing worth having: what an operator needs is which failure
            // it was, and the cause's type says it exactly — ConnectException,
            // HttpConnectTimeoutException, UnknownHostException — while a 4xx arrives as
            // HttpClientErrorException, which is the distinction RoundPlanningService reads to tell
            // "refused" from "unreachable". None of them can quote anything.
            LOG.warn(
                "professionalservice refused or could not be reached while filing a round: {} caused by {}",
                e.getClass().getSimpleName(),
                e.getCause() == null ? "nothing further" : e.getCause().getClass().getSimpleName()
            );
            throw new RosterServiceUnavailableException("Could not file the round with professionalservice", e);
        }
    }
}
