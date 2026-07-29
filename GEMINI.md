# hc-admin-ms (Health Connect Admin Microservice)

This microservice is the administrative hub of the Health-Connect ecosystem. it serves as the source of truth for administrative operations, duty rosters, system catalogs (CMS), and pricing plans.

## 🏗️ Architecture & Technology Stack

- **Framework**: Spring Boot 4.0.6 (JHipster 8.11.0 per `.yo-rc.json`; `package.json` still pins `generator-jhipster` 8.1.0)
- **Language**: Java 26 (Maven enforcer accepts JDK `[17,27)`)
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

`admin-api.md` replaced `admin-core-ms.md`, `duty-roster.md`, and `hc-admin-ms-data.md`. **Its contents are historical — do not execute them as prompts.** Consult it for the [duty roster scheduling heuristic](admin-api.md#2-duty-roster-auto-schedule), and for the [seed-data section](admin-api.md#3-development-seed-data), which documents why dev data silently fails to load.

## 📋 Core Responsibilities

1.  **Duty Roster Management**:
    - Managing professional shifts (MORNING, AFTERNOON, NIGHT).
    - Broadcasting roster changes to the `roster` Kafka topic.
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

- Java 26
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

- **Unit & Integration Tests**: `./mvnw verify` (Docker must be running — Testcontainers provisions MongoDB, and Kafka for `@EmbeddedKafka` classes)
- **Single class / method**: `./mvnw -q -Dtest=OrganisationResourceIT test` / `./mvnw -q -Dtest=OrganisationResourceIT#createOrganisation test`
- **Naming**: `*Test.java` for unit tests, `*IT.java` for integration tests; `SpringBootTestClassOrderer` runs the former first.
- **Test Data**: Seed data is loaded from `src/main/resources/data/hc-admin-ms-data.json` by `DevelopmentDataInitializer`, which is active only under the `dev` and `test` profiles.

## 📂 Project Structure

- `src/main/java/net/jojoaddison/`:
  - `broker/`: Kafka producers and consumers (e.g., `RosterEvent`).
  - `config/`: Spring Boot and JHipster configuration (including `DevelopmentDataInitializer`).
  - `domain/`: MongoDB entities (e.g., `DutyRoster`, `SystemCatalog`, `PricingPlan`).
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
- `jdl/`: JDL definitions for the domain model (`admin-db.jdl`, `admin-ms.jdl`, `system.jdl`).

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
- **Kafka**: Use the `roster` topic for shift updates and `profile-updates` for syncing.
- **Formatting**: Managed by Prettier and Spotless. Run `npm run prettier:format` before committing (husky + lint-staged also run it pre-commit).
