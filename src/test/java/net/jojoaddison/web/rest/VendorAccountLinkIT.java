package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Vendor;
import net.jojoaddison.repository.VendorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code Vendor.accountId} — the only thing relating a vendor here to that vendor in hc-vendor.
 *
 * <p>hc-vendor is PostgreSQL and this service is MongoDB, so the two can never be joined in a
 * query; the link is resolved over HTTP through {@code /api/vendors?accountId.equals=…} and is an
 * application-level convention that no database enforces. That is exactly why it needs a test: the
 * ways it breaks are all quiet. A filter that is silently dropped returns the whole directory with
 * a 200, and the portal shows its user someone else's vendor. A seed with two vendors on one login
 * resolves to whichever Mongo returns first. Neither fails anything else in this suite.
 *
 * <p>{@code VendorResourceIT} covers that the field round-trips through the CRUD endpoints. This
 * covers what the field is <em>for</em>.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class VendorAccountLinkIT {

    private static final String SEED = "data/hc-admin-ms-data.json";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private VendorRepository vendorRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Vendor linked;
    private Vendor unlinked;

    @BeforeEach
    void seed() {
        linked = vendorRepository.save(VendorResourceIT.createEntity().accountId("kaneshie"));
        // Explicitly null rather than merely unset: createEntity() carries a DEFAULT_ACCOUNT_ID, and
        // the case worth covering is the ordinary one — a vendor with no portal login at all.
        unlinked = vendorRepository.save(VendorResourceIT.createEntity().accountId(null));
    }

    @AfterEach
    void cleanup() {
        vendorRepository.deleteAll();
    }

    @Test
    void resolvesALoginToItsVendor() throws Exception {
        mvc
            .perform(get("/api/vendors").param("accountId.equals", "kaneshie").param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(linked.getId())).exists())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(unlinked.getId())).doesNotExist());
    }

    /**
     * The failure the vendor portal's spec is most worried about, from the other side: a login that
     * names no vendor has to be distinguishable from the service being unavailable. A 200 with an
     * empty body is; a 404 is not, which is why this resolves through a filter rather than through
     * a {@code /vendors/account/{login}} route.
     */
    @Test
    void anUnknownLoginIsAnEmptyPageAndNotAnError() throws Exception {
        mvc
            .perform(get("/api/vendors").param("accountId.equals", "nobody").param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    /**
     * Documents a sharp edge rather than asserting a good behaviour. {@code NamedFilters} drops
     * blank strings as well as nulls, so a blank login is not "no vendor" — it is no filter, and the
     * caller gets the entire directory. A resolver that passes an empty login straight through would
     * hand its user the first vendor in the collection.
     */
    @Test
    void aBlankLoginIsNoFilterAtAllAndReturnsEverything() throws Exception {
        mvc
            .perform(get("/api/vendors").param("accountId.equals", "").param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(linked.getId())).exists())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(unlinked.getId())).exists());
    }

    /**
     * The uniqueness no index enforces. Nothing in MongoDB stops two vendors sharing a login, and
     * the symptom would be one vendor seeing another's purchase orders — so the seed, which is the
     * one place these values are written down, is held to it here.
     */
    @Test
    void seededAccountIdsAreDistinct() throws Exception {
        List<String> accountIds = seededVendors()
            .stream()
            .map(v -> v.path("accountId"))
            .filter(node -> !node.isMissingNode() && !node.isNull())
            .map(JsonNode::asText)
            .toList();

        assertThat(accountIds).as("every seeded vendor carries an accountId").hasSize(seededVendors().size());
        assertThat(accountIds).as("no login resolves to two vendors").doesNotHaveDuplicates();
    }

    /**
     * The seed is the hc-vendor fixture's other half: {@code quality/seed-data.py} over there
     * carries the same nine logins and resolves each to the vendor id it expects. If these drift,
     * that fixture loads orders against the wrong vendor and nothing here notices.
     */
    @Test
    void seededLoginsMatchTheVendorPortalFixture() throws Exception {
        Map<String, String> expected = Map.of(
            "v1",
            "kaneshie",
            "v2",
            "ridge",
            "v3",
            "goldstar",
            "v4",
            "swiftamb",
            "v5",
            "homecare",
            "v6",
            "volta",
            "v7",
            "tkphysio",
            "v8",
            "bridgepay",
            "v9",
            "cclinens"
        );

        Map<String, String> actual = seededVendors()
            .stream()
            .collect(java.util.stream.Collectors.toMap(v -> v.path("id").asText(), v -> v.path("accountId").asText()));

        assertThat(actual).isEqualTo(expected);
    }

    private List<JsonNode> seededVendors() throws Exception {
        try (var stream = new ClassPathResource(SEED).getInputStream()) {
            JsonNode vendors = objectMapper.readTree(stream).path("test").path("vendors");
            assertThat(vendors.isArray()).as("the test profile seeds a vendors array").isTrue();
            List<JsonNode> rows = new java.util.ArrayList<>();
            vendors.forEach(rows::add);
            return List.copyOf(rows);
        }
    }
}
