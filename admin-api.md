# hc-admin-service — Design Plans & Blueprints

Consolidated record of the design briefs that produced this service. It replaces `admin-core-ms.md`, `duty-roster.md`, and `hc-admin-ms-data.md`, which were merged here and deleted.

**These are historical plans, not specifications to execute.** Every brief below has already been implemented. Where the delivered code diverges from the brief, the code is the authority and the divergence is recorded. Do not re-run any of this as a prompt.

Operational docs live elsewhere and are still current: [`README.md`](README.md) for setup and commands, [`GEMINI.md`](GEMINI.md) and [`.github/copilot-instructions.md`](.github/copilot-instructions.md) for working conventions.

---

## Contents

1. [Core microservice brief](#1-core-microservice-brief) — original JDL and service scaffolding plan
2. [Duty roster auto-schedule](#2-duty-roster-auto-schedule) — patient-centric scheduling design
3. [Development seed data](#3-development-seed-data) — mock data generation brief, and the bug that stops it loading

---

## 1. Core microservice brief

Originally: _"Act as a Senior Backend Java/Spring Boot Developer specializing in Domain-Driven Design (DDD) and JHipster microservices. Generate the complete JHipster JDL and essential business logic components for the Health-Connect Admin Microservice."_

### Intended configuration vs. delivered

| Brief                       | Actual                                                                                                                                                  |
| --------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `baseName hcAdminMs`        | `hcAdminService` (`.yo-rc.json`); Consul `service-name` is `hcadminservice`                                                                             |
| `authenticationType oauth2` | `jwt` in `.yo-rc.json` — the app still runs as an OAuth2 resource server via `SecurityJwtConfiguration`, validating tokens minted by `hc-admin-gateway` |
| Spring Boot 3.x             | Spring Boot 4.0.6 on Java 26                                                                                                                            |
| `serverPort 5507`           | correct for the dev profile; prod runs on `8080`                                                                                                        |

Unchanged from the brief: microservice application type, `net.jojoaddison` package root, Consul service discovery, MongoDB persistence, Kafka message broker.

### Architecture requirements (as briefed)

- **Persistence:** MongoDB — a document DB suits the flexible schemas needed for features, catalogs, and metadata.
- **Messaging:** Apache Kafka. The service is both a Producer (broadcasting roster changes) and a Consumer (syncing Profile data from `hc-patient-ms` and `hc-professional-service`).
- **Security:** Resource server securing endpoints from JWT claims issued by the Gateway.

### Original JDL domain sketch

The JDL below is the _brief's_ sketch. The authoritative domain model is now `jdl/admin-ms.jdl`, `jdl/admin-db.jdl`, `jdl/system.jdl`, and the `.jhipster/*.json` entity configs. Several names changed in delivery — `Catalog` → `SystemCatalog`, `Organization` → `Organisation`, `Subscription` → `HCSubscription`, `PersonalInformation` → `Person` — and the sketch contains a duplicate `CatalogType` enum plus relationships to entities (`Patient`, `Admin`, `ProfessionalService`) that were never generated in this service.

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

entity Catalog {
  name String required,
  description String,
  type String required // CatalogType: SERVICE, PRODUCT, INFORMATION, ABOUT
}

entity PricingPlan {
  name String required,
  price BigDecimal required,
  features String,
  billingCycle String required,
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
  Team{supervisor} to Profile
  Team{organization} to Organization
  Subscription{patient} to Patient
  Subscription{plan} to PricingPlan
  DutyRoster{professional} to ProfessionalService
  SystemCatalog{admin} to Admin
  PricingPlan{admin} to Admin
}
```

Enums sketched: `Role`, `Gender`, `MaritalStatus`, `Status`, `BillingCycle`, `Shift`, `CatalogType`, `FeatureType`, `MessageType`, `DocumentType`, `OrganizationType`, `TeamRole`, `ProfessionalRole`, `PatientRole`, `VendorRole`, `AdminRole`, `ProfileStatus`, `DutyRole`.

Delivered enums (`domain/enumeration/`): `BillingType`, `CatalogType`, `DocumentType`, `DutyRole`, `FacilityType`, `FeatureType`, `GenderType`, `LanguageType`, `MessageType`, `RoleType`, `ShiftStatus`, `ShiftType`, `UnavailabilityReason`.

### Business logic scope

Four areas were briefed, all now implemented under `service/` and `web/rest/`:

- **Duty roster management** — CRUD plus Kafka broadcast of changes to the `roster` topic.
- **System catalog management** — CRUD for the CMS.
- **Pricing plan management** — CRUD plus patient subscription handling.
- **Profile synchronization** — consume Kafka messages to sync profile data from other services.

The brief's example services used field injection (`@Autowired`) and a raw `KafkaTemplate`. Delivery uses constructor injection throughout and publishes via `StreamBridge` over Spring Cloud Stream (`spring.cloud.function.definition: kafkaConsumer;kafkaProducer`), with `broker/KafkaConsumer` fanning inbound messages out to `SseEmitter` clients. Follow the delivered pattern, not the sketch.

---

## 2. Duty roster auto-schedule

Originally: _"Patient-Centric Duty Roster Auto-Schedule Implementation"_ — a heuristic scheduler where a shift represents an entry in a patient's personalised "Daily Service Plan", enforcing geographic, team-based, and date-range availability constraints.

### Delivered

- Scheduling logic: `service/DutyRosterService.java#autoScheduleShifts(LocalDate)`
- REST surface: `web/rest/DutyRosterResource.java`, mapped at **`/api/duty-rosters`** (the brief proposed `/api/v1/roster`)
  - `POST /api/duty-rosters/auto-schedule?date=…` — `ROLE_ADMIN`
  - `GET /api/duty-rosters/patient/{patientId}?date=…` — `ROLE_ADMIN` or `ROLE_PATIENT`
  - plus standard JHipster CRUD on the same base path
- Supporting types: `domain/GeographicSpace.java`, `domain/Team.java`, `domain/UnavailabilityPeriod.java`, and enums `UnavailabilityReason`, `ShiftType`, `ShiftStatus`, `DutyRole`

### Model divergences — important

- `Unavailability` shipped as **`UnavailabilityPeriod`**.
- **There is no `Shift` document and no `PatientProfile` document.** A shift _is_ a `DutyRoster` record: it carries `date`, `duty`, `shift`, `status`, `professionalId`, `patientId`, and `geographicSpaceId` directly. The patient/geography link the brief routed through `PatientProfile` is denormalised onto `DutyRoster` itself.
- Delivered domain classes are plain Java with explicit accessors; the brief's snippets use Lombok `@Data`, which this codebase does not use.
- The service layer works in `DutyRosterDTO` via MapStruct mappers, not raw documents.

### Availability rule

A professional is unavailable on a date when any `UnavailabilityPeriod` covers it — `fromDate <= date` and (`toDate` is null or `date <= toDate`) — or when the profile is inactive. Reasons: `NOT_VERIFIED`, `HOLIDAY`, `SICK_LEAVE`, `ABSENCE`.

### Scheduling heuristic (as briefed and implemented)

For each unassigned shift on the target date:

1. **Hard — geography.** Find teams covering the shift's `geographicSpaceId`. Skip the shift if none.
2. **Hard — role and team membership.** Candidates must match the required role and belong to a covering team, and be active.
3. **Hard — availability and double-booking.** Filter to professionals available on the date and not already assigned that day.
4. **Soft — fairness.** Sort candidates by fewest shifts already assigned that week (Monday–Sunday), to spread load and limit burnout.
5. **Assign** the first candidate, set status to assigned, and persist.
6. **Emit** a `RosterEvent` so patient and professional apps update in real time.

Reference implementation from the brief, kept for the constraint ordering:

```java
public void autoScheduleShifts(LocalDate date) {
  List<Shift> unassignedShifts = shiftRepository.findByDateAndStatus(date, ShiftStatus.UNASSIGNED);
  LocalDate startOfWeek = date.with(DayOfWeek.MONDAY);
  LocalDate endOfWeek = date.with(DayOfWeek.SUNDAY);

  for (Shift shift : unassignedShifts) {
    List<Team> coveringTeams = teamRepository.findByGeographicSpaceIdsContaining(shift.getGeographicSpaceId());
    List<String> validTeamIds = coveringTeams.stream().map(Team::getId).collect(Collectors.toList());
    if (validTeamIds.isEmpty()) continue;

    List<Profile> availableProfessionals = profileRepository.findByRoleTypeAndTeamIdInAndIsActiveTrue(
      shift.getRequiredRole(),
      validTeamIds
    );

    List<Profile> eligibleProfessionals = availableProfessionals
      .stream()
      .filter(p -> p.isAvailable(date))
      .filter(p -> !shiftRepository.existsByAssigneeIdAndDate(p.getId(), date))
      .collect(Collectors.toList());
    if (eligibleProfessionals.isEmpty()) continue;

    eligibleProfessionals.sort(
      Comparator.comparingInt(p -> shiftRepository.countByAssigneeIdAndDateBetween(p.getId(), startOfWeek, endOfWeek))
    );

    Profile selected = eligibleProfessionals.get(0);
    shift.setAssigneeId(selected.getId());
    shift.setStatus(ShiftStatus.ASSIGNED);
    shiftRepository.save(shift);

    kafkaTemplate.send("roster-events", new RosterEvent(shift.getId(), "PATIENT_SHIFT_ASSIGNED", shift.getPatientId(), selected.getId()));
  }
}

```

The frontend counterpart to this brief is in `hc-admin-dashboard`'s consolidated `admin-web.md`.

---

## 3. Development seed data

Originally: _"Act as a Senior Java Data Engineer. Generate a JSON file containing mock business entity data for `dev` and `test` environments, consistent with the user roles defined in the gateway."_

### Delivered artefacts

- Data file: `src/main/resources/data/hc-admin-ms-data.json`
- Initializer: `src/main/java/net/jojoaddison/config/DevelopmentDataInitializer.java`

### Actual data shape

`dev` and `test` are **root-level** keys, each holding eleven collections keyed by entity type — not the `facilities` / `audits` / `metrics` triple the brief sketched. There is no `metrics` collection.

| Collection       | `dev` records | `test` records |
| ---------------- | ------------- | -------------- |
| `addresses`      | 1             | 0              |
| `contacts`       | 1             | 0              |
| `facilities`     | 1             | 1              |
| `audits`         | 1             | 1              |
| `organisations`  | 1             | 0              |
| `persons`        | 1             | 0              |
| `teams`          | 1             | 0              |
| `profiles`       | 0             | 0              |
| `dutyRosters`    | 1             | 0              |
| `pricingPlans`   | 1             | 1              |
| `systemCatalogs` | 1             | 0              |

Records use real domain-model field names (`id`, `street`, `district`, …), not the `entityId` / `status` / `payload` envelope described in the brief.

### Initializer behaviour

`DevelopmentDataInitializer` is a `@Component` annotated `@Profile({dev, test})` implementing `ApplicationRunner`. It **returns early unless `spring.profiles.active` was passed as a command-line argument** — setting the profile through `application.yml` or an environment variable alone does not trigger it. It then reads the JSON and calls `saveAll` on each of the eleven repositories.

### ⚠ Seeding does not currently work

Two structural mismatches mean the load fails and no data is inserted:

1. **Root-key mismatch.** The initializer deserialises into `SeedData`, whose only field is `Map<String, ProfileData> data`, then calls `seedData.getData().get(profile)`. That expects `{"data": {"dev": …, "test": …}}`, but the JSON has `dev` and `test` at the root with no `data` wrapper.
2. **Non-static inner classes.** `SeedData` and `ProfileData` are declared `private class` inside `DevelopmentDataInitializer` (lines 131 and 140). Jackson cannot instantiate non-static inner classes, so binding fails regardless of the key structure.

Both failures are swallowed by the surrounding `try/catch`, which logs `Failed to initialize {} data` (or `No data found for profile: {}`) and lets startup continue — so an empty database after boot looks like a data problem rather than a wiring problem. **Check the application log for those two messages before assuming the JSON is at fault.**

A fix requires making `SeedData`/`ProfileData` `static` (or extracting them to their own files) and reconciling the root key — either wrap the JSON contents in a `data` object, or deserialise directly into `Map<String, ProfileData>`. This has not been done.

### ⚠ Cross-service IDs are dangling

The brief specified `managedBy` values referencing gateway users `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11` (admin) and `…a12` (operator). Those UUIDs exist only in `hc-admin-gateway`'s unused `hc-admin-gw-data.json` blueprint. The gateway actually seeds `user-1` (admin), `user-2` (user), and a random UUID (operator) via `InitialSetupMigration`, so any `managedBy` reference in this service's seed data points at a user that does not exist. See the gateway's `admin-gateway.md` for the full comparison.
