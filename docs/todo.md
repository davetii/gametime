# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Deferred work lives in the
Backlog at the bottom.

Current focus: **Phase 2 (Rosters & Lineups) COMPLETE. Before gameplay: revisit
the Coach model (open Design Decision #3). Then Phase 3 — Game Engine.**

---

## Phase 2 — Rosters & Lineups — COMPLETE (2026-06-28)

### 2.1 Roster APIs — DONE (2026-06-27)

Three endpoints on top of the existing player↔team model, plus the lineup
(starting 5 + rotation order) concept the game engine needs.

- [x] Team roster with lineup slots — folded into `GET /v1/team/{teamId}`
      (`Team.players` are `RosterEntry`); standalone `/roster` removed (#015).
- [x] `PUT /v1/team/{teamId}/lineup` — replace-all `LineupRequest`; hard 400 on
      ≠5 STARTER, starter-with-order, dup player, or dup rotation order; 404 if a
      player isn't on the team. Returns the updated Team.
- [x] `DELETE /v1/team/{teamId}/{playerId}` — release to free agency: drop the
      `player_team` row, append a `RELEASE` row to `player_team_hist`. 200/404.
- [x] Schema: `release.1.0.4.lineup.sql` adds `lineup_role` + `rotation_order`
      to `player_team`. Entity, service, delegate, `EntityMapper.toRosterEntry`,
      `.http` samples all wired. 12 delegate tests; JaCoCo gate green.

Decisions locked 2026-06-27: lineup lives on `player_team` (not a new join
table); lineup PUT is replace-all with a hard 5-starter invariant.

### 2.2 Roster rules — DONE (2026-06-28)
- [x] Roster size limits — 15 active (everything but MINORS), 5 minors. Active cap
      enforced on sign (`POST /{playerId}` → 409) and on lineup PUT; minors cap
      enforced on lineup PUT (→ 400). (#016)
- [x] Roster validation on add — sign now defaults `lineupRole = INACTIVE` and
      checks the active-roster cap (was: only "not already on a team"). (#016)
- [~] Position minimums/maximums per roster — **decided against** (#017). No seed
      team carries all 9 positions, so minimums would invalidate the league; and a
      lopsided roster (e.g. 10 centers) is best punished by the game engine, not an
      API rule. Roster construction stays unconstrained by position.

> The old §2.3 (minutes allocation, fatigue, coach rotation influence) was **not
> roster content** — it's "produced by games being played" (gameplay), per the
> domain boundary in roster.md. It has moved to **roadmap.md §3.5**. Its one
> roster-domain input — the `rotationOrder` bench depth chart — already shipped
> in 2.1 (#014).

## Next — before Phase 3 (gameplay)

### Coach model — revisit (gates the game engine)
- [ ] Resolve **Design Decision #3**: coach attributes as continuous (1–10 like
      players) vs. categorical styles (enum). `CoachEntity` is name-only today.
- [ ] Define the coach attribute set + the interface the engine reads (pace, shot
      distribution, defensive scheme, rotation style — consumed by §3.4/§3.5).
- [ ] Implement only the attribute model now; the *coaching effects* land with the
      engine that consumes them (avoid designing formulas in a vacuum — cf. #014).

---

## Backlog

Loose tactical chores with no phase home (deferred scope lives in
[roadmap.md](roadmap.md), not here):

- [ ] Hand-tune marquee/star players to 18–20 where appropriate (the rescale was
      mechanical). Deferred until the game engine shows whether it matters.
- [ ] Evaluate Testcontainers as an alternative to H2 for integration tests.
- [ ] Separate test seed data from production seed. Today both the `local`
      (Postgres) and test (H2) profiles load the *same* Liquibase changelog
      (`db/changelog.yml` → `players.csv`, `roster.csv`, `release.1.0.1.dataload.sql`),
      so tests assert against production seed rows. Runtime league changes (trades,
      new signings) only touch Postgres and don't affect tests, but *editing the seed
      files* can break tests that hardcode team IDs / sizes. Introduce a small fixed
      test-only fixture (e.g. `test/resources/db/` changelog the test profile points
      at) so `main/resources/db/` can evolve for production independently. Interim
      mitigation done: roster-rule tests in `RosterLineupDelegateTest` now sign their
      own players instead of assuming seed roster sizes; remaining brittleness is the
      hardcoded team IDs themselves.
