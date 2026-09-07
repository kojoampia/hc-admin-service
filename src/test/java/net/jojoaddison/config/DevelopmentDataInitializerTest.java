package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.jojoaddison.domain.DirectoryLink;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.ProfessionalVerification;
import net.jojoaddison.domain.Profile;
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
        assertThat(test.getPatients()).hasSize(14);
        assertThat(test.getProfessionals()).hasSize(9);
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

        // Every professional is now reachable by a login somebody can actually sign in with.
        assertThat(loginToRole).hasSameSizeAs(test.getProfessionals());

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
     */
    @Test
    void shouldStoreTheAccountLinkOnTheProfessionalAndNotOnTheProfile() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getProfessionals()).allSatisfy(professional -> assertThat(professional.getProfile()).isNotNull());
        assertThat(test.getPersonProfiles()).allSatisfy(profile -> assertThat(profile.getProfessional()).isNull());
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
     */
    @Test
    void shouldGiveEveryProfessionalAHomeSpaceThatMakesProximityMeaningful() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        // Collected into a HashMap rather than through Collectors.toMap, which throws on a null
        // value — the assertion below has to be able to report a missing home space rather than be
        // pre-empted by an NPE inside the collector.
        Map<String, String> homeOf = test
            .getProfessionals()
            .stream()
            .collect(HashMap::new, (map, professional) -> map.put(professional.getId(), professional.getHomeSpaceId()), HashMap::putAll);
        Map<String, String> parentOf = test
            .getGeographicSpaces()
            .stream()
            .collect(HashMap::new, (map, space) -> map.put(space.getId(), space.getParentId()), HashMap::putAll);

        assertThat(homeOf).hasSameSizeAs(test.getProfessionals()).doesNotContainValue(null);
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
        assertThat(test.getProfessionals())
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
     * <p><b>Both states are here because they render differently and neither may be guessed at.</b>
     * {@code a13} is linked, so the row shows the address off the link. {@code a14} has no link at
     * all — a real and permanent state, not a pending one — so the row says its name is not on file
     * and says only that.
     *
     * <p>Named rather than counted, for the reason {@link #shouldLeaveTwoNamedCellsUnpricedUnderTest}
     * gives: "two patients without a profile" would go on passing if a different two lost theirs,
     * and an accidentally profile-less patient is exactly what a fixture guard should catch.
     */
    @Test
    void shouldSeedAPatientWithNoProfileBothWithAndWithoutALinkToNameThem() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getPatients())
            .filteredOn(patient -> patient.getProfile() == null)
            .extracting(Patient::getId)
            .as("the two rows that exercise the learned-patient rendering")
            .containsExactlyInAnyOrder("a13", "a14");

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
        // for is {@link #shouldSeedTwoClinicianLinksThatNoEventCanEverGiveARecord}.
        assertThat(test.getDirectoryLinks())
            .filteredOn(link -> link.getSource() == DirectorySource.HC_PROFESSIONAL)
            .isNotEmpty()
            .allSatisfy(link -> assertThat(link.getLocalId()).isNull());
    }

    /**
     * <b>Two clinicians this service knows about and holds no record for, which is what backlog item
     * 46 was reported as.</b>
     *
     * <p>A professional who registers on hc-professional reaches this service, is stored as a
     * {@code DirectoryLink} with {@code local_id: null}, and appears in no {@code Professional}
     * collection — both event types on that topic are {@code LINK_ONLY}, because {@code role} and
     * {@code licenceNumber} are {@code @NotNull} here and are on the wire in no event, in any
     * version. So the dashboard tile could not move and the directory could not list them, and on
     * production that read as a registration having been lost.
     *
     * <p><b>Two rows, because they render differently and item 45 was fixed with only the first of
     * its two states reachable.</b> {@code dl-prof} carries an address, so the directory names the
     * row by it. {@code dl-prof-anon} carries neither an address nor a login — the real state of a
     * subject whose only event was an {@code onboarding.state}, which the parser's own javadoc says
     * carries no email at all — so the row has to say that its identity is not on file and say only
     * that. Without the second, the "unidentified" branch is unreachable on every stack again.
     *
     * <p>Named rather than counted, on {@link #shouldLeaveTwoNamedCellsUnpricedUnderTest}'s
     * reasoning: "two professional links" would go on passing if one of them quietly gained an
     * address, which is precisely the state that stops exercising the screen.
     */
    @Test
    void shouldSeedTwoClinicianLinksThatNoEventCanEverGiveARecord() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        Map<String, DirectoryLink> clinicians = test
            .getDirectoryLinks()
            .stream()
            .filter(link -> link.getSource() == DirectorySource.HC_PROFESSIONAL)
            .collect(Collectors.toMap(DirectoryLink::getId, link -> link));

        assertThat(clinicians).as("the two rows the professional directory's awaiting-a-record panel is built on").hasSize(2);

        assertThat(clinicians.get("dl-prof"))
            .as("the named half: the panel shows this address where a name would go")
            .isNotNull()
            .satisfies(link -> {
                assertThat(link.getSubjectKind()).isEqualTo(DirectorySubjectKind.PROFESSIONAL);
                assertThat(link.getEmail()).isEqualTo("k.quartey@abofonsa.care");
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
