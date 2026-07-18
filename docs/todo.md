# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**§3.x Deferred sim-fidelity details**.

Current focus: **Phase 3.6 — Simulation APIs** (roadmap.md §3.6). §3.1–§3.5 are
shipped: the engine simulates a full game end-to-end (dynamic rotation, fatigue,
foul-outs, real minutes) and persists `Game` / `GameEvent` / `BoxScore` — but there
is **no API surface** yet. §3.6 exposes the game engine over REST.

> **Design is DONE — this is the execution plan.** The six §3.6 decisions are
> resolved and recorded as **decisions.md #024** (A–F). Work the tasks below **in
> order**; no new decisions should be needed. If one is, #024 under-delivered —
> stop and resolve it with the user, don't guess.

---

## What #024 decided (the one-screen summary — read before task 1)

| # | Decision | Net effect on the code |
|---|----------|------------------------|
| A | Shared **`GameResult`** = `game` + `homeBoxScore` + `awayBoxScore` (team-split server-side); same shape for `POST /simulate` and `GET /game/{gameId}`. No per-period columns. Event list NOT in it. | New OpenAPI schemas; mapper must split box scores by team via `player_team`. |
| B | **Seed**: optional request input (random default), **persisted** on `Game`, echoed in the response header. | **The one schema change** — `game.seed BIGINT`, appended to `release.1.0.4.game.sql`. |
| C | **Pace** (`possessionsPerPeriod`) **not exposed** — always `SimConfig.DEFAULT_POSSESSIONS_PER_PERIOD`. | Nothing to build; do NOT add it to the request. |
| D | **Play-by-play** = plain ordered `[GameEvent]`, **no pagination**. Surfaces `assistPlayerId`. | Reuse `findByGameIdOrderBySequenceAsc` as-is. Orphaned `pageNumber`/`pageSize` stay orphaned. |
| E | **Event time**: no field, no column — derived on read; revisit at Phase 7. | **No schema/time work.** Leaves `game.seed` the ONLY schema change. |
| F | **Errors**: 404 unknown game/team; **new 422** (`ResourceUnprocessableException`) for `homeTeamId == awayTeamId`; empty bodies. | New exception + `ApiExceptionHandler` mapping. |

**Scope fence (what §3.6 is NOT):** no Phase-4 stats aggregation (season totals/
leaders/rankings are Phase 4); no new gameplay/engine logic (the engine is done
through §3.5); no frontend. §3.6 is **API + read-projection + entity→model mapping
only.** No pagination, no time column, no pace knob (all deferred per #024 C/D/E).

**The structural crux (plan it once):** there is **NO entity→model mapper for
Game/GameEvent/BoxScore today** — `mapper/EntityMapper.java` maps player/team/coach
and does not touch the game entities. Building that mapping layer is the real chunk
of net-new code (the analog of `EntityMapper.entityToTeam`), and Decision A's
home/away box-score split lives inside it (resolve each `box_score` row's team via
`player_team`, since the row has no `team_id` — #020).

**Copy-me reference (a read + a multi-endpoint write):**
- **Read endpoint end-to-end:** `fetchTeam` — yaml op (`gametime.yaml` ~line 34) →
  `V1ApiDelegateimpl.fetchTeam` → `GametimeServiceImp.getTeam` →
  `EntityMapper.entityToTeam`. Copy this shape for `GET /game/{gameId}`.
- **Adding several endpoints in one diff:** `git show c4b6fd4` (roster + lineup API —
  `PUT`+`POST`+`DELETE` with schemas, delegate impls, and Cucumber features together).
  NOT `cb232d5` (§3.4 — added no endpoint, OpenAPI was deferred to §3.6).

**Blast radius / what re-baselines:** adding the first game endpoints should NOT
break existing tests. The `game.seed` column (Decision B) is the one schema change —
existing dev Postgres DBs need `docker compose down -v` to pick it up (#009); H2
tests rebuild fresh each run. Persisting the seed shifts nothing in the seeded RNG
stream (the seed value is just recorded, not changed).

---

## Verified facts (confirmed against the code — paths under `gametime-service/`)

Trust these, re-check only if the code moved since 2026-07.

**OpenAPI / delegate machinery:**
- **Spec (source of truth):** `gametime-api/yml/gametime.yaml`. `paths:` then
  `components:` → `parameters:` (~line 198) → `schemas:` (~line 237). **ZERO game
  endpoints today** — §3.6 adds the first. Templates: `fetchTeam` op (~line 34) +
  `Team` schema; path params `$ref`'d from `components/parameters`. Generated stubs
  land in `gametime-api/target/` — **never hand-edit generated code** (#001/#002).
- **Delegate impl (single file):** `gametime-app/.../api/V1ApiDelegateimpl.java`
  (lowercase `impl` — match it). `implements V1ApiDelegate`, constructor-injected
  with `GametimeService`; each op is a thin `@Override` returning
  `ResponseEntity.ok(service.xxx(...))`. `fetchTeam` calls
  `service.getTeam(id).orElseThrow(ResourceNotFoundException::new)`.
- **Error handling:** `gametime-app/.../api/ApiExceptionHandler.java`
  (`@RestControllerAdvice`) maps `ResourceNotFoundException → 404`,
  `ResourceConflictException → 409`, `ResourceBadRequestException → 400`, all
  `.build()` (empty body). Exceptions live in `gametime-app/.../exception/`.
- **Orphaned pagination params** (#019): `pageNumberParam` (default 1) /
  `pageSizeParam` (default 25) in `components/parameters` (~line 200) — **wired to
  nothing; leave them so per Decision D.**

**Where the game data lives:**
- **Entities** (`gametime-app/.../entity/`): `GameEntity` (`id`, `homeTeamId`,
  `awayTeamId`, `status: GameStatus`, `homeScore`, `awayScore`, `periods` — **no
  per-period scores, no `seed` yet**), `GameEventEntity` (`id`, `gameId`, `sequence`,
  `period`, `offenseTeamId`, `defenseTeamId`, `playType: PlayType`, `outcome` (free
  text), `primaryPlayerId`, `assistPlayerId` (nullable — §3.4)), `BoxScoreEntity`
  (`id`, `gameId`, `playerId`, then the stat counters incl. `minutes`, `fouls`;
  **no `team_id`** — #020).
- **Repos** (`gametime-app/.../repo/`): `GameRepo extends CrudRepository` (`findById`),
  `GameEventRepo.findByGameIdOrderBySequenceAsc(gameId)` → `List` (**use as-is**;
  no `Pageable` needed per Decision D), `BoxScoreRepo.findByGameId(gameId)`.
- **⚠️ NO entity→model mapper for the game entities.** `mapper/EntityMapper.java`
  maps player/team/coach only. §3.6 builds the Game/GameEvent/BoxScore mapping (the
  crux above).
- **Engine entry point:** `sim/GameSimulator.simulate(String homeTeamId, String
  awayTeamId, long seed, int possessionsPerPeriod)` → `sim/SimResult` (`gameId`,
  `homeTeamId`, `awayTeamId`, `homeScore`, `awayScore`, `periods`, `totalEvents` —
  **no box score**). `@Transactional`, persists everything (#021 E). It throws
  `ResourceNotFoundException` on an unknown team id (lines ~40/42) — Decision F's 404
  is free. `SimConfig.DEFAULT_POSSESSIONS_PER_PERIOD = 25`, `PERIODS = 4`.

**Schema / migration:**
- `gametime-app/src/main/resources/db/release.1.0.4.game.sql` — created
  `game`/`game_event`/`box_score` (incl. `box_score.minutes`, `box_score.fouls`,
  `game_event.assist_player_id`). **Still unreleased** → **append the `seed` column
  here, do NOT cut a `release.1.0.5`** (memory note). Wired into `changelog.yml` at
  line 28 (last include). Load order matters (#009); gate Postgres-only SQL with
  `dbms:postgresql`.

**Test patterns:**
- **Cucumber:** `gametime-app/src/test/resources/features/*.feature` (e.g.
  `fetch-team.feature`) + step defs; JUnit 5 Platform `@Suite` (#007).
- **Integration:** `@SpringBootTest`/H2 style from `GameSimulatorIntegrationTest`
  (already exercises `simulate()` + the repos).
- **Coverage gate:** 80% line **per package** (target ~90%), only shown at
  `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install`.
  The new `api` delegate code + the new mapper package need tests.

---

# ===== SESSION 3 EXECUTION PLAN (work in order) =====

### 1. Schema — the seed column (Decision B)
- [ ] Append a changeset to
  `gametime-app/src/main/resources/db/release.1.0.4.game.sql` adding
  `game.seed BIGINT` (nullable is fine — every engine-produced game sets it).
  **Do NOT create `release.1.0.5`.** Respect the existing changeset/FK order; no
  `dbms:postgresql` gate needed for a plain column add (add one only if you use
  Postgres-specific syntax).
- [ ] Add `private Long seed;` (`@Column(name = "seed")`) to
  `gametime-app/.../entity/GameEntity.java`.
- [ ] Populate it in `sim/GameSimulator.simulate(...)` at persist time — the method
  already has the `long seed`; write it onto the `GameEntity` before save.

### 2. OpenAPI schemas + operations (Decision A, D, F) — `gametime-api/yml/gametime.yaml`
- [ ] **Schemas** (under `components/schemas`, copy `Team`'s object/`$ref` style):
  - `Game` — `id`, `homeTeamId`, `awayTeamId`, `status`, `homeScore`, `awayScore`,
    `periods`, `seed`. (No per-period scores — Decision A.)
  - `BoxScore` — `playerId` + the stat fields from `BoxScoreEntity` (points, off/def
    rebounds, assists, steals, blocks, turnovers, fouls, minutes, FGA/FGM, 3PA/3PM,
    FTA/FTM). No `teamId` (#020).
  - `GameEvent` — `sequence`, `period`, `offenseTeamId`, `defenseTeamId`, `playType`,
    `outcome`, `primaryPlayerId`, **`assistPlayerId`** (Decision D surfaces #022's
    column). No time field (Decision E).
  - `GameResult` — `game: Game`, `homeBoxScore: [BoxScore]`, `awayBoxScore: [BoxScore]`
    (Decision A).
  - `SimulateGameRequest` — `homeTeamId` (req), `awayTeamId` (req), `seed` (optional
    int64) (Decision B). **Do NOT add `possessionsPerPeriod`** (Decision C).
- [ ] **Operations** (copy `fetchTeam`'s op shape; `$ref` path params from
  `components/parameters`, add a `gameIdPathParam` alongside `teamIdPathParam`):
  - `POST /v1/game/simulate` — body `SimulateGameRequest` → `200 GameResult`,
    `404` (unknown team), `422` (same-team) (Decision F).
  - `GET /v1/game/{gameId}` → `200 GameResult`, `404`.
  - `GET /v1/game/{gameId}/play-by-play` → `200` array of `GameEvent`, `404`.
    **No `pageNumber`/`pageSize` params** (Decision D).
- [ ] Regenerate stubs:
  `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml -pl gametime-api compile`.

### 3. Error type for 422 (Decision F)
- [ ] Add `gametime-app/.../exception/ResourceUnprocessableException.java` (mirror
  `ResourceNotFoundException` — `extends RuntimeException`).
- [ ] Add the mapping in `gametime-app/.../api/ApiExceptionHandler.java`:
  `@ExceptionHandler(ResourceUnprocessableException.class)` →
  `ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build()` (empty body,
  matching the others).

### 4. Entity→model mapper — THE CRUX (Decision A)
- [ ] Build the Game/GameEvent/BoxScore mapping (extend `mapper/EntityMapper.java`
  or add a focused `GameMapper` in the same package — match the existing style):
  - `GameEntity → Game`, `GameEventEntity → GameEvent` (incl. `assistPlayerId`),
    `BoxScoreEntity → BoxScore`.
  - **The home/away split — use `PlayerTeamRepo.findByTeamId`, NOT a per-row lookup.**
    ⚠️ `PlayerTeamRepo` has **`findByTeamId(teamId)` only — there is NO
    `findByPlayerId`**, so "resolve each box-score row's team" is not directly
    available. The clean shape: fetch the home roster once —
    `Set<String> homePlayerIds = findByTeamId(game.getHomeTeamId())` mapped to
    player ids — then bucket each `BoxScore` row into `homeBoxScore` if its
    `playerId` is in that set, else `awayBoxScore`. (Row has no `team_id` — #020.)
    This resolves the split with **one repo call**, not one per row. This lookup is
    the net-new work — do it in the mapper/service assembly, not the delegate.

### 5. Service methods (Decision A, B, F) — `service/GametimeService.java` (interface) + `service/GametimeServiceImp.java` (impl)
> **`GametimeService` is an INTERFACE** (declares `getPlayer`/`getTeam`; impl in
> `GametimeServiceImp`). Each new method below needs **both** a declaration in the
> interface **and** an implementation in the Imp — the `getTeam` pattern.
> **Wiring:** `GametimeServiceImp` does **not** inject `GameSimulator` today — add it
> as a **constructor-injected dependency** (it's a `sim`-package `@Service`).
> `PlayerTeamRepo` is likewise needed for the split (task 4) — inject it too if not
> already present.
- [ ] `simulateGame(homeTeamId, awayTeamId, seedOrNull) → GameResult`:
  - Validate `homeTeamId != awayTeamId` → else throw
    `ResourceUnprocessableException` (Decision F, 422).
  - Resolve the seed: use the provided one, else generate a fresh random `long`
    (e.g. `new SecureRandom().nextLong()` or `ThreadLocalRandom` — a fresh value per
    call, #021 A). The engine writes it onto `GameEntity` (task 1).
  - Call `gameSimulator.simulate(homeTeamId, awayTeamId, seed,
    SimConfig.DEFAULT_POSSESSIONS_PER_PERIOD)` (Decision C). The engine takes **string
    ids** and **already throws `ResourceNotFoundException`** on an unknown team
    (→ 404, free — no pre-check needed). It returns a `SimResult` (gameId + score,
    **no box score**), so assemble the `GameResult` by reading the persisted rows
    back via `getGame(simResult.getGameId())`.
- [ ] `getGame(gameId) → GameResult` — `GameRepo.findById(gameId)`
  `.orElseThrow(ResourceNotFoundException::new)` (404); then
  `BoxScoreRepo.findByGameId` + the task-4 split + mapper → `GameResult`.
- [ ] `getPlayByPlay(gameId) → List<GameEvent>` — verify the game exists first
  (`GameRepo.findById...orElseThrow` → 404), then
  `GameEventRepo.findByGameIdOrderBySequenceAsc(gameId)` + map (Decision D).

### 6. Delegate impls (thin) — `api/V1ApiDelegateimpl.java`
- [ ] Three `@Override`s delegating to the service (the `fetchTeam` pattern):
  `simulateGame` → `ResponseEntity.ok(service.simulateGame(...))`; the two gets
  likewise. No logic in the delegate — validation/mapping live in the service/mapper.

### 7. Tests (target ~90% per package)
- [ ] **Cucumber features** (copy `fetch-team.feature`) + step defs:
  simulate → 200 + `GameResult`; get game → 200/404; play-by-play → 200 (ordered)
  /404; same-team → **422**; unknown team on simulate → **404**.
- [ ] **Mapper unit tests** — the home/away split (right players in the right
  bucket), `assistPlayerId` mapping, all three entity→model maps.
- [ ] **Service/delegate tests** — seed resolution (provided vs. generated),
  same-team guard, box-score reconciliation against the event log (extend the
  existing #020/#022 reconciliation), play-by-play `sequence` ordering.
- [ ] Cover the new `api` delegate code, the new/extended mapper package, and the
  new exception.

### 8. Gate
- [ ] `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install`
  green (per-package coverage ≥80%, target ~90%).

### 9. Docs + close-out
- [ ] Fill in game.md's API section (the three ops + `GameResult`/`SimulateGameRequest`
  shapes) so it matches what shipped.
- [ ] Verify #024 matches the shipped code (adjust the entry if execution diverged —
  the way #023 carried an "implementation note").
- [ ] roadmap.md §3.6: check the four boxes + add a "Shipped" note (mirror §3.5's).
  Note the time-column bullet closed as *derived-on-read* (#024 E), not built.
- [ ] Reset this file's focus to **Phase 4 — Statistics & Box Scores**; strip the
  completed §3.6 content (keep the "Where deferred work lives" footer + the JDK/gate
  reminders).

---

> All Maven commands set `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
> (JDK 21 — decisions.md #005; Homebrew JDK 25 breaks Lombok). Per-package coverage
> gate only shows at `mvn -f gametime-service/pom.xml clean install`.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md).
- **Deferred gameplay realism** (OOB-no-rebound, rebounding fouls, and-1, richer
  turnovers, blocked shots) → roadmap.md's **§3.x Deferred sim-fidelity details**.
- **Untriaged future-improvement ideas** (no phase home, not chores) →
  [ideas.md](ideas.md).
- **§3.6 seams left open by #024** (carry to Phase 4/7): play-by-play pagination +
  a `period` filter (Decision D); the per-event time column's storage shape
  (Decision E — decided by the Phase 7 game view); a lean header-only `GameResult`
  projection (Decision A).
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true`) stays in the `sim` test sources; it reports the §3.4
  aggregates + the §3.5 minutes/period-FG% distributions. Re-run it after any
  `SimConfig` change.
