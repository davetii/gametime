# Gametime Project Plan

Basketball simulation game — 40-team league with attribute-driven gameplay, season management, and a React frontend.

_Last updated: 2026-09. **Next: the pre-work gate below.**_

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
- **Possession engine — ALL of Phase 3 (§3.1–§3.22, shipped 2026-06 → 2026-09)** — a full
  seeded, deterministic possession simulation with the complete foul system, minutes/fatigue/
  substitution, and **63 tunable constants in swappable profiles**. Persists a `GameEvent` log +
  per-player `BoxScore` behind the §3.6 APIs. Calibrated against sourced modern-NBA targets;
  every row lands but four reported residuals (Blocks −0.26, Fouls −0.55, OffReb +0.60, DefReb −0.85).
  Flow: **game.md** / **possession-flow.puml** (the overview — each resolver has its
  own diagram). Targets: **calibration.md**.
  ⚠ Traps and measured findings: **engine-traps.md**. Per-sub-phase record: **decisions.md #020–#044**.
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

## How to read this file

**Phases are dependency-ordered, not calendar-ordered** — no dates or quarters,
because what gates a phase is the phase before it, not the month. `- [ ]` is not
started, `- [x]` is shipped, ✓ marks a complete phase.

⚠ **Bullets are seams, not plans.** Every one is deliberately under-specified and
needs its own design pass (a `decisions.md` #NNN + an execute-ready `todo.md`
plan) before execution — every phase so far has found real design questions the
one-liner hid. **Do not execute a bullet as if it were a plan.**

**Next up is the gate below, not Phase 4.** Shipped work is summarized in *What
Exists Today*; its record is `decisions.md`, not this file.

---

## Now — Phase 3 → Phase 4 pre-work (a GATE)

⚠ **Phase 4's design pass does not start until these are done.** It is its own
section deliberately: mixing gate work into the phase is how a gate becomes a
backlog item that ships late or not at all. **Order is dependency-driven** — 2+3
are one pass, 5 depends on 4, 8 must be last.

- [x] **1.** Rewrite `todo.md` ✅ *(2026-09)*
- [x] **2.** Condense `decisions.md` ✅ *(2026-09)* — SPLIT by audience, not condensed in place:
      findings → **[engine-traps.md](engine-traps.md)**, entries cut to their decision
      letters. 599k → 38k + 20k. ⚠ Never renumber: ~1,900 citations resolve by number **and letter**.
- [x] **3.** Sweep the `sim` package's Java comments — ⚠ **not coupled to 2**; only renumbering
      would break a citation. ⚠ Fix here: `SimConfig.java` (~L75, ~L176) still warns that
      `base-no-basket-foul` runs the wrong way — #036 disproved it and the properties file
      already disagrees.
- [x] **4.** `possession-flow.puml` ✅ *(2026-09)* — **SPLIT into an overview + six
      detail diagrams**, after first cutting the prose and moving the 279-line legend to
      **possession-flow-model.md**. ⚠ **The real ceiling was never PlantUML's 16,384px —
      it was the SCREEN.** Thinning alone got 13,800px → 7,365px, still ~15 pages and
      unreadable. Every diagram is now screen-sized (549–2,102px), and the overview is
      the first artifact that shows the second-chance **loop as a loop**.
      ⚠ **Ownership rule**: a fork inside a resolver → that resolver's file; a new
      resolver or **a new retention path** → the overview too.
- [x] **5.** `game.md` dedup against the diagrams ✅ *(2026-09)* — 48k → 41.6k. The
      **event walk and the 18 emission patterns were cut** (the diagrams and
      **game-events.md**'s test-enforced master table own them); the **calculation
      sequence absorbed them** and is now one flow section, not two. ⚠ **What game.md
      uniquely owns is the ORDERING RATIONALE** — why steps cannot be reordered, which
      a diagram cannot state ("because the method returns"). Three facts that lived
      nowhere else were rescued into it: the **FGA-headroom arithmetic** behind #039 C,
      the cap's **`OUT_OF_BOUNDS_OFFENSE`→`OUT_OF_BOUNDS_DEFENSE`** forcing pair, and
      sail-out/tipped-OOB sharing one outcome. The shot-type skills table went to
      **player.md**, which already owned that view. All 27 `#NNN` citations preserved.
      ⚠ **The §-list was also STALE** — it stopped at §3.16 while the body cited
      §3.17–§3.22; rebuilt, which is why the net cut is only 13%.
- [ ] **6.** Triage `backlog.md`
- [ ] **7.** Adopt Beads and cut over for Phase 4 work
- [ ] **8.** Re-read every `calibration.md` verdict against its own Current number — **last**

**The how, and the traps for each, live in `todo.md`** when the gate is the active
work. **The roadmap ↔ Beads boundary:** Beads holds actionable work with state;
this file holds phase structure and seams. Phases 4–8's bullets do **not** migrate
— a seam turned into a ticket lies about its readiness. When work becomes a bead,
the **bead is the only record** and the doc entry is deleted.

---

## Phase 4 — Statistics & Box Scores

**Goal**: Track, aggregate, and expose stats.

> ⚠ **Worth reading before designing 4.1: stats are written TWICE and the two paths agree
> only by convention** — `PlayerGameState.record*()` counters and the `GameEvent` for the
> same play. §3.20 shipped (and fixed) a bug that is one realisation of it. **Not a gate
> on this phase**, and not scheduled — but if the box score is ever to be **derived from
> the event log**, this phase's design is the natural place to ask. Full write-up in
> [risks.md](risks.md); parked as an idea in [ideas.md](ideas.md).

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

> ⚠ **READ [risks.md](risks.md)'s "Skill sensitivity is ~10× too steep" BEFORE designing
> this phase.** Season play is the **first consumer of the engine's response to skill
> SPREAD** — every simulation to date has been average-vs-average, because that is what
> `CalibrationHarness` builds. Measured, an elite defense holds an average offense to
> **16.2% FG** and a terrible one concedes **79.8%**, against a real spread of ~5 points.
> **Left unaddressed, good teams will beat bad teams by impossible margins and the
> standings will be degenerate — and the symptom will look like a scheduling or standings
> bug, not an engine one.** It is a tuning problem with a cheap fix, but it needs a
> skill-LADDER harness mode first, because the current report cannot show a slope.

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

Open questions to resolve before or during the phase that needs them.

1. **Salary/contract complexity** (Phase 6.4): simple (flat salary, fixed years) or realistic
   (cap exceptions, bird rights, max contracts)?
2. **Draft class generation** (Phase 6.3): fully random, template-based archetypes, or a mix?
3. **Real-time simulation** (Phase 7): stream play-by-play over WebSocket, or generate all at
   once and let the frontend replay?
4. **Multi-user** (Phase 7/8): single-player (user controls one team) or spectator mode?
5. **Season length** (Phase 5): games per team per season — 82 is a lot of simulation data.

⚠ **Already decided — do not reopen**: simulation granularity and the clock model
(possession-by-possession, no clock — **#021 B**, **#024 E**); coach attributes
(continuous 1–20, not enums — **#018**); event persistence (every `GameEvent` stored,
events are the source of truth — **#020**).

---

## Build order

Phases 1–3 are shipped. The remaining path: **Phase 4 (Stats) → Phase 5 (Season) →
Phase 6 (Progression)**, with **Phase 7 (Frontend)** able to start in parallel from
Phase 4 onward (the §3.6 simulation API it needs already exists), and **Phase 8** last.
