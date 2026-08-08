# Gametime

Basketball simulation game — API service, future React frontend.

## Project structure

```
gametime/
├── CLAUDE.md                  AI session entry point (this file)
├── docs/                      Project documentation
│   ├── roadmap.md        Phased roadmap (8 phases)
│   ├── decisions.md           Architecture decision log
│   ├── risks.md               Active risks and concerns
│   ├── todo.md                Tactical task list (current phase only)
│   ├── backlog.md             Homeless infra/tooling chores (cross-phase)
│   ├── ideas.md               Parking lot — untriaged future-improvement ideas
│   ├── player.md              Player domain design (attributes, skills, formulas)
│   ├── roster.md              Roster & lineup domain (player↔team, lineups)
│   ├── coach.md               Coach domain design (5 decision attributes, #018)
│   ├── game.md                Game domain + possession engine (§3.1 models, §3.2–§3.11 flow)
│   └── possession-flow.puml   Possession-flow diagram (kept in sync with the engine)
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
- **`docs/roadmap.md`** — phased roadmap, what's built vs what's needed
- **`docs/roster.md`** — roster & lineup domain: player↔team link, lineups, transactions
- **`docs/player.md`** — player domain reference: attributes, derived skills, calculator design
- **`docs/coach.md`** — coach domain design: 5 continuous decision attributes (#018) + engine interface
- **`docs/game.md`** — game domain + the possession engine: Game/GameEvent/BoxScore
  models (§3.1) and the event vocabulary + flow the engine actually runs (§3.2–§3.11)
- **`docs/decisions.md`** — past architecture choices (check before proposing alternatives)
- **`docs/todo.md`** — current-phase task list (deferred work lives in backlog.md + roadmap.md)
- **`docs/backlog.md`** — cross-phase infra/tooling chores with no phase home
- **`docs/ideas.md`** — parking lot: untriaged future-improvement ideas (not planned work)
- **`docs/risks.md`** — known risks and concerns

When writing or editing any of these planning docs (a `#NNN` decision, a design
pass, an execute-ready plan, moving deferred work, parking an idea), invoke the
**`project-docs`** skill first — it captures the house format, the cross-file
routing rules, and the design-pass→decision→plan rhythm the docs follow.

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

## Git — never commit without the user's say-so

**Do not run `git commit` (or `git push`) unless the user explicitly asks for it
in that message.** This applies to every kind of change — engine code, tests,
planning docs, skills — and to work that is finished, verified, and green. A
passing build is not permission to commit.

The workflow is: make the edits, summarize what changed, then **stop and wait**.
The user reviews first and asks for the commit when they're ready. "Finish the
task" / "close it out" / "ship it" do **not** imply a commit; if it's ambiguous,
ask rather than assume. Completing a phase's close-out means the *files* are
updated, not that history is written.

Corollary: don't batch up "I'll commit it since I'm here" side commits, and don't
push a branch just because it's ahead of origin.

## Key conventions

- OpenAPI delegate pattern: generated `V1ApiDelegate` interface, hand-written `V1ApiDelegateimpl` implements it.
- Entities use Lombok `@Data` for boilerplate reduction.
- Entity `@Table` annotations include `schema = "gametime"`.
- Cucumber tests use JUnit 5 Platform (`@Suite` + `cucumber-junit-platform-engine`), not JUnit 4 vintage.
- Spring profile `local` is active by default. Test profile overrides to H2 via `src/test/resources/`.
