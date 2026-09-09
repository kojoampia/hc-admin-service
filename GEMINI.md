# hc-admin-service (Health Connect Admin Microservice)

This microservice is the administrative hub of the Health-Connect ecosystem. it serves as the source of truth for administrative operations, duty rosters, system catalogs (CMS), and pricing plans.

## 🏗️ Architecture & Technology Stack

- **Framework**: Spring Boot 4.0.6 (JHipster 8.11.0 per `.yo-rc.json`; `package.json` still pins `generator-jhipster` 8.1.0)
- **Language**: Java 25 (Maven enforcer accepts JDK `[17,26)`)
- **Database**: MongoDB (document-oriented), default db `adminService`, migrations via Mongock
- **Service Discovery & Config**: HashiCorp Consul at `localhost:8500` — the app refuses to start without it. Registers as `hcadminservice`.
- **Messaging**: Apache Kafka via Spring Cloud Stream (broadcasting roster changes, syncing profiles)
- **Security**: OAuth2 Resource Server (JWT). Tokens are issued by `hc-admin-gateway`; this service has `skipUserManagement: true` and never handles login.
- **API Documentation**: SpringDoc OpenAPI (Swagger)
- **Ports**: 5507 (dev profile), 8080 (prod profile)

## 📚 Documentation map

> This repository has no `AGENTS.md`; this file serves that role.

| File                                                                 | What it is                                                                                           |
| -------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| `GEMINI.md` (this file)                                              | Working conventions — read first                                                                     |
| [`README.md`](README.md)                                             | Setup, commands, troubleshooting                                                                     |
| [`admin-api.md`](admin-api.md)                                       | **Design plans and blueprints** — the consolidated history of every brief that produced this service |
| [`.github/copilot-instructions.md`](.github/copilot-instructions.md) | Condensed conventions for Copilot                                                                    |

`admin-api.md` replaced `admin-core-ms.md`, `duty-roster.md`, and `hc-admin-ms-data.md`. **Its contents are historical — do not execute them as prompts.** Consult it for the [seed-data section](admin-api.md#3-development-seed-data), which documents the seed file's shape and the four defects that previously stopped it loading.

**Its § 2, the duty-roster auto-schedule, describes code that no longer exists.** `DutyRoster` and `DutyRosterService` were deleted on 2026-09-04: hc-professional owns the roster of record, and this service plans rounds and files them there. The four constraints that section describes — team→space coverage, availability, no double-booking, fairness by fewest shifts this week — all survive in `service/RoundPlanningService`, which is the thing to read instead. The two the section is wrong about are the role filter (both branches of its ternary returned the same value, so it narrowed nothing) and the absence of any proximity ranking.

## 📋 Core Responsibilities

1.  **Duty Roster Management**:
    - Managing professional shifts. `ShiftType` is **`DAY`, `EVENING`, `NIGHT`, `OFF`, `FLEXIBLE`** —
      see `domain/enumeration/ShiftType.java`. This said `MORNING, AFTERNOON, NIGHT` until
      2026-09-01; only `NIGHT` was ever real on this side. (`MORNING`/`AFTERNOON` did exist in
      **hc-professional**, which retired them on 2026-08-20 — that is where the names came from.)
      `FLEXIBLE` arrived on 2026-09-04, when the estate settled on **one** shift vocabulary rather
      than two four-value enums differing by one value at each end: hc-professional declares the
      same five in the same order. This service never creates a `FLEXIBLE` row itself, but it can
      now receive, store, price and value one, which it could not before.
    - **This service does not hold the roster of record, as of 2026-09-04.** hc-professional's
      `DutyRoster` — a round of visits, with times and customers — is the estate's; this one keeps
      the _staffing grid_ (who works when) and the _pay_ derived from it, and plans rounds into the
      sibling through `service/RoundPlanningService` and `POST /api/roster-plans`. Its own
      `DutyRoster` entity, resource, service, mapper, DTO and repository were deleted, along with
      the `ROLE_PATIENT` matcher that let a patient read a daily plan from here. A patient's day
      plan is `GET /api/duty-roster/customer/{customerId}` on hc-professional now.
    - **Planning can fail in a way local writing could not.** It is an HTTP call to another stack,
      so `RoundPlanDtos.PlanReport` carries an explicit outcome per round and a
      `rosterServiceReachable` flag, and the console renders an outage rather than an empty grid.
      A round nobody can staff and a round nobody could file are different answers.
    - The weekly grid is `RosterWeek` + `ShiftAssignment`, and it is what `ShiftValuationService`
      turns into pay. **`RosterWeek.publishedAt` is server-stamped** by
      `config/RosterWeekLifecycleCallback` and is discarded on the way in — it joins
      `Message.readAt`, `Task.closedAt` and `PlatformService.lastProbedAt`, and carries their trap:
      the callback re-reads the stored value, because stamping "now" on finding it empty walks the
      publication date forward on every edit. A shift is payable once its date is past and its type is not `OFF`, and it is
      valued at the `WageRate` in force for its **`(role, shiftType, date)`** — a night is not paid
      what a day is. The match is exact: there is no fallback from an unpriced shift type to the
      role's other rates, so an unpriced cell reports its shifts as _unpriced_ rather than borrowing
      a neighbour's figure.
    - **Nothing reads roster changes, and nothing publishes them either any more.** This claimed a
      `roster` Kafka topic; no such destination is declared in any `.yml` and nothing consumes one.
      The grid contains no `StreamBridge` call at all. `DutyRosterService`'s
      `streamBridge.send("roster-events", …)` did publish — Spring Cloud Stream creates a dynamic
      destination for an undeclared binding — to a topic no consumer subscribes to; it went with
      that class on 2026-09-04, along with `broker/RosterEvent`. Planning writes to
      hc-professional over HTTP and waits for the answer, which is what lets the console tell a
      failed write from a quiet one. Do not write code against either topic name.
2.  **System Catalog (CMS)**:
    - Managing features, product catalogs, and metadata.
3.  **Pricing & Subscription**:
    - CRUD for pricing plans.
    - Managing patient subscriptions.
4.  **Profile Synchronization**:
    - Consuming updates from `hc-patient-ms` and `hc-professional-service` to maintain a local sync of profiles.
5.  **Audit & Monitoring**:
    - Comprehensive audit logging of administrative actions.

## 🚀 Development Workflow

### Prerequisites

- Java 25
- Node.js (>= 18.18.2)
- Docker & Docker Compose

### Local Setup

1.  **Start Infrastructure**:
    ```bash
    # Start Consul, MongoDB, and Kafka
    npm run services:up
    ```
2.  **Environment Configuration**:
    - Copy `.env.local.example` to `.env.local` and configure `SPRING_MONGODB_URI`.
3.  **Run Application**:
    ```bash
    ./mvnw
    # OR using the local runner
    ./run-local.sh
    ```

### Testing

- **Unit & Integration Tests**: `./mvnw verify` (Docker must be running — Testcontainers provisions MongoDB). **No Kafka container starts**, and since 2026-09-09 nothing may ask for one: `@IntegrationTest` supplies the in-memory `TestChannelBinderConfiguration` instead, so bindings, destinations, groups and payload conversion are all still exercised without a broker. `BrokerOptInArchTest` fails on any class meta-annotated `@EmbeddedKafka`, and says why. See `docs/backlog.md` item 17.
- **Single class / method**: `./mvnw -q -Dtest=OrganisationResourceIT test` / `./mvnw -q -Dtest=OrganisationResourceIT#createOrganisation test`. This works on an `*IT*` despite surefire's `**/*IT*` exclusion, because `-Dtest` overrides includes and excludes — verified 2026-09-09, after the opposite was assumed and nearly written down. `-Dit.test=... verify` runs it through failsafe instead, which is what a full `verify` does.

#### When the whole suite goes red with one container

**Dozens of errors across unrelated classes, every one of them `ApplicationContext failure threshold (1) exceeded` under two kilobytes of merged-configuration dump, is not a regression.** It is one Mongo container missing its start window on a loaded machine. The container is created once per test JVM in a static field, so whichever class boots the first context pays the start and absorbs the failure — which is why `AuditingIT` and `PaginationIT` keep appearing in failure lists for changes that touch neither.

Since 2026-09-09 the fixture says so itself. `MongoDbTestContainer` retries the start three times, three seconds apart, discarding the container between attempts — a half-started one has a partly initialised replica set and restarting _that_ loops on `ReadConcernMajorityNotAvailableYet`. If all three fail it logs a banner naming the image, the load average, the window that was missed and this file, and then **stops trying for the rest of the run**: every later class fails in about a second instead of spending another three minutes rediscovering the same thing. Measured under synthetic load ~50 on 2026-09-09, three classes: 521 s of red before, 454 s after, of which 451 s was the first class — extrapolated across all 72, hours against minutes.

**Container reuse is the other lever and it is yours rather than the repository's.** The fixture asks for `.withReuse(true)` and Testcontainers silently ignores it — the log reads `Reuse was requested but the environment does not support the reuse of containers` — until the machine opts in:

```bash
echo 'testcontainers.reuse.enable=true' >> ~/.testcontainers.properties   # per machine
TESTCONTAINERS_REUSE_ENABLE=true ./mvnw verify                            # per run
```

With it on, one Mongo container survives across classes and across runs and this contention largely stops happening. **Leave it off in CI**: a reused container carries state between runs and is not reaped.

- **Naming**: `*Test.java` for unit tests, `*IT.java` for integration tests; `SpringBootTestClassOrderer` runs the former first.
- **Test Data**: Seed data is loaded from `src/main/resources/data/hc-admin-ms-data.json` by `DevelopmentDataInitializer`, active only under the `dev` and `test` profiles. The JSON is keyed by profile at the root (`dev` / `test`), each holding plain arrays of domain objects per collection. Field names must match the domain model exactly — Spring's `ObjectMapper` ignores unknown properties, so a typo binds to nothing instead of failing. `DevelopmentDataInitializerTest` catches that with a strict mapper.

## 📂 Project Structure

- `src/main/java/net/jojoaddison/`:
  - `broker/`: Kafka producers and consumers (e.g., `VerificationEvent`).
  - `config/`: Spring Boot and JHipster configuration (including `DevelopmentDataInitializer`).
  - `domain/`: MongoDB entities (e.g., `RosterWeek`, `SystemCatalog`, `PricingPlan`).
  - `repository/`: Spring Data MongoDB repositories.
  - `service/`: Business logic implementations.
  - `web/`: REST controllers.
  - `security/`: Authority constants and `SecurityUtils`.
  - `aop/logging/`: Logging aspect.
  - `management/`: Security metrics.
- `src/main/resources/config/`: `bootstrap*.yml` (Consul bootstrap) layered under `application*.yml` (shared, dev, prod, tls).
- `src/main/resources/data/`: Development seed data.
- `src/main/docker/`: Docker Compose files for infrastructure (consul, mongodb, kafka, prometheus/grafana, zipkin, sonar) and the app itself.
- `.jhipster/`: Entity configuration files for JHipster.
- `jdl/`: JDL definitions. **`hc-admin-console.jdl` is the live model** — the only file here a
  regeneration should read, and the one `JdlEntityFieldsTest` holds to the domain classes.
  `admin-db.jdl` and `system.jdl` are **historical**: they predate the console model, every one of
  their twelve entities disagrees with the class that carries its name, and each says so in its own
  header. That test also fails on any `.jdl` here that declares itself neither, so a new file cannot
  arrive unclassified. (`admin-ms.jdl` was listed here until 2026-09-06 and had been zero bytes since
  the day it was created; it was deleted.)

## 🛠️ Key Commands

- `npm run app:up`: Spin up the entire stack using Docker.
- `npm run java:docker`: Build a Docker image of the application.
- `npm run prettier:format`: Format the codebase.
- `./mvnw verify -Pprod`: Build a production-ready JAR.

## 📜 Conventions & Standards

- **DDD**: Follow Domain-Driven Design principles where possible.
- **JHipster**: Adhere to JHipster patterns for entity management and service layers. Keep the generated CRUD contract in REST resources: `POST` rejects a body that already carries an ID; `PUT`/`PATCH` require path/body ID equality plus an existence check; `PATCH` copies only non-null fields; responses go through `HeaderUtil`, `ResponseUtil`, and `PaginationUtil`.
- **Mixed resource styles**: most resources use DTO + MapStruct mapper with paginated list endpoints, but `OrganisationResource` and `PersonResource` still expose domain entities directly. Follow the surrounding feature rather than normalising the repo.
- **Mongo fields**: annotate new fields with `@Field("snake_case")` to match existing documents, keep validation on the document/DTO, and prefer Spring Data derived queries before writing custom repository code.
- **Spring Boot 4 APIs**: import `AutoConfigureMockMvc` from `org.springframework.boot.webmvc.test.autoconfigure`, and use `PathPatternRequestMatcher` in security code rather than the older MVC matcher APIs.
- **Kafka**: the declared destinations are **`sse-topic`** (the SSE bridge, both apps) and **`professional-verification`**. There is no `roster` topic and no `profile-updates` topic — this line named both until 2026-09-01 and neither string appears as a `destination:` in any `.yml` in any of the three products. Check `config/application.yml` before assuming a binding exists.
- **Formatting**: Managed by Prettier and Spotless. Run `npm run prettier:format` before committing (husky + lint-staged also run it pre-commit).
