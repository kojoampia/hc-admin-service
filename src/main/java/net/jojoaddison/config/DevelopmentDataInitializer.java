package net.jojoaddison.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.jojoaddison.domain.Address;
import net.jojoaddison.domain.Angel;
import net.jojoaddison.domain.AuditEntry;
import net.jojoaddison.domain.AuditLog;
import net.jojoaddison.domain.CareActivity;
import net.jojoaddison.domain.Category;
import net.jojoaddison.domain.Contact;
import net.jojoaddison.domain.DutyRoster;
import net.jojoaddison.domain.Facility;
import net.jojoaddison.domain.HCProfile;
import net.jojoaddison.domain.Hub;
import net.jojoaddison.domain.Message;
import net.jojoaddison.domain.Organisation;
import net.jojoaddison.domain.Patient;
import net.jojoaddison.domain.Person;
import net.jojoaddison.domain.PlanFeature;
import net.jojoaddison.domain.PlatformService;
import net.jojoaddison.domain.PricingPlan;
import net.jojoaddison.domain.Professional;
import net.jojoaddison.domain.RosterWeek;
import net.jojoaddison.domain.ServiceActivity;
import net.jojoaddison.domain.ServicePlan;
import net.jojoaddison.domain.ShiftAssignment;
import net.jojoaddison.domain.SystemCatalog;
import net.jojoaddison.domain.Task;
import net.jojoaddison.domain.Team;
import net.jojoaddison.domain.UserOption;
import net.jojoaddison.domain.Vendor;
import net.jojoaddison.domain.WageRate;
import net.jojoaddison.repository.AddressRepository;
import net.jojoaddison.repository.AngelRepository;
import net.jojoaddison.repository.AuditEntryRepository;
import net.jojoaddison.repository.AuditLogRepository;
import net.jojoaddison.repository.CareActivityRepository;
import net.jojoaddison.repository.CategoryRepository;
import net.jojoaddison.repository.ContactRepository;
import net.jojoaddison.repository.DocumentRepository;
import net.jojoaddison.repository.DutyRosterRepository;
import net.jojoaddison.repository.FacilityRepository;
import net.jojoaddison.repository.HCProfileRepository;
import net.jojoaddison.repository.HubRepository;
import net.jojoaddison.repository.MessageRepository;
import net.jojoaddison.repository.OrganisationRepository;
import net.jojoaddison.repository.PatientRepository;
import net.jojoaddison.repository.PersonRepository;
import net.jojoaddison.repository.PlanFeatureRepository;
import net.jojoaddison.repository.PlatformServiceRepository;
import net.jojoaddison.repository.PricingPlanRepository;
import net.jojoaddison.repository.ProfessionalRepository;
import net.jojoaddison.repository.ProfileRepository;
import net.jojoaddison.repository.RosterWeekRepository;
import net.jojoaddison.repository.ServiceActivityRepository;
import net.jojoaddison.repository.ServicePlanRepository;
import net.jojoaddison.repository.ShiftAssignmentRepository;
import net.jojoaddison.repository.SystemCatalogRepository;
import net.jojoaddison.repository.TaskRepository;
import net.jojoaddison.repository.TeamRepository;
import net.jojoaddison.repository.UserOptionRepository;
import net.jojoaddison.repository.VendorRepository;
import net.jojoaddison.repository.WageRateRepository;
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
    private final ProfileRepository profileRecordRepository;
    private final HubRepository hubRepository;
    private final AngelRepository angelRepository;
    private final PatientRepository patientRepository;
    private final ProfessionalRepository professionalRepository;
    private final VendorRepository vendorRepository;
    private final MessageRepository messageRepository;
    private final TaskRepository taskRepository;
    private final RosterWeekRepository rosterWeekRepository;
    private final ShiftAssignmentRepository shiftAssignmentRepository;
    private final ServicePlanRepository servicePlanRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final CategoryRepository categoryRepository;
    private final ServiceActivityRepository serviceActivityRepository;
    private final CareActivityRepository careActivityRepository;
    private final DocumentRepository documentRepository;
    private final UserOptionRepository userOptionRepository;
    private final PlatformServiceRepository platformServiceRepository;
    private final AuditEntryRepository auditEntryRepository;
    private final WageRateRepository wageRateRepository;
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
        SystemCatalogRepository systemCatalogRepository,
        ProfileRepository profileRecordRepository,
        HubRepository hubRepository,
        AngelRepository angelRepository,
        PatientRepository patientRepository,
        ProfessionalRepository professionalRepository,
        VendorRepository vendorRepository,
        MessageRepository messageRepository,
        TaskRepository taskRepository,
        RosterWeekRepository rosterWeekRepository,
        ShiftAssignmentRepository shiftAssignmentRepository,
        ServicePlanRepository servicePlanRepository,
        PlanFeatureRepository planFeatureRepository,
        CategoryRepository categoryRepository,
        ServiceActivityRepository serviceActivityRepository,
        CareActivityRepository careActivityRepository,
        DocumentRepository documentRepository,
        UserOptionRepository userOptionRepository,
        PlatformServiceRepository platformServiceRepository,
        AuditEntryRepository auditEntryRepository,
        WageRateRepository wageRateRepository
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
        this.profileRecordRepository = profileRecordRepository;
        this.hubRepository = hubRepository;
        this.angelRepository = angelRepository;
        this.patientRepository = patientRepository;
        this.professionalRepository = professionalRepository;
        this.vendorRepository = vendorRepository;
        this.messageRepository = messageRepository;
        this.taskRepository = taskRepository;
        this.rosterWeekRepository = rosterWeekRepository;
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.servicePlanRepository = servicePlanRepository;
        this.planFeatureRepository = planFeatureRepository;
        this.categoryRepository = categoryRepository;
        this.serviceActivityRepository = serviceActivityRepository;
        this.careActivityRepository = careActivityRepository;
        this.documentRepository = documentRepository;
        this.userOptionRepository = userOptionRepository;
        this.platformServiceRepository = platformServiceRepository;
        this.auditEntryRepository = auditEntryRepository;
        this.wageRateRepository = wageRateRepository;
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
            save("personProfiles", profileRecordRepository, profileData.getPersonProfiles());
            save("hubs", hubRepository, profileData.getHubs());
            save("angels", angelRepository, profileData.getAngels());
            save("professionals", professionalRepository, profileData.getProfessionals());
            save("servicePlans", servicePlanRepository, profileData.getServicePlans());
            save("planFeatures", planFeatureRepository, profileData.getPlanFeatures());
            save("patients", patientRepository, profileData.getPatients());
            save("vendors", vendorRepository, profileData.getVendors());
            save("messages", messageRepository, profileData.getMessages());
            save("tasks", taskRepository, profileData.getTasks());
            save("rosterWeeks", rosterWeekRepository, profileData.getRosterWeeks());
            save("shiftAssignments", shiftAssignmentRepository, profileData.getShiftAssignments());
            save("categories", categoryRepository, profileData.getCategories());
            save("serviceActivities", serviceActivityRepository, profileData.getServiceActivities());
            save("careActivities", careActivityRepository, profileData.getCareActivities());
            save("documents", documentRepository, profileData.getDocuments());
            save("userOptions", userOptionRepository, profileData.getUserOptions());
            save("platformServices", platformServiceRepository, profileData.getPlatformServices());
            save("auditEntries", auditEntryRepository, profileData.getAuditEntries());
            save("wageRates", wageRateRepository, profileData.getWageRates());
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
        // Fully qualified: this file imports Spring's @Profile, which shadows the domain type.
        private List<net.jojoaddison.domain.Profile> personProfiles = new ArrayList<>();
        private List<Hub> hubs = new ArrayList<>();
        private List<Angel> angels = new ArrayList<>();
        private List<Patient> patients = new ArrayList<>();
        private List<Professional> professionals = new ArrayList<>();
        private List<Vendor> vendors = new ArrayList<>();
        private List<Message> messages = new ArrayList<>();
        private List<Task> tasks = new ArrayList<>();
        private List<RosterWeek> rosterWeeks = new ArrayList<>();
        private List<ShiftAssignment> shiftAssignments = new ArrayList<>();
        private List<ServicePlan> servicePlans = new ArrayList<>();
        private List<PlanFeature> planFeatures = new ArrayList<>();
        private List<Category> categories = new ArrayList<>();
        private List<ServiceActivity> serviceActivities = new ArrayList<>();
        private List<CareActivity> careActivities = new ArrayList<>();
        // Fully qualified for the same reason the entity itself is: Document is a name Spring
        // Data also uses, and this file is easier to read without deciding which one won.
        private List<net.jojoaddison.domain.Document> documents = new ArrayList<>();
        private List<UserOption> userOptions = new ArrayList<>();
        private List<PlatformService> platformServices = new ArrayList<>();
        private List<AuditEntry> auditEntries = new ArrayList<>();
        private List<WageRate> wageRates = new ArrayList<>();

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

        public List<net.jojoaddison.domain.Profile> getPersonProfiles() {
            return personProfiles;
        }

        public void setPersonProfiles(List<net.jojoaddison.domain.Profile> personProfiles) {
            this.personProfiles = nullSafe(personProfiles);
        }

        public List<Hub> getHubs() {
            return hubs;
        }

        public void setHubs(List<Hub> hubs) {
            this.hubs = nullSafe(hubs);
        }

        public List<Angel> getAngels() {
            return angels;
        }

        public void setAngels(List<Angel> angels) {
            this.angels = nullSafe(angels);
        }

        public List<Patient> getPatients() {
            return patients;
        }

        public void setPatients(List<Patient> patients) {
            this.patients = nullSafe(patients);
        }

        public List<Professional> getProfessionals() {
            return professionals;
        }

        public void setProfessionals(List<Professional> professionals) {
            this.professionals = nullSafe(professionals);
        }

        public List<Vendor> getVendors() {
            return vendors;
        }

        public void setVendors(List<Vendor> vendors) {
            this.vendors = nullSafe(vendors);
        }

        public List<Message> getMessages() {
            return messages;
        }

        public void setMessages(List<Message> messages) {
            this.messages = nullSafe(messages);
        }

        public List<Task> getTasks() {
            return tasks;
        }

        public void setTasks(List<Task> tasks) {
            this.tasks = nullSafe(tasks);
        }

        public List<RosterWeek> getRosterWeeks() {
            return rosterWeeks;
        }

        public void setRosterWeeks(List<RosterWeek> rosterWeeks) {
            this.rosterWeeks = nullSafe(rosterWeeks);
        }

        public List<ShiftAssignment> getShiftAssignments() {
            return shiftAssignments;
        }

        public void setShiftAssignments(List<ShiftAssignment> shiftAssignments) {
            this.shiftAssignments = nullSafe(shiftAssignments);
        }

        public List<ServicePlan> getServicePlans() {
            return servicePlans;
        }

        public void setServicePlans(List<ServicePlan> servicePlans) {
            this.servicePlans = nullSafe(servicePlans);
        }

        public List<PlanFeature> getPlanFeatures() {
            return planFeatures;
        }

        public void setPlanFeatures(List<PlanFeature> planFeatures) {
            this.planFeatures = nullSafe(planFeatures);
        }

        public List<Category> getCategories() {
            return categories;
        }

        public void setCategories(List<Category> categories) {
            this.categories = nullSafe(categories);
        }

        public List<ServiceActivity> getServiceActivities() {
            return serviceActivities;
        }

        public void setServiceActivities(List<ServiceActivity> serviceActivities) {
            this.serviceActivities = nullSafe(serviceActivities);
        }

        public List<CareActivity> getCareActivities() {
            return careActivities;
        }

        public void setCareActivities(List<CareActivity> careActivities) {
            this.careActivities = nullSafe(careActivities);
        }

        public List<net.jojoaddison.domain.Document> getDocuments() {
            return documents;
        }

        public void setDocuments(List<net.jojoaddison.domain.Document> documents) {
            this.documents = nullSafe(documents);
        }

        public List<UserOption> getUserOptions() {
            return userOptions;
        }

        public void setUserOptions(List<UserOption> userOptions) {
            this.userOptions = nullSafe(userOptions);
        }

        public List<PlatformService> getPlatformServices() {
            return platformServices;
        }

        public void setPlatformServices(List<PlatformService> platformServices) {
            this.platformServices = nullSafe(platformServices);
        }

        public List<AuditEntry> getAuditEntries() {
            return auditEntries;
        }

        public void setAuditEntries(List<AuditEntry> auditEntries) {
            this.auditEntries = nullSafe(auditEntries);
        }

        public List<WageRate> getWageRates() {
            return wageRates;
        }

        public void setWageRates(List<WageRate> wageRates) {
            this.wageRates = nullSafe(wageRates);
        }

        private static <T> List<T> nullSafe(List<T> value) {
            return value == null ? new ArrayList<>() : value;
        }
    }
}
