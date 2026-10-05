# Gametime

Basketball simulation game — API service, future React frontend.

---

# PART 1 — The user's decisions

**Everything in this Part is Dave's, stated by him. Follow it. Don't re-argue it.**
Part 2 is different in kind — read the note there before citing anything from it.

### Git — never commit without being asked

**Do not run `git commit` or `git push` unless the user asks in that message.** Applies
to every kind of change, including work that is finished, verified and green. Make the
edits, summarize, then **stop and wait**. "Finish the task" / "close it out" / "ship it"
do **not** imply a commit. No side commits, and don't push a branch just because it's
ahead of origin.

### The product is UNRELEASED — 1.0.4 is an open file

**Append schema work to `release.1.0.4.game.sql`. Do not create a new release version.**
Nothing has shipped, so there is no released version to protect. ⚠ **Liquibase has no
opinion about release files or version numbers** — don't cite it as a reason to open
1.0.5. *(This has been re-litigated across several sessions. Don't.)*

### Phase 3 is an OPEN ARC — the engine is the heart of the game

**Work on the possession engine does not end, and new sub-phases are expected.** Phase 3
was planned with far fewer than 22 sub-phases and grew to 22 **because holes kept being
found** — every addition was correct. More will surface. **"Phase 3 shipped" describes
what's built so far; it is NOT evidence the engine is finished.** When the user wants a
new sub-phase, help scope it — **do not argue about whether it's allowed.**

### Issue tracking — beads (`bd`)

Work items live in beads, not markdown lists. `bd prime` runs at session start (hook).
- **Beads holds:** chores (open) and parked ideas (`deferred`, label `idea`). Phase 3
  sub-phases may become children of a Phase 3 epic — put the phase **NAME** in the title,
  not just §N.
- **Docs still hold:** decisions.md, engine-traps.md, calibration.md, the reference docs,
  the diagrams, roadmap.md's phases and seams, and todo.md's execute-ready plans — those
  are reasoning and structure, not tickets.
- **When work becomes a bead, the bead is the only record** — a doc may cite its id
  (`gametime-xxx`), never copy its description.
- **`bd dolt push` is a push** — same rule as git: only when asked. Run `bd` writes with
  `--sandbox` (disables Dolt auto-push; `sync.remote` points at GitHub).
- `.beads/issues.jsonl` is the **git-tracked copy** of the issues (auto-export; the Dolt DB
  is gitignored). Every `bd` write rewrites it — it rides along in whatever commit is next.
- Don't use `bd remember`; durable facts go where they already go.
- **`scrap.md` (repo root) is Dave's scratch page for bead descriptions — never write to
  it.** Put `--body-file` text in the session scratchpad. Human-facing beads routines are
  in README.md.

**Work protocol.** ⚠ **Where `bd prime`'s generic workflow disagrees with this file, this
file wins** — notably its "create an issue before writing code", "use `bd remember`, not
MEMORY.md", `bd dolt pull`/`push` at session close, and "use parallel subagents".
- **Propose new beads; don't create them unasked.** When work surfaces that should outlive
  the session (a drift, a follow-up, an idea), propose it with the exact `bd create`
  command. Create it only after Dave's go-ahead — then creating it directly is fine.
- **No bead for a one-off chat request.** Beads are for work with state, not every edit.
- **Claim** an existing bead (`bd update <id> --claim`) when starting work it covers.
- **Close** a bead only when its Done/acceptance criteria are met **and verified**; say so
  in the summary. The `issues.jsonl` change waits for Dave's commit like any other edit.
- **Never run** `bd dolt push`, `bd dolt pull`, `bd remember`, or `bd edit` (opens an
  editor and blocks).

### Java 21 — never JDK 25

Homebrew Maven defaults to JDK 25, which breaks Lombok 1.18.x. Always:
```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean test
```

### Test coverage is gated

New/changed production code under `gametime-app/src/main/java` ships with tests. The
JaCoCo gate is **per-package and runs at `install`, not `test`** — a green `mvn test`
does not prove it passes. The `test-coverage` skill has the details.

---

# PART 2 — Conventions and accumulated knowledge

⚠ **READ THIS BEFORE CITING ANYTHING BELOW.** Most of Part 2 was **written by past
assistant sessions**, not mandated by the user. It is here because it was useful — the
traps are real and cost real time — but it is **guidance, not authority**.

- **Never cite a Part 2 rule back at the user as a constraint.** If it seems to conflict
  with what he's asking for, say so in one line, note it's a convention, and follow his call.
- **The user has not read all of this and is not expected to.** A rule he never
  agreed to has no force just because it's written down. Don't create fine print, and
  don't enforce it.
- **When you state a rule, say where it came from** — "your git rule" vs. "a past session
  wrote this, want to keep it?" are different sentences.
- **Hard constraints** (which are NOT conventions): Java 21/Lombok, the Liquibase
  checksum, the JaCoCo gate, and Part 1 above.

## Where things live

```
docs/
  engine-traps.md   ⚠ THE ENGINE TRAPS — read before changing any engine constant
  calibration.md       The calibration targets — source of truth for what to tune toward
  possession-flow.puml ⚠ START HERE — the possession on ONE SCREEN, loop and all
    possession-flow-rotation.puml        subs · fatigue · the technical foul
    possession-flow-shot-selection.puml  who shoots · what shot · the putback
    possession-flow-turnover.puml        the gate · nine causes · the charge
    possession-flow-foul.puml            one foul → three labels · FTs · live board
    possession-flow-shot.puml            make/miss/block · recovery · assist · and-1
    possession-flow-rebound.puml         the rebounding foul · the four-way board
  possession-flow-model.md  The maths behind the boxes + each pass's history
  game-events.md       The event vocabulary — every (play_type, outcome), one master table
  game.md              The game's rules: models, possession order, API, what is simplified
  decisions.md         Decision INDEX — crux, constants, traps per #NNN (~1,900 citations resolve here)
  roadmap.md           Phased roadmap; phase structure and seams
  todo.md              Current phase only — states which session it needs
  risks.md             Active risks  (chores + parked ideas → beads: `bd ready`, `bd list --status deferred`)
  player.md · roster.md · coach.md    Domain rules: current behavior, no decision history

gametime-service/      Multi-module Maven (Spring Boot 3.5.14)
  gametime-api/        Generated code ONLY — spec at yml/gametime.yaml
  gametime-app/        All hand-written code
```

**Current state:** §3.22 (#044) is the latest engine sub-phase — **not the last** (see
Part 1). Next is Phase 4's execution (#045, plan in todo.md), then the Phase 3 revisit.

## Before touching the engine

- **`engine-traps.md` first** — the traps, wrong-way levers, inert knobs and measured
  elasticities. The facts no source file holds.
- **The flow diagrams** are a living spec, not an illustration. A new branch or event
  belongs there in the same change. ⚠ **They tell the story of the LOGIC — notes say
  what the flow does, they do not argue the decision behind it** (the user's rule,
  2026-09). The argument goes to `possession-flow-model.md` or `decisions.md`.
  ⚠ **WHERE A NEW BRANCH GOES**: a fork *inside* a resolver → that resolver's own
  file. A new resolver, or **a new way the offense RETAINS** → the overview **too**.
  Drawing it in both places by accident is a drift bug; so is adding a retention path
  the overview's loop condition doesn't mention.
  ⚠ **Every diagram must stay screen-sized** (~2,000px). That is the real ceiling —
  PlantUML's 16,384px limit is only when it stops rendering. The 2026-09 gate found
  the single 13,800px file failed both readers (learn it / modify one branch) and
  split it into an overview + six detail diagrams. ⚠ Branch **order** within a file is
  often load-bearing. Render them all with:
  ```bash
  plantuml -DPLANTUML_LIMIT_SIZE=16384 -tpng docs/possession-flow*.puml
  ```
  **The size flag is required** — plain `-tpng` silently truncates at 4096px. ⚠ And
  **`-checkonly` is not enough**: it does not lay out (so it cannot tell you a PNG is
  complete) and it does not catch deprecated syntax, which renders as a **warning box
  inside the diagram**. Look at the PNG.
- **`calibration.md`** owns the targets. Read it before changing a `SimConfig` constant or
  calling a landing "on target"; update it and the harness `(target ~N)` strings together.
- **`game-events.md`** owns the event vocabulary — one table, one place. A new outcome
  lands there in the same change.

⚠ **Two traps worth knowing cold** (the rest are in `engine-traps.md`):

1. **Sub-phase numbers were REUSED — read the phase NAME, never the number alone.**
   Recalibration was renumbered four times (§3.16 → §3.18 → §3.19 → **§3.20**) and both
   vacated slots were reassigned. In anything written before 2026-08, **"§3.16" and
   "§3.19" both mean RECALIBRATION**. Stale citations remain in `decisions.md` and
   elsewhere **deliberately** (history is never retro-edited) — annotate what you touch,
   don't mass-rewrite.

   | number | phase |
   |---|---|
   | §3.16 | shooting-foul composition + the charge fix (#039) |
   | §3.19 | instrumentation (no `#NNN`) |
   | §3.20 | recalibration against verified targets (#042) |

2. **The harness needs its profile from the ENVIRONMENT** — `-Dspring.profiles.active`
   does **not** reach the forked Surefire JVM, and the failure looks like a database
   error. Use `SPRING_PROFILES_ACTIVE=local,baseline`, run from `gametime-service/`, and
   confirm the report's `Profiles:` line before trusting a number. *(Cost two sessions.)*

⚠ **To prove a change moved nothing:** run the harness, `git stash`, run again **on the
same seed**, diff. Identical line for line, or the claim is false. Plan figures are
usually 5-seed means; a 1-seed run prints different numbers, so same-seed before/after is
the check that means something.

Run the sim tests alone after touching `PossessionEngine`/`ShotSelector`/`RotationState`:
```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -pl gametime-app -f gametime-service/pom.xml test -Dtest='GameSimulatorIntegrationTest' -DfailIfNoTests=false
```

## How a phase has moved so far

*(A convention, and a useful one — it's what let §3.22 be verified against §3.21
byte-for-byte. Not a rule the user is bound by.)* Engine sub-phases have run in three
sessions: a **design pass** (open questions → a short `decisions.md #NNN` + an execute-ready
todo.md plan, no production code), an **execution** (build it, edit any decision letter that
diverged, put a trap in `engine-traps.md`, flip the roadmap bullet), then the next design
pass. `todo.md`'s header says which session the current phase needs. Roadmap bullets are
seams, not plans — they're under-specified on purpose.

The `project-docs` skill carries the doc house style and routing rules. Same status as
this section: useful defaults, not mandates.

## Build

```bash
# compile · test (H2, no Docker) · integration tests
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean compile
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean test
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn verify -Ptest

# local run — Postgres on 8080, Swagger at /swagger-ui.html
cd gametime-service && docker compose up -d     # add `down -v` first for a fresh DB
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn spring-boot:run -pl gametime-app
```

## Database

- **Local dev**: Postgres in Docker (port 5432). **Tests**: H2 in-memory, no Docker.
- All app tables live in the `gametime` schema. Liquibase changelog at
  `src/main/resources/db/changelog.yml`; Postgres-only features gated with `dbms:postgresql`.
- ⚠ **Never edit a changeset that has already run — append a new one.** Liquibase fixes a
  checksum on first run and refuses to start against a changed one. *(This is narrower than
  Part 1's rule: it's about changesets already applied, not about release files.)*
  `1.04.1` is untouchable. Established shape: additive nullable column + its FK, no `dbms` gate.
- ⚠ **H2 runs the SAME changelog as Postgres**, so a malformed changeset fails the whole
  test suite immediately — no Docker needed to verify one.
- Audit columns have defaults for H2; Postgres triggers override them.

## Code conventions

- **`SimConfig` declaration form says whether a constant is tunable**: `public static final`
  is a rule of basketball or the model's shape; `private final` + accessor is a tunable
  knob (`@ConfigurationProperties(prefix = "sim")`).
- **Tunables have no initializers in Java** — values live only in
  `application-baseline.properties`. Never add a default or a `public static final` alias;
  javac inlines constants, so both silently create a second value. Outside Spring, use
  `SimConfig.baseline()`.
- **The tunable count lives in FOUR places that move together**: the header comment,
  `baseline()`'s javadoc, its error message, and `SimConfigProfileBindingTest.EXPECTED_TUNABLE`.
  Currently **63 tunables / 29 statics**.
- Sim profiles are ordinary Spring profiles composed with the infra ones
  (`local,baseline`); era profiles are deltas over `baseline`, so it must stay in the list.
- **`game_event`'s two participant columns are different kinds of fact.**
  `assist_player_id` = the **teammate** who helped. `opponent_player_id` = the
  **counterparty**, always on the **opposite team**. ⚠ A teammate never goes in the opponent
  column (a merge was tried and reversed, #041 D). Populated **only where a real contest
  identified an individual victim**; null by contract elsewhere — don't populate a site just
  because a player is reachable. Test-enforced. Detail: `game-events.md`.
- **On every `FOUL` event, `primary_player_id` is the COMMITTER** — the inverse of
  `SHOT`/`TURNOVER`, where primary is the victim. Test-enforced.
- **gametime-api is generated code only** — never hand-write there; spec changes go in
  `yml/gametime.yaml`. All hand-written code is in gametime-app.
- OpenAPI delegate pattern (`V1ApiDelegate` → `V1ApiDelegateimpl`); Lombok `@Data` entities;
  `@Table(schema = "gametime")`; Cucumber on JUnit 5 Platform (not vintage).
