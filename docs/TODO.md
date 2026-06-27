# TODO

Tactical task list. Check items off or remove them as completed. For the
big-picture phased roadmap, see [PROJECT_PLAN.md](PROJECT_PLAN.md).

Current focus: **Phase 2 — Rosters & Lineups**.

---

## Phase 1 — Player Modeling — DONE

Player model hardened to 21 attributes + 23 skills on a consistent 1–20 /
avg-10 scale. Data is CSV-driven (`players.csv`, 422 players, loaded via
Liquibase). All 23 `*SkillCalculator`s run off a shared deviation helper
(`adj`/`comboAdj`/`experienceAdj`/`clamp`); every skill centers at 10.0 for an
average player. See [player.md](player.md) for the design and the calculator
classes for exact formulas.

- [ ] Hand-tune marquee/star players to 18–20 where appropriate (rescale was
      mechanical). Low priority — deferred until the game engine shows whether
      it matters.

## Phase 1b — Foundation (CRUD) — DONE

- [x] `createPlayer`, `updatePlayer`, `addPlayerToTeam` endpoints (happy +
      404/409 paths, tested).
- [x] `fetchConference` DROPPED — clients filter `/v1/league`. See DECISIONS.md #010.
- [x] Player↔team decoupled: `player_team` (current) + `player_team_hist`
      (append-only) tables; `Player.currentTeamId` derived; `GET
      /v1/player/{id}/history`. See DECISIONS.md #012.

### Coach / GM — PARKED
Deferred by decision (2026-06-27). The slot exists (name-only `Coach`/`GM`)
but attribute design is on hold until the game engine defines what coach/GM
attributes actually need to drive. Revisit alongside Phase 3.4/3.5 and Phase 6.
- [ ] Design coach attribute model (open question: continuous attrs vs. style
      enum — see PROJECT_PLAN.md open question #3, DECISIONS.md)
- [ ] Design GM attribute model
- [ ] Update OpenAPI spec once Coach/GM models are decided

## Phase 1c — Data model evaluation — DONE (2026-06-27)

Quality pass over attributes/skills + positions. Conclusion: the model is
internally consistent and the archetypes are legible (guards = IQ/character,
forwards = scoring, bigs = defense/grit). No further tuning needed pre-engine;
balance will be revisited once games simulate (see Backlog).

- [x] Rename position `BG` (Two Guard) → `SG` (Shooting Guard): enum, OpenAPI
      spec, 56 CSV rows, docs. SG was previously absent; BG profiled as a SG.
- [x] Reviewed CG (combo guard) — left as-is; genuinely spans the PG–SG range.
- [x] Reviewed W (wing) — left as-is; no dominant tweener exists in this league
      by design. Tuned 2 wings (Griffin, Westbrook) for athleticism.
- [x] Reviewed F (generic forward) — confirmed as the premium two-way archetype;
      reclassified 3 perimeter-only forwards (Carrado, Macafee, Zenobile) → SF.
- [x] Reviewed FC (forward-center) — weakest bucket, accepted as the intentional
      "lumbering big" archetype; tuned 6 rotation bigs for depth.
- [x] Composite analyses (scoring / defense / basketball-IQ / intangibles)
      confirmed the model holds together; F is the strongest bucket, WING/FC the
      weakest (by design).

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

- [ ] Review skill calculator formulas for balance after the game engine exists
      (Phase 1c deferred final balance to here — clutch/foulProne/transition etc.
      only get exercised once possessions run).
- [ ] Evaluate Testcontainers as alternative to H2 for integration tests.
- [ ] Add API pagination (parameters already defined in OpenAPI spec but not wired).
- [ ] Consider adding player age field (currently only yearsPro, no birth date).
- [ ] Set up React project in `gametime-frontend/`.
