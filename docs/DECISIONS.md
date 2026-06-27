# Architecture Decisions

Log of significant design and architecture decisions. Append new entries at the bottom.

---

### 001 — Multi-module Maven structure
**Date**: 2026-05  
**Decision**: Split gametime-service into `gametime-api` (generated stubs) and `gametime-app` (hand-written code).  
**Rationale**: Keeps generated OpenAPI code isolated from application logic. The api module is a pure build artifact — no hand-written code lives there. The app module depends on it as a Maven dependency.  
**Alternatives considered**: Single module with generated sources in target/ (simpler but generated and hand-written code mixed in IDE).

### 002 — OpenAPI delegate pattern
**Date**: 2026-05  
**Decision**: Use `delegatePattern=true` in openapi-generator-maven-plugin.  
**Rationale**: Generated controller delegates to a hand-written `V1ApiDelegate` implementation. This means we never touch generated controller code — the delegate is the integration point.

### 003 — Postgres for local dev, H2 for tests
**Date**: 2026-05  
**Decision**: Local development runs against Postgres in Docker. `mvn test` uses H2 in-memory with no Docker dependency.  
**Rationale**: Postgres for local dev gives realistic behavior (triggers, schemas, data types). H2 for tests keeps the test suite fast and CI-friendly with zero infrastructure.  
**Trade-off**: Liquibase changesets must be dual-compatible. Postgres-specific SQL gated with `dbms:postgresql`.

### 004 — Dedicated gametime schema
**Date**: 2026-05  
**Decision**: All application tables live in the `gametime` schema, not `public`.  
**Rationale**: Clean separation from system tables. H2 supports schemas, so this works in both environments.

### 005 — JDK 21 LTS via SDKMAN
**Date**: 2026-05  
**Decision**: Target Java 21 (SDKMAN: `21.0.9-tem`). Do not use Homebrew JDK 25.  
**Rationale**: Lombok 1.18.x is incompatible with JDK 25. JDK 21 is the current LTS release. All Maven commands must set `JAVA_HOME` explicitly.

### 006 — Attribute-driven skill calculation
**Date**: 2026-05 (pre-existing design)  
**Decision**: 16 raw player attributes feed into 13 derived skill calculations via weighted formulas with threshold bonuses/penalties.  
**Rationale**: Attributes are the "DNA" of a player — stable, slowly changing. Skills are the observable output — what a player can actually do on the court. This two-tier model allows the same attribute profile to produce different skill outcomes based on formula tuning.

### 007 — Cucumber on JUnit 5 Platform
**Date**: 2026-05  
**Decision**: Migrated from JUnit 4 vintage to JUnit 5 Platform (`@Suite` + `cucumber-junit-platform-engine`).  
**Rationale**: JUnit 4 vintage engine is deprecated. JUnit 5 Platform is the standard runner. Removed junit-vintage-engine dependency.

### 008 — Attribute scale: 1–20, average = 10
**Date**: 2026-06  
**Decision**: Canonical player attribute scale is **1–20, with 10 = league average**. Applies to all 21 attributes (16 original + 5 new). The existing 16 attributes were rescaled to mean ~10 / stdev ~3.5 via a distribution-based (rank → inverse-normal) transform.  
**Rationale**: The original 1–10 scale was too coarse to separate ~420 players — values compressed around 5–6, and stars had to be hand-bumped past 10 (the data had drifted to a nominal 1–15 with most values still in 3–8). The real fix was widening the *distribution*, not just the ceiling. Centering average at 10 leaves deliberate headroom for genuine outliers (stars 16–18, generational 19–20) and below-average players (3–5), and matches how rating systems keep "average" well below max.  
**Trade-off**: The existing 13 skill calculators' threshold branches (`>9`, `>7`, etc.) were written against the old ~1–10 range and must be re-tuned for 1–20, or they mis-fire (an "above average" bonus now triggers for most of the league).  
**Alternatives considered**: 1–15 (keep current data — marginal variability gain); 1–100 (false precision, can't meaningfully assign across 420 hand-curated players); linear shift+stretch instead of distribution-based (preserves exact gaps but amplifies existing data artifacts).

### 009 — Player data is CSV-driven
**Date**: 2026-06  
**Decision**: Player seed data is the source-of-truth CSV `db/players.csv` (422 players), loaded via a Liquibase `loadData` changeset. Replaced the ~420 inline SQL `INSERT` statements previously in `release.1.0.1.dataload.sql`.  
**Rationale**: A CSV is far easier to inspect, sort, bulk-transform (rescale/re-center), and eyeball for outliers than 420 single-line inserts. Follows the existing `gm.csv` / `loadData` precedent. The old `player.csv` had drifted stale (421 rows, missing a player); the canonical CSV was regenerated from the authoritative SQL. Team inserts stay in SQL (FK parent — must load before players).  
**Trade-off**: Schema changes now require both a migration (add column) and a matching CSV column; load order in `changelog.yml` must keep teams → add-columns → players. Existing dev DBs need `docker compose down -v` to pick up schema changes.  
**Alternatives considered**: Keep editing inline SQL (error-prone at 420 rows); append columns to the stale `player.csv` (would bake in the old compressed distribution and the missing player).

### 010 — Dropped fetchConference endpoint
**Date**: 2026-06  
**Decision**: Removed `GET /v1/conference/{confId}` (`fetchConference`) from the OpenAPI spec. Clients that want a conference view filter the `GET /v1/league` response by `Team.conference` themselves.  
**Rationale**: The endpoint's spec was already inconsistent (declared a single `Team` response while its summary promised a list), and conference grouping is a trivial client-side filter over the full-league payload the client already fetches. Dropping it removes a redundant server endpoint rather than fixing a half-specified one.  
**Trade-off**: If the league grows large enough that returning all 40 teams to filter for 10 is wasteful, a server-side filtered endpoint can be reintroduced. Acceptable at current scale.

### 011 — Player→team link: nullable team_id, decoupled from Team entity (SUPERSEDED by #012)
**Date**: 2026-06  
**Superseded**: The interim step — a plain nullable `player.team_id` FK — was replaced by the `player_team` / `player_team_hist` model in #012 before it shipped. Kept here for the trail. The original reasoning (separate create/assign endpoints require a "created but unassigned" state; the player aggregate should not hold a `Team` object reference) carried forward into #012.

### 012 — Player↔team is a join model with history (player_team + player_team_hist)
**Date**: 2026-06  
**Decision**: The player→team relationship is fully decoupled from both the player and team entities and modeled as two tables:
- `player_team` — **current** roster assignment. `player_id` is the PK (at most one team per player); columns `team_id`, `transaction_type`, `assigned_date`. A **free agent has no row**.
- `player_team_hist` — **append-only** history of roster transactions (assignments only). One row per move; never updated or deleted.

`player.team_id` was removed entirely. `TeamEntity` no longer has a JPA `players` association — the service sources a team's roster via `PlayerTeamRepo.findByTeamId` and `EntityMapper.entityToTeam(entity, roster)` composes it. In the API, `Player` exposes a read-only `currentTeamId` (derived, nullable), and `GET /v1/player/{playerId}/history` returns the `PlayerTransaction` list (most-recent-first). `createPlayer` makes a free agent; `addPlayerToTeam` inserts the `player_team` row + appends a `player_team_hist` row (type `SIGN`), 409 if already assigned. Seed roster is loaded from `roster.csv` into `player_team` with type `SEED`; history starts empty.  
**Rationale**: Trades and free agency mean a player moves between teams over time, and the game needs that history as first-class data — which a single current-state FK cannot represent. Splitting current state (`player_team`) from the immutable transaction log (`player_team_hist`) keeps "who is on this team now" a cheap PK lookup while preserving the full trail.  
**Trade-off**: Two-table writes per assignment (kept atomic in one `@Transactional` service method). Roster reads are an extra query (player_team → players) rather than a JPA join. Since there are no users/data yet, the migrations were **consolidated from scratch** into `release.1.0.1.sql` (player table created without team_id; `player_team`/`player_team_hist` part of the base schema) rather than layered as patch migrations — the interim `release.1.0.2/1.0.3/1.0.4` SQL patches were removed.  
**Alternatives considered**: Single nullable `team_id` FK (#011 — no history); a normalized `transaction` table modeling multi-player trades as one event (deferred — not needed until trades exist); season-scoped stints (no season/calendar model exists yet).

---

*Template for new entries:*
```
### NNN — Short title
**Date**: YYYY-MM  
**Decision**: What was decided.  
**Rationale**: Why this choice was made.  
**Alternatives considered**: What else was evaluated (optional).
```
