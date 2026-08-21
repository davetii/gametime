# Gametime Project Plan

Basketball simulation game — 40-team league with attribute-driven gameplay, season management, and a React frontend.

_Last updated: 2026-07-10_

## What Exists Today

### Shipped
- **21 player attributes**: agility, awareness, aggression, charisma, cohesion, composure, determination, ego, endurance, energy, handle, health, intelligence, luck, shotSelection, shotSkill, size, speed, strength, verticality, wingspan
- **23 derived skills** on a 1–20 / avg-10 scale via a shared deviation helper: acumen, ballSecurity, passing, teamOffense, drive, freeThrows, longRange, perimeter, post, individualDefense, teamDefense, offenseRebound, defenseRebound, finishing, transition, rimProtection, stealing, shotContest, foulDrawing, foulProne, clutch, screenSetting, offBallMovement
- **9 positions**: PG, CG, SG, W, SF, F, PF, FC, C
- **Player status** (availability): ACTIVE, INJURED, SUSPENDED. Lineup slot is a
  separate `lineupRole` (STARTER, ROTATION, BENCH, INACTIVE, MINORS) on the
  roster assignment — see decisions.md #013.
- **40-team league** across 4 conferences (EAST, NORTH, SOUTH, WEST), 422 seed players (CSV-driven via Liquibase)
- **Entity layer**: Player, Team, Coach (5 decision attributes — #018), GM (name only); player↔team decoupled via `player_team` + `player_team_hist`
- **Roster & lineup model**: a team's roster is part of the `Team` resource (`players` are roster entries with lineup slot); lineup (starting 5 + bench rotation order) is sticky state on `player_team`; player status (availability) is separate from lineup role; roster size caps (15 active / 5 minors) enforced on sign + lineup, signed players default to `INACTIVE`. Roster construction is unconstrained by position — no position minimums/maximums (#017). See decisions.md #013–#017.
- **REST endpoints**: GET league, GET player by ID + history, createPlayer, updatePlayer; GET team by ID (incl. roster), addPlayerToTeam, removePlayerFromTeam, set lineup
- **Skill calculation engine**: SkillCalculator interface, 23 calculator implementations, SkillMapper orchestrator
- **Entity-to-model mapping**: EntityMapper with full attribute + skill wiring
- **Database**: Postgres (local dev) + H2 (tests), Liquibase migrations, gametime schema, audit triggers
- **Possession engine (§3.2–§3.11)** — a full, seeded, deterministic game
  simulation, not a stub. Shot selection → turnover (9 causes) → foul → three-way
  MAKE/MISS/BLOCK draw → four-way missed-shot outcome (rebound / out of bounds),
  with coach pace/scheme modifiers, real assists, minutes/fatigue/substitution,
  rebounding fouls + a derived team-foul/bonus model, and and-1s. Persists a full
  `GameEvent` log + per-player `BoxScore`, exposed via the §3.6 simulation APIs.
  Calibrated against modern-NBA benchmarks with a `CalibrationHarness`
  (disabled-by-default). See game.md for the flow and decisions.md #021–#029.
- **Test suite**: unit + Cucumber integration, 80% line coverage enforced (JaCoCo gate)
- **Build pipeline**: Multi-module Maven, OpenAPI codegen with delegate pattern, Docker Compose

### Deferred
- **GM attributes** — the name-only `GM` slot stays until its consumers are real
  (Phase 6.3 draft scouting / 6.4 trade evaluation). Resolve with the same
  continuous 1–20 model as coach (decisions.md #018); see coach.md open-Q #3.
  *(Coach attributes — done: Design Decision #3 resolved as #018, modeled
  end-to-end; see Shipped above.)*
- **Player age** — only `yearsPro` is modeled; no birth date / true age yet.
  `yearsPro` is sufficient for everything built so far; true age gains a consumer
  at Phase 6.1 (aging & development — attribute peak/decline curves), where the
  full date-of-birth model belongs.

---

## Phase 3 — Game Simulation Engine

**Goal**: Build the core possession-by-possession simulation.

### 3.1 Game Model ✓
- [x] Define `Game` entity: homeTeam, awayTeam, quarter structure, final score
- [x] Define `GameEvent` model: possessions, plays, outcomes
- [x] Define `BoxScore` model: per-player stats for a single game

_Shipped: `GameEntity` / `GameEventEntity` / `BoxScoreEntity` + repos, Liquibase
`release.1.0.4.game.sql`, persistence-only (no API surface — §3.6). See
decisions.md #020 and game.md for the modeling choices._

### 3.2 Possession Engine ✓
- [x] Possession flow: inbound → set play / fast break → shot clock → outcome
- [x] Shot selection logic: which player gets the ball, what type of shot (drive, perimeter, post, longRange)
- [x] Shot outcome: probability based on shooter skills vs defender skills
- [x] Turnover probability: ballSecurity vs defender individualDefense
- [x] Foul model: drive/post attempts → foul probability → freeThrow skill

_Shipped: `sim` package — `GameSimulator` @Service (transactional simulate()),
`PossessionEngine` (pure-sim loop), `ShotResolver`/`TurnoverResolver`/`FoulResolver`
(logistic-contest math per decisions.md #021), `ShotSelector` (skill-weighted),
`SimConfig` (all constants in one place for §3.4 tuning). Seeded RNG for
determinism. Missed shot ends possession (rebounding seam for §3.3). Starters
play the whole game (minutes/fatigue is §3.5). Assists left at 0 (§3.4).
Constants are placeholder starting points; §3.4 calibrates empirically._

### 3.3 Rebounding ✓
- [x] Offensive rebound probability: offenseRebound skills vs defenseRebound skills
- [x] Second-chance possessions

_Shipped: `ReboundResolver` @Component in the `sim` package (logistic-contest
`isOffensiveRebound()` per decisions.md #021 shape; skill-weighted
`pickOffensiveRebounder()`/`pickDefensiveRebounder()` mirroring `ShotSelector`).
`PossessionEngine.resolvePossession()` now resolves a rebound after every missed
shot: a `DEFENSIVE` rebound ends the possession, an `OFFENSIVE` rebound runs a
second-chance possession through the full flow (turnover → foul → shot), capped
at `SimConfig.MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION` (a loop, not recursion).
`PlayType.REBOUND` is now live (outcomes `OFFENSIVE`/`DEFENSIVE`); box-score
offensive/defensive rebound counts are real (no longer hardcoded 0). Coach/
chemistry modifiers (§3.4), fatigue (§3.5), blocks, and team box-out are
deliberately out of scope — raw `offenseRebound` vs `defenseRebound` skills only.
`BASE_OFFENSIVE_REBOUND` is a placeholder (~0.27); §3.4 calibrates empirically._

### 3.4 Team Chemistry & Coaching Effects ✓
- [x] teamOffense/teamDefense affect overall team efficiency
- [x] Passing skill influences assist rate and ball movement
- [x] Acumen influences shot selection quality
- [x] Coach system modifies possession pace, shot distribution, defensive scheme
- [x] Rebalance skill calculator formulas once possessions exercise them
      (calibrated empirically against modern-NBA benchmarks)

_Shipped (decisions.md #022): coach + chemistry modifiers layered onto the §3.2/
§3.3 flow as a thumb on the scale, plus the deferred empirical calibration pass.
All effects use the avg-10 deviation multiplier `base × (1 + COACH_SENSITIVITY ×
(attr−10)/10)`. `CoachModifiers` value object turns a `Coach` into pace / shot-mix
/ defensive-pressure multipliers (reads `pace`/`offensiveScheme`/`defensiveScheme`
ONLY — rotation attrs are §3.5); a `TeamContext` (players + modifiers) threads
them through `PossessionEngine` without re-churning signatures. **pace scales the
possession count**, offensiveScheme leans the `ShotSelector` shot-type draw,
defensiveScheme scales turnover/foul pressure. `PlayerGameState` now extracts
`teamOffense`/`teamDefense`/`passing`/`acumen`: `acumen` is a small make-rate nudge
+ `teamOffense`/`teamDefense` a possession-level efficiency multiplier in
`ShotResolver` (Decision C). **Assists are now real (Decision B1)**: a made FG may
be assisted (rolled from the supporting cast's passing), the assister picked by a
weighted passing draw over the other four and stamped on a new nullable
`game_event.assist_player_id` column (first schema change since §3.1; OpenAPI
deferred to §3.6); `BoxScore.assists` reconciles against assisted-SHOT events
(#020). A disabled-by-default `CalibrationHarness` simulates ~100 games and reports
aggregates; `SimConfig` base rates were tuned to land near ~112 pts / 47% FG / 36%
3P / 26 ast / 14 TO per team. Minutes/fatigue (§3.5) stays out of scope — starters
play the whole game._

### 3.5 Minutes, Fatigue & Substitution ✓
*(gameplay, not roster — minutes/fatigue/coach rotation are produced by games
being played. Input: the `rotationOrder` bench depth chart already shipped in
the roster domain, #014.)*
- [x] Minutes allocation: bench `rotationOrder` → distribution of playing time
- [x] Per-player energy tracking within a game (`endurance` ↔ minutes played)
- [x] Skill degradation as energy drops
- [x] Automatic substitution triggers based on fatigue thresholds
- [x] Coach rotation style determines when subs happen (gated on Coach model)

_Shipped (decisions.md #023): the on-floor five is now **dynamic** — bench players
enter, fatigue accumulates, and the coach's `rotationDepth` /
`substitutionAggressiveness` (the two attributes §3.4 deliberately left unread)
drive substitutions. A new `RotationState` (per team, held by `TeamContext`) owns
the full squad + the current five, exposes `onFloor()` (always exactly 5), and runs
a **between-possession** sub step in `PossessionEngine.simulate()`: drain on-floor
energy (scaled by `endurance`), recover the benched, force off any fouled-out player,
then run a fatigue sub (most-tired starter below a `substitutionAggressiveness`-scaled
threshold → freshest bench within `rotationDepth`; starters pulled later / return
first). The step is **deterministic given (energy, fouls, coach attrs) and consumes
no RNG** — the §3.4 seed-pinned stream only shifts because the five now change.
`PlayerGameState` gained `currentEnergy` (drain/recover, flat full-tank start) + an
on-floor-possession accumulator; a single `fatigueFactor(energy)` multiplier bends
each contest skill (shot/turnover/foul/rebound), composed multiplicatively with the
§3.4 modifiers. **Minutes are real** (`BoxScore.minutes`): a possession-share
projection (no game clock, #021), and **every player who took the floor gets a
box-score row** — team minutes sum to 5 × game-minutes. **Foul-outs** (`FOUL_OUT_LIMIT
= 6`) are a **derived predicate** over the existing foul counter (no stored flag),
forcing a sub from the full roster; the floor never drops below 5. §3.5 is
**schema-free** (within-game fatigue only; `box_score.minutes` already existed). The
`CalibrationHarness` was extended with per-slot minutes + period FG% reporting; the
§3.4 aggregates held with fatigue on (112.4 pts / 47.2% FG / 35.4% 3P / 27.6 ast /
13.4 TO) and the minutes curve lands on target (top starter ~37, none over ~42)._

### 3.6 Simulation APIs ✓
- [x] `POST /v1/game/simulate` — simulate a single game, return box score
- [x] `GET /v1/game/{gameId}` — retrieve game result
- [x] `GET /v1/game/{gameId}/play-by-play` — event log
- [x] Decide + migrate a stored per-event **time column** for play-by-play
      display — **closed as _derived-on-read_ (no column), not built** (#024 E);
      revisit trigger is the Phase 7 game view

_Shipped (decisions.md #024): the persisted engine is now exposed over OpenAPI —
`POST /v1/game/simulate`, `GET /v1/game/{gameId}`, `GET .../play-by-play` — as
**API + read-projection + entity→model mapping only** (no engine change). Both
simulate and get return a shared **`GameResult`** (`game` + home/away box scores):
the box scores are **split server-side** by resolving each `box_score` row's team
via `player_team` (the row has no `team_id`, #020) — one `findByTeamId` roster
lookup buckets the rows in a new `EntityMapper` mapping (the net-new work; the crux).
Play-by-play is a **flat sequence-ordered `[GameEvent]`, no pagination** (#024 D),
surfacing `assistPlayerId`. **Seed** is optional in / random default / **persisted**
(`game.seed BIGINT` — the ONE schema change, appended to the unreleased
`release.1.0.4.game.sql`, #024 B) and echoed on the `Game` header. Pace stays
internal (#024 C). Errors: 404 unknown game/team (engine already throws), a **new
422** (`ResourceUnprocessableException`) for the same-team guard (#024 F). No
per-event time column — derived on read (#024 E), so `game.seed` is the only schema
change. **Impl note:** to avoid a `GametimeServiceImp ↔ GameSimulator` constructor
cycle (the simulator needed a team lookup that lived on the top-level service), the
"load a team with its roster" logic was extracted into a focused `TeamQueryService`
both sides depend on — an acyclic, one-way graph, no `@Lazy`. Touched packages land
at 99–100% line coverage._

### Possession-fidelity completion (§3.7–§3.18) — before Phase 4

Real-basketball events the §3.2/§3.3 engine does **not** model yet. Originally
parked as "§3.x deferred sim-fidelity details," now **promoted to numbered
sub-phases and scheduled ahead of Phase 4** (decision 2026-07): the goal of Phase 3
is a complete possession engine, and shipping Phase 4 stats on a knowingly-thin
engine means the first leaderboards describe an incomplete game (a blocks leader of
all zeros, slightly-low scoring, a two-value turnover taxonomy).

**Each is its own sub-phase with its own design pass** — the §3.4/§3.5/§3.6
three-session workflow (a numbered `decisions.md` entry + an execute-ready
`todo.md` plan, then execution). These bullets are *seams, not plans*: the §3.7
blocks pass proved the one-line description under-specified the real design (its
"block only on misses" shortcut was rejected, and the modeled result — a three-way
MAKE/MISS/BLOCK draw with its own recovery resolver — bears little resemblance to
the original bullet). Do **not** execute a bullet without running its design pass first.

**Sequenced by calibration blast radius, not roadmap order.** The `CalibrationHarness`
guards ~112 pts / 47% FG / 36% 3P / 26 ast / 14 TO per team (§3.4). Some items are
pure event-log *labeling* (no effect on those numbers); others move scoring and cost
a recalibration pass. Ordering isolates the scoring-affecting changes so each lands
against a known-good baseline instead of tuning against two moving point-sources at
once. The harness **reports**, it does not gate the build — "recalibration" means
re-running the loop and re-agreeing the numbers, not a red build.

> **Shared dependency — BUILT in §3.10 (decisions.md #028):** §3.10 and §3.11 both
> need a **team-foul / bonus model** (foul counts per team per period → bonus free
> throws). They're adjacent so that substrate was designed and built once, in §3.10.
> It is **derived, not stored** — `GameData.isInBonus(teamId, period)` counts `FOUL`
> events by the `committing_team_id` column against `SimConfig.BONUS_FOULS_PER_PERIOD`,
> emit-then-count. §3.11 **did** reuse it as-is plus the extracted
> `PossessionEngine.awardFreeThrows` block (widening it with a per-situation FT count
> + source, #029 D); no further substrate work is needed, and §3.12 inherits both.

- [x] **§3.7 — Blocked shots ✓** *(own recalibration; the pilot)*.
      _Shipped (decisions.md #025): a blocked shot is now a first-class event — a
      **three-way MAKE/MISS/BLOCK draw** inside the shot resolution. `ShotResolver.isBlocked`
      carves `P(BLOCK)` off the top (A1, preserving §3.4 calibration) via a
      **defender(`rimProtection`/`shotContest`)-vs-shooter(`finishing`) contest** (B2)
      with its own gentle `BLOCK_SENSITIVITY` (the global 0.5 made blocks 2–3× too
      common — impl note on #025), shot-type-scaled with `THREE` very-low (C). A block is
      a **`SHOT`/`BLOCKED_*` event + a `recordBlock()` credit — exactly as a steal is a
      `TURNOVER`/`STOLEN` event + `recordSteal()`** (F, no new `PlayType`): the shooter is
      the victim on the event, charged a missed FGA, no assist; the blocker is credited
      separately. The new **`BlockResolver`** runs a flat four-way loose-ball recovery
      (offense/defense × in-bounds/OOB, D); offense-recovered blocks skip `ReboundResolver`
      and re-enter the second-chance loop at `ShotSelector` (E), capped like an offensive
      rebound. **No schema change** (`box_score.blocks` existed; `setBlocks(0)` → real).
      Recalibrated: **blocks 4.8/team (~5)**, and the §3.4/§3.5 aggregates recentered
      (112.9 pts / 47.2% FG / 36.0% 3P / 27.1 ast / 13.5 TO) by nudging the shot base
      rates up to refill the points blocks removed. Reconciliation `count(SHOT outcome
      LIKE 'BLOCKED%') == Σ BoxScore.blocks` holds. New/changed `sim` classes at
      99.4–100% line coverage. Flow: `docs/possession-flow.puml`._
- [x] **§3.8 — Missed shot out of bounds (no rebound) ✓** *(landed **free** —
      harness-neutral, no recalibration)*.
      _Shipped (decisions.md #026): a missed shot now resolves to **one of four
      outcomes in a single draw** — offensive rebound / defensive rebound /
      OOB-offense / OOB-defense — owned by a new **`MissedShotResolver`** that
      **wraps** `ReboundResolver` (unchanged, A/B). The OOB share is carved off the
      top FIRST by a **flat, defense-leaning, skill-independent** lean; the skill
      board contest (`offenseRebound` vs `defenseRebound`) runs only on the clean-
      rebound remainder (A) — so OOB never inherits the board winner. **Skill-
      weighted** — the deliberate difference from §3.7's flat `BlockResolver` (C).
      Both **sail-out** and **tipped-OOB** land here, sharing one OOB outcome each
      (offense/defense). OOB is a `REBOUND`/`OUT_OF_BOUNDS_*` event that **credits
      no rebounder** (E, `primary_player` null) and is **excluded** from the rebound
      reconciliation (which exact-matches `OFFENSIVE`/`DEFENSIVE`). **OOB-offense is a
      third offense-retention path** (with the offensive rebound + §3.7 block
      recovery) against the one `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION` cap (value
      unchanged at 3); the resolver takes `capReached` and never returns a retained
      outcome when capped. **No schema change** (`outcome` free text, #020).
      **Landed free (Decision D verified, not assumed):** the `CalibrationHarness`
      OOB line reads **~3.0 OOB/team/game** and the §3.4/§3.5 aggregates held
      (113.0 pts / 47.0% FG / 27.6 ast / 13.6 TO / 5.1 blk, minutes + FG%-by-period
      intact) — no recalibration pass needed. New `sim` classes at 99.4–100% line
      coverage. Flow: `docs/possession-flow.puml`._
- [x] **§3.9 — Turnover sub-categories (richer causes) ✓** *(landed **free by
      construction** — the turnover count never moved, no recalibration)*.
      _Shipped (decisions.md #027): the pre-§3.9 binary `STOLEN` / `LOST_BALL`
      turnover outcome is replaced by a **weighted draw over nine causes** — a new
      **`TurnoverCause`** enum + **`TurnoverResolver.pickCause`** — run **inside** the
      `if (isTurnover)` block. The **gate (`isTurnover` / `BASE_TURNOVER`) is
      untouched** (A): the draw only re-partitions a turnover that already fired, so
      it can shift the *mix* but never the *count* — freeness is **structural, not
      verified** (contrast §3.8). `STOLEN` kept **dominant (~56%)** so
      `BoxScore.steals` doesn't drift (B); `LOST_BALL` **retired** as the catch-all,
      its old share split across the eight non-`STOLEN` causes. Every cause charges
      the ball-handler (`recordTurnover`, `primary_player` = shooter); `STOLEN` keeps
      the `pickStealer` + `recordSteal()` path. Four causes carry a **modest avg-10
      lean** (C): `SHOT_CLOCK_VIOLATION` (ball-handler `acumen` ↓ + defending
      `defensiveScheme` ↑), `OFFENSIVE_FOUL` / `BAD_PASS` (offense `teamOffense` ↓);
      the rest are flat tier weights, all normalized per-turnover so no lean touches
      the count. `LOST_BALL_OUT_OF_BOUNDS` is deliberately **distinct** from §3.8's
      `OUT_OF_BOUNDS_*` (a `TURNOVER` event vs. a `REBOUND` event, D). §3.7-E
      (second-chance shot-clock pressure) stayed **parked** (D). **No schema change,
      no OpenAPI change, no new `PlayType`** (`outcome` free text, #020). **Landed
      free (aggregates byte-unchanged):** the `CalibrationHarness` per-cause line (E)
      reads **STOLEN 56% / SHOT_CLOCK 9% / OFF_FOUL 9% / BAD_PASS 8% / TRAVELLING 6% /
      the rest ≤4% (OVER_AND_BACK ~2%)**, and the §3.4/§3.5/§3.7/§3.8 aggregates held
      (112.9 pts / 47.0% FG / 37.5% 3P / 27.7 ast / **13.5 TO** / 5.0 blk / 3.0 OOB,
      minutes + FG%-by-period intact) — no recalibration. New `sim` classes at
      95.8–100% line coverage, `PossessionEngine` 99.4%; full `mvn clean install`
      gate green. Seam: `TurnoverResolver` + `SimConfig` + `PossessionEngine`._
- [x] **§3.10 — Loose-ball / rebounding fouls + the team-foul/bonus substrate ✓**
      *(NOT free — recalibrated, points re-agreed at 113.8)*.
      _Shipped (decisions.md #028): a **two-sided non-shooting foul in the rebound
      phase** — a defensive box-out push OR an offensive over-the-back — carved off
      the **top** of the miss flow (C, the §3.7 block-carve shape) and
      short-circuiting the four-way board contest when it fires. New
      `FoulResolver.resolveReboundFoul` + a `ReboundFoul` record; the committer is a
      `foulProne`-weighted draw over the committing five. **Penalty status is
      DERIVED** from the `FOUL` event log (A1) — `GameData.isInBonus(team, period)`
      counts fouls by committing team against `BONUS_FOULS_PER_PERIOD = 5`, with **no
      stored counter and no reset logic** (the #023-F discipline at team level), and
      **emit-then-count** so the **5th foul itself** sends the fouled team to the
      line. Fouls are **two-sided** (A2), so the possession **forks by who fouled**
      (B): defense-commits → the offense retains (under the bonus) or shoots bonus FTs;
      offense-commits → the possession **always ends**, defense's ball or defense's
      bonus FTs. Bonus FTs reuse the existing FT block verbatim (extracted to
      `awardFreeThrows`), so points/FT reconciliation is automatic. **ONE additive
      schema column** — `game_event.committing_team_id` (D), appended to the
      unreleased `release.1.0.4.game.sql`, populated on **every** `FOUL` (including
      `SHOOTING_FOUL` = the defender's team) so the derivation reads one uniform
      field; a deliberate, user-approved reversal of the §3.7–§3.9 schema-free stance,
      earned by a real day-one consumer. **No new `PlayType`, no OpenAPI change** —
      `REBOUNDING_FOUL_DEFENSE` / `REBOUNDING_FOUL_OFFENSE` are `outcome` strings on
      `PlayType.FOUL`. **Recalibrated (E):** §3.10 landed **+2.7 pts** raw, of which
      only ~1.1 was bonus FTs — **the bigger channel is retained possessions**, a
      finding #028 E did not anticipate (noted for §3.11). `BASE_FOUL` proved the
      **wrong lever** (trimming it *raises* points — a shooting foul ends a possession
      for ~1.5 expected FT points, worth less than the live shot it replaces), so the
      fix was the **§3.7 lever in reverse**: shot `BASE_*` rates trimmed ~1.2%.
      Execution also surfaced that `PROB_FLOOR` (0.02) made the new rare-event knob
      **tunable only upward** — fixed with a floor-free
      `SimConfig.rareEventProbability` + its own `REBOUND_FOUL_SENSITIVITY`, the same
      shape `BLOCK_SENSITIVITY` took in §3.7. **Landing (harness, 102 games): 113.8
      pts / 46.9% FG / 37.6% 3P / 27.7 ast / 14.2 TO / 5.0 blk / 2.8 OOB**, minutes +
      period-FG% intact; **fouls 3.95/team/period, 37.1% of team-periods in the
      penalty, rebounding fouls 1.75 def + 0.48 off, bonus FTA 0.8/team/game**. New
      `sim` classes at **100%**, `PossessionEngine` 99.5%; full `mvn clean install`
      gate green (421 unit + 52 Cucumber). Seam: `FoulResolver` + `PossessionEngine` +
      `GameData` + `SimConfig` + the one DDL column._
- [x] **§3.11 — And-1 / shooting foul on a made basket ✓**
      _Shipped (decisions.md #029): a defensive foul on a shot that **still goes in** —
      made FG **+ 1** free throw. Before this, a foul fired *instead of* the shot (the
      pre-shot foul branch returned before the make/miss roll), so a foul and a made
      basket were mutually exclusive. Built as a **second, post-make foul roll** carved
      beside the assist roll (A1/A3) — the pre-shot branch and `BASE_FOUL` left
      **untouched**, so that branch keeps meaning "P(contact stopped the shot)" and the
      and-1 rate stays independently tunable (the §3.7/§3.10 carve shape a third time).
      Scoped to **made DRIVE/POST** (`made && isContactType`, A2) so exactly one new
      scoring source was tuned; all-shot-type contact is §3.12. Awards **one** FT (B) via
      a **per-situation count** through the reused `awardFreeThrows`
      (`AND_ONE_FREE_THROWS = 1`) — it never forks the possession (the make already
      ended it) and **never consults the bonus** (an and-1 is 1 FT by rule). Rate is a
      **rare-event probability** (`rareEventProbability` + `AND_ONE_BASE = 0.055` +
      `AND_ONE_SENSITIVITY = 0.10`, C) — reusing the machine with its own dials from the
      start meant the `PROB_FLOOR`/global-`SENSITIVITY` traps §3.7+§3.10 each hit were
      **never rediscovered**. **Free throws are now self-describing (D):** every
      `FREE_THROW` carries its source as a `MADE_*`/`MISSED_*` suffix
      (`MADE_SHOOTING` / `MISSED_BONUS` / `MADE_AND_ONE`), retiring §3.10's accepted
      ambiguity now that there are three sources; the suffix (not a structured field)
      was chosen because leading with `MADE`/`MISSED` keeps every existing prefix read
      working. **No schema, no OpenAPI, no new `PlayType`** — `AND_ONE` is an `outcome`
      on `PlayType.FOUL` (`committing_team_id = defTeamId`, reusing #028's column).
      **Recalibrated (E):** +2.4 pts raw at the 0.11 placeholder, and — unlike §3.10 —
      **essentially all of it was the FT channel** (+2.35 of +2.4), exactly as the
      pure-additive prediction said, since an and-1 never forks a possession. The
      placeholder was also wrong on realism (11.4% of made contact FG vs. a real ~4–6%),
      so **`AND_ONE_BASE` 0.11 → 0.055** fixed the rate *and* removed ~0.9 of the lift.
      **User call: no shot-`BASE_*` trim** — the measured exchange rate is ~0.6% FG% per
      1.0 point, worse than §3.10's precisely because this lift is free throws (which
      cost no FG%), and §3.12 adds more foul volume anyway, so points are re-centered
      once, there. `BASE_FOUL` untouched (#028's wrong-way finding). **Landing (harness,
      ~102 games × 5 seeds, tuned to the mean): 115.9 pts / 46.8% FG / 37.6% 3P / 27.5
      ast / 13.9 TO / 4.9 blk / 2.8 OOB** — FG%/3P% **on** their §3.4 targets, which is
      what the no-trim call bought; **and-1s 1.67/team/game (6.4% of made contact FG),
      FT-source split SHOOTING 90.6% / AND_ONE 5.8% / BONUS 3.6%**, fouls
      4.41/team/period, 43.4% of team-periods in the penalty. Also recorded: **a zero
      base does NOT switch a rare event off** (the skill term alone keeps it positive
      off an even contest) — only the caller's gate does. `FoulResolver` /
      `FreeThrowSource` / `SimConfig` / `GameData` at **100%**, `PossessionEngine` 99.5%,
      `sim` package 99.1%; full `mvn clean install` gate green (441 unit + 52 Cucumber).
      Seam: `FoulResolver` + `PossessionEngine` + `FreeThrowSource` + `SimConfig` +
      `CalibrationHarness`. **Points at 115.9 is a deliberate deferral to §3.12, not
      drift** — treat it as §3.12's baseline._
- [x] **§3.12 — All-shot-type contact fouls + graduated foul rate + fouled-three = 3 FTs**
      _Shipped (decisions.md #030): the binary `ShotType.isContactType()` **deleted** for a
      per-shot-type foul-multiplier table in `SimConfig` (`FOUL_MULT_DRIVE`/`_POST` = **1.0**
      anchor, `_PERIMETER` = **0.30**, `_THREE` = **0.133** ⇒ a 2% stopped-three rate), one
      shared table driving **both** foul rolls, `BASE_FOUL` renamed **`BASE_NO_BASKET_FOUL`**
      at its unchanged 0.15, and `ShotType.freeThrowsIfFouled()` awarding **3** FTs on a
      stopped three (an and-1 stays **1** for every type). Net schema change: **NONE** — no
      new `PlayType`, `FreeThrowSource`, or `outcome` string.
      **Landing (harness, ~102 games × 5 seeds, tuned to the MEAN): 117.0 pts / 46.4% FG /
      36.7% 3P / 26.8 ast / 13.5 TO / 4.8 blk / 2.8 OOB**; fouls **19.1**/team/game (was
      16.8, ballpark ~19–20), and-1s **1.87**, 3-FT trips **0.59** (2.9% of 3PA), foul-outs
      **0.60**. **NO shot-`BASE_*` trim was taken** — the third consecutive pass to decline
      it. The lift was **+1.1** (not the ~+2.5 the design sized): decomposed, stopped-shot
      **+1.6** (fouled threes +0.71, extra stopped twos +0.89), and-1 **+0.15** (a
      near-non-event, exactly as #030 B predicted), possession-ending **−0.6**. Two findings
      dominate the close-out and each opened a new sub-phase: the re-centering to ~112 was
      **not taken** because the target itself is contested (→ recalibration, **now §3.19**
      per #038 — this said §3.16 when that number meant recalibration; see
      [calibration.md](calibration.md)). ✅ **Vindicated by #036**: the real target is
      **115.6**, so declining the trim was right and ~112 was simply wrong. And the new foul-out instrument found a rate ~2.4×
      its ballpark that **predates this pass** (→ **§3.13**). Coverage: `FoulResolver`,
      `ShotType`, `SimConfig` all **100%**; `PossessionEngine` 99.5% (the same single
      pre-existing uncovered line as §3.10/§3.11); `sim` package **99.1%**. Full
      `mvn clean install` gate green — **455 unit + 52 Cucumber**._
      _Two `CalibrationHarness` repairs rode along, both prerequisites to reading any
      §3.12 number (#030 D/G): the §3.11 and-1 **denominator** was widened from made
      DRIVE/POST to all made FG (leaving it would have silently inflated the printed
      percentage once the numerator widened — an instrument wrong in the direction of the
      change it measures), and the **foul-out line was added** with the end-of-game
      per-player foul distribution. Compare and-1s across the change on **per team per
      game** (1.67 → 1.87), which is denominator-independent; §3.11's "6.4% of made
      contact FG" is not comparable to the new percentage._
- [x] **§3.13 — Foul trouble & foul-outs** *(SHIPPED — decisions.md **#031 A–H** +
      implementation note. Moved AHEAD of flagrants by user call
      2026-08: "it seems more core to the game")*. §3.12's new foul-out instrument (#030 G)
      measured the rate for the **first time** and found it **~2.4× the plausibility
      ballpark** — **0.60 foul-outs/team/game against ~0.1–0.25**, **~70% of it predating
      §3.12** (0.425 at §3.11's exact foul reach), so a §3.5-era problem the arc had been
      carrying unmeasured.
      **The design pass decomposed it first (#031 A) and OVERTURNED the hypothesis the phase
      was created under.** The cause is neither foul volume (19.0/team/game is inside its own
      ballpark) nor purely the missing benching rule: fouls are **over-dispersed by defender
      selection**. `ShotSelector.pickDefender` weights by `individualDefense` (measured sd
      **4.11**, a 3.2× league spread) while `foulProne` is nearly flat (sd 1.22), so the best
      defenders guard — and foul — disproportionately. A flat-rate Poisson over the engine's
      own minutes predicts **0.262**; the measured rate is **0.593**, i.e. **2.3×**. Feeding
      the measured spread in reproduces **0.403** ≈ the 0.425 baseline. **That concentration
      is correct realism, and the engine simply lacks its counterweight — a coach who sits
      the player.**
      **So the pass is purely behavioral**: a **probabilistic** foul-trouble bench rule in
      `RotationState`, scaled by `substitutionAggressiveness` **and** the player's value to
      the team (stars protected *more* — the inverse of §3.5's fatigue rule), a rest-then-
      return cycle with **no timer and no stored state** (#023 F derived-predicate
      discipline), yielding to the never-below-5 invariant. Its one significant mechanical
      cost: it **revises #023 C** by putting an RNG draw in the rotation step, so seed-pinned
      assertions re-baseline. Budgeted trade (#031 G): reaching the ballpark costs ~1–2.5
      minutes off the top starter, which sits at 36.6 against a calibrated ~34–36 — with an
      explicit **stop condition** if it would fall below ~34.
      **Off the table**: `BASE_NO_BASKET_FOUL` (wrong-way lever, #028 — trimming it *raises*
      points), §3.12's `FOUL_MULT_*` (settled on realism, #030 G), `pickDefender`'s weighting
      (would delete correct realism and move calibrated blocks/steals), and points/FG%
      re-centering (recalibration — **§3.19** since #038, not §3.16; and the targets are
      no longer contested, #036 sourced them — see that bullet).
      Moves minutes and fouls → recalibration-adjacent; sequenced before §3.14 so the
      ejection path lands on a rotation that already understands "get this player off" —
      #031 H leaves a three-tier structure (hard/forced · soft/preference · fatigue) for
      §3.14's ejections to extend rather than parallel.
      _Shipped (decisions.md #031 + implementation note, 5-seed mean): **foul-outs
      0.616 → 0.388**, distribution at 4/5/6 `0.83/0.49/0.62` → **`1.00/0.52/0.39`** —
      the intended mechanism (players held at 4–5, fewer converting to 6). Points 117.0 |
      FG% 46.6% | 3P% 36.6% | Assists 26.7 | Turnovers 13.8 | Blocks 5.0 | Fouls 19.0 —
      **the §3.4 aggregates are unmoved**. **G's stop condition did NOT fire**: the top
      starter went 36.6 → **36.1**, i.e. ~0.5 min against a budgeted 1–2.5, and *further
      into* the calibrated ~34–36 band. **The landing deliberately misses the ~0.1–0.25
      ballpark, because the lever is SATURATED** — measured, not assumed: a curve ~3×
      stronger produces ~60% more subs and moves foul-outs by nothing (0.377 → 0.382),
      since #031 D's earned return sends the player back into the same over-dispersed
      defender draw. Reaching ~0.25 needs a lever §3.13 does not own (see #031's
      follow-up); **~0.39 promoted to a soft TARGET** in calibration.md + the harness
      string. One divergence: a fifth constant, `FOUL_TROUBLE_FRESHNESS_MARGIN`, was
      required — D's sticky sit prevents a competing *return* roll but not an immediate
      *re-sit*, which produced measured 1-possession flicker. Coverage: `mvn clean
      install` green, 487 tests._
> **§3.14 SPLIT IN TWO (decisions.md #032 A, design pass 2026-08, user call).** The
> design pass found that technicals and flagrants share **nothing but the word
> "foul"** — different trigger (behavioral vs. a contact by-product), different
> location in the engine (`RotationState` vs. the possession flow), different FT count
> (1 vs. 2), different possession effect (**none** vs. a **retention fork**), different
> disqualification accounting (excluded from vs. counting toward the 6-foul limit), and
> different committer pool (drawn from the floor vs. already picked). Building them as
> one resolver would assert a shared mechanism that does not exist, and would tune two
> independent rate sources at once — the trap **#029 A2** split §3.11/§3.12 to avoid,
> with the same ordering: **the self-contained half first, the structural half second.**
> **§3.14 used the `a`/`b` suffix** rather than renumbering, because §3.16 is named in
> the shipped, never-retro-edited text of #030 and #031.
> ⚠ **§3.16 WAS RESEQUENCED ANYWAY in 2026-08 (user call), TWICE.**
> **Every "§3.16" in #030, #031, #032, #034 and #035 means RECALIBRATION, which is now
> §3.19** — it was §3.16, briefly §3.18 (`decisions.md` #036 F), and is now §3.19
> (**#038**, the current record). ~43 references across those entries. They are shipped
> and not retro-edited.
> **Recalibration is LAST BY RULE, not by position** (#038): every pass before it settles
> the SHAPE of the game — foul mix (§3.16), shot mix (§3.17), event vocabulary (§3.18) —
> and recalibration re-solves the numbers once that shape is final. **Do not append a new
> sub-phase after it.**

- [x] **§3.14a — Technical fouls** *(SHIPPED — `decisions.md` **#032 A–J** + its
      implementation note)*. A non-contact, **behavioral** penalty the engine has no path for: today
      *every* foul is a by-product of a contest (`pickDefender` → `isFoul`), so a foul
      not caused by the possession cannot be expressed. **Deliberately random — no causal
      model at all** (#032 B), rolled per team in `RotationState.advancePossession(rng)`
      (the only "happens to a team, not to a possession" seam), committer drawn from the
      **on-floor five** weighted by `foulProne`, **one** free throw by a **deterministic
      best-shooter** pick beside the untouched weighted draw (#032 G), and **no possession
      change whatsoever** (#032 D) — which is exactly what makes it the cheap half.
      **Its own counter**: technicals do **not** feed the 6-foul limit or the period bonus
      tally (#032 E), so §3.13's calibrated foul-outs and penalty rate must not move.
      **The ejection stays DERIVED** — `technicalFouls >= 2` is a monotonic counter, so
      #023 F applies unchanged and the predicted stored-state exception **does not arise
      here** (#032 F); it extends `eligible(...)` per #031 H. **Also folds in
      #030's clamp-helper consolidation at its fourth site** (#032 H) — that trigger is
      now **retired**; a fifth floor-free site costs one call, not a fourth copy.
      _Shipped (decisions.md #032 + implementation note): technicals **0.367/team/game**
      (5-seed mean) against the 0.35 configured — the ~5% gap is #032 B2's nominal-vs-actual
      pace effect, not drift. **The inverted stop condition (#032 I) held: the §3.4
      aggregates did not move measurably** — points 117.5 (§3.13: 117.0), FG% 46.6%
      (46.6%), 3P% 37.0% (36.6%), assists 26.8 (26.7), turnovers 13.5 (13.8), all inside
      the ±1.5 band. The two leak detectors are clean: **penalty rate flat at 51.2%**
      (51.3%) and foul-outs 0.409 inside their own seed spread. **Ejections landed at
      0.014/team/game, not the predicted 0.00** — ~one per team per season; #032 F's
      estimate was an order of magnitude pessimistic, the mechanism is correct.
      Two divergences: a new `FreeThrowSource.TECHNICAL` (the harness reads FT source off
      the outcome suffix, so reuse would corrupt the instrument §3.14a is judged by), and
      **three** RNG draws per `advancePossession` rather than two (the committer draw is
      unconditional, for the same fixed-point reason). #032 B2's "~0.0035" per-check rate
      is corrected to **~0.00175** — both rotations advance on every possession, so a team
      gets ~200 checks, not ~100. 512 tests green; `sim` **99.2%** line coverage; gate green._
- [x] **§3.14b — Flagrant fouls** *(SHIPPED — `decisions.md` **#034 A–J** + its
      implementation note)*.
      **The structural half — and the design pass found that only ONE of its two
      predicted hard problems is real.** A flagrant is an *additional* severity roll on a
      foul that already happened (no change to any existing foul roll), always **two**
      FTs that **replace** the underlying award, and it **does** count toward the 6-foul
      limit and the period bonus tally (#034 A/C/I). It rides **all three** existing foul
      sites; only the rebounding site can be committed by the offense, which is what
      produces the possession-flipping case.
      **The retention fork is real but cheap (#034 B):** a flagrant awards FTs **and
      returns the ball**, breaking #030 B's invariant that every existing FT path ends
      the possession — **but the second-chance `while (true)` loop already is the "same
      team, run it again" machine**, so retention is the same `offensiveRebounds++;
      continue;` §3.7/§3.8/§3.10 use, **under the same cap**. Five lines, not a new
      mechanism.
      **The stored-state exception is NOT real, and the prediction is RETIRED (#034 F).**
      #031 H and #032 F both held that a flagrant-2 is "a severity grade with no counter
      behind it" and would finally force #023 F's exception. It doesn't: a flagrant-2
      ejects **immediately**, so there is no threshold to remember and `flagrantTwos >= 1`
      is a monotonic counter exactly like `fouls >= 6`. It stays **derived**, extending
      the shared `RotationState.isDisqualified(p)` predicate behind `eligible(...)`
      (#031 H) to a third cause — **not** a fourth removal path. **Every disqualification
      in the model is absorbing and monotonic**, which is the shape #023 F's derivation
      was built for.
      Rate ~0.25–0.40/game league-wide (~0.16/team); **the coarsest row in
      calibration.md** (~33 events at 102 games, 17.4% relative sd — 5 seeds minimum).
      Budgeted at **+0.43 points/team/game** across two channels, deliberately
      over-estimated and still sub-noise, so **#032 I's inverted stop condition applies
      again** — measurable aggregate movement is a bug, not a calibration result.
      **Was billed as the last new mechanic in Phase 3** — ⚠ **AMENDED by #036 G**:
      §3.15 (profiles) and recalibration still add no possession branch, but **§3.16
      did** — it added two forks (`COMMON_FOUL` and the charge's `FOUL`), both now drawn.
      `possession-flow.puml` is structurally current as of §3.16; §3.17/§3.18 may add more.
      _Shipped (decisions.md #034 + implementation note): **no design divergence** — A–J
      shipped as written. Flagrants **0.148/team/game** (5-seed mean) against the 0.16
      configured, inside the 7.8% relative sd this line resolves at; flagrant-2s 0.023 and
      **ejections 0.027**, against #034 F's predicted ~0.024 — **roughly double §3.14a's
      0.014, so the hard tier is finally exercised**. **The inverted stop condition (#034 J)
      held**: points 118.3 (§3.14a: 117.5), FG% 46.9% (46.6%), 3P% 36.7% (37.0%), assists
      27.1 (26.8), turnovers 13.6 (13.5) — **+0.76 points**, inside the ±1.5 band and
      between J's over-estimated +0.43 budget and §3.14a's own budget-vs-landing precedent.
      **None of J's three bug signatures present**: FT/points reconciliation exact, the
      retention loop stops at the cap (asserted at all three sites), penalty rate flat at
      **51.9%** (51.2%) with a test pinning that `isInBonus` counts a flagrant; foul-outs
      0.358, untouched. **The #023 F stored-state exception was refused a THIRD time and
      the prediction is retired** — `flagrantTwos >= 1` behind the shared `isDisqualified`,
      no flag, no fourth removal path. Three execution findings: a flagrant **replaces**
      the underlying foul's EVENT (so `Fouls / team / game` does **not** rise — 19.35 vs
      ~19.4 — unlike §3.14a's technical, and the harness caveat was corrected); `capReached`
      hoisted to the loop top, removing a pre-existing duplicate pair; and a **latent §3.14a
      bug in `PossessionEngineTest.scoringTeamId`** surfaced by the RNG shift — a
      `MADE_TECHNICAL` FT shot by a defender was attributed to the wrong team, fixed by
      replacing the suffix list with the actual rule ("scores for whoever did not commit
      the foul"). One seed re-baselined by re-derivation (7L → 9L, a non-vacuity
      precondition); `RotationStateTest` needed none, as #034 A predicted. **542 tests
      green** (from 513); `sim` **99.2%** line coverage; gate green._
- [x] **§3.15 — `SimConfig` profiles** *(**SHIPPED** — designed as `decisions.md` #035 A–I,
      built 2026-08. Promoted from a backlog chore to a numbered phase by user call,
      2026-08.)*
      _Shipped (decisions.md #035): **57 constants converted to initializer-free instance
      state** on `SimConfig`, bound by `@ConfigurationProperties(prefix="sim")` +
      `@Validated` from `application-baseline.properties` — **their only copy**; **26 stay
      `public static final`** (9 rules + 16 machinery + `PERSONAL_FOULS_PER_TEAM_GAME`).
      **323 call sites converted (84 main, 239 test), no static aliases.** Two profile
      files ship (`baseline`, plus an **illustrative, untuned** `nineties`); profiles are
      ordinary Spring profiles (`local,baseline`). Harness gained an **effective-config
      dump** + baseline-only targets (era runs print deltas). **The validation gate HELD
      EXACTLY, per-seed on baseline** (seeds 1000–5000): 118.3 pts / 46.9% FG / 36.7% 3P /
      27.1 ast / 13.6 TO / 51.9% penalty / 0.358 foul-outs / **0.148 flagrants** — byte-for-
      byte identical to §3.14b, with no RNG draw added, removed or reordered.
      `nineties` reads 99.7 pts / 72.9 FGA — different, which is the goal, and not tuned.
      **548 tests green, coverage gate met**; `possession-flow.puml` unchanged.
      Three additions the design did not anticipate: `@ConfigurationPropertiesScan`,
      a `spring-boot-starter-validation` dependency (the project had no Bean Validation
      impl, so `@Validated` failed the context), and **the harness's hardcoded
      `POSSESSIONS_PER_PERIOD = 25`, which silently shadowed the profile's pace knob** —
      caught by the effective-config dump, the concrete case for it being load-bearing._ Load the tunable constants from
      **named, swappable profiles** instead of compile-time constants, so tuning becomes
      **comparable experiments** rather than sequential edits (change a constant, rebuild,
      run, write the number down — the previous config gone unless someone remembered it).
      **The GOAL, stated by the user (2026-08): author several named profiles for
      different LEAGUE STYLES — a 1990s low-pace/high-foul style, a modern three-heavy
      style, plus the shipped baseline — run the harness against each, and compare the
      generated stat lines between them.**
      **THE RESOLVED SHAPE (#035):** **57 of the 83 constants become INSTANCE state on the
      existing `SimConfig` bean**, loaded from flat properties profiles carrying **only
      deltas** (#035 A); **26 stay `public static final`** — 9 rules,
      16 model-machinery entries (the sensitivities plus the two **scale definitions**,
      `SCALE_AVG` and `MAX_ENERGY`), and `PERSONAL_FOULS_PER_TEAM_GAME` (#034 G's
      measured assumption). The invariant reads off the source: **`static final` means a
      rule or the shape of the model, not a knob.** An unknown or non-profilable key is a
      **startup failure** (#035 D). Profiles are ordinary **Spring** profiles composed with the
      existing infrastructure ones (`local,baseline` / `test,nineties`), bound by
      `@ConfigurationProperties(prefix="sim")` — no custom loader. The harness selects one with
      `-Dspring.profiles.active=` beside
      the existing `-DcalibrationSeed=NNNN`, **one profile per invocation** (#035 F), and
      prints an **effective-config block** naming every constant's origin.
      ⚠ **Why instance and not `static` non-final — the finding that sized the pass
      (#035 B):** a `public static final` primitive initialized from a literal is a JLS
      §4.12.4 *constant variable*, so **javac inlines it into every caller's bytecode** and
      `SimConfig` is never read at runtime. A properties file cannot override such a field,
      and a `static final` "compatibility alias" kept so tests compile would be baked into
      each test class at *its* compile time — a test could assert 0.3375 while the engine
      ran 0.31, silently. **This is also why §3.11's `-DandOneBaseOverride=0` flag did
      nothing.** Instance rather than static-mutable because a profile must eventually be a
      **per-simulation** input in a concurrent Spring app. **Blast radius: 323 call sites —
      84 main, 239 test.**
      **Scope fence:** §3.15 is **engine + harness — no schema, no OpenAPI, no
      persistence**. The instance conversion **unblocks** per-game profiles in the running
      app (the user's stated destination) but deliberately does not build them: that needs
      persistence of which profile a game/league ran plus an API surface, and is
      **Phase 4+** (#014/#017 — build the substrate, stop there).
      ⚠ **Calibration targets are BASELINE-ONLY (#035 I)** — an era profile is *supposed*
      to miss them, so off-baseline the harness suppresses the target strings and prints
      **deltas against §3.14b's landing** instead. §3.15 must not invent a second target
      set; target *sourcing* was owned by a separate research chore — ✅ **done by #036
      (2026-08)**, which sourced the five §3.4 targets against Basketball-Reference
      2025-26. *(This line said "§3.16 owns target sourcing" when §3.16 meant
      recalibration; #038 renumbered that to §3.19, and the sourcing landed earlier.)*
      **Timing**: placed after the fidelity arc per backlog.md's "do NOT do this mid-arc"
      note — changing how constants load *while* actively tuning them forfeits exact
      reproducibility of a prior landing. Placed *before* §3.16 deliberately: the
      recalibration is the largest multi-config sweep the project will run, and this is the
      tool for it. **Validation gate**: §3.15 must reproduce **§3.14b's** shipped landing
      **exactly — per-seed, on the BASELINE profile** — before any §3.16 number is read off
      it (the same check that validated the §3.11 harness against §3.10's numbers). An era
      profile producing different numbers is the goal working, not a failure.
- [x] **§3.16 — Shooting-foul composition + the charge correctness fix**
      _Shipped (decisions.md **#039** A–H, no design divergence): a **second roll layered
      on the already-charged foul** re-partitions `SHOOTING_FOUL` into a
      free-throw-free **`COMMON_FOUL`**, and charges become personal fouls (#037).
      **74.8% of fouls were `SHOOTING_FOUL`, producing 29.84 of 34.0 FTA**; they are now
      **35.6%**. **Landing (5 seeds): FTA 34.0 → 23.60** against the sourced 23.5 ✅ ·
      **FGA 88.80** (89.1 real — the binding tripwire, held, and it moved *toward* real)
      ✅ · **FG% 46.68** (47.1, inside noise) ✅ · fouls **20.53** · foul-outs 0.517 ·
      penalty rate 55.7%. 562 unit + 52 Cucumber tests green, coverage gate met._

      ⚠ **POINTS LANDED AT 109.6 AND THAT IS BY DESIGN, NOT A REGRESSION** (#039 H).
      Removing ~10 free throws costs ~8 points; **§3.19 owns recovering it** with a ~5%
      pace bump, and #038's rule is that recalibration re-solves the numbers once the
      SHAPE is final. Do not read this bullet as a phase that made the engine worse, and
      do not "fix" it before §3.17 has moved the shot mix.

      ⚠ **The share shipped at 0.50, not the 0.43 #039 D predicted, and the two steps
      turned out COUPLED.** The charge fix raises team-periods in bonus 51.1% → ~56%, so
      more converted fouls award 2 bonus FTs and each conversion removes ~1.47 FTs
      instead of 1.664. **Anything that moves the penalty rate re-prices this share** —
      §3.19 must re-check FTA rather than trust the constant. It remains **derived, not
      sourced**: the real NBA shooting-foul share is still unsourced.

      ⚠ **The possession ENDS on a common foul — the ball does not come back** (#039 C),
      which is deliberately wrong as basketball. A retaining variant is capped at a ~6%
      share by FGA's 0.7 of headroom, which moves FTA by less than 1.0. **Revisit only
      if §3.17's shot-mix work buys FGA headroom.**

      ⚠ **`PERSONAL_FOULS_PER_TEAM_GAME` was re-measured to 20.15** (from 19.0) — the
      flagrant divisor, and #034 G forbids letting it drift. **A pass that moves the foul
      rate must decide it again**, deliberately.

- [ ] **§3.17 — Shot mix / the 3PA gap** *(needs its own design pass; **deliberately
      UNSCOPED** — `decisions.md` **#036 D**)*. **The engine takes 19.9 3PA against the
      NBA's 37.0 — 54% of real, the largest single divergence and ~60% of the
      composition error.** 3P% itself is fine (36.7 vs 36.0), so this is **volume, not
      accuracy**.
      ⚠ **THE FIRST DESIGN QUESTION IS WHETHER THIS IS AN ENGINE PROBLEM AT ALL.**
      `ShotSelector.pickShotType` draws on `PlayerGameState.shotTypeWeight(type)` — i.e.
      **per-player skills** — leaned by the coach's `offensiveScheme` through
      `shotMixLean`. **That lean scales PERIMETER and THREE together**, so it cannot
      raise threes while lowering mid-range, which is the shape the real gap needs. If
      the gap traces to the generated player population's skill distribution, the fix is
      **upstream of the possession engine** (player generation / seeded rosters) and this
      is not a `SimConfig` phase. **Answer that before designing any knob.**

- [ ] **§3.18 — The steal as a first-class event** *(needs its own design pass; added
      2026-08 by user call)*. **A steal is the only contested defensive play the event
      log does not attribute.** At `PossessionEngine:174–181` the stealer is picked
      (`pickStealer`), credited in the box score (`recordSteal()`), and then **dropped**:
      the emitted `TURNOVER` event carries `primaryPlayerId = shooter` — **the player who
      LOST the ball** — and the stealer's identity exists only in the box-score column.
      **The asymmetry against the other two contested credits:** an assist rides
      `assistPlayerId` on the SHOT event; a block gets its own `BLOCKED_*` SHOT event and
      a §3.7 reconciliation invariant. A steal gets neither, so **"who stole it" is
      unanswerable from the event log** and no event-vs-box-score check is possible on
      the creditor.
      **Why it is this way**: steals arrived with the §3.4 turnover model, where a steal
      was a *property of a turnover* rather than a defensive play. §3.9 then re-partitioned
      turnover causes and explicitly kept the steal path "unchanged" (#027 A), so the gap
      was carried forward rather than examined. **Not a bug** — the box score is correct
      and the rate is calibrated — **a fidelity/parity gap**, in the class §3.7 closed for
      blocks.
      **Open for its design pass**: whether the stealer rides the existing TURNOVER event
      (a second participant column, which the schema may not have — check #028 D's
      `committing_team_id` precedent and its OpenAPI follow-up), or gets its own event the
      way a block does; and whether that is worth a schema change, which every sub-phase
      since §3.10 has avoided.
      ⚠ **Do NOT bundle this with the harness's steal REPORTING** — that is a
      backlog.md chore, is test-only, needs no engine change, and **a count-based
      reconciliation (`STOLEN` events vs. box-score steals) already works today**. Do the
      cheap measurement first; it may show the rate is fine and this phase is pure parity.

- [ ] **§3.19 — Recalibration against verified targets** *(needs its own design pass;
      **was §3.16, then §3.18** — see the mapping callout above and `decisions.md` #036 F
      / #037. **THE LAST Phase-3 sub-phase, and it must stay last**: every pass before it
      settles the SHAPE of the game — the foul mix (§3.16), the shot mix (§3.17), the
      event vocabulary (§3.18) — and recalibration re-solves the numbers **once that
      shape is final**. A different KIND of pass: it adds no mechanic.)*. **A second §3.4.** See [calibration.md](calibration.md).
      ✅ **Job (1) — sourcing — is DONE** (Basketball-Reference league averages, per game,
      **2025-26**), and it made this pass **smaller than it was scoped for**:
      **FG% was never contested** — real 47.1% vs the engine's 46.9%, a gap **inside the
      ±0.14 standard error** of the 5-seed mean, so the *target* was wrong and no engine
      work is owed (#036 A). Points' target moves ~112 → **115.6**, leaving a **+2.7**
      gap that is itself **largely an artifact** of three cancelling composition errors
      (2-pt +10.9, 3-pt −18.0, FT +7.1 — #036 B).
      **Sequenced LAST on the calibration-blast-radius principle**: §3.16 moves FTA by
      ~10 and §3.17 moves 3PA by ~17, so recalibrating first would tune against a
      baseline both invalidate — the same stale-anchor error three passes already paid
      for (§3.10/§3.11/§3.12).
      **Still unsourced and owed:** foul-outs (its ~0.39 "target" came from §3.13's own
      landing — circular), technicals, flagrants, the minutes distribution, and the real
      shooting-foul share. **Exit condition:** every `calibration.md` row is either a
      `TARGET` with a named source and season, or deliberately `observed`/`ballpark`.

_(A future defensive-fidelity or Phase-4 stats pass may surface more; add new
numbered sub-phases here rather than reopening a catch-all deferred bucket.)_

---

### Phase 3 → Phase 4 gate: the documentation condense pass

**Deliberately NOT numbered `§3.17`** (user call, 2026-08). Every `§3.x` in this
section is an **engine mechanic**, and §3.14b was on record as *the last new mechanic
in Phase 3* (⚠ **amended by #036 G** — §3.16 adds one, and **it shipped: two branches**,
the `COMMON_FOUL` fork and the charge's `FOUL`) (#034) — numbering a docs pass alongside them would imply the possession
arc continues and would contradict that. It is **Phase 4 pre-work**: an explicit exit
criterion on Phase 3, not an optional chore that slides.

- [ ] **Condense `decisions.md` before Phase 4 starts.** The plan already exists — it
      is [backlog.md](backlog.md)'s parked "Condense `decisions.md`" entry, which
      carries the what-to-compress / what-to-keep split, the one-file and never-renumber
      constraints, and the index-plus-preamble idea. **No design pass needed**; that
      entry *is* the plan. Re-measure before starting — its figures are stale.
      **The trend is the argument.** When that entry was filed the file was ~170k chars
      with §3.x entries averaging ~16k. Measured 2026-08 after §3.15's design pass:
      **445k chars** (re-measured after §3.16), and the **nineteen** engine entries
      average **22.0k** — ~94% of the file. The five largest were all written *before*
      the size cap (#030 53.8k, #031 47.6k, #032 44.9k, #034 44.4k, #035 35.3k).
      **The trend has been arrested and has now REVERSED**: the `project-docs` size cap
      produced **#035 at 22.3k** and **#039 (§3.16) at 21.4k** including its
      implementation note, and the engine-entry average has fallen for the first time
      (24.4k → 22.0k).
      **Why it waits for RECALIBRATION rather than running now** — ⚠ **this said "§3.16"
      when that number meant recalibration; #038 renumbered it to §3.19, so the gate is
      after §3.19, three sub-phases later than a literal reading suggests.** Same gate
      reasoning the backlog entry used for §3.11, one arc later: **recalibration is the
      single heaviest consumer of this file.** It will reach for #028's wrong-way-lever
      finding, #030's exchange-rate measurements, #031's saturation result, #034's
      emergent-divisor coupling and now **#039's penalty-rate/share coupling**.
      Compressing first risks cutting exactly what it needs while nobody yet knows which.
      After §3.19, §3.7–§3.19 is a closed arc that goes historical at once and the
      cross-refs actually reached for are **known rather than guessed**.
      **Why it must not slide past Phase 4's start**: Phase 4 is a stats/consumer phase
      whose reader wants "can I add a column?" — a `#014`/`#017`/`#020` one-liner — and
      would otherwise scroll past 366k of engine reasoning to find it. That is the exact
      two-readers-one-file problem the backlog entry describes, and Phase 4 is when the
      second reader arrives.
      ⚠ **Both entries anticipated here have now landed** (#035 for §3.15 at 22.3k, and
      #039 for §3.16 at 21.4k — #036/#037/#038 were small). Neither is oversized by the
      old standard. The `project-docs` skill carries a proportionality rule (added 2026-08)
      so they do not re-grow at the 44k trend — but they will still need compressing
      here.

---

## Phase 4 — Statistics & Box Scores

**Goal**: Track, aggregate, and expose stats.

### 4.1 Game Stats Model
- [ ] Per-game player stats: points, rebounds (off/def), assists, steals, blocks, turnovers, fouls, minutes, FGA/FGM, 3PA/3PM, FTA/FTM
- [ ] Per-game team stats: same aggregated, plus pace, efficiency rating
- [ ] Persist to database

### 4.2 Season Stats Aggregation
- [ ] Season averages per player
- [ ] Season totals per player
- [ ] Team season stats
- [ ] League leaders / rankings

### 4.3 Stats APIs
- [ ] `GET /v1/player/{playerId}/stats` — season stats
- [ ] `GET /v1/player/{playerId}/gamelog` — game-by-game log
- [ ] `GET /v1/team/{teamId}/stats` — team stats
- [ ] `GET /v1/league/leaders` — league leaders by category

---

## Phase 5 — Season Structure

**Goal**: Full season lifecycle — schedule, standings, playoffs, awards.

### 5.1 Schedule Generation
- [ ] Regular season: N games per team, balanced home/away
- [ ] Conference-weighted scheduling (more intra-conference games)
- [ ] Calendar-based game dates

### 5.2 Standings & Tiebreakers
- [ ] Win/loss record, conference record
- [ ] Division standings (if divisions are added within conferences)
- [ ] Tiebreaker rules
- [ ] Playoff seeding

### 5.3 Playoffs
- [ ] Bracket generation from standings
- [ ] Best-of-N series format
- [ ] Series simulation

### 5.4 Season Simulation APIs
- [ ] `POST /v1/season/create` — generate a new season with schedule
- [ ] `POST /v1/season/{id}/simulate-day` — simulate one day of games
- [ ] `POST /v1/season/{id}/simulate-all` — run entire season
- [ ] `GET /v1/season/{id}/standings` — current standings
- [ ] `GET /v1/season/{id}/schedule` — full schedule with results

---

## Phase 6 — Player Progression & Off-Season

**Goal**: Multi-season continuity with player development and aging.

### 6.1 Aging & Development
- [ ] Define age curves: when do attributes peak and decline?
- [ ] Young players: attribute growth between seasons based on yearsPro, determination, intelligence
- [ ] Veterans: gradual physical decline (speed, agility, energy) offset by mental gains (intelligence, shotSelection)
- [ ] Breakout/bust probability for young players

### 6.2 Injury System
- [ ] Injury probability model: `health` attribute, fatigue level, play type (drive/post riskier)
- [ ] Injury severity tiers: minor (miss games), moderate (miss weeks), major (miss season)
- [ ] Recovery and rehab affecting attributes on return
- [ ] Status transitions: ACTIVE <-> INJURED

### 6.3 Draft
- [ ] Generate draft class of rookies with semi-random attributes
- [ ] Draft order based on inverse standings
- [ ] Draft pick evaluation (GM scouting attribute affects accuracy of prospect assessment)

### 6.4 Free Agency & Trades
- [ ] Contract model: years, salary
- [ ] Salary cap
- [ ] Free agent signing period
- [ ] Trade logic: player-for-player, picks, salary matching
- [ ] GM attributes influence trade evaluation (model as continuous 1–20 like
      coach #018 — see coach.md open-Q #3)

---

## Phase 7 — React Frontend

**Goal**: Browser-based UI for viewing and interacting with the simulation.

### 7.1 Core Views
- [ ] League dashboard — all 40 teams by conference
- [ ] Team detail — roster, coach, GM, current record (surface coach attributes +
      a derived archetype label, computed on read — see coach.md open-Q #1)
- [ ] Player detail — attributes, skills radar chart, stats, game log
- [ ] Game view — box score, play-by-play

### 7.2 Season Views
- [ ] Standings page with conference tabs
- [ ] Schedule calendar
- [ ] Playoff bracket visualization

### 7.3 Management Views
- [ ] Lineup editor (drag-and-drop starters/bench)
- [ ] Roster management (add/drop/trade)
- [ ] Draft board

### 7.4 Simulation Controls
- [ ] "Simulate next game" / "Simulate day" / "Simulate week" buttons
- [ ] Season progress indicator
- [ ] Live game simulation with play-by-play feed (websocket or polling)

### 7.5 Tech Stack
- [ ] React + TypeScript
- [ ] Component library (TBD: Material UI, Tailwind, etc.)
- [ ] State management (TBD: React Query for server state, Context/Zustand for local)
- [ ] Chart library for player radar charts and stat visualizations

---

## Phase 8 — Polish & Advanced Features

**Goal**: Depth and replayability.

- [ ] Awards: MVP, Rookie of Year, All-League teams, Defensive Player of Year
- [ ] Historical records: track season-over-season stats
- [ ] Coach hiring/firing between seasons
- [ ] GM hiring/firing
- [ ] Player morale/chemistry system (charisma, cohesion, ego interactions)
- [ ] Home court advantage modifier
- [ ] Rivalry bonuses
- [ ] Pre-season / exhibition games
- [ ] All-Star game
- [ ] Export/import save state

---

## Design Decisions To Make

These are open questions that should be resolved before or during implementation:

1. **Simulation granularity**: Possession-by-possession (detailed, slow) vs. quarter-level (faster, less detail) vs. configurable?
2. **Game clock model**: Real seconds ticking down, or abstract possession count per quarter?
3. **Coach attribute design**: Continuous attributes (1-10 scale like players) or categorical styles (enum-based)?
4. **Salary/contract complexity**: Simple (flat salary, fixed years) or realistic (cap exceptions, bird rights, max contracts)?
5. **Draft class generation**: Fully random, template-based archetypes, or a mix?
6. **Frontend-first or API-first for new features?**: Build APIs then UI, or design UI mockups first?
7. **Real-time simulation**: Should game simulation stream play-by-play via WebSocket, or generate all at once and let the frontend replay?
8. **Multi-user**: Is this single-player (user controls one team) or spectator-mode (AI runs everything, user watches)?
9. **Persistence strategy for game events**: Store every possession in the DB, or only final box scores?
10. **Season length**: How many games per team per season? (NBA is 82 — that's a lot of simulation data)

---

## Suggested Build Order

The phases above are roughly sequential, but here's the critical path:

```
[Foundation ✓] ──> Phase 2 (Rosters) ✓ ──> Coach model ✓ ──> Phase 3 (Game Engine) ──> Phase 4 (Stats)
                                                        │
                                                        v
                                                Phase 5 (Season) ──> Phase 6 (Progression)
                                                        │
                                                        v
                                                Phase 7 (Frontend) ──> Phase 8 (Polish)
```

Phases 1-3 are the core loop. Once you can simulate a game and get a box score, everything else builds on top. The frontend can start in parallel with Phase 4+ once the game simulation API exists.
