# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Deferred work lives in the
Backlog at the bottom.

Current focus: **Phase 3.2 — Possession Engine** (roadmap.md §3.2). The §3.1
Game Model is shipped (see roadmap.md §3.1, decisions.md #020); §3.2 is the first
code that *fills* those models.

---

## Phase 3.2 — Possession Engine

**Goal**: A working possession-by-possession simulation that, given two teams
and their lineups, plays a full game and **produces the §3.1 models** — an
ordered list of `GameEvent`s and a `BoxScore` per player, with a final `Game`
score. This is the core of Phase 3; §3.3–§3.5 layer onto the loop it builds.

The roadmap §3.2 deliverables:
- Possession flow: inbound → set play / fast break → shot clock → outcome
- Shot selection: which player shoots, what shot type (drive, perimeter, post, longRange)
- Shot outcome: probability from shooter skills vs. defender skills
- Turnover probability: `ballSecurity` vs. defender `individualDefense`
- Foul model: drive/post attempts → foul probability → `freeThrows` skill

### ⚠️ Scope discipline — what §3.2 is and is NOT

§3.2 builds the **possession loop and shot/turnover/foul resolution**. It is
deliberately *narrower* than "a realistic NBA sim." Defer these to their own
roadmap sections so the loop lands clean and each layer is testable on its own:

- **§3.3 Rebounding** — for §3.2, a **missed shot ends the possession** (ball goes
  to the other team). Do NOT model offensive rebounds / second-chance points yet.
  Leave a clear seam (a "shot missed" outcome the rebounding layer will hook).
- **§3.4 Coaching & chemistry effects** — §3.2 uses the players' **raw skills
  only** and a flat baseline pace. Do NOT read `Coach` attributes
  (`pace`/`offensiveScheme`/etc.) or `teamOffense`/`teamDefense`/`acumen` team
  effects yet. Build the math so a coach modifier is a *multiplier you can slot
  in later* (coach.md's `basePace × f(pace)` shape), but pass `1.0` for now.
- **§3.5 Minutes / fatigue / substitution** — §3.2 plays the **5 starters the
  whole game** (no subs, no energy decay). Do NOT read `rotationOrder`,
  `endurance`, or fire substitutions. Minutes in the box score can be a flat
  split or left at 0 until §3.5.

If you find yourself reading a coach attribute, decaying energy, or awarding an
offensive rebound — stop; that's a later section. The discipline (cf. #014/#017):
ship the smallest correct loop, then layer.

### What §3.2 must touch (and what it must not)

- **New code lives in a new package**: `software.daveturner.gametime.sim` (engine,
  possession loop, shot/foul/turnover resolvers, RNG). This is hand-written
  `gametime-app` code — there is no existing engine to copy, so this section
  *establishes* the pattern. Keep it a plain Spring `@Component`/`@Service` graph,
  constructor-injected, like `GametimeServiceImp` + the `*SkillCalculator` beans.
- **Reads** (already exposed — do not re-model): player skills via
  `SkillMapper.mapSkills(player)` → `PlayerSkills` (23 `BigDecimal` skills on the
  1–20/avg-10 scale), the team roster + lineup via the existing `Team` model
  (`RosterEntry` carries `lineupRole`/`rotationOrder`), and `Coach` attributes are
  *available* on `Team.coach` but **not consumed yet** (see §3.4 above).
- **Writes** the §3.1 entities: builds `GameEntity` + ordered `GameEventEntity`s +
  `BoxScoreEntity`s and persists them via the existing `GameRepo` /
  `GameEventRepo` / `BoxScoreRepo`. Box score is **accumulated during the loop**,
  events are the source of truth (decisions.md #020).
- **No OpenAPI / endpoint work** — the `POST /v1/game/simulate` endpoint is
  **§3.6**, not §3.2. §3.2 exposes the engine as an internal service method
  (e.g. `GameSimulator.simulate(homeTeamId, awayTeamId)`), unit-tested directly.
  Wiring it to a delegate is §3.6. (Same defer-the-API discipline as §3.1.)

---

## 🚧 Decisions to make BEFORE writing the engine

These are blocking and shape the whole engine. Resolve each, then record the
outcome in [decisions.md](decisions.md) as the next entry (**#021**) and reflect
the possession-flow choices in [game.md](game.md) (its `GameEvent` section says
the shape "grows additively with the §3.2 engine" — fill that in). Use the
decisions.md template at the bottom of that file. Roadmap "Design Decisions To
Make" #1, #2, #7 map directly onto A/B below.

### Decision A — Determinism / RNG ✅ RESOLVED (decisions.md #021)

**Settled: a seeded `RandomGenerator`, with the seed passed as a per-`simulate()`
parameter.** Do not re-open — build to this:

- `simulate(...)` takes a **`long seed`** argument (the seed belongs to the call,
  because a *game* is the unit of randomness — not a bean, not config). The method
  builds a `RandomGenerator` from that seed and threads it through the loop and all
  resolvers. Same seed + same teams ⇒ byte-identical game.
- **No properties-file entry.** The seed is NOT read from
  `application-local.properties` / `application-test.properties` — a single global
  value can't let each test choose its own seed to drive specific branches. It's a
  method parameter so every test pins its own value.
- **Tests** pass a fixed seed → reproducible → assertable (this is what makes the
  branchy `sim` package coverable against the per-package gate). **Production /
  §3.6** passes a *fresh* seed each call (e.g. `System.nanoTime()` or a random
  `long`) so real games vary — a fixed production seed would make every game
  identical.
- **Do NOT** call `Math.random()` or `new Random()` un-seeded anywhere — untestable
  and un-seedable.
- Seed is **not persisted** in §3.2 (events are already persisted — #020 — so
  replay needs no seed). If §3.6 ever wants seed-based replay it can add a column
  then; don't add one now (#014/#017 — no plumbing ahead of a consumer).

### Decision B — Possession granularity & clock model ✅ RESOLVED (decisions.md #021)

**Settled: abstract possession count, configurable; event time derived for display
and NOT persisted in §3.2.** Build to this:

- **Granularity**: possession-by-possession with an **abstract possession count**,
  not a ticking second-clock. A game is a target number of possessions per team,
  **configurable** (the calibration knob — tune it until simulated box scores match
  real games; roadmap §3.4 anticipates this tuning). No shot-clock seconds, no
  real-time decrement. `period` + game-wide `sequence` order events; possession
  count bounds the game.
- **Pace is an input to `simulate()`**, alongside the seed (#021). Two games with
  the same seed but different pace are simply two different (each reproducible)
  games — tuning pace doesn't break seed-replay, it *defines a different game*.
- **Event time is derived, not stored — in §3.2.** Each event's elapsed time/clock
  is a pure function of that game's pace + `period` + `sequence`, so it can be
  **computed on read** (e.g. by §3.6 play-by-play) for display. §3.2 adds **no time
  column** — consistent with #020's "no in-game clock column until a consumer needs
  one."
- **DEFERRED to §3.6 (not backlog — it has a home):** persisting a per-event time
  **as a stored column**, and the **single-value-vs-range** shape, are deferred to
  §3.6 where the play-by-play *display* is the consumer that reads it. When it
  lands it's one small migration, populated by the engine, with the right column
  name(s) — no half-decided columns now. Note: a persisted game is **frozen
  history**, so retuning pace later only affects *future* games, never a stored one
  — stored time (when it lands) carries no staleness risk.
- **Periods / overtime**: decide how possessions map to the 4 periods (e.g. N
  possessions/period) and OT handling — recommended: if tied after regulation,
  play fixed-size OT possession blocks until untied (`Game.periods` already
  supports >4). *(This sub-choice is fine to finalize during implementation; it
  doesn't change the schema.)*

### Decision C — Where do shot probabilities come from (calibration)?

**Question**: The roadmap says "probability based on shooter skills vs defender
skills." What's the concrete formula family, and what baseline makes league
averages realistic?

- **Recommended**: a **logistic/contest model** over the deviation-from-10 the
  skill system already uses — e.g. `P(make) = base(shotType) + k * (shooterSkill −
  defenderContestSkill)/10`, clamped to a sane range. Reuse the *spirit* of
  `SkillCalculator`'s deviation helpers (avg-10, soft clamp) so the engine and
  skill layer speak the same scale (the #018 "one scale, no translation" win).
- **You do not need real NBA accuracy in §3.2** — you need *plausible, bounded,
  testable* outputs (an average shooter vs. average defender lands near a sensible
  base FG%; a 20-skill shooter beats a 1-skill defender most of the time).
  Roadmap §3.4 explicitly defers **rebalancing the formulas** "once possessions
  exercise them," so pick reasonable constants now and leave a TODO to tune.
- Map the skills to shot types: `drive`/`finishing` (rim), `perimeter` (mid),
  `longRange` (3pt), `post` (inside); defender side: `individualDefense`,
  `shotContest`, `rimProtection`. `ballSecurity` vs `stealing`/`individualDefense`
  for turnovers; `foulDrawing` vs `foulProne` + `freeThrows` for the foul/FT path.

### Decision D — Shot-selection model (who shoots, what shot)

**Question**: How is the shooter and shot type chosen each possession?

- **Recommended**: a **skill-weighted random pick** among the 5 on-floor
  offensive players — players with higher offensive skills get the ball more, and
  each player's shot-type distribution follows their relative drive/perimeter/
  post/longRange skills. Keep it simple and weighted; `acumen`-driven
  shot-*quality* and `passing`-driven assist/ball-movement are **§3.4**, not §3.2
  (use raw weighting now, leave the seam).
- Assists: §3.2 can leave `assists` at 0 (ball-movement is §3.4) **or** record a
  trivial assist on made shots — recommend leaving assists for §3.4 to avoid
  fabricating a model; document whichever you choose.

### Decision E — Engine output contract & transaction boundary

**Question**: What does `simulate(...)` return, and what does it persist?

- **Recommended**: a single `@Transactional` service method that builds and saves
  the `Game` (status `FINAL`), all `GameEvent`s, and all `BoxScore`s atomically,
  returning the persisted `Game` id (or a small result object). Keep persistence
  in the service boundary; keep the *pure* simulation (loop + math) in
  side-effect-free classes that return data, so they're unit-testable without a
  DB. This mirrors `GametimeServiceImp`'s `@Transactional` multi-write methods.

---

## ⚠️ Risks / gaps / things that can bite you

- **Coverage gate is 80% LINE per *package*, enforced at `verify`/`install`
  (NOT `mvn test`).** A new `sim` package full of branchy probability code is the
  single biggest risk this section. See the test-coverage skill. Concretely:
  - A green `mvn test` does **not** prove the gate. Always finish with
    `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install`
    and look for `All coverage checks have been met.`
  - The new package must clear 0.80 **on its own** — aim ~90%. Seeded RNG
    (Decision A) is what makes the branches reachable and assertable. Test each
    resolver (shot/turnover/foul) with fixed seeds and with skill extremes (1 vs
    20) to drive both sides of every probability branch.
  - Keep pure math in small, separately-testable classes; don't bury all logic in
    one giant method that's impossible to cover.
- **Determinism is a correctness requirement, not a nice-to-have** — without it
  the engine can't be tested and the gate can't be met. Do not use
  `Math.random()` / `new Random()` un-seeded anywhere.
- **Numeric types**: `PlayerSkills` exposes skills as `BigDecimal` (1–20, one
  decimal). Convert to `double` at the engine boundary for the math; don't thread
  `BigDecimal` through hot loops. The `SkillCalculator` constants (`SCALE_AVG=10`,
  `SOFT_KNEE`, `clamp`) are the reference for staying on-scale.
- **JDK 21 only** — every Maven command sets
  `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem` (Homebrew JDK 25
  breaks Lombok; CLAUDE.md "Build requirements").
- **Don't read what you're deferring.** It's tempting to "just use the coach pace
  since it's right there on `Team.coach`." Resist — that's §3.4 and pulls
  untested coaching math into §3.2's coverage. Pass a `1.0` modifier seam instead.
- **Persisted events are real rows** (#020): a simulated game writes ~150–200
  `game_event` rows. In tests, simulate against the H2-seeded league (real team
  ids), and either roll back (`@Transactional` test) or use a throwaway game id so
  you don't accumulate state. Don't add a games CSV / seed (games come from sim).
- **No `EntityMapper` change needed** — the engine writes entities directly via
  repos; it does not map to/from API models (that's §3.6). Don't wire game models
  into `gametime.yaml` this section.
- **`sequence` is game-wide and monotonic** (#020) — the loop must increment one
  counter across all periods, not reset per period. Easy to get wrong.

---

## Task sequence (do in order)

> Each task says where the code lives and which existing file models the pattern.
> Run `mvn -f gametime-service/pom.xml clean install` (JDK 21) after the engine
> + tests land — the per-package gate only shows up there. Tick the `[ ]` box as
> each step lands.

- [ ] **1. Resolve the remaining decisions (C–E)** above. **Decisions A and B are
  already resolved** — seeded `RandomGenerator` with seed as a per-`simulate()`
  parameter (A), and abstract configurable possession count with event time
  derived-not-stored in §3.2 (B). Both recorded in decisions.md #021. Finalize C
  (shot-probability formula), D (shot-selection), E (output contract), record them
  (extend #021 or add #022), and fill in [game.md](game.md)'s `GameEvent` section
  with the now-known possession flow (which `play_type`s the loop emits, what
  `outcome` strings mean). *Output: docs updated; no engine code yet.*

- [ ] **2. Scaffold the `sim` package.** Create
  `src/main/java/software/daveturner/gametime/sim/`. Define the engine entry point
  (e.g. `GameSimulator` `@Service`) and the small collaborators (possession loop,
  shot resolver, turnover resolver, foul resolver, an injected
  `RandomGenerator`/seed). Constructor-inject everything (copy the DI style of
  `GametimeServiceImp` and the `*SkillCalculator` beans). Keep the pure
  simulation side-effect-free; persistence lives only in the service-boundary
  method (Decision E).

- [ ] **3. Possession loop (no shooting yet).** Implement the period/possession
  structure (Decision B): alternate possessions between teams, increment the
  game-wide `sequence`, advance `period`. Produce placeholder events to prove the
  loop + ordering, then build outward. Verify a game runs to completion with a
  bounded possession count.

- [ ] **4. Shot selection + outcome** (Decisions C, D). Pick shooter + shot type
  (skill-weighted), resolve make/miss from shooter vs. defender skills via the
  logistic/contest formula. On a make: award points (2 or 3), emit a `SHOT` event,
  update the shooter's box score (FGM/FGA, points; 3PM/3PA for `longRange`). On a
  miss: emit the `SHOT` event with a "missed" outcome and **end the possession**
  (rebounding is §3.3 — leave the seam).

- [ ] **5. Turnovers** (Decision C). Before/at shot time, roll turnover from
  offensive `ballSecurity` vs. defender `individualDefense`/`stealing`. On a
  turnover: emit `TURNOVER`, credit the defender a steal where applicable, end the
  possession.

- [ ] **6. Fouls + free throws** (Decision C). On drive/post attempts, roll a foul
  from `foulDrawing` vs. `foulProne`. On a foul: emit `FOUL`, then resolve free
  throws via the `freeThrows` skill (emit `FREE_THROW` events, update FTA/FTM +
  points + fouls). Keep bonus/and-1 nuance minimal — note any simplification.

- [ ] **7. Assemble + persist** (Decision E). The `@Transactional` `simulate(...)`
  method: run the loop, build the `GameEntity` (status `FINAL`, period + final
  scores), the ordered `GameEventEntity` list, and the per-player
  `BoxScoreEntity`s; save via `GameRepo`/`GameEventRepo`/`BoxScoreRepo`. Confirm
  box-score totals reconcile with the event log (points from `SHOT`/`FREE_THROW`
  events == box score points).

- [ ] **8. Tests** (the gate — see test-coverage skill). Under
  `src/test/.../sim/`:
  - **Pure-math unit tests** with a fixed seed for each resolver (shot, turnover,
    foul): assert outcomes at skill extremes (1 vs 20 drives each branch) and that
    the average-vs-average case lands in a sensible band. This is what carries the
    per-package coverage.
  - **Determinism test**: same seed + same teams ⇒ identical event list + box
    score; different seed ⇒ (very likely) different.
  - **Full-game integration test** (`@SpringBootTest`, H2-seeded league, real team
    ids like `RosterLineupDelegateTest` uses): simulate a game, assert it persists
    a `FINAL` game with events in `sequence` order and box scores that reconcile
    with the score. Roll back or use a throwaway id.
  - Cover edge branches: a possession ending in make / miss / turnover / foul; OT
    tie-break path.

- [ ] **9. Verify the gate.**
  `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install`
  must print `All coverage checks have been met.` and `BUILD SUCCESS`. If the new
  `sim` package is under 0.80, add resolver tests (don't lower the gate, don't
  write token tests — exercise real branches with seeds). Optionally
  `mvn verify -Ptest` for the Docker/Postgres path.

- [ ] **10. Docs cleanup.** Check the §3.2 boxes in [roadmap.md](roadmap.md) and
  add a one-line "Shipped" note under §3.2 (mirror the §3.1 note style). Make sure
  [game.md](game.md) `GameEvent` section reflects the real emitted `play_type`s /
  `outcome` vocabulary. Reset this "Current focus" line to **§3.3 — Rebounding**
  and move any deferred §3.2 scraps (formula-tuning TODOs, etc.) to the Backlog.
  **Carry forward the §3.6 follow-up** (Decision B): a stored per-event time
  column (single value vs. range — undecided) lands with §3.6 play-by-play, its
  consumer. Until §3.6's todo exists, note it on the §3.6 roadmap line or
  decisions.md #021 so it isn't lost.

---

## Patterns to follow (quick reference)

- **Spring DI / service boundary**: `service/GametimeServiceImp.java` —
  constructor injection, `@Service`, `@Transactional` multi-write methods.
- **Bean-per-unit math**: the 23 `mapper/*SkillCalculator.java` + `SkillMapper` —
  small `@Component`s composed by an orchestrator; the model the `sim` resolvers
  should imitate.
- **On-scale math helpers**: `mapper/SkillCalculator.java` (`SCALE_AVG=10`,
  `SOFT_KNEE`, `clamp`, deviation `adj`) — reference for keeping engine math on
  the 1–20/avg-10 scale.
- **Reading skills**: `mapper/SkillMapper.mapSkills(player)` → `PlayerSkills`
  (`BigDecimal` per skill). Reading lineup: `Team.getPlayers()` → `RosterEntry`
  (`lineupRole`, `rotationOrder`).
- **Writing §3.1 entities**: `entity/GameEntity`, `GameEventEntity`,
  `BoxScoreEntity` + `repo/GameRepo`, `GameEventRepo`, `BoxScoreRepo` (shipped in
  §3.1).
- **Integration test style**: `api/RosterLineupDelegateTest.java` —
  `@SpringBootTest` over the H2-seeded league, deriving real ids at runtime.
- *(Not needed in §3.2: OpenAPI `gametime.yaml`, `EntityMapper`, delegate impl —
  those are §3.6.)*

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
