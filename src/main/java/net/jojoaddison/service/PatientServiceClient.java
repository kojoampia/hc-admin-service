package net.jojoaddison.service;

import java.time.Duration;
import net.jojoaddison.domain.enumeration.NameResolution;
import net.jojoaddison.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * Asks {@code hcpatientservice} to name a patient this directory learned about from an event.
 *
 * <h2>Why this exists at all — backlog item 50</h2>
 *
 * <p>hc-patient's {@code PatientEventPublisher.assertNothingClinical} refuses at runtime to publish
 * a name, a date of birth, a phone number or a document number, and hc-admin's {@code Profile}
 * requires all four — so <b>no {@code Profile} can ever be created from an event</b> and a patient
 * who registered on the patient app has no name here. Item 45 put the address from their
 * {@code DirectoryLink} on the row, which is honest and is what an operator reported from
 * production as an email where a name should be. This client is the missing half: the name is
 * <b>read from the stack that owns it, at render time, and never stored.</b>
 *
 * <p><b>Never stored</b> is item 27(a)'s rule and it is the reason this is a lookup and not an
 * import. A second copy of a person's name is a second thing that can disagree with hc-patient, and
 * a defaulted one ({@code Unknown / 1970-01-01}) is indistinguishable from a real record entered
 * badly. Nothing on this path writes to Mongo — {@code DirectoryNameResolutionService} sets a
 * transient field and the document is never saved.
 *
 * <h2>The caller's own token, and why nothing had to be built on their side</h2>
 *
 * <p>Relayed rather than a service account, exactly as {@link ProfessionalServiceClient} relays an
 * administrator's. The three gateways share one signing key, so the token this service received is
 * accepted over there, and their guard is
 * {@code patientScope.isUnrestricted() || the caller's own email matches} — true for
 * {@code ROLE_ADMIN}, which every hc-admin administrator holds. Verified against their quality
 * stack on 2026-09-09: an administrator's token reads a profile by address (200), an operator's
 * does not (404), and the same operator's token reads this service's own patients (200), so the
 * 404 is their guard and not a dead stack.
 *
 * <p><b>Do not "fix" the operator's 404 by calling with a service identity.</b> That would hand
 * every operator the read their guard exists to deny them. The difference is accepted, argued in
 * item 50's entry, and recorded in the console's {@code patient.ts}; an operator sees the address,
 * which is exactly what they saw before this existed.
 *
 * <h2>This read degrades. {@link ProfessionalServiceClient} does not, and the inversion is the
 * point</h2>
 *
 * <p>That client writes a round to the roster of record, so a swallowed failure would tell an
 * administrator a clinician had been rostered when nobody had — it throws, and the console renders
 * an outage. This one decorates a directory row. A directory that will not load because a sibling
 * stack is down is a far worse screen than one showing the address it showed yesterday, so
 * <b>every failure here is an outcome and not an exception</b>: this class throws nothing, at all,
 * ever. Its counterpart in spirit is {@code ObservabilityClient}, which answers empty when Mimir is
 * down.
 *
 * <h2>What is logged, which is never the address</h2>
 *
 * <p>Backlog item 43: the correlation key is a patient's email address, those lines reach an
 * unauthenticated estate-wide Loki, and the answer is a flat no at any level. Every line here goes
 * through {@link LogPseudonym#subject(String)}. The endpoint may serve the address to an authorised
 * screen — {@code DirectoryLinkResource}'s javadoc argues why the two decisions are consistent —
 * and a log may not.
 */
@Service
public class PatientServiceClient {

    private static final Logger LOG = LoggerFactory.getLogger(PatientServiceClient.class);

    /**
     * hc-patient's read path. Theirs, verified on {@code origin/main} and against their running
     * stack: {@code ProfileResource.getProfileByEmail}, matched case-insensitively on their side.
     */
    private static final String PROFILE_BY_EMAIL = "/api/profiles/email/{email}";

    private final RestClient restClient;
    private final boolean enabled;

    public PatientServiceClient(
        RestClient.Builder builder,
        @Value("${application.patientservice.base-url:http://hc-patient-service:8081}") String baseUrl,
        @Value("${application.patientservice.enabled:true}") boolean enabled,
        @Value("${application.patientservice.timeout-seconds:2}") int timeoutSeconds
    ) {
        this.enabled = enabled;
        // Both timeouts, for the reason ProfessionalServiceClient records: the connect timeout lives
        // on the HttpClient and the read timeout on the factory, and setting only the second leaves
        // the connect side unbounded — a sibling that accepts nothing hangs a request thread as
        // effectively as one that answers nothing. Shorter here than the roster client's five
        // seconds, because this call is on the path of a screen a person is waiting for and its
        // failure costs a name rather than a rostered round.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build()
        );
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
        LOG.info("patientservice client -> {} (enabled={}, timeout={}s)", baseUrl, enabled, timeoutSeconds);
    }

    /**
     * One lookup's answer: what happened, and the name if there is one.
     *
     * @param outcome what this service genuinely knows about the lookup.
     * @param name the person's name, or {@code null} — which is possible on {@link NameResolution#RESOLVED}
     *             as well, because hc-patient's {@code Profile} does not require one.
     */
    public record ResolvedName(NameResolution outcome, String name) {
        static ResolvedName unavailable() {
            return new ResolvedName(NameResolution.UNAVAILABLE, null);
        }

        static ResolvedName notFound() {
            return new ResolvedName(NameResolution.NOT_FOUND, null);
        }

        /** True when there is something to put on a screen. */
        public boolean hasName() {
            return name != null && !name.isBlank();
        }
    }

    /**
     * Whether this deployment is configured to ask at all.
     *
     * <p>Read by {@link DirectoryNameResolutionService} before it enters its loop, so a deployment
     * with no sibling stack marks its candidate rows without counting a lookup per row. The answer
     * would be the same without it — {@link #resolveName(String)} refuses when disabled — but the
     * budget warning would then report lookups that never opened a socket, which is a figure that
     * sends a reader to the network.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Names the patient hc-patient holds at this address, or says why it cannot.
     *
     * <p><b>Only the name crosses back.</b> Their endpoint returns the whole {@code Profile} —
     * 27 fields, measured against their running stack, including {@code bloodGroup},
     * {@code cardNumber}, {@code birthDate} and {@code careAngelPhone} — and this method reads two
     * of them out of the tree and drops the rest on the floor. That is item 50's consequence (a),
     * taken deliberately: discarding here is the cheaper correct answer and keeps every clinical
     * field off the wire to the browser entirely. A projection on their side would be better and
     * needs their team; the ask is filed rather than assumed.
     *
     * <p>Do not "improve" this by returning the node. The moment a caller can reach the rest of the
     * document, a screen will render one field of it and this service will be shipping another
     * stack's medical record to a console that has no business holding it.
     *
     * @param email the address to look up — the correlation key off an {@code HC_PATIENT} link.
     * @return never {@code null}, never a throw.
     */
    public ResolvedName resolveName(String email) {
        if (!enabled) {
            // Debug, not warn: an environment with no sibling stack (deploy/e2e, a laptop) would
            // otherwise log a line per row per page turn for a configuration that is correct.
            LOG.debug("patientservice is disabled; not resolving a name for subject {}", LogPseudonym.subject(email));
            return ResolvedName.unavailable();
        }
        if (email == null || email.isBlank()) {
            return ResolvedName.unavailable();
        }
        String token = SecurityUtils.getCurrentRequestJwt().orElse(null);
        if (token == null) {
            // A configuration or plumbing fault rather than a fact about the patient: this endpoint
            // is behind the read/write split, so a request that reached it had a token. Worth a warn
            // and not worth an exception — the row falls back to the address either way.
            LOG.warn("No caller token to relay; cannot resolve a name for subject {}", LogPseudonym.subject(email));
            return ResolvedName.unavailable();
        }
        try {
            ResponseEntity<JsonNode> response = restClient
                .get()
                .uri(PROFILE_BY_EMAIL, email)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                // No default status handler: a 404 is an answer here, not a failure, and letting
                // RestClient throw for it would make "hc-patient does not know them" and "hc-patient
                // could not be reached" the same catch block — which is exactly the collapse
                // NameResolution exists to prevent.
                .onStatus(HttpStatusCode::isError, (request, errorResponse) -> {})
                .toEntity(JsonNode.class);

            HttpStatusCode status = response.getStatusCode();
            if (status.value() == 404) {
                return ResolvedName.notFound();
            }
            if (!status.is2xxSuccessful()) {
                LOG.warn("patientservice answered {} for subject {}", status.value(), LogPseudonym.subject(email));
                return ResolvedName.unavailable();
            }
            return new ResolvedName(NameResolution.RESOLVED, nameOf(response.getBody()));
        } catch (RestClientException e) {
            // TYPES, NEVER THE MESSAGE — and this line carried the message until it was reviewed.
            //
            // Spring wraps an I/O failure in a ResourceAccessException whose message quotes the
            // REQUEST URL, and the address is a path segment of that URL. So the line read
            //
            //   ... for subject subj-df2b50538c26: I/O error on GET request for
            //   "http://.../api/profiles/email/kojo%40jac.net": null
            //
            // — a pseudonymised subject and a readable address on the same line, percent-encoded,
            // which is not pseudonymisation. That is backlog item 43's exact breach, into an
            // unauthenticated estate-wide Loki, and it fired precisely when hc-patient was
            // unreachable: the moment somebody would be reading these logs.
            //
            // The two class names lose nothing worth having. What an operator needs from this line
            // is which failure it was, and the cause's type says it exactly — ConnectException,
            // HttpConnectTimeoutException, HttpTimeoutException, UnknownHostException — where the
            // message adds only a URL nobody may write down. LogPseudonymTest sweeps for the shape.
            LOG.warn(
                "patientservice could not be reached for subject {}: {} caused by {}",
                LogPseudonym.subject(email),
                e.getClass().getSimpleName(),
                e.getCause() == null ? "nothing further" : e.getCause().getClass().getSimpleName()
            );
            return ResolvedName.unavailable();
        }
    }

    /**
     * First and last name out of their document, or {@code null}.
     *
     * <p>{@code middleNames} is deliberately left behind. The console's own {@code displayName} for
     * a patient with a local profile is first plus last, and a name that gained a middle name only
     * for the rows learned from an event would read as two different renderings of the same column.
     *
     * <p>A blank result is {@code null} rather than {@code ""}: {@code trim()} yields a falsy but
     * non-null empty string, and a blank name reaching a screen would print an empty cell where the
     * address should have fallen through.
     */
    private static String nameOf(JsonNode profile) {
        if (profile == null) {
            return null;
        }
        String name = (text(profile, "firstName") + " " + text(profile, "lastName")).trim();
        return name.isEmpty() ? null : name;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asString().trim();
    }
}
