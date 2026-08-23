# Gametime

Basketball simulation game — API service, future React frontend.

## Project structure

```
gametime/
├── CLAUDE.md                  AI session entry point (this file)
├── docs/                      Project documentation
│   ├── roadmap.md             Phased roadmap (Phases 3–8; 1–2 shipped, see "What Exists Today")
│   ├── decisions.md           Architecture decision log
│   ├── risks.md               Active risks and concerns
│   ├── todo.md                Tactical task list (current phase only — CURRENTLY:
│   │                          §3.20 RECALIBRATION, needs a DESIGN PASS)
│   ├── backlog.md             Homeless infra/tooling chores (cross-phase)
│   ├── ideas.md               Parking lot — untriaged future-improvement ideas
│   ├── calibration.md         Calibration targets — THE source of truth for them
│   ├── player.md              Player domain design (attributes, skills, formulas)
│   ├── roster.md              Roster & lineup domain (player↔team, lineups)
│   ├── coach.md               Coach domain design (5 decision attributes)
│   ├── game.md                Game domain + possession engine (models + flow)
│   ├── game-events.md         The event vocabulary — every (play_type, outcome) the
│   │                          engine emits, with who is on each row (game.md keeps
│   │                          the models + flow)
│   └── possession-flow.puml   Possession-flow diagram (kept in sync with the engine)
│                              (.png gitignored — render with `plantuml
│                              -DPLANTUML_LIMIT_SIZE=16384 -tpng`; plain -tpng
│                              silently truncates at 4096px — see below)
├── gametime-service/          Multi-module Maven project (Spring Boot 3.5.14)
│   ├── pom.xml                Parent POM (packaging=pom)
│   ├── gametime-api/          OpenAPI codegen module (generates server stubs)
│   │   ├── yml/gametime.yaml  API spec (source of truth)
│   │   └── pom.xml            openapi-generator-maven-plugin
│   ├── gametime-app/          Spring Boot application (hand-written code)
│   │   └── src/
│   ├── docker-compose.yml     Local Postgres container
│   └── http/                  IntelliJ .http files for manual REST testing
```

## Project docs

Before starting work, review these for context:
- **`docs/roadmap.md`** — phased roadmap, what's built vs what's needed
- **`docs/roster.md`** — roster & lineup domain: player↔team link, lineups, transactions
- **`docs/player.md`** — player domain reference: attributes, derived skills, calculator design
- **`docs/coach.md`** — coach domain design: 5 continuous decision attributes + engine interface
- **`docs/game.md`** — game domain + the possession engine: Game/GameEvent/BoxScore
  models, the possession flow the engine actually runs, and the API surface
- **`docs/game-events.md`** — **the event vocabulary, and the single per-event
  reference.** Every `(play_type, outcome)` pair the engine emits, in ONE master table
  with explicit participant columns (`primary` · `opponent` · `assist` ·
  `committing_team`), plus the three rules that let you derive most of it (the emission
  rule, the counterparty invariant, and free-text `outcome` vs. the closed `play_type`
  enum). ⚠ **A new outcome, or a change to who is on a row, must land here in the same
  change** — and **do not start a second per-event table anywhere.**

- **`docs/possession-flow.puml`** — **the possession flow as a diagram, and the
  fastest way to understand the engine.** Read it before changing anything in the
  `sim` package: every branch in order, with inline notes citing the decision behind
  each fork. ⚠ **Branch ORDER within a partition is often load-bearing** — this is the
  place that records why. **A new branch or event must be reflected here in the same
  change** — it is a living spec, not an illustration.
  ⚠ **ADDING A BRANCH CORRECTLY CAN STILL BREAK THE BOXES ALREADY THERE.** §3.14b and
  §3.16 each inserted a fork above an existing step, and `SHOOTING_FOUL` silently became
  the *fall-through* while still being drawn as the entry step — the picture claimed one
  foul emits three events; the engine emits one. **Each change was locally right and the
  whole drifted.** **The tell, and it generalizes: when a node's NOTES are busy
  explaining that the boxes do not mean what they appear to mean, the boxes are wrong.**
  After adding a fork, re-read the branch cold and ask "does this still say ONE event
  comes out?"
  Validate with `plantuml -checkonly docs/possession-flow.puml`; render with
  **`plantuml -DPLANTUML_LIMIT_SIZE=16384 -tpng`** — ⚠ **the size flag is REQUIRED**: a
  plain `-tpng` **silently truncates** at 4096px. `-checkonly` does not lay out, so a
  green check does **not** prove the PNG is complete; confirm the height after rendering.
  ⚠ **It is 53k and 45% inline notes** — a thinning chore is filed in `backlog.md`,
  paired with `game.md` (the two describe the same flow twice).

- **`docs/calibration.md`** — **the calibration targets, and the single source of
  truth for them.** What the simulation is tuned toward (points, FG%, 3P%, assists,
  turnovers), the minutes targets, the soft foul-out target, the per-phase rates, and
  the plausibility ballparks that are deliberately *not* targets. Read it before
  changing any `SimConfig` constant or claiming a landing is "on target" — and update
  it (plus the `CalibrationHarness` `(target ~N)` strings) in the same change whenever
  a target moves.
  **Most targets were sourced against real league averages in 2026-08**; the file names
  the season and source per row. A few (foul-outs, technicals, flagrants, the minutes
  distribution) are still unsourced and say so. It supersedes the original target
  agreement in `decisions.md`, which is historical on the target question.
- **`docs/decisions.md`** — past architecture choices (check before proposing alternatives)
- **`docs/todo.md`** — current-phase task list (deferred work lives in backlog.md + roadmap.md)
- **`docs/backlog.md`** — cross-phase infra/tooling chores with no phase home
- **`docs/ideas.md`** — parking lot: untriaged future-improvement ideas (not planned work)
- **`docs/risks.md`** — known risks and concerns

When writing or editing any of these planning docs (a `#NNN` decision, a design
pass, an execute-ready plan, moving deferred work, parking an idea), invoke the
**`project-docs`** skill first — it captures the house format, the cross-file
routing rules, and the design-pass→decision→plan rhythm the docs follow.

⚠ **THESE DOCS BLOAT, AND THE SKILL NOW CARRIES THREE CONVENTIONS THAT SAY HOW TO STOP
IT** *(all three established 2026-08 by user call, after measuring; `decisions.md` is
already under a condense gate)*. Follow them rather than re-deriving:
- **`calibration.md` is a REFERENCE, not a history** — keep the **operative rules**
  (*judge at N seeds*, *don't back-solve X*, *this knob is priced by Y*, *this row is
  green because a clamp holds it*); the narrative of which pass argued what belongs in
  its `#NNN`. *(35k → 13k; it had 242 `#NNN`/§X.Y citations and headers like "✅ HISTORY".)*
- **In `backlog.md`, a completed chore is REMOVED, not checked off** — it has a phase home
  by definition. Confirm the record lives in a `#NNN` or roadmap landing first, then grep
  for inbound references to the deleted text. *(65k → 47k; 30% was done work.)*
- **One vocabulary, one place.** The per-event vocabulary lives in `game-events.md`
  alone; a second table anywhere WILL drift. *(`game.md` 67.6k → 45.6k.)*
⚠ **The distinction that decides whether a citation is bloat:** in `calibration.md` the
`#NNN`s marked *history* sitting in a *targets* reference — cut. In `game.md` they
annotate *live mechanics* and are how a reader finds the argument — **keep**; that file's
problem is duplication against `possession-flow.puml`, not citation. **A post-§3.19 /
pre-Phase-4 documentation pass owns that one** (roadmap.md's Phase 4 pre-work).

When adding or changing production code under `gametime-app/src/main/java`,
invoke the **`test-coverage`** skill — the JaCoCo gate is per-package and runs at
`install`, not `test`, so a green `mvn test` does not prove it passes.

✅ **§3.19 closed the full-suite-vs-isolation gap** — `V1ApiDelegateimplTest` used to
commit roster rows, shifting the sim's RNG consumption downstream, so sim tests could
pass in the full suite and fail alone. It is `@Transactional` now. **Still cheap and
still worth doing** after any change touching `PossessionEngine`/`ShotSelector`/
`RotationState` — run the sim classes alone:
```
JAVA_HOME=... mvn -pl gametime-app -f gametime-service/pom.xml test -Dtest='GameSimulatorIntegrationTest' -DfailIfNoTests=false
```

### How a phase moves (read this before starting work)

Engine sub-phases run in **three separate sessions**, and knowing which one you're
in matters more than anything else in these docs:

1. **Design pass** — resolve the open questions in todo.md into a new numbered
   `decisions.md #NNN` (Decisions A, B, C…) **plus** an execute-ready plan in
   todo.md. **Write no production code in this session.**
2. **Execution** — build exactly that plan; add an implementation note to `#NNN`
   recording any divergence; flip the roadmap bullet to `[x]`.
3. The next phase's design pass starts the cycle again, and todo.md is rewritten.

**`docs/todo.md` always states the current phase and which session it needs** —
its header callout says either "needs a DESIGN PASS first" or "execute-ready,
design resolved as #NNN". Start there. Don't execute a roadmap bullet as if it
were a plan: the bullets are seams, deliberately under-specified, and every phase
so far has found real design questions the one-liner hid.

### ⚠ Three traps that bite EVERY session — read these before trusting a doc

**1. SUB-PHASE NUMBERS HAVE BEEN REUSED — A NUMBER IN AN OLDER DOC MAY NAME A
DIFFERENT PHASE.** Recalibration has been renumbered **four times**: §3.16 → §3.18 →
§3.19 → **§3.20**. Both vacated slots were reassigned — **§3.16** to shooting-foul
composition (shipped) and **§3.19** to instrumentation. So in anything written before
2026-08, **"§3.16" and "§3.19" both mean RECALIBRATION**, and a literal reading sends you
to a different phase entirely. Stale references remain throughout `decisions.md`,
`roadmap.md`, `backlog.md` and `ideas.md` — **left deliberately**, because most sit in
`#NNN` entries, which are history and must never be retro-edited. **Annotate what you
touch; do not mass-rewrite.**
**Rule: read the phase NAME, never the number alone.** roadmap.md carries the mapping.

**2. A NUMBER CAN MOVE — OR FAIL TO MOVE — FOR REASONS THAT ARE NOT THE ENGINE, and no
test will catch any of them.** `CalibrationHarness` infers meaning from what an event
*awards*, and a `SimConfig` constant is not always what sets a rate. **No test asserts an
instrument's meaning**, so re-read calibration.md row by row against a fresh harness run
whenever a phase changes what an event awards or what the shot mix is. Three shapes, all
found the hard way:

- **The instrument broke.** A harness row classified a stopped shot by counting **3 free
  throws**; a later phase added a foul awarding 0 or 2 but never 3, so the row silently
  under-counted by ~2× while the engine stayed correct.
- **⚠ FIXING an instrument also moves numbers, and looks like a regression.** After that
  fix, `Stopped shots / team` read **12.43** against a pre-fix **7.65** and appeared to
  have doubled. **It had fallen** — 7.39 → 6.24 like-for-like on the raw tally.
  **Compare raw-to-raw across an instrument change; never compare a corrected number to
  an uncorrected one.**
- **⚠ A CLAMP CAN DO WHAT A CONSTANT APPEARS TO DO, and the tell is a number that
  DOESN'T move.** §3.17 predicted blocks would fall 4.8 → ~2.6 as the mix went
  three-heavy; they stayed at **4.80**. `PROB_FLOOR` (0.02) is **4×
  `sim.base-block-three` (0.005)**, so a three's block chance is **floored, not based**
  — the constant is **inert**, and `PROB_FLOOR` applies to *every* probability.
  ⚠ **How that one was CLOSED is the reusable lesson: it was SIZED.** The effect is
  **~half a blocked three per team-game**, on a row already on target, with no consumer
  for the per-type split. It had been pattern-matched to §3.17's FG% finding — *a green
  total hiding a wrong composition* — but that gap was **6 points on a sourced target**
  and this one is half a block: same SHAPE, two orders of magnitude less consequence.
  **SIZE A FINDING BEFORE PROMOTING IT.**

**The question to ask of ANY moved number, before tuning it: "did the engine change, did
the measurement change, or is a clamp holding it?"**

⚠ **AND THE CHEAPEST WAY TO PROVE A PASS MOVED NOTHING** — use it whenever a change
claims to be non-behavioral: run the harness, `git stash` the tree, run it again on the
same seed, and diff. **Identical line for line, or the claim is false.** ⚠ Note a plan's
figures are usually **5-SEED MEANS**, while a 1-seed run prints different numbers —
**before/after on the SAME seed is the check that means something.**

**3. THE HARNESS NEEDS ITS PROFILE IN THE ENVIRONMENT — `-Dspring.profiles.active` DOES
NOT REACH THE FORKED SUREFIRE JVM.** The context comes up with `activeProfiles = []`,
tries to reach Postgres, and fails — with an error that looks like a database problem
rather than a profile one. Use `SPRING_PROFILES_ACTIVE=local,baseline`, run from
`gametime-service/`, and **confirm the report's `Profiles:` line before trusting any
number**. This has cost two sessions so far.

## Build requirements

- **Java 21** (LTS) via SDKMAN: `~/.sdkman/candidates/java/21.0.9-tem`
- **Homebrew Maven uses JDK 25 by default** which breaks Lombok. Always set JAVA_HOME:
  ```
  JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean test
  ```
- Do not use JDK 25 (Homebrew default) — Lombok 1.18.x is incompatible.

## Build commands

```bash
# Compile
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean compile

# Run tests (uses H2 in-memory, no Docker needed)
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean test

# Run with Docker integration tests
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn verify -Ptest
```

## Module boundaries

- **gametime-api**: Generated code only. Never put hand-written code here. API spec changes go in `gametime-api/yml/gametime.yaml`. Generated stubs land in `target/`.
- **gametime-app**: All hand-written code — entities, services, repos, delegate implementations, tests, resources.
- The app module depends on the api module as a Maven dependency.

## Database

- **Local dev**: Postgres in Docker container (`docker-compose.yml`), port 5432
- **Tests (`mvn test`)**: H2 in-memory, no Docker needed. Test properties override in `src/test/resources/application-local.properties`.
- **Schema**: All app tables live in the `gametime` schema (not `public`).
- **Liquibase**: Manages schema creation and migrations. Changelog at `src/main/resources/db/changelog.yml`.
- Postgres-specific features (triggers, plpgsql functions) are gated with `dbms:postgresql` in Liquibase changesets.
- ⚠ **NEVER edit a changeset that has already run — APPEND a new one.** Liquibase fixes a
  checksum on first run and will refuse to start against a changed one. §3.6 (`1.04.2`),
  §3.10 (`1.04.3`), §3.14a (`1.04.4`) and §3.18 (`1.04.5`) each appended for exactly this
  reason; `1.04.1` is untouchable. **Additive nullable column + its FK, no `dbms` gate**
  is the established shape — see `release.1.0.4.game.sql` for the house comment style.
- ⚠ **The H2 test DB runs the SAME changelog as Postgres**
  (`spring.liquibase.change-log` in `src/test/resources/application-local.properties`), so
  **a malformed changeset fails the whole test suite immediately** — no Docker needed to
  verify one.
- Audit columns (`create_user`, `create_date`, `update_user`, `update_date`) have defaults for H2 compatibility; Postgres triggers override them.

## Local dev setup

```bash
cd gametime-service
docker compose up -d                    # Start Postgres
# If fresh container needed:
docker compose down -v && docker compose up -d

# Run from IDE or:
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn spring-boot:run -pl gametime-app
```

App runs on port 8080. Swagger UI at http://localhost:8080/swagger-ui.html

## Git — never commit without the user's say-so

**Do not run `git commit` (or `git push`) unless the user explicitly asks for it
in that message.** This applies to every kind of change — engine code, tests,
planning docs, skills — and to work that is finished, verified, and green. A
passing build is not permission to commit.

The workflow is: make the edits, summarize what changed, then **stop and wait**.
The user reviews first and asks for the commit when they're ready. "Finish the
task" / "close it out" / "ship it" do **not** imply a commit; if it's ambiguous,
ask rather than assume. Completing a phase's close-out means the *files* are
updated, not that history is written.

Corollary: don't batch up "I'll commit it since I'm here" side commits, and don't
push a branch just because it's ahead of origin.

## Key conventions

- `SimConfig`'s declaration form says whether a constant is tunable: `public static
  final` is a rule of basketball or the shape of the model; `private final` +
  accessor is a tunable knob, bound by `@ConfigurationProperties(prefix = "sim")`.
- Tunable sim constants have **no initializers in Java** — their values live only in
  `application-baseline.properties`. Never give one a default or a `public static
  final` alias; both silently create a second value, since javac inlines constants
  into each caller. Outside a Spring context, use `SimConfig.baseline()`.
- Sim profiles are ordinary Spring profiles composed with the infra ones
  (`spring.profiles.active=local,baseline`). Era profiles are deltas over `baseline`,
  so `baseline` must stay in the list and profile order is positional. ⚠ **For the
  harness the profile must come from the ENVIRONMENT (`SPRING_PROFILES_ACTIVE`), not
  `-D`** — see trap 3 above.
- `SimConfig`'s tunable count lives in **FOUR** places that all move together: the header
  comment, `baseline()`'s javadoc, its error message, and
  `SimConfigProfileBindingTest.EXPECTED_TUNABLE` (which asserts **both** constructor
  arity and instance-field count, so a missed accessor fails loudly). Currently **62
  tunables / 27 statics**.
- **`game_event` has TWO participant columns and they are different KINDS of fact.**
  `assist_player_id` is the **teammate** who helped; `opponent_player_id` is the
  **counterparty** — the player on the other side of the play, and therefore **ALWAYS on
  the opposite team**, which is what lets a reader resolve their team without decoding
  `outcome`. ⚠ **A teammate never goes in the opponent column**; a migration merging them
  was pursued and **reversed** (#041 D). It is populated **only where a real contest
  identified an individual victim** (the stealer, the blocker, the fouled shooter) and is
  **null by contract** elsewhere — ⚠ **do NOT populate a site just because a player is
  reachable**; a rebounding foul's FT shooter is a weighted *draw*, not the victim. Both
  invariants are enforced by tests in `GameSimulatorIntegrationTest`. Full per-event
  detail: **`docs/game-events.md`**.
- **On every `FOUL` event, `primary_player_id` is the COMMITTER** (charged `recordFoul()`,
  on `committing_team_id`) — the *inverse* of `SHOT`/`TURNOVER`, where primary is the
  victim. Test-enforced.
- OpenAPI delegate pattern: generated `V1ApiDelegate` interface, hand-written `V1ApiDelegateimpl` implements it.
- Entities use Lombok `@Data` for boilerplate reduction.
- Entity `@Table` annotations include `schema = "gametime"`.
- Cucumber tests use JUnit 5 Platform (`@Suite` + `cucumber-junit-platform-engine`), not JUnit 4 vintage.
- Profiles `local,baseline` are active by default. Tests inherit that and override
  `local` to H2 via `src/test/resources/application-local.properties`.
