# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**§3.x Deferred sim-fidelity details**.

Current focus: **Phase 3.4 — Team Chemistry & Coaching Effects** (roadmap.md
§3.4). §3.1 Game Model, §3.2 Possession Engine, and §3.3 Rebounding are shipped;
§3.4 layers team/coach modifiers onto the existing possession + rebound flow and
calibrates the placeholder `SimConfig` constants empirically.

---

## Phase 3.4 — Team Chemistry & Coaching Effects

**Goal**: layer team-chemistry skills (`teamOffense`/`teamDefense`/`passing`/
`acumen`) and the 5 coach decision attributes (decisions.md #018) onto the
existing §3.2/§3.3 possession + rebound flow — *as modifiers on a baseline, not
replacements* — and run the empirical calibration pass that §3.2/§3.3 deferred
(the placeholder `SimConfig` constants get tuned against simulated output).

Roadmap §3.4 deliverables:
- `teamOffense`/`teamDefense` affect overall team efficiency
- `passing` influences assist rate / ball movement (assists are **0** from §3.2 —
  this is where they become real)
- `acumen` influences shot-selection quality
- Coach system modifies possession pace, shot distribution, defensive scheme
- Rebalance skill-calculator formulas + `SimConfig` base rates once possessions
  (and rebounds) exercise them (clutch/foulProne/transition, `BASE_OFFENSIVE_REBOUND`,
  shot base rates, etc.)

> ✅ **Decisions resolved.** All five §3.4 decisions (A–E) are settled with the
> user — see the decisions section below. Notably **B = B1**: §3.4 **does** touch
> the database schema (a nullable `assist_player_id` column on `game_event`), so
> unlike §3.3 this phase has a Liquibase + entity + mapper task. The §3.4 session
> can proceed straight to implementation: record A–E in decisions.md (#022),
> finalize the task sequence below against the resolved decisions, and build.

---

## Verified facts about the current code (confirmed this session)

A cold session can trust these without re-deriving them (re-verify only if the
code has moved):

- **The coach IS hydrated end-to-end but NOT yet in the engine.** `CoachEntity`
  has all 5 attributes (`pace`, `offensiveScheme`, `defensiveScheme`,
  `rotationDepth`, `substitutionAggressiveness`, all `Integer`);
  `EntityMapper.entityToTeam()` maps them onto the `Team` model via
  `entityToCoach()`; `GametimeServiceImp.getTeam()` returns the hydrated `Team`.
  So `GameSimulator.simulate()` already has `homeTeam.getCoach()` /
  `awayTeam.getCoach()` in hand — **but `buildStarters(team)` extracts only
  players, and the coach is never threaded into `PossessionEngine` or the
  resolvers.** Plumbing the coach (or a derived modifier object) down is §3.4's
  first structural task.
- **Seams are clean** (decisions.md #021): every probability constant lives in
  `SimConfig`; `contestProbability()` / `freeThrowProbability()` are the math
  hooks; `ShotSelector.pickShooter`/`pickShotType` is the weighted draw a coach
  shot-distribution modifier bends; resolvers are pure (skills + RNG in, value
  out).
- **Assists are 0 and the event model has no assister field.** `GameEvent`
  carries a single `primary_player_id` — there is no second-participant column.
  `BoxScore.assists` is hardcoded 0 in `GameSimulator`. (This is the crux of
  Decision B.)
- **`BASE_*` shot/turnover/foul/rebound constants are placeholders** chosen in
  §3.2/§3.3, never calibrated. §3.4 is the phase that tunes them.
- **`teamOffense`/`teamDefense`/`passing`/`acumen` skills exist** on
  `PlayerSkills` (generated model) and are calculated, but `PlayerGameState` does
  **not** extract them yet (same situation rebound skills were in before §3.3).

---

## Decisions to resolve BEFORE the task sequence

Mirror of §3.2's A–E. **Status: ALL FIVE RESOLVED with the user (2026-06-30).**
A confirmed (avg-10 deviation multiplier), B decided B1 (assists first-class —
`assist_player_id` on `game_event`, attributed by weighted `passing` draw; §3.4
IS schema-touching), C decided (acumen → make-prob modifier in `ShotResolver`,
teamOffense/teamDefense → possession-level efficiency multiplier), D decided D1
(build a calibration harness), E decided (rotation attrs are §3.5). Only
implementation-time details remain open: A's pace-vs-loop sub-question, the
harness form, and the target benchmarks — all settled while building.

The §3.4 session should record these in a decisions.md entry (#022) and proceed
straight to implementation — no decisions remain to resolve with the user first.

### Decision A — Coach/chemistry modifier shape  *(CONFIRMED)*

**Question**: how does a coach attribute (or team-chemistry skill) bend a
baseline value?

**Confirmed (user)**: reuse the avg-10 deviation form from decisions.md #021/#018
so coach numbers and player numbers compose with no translation layer. A
multiplier:
```
effectiveValue = baseValue × (1 + COACH_SENSITIVITY × (attr − 10) / 10)
```
An attribute of 10 ⇒ ×1.0 (no effect). Start with a **single** `COACH_SENSITIVITY`
constant in `SimConfig`; split per-effect only if calibration (Decision D) shows
one knob can't fit all effects. **All new constants live in `SimConfig`** (the
#021 discipline), never inline.

**Open sub-question to settle at implementation**: does `pace` scale the
**possession count** (a structural change to `PossessionEngine.simulate()`'s loop
bound) or only shot-clock urgency / shot-type lean? Pace-as-loop-count is the
realistic reading but changes the loop structure — decide while looking at the
plumbing.

**Resolution changes**: the signature of the modifier helper and whether the sim
loop bound becomes coach-derived.

### Decision B — Assist model (THE scope-defining decision)  *(DECIDED — B1, first-class & reconcilable)*

**Question**: how does `passing` produce assists on made field goals, and **does
this require a schema change?**

**Decided (user): B1 — assists are first-class and reconcilable against the event
log.** Add a nullable **`assist_player_id`** column to `game_event` (the assister
participant). This is the **first schema change since §3.1**, so §3.4 DOES touch:
- **Liquibase** — a changeset adding `assist_player_id` to `game_event`
  (H2-compatible; follow the existing audit-column / `dbms:postgresql` conventions).
- **`GameEventEntity`** — add the field.
- **`EntityMapper`** / event persistence in `GameSimulator` — emit + persist it.
- **OpenAPI** — only if/when the play-by-play model surfaces it (likely §3.6, not
  required here; add to `game_event`/`GameEvent` model only if a §3.4 read needs
  it). Default: schema + entity now, OpenAPI deferred to §3.6.

Assists reconcile the §3.3 way: `BoxScore.assists` is derived from / checked
against the count of made-FG events carrying an `assist_player_id` (events are the
source of truth, #020). The integration test extends the existing reconciliation
to cover assists.

**Attribution model (decided)**: on a **made field goal**, roll whether it was
assisted (probability scaled by the **passing** of the other on-floor offensive
players and/or team `teamOffense` — tune in calibration). If assisted, **pick the
assister by weighted `passing` draw** over the other 4 offensive players (the
shooter is excluded), mirroring `ShotSelector.pickShooter`. Credit that player an
assist and stamp `assist_player_id` on the SHOT event. Not every make is assisted.

**This UNLOCKS the scope fence**: §3.4 is **no longer schema-free** (unlike §3.3).
The "what §3.4 must NOT touch" list below is adjusted — a `game_event` migration +
entity + mapper change is now in scope; broader API surface stays out (§3.6).

**Resolution applied**: the task sequence gains a Liquibase/entity/mapper task and
an assist-attribution task; the integration test gains assist reconciliation.

### Decision C — Where `acumen` / `teamOffense` enter shot selection  *(DECIDED — per recommendation)*

**Question**: `acumen` is meant to influence **shot quality**, and `teamOffense`/
`teamDefense` team efficiency. Where do they hook in?

**Decided (user, per recommendation)**: keep the individual skill contests as they
are; layer two new modifiers, both using the Decision-A modifier form:
- **`acumen` → a small shot-make-probability modifier in `ShotResolver`** (better
  shot selection ⇒ higher-quality looks ⇒ a modest make bonus). NOT a shot-type
  reweighting — it bends the make rate, not the draw.
- **`teamOffense` / `teamDefense` → a single possession-level efficiency
  multiplier** (the roadmap's "overall team efficiency" deliverable), applied once
  per possession rather than folded into every individual contest.

Keep the modifiers **modest** — they're a thumb on the scale over the player-skill
contest, not a replacement. Sensitivities live in `SimConfig` and are tuned in
calibration (Decision D).

**Resolution applied**: `PlayerGameState` extracts `acumen` / `teamOffense` /
`teamDefense`; `ShotResolver` gains the acumen + team-efficiency modifiers.

**Resolution changes**: which resolver/selector gets the new modifier and whether
`PlayerGameState` extracts `acumen`/`teamOffense`/`teamDefense` (likely yes).

### Decision D — Calibration approach (§3.4 is the empirical-tuning phase)  *(DECIDED — D1)*

**Question**: §3.4 must "rebalance constants empirically by simulating games"
(decisions.md #021, roadmap §3.4) — but there is **no harness to observe
aggregate output** today. Tests assert plausibility *bounds*, not realism, so you
can't *see* the distributions you're tuning toward.

**Decided (D1)**: build a small **calibration harness** — a test/util that
simulates N games (e.g. 100) over the H2-seeded league and reports aggregate
distributions (avg team points, FG%, 3P%, reb%, assists/game, TO/game, etc.).
Tune the `SimConfig` constants (the §3.4 coach knobs *and* the placeholder
§3.2/§3.3 `BASE_*` rates) against agreed real-basketball benchmarks, watching the
aggregates converge. Keep the harness for future re-tuning. The user explicitly
wants a **fast, automated** way to iterate constants → observe output → converge,
rather than guess-and-check against pass/fail bounds (D2, rejected).

Note: the `SimConfig` knobs already exist (#021 put all constants in one place) —
D1 builds the *observation instrument*, not the knobs.

**Open sub-questions for implementation** (not blockers):
- **Form**: a JUnit test disabled-by-default (e.g. `@Disabled` / a tag excluded
  from the normal build, run on demand) vs. a small standalone runner with a
  `main()`. Lean: disabled-by-default test — reuses the `@SpringBootTest` H2 setup
  and stays out of the coverage-gate path. Pick while building.
- **Target benchmarks**: agree the numbers to tune toward (e.g. ~112 pts/team,
  ~47% FG, ~36% 3P, ~26 assists/game, ~14 TO/game) with the user before declaring
  the constants "calibrated" (see the calibration risk below).

**Resolution applied**: the task sequence includes a "build calibration harness"
task (task 10) feeding a "tune constants" task (task 11); "calibrated" is verified
against the agreed benchmarks via the harness, not by eye.

### Decision E — Scope fence: rotation attributes are §3.5, not §3.4  *(DECIDED)*

**Decided**: §3.4 reads `pace`, `offensiveScheme`, `defensiveScheme` only.
`rotationDepth` and `substitutionAggressiveness` are coach attributes but their
consumers are **§3.5** (minutes / fatigue / substitution) — coach.md's attribute
table tags them §3.5. §3.4 must **NOT** read them (the same discipline §3.3 held
on fatigue/blocks). Starters still play the whole game in §3.4; no minutes model.

*(No open question — recorded so the fence is explicit and a cold session doesn't
pull §3.5 work forward.)*

---

## ⚠️ Scope discipline — what §3.4 is and is NOT

§3.4 adds **coach + chemistry modifiers and calibration** onto the existing flow.
Keep out:

- **§3.5 minutes / fatigue / substitution** — no `rotationDepth` /
  `substitutionAggressiveness` reads (Decision E); starters play the whole game;
  no energy/fatigue.
- **§3.6 endpoints / API surface** — no `POST /simulate` API, no play-by-play
  endpoint. Decision B (B1) adds the `assist_player_id` **column + entity + mapper**
  but **NOT** an OpenAPI operation or (unless a §3.4 read needs it) a `GameEvent`
  model field — surfacing the assister in the API is §3.6.
- **Phase 6 coach progression / `playerDevelopment`** — coach attributes are read
  only, never written or evolved.
- **The deferred sim-fidelity items** (OOB, rebounding fouls, and-1, richer
  turnovers) stay in roadmap.md's §3.x — §3.4 does not pick them up unless a
  decision explicitly pulls one in.

---

## ⚠️ Risks / gaps (significance for a new session)

- **Coach-plumbing churn is a breaking change (like §3.3's `PlayerGameState`).**
  Threading the coach/modifiers into `PossessionEngine.simulate()` and
  `resolvePossession()` changes their signatures, which breaks the ~46 existing
  `sim` tests that call them with the current arity. **Significance**: plan the
  signature change once, up front (consider a `TeamContext`/`CoachModifiers`
  value object so future coach effects don't re-churn the signature). Budget for
  updating every call site, same as §3.3 did for the factory.
- **Decision B1 adds the first schema migration since §3.1 (H2/Postgres risk).**
  §3.4 adds a nullable `assist_player_id` column to `game_event` via Liquibase.
  **Significance**: tests run on H2, production on Postgres (risks.md "H2/Postgres
  divergence") — keep the changeset dual-compatible (nullable column, no
  Postgres-only syntax unless gated `dbms:postgresql`), and remember existing dev
  Postgres DBs need `docker compose down -v` to pick up the new column (decisions.md
  #009 trade-off). Do the schema task (task 2) early so the entity/persistence
  work builds on a migrated schema.
- **Calibration has no objective target.** §3.4's "tune the constants" goal is
  inherently fuzzy — "realistic" is a judgment call, and the seed data itself may
  be unrealistic (risks.md "Seed data realism"). **Significance**: without a
  harness (Decision D) and agreed target ranges, "calibrated" is unfalsifiable.
  Pin target benchmarks (e.g. ~110 pts/team, ~46% FG, ~36% 3P, ~24 assists) with
  the user before claiming the constants are tuned. Constants stay tunable
  regardless — §3.4 ships a *calibrated-ish, tunable* engine, not a perfect one.
- **Determinism must survive new RNG draws.** Any new roll (assist attribution,
  coach-influenced shot-type lean) consumes from the same seeded `RandomGenerator`
  threaded from `simulate()`. **Significance**: adding draws shifts the RNG
  stream, so existing seed-pinned assertions will change values — expect to
  re-baseline some determinism tests, and never create a second RNG.
- **Coverage gate is 80% line per package at `clean install`** (not `mvn test`).
  New modifier code + assist branches need seeded-RNG tests. Target ~90%.
- **JDK 21 only** — every Maven command sets
  `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`.

---

## Task sequence

> All decisions (A–E) are resolved, so this is the working sequence (no longer a
> draft). Run `mvn -f gametime-service/pom.xml clean install` (JDK 21) after
> engine + tests land — the per-package gate only shows there. Tick `[ ]` as each
> lands. Order is roughly: record decisions → schema → skills/constants →
> modifiers → thread into engine → assists → calibrate → test → close out.

- [ ] **1. Record Decisions A–E in decisions.md (#022).** One entry capturing the
  §3.4 modifier model (A), the assist model + `assist_player_id` schema choice
  (B1), the acumen/team-efficiency hooks (C), the calibration harness (D1), and
  the §3.5 scope fence (E) — mirroring how #021 captured §3.2. No user decisions
  remain; this is transcription.

- [ ] **2. Add the `assist_player_id` schema (Decision B1).** Liquibase changeset
  adding a nullable `assist_player_id` to `game_event` (H2-compatible; follow the
  audit-column / `dbms:postgresql` conventions in the existing changelog). Add the
  field to `GameEventEntity`. *No OpenAPI/model change — surfacing it in the API
  is §3.6.* *Files: `db/changelog.yml` (+ a release SQL), `entity/GameEventEntity.java`.*

- [ ] **3. Update docs for the agreed model.** game.md: add the `assist_player_id`
  participant to the `GameEvent` section + note that a made `SHOT` may carry an
  assister; note coach-modifier effects on the possession flow. coach.md: fill in
  the `f(...)` formulas now that Decision A is settled (the doc says "formulas TBD
  with the engine"). *Files: `docs/game.md`, `docs/coach.md`.*

- [ ] **4. Add §3.4 constants to `SimConfig`.** `COACH_SENSITIVITY` (single, to
  start — split per-effect only if calibration needs it), an assist base rate, and
  an `acumen` / team-efficiency sensitivity. *File: `sim/SimConfig.java`.*

- [ ] **5. Extract chemistry skills in `PlayerGameState`.** Add `teamOffense`,
  `teamDefense`, `passing`, `acumen` (double, via `toDouble()` from
  `PlayerSkills`), getters, and a `recordAssist()` accumulator + getter. Update
  `TestPlayerFactory` (add an overload; same approach as §3.3's 15-param overload,
  see commit c509f87). *File: `sim/PlayerGameState.java`,
  `test/.../sim/TestPlayerFactory.java`.*

- [ ] **6. Build the coach-modifier seam.** A `CoachModifiers` value object (or
  helper) that turns a `Coach` into the Decision-A multipliers: `paceMultiplier()`,
  `shotMixLean()`, `defensivePressure()`. Reads `pace` / `offensiveScheme` /
  `defensiveScheme` ONLY (Decision E — not the rotation attrs). Pure math, seeded
  unit tests. *Pattern: `sim/SimConfig.contestProbability` / `sim/FoulResolver`.*

- [ ] **7. Thread the coach into the engine (the breaking change).** Pass each
  team's coach/modifiers from `GameSimulator.simulate()` into
  `PossessionEngine.simulate()` / `resolvePossession()` (consider a `TeamContext`
  value object so future coach effects don't re-churn the signature).
  `buildStarters` stays player-only. Apply: pace → possession count or shot
  urgency (settle A's pace-vs-loop sub-question here); offensiveScheme →
  `ShotSelector` shot-type lean; defensiveScheme → turnover/foul pressure. Update
  all existing `sim` test call sites. *File: `sim/PossessionEngine.java`,
  `sim/GameSimulator.java`.*

- [ ] **8. Wire chemistry skills into resolution (Decision C).** `acumen` → small
  make-probability modifier in `ShotResolver`; `teamOffense`/`teamDefense` → a
  single possession-level efficiency multiplier. Keep both modest (thumb on the
  scale). *File: `sim/ShotResolver.java`.*

- [ ] **9. Implement assists (Decision B1 attribution).** On a made FG, roll
  whether it was assisted (probability scaled by the other on-floor offensive
  players' `passing` / team `teamOffense`); if assisted, pick the assister by
  weighted `passing` draw over the other 4 (shooter excluded, mirrors
  `ShotSelector.pickShooter`), credit `recordAssist()`, and stamp the assister on
  the made SHOT event (`assist_player_id`). Persist it in `GameSimulator` event
  writes; derive `BoxScore.assists` reconcilable against the events (#020).
  Replace `bs.setAssists(0)` with the real count. *File: `sim/PossessionEngine.java`,
  `sim/GameData.java` (event record carries the assister), `sim/GameSimulator.java`.*

- [ ] **10. Build the calibration harness** (Decision D = D1, decided). A
  disabled-by-default test (or small runner) that simulates N games over the
  H2-seeded league and reports aggregate distributions (avg pts, FG%, 3P%, reb%,
  assists/game, TO/game). Keep it for future re-tuning. Record the agreed target
  benchmarks here. *Pattern: `sim/GameSimulatorIntegrationTest` for the
  @SpringBootTest H2 setup.*

- [ ] **11. Tune the constants.** Adjust `SimConfig` base rates + sensitivities
  until aggregates land in the agreed ranges. Document the final values' rationale.

- [ ] **12. Tests.** Seeded-RNG unit tests for `CoachModifiers`, chemistry
  modifiers, and the assist model; `PossessionEngineTest` additions for
  coach-influenced flow; `GameSimulatorIntegrationTest` additions — assists are no
  longer 0, and **assist events reconcile with box-score assists** (extend the
  existing points/rebound reconciliation: count of made-SHOT events carrying an
  `assist_player_id` == sum of box-score assists). Re-baseline determinism tests
  whose seeded values shifted (new RNG draws move the stream). Keep `sim` ≥80%
  line (target ~90%).

- [ ] **13. Verify the gate.**
  `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install`
  → `All coverage checks have been met.` + `BUILD SUCCESS`.

- [ ] **14. Update docs/diagrams + close out.** Check §3.4 boxes in roadmap.md +
  add a "Shipped" note (mirror §3.2/§3.3 style); update possession-flow.puml if
  the flow changed (coach modifiers / assist on the made-shot branch); verify the
  decisions.md #022 entry (task 1) matches what shipped; reset this "Current
  focus" to **§3.5 — Minutes, Fatigue & Substitution**; remove completed §3.4
  content from this file (keep focus line + the pointers to backlog.md /
  roadmap.md deferred sections).

---

## Patterns to follow (quick reference)

- **Resolver / modifier pattern**: `sim/FoulResolver.java`, `sim/SimConfig.java`
  — `@Component`, constructor-inject `SimConfig`, pure math, seeded RNG. New
  modifier helpers mirror this.
- **Weighted random pick** (for assister selection): `sim/ShotSelector.pickShooter`
  / `sim/ReboundResolver.pickOffensiveRebounder` — sum weights, draw, pick on
  cumulative crossing.
- **Skill extraction + factory churn**: how §3.3 added rebound skills to
  `PlayerGameState` + the 15-param `TestPlayerFactory` overload (git show the §3.3
  commit) — the same approach for the chemistry skills.
- **Coach hydration**: `EntityMapper.entityToCoach` + `entityToTeam` already put
  the coach on `Team`; `GametimeServiceImp.getTeam` returns it.
- **Integration test style**: `sim/GameSimulatorIntegrationTest.java`
  (`@SpringBootTest` over H2-seeded league).
- **Decision record**: decisions.md #021 is the model for capturing the §3.4
  engine decisions (A/B/C as a new entry).

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md).
- **Deferred gameplay realism** (OOB-no-rebound, rebounding fouls, and-1, richer
  turnovers) → roadmap.md's **§3.x Deferred sim-fidelity details**, attached to
  the engine phase that will consume each.
