# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.13).

Current focus: **§3.11 — And-1 / shooting foul on a made basket**. The full
§3.7–§3.13 sequence, the calibration-blast-radius ordering, and what follows (Phase 4)
live in **roadmap.md's "Possession-fidelity completion" section** — not here
(todo.md is current-phase-only). §3.7, §3.8, §3.9, and §3.10 shipped; §3.11 is
next. §3.12 (all-shot-type contact) and §3.13 (flagrants) are new sub-phases that
follow it — **not this pass** (see the "Do NOT" guardrails).

> **§3.11 is now execute-ready — design resolved as decisions.md #029 (A1/A2/A3/B–E).**
> The shape: an **and-1** (a defensive foul on a shot that still goes in) becomes a
> made FG **+ 1** free throw. The crux (Decision A1, user call): a **second, post-make
> foul roll** placed beside the assist roll — the pre-shot foul branch and `BASE_FOUL`
> are left **untouched** (the §3.7/§3.10 carve shape a third time), so the and-1 rate is
> independently tunable. Scoped to **made DRIVE/POST only** (`made && isContactType`, A2)
> so exactly one new scoring source is tuned; all-shot-type contact is **§3.12**. Awards
> **one** FT (B) via a **per-situation count** through the reused `awardFreeThrows`. The
> and-1 rate is a **rare-event probability** (`rareEventProbability` + its own
> `AND_ONE_BASE`/`AND_ONE_SENSITIVITY`, C). And **free throws become self-describing**
> (D) — each `FREE_THROW` carries its source (`SHOOTING`/`BONUS`/`AND_ONE`), retiring
> §3.10's accepted bonus/shooting-foul ambiguity now that there are three sources.
> **No schema, no OpenAPI, no new `PlayType`.** Reuses the §3.10 substrate as-is.
> **This one is NOT free** — a **pure-additive** points lift (FTs on already-scored
> makes), so §3.11 earns a recalibration pass (E). **Read decisions.md #029 — and #028's
> implementation note (the `BASE_FOUL` wrong-way lever + the shot-`BASE_*` fix) — before
> executing.**

---

## §3.11 execution plan (decisions.md #029 A1/A2/A3/B–E — resolved, ready to build)

Build order. Seam: a **post-make and-1 roll** in `PossessionEngine`'s `if (made)` block
(after the assist roll) + a `FoulResolver` and-1 probability method (reusing
`rareEventProbability`) + a **per-situation count + source** threaded through
`awardFreeThrows` + `SimConfig` + `CalibrationHarness`. **No schema, no OpenAPI, no new
`PlayType`** (the and-1 is a `PlayType.FOUL` `AND_ONE` outcome reusing #028's
`committing_team_id`; the FT source is an `outcome` on `PlayType.FREE_THROW`, #020).
Mirror the §3.7–§3.10 execution rhythm.

> All Maven commands set `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
> (JDK 21). Per-package coverage gate (80% line, target ~90%) only at
> `mvn -f gametime-service/pom.xml clean install`. Engine work in the `sim` package —
> no OpenAPI/schema change. `CalibrationHarness` is disabled by default
> (`-Dcalibration=true`).

**Step 1 — the and-1 probability method on `FoulResolver` (#029 C).**
- [ ] A method (leading candidate: `FoulResolver.isAndOne(shooter, defender,
  defensivePressure, rng)` returning `boolean`) that reuses
  `SimConfig.rareEventProbability(base, drivingSkill, opposingSkill, sensitivity)` — the
  **floor-free** clamp (#028), **not** `contestProbability` (its `PROB_FLOOR = 0.02`
  would trap a thin base tunable-only-upward — the #028 trap) and **not** the global
  `SENSITIVITY = 0.5` (it swamps a thin base — §3.7/§3.10, twice).
- [ ] Skill inputs are the shooter's `foulDrawing` vs. the defender's effective
  discipline (`SCALE_AVG*2 - foulProne`, fatigue-scaled) — the exact avg-10 wiring
  `isFoul` uses. Scale by `defensivePressure` like the other foul rolls.
- [ ] `SimConfig.AND_ONE_BASE` (thin placeholder) + `SimConfig.AND_ONE_SENSITIVITY`
  (gentle placeholder, like `REBOUND_FOUL_SENSITIVITY = 0.10`) — both harness-tunable
  (Step 5). Reuse the machine, its own two dials (#029 C).

**Step 2 — per-situation FT count + self-describing source through `awardFreeThrows` (#029 B/D).**
- [ ] Give `awardFreeThrows` a **count** parameter (replacing the hard-coded
  `SimConfig.FREE_THROWS_PER_FOUL` loop bound) and a **source** parameter stamped onto
  each emitted `FREE_THROW` event's `outcome`. All **three** call sites pass their pair:
  - shooting foul (`// 2. Foul check`, ~L140) → count `FREE_THROWS_PER_FOUL` (2),
    source `SHOOTING`.
  - §3.10 bonus (~L313) → count `FREE_THROWS_PER_FOUL` (2), source `BONUS`.
  - new and-1 (Step 3) → count `AND_ONE_FREE_THROWS` (**1**), source `AND_ONE`.
- [ ] `SimConfig.AND_ONE_FREE_THROWS = 1`; `FREE_THROWS_PER_FOUL = 2` **unchanged**
  (shooting/bonus keep it).
- [ ] The `FREE_THROW` `outcome` becomes self-describing — the source combined with
  `MADE`/`MISSED` (final spelling an execution call: e.g. `MADE_AND_ONE`/`MISSED_AND_ONE`,
  or a structured source field). **Constraint:** made/missed must still be readable, and
  the source must not collide with `SHOOTING_FOUL`/`REBOUNDING_FOUL_*` (those are `FOUL`
  outcomes, a different `PlayType`). This retires #028 D's accepted ambiguity (D).

**Step 3 — the post-make and-1 roll, carved beside the assist (#029 A1/A2/A3/B).**
- [ ] In `PossessionEngine`'s `if (made)` block, **after** the assist roll + the `SHOT`
  event emit (~L194–201), **before** the `return`: if `shotType.isContactType()` (A2 —
  DRIVE/POST only), roll `foulResolver.isAndOne(...)`.
- [ ] On a hit:
  - `defender.recordFoul()` (feeds foul-outs #023-F, and the §3.10 period tally).
  - Emit a `PlayType.FOUL` event, `outcome = "AND_ONE"` (final spelling an execution
    call), `primary_player_id` = the defender, **`committing_team_id = defTeamId`**
    (one-sided — reuses #028 D's column, no new plumbing).
  - `awardFreeThrows(..., AND_ONE_FREE_THROWS, source=AND_ONE, ...)` for the shooter (B)
    — **one** FT, points from the made FG already recorded.
- [ ] The made FG points/FGM/assist are **not** re-rolled or re-scored (B). The and-1
  **never forks the possession** — a made basket already ends it; the FT is tacked on
  before the ball changes hands.
- [ ] Fixed RNG order: the and-1 roll consumes the seed **after** the assist roll,
  **before** the `if (made)` return — existing seed-pinned made-shot assertions
  re-baseline (a controlled structural shift, as §3.7–§3.10 did).

**Step 4 — assist coexistence guard (#029 A3).**
- [ ] No code change beyond Step 3's ordering — confirm by test that a made and-1 can
  **still** carry an `assist_player_id` (the assist is stamped on the `SHOT` event
  before the and-1 roll), and that a `FOUL`/`FREE_THROW` landing between the `SHOT` and
  the next possession does **not** disturb assist reconciliation.

**Step 5 — `CalibrationHarness` and-1 line + recalibration (#029 E).**
- [ ] Extend the §3.10 fouls/bonus-FT line: print the **and-1 rate** (and-1s per
  team/game, or and-1 FTA as a share of made contact shots) and the **FT-source split**
  (`SHOOTING` / `BONUS` / `AND_ONE` shares of all FTs) — now that D makes the source
  visible, this is a self-check on the tag too.
- [ ] Run the harness (`-Dcalibration=true`). **§3.11 is NOT free** — a **pure-additive**
  lift (FTs on already-scored makes, no offset), so expect a **larger, cleaner** points
  rise than §3.10's. **Recalibrate against the shot `BASE_*` lever** (trim to re-center),
  **NOT `BASE_FOUL`** — trimming `BASE_FOUL` moves points the **wrong way** (it converts
  a possession-ending 2-FT trip back into a live shot worth more; #028's implementation
  note). Re-agree the numbers with the user against the §3.10 landing (113.8 pts / 46.9%
  FG / 37.6% 3P / 27.7 ast / 14.2 TO / 5.0 blk / 2.8 OOB) and the ~112/47/36/26/14
  §3.4 targets.
- [ ] **Steer by MULTIPLE runs (different seeds), tuning to the mean** — a single run
  carries per-seed noise that can bait an over-correction (the user's guard against
  low/high randomness). Don't chase a lone outlier.

**Step 6 — tests + close-out.**
- [ ] Unit-test the and-1 probability (a fixed seed drives an and-1; a high-`foulDrawing`
  or high-pressure input raises the rate; a **zero** `AND_ONE_BASE` yields zero — the
  feature can be switched off, like the §3.10 rare-event regression test), the count +
  source threading (and-1 awards exactly **1** FT tagged `AND_ONE`; shooting/bonus still
  award 2 tagged `SHOOTING`/`BONUS`), and the post-make placement (a **missed** contact
  shot draws no and-1; a made **PERIMETER/THREE** draws no and-1 per A2; a made DRIVE/POST
  can).
- [ ] The assist-coexistence guard (Step 4) and a determinism/count test.
- [ ] Confirm `GameSimulatorIntegrationTest` still reconciles — the new `FOUL`/
  `FREE_THROW` events must not break points or foul reconciliation. And-1 FTs `addScore`
  exactly as shooting-foul FTs do; points still reconcile with the event log (#020).
- [ ] Re-baseline (do **not** delete) any test asserting bare `MADE`/`MISSED` FT
  outcomes — D makes them self-describing (`SHOOTING`/`BONUS`/`AND_ONE`), so shooting-foul
  + bonus FT-event assertions shift with the source tag.
- [ ] New/changed `sim` classes to ~90%+ line coverage (JaCoCo gate), matching
  §3.7–§3.10; full `mvn clean install` gate green.
- [ ] **Doc close-out (the §3.7–§3.10 pattern — do all of these):**
  - [ ] `game.md` — add the `FOUL` / `AND_ONE` row and the self-describing `FREE_THROW`
    source rows (`SHOOTING`/`BONUS`/`AND_ONE`) to the vocabulary table; add an **and-1**
    note to the possession-flow narrative (a foul rolled *alongside* a made basket, +1
    FT). This is the living event-vocabulary reference.
  - [ ] `roadmap.md` — flip the §3.11 bullet to `[x]` with an indented italic landing
    summary (the recalibration result — final aggregates incl. the and-1 FT lift, the
    and-1 rate + FT-source split, coverage), matching the §3.7–§3.10 shipped bullets.
  - [ ] `decisions.md #029` — add the **implementation note** (`from execution,
    YYYY-MM`): the resolved open-at-execution items (final `AND_ONE` + FT-source `outcome`
    spellings, the `AND_ONE_BASE`/`AND_ONE_SENSITIVITY` numbers, the FT-source
    suffix-vs-field call, the recalibration size/direction), the landing aggregates, and
    coverage. Note any divergence from A1–E.
  - [ ] `possession-flow.puml` — confirm the `And1` partition (added this design pass)
    matches what shipped; adjust if the placement moved.
- [ ] **After §3.11 ships:** the queued `decisions.md` **condense pass** (backlog.md) is
  now unblocked — §3.7–§3.11 is a complete arc, and #028's implementation note is no
  longer live context for a pending design pass.

**Reconciliation invariant:** §3.11 adds `FOUL` (`AND_ONE`) and `FREE_THROW`
(`*_AND_ONE`) events. Points must still reconcile with the event log (#020) — and-1 FTs
`addScore` exactly as shooting-foul FTs do. The and-1 foul feeds the per-player `fouls`
counter (foul-outs #023-F) and the §3.10 period tally like any foul. **No new box-score
counter**, no re-scored FG.

**Do NOT (guardrails from #029 / #028 / #020):**
- Do **not** restructure the pre-shot foul branch or touch `BASE_FOUL` — the and-1 is a
  **second, post-make roll** (#029 A1). Keeping the pre-shot branch as-is is what keeps
  `BASE_FOUL`'s §3.4 calibration legible.
- Do **not** draw and-1s on `PERIMETER`/`THREE` (or change `isContactType`) — that is
  **§3.12** (all-shot-type contact + fouled-three = 3 FTs), its own design pass (#029 A2).
- Do **not** re-roll or re-score the made field goal, and do **not** fork the possession
  on an and-1 (#029 B) — the make already ended it; the FT is tacked on.
- Do **not** consult the bonus for the and-1 FT count — an and-1 is **always exactly 1
  FT** by rule (#029 B), independent of penalty status.
- Do **not** introduce a new `PlayType` — an and-1 is a **kind of `FOUL`** (#025 F /
  #026 E / #028 D); reuse `PlayType.FOUL` with an `AND_ONE` outcome + #028's
  `committing_team_id`.
- Do **not** correct an over-shoot with `BASE_FOUL` — it moves points the **wrong way**
  (#028 impl note); use the shot `BASE_*` lever (#029 E).
- Do **not** pick up §3.13 (flagrants/technicals) — a distinct foul sub-system (shooter
  selection, possession retention, ejections) with its own design pass.

**Open-at-execution (small, constrained — #029 status):** the exact `AND_ONE` + FT-source
`outcome` spellings (no collision with `SHOOTING_FOUL`/`REBOUNDING_FOUL_*`/`MADE`/
`MISSED`, made/missed still readable); the `AND_ONE_BASE` + `AND_ONE_SENSITIVITY` numbers
(placeholders, settled by the Step-5 harness line); whether the FT source is a suffix on
the existing `outcome` or a small structured field; and the size/direction of the
recalibration (E), re-agreed with the user against multiple harness runs.

---

## Verified facts (the `sim` package map — confirmed against the code 2026-08, post-§3.10)

Paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`.
Trust these; re-check only if the code moved. All engine code is in the `sim/` package.

**The seam §3.11 opens — `sim/PossessionEngine.java`:**
- `// 2. Foul check` (~L130) fires **only** on contact shot types
  (`shotType.isContactType()`) and **returns before the shot** — the "fouled, shot
  stopped, 2-FT trip" case. §3.11 leaves this **untouched** (#029 A1); it is NOT the
  and-1 seam. It emits `FOUL` / `SHOOTING_FOUL` (`committing_team_id = defTeamId`) then
  `awardFreeThrows(...)`.
- `// 3. Shot`'s `if (made)` block (~L184–202) records points/FGM, rolls the assist
  (~L194, `resolveAssist` → `assister.recordAssist()` → `assistPlayerId` on the `SHOT`
  event), then **returns**. **The and-1 seam is between the assist roll and that
  return** (#029 A1/A3) — a made DRIVE/POST rolls `isAndOne`, and on a hit emits its
  `FOUL` + one `FREE_THROW` **after** the `SHOT` event.
- `awardFreeThrows(data, shooter, shootingTeamId, offTeamId, defTeamId, period,
  sequence, rng)` (~L359) loops a **hard-coded** `SimConfig.FREE_THROWS_PER_FOUL` and
  emits bare `MADE`/`MISSED` `FREE_THROW` events. §3.11 gives it a **count** parameter
  (and-1 passes 1) + a **source** parameter (stamped on the outcome) — all three call
  sites (shooting ~L140, bonus ~L313, new and-1) pass their pair (#029 B/D).

**The rare-event probability machine — ALREADY BUILT (§3.7 + §3.10):**
- `SimConfig.rareEventProbability(base, drivingSkill, opposingSkill, sensitivity)`
  (~L441) clamps to **[0, PROB_CEILING]** with **no floor** — a thin base stays tunable
  downward and can be zeroed. `clampProbability` (floor `PROB_FLOOR = 0.02`) is for
  normal-frequency outcomes only. The and-1 rate reuses `rareEventProbability` (#029 C).
- Rare events carry their **own** sensitivity: `BLOCK_SENSITIVITY = 0.12` (§3.7),
  `REBOUND_FOUL_SENSITIVITY = 0.10` (§3.10). The global `SENSITIVITY = 0.5` swamps a
  thin base. §3.11 adds `AND_ONE_SENSITIVITY` (#029 C).
- `FoulResolver.isFoul` (~L28) has the avg-10 skill wiring to reuse: shooter
  `foulDrawing × fatigueFactor` vs. defender `(SCALE_AVG*2 - foulProne) × fatigueFactor`,
  scaled by `defensivePressure`.

**The team-foul / bonus + `committing_team_id` substrate — ALREADY BUILT (§3.10, #028):**
- `GameData.isInBonus(teamId, period)` + `periodFoulCount` derive the penalty from the
  `FOUL` event log — no stored counter, emit-then-count. The and-1 foul counts toward
  the committing (defense) team's tally like any other; §3.11 reuses this as-is (it does
  **not** consult the bonus for the FT count — that's always 1).
- `game_event.committing_team_id` (nullable `VARCHAR`) carries the committer on every
  `FOUL`. An and-1 foul is on the **defense** → `defTeamId` (the simple one-sided case).
- `SimConfig.BONUS_FOULS_PER_PERIOD = 5`, `FREE_THROWS_PER_FOUL = 2` (kept for
  shooting/bonus), `BASE_FOUL = 0.15` (**do not touch** — #029 A1/E).
- `ShotType.isContactType()` = `DRIVE || POST` only (unchanged this pass — the A2 gate;
  widening it is §3.12).

**`test/.../sim/CalibrationHarness.java`:** disabled-by-default (`-Dcalibration=true`),
prints the §3.4 aggregates + §3.5 minutes/period-FG% + §3.7 blocks + §3.8 OOB + §3.9
turnover-cause mix + §3.10 team-fouls/bonus-FT lines. Re-run after any `SimConfig`
change. §3.11 extends the §3.10 line with an and-1 rate + FT-source split (Step 5).

**§3.10 landing to recalibrate against (harness, 102 games):**
`113.8 pts / 46.9% FG / 37.6% 3P / 27.7 ast / 14.2 TO / 5.0 blk / 2.8 OOB`,
fouls 3.95/team/period, 37.1% of team-periods in the penalty, bonus FTA
0.8/team/game. §3.4 targets remain ~112/47/36/26/14.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md). *(Includes a **`decisions.md` condense pass,
  queued for AFTER §3.11 ships** — §3.11's design pass was the heaviest consumer of the
  very entries that would be compressed (#025/#026 D/#028); with §3.11 shipped, §3.7–§3.11
  is a complete arc and the pass is unblocked. **Do not compress #028 while §3.11 is
  live** — its implementation note is still load-bearing for §3.11's recalibration.)*
- **Untriaged future-improvement ideas** (no phase home, not chores) →
  [ideas.md](ideas.md). *(Includes the parked cap 3→5 tuning idea — do NOT touch the
  `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` value without its own recalibration pass.)*
- **§3.12 (all-shot-type contact fouls + graduated rate + fouled-three = 3 FTs)** and
  **§3.13 (flagrant/technical fouls)** → roadmap.md's Possession-fidelity section, each a
  numbered sub-phase needing its own design pass (decisions.md #029 A2 + follow-up). **Not
  this pass.**
- **§3.6 seams left open by #024** (carry into Phase 4/7): play-by-play pagination
  + a `period` filter (Decision D); the per-event time column's storage shape
  (Decision E — decided by the Phase 7 game view); a lean header-only `GameResult`
  projection (Decision A).
- **§3.7 seams left open by #025** (carry forward): a skilled-blocker recovery edge
  (Decision D — additive if a consumer ever wants it); shot-clock pressure on
  block-recovered second-chance possessions (Decision E → §3.9-E, still parked #027 D).
- **§3.9 seams left open by #027** (carry forward): §3.7-E shot-clock pressure (still
  parked); finer turnover sub-types (`DOUBLE_DRIBBLE`/`CARRYING`/`PALMING`/etc. — add a
  weight + enum value when a consumer wants the granularity).
- **§3.10 seams left open by #028** (carry forward): `committing_team_id` is
  **populated and queryable but not surfaced on the OpenAPI `GameEvent`** (the and-1
  `FOUL` populates it too) — additive whenever a play-by-play or Phase-4 stats consumer
  wants it; and the observed off/def rebounding-foul split (**78/22**) drifts from the
  configured 75/25 because defensive fouls compound through retained possessions —
  back-solve only if a consumer needs the observed split to hit a target.
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true`) stays in the `sim` test sources; it reports the §3.4
  aggregates + the §3.5 minutes/period-FG% distributions + the §3.7 blocks line
  + the §3.8 OOB line + the §3.9 turnover-cause mix + the §3.10 team-fouls/bonus-FT
  lines + (this pass) the §3.11 and-1 rate + FT-source split. Re-run it after any
  `SimConfig` change.
