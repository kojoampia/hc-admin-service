package net.jojoaddison.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.jojoaddison.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Reads the membership catalogue Abofonsa publishes, which is the estate's source of truth for it.
 *
 * <p>{@code GET https://web.abofonsa.com/api/v1/content/plans?locale=en} is <b>public</b> —
 * hc-abofonsa-web's {@code SecurityConfiguration} permits {@code /api/v1/content/**} — so no token,
 * no service account and no shared secret is involved. Verified 2026-09-08: 200, three plans, codes
 * {@code PEAR} / {@code PAWPAW} / {@code MELON}.
 *
 * <p>It is <b>not</b> hc-patient's {@code /api/plans}. That path is their gateway proxying this same
 * upstream so a browser stays same-origin; reached from here it is another product's authenticated
 * surface and answers 401. Read the publisher, not a sibling's proxy of it.
 *
 * <h2>Unreachable is a normal state, not an error</h2>
 *
 * <p>The same posture {@link ObservabilityClient} takes, and for a stronger reason. hc-patient's
 * {@code membership-plan.service.ts} already records that Abofonsa is separately deployed and that
 * its production host was not serving when they wrote it, so <em>"a patient must not meet an error
 * page because a second product is down"</em>. Nothing in this console reads the catalogue on a
 * request path at all — every screen reads the local {@code service_plan} collection, and this
 * client only ever feeds a background refresh of it. A failed read therefore means one thing: the
 * copy stays as it was. It is logged at {@code warn} once per attempt and nothing else happens.
 *
 * <p><b>An empty list and a failed read are different answers and are typed as such.</b> A publisher
 * that answers {@code []} is telling us it has no plans; a publisher that cannot be reached is
 * telling us nothing. Both return no plans to sync, but only the first is evidence, and conflating
 * them here would push the distinction into the caller where it cannot be recovered.
 */
@Service
public class AbofonsaContentClient {

    private static final Logger LOG = LoggerFactory.getLogger(AbofonsaContentClient.class);

    /** The published catalogue. Locale decides the language and the price formatting. */
    private static final String PLANS = "/api/v1/content/plans?locale=";

    private final RestClient restClient;
    private final boolean enabled;
    private final String locale;

    /**
     * Takes the properties object rather than four placeholders — backlog item 58.
     *
     * <p>Not named by that item, which found the same defect in the two sibling-stack clients and
     * missed this one; binding two of the three would have left the repository with two idioms and
     * made the guard that enforces the rule unwritable. The two remaining
     * {@code application.abofonsa-content.*} keys — the schedule — cannot be read this way and are
     * argued at their fields in {@link ApplicationProperties.AbofonsaContent}.
     */
    public AbofonsaContentClient(RestClient.Builder builder, ApplicationProperties properties) {
        ApplicationProperties.AbofonsaContent config = properties.getAbofonsaContent();
        String baseUrl = config.getBaseUrl();
        int timeoutSeconds = config.getTimeoutSeconds();
        this.enabled = config.isEnabled();
        this.locale = config.getLocale();
        // Both timeouts, for the reason ProfessionalServiceClient records: the connect timeout lives
        // on the HttpClient and the read timeout on the factory, and setting only the second leaves
        // the connect side unbounded. This runs on a scheduler rather than a request thread, so the
        // cost of getting it wrong is a stuck refresh rather than a stuck user — which is worse to
        // notice, not better.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
            java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(timeoutSeconds)).build()
        );
        factory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restClient = builder.baseUrl(baseUrl).requestFactory(factory).build();
        LOG.info("Abofonsa content client -> {} (enabled={}, locale={}, timeout={}s)", baseUrl, enabled, locale, timeoutSeconds);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * One published tier, reduced to the fields this service has any business holding.
     *
     * <p>{@code priceAmount} and {@code priceCurrency} are carried and are <b>not</b> the same kind
     * of value. The currency is a machine-readable ISO code; the amount is a string already
     * formatted for {@code locale} — {@code "8,000"} — and is here so that a reader of a sync log or
     * a test can see what was published without this service ever turning it into a number. See
     * {@link ServicePlanCatalogueSyncService} for why it is never parsed.
     */
    public record PublishedPlan(
        String code,
        String name,
        String forWho,
        String priceAmount,
        String priceCurrency,
        boolean featured,
        int displayOrder
    ) {}

    /**
     * The published catalogue, or {@link Optional#empty()} if it could not be read.
     *
     * <p>A plan with no {@code code} is dropped rather than carried: the code is the entire join key,
     * and a nameless row would be indistinguishable from a plan this service has not learned about
     * yet.
     */
    public Optional<List<PublishedPlan>> plans() {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            JsonNode body = restClient.get().uri(PLANS + locale).retrieve().body(JsonNode.class);
            if (body == null || !body.isArray()) {
                LOG.warn("Abofonsa's plan catalogue answered something that is not an array; the local copy is unchanged");
                return Optional.empty();
            }
            List<PublishedPlan> plans = new ArrayList<>(body.size());
            for (JsonNode node : body) {
                String code = text(node, "code");
                if (code == null || code.isBlank()) {
                    LOG.warn("Abofonsa published a plan with no code; skipping it, because the code is the join key");
                    continue;
                }
                plans.add(
                    new PublishedPlan(
                        code.strip(),
                        text(node, "name"),
                        text(node, "forWho"),
                        text(node, "priceAmount"),
                        text(node, "priceCurrency"),
                        node.path("featured").asBoolean(false),
                        // Defaults to 0 if the publisher ever stops sending the field, which would
                        // rewrite every plan's card order to the same value in one pass and leave
                        // the board in whatever order Mongo returned. Cheap to live with while the
                        // field is published for every tier — the alternative is refusing a
                        // catalogue over an ordering — but it is a silent flattening, so a board
                        // that suddenly orders wrongly is worth checking against this line before
                        // anything else.
                        node.path("displayOrder").asInt(0)
                    )
                );
            }
            return Optional.of(List.copyOf(plans));
        } catch (Exception e) {
            // warn, not error. A third party being down is not this service's fault and not its
            // outage — one line saying the copy is unchanged is the whole story.
            LOG.warn("Abofonsa's plan catalogue could not be read ({}); the local copy is unchanged", e.getMessage());
            return Optional.empty();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }
}
