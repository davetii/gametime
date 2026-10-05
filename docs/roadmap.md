# Gametime Project Plan

Basketball simulation game — 40-team league with attribute-driven gameplay, season management, and a React frontend.

_Last updated: 2026-10. **Next: Phase 4 execution** (designed as decisions.md #045)._

## What Exists Today

### Shipped
- **Player and skill model**: 21 attributes and 23 derived skills on a 1–20 / avg-10 scale,
  9 positions, player status and lineup role as separate axes. See [player.md](player.md).
- **League data**: 40 teams across 4 conferences (EAST, NORTH, SOUTH, WEST), 422 seed players
  and 40 coaches, loaded through Liquibase. GM is name only.
- **Roster and lineup**: a team's roster is part of the `Team` resource; lineup is sticky state
  on `player_team`; size caps of 15 active / 5 minors; no position constraints. See
  [roster.md](roster.md).
- **REST endpoints**: league, player (get, create, update, history), team (get, add and remove
  player, set lineup), and the game endpoints (simulate, get, play-by-play).
- **Possession engine (Phase 3, §3.1–§3.22)**: a seeded, deterministic possession simulation
  with the full foul system, minutes, fatigue and substitution, tunables in swappable Spring
  profiles, and a persisted `GameEvent` log plus per-player `BoxScore`. Calibrated against
  sourced modern-NBA targets. See [game.md](game.md), [calibration.md](calibration.md) and
  [engine-traps.md](engine-traps.md).
- **Infrastructure**: Postgres (local) and H2 (tests), Liquibase, multi-module Maven with
  OpenAPI codegen, Docker Compose, unit and Cucumber tests with an 80% JaCoCo gate.

### Deferred
- **GM attributes** — the name-only `GM` slot stays until its consumers are real
  (Phase 6.3 draft scouting / 6.4 trade evaluation). Resolve with the same
  continuous 1–20 model as coach; see coach.md open-Q #3.
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
needs its own design pass (a `decisions.md` entry plus an execute-ready `todo.md`
plan) before execution — every phase so far has found real design questions the
one-liner hid. **Do not execute a bullet as if it were a plan.**

**Next up is Phase 4's execution**, then the Phase 3 revisit. Shipped work is summarized
in *What Exists Today*; its record is `decisions.md`, not this file.

---

## Done — Phase 3 → Phase 4 pre-work (a GATE)

⚠ **Phase 4's design pass does not start until these are done.** It is its own
section deliberately: mixing gate work into the phase is how a gate becomes a
backlog item that ships late or not at all. **Order is dependency-driven** — 2+3
are one pass, 5 depends on 4, 8 must be last.

- [x] **1.** Rewrite `todo.md` *(2026-09)*
- [x] **2.** Condense `decisions.md` *(2026-09)*: split by audience; findings moved to
      [engine-traps.md](engine-traps.md), entries cut to their decision letters. Never
      renumber: citations resolve by number **and letter**.
- [x] **3.** Sweep the `sim` package's Java comments *(2026-09)*.
- [x] **4.** `possession-flow.puml` *(2026-09)*: split into an overview and six detail
      diagrams, each screen-sized. Ownership rule: a fork inside a resolver goes in that
      resolver's file; a new resolver or a new retention path goes in the overview too.
- [x] **5.** `game.md` dedup against the diagrams *(2026-09)*.
- [x] **6.** Triage `backlog.md` *(2026-10, docs-reduction pass)*
- [x] **7.** Adopt Beads and cut over for Phase 4 work *(2026-10: `backlog.md` and `ideas.md`
      migrated to beads and deleted — chores open, ideas `deferred`)*
- [x] **8.** Re-read every `calibration.md` verdict *(2026-10, docs-reduction pass: rewritten as a
      reference; its Current column re-measured at 5 seeds in `gametime-6q5` and reproduced the
      §3.22 landing to the decimal)*

**The how, and the traps for each, live in `todo.md`** when the gate is the active
work. **The roadmap ↔ Beads boundary:** Beads holds actionable work with state;
this file holds phase structure and seams. Phases 4–8's bullets do **not** migrate
— a seam turned into a ticket lies about its readiness. When work becomes a bead,
the **bead is the only record** and the doc entry is deleted.

---

## Phase 4 — Statistics & Box Scores

**Goal**: Track, aggregate, and expose stats.

> **Designed as [decisions.md](decisions.md) #045** (design pass `gametime-ta7`); the
> execute-ready plan is in [todo.md](todo.md).

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
- [ ] `GET /v1/league/game-highs` — single-game highs by category

---

## Next — Phase 4 → Phase 5: the Phase 3 revisit

Phase 4's design pass found facts the event log does not record: starters, the team a player
played for, substitutions and the tip-off. Phase 4 ships them as columns filled from the
rotation. This revisit returns to the engine **after** Phase 4 ships, so it is scoped against
real per-player stats rather than guesses. Its sub-phases continue Phase 3's numbering
(§3.23+): Phase 3 is an open arc, and a new phase number would repeat the renumbering
confusion.

- **Starts with** `gametime-3sc`: game initialization (10 check-ins + a jump ball) and
  substitution events. When it lands, `box_score.started` and `team_id` are derived from the
  log instead; the columns stay.
- **Candidates** are the engine and calibration beads (`bd list --label engine`,
  `bd list --status deferred --label idea`). Its design pass picks which become sub-phases.
- **Not a Phase 5 gate by default.** The design pass decides what must land before season
  play.

---

## Phase 5 — Season Structure

**Goal**: Full season lifecycle — schedule, standings, playoffs, awards.

> **Read [risks.md](risks.md)'s "Skill sensitivity is ~10× too steep" before designing this
> phase.** Season play is the first consumer of the engine's response to skill spread, and
> left unaddressed the standings will be degenerate in a way that looks like a scheduling bug.
> It needs a skill-ladder harness mode first.

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

### 5.5 Season close
- [ ] Freeze a closed season's stats into summaries and decide retention; may become its own
      phase. Scoped in `gametime-1b1`

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
      coach; see coach.md open-Q #3)

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

⚠ **Already decided, do not reopen**: simulation granularity and the clock model
(possession-by-possession, no clock); coach attributes (continuous 1–20, not enums); event
persistence (every `GameEvent` stored, events are the source of truth).

---

## Build order

Phases 1–3 are shipped. The remaining path: **Phase 4 (Stats) → the Phase 3 revisit →
Phase 5 (Season) → Phase 6 (Progression)**, with **Phase 7 (Frontend)** able to start in parallel from
Phase 4 onward (the §3.6 simulation API it needs already exists), and **Phase 8** last.
