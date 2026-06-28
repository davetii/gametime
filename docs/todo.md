# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Deferred work lives in the
Backlog at the bottom.

Current focus: **Coach model** — the pre-gameplay prerequisite. Phase 2
(Rosters & Lineups) is complete; its record lives in [roadmap.md](roadmap.md)
§2 and decisions.md #013–#017.

---

## Coach model (COMPLETE — unblocks the game engine)

Was: `CoachEntity` name-only, so Phase 3 (§3.4/§3.5) had nothing to build
against. Now: 5 continuous attributes (decisions.md #018) modeled end-to-end
(schema → entity → mapper → API), seeded, and tested. Design in
[coach.md](coach.md). The next move is **Phase 3** — the engine that reads
these attributes (the `f(...)` effects deferred here). See roadmap.md §3.

- [x] Resolve **Design Decision #3**: coach attributes continuous vs. categorical.
      → **#018**: continuous **1–20, avg 10** (matches players #008), not enums.
      Engine-led rationale; 1–10 rejected (forks the avg-10 deviation helper).
- [x] Define the coach attribute set + the engine-facing interface. → **5**
      attributes (pace, offensiveScheme, defensiveScheme, rotationDepth,
      substitutionAggressiveness), all §3.4/§3.5 consumers; `playerDevelopment`
      deferred to Phase 6 (its only consumer). See [coach.md](coach.md).
- [x] Schema + entity: 5 `SMALLINT` columns added to the `coach` table
      (`release.1.0.1.sql`) + dataload XML + 5 `Integer` fields on `CoachEntity`.
      Seeded into `coach.csv` **wide-spread** (mean 10.0, stdev 3.4, range 3–18).
- [x] Map attributes through `EntityMapper.entityToCoach`; **exposed** on the
      `Coach` schema in `gametime.yaml`, so they surface in `GET /team` & `/league`.
- [x] Tests for the new mapping (`EntityMapperTest`, distinct values to catch
      transposition); full suite + JaCoCo `verify` gate green (114 tests).
- [x] Attribute *model* only — coaching *effects* deferred to the Phase 3 engine
      that consumes them (no formulas in a vacuum — cf. #014).

**Coach model: complete.** Remaining design open questions (not blocking):
- Derived **archetype label** for display ("modern offense") — compute-on-read,
  add when a UI consumer is real (Phase 7). coach.md open-Q #1.
- **GM attributes** — same name-only gap; consumers are further off (Phase 6.4).
  Resolve separately, later. coach.md open-Q #3.

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
