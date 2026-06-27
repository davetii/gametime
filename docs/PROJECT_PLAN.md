# Gametime Project Plan

Basketball simulation game — 40-team league with attribute-driven gameplay, season management, and a React frontend.

## What Exists Today

### Fully Built
- **16 player attributes**: agility, charisma, cohesion, determination, ego, endurance, energy, handle, health, intelligence, luck, shotSelection, shotSkill, size, speed, strength
- **13 derived skills** with weighted formulas and threshold bonuses: acumen, ballSecurity, passing, teamOffense, drive, freeThrows, longRange, perimeter, post, individualDefense, teamDefense, offenseRebound, defenseRebound
- **9 positions**: PG, CG, BG, W, SF, F, PF, FC, C
- **6 player statuses**: STARTER, BENCH, ROTATION, MINORS, INJURED, SUSPENDED
- **40-team league** across 4 conferences (EAST, NORTH, SOUTH, WEST), ~420 seed players
- **Entity layer**: Player, Team, Coach (name only), GM (name only) with JPA relationships
- **3 working REST endpoints**: GET league, GET player by ID, GET team by ID
- **Skill calculation engine**: SkillCalculator interface, 13 calculator implementations, SkillMapper orchestrator
- **Entity-to-model mapping**: EntityMapper with full attribute + skill wiring
- **Database**: Postgres (local dev) + H2 (tests), Liquibase migrations, gametime schema, audit triggers
- **Test suite**: 78 tests (unit + Cucumber integration), 80% line coverage enforced
- **Build pipeline**: Multi-module Maven, OpenAPI codegen with delegate pattern, Docker Compose

### Partially Built (Spec'd but Unimplemented)
- `POST /v1/player/` — createPlayer
- `PUT /v1/player/` — updatePlayer
- `POST /v1/team/{teamId}/{playerId}` — addPlayerToTeam
- `GET /v1/conference/{confId}` — fetchConference
- `health` attribute — defined and stored but not used in any skill calculator

---

## Phase 1 — Complete the Foundation

**Goal**: Finish the CRUD API surface, flesh out Coach/GM models, clean up loose ends.

### 1.1 Implement Remaining REST Endpoints
- [ ] `createPlayer` — validate attributes, persist, return with computed skills
- [ ] `updatePlayer` — validate, update, recompute skills
- [ ] `addPlayerToTeam` — validate roster limits, handle team assignment
- [ ] `fetchConference` — filter teams by conference ID

### 1.2 Expand Coach Model
Coaches currently have only firstName/lastName. They need attributes that influence game simulation.
- [ ] Define coach attributes (offensive system, defensive system, player development, rotation management, adaptability)
- [ ] Add to CoachEntity, OpenAPI spec, and database schema
- [ ] Decide: do coaches have a "style" enum (run-and-gun, half-court, etc.) or continuous attributes?

### 1.3 Expand GM Model
GMs currently have only firstName/lastName. They'll drive off-court decisions.
- [ ] Define GM attributes (scouting, negotiation, draft strategy, analytics focus)
- [ ] Add to GMEntity, OpenAPI spec, and database schema

### 1.4 Wire Up the Health Attribute
- [ ] Incorporate `health` into relevant skill calculators (endurance interaction, injury susceptibility)
- [ ] Decide: does health affect skill output directly, or does it feed into a separate injury probability model?

### 1.5 Cleanup
- [ ] Remove orphaned `gametime-service/src/` directory (empty leftover from restructuring)
- [ ] Verify IntelliJ `.http` files work with environment selection
- [ ] Add missing test coverage for new endpoints

---

## Phase 2 — Roster & Lineup Management

**Goal**: Turn the static player list into a managed roster with lineup logic.

### 2.1 Roster Rules
- [ ] Define roster size limits (e.g., 15 active, 5 minors)
- [ ] Enforce position minimums/maximums per roster
- [ ] Implement roster validation on add/remove player

### 2.2 Lineup & Rotation
- [ ] Starting lineup (5 starters) — position-constrained
- [ ] Bench rotation order and minutes allocation
- [ ] Fatigue model: how does `endurance` interact with minutes played?
- [ ] Coach attributes influence rotation depth and minutes distribution

### 2.3 Roster APIs
- [ ] `GET /v1/team/{teamId}/roster` — roster with lineup slots
- [ ] `PUT /v1/team/{teamId}/lineup` — set starting 5 + rotation order
- [ ] `DELETE /v1/team/{teamId}/{playerId}` — remove player from team

---

## Phase 3 — Game Simulation Engine

**Goal**: Build the core possession-by-possession simulation.

### 3.1 Game Model
- [ ] Define `Game` entity: homeTeam, awayTeam, quarter structure, final score
- [ ] Define `GameEvent` model: possessions, plays, outcomes
- [ ] Define `BoxScore` model: per-player stats for a single game

### 3.2 Possession Engine
- [ ] Possession flow: inbound → set play / fast break → shot clock → outcome
- [ ] Shot selection logic: which player gets the ball, what type of shot (drive, perimeter, post, longRange)
- [ ] Shot outcome: probability based on shooter skills vs defender skills
- [ ] Turnover probability: ballSecurity vs defender individualDefense
- [ ] Foul model: drive/post attempts → foul probability → freeThrow skill

### 3.3 Rebounding
- [ ] Offensive rebound probability: offenseRebound skills vs defenseRebound skills
- [ ] Second-chance possessions

### 3.4 Team Chemistry & Coaching Effects
- [ ] teamOffense/teamDefense affect overall team efficiency
- [ ] Passing skill influences assist rate and ball movement
- [ ] Acumen influences shot selection quality
- [ ] Coach system modifies possession pace, shot distribution, defensive scheme

### 3.5 Fatigue & Substitution
- [ ] Per-player energy tracking within a game
- [ ] Skill degradation as energy drops
- [ ] Automatic substitution triggers based on fatigue thresholds
- [ ] Coach rotation style determines when subs happen

### 3.6 Simulation APIs
- [ ] `POST /v1/game/simulate` — simulate a single game, return box score
- [ ] `GET /v1/game/{gameId}` — retrieve game result
- [ ] `GET /v1/game/{gameId}/play-by-play` — event log

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
- [ ] GM attributes influence trade evaluation

---

## Phase 7 — React Frontend

**Goal**: Browser-based UI for viewing and interacting with the simulation.

### 7.1 Core Views
- [ ] League dashboard — all 40 teams by conference
- [ ] Team detail — roster, coach, GM, current record
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
Phase 1 (Foundation) ──> Phase 2 (Rosters) ──> Phase 3 (Game Engine) ──> Phase 4 (Stats)
                                                        │
                                                        v
                                                Phase 5 (Season) ──> Phase 6 (Progression)
                                                        │
                                                        v
                                                Phase 7 (Frontend) ──> Phase 8 (Polish)
```

Phases 1-3 are the core loop. Once you can simulate a game and get a box score, everything else builds on top. The frontend can start in parallel with Phase 4+ once the game simulation API exists.
