package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.stream.Collectors;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.Profile;
import net.jojoaddison.domain.WageRate;
import net.jojoaddison.domain.enumeration.DutyRole;
import net.jojoaddison.domain.enumeration.ProfessionalRole;
import net.jojoaddison.domain.enumeration.ShiftStatus;
import net.jojoaddison.domain.enumeration.ShiftType;
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
        assertThat(dev.getDutyRosters()).hasSize(1);
        assertThat(dev.getPricingPlans()).hasSize(1);
        assertThat(dev.getSystemCatalogs()).hasSize(1);
    }

    @Test
    void shouldBindScalarsEnumsAndDatesOnDomainObjects() throws Exception {
        DevelopmentDataInitializer.ProfileData dev = readSeedData().get("dev");

        var roster = dev.getDutyRosters().get(0);
        assertThat(roster.getId()).isEqualTo("dr-001");
        assertThat(roster.getDate()).isEqualTo(LocalDate.of(2026, 6, 10));
        assertThat(roster.getDuty()).isEqualTo(DutyRole.DOCTOR);
        assertThat(roster.getShift()).isEqualTo(ShiftType.DAY);
        assertThat(roster.getStatus()).isEqualTo(ShiftStatus.ASSIGNED);
        assertThat(roster.getPatientId()).isEqualTo("pat-001");

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
        assertThat(test.getPatients()).hasSize(12);
        assertThat(test.getProfessionals()).hasSize(9);
        assertThat(test.getVendors()).hasSize(9);
        // The relation itself, not just the two collections. Both sides are DBRefs and only the
        // vendor's array is the owning one, so a seed that sets `vendor` on the facility and
        // nothing on the vendor writes two documents that agree in the file and disagree in Mongo:
        // the facility knows its vendor, the vendor lists no sites, and the record renders empty.
        assertThat(test.getVendors().stream().filter(vendor -> !vendor.getFacilities().isEmpty()).count()).isEqualTo(3);
        assertThat(test.getFacilities().stream().filter(facility -> facility.getVendor() != null).count()).isEqualTo(5);
        assertThat(test.getAngels()).hasSize(12);
        assertThat(test.getMessages()).hasSize(12);
        assertThat(test.getTasks()).hasSize(13);
        assertThat(test.getRosterWeeks()).hasSize(15);
        assertThat(test.getShiftAssignments()).hasSize(921);
        assertThat(test.getServicePlans()).hasSize(3);
        assertThat(test.getPlanFeatures()).hasSize(18);
        assertThat(test.getPlatformServices()).hasSize(13);
        assertThat(test.getAuditEntries()).hasSize(7);
        assertThat(test.getAddresses()).hasSize(13);
        assertThat(test.getTeams()).hasSize(4);
        assertThat(test.getHubs()).hasSize(2);
        assertThat(test.getOrganisations()).hasSize(1);
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
     * <p>The four below are hc-professional's seeded clinical logins, each matched to a professional
     * of the corresponding role. They belong to that stack's gateway rather than this one's, which
     * is the point: {@code account_id} holds a platform login, and the three stacks share one
     * identity space through a common signing key.
     *
     * <p>Five professionals are deliberately left on placeholder ids. "This account has no
     * professional record" is a real state — every applicant mid-onboarding is in it — and it has to
     * stay reachable in the seed rather than becoming a case nobody can produce.
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
            .containsEntry("carer", ProfessionalRole.CAREGIVER);

        // The unlinked ones, kept on purpose.
        assertThat(
            test
                .getProfessionals()
                .stream()
                .filter(professional -> professional.getProfile() != null)
                .map(professional -> accountIdByProfileId.get(professional.getProfile().getId()))
                .filter(accountId -> accountId != null && accountId.startsWith("cred-"))
                .count()
        )
            .isEqualTo(5);
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
     * <b>At least one role has to carry a superseded rate.</b> A seed with one row per role reads
     * identically whether rates are effective-dated or simply editable in place — the distinction
     * that the whole model turns on is invisible until a rate has history behind it, and the
     * console's history view has nothing to show.
     */
    @Test
    void shouldSeedARateThatHasBeenSuperseded() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        Map<ProfessionalRole, Long> perRole = test
            .getWageRates()
            .stream()
            .collect(Collectors.groupingBy(WageRate::getRole, Collectors.counting()));

        assertThat(perRole).containsEntry(ProfessionalRole.DOCTOR, 2L).containsEntry(ProfessionalRole.NURSE, 2L);
        assertThat(test.getWageRates().stream().map(WageRate::getValidFrom).distinct()).hasSizeGreaterThan(1);
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
