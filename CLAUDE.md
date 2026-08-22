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
│   ├── todo.md                Tactical task list (current phase only)
│   ├── backlog.md             Homeless infra/tooling chores (cross-phase)
│   ├── ideas.md               Parking lot — untriaged future-improvement ideas
│   ├── calibration.md         Calibration targets — THE source of truth for them
│   ├── player.md              Player domain design (attributes, skills, formulas)
│   ├── roster.md              Roster & lineup domain (player↔team, lineups)
│   ├── coach.md               Coach domain design (5 decision attributes)
│   ├── game.md                Game domain + possession engine (models + flow)
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
  models and the event vocabulary + flow the engine actually runs
- **`docs/possession-flow.puml`** — **the possession flow as a diagram, and the
  fastest way to understand the engine.** Read it before changing anything in the
  `sim` package: it shows every branch in order (turnover → foul → block →
  make/miss → and-1 → rebound-foul → missed-shot outcome), which fork each
  sub-phase added, and carries inline notes citing the decision behind each one.
  ⚠ **Branch ORDER within a partition is often load-bearing** — the diagram is the
  place that records why. Read it there rather than re-deriving it.
  A new branch or event **must** be reflected here in the same change — it is a
  living spec, not an illustration.
  ⚠ **ADDING A BRANCH CORRECTLY CAN STILL BREAK THE DIAGRAM: check what your new fork
  did to the boxes ALREADY THERE.** Found 2026-08 by the user. The pre-shot foul drew
  `FOUL — SHOOTING_FOUL` as a step at the TOP, which was accurate when it was the only
  outcome of that roll. Then §3.14b inserted a flagrant fork above it and §3.16 a
  non-shooting fork below that — each added correctly — and `SHOOTING_FOUL` silently
  became the **fall-through** while still being drawn as the entry step. The picture
  then claimed one foul emits up to three events; the engine emits exactly one. **Each
  change was locally right and the whole drifted.**
  **The tell, and it generalizes: when a node's NOTES are busy explaining that the
  boxes do not mean what they appear to mean** — here "REPLACES the FTs above", "no
  rate re-partitioned", "the two never compose" — **the boxes are wrong.** Prose
  compensating for a diagram is a defect, not a clarification. After adding a fork,
  re-read the branch top-to-bottom as a cold reader and ask "does this still say one
  event comes out?"
  Validate edits with
  `plantuml -checkonly docs/possession-flow.puml`; render a viewable copy with
  **`plantuml -DPLANTUML_LIMIT_SIZE=16384 -tpng docs/possession-flow.puml`**
  (the `.png` is gitignored, so the `.puml` is the artifact that matters).
  ⚠ **The size flag is REQUIRED, not optional**: the diagram is far taller than
  PlantUML's default 4096px ceiling, so a plain `-tpng` **silently truncates it**
  with no warning. `-checkonly` parses without laying out, so **a green checkonly
  does not prove the PNG is complete** — after rendering, confirm the image is
  taller than 4096px whenever you add a partition.
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

When adding or changing production code under `gametime-app/src/main/java`,
invoke the **`test-coverage`** skill — the JaCoCo gate is per-package and runs at
`install`, not `test`, so a green `mvn test` does not prove it passes.

⚠ **AND A GREEN `mvn clean install` DOES NOT PROVE CI WILL PASS EITHER.** Measured
2026-08: §3.17 shipped a branch that failed in GitHub Actions after **two** clean local
full builds. `GameSimulatorIntegrationTest` **fails in isolation and passes in the full
suite on the same seed**, because `V1ApiDelegateimplTest` is not `@Transactional` and
commits roster rows — which changes who is on the floor and therefore the RNG
consumption pattern downstream. **The suite passing was the lucky ordering.**
**After any change that moves the sim's RNG stream** — a new draw, a changed draw
result, anything touching `PossessionEngine`/`ShotSelector`/`RotationState` — **also run
the sim test classes ALONE**, which is the stricter check:
```
JAVA_HOME=... mvn -pl gametime-app -f gametime-service/pom.xml test -Dtest='GameSimulatorIntegrationTest' -DfailIfNoTests=false
```
⚠ Several sim tests pin a seed and assert something that is **probabilistic, not
invariant** (the technicals precondition asserts a ~13% event). Those re-baseline
whenever the stream shifts — measure a new seed, never weaken the assertion. See
backlog.md for the durable fix.

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

**1. SUB-PHASE NUMBERS HAVE BEEN REUSED. `§3.16` IN AN OLDER DOC DOES NOT MEAN
§3.16.** Recalibration was §3.16, then briefly §3.18, and is now **§3.19** (#038).
The §3.16 slot was reassigned to shooting-foul composition, which has shipped. So
**every "§3.16" written before 2026-08 means RECALIBRATION** — including in #030,
#031, #032, #034, #035, and in scattered lines of roadmap.md, backlog.md, ideas.md,
risks.md and game.md. A literal reading sends you three sub-phases too early, to a
phase that already shipped and does something else entirely.
**Rule: read the phase NAME, never the number alone.** roadmap.md carries the
mapping callout. When you find a stale one, annotate it rather than silently
rewriting — the history is worth keeping legible.

**2. A NUMBER CAN MOVE — OR FAIL TO MOVE — FOR REASONS THAT ARE NOT THE ENGINE, and no
test will catch any of them.** `CalibrationHarness` infers meaning from what an event
*awards*, and `SimConfig` constants are not always what sets a rate. **No test asserts an
instrument's meaning**, so re-read calibration.md row by row against a fresh harness run
whenever a phase changes what an event awards or what the shot mix is. Three shapes, all
found the hard way:

- **The instrument broke.** The harness classified a stopped shot as a three by counting
  **3 free throws**; §3.16 added a foul awarding 0 or 2 but never 3, so the row silently
  under-counted by ~2× while the engine stayed correct. ✅ **Fixed by §3.17 Step 0**
  (#040 G) — kept here because it is the cleanest example, not because it is open.
- **⚠ THE FIX TO AN INSTRUMENT ALSO MOVES NUMBERS, AND LOOKS LIKE A REGRESSION.** After
  §3.17, `Stopped shots / team` reads **12.43** against a pre-§3.17 **7.65** and appears
  to have doubled. **It fell** — 7.39 → 6.24 like-for-like on the raw visible tally.
  **Compare raw-to-raw across an instrument change; never compare a corrected number to
  an uncorrected one.**
- **⚠ A CLAMP CAN DO WHAT A CONSTANT APPEARS TO DO, and the tell is a number that DOESN'T
  move.** §3.17 predicted blocks would fall 4.8 → ~2.6 as the mix went three-heavy; they
  stayed at **4.80**. `PROB_FLOOR` (0.02) is **4× `sim.base-block-three` (0.005)**, so a
  three's block chance is **floored, not based** — and `base-block-three` is currently
  **inert**. ⚠ **The blocks row is green for the wrong reason**: two mechanisms cancel,
  which is the #036 B cancelling-errors pattern §3.17 spent a phase un-hiding for FG%.
  **`PROB_FLOOR` applies to every probability**, so any constant set below 0.02 is
  equally inert and nothing says so at its declaration. See backlog.md.

**The question to ask of ANY moved number, before tuning it: "did the engine change, did
the measurement change, or is a clamp holding it?"**

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
- OpenAPI delegate pattern: generated `V1ApiDelegate` interface, hand-written `V1ApiDelegateimpl` implements it.
- Entities use Lombok `@Data` for boilerplate reduction.
- Entity `@Table` annotations include `schema = "gametime"`.
- Cucumber tests use JUnit 5 Platform (`@Suite` + `cucumber-junit-platform-engine`), not JUnit 4 vintage.
- Profiles `local,baseline` are active by default. Tests inherit that and override
  `local` to H2 via `src/test/resources/application-local.properties`.
