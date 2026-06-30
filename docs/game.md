# Game Domain *(model is Phase 3.1; the engine that fills it is §3.2+)*

The Game domain is the **data a simulated game produces**: the matchup and its
result (`Game`), the event log that records how it unfolded (`GameEvent`), and
the per-player stat line for that game (`BoxScore`). These three are one cohesive
shape — a `Game` *has* `GameEvent`s and *produces* a `BoxScore` — so they live in
one doc, the way [roster.md](roster.md) holds player↔team + lineups + transactions
together.

This doc covers **§3.1 — the model only**. The possession engine that *fills*
these models (§3.2 shot selection/outcomes, §3.3 rebounding, §3.4 coaching
effects, §3.5 minutes/fatigue) is designed against the real possession flow when
that work starts — not guessed at here.

> **Scope discipline** (cf. decisions.md #014, #017): define the *entities* now;
> the *simulation logic* that produces them lands with the engine that consumes
> them. We shape the data, not the algorithm, ahead of a consumer that doesn't
> exist yet.

---

## Event persistence — RESOLVED *(decisions.md #020)*

The headline §3.1 choice (roadmap Design Decision #9) is settled: **every
`GameEvent` is persisted.** Play-by-play (§3.6 `GET /{gameId}/play-by-play`)
replays from stored rows rather than re-simulating, and the Phase 4 stats model
reconciles against the actual event log. The trade-off is row volume
(~150–200 events/game) — bounded/paginated at the *read* endpoint in §3.6 / Phase
4 (the large-result-set case #019 flagged), not by dropping the data. See
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
- *No in-game clock column yet.* §3.2 models time as an **abstract, configurable
  possession count** (decisions.md #021), and event time is **derived on read**
  (pace + `period` + `sequence`) for play-by-play display — not stored. A stored
  per-event time column (single value vs. range — undecided) is **deferred to
  §3.6**, where the play-by-play display is its consumer. `period` + `sequence`
  give full ordering today.

### Possession flow (§3.2) — see also [possession-flow.puml](possession-flow.puml)

Each possession produces **one or more** `GameEvent` rows in this order:

1. **Turnover check** — rolled before the shot. If triggered:
   - `TURNOVER` event → possession ends, ball goes to the other team.
2. **Foul check** — rolled on `DRIVE` and `POST` shot types only. If triggered:
   - `FOUL` event (primary_player = fouling defender) → free throws follow.
   - Two `FREE_THROW` events (primary_player = shooter), each with its own
     make/miss outcome. Possession ends after free throws.
3. **Shot** — if no turnover and no foul:
   - `SHOT` event → made or missed. On a miss, possession ends (rebounding is
     §3.3 — the seam is the `MISSED_*` outcome). On a make, points are scored.

A single possession therefore emits exactly **one** of these patterns:
- `TURNOVER`
- `FOUL` → `FREE_THROW` → `FREE_THROW`
- `SHOT` (made or missed)

All events in a possession share the same `offense_team_id` / `defense_team_id`
and `period`. `sequence` increments globally (not per possession).

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
| `REBOUND` | *(not emitted in §3.2)* | Deferred to §3.3 — a missed shot ends the possession |

**§3.2 does NOT emit** `REBOUND` events. A missed shot simply ends the
possession and the ball goes to the other team. The `MISSED_*` outcome on `SHOT`
is the seam where §3.3's rebounding model hooks in.

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
- per-player counters: points, rebounds (off/def), assists, steals, blocks,
  turnovers, fouls, minutes, FGA/FGM, 3PA/3PM, FTA/FTM (cf. roadmap §4.1).
- **accumulated during simulation**, then reconciled against the persisted
  `GameEvent` log (events are the source of truth — decisions.md #020).
