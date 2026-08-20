package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Vendor;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.repository.VendorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/vendors/summary} — the vendor directory's four tiles.
 *
 * <p>Its own class rather than another case in {@link VendorResourceIT}, because every assertion
 * here is about totals over the whole collection and that only means anything if this test owns
 * what is in it. {@code VendorResourceIT} inserts one vendor and deletes it again; a sum asserted
 * alongside it would be reading whatever else happened to be there.
 *
 * <p><strong>The two computed figures are the point.</strong> The counts could be checked from
 * {@code X-Total-Count} on the list endpoint — the sum and the distinct count cannot be got any
 * other way, and are the reason the endpoint exists.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class VendorSummaryIT {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private VendorRepository vendorRepository;

    @BeforeEach
    void seed() {
        vendorRepository.deleteAll();
        vendorRepository.saveAll(
            List.of(
                vendor("Accra Medical Supplies", "Consumables", AccountStatus.ACTIVE, new BigDecimal("1000.50"), false),
                vendor("Tema Diagnostics", "Diagnostics", AccountStatus.ACTIVE, new BigDecimal("2000.25"), false),
                vendor("Kumasi Logistics", "Consumables", AccountStatus.UNDER_REVIEW, new BigDecimal("500.25"), false),
                vendor("Takoradi Oxygen", "Gases", AccountStatus.PENDING, new BigDecimal("99.00"), false),
                // No category recorded: counts as a vendor, not as a category of its own.
                vendor("Unclassified Supplier", "  ", AccountStatus.ACTIVE, new BigDecimal("10.00"), false),
                // Spend not recorded. Must contribute nothing rather than break the sum.
                vendor("Never Traded", "Diagnostics", AccountStatus.PENDING, null, false),
                // Archived: out of every figure, because the directory below does not show it.
                vendor("Retired Vendor", "Retired Category", AccountStatus.ACTIVE, new BigDecimal("9999.99"), true)
            )
        );
    }

    @AfterEach
    void tearDown() {
        vendorRepository.deleteAll();
    }

    @Test
    void summaryTotalsTheWholeCollection() throws Exception {
        mvc
            .perform(get("/api/vendors/summary"))
            .andExpect(status().isOk())
            // 1000.50 + 2000.25 + 500.25 + 99.00 + 10.00. The archived 9999.99 is excluded, and the
            // null contributes nothing — asserted as a number so 3610.00 and 3610 both pass.
            .andExpect(jsonPath("$.spendToDate").value(new BigDecimal("3610.00").doubleValue()))
            // Consumables, Diagnostics, Gases. The blank is not a category and the archived
            // vendor's "Retired Category" is not in the directory.
            .andExpect(jsonPath("$.categoryCount").value(3))
            // Three unarchived ACTIVE — Accra, Tema and the uncategorised one. The archived fourth
            // is excluded, which is what makes this 3 and not 4.
            .andExpect(jsonPath("$.activeContracts").value(3))
            // UNDER_REVIEW and PENDING share the tile: one under review, two pending.
            .andExpect(jsonPath("$.underReview").value(3));
    }

    /**
     * An empty directory reports zeros, not nulls.
     *
     * <p>The tiles render the number they are given. A null would print as blank and read as
     * "not loaded" rather than "none", which is the distinction the wage-rates screen already
     * takes trouble over.
     */
    @Test
    void summaryOfAnEmptyDirectoryIsZero() throws Exception {
        vendorRepository.deleteAll();

        mvc
            .perform(get("/api/vendors/summary"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.spendToDate").value(0))
            .andExpect(jsonPath("$.categoryCount").value(0))
            .andExpect(jsonPath("$.activeContracts").value(0))
            .andExpect(jsonPath("$.underReview").value(0));
    }

    /**
     * The path is a literal segment, not a vendor whose id is "summary".
     *
     * <p>{@code /{id}} is declared on the same controller. PathPattern prefers the literal, but the
     * two orderings are indistinguishable in a passing test unless something asserts which handler
     * answered — a 404 here would be {@code getVendor("summary")} winning.
     */
    @Test
    void summaryIsNotReadAsAVendorId() throws Exception {
        mvc.perform(get("/api/vendors/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.categoryCount").exists());
    }

    private static Vendor vendor(String name, String category, AccountStatus status, BigDecimal spend, boolean archived) {
        return new Vendor().name(name).category(category).status(status).spendToDate(spend).isArchived(archived);
    }
}
