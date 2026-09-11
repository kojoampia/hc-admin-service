package net.jojoaddison.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.repository.DirectoryLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The console's dashboard endpoint answers, and answers with every key the client destructures.
 *
 * <p>This exists because the absence of it was invisible. {@code ConsoleMetricsService} has called
 * {@code api/dashboard/metrics} since it was written; an in-browser mock answered it, and when that
 * was deleted the call became a 404 that no screen surfaced — the dashboard, platform-health and the
 * sign-in figures simply rendered empty, in production, for days.
 *
 * <p>So the assertions are about <em>shape</em>, not values. An empty database is the normal state
 * here (production seeds nothing), and a test that demanded non-zero counts would fail for the
 * wrong reason. What must hold is that the endpoint exists, is readable by an operator, and returns
 * every field the client reads — a missing key is what produces `undefined` in a template and a
 * blank card on screen.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class DashboardMetricsResourceIT {

    private static final String ENDPOINT = "/api/dashboard/metrics";

    @Autowired
    private MockMvc restMockMvc;

    @Autowired
    private DirectoryLinkRepository directoryLinkRepository;

    @Test
    void endpointAnswers() throws Exception {
        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    /**
     * Every top-level key {@code DashboardMetrics} declares. Parameterised so a newly added field
     * that nobody serves shows up as one named failure rather than a vague one.
     */
    @ParameterizedTest
    @ValueSource(
        strings = {
            "$.network",
            "$.loaded",
            "$.unreadMessages",
            "$.openTasks",
            "$.pendingApprovals",
            "$.roster",
            "$.degradedServices",
            "$.platformServices",
            "$.messageVolume",
            "$.accountMix",
            "$.caseLoad",
            "$.sparklines",
            "$.capabilities",
            "$.uptime",
            "$.professionalsAwaitingRecord",
        }
    )
    void servesEveryFieldTheClientReads(String path) throws Exception {
        restMockMvc.perform(get(ENDPOINT)).andExpect(status().isOk()).andExpect(jsonPath(path).exists());
    }

    @Test
    void nestedShapesAreComplete() throws Exception {
        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.network.patients").exists())
            .andExpect(jsonPath("$.network.professionals").exists())
            .andExpect(jsonPath("$.network.vendors").exists())
            .andExpect(jsonPath("$.roster.coverPercent").exists())
            .andExpect(jsonPath("$.roster.unassignedSlots").exists())
            .andExpect(jsonPath("$.roster.rosteredStaff").exists())
            .andExpect(jsonPath("$.roster.shiftsThisWeek").exists())
            .andExpect(jsonPath("$.platformServices.total").exists())
            .andExpect(jsonPath("$.platformServices.healthy").exists());
    }

    /**
     * An empty roster is 0% covered, not 100%.
     *
     * <p>The division has no answer with no shifts, and the two defensible defaults say opposite
     * things on a card labelled "cover". Pinning it means a later refactor cannot quietly flip a
     * screen from "nothing is covered" to "everything is".
     */
    @Test
    void anEmptyRosterReportsNoCoverRatherThanFullCover() throws Exception {
        restMockMvc.perform(get(ENDPOINT)).andExpect(status().isOk()).andExpect(jsonPath("$.roster.coverPercent").value(0));
    }

    /**
     * Message volume always spans the full window, including months with nothing in them.
     *
     * <p>Dropping empty months compresses the axis and draws a trend that did not happen.
     */
    @Test
    void messageVolumeCoversEveryMonthInTheWindow() throws Exception {
        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messageVolume.length()").value(6))
            .andExpect(jsonPath("$.messageVolume[0].month").exists())
            .andExpect(jsonPath("$.messageVolume[0].count").exists());
    }

    /**
     * Uptime is present but unmeasured here: no metrics store is configured in tests, so the
     * percentage is null and the window still says what it would have measured. Null rather than
     * zero, because 0% reads as a total outage rather than "not measured".
     */
    @Test
    void uptimeIsReportedAsUnmeasuredWithoutAMetricsStack() throws Exception {
        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uptime.percent").doesNotExist())
            .andExpect(jsonPath("$.uptime.windowDays").value(7));
    }

    /**
     * <b>A clinician this service knows about and holds no record for is counted, and counted
     * apart.</b>
     *
     * <p>Backlog item 46, reported from production: a professional registered, the consumer group's
     * offset moved with no lag, the link was written — and the dashboard did not move, because
     * {@code network.professionals} counts {@code Professional} documents and a link is not one. It
     * still counts documents, deliberately; what is new is that the ones with no document are a figure
     * of their own rather than nothing at all.
     *
     * <p>The tile's own number is asserted here too, and that is the point of the case rather than
     * thoroughness: the way this goes wrong is somebody folding the links into
     * {@code network.professionals} to make the tile move, which would silently redefine three other
     * figures derived from that collection — the account-mix chart, the professionals sparkline whose
     * last point {@code SparklinesIT} pins to this number, and {@code loaded}.
     *
     * <p><b>A before/after delta, not {@code == 1} and {@code == 0}.</b> It was the literals until
     * 2026-09-07, which made this case depend on no other integration test leaving an
     * {@code HC_PROFESSIONAL} link behind — a dependency nothing declares and nothing enforces, in a
     * repository whose backlog carries three separate incidents of a literal another test could move
     * (items 15, 34 and 45). The delta is immune to it and says the same thing more exactly: what is
     * being asserted is that <em>one</em> link moves this figure by one and the other figure not at
     * all, which is the property, where "the answer is 1" is a coincidence of an empty database.
     */
    @Test
    void countsTheCliniciansItKnowsAboutSeparatelyFromTheOnesItHasRecordsFor() throws Exception {
        int awaitingBefore = readInt("$.professionalsAwaitingRecord");
        int professionalsBefore = readInt("$.network.professionals");

        DirectoryLink clinician = new DirectoryLink();
        clinician.setSource(DirectorySource.HC_PROFESSIONAL);
        clinician.setExternalKey("acc-dashboard-metrics-it");
        clinician.setSubjectKind(DirectorySubjectKind.PROFESSIONAL);
        directoryLinkRepository.save(clinician);

        try {
            restMockMvc
                .perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalsAwaitingRecord").value(awaitingBefore + 1))
                .andExpect(jsonPath("$.network.professionals").value(professionalsBefore));
        } finally {
            directoryLinkRepository.deleteById(clinician.getId());
        }
    }

    /**
     * <b>The tile and the list it links to are one rule, not two spellings of it.</b>
     *
     * <p>The dashboard counts through {@code DirectoryLinkRepository.countBySourceWithNoLocalRecord}
     * and the professional directory lists through
     * {@code GET /api/directory-links?source=HC_PROFESSIONAL&unlinked=true}. Those are two queries,
     * and a figure that disagrees with the rows underneath it is the defect this dashboard has
     * already had twice — the hero saying 0% cover over a grid saying 80%, and an account-mix chart
     * counted a second time. Asserted against each other rather than each against a literal, which
     * would pass with both wrong the same way.
     *
     * <p>The "non-zero first" guard is a <b>delta</b> since 2026-09-07 and no longer the literal
     * {@code "1"}, for the reason given on the case above: the literal was a claim about every other
     * integration test in the suite, not about this one. Both readings still have to move, and move
     * together — two counts that agree on a number neither of them changed would prove nothing.
     */
    @Test
    void theTileAgreesWithTheListItSendsYouTo() throws Exception {
        int listedBefore = listedAwaitingClinicians();
        int tileBefore = readInt("$.professionalsAwaitingRecord");

        DirectoryLink clinician = new DirectoryLink();
        clinician.setSource(DirectorySource.HC_PROFESSIONAL);
        clinician.setExternalKey("acc-dashboard-metrics-it-agreement");
        clinician.setSubjectKind(DirectorySubjectKind.PROFESSIONAL);
        directoryLinkRepository.save(clinician);

        try {
            int listed = listedAwaitingClinicians();

            // Both readings moved, by this one link: two figures agreeing on a number that neither
            // of them changed is the state every stack that has consumed no registration is in, and
            // it would pass with the endpoint returning a constant.
            assertThat(listed)
                .as("the list is one longer for the link just written")
                .isEqualTo(listedBefore + 1);
            restMockMvc
                .perform(get(ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.professionalsAwaitingRecord").value(listed))
                .andExpect(jsonPath("$.professionalsAwaitingRecord").value(tileBefore + 1));
        } finally {
            directoryLinkRepository.deleteById(clinician.getId());
        }
    }

    /** One figure off the dashboard payload, so a case can assert what a write moved rather than what it landed on. */
    private int readInt(String jsonPath) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(
            restMockMvc.perform(get(ENDPOINT)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
            jsonPath
        );
    }

    /** The same rule read the other way — through the endpoint the tile links to. */
    private int listedAwaitingClinicians() throws Exception {
        String total = restMockMvc
            .perform(get("/api/directory-links").param("source", "HC_PROFESSIONAL").param("unlinked", "true"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("X-Total-Count");
        assertThat(total).as("every list endpoint here is paginated and says so in a header").isNotNull();
        return Integer.parseInt(total);
    }

    /** Capabilities are present even on an empty database, and none of them claims to be Live. */
    @Test
    void capabilitiesAreServedWithoutData() throws Exception {
        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capabilities.length()").value(4))
            .andExpect(jsonPath("$.capabilities[0].name").exists())
            .andExpect(jsonPath("$.capabilities[0].icon").exists())
            .andExpect(jsonPath("$.capabilities[0].status").exists())
            // Nothing is checkable without a metrics store, so nothing may claim it works.
            .andExpect(jsonPath("$.capabilities[?(@.status == 'Live')]").isEmpty());
    }
}
