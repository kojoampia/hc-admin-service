package net.jojoaddison.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Map;
import net.jojoaddison.domain.enumeration.DutyRole;
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

        // `profiles` is an empty array in both profiles; several others are empty under `test`.
        assertThat(test.getProfiles()).isNotNull().isEmpty();
        assertThat(test.getAddresses()).isNotNull().isEmpty();
        assertThat(test.getOrganisations()).isNotNull().isEmpty();

        // A ProfileData with no JSON at all must still expose empty lists, not nulls.
        DevelopmentDataInitializer.ProfileData empty = mapper.readValue("{}", DevelopmentDataInitializer.ProfileData.class);
        assertThat(empty.getAddresses()).isNotNull().isEmpty();
        assertThat(empty.getSystemCatalogs()).isNotNull().isEmpty();
    }

    @Test
    void shouldPopulateTestProfileCollectionsThatCarryRecords() throws Exception {
        DevelopmentDataInitializer.ProfileData test = readSeedData().get("test");

        assertThat(test.getFacilities()).hasSize(1);
        assertThat(test.getAudits()).hasSize(1);
        assertThat(test.getPricingPlans()).hasSize(1);
    }
}
