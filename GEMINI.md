# hc-admin-ms (Health Connect Admin Microservice)

This microservice is the administrative hub of the Health-Connect ecosystem. it serves as the source of truth for administrative operations, duty rosters, system catalogs (CMS), and pricing plans.

## 🏗️ Architecture & Technology Stack

- **Framework**: Spring Boot 4.x (JHipster 8.x/9.x blueprint)
- **Language**: Java 26
- **Database**: MongoDB (Document-oriented for flexible schemas)
- **Service Discovery & Config**: HashiCorp Consul
- **Messaging**: Apache Kafka (Broadcasting roster changes, syncing profiles)
- **Security**: OAuth2 Resource Server (JWT-based authentication)
- **API Documentation**: SpringDoc OpenAPI (Swagger)

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

- **Unit & Integration Tests**: `./mvnw verify`
- **Test Data**: Mock data is generated from `src/main/resources/data/hc-admin-ms-data.json` and initialized by `DevelopmentDataInitializer`.

## 📂 Project Structure

- `src/main/java/net/jojoaddison/`:
  - `broker/`: Kafka producers and consumers (e.g., `RosterEvent`).
  - `config/`: Spring Boot and JHipster configuration (including `DevelopmentDataInitializer`).
  - `domain/`: MongoDB entities (e.g., `DutyRoster`, `SystemCatalog`, `PricingPlan`).
  - `repository/`: Spring Data MongoDB repositories.
  - `service/`: Business logic implementations.
  - `web/`: REST controllers.
- `src/main/resources/config/`: Application YAML configurations (dev, prod, tls).
- `src/main/docker/`: Docker Compose files for infrastructure and the app itself.
- `.jhipster/`: Entity configuration files for JHipster.
- `jdl/`: JDL definitions for the domain model.

## 🛠️ Key Commands

- `npm run app:up`: Spin up the entire stack using Docker.
- `npm run java:docker`: Build a Docker image of the application.
- `npm run prettier:format`: Format the codebase.
- `./mvnw verify -Pprod`: Build a production-ready JAR.

## 📜 Conventions & Standards

- **DDD**: Follow Domain-Driven Design principles where possible.
- **JHipster**: Adhere to JHipster patterns for entity management and service layers.
- **Kafka**: Use the `roster` topic for shift updates and `profile-updates` for syncing.
- **Formatting**: Managed by Prettier and Spotless. Run `npm run prettier:format` before committing.
