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
at `SimConfig.MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` (a loop, not recursion).
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

### Possession-fidelity completion (§3.7–§3.11) — before Phase 4

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
> emit-then-count. §3.11 reuses it as-is plus the extracted
> `PossessionEngine.awardFreeThrows` block; no further substrate work is needed.

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
      recovery) against the one `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` cap (value
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
- [ ] **§3.11 — And-1 / shooting foul on a made basket** *(biggest recalibration)*.
      Today a foul check happens *instead of* a shot (`PossessionEngine`: the foul
      branch returns before the make/miss roll — drive/post → foul → 2 FTs, never
      *with* a made shot). A real and-1 is: made FG **+** 1 bonus free throw. Needs
      the foul model to roll **alongside** (not before) shot resolution — the more
      invasive change, restructuring the possession branching. Adds points to the
      system (bonus FTs that don't exist today) → pushes pts above ~112 → re-tune
      `BASE_FOUL` / FT rate to re-center. Reuses the team-foul/bonus substrate from
      §3.10.

_(A future defensive-fidelity or Phase-4 stats pass may surface more; add new
numbered sub-phases here rather than reopening a catch-all deferred bucket.)_

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
