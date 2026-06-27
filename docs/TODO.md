# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [PROJECT_PLAN.md](PROJECT_PLAN.md). Deferred work lives in the
Backlog at the bottom.

Current focus: **Phase 2 — Rosters & Lineups**.

---

## Phase 2 — Rosters & Lineups (CURRENT)

Goal: finish the roster API surface on top of the existing player↔team model,
and add the lineup (starting 5 + rotation order) concept the game engine needs.
Three endpoints from PROJECT_PLAN.md §2.3.

### Decisions (locked 2026-06-27)
- **Lineup storage**: extend `player_team` with `lineup_role` + `rotation_order`
  columns (lineup is a property of the current assignment; avoids a duplicate
  team↔player join table).
- **Release behavior** (`DELETE`): remove the `player_team` row AND append a
  release record to `player_team_hist` — player returns to free agency, past
  stints stay queryable via `/history`. Mirrors `addPlayerToTeam` in reverse.

### Schema
- [ ] Liquibase changeset: add `lineup_role` (STARTER/ROTATION/BENCH/INACTIVE,
      nullable) + `rotation_order` (int, nullable) to `player_team`.

### API spec (gametime.yaml)
- [ ] `GET /v1/team/{teamId}/roster` — new `Roster` schema: list of entries
      (player + lineup_role + rotation_order). 200/404.
- [ ] `PUT /v1/team/{teamId}/lineup` — `LineupRequest` schema (ordered entries).
      200 / 400 (validation) / 404.
- [ ] `DELETE /v1/team/{teamId}/{playerId}` — release player. 200 / 404.

### App code (gametime-app)
- [ ] `PlayerTeamEntity`: add `lineupRole` + `rotationOrder` fields.
- [ ] `GametimeService`/`Imp`: `getRoster`, `setLineup`, `removePlayerFromTeam`.
- [ ] `V1ApiDelegateimpl`: wire the 3 operations.
- [ ] `EntityMapper`: map roster entries to the `Roster` schema.

### Validation rules (setLineup)
- [ ] Exactly 5 STARTER entries.
- [ ] All playerIds belong to the team → else 404.
- [ ] No duplicate players in the request.
- [ ] `rotation_order` unique among non-bench entries.

### Tests (target ~90% per test-coverage gate)
- [ ] Delegate: roster happy path + 404; setLineup happy + each validation
      failure (not-5-starters, player-not-on-team, dupes); delete happy + 404 +
      history-record-on-release.
- [ ] `EntityMapper` roster mapping.

### Housekeeping
- [ ] Add `.http` samples for the 3 new endpoints.

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
