# Copilot instructions for `hc-admin-service`

## ⚠ First: confirm which repository you are in

This product is several independent repositories sitting side by side in a plain directory, so a checkout that looks right can be the
wrong one. **Run `git remote -v` before drawing any conclusion from `git`** — before `git log`, `git branch` or `git status`, not after.
It must name the repository you were asked to change; if it names another, **stop and report rather than editing the shared checkout**.

The wrong answer is not an error. It is a plausible repository with unfamiliar history, and a session's working directory can also move
after a correct start. So a `git log` that surprises you — an unrecognised `main`, a branch of yours that is suddenly missing — means
find out which repository you are reading. It does not mean your work was lost.

## Build, test, and lint commands

- **Start locally:** copy `.env.local.example` to `.env.local`, set `SPRING_MONGODB_URI`, then run `./run-local.sh`. This launcher exports `SPRING_MONGODB_URI` before invoking Maven. Local startup also expects Consul on `localhost:8500`; use `npm run docker:consul:up` when needed.
- **Run the app directly:** `./mvnw`
- **Production build:** `./mvnw -Pprod clean verify`
- **Full test suite:** `./mvnw verify` — not `test`. Surefire excludes `**/*IT*` and `**/*IntTest*` (`pom.xml:742-744`), so the `test` phase runs the unit tests, skips every integration test, and reports success.
- **Single test class:** `./mvnw -q -Dtest=OrganisationResourceIT test` — an `*IT*` selects despite the exclusion above, because naming a test empties surefire's exclude list (measured 2026-09-18).
- **Single test method:** `./mvnw -q -Dtest=OrganisationResourceIT#createOrganisation test`
- **No-HTTP / checkstyle lint:** `npm run backend:nohttp:test`
- **Formatting check:** `npm run prettier:check`
- **Apply formatting:** `npm run prettier:format`

## High-level architecture

- This is a JHipster-generated Spring Boot 4.1.0 / Java 25 microservice backed by **MongoDB** (default db `adminService`), registered in **Consul**, and secured as an **OAuth2 resource server**. The main entry point is `net.jojoaddison.HcAdminServiceApp`.
- It is one of three application repositories in the admin stack: `hc-admin-app` (the Angular console) → `hc-admin-gateway` (:5504 dev / :5503 prod) → this service (:5507 dev / :8080 prod). The gateway owns users, authorities, and login (`skipUserManagement: true` here, `.yo-rc.json:75`); this service only validates the JWT relayed to it. The console was `hc-admin-dashboard` until that repository was **archived on 2026-08-11** — it is read-only on GitHub and nothing lands there, not even fixes.
- **Service naming:** this service registers in Consul as `hcadminservice` (`config/application.yml:109`); the gateway publishes it at `/services/hcadminservice/**` and the console calls exactly that. If a `/services/...` call 404s, check the Consul catalogue first — an unregistered service is a 404, not an error.
- The main domain areas in this service are administrative master data and operational admin workflows: profiles, teams, organisations, contacts/addresses, facilities, system catalogs, pricing plans, subscriptions, messages/notifications, geographic spaces, directory links, and the staffing grid — `RosterWeek` + `ShiftAssignment`, priced by `WageRate` and valued by `ShiftValuationService`.
- **Not duty rosters.** There is no `DutyRoster` document, resource, service or repository in this codebase; hc-professional owns the roster of record, and this service plans rounds into it over HTTP through `service/RoundPlanningService` and `POST /api/roster-plans`. Every remaining `DutyRoster` mention in `src/main/java` is javadoc about the far service or about what was deleted.
- Most business flows follow `web/rest -> service -> repository -> domain`:
  - `web/rest` exposes CRUD endpoints and uses JHipster response helpers.
  - `service` contains orchestration and partial-update logic.
  - `repository` uses Spring Data Mongo repositories with derived query methods.
  - `domain` contains Mongo `@Document` models with explicit `@Field("snake_case")` mappings.
- Configuration is split between `bootstrap*.yml` and `application*.yml`:
  - `bootstrap.yml` handles Consul discovery/config bootstrap.
  - `application.yml` defines shared Spring Cloud Stream bindings, management endpoints, and common app settings.
  - `application-dev.yml` runs on port `5507` and reads MongoDB from `SPRING_MONGODB_URI`.
  - `application-prod.yml` switches to port `8080` and keeps the same MongoDB env-var based override pattern.
- The service also exposes a lightweight **Kafka/SSE bridge**:
  - Spring Cloud Stream bindings are defined in `application.yml`.
  - `spring.cloud.function.definition` wires **five** functions — `kafkaConsumer;kafkaProducer;patientDirectoryConsumer;professionalDirectoryConsumer;professionalProfileConsumer` (`config/application.yml:127`). The last three are the inbound directory subscriptions to the sibling stacks; a function missing from that list is a bean that exists, a binding that is configured, and no message ever delivered, and nothing logs it.
  - `HcAdminServiceKafkaResource` publishes through `broker/OutboundEventPublisher`, which is the only class permitted to hold a `StreamBridge` (`OutboundEventPublisher.java:110`). `broker/OutboundPublishingArchTest` enforces that on the dependency, so do not inject `StreamBridge` anywhere else.
  - `broker/KafkaConsumer` fans inbound messages out to registered `SseEmitter` clients.
- Security is centralized in `config/SecurityConfiguration`, and `/api/**` is **not** `authenticated()`. It is a read/write split: `GET /api/**` is `ROLE_ADMIN` or `ROLE_OPERATOR`, and everything else under `/api/**` is `ROLE_ADMIN` (`SecurityConfiguration.java:183-185`), so a bare `ROLE_USER` reaches nothing **under the split**. `/api/admin/**` (`:38`) and every management endpoint other than health/info/prometheus require `ROLE_ADMIN` (`:187-191`). Narrower matchers sit above the blanket rules and must stay there or they are never evaluated: `GET /api/professionals/me/**` (`:71`) and `GET /api/geographic-spaces` and `/{id}` (`:106-107`) are `.authenticated()` — which that bare `ROLE_USER` does satisfy — and `GET /api/vendors/{id}` (`:178`) also admits `ROLE_VENDOR`. The app is stateless.
- Integration tests boot the full Spring context through `@IntegrationTest`, which wires a reusable **MongoDB** Testcontainer via `src/test/resources/META-INF/spring.factories` context customizers, and the in-memory `TestChannelBinderConfiguration` in place of a broker. **No Kafka container starts.**
- Test execution is intentionally structured:
  - `TestContainersSpringContextCustomizerFactory` injects the Mongo replica-set URI into Spring tests.
  - `KafkaTestContainersSpringContextCustomizerFactory` starts Kafka only for a class carrying `@EmbeddedKafka`, and nothing does. It sat on `@IntegrationTest` itself until 2026-09-09 and therefore started a broker for all 72 integration tests, none of which asserted anything about one (`docs/backlog.md` item 17).
  - `junit-platform.properties` uses `SpringBootTestClassOrderer` so non-Spring tests run before full integration tests.

## Key conventions

- Preserve the existing JHipster CRUD shape in REST resources:
  - `POST` rejects bodies that already have an ID.
  - `PUT` and `PATCH` require path/body ID equality and an existence check before save.
  - `PATCH` only copies non-null fields.
  - Responses use `HeaderUtil`, `ResponseUtil`, and `PaginationUtil`.
- Follow the style of the surrounding feature instead of forcing one API shape across the repo. Most resources use **DTO + MapStruct mapper + paginated list endpoints**, but some endpoints such as `OrganisationResource` and `PersonResource` still expose domain entities directly.
- For new Mongo fields, mirror the existing document style: annotate with `@Field("snake_case")`, keep validation on the document/DTO, and prefer Spring Data derived queries before adding custom repository code.
- REST integration tests should use `@IntegrationTest`, `@AutoConfigureMockMvc(addFilters = false)`, and `@WithMockUser`. **Do not add `@EmbeddedKafka`** — `BrokerOptInArchTest` fails on it, because a broker costs every test in the run and no test here needs one. Stream-focused tests drive bindings through `InputDestination` / `OutputDestination`, as `HcAdminServiceKafkaResourceIT`, `VerificationEventIT` and `DirectoryEventConsumptionIT` do.
- This repo is already on **Spring Boot 4** conventions: import `AutoConfigureMockMvc` from `org.springframework.boot.webmvc.test.autoconfigure`, and use `PathPatternRequestMatcher` in security code instead of older MVC matcher APIs.
- Formatting is handled with **Prettier** for Java, YAML, JSON, HTML, and Markdown; keep new files compatible with the existing Prettier/lint-staged setup.
