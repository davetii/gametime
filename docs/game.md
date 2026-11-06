# Game Domain

The rules of a simulated game: the data it produces (`Game`, `GameEvent`, `BoxScore`),
the order the engine resolves a possession, and the API that serves it.

This doc states **how it works now**. For *why*, see [decisions.md](decisions.md); for
how each pass got here, [possession-flow-model.md](possession-flow-model.md) and
[roadmap.md](roadmap.md). Where to look instead of here:

| Question | Doc |
|---|---|
| What happens in a possession, branch by branch | [possession-flow.puml](possession-flow.puml) (overview) + its six `possession-flow-*.puml` detail diagrams |
| Every `(play_type, outcome)` the engine emits, and who is on the row | [game-events.md](game-events.md) |
| Which skills each event reads | [player.md](player.md) |
| What the numbers are tuned toward | [calibration.md](calibration.md) |
| Constants that bite | [engine-traps.md](engine-traps.md) |

---

## `Game`

The matchup and its outcome.

- `homeTeam` / `awayTeam` — `Team` FKs. No season FK (no schedule table yet).
- `status` — `SCHEDULED | IN_PROGRESS | FINAL`. The engine only produces `FINAL`.
- final score, periods (regulation plus overtime), and the `seed` the game was run with.
  The score is each team's summed box-score points.
- `home_possessions` / `away_possessions` — the engine's own count, one per trip down
  the floor (an offensive-rebound retention is the same possession). Equal today because
  the teams alternate; stored as two facts. Pace and ratings divide by these.
- `played_at` — game-world time, when the game was played. Run time today; the schedule's
  date once seasons exist. Game logs order by it. `create_date` is audit time and is
  never read.
- No per-period line scores: derive them from the event log.

---

## `GameEvent`

The ordered event log of a game. **Every event is persisted**, so play-by-play replays
from stored rows and box scores are built from them. The log is the source of truth.

| Column | Meaning |
|---|---|
| `game_id` | FK to `game` |
| `sequence` | monotonic across the **whole game** (does not restart per period) |
| `period` | |
| `offense_team_id` / `defense_team_id` | |
| `play_type` | `SHOT, TURNOVER, REBOUND, FOUL, FREE_THROW` (closed enum) |
| `outcome` | free text; vocabulary in [game-events.md](game-events.md) |
| `primary_player_id` | the player the event is about — shooter, turnover committer, free-throw shooter. **On `FOUL` it is the committer.** |
| `assist_player_id` | the assister on a made field goal; null otherwise |
| `opponent_player_id` | the counterparty on the **opposite** team (stealer, blocker, fouled shooter); null where no individual victim was identified |
| `committing_team_id` | set on every `FOUL` event and only those; the team that committed it. Needed because a rebounding foul can be committed by the offense, so it is not recoverable from `defense_team_id` |

- A teammate never goes in the opponent column.
- There is no clock column. The engine counts possessions, not seconds; any display
  clock is derived on read from `period`, `sequence` and pace.
- All events in one possession share `offense_team_id`, `defense_team_id` and `period`.

---

## Game structure

- Four periods of `sim.default-possessions-per-period` possessions **per team**, scaled by
  the average of the two coaches' `pace` multipliers (both teams share one count).
- Possessions strictly alternate, **home first**. A second chance is part of the same
  possession, not a new one.
- A tie after period 4 adds overtime periods (`sim.ot-possessions-per-period` each) until
  the score differs.
- **Team penalty:** a team is in the penalty once it has committed
  `sim.bonus-fouls-per-period` fouls in the period, and the foul that reaches the count
  already sends its team to the line. The tally is read from the event log (every
  `FOUL` by `committing_team_id` in that period); technicals are excluded and flagrants
  count. A rebounding foul or non-shooting foul in the penalty awards 2 bonus FTs.

---

## The possession

Each possession has a **rotation phase** (state only) then the **possession phase**
(the branching path). The diagrams draw the branches; this section is the order and the
constraints behind it.

| # | Resolver | Decides | On a hit |
|---|---|---|---|
| 0 | `RotationState.advancePossession()` — both teams | energy drain/recover, technical roll, forced removal of fouled-out or ejected players, foul-trouble sub, fatigue sub | technical: a `FOUL` + 1 `FREE_THROW`, emitted by `PossessionEngine` |
| 1 | `ShotSelector` | the shooter, then the shot type | — |
| 2 | `TurnoverResolver` | turnover, then one of nine causes | possession ends. A charge also emits a `FOUL` and charges a personal foul |
| 3 | `FoulResolver.isFoul` | a foul that stops the shot (no basket); then flagrant?; then non-shooting? | free throws and the possession ends — except a flagrant (2 FTs, offense **retains**) and a non-shooting foul (2 bonus FTs in the penalty, none outside it) |
| 4 | `BlockResolver` | block | loose-ball recovery emits a `REBOUND` and credits a rebounder on the in-bounds outcomes |
| 5 | `ShotResolver.isMade` | make / miss | — |
| 6 | `FoulResolver.isAndOne` — makes only | a foul the shot survived; then flagrant? | +1 FT, possession ends — except a flagrant (basket + 2 FTs, offense retains) |
| 7 | `MissedShotResolver` — misses only | the rebounding foul, carved off first (then flagrant?); then one four-way board/OOB draw | possession ends, or a second-chance possession |

Steps 2–7 are `resolvePossession()`. Step 0 runs in `PossessionEngine`'s loop before it,
for both teams. Step 1 precedes step 3 because the shot type feeds the foul roll.

### Rules the order implies

- **A shot can be fouled once.** Step 3 returns, so a shot that draws a stopped-shot foul
  never reaches make/miss or the and-1.
- **A flagrant is a question asked at steps 3, 6 and 7, not a step.** It is layered on a
  foul already rolled and charged, so no foul rate moves. Its 2 FTs **replace** the step's
  own award (a flagrant stopped three is 2 FTs; a flagrant and-1 is 2, not 1). A flagrant
  and-1 therefore yields a made basket, 2 FTs and the ball back — correct, though it looks
  like a double count.
- **A flagrant is a personal foul** (counts toward the 6-foul limit and the bonus). A
  technical is not.
- **Non-shooting fouls**: step 3's `NON_SHOOTING_FOUL` (a second roll partitions the
  stopped-shot foul into shooting or non-shooting), step 7's rebounding foul, and step 2's
  charge. The step-3 roll is named for stopping the shot, not for being non-shooting.
- **Flagrant is asked before non-shooting**, so a flagrant common foul is just a flagrant.
- **A `NON_SHOOTING_FOUL` ends the possession; the ball does not come back.** This is
  knowingly unlike basketball. Returning the ball would re-enter at `ShotSelector` for a
  live attempt where the stopped shot charged none, and FGA has under one attempt of
  headroom to the target. Revisit only if shot-mix work buys FGA headroom.
- **Free-throw paths can return the ball.** Flagrants do, and a missed **last** free
  throw is a live board whose offensive rebound retains. The and-1's single FT is always
  the trip's last, so it is live too.
- **Steps 4 and 7 are one four-way draw.** `MissedShotResolver` wraps `ReboundResolver`:
  offensive rebound, defensive rebound, out of bounds to offense, out of bounds to
  defense. A sail-out (shot flies out untouched) and a tipped-out ball resolve to the same
  two `OUT_OF_BOUNDS_*` outcomes; the difference is not recorded.
- **The retention cap forces a sibling outcome.** Every offense-retaining path shares one
  counter bounded by `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`. At the cap a retained
  outcome is rewritten to its possession-ending sibling: `OFFENSIVE` → `DEFENSIVE`,
  `OUT_OF_BOUNDS_OFFENSE` → `OUT_OF_BOUNDS_DEFENSE`.
- **A charge emits two events for one occurrence** (`TURNOVER` and `FOUL`); a block emits
  two (`SHOT` then `REBOUND`). Reconciliation must count per side of the identity, not
  per possession.

### The putback

The player who just took an offensive board carries `sim.offensive-rebounder-shot-weight`
at the **next** `ShotSelector` draw only. It is a weight on one draw, not a branch.

### Shot selection

The base shot mix is a four-value share table (`sim.shot-share-*`: three, perimeter,
drive, post), modulated by an avg-10 skill modifier. The coach's `offensiveScheme` tilts
it: `THREE` × the multiplier, `PERIMETER` × its reciprocal, `DRIVE`/`POST` unscaled. The
share table sets the league's mix; the coach sets only a team's tilt on it. Points per
shot (2, or 3 for `THREE`) live on `ShotType`.

---

## Rotation

Substitution, fatigue and disqualification run between possessions for both teams, change
who is on the floor, and emit no events of their own (a technical's events come from
`PossessionEngine`). [possession-flow-rotation.puml](possession-flow-rotation.puml) draws
the tiers. What it does not carry:

- **The floor is always exactly five.** If everyone is fouled out or ejected, a
  disqualified player stays on rather than the floor dropping below five.
- **Disqualification is derived, never stored.** Fouled out (6 personal fouls), two
  technicals, or one flagrant-2 each read a monotonic counter. All three pass through one
  filter (`RotationState.isDisqualified`) behind the single `eligible(...)` gate every
  candidate pool uses.
- **`advancePossession()` consumes exactly three RNG draws per call**: the foul-trouble
  sit roll, the technical roll, and the technical's committer draw. All are taken
  unconditionally at a fixed point (the committer draw even when discarded), so the seed
  stream never forks on rotation state. The step is reproducible from the seed but not
  RNG-free.
- **Fatigue is a multiplier, not a gate.** `effectiveSkill = skill × fatigueFactor(energy)`
  bends the shot, defense and rebound contests, composed with the coach and chemistry
  modifiers.

### Foul-trouble sub

Sequenced between the forced removal and the fatigue sub; the engine's one strategic
substitution.

- **Probabilistic, not a threshold**: `base(foulCount) × coach × value × roster`. The base
  curve is zero below 3 fouls, rises to 5, and is zero at 6 (the hard rule handles a
  foul-out). `substitutionAggressiveness` scales it: an aggressive coach thinks about it
  at 3, an average one at 4, a passive one at 5.
- **It protects the best players more**, the inverse of fatigue, where starters tolerate
  more. Value is a derived defense-leaning composite (`PlayerGameState.valueComposite()`)
  combined with the `lineupRole`/`rotationOrder` signal.
- **It yields**: it draws only from the `rotationDepth` window and fires only if an
  eligible, meaningfully fresher replacement exists. Otherwise the player keeps playing.
- **Sticky sit, earned return**: a benched player is an ordinary bench player, with no
  competing roll to bring him back. He returns through the fatigue rule's freshness path.
  A fouled-out player never returns.

### Technical fouls

Rolled per team between possessions, not on the possession path. The committer is a
`foulProne`-weighted draw from the five on the floor. A technical awards one free throw,
taken by the team's best free-throw shooter on the floor (deterministic, since nobody was
fouled). It has its own counter that feeds neither the 6-foul limit nor the penalty
tally, and two eject. It carries `committing_team_id` like any foul. Both teams can draw
one in the same possession; home resolves first.

---

## Coach modifiers

All effects use the avg-10 deviation multiplier `base × (1 + COACH_SENSITIVITY·(attr−10)/10)`.

- **`pace`** scales the possession count: faster coach, more possessions and events.
- **`offensiveScheme`** tilts the shot mix (see Shot selection).
- **`defensiveScheme`** scales the defense's turnover and foul pressure.
- **Team chemistry**: `acumen` is a small make-rate nudge in `ShotResolver`;
  `teamOffense` / `teamDefense` form one possession-level efficiency multiplier.

`rotationDepth` and `substitutionAggressiveness` belong to rotation, not the above.

---

## Events and skills

Every event the engine emits, with its participant columns, is the master table in
[game-events.md](game-events.md); it is checked against real simulated events by
`GameSimulatorIntegrationTest`, so do not restate it here. The skills each event reads
are in [player.md](player.md).

---

## `BoxScore`

One row per `(game_id, player_id)`, enforced by a unique key, for every player who **took
the floor**, starters and bench (tracked by `tookFloor()`, not by possession count — a
substitute can score before his first possession registers). A player who never checked
in has no row.

**Built from the event log at game end.** Every stat column is derived from the game's
`GameEvent` rows, by player:

| Column | Derived from |
|---|---|
| `points` | 2 per `SHOT` `MADE_2PT_*`, 3 per `MADE_3PT`, 1 per `FREE_THROW` `MADE_*` |
| `field_goals_attempted` / `_made` | every `SHOT` (`BLOCKED_*` included; a stopped shot is a `FOUL`, not an attempt) / `MADE_*` |
| `three_pointers_attempted` / `_made` | `SHOT` ending `_3PT` / `MADE_3PT` |
| `free_throws_attempted` / `_made` | every `FREE_THROW` / `MADE_*` |
| `offensive_rebounds` / `defensive_rebounds` | `REBOUND` `OFFENSIVE` / `DEFENSIVE`. `OUT_OF_BOUNDS_*` credits no one |
| `assists` | `SHOT` with `assist_player_id` = player |
| `steals` | `TURNOVER` `STOLEN` with `opponent_player_id` = player |
| `blocks` | `SHOT` `BLOCKED_*` with `opponent_player_id` = player (the shooter is primary) |
| `turnovers` | every `TURNOVER`, all nine causes, the charge included |
| `fouls` | every `FOUL` except `TECHNICAL_FOUL` (primary is the committer). The charge's `FOUL` row counts; a flagrant is one row |
| `technical_fouls` | `FOUL` `TECHNICAL_FOUL` |

Three columns are not derived:

- **`team_id`** — the team the player played for in this game, from the squad at sim
  time. `player_team` holds only the current team, so it cannot answer this for a past game.
- **`started`** — in the opening five on the floor. Not the same as lineup role
  `STARTER`: a team whose fifth starter was released opens with its first bench player.
- **`minutes`** — a possession-share projection, not a clock. Each on-floor player
  accumulates possessions; at write time his share of the team's on-floor possessions
  scales to `5 × game-minutes` (`PERIODS × 12`, +5 per overtime) and rounds to a whole
  number. Team minutes therefore sum to that only approximately.

**The guard.** The engine also keeps per-player counters, because foul-outs, foul trouble
and ejections read them mid-game. Before anything is saved, every derived column is
compared with the player's counter and each team's derived points with the engine's
score; any difference, or an event crediting a player who never took the floor, throws
and rolls the game back. A disagreement between the log and the box score therefore
cannot be persisted.

- **Fouls and technical fouls are separate.** `fouls` is personal fouls only and is what
  the 6-foul limit, foul-trouble level and penalty read; `technical_fouls` is never summed
  into it.
- **Ejection** is a derived predicate, not a counter, event or column: `technicalFouls >= 2`
  or any `FLAGRANT_FOUL_2`. Nothing announces it in the log.

**Season stats read `box_score` joined to `game`, never `game_event`.** Percentages come
from summed makes and attempts. TS% = PTS / (2 × (FGA + 0.44 × FTA)); eFG% = (FGM + 0.5 ×
3PM) / FGA. Pace = possessions × 48 / game minutes; offensive / defensive rating = points
scored / allowed × 100 / possessions. Every aggregate reads all `FINAL` games through one
scope (`StatsScope`), the seam a season boundary will replace.

---

## API

Spec: `gametime-api/yml/gametime.yaml`. Delegate: `api/V1ApiDelegateimpl.java`.

| Verb | Path | Request | Response | Errors |
|---|---|---|---|---|
| `POST` | `/v1/game/simulate` | `SimulateGameRequest` | `200 GameResult` | `404` unknown team · `422` same team |
| `GET` | `/v1/game/{gameId}` | — | `200 GameResult` | `404` unknown game |
| `GET` | `/v1/game/{gameId}/play-by-play` | — | `200 [GameEvent]` | `404` unknown game |
| `GET` | `/v1/player/{playerId}/stats` | — | `200 PlayerStats` | `404` unknown player |
| `GET` | `/v1/player/{playerId}/gamelog` | — | `200 [GameLogEntry]` | `404` unknown player |
| `GET` | `/v1/team/{teamId}/stats` | — | `200 TeamStats` | `404` unknown team |
| `GET` | `/v1/league/leaders?category=&limit=` | — | `200 LeaderBoard` | `400` bad category or limit |
| `GET` | `/v1/league/game-highs?category=&limit=` | — | `200 GameHighs` | `400` bad category or limit |

- **`GameResult`**: `game`, `homeBoxScore`, `awayBoxScore`, `homeTeamStats`,
  `awayTeamStats`. Box scores are split by each row's `team_id`. A `TeamGameStats` is the
  team's totals, possessions, pace and the three ratings for that game. The event log is
  not included; it has its own endpoint.
- **`Game`**: `id`, `homeTeamId`, `awayTeamId`, `status`, `homeScore`, `awayScore`,
  `periods`, `seed`.
- **`GameEvent`**: `sequence`, `period`, `offenseTeamId`, `defenseTeamId`, `playType`,
  `outcome`, `primaryPlayerId`, `assistPlayerId`, `opponentPlayerId`, `committingTeamId`.
  No time field.
- **`BoxScore`**: `playerId` plus the counters above.
- **`SimulateGameRequest`**: `homeTeamId`, `awayTeamId` (both required), `seed` (optional
  `int64`). Pace is not exposed: the endpoint uses `sim.default-possessions-per-period`
  from the active profile, and tempo varies per game through the coach `pace` attribute.
- **Seed.** Omitted gives a fresh random `long`; provided gives a reproducible run. It is
  persisted (`game.seed`) and echoed on `Game`.
- **Play-by-play** is a flat list in `sequence` order, unpaginated (~200–350 events a game).
- **`PlayerStats`**: games played and started, `totals`, `perGame`, `shooting`. A player
  with no games answers `200` with zero totals and null averages and percentages.
- **`GameLogEntry`**: one per game, ordered by `playedAt` then game id: team, opponent,
  home/away, the score, `started`, and the box line.
- **`TeamStats`**: the team's and its opponents' totals and per-game, possessions, pace,
  ratings. No win–loss record.
- **Leaders.** Counting categories rank the per-game average among players with 70% of the
  most games any team has played; `TECHNICAL_FOULS` ranks the total with no qualifier.
  Percentages rank the summed fraction among players with 3.66 FGM (FG%, TS%, eFG%),
  1.0 3PM or 1.52 FTM per such game. `basis` says which: `PER_GAME`, `TOTAL` or
  `PERCENTAGE`. Ties share a rank; within a tie, more games first, then player id.
  `teamId` is the team of the player's most recent game. Thresholds and the `limit`
  default (10) and maximum (100) are `stats.leaders.*` in `application.properties`.
- **Game highs.** The top single-game `box_score` lines in a counting stat; a player can
  appear more than once. Ties share a rank, earliest `playedAt` first, then game id.
- **Numbers are raw**, unrounded; percentages are fractions (0–1), null without a
  denominator.
- **Errors.** `404` for an unknown game, team or player. `400` for an unknown category or
  a `limit` outside 1–100. `422` for `homeTeamId == awayTeamId`, via
  `ResourceUnprocessableException` and `ApiExceptionHandler`. Error bodies are empty.

---

## Simplifications and gaps

Where the engine differs from real basketball, so a difference is not mistaken for a bug.
The groups differ in kind: only the first is "not built yet".

### Not represented

Real concepts the engine does not simulate. No event reads them, so they are gaps rather
than decisions, and any could be added later.

| What | Today |
|---|---|
| A game clock | Time is a possession count; display time is derived on read |
| Transition vs. half-court, pick-and-roll, late-game clutch, off-ball movement | The skills exist on players but no event reads them; see [player.md](player.md) |
| Bench and coach technicals | Technicals come only from players on the floor |
| An ejection announcement | There is no `EJECTION` event; derive it from `TECHNICAL_FOUL` / `FLAGRANT_FOUL_2` events |

### Deliberately different from basketball

The engine knowingly does something other than the real rule, to protect a calibration
target or a reconciliation identity.

| What | Behavior instead |
|---|---|
| A foul **during** the shooting motion on a shot that is released and missed (real: an FGA, no FGM) | Every stopped-shot foul is treated as contact **before** release: free throws, no FGA. FGA runs lower than a real league's by about the shooting-foul rate. Only the and-1 is an attempt |
| A common (non-shooting) foul returning the ball to the offense | It ends the possession. See the FGA-headroom rule above |

### Not recorded

Facts the engine could know but does not store. Naming a victim or a distinction needs
either a new RNG draw (which re-baselines every seeded test) or a column nothing reads.

| What | Behavior instead |
|---|---|
| Who drew a charge | `OFFENSIVE_FOUL` has no counterparty; picking one needs a new RNG draw |
| Who was fouled in a rebounding foul | The foul is against the team contesting the board; `opponent_player_id` is null. The free-throw shooter is a `foulDrawing`-weighted stand-in, not a victim |
| Sail-out vs. tipped-out on a missed shot | Same two `OUT_OF_BOUNDS_*` outcomes; the difference is not stored |
