# Gametime

Basketball simulation game — API service, future React frontend.

## Project structure

```
gametime/
├── CLAUDE.md                  AI session entry point (this file)
├── docs/                      Project documentation
│   ├── PROJECT_PLAN.md        Phased roadmap (8 phases)
│   ├── DECISIONS.md           Architecture decision log
│   ├── RISKS.md               Active risks and concerns
│   ├── TODO.md                Tactical task list
│   └── WORKFLOW.md            Development workflow and conventions
├── gametime-service/          Multi-module Maven project (Spring Boot 3.5.14)
│   ├── pom.xml                Parent POM (packaging=pom)
│   ├── gametime-api/          OpenAPI codegen module (generates server stubs)
│   │   ├── yml/gametime.yaml  API spec (source of truth)
│   │   └── pom.xml            openapi-generator-maven-plugin
│   ├── gametime-app/          Spring Boot application (hand-written code)
│   │   └── src/
│   ├── docker-compose.yml     Local Postgres container
│   └── http/                  IntelliJ .http files for manual REST testing
```

## Project docs

Before starting work, review these for context:
- **`docs/PROJECT_PLAN.md`** — phased roadmap, what's built vs what's needed
- **`docs/player.md`** — player domain design: attributes, derived skills, calculator formulas (current focus)
- **`docs/DECISIONS.md`** — past architecture choices (check before proposing alternatives)
- **`docs/TODO.md`** — current task list
- **`docs/RISKS.md`** — known risks and concerns
- **`docs/WORKFLOW.md`** — coding conventions, testing strategy, branch protocol

## Build requirements

- **Java 21** (LTS) via SDKMAN: `~/.sdkman/candidates/java/21.0.9-tem`
- **Homebrew Maven uses JDK 25 by default** which breaks Lombok. Always set JAVA_HOME:
  ```
  JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean test
  ```
- Do not use JDK 25 (Homebrew default) — Lombok 1.18.x is incompatible.

## Build commands

```bash
# Compile
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean compile

# Run tests (uses H2 in-memory, no Docker needed)
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean test

# Run with Docker integration tests
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn verify -Ptest
```

## Module boundaries

- **gametime-api**: Generated code only. Never put hand-written code here. API spec changes go in `gametime-api/yml/gametime.yaml`. Generated stubs land in `target/`.
- **gametime-app**: All hand-written code — entities, services, repos, delegate implementations, tests, resources.
- The app module depends on the api module as a Maven dependency.

## Database

- **Local dev**: Postgres in Docker container (`docker-compose.yml`), port 5432
- **Tests (`mvn test`)**: H2 in-memory, no Docker needed. Test properties override in `src/test/resources/application-local.properties`.
- **Schema**: All app tables live in the `gametime` schema (not `public`).
- **Liquibase**: Manages schema creation and migrations. Changelog at `src/main/resources/db/changelog.yml`.
- Postgres-specific features (triggers, plpgsql functions) are gated with `dbms:postgresql` in Liquibase changesets.
- Audit columns (`create_user`, `create_date`, `update_user`, `update_date`) have defaults for H2 compatibility; Postgres triggers override them.

## Local dev setup

```bash
cd gametime-service
docker compose up -d                    # Start Postgres
# If fresh container needed:
docker compose down -v && docker compose up -d

# Run from IDE or:
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn spring-boot:run -pl gametime-app
```

App runs on port 8080. Swagger UI at http://localhost:8080/swagger-ui.html

## Key conventions

- OpenAPI delegate pattern: generated `V1ApiDelegate` interface, hand-written `V1ApiDelegateimpl` implements it.
- Entities use Lombok `@Data` for boilerplate reduction.
- Entity `@Table` annotations include `schema = "gametime"`.
- Cucumber tests use JUnit 5 Platform (`@Suite` + `cucumber-junit-platform-engine`), not JUnit 4 vintage.
- Spring profile `local` is active by default. Test profile overrides to H2 via `src/test/resources/`.
