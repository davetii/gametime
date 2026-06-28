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

## Open modeling decision — event persistence

The headline §3.1 choice (roadmap Design Decision #9): **persist every
`GameEvent`, or only the final `BoxScore`?** This determines whether
play-by-play (§3.6 `GET /{gameId}/play-by-play`) is replayable from stored data
or regenerated. Resolve and record in decisions.md before the schema lands; it
also interacts with #009 (CSV-driven seed) and the Phase 4 stats model.

*(TODO: fill in the decision once 3.1 starts. Cross-ref the pagination call in
#019 — play-by-play is one of the large result sets that motivates pagination at
Phase 4.)*

---

## `Game`

The matchup and its outcome.

- `homeTeam` / `awayTeam` — references to `Team`
- quarter structure — period scores, regulation vs. overtime
- final score
- *(TODO: status lifecycle — SCHEDULED → IN_PROGRESS → FINAL; ties to Phase 5
  schedule)*

---

## `GameEvent`

The possession-by-possession event log: possessions, plays, outcomes. One game
is an ordered sequence of these. Persistence is gated on the open decision above.

- *(TODO: event shape — possession id, offense/defense team, play type, outcome,
  participating players, clock/period. Define against the §3.2 possession flow.)*

---

## `BoxScore`

Per-player stat line for a single game (the Phase 4 stats model aggregates these
into season totals).

- per-player: points, rebounds (off/def), assists, steals, blocks, turnovers,
  fouls, minutes, FGA/FGM, 3PA/3PM, FTA/FTM (cf. roadmap §4.1)
- *(TODO: derive from `GameEvent`s vs. accumulate during simulation — decide with
  the event-persistence call above.)*
