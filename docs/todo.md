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

> ⚠️ **Decisions gate.** Unlike §3.3 (whose decisions were pre-resolved), §3.4
> has genuinely open decisions — **resolve A–E below with the user BEFORE
> finalizing the task sequence.** Decision **B** in particular determines whether
> §3.4 touches the database schema, which changes the scope fence and reorders
> tasks. The task sequence at the bottom is a **draft**; tasks marked
> *(decision-dependent)* must be confirmed against the resolved decisions first.

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

Mirror of §3.2's A–E. Each is framed so the next session can resolve it with the
user and apply it correctly. **Status: A leaning, E decided, B/C/D open.**

### Decision A — Coach/chemistry modifier shape  *(leaning; confirm at impl)*

**Question**: how does a coach attribute (or team-chemistry skill) bend a
baseline value?

**Lean (confirm)**: reuse the avg-10 deviation form from decisions.md #021/#018 so
coach numbers and player numbers compose with no translation layer. A multiplier:
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

### Decision B — Assist model (THE scope-defining decision)  *(open)*

**Question**: how does `passing` produce assists on made field goals, and **does
this require a schema change?**

**Why it's the crux**: `BoxScore.assists` is 0 and `GameEvent` has only
`primary_player_id` — there is no assister participant field. To credit an
assister you must either:
- **(B1) Add an assister to the event model** — a new nullable
  `secondary_player_id` (or `assist_player_id`) column on `game_event`. This is a
  **Liquibase changeset + entity + mapper touch** — the *first schema change since
  §3.1* — and possibly an OpenAPI/model addition. It makes assists reconcilable
  against the event log (the #020 "events are source of truth" property).
- **(B2) Accumulate assists in `PlayerGameState` only** (box-score counter), no
  event-model change. Simpler, no migration — but assists then are **not**
  represented in the event log, breaking the §3.3 reconciliation pattern (box ==
  events) for this stat. A play-by-play feed (§3.6) couldn't show "assisted by".

**Recommendation to weigh with user**: B1 if we want assists first-class and
reconcilable (consistent with #020); B2 if §3.4 should stay schema-free and
assists are box-score-only for now (defer the event field to §3.6 when
play-by-play is the consumer). **This decision sets the "what §3.4 must NOT
touch" fence** — if B2, §3.4 touches no schema/OpenAPI/mapper (like §3.3); if B1,
it does.

**Also decide**: the assist *attribution model* — e.g. on a made FG, roll whether
it was assisted (probability scaled by the **passing** of the other 4 on-floor
offensive players / team `teamOffense`), then pick the assister by `passing`
weight (mirrors `ShotSelector` weighted draw). Keep it simple — not every make is
assisted.

**Resolution changes**: whether there's a migration task, a mapper task, and an
OpenAPI task; the §3.4 scope fence.

### Decision C — Where `acumen` / `teamOffense` enter shot selection  *(open)*

**Question**: `acumen` is meant to influence **shot quality**, and `teamOffense`/
`teamDefense` team efficiency. Where do they hook in?

**Options to weigh**:
- `acumen` as a modifier on **shot-make probability** (better shot selection ⇒
  higher-quality looks ⇒ a small make bonus) — a modifier in `ShotResolver`.
- `acumen` as a modifier on **shot-type weighting** in `ShotSelector` (steers
  toward higher-percentage shots) — bends the draw, not the make rate.
- `teamOffense`/`teamDefense` as a **team-level multiplier** applied once per
  possession (the "overall efficiency" deliverable) vs. folded into each contest.

**Recommendation**: keep individual contests as-is; add `teamOffense`/
`teamDefense` as a single possession-level efficiency multiplier and `acumen` as a
small make-probability modifier — both via the Decision-A modifier form. Confirm
with user.

**Resolution changes**: which resolver/selector gets the new modifier and whether
`PlayerGameState` extracts `acumen`/`teamOffense`/`teamDefense` (likely yes).

### Decision D — Calibration approach (§3.4 is the empirical-tuning phase)  *(open)*

**Question**: §3.4 must "rebalance constants empirically by simulating games"
(decisions.md #021, roadmap §3.4) — but there is **no harness to observe
aggregate output** today. Tests assert plausibility *bounds*, not realism.

**Options**:
- **(D1) Build a small calibration harness** — a test/util that simulates N games
  (e.g. 100) and prints aggregate distributions (avg team points, FG%, 3P%,
  reb%, assists/game, TO/game). Tune constants against real-basketball
  benchmarks, then keep the harness for future tuning. (risks.md "Skill formula
  balance" + "Seed data realism" are the consumers.)
- **(D2) Tune by eye** against the existing plausibility-bound tests. Cheaper, no
  new code, but no visibility into *distributions* — only pass/fail bounds.

**Recommendation**: D1 — a lightweight aggregate harness is the only honest way to
calibrate, and it's reusable for the §3.2/§3.3 constants too. Confirm scope (a
disabled-by-default test vs. a small runner) with user.

**Resolution changes**: whether there's a "build calibration harness" task and how
the "tune constants" task is verified.

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
- **§3.6 endpoints** — no `POST /simulate` API, no play-by-play endpoint. (If
  Decision B is B1, an assister *column* may be added, but no API operation.)
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
- **Decision B can silently expand scope into a schema migration.** If B resolves
  to B1, §3.4 suddenly touches Liquibase + entity + mapper (+ maybe OpenAPI) —
  the first schema change since §3.1. **Significance**: do NOT start the assist
  task before B is resolved, or you may build B2 and then have to add a migration
  (or build B1 and bloat a "schema-free" phase). The decision gates the fence.
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

## Task sequence (DRAFT — finalize after Decisions A–E)

> Tasks marked *(decision-dependent)* must be confirmed against the resolved
> decisions before starting. Run `mvn -f gametime-service/pom.xml clean install`
> (JDK 21) after engine + tests land — the per-package gate only shows there.
> Tick `[ ]` as each lands. The §3.4 session should rewrite/expand this list once
> A–E are settled (e.g. add/remove the migration + mapper tasks per Decision B).

- [ ] **1. Resolve Decisions A–E with the user.** Record outcomes here and, where
  they're architectural, append to decisions.md (a new entry, e.g. #022, for the
  §3.4 modifier model + assist decision — mirroring how #021 captured §3.2).

- [ ] **2. Update docs for the agreed model.** game.md: add the assist vocabulary
  if Decision B adds an event/outcome; note coach-modifier effects on the
  possession flow. coach.md: fill in the `f(...)` formulas now that they're
  decided (the doc currently says "formulas TBD with the engine").
  *(decision-dependent on A, B)*

- [ ] **3. Add §3.4 constants to `SimConfig`.** `COACH_SENSITIVITY` (+ per-effect
  variants if Decision A split them), assist base rate (if B), any
  team-efficiency sensitivity. *File: `sim/SimConfig.java`.* *(dep: A, B, C)*

- [ ] **4. Extract chemistry skills in `PlayerGameState`.** Add `teamOffense`,
  `teamDefense`, `passing`, `acumen` (double, via `toDouble()` from
  `PlayerSkills`), getters, and — if Decision B is B2 or B1 — a
  `recordAssist()` accumulator + getter. Update `TestPlayerFactory` (add an
  overload or extend params; same approach as §3.3's 15-param overload).
  *File: `sim/PlayerGameState.java`, `test/.../sim/TestPlayerFactory.java`.*
  *(dep: B, C)*

- [ ] **5. Build the coach-modifier seam.** A `CoachModifiers` value object (or
  helper on `SimConfig`) that turns a `Coach` into the multipliers from Decision
  A: `paceMultiplier()`, `shotMixLean()`, `defensivePressure()`. Pure math,
  unit-tested with seeded values. *Pattern: `sim/SimConfig.contestProbability`.*
  *(dep: A, E — read pace/offensiveScheme/defensiveScheme ONLY)*

- [ ] **6. Thread the coach into the engine (the breaking change).** Pass each
  team's coach/modifiers from `GameSimulator.simulate()` → `buildStarters` stays
  player-only, but `PossessionEngine.simulate()` / `resolvePossession()` gain a
  coach/modifier parameter (consider a `TeamContext` to avoid future re-churn).
  Apply: pace → possession count or shot urgency (per A's sub-question);
  offensiveScheme → `ShotSelector` shot-type lean; defensiveScheme →
  turnover/foul pressure. Update all existing `sim` test call sites.
  *File: `sim/PossessionEngine.java`, `sim/GameSimulator.java`.* *(dep: A, C, E)*

- [ ] **7. Wire chemistry skills into resolution.** `teamOffense`/`teamDefense`
  efficiency multiplier (per Decision C); `acumen` shot-quality modifier.
  *File: `sim/ShotResolver.java` / `sim/ShotSelector.java`.* *(dep: C)*

- [ ] **8. Implement assists.** Per Decision B: roll whether a made FG is
  assisted (scaled by other players' `passing` / `teamOffense`), pick the
  assister by `passing` weight, credit it. **If B1**: add `game_event` assister
  column (Liquibase changeset + `GameEventEntity` + `EntityMapper` + emit the
  participant), wire `BoxScore.assists` from the event log. **If B2**: accumulate
  in `PlayerGameState`, wire `GameSimulator` `bs.setAssists(p.getAssists())`.
  *Files depend on B.* *(decision-dependent on B — may add migration/mapper/OpenAPI tasks)*

- [ ] **9. Wire assist counts in `GameSimulator`.** Replace `bs.setAssists(0)`
  with the real count. *File: `sim/GameSimulator.java`.*

- [ ] **10. Build the calibration harness** *(if Decision D = D1)*. A test/util
  that simulates N games and reports aggregate distributions; use it to tune the
  `SimConfig` constants (coach + the placeholder §3.2/§3.3 `BASE_*`). Record the
  agreed target benchmarks. *(decision-dependent on D)*

- [ ] **11. Tune the constants.** Adjust `SimConfig` base rates + sensitivities
  until aggregates land in the agreed ranges. Document the final values' rationale.

- [ ] **12. Tests.** Seeded-RNG unit tests for `CoachModifiers`, chemistry
  modifiers, and the assist model; `PossessionEngineTest` additions for
  coach-influenced flow; `GameSimulatorIntegrationTest` additions (assists no
  longer 0; if B1, assist events reconcile with box scores — extend the existing
  reconciliation). Re-baseline determinism tests whose seeded values shifted.
  Keep `sim` ≥80% line (target ~90%).

- [ ] **13. Verify the gate.**
  `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install`
  → `All coverage checks have been met.` + `BUILD SUCCESS`.

- [ ] **14. Update docs/diagrams + close out.** Check §3.4 boxes in roadmap.md +
  add a "Shipped" note (mirror §3.2/§3.3 style); update possession-flow.puml if
  the flow changed (coach modifiers / assist branch); add the decisions.md entry;
  reset this "Current focus" to **§3.5 — Minutes, Fatigue & Substitution**; remove
  completed §3.4 content from this file (keep focus line + the pointers to
  backlog.md / roadmap.md deferred sections).

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
