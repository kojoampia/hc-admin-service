package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.PlanFeature;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.ProfessionalVerification;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.ServicePlan;
import net.jojoaddison.domain.WageRate;
import net.jojoaddison.domain.enumeration.AccountStatus;
import net.jojoaddison.domain.enumeration.BillingType;
import net.jojoaddison.domain.enumeration.DirectorySource;
import net.jojoaddison.domain.enumeration.DirectorySubjectKind;
import net.jojoaddison.domain.enumeration.MessageStatus;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.ShiftType;
import net.jojoaddison.domain.enumeration.TaskState;
import net.jojoaddison.domain.enumeration.VerificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Guards the contract between {@code data/hc-admin-ms-data.json} and
 * {@link DevelopmentDataInitializer.ProfileData}.
 *
 * <p>The seed data previously failed to load in silence: the initializer expected a {@code data}
 * root wrapper the file does not have, bound collections as {@code List<Map<String, T>>} when the
 * file holds plain arrays, and used non-static inner classes Jackson cannot instantiate. Every
 * failure was swallowed by a catch-and-log. These tests fail loudly if any of that regresses.
 */
class DevelopmentDataInitializerTest {

    private static final String SEED_DATA_LOCATION = "data/hc-admin-ms-data.json";

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private Map<String, DevelopmentDataInitializer.ProfileData> readSeedData() throws Exception {
        try (InputStream inputStream = new ClassPathResource(SEED_DATA_LOCATION).getInputStream()) {
            return mapper.readValue(inputStream, new TypeReference<Map<String, DevelopmentDataInitializer.ProfileData>>() {});
        }
    }

    @Test
    void shouldBindSeedDataKeyedByProfileAtTheRoot() throws Exception {
        Map<String, DevelopmentDataInitializer.ProfileData> seedData = readSeedData();

        assertThat(seedData).containsOnlyKeys("dev", "test");
        assertThat(seedData.get("dev")).isNotNull();
        assertThat(seedData.get("test")).isNotNull();
    }

    @Test
    void shouldBindEveryDevCollection() throws Exception {
        DevelopmentDataInitializer.ProfileData dev = readSeedData().get("dev");

        assertThat(dev.getAddresses()).hasSize(1);
        assertThat(dev.getContacts()).hasSize(1);
        assertThat(dev.getFacilities()).hasSize(1);
        assertThat(dev.getAudits()).hasSize(1);
        assertThat(dev.getOrganisations()).hasSize(1);
        assertThat(dev.getPersons()).hasSize(1);
        assertThat(dev.getTeams()).hasSize(1);
        assertThat(dev.getGeographicSpaces()).hasSize(2);
        assertThat(dev.getPricingPlans()).hasSize(1);
        assertThat(dev.getSystemCatalogs()).hasSize(1);
    }

    @Test
    void shouldBindScalarsEnumsAndDatesOnDomainObjects() throws Exception {
        DevelopmentDataInitializer.ProfileData dev = readSeedData().get("dev");

        // This read a dutyRosters row until 2026-09-04 — the one record in the dev seed that
        // carried a String, a LocalDate, two enums and an id in one document, which is what made it
        // the natural subject here. It went with the entity when hc-professional became the roster
        // of record. The pricing plan is the replacement and covers the same four shapes: an id, a
        // String, a BigDecimal and an enum.
        var plan = dev.getPricingPlans().get(0);
        assertThat(plan.getId()).isEqualTo("plan-basic");
        assertThat(plan.getName()).isEqualTo("Basic Health Plan");
        assertThat(plan.getPrice()).isEqualByComparingTo("29.99");
        assertThat(plan.getBillingCycle()).isEqualTo(BillingType.MONTHLY);
        assertThat(plan.getActive()).isTrue();

        var space = dev.getGeographicSpaces().get(0);
        assertThat(space.getId()).isEqualTo("building-a");
        assertThat(space.getParentId()).isNull();

        assertThat(dev.getAddresses().get(0).getStreetAddress()).isEqualTo("123 Main St");
        assertThat(dev.getAudits().get(0).getCreatedDate()).isNotNull();
    }

    @Test
    void shouldDefaultAbsentAndEmptyCollectionsToEmptyListsRatherThanNull() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        // `profiles` (the pre-console HCProfile) is an empty array in both profiles, and three of
        // the console collections carry no records because the prototype this data came from had
        // none — they are the honest empties to assert on now that `test` holds the console dataset.
        assertThat(test.getProfiles()).isNotNull().isEmpty();
        assertThat(test.getCareActivities()).isNotNull().isEmpty();
        assertThat(test.getDocuments()).isNotNull().isEmpty();
        assertThat(test.getUserOptions()).isNotNull().isEmpty();

        // A ProfileData with no JSON at all must still expose empty lists, not nulls.
        DevelopmentDataInitializer.ProfileData empty = mapper.readValue("{}", DevelopmentDataInitializer.ProfileData.class);
        assertThat(empty.getAddresses()).isNotNull().isEmpty();
        assertThat(empty.getSystemCatalogs()).isNotNull().isEmpty();
    }

    @Test
    void shouldPopulateTestProfileCollectionsThatCarryRecords() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        // Five sites across three vendors, plus the one decommissioned wing that predates the
        // console model. The five exist so the vendor record's facilities card has something to
        // render — a relation seeded empty is a card nobody can tell is broken.
        assertThat(test.getFacilities()).hasSize(6);
        assertThat(test.getAudits()).hasSize(1);
        assertThat(test.getPricingPlans()).hasSize(1);
    }

    /**
     * The console dataset, seeded under `test` so the client can run against a real api instead of
     * its own in-browser mock.
     *
     * <p>Counts rather than contents: the point is that every collection the console reads is
     * present and non-trivial. A seed file that parses but delivers three records would satisfy a
     * "not empty" assertion and still leave every screen looking broken.
     */
    @Test
    void shouldCarryTheConsoleDatasetUnderTest() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getPersonProfiles()).hasSize(22);
        assertThat(test.getPatients()).hasSize(15);
        assertThat(test.getProfessionals()).hasSize(10);
        assertThat(test.getVendors()).hasSize(9);
        // The relation itself, not just the two collections. Both sides are DBRefs and only the
        // vendor's array is the owning one, so a seed that sets `vendor` on the facility and
        // nothing on the vendor writes two documents that agree in the file and disagree in Mongo:
        // the facility knows its vendor, the vendor lists no sites, and the record renders empty.
        assertThat(test.getVendors().stream().filter(vendor -> !vendor.getFacilities().isEmpty()).count()).isEqualTo(3);
        assertThat(test.getFacilities().stream().filter(facility -> facility.getVendor() != null).count()).isEqualTo(5);
        assertThat(test.getAngels()).hasSize(12);
        assertThat(test.getMessages()).hasSize(43);
        assertThat(test.getTasks()).hasSize(34);
        assertThat(test.getRosterWeeks()).hasSize(15);
        assertThat(test.getShiftAssignments()).hasSize(921);
        assertThat(test.getServicePlans()).hasSize(3);
        assertThat(test.getPlanFeatures()).hasSize(18);
        assertThat(test.getPlatformServices()).hasSize(13);
        assertThat(test.getAuditEntries()).hasSize(7);
        assertThat(test.getAddresses()).hasSize(13);
        assertThat(test.getTeams()).hasSize(4);
        assertThat(test.getGeographicSpaces()).hasSize(10);
        assertThat(test.getHubs()).hasSize(2);
        assertThat(test.getOrganisations()).hasSize(1);
    }

    /**
     * The message and task fixtures reach back far enough for the backlog sparklines to draw.
     *
     * <p>Both spanned one week in August until 2026-08-24, so five of the six monthly points were
     * zero — honest, and a chart nobody can read, which is the same problem the roster fixture had
     * before it grew from one week to fifteen. <b>The fix is the fixture, never the query</b>: the
     * two series count what was outstanding at each month end, and making the line look better by
     * counting something else is the fabricated-figure failure the in-browser mock was deleted for.
     *
     * <p>Six distinct months is the floor because {@code VOLUME_MONTHS} is six — a fixture that
     * covers five leaves the oldest point at zero and the line starting from nowhere.
     */
    @Test
    void shouldSeedEnoughCorrespondenceToDrawABacklogSeries() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getMessages().stream().map(message -> YearMonth.from(message.getSentAt().atZone(ZoneOffset.UTC))).distinct())
            .hasSizeGreaterThanOrEqualTo(6);
        assertThat(test.getTasks().stream().map(task -> YearMonth.from(task.getCreatedAt().atZone(ZoneOffset.UTC))).distinct())
            .hasSizeGreaterThanOrEqualTo(6);
    }

    /**
     * <b>The historical rows carry their own read and close times.</b>
     *
     * <p>Without them the backlog is undrawable, and not obviously so: {@code readAt} and
     * {@code closedAt} are stamped server-side, and {@code MessageLifecycleCallback} only stamps
     * when the field arrives empty. A historical message seeded without one would be stamped
     * <em>now</em> — so every archived message would read as having sat unread for months and left
     * the backlog today, and the line would be a ramp that never happened.
     */
    @Test
    void shouldSeedHistoricalCorrespondenceWithItsOwnReadAndCloseTimes() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getMessages().stream().filter(message -> message.getStatus() != MessageStatus.NEW))
            .allSatisfy(message -> assertThat(message.getReadAt()).as("read time for %s", message.getId()).isNotNull());
        assertThat(test.getTasks().stream().filter(task -> task.getState() == TaskState.DONE))
            .allSatisfy(task -> assertThat(task.getClosedAt()).as("close time for %s", task.getId()).isNotNull());
    }

    /**
     * <b>Some seeded professionals have to be reachable by a real login, or the self-service
     * earnings endpoint cannot be exercised at all.</b>
     *
     * <p>{@code /api/professionals/me/earnings} resolves its caller as login → {@code
     * Profile.account_id} → the professional holding that profile. Every seeded {@code account_id}
     * used to be a placeholder — {@code cred-p1} and friends, matching no login on any gateway — so
     * the endpoint answered 404 for every account in existence, and nothing distinguished that from
     * the resolver being broken. It <em>was</em> broken, reading a back-reference nothing populates,
     * and this dataset could not have told anyone.
     *
     * <p>The first four are hc-professional's seeded clinical logins, each matched to a professional
     * of the corresponding role. They belong to that stack's gateway rather than this one's, which
     * is the point: {@code account_id} holds a platform login, and the three stacks share one
     * identity space through a common signing key.
     *
     * <p><b>The remaining five were placeholders until 2026-08-22 and now name their holders</b> —
     * first initial then surname, so Nii Osae is {@code nosae}. hc-professional's quality fixture
     * ({@code quality/seed-data.json} there) creates gateway accounts under exactly these logins,
     * which is what makes p5–p9's shifts and earnings reachable by the people they belong to.
     * <b>The two ends have to move together</b>: this initializer calls {@code saveAll} on every
     * start, so a login changed only in a database is rewritten from this file at the next restart,
     * and a login changed only there resolves to nobody. Either way the symptom is a 404 that reads
     * as "no professional record" rather than as a broken link.
     *
     * <p>The placeholders existed to keep "this account has no professional record" producible, and
     * it still is: {@code profile-me} — {@code admin}, a login on all three gateways — is a profile
     * with no professional attached, and the twelve {@code cred-aN} office profiles have none
     * either. The last assertion holds that line explicitly, so linking the last unlinked profile
     * cannot happen unnoticed.
     */
    @Test
    void shouldLinkSomeProfessionalsToRealClinicalLogins() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        // Joined through personProfiles by id, not read off professional.getProfile(). In the seed
        // file the nested profile on a professional is a partial object — id and name only — and
        // carries no accountId at all; the full document lives in personProfiles, and at runtime the
        // DBRef resolves to that. Reading the nested copy yields null and says nothing.
        Map<String, String> accountIdByProfileId = test
            .getPersonProfiles()
            .stream()
            .filter(profile -> profile.getAccountId() != null)
            .collect(Collectors.toMap(Profile::getId, Profile::getAccountId));

        Map<String, ProfessionalRole> loginToRole = test
            .getProfessionals()
            .stream()
            .filter(professional -> professional.getProfile() != null)
            .filter(professional -> accountIdByProfileId.containsKey(professional.getProfile().getId()))
            .filter(professional -> !accountIdByProfileId.get(professional.getProfile().getId()).startsWith("cred-"))
            .collect(Collectors.toMap(professional -> accountIdByProfileId.get(professional.getProfile().getId()), Professional::getRole));

        // Role-matched, not merely linked: a `nurse` login pointing at a DOCTOR would be valued at
        // the doctor's rate, and the mistake would look exactly like a working feature.
        assertThat(loginToRole)
            .containsEntry("doctor", ProfessionalRole.DOCTOR)
            .containsEntry("nurse", ProfessionalRole.NURSE)
            .containsEntry("paramedic", ProfessionalRole.PARAMEDIC)
            .containsEntry("carer", ProfessionalRole.CAREGIVER)
            .containsEntry("nosae", ProfessionalRole.DOCTOR)
            .containsEntry("asarpong", ProfessionalRole.NURSE)
            .containsEntry("kntim", ProfessionalRole.CAREGIVER)
            .containsEntry("afrimpong", ProfessionalRole.PARAMEDIC)
            .containsEntry("makoto", ProfessionalRole.NURSE);

        // Every professional who has a profile is reachable by a login somebody can actually sign in
        // with. `p10` is the one that has none, so nothing can resolve a login to it and nothing
        // should — see shouldCarryAProfessionalWithNobodyOnIt. It is named here rather than
        // subtracted, because `hasSize(getProfessionals().size() - 1)` goes on passing if a
        // *different* professional quietly loses its link and p10 quietly gains one.
        List<Professional> withAProfile = test.getProfessionals().stream().filter(p -> p.getProfile() != null).toList();
        assertThat(withAProfile).extracting(Professional::getId).doesNotContain("p10");
        assertThat(loginToRole).hasSameSizeAs(withAProfile);

        // And the state the placeholders used to hold open is still reachable: profiles that belong
        // to no professional at all. `admin` is the one that matters, because it is a login on all
        // three gateways — signing in as it and asking for /professionals/me/earnings is how "this
        // account has no professional record" gets produced now.
        java.util.Set<String> linkedProfileIds = test
            .getProfessionals()
            .stream()
            .filter(professional -> professional.getProfile() != null)
            .map(professional -> professional.getProfile().getId())
            .collect(Collectors.toSet());
        assertThat(test.getPersonProfiles())
            .filteredOn(profile -> profile.getAccountId() != null && !linkedProfileIds.contains(profile.getId()))
            .extracting(Profile::getAccountId)
            .contains("admin");
    }

    /**
     * <b>The signed-in administrator has to have a profile, reachable by the login they sign in
     * with.</b>
     *
     * <p>{@code profile-me} — Efua Mensah, the persona the demo greets by name — is in this fixture
     * for exactly one screen: {@code /account}, which asks {@code GET
     * /api/profiles/by-account/{login}} for the caller's own record. It was linked to
     * {@code cred-me}, an id carried over from the in-browser mock's Credential collection and
     * matching no login on any gateway, so the screen 404ed and offered to create a profile that was
     * already sitting three collections away.
     *
     * <p>The second assertion is the one that matters and it is deliberately about shape rather than
     * about a value. {@code account_id} holds a <b>login</b>; a UUID there is the gateway user id,
     * which is the specific wrong identifier the console sent for eight days. Both are opaque
     * strings, the lookup is an equality match, and the answer to either is the same 404 — so no
     * assertion about a working link can catch the wrong kind of link, only an assertion about the
     * kind.
     */
    @Test
    void shouldLinkTheAdministratorProfileToTheLoginTheySignInWith() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getPersonProfiles())
            .filteredOn(profile -> "admin".equals(profile.getAccountId()))
            .singleElement()
            .satisfies(profile -> assertThat(profile.getFirstName()).isEqualTo("Efua"));

        assertThat(test.getPersonProfiles())
            .extracting(Profile::getAccountId)
            .filteredOn(java.util.Objects::nonNull)
            .noneMatch(accountId -> accountId.matches("(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"));
    }

    /**
     * The link has to be stored on the PROFESSIONAL, which is the direction the resolver reads.
     *
     * <p>{@code Profile} declares a {@code professional} back-reference and this seed leaves it
     * null, exactly as production does. A dataset that populated both sides would let a resolver
     * reading either one pass — which is how a version that read the unpopulated side shipped and
     * answered 404 for every caller.
     *
     * <p><b>{@code p10} is excluded by name and the exclusion is the point of another test.</b> This
     * assertion read {@code allSatisfy} over every professional until 2026-09-09, and while its
     * subject is the <em>direction</em> of the link it also asserted, in passing, that a professional
     * always has one. That is not true of the api — {@code Professional.profile} carries no
     * {@code @NotNull} — and it was the pin that made the missing fixture state look impossible to
     * add (backlog item 53's refusal cites this line). Excluding it by id keeps the direction rule
     * exact for the nine records it is about, and leaves the tenth to
     * {@link #shouldCarryAProfessionalWithNobodyOnIt()}.
     */
    @Test
    void shouldStoreTheAccountLinkOnTheProfessionalAndNotOnTheProfile() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getProfessionals())
            .filteredOn(professional -> !"p10".equals(professional.getId()))
            .allSatisfy(professional -> assertThat(professional.getProfile()).isNotNull());
        assertThat(test.getPersonProfiles()).allSatisfy(profile -> assertThat(profile.getProfessional()).isNull());
    }

    /**
     * <b>One professional has no {@code Profile} at all, and it is the fixture rather than an
     * omission.</b>
     *
     * <p>{@code p10} exists to make three renderings reachable that were reachable on no stack short
     * of production. All three were written for backlog item 45's defect — a record named by its own
     * ObjectId — and item 49 fixed the last of them on a screen nobody could drive:
     *
     * <ul>
     *   <li>{@code professional-detail.ts}'s {@code headingName()}, whose second branch shows the
     *       licence number and whose third says "Identity not on file". Nine seeded professionals all
     *       carried a profile, so only the first branch had ever rendered.
     *   <li>{@code professional/list/professional.ts}'s {@code displayName()}, the same fallback one
     *       screen along.
     *   <li>{@code initials()} on both, which returns an em dash rather than two hex characters of
     *       an id.
     * </ul>
     *
     * <p><b>The state is ordinary, and the record is what the api writes when only its required
     * fields are supplied.</b> {@code role}, {@code licenceNumber}, {@code verification},
     * {@code status} and {@code joinedOn} are {@code @NotNull} on {@code Professional} and nothing
     * else is, so a {@code POST} or {@code PUT} carrying those five is accepted — and because
     * {@code ProfessionalResource} restores only {@code verification}, {@code homeSpaceId} and
     * {@code unavailabilityPeriods} from the stored copy, such a {@code PUT} nulls a profile that was
     * there. {@code ProfileResource.deleteProfile} reaches the same state from the other side: it
     * cascades nowhere, and a dangling {@code @DBRef} reads back as null.
     *
     * <p>Seeding it moves nothing that is pinned, and that was measured rather than hoped:
     *
     * <ul>
     *   <li><b>Status {@code PENDING}</b> keeps {@code duty-roster.cy.ts}'s {@code staff: 7} exact —
     *       {@code buildRows()} drops PENDING, so the figure is ten professionals less three rather
     *       than nine less two. The comment in that file says so; the number did not move.
     *   <li>The dashboard's approval card caps at {@code APPROVAL_ROWS = 5} and orders patients,
     *       then professionals, then vendors, so a sixth PENDING account leaves five rows with
     *       Beatrice Sarsah still first. <b>It also makes that assertion mean what it claims:</b>
     *       {@code dashboard.cy.ts} says it pins "five rows with more behind them", and until now
     *       there was nothing behind them.
     *   <li><b>No {@code team}, deliberately.</b> {@code RoundPlanningService} draws candidates from
     *       {@code findByTeamIn}, and its private {@code displayName(Professional)} returns
     *       {@code chosen.getId()} when the profile is null — the same defect one service along,
     *       which backlog item 53 recorded and did not fix. A team on this record would put an
     *       ObjectId on the planning screen. The assertion below is what stops one being added
     *       without that being noticed.
     * </ul>
     *
     * <p>It is named here rather than counted, for the reason the whole file is: "one professional
     * without a profile" goes on passing when the one is a different one, or when it quietly gains
     * a profile and some other record loses theirs.
     */
    @Test
    void shouldCarryAProfessionalWithNobodyOnIt() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getProfessionals())
            .filteredOn(professional -> professional.getProfile() == null)
            .singleElement()
            .satisfies(professional -> {
                assertThat(professional.getId()).isEqualTo("p10");
                // The licence number is the second branch of `headingName()`, so it has to be there
                // and has to be legible — a blank one would render the third branch instead and the
                // second would still never be seen.
                assertThat(professional.getLicenceNumber()).isNotBlank();
                // See the javadoc: a team makes it a planning candidate, and the planner names a
                // profile-less candidate by its ObjectId.
                assertThat(professional.getTeam()).isNull();
            });
    }

    /**
     * Every role a professional can hold has to be priced, or the console shows a wage bill with a
     * hole in it that reads exactly like a professional who earned nothing.
     */
    @Test
    void shouldPriceEveryProfessionalRoleUnderTest() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getWageRates().stream().map(WageRate::getRole).distinct()).containsExactlyInAnyOrder(ProfessionalRole.values());
    }

    /**
     * <b>At least one cell has to carry a superseded rate.</b> A seed with one row per cell reads
     * identically whether rates are effective-dated or simply editable in place — the distinction
     * that the whole model turns on is invisible until a rate has history behind it, and the
     * console's history view has nothing to show.
     *
     * <p>Grouped by {@code (role, shiftType)} since 2026-09-04. Grouping by role alone would now be
     * satisfied by the grid itself: DOCTOR has five rows because it is priced for five shift types,
     * and the check would pass with nothing superseded at all.
     */
    @Test
    void shouldSeedARateThatHasBeenSuperseded() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        Map<String, Long> perCell = test
            .getWageRates()
            .stream()
            .collect(Collectors.groupingBy(rate -> rate.getRole() + "/" + rate.getShiftType(), Collectors.counting()));

        assertThat(perCell).containsEntry("DOCTOR/NIGHT", 2L).containsEntry("NURSE/NIGHT", 2L);
        assertThat(test.getWageRates().stream().map(WageRate::getValidFrom).distinct()).hasSizeGreaterThan(1);
    }

    /**
     * <b>{@code dev} prices every {@code (role, shiftType)} cell.</b>
     *
     * <p>The lookup is exact — there is no fallback from an unpriced shift type to the role's other
     * rates — so an unfilled cell is a clinician whose earnings screen reports shifts worked and
     * nothing accrued. Against a fully-priced fixture that reads like a bug in the pay service
     * rather than a hole in the data, so one profile fills the grid and stays the baseline.
     *
     * <p>Includes {@code OFF}, which is never read: {@code ShiftValuationService} drops an off day
     * before resolving any rate. The go-live pricing ask is the whole five-by-five grid so that
     * whoever owns pricing is asked once, and the fixture is the shape of the thing being asked for.
     */
    @Test
    void shouldPriceEveryRoleAndShiftTypeCombinationUnderDev() throws Exception {
        DevelopmentDataInitializer.ProfileData dev = readSeedData().get("dev");

        assertThat(dev.getWageRates().stream().map(rate -> rate.getRole() + "/" + rate.getShiftType()).distinct())
            .as("dev prices every (role, shiftType) cell")
            .hasSize(ProfessionalRole.values().length * ShiftType.values().length);
    }

    /**
     * <b>{@code test} deliberately leaves two cells unpriced, and this asserts which two.</b>
     *
     * <p>This test asserted the full grid for both profiles until 2026-09-04, and that was the defect
     * rather than the guard: with all 25 cells priced the unpriced rendering — {@code wage-rates.html}'s
     * "Not set" branch, and every {@code unpricedShifts} count on the earnings screens — was
     * <b>unreachable on the quality stack</b>, so the one state the distinct rendering exists to
     * express could not be looked at. Worse, the case a reader did see was {@code OFF} at
     * {@code 0 GHS}, which is precisely the state "Not set" exists to be told apart from.
     *
     * <p>{@code CAREGIVER} is the role, and the pair is chosen rather than arbitrary.
     * {@code EVENING} has 45 assignments in the roster fixture, so the counts move and the screens
     * have something to report; {@code FLEXIBLE} has none anywhere, so it exercises the rendering
     * without touching a total. {@code dev} stays whole, which is what keeps
     * {@link #shouldPriceEveryRoleAndShiftTypeCombinationUnderDev} meaningful as the baseline.
     *
     * <p>Named explicitly rather than counted: "23 of 25 cells" would go on passing if a different
     * two went missing, and an accidental hole in the grid is exactly what the old assertion was
     * there to catch. Only the deliberateness has changed.
     */
    @Test
    void shouldLeaveTwoNamedCellsUnpricedUnderTest() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        List<String> priced = test.getWageRates().stream().map(rate -> rate.getRole() + "/" + rate.getShiftType()).distinct().toList();

        assertThat(priced).doesNotContain("CAREGIVER/EVENING", "CAREGIVER/FLEXIBLE");
        assertThat(priced).hasSize(ProfessionalRole.values().length * ShiftType.values().length - 2);
    }

    /**
     * The seeded catalogue is the one Abofonsa publishes, tier for tier and price for price.
     *
     * <p>Backlog item 51. Until 2026-09-08 this fixture held {@code Bridge Essential} /
     * {@code Bridge Plus} / {@code Bridge Family} at GHS 320 / 680 / 1,240 while
     * {@code web.abofonsa.com} and hc-patient both showed {@code PEAR} / {@code PAWPAW} /
     * {@code MELON} at 3,000 / 5,000 / 8,000 — so every stack anybody could drive taught a reader a
     * price list that exists nowhere but here, by a factor of roughly ten.
     *
     * <p><b>Named and priced rather than counted, and the price is the assertion that matters.</b>
     * {@code hasSize(3)} above already covers the count and would go on passing against any three
     * plans at any three prices, which is precisely the state this fixture was in for months. What
     * this cannot catch is Abofonsa changing a published price: {@code monthlyPrice} is this
     * service's own figure by design — see {@code ServicePlanCatalogueSyncService} — so these numbers
     * are a copy, and a copy is what item 51 is still open about. Check them against the live
     * catalogue rather than trusting them, and if they disagree the fixture is what is wrong.
     *
     * <p>{@code displayOrder} is asserted as ascending-with-price because that is what makes the
     * order meaningful: the summary sorts on it now rather than on a deleted enum's ordinal, and
     * three plans in an arbitrary order would satisfy any weaker check.
     *
     * <p><b>{@code MELON} is deliberately left unpriced, and it is the one tier this asserts a null
     * for.</b> Same argument as {@link #shouldLeaveTwoNamedCellsUnpricedUnderTest} one collection
     * along, and the same objection it answers: {@code monthlyPrice} became nullable on 2026-09-08
     * because a plan the catalogue sync learns arrives with no price, and with all three published
     * tiers priced here that state rendered on <b>no stack anybody can drive</b> — {@code quality/}
     * and {@code deploy/e2e/compose.yml} both run {@code dev,test}. Three template branches depend
     * on it: the board's "Not priced" card, the record's "Not set", and the mix table's em dash
     * against a plan that has subscribers, which is the row the {@code monthlyRevenue} null exists
     * for. MELON has three non-archived subscribers in this fixture, so the mix shows an unpriced
     * plan somebody is holding rather than an unpriced plan nobody has taken up — the two are not
     * the same screen, and only the first is the state that matters.
     *
     * <p>It does <b>not</b> restate a wrong price. Abofonsa publishes MELON at GHS 8,000 and this
     * fixture says nothing about what MELON costs; what it says is that this console has not
     * recorded a figure for it, which is a true and reachable state of a service whose copy of the
     * price is its own. Do not "complete" the catalogue here — the same instruction the wage rate
     * grid carries.
     */
    @Test
    void shouldSeedTheCatalogueAbofonsaPublishes() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getServicePlans())
            .extracting(
                ServicePlan::getCode,
                ServicePlan::getName,
                ServicePlan::getDisplayOrder,
                plan -> plan.getMonthlyPrice() == null ? null : plan.getMonthlyPrice().intValueExact(),
                ServicePlan::getCurrency
            )
            .containsExactlyInAnyOrder(
                tuple("PEAR", "PEAR Plan", 1, 3000, "GHS"),
                tuple("PAWPAW", "PAWPAW Plan", 2, 5000, "GHS"),
                tuple("MELON", "MELON Plan", 3, null, "GHS")
            );
        // PAWPAW is the featured tier on the public site and is the only one here. Asserted because
        // `featured` is seeded from the publisher once, at creation, and is this console's field
        // thereafter — so a fixture that featured the wrong tier would be a plausible screen.
        assertThat(test.getServicePlans().stream().filter(ServicePlan::getFeatured).map(ServicePlan::getCode)).containsExactly("PAWPAW");
    }

    /**
     * A card's bullets are the ones Abofonsa publishes for that tier.
     *
     * <p>The other half of item 51's review, and the way this fixture went wrong is worth keeping:
     * {@code servicePlans} was rewritten to the published names and prices and {@code planFeatures}
     * was left alone, so PEAR Plan at GHS 3,000 advertised <em>"1 home visit per month"</em> and
     * MELON <em>"Weekly home visits"</em> — the retired Bridge feature lists, under the new tiers'
     * names, on every stack anybody can drive. That is exactly the class of wrong fact item 51 was
     * opened about, one field along from the prices, and nothing failed while it was true.
     *
     * <p>Only the tiers' {@code included: true} features are seeded. The published payload carries
     * the excluded ones as well, to draw a comparison table the public site has and this console
     * does not: the plan board renders every bullet with a tick, so seeding a
     * {@code "24/7 on-call availability", included: false} would put a tick beside something the
     * plan does not include. The counts follow from that and are not a coincidence to preserve — 5,
     * 6 and 7 are how many included features each tier publishes.
     *
     * <p>The first bullet of each tier is asserted by value because it is the one that differs most
     * from what it replaced and the one a reader sees first; the rest by count, because a published
     * feature list is edited by whoever writes the marketing page and pinning all eighteen here
     * would make this test fail on a copy edit rather than on a fault.
     */
    @Test
    void shouldSeedTheFeatureListsAbofonsaPublishes() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getPlanFeatures())
            .filteredOn(feature -> "pl1".equals(feature.getPlan().getId()))
            .extracting(PlanFeature::getLabel)
            .hasSize(5)
            .startsWith("5 weekly visits");
        assertThat(test.getPlanFeatures())
            .filteredOn(feature -> "pl2".equals(feature.getPlan().getId()))
            .extracting(PlanFeature::getLabel)
            .hasSize(6)
            .startsWith("7 weekly visits");
        assertThat(test.getPlanFeatures())
            .filteredOn(feature -> "pl3".equals(feature.getPlan().getId()))
            .extracting(PlanFeature::getLabel)
            .hasSize(7)
            .startsWith("24/7 availability");

        // The retired lists, named so that restoring one fails here rather than reading as a copy
        // edit somebody made on purpose.
        assertThat(test.getPlanFeatures())
            .extracting(PlanFeature::getLabel)
            .doesNotContain("1 home visit per month", "2 home visits per month", "Weekly home visits");
    }

    /**
     * Every professional carries a verification history, and its head agrees with the field.
     *
     * <p><b>The agreement is the invariant the whole design rests on.</b>
     * {@code Professional.verification} is a projection of the newest row, written only by
     * {@code ProfessionalVerificationService}. A fixture where the two disagree is a fixture that
     * contradicts the rule the service enforces on every write — and it would be read later as
     * evidence that the projection drifts, which is exactly the property being avoided.
     */
    @Test
    void shouldSeedAVerificationHistoryWhoseHeadMatchesTheStoredStatus() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        Map<String, ProfessionalVerification> heads = test
            .getProfessionalVerifications()
            .stream()
            .collect(
                Collectors.toMap(
                    verification -> verification.getProfessional().getId(),
                    verification -> verification,
                    (a, b) -> a.getRecordedAt().isAfter(b.getRecordedAt()) ? a : b
                )
            );

        assertThat(heads).hasSize(test.getProfessionals().size());
        for (Professional professional : test.getProfessionals()) {
            assertThat(heads.get(professional.getId()))
                .as("history head for %s", professional.getId())
                .isNotNull()
                .extracting(ProfessionalVerification::getStatus)
                .isEqualTo(professional.getVerification());
        }
    }

    /**
     * <b>At least one professional has to have been superseded.</b> Same reasoning as the wage
     * rates above: with one row each, the fixture reads identically whether verifications are a
     * history or a current-state field, and the distinction the model turns on stays invisible.
     *
     * <p>The two outcomes a date field could never represent are pinned by name. A
     * {@code verifiedOn} on {@code Professional} — the change this collection was built instead of —
     * can say when somebody was last verified and can never say that they were revoked in between.
     */
    @Test
    void shouldSeedAVerificationThatHasBeenSuperseded() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        Map<String, Long> perProfessional = test
            .getProfessionalVerifications()
            .stream()
            .collect(Collectors.groupingBy(verification -> verification.getProfessional().getId(), Collectors.counting()));

        assertThat(perProfessional).containsEntry("p3", 2L).containsEntry("p6", 3L).containsEntry("p9", 3L);
        assertThat(test.getProfessionalVerifications().stream().map(ProfessionalVerification::getStatus).distinct())
            .contains(VerificationStatus.REVOKED, VerificationStatus.EXPIRED, VerificationStatus.PENDING, VerificationStatus.VERIFIED);
    }

    /**
     * The seeded roster has to reach back far enough to draw. A single week produces one point on a
     * monthly chart, which is a chart nobody can read — and would have looked like a working
     * feature.
     */
    @Test
    void shouldSeedEnoughRosterToDrawAMonthlySeries() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getShiftAssignments().stream().map(shift -> YearMonth.from(shift.getShiftDate())).distinct())
            .hasSizeGreaterThanOrEqualTo(4);
        assertThat(test.getShiftAssignments().stream().filter(shift -> shift.getShift() != ShiftType.OFF)).hasSize(654);
    }

    /**
     * <b>The geography is a tree, not a flat list of names.</b>
     *
     * <p>Proximity — same space, then same parent, then same ancestor — is a walk up
     * {@code parentId}. A fixture of unrelated rows satisfies "spaces exist" and can only ever
     * exercise the first of those three steps, so the ranking would look implemented and would be
     * untestable past its cheapest case. This asserts the shape the walk needs: exactly one root,
     * every other space reaching it, and depth enough for "same parent" and "same ancestor" to be
     * different answers.
     *
     * <p>It also asserts what the guard forbids, from the data side. {@code
     * GeographicSpaceCycleGuard} refuses a cycle on write; this refuses one in the file, which is the
     * copy that gets re-saved on every start and would therefore be the thing reintroducing it.
     */
    @Test
    void shouldSeedGeographicSpacesAsATreeWithASingleRoot() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        Map<String, String> parentOf = test
            .getGeographicSpaces()
            .stream()
            .collect(HashMap::new, (map, space) -> map.put(space.getId(), space.getParentId()), HashMap::putAll);

        assertThat(parentOf).hasSameSizeAs(test.getGeographicSpaces());
        assertThat(parentOf.entrySet().stream().filter(entry -> entry.getValue() == null).map(Map.Entry::getKey))
            .as("exactly one root")
            .containsExactly("gs-ghana");
        assertThat(parentOf.values())
            .filteredOn(java.util.Objects::nonNull)
            .allSatisfy(parentId -> assertThat(parentOf).as("parent %s is itself a seeded space", parentId).containsKey(parentId));

        // Every space reaches the root, in a bounded number of steps. The bound is what makes this a
        // test rather than a hang: an unrooted cycle would otherwise spin here exactly as it would
        // in the ranking.
        for (String id : parentOf.keySet()) {
            String walker = id;
            int steps = 0;
            while (parentOf.get(walker) != null && steps <= parentOf.size()) {
                walker = parentOf.get(walker);
                steps++;
            }
            assertThat(walker).as("ancestry of %s terminates at the root", id).isEqualTo("gs-ghana");
        }

        // Four levels, so "same parent" and "same ancestor" can disagree.
        assertThat(parentOf).containsEntry("gs-osu", "gs-accra").containsEntry("gs-accra", "gs-greater-accra");
    }

    /**
     * <b>Every professional is based somewhere, and the somewheres make proximity testable.</b>
     *
     * <p>{@code Professional.homeSpaceId} is the other end of the comparison the ranking makes
     * against a shift's own space; without it there is nothing to measure "near" against. A fixture
     * that merely filled the field would satisfy "not null" and still leave the ranking with one
     * possible answer, so this asserts the four cases the ranking has to tell apart: two clinicians
     * in the same space, two sharing a parent, two sharing only a distant ancestor, and — through the
     * hub check below — no home that contradicts where the person actually works.
     *
     * <p><b>"Every professional" is now "every professional the planner can choose", and that is a
     * narrowing towards the truth rather than away from it.</b> {@code RoundPlanningService} draws
     * its candidates from {@code findByTeamIn}, so a professional with no team is never ranked and
     * has nothing to be near. This read {@code getProfessionals()} whole until 2026-09-09, which was
     * indistinguishable while the fixture happened to give all nine a team; {@code p10} has none, on
     * purpose, and is the record that makes the difference visible (see
     * {@link #shouldCarryAProfessionalWithNobodyOnIt()}).
     */
    @Test
    void shouldGiveEveryProfessionalAHomeSpaceThatMakesProximityMeaningful() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        List<Professional> candidates = test.getProfessionals().stream().filter(professional -> professional.getTeam() != null).toList();
        // Who the filter removes, by name rather than by count: a rule over nothing passes, and
        // "one fewer than all of them" goes on passing when the one is a different one.
        assertThat(test.getProfessionals())
            .filteredOn(professional -> professional.getTeam() == null)
            .extracting(Professional::getId)
            .containsExactly("p10");

        // Collected into a HashMap rather than through Collectors.toMap, which throws on a null
        // value — the assertion below has to be able to report a missing home space rather than be
        // pre-empted by an NPE inside the collector.
        Map<String, String> homeOf = candidates
            .stream()
            .collect(HashMap::new, (map, professional) -> map.put(professional.getId(), professional.getHomeSpaceId()), HashMap::putAll);
        Map<String, String> parentOf = test
            .getGeographicSpaces()
            .stream()
            .collect(HashMap::new, (map, space) -> map.put(space.getId(), space.getParentId()), HashMap::putAll);

        assertThat(homeOf).hasSameSizeAs(candidates).doesNotContainValue(null);
        assertThat(homeOf.values())
            .allSatisfy(spaceId -> assertThat(parentOf).as("home space %s is a seeded space", spaceId).containsKey(spaceId));

        // Same space, same parent, and same ancestor only — the three tiers, each reachable.
        assertThat(homeOf.get("p2")).as("same space as p1").isEqualTo(homeOf.get("p1"));
        assertThat(homeOf.get("p3")).as("a different space from p1").isNotEqualTo(homeOf.get("p1"));
        assertThat(parentOf.get(homeOf.get("p3"))).as("but the same parent as p1").isEqualTo(parentOf.get(homeOf.get("p1")));
        assertThat(parentOf.get(homeOf.get("p5"))).as("p5 shares no parent with p1").isNotEqualTo(parentOf.get(homeOf.get("p1")));

        // And a home space that agrees with the hub the person is attached to. The two are separate
        // fields and nothing joins them, so a fixture can put an Accra clinician in Kumasi and read
        // as complete — which would make every proximity result look like a bug in the ranking.
        Map<String, String> regionOf = Map.of("hub-1", "gs-greater-accra", "hub-2", "gs-ashanti");
        assertThat(candidates)
            .allSatisfy(professional ->
                assertThat(regionOf.get(professional.getHub().getId()))
                    .as("home region of %s", professional.getId())
                    .isEqualTo(parentOf.get(parentOf.get(professional.getHomeSpaceId())))
            );
    }

    /**
     * <b>Two patients have no {@code Profile}, and only one of them has a link that names them.</b>
     *
     * <p>This is the state a patient learned from a sibling domain event is permanently in — the
     * streams carry no name, date of birth, phone number or document number, so no {@code Profile}
     * can be created from one at all — and until 2026-09-07 <b>it existed in production and nowhere
     * else</b>. Every seeded patient had a profile, so on {@code quality/}, on
     * {@code deploy/e2e/compose.yml} and under {@code ng serve} with this profile, the console's
     * whole "name a patient from the link" path returned early on every page and none of it ran:
     * not the address as a name, not the mailbox initials, not "Identity not on file", not the
     * {@code localId.in} round trip. Running a quality stack proved nothing about the change,
     * which is what makes the fixture part of the fix rather than a convenience.
     *
     * <p><b>Three states are here because they render differently and none may be guessed at.</b>
     * {@code a13} is linked to an address no sibling stack holds, so the row shows that address off
     * the link. {@code a14} has no link at all — a real and permanent state, not a pending one — so
     * the row says its name is not on file and says only that. {@code a15} is linked to an address
     * hc-patient really can name, which is backlog item 50 and is the case
     * {@link #shouldSeedALearnedPatientWhosePatientAppAccountReallyExists} exists for.
     *
     * <p>Named rather than counted, for the reason {@link #shouldLeaveTwoNamedCellsUnpricedUnderTest}
     * gives: "three patients without a profile" would go on passing if a different three lost theirs,
     * and an accidentally profile-less patient is exactly what a fixture guard should catch.
     */
    @Test
    void shouldSeedAPatientWithNoProfileBothWithAndWithoutALinkToNameThem() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getPatients())
            .filteredOn(patient -> patient.getProfile() == null)
            .extracting(Patient::getId)
            .as("the three rows that exercise the learned-patient rendering")
            .containsExactlyInAnyOrder("a13", "a14", "a15");

        Map<String, DirectoryLink> linkByLocalId = test
            .getDirectoryLinks()
            .stream()
            .filter(link -> link.getLocalId() != null)
            .collect(Collectors.toMap(DirectoryLink::getLocalId, link -> link));

        assertThat(linkByLocalId.get("a13"))
            .as("a13 is the linked half: the row shows this address where a name would go")
            .isNotNull()
            .satisfies(link -> {
                assertThat(link.getEmail()).isEqualTo("naa.adjeley@mail.gh");
                assertThat(link.getSource()).isEqualTo(DirectorySource.HC_PATIENT);
                assertThat(link.getSubjectKind()).isEqualTo(DirectorySubjectKind.PATIENT);
            });
        assertThat(linkByLocalId).as("a14 is the unlinked half and must stay unlinked").doesNotContainKey("a14");
    }

    /**
     * <b>One nameless patient's link carries an address hc-patient's own fixture really seeds, and
     * that foreign-looking address is the fixture rather than a typo.</b>
     *
     * <p>Backlog item 50: hc-admin now asks hc-patient to name a patient it learned from an event
     * ({@code GET /api/profiles/email/{email}}, with the administrator's own token). The lookup has
     * three outcomes and <b>the one the whole change exists for — a name comes back — was renderable
     * on no stack at all</b> before this row. Every linked patient here was {@code @mail.gh};
     * hc-patient's quality fixture seeds {@code kojo@jac.net}, {@code ophelia@localhost} and
     * {@code adjoa@localhost} under {@code dev} and nothing under {@code test}. Zero overlap, so
     * every lookup on the quality stack 404ed and the screen showed exactly what it showed before
     * item 50 — which is item 52's finding for the fifth time, applied here instead of learned again.
     *
     * <p><b>{@code kojo@jac.net} of the three, and the choice is not arbitrary.</b> It is the anchor
     * of hc-patient's {@code dev} fixture: {@code patient-kojo} is referenced 135 times across their
     * seed file against 2 and 3 for the other two, so it is the row least likely to be renamed or
     * dropped, and it resolves to a first and last name ({@code Kojo Ampia-Addison}) rather than to a
     * record with half a name on it. The other two are {@code @localhost}, which is unroutable and
     * reads as a mistake in a directory of real contact addresses.
     *
     * <p><b>This is a coupling to another repository's fixture and it is the house pattern, not a new
     * one.</b> {@code Profile.accountId} already names nine logins that only hc-professional's
     * {@code quality/seed-data.json} creates — see
     * {@link #shouldLinkSomeProfessionalsToRealClinicalLogins}, whose rule applies here word for
     * word: <b>both ends have to move together.</b> If hc-patient renames or drops
     * {@code patient-kojo}, nothing fails anywhere — this row simply goes back to rendering an
     * address, which is the state it is meant to be told apart from.
     *
     * <p><b>{@code a13} is deliberately left pointing at an address nobody holds.</b> That keeps the
     * {@code NOT_FOUND} outcome renderable beside the resolved one, and it keeps {@code dl-a13}'s
     * plan choice — which several other cases in this file assert against — exactly as item 48 left
     * it.
     */
    @Test
    void shouldSeedALearnedPatientWhosePatientAppAccountReallyExists() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        Map<String, DirectoryLink> linkByLocalId = test
            .getDirectoryLinks()
            .stream()
            .filter(link -> link.getLocalId() != null)
            .collect(Collectors.toMap(DirectoryLink::getLocalId, link -> link));

        assertThat(linkByLocalId.get("a15"))
            .as("a15 is the row whose name hc-patient can actually supply")
            .isNotNull()
            .satisfies(link -> {
                assertThat(link.getEmail()).isEqualTo("kojo@jac.net");
                assertThat(link.getSource()).isEqualTo(DirectorySource.HC_PATIENT);
                assertThat(link.getSubjectKind()).isEqualTo(DirectorySubjectKind.PATIENT);
            });

        // And the other half of the pair, stated here rather than left implicit: without an address
        // no sibling holds, the resolved rendering and the not-found rendering cannot be told apart
        // on any stack, because everything on screen would resolve.
        assertThat(linkByLocalId.get("a13"))
            .as("a13 stays unresolvable, so NOT_FOUND is renderable beside RESOLVED")
            .isNotNull()
            .satisfies(link -> assertThat(link.getEmail()).isEqualTo("naa.adjeley@mail.gh"));
    }

    /**
     * <b>{@code a13} is {@code PENDING}, and the count of pending patients has not moved.</b>
     *
     * <p>A patient the consumer opens is {@code PENDING} until the stream says the account is
     * activated ({@code DirectoryProjectionService.createAndClaim}), so the dashboard's "Needs your
     * approval" card is where an administrator meets a nameless row first — it joins the profile's
     * name fields and has no link read behind it, so before this it rendered an empty title under an
     * empty avatar. Seeding a {@code PENDING} patient with no profile is what makes that reachable.
     *
     * <p><b>{@code a12} was flipped to {@code ACTIVE} in the same change and that is deliberate.</b>
     * {@code dashboard.cy.ts} asserts five approval rows against {@code APPROVAL_ROWS = 5}; a sixth
     * pending account would push the card into its overflow state and drop the vendor off it, which
     * is a change to a gate that cannot be run from here. Holding the total keeps that spec's
     * deliberate fixture coupling intact while the nameless row becomes reachable.
     */
    @Test
    void shouldKeepThePendingApprovalCountWhileMakingOneOfThemNameless() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getPatients())
            .filteredOn(patient -> patient.getStatus() == AccountStatus.PENDING)
            .extracting(Patient::getId)
            .containsExactlyInAnyOrder("a7", "a13");
    }

    /**
     * <b>Four plan choices, spanning every branch the console has to draw for one.</b>
     *
     * <p>Backlog item 48: hc-patient publishes {@code PlanChosen} when a patient picks a tier, and
     * the four fields it carries land on the link. This is the only state on {@code directory_link}
     * that arrives from an event and has a screen behind it, so a fixture that omitted it would leave
     * the whole "plan choices awaiting a decision" panel unreachable on {@code quality/}, on
     * {@code deploy/e2e/compose.yml} and under {@code ng serve} — which is <b>item 52's finding, for
     * the fourth time</b>: items 45, 47 and 49 each shipped a rendering whose state existed only in
     * production, and item 52 asks that the generalisation be applied rather than the lesson learned
     * again. It is applied here.
     *
     * <p>The four, and what each one exists to render:
     *
     * <ul>
     *   <li>{@code dl-plan-a6} — the ordinary case. A named patient who holds {@code PEAR} and has
     *       chosen {@code PAWPAW}: the code resolves against this catalogue, the tier has a price,
     *       and the row differs from the plan on the patient record beside it. Without a row where
     *       the two differ, a panel showing the chosen plan is indistinguishable from one showing the
     *       held plan.</li>
     *   <li>{@code dl-a13} — the same choice on a patient <b>with no profile</b>, so the panel has to
     *       name them from the link exactly as the directory does. It chooses {@code MELON}, which
     *       item 51 deliberately seeds with <b>no {@code monthlyPrice}</b>, so the "no price set"
     *       rendering is reachable inside this panel and not only on the catalogue screen.</li>
     *   <li>{@code dl-plan-a8} — <b>{@code SOURSOP}, which is in no catalogue here.</b> This is the
     *       tier Abofonsa has published and {@code ServicePlanCatalogueSyncService} has not brought
     *       across, it is the branch {@code DirectoryProjectionService.announcePlanChoice} warns
     *       about, and it is the failure item 51 predicted in as many words. Nothing invents a
     *       {@code ServicePlan} for it, so without this row that path is unreachable too.</li>
     *   <li>{@code dl-plan-a10} — reported {@code ACTIVE} rather than {@code PENDING}, so the
     *       {@code planStatus=PENDING} filter has something to <b>exclude</b>. A filter proven only
     *       against rows it admits is a filter proven against nothing.</li>
     *   <li>{@code dl-plan-a5} — <b>a membership naming no tier at all.</b> Added by the item 48
     *       review with the defect it belongs to: {@code Membership.plan} and {@code .name} carry no
     *       {@code @NotNull} on hc-patient and their administrative CRUD path can create a membership
     *       with neither, so a status with no tier under it is a real stored state. It is what makes
     *       the console's "No tier named" branch reachable, and without it that branch would have
     *       been production-only — which is item 52's finding for the fifth time, inside the change
     *       that cites item 52.</li>
     * </ul>
     *
     * <p><b>Every one of them carries {@code lastEventType: PlanChosen}</b>, which {@code dl-a13} did
     * not until the same review: it was seeded with a plan choice and a {@code lastEventType} of
     * {@code OnboardingStarted}, an ordering the consumer cannot produce. hc-patient writes a
     * {@code Membership} only for a patient whose {@code Profile} exists, and {@code OnboardingStarted}
     * is the event that creates it — so the plan choice cannot precede it. A fixture depicting a state
     * no code path reaches is worse than no fixture: it is the thing a reader calibrates against.
     *
     * <p>Named rather than counted, for the reason every fixture guard in this class gives: "four
     * plan choices" goes on passing when one quietly changes tier, and the tier is the whole point of
     * three of the four.
     *
     * <p><b>Every one of them names a patient that exists.</b> A plan choice is {@code UPDATE_ONLY} —
     * hc-patient writes a {@code Membership} only for a patient whose account events came first — so a
     * seeded plan choice on a link with no {@code localId} would be a state the consumer cannot
     * produce and the console would have to render anyway.
     */
    @Test
    void shouldSeedFivePlanChoicesSpanningEveryBranchThePanelDraws() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        // Keyed on the membership rather than on the tier, because one of the five deliberately has
        // no tier — filtering on planCode would drop the row this test was extended to cover.
        Map<String, DirectoryLink> chosen = test
            .getDirectoryLinks()
            .stream()
            .filter(link -> link.getPlanMembershipId() != null)
            .collect(Collectors.toMap(DirectoryLink::getId, link -> link));

        assertThat(chosen.keySet()).containsExactlyInAnyOrder("dl-plan-a6", "dl-a13", "dl-plan-a8", "dl-plan-a10", "dl-plan-a5");

        Set<String> catalogued = test.getServicePlans().stream().map(ServicePlan::getCode).collect(Collectors.toSet());

        assertThat(chosen.get("dl-plan-a6"))
            .as("the ordinary case: resolves, is priced, and differs from the plan the patient holds")
            .satisfies(link -> {
                assertThat(link.getLocalId()).isEqualTo("a6");
                assertThat(link.getPlanCode()).isEqualTo("PAWPAW");
                assertThat(link.getPlanStatus()).isEqualTo("PENDING");
                assertThat(catalogued).contains("PAWPAW");
            });

        assertThat(chosen.get("dl-a13"))
            .as("a patient with no profile, choosing the tier item 51 leaves unpriced")
            .satisfies(link -> {
                assertThat(link.getLocalId()).isEqualTo("a13");
                assertThat(link.getPlanCode()).isEqualTo("MELON");
                assertThat(link.getPlanStatus()).isEqualTo("PENDING");
            });
        assertThat(test.getServicePlans())
            .filteredOn(plan -> "MELON".equals(plan.getCode()))
            .singleElement()
            .satisfies(plan -> assertThat(plan.getMonthlyPrice()).as("MELON is the unpriced tier this row exists to render").isNull());

        assertThat(chosen.get("dl-plan-a8"))
            .as("the tier this catalogue has never synced — the branch announcePlanChoice warns on")
            .satisfies(link -> {
                assertThat(link.getLocalId()).isEqualTo("a8");
                assertThat(link.getPlanCode()).isEqualTo("SOURSOP");
                assertThat(catalogued)
                    .as("if this is ever synced, the unresolvable branch stops being reachable")
                    .doesNotContain("SOURSOP");
            });

        assertThat(chosen.get("dl-plan-a10"))
            .as("reported ACTIVE, so the PENDING filter has something to exclude")
            .satisfies(link -> assertThat(link.getPlanStatus()).isEqualTo("ACTIVE"));

        assertThat(chosen.get("dl-plan-a5"))
            .as("a membership naming no tier — the state their admin CRUD path produces")
            .satisfies(link -> {
                assertThat(link.getPlanMembershipId()).isNotBlank();
                assertThat(link.getPlanStatus()).isEqualTo("PENDING");
                assertThat(link.getPlanCode()).as("this row exists to make the No tier named branch reachable").isNull();
                assertThat(link.getPlanName()).isNull();
            });

        assertThat(chosen.values())
            .as("a plan choice is UPDATE_ONLY, so every one of them names a patient that exists")
            .allSatisfy(link -> {
                assertThat(link.getSource()).isEqualTo(DirectorySource.HC_PATIENT);
                assertThat(link.getLocalId()).isNotNull();
                assertThat(link.getPlanMembershipId()).isNotBlank();
                // The event that last touched the row is the one that put the choice on it. A row
                // carrying a plan choice under some other lastEventType depicts an ordering the
                // consumer cannot produce — dl-a13 did, until the item 48 review.
                assertThat(link.getLastEventType()).isEqualTo("PlanChosen");
                assertThat(link.getState()).isEqualTo("PlanChosen");
            });

        assertThat(chosen.values())
            .filteredOn(link -> link.getLocalId() != null)
            .extracting(DirectoryLink::getLocalId)
            .allSatisfy(localId ->
                assertThat(test.getPatients()).extracting(Patient::getId).as("the patient this choice is about").contains(localId)
            );
    }

    /**
     * The links that keep no local record, which is the shape {@code /reconcile} has to leave alone.
     *
     * <p>A care angel and an erased subject are both links with a null {@code localId} <em>by
     * design</em>, so "the record is missing" is their normal state — and both were live defects
     * before 2026-09-05, when an untyped creation path made every nomination an ACTIVE patient and
     * the erasure event was what started storing them. Seeding them is what lets a person press
     * Reconcile on a quality stack and see {@code skipped} be a number rather than a zero that
     * proves nothing.
     */
    @Test
    void shouldSeedTheLinkKindsThatDeliberatelyKeepNoLocalRecord() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getDirectoryLinks())
            .filteredOn(link -> link.getSubjectKind() == DirectorySubjectKind.CARE_ANGEL)
            .singleElement()
            .satisfies(link -> assertThat(link.getLocalId()).as("a nomination never opens a patient record").isNull());

        assertThat(test.getDirectoryLinks())
            .filteredOn(link -> link.getErasedAt() != null)
            .singleElement()
            .satisfies(link -> assertThat(link.getLocalId()).as("an erased subject is not rebuilt as a patient").isNull());

        // And the ones from the other stack, which reconcile() never walks at all: it reads
        // HC_PATIENT links only. Their externalKey is an accountId rather than an address, which is
        // why resolveLinkIdentity in the console refuses to fall back to that field. What they are
        // for is {@link #shouldSeedFiveClinicianLinksSpanningBothPhasesOfTheContract}.
        assertThat(test.getDirectoryLinks())
            .filteredOn(link -> link.getSource() == DirectorySource.HC_PROFESSIONAL)
            .isNotEmpty()
            .allSatisfy(link -> assertThat(link.getLocalId()).isNull());
    }

    /**
     * <b>Six clinicians this service knows about and holds no record for — the six states the
     * two-phase contract can put one in.</b>
     *
     * <p>A professional who registers on hc-professional reaches this service, is stored as a
     * {@code DirectoryLink} with {@code local_id: null}, and appears in no {@code Professional}
     * collection — both event types on that topic are {@code LINK_ONLY}, because {@code role} and
     * {@code licenceNumber} are {@code @NotNull} here and are on the wire in no event, in any
     * version. So the dashboard tile could not move and the directory could not list them, and on
     * production that read as a registration having been lost.
     *
     * <p><b>Five rows, because they render differently, and every one of them was added after a
     * branch turned out to be unreachable on every stack but production.</b> That has now happened
     * three times in this file — item 45's nameless patient, item 46's unidentified clinician, item
     * 46's review finding a freshly registered one — so the rows for item 47 were written with the
     * screen open rather than after it.
     *
     * <ul>
     *   <li>{@code dl-prof} — phase 1 only, <b>not activated</b>. Named by its login, incomplete and
     *       unverified because no {@code ProfileStatus} has arrived, which the row must render as
     *       <em>unknown</em> and not as "no".</li>
     *   <li>{@code dl-prof-anon} — neither an address nor a login, the real state of a subject whose
     *       only event was an {@code onboarding.state}. It is what makes the "identity not on file"
     *       branch reachable.</li>
     *   <li>{@code dl-prof-fresh} — phase 1 only, <b>activated</b>, and with no onboarding state at
     *       all: {@code registration.created} carries no {@code state} field, and since 2026-09-07
     *       the parser no longer falls back to the event type. The pair with {@code dl-prof} is what
     *       makes the two activation states distinguishable on screen, which is why backlog item 47
     *       added {@code activated} to the display list.</li>
     *   <li>{@code dl-prof-unreported} — <b>named, and with activation unreported</b>, which is the
     *       third of those three states and was the one missing. It is also the state this change
     *       actually produces: {@code activated} arrives only on {@code AccountCreated}, so a
     *       clinician whose consumed frames are an {@code onboarding.state} — or a
     *       {@code registration.created} published before hc-professional's item 47 work — has a name
     *       and no answer to the question the column asks. Every other seeded phase-1 row carried an
     *       explicit {@code activated}, so the one cell that pairs a real name with "Not reported"
     *       was unreachable on every stack. <b>Three findings in this file have now been exactly
     *       this shape</b> — items 45, 46 and now 47's own review — which is why it is stated as a
     *       rule rather than as a row: the fixture must hold the state the change <em>produces</em>,
     *       not only the state it is built for.</li>
     *   <li>{@code dl-prof-profile-only} — <b>phase 2 with no phase 1</b>. Not an error and not
     *       rare enough to leave untested: the two phases are on two topics with no ordering between
     *       them and both groups read from the earliest offset, so this is normal on every backfill.
     *       The row must render keyed on the accountId rather than be held back until a name
     *       arrives.</li>
     *   <li>{@code dl-prof-complete} — <b>both phases joined</b>, complete and verified, with a
     *       {@code lastModifiedBy} that is hc-professional's login for whoever last wrote the
     *       profile. The only row on which the phase-2 half of the table has anything in it, and
     *       still no {@code Professional}.</li>
     * </ul>
     *
     * <p>Named rather than counted, on {@link #shouldLeaveTwoNamedCellsUnpricedUnderTest}'s
     * reasoning: "six professional links" would go on passing if one of them quietly gained an
     * address or a profile status, which is precisely the state that stops exercising the screen.
     */
    @Test
    void shouldSeedSixClinicianLinksSpanningBothPhasesOfTheContract() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        Map<String, DirectoryLink> clinicians = test
            .getDirectoryLinks()
            .stream()
            .filter(link -> link.getSource() == DirectorySource.HC_PROFESSIONAL)
            .collect(Collectors.toMap(DirectoryLink::getId, link -> link));

        assertThat(clinicians).as("the rows the professional directory's two-phase table is built on").hasSize(6);

        assertThat(clinicians.get("dl-prof"))
            .as("the named half: the panel shows this address where a name would go")
            .isNotNull()
            .satisfies(link -> {
                assertThat(link.getSubjectKind()).isEqualTo(DirectorySubjectKind.PROFESSIONAL);
                assertThat(link.getEmail()).isEqualTo("k.quartey@abofonsa.care");
                assertThat(link.getState()).as("the far side's own word, which is what the panel prints").isEqualTo("DOCUMENTS_SUBMITTED");
                assertThat(link.getLocalId()).as("no local record, and no event can ever supply one").isNull();
            });

        assertThat(clinicians.get("dl-prof-anon"))
            .as("the unidentified half — an onboarding.state frame carries neither an address nor a login")
            .isNotNull()
            .satisfies(link -> {
                assertThat(link.getSubjectKind()).isEqualTo(DirectorySubjectKind.PROFESSIONAL);
                assertThat(link.getEmail()).isNull();
                assertThat(link.getLogin()).isNull();
                assertThat(link.getLocalId()).isNull();
            });

        assertThat(clinicians.get("dl-prof-fresh"))
            .as("just registered: named, and with nothing yet to say about onboarding")
            .isNotNull()
            .satisfies(link -> {
                assertThat(link.getSubjectKind()).isEqualTo(DirectorySubjectKind.PROFESSIONAL);
                assertThat(link.getEmail()).isEqualTo("a.owusu@abofonsa.care");
                assertThat(link.getState()).as("registration.created carries no state, and its type is not one").isNull();
                assertThat(link.getLastEventType())
                    .as("the type is still recorded — in the field that is for types")
                    .isEqualTo("registration.created");
                assertThat(link.getLocalId()).isNull();
                assertThat(link.getActivated()).as("phase 1 says this account can sign in, and the row shows it").isTrue();
                assertThat(link.getAccountCreatedDate())
                    .as("the ACCOUNT's own date, which is not firstSeenAt and must not be rendered as it")
                    .isEqualTo(Instant.parse("2026-09-07T06:12:00Z"));
                assertThat(link.getProfileEventAt()).as("and no phase 2 at all: verified and complete are UNKNOWN here").isNull();
                assertThat(link.getProfileComplete()).isNull();
                assertThat(link.getProfileVerified()).isNull();
            });

        assertThat(clinicians.get("dl-prof-unreported"))
            .as("named, and with nothing said about activation — the cell that pairs a real login with 'Not reported'")
            .isNotNull()
            .satisfies(link -> {
                assertThat(link.getLogin())
                    .as("so the row is named, and the unknown is about activation and nothing else")
                    .isEqualTo("mboateng");
                assertThat(link.getActivated())
                    .as("no AccountCreated has been consumed for this account, so nobody has said — and null is not false")
                    .isNull();
                assertThat(link.getAccountCreatedDate())
                    .as("nor has anything said when the account was made: hc-professional publishes that on no frame today")
                    .isNull();
                assertThat(link.getProfileEventAt()).as("and no phase 2 either").isNull();
                assertThat(link.getLocalId()).isNull();
            });

        assertThat(clinicians.get("dl-prof-profile-only"))
            .as("phase 2 with no phase 1 — a profile for an account nobody has told this service about")
            .isNotNull()
            .satisfies(link -> {
                assertThat(link.getLastEventAt())
                    .as("no registration has been seen: this is the state two topics with no ordering between them produce")
                    .isNull();
                assertThat(link.getLogin()).as("so there is nothing to name the row by, and nothing is invented").isNull();
                assertThat(link.getEmail()).isNull();
                assertThat(link.getActivated()).as("and activation is unknown rather than false").isNull();
                assertThat(link.getProfileId()).isEqualTo("prof-profile-9001");
                assertThat(link.getProfileComplete()).as("reported as incomplete, which is not the same as unreported").isFalse();
                assertThat(link.getProfileVerified()).isFalse();
                assertThat(link.getLocalId()).isNull();
            });

        assertThat(clinicians.get("dl-prof-complete"))
            .as("both phases, joined on accountId — complete, verified, and still no Professional record")
            .isNotNull()
            .satisfies(link -> {
                assertThat(link.getLogin()).as("phase 1 names the row; the email is on the wire and off the screen").isEqualTo("yasante");
                assertThat(link.getActivated()).isTrue();
                assertThat(link.getProfileComplete()).isTrue();
                assertThat(link.getProfileVerified()).isTrue();
                assertThat(link.getProfileLastModifiedBy())
                    .as("hc-professional's LOGIN for whoever last wrote the profile — their auditor fills it from the JWT subject")
                    .isEqualTo("yasante");
                assertThat(link.getProfileModifiedDate())
                    .as("the profile's own date, which is what the row shows once a ProfileStatus has landed")
                    .isEqualTo(Instant.parse("2026-09-02T14:47:00Z"));
                assertThat(link.getLocalId())
                    .as("a complete, verified profile is still not a role and a licence number, so no record is created")
                    .isNull();
            });
    }

    /**
     * <b>No seeded link may carry {@code phasesJoinedAt}, because that field is what keeps a guard
     * alive on the stacks the guard is for.</b>
     *
     * <p>{@code DirectoryProjectionService.warnIfTheTwoPhasesNeverJoin} exists for the one failure in
     * the two-phase contract that looks correct on both sides: hc-professional's two publishers keying
     * their phases on different identifiers, with both consumer groups at lag zero, nothing
     * dead-lettered, and this service filling with rows that can never be paired. It is suppressed by
     * evidence that a join is possible — and until 2026-09-08 that evidence was "some link holds both
     * watermarks", which {@code dl-prof-complete} satisfies by being seeded. So on {@code quality/},
     * on {@code deploy/e2e/} and under {@code ng serve} the guard could not fire however wrong the
     * keys were: the fixture added for this contract silenced the guard added for it, in the same
     * change.
     *
     * <p>The suppressor is now {@code phases_joined_at}, which only the two consumer write paths set.
     * That is only true while the fixture leaves it alone, and a seeded value would re-silence the
     * guard with nothing failing — so this asserts the absence rather than trusting the comment on the
     * field. Seed the joined <em>row</em>, never the joined <em>observation</em>.
     */
    @Test
    void shouldSeedNoLinkThatSilencesTheTwoPhaseJoinGuard() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getDirectoryLinks())
            .as("phases_joined_at records that THIS SERVICE saw two phases meet, which no fixture can have witnessed")
            .allSatisfy(link -> assertThat(link.getPhasesJoinedAt()).isNull());
    }

    /**
     * The natural key is unique, which is what the startup index enforces at runtime.
     *
     * <p>{@code DirectoryLinkIndexes} creates a unique index on {@code (source, external_key)}, so a
     * seed file with two rows sharing one would fail {@code saveAll} — and the initializer catches
     * per collection, so the failure would be one log line on a stack that otherwise starts
     * healthily.
     */
    @Test
    void shouldSeedDirectoryLinksWithADistinctNaturalKey() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getDirectoryLinks().stream().map(link -> link.getSource() + "/" + link.getExternalKey()).distinct())
            .hasSameSizeAs(test.getDirectoryLinks());
    }

    /**
     * The strict mapper is the whole point of this file: Spring's ObjectMapper ignores unknown
     * properties, so a field name that does not match the domain model binds to nothing and the
     * record seeds with a null where the console expects a value. Reading the console dataset
     * through a mapper that rejects unknown properties is what catches that.
     */
    @Test
    void shouldBindEveryConsoleFieldToTheDomainModel() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getPatients().getFirst().getStatus()).isNotNull();
        assertThat(test.getProfessionals().getFirst().getLicenceNumber()).isNotNull();
        assertThat(test.getPersonProfiles().getFirst().getFirstName()).isNotNull();
        assertThat(test.getVendors().getFirst().getName()).isNotNull();
    }
}
