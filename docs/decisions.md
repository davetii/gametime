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

### 013 — Player status (availability) split from lineup role (team slot)
**Date**: 2026-06  
**Decision**: Two previously-overlapping concepts are now separate enums with no shared values:
- `Player.status` — player-**intrinsic availability**, independent of any team: `ACTIVE, INJURED, SUSPENDED`.
- `player_team.lineupRole` — the player's **slot on a specific team**: `STARTER, ROTATION, BENCH, INACTIVE, MINORS`.

Roster membership ("on a team or not") stays **derived from `player_team`** (no row = free agent) — it is *not* encoded in status (no `UNSIGNED`). `MINORS` moved out of status into `lineupRole` (it's a roster placement, not an availability state). The old `Status` enum (`STARTER, BENCH, ROTATION, MINORS, INJURED, SUSPENDED`) conflated both axes; the seed `players.csv` had been using `status` to carry lineup intent (200 STARTER / 143 ROTATION / 79 BENCH). That intent was migrated into `player_team.lineup_role` (seeded via `roster.csv`) and all player statuses set to `ACTIVE`.  
**Rationale**: One field answering two questions ("is this player available?" and "what's their lineup slot?") is a duplicate source of truth waiting to disagree. Availability is a fact about the person; lineup slot is a fact about the assignment. Separating them removes the four-value collision and gives each a single owner.  
**Alternatives considered**: Add `UNSIGNED` to status to mirror roster membership (rejected — second source of truth vs. `player_team`); keep `MINORS` in status (rejected — it's a roster concept).

### 014 — Lineup is sticky persistent state; rotationOrder is the bench queue
**Date**: 2026-06  
**Decision**: `PUT /v1/team/{teamId}/lineup` is **replace-all** and sets persistent state on `player_team` (`lineup_role` + `rotation_order`). It is **set-on-change**, not called per game: once set, the lineup persists until explicitly changed, and every team boots with a valid 5-starter lineup from the seed. In-game substitutions are **transient game-simulation state** (Phase 3) and never write back to `player_team`. Validation: exactly 5 `STARTER`; no duplicate players; all players must be on the team (else 404). `rotationOrder` semantics: **starters are an unordered set of 5 and carry no rotation order**; `rotationOrder` is the **bench substitution queue** (first off the bench = 1, then 2, 3…), and must be unique among all non-null values.  
**Rationale**: The coach's lineup is a standing plan, not a per-game action — modeling it as sticky state means unchanged rotations need no API call, and the engine simply reads it. Forcing starters to carry an order would fabricate data the engine doesn't need (all 5 are on the floor at tip-off); ordering only has a real meaning for the bench sub queue.  
**Trade-off**: rotationOrder has no consumer until the Phase 3 engine exists — it is correct-but-latent plumbing laid ahead of its use.  
**Alternatives considered**: Per-game lineup submission (rejected — churns roster state every game); a full 1→N depth chart including starters (deferred — additive if the engine later needs starter ordering); starters all sharing `rotationOrder = 1` (rejected — not-null but not-distinct, the worst of both).

### 015 — Team is the single source of roster state; `/roster` endpoint removed
**Date**: 2026-06  
**Decision**: `GET /v1/team/{teamId}` is the **one** complete view of a team's state with respect to its players. `Team.players` is now a list of `RosterEntry` (`player` + `lineupRole` + `rotationOrder`) rather than bare `Player`. The separate `GET /v1/team/{teamId}/roster` endpoint and the `Roster` schema were **removed**; `PUT /lineup` now returns the updated `Team`. `GET /v1/league` likewise exposes lineup slots per player.  
**Rationale**: The two read endpoints each carried half of "a team's state w.r.t. players" — `/team/{id}` had team context but no lineup, `/roster` had lineup but no team context — forcing clients to call both and stitch. A player's lineup slot is part of the team's state; there should be one endpoint. (Same overload-elimination principle as #013.)  
**Trade-off**: `GET /team` and `GET /league` now hydrate lineup fields for every player even when a caller only wanted names; trivial at 40 teams, and a lean projection can be added later if it ever matters. `PUT /lineup` and `DELETE /{playerId}` are unchanged (they are actions, not redundant reads).  
**Alternatives considered**: Keep both as summary vs. detail tiers (rejected — two endpoints to keep coherent, no real consumer for the split); fold team context into `/roster` instead (rejected — `Team` is the more fundamental resource).

### 016 — Roster size caps; signed players default to `INACTIVE`
**Date**: 2026-06  
**Decision**: A team's roster is capped at **15 active** (every `lineupRole` except `MINORS`) + **5 minors**. Signing a free agent (`POST /v1/team/{teamId}/{playerId}`) now defaults the new `player_team.lineupRole` to **`INACTIVE`** ("on the active roster, not yet slotted") and rejects with **409** when the active roster is already full. The lineup PUT also re-checks both caps over the *resulting* roster (request overlaid on current, omitted players keep their role) and rejects with **400** if active > 15 or minors > 5. Caps live as constants `MAX_ACTIVE_ROSTER` / `MAX_MINORS` in `GametimeServiceImp`.  
**Rationale**: The sign endpoint had no role to set, leaving a just-signed player in a fourth, null-bucket state that size limits couldn't count. Defaulting to `INACTIVE` (chosen over a new `RESERVE` value or splitting `lineupRole` into a separate `rosterStatus` field) eliminates the null without reversing #013/#014's single-field model. The seed data made this safe with no migration: all 422 `roster.csv` rows already carry a bucket (zero nulls), `INACTIVE` was referenced nowhere but the enum decl, and every seeded team is 9–12 players — under the 15 cap, so no grandfathering. The active cap is enforced at *both* sign and lineup PUT so role shuffles can't grow the active roster past what signing allows.  
**Trade-off**: `INACTIVE` now carries a slight double meaning — "just signed, unslotted" and the eventual in-game "dressed but not playing"; acceptable since the latter has no consumer yet. Position min/max is still unbuilt (deferred to the lineup PUT where the full assignment is visible).  
**Alternatives considered**: Add a dedicated `RESERVE` lineupRole (rejected — extra enum value for a state `INACTIVE` already covers); split `lineupRole` into membership-tier + in-game-slot fields (rejected — partially reverses #013/#014 and still needs a sign-time default); treat null as a first-class "unassigned" state (rejected — pushes null-handling into every count + the engine).

### 017 — No position min/max roster rules (won't build)
**Date**: 2026-06  
**Decision**: Roster construction is **not** constrained by player position. There are no per-position (or position-group) minimums or maximums on a roster or lineup. A team may carry any positional mix it likes.  
**Rationale**: Two reasons. (1) The seed data can't support minimums — no team carries all 9 positions (each has 6–8 of 9), and CG/WING are thin league-wide, so any per-position floor would invalidate most seeded rosters on day one (same trap avoided in #016). (2) Maximums solve a problem the game engine should own: a lopsided roster (e.g. 10 centers) is naturally punished by losing — poor guard play, no spacing — rather than by an API validation rule. Pre-empting that with a roster-layer cap fabricates a constraint ahead of any consumer, against the "let the engine decide" principle (cf. #014 deferring rotationOrder to the engine).  
**Trade-off**: A user *can* build a nonsensical roster. Accepted — it's their loss in simulation, and it keeps the roster API free of balance rules that belong in gameplay.  
**Revisit if**: the game engine ships and playtesting shows unconstrained rosters produce degenerate or unfun results that the simulation alone doesn't correct — then position rules become an engine-informed feature, not a guessed-at one.

---

*Template for new entries:*
```
### NNN — Short title
**Date**: YYYY-MM  
**Decision**: What was decided.  
**Rationale**: Why this choice was made.  
**Alternatives considered**: What else was evaluated (optional).
```
