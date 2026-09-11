package net.jojoaddison.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import net.jojoaddison.domain.GeographicSpace;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Team;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.ShiftType;
import net.jojoaddison.repository.GeographicSpaceRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.TeamRepository;
import net.jojoaddison.service.dto.RoundPlanDtos.Outcome;
import net.jojoaddison.service.dto.RoundPlanDtos.PlanReport;
import net.jojoaddison.service.dto.RoundPlanDtos.PlanRequest;
import net.jojoaddison.service.dto.RoundPlanDtos.Reason;
import net.jojoaddison.service.dto.RoundPlanDtos.RoundRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;

/**
 * The shipped {@code test} fixture can reach {@link ProfessionalServiceClient} — backlog item 60.
 *
 * <h2>What this exists to stop, and it is not a logic defect</h2>
 *
 * <p>{@link RoundPlanningService} selects staff with
 * {@code teamRepository.findByGeographicSpaceIdsContaining(round.geographicSpaceId())}. Until
 * 2026-09-11 <b>no {@code test} team carried {@code geographicSpaceIds} at all</b> — only {@code dev}'s
 * single team did, and {@code DevelopmentDataInitializer.resolveProfile()} prefers {@code test} where
 * both are active, which is what every stack runs. So every round short-circuited at
 * {@link Reason#NO_TEAM_COVERS_THE_SPACE} <b>before the cross-stack client was reached</b>: on
 * {@code quality/}, on {@code deploy/e2e/compose.yml} and under {@code ng serve} alike. Measured on the
 * live quality stack, four rounds across three spaces all came back
 * {@code outcome=UNPLANNED, reason=NO_TEAM_COVERS_THE_SPACE, roundId=null} with HTTP 200 and
 * {@code rosterServiceReachable=true} — a healthy-looking answer from a path that had not run.
 *
 * <p>That is why item 57's token defect — every round this service ever filed failing before a socket
 * was opened — was demonstrable on no stack: <em>nobody could file one.</em> {@code RoundPlanningServiceTest}
 * covers the planner against fixtures it builds itself and always passed; the gap was between the
 * planner and the data that ships beside it, which is a seam no test in this repository was reading.
 *
 * <h2>⚠ What the fixture field now costs, for whoever is about to remove it</h2>
 *
 * <p>The seed cannot carry a comment — JSON has none, and
 * {@code DevelopmentDataInitializer.ProfileData} binds strictly, so an explanatory key would fail to
 * bind rather than document anything. This javadoc is therefore where the consequence is written,
 * because this is the test that goes red when {@code team-2}'s coverage is taken away.
 *
 * <p>After item 57 fixed the token relay, {@code geographicSpaceIds: ["gs-osu"]} on {@code team-2}
 * converts the quality console's <em>"Plan and file"</em> button from <b>inert into a live cross-stack
 * write</b>: {@code quality/compose.yml} points {@code ProfessionalServiceClient} at
 * {@code hc-professional-quality-service:8081}, so pressing it creates a real {@code DutyRoster} on
 * hc-professional's quality roster, <b>with no undo in this console</b>. Since item 22 that write
 * carries real hc-patient {@code patientId}s taken from {@code directory_link} rather than whatever an
 * operator typed, so what lands over there is a plausible round against real subjects.
 *
 * <p>That was decided deliberately on 2026-09-11 and is the point rather than a side effect: quality is
 * the only place short of production that can exercise the cross-stack write at all, and the estate's
 * one other cross-stack assertion ({@code quality/startup.sh --verify}'s registration check) was added
 * for the same reason. On {@code deploy/e2e/compose.yml} there is no sibling and the base URL is
 * deliberately unset, so a press there spends a connect timeout and reports
 * {@link Reason#ROSTER_SERVICE_UNREACHABLE}; no Cypress spec drives the planner, so nothing changes
 * there.
 *
 * <h2>A unit test, over the fixture that ships</h2>
 *
 * <p>The repositories are mocked, but the <b>rows are the seed's own</b> — read out of
 * {@code data/hc-admin-ms-data.json} and bound to the same domain types the initializer saves, so a
 * field removed from that file is a field missing here. The two mocked queries are one-line filters that restate
 * their own method names ({@code geographicSpaceIds} contains the space; the professional's team is one
 * of those), which is the whole of what MongoDB does for them.
 *
 * <p>No container, and no real cross-stack call: {@link ProfessionalServiceClient} is stubbed. Asserting
 * against a live sibling is {@code quality/}'s job and deliberately not a unit test's.
 */
class SeedRoundReachabilityTest {

    private static final String SEED_DATA_LOCATION = "data/hc-admin-ms-data.json";

    /** A Wednesday, so the fairness window is an ordinary Monday-to-Sunday week. */
    private static final LocalDate WEDNESDAY = LocalDate.of(2026, 8, 12);

    /**
     * Osu, and the round this stack was measured failing to plan.
     *
     * <p>Named rather than derived because the fixture's coupling is the subject: {@code p2} is the
     * {@code NURSE} on {@code team-2} whose {@code homeSpaceId} is this space. Deriving "some role in
     * some space that happens to work" would go on passing against a fixture that had quietly become
     * reachable through an unrelated team, which is the state this test exists to tell apart.
     */
    private static final String OSU = "gs-osu";

    private static final String THE_NURSE_ON_TEAM_2 = "p2";

    private final TeamRepository teams = mock(TeamRepository.class);
    private final ProfessionalRepository professionals = mock(ProfessionalRepository.class);
    private final GeographicSpaceRepository spaces = mock(GeographicSpaceRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ProfessionalServiceClient client = mock(ProfessionalServiceClient.class);

    private final RoundPlanningService service = new RoundPlanningService(teams, professionals, spaces, mongoTemplate, client);

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    /**
     * The {@code test} profile's node of the shipped seed file.
     *
     * <p>Read as a tree and bound per collection rather than through
     * {@code DevelopmentDataInitializer.ProfileData}, which is package-private and deliberately stays
     * that way. Nothing is lost: the rows below bind to the same domain types the initializer saves, and
     * whether the <em>file</em> binds faithfully to {@code ProfileData} is
     * {@code DevelopmentDataInitializerTest}'s question, asked there with a strict mapper.
     */
    private JsonNode testProfile() throws Exception {
        try (InputStream inputStream = new ClassPathResource(SEED_DATA_LOCATION).getInputStream()) {
            return mapper.readTree(inputStream).get("test");
        }
    }

    private <T> List<T> rows(JsonNode profile, String collection, Class<T> type) {
        return mapper.convertValue(profile.get(collection), mapper.getTypeFactory().constructCollectionType(List.class, type));
    }

    /**
     * Wires the mocked repositories over the seeded rows, optionally with every team's coverage stripped.
     *
     * <p>The stripping is what makes this file's own sensitivity checkable: the same fixture, the same
     * planner, the same assertions, and the one field removed — see
     * {@link #withoutTheCoverageTheRoundNeverReachesTheClient()}.
     */
    private void seed(JsonNode profile, boolean stripCoverage) {
        List<Team> seededTeams = rows(profile, "teams", Team.class);
        List<Professional> seededProfessionals = rows(profile, "professionals", Professional.class);
        List<GeographicSpace> seededSpaces = rows(profile, "geographicSpaces", GeographicSpace.class);
        if (stripCoverage) {
            seededTeams.forEach(team -> team.setGeographicSpaceIds(null));
        }

        when(teams.findByGeographicSpaceIdsContaining(anyString())).thenAnswer(invocation -> {
            String wanted = invocation.getArgument(0);
            return seededTeams
                .stream()
                .filter(team -> team.getGeographicSpaceIds() != null && team.getGeographicSpaceIds().contains(wanted))
                .toList();
        });
        when(professionals.findByTeamIn(anyCollection())).thenAnswer(invocation -> {
            Collection<Team> covering = invocation.getArgument(0);
            List<String> ids = covering.stream().map(Team::getId).toList();
            return seededProfessionals
                .stream()
                .filter(candidate -> candidate.getTeam() != null && ids.contains(candidate.getTeam().getId()))
                .toList();
        });
        when(spaces.findById(anyString())).thenAnswer(invocation ->
            seededSpaces
                .stream()
                .filter(space -> invocation.getArgument(0).equals(space.getId()))
                .findFirst()
        );
        // The unstubbed MongoTemplate answers 0 shifts and no rostered OFF day, which is the state of
        // any candidate the fairness and double-booking rules have nothing to say about. Neither rule
        // is this file's subject; both have their own cases in RoundPlanningServiceTest.
    }

    private static PlanRequest nurseRoundInOsu() {
        return new PlanRequest(
            WEDNESDAY,
            List.of(new RoundRequest(ProfessionalRole.NURSE, ShiftType.DAY, OSU, "Morning round", "Routine calls", List.of()))
        );
    }

    /**
     * The shipped fixture plans a round and the client is called.
     *
     * <p><b>This is the assertion that goes red if {@code team-2}'s {@code geographicSpaceIds} is
     * removed from the {@code test} seed</b>, and it fails at the reason rather than at the outcome:
     * {@link Reason#NO_TEAM_COVERS_THE_SPACE} is the exact short circuit item 60 was opened about.
     */
    @Test
    void theShippedTestFixtureCanReachTheCrossStackWrite() throws Exception {
        seed(testProfile(), false);
        when(client.fileRound(any())).thenReturn("round-created-over-there");

        PlanReport report = service.plan(nurseRoundInOsu());

        assertThat(report.rounds()).hasSize(1);
        assertThat(report.rounds().getFirst().reason()).isNotEqualTo(Reason.NO_TEAM_COVERS_THE_SPACE);
        assertThat(report.rounds().getFirst().outcome()).isEqualTo(Outcome.PLANNED);
        assertThat(report.rounds().getFirst().professionalId()).isEqualTo(THE_NURSE_ON_TEAM_2);
        assertThat(report.rounds().getFirst().roundId()).isEqualTo("round-created-over-there");
        verify(client).fileRound(any());
    }

    /**
     * The same fixture with the coverage taken away stops exactly where the stacks were stopping.
     *
     * <p>Without this case the one above could pass for a reason that has nothing to do with the field
     * — a second team gaining coverage, a repository mock that matches too much — and nobody would
     * know. Here the planner never opens a socket, which is the measured production-to-be behaviour
     * this fixture change exists to end.
     */
    @Test
    void withoutTheCoverageTheRoundNeverReachesTheClient() throws Exception {
        seed(testProfile(), true);

        PlanReport report = service.plan(nurseRoundInOsu());

        assertThat(report.rounds().getFirst().outcome()).isEqualTo(Outcome.UNPLANNED);
        assertThat(report.rounds().getFirst().reason()).isEqualTo(Reason.NO_TEAM_COVERS_THE_SPACE);
        verify(client, never()).fileRound(any());
    }
}
