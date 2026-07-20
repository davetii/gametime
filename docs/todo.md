# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.11).

Current focus: **§3.10 — Loose-ball / rebounding fouls + the shared team-foul/bonus
substrate**. The full §3.7–§3.11 sequence, the calibration-blast-radius ordering, and
what follows (Phase 4) live in **roadmap.md's "Possession-fidelity completion"
section** — not here (todo.md is current-phase-only). §3.7, §3.8, and §3.9 shipped;
§3.10 is next.

> **§3.10 is now execute-ready — design resolved as decisions.md #028 (A1/A2/B/C/D/E).**
> The shape: a **two-sided non-shooting foul during the rebound phase** (defensive
> box-out push OR offensive over-the-back) becomes a real `FOUL` event, and §3.10 builds
> the **team-foul / bonus (penalty) substrate shared with §3.11**. The crux (A1, user
> call): **penalty status is DERIVED from the FOUL event log** —
> `count(FOUL committed-by team T in period P) >= BONUS_FOULS_PER_PERIOD` — **no stored
> counter** (#023-F derive-don't-store; #020 events-as-truth), **emit-then-count** so the
> Nth foul itself awards the bonus. Because fouls are **two-sided** (A2, user call — the
> committer isn't implied by off/def orientation), §3.10 adds **ONE nullable column,
> `game_event.committing_team_id`** (D, user call — a deliberate reversal of the
> schema-free stance, mirroring `assist_player_id`, with a real day-one consumer). A
> rebounding foul retains/forks possession by who-fouled and goes **straight to bonus
> free throws once the committing team is IN the penalty** (B, user requirement),
> reusing the existing FT block. **NOT free** — bonus FTs add points → a small
> **recalibration pass** (E), unlike §3.8/§3.9. Read #028 before executing.

---

## §3.10 execution plan (decisions.md #028 A1/A2/B/C/D/E — resolved, ready to build)

Build order. Seam: the rebound-phase foul roll (in/around `MissedShotResolver` +
`FoulResolver`) + a **derived penalty predicate** over the `FOUL` event log + the new
`committing_team_id` column + `SimConfig` + `PossessionEngine`'s `// 4. Rebound` block +
`CalibrationHarness`. **No OpenAPI, no new `PlayType`** (rebound foul is a `PlayType.FOUL`
`outcome`) — but **ONE additive schema column** (`committing_team_id`, #028 D). Mirror
the §3.7/§3.8/§3.9 execution rhythm.

> All Maven commands set `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
> (JDK 21). Per-package coverage gate (80% line, target ~90%) only at
> `mvn -f gametime-service/pom.xml clean install`. Engine work in the `sim` package.
> `CalibrationHarness` is disabled by default (`-Dcalibration=true`).

**Step 1 — the `committing_team_id` column + plumbing (#028 D — the one schema change).**
- [ ] Add a nullable `committing_team_id VARCHAR` to `gametime.game_event`, appended to
  **`release.1.0.4.game.sql`** (the unreleased game DDL — do NOT make a new release
  version), mirroring `assist_player_id` (plain column add, no `dbms` gate; H2 +
  Postgres both fine; an FK to team is optional, like `fk_game_event_assist_player`).
- [ ] Thread it through: a `committingTeamId` field + `@Column` on `GameEventEntity`; a
  field on `GameData.EventRecord`; an `addEvent(...)` overload carrying it (as
  `assistPlayerId` already has a 7-arg/8-arg overload pair); `setCommittingTeamId(...)`
  in `GameSimulator` (the `EventRecord`→`GameEventEntity` build, ~L86) and `EntityMapper`
  (~L197). Populate it for **`SHOOTING_FOUL` too** (= the defender's team) so the penalty
  derivation reads one uniform field across all `FOUL` events.

**Step 2 — the derived penalty predicate over the FOUL event log (#028 A1).**
- [ ] A helper (leading candidate: on `GameData`, next to `getEvents()`, or a small
  `sim` utility) answering **"is team T in the bonus in period P?"** as
  `count(FOUL events where committing_team_id == T and period == P) >=
  SimConfig.BONUS_FOULS_PER_PERIOD`. **No stored `teamFouls` field, no reset logic** — it
  reads the events (#020) exactly as #023-F's foul-out predicate reads the `fouls`
  counter. Grouping is by the Step-1 `committing_team_id` (uniform for both foul types).
- [ ] Both `SHOOTING_FOUL` and the new rebounding fouls count toward the tally (all
  fouls, #028 A1). **Emit-then-count** (#028 A1): the current FOUL event is emitted, then
  the predicate includes it — so the *Nth* foul (reaching the threshold) itself awards
  bonus FTs.
- [ ] `SimConfig.BONUS_FOULS_PER_PERIOD = 5` (modern-NBA placeholder, harness-tunable).

**Step 3 — the two-sided rebound-phase foul roll, carved off the top (#028 A2/C).**
- [ ] In the miss-resolution flow (`MissedShotResolver`, #026), roll a **rebound-foul
  chance FIRST** — before the four-way board draw. On a foul, **short-circuit** the board
  contest (the whistle stopped play). Same "carve off the top, run the existing contest
  on the remainder" shape as §3.7's `P(BLOCK)`.
- [ ] On a foul, a **side draw** picks the committer — **defense-leaning** (box-out
  dominates; over-the-back is the minority — a `SimConfig` split placeholder, e.g.
  defense ~70–80%, harness-tunable). Defense-commits → the offense is fouled;
  offense-commits → the defense is fouled.
- [ ] The foul probability is a small base scaled by the discipline/pressure skills the
  shooting foul uses (`foulProne` / `defensivePressure`, coach.md pressure/breakdown), in
  the avg-10 form (#021 C / #022). Keep it **independent of the rebound weights** (why
  it's carved off the top, not a fifth draw outcome — #028 C).
- [ ] Whether this lives in an extended `FoulResolver` method or a small sibling resolver
  is an execution call (#028 status). New base rate + off/def split → `SimConfig`.

**Step 4 — reward + possession fork by who-fouled + emit the FOUL event (#028 A2/B/D).**
- [ ] Evaluate the Step-2 predicate for the **committing team** (from the side draw):
  - **Defense committed** → offense is fouled → **not in bonus:** offense retains for a
    second chance (an offense-retention path like the offensive rebound / §3.7 recovery /
    §3.8 OOB-offense, respecting `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`); **in bonus:**
    offense shoots bonus FTs.
  - **Offense committed** → defense is fouled → **possession ends for the offense** (`return`);
    **in bonus:** the defense shoots bonus FTs (possession still ends).
  - Bonus FTs reuse the existing FT-award block verbatim (`PossessionEngine` ~L136–144:
    the `for` over `FREE_THROWS_PER_FOUL`, `recordFreeThrowAttempt()`/
    `recordFreeThrowMade()`, `MADE`/`MISSED` `FREE_THROW` events, `addScore`). The FT
    shooter is a player on the **fouled** team.
- [ ] Emit a `PlayType.FOUL` event with `outcome = "REBOUNDING_FOUL_DEFENSE"` /
  `"REBOUNDING_FOUL_OFFENSE"` (final spelling an execution call — the §3.8 `_OFFENSE`/
  `_DEFENSE` suffix precedent), `primary_player_id` = the committing player,
  **`committing_team_id`** = the committing team (Step 1); **increment that player's
  `fouls`** (feeds foul-outs, #023-F). Emit the FOUL **before** evaluating the predicate
  for THIS foul's award is not required (emit-then-count, #028 A1) but the event must be
  in the log for the *next* possession's check.
- [ ] Fixed RNG order in the miss flow: the rebound-foul roll (+ its side draw) consumes
  the seed **before** the board draw — existing seed-pinned rebound assertions
  re-baseline (a controlled structural shift, as §3.7/§3.9 did).

**Step 5 — `CalibrationHarness` team-fouls / bonus-FT line + recalibration (#028 E).**
- [ ] Add a line printing **team fouls / period** and the **bonus-FT rate** next to the
  existing aggregates (the §3.8-OOB / §3.9-cause-mix precedent) so the new FT source is
  visible.
- [ ] Run the harness (`-Dcalibration=true`). **§3.10 is NOT free** — bonus FTs add
  points, so expect points to lift. **Recalibrate** (nudge `BASE_FOUL` / the rebound-foul
  base down, or accept the lift) and **re-agree the numbers with the user** against the
  ~112/47/36/26/14 (+5 blk, +3 OOB) baseline — like §3.7's pass, not §3.8/§3.9's
  confirm-green.
- [ ] Eyeball the team-fouls/period so it's plausible (roughly a handful per period, not
  0 and not 20).

**Step 6 — tests + close-out.**
- [ ] Unit-test the penalty predicate (below/at/above threshold; **emit-then-count so the
  Nth foul awards** — a foul at count 4→5 puts them in; only in-period fouls count; both
  foul types count; grouped by `committing_team_id`), the two-sided rebound-foul roll (a
  fixed seed drives a foul; the side draw leans defense; a high-`foulProne`/pressure input
  raises the rate), and the reward fork (**defense-commits → offense retain/FTs;
  offense-commits → possession ends/defense FTs**). Plus a determinism/count test.
- [ ] Confirm `GameSimulatorIntegrationTest` still reconciles — the new `FOUL`/
  `FREE_THROW` events must not break points. Bonus FTs `addScore`; confirm points still
  reconcile with the event log (#020). Confirm `committing_team_id` persists + round-trips
  (H2 test + the mapper).
- [ ] New/changed `sim` classes to ~90%+ line coverage (JaCoCo gate), matching
  §3.7/§3.8/§3.9; full `mvn clean install` gate green.
- [ ] **Doc close-out (the §3.7/§3.8/§3.9 pattern — do all of these):**
  - [ ] `game.md` — add the two `FOUL` / `REBOUNDING_FOUL_DEFENSE` / `_OFFENSE` rows to
    the vocabulary table; **document the new `committing_team_id` column** in the
    `GameEvent` shape section; add a **penalty/bonus** note to the possession-flow
    narrative (fouls counted from the event log per period by committing team; the bonus
    sends non-shooting fouls to FTs; two-sided fork). Living event-vocabulary reference.
  - [ ] `roadmap.md` — flip the §3.10 bullet to `[x]` with an indented italic landing
    summary (the recalibration result — final aggregates incl. the FT lift, team-fouls/
    period, the off/def foul split, coverage), matching the §3.7/§3.8/§3.9 shipped bullets.
  - [ ] `decisions.md #028` — add the **implementation note** (`from execution,
    YYYY-MM`): the resolved open-at-execution items (final `outcome` spellings, the
    rebound-foul base + off/def split + `BONUS_FOULS_PER_PERIOD`, resolver placement, the
    recalibration size/direction), the landing aggregates, and coverage. Note any
    divergence from A1/A2/B/C/D/E.
  - [ ] `game.md`/`decisions.md #020` — note the `committing_team_id` column addition
    against the #020 `GameEvent` shape (the shape is "additive when a consumer defines
    it", #020 — §3.10 is that consumer).
  - [ ] `coach.md` (optional) — the `defensiveScheme` pressure/foul row could note that
    scheme now also drives rebounding fouls + bonus exposure. `player.md` — the
    `foulProne`/`foulDrawing` foul row could note rebounding fouls.

**Reconciliation invariant:** §3.10 adds `FOUL` (`REBOUNDING_FOUL_*`) and, in the bonus,
`FREE_THROW` events. Points must still reconcile with the event log (#020) — bonus FTs
`addScore` exactly as shooting-foul FTs do. The rebounding foul feeds the per-player
`fouls` counter (foul-outs, #023-F) like any foul. No new box-score counter. The
`committing_team_id` column is a raw event fact (not derived state), so it introduces no
reconciliation obligation of its own.

**Do NOT (guardrails from #028 / #023-F / #020):**
- Do **not** add a stored `teamFouls` *count* field — the penalty is **derived from the
  FOUL event log** (#028 A1). A stored running total is the #013/#015 duplicate-state trap
  #023-F rejected. (The `committing_team_id` column is DIFFERENT — it stores a raw fact
  about the event, WHO committed it, not a total; it is the one approved schema change.)
- Do **not** introduce a new `PlayType` — a rebounding foul is a **kind of `FOUL`**
  (#025 F / #026 E); it reuses `PlayType.FOUL` with a new `outcome`.
- Do **not** make the committing team `defense_team_id` blindly — fouls are **two-sided**
  (#028 A2); an offense-committed foul's committing team is `offense_team_id`. Read it from
  the side draw and store it in `committing_team_id`.
- Do **not** make a NEW release SQL file — append the column to `release.1.0.4.game.sql`
  (the unreleased game DDL).
- Do **not** pick up §3.11 (and-1) — it restructures the possession branching (foul
  *alongside* the shot) and is the bigger recalibration; it gets its own design pass on
  **this** substrate (#028 scope call).
- Do **not** couple the rebound-foul rate to the rebound weights — carve it off the top
  so it stays independently tunable (#028 C).

**Open-at-execution (small, constrained — #028 status):** the exact `REBOUNDING_FOUL_*`
outcome spellings; the rebound-foul base rate + the off/def split + `BONUS_FOULS_PER_PERIOD`
numbers (placeholders, settled by the harness line); whether the foul roll lives in an
extended `FoulResolver` method or a small sibling resolver; and the size/direction of the
recalibration (E), re-agreed with the user against the harness.

---

## Verified facts (the `sim` package map — confirmed against the code 2026-07, post-§3.9)

Paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`.
Trust these; re-check only if the code moved. All engine code is in the `sim/` package.

**The foul model to extend — `sim/FoulResolver.java` + `sim/PossessionEngine.java`:**
- `FoulResolver.isFoul(shotType, shooter, defender, defensivePressure, rng)` fires
  **only** on contact shot types (`shotType.isContactType()`), returns before the shot,
  and `PossessionEngine`'s `// 2. Foul check` emits a `FOUL` / `SHOOTING_FOUL` event
  (`primary_player_id` = the fouling **defender**), then the FT-award loop (~L136–144).
  §3.10 adds a **two-sided rebound-phase** foul with the same event shape + committer
  logging (but the committer can be offense OR defense — #028 A2).
- The rebound contest (`MissedShotResolver.resolve(offense, defense, capReached, rng)`,
  line ~224, `// 4. Rebound`) is **foul-free today**. §3.10 carves a foul roll off the
  top of it (#028 C). The fork below (`if (miss.outcome().offenseRetains()) { ...
  continue; } return sequence;`, ~L228–232) is the retain-vs-end shape §3.10's fork
  reuses.
- `period` is threaded through `resolvePossession` (param) to every `addEvent` — the
  penalty derivation (committing team + period) has it in scope.

**The `game_event` event record — where the new column threads (#028 D):**
- `GameData.EventRecord(offTeamId, defTeamId, period, sequence, playType, outcome,
  primaryPlayerId, assistPlayerId)` (record, ~L48) + `addEvent(...)` (7-arg + 8-arg
  overloads, ~L16/L23). `assistPlayerId` is the template for adding `committingTeamId`:
  a nullable field threaded record → `addEvent` overload.
- Persistence path: `GameSimulator` (~L76–86) builds `GameEventEntity` from each
  `EventRecord` (`setOffenseTeamId`/`setPrimaryPlayerId`/`setAssistPlayerId`);
  `EntityMapper` (~L190–197) does the same for the read path. `GameEventEntity` columns
  at `entity/GameEventEntity.java` (`assist_player_id` at ~L52–53 is the template).
- Schema DDL: `game_event` table in `main/resources/db/release.1.0.4.game.sql`
  (`assist_player_id VARCHAR` at ~L52; the `seed` column add at ~L120 shows a plain
  nullable add). **Append here — no new release** (per the release.1.0.4-unreleased note).

**The team-foul / bonus substrate (new, #028 A1):**
- **No per-team-per-period foul count exists.** `PlayerGameState.fouls` is a per-player,
  whole-game counter (foul-outs, #023-F). The penalty is a **derived predicate over the
  `FOUL` events** (committing team + period + `BONUS_FOULS_PER_PERIOD`), NOT a stored
  counter. Grouped by the new `committing_team_id` (uniform for both foul types).
- `GameData.getEvents()` already exposes the event list (the harness reads it); the
  derivation reads FOUL events from there.

**The §3.7/§3.8/§3.9 templates to mirror (all shipped in `sim`):**
- **§3.7 blocks** — the "carve a slice off the top, run the existing contest on the
  remainder" shape (`P(BLOCK)` before make/miss). §3.10's foul roll mirrors this.
- **§3.9 turnover causes** — a derived/relabel pass; and the discipline of reusing an
  existing `PlayType` + an open-ended `outcome`, no schema.
- **#023-F foul-outs** — the **derived-predicate, no-stored-flag** discipline the
  penalty derivation copies to the team level.

**Reconciliation precedent — `GameSimulatorIntegrationTest`:** reconciles box-score
counters against event counts (points, rebounds, assists, blocks — #020/#022/#025/#026).
§3.10 adds `FOUL`/`FREE_THROW` events; points must still reconcile (bonus FTs `addScore`
like shooting-foul FTs) — confirm it.

**`test/.../sim/CalibrationHarness.java`:** disabled-by-default (`-Dcalibration=true`),
prints the §3.4 aggregates + §3.5 minutes/period-FG% + §3.7 Blocks line + §3.8 OOB line
+ §3.9 turnover-cause mix. Re-run after any `SimConfig` change; add the §3.10 team-fouls/
bonus-FT line (Step 5).

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md).
- **Deferred gameplay realism** (and-1) → **numbered sub-phase §3.11** in roadmap.md
  ("Possession-fidelity completion"), scheduled before Phase 4. It builds on §3.10's
  team-foul/bonus substrate (#028 follow-up) and gets its own design-pass + execution.
- **Untriaged future-improvement ideas** (no phase home, not chores) →
  [ideas.md](ideas.md). *(Includes the parked cap 3→5 tuning idea — do NOT touch the
  `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` value without its own recalibration pass.)*
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
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true`) stays in the `sim` test sources; it reports the §3.4
  aggregates + the §3.5 minutes/period-FG% distributions + the §3.7 blocks line
  + the §3.8 OOB line + the §3.9 turnover-cause mix. Re-run it after any `SimConfig`
  change.
