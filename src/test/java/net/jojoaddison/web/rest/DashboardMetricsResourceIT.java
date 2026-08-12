package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import net.jojoaddison.IntegrationTest;
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

    /** Capabilities are declared rather than counted, so they are present even on an empty database. */
    @Test
    void capabilitiesAreServedWithoutData() throws Exception {
        restMockMvc
            .perform(get(ENDPOINT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capabilities.length()").value(4))
            .andExpect(jsonPath("$.capabilities[0].name").exists())
            .andExpect(jsonPath("$.capabilities[0].icon").exists())
            .andExpect(jsonPath("$.capabilities[0].status").exists());
    }
}
