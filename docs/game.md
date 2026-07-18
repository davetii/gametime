# Game Domain *(model shipped §3.1; engine fills it through §3.5)*

The Game domain is the **data a simulated game produces**: the matchup and its
result (`Game`), the event log that records how it unfolded (`GameEvent`), and
the per-player stat line for that game (`BoxScore`). These three are one cohesive
shape — a `Game` *has* `GameEvent`s and *produces* a `BoxScore` — so they live in
one doc, the way [roster.md](roster.md) holds player↔team + lineups + transactions
together.

The **model** shipped in §3.1; the **possession engine** that fills it is built
through §3.4 and this doc now documents both:
- **§3.2** possession flow — shot selection / turnover / foul / shot outcome
- **§3.3** rebounding — the second-chance loop after a missed shot
- **§3.4** coaching + chemistry — coach modifiers on the flow, and real assists
- **§3.5** minutes / fatigue / substitution — the on-floor five changes during a
  game; `BoxScore.minutes` is now real (derived from possession share)

The "Possession flow" section below reflects what the engine actually does today.

> **Scope discipline** (cf. decisions.md #014, #017, #020): the entities shipped
> §3.1 shaped for their consumers, not guessing the algorithm; each piece of
> simulation logic + any event-shape change (e.g. §3.4's `assist_player_id`)
> landed *with* the engine phase that consumes it, additively.

---

## Event persistence — RESOLVED *(decisions.md #020)*

The headline §3.1 choice (roadmap Design Decision #9) is settled: **every
`GameEvent` is persisted.** Play-by-play (§3.6 `GET /{gameId}/play-by-play`)
replays from stored rows rather than re-simulating, and the Phase 4 stats model
reconciles against the actual event log. The trade-off is row volume
(~200–350 events/game) — served **unpaginated** as a flat ordered list at the §3.6
read endpoint (#024 D found the volume modest; pagination deferred, not dropped),
not by dropping the data. See
decisions.md #020 for the full call (status lifecycle, box-score keying, event
shape).

---

## `Game`

The matchup and its outcome.

- `homeTeam` / `awayTeam` — references to `Team` (`home_team_id` / `away_team_id`
  FKs). **No season FK** — no schedule/season table exists yet (Phase 5); don't
  fabricate the link ahead of its consumer (#014/#017).
- quarter structure — period scores, regulation vs. overtime
- final score
- **status** — `GameStatus { SCHEDULED, IN_PROGRESS, FINAL }` (decisions.md
  #020). Minimal on purpose: no CANCELLED/POSTPONED until a consumer needs them.

---

## `GameEvent`

The possession-by-possession event log: possessions, plays, outcomes. One game
is an ordered sequence of these. **Every event is persisted** (event-persistence
section above; decisions.md #020).

### Columns

- `game_id` — FK to `game`
- `sequence` — monotonic ordering **across the whole game; does not restart per
  period**, so play-by-play is a single `ORDER BY sequence` read
- `period`
- `offense_team_id` / `defense_team_id`
- `play_type` — enum: `SHOT, TURNOVER, REBOUND, FOUL, FREE_THROW`
- `outcome` — free text (vocabulary below)
- `primary_player_id` — the one player the event is about (shooter, turnover
  committer, fouler, free-throw shooter)
- `assist_player_id` — **(§3.4)** the assister on a made-FG `SHOT` event, or
  `null`. Nullable because not every made shot is assisted, and non-`SHOT` events
  never carry one (decisions.md #022). It is a *second participant* on the SHOT
  event, not its own event — so `BoxScore.assists` reconciles against the count of
  `SHOT` events whose `assist_player_id` is set (events are the source of truth,
  #020). *Surfaced in the OpenAPI `GameEvent` model as `assistPlayerId` in §3.6
  (#024 D — see the API surface section below).*
- *No in-game clock column.* §3.2 models time as an **abstract, configurable
  possession count** (decisions.md #021), and event time is **derived on read**
  (pace + `period` + `sequence`) for play-by-play display — not stored. §3.6
  resolved #021 B's open "single value vs. range" question by keeping time
  **derived, not stored** (#024 E); the storage shape is revisited only if the
  Phase 7 game view needs a per-event clock. `period` + `sequence` give full
  ordering today.

### Possession flow (§3.2–§3.3) — see also [possession-flow.puml](possession-flow.puml)

Each possession produces **one or more** `GameEvent` rows in this order:

1. **Turnover check** — rolled before the shot. If triggered:
   - `TURNOVER` event → possession ends, ball goes to the other team.
2. **Foul check** — rolled on `DRIVE` and `POST` shot types only. If triggered:
   - `FOUL` event (primary_player = fouling defender) → free throws follow.
   - Two `FREE_THROW` events (primary_player = shooter), each with its own
     make/miss outcome. Possession ends after free throws.
3. **Shot** — if no turnover and no foul:
   - `SHOT` event → made or missed. On a make, points are scored and the
     possession ends. On a made FG, an **assist** may be attributed (§3.4): a roll
     (scaled by the other on-floor offensive players' `passing` / team
     `teamOffense`) decides whether the make was assisted; if so, an assister is
     picked by a weighted `passing` draw over the other four offensive players
     (the shooter excluded) and stamped on the SHOT event's `assist_player_id`.
     Not every make is assisted. On a **miss**, a rebound is resolved (§3.3):
4. **Rebound** (§3.3) — rolled only after a missed `SHOT`:
   - `REBOUND` event with outcome `DEFENSIVE` (primary_player = defensive
     rebounder) → possession ends, ball goes to the other team; **or**
   - `REBOUND` event with outcome `OFFENSIVE` (primary_player = offensive
     rebounder) → the shooting team retains the ball and runs a **second-chance
     possession** through the full flow above (turnover → foul → shot → rebound).
     Offensive rebounds are capped per possession
     (`MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`); after the cap a miss is forced to
     a defensive rebound so the possession terminates.

A single possession therefore emits one of these patterns (a missed shot is
always followed by a `REBOUND`):
- `TURNOVER`
- `FOUL` → `FREE_THROW` → `FREE_THROW`
- `SHOT` (made) — possession ends
- `SHOT` (missed) → `REBOUND` (`DEFENSIVE`) — possession ends
- `SHOT` (missed) → `REBOUND` (`OFFENSIVE`) → … second-chance possession …

All events in a possession share the same `offense_team_id` / `defense_team_id`
and `period`. `sequence` increments globally (not per possession).

**Substitution + fatigue + foul-outs (§3.5)** run as a **between-possession** step
that changes *who* is on the floor without altering the possession flow's shape
(decisions.md #023). Before each possession the engine, for the team about to
play: drains the on-floor five's `currentEnergy` (drain scaled by `endurance`),
recovers the benched players' energy, **forces off any player who has fouled out**
(a derived predicate `getFouls() >= FOUL_OUT_LIMIT`, not a stored flag), then runs
a fatigue substitution (pull the most-tired starter below a
`substitutionAggressiveness`-scaled threshold for the freshest eligible bench
player, drawing down the `rotationOrder` queue only as far as `rotationDepth`
allows; starters tolerate more fatigue and return first). The whole step is
**deterministic given (energy, fouls, coach attrs) and consumes NO RNG** — it
produces no `GameEvent` rows and does not touch the seeded stream. The on-floor
five (`RotationState.onFloor()`) is **always exactly 5**: if the roster is
exhausted (everyone fouled out), the least-fouled available player stays on so the
floor never drops below 5. A fatigue **multiplier** over each on-floor player's
skills (`effectiveSkill = skill × fatigueFactor(energy)`) then bends shot/defense/
rebound contests — a modest thumb on the scale composed multiplicatively with the
§3.4 coach/chemistry modifiers.

**Coach modifiers (§3.4)** bend this flow without changing its shape (decisions.md
#022, all effects via the avg-10 deviation multiplier
`base × (1 + COACH_SENSITIVITY·(attr−10)/10)`):
- **`pace`** scales the **possession count** — a faster coach runs more
  possessions per game (more events), a slower coach fewer.
- **`offensiveScheme`** leans the shot-type draw (perimeter/three vs. inside/post)
  in `ShotSelector`.
- **`defensiveScheme`** scales the defense's turnover/foul pressure.
- Team chemistry skills also enter resolution: **`acumen`** is a small make-rate
  nudge in `ShotResolver`, and **`teamOffense`/`teamDefense`** a single
  possession-level efficiency multiplier. All are a modest thumb on the scale over
  the player-skill contest, not a replacement. `rotationDepth` /
  `substitutionAggressiveness` are **not** read in §3.4 — they belong to §3.5
  minutes/fatigue (Decision E).

### `play_type` values and their `outcome` vocabulary

| `play_type` | `outcome` | Meaning |
|---|---|---|
| `SHOT` | `MADE_2PT_DRIVE` | Made 2-point field goal (drive/finish at rim) |
| `SHOT` | `MADE_2PT_PERIMETER` | Made 2-point field goal (mid-range / perimeter) |
| `SHOT` | `MADE_2PT_POST` | Made 2-point field goal (post move) |
| `SHOT` | `MADE_3PT` | Made 3-point field goal (long range) |
| `SHOT` | `MISSED_2PT_DRIVE` | Missed 2-point field goal (drive/finish at rim) |
| `SHOT` | `MISSED_2PT_PERIMETER` | Missed 2-point field goal (mid-range / perimeter) |
| `SHOT` | `MISSED_2PT_POST` | Missed 2-point field goal (post move) |
| `SHOT` | `MISSED_3PT` | Missed 3-point field goal (long range) |
| `TURNOVER` | `STOLEN` | Ball handler lost the ball; defender credited a steal |
| `TURNOVER` | `LOST_BALL` | Unforced turnover (no steal credited) |
| `FOUL` | `SHOOTING_FOUL` | Defensive foul on a drive/post attempt; free throws follow |
| `FREE_THROW` | `MADE` | Free throw converted |
| `FREE_THROW` | `MISSED` | Free throw missed |
| `REBOUND` | `OFFENSIVE` | Offensive rebound; ball stays with the shooting team for a second-chance possession |
| `REBOUND` | `DEFENSIVE` | Defensive rebound; possession ends, ball goes to the other team |

After a missed `SHOT`, §3.3 rolls a rebound: the `REBOUND` event's
`primary_player_id` is the rebounder. An `OFFENSIVE` rebound keeps the ball with
the shooting team (a second-chance possession runs through the full flow again);
a `DEFENSIVE` rebound ends the possession. See the possession-flow section below.

### Shot types → skill matchups (decisions.md #021, Decision C)

| Shot type | Offensive skill(s) | Defensive skill(s) | Points |
|---|---|---|---|
| Drive / finish | `drive`, `finishing` | `rimProtection` | 2 |
| Perimeter / mid-range | `perimeter` | `individualDefense`, `shotContest` | 2 |
| Post | `post` | `individualDefense` | 2 |
| Long range / three | `longRange` | `shotContest` | 3 |

---

## `BoxScore`

Per-player stat line for a single game (the Phase 4 stats model aggregates these
into season totals).

- one row per **`(game_id, player_id)`** — FK to `game` and `player`. **No
  `team_id` on the row**: the player's team is derivable (`game` home/away +
  `player_team`), so storing it here would be a third copy of "what team is this
  player on" (the duplicate-source trap of #013/#015). If "team in *this* game"
  ever needs to survive a mid-season trade, derive it from `player_team_hist` by
  date, or denormalize then with a real consumer — not now.
- **Every player who took the floor gets a row (§3.5), not just the 5 starters.**
  With substitutions live, bench players who entered the game accumulate stats and
  minutes and so get their own box-score row. A player who never checked in gets no
  row (nothing to reconcile).
- per-player counters: points, rebounds (off/def), assists, steals, turnovers,
  fouls, FGA/FGM, 3PA/3PM, FTA/FTM (cf. roadmap §4.1).
- **accumulated during simulation**, then reconciled against the persisted
  `GameEvent` log (events are the source of truth — decisions.md #020).
- **Two columns exist but are not modeled yet — always `0`:**
  - **`blocks`** — there is no `BLOCK` play type; a blocked shot is currently
    indistinguishable from a normal miss (it becomes a `MISSED` `SHOT` → rebound).
    A real block model (a defender's `rimProtection`/`shotContest` converting a
    contest into a recorded block, with the miss attributed) is future §3.x work —
    the same honest "not modeled yet, stored as 0" state assists were in before
    §3.4. *(See roadmap §3.x deferred sim-fidelity.)*
  Every other counter is real and reconciles against the event log; `assists`
  became real in §3.4 (Decision B1), and `minutes` became real in §3.5 (below).

- **`minutes` is real as of §3.5 (decisions.md #023, Decision A).** The engine has
  no game clock (#021), so minutes are a **derived possession-share projection**,
  not a clocked measurement: each on-floor player accumulates a possession counter,
  and at box-score-write time the game's total possessions map to
  `PERIODS × 12` (+5 per OT) minutes by ratio, attributing each player their share.
  Team minutes sum to `5 × game-minutes` by construction. `blocks` remains an
  unmodeled `0` (no `BLOCK` play type — future §3.x).

---

## API surface — §3.6 *(decisions.md #024)*

§3.6 exposes the persisted game engine over OpenAPI. It is **API + read-projection +
entity→model mapping only** — no new engine logic (the engine is done through §3.5).
Spec lives in `gametime-api/yml/gametime.yaml`; the hand-written delegate is
`api/V1ApiDelegateimpl.java`.

### Operations

| Verb | Path | Request | Response | Errors |
|------|------|---------|----------|--------|
| `POST` | `/v1/game/simulate` | `SimulateGameRequest` | `200 GameResult` | `404` unknown team · `422` same-team |
| `GET`  | `/v1/game/{gameId}` | — | `200 GameResult` | `404` unknown game |
| `GET`  | `/v1/game/{gameId}/play-by-play` | — | `200 [GameEvent]` | `404` unknown game |

### Models

- **`GameResult`** *(the shared "here's your game" object — same shape for simulate
  and get; #024 A)*: `game: Game`, `homeBoxScore: [BoxScore]`, `awayBoxScore:
  [BoxScore]`. The box scores are **split home/away server-side** — the mapper
  resolves each `box_score` row's team via `player_team` (the row has no `team_id`,
  #020). The **event log is not included** — it's the separate play-by-play endpoint,
  so the payload stays bounded (~30 stat lines, not hundreds of events).
- **`Game`** *(the header)*: `id`, `homeTeamId`, `awayTeamId`, `status`, `homeScore`,
  `awayScore`, `periods`, **`seed`**. No per-period line scores — a period line is
  derivable from the event log (#020), so it isn't stored. Only `FINAL` status is
  produced by the engine.
- **`GameEvent`** *(one per play-by-play row)*: `sequence`, `period`, `offenseTeamId`,
  `defenseTeamId`, `playType`, `outcome`, `primaryPlayerId`, **`assistPlayerId`**
  (the §3.4 column, #022 B — surfaced in the API here per #024 D). **No time field**
  (#024 E — see below).
- **`BoxScore`**: `playerId` + the per-player counters (see the `BoxScore` section
  above). No `teamId` (#020).
- **`SimulateGameRequest`**: `homeTeamId` (required), `awayTeamId` (required),
  **`seed`** (optional `int64`). **No `possessionsPerPeriod`** — pace is not exposed
  (#024 C); the endpoint always uses `SimConfig.DEFAULT_POSSESSIONS_PER_PERIOD`, and
  per-game tempo variety comes from the coach `pace` attribute (#022 A).

### Seed — optional in, and PERSISTED *(#024 B, revises #021 A)*

`POST /simulate` accepts an **optional** seed: omitted ⇒ a fresh random `long` (real
games vary, per #021 A); provided ⇒ a reproducible run. The seed is now **persisted**
(`game.seed BIGINT`, appended to the unreleased `release.1.0.4.game.sql`) and
**echoed back** on the `Game` header — so a game is reproducible from its own record.
This revises #021 A's original "seed not persisted" stance; the variation rule is
unchanged (random default), we simply record what was rolled.

### Play-by-play — a plain ordered list, no pagination *(#024 D)*

`GET /{gameId}/play-by-play` returns the events as a **flat `[GameEvent]` in
`sequence` order** (reusing `GameEventRepo.findByGameIdOrderBySequenceAsc`). Event
volume is modest (~200–350 events/game), and a play-by-play UI wants the whole game;
pagination is deferred (the orphaned `pageNumber`/`pageSize` params, #019, stay
orphaned — a `period` filter is the likelier first future need). This supersedes the
"bounded/paginated at the read endpoint" language in the earlier sections — at the
current scale, unpaginated is correct.

### Event time — no column, derived on read *(#024 E, closes #021 B)*

§3.6 ships **no per-event time field and no time column**. `period` + `sequence`
suffice to render an ordered, period-grouped play-by-play; any display clock is
**computed on read** from `period` + `sequence` + pace. #021 B left the single-vs-range
column shape "for §3.6 to decide" — §3.6 resolves it as *derived, not stored*. The
revisit trigger is the **Phase 7 game view**: if that UI shows a per-event clock, it
decides the shape and adds a column only if computed-on-read proves insufficient.
Consequence: **`game.seed` is the only §3.6 schema change.**

### Errors *(#024 F)*

404 for an unknown `gameId` (both gets) or an unknown team on simulate (the engine
already throws `ResourceNotFoundException`, #021 E). **422 Unprocessable Entity** for
a same-team request (`homeTeamId == awayTeamId`) — a well-formed request that violates
a business rule, distinct from 400 "malformed"; a **new `ResourceUnprocessableException`**
+ `ApiExceptionHandler` mapping, the first 422 in the API. Error bodies stay empty,
matching the existing handler convention.
