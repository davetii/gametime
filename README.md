# Gametime

A basketball simulation game — a Spring Boot API service with a React frontend.

Teams, players, and a deep player model (21 attributes feeding 23 derived skills
on a 1–20 scale) form the foundation for a season simulation engine.

## Repository layout

```
gametime/
├── gametime-service/     Multi-module Maven project (Spring Boot 3.5)
│   ├── gametime-api/     OpenAPI codegen — spec at yml/gametime.yaml (source of truth)
│   ├── gametime-app/     Hand-written code: entities, services, repos, mappers, tests
│   ├── http/             IntelliJ .http files for manual REST testing
│   └── docker-compose.yml  Local Postgres
├── gametime-frontend/    React + Vite app (early scaffold)
├── docs/                 Design docs (see below)
└── CLAUDE.md             Detailed build commands, module boundaries, conventions
```

## Quick start

Requires **JDK 21** (Lombok is incompatible with newer JDKs — see CLAUDE.md).

```bash
cd gametime-service
./mvnw clean install        # build + run tests (H2 in-memory, no Docker needed)
```

Run the service locally (starts Postgres via Docker, then the app on port 8080):

```bash
docker compose up -d
./mvnw spring-boot:run -pl gametime-app
```

API docs (Swagger UI): http://localhost:8080/swagger-ui.html

For the exact `JAVA_HOME` setup, profiles, and database details, see
[CLAUDE.md](CLAUDE.md).

## Documentation

- [docs/PROJECT_PLAN.md](docs/PROJECT_PLAN.md) — phased roadmap (what's built vs. planned)
- [docs/player.md](docs/player.md) — player domain: attributes, derived skills, formulas
- [docs/DECISIONS.md](docs/DECISIONS.md) — architecture decision log
- [docs/RISKS.md](docs/RISKS.md) — active risks and concerns
- [docs/TODO.md](docs/TODO.md) — tactical task list

## Tech stack

Java 21 · Spring Boot 3.5 · Maven (multi-module) · OpenAPI (delegate pattern) ·
Liquibase · PostgreSQL (local) / H2 (tests) · React + Vite (frontend).

## CI

Pull requests to `main` run `mvn clean install` (tests + JaCoCo coverage gate)
via GitHub Actions.
