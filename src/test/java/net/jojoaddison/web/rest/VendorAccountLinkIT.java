package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.MediaType;
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
        mvc.perform(get("/api/vendors").param("accountId.equals", "kaneshie").param("size", "100"))
            .andExpect(status().isOk())
            // Exactly one is the whole contract, so assert the count and not merely that the right
            // vendor is somewhere in the page: a regression that OR-combined the criteria, or a
            // duplicate in the data, would satisfy "present and the other absent" and still be wrong.
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(linked.getId())).exists())
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(unlinked.getId())).doesNotExist());
    }

    /**
     * Resolution is case- and whitespace-insensitive because the values are normalised on both
     * sides. Gateway logins are always stored lower-case, so {@code "Kaneshie "} names the same
     * account — and an exact-match filter that missed it would tell the vendor they have no record,
     * which is the failure this mechanism exists to avoid.
     */
    @Test
    void resolutionIsNormalisedOnTheWayIn() throws Exception {
        mvc.perform(get("/api/vendors").param("accountId.equals", "  KaneShie ").param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(linked.getId())).exists());
    }

    /**
     * The failure the vendor portal's spec is most worried about, from the other side: a login that
     * names no vendor has to be distinguishable from the service being unavailable. A 200 with an
     * empty body is; a 404 is not, which is why this resolves through a filter rather than through
     * a {@code /vendors/account/{login}} route.
     */
    @Test
    void anUnknownLoginIsAnEmptyPageAndNotAnError() throws Exception {
        mvc.perform(get("/api/vendors").param("accountId.equals", "nobody").param("size", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
    }

    /**
     * A blank login is rejected rather than silently treated as no filter.
     *
     * <p>{@code NamedFilters} drops blank strings as well as nulls, so before this guard
     * {@code ?accountId.equals=} returned the entire directory and a resolver passing an empty login
     * straight through would have handed its user the first vendor in the collection. On a filter
     * that decides which vendor a caller <em>is</em>, that is a 400 and not a footnote.
     *
     * <p>Absent remains "no filter" — that is the console listing the directory, and it is fine.
     */
    @Test
    void aBlankLoginIsRejectedRatherThanIgnored() throws Exception {
        mvc.perform(get("/api/vendors").param("accountId.equals", "").param("size", "100")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/vendors").param("accountId.equals", "   ").param("size", "100")).andExpect(status().isBadRequest());
    }

    @Test
    void anAbsentFilterStillListsTheDirectory() throws Exception {
        mvc.perform(get("/api/vendors").param("size", "100"))
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

    /**
     * The duplicate no index prevents. Two vendors on one login would show one of them the other's
     * purchase orders, and an admin pasting the wrong login into the console is the realistic way it
     * happens — so the three write handlers check, and these hold them to it.
     */
    @Test
    void aLoginCannotBeGivenToASecondVendorOnCreate() throws Exception {
        mvc.perform(
            post("/api/vendors")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(VendorResourceIT.createEntity().accountId("kaneshie")))
        ).andExpect(status().isBadRequest());

        assertThat(vendorRepository.count()).as("nothing was written").isEqualTo(2);
    }

    @Test
    void aLoginCannotBeMovedOntoASecondVendorByPatch() throws Exception {
        Vendor patch = new Vendor();
        patch.setId(unlinked.getId());
        patch.setAccountId("kaneshie");

        mvc.perform(
            patch("/api/vendors/{id}", unlinked.getId())
                .contentType("application/merge-patch+json")
                .content(objectMapper.writeValueAsBytes(patch))
        ).andExpect(status().isBadRequest());

        assertThat(vendorRepository.findById(unlinked.getId()).orElseThrow().getAccountId()).isNull();
    }

    /** Re-saving a vendor with the login it already holds is not a collision with itself. */
    @Test
    void aVendorKeepingItsOwnLoginIsNotADuplicate() throws Exception {
        Vendor patch = new Vendor();
        patch.setId(linked.getId());
        patch.setAccountId("kaneshie");

        mvc.perform(
            patch("/api/vendors/{id}", linked.getId())
                .contentType("application/merge-patch+json")
                .content(objectMapper.writeValueAsBytes(patch))
        ).andExpect(status().isOk());
    }

    /**
     * Stored values are normalised, so the exact-match filter can be trusted. A blank becomes null
     * rather than {@code ""}: the two differ in MongoDB but not to the filter, so a stored empty
     * string would be a link that looks present and can never resolve.
     */
    @Test
    void accountIdIsNormalisedOnWrite() throws Exception {
        Vendor patch = new Vendor();
        patch.setId(unlinked.getId());
        patch.setAccountId("  RiDGe  ");

        mvc.perform(
            patch("/api/vendors/{id}", unlinked.getId())
                .contentType("application/merge-patch+json")
                .content(objectMapper.writeValueAsBytes(patch))
        )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value("ridge"));

        assertThat(vendorRepository.findById(unlinked.getId()).orElseThrow().getAccountId()).isEqualTo("ridge");
    }

    @Test
    void aBlankAccountIdIsStoredAsNull() throws Exception {
        Vendor patch = new Vendor();
        patch.setId(linked.getId());
        patch.setAccountId("   ");

        mvc.perform(
            patch("/api/vendors/{id}", linked.getId())
                .contentType("application/merge-patch+json")
                .content(objectMapper.writeValueAsBytes(patch))
        ).andExpect(status().isOk());

        // The merge ignores nulls, so a blank does not clear an existing link - it is simply not a
        // value. What matters is that "" never reaches the database.
        assertThat(vendorRepository.findById(linked.getId()).orElseThrow().getAccountId()).isEqualTo("kaneshie");
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
