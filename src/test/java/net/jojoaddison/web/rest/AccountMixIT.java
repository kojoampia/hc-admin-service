package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Vendor;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.VendorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The account-mix chart — item 10 of admin-gaps.md.
 *
 * <p>It grouped professionals by role until 2026-08-21: a breakdown of one of the three tiles above
 * it rather than of the network, under a caption asking who holds an account on the platform.
 *
 * <p><b>The assertion that matters is that the chart adds up to the tiles it sits under.</b> A
 * stacked bar is a breakdown of a number, and this dashboard has already shipped a figure that
 * disagreed with the screen one click away; a figure disagreeing with the tile directly above it
 * would be the same defect with less excuse. The service therefore derives the mix from the same
 * {@code NetworkTotals} the payload carries, and these tests pin that rather than the counting.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class AccountMixIT {

    private static final String ENDPOINT = "/api/dashboard/metrics";

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private VendorRepository vendorRepository;

    @BeforeEach
    void seed() {
        patientRepository.deleteAll();
        professionalRepository.deleteAll();
        vendorRepository.deleteAll();
    }

    /**
     * Three segments, named for account types and in a fixed order.
     *
     * <p>Order is asserted because the legend colours segments by index: sorted by size, a colour
     * silently changes meaning the day two counts cross, and nothing on the screen would say so.
     */
    @Test
    void breaksTheNetworkDownByAccountType() throws Exception {
        patientRepository.save(new Patient().joinedOn(LocalDate.of(2026, 1, 5)).status(AccountStatus.ACTIVE));
        patientRepository.save(new Patient().joinedOn(LocalDate.of(2026, 2, 5)).status(AccountStatus.ACTIVE));
        professionalRepository.save(professional());
        vendorRepository.save(vendor("Ridge Diagnostics"));
        vendorRepository.save(vendor("Volta Nutrition"));
        vendorRepository.save(vendor("Kumasi Supplies"));

        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountMix.length()").value(3))
            .andExpect(jsonPath("$.accountMix[0].key").value("patients"))
            .andExpect(jsonPath("$.accountMix[0].value").value(2))
            .andExpect(jsonPath("$.accountMix[1].key").value("professionals"))
            .andExpect(jsonPath("$.accountMix[1].value").value(1))
            .andExpect(jsonPath("$.accountMix[2].key").value("vendors"))
            .andExpect(jsonPath("$.accountMix[2].value").value(3));
    }

    /**
     * <b>The chart is the tiles, split three ways.</b>
     *
     * <p>Counted once in the service and handed to both, so this cannot drift — which is the point
     * of asserting it here rather than trusting two independent queries to agree forever. Archived
     * records are included on both sides for the same reason: the tiles render {@code network}.
     */
    @Test
    void addsUpToTheTilesAboveIt() throws Exception {
        patientRepository.save(new Patient().joinedOn(LocalDate.of(2026, 1, 5)).status(AccountStatus.ACTIVE));
        patientRepository.save(new Patient().joinedOn(LocalDate.of(2026, 3, 5)).status(AccountStatus.ACTIVE).isArchived(true));
        professionalRepository.save(professional());

        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.network.patients").value(2))
            .andExpect(jsonPath("$.accountMix[0].value").value(2))
            .andExpect(jsonPath("$.network.professionals").value(1))
            .andExpect(jsonPath("$.accountMix[1].value").value(1))
            .andExpect(jsonPath("$.network.vendors").value(0))
            .andExpect(jsonPath("$.accountMix[2].value").value(0));
    }

    /**
     * An empty category stays in the chart as a zero.
     *
     * <p>The shape this replaced dropped groups with nobody in them, which on a platform with no
     * vendors yet leaves a two-segment chart that looks complete. "Vendors · 0" is the honest
     * rendering, and the client's share calculation already handles a zero total.
     */
    @Test
    void keepsAnEmptyCategoryRatherThanDroppingIt() throws Exception {
        professionalRepository.save(professional());

        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountMix.length()").value(3))
            .andExpect(jsonPath("$.accountMix[0].value").value(0))
            .andExpect(jsonPath("$.accountMix[2].value").value(0));
    }

    /**
     * And no role appears in it.
     *
     * <p>The keys are account types now. A role key here would mean the old grouping is back, and it
     * would render as a plausible chart under a caption that no longer describes it.
     */
    @Test
    void carriesNoProfessionalRoles() throws Exception {
        professionalRepository.save(professional());

        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountMix[?(@.key == 'NURSE')]").isEmpty())
            .andExpect(jsonPath("$.accountMix[?(@.key == 'DOCTOR')]").isEmpty());
    }

    /** Name, category and status are all required — a vendor with any of them missing will not save. */
    private static Vendor vendor(String name) {
        return new Vendor().name(name).category("Diagnostics").status(AccountStatus.ACTIVE);
    }

    private static Professional professional() {
        return new Professional()
            .role(ProfessionalRole.NURSE)
            .licenceNumber("NMC/RN/19-2210")
            .verification(VerificationStatus.VERIFIED)
            .status(AccountStatus.ACTIVE)
            .joinedOn(LocalDate.of(2021, 6, 11));
    }
}
