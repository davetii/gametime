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

Start here: [docs/roadmap.md](docs/roadmap.md) (what's built vs. planned) and
[docs/decisions.md](docs/decisions.md) (architecture decision log).

The domain design docs (player, roster, coach, game …) and the full doc index
live in [CLAUDE.md](CLAUDE.md).

## Issue tracking (beads)

Work items live in [beads](https://github.com/gastownhall/beads) (`bd`), an issue
tracker that sits in the repo — not in markdown TODO lists. The general command guide is
[.beads/README.md](.beads/README.md); this section is how *this* project uses it.

**Setup** — `brew install beads`, then `bd bootstrap` on a fresh clone. The database
(`.beads/embeddeddolt/`) is gitignored; `.beads/issues.jsonl` is the git-tracked copy
and is rewritten after every `bd` write — commit it along with the work.

**Conventions**

- **Chores** are open issues (`-t chore`). **Ideas** are parked:
  `-t feature -s deferred -p 4 -l idea`, so they stay out of `bd ready`.
  `bd list --status deferred` shows them.
- **Dependency vs priority** — a dependency means *can't start yet* (the blocked issue
  leaves `bd ready`); priority means *do this first* (0 highest). If doing B first would
  make B wrong or wasted, B depends on A; if it's only *better* to do A first, use priority.
  `-t related` links without blocking.
- **The bead is the only record** of its work. Docs cite the id (`gametime-xxx`); they
  never copy the description.
- Record *why* an order matters with `bd note <id> "..."` rather than naming blockers in
  a description — the dependency edges already record *what* blocks what.
- Put the phase **name** in an engine issue's title, not just `§3.N` — sub-phase numbers
  have been reused.

**Writing descriptions**

- Anything longer than a line: write it in `scrap.md` (repo root, gitignored — a
  reusable scratch page), then `bd create ... --body-file ./scrap.md` or
  `bd update <id> --body-file ./scrap.md`. Pasting multi-line `-d "..."` text into a
  terminal is fragile.
- Re-read `scrap.md` before each use, so the last issue's text doesn't ride along.
- No backticks inside a double-quoted `-d "..."` — the shell runs them as a command.
- Check the result with `bd show <id>`; use the id `bd create` printed.

**Everyday commands** — `bd ready` · `bd show <id>` · `bd update <id> --claim` ·
`bd close <id> "reason"` · `bd dep add <blocked> <blocker>` · `bd note <id> "..."` ·
`bd human` (the essentials).

## Tech stack

Java 21 · Spring Boot 3.5 · Maven (multi-module) · OpenAPI (delegate pattern) ·
Liquibase · PostgreSQL (local) / H2 (tests) · React + Vite (frontend).

## CI

Pull requests to `main` run `mvn clean install` (tests + JaCoCo coverage gate)
via GitHub Actions.
