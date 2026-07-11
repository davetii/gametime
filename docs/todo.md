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

> ### ⚠️ §3.6 is being run as THREE sessions — read this first
>
> This todo is written for a **cold session** at each stage. Know which you are:
>
> - **Session 1 (planning-the-planning — DONE):** produced this briefing. It did the
>   code archaeology so Session 2 doesn't start blind. Everything below the
>   "SESSION 2 BRIEFING" header is its output.
> - **Session 2 (analysis / design):** resolves the open decisions **with the user**,
>   turns them into a decisions.md **#024** entry, and rewrites the "SESSION 3
>   EXECUTION PLAN" section below into an execute-ready, decisions-resolved task
>   sequence — the way §3.5's todo listed 13 concrete ticked-off tasks. **Session 2
>   writes a spec; it does not write production code.** Its deliverable is: (a) #024
>   drafted/agreed, (b) the execution plan below made concrete, (c) any remaining
>   "verified facts" gaps it discovers filled in.
> - **Session 3 (execution):** works the Session-2 task sequence in order, writes the
>   code + tests, clears the coverage gate, closes out the docs. It should need **no**
>   new decisions — if it does, Session 2 under-delivered.
>
> **Session 2's success test:** a Session 3 that has *only* this file + the repo can
> finish §3.6 without asking the user anything.

---

# ===== SESSION 2 BRIEFING (analysis session — start here) =====

## Your charter (Session 2)

Produce the design + execute-ready plan for §3.6. Concretely:
1. Resolve the **Open decisions** (below) *with the user* — do not guess; these are
   genuine product/architecture calls (some reverse earlier decisions).
2. Write them up as **decisions.md #024**, mirroring the shape of #021/#022/#023
   (Decision A/B/C… + Rationale + Trade-off + Alternatives + Status).
3. Rewrite the **SESSION 3 EXECUTION PLAN** below into a concrete, ordered,
   `[ ]`-tickable task list with file paths — the standard §3.5's todo set.
4. Update docs that describe the model *design* (game.md API section stub, coach/
   player if touched) so Session 3 only writes code + close-out.
5. Fill any **verified-facts** gap you find — if something below is stale or you had
   to re-derive a fact, add it here so Session 3 doesn't repeat the dig.

**Goal** (roadmap §3.6): expose the persisted game engine over OpenAPI. Everything
§3.6 *reads* already exists in the DB (decisions.md #020 persists every `GameEvent`;
§3.2–§3.5 fill `Game`/`GameEvent`/`BoxScore`) — so §3.6 is **API + read-projection +
entity→model mapping work, not new engine logic.**

Roadmap §3.6 deliverables:
- `POST /v1/game/simulate` — simulate a single game, return the result/box score
- `GET /v1/game/{gameId}` — retrieve a game result
- `GET /v1/game/{gameId}/play-by-play` — the event log (paginated — #019)
- Decide + migrate a stored per-event **time column** for play-by-play display
  (single value vs. range — deferred from §3.2, decisions.md #021)

---

## Verified facts about the current code (confirmed by Session 1 — trust, but re-check if code moved)

Paths are under `gametime-service/` unless noted. **These are what let Session 2/3
skip the archaeology.**

**OpenAPI / delegate machinery (the core of the phase):**
- **API spec (source of truth):** `gametime-api/yml/gametime.yaml` (~516 lines).
  Structure: `paths:` (line ~16) then `components:` (line ~196) → `parameters:` then
  `schemas:` (line ~237). Generated stubs land in `gametime-api/target/` — **never
  hand-edit generated code** (decisions.md #001/#002).
- **There are ZERO game endpoints in the yaml today.** §3.6 adds the *first* `game`
  operations. Existing ops to **copy as templates**: `fetchTeam` (a `GET` with a path
  param → schema response + 404, yaml ~line 34) and the `Team` schema (~line 43,
  object with nested `$ref` arrays). Path params are `$ref`'d from
  `components/parameters` (e.g. `teamIdPathParam`).
- **Delegate pattern:** generated `V1ApiDelegate` interface; hand-written impl is a
  **single file** `gametime-app/src/main/java/software/daveturner/gametime/api/`
  **`V1ApiDelegateimpl.java`** (note the lowercase `impl` — match it exactly).
  It `implements V1ApiDelegate`, is constructor-injected with `GametimeService`, and
  every op is a thin `@Override` returning `ResponseEntity.ok(service.xxx(...))`.
  Example (`fetchTeam`): calls `service.getTeam(id).orElseThrow(ResourceNotFoundException::new)`
  then `ResponseEntity.ok(...)`. §3.6 adds new `@Override`s here for the game ops.
- **Pagination params already exist but are orphaned** (decisions.md #019):
  `pageNumberParam` / `pageSizeParam` in `components/parameters` (~line 5/14), wired
  to nothing. Play-by-play is the intended first consumer — §3.6 `$ref`s them.

**Where the game data lives (what the API projects):**
- **Entities:** `gametime-app/.../entity/GameEntity` (fields: `id`, `homeTeamId`,
  `awayTeamId`, `status` (`GameStatus`), `homeScore`, `awayScore`, `periods` — **note:
  NO per-period/quarter scores, just a period *count* + final scores**),
  `GameEventEntity`, `BoxScoreEntity`.
- **Repos** (`gametime-app/.../repo/`): `GameRepo` (`findById`),
  `GameEventRepo.findByGameIdOrderBySequenceAsc(gameId)` (**no paginated finder
  exists** — §3.6 adds a `Pageable` variant for play-by-play),
  `BoxScoreRepo.findByGameId(gameId)`.
- **⚠️ There is NO entity→model mapper for Game/GameEvent/BoxScore.** `EntityMapper`
  maps player/team/coach but **does not touch the game entities**. §3.6 must build
  that mapping layer (entity → the new OpenAPI models) — a real chunk of work the
  original draft omitted. This is the analog of `entityToTeam`.
- **Engine entry point:** `GameSimulator.simulate(String homeTeamId, String awayTeamId,
  long seed, int possessionsPerPeriod)` returns a **`SimResult`** (`gameId`,
  `homeTeamId`, `awayTeamId`, `homeScore`, `awayScore`, `periods`, `totalEvents`).
  **`SimResult` has NO box score** — so "return the box score" can't reuse it as-is;
  either extend it, add a new response model, or make the client `GET` after simulate.
  `simulate()` is `@Transactional` and persists everything (decisions.md #021 E).

**Schema / migration:**
- `release.1.0.4.game.sql` exists and **IS wired into** `changelog.yml` (line ~28) —
  it created `game`/`game_event`/`box_score` incl. `box_score.minutes`,
  `box_score.fouls`, and `game_event.assist_player_id`. It is **still unreleased**, so
  per the memory note **append new columns here — do NOT cut a `release.1.0.5`**.
- The load-order gotcha (decisions.md #009): `changelog.yml` is an ordered `include`
  list; any new changeset must respect FK parent order. H2/Postgres dual-compat: gate
  Postgres-only SQL with `dbms:postgresql` (see existing `release.1.0.1.sql`).

**Test patterns:**
- **Cucumber features:** `gametime-app/src/test/resources/features/*.feature` (e.g.
  `fetch-team.feature`, `read-player.feature`) + step defs — the pattern for
  endpoint/integration tests. JUnit 5 Platform `@Suite` (decisions.md #007).
- **Delegate unit tests** + the `@SpringBootTest`/H2 integration style from
  `GameSimulatorIntegrationTest` (already exercises `simulate()` + the repos).
- **Coverage gate:** 80% line **per package**, only at
  `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install`.
  Target ~90%. New `api`-package delegate code + a new mapper package need tests.

**Carry-forward follow-ups explicitly deferred TO §3.6 (check these off in #024):**
- **`assist_player_id` in the OpenAPI `GameEvent` model** — the *column* shipped §3.4
  (decisions.md #022 B1) but was never surfaced in the API. Lands with play-by-play.
- **Per-event time column** — §3.2 kept event time *derived on read* (pace + period +
  sequence), **no stored column** (decisions.md #021 B, restated #021 §3.6 follow-up).
  Play-by-play display is its consumer: decide single-value vs. range, then one small
  migration (append to `release.1.0.4.game.sql`) populated by the engine at persist.
  **This is the one likely schema change in §3.6.**
- **`GameStatus.SCHEDULED`/`IN_PROGRESS`** exist (#020) but only `FINAL` is produced.
  Confirm §3.6 needs only `FINAL` (leave the others for Phase 5 scheduling).

---

## Open decisions for Session 2 to resolve WITH THE USER (do not guess)

Each notes what it changes + which earlier decision it touches. Present these to the
user the way §3.5's A–F were presented, then capture as **#024**.

1. **Simulate response shape.** `POST /simulate` returns: (a) just `SimResult`-ish
   (`gameId` + score) and the client `GET`s the box score, (b) the full box score
   inline, or (c) a fuller `GameResult` (game + box scores, no event list). Trade-off:
   payload size vs. round-trips. Touches: new response schema + whether `SimResult`
   is extended or a model is added.
2. **Seed handling.** §3.2 (decisions.md #021 A) mandates a *fresh* seed per real game
   (fixed seed ⇒ identical games) and **did not persist** the seed. Decide: does
   `POST /simulate` accept an *optional* seed (reproducible/testing) defaulting to
   random, or always generate? **Do we now persist the seed on `Game`** (reverses a
   §3.2 non-decision — needs a column, a #024 note, and a reason)? Recommendation to
   weigh: optional seed in, persist it, so a game is reproducible from its record.
3. **Pace input.** `simulate()` takes `possessionsPerPeriod`. Does the endpoint expose
   it (advanced knob) or always use `SimConfig.DEFAULT_POSSESSIONS_PER_PERIOD`? Lean:
   don't expose yet (no consumer); default it.
4. **Play-by-play pagination semantics.** #019 flagged this as the first genuinely
   large result set. Decide page model: offset/limit via the orphaned
   `pageNumber`/`pageSize`? By `sequence` range? By `period`? And the default page
   size. Needs a new `Pageable` repo finder + a paged response envelope schema.
5. **Per-event time column (the schema call).** Single scalar (e.g. `secondsElapsed`)
   vs. an explicit range (`startSeconds`/`endSeconds`)? Or **defer again** and have
   play-by-play compute time on read from pace+period+sequence (no column at all)?
   §3.2 left this open *specifically* for §3.6 to decide. This determines whether
   §3.6 touches the schema.
6. **Error contract.** 404 for unknown `gameId` (matches `fetchTeam`); what does
   `POST /simulate` return for an unknown team id (the engine throws
   `ResourceNotFoundException` today → 404)? Confirm the status codes in the yaml.

---

# ===== SESSION 3 EXECUTION PLAN (Session 2 rewrites this into concrete tasks) =====

> **This is a skeleton, not an execute-ready list.** Session 2 must resolve the
> decisions above, then replace each bullet here with concrete, file-pathed,
> `[ ]`-tickable steps (the granularity §3.5 had). Do not execute this skeleton as-is.

- [ ] **1. Record #024** — the six decisions above, resolved, mirroring #021/#022/#023.
- [ ] **2. OpenAPI schemas + operations** in `gametime-api/yml/gametime.yaml`:
  `Game`, `GameEvent` (incl. `assistPlayerId` + the time field per decision 5),
  `BoxScore`, the simulate-response model (per decision 1), a paged play-by-play
  envelope (per decision 4), and the 3 operations. Regenerate stubs (`mvn ... compile`
  on the api module). Copy `fetchTeam` / `Team` as templates.
- [ ] **3. Schema migration (only if decision 5 adds a column)** — append to
  `release.1.0.4.game.sql` (NOT a new release); dbms-gate Postgres SQL; populate from
  the engine at persist time in `GameSimulator`.
- [ ] **4. Entity→model mapper** — new mapping (Game/GameEvent/BoxScore entity → the
  new models), the analog of `EntityMapper.entityToTeam`. **Does not exist today.**
- [ ] **5. Service + repo** — a game read/simulate service method (or extend
  `GametimeService`); add the paginated `GameEventRepo` finder (Pageable).
- [ ] **6. Delegate impls** in `V1ApiDelegateimpl.java` — the 3 new `@Override`s,
  thin, delegating to the service (simulate → `GameSimulator`; gets → repos+mapper).
- [ ] **7. Tests** — Cucumber features (copy `fetch-team.feature`) + step defs;
  delegate unit tests; play-by-play ordering + pagination; simulate response +
  box-score reconciliation; 404 paths. Keep per-package coverage ≥80% (target ~90%).
- [ ] **8. Gate** — `mvn -f gametime-service/pom.xml clean install` green.
- [ ] **9. Docs + close-out** — game.md API section, verify #024 matches shipped,
  roadmap §3.6 ✓ + Shipped note, reset this focus to **Phase 4 — Statistics & Box
  Scores**, strip completed §3.6 content.

---

## Meta-lessons for Session 2 (what a good analysis session produces — from the §3.5 experience)

The §3.5 execution went smoothly *because* its planning session left these. Session 2
should produce the same for §3.6:

- **Resolve ALL decisions with the user up front** and record them before any code.
  §3.5's A–F were fully settled — the exec session never had to stop and ask. Half-
  resolved decisions are where a coding session stalls or guesses wrong.
- **A "verified facts" section with file paths + method names + line numbers**, and an
  explicit "this exists / this does NOT exist" (e.g. "no entity→model mapper exists",
  "no paginated finder exists"). The single most useful thing §3.5's todo had.
- **Name the single seam / structural crux** if there is one. §3.5's was "the on-floor
  five is read at ONE place (`resolvePossession` lines 72–73)". For §3.6 the likely
  crux is **the missing entity→model mapper** + **the simulate-vs-GET response split**
  — call out the equivalent up front so Session 3 plans it once.
- **Flag what breaks / re-baselines.** §3.5 warned that seed-pinned tests would shift.
  For §3.6: adding the first game endpoints shouldn't break existing tests, but a
  persisted-seed or schema change would need `docker compose down -v` for dev DBs
  (#009) — note the blast radius.
- **State the scope fence.** §3.5 had an explicit "what this is NOT" (no injuries, no
  §3.6). §3.6's fence: **no Phase-4 stats aggregation** (season totals/leaders are
  Phase 4), **no new gameplay** (engine is done), **no frontend**. API + read
  projection only.
- **Give a worked example to copy.** For §3.6, point Session 3 at the **existing
  `fetchTeam` end-to-end** (yaml op → `V1ApiDelegateimpl.fetchTeam` → `service.getTeam`
  → `EntityMapper.entityToTeam`) as the copy-me template for a read endpoint. **Note:
  §3.4's commit `cb232d5` is NOT a useful example here — it did not add an endpoint
  (OpenAPI was deferred to §3.6).** The real "how an operation was last added"
  reference commits are **`aac86f6`** (consolidated `GET /team` reads) and
  **`c4b6fd4`** (added the roster + lineup API — a `PUT` + `POST` + `DELETE` with
  schemas, delegate impls, and Cucumber features in one commit — the closest analog
  to the multi-endpoint §3.6 diff). `git show c4b6fd4` for the full pattern.
- **Confirm the coverage gate command + JDK 21** in the plan (both below) — §3.5's
  todo repeated them so no session forgot.

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
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true`) stays in the `sim` test sources; it reports the §3.4
  aggregates + the §3.5 minutes/period-FG% distributions. Re-run it after any
  `SimConfig` change.
