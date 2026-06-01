# Copilot instructions for `hc-admin-service`

## Build, test, and lint commands

- **Start locally:** copy `.env.local.example` to `.env.local`, set `SPRING_MONGODB_URI`, then run `./run-local.sh`. This launcher exports `SPRING_MONGODB_URI` before invoking Maven. Local startup also expects Consul on `localhost:8500`; use `npm run docker:consul:up` when needed.
- **Run the app directly:** `./mvnw`
- **Production build:** `./mvnw -Pprod clean verify`
- **Full test suite:** `./mvnw -q test`
- **Single test class:** `./mvnw -q -Dtest=OrganisationResourceIT test`
- **Single test method:** `./mvnw -q -Dtest=OrganisationResourceIT#createOrganisation test`
- **No-HTTP / checkstyle lint:** `npm run backend:nohttp:test`
- **Formatting check:** `npm run prettier:check`
- **Apply formatting:** `npm run prettier:format`

## High-level architecture

- This is a JHipster-generated Spring Boot microservice backed by **MongoDB**, registered in **Consul**, and secured as an **OAuth2 resource server**. The main entry point is `net.jojoaddison.HcAdminServiceApp`.
- The main domain areas in this service are administrative master data and operational admin workflows: profiles, teams, organisations, contacts/addresses, facilities, system catalogs, pricing plans, subscriptions, messages/notifications, and duty rosters.
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
  - `spring.cloud.function.definition` wires `kafkaConsumer;kafkaProducer`.
  - `HcAdminServiceKafkaResource` publishes through `StreamBridge`.
  - `broker/KafkaConsumer` fans inbound messages out to registered `SseEmitter` clients.
- Security is centralized in `config/SecurityConfiguration`: `/api/**` requires authentication, `/api/admin/**` and most management endpoints require `ROLE_ADMIN`, and the app is stateless.
- Integration tests boot the full Spring context through `@IntegrationTest`, which wires reusable **MongoDB** and **Kafka** Testcontainers via `src/test/resources/META-INF/spring.factories` context customizers.
- Test execution is intentionally structured:
  - `TestContainersSpringContextCustomizerFactory` injects the Mongo replica-set URI into Spring tests.
  - `KafkaTestContainersSpringContextCustomizerFactory` only starts Kafka when the test class carries `@EmbeddedKafka`.
  - `junit-platform.properties` uses `SpringBootTestClassOrderer` so non-Spring tests run before full integration tests.

## Key conventions

- Preserve the existing JHipster CRUD shape in REST resources:
  - `POST` rejects bodies that already have an ID.
  - `PUT` and `PATCH` require path/body ID equality and an existence check before save.
  - `PATCH` only copies non-null fields.
  - Responses use `HeaderUtil`, `ResponseUtil`, and `PaginationUtil`.
- Follow the style of the surrounding feature instead of forcing one API shape across the repo. Most resources use **DTO + MapStruct mapper + paginated list endpoints**, but some endpoints such as `OrganisationResource` and `PersonResource` still expose domain entities directly.
- For new Mongo fields, mirror the existing document style: annotate with `@Field("snake_case")`, keep validation on the document/DTO, and prefer Spring Data derived queries before adding custom repository code.
- REST integration tests should use `@IntegrationTest`, `@AutoConfigureMockMvc(addFilters = false)`, and `@WithMockUser`. Add `@EmbeddedKafka` only when the test needs the Kafka container path; stream-focused tests can also import `TestChannelBinderConfiguration` like `HcAdminServiceKafkaResourceIT`.
- This repo is already on **Spring Boot 4** conventions: import `AutoConfigureMockMvc` from `org.springframework.boot.webmvc.test.autoconfigure`, and use `PathPatternRequestMatcher` in security code instead of older MVC matcher APIs.
- Formatting is handled with **Prettier** for Java, YAML, JSON, HTML, and Markdown; keep new files compatible with the existing Prettier/lint-staged setup.
