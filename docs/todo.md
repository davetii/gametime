# TODO

Tactical task list for the **current phase only**. For the phased roadmap and what has
shipped, see [roadmap.md](roadmap.md). Chores and parked ideas live in beads (`bd list`);
deferred *gameplay* scope lives in roadmap.md's phase bullets.

Current focus: **Phase 4 — Statistics & Box Scores, EXECUTE-READY.** The design is
resolved as [decisions.md](decisions.md) **#045** (design pass `gametime-ta7`, 2026-10).
Next session: execution (`gametime-ra1`), following the plan below.

Phase 3 is an **open arc**. The design pass found event-log gaps it deliberately routed to a
**Phase 3 revisit after Phase 4 ships** (roadmap.md, starting with `gametime-3sc`). Do not
pull that work into this phase.

---

## Design pass — how each question resolved

The reasoning behind #045's letters. Deleted when Phase 4 closes; the letters are the
record.

| Q | Question | Resolved as | Why, in one line |
|---|---|---|---|
| 1 | Team stats: stored or computed? | #045 C | A team line is the sum of its players' rows; a stored copy is a third tally to drift |
| 2 | Which team a player's line belongs to | #045 D | `player_team` is current-only, so today's read-time lookup mis-buckets anyone released or moved after a game. A `player_team_hist` lookup was rejected: seeded players have no history rows (`gametime-6x9`), there is no game time to compare, and from Phase 5 game dates and move dates run on different clocks |
| 3 | Games started | #045 E, M | Lineups are replace-all (#014), so who started a past game is unrecoverable unless stored. Check-in events would make it derivable; they come after Phase 4 (M) |
| 4 | Possessions for pace and ratings | #045 F | The count depends on both coaches' pace and the active profile at sim time and is in no event, so it cannot be recomputed. Two columns: they are two facts, equal only by today's loop |
| 5 | What "efficiency rating" means | #045 H | Team ratings per 100 possessions; PER needs league normalization and has no consumer |
| 6 | "Season" before Phase 5 | #045 I | A season table now would be built ahead of its consumer. The aggregation code is reused by a future season-close rollup (`gametime-1b1`) |
| 7 | Derive or store `box_score` | #045 A, B | Today stats, score and events are three tallies agreeing by convention; one write path from the log makes "events win" true in code. The counters stay because the engine reads them mid-game |
| 8 | Game log order | #045 G | `create_date` is real-world run time; from Phase 5 a season sims in minutes but is played across months |
| 9 | Leaders and game highs | #045 J, K | Per-game average is the real-world convention; the qualifier is relative because local DBs hold ad-hoc games of uneven count |
| 10 | Existing data | #045 L | Unreleased; old rows lack every new column and a backfill would repeat Q2's bug |
| 11 | Keys and indexes | #045 L | #020 says keyed `(game_id, player_id)` but nothing enforces it; Postgres does not index FKs |

---

## Phase 4 execution plan (decisions.md #045 — resolved)

**Build preamble.** Java 21 or Lombok breaks:
`JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`. The **JaCoCo gate is
per-package and runs at `install`, not `test`**: invoke the `test-coverage` skill; a green
`mvn test` does not prove it passes. A new package (e.g. `stats`) has its own gate. Invoke
`project-docs` before touching any doc. Step 3 touches `PossessionEngine`, so run the sim
tests **alone** afterwards (CLAUDE.md).

**⚠ PREREQUISITE — reset the local database** (#045 L): no backfill, so old games would
pollute every aggregate.

```bash
cd gametime-service && docker compose down -v && docker compose up -d
```

**⚠ EXPECTED BLAST RADIUS: no simulated number moves.** No step adds an RNG draw or changes
what the engine does; the derived `box_score` must equal today's copied one value for value.
**Prove it with the same-seed diff**: run the harness on seeds 1000–5000 before Step 1 and
after Step 4, diff line for line. Any difference is a finding, not noise. No seeded test
should need re-baselining. The harness (profile from the ENVIRONMENT, confirm `Profiles:
local,baseline` and all four reconciliation lines `OK`):

```bash
cd gametime-service && for s in 1000 2000 3000 4000 5000; do SPRING_PROFILES_ACTIVE=local,baseline JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -q -pl gametime-app test -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=$s -DfailIfNoTests=false; done
```

**Step 0 — baseline and claim**
- [ ] Claim `gametime-ra1` (the execution bead) and `gametime-fwy`
      (#045 B supersedes it; it closes in Step 11).
- [ ] Run the harness on the five seeds **before any change**; keep the output for the diff.

**Step 1 — schema: changeset `1.04.6` appended to `release.1.0.4.game.sql` (#045 D, E, F, G, L)**
- [ ] `box_score.team_id VARCHAR` + FK to `team(id)`; `box_score.started BOOLEAN`.
- [ ] `game.home_possessions SMALLINT`, `game.away_possessions SMALLINT`,
      `game.played_at TIMESTAMP`.
- [ ] Unique constraint `box_score(game_id, player_id)`; indexes on `box_score(player_id)`
      and `box_score(team_id)`.
- [ ] All nullable, no `dbms` gate (the established shape). Comment block in the file's
      house style. ⚠ Never edit `1.04.1`–`1.04.5`.
- [ ] Entities: `BoxScoreEntity.teamId`, `started`; `GameEntity.homePossessions`,
      `awayPossessions`, `playedAt` (`LocalDateTime`, matching `player_team_hist`). Update
      the class javadocs that say "no team_id" (#045 D).
- [ ] ⚠ **`ddl-auto=update` masks a missing changeset**: Hibernate adds the column itself
      and only warns (`gametime-r3s`). Confirm each column exists because Liquibase made it,
      e.g. by a repo test that reads it back on H2 with the changeset in place, not by a
      green boot.

**Step 2 — `BoxScoreSummarizer`: the event log → per-player lines (#045 A)**
- [ ] A pure class (no Spring, no repos) in `sim`: input the game's
      `List<GameData.EventRecord>`, output one line per player id carrying every derived
      column in the table below, plus each team's derived points.
- [ ] Use the engine's outcome constants where they exist (`GameData.TECHNICAL_FOUL_OUTCOME`,
      `PossessionEngine.FLAGRANT_FOUL_*`); prefix/suffix matching (`MADE_`, `_3PT`,
      `BLOCKED_`) as the harness already does.
- [ ] `BoxScoreSummarizerTest`: one hand-built event list per table row, plus the traps:
      the charge's two rows (one turnover AND one foul), a technical (not a foul), an
      `OUT_OF_BOUNDS_*` rebound (no one credited), a blocked three (FGA + 3PA, no FGM),
      an and-1 (made FG, then a foul on the defender, then one FT).

**Step 3 — possessions counted by the engine (#045 F)**
- [ ] `GameData` gains per-team possession counters; `PossessionEngine.simulate()`
      increments the offense's count once per loop iteration (a retention is the same
      possession). **No RNG, no other change.**
- [ ] Test: `home == away`, and both equal the paced per-period count ×
      `SimConfig.PERIODS` plus the OT count per overtime period. ⚠ The equality is pinned **on purpose**: a future engine
      change that breaks it (a clock, the possession arrow in `gametime-3sc`) must fail
      this test and be accepted deliberately (#045 F).

**Step 4 — `GameSimulator` writes `box_score` from the summarizer (#045 A, B, D, E, G)**
- [ ] Build the lines with `BoxScoreSummarizer` from `data.getEvents()`, for every player
      with `tookFloor()` (the predicate is unchanged; see its comment). Non-derived fields:
      `minutes` (unchanged formula), `started`, `teamId` (`PlayerGameState.getTeamId()`).
- [ ] **The guard (#045 B):** before saving, compare every derived column with the
      player's `PlayerGameState` counter, and each team's derived points with
      `GameData`'s score. Any difference throws `IllegalStateException` naming player,
      column, derived and counted values. `@Transactional` rolls the game back.
      Also throw if an event names a player with no line (credited but never took the floor).
- [ ] `game.home_score` / `away_score` = the derived team points (equal to `GameData`'s by
      the guard). `game.home_possessions` / `away_possessions` from Step 3.
- [ ] `game.played_at` = now, from an injected `java.time.Clock` so tests can pin it
      (#045 G). ⚠ Never read `create_date`.
- [ ] `started` (#045 E): `PlayerGameState` gains a never-cleared `startedGame` flag, set in
      `RotationState`'s constructor in the same loop as `markTookFloor()`. ⚠ **Not**
      `isStarter()`: that reads `lineupRole`, and a released starter leaves four.
- [ ] Tests: the guard fires (a test-only way to desync one counter, or a summarizer
      fed a doctored event list); exactly five `started` per team per game, **including a
      team whose fifth `STARTER` was released** (the first bench player starts); `team_id`
      set on every row; possessions and `played_at` persisted.
- [ ] Run the sim tests alone, then the harness same-seed diff against Step 0. **Must be
      identical.**

**Step 5 — fix the home/away split on `GameResult` (#045 D)**
- [ ] `EntityMapper.toGameResult` buckets by `box_score.team_id` vs `game.home_team_id`;
      delete `GametimeServiceImp.homePlayerIds()` and its `player_team` lookup.
- [ ] Regression test: simulate, **release a home player**, fetch the game: his line stays
      in `homeBoxScore`. (Fails on today's code.)
- [ ] Test: `team_id` equals the team of the player's latest `player_team_hist` row at sim
      time, for a player signed before the game (#045 D's agreement check; seeded players
      have no history rows until `gametime-6x9`, so use a signed player).

**Step 6 — `StatsConfig` (#045 J)**
- [ ] `@ConfigurationProperties(prefix = "stats")`, values in `application.properties`
      (not a sim profile): `stats.leaders.min-games-fraction=0.70`,
      `stats.leaders.min-fgm-per-game=3.66`, `stats.leaders.min-3pm-per-game=1.0`,
      `stats.leaders.min-ftm-per-game=1.52`, `stats.leaders.default-limit=10`,
      `stats.leaders.max-limit=100`.
- [ ] **No Java initializers** (SimConfig's rule: a default in Java is a second value).
      A binding test asserts every property is bound. ⚠ `SimConfig`'s 63/29 counts do
      **not** change.

**Step 7 — the aggregation layer (#045 C, H, I)**
- [ ] A `StatsService` (interface + impl, beside `GametimeService`) over `box_score` ⋈
      `game`, using `SUM … GROUP BY` queries (JPQL or native: builder's call) into
      projections. Never reads `game_event` (#045 C).
- [ ] **One scope seam (#045 I):** every aggregate takes the same game filter, today "all
      `FINAL` games". Phase 5 replaces it with a season; nothing else should need to change.
- [ ] Percentages from summed makes/attempts, never a mean of per-game percentages.
      TS% = PTS / (2 × (FGA + 0.44 × FTA)); eFG% = (FGM + 0.5 × 3PM) / FGA. Null when the
      denominator is 0.
- [ ] Team: totals and per-game for the team (`team_id = T`) and its opponents (the other
      rows of the same games); possessions from the `game` side it played;
      pace = possessions × 48 / game minutes; offensive/defensive rating = points
      scored/allowed × 100 / possessions; net = their difference.
- [ ] "Team games" (the qualifier's base) = the most `FINAL` games any one team has in
      scope.

**Step 8 — OpenAPI + endpoints (#045 C, H, J, K)**
- [ ] `GameResult` gains `homeTeamStats` / `awayTeamStats`: totals + possessions + pace +
      the three ratings for that one game.
- [ ] `GET /v1/player/{playerId}/stats`: games played and started, minutes, totals,
      per-game, FG% / 3P% / FT% / TS% / eFG%. 404 for an unknown player.
- [ ] `GET /v1/player/{playerId}/gamelog`: one entry per game in `played_at`, then id,
      order: game id, `playedAt`, team, opponent, home/away, the game's score, `started`,
      the box line. 404 for an unknown player.
- [ ] `GET /v1/team/{teamId}/stats`: team and opponent totals and per-game, pace, ratings.
      404 for an unknown team. No win–loss record (standings are Phase 5).
- [ ] `GET /v1/league/leaders?category=&limit=` (#045 J): `category` an enum (points,
      rebounds, offensive and defensive rebounds, assists, steals, blocks, turnovers,
      minutes, fouls, technical fouls, FG%, 3P%, FT%, TS%, eFG%). Response:
      `category`, `basis` (`PER_GAME` | `TOTAL`), entries of `rank, playerId, playerName,
      teamId, gamesPlayed, value` plus the counts behind it. `teamId` = the team of the
      player's most recent game. Ties share a rank; within a tie, more games played first,
      then player id. Unknown category or `limit` outside 1–100 → 400; nobody qualifies →
      empty list. The category enum's description names the `TOTAL` exception.
- [ ] `GET /v1/league/game-highs?category=&limit=` (#045 K): a separate counting-stat enum
      (no percentages); entries of `rank, playerId, playerName, teamId, opponentTeamId,
      gameId, playedAt, value`; a player may appear more than once; ties share a rank,
      earliest `played_at` first, then game id. Same 400s.
- [ ] Raw numbers, unrounded. Delegate methods in `V1ApiDelegateimpl` calling
      `StatsService`; never hand-write in `gametime-api`.

**Step 9 — tests and coverage**
- [ ] Service tests over a small seeded set of games with known totals: per-game vs
      percentage math, the qualifier at the 70% boundary, `TECHNICAL_FOULS` by total
      without a qualifier, ties, the opponent split, a traded player's line counting for
      the team he played for (#045 D).
- [ ] Delegate tests for each endpoint's 200 / 400 / 404.
- [ ] `mvn install` with the coverage gate, per the `test-coverage` skill.

**Step 10 — `GameSimulatorIntegrationTest` and the harness**
- [ ] The integration test's own event-counting helpers (`pointsFromEntity` and the
      per-identity tests) now duplicate the summarizer. Keep the per-creditor and
      per-identity assertions as an **independent** check where they add one; drop pure
      duplicates.
- [ ] The harness's four reconciliation lines stay (they aggregate over thousands of
      games); they must still read `OK`.

**Step 11 — docs and beads**
- [ ] [game.md](game.md) `BoxScore`: built from the event log at game end, the guard, the
      three non-derived fields, `team_id` (delete "no `team_id`; derive it from
      `player_team`"), `started`. `Game`: possessions, `played_at`. API table: the five
      endpoints and the `GameResult` team lines. Reference-doc rules: no `#NNN`, no history.
- [ ] [game-events.md](game-events.md) *Reconciliation identities*: now enforced at every
      write; point to game.md rather than restating.
- [ ] [risks.md](risks.md) "Stats are written twice": rewrite to what is left (the counters
      are still a second write path, but a disagreement can no longer persist) or delete it.
- [ ] [game-tables.puml](game-tables.puml): drop the "planned" styling and the red
      read-path line; render and look at the PNG.
- [ ] [roadmap.md](roadmap.md): flip 4.1–4.3 to `[x]` with a landing note of a few lines
      citing #045; the header's "Next" moves to the Phase 3 revisit.
- [ ] Edit any #045 letter the build diverged from. Close `gametime-fwy` citing #045 B,
      after `grep -rn gametime-fwy docs/ CLAUDE.md`.

### The derivation table (#045 A)

Every column comes from the game's events, **by player**. This table is the summarizer's
spec and its test list.

| `box_score` column | Derived from |
|---|---|
| `points` | 2 per `SHOT` `MADE_2PT_*`, 3 per `SHOT` `MADE_3PT` (primary), + 1 per `FREE_THROW` `MADE_*` (primary) |
| `field_goals_attempted` | every `SHOT` (primary). `BLOCKED_*` included: every `SHOT` row is an attempt |
| `field_goals_made` | `SHOT` `MADE_*` |
| `three_pointers_attempted` / `_made` | `SHOT` ending `_3PT` (made, missed, blocked) / `MADE_3PT` |
| `free_throws_attempted` / `_made` | `FREE_THROW` (primary) / `MADE_*` |
| `offensive_rebounds` / `defensive_rebounds` | `REBOUND` `OFFENSIVE` / `DEFENSIVE` (primary). `OUT_OF_BOUNDS_*` has no primary and credits no one |
| `assists` | `SHOT` with `assist_player_id` = player |
| `steals` | `TURNOVER` `STOLEN` with `opponent_player_id` = player |
| `blocks` | `SHOT` `BLOCKED_*` with `opponent_player_id` = player |
| `turnovers` | every `TURNOVER` (primary), all nine causes, the charge included |
| `fouls` | every `FOUL` (primary) **except** `TECHNICAL_FOUL`. The charge's `FOUL` row counts; a flagrant is one row (it replaces the underlying foul's event) |
| `technical_fouls` | `FOUL` `TECHNICAL_FOUL` (primary) |
| `minutes` | **not derived**: the possession-share projection, unchanged |
| `started` | **not derived**: `RotationState`'s opening five (#045 E) |
| `team_id` | **not derived**: the player's squad (#045 D) |

### Do NOT

- **Aggregate stats from `game_event` at read time** (#045 C). The summarizer runs once,
  at game end.
- **Remove the `record*()` counters or their calls.** Foul-outs, foul trouble and ejections
  read them mid-game; they are now the guard's second side (#045 B).
- **Store `player_team_hist_id` alongside `team_id`** (#045 D).
- **Read `create_date` for anything** (#045 G).
- **Put the leader thresholds in `SimConfig`** (#045 J): the 63/29 counts must not move.
- **Add a season table, a season parameter, or team/season summary tables** (#045 C, I).
- **Backfill old games** (#045 L).
- **Add an RNG draw or change any engine behavior.** A same-seed diff that moves is a bug in
  this phase.
- **Add events** (check-ins, jump ball, substitutions): that is `gametime-3sc`, after this
  phase (#045 M).

### Open at execution (builder's call, no design decision needed)

- Class and package names (`stats`?), JPQL vs native SQL, the response schema names.
- How a player with no games answers `/stats`: recommended 200 with `gamesPlayed: 0` and
  null averages/percentages, rather than 404 (the player exists).
- Whether `StatsService` stays one class or splits (player / team / league).

### Verified facts (design pass, against code)

- `GameSimulator.simulate()` copies `PlayerGameState` counters into `box_score`; nothing in
  production reads `game_event` except the play-by-play endpoint. The score is a third
  tally (`GameData.addScore()`).
- `GametimeServiceImp.homePlayerIds()` buckets home/away by the **current** roster.
- `player_team_hist` is written only by `assign()` / `removePlayerFromTeam()`; the seed
  roster (`release.1.0.3.roster.dataload.yml`) writes `player_team` only.
- `PossessionEngine` gives each team exactly `pacedPossessions` per regulation period
  (`homeOnOffense = poss % 2 == 0`, `poss` restarts per period); `pacedPossessions` depends
  on both coaches' `paceMultiplier()`. The count is not persisted.
- `RotationState` puts `squad.subList(0, 5)` on the floor and marks them `tookFloor`;
  `PlayerGameState.isStarter()` reads `lineupRole == STARTER`.
- The audit trigger sets `create_date := NOW()` (Postgres: transaction start); H2 uses the
  column default.
- A flagrant emits **one** `FOUL` row in place of the underlying foul's
  (`awardFlagrant`), after a single `recordFoul()`.

### Known effects the stats will surface

Not gates; the stats are faithful to the engine. Per-player aggregates will make these
visible, and may be the instrument that diagnoses them:

- **Foul concentration**: the same defenders are drawn too often, so fouls and foul-outs
  pile up on a few players → `gametime-vp6`. The `FOULS` leaderboard is its instrument.
- **Turnover floor**: good ball-handlers are floored at a 2% turnover chance, compressing the
  low end of a turnover leaderboard → [calibration.md](calibration.md) *Turnover floor*,
  `gametime-g1g`.
- **Per-player realism**: [risks.md](risks.md) "Skill formula balance" and "Seed data
  realism" wait on per-player season lines; `gametime-rdo`, `gametime-9up`.

---

## Where deferred work lives (not here)

Pointers only; the reasoning lives at the destination.

- **Phase structure and seams** → [roadmap.md](roadmap.md), including the Phase 3 revisit
  between Phase 4 and Phase 5
- **Event-log work this pass uncovered** → `gametime-3sc` (check-ins, jump ball,
  substitutions), `gametime-qs7` (plus/minus, blocked by it)
- **Historical summarization, retention and season-close rollups** → `gametime-1b1`
- **Seed roster history gap** → `gametime-6x9`
- **PER** → `gametime-bnp`
- **Calibration targets, residuals and the rules for moving them** →
  [calibration.md](calibration.md), the source of truth
- **Engine traps and measured elasticities** → [engine-traps.md](engine-traps.md)
- **Open seams of a shipped engine sub-phase** → that entry's letters in
  [decisions.md](decisions.md): technicals **#032**, foul trouble and defender
  over-dispersion **#031**
- **Chores and parked ideas** → beads (`bd ready`; `bd list --status deferred`)
- **Live risks** → [risks.md](risks.md). Phase 5 must read "Skill sensitivity is ~10× too
  steep" first.

**Standing facts (true every phase):**

- `CalibrationHarness` is disabled by default; run it with `-Dcalibration=true
  -DcalibrationSeed=NNNN` and judge the 5-seed mean. **Re-run after any `SimConfig`
  change.** The sim profile comes from the environment, `SPRING_PROFILES_ACTIVE=local,baseline`;
  the `-D` form does not reach the forked Surefire JVM. `baseline` must always be in the list
  (it carries all 63 tunables), and **order is positional**: putting it last lets it win and
  silently neutralizes an era profile. The report's effective-config dump catches that.
- **Do not touch `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`'s baseline value** without its
  own recalibration pass (#034 B).
