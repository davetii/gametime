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

Initial (intentionally minimal — grows additively with the §3.2 engine):

- `game_id` — FK to `game`
- `sequence` — monotonic ordering **across the whole game; does not restart per
  period**, so play-by-play is a single `ORDER BY sequence` read
- `period`
- `offense_team_id` / `defense_team_id`
- `play_type` — enum, start small: `SHOT, TURNOVER, REBOUND, FOUL, FREE_THROW`
- `outcome` — free text for now (enum-ify when the engine's outcomes are known)
- `primary_player_id` — the one player the event is about, for now. Richer
  participation (assister, defender, rebounder vs. shooter) is **additive** when
  §3.2 defines it.
- *No in-game clock column yet.* §3.2 models time as an **abstract, configurable
  possession count** (decisions.md #021), and event time is **derived on read**
  (pace + `period` + `sequence`) for play-by-play display — not stored. A stored
  per-event time column (single value vs. range — undecided) is **deferred to
  §3.6**, where the play-by-play display is its consumer. `period` + `sequence`
  give full ordering today.

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
