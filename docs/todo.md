# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [PROJECT_PLAN.md](PROJECT_PLAN.md). Deferred work lives in the
Backlog at the bottom.

Current focus: **Phase 2 roster APIs shipped — next up: roster rules + rotation
depth (PROJECT_PLAN.md §2.2/§2.3).**

---

## Phase 2 — Rosters & Lineups

### 2.1 Roster APIs — DONE (2026-06-27)

Three endpoints on top of the existing player↔team model, plus the lineup
(starting 5 + rotation order) concept the game engine needs.

- [x] `GET /v1/team/{teamId}/roster` — `Roster` of entries (player + lineupRole
      + rotationOrder). 200/404.
- [x] `PUT /v1/team/{teamId}/lineup` — replace-all `LineupRequest`; hard 400 on
      ≠5 STARTER, dup player, or dup rotation order; 404 if a player isn't on
      the team. Returns the updated roster.
- [x] `DELETE /v1/team/{teamId}/{playerId}` — release to free agency: drop the
      `player_team` row, append a `RELEASE` row to `player_team_hist`. 200/404.
- [x] Schema: `release.1.0.4.lineup.sql` adds `lineup_role` + `rotation_order`
      to `player_team`. Entity, service, delegate, `EntityMapper.toRosterEntry`,
      `.http` samples all wired. 12 delegate tests; JaCoCo gate green.

Decisions locked 2026-06-27: lineup lives on `player_team` (not a new join
table); lineup PUT is replace-all with a hard 5-starter invariant.

### 2.2 Roster rules (next)
- [ ] Roster size limits (e.g. 15 active, 5 minors).
- [ ] Position minimums/maximums per roster.
- [ ] Roster validation on add (currently only "not already on a team").

### 2.3 Lineup & rotation depth (feeds the engine)
- [ ] Bench rotation order → minutes allocation.
- [ ] Fatigue model: how `endurance` interacts with minutes played.
- [ ] Coach attributes influence rotation depth (gated on Coach model — backlog).

---

## Backlog

Deferred by decision — not in flight. Promote into a phase section when picked up.

- [ ] Hand-tune marquee/star players to 18–20 where appropriate (the rescale was
      mechanical). Deferred until the game engine shows whether it matters.
- [ ] Design Coach attribute model (open question: continuous attrs vs. style
      enum — see PROJECT_PLAN.md open question #3, DECISIONS.md). Parked
      2026-06-27 until the engine defines what coach attributes must drive;
      revisit alongside Phase 3.4/3.5 and Phase 6.
- [ ] Design GM attribute model (scouting, negotiation, draft, analytics). Parked
      with Coach above; revisit alongside Phase 6.4.
- [ ] Update OpenAPI spec once Coach/GM models are decided.
- [ ] Review skill calculator formulas for balance after the game engine exists
      (clutch/foulProne/transition etc. only get exercised once possessions run).
- [ ] Evaluate Testcontainers as alternative to H2 for integration tests.
- [ ] Add API pagination (parameters already defined in OpenAPI spec but not wired).
- [ ] Consider adding player age field (currently only yearsPro, no birth date).
- [ ] Set up React project in `gametime-frontend/`.
