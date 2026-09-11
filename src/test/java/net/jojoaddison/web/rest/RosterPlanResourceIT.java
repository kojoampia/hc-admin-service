package net.jojoaddison.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import net.jojoaddison.IntegrationTest;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.Team;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.repository.TeamRepository;
import net.jojoaddison.security.AuthoritiesConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /api/roster-plans} — the planner's REST contract.
 *
 * <p><b>The roster service is disabled in the shared test config</b>, so every write here takes the
 * {@code ROSTER_SERVICE_NOT_CONFIGURED} path — nothing is dialled. That is not a limitation worked
 * around; it is the point of this class. What only a booted context can check is the wiring — that
 * the request binds, that validation refuses a malformed body before anything is planned, and that a
 * cross-stack write that did not happen comes back as a {@code 200} carrying a per-round failure
 * rather than as a {@code 5xx} the console would render as a generic error. The ranking itself is
 * {@code RoundPlanningServiceTest}'s, over mocks, where the cases can be made to differ by one
 * property at a time — and so is the genuine outage, which cannot be produced here without opening a
 * socket to a port nobody is on.
 *
 * <p>{@code addFilters = false} like every other {@code *ResourceIT} here; who may call this is
 * {@code ApiAuthorizationIT}'s, which is the only class in this repository that runs with the
 * security chain on.
 *
 * <p><b>{@code ROLE_ADMIN} on the mock user, and that is not padding.</b> This is the one resource
 * in the service carrying a {@code @PreAuthorize}, which is method security and therefore runs even
 * with the filter chain switched off — so a plain {@code @WithMockUser} gets 403 here where it gets
 * through everywhere else. That difference is the point of the annotation: it is the only rule in
 * this service that an {@code addFilters = false} test cannot bypass, on the one endpoint that
 * spends the caller's own token on another stack.
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(authorities = AuthoritiesConstants.ADMIN)
class RosterPlanResourceIT {

    private static final String API_URL = "/api/roster-plans";
    private static final String SPACE = "space-osu-it";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ProfileRepository profileRepository;

    private Team team;
    private Professional professional;
    private Profile profile;

    @BeforeEach
    void seed() {
        // Built from the generated fixtures rather than by hand: Profile and Professional carry
        // eight @NotNull fields between them and DatabaseConfiguration registers a
        // ValidatingMongoEventListener, so a hand-rolled fixture fails on a constraint that has
        // nothing to do with what is being tested here.
        team = teamRepository.save(TeamResourceIT.createEntity().geographicSpaceIds(List.of(SPACE)));
        profile = profileRepository.save(ProfileResourceIT.createEntity().firstName("Ama").lastName("Boateng"));
        professional = professionalRepository.save(
            ProfessionalResourceIT.createEntity()
                .role(ProfessionalRole.NURSE)
                .status(AccountStatus.ACTIVE)
                .profile(profile)
                .team(team)
                .homeSpaceId(SPACE)
        );
    }

    @AfterEach
    void cleanup() {
        professionalRepository.delete(professional);
        profileRepository.delete(profile);
        teamRepository.delete(team);
    }

    private static String plan(String spaceId) {
        return (
            "{\"date\":\"%s\",\"rounds\":[{\"role\":\"NURSE\",\"shift\":\"DAY\",\"geographicSpaceId\":\"%s\"," +
            "\"name\":\"Morning round\",\"visits\":[]}]}"
        ).formatted(LocalDate.now().plusDays(1), spaceId);
    }

    /**
     * A staffed round whose write does not happen is {@code FAILED} with a {@code 200} — not
     * {@code UNPLANNED}, and not a {@code 5xx}.
     *
     * <p>Decision 10 of {@code duty-roster-resolution.md} § 9.1, asserted on the wire. The chosen
     * professional is still named, so the administrator can retry the same round rather than
     * reconstructing it.
     *
     * <p><b>The reason is {@code ROSTER_SERVICE_NOT_CONFIGURED} and {@code rosterServiceReachable}
     * stays {@code true}</b>, which is backlog item 24 asserted at the layer that carries it. The
     * shared test config disables the client, so this run is the {@code enabled=false} case: nothing
     * was dialled, so nothing is known about hc-professional and the report must not claim an
     * outage. This assertion read {@code false} / {@code ROSTER_SERVICE_UNREACHABLE} until
     * 2026-09-06 and passed, because the suite — like the console — had one reason standing in for
     * both facts.
     */
    @Test
    void aWriteThisDeploymentCannotMakeIsReportedAsMisconfiguredWithA200() throws Exception {
        mvc.perform(post(API_URL).contentType(MediaType.APPLICATION_JSON).content(plan(SPACE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rosterServiceReachable").value(true))
            .andExpect(jsonPath("$.rounds[0].outcome").value("FAILED"))
            .andExpect(jsonPath("$.rounds[0].reason").value("ROSTER_SERVICE_NOT_CONFIGURED"))
            .andExpect(jsonPath("$.rounds[0].professionalId").value(professional.getId()))
            .andExpect(jsonPath("$.rounds[0].professionalName").value("Ama Boateng"))
            .andExpect(jsonPath("$.rounds[0].roundId").doesNotExist());
    }

    /**
     * A round nobody can staff is {@code UNPLANNED}, and the roster service is <b>not</b> reported
     * unreachable — nothing was written, so nothing is known to be wrong with it.
     *
     * <p>The pair with the case above is the assertion that matters: the two states have to be
     * distinguishable on the wire, or the console cannot tell "widen the request" from "this
     * deployment cannot file anything".
     *
     * <p><b>They are told apart by {@code outcome} and {@code reason}, and no longer by
     * {@code rosterServiceReachable}</b> — both cases now report it {@code true}, because in neither
     * of them did anything dial the far service. That is the correct reading of a flag that is an
     * observation, and it is why the console reads the round rather than the flag when deciding
     * which standing panel to show.
     */
    @Test
    void anUnstaffableRoundIsUnplannedAndDoesNotClaimAnOutage() throws Exception {
        mvc.perform(post(API_URL).contentType(MediaType.APPLICATION_JSON).content(plan("space-nobody-covers")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rosterServiceReachable").value(true))
            .andExpect(jsonPath("$.rounds[0].outcome").value("UNPLANNED"))
            .andExpect(jsonPath("$.rounds[0].reason").value("NO_TEAM_COVERS_THE_SPACE"));
    }

    /** A body with no rounds is refused rather than answered with an empty report. */
    @Test
    void refusesARequestWithNoRounds() throws Exception {
        mvc.perform(post(API_URL).contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-08-12\",\"rounds\":[]}")).andExpect(
            status().isBadRequest()
        );
    }

    /** A round with no date, role, shift or space is refused before anything is planned. */
    @Test
    void refusesAMalformedRound() throws Exception {
        mvc.perform(
            post(API_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2026-08-12\",\"rounds\":[{\"name\":\"No role, no shift, no space\"}]}")
        ).andExpect(status().isBadRequest());
    }
}
