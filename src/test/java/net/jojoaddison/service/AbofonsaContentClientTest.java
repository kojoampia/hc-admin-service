package net.jojoaddison.service;

import static net.jojoaddison.config.ApplicationPropertiesFixture.abofonsaContent;
import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.jojoaddison.service.AbofonsaContentClient.PublishedPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * {@link AbofonsaContentClient}'s parsing, over a captured body.
 *
 * <p>{@link ServicePlanCatalogueSyncServiceTest} mocks this client away, which is right for what it
 * asserts and leaves the field extraction, the {@code !body.isArray()} branch and the blank-code
 * skip covered by nothing. Every one of those failing produces the same visible outcome as Abofonsa
 * being down — a sync that writes nothing and logs one {@code warn} — which is the outcome this
 * whole design treats as normal, so a parsing fault would be invisible by construction.
 *
 * <p><b>A loopback HTTP server rather than {@code MockRestServiceServer}, and not by preference.</b>
 * {@code MockRestServiceServer.bindTo(RestClient.Builder)} installs its stub by calling
 * {@code builder.requestFactory(…)}; this client sets its own request factory <em>after</em>
 * receiving the builder, because it has to bound both the connect and the read timeout, so the stub
 * is replaced and the request goes to the real host. Binding it would produce a test that passes
 * while reaching {@code web.abofonsa.com}, which is the one thing
 * {@code src/test/resources/config/application.yml} disables this client to prevent. The server here
 * is on {@code 127.0.0.1} on an ephemeral port, is started and stopped per test, and depends on
 * nobody's deploy.
 *
 * <p>The body is a capture of {@code GET /api/v1/content/plans?locale=en} taken on 2026-09-08,
 * trimmed to the tiers this needs and keeping fields the client ignores — {@code id},
 * {@code priceNote}, {@code features}, {@code comparison} — because "reads the fields it wants out
 * of a bigger document" is part of what is being asserted.
 */
class AbofonsaContentClientTest {

    private static final String CAPTURED =
        """
        [
          {
            "id": "6a65d3cec28dba1c0750efe9",
            "code": "PEAR",
            "name": "PEAR Plan",
            "forWho": "A dependable weekday routine for a relative who is broadly well but should not be alone.",
            "priceAmount": "3,000",
            "priceCurrency": "GHS",
            "priceNote": "Minimum three-month term · 30 days' notice",
            "featured": false,
            "features": [{ "label": "5 weekly visits", "included": true, "emphasised": true }],
            "comparison": { "visitsPerWeek": "5 weekly visits" },
            "displayOrder": 1
          },
          {
            "id": "6a65d3cec28dba1c0750efeb",
            "code": "MELON",
            "name": "MELON Plan",
            "forWho": "Continuous cover for complex, palliative or high-dependency care needs.",
            "priceAmount": "8,000",
            "priceCurrency": "GHS",
            "featured": true,
            "displayOrder": 3
          }
        ]
        """;

    private HttpServer server;
    private final AtomicReference<String> body = new AtomicReference<>(CAPTURED);
    private final AtomicReference<String> contentType = new AtomicReference<>("application/json");
    private final AtomicReference<String> requestedPath = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
            "/",
            exchange -> {
                requests.incrementAndGet();
                requestedPath.set(exchange.getRequestURI().toString());
                byte[] payload = body.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", contentType.get());
                exchange.sendResponseHeaders(200, payload.length);
                exchange.getResponseBody().write(payload);
                exchange.close();
            }
        );
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    /**
     * The published shape, read field for field.
     *
     * <p>{@code priceAmount} is asserted as the string {@code "3,000"} on purpose. It is the whole
     * point of the design that this value is carried and never parsed — a number here would mean
     * somebody had "completed" the client, and it would break silently on the first locale that
     * groups differently.
     */
    @Test
    void readsThePublishedCatalogue() {
        Optional<List<PublishedPlan>> plans = client(true).plans();

        assertThat(plans).isPresent();
        assertThat(plans.orElseThrow()).hasSize(2);
        PublishedPlan pear = plans.orElseThrow().getFirst();
        assertThat(pear.code()).isEqualTo("PEAR");
        assertThat(pear.name()).isEqualTo("PEAR Plan");
        assertThat(pear.forWho()).startsWith("A dependable weekday routine");
        assertThat(pear.priceAmount()).isEqualTo("3,000");
        assertThat(pear.priceCurrency()).isEqualTo("GHS");
        assertThat(pear.featured()).isFalse();
        assertThat(pear.displayOrder()).isEqualTo(1);

        PublishedPlan melon = plans.orElseThrow().get(1);
        assertThat(melon.code()).isEqualTo("MELON");
        assertThat(melon.featured()).isTrue();
        assertThat(melon.displayOrder()).isEqualTo(3);

        // The locale is on the query string, and it is what decides how priceAmount is formatted.
        assertThat(requestedPath.get()).isEqualTo("/api/v1/content/plans?locale=en");
    }

    /**
     * A tier with no code is dropped, and the tiers around it are not.
     *
     * <p>The code is the entire join key: a plan without one cannot be matched against anything this
     * service holds, so carrying it would mean creating a second, unjoinable copy of a tier on every
     * single run. Blank as well as missing, because a publisher trimming a field to {@code ""} is
     * the same absence spelled differently.
     */
    @Test
    void skipsAPublishedPlanWithNoCode() {
        body.set(
            """
            [
              { "name": "Nameless", "displayOrder": 1 },
              { "code": "  ", "name": "Blank", "displayOrder": 2 },
              { "code": " MELON ", "name": "MELON Plan", "displayOrder": 3 }
            ]
            """
        );

        List<PublishedPlan> plans = client(true).plans().orElseThrow();

        // Stripped, because the code is compared for equality against what is stored.
        assertThat(plans).extracting(PublishedPlan::code).containsExactly("MELON");
    }

    /**
     * A body that is not an array is a failed read, not an empty catalogue.
     *
     * <p>The two must not collapse. {@code []} says Abofonsa publishes no tiers; anything else — an
     * error document, a login page from an intercepting proxy, an HTML holding page — says this
     * client could not read the catalogue, and only the first is evidence. Both leave the local copy
     * alone, which is why the difference has to be typed rather than noticed.
     */
    @Test
    void treatsANonArrayBodyAsAFailedRead() {
        body.set("{ \"error\": \"nope\" }");

        assertThat(client(true).plans()).isEmpty();
    }

    /** An empty catalogue is a successful read of nothing, and is not the same answer. */
    @Test
    void treatsAnEmptyArrayAsAnEmptyCatalogue() {
        body.set("[]");

        assertThat(client(true).plans()).contains(List.of());
    }

    /**
     * Disabled, it opens no socket at all.
     *
     * <p>Asserted as "the server was never asked" rather than as an empty {@link Optional}: this is
     * the switch every integration test in the repository runs behind, and a version of it that
     * dialled and discarded the answer would make the whole suite depend on a third party's host
     * while looking exactly like this one.
     */
    @Test
    void dialsNobodyWhenDisabled() {
        assertThat(client(false).plans()).isEmpty();

        assertThat(requests).hasValue(0);
    }

    private AbofonsaContentClient client(boolean enabled) {
        return new AbofonsaContentClient(RestClient.builder(), abofonsaContent(baseUrl(), enabled, "en", 5));
    }

    private String baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort()).toString();
    }
}
