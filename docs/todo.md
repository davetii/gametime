# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Deferred work lives in the
Backlog at the bottom.

Current focus: **Phase 3.1 — Game Model: ✓ COMPLETE.** Entities, schema, repos,
and tests shipped (see roadmap.md §3.1, decisions.md #020). **Next up: §3.2 —
Possession Engine**; break it into tactical items here when that work starts.

---

## Phase 3.1 — Game Model

**Goal**: Define the *data a simulated game produces* — three cohesive models:
`Game` (matchup + result), `GameEvent` (possession-by-possession log), and
`BoxScore` (per-player stat line for one game). See [game.md](game.md) for the
domain design that frames this work.

### ⚠️ Read this before writing any code — scope discipline

This section is **the model only**. Do **NOT** build the possession/simulation
engine — that is §3.2–§3.5 and lands later with the code that *consumes* these
models. The same discipline governed the coach attributes (#018) and
`rotationOrder` (#014): *shape the data now, write the algorithm when its
consumer exists.* If you find yourself writing shot-probability math, stop —
that's out of scope.

Mirror the existing domain conventions exactly (see "Patterns to follow" below).
Don't invent new structure; copy what `PlayerEntity` / `CoachEntity` /
`PlayerTeamEntity` and their repos/mappers already do.

### Concretely, §3.1 delivers:

- New entities: `GameEntity`, `GameEventEntity` (events are persisted — #020),
  `BoxScoreEntity`.
- Liquibase changeset(s) creating the matching tables in the `gametime` schema
  (H2 + Postgres compatible, audit columns, Postgres triggers).
- Spring Data repos for each entity (`game`, `game_event`, `box_score`).
- **No OpenAPI surface in §3.1** — `Game`/`GameEvent`/`BoxScore` stay
  persistence-only and get their schemas + endpoints in §3.6 (decisions.md #020).
  So **no `gametime.yaml` change and no `EntityMapper` wiring** this section.
- Tests clearing the JaCoCo line-coverage gate (0.80 floor; aim ~90%).

---

## ✅ Decisions — RESOLVED (recorded in decisions.md #020, detailed in game.md)

The §3.1 modeling questions are settled — **do not re-open them**, just build to
them. Full rationale is in [decisions.md](decisions.md) #020 and
[game.md](game.md); the binding outcomes:

- **A — Event persistence**: **persist every `GameEvent`.** `box_score` is
  accumulated during sim and reconciled against the event log (events are the
  source of truth). `GameEventEntity` gets a real table + repo.
- **B — Game status**: enum `GameStatus { SCHEDULED, IN_PROGRESS, FINAL }`.
  Minimal — no CANCELLED/POSTPONED yet.
- **C — API surface**: **none in §3.1.** Persistence + entities only; OpenAPI
  schemas/endpoints land in §3.6 with their consumers. No `gametime.yaml` or
  `EntityMapper` change this section.
- **D — `BoxScore` key**: one row per `(game_id, player_id)`, FK to `game` and
  `player`, stat counters from roadmap §4.1. **No `team_id` on the row** (team is
  derivable; storing it duplicates a fact — #013/#015). No player snapshot. No
  stored team-totals entity (Phase 4 aggregation).
- **E — `GameEvent` shape**: minimal + additive — `game_id`, `sequence`,
  `period`, `offense_team_id`, `defense_team_id`, `play_type` (enum `SHOT,
  TURNOVER, REBOUND, FOUL, FREE_THROW`), `outcome` (free text), `primary_player_id`.
  **`sequence` is monotonic across the whole game and does NOT restart per
  period.** **No in-game clock column.** Richer participation is additive in §3.2.

---

## ⚠️ Risks / gaps / things that can bite you

- **JDK 21 only.** Every Maven command must set
  `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem` (Homebrew defaults
  to JDK 25, which breaks Lombok). See CLAUDE.md "Build requirements".
- **Generated vs. hand-written modules.** OpenAPI schema changes go in
  `gametime-api/yml/gametime.yaml` (regenerates stubs in `target/`). All
  entities/repos/services/mappers/tests go in `gametime-app`. Never hand-write
  in `gametime-api` (decisions.md #001).
- **Dual-DB Liquibase.** New tables must work on **both** H2 (tests) and
  Postgres (local). Copy the `release.1.0.1.sql` pattern: plain `CREATE TABLE`
  with audit columns having H2-compatible `default`s, then a separate
  `dbms:postgresql` changeset for the `on_new_row` triggers. New changeset file
  must be `include`d in `db/changelog.yml` **in dependency order** (game tables
  reference `team`/`player`, so they load *after* those — append at the end).
- **No FK to a season yet.** There is no `season`/`schedule` table (Phase 5).
  `Game` references `team` (home/away) but must **not** reference a season —
  don't fabricate that FK ahead of Phase 5 (#014/#017 discipline). A nullable
  `season_id` column with no FK/table is also premature; leave it out.
- **Seed data.** Unlike coach/roster, §3.1 ships **no seed rows** — games are
  produced by simulation, not seeded. Do not add a games CSV. (If a test needs a
  game, it should create one in-test, per the Backlog note about test fixtures
  not depending on production seed.)
- **Coverage gate is enforced at `mvn test`/`verify`.** New entities with Lombok
  `@Data` get getters/setters generated; JaCoCo counts them. The existing
  `*EntityTest` classes (e.g. `CoachEntityTest`, `PlayerEntityTest`) exist
  largely to exercise that boilerplate and keep the line ratio above 0.80 — you
  must add equivalents for the new entities or the gate fails.
- **`@Enumerated(EnumType.STRING)`** for all new enums (matches `Status`,
  `LineupRole`, `TransactionType`) — never ordinal.
- **Don't break existing tests.** Adding tables shouldn't, but if you touch
  `EntityMapper` or `changelog.yml` ordering, run the full suite. H2 recreates
  schema per run; Postgres dev DBs need `docker compose down -v` to pick up
  schema changes (decisions.md #009).

---

## Task sequence (do in order)

> Each task says where the file lives and which existing file to copy the
> pattern from. Run `mvn clean test` after each schema/entity step. Tick the
> `[ ]` box as each step lands.

- [x] **1. Decisions recorded.** ✅ *Done* — the §3.1 modeling decisions are
   settled in decisions.md #020 and game.md (see the RESOLVED block above); no
   doc-decision work remains. **First code step:** skim #020 + game.md once so
   the shapes are fresh, then start at step 2.

- [x] **2. Enums.** Add `entity/GameStatus.java` (`SCHEDULED, IN_PROGRESS, FINAL`) and
   `entity/PlayType.java` (`SHOT, TURNOVER, REBOUND, FOUL, FREE_THROW`). Plain
   Java enums — copy the style of `entity/TransactionType.java` /
   `entity/LineupRole.java`.

- [x] **3. Liquibase schema.** Create
   `src/main/resources/db/release.1.0.4.game.sql` (next free release number;
   1.0.1–1.0.3 are taken). Define `gametime.game`, `gametime.game_event`, and
   `gametime.box_score`. Follow `release.1.0.1.sql` exactly:
   - audit columns with H2 defaults (`create_user`, `create_date`,
     `update_user`, `update_date`);
   - FKs to `gametime.team` (home/away on `game`), `gametime.game`
     (box_score.game_id, game_event.game_id), `gametime.player`
     (box_score.player_id, game_event player refs);
   - a trailing `dbms:postgresql` changeset adding `on_new_row` `BEFORE INSERT`
     triggers for each new table.
   Then **register it** in `db/changelog.yml` with an `include` at the **end**
   (after the roster dataload), since these tables depend on team/player.

- [x] **4. Entities.** Add to `entity/`:
   - `GameEntity` — home/away team ids, `GameStatus`, period scores + final
     score, (no season FK). Copy `@Entity`/`@Table(schema = "gametime")`/`@Data`
     from `CoachEntity`/`PlayerTeamEntity`. Use `@Id` matching the table PK type
     (`String` id, like the other entities).
   - `GameEventEntity` — `game_id`, `sequence`, `period`, `offense_team_id`,
     `defense_team_id`, `play_type` (`@Enumerated(STRING)` `PlayType`), `outcome`
     (String), `primary_player_id`. Surrogate `String id` PK; `sequence` is
     game-wide and does **not** restart per period.
   - `BoxScoreEntity` — `game_id` + `player_id` plus the §4.1 stat counters
     (points, oreb, dreb, ast, stl, blk, tov, pf, min, fga, fgm, tpa, tpm, fta,
     ftm). **No `team_id`.** Surrogate `String id` PK (simplest, matches the
     codebase) over a composite key, for consistency.
   Match column-name `@Column(name = "...")` snake_case to the SQL.

- [x] **5. Repos.** Add Spring Data interfaces in `repo/` extending
   `CrudRepository<Entity, IdType>` (copy `PlayerTeamRepo`). Add finder methods
   the §3.6 endpoints will need, e.g. `GameRepo` (by id is free via
   `CrudRepository`), `BoxScoreRepo.findByGameId(String)`,
   `GameEventRepo.findByGameIdOrderBySequenceAsc(String)`. **Only add finders
   with a near-term consumer** — don't speculatively add query methods.

- [x] **6. No API / mapping work.** Per Decision C (#020), §3.1 is persistence-only —
   **do not** touch `gametime.yaml` or `EntityMapper`. The OpenAPI schemas and
   simulate/fetch endpoints land in §3.6. (Step kept as a marker so nobody adds
   an orphaned schema; skip to tests.)

- [x] **7. Tests** (this is the gate — see test-coverage skill). Add under
   `src/test/.../entity/`:
   - `GameEntityTest`, `GameEventEntityTest`, `BoxScoreEntityTest` —
     exercise constructor + every getter/setter so Lombok boilerplate
     is covered. Copy `entity/CoachEntityTest.java` / `entity/PlayerEntityTest.java`.
   - Enum tests if you add a non-trivial enum (copy `ConferenceEnumTest` if a
     pattern is needed; trivial enums may just be covered transitively).
   - Repo round-trip: a `@DataJpaTest`-style or existing integration harness
     test that persists and reads back a `Game` (+ box score / events) verifies
     the Liquibase schema actually creates the tables on H2. Check how existing
     tests bootstrap the context (`GametimeApplicationTests`,
     `RosterLineupDelegateTest`) and follow that.

- [x] **8. Verify the gate.**
   `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean test`
   must pass with JaCoCo ≥ 0.80 (target ~0.90). If new `@Data` getters/setters
   drag the ratio down, add the missing entity-test coverage rather than
   lowering the gate. Then optionally
   `JAVA_HOME=... mvn verify -Ptest` for the Docker/Postgres path to confirm the
   `dbms:postgresql` triggers are valid.

- [x] **9. Docs cleanup.** Check the §3.1 boxes in [roadmap.md](roadmap.md) (lines
   ~44–46). [game.md](game.md) and [decisions.md](decisions.md) #020 are already
   in sync with these decisions — only update them if you deviated from the
   resolved shapes while building. Reset this "Current focus" line to point at
   §3.2 (or "none active — between phases") and move any unfinished §3.1 scraps
   to the Backlog.

---

## Patterns to follow (quick reference)

- **Entity**: `software/daveturner/gametime/entity/CoachEntity.java`,
  `PlayerTeamEntity.java` — `@Entity` + `@Table(name=..., schema="gametime")` +
  `@Data`, `@Id` on a `String` id, `@Column(name="snake_case")`.
- **Enum + persistence**: `entity/TransactionType.java`,
  `entity/LineupRole.java`; persisted via `@Enumerated(EnumType.STRING)`.
- **Repo**: `repo/PlayerTeamRepo.java` — `extends CrudRepository<E, String>`
  with derived finder methods.
- **Liquibase table + triggers**: `db/release.1.0.1.sql` (the canonical
  H2+Postgres pattern); registration in `db/changelog.yml`.
- **Entity tests for coverage**: `test/.../entity/CoachEntityTest.java`,
  `PlayerEntityTest.java`.
- *(Not needed in §3.1: `EntityMapper`, OpenAPI `gametime.yaml`, and the
  delegate/service layer — those are §3.6. Decisions are already in
  `decisions.md` #020.)*

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
