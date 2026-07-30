# hcAdminService (`hc-admin-service`)

The administrative microservice of the Health Connect platform — the source of truth for admin master data and operational admin workflows: profiles, teams, organisations, contacts/addresses, facilities, system catalogs (CMS), pricing plans, patient subscriptions, messages/notifications, duty rosters, and audit logs.

Generated with JHipster (`.yo-rc.json` records `jhipsterVersion: 8.11.0`; `package.json` still pins `generator-jhipster: 8.1.0`). Documentation: [https://www.jhipster.tech/documentation-archive/v8.1.0](https://www.jhipster.tech/documentation-archive/v8.1.0).

This is a "microservice" application intended to be part of a microservice architecture, please refer to the [Doing microservices with JHipster][] page of the documentation for more information.
This application is configured for Service Discovery and Configuration with Consul. On launch, it will refuse to start if it is not able to connect to Consul at [http://localhost:8500](http://localhost:8500). For more information, read our documentation on [Service Discovery and Configuration with Consul][].

## At a glance

|                    |                                                                                                      |
| ------------------ | ---------------------------------------------------------------------------------------------------- |
| Java / Spring Boot | 26 / 4.0.6 (enforcer accepts JDK `[17,27)`)                                                          |
| Database           | MongoDB, default db `adminService`; migrations via Mongock                                           |
| Ports              | **5507** (dev profile), **8080** (prod profile)                                                      |
| Discovery          | Consul at `localhost:8500` — startup fails without it                                                |
| Messaging          | Kafka via Spring Cloud Stream (`kafkaConsumer;kafkaProducer`)                                        |
| Security           | OAuth2 resource server; JWTs are issued by `hc-admin-gateway`, not here (`skipUserManagement: true`) |
| Package root       | `net.jojoaddison`                                                                                    |

### Place in the stack

```
hc-admin-dashboard (Angular, :4200)
  └─ hc-admin-gateway (:5504 dev / :5503 prod)
       └─ hc-admin-service (:5507 dev / :8080 prod)   ← this repo
```

The gateway owns users, authorities, and login; this service trusts the JWT relayed to it.

**Known routing mismatch:** this service registers in Consul as `hcadminservice` (`spring.cloud.consul.discovery.service-name`), the gateway's static dev route matches `/services/admin-service/**`, and the Angular dashboard calls `/services/hc-admin-ms/...`. None of the three agree — expect 404s on entity endpoints through the gateway until they are reconciled.

## Project Structure

Node is required for generation and recommended for development. `package.json` is always generated for a better development experience with prettier, commit hooks, scripts and so on.

In the project root, JHipster generates configuration files for tools like git, prettier, eslint, husky, and others that are well known and you can find references in the web.

`/src/*` structure follows default Java structure.

- `.yo-rc.json` - Yeoman configuration file
  JHipster configuration is stored in this file at `generator-jhipster` key. You may find `generator-jhipster-*` for specific blueprints configuration.
- `.yo-resolve` (optional) - Yeoman conflict resolver
  Allows to use a specific action when conflicts are found skipping prompts for files that matches a pattern. Each line should match `[pattern] [action]` with pattern been a [Minimatch](https://github.com/isaacs/minimatch#minimatch) pattern and action been one of skip (default if ommited) or force. Lines starting with `#` are considered comments and are ignored.
- `.jhipster/*.json` - JHipster entity configuration files
- `/src/main/docker` - Docker configurations for the application and services that the application depends on

## Development

To start your application in the dev profile, run:

```
./mvnw
```

If your local MongoDB requires authentication, use the local env + launcher workflow:

```bash
# One-time setup
cp .env.local.example .env.local
# Edit .env.local and set SPRING_MONGODB_URI for your machine
./run-local.sh
```

The launcher reads `SPRING_MONGODB_URI` from `.env.local` and exports it before starting Maven. You can also pass Maven arguments through:

```bash
./run-local.sh -ntp -DskipTests spring-boot:run
```

For further instructions on how to develop with JHipster, have a look at [Using JHipster in development][].

## Building for production

### Packaging as jar

To build the final jar and optimize the hcAdminService application for production, run:

```
./mvnw -Pprod clean verify
```

To ensure everything worked, run:

```
java -jar target/*.jar
```

Refer to [Using JHipster in production][] for more details.

### Packaging as war

To package your application as a war in order to deploy it to an application server, run:

```
./mvnw -Pprod,war clean verify
```

### JHipster Control Center

JHipster Control Center can help you manage and control your application(s). You can start a local control center server (accessible on http://localhost:7419) with:

```
docker compose -f src/main/docker/jhipster-control-center.yml up
```

## Testing

### Spring Boot tests

To launch your application's tests, run:

```
./mvnw verify
```

Run a single test class or method:

```bash
./mvnw -q -Dtest=OrganisationResourceIT test
./mvnw -q -Dtest=OrganisationResourceIT#createOrganisation test
```

Conventions:

- Unit tests are `*Test.java`; integration tests are `*IT.java`. `junit-platform.properties` uses `SpringBootTestClassOrderer`, so plain unit tests run before context-booting ones.
- Integration tests annotate `@IntegrationTest` plus `@AutoConfigureMockMvc(addFilters = false)` and `@WithMockUser`.
- Testcontainers are wired via `src/test/resources/META-INF/spring.factories`. `TestContainersSpringContextCustomizerFactory` injects a MongoDB replica-set URI; `KafkaTestContainersSpringContextCustomizerFactory` only starts Kafka for classes annotated `@EmbeddedKafka`. Stream-focused tests can import `TestChannelBinderConfiguration` instead — see `HcAdminServiceKafkaResourceIT`.
- Docker must be running for any integration test, since Testcontainers provisions MongoDB (and optionally Kafka).

### Development seed data

`DevelopmentDataInitializer` (`@Profile({"dev","test"})`) loads `src/main/resources/data/hc-admin-ms-data.json` on startup, seeding eleven collections and logging a count for each. Records carry explicit ids, so restarts overwrite the same documents rather than duplicating them. `DevelopmentDataInitializerTest` guards the JSON-to-model contract with a strict `ObjectMapper` that fails on unknown fields, so the build breaks if the seed file and the domain model drift apart. See [`admin-api.md`](admin-api.md#3-development-seed-data) for the file's shape.

### Kafka / SSE bridge

Spring Cloud Stream bindings live in `application.yml` with `spring.cloud.function.definition: kafkaConsumer;kafkaProducer`. `HcAdminServiceKafkaResource` publishes through `StreamBridge`, and `broker/KafkaConsumer` fans inbound messages out to registered `SseEmitter` clients. Roster changes are broadcast on the `roster` topic; profile syncs from `hc-patient-ms` / `hc-professional-service` arrive on `profile-updates`.

## Others

### Code quality using Sonar

Sonar is used to analyse code quality. You can start a local Sonar server (accessible on http://localhost:9001) with:

```
docker compose -f src/main/docker/sonar.yml up -d
```

Note: we have turned off forced authentication redirect for UI in [src/main/docker/sonar.yml](src/main/docker/sonar.yml) for out of the box experience while trying out SonarQube, for real use cases turn it back on.

You can run a Sonar analysis with using the [sonar-scanner](https://docs.sonarqube.org/display/SCAN/Analyzing+with+SonarQube+Scanner) or by using the maven plugin.

Then, run a Sonar analysis:

```
./mvnw -Pprod clean verify sonar:sonar -Dsonar.login=admin -Dsonar.password=admin
```

If you need to re-run the Sonar phase, please be sure to specify at least the `initialize` phase since Sonar properties are loaded from the sonar-project.properties file.

```
./mvnw initialize sonar:sonar -Dsonar.login=admin -Dsonar.password=admin
```

Additionally, Instead of passing `sonar.password` and `sonar.login` as CLI arguments, these parameters can be configured from [sonar-project.properties](sonar-project.properties) as shown below:

```
sonar.login=admin
sonar.password=admin
```

For more information, refer to the [Code quality page][].

### Using Docker to simplify development (optional)

You can use Docker to improve your JHipster development experience. A number of docker-compose configuration are available in the [src/main/docker](src/main/docker) folder to launch required third party services.

For example, to start a MongoDB database in a docker container, run:

```bash
# One-time setup for mongodb.yml
cp src/main/docker/mongo-env.example src/main/docker/mongo.env

docker compose -f src/main/docker/mongodb.yml up -d
```

`mongodb.yml` uses `mongo:8` and loads credentials from `src/main/docker/mongo.env`.

To stop it and remove the container, run:

```bash
docker compose -f src/main/docker/mongodb.yml down
```

You can also fully dockerize your application and all the services that it depends on.
To achieve this, first build a docker image of your app by running:

```
npm run java:docker
```

Or build a arm64 docker image when using an arm64 processor os like MacOS with M1 processor family running:

```
npm run java:docker:arm64
```

Then run:

```
docker compose -f src/main/docker/app.yml up -d
```

When running Docker Desktop on MacOS Big Sur or later, consider enabling experimental `Use the new Virtualization framework` for better processing performance ([disk access performance is worse](https://github.com/docker/roadmap/issues/7)).

For more information refer to [Using Docker and Docker-Compose][], this page also contains information on the docker-compose sub-generator (`jhipster docker-compose`), which is able to generate docker configurations for one or several JHipster applications.

## Troubleshooting

### MongoDB: `Command listIndexes requires authentication` on startup

Mongock performs index checks at startup and fails immediately if MongoDB authentication is enabled but credentials are missing.

Use `.env.local` + `run-local.sh` so `SPRING_MONGODB_URI` is exported before startup.

### `.env.local` missing

If `run-local.sh` exits with a missing env file error:

```bash
cp .env.local.example .env.local
```

Then set `SPRING_MONGODB_URI=...` in `.env.local`.

### `SPRING_MONGODB_URI` not set

Ensure `.env.local` contains a line starting with:

```text
SPRING_MONGODB_URI=mongodb://user:password@localhost:27017/adminService?authSource=admin
```

### Consul not reachable

If startup fails with `Connection refused` on `localhost:8500`, start Consul first:

```bash
docker compose -f src/main/docker/consul.yml up -d
# or
npm run docker:consul:up
```

### MongoDB default database

The project default database is `adminService` across app and Docker configs.
If you override `SPRING_MONGODB_URI` in `.env.local`, keep the same database name unless you intentionally target another database.

## Continuous Integration (optional)

To configure CI for your project, run the ci-cd sub-generator (`jhipster ci-cd`), this will let you generate configuration files for a number of Continuous Integration systems. Consult the [Setting up Continuous Integration][] page for more information.

[JHipster Homepage and latest documentation]: https://www.jhipster.tech
[JHipster 8.1.0 archive]: https://www.jhipster.tech/documentation-archive/v8.1.0
[Doing microservices with JHipster]: https://www.jhipster.tech/documentation-archive/v8.1.0/microservices-architecture/
[Using JHipster in development]: https://www.jhipster.tech/documentation-archive/v8.1.0/development/
[Service Discovery and Configuration with Consul]: https://www.jhipster.tech/documentation-archive/v8.1.0/microservices-architecture/#consul
[Using Docker and Docker-Compose]: https://www.jhipster.tech/documentation-archive/v8.1.0/docker-compose
[Using JHipster in production]: https://www.jhipster.tech/documentation-archive/v8.1.0/production/
[Running tests page]: https://www.jhipster.tech/documentation-archive/v8.1.0/running-tests/
[Code quality page]: https://www.jhipster.tech/documentation-archive/v8.1.0/code-quality/
[Setting up Continuous Integration]: https://www.jhipster.tech/documentation-archive/v8.1.0/setting-up-ci/
[Node.js]: https://nodejs.org/
[NPM]: https://www.npmjs.com/
