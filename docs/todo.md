# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.11).

Current focus: **§3.7 — Blocked shots**. The full §3.7–§3.11 sequence, the
calibration-blast-radius ordering, and what follows (Phase 4) live in
**roadmap.md's "Possession-fidelity completion" section** — not here (todo.md is
current-phase-only). This file holds only the §3.7 execution plan below; when §3.7
ships it gets rewritten to §3.8's plan.

> **§3.7 design pass DONE — resolved as decisions.md #025 (A–F). Execute-ready.**
> The design is settled; the plan below is the third (execution) session. Locked:
> **v3** three-way MAKE/MISS/BLOCK draw; **A1** block slice carved off the top, §3.4
> make contest on the remainder; **B2** block = `finishing`-vs-`rimProtection`/
> `shotContest` contest; **C** rate ordering DRIVE≥POST>PERIMETER≫THREE, THREE
> very-low, ~5/team; **D** flat four-way `BlockResolver` recovery
> (RECOVERED_OFFENSE/DEFENSE + OOB_OFFENSE/DEFENSE); **E** offense-recovered → skip
> ReboundResolver, re-enter at ShotSelector; **F** block recorded as a `SHOT`/
> `BLOCKED_*` outcome + `recordBlock()` credit (mirrors steals). Flow:
> `possession-flow.puml`. **No schema change** (`box_score.blocks` exists).

---

## Verified facts (confirmed against the code 2026-07 — paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`)

Trust these; re-check only if the code moved. All engine code is in the `sim/` package.

**The possession loop — `sim/PossessionEngine.java`:**
- `resolvePossession(...)` (~line 80) is the per-possession method. Its body is a
  `while (true)` loop (~line 101) run for second-chance possessions; **every terminal
  path `return sequence(+1)`** ends the possession, and a second-chance **loops by NOT
  returning** — an offensive rebound does `offensiveRebounds++` (~line 194) and falls
  to the bottom of the loop, which re-runs from the top (turnover → foul → shot). This
  is the "re-enter at ShotSelector" mechanism for Decision E — the offense-recovered
  block does the same `offensiveRebounds++` + continue, **not** a return.
- The branch order inside the loop is **`// 1.` turnover** (~107), **`// 2.` foul**
  (~123), **`// 3.` Shot** (~144), **`// 4.` Rebound** (~182). The `BLOCKED?` fork goes
  at the **top of `// 3. Shot`**, after `shooter.recordFieldGoalAttempt()` (~145) and
  before `shotResolver.isMade(...)` (~152).
- **Event emission:** `data.addEvent(offTeamId, defTeamId, period, sequence, PlayType,
  outcome, primaryPlayerId)` (7-arg) and an 8-arg overload adding `assistPlayerId`
  (~line 173, used only by made assisted shots). A `BLOCKED` event uses the **7-arg**
  form (no assist, F4).
- **Injected resolvers** are constructor fields (`shotResolver`, `reboundResolver`,
  ~lines 13–26). Add `blockResolver` the same way.

**The steal pattern to mirror (Decision F) — `sim/PossessionEngine.java` ~lines 107–119:**
`if (turnover) { … stealer.recordSteal(); outcome = "STOLEN"; … shooter.recordTurnover();
data.addEvent(…, PlayType.TURNOVER, outcome, shooter.getPlayerId()); }`. The event names
the **victim** (`shooter`), the defender is credited by a **separate `recordSteal()`**,
not on the event. A block mirrors this exactly with `PlayType.SHOT` / `"BLOCKED_*"` /
`blocker.recordBlock()`.

**`sim/PlayerGameState.java`:**
- Stat accumulators + record methods exist: `recordSteal()` (line 241),
  `recordTurnover()` (240), `recordFieldGoalAttempt()`, `recordThreePointAttempt()`,
  `recordFieldGoalMade(pts)`. **Add `blocks` field + `recordBlock()` + `getBlocks()`
  mirroring `steals`/`recordSteal()`/`getSteals()` (lines 62, 241, 228).**
- Skills the block contest needs are already exposed: `getFinishing()` (200),
  `getRimProtection()` (208), `getShotContest()` (209), and `fatigueFactor()` (148).
- F3 (shooter charged the attempt) is **automatic** — the block branch still calls
  `recordFieldGoalAttempt()` (+`recordThreePointAttempt()` for a blocked THREE) but
  **not** `recordFieldGoalMade`, so it's a missed FGA with no special handling.

**`sim/ShotResolver.java`:** `isMade(shotType, shooter, defender, chemistryMultiplier,
rng)` (~line 28) is the contest template; `baseProbability(shotType)` (~line 43) switches
on `ShotType`; math is `config.contestProbability(base, offense, defense)` clamped. Add
`isBlocked(...)` in the same shape.

**`sim/ShotType.java`:** enum `DRIVE(2) / PERIMETER(2) / POST(2) / THREE(3)`;
`isContactType()` returns true for DRIVE/POST (~line 18) — handy for the per-type block
base-rate switch.

**`sim/ReboundResolver.java`:** an `@Component` (line 15) with a weighted-pick +
logistic-contest shape — the **template to copy for the new `BlockResolver`** (also an
`@Component`).

**`GameSimulator.java` box-score mapping** (~lines 104–131): one `bs.setX(p.getX())` per
counter. **`bs.setBlocks(0)` is the hardcode to replace (line 112)** → `p.getBlocks()`
(sits right after `setSteals(p.getSteals())`, line 111 — same mirror).

**`SimConfig.java`:** all base rates live here (e.g. `BASE_DRIVE = 0.59`, `SENSITIVITY`,
`PROB_FLOOR`/`PROB_CEILING`, `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`). Add `BASE_BLOCK_*`
+ the four recovery weights here.

**`test/.../sim/CalibrationHarness.java`:** disabled-by-default (`-Dcalibration=true`).
The aggregate-print block is ~lines 210–220 (`Points/FG%/3P%/Assists/Turnovers/Off reb/
Def reb per team`). **Add a `Blocks / team / game … (target ~5)` line there**, alongside
an accumulator over `BoxScoreEntity.getBlocks()` (mirror how `steals`/`offReb` are summed).

**Reconciliation precedent:** `GameSimulatorIntegrationTest` already reconciles box-score
stats against the event log (assists vs. assisted-SHOT events, rebounds vs. REBOUND
events — #020/#022). The blocks reconciliation (step 6) extends that exact pattern.

---

## §3.7 execution plan (work in order) — decisions.md #025

> All Maven commands set `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
> (JDK 21). Per-package coverage gate (80% line, target ~90%) only at
> `mvn -f gametime-service/pom.xml clean install`. This is engine work in the `sim`
> package — no OpenAPI/schema change. Re-run the harness (`-Dcalibration=true`) at
> the end; blocks remove would-be-make points → one recalibration pass.

1. **`SimConfig` constants (#025 C, D).**
   - [ ] Block base rates per shot type — `BASE_BLOCK_DRIVE` ≥ `BASE_BLOCK_POST` >
     `BASE_BLOCK_PERIMETER` ≫ `BASE_BLOCK_THREE` (very-low, not 0). Placeholders;
     tuned by the harness toward ~5 blocks/team/game.
   - [ ] Flat four-way recovery weights (defense-leaning placeholders):
     `RECOVERED_DEFENSE` > `RECOVERED_OFFENSE` > `OOB_DEFENSE` ≈ `OOB_OFFENSE`.
2. **`PlayerGameState` (#025 F2).**
   - [ ] Add a `blocks` counter + `recordBlock()` + `getBlocks()`, mirroring
     `steals`/`recordSteal()`/`getSteals()` (`PlayerGameState.java` lines 62, 241, 228).
3. **Block contest — `ShotResolver` (#025 A1, B2).**
   - [ ] Add `isBlocked(shotType, shooter, defender, rng)` — the logistic contest
     `base(shotType) + SENSITIVITY × (defenderBlockSkill − shooter finishing)/10`,
     `defenderBlockSkill` = `rimProtection` (DRIVE/POST) / `shotContest` (PERIMETER),
     × `fatigueFactor`. Rolls BEFORE the make/miss roll (block-first, A1).
4. **`BlockResolver` (new `sim` @Component) (#025 D).**
   - [ ] Flat four-way roll → `RECOVERED_OFFENSE` / `RECOVERED_DEFENSE` /
     `OOB_OFFENSE` / `OOB_DEFENSE` (fixed weights from step 1, no skill input).
5. **Wire into `PossessionEngine` (#025 A1, E, F).** (Shot branch is `// 3. Shot`,
   ~line 144; the block fork goes after `recordFieldGoalAttempt()` ~145, before
   `isMade(...)` ~152. Mirror the steal block at ~107–119 for the event shape.)
   - [ ] If `isBlocked` → `shooter.recordFieldGoalAttempt()` (+`recordThreePointAttempt()`
     if THREE) then emit a **7-arg** `data.addEvent(…, PlayType.SHOT, "BLOCKED_*",
     shooter.getPlayerId())` (shot type in the outcome, e.g. `BLOCKED_DRIVE`) — **no**
     `assistPlayerId` (F4); `blocker.recordBlock()` (F2). Do **not** call
     `recordFieldGoalMade` — it's a missed FGA (F3, automatic).
   - [ ] Run `blockResolver`; on `RECOVERED_OFFENSE`/`OOB_OFFENSE` do `offensiveRebounds++`
     and **continue the loop** (re-runs from the top = ShotSelector), respecting the
     `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` cap — the same mechanism as an offensive
     rebound (~line 194), **skipping** `ReboundResolver` (E). `RECOVERED_DEFENSE`/
     `OOB_DEFENSE` → `return sequence + 1` (possession over).
   - [ ] Replace `GameSimulator.setBlocks(0)` (line 112) with `p.getBlocks()`.
6. **Tests (target ~90% per package).**
   - [ ] `ShotResolver.isBlocked` unit tests (elite rim protector vs. weak finisher →
     higher block; THREE near-zero; fatigue effect).
   - [ ] `BlockResolver` unit tests (four outcomes reachable, defense-leaning split,
     flat/skill-independent).
   - [ ] `PossessionEngine` / integration: a `BLOCKED_*` event is a `SHOT` with a
     missed FGA and no assist; blocker credited; offense-recovered block runs a
     second-chance possession (counter bumps, cap respected).
   - [ ] **Reconciliation** (extend the #020/#022 pattern): count of `SHOT` events
     with `outcome LIKE 'BLOCKED%'` == sum of `BoxScore.blocks`.
7. **Calibrate.** *(Interactive loop with the user — not one-shot: run, read the
   harness numbers, adjust, repeat, and re-agree the targets. The harness reports,
   it does not gate the build.)*
   - [ ] Add a `Blocks / team / game … (target ~5)` line to `CalibrationHarness`
     (~lines 210–220, next to the reb lines; sum `BoxScoreEntity.getBlocks()`).
   - [ ] Run `-Dcalibration=true`; tune `BASE_BLOCK_*` toward ~5/team and re-center
     scoring (nudge `BASE_DRIVE`/etc. up to refill the points blocks removed).
     Re-agree the §3.4/§3.5 aggregates (~112 pts / 47% FG / 36% 3P / 26 ast / 14 TO)
     with the user before proceeding.
8. **Gate.**
   - [ ] `JAVA_HOME=…/21.0.9-tem mvn -f gametime-service/pom.xml clean install` green.
9. **Docs + close-out.**
   - [ ] **Update `game.md`** — it currently says blocks are unmodeled (lines ~216/230:
     "no `BLOCK` play type… `blocks` remains an unmodeled 0… future §3.x") and that is
     now stale. Add `SHOT` / `BLOCKED_2PT_DRIVE` etc. rows to the `play_type`/`outcome`
     vocabulary table (mirroring the `MADE_*`/`MISSED_*` rows and the `STOLEN` row),
     flip the block box-score note to the real model (SHOT/BLOCKED_* event + a BLK via
     `recordBlock`, missed FGA on the shooter, no assist), and document the
     **steal/block symmetry** (a block is a field-goal outcome as a steal is a turnover
     outcome). Do this ONLY at close-out — game.md documents shipped reality.
   - [ ] **Update `player.md`** — the engine-wiring status paragraph under "How
     Attributes Map to Game Engine Needs" (~lines 195–208) lists what the engine
     wires today; add **shot blocks (`rimProtection`/`shotContest` vs `finishing`)**
     to that list. The table's "Shot block attempt | rimProtection" row (~line 218) is
     design intent and already present — just make the status prose match reality
     (and note `finishing` as the shooter counter-factor). Close-out only — it
     documents shipped reality.
   - [ ] roadmap.md §3.7: check the box + add a "Shipped" note (mirror §3.6's).
   - [ ] Verify #025 matches the shipped code; add an implementation note if execution
     diverged (as #023/#024 did).
   - [ ] Reset this file's focus to **§3.8 — Missed shot out of bounds** (the next
     free/no-recalibration item); strip the completed §3.7 plan.

> **What's next after §3.7** (§3.8–§3.11, then Phase 4) and the
> calibration-blast-radius ordering live in **roadmap.md's "Possession-fidelity
> completion" section** — not duplicated here, per current-phase-only.

---

> All Maven commands set `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
> (JDK 21 — decisions.md #005; Homebrew JDK 25 breaks Lombok). Per-package coverage
> gate (80% line, target ~90%) only shows at
> `mvn -f gametime-service/pom.xml clean install`.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md).
- **Deferred gameplay realism** (blocks, OOB-no-rebound, richer turnovers,
  rebounding fouls, and-1) → **promoted to numbered sub-phases §3.7–§3.11** in
  roadmap.md ("Possession-fidelity completion"), scheduled before Phase 4. No
  longer a catch-all deferred bucket; each is its own design-pass + execution.
- **Untriaged future-improvement ideas** (no phase home, not chores) →
  [ideas.md](ideas.md).
- **§3.6 seams left open by #024** (carry into Phase 4/7): play-by-play pagination
  + a `period` filter (Decision D); the per-event time column's storage shape
  (Decision E — decided by the Phase 7 game view); a lean header-only `GameResult`
  projection (Decision A).
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true`) stays in the `sim` test sources; it reports the §3.4
  aggregates + the §3.5 minutes/period-FG% distributions. Re-run it after any
  `SimConfig` change.
