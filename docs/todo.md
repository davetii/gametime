# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Deferred work lives in the
Backlog at the bottom.

Current focus: **Coach model** — the pre-gameplay prerequisite. Phase 2
(Rosters & Lineups) is complete; its record lives in [roadmap.md](roadmap.md)
§2 and decisions.md #013–#017.

---

## Coach model (CURRENT — gates the game engine)

`CoachEntity` is name-only; Phase 3 (§3.4/§3.5) can't be built against it.
Design work goes in [coach.md](coach.md); this is the task checklist.

- [ ] Resolve **Design Decision #3**: coach attributes as continuous (1–10 like
      players) vs. categorical styles (enum). Write the decision into decisions.md.
- [ ] Define the coach attribute set + the engine-facing interface (pace, shot
      distribution, defensive scheme, rotation style — consumed by §3.4/§3.5).
- [ ] Schema + entity: add the attributes to `CoachEntity` / `coach` table
      (Liquibase changeset), seed values into `coach.csv`.
- [ ] Map attributes through `EntityMapper`; expose on the coach in `GET /team`
      if the API should surface them.
- [ ] Tests for the new mapping/entity (JaCoCo gate).
- [ ] Implement the attribute *model* only — coaching *effects* land with the
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
