package net.jojoaddison.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.jojoaddison.domain.Address;
import net.jojoaddison.domain.AuditLog;
import net.jojoaddison.domain.Contact;
import net.jojoaddison.domain.DutyRoster;
import net.jojoaddison.domain.Facility;
import net.jojoaddison.domain.HCProfile;
import net.jojoaddison.domain.Organisation;
import net.jojoaddison.domain.Person;
import net.jojoaddison.domain.PricingPlan;
import net.jojoaddison.domain.SystemCatalog;
import net.jojoaddison.domain.Team;
import net.jojoaddison.repository.AddressRepository;
import net.jojoaddison.repository.AuditLogRepository;
import net.jojoaddison.repository.ContactRepository;
import net.jojoaddison.repository.DutyRosterRepository;
import net.jojoaddison.repository.FacilityRepository;
import net.jojoaddison.repository.HCProfileRepository;
import net.jojoaddison.repository.OrganisationRepository;
import net.jojoaddison.repository.PersonRepository;
import net.jojoaddison.repository.PricingPlanRepository;
import net.jojoaddison.repository.SystemCatalogRepository;
import net.jojoaddison.repository.TeamRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Component;
import tech.jhipster.config.JHipsterConstants;

/**
 * Loads development and test seed data from {@code data/hc-admin-ms-data.json}.
 *
 * <p>The JSON is keyed by profile at the root ({@code dev} / {@code test}), and each profile holds
 * plain arrays of domain objects per collection. Records carry explicit ids, so repeated startups
 * overwrite the same documents rather than accumulating duplicates.
 */
@Component
@Profile({ JHipsterConstants.SPRING_PROFILE_DEVELOPMENT, JHipsterConstants.SPRING_PROFILE_TEST })
public class DevelopmentDataInitializer implements ApplicationRunner {

    private static final String SEED_DATA_LOCATION = "data/hc-admin-ms-data.json";

    private final Logger log = LoggerFactory.getLogger(DevelopmentDataInitializer.class);
    private final ObjectMapper mapper;
    private final Environment environment;
    private final AddressRepository addressRepository;
    private final ContactRepository contactRepository;
    private final FacilityRepository facilityRepository;
    private final AuditLogRepository auditLogRepository;
    private final OrganisationRepository organisationRepository;
    private final PersonRepository personRepository;
    private final TeamRepository teamRepository;
    private final HCProfileRepository profileRepository;
    private final DutyRosterRepository dutyRosterRepository;
    private final PricingPlanRepository pricingPlanRepository;
    private final SystemCatalogRepository systemCatalogRepository;

    public DevelopmentDataInitializer(
        ObjectMapper mapper,
        Environment environment,
        AddressRepository addressRepository,
        ContactRepository contactRepository,
        FacilityRepository facilityRepository,
        AuditLogRepository auditLogRepository,
        OrganisationRepository organisationRepository,
        PersonRepository personRepository,
        TeamRepository teamRepository,
        HCProfileRepository profileRepository,
        DutyRosterRepository dutyRosterRepository,
        PricingPlanRepository pricingPlanRepository,
        SystemCatalogRepository systemCatalogRepository
    ) {
        this.mapper = mapper;
        this.environment = environment;
        this.addressRepository = addressRepository;
        this.contactRepository = contactRepository;
        this.facilityRepository = facilityRepository;
        this.auditLogRepository = auditLogRepository;
        this.organisationRepository = organisationRepository;
        this.personRepository = personRepository;
        this.teamRepository = teamRepository;
        this.profileRepository = profileRepository;
        this.dutyRosterRepository = dutyRosterRepository;
        this.pricingPlanRepository = pricingPlanRepository;
        this.systemCatalogRepository = systemCatalogRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        String profile = resolveProfile();
        log.info("Initializing {} data from {}", profile, SEED_DATA_LOCATION);

        ProfileData profileData;
        try (InputStream inputStream = new ClassPathResource(SEED_DATA_LOCATION).getInputStream()) {
            Map<String, ProfileData> seedData = mapper.readValue(inputStream, new TypeReference<Map<String, ProfileData>>() {});
            profileData = seedData.get(profile);
        } catch (Exception e) {
            // Seed data is a development convenience: log loudly but let the application start.
            log.error("Failed to read seed data from {} for profile {}", SEED_DATA_LOCATION, profile, e);
            return;
        }

        if (profileData == null) {
            log.warn("No seed data found for profile '{}' in {}", profile, SEED_DATA_LOCATION);
            return;
        }

        try {
            save("addresses", addressRepository, profileData.getAddresses());
            save("contacts", contactRepository, profileData.getContacts());
            save("facilities", facilityRepository, profileData.getFacilities());
            save("audits", auditLogRepository, profileData.getAudits());
            save("organisations", organisationRepository, profileData.getOrganisations());
            save("persons", personRepository, profileData.getPersons());
            save("teams", teamRepository, profileData.getTeams());
            save("profiles", profileRepository, profileData.getProfiles());
            save("dutyRosters", dutyRosterRepository, profileData.getDutyRosters());
            save("pricingPlans", pricingPlanRepository, profileData.getPricingPlans());
            save("systemCatalogs", systemCatalogRepository, profileData.getSystemCatalogs());
        } catch (RuntimeException e) {
            log.error("Failed to persist {} seed data", profile, e);
        }
    }

    /**
     * The bean is only created under the {@code dev} or {@code test} profile, so one of the two is
     * always active here. {@code test} wins when both are present.
     */
    private String resolveProfile() {
        return environment.acceptsProfiles(org.springframework.core.env.Profiles.of(JHipsterConstants.SPRING_PROFILE_TEST))
            ? JHipsterConstants.SPRING_PROFILE_TEST
            : JHipsterConstants.SPRING_PROFILE_DEVELOPMENT;
    }

    private <T> void save(String collection, MongoRepository<T, String> repository, List<T> records) {
        if (records.isEmpty()) {
            log.debug("No {} records to seed", collection);
            return;
        }
        repository.saveAll(records);
        log.info("Seeded {} {} record(s)", records.size(), collection);
    }

    /**
     * One profile's worth of seed data. Must stay {@code static} — Jackson cannot instantiate
     * non-static inner classes.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ProfileData {

        private List<Address> addresses = new ArrayList<>();
        private List<Contact> contacts = new ArrayList<>();
        private List<Facility> facilities = new ArrayList<>();
        private List<AuditLog> audits = new ArrayList<>();
        private List<Organisation> organisations = new ArrayList<>();
        private List<Person> persons = new ArrayList<>();
        private List<Team> teams = new ArrayList<>();
        private List<HCProfile> profiles = new ArrayList<>();
        private List<DutyRoster> dutyRosters = new ArrayList<>();
        private List<PricingPlan> pricingPlans = new ArrayList<>();
        private List<SystemCatalog> systemCatalogs = new ArrayList<>();

        public List<Address> getAddresses() {
            return addresses;
        }

        public void setAddresses(List<Address> addresses) {
            this.addresses = nullSafe(addresses);
        }

        public List<Contact> getContacts() {
            return contacts;
        }

        public void setContacts(List<Contact> contacts) {
            this.contacts = nullSafe(contacts);
        }

        public List<Facility> getFacilities() {
            return facilities;
        }

        public void setFacilities(List<Facility> facilities) {
            this.facilities = nullSafe(facilities);
        }

        public List<AuditLog> getAudits() {
            return audits;
        }

        public void setAudits(List<AuditLog> audits) {
            this.audits = nullSafe(audits);
        }

        public List<Organisation> getOrganisations() {
            return organisations;
        }

        public void setOrganisations(List<Organisation> organisations) {
            this.organisations = nullSafe(organisations);
        }

        public List<Person> getPersons() {
            return persons;
        }

        public void setPersons(List<Person> persons) {
            this.persons = nullSafe(persons);
        }

        public List<Team> getTeams() {
            return teams;
        }

        public void setTeams(List<Team> teams) {
            this.teams = nullSafe(teams);
        }

        public List<HCProfile> getProfiles() {
            return profiles;
        }

        public void setProfiles(List<HCProfile> profiles) {
            this.profiles = nullSafe(profiles);
        }

        public List<DutyRoster> getDutyRosters() {
            return dutyRosters;
        }

        public void setDutyRosters(List<DutyRoster> dutyRosters) {
            this.dutyRosters = nullSafe(dutyRosters);
        }

        public List<PricingPlan> getPricingPlans() {
            return pricingPlans;
        }

        public void setPricingPlans(List<PricingPlan> pricingPlans) {
            this.pricingPlans = nullSafe(pricingPlans);
        }

        public List<SystemCatalog> getSystemCatalogs() {
            return systemCatalogs;
        }

        public void setSystemCatalogs(List<SystemCatalog> systemCatalogs) {
            this.systemCatalogs = nullSafe(systemCatalogs);
        }

        private static <T> List<T> nullSafe(List<T> value) {
            return value == null ? new ArrayList<>() : value;
        }
    }
}
