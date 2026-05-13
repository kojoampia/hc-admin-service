### The Core Microservice (`hc-admin-ms`)

Act as a Senior Backend Java/Spring Boot Developer specializing in Domain-Driven Design (DDD) and JHipster microservices.

Your task is to generate the complete JHipster JDL and essential business logic components for the Health-Connect Admin Microservice (`hc-admin-ms`). This service is the source of truth for administrative operations, duty rosters, system catalogs (CMS), and pricing plans.

### 1. Architecture & Configuration Requirements

- **Framework:** Spring Boot 3.x.
- **Port:** `5507`
- **Service Discovery:** Consul.
- **Security:** OAuth2 (Resource Server securing endpoints based on JWT claims from the Gateway).
- **Persistence:** MongoDB (Document DB ideal for flexible schemas like features, catalogs, and metadata).
- **Messaging:** Apache Kafka. This service acts as both a Producer (broadcasting roster changes) and a Consumer (syncing Profile data from `hc-patient-ms` and `hc-professional-service`).

### 2. JHipster JDL Specification

Generate the comprehensive JDL for the `hc-admin-ms` domain model.

**Application Config:**

```jdl
application {
  config {
    applicationType microservice
    baseName hcAdminMs
    packageName net.jojoaddison
    serviceDiscoveryType consul
    authenticationType oauth2
    databaseType mongodb
    serverPort 5507
    messageBroker kafka
  }
}
```

// Roster changes are broadcasted to the 'roster' topic

### 3. Domain Model

Define the entities, their relationships, and constraints.

```jdl
entity Feature {
  name String required,
  description String,
  type String required
}

entity Message {
  content String required,
  timestamp Instant required,
  senderId String required,
  recipientId String required,
  messageType String required
}

entity DutyRoster {
  date LocalDate required,
  shift String required, // Shift: MORNING, AFTERNOON, NIGHT
  professionalId String required,
  duty String required // DutyRole: CARE, VENDOR, DOCTOR, NURSE, MEDIC, TECHNICIAN, ADMINISTRATOR, OTHER
}

entity SystemCatalog {
  name String required,
  description String,
  type String required //CatalogType: SERVICE, PRODUCT, INFORMATION, ABOUT
}
entity PricingPlan {
  name String required,
  price BigDecimal required,
  features String,
  billingCycle String required
  active boolean required
}
entity PatientPlan {
  patientId String required,
  planId String required,
  startDate LocalDate required,
  endDate LocalDate
}
entity Organization {
  name String required,
  description String,
  teams String,
  address String,
  contactInfo String
}

relationship ManyToOne {
  Profile{personalInformation} to PersonalInformation
  Profile{address} to Address
  Profile{organization} to Organization
  Profile{team} to Team
}
relationship ManyToOne {
  Team{supervisor} to Profile
  Team{organization} to Organization
}
relationship ManyToOne {
  Subscription{patient} to Patient
  Subscription{plan} to PricingPlan
}
relationship ManyToOne {
  DutyRoster{professional} to ProfessionalService
}
relationship ManyToOne {
  SystemCatalog{admin} to Admin
}
relationship ManyToOne {
  PricingPlan{admin} to Admin
}

enum Role {
  ADMIN, PROFESSIONAL, PATIENT, VENDOR
}
enum Gender {
  MALE, FEMALE, OTHER
}
enum MaritalStatus {
  SINGLE, MARRIED, DIVORCED, WIDOWED
}
enum Status {
  ACTIVE, INACTIVE
}
enum BillingCycle {
  MONTHLY, QUARTERLY, ANNUALLY
}
enum Shift {
  MORNING, AFTERNOON, NIGHT
}
enum CatalogType {
  SERVICE, PRODUCT, INFORMATION, ABOUT, LEGAL, CONTACT, POLICY, OTHER
}
enum FeatureType {
  CORE, ADDON, PREMIUM
}
enum MessageType {
  NOTIFICATION, ALERT, REMINDER
}
enum DocumentType {
  ID, LICENSE, CERTIFICATE, OTHER
}
enum OrganizationType {
  HOSPITAL, CLINIC, LABORATORY, PHARMACY, OTHER
}
enum TeamRole {
  DOCTOR, NURSE, TECHNICIAN, ADMINISTRATOR, OTHER
}
enum ProfessionalRole {
  DOCTOR, NURSE, TECHNICIAN, ADMINISTRATOR, OTHER
}
enum PatientRole {
  PATIENT
}
enum VendorRole {
  VENDOR
}
enum AdminRole {
  ADMIN
}
enum ProfileStatus {
  ACTIVE, INACTIVE
}
enum DutyRole {
  CARE, VENDOR, DOCTOR, NURSE, MEDIC, TECHNICIAN, ADMINISTRATOR, OTHER
}
enum CatalogType {
  SERVICE, PRODUCT, INFORMATION, ABOUT
}
```

### 4. Business Logic

Implement the core business logic for managing duty rosters, system catalogs, and pricing plans. This includes:

- **Duty Roster Management:** Create, update, and delete duty rosters. Broadcast changes to Kafka.
- **System Catalog Management:** CRUD operations for system catalogs.
- **Pricing Plan Management:** CRUD operations for pricing plans and managing subscriptions.
- **Profile Synchronization:** Consume Kafka messages to sync profile data from other services.

```java
@Service
public class DutyRosterService {

  @Autowired
  private DutyRosterRepository dutyRosterRepository;

  @Autowired
  private KafkaTemplate<String, DutyRoster> kafkaTemplate;

  public DutyRoster createDutyRoster(DutyRoster dutyRoster) {
    DutyRoster savedRoster = dutyRosterRepository.save(dutyRoster);
    kafkaTemplate.send("roster", savedRoster);
    return savedRoster;
  }
  // Additional methods for update and delete
}

```

```java
@Service
public class SystemCatalogService {

  @Autowired
  private SystemCatalogRepository systemCatalogRepository;

  public SystemCatalog createSystemCatalog(SystemCatalog systemCatalog) {
    return systemCatalogRepository.save(systemCatalog);
  }
  // Additional methods for update and delete
}

```

```java
@Service
public class PricingPlanService {

  @Autowired
  private PricingPlanRepository pricingPlanRepository;

  @Autowired
  private SubscriptionRepository subscriptionRepository;

  @Autowired
  private KafkaTemplate<String, PricingPlan> kafkaTemplate;

  public PricingPlan createPricingPlan(PricingPlan pricingPlan) {
    PricingPlan savedPlan = pricingPlanRepository.save(pricingPlan);
    kafkaTemplate.send("pricing", savedPlan);
    return savedPlan;
  }

  // Additional methods for update and delete
  public Subscription subscribeToPlan(String patientId, String planId) {
    Subscription subscription = new Subscription();
    subscription.setPatientId(patientId);
    subscription.setPlanId(planId);
    subscription.setStartDate(LocalDate.now());
    subscription.setEndDate(LocalDate.now().plusMonths(1)); // Example duration
    return subscriptionRepository.save(subscription);
  }
}

```

```java
@Service
public class ProfileService {
    @Autowired
    private ProfileRepository profileRepository
    @KafkaListener(topics = "profile-updates", groupId = "hc-admin-ms")
    public void syncProfile(Profile profile) {
        profileRepository.save(profile);
    }
}
```

### 5. Conclusion

The `hc-admin-ms` microservice is designed to be the central hub for administrative operations within the Health-Connect ecosystem. By leveraging Spring Boot, MongoDB, Kafka, and Consul, it ensures scalability, flexibility, and seamless integration with other services. The JDL specification provides a clear blueprint for the domain model, while the business logic implementations ensure that core functionalities are robust and efficient.
