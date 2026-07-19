# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**§3.x Deferred sim-fidelity details**.

Current focus: **§3.7 — Blocked shots** (roadmap.md §3.7), the first of the
**possession-fidelity completion** sub-phases (§3.7–§3.11) now scheduled **before
Phase 4** (decision 2026-07). Phase 3's engine simulates + persists + exposes a
full game (§3.1–§3.6 shipped), but deliberately omits five possession-level
realism items; we decided each **should be in the game** and belongs to Phase 3
(finish the engine before Phase 4 measures it). Each is its **own numbered
sub-phase with its own design pass** (the §3.4/§3.5/§3.6 three-session workflow),
sequenced by calibration blast radius so scoring-affecting changes land one at a
time against a known-good baseline.

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
   - [ ] Add a `blocks` counter + `recordBlock()` (mirror `recordSteal()`), exposed
     via a getter for the box-score mapping.
3. **Block contest — `ShotResolver` (#025 A1, B2).**
   - [ ] Add `isBlocked(shotType, shooter, defender, rng)` — the logistic contest
     `base(shotType) + SENSITIVITY × (defenderBlockSkill − shooter finishing)/10`,
     `defenderBlockSkill` = `rimProtection` (DRIVE/POST) / `shotContest` (PERIMETER),
     × `fatigueFactor`. Rolls BEFORE the make/miss roll (block-first, A1).
4. **`BlockResolver` (new `sim` @Component) (#025 D).**
   - [ ] Flat four-way roll → `RECOVERED_OFFENSE` / `RECOVERED_DEFENSE` /
     `OOB_OFFENSE` / `OOB_DEFENSE` (fixed weights from step 1, no skill input).
5. **Wire into `PossessionEngine` (#025 A1, E, F).**
   - [ ] In the shot branch: `record FGA` → if `isBlocked` → emit a `SHOT` event with
     `outcome = BLOCKED_*` (shot type, e.g. `BLOCKED_DRIVE`; `+1 3PA` if THREE),
     `primaryPlayerId = shooter`, **no** `assistPlayerId` (F4); `blocker.recordBlock()`
     (F2); shooter charged the FGA/miss (F3 — automatic via the SHOT event).
   - [ ] Run `BlockResolver`; on `RECOVERED_OFFENSE`/`OOB_OFFENSE` re-enter the
     second-chance loop at `ShotSelector`, bumping the offensive-rebound counter,
     **skipping** `ReboundResolver` (E). Defense/OOB-defense → possession over.
   - [ ] Replace `GameSimulator`'s `setBlocks(0)` with the real
     `PlayerGameState.getBlocks()`.
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
7. **Calibrate.**
   - [ ] Add a blocks/team line to `CalibrationHarness`; run `-Dcalibration=true`,
     tune `BASE_BLOCK_*` toward ~5/team and re-center scoring (nudge shot base rates
     up to refill the points blocks removed). Re-agree the §3.4/§3.5 aggregates.
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
   - [ ] roadmap.md §3.7: check the box + add a "Shipped" note (mirror §3.6's).
   - [ ] Verify #025 matches the shipped code; add an implementation note if execution
     diverged (as #023/#024 did).
   - [ ] Reset this file's focus to **§3.8 — Missed shot out of bounds** (the next
     free/no-recalibration item); strip the completed §3.7 plan.

---

## §3.7–§3.11 sequence (roadmap.md — work in order, one design pass each)

Full seams + design forks live in roadmap.md's "Possession-fidelity completion"
section. Ordering is by calibration cost, NOT roadmap number order:

| # | Item | Recalibration | Notes |
|---|------|---------------|-------|
| **§3.7** | Blocked shots | one pass | **Design DONE (#025); execute-ready plan above.** v3 three-way draw; block = a SHOT/BLOCKED_* outcome (mirrors steals); flat four-way recovery. |
| **§3.8** | Missed shot OOB (no rebound) | none (free) | Event-log labeling only; possession outcome unchanged. |
| **§3.9** | Richer turnover taxonomy | none (free) | New `outcome` values on `TURNOVER` events (no schema change, #021); turnover count unchanged. |
| **§3.10** | Rebounding / loose-ball fouls | small | **Builds the team-foul/bonus model** (shared with §3.11). |
| **§3.11** | And-1 (foul on a made basket) | biggest | Foul rolls *alongside* the shot; adds bonus-FT points → re-tune scoring. Reuses §3.10's substrate. |

**Cross-cutting:** §3.10 + §3.11 both need a **team-foul / bonus model** (per-team
per-period foul counts → bonus FTs) that doesn't exist yet — designed/built once in
§3.10. The `CalibrationHarness` **reports** (doesn't gate the build); "recalibration"
= re-run `-Dcalibration=true`, re-agree the numbers. Re-run it after every
scoring-affecting item.

### After §3.11 → Phase 4 — Statistics & Box Scores (roadmap.md §4)

Deferred until the engine is complete. Mostly **aggregation** of the per-game
`BoxScoreEntity` rows the engine already persists (#020) — season averages/totals,
team rollups, league leaders — plus stats APIs (`fetchTeam`/§3.6 endpoints are the
copy-me template). Open questions to resolve in *its* design pass: compute-on-read
vs. a materialized season-stats table (#013/#015/#020 discipline); how "season" is
scoped before a schedule/season table exists (Phase 5); leaders/rankings shape +
whether the orphaned `pageNumber`/`pageSize` params (#019) finally get a consumer.

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
