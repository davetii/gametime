# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.19).

Current focus: **§3.18 — the steal as a first-class event**. Phase 3's tail is
**§3.17 shot mix (SHIPPED) → §3.18 steals → §3.19 recalibration**
(`decisions.md` **#036**, resequenced by **#038**). ⚠ **The old "§3.16 =
recalibration" is now §3.19** — see #038 for the current mapping. §3.7–§3.13,
§3.14a, §3.14b, §3.15, §3.16 and **§3.17** have all shipped.

> **✅ §3.18 IS EXECUTE-READY — the design pass is resolved as `decisions.md` #041.**
> Build exactly the plan below, add an implementation note to **#041** recording any
> divergence, and flip the roadmap bullet to `[x]`. **Decisions A–H are settled; do not
> re-litigate them** — the event-vs-column and `PlayType.STEAL` questions were argued at
> length and resolved (B/C), and the assists migration was pursued and **reversed** (D).
>
> **The gap, in one line:** a steal is the **only contested defensive play the event
> log does not attribute**. `PossessionEngine:210–216` picks the stealer
> (`pickStealer`), credits the box score (`recordSteal()`), and then **drops them**.
>
> **The fix, in one line:** a new **`opponent_player_id`** column — *the counterparty,
> always on the opposite team* — carrying the **stealer** on `TURNOVER`/`STOLEN`, the
> **blocker** on `BLOCKED_*`, and the **fouled shooter** on `SHOOTING_FOUL`.
>
> ⚠ **THIS PHASE MUST MOVE NO NUMBER.** No new RNG draw, no `SimConfig` change, no
> resolver. Every value it writes is a player an **existing** draw already selected.
> If a harness aggregate moves, something is wrong — stop and find it.
>
> ⚠ **The steal RATE is measured at 7.72 vs a target 8.4 and is deliberately NOT fixed
> here** (#041 F). It is **§3.19's**, as a derived quantity. Do not tune it.

---

## §3.18 execution plan (decisions.md #041 — resolved)

**Build preamble.** Java 21 or Lombok breaks:
`JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`. The **JaCoCo gate is
per-package and runs at `install`, not `test`** — invoke the `test-coverage` skill;
a green `mvn test` does not prove it passes. Invoke `project-docs` before touching
any doc. ⚠ **A green `mvn clean install` does not prove CI will pass** — after any
change touching the sim, also run the sim classes **alone** (CLAUDE.md's warning).
**This phase should not move the RNG stream at all**, so that run is a *verification
that nothing moved*, not a re-baselining exercise.

⚠ **EVERY LINE NUMBER BELOW IS A 2026-08 SNAPSHOT AND WILL DRIFT** as you edit — they
are given so you can *find* the site, not as addresses to trust. `PossessionEngine` has
**13 `addEvent` call sites**; the three this phase touches are identifiable by their
`PlayType` + outcome, not by line. Re-grep (`grep -n "addEvent(" PossessionEngine.java`)
rather than seeking a line.

⚠ **The H2 test database runs the SAME Liquibase changelog as Postgres**
(`spring.liquibase.change-log=classpath:db/changelog.yml` in
`src/test/resources/application-local.properties`), so **a malformed changeset fails the
whole test suite immediately**, not just a local `docker compose` run. That is the
fastest verification of Step 1 — no Docker needed.

⚠ **Expected blast radius, so you can tell "done" from "missed something":** this phase
touches **no** `SimConfig` value, so `SimConfigProfileBindingTest.EXPECTED_TUNABLE`
(**62**) must **not** move, and CLAUDE.md's four-places tunable-count rule does **not**
apply here. No harness aggregate should move either — see Step 5's stream check.

**Step 1 — the schema column (#041 A)**
- [ ] Append a **NEW changeset `-- changeset 1.04.5`** to
      `gametime-app/src/main/resources/db/release.1.0.4.game.sql` adding
      `opponent_player_id VARCHAR` to `game_event`, nullable, no `dbms` gate.
      ⚠ **NEVER edit `1.04.1`** — it has already run against live dev databases and its
      checksum is fixed; Liquibase will refuse to start. §3.6 (`1.04.2`), §3.10
      (`1.04.3`) and §3.14a (`1.04.4`) each appended for exactly this reason.
- [ ] Add the FK to `player`, mirroring the existing `assist_player_id` FK
      (`release.1.0.4.game.sql:71`).
- [ ] Comment the changeset in the house style of `1.04.3`: what it holds, the
      counterparty invariant, and its three day-one consumers.

**Step 2 — the plumbing (the path `committing_team_id` already walked, #028 D)**
- [ ] `GameEventEntity`: `@Column(name = "opponent_player_id") private String
      opponentPlayerId;` with a javadoc stating **the counterparty contract** — always
      the opposite team from `primaryPlayerId`; which events populate it; and that a
      **teammate never** goes here (an assister rides `assistPlayerId`, #041 D).
- [ ] `GameData.EventRecord`: add the field.
- [ ] `GameData.addEvent(...)`: add the overload. ⚠ There are already three overloads
      chaining into a full one; extend that chain rather than adding a parallel path.
- [ ] `EntityMapper` (~line 197): map it alongside `assistPlayerId`. ⚠ Also update the
      **javadoc above it** (~line 182), which currently says the mapper "surfaces
      assistPlayerId" — it will surface two participant columns after this.
- [ ] ⚠ **`GameEventEntityTest`** asserts a getter per column on a fully-populated entity;
      add `opponentPlayerId` there or the new field ships untested (the JaCoCo gate is
      per-package and runs at `install`, not `test`).
- [ ] ⚠ **Update `assistPlayerId`'s javadoc** — it currently says "null on every
      non-SHOT event", which is still true but now needs to say *why* the two columns
      are different kinds of fact (teammate vs. opponent, #041 D), so a later reader
      does not merge them.

**Step 3 — populate the three sites (#041 A). No new draws.**
- [ ] **The steal** — `PossessionEngine:210–216`. `stealer` is **already in scope**;
      pass it as the opponent on the `TURNOVER` event. ⚠ **Hoist the `pickStealer`
      call so the value is available at `addEvent`, without changing WHEN the draw
      happens** — the cause draw precedes it and must keep doing so.
- [ ] **The block** — `PossessionEngine:352`, the `BLOCKED_*` SHOT event. The blocker
      is credited via `recordBlock()`; carry them as the opponent. This closes #025 F2
      deliberately (that entry left the blocker off the event **by design**, mirroring
      the steal — #041's rationale is that it copied the gap rather than closing it).
- [ ] **The shooting foul** — `PossessionEngine:327`. `primaryPlayerId` is the
      **defender** who committed it; `shooter` is in scope — carry them as the opponent.
- [ ] ⚠ **`OFFENSIVE_FOUL` (the charge) stays NULL on both its events** — the drawer is
      **not modelled** and picking one needs a new RNG draw (#041 follow-up). Add a
      comment at the site so it reads as deliberate, not overlooked.
- [ ] ⚠ **Everything else stays null.** Rebound, free-throw, technical and the other
      eight turnover causes have no counterparty.

**Step 4 — the OpenAPI surface (#041, and #028 D's still-open follow-up)**
- [ ] Add `opponentPlayerId` to `GameEvent` in `gametime-api/yml/gametime.yaml`
      (~line 726), with a description carrying the counterparty rule.
- [ ] ⚠ **Also add `committingTeamId` in the same edit** — it has been on the entity
      since §3.10 and **never reached the spec**, which is #028 D's open follow-up.
      A one-line addition while the file is open; note it in #041's implementation note.

**Step 5 — the invariants (#041 G) — the real deliverable**
- [ ] **The counterparty invariant**, over every event of a simulated game: where
      `opponentPlayerId` is non-null, the opponent's team **≠** primary's team, and both
      are among the two teams on the row. One assertion covering every present and
      future site.
- [ ] **The per-creditor steal reconciliation**: for **every** player X,
      `count(TURNOVER/STOLEN events with opponentPlayerId = X)` **==**
      `box_score.steals(X)`. ⚠ This is the upgrade — the existing **total** check passes
      even when the engine credits the **wrong** player.
- [ ] The same per-creditor shape for **blocks** against `box_score.blocks(X)` (now
      expressible for the first time — #025 F4 only ever got the count-based version).
- [ ] ⚠ **Assert the RNG stream did NOT move**: the pre-existing seeded expectations in
      `GameSimulatorIntegrationTest` must pass **unchanged**. If any needs a new value,
      **stop** — something consumed a draw and #041's central premise is broken.

**Step 6 — `docs/game-events.md` (#041 H) — an EXTRACTION, not a new document**

⚠ **READ THIS BEFORE WRITING ANYTHING.** `game-events.md` **already exists in substance**:
it is `game.md`'s **`### play_type values and their outcome vocabulary`** section, **lines
466–724, 23.9k chars — 35% of that 67.6k file**. *(Found 2026-08 by the user, after #041 H
was written as if the doc were new. #041 H's intent stands; its framing was wrong.)*
**Authoring a fresh table would create a SECOND per-event vocabulary and guarantee drift**
— the exact failure CLAUDE.md documents for `possession-flow.puml`, and the one Step 7
guards against for `player.md`. **Move it; do not rewrite it from scratch.**

- [ ] **Move** `game.md` lines 466–724 into a new `docs/game-events.md`. It already
      carries every `play_type` × `outcome` pair, the `committing_team_id` rules and the
      `#NNN` citations — **preserve all of it**, including the ⚠ notes (the
      `COMMON_FOUL` → `NON_SHOOTING_FOUL` no-migration warning especially).
- [ ] **Leave a pointer** in `game.md` where the section was, so the flow doc still routes
      readers to the vocabulary. `game.md` keeps the **models** (`Game`, `GameEvent`,
      `BoxScore`), the **possession flow / calculation sequence**, and the **API surface**.
- [ ] **Add the three rules as the preamble** — this is the genuinely NEW content:
      **(1)** the emission rule (second participant → column, second accounting → event,
      neither → nothing) — it explains why the steal is one row and the charge is two;
      **(2)** the counterparty invariant + why assists keep their own column (#041 D);
      **(3)** `outcome` is free text (#020) while `play_type` is a **closed OpenAPI enum**
      — why new causes are free and new play types are expensive.
- [ ] **Restructure the table to explicit participant columns** *(user call, 2026-08)*:
      `play_type | outcome | primary | opponent | assist | committing_team | notes`.
      Today the participant rules are **buried in prose** in the Meaning column; making
      them columns is what lets a reader answer "who is on this event?" at a glance —
      and gives the Step-5 invariant test something structured to check against.
      Move only the *rules* into columns; keep the reasoning and ⚠ notes in `notes`.
- [ ] **Fix the two rows §3.18 changes.** They currently document the gap as designed:
      `BLOCKED_*` says *"primary_player = shooter (victim), blocker credited a BLK
      separately"* and `STOLEN` says *"ball-handler on the event, defender credited a
      steal separately"*. After §3.18 both carry the creditor in `opponent`.
      Also fill `opponent` on `SHOOTING_FOUL` (the fouled shooter).
- [ ] ⚠ **Record the deliberate NULLs with their reason**, not as blanks — especially
      `OFFENSIVE_FOUL` (the charge-drawer is **not modelled**; picking one needs a new RNG
      draw, #041 follow-up) and `TECHNICAL_FOUL` (the FT shooter is **not** a counterparty).
      A blank reads as "nobody got to it" and invites a later "fix".
- [ ] ⚠ **Document what the engine DOES, never what would be nice.** Derive every row
      from the 13 `addEvent` sites (audited 2026-08: 215, 247, 303, 326, 351, 390, 448,
      570, 654, 695, 736, 845, 872). An outcome listed but never emitted makes this a spec
      nobody implemented.
- [ ] **Update the references** to the moved section: `CLAUDE.md`'s project-structure
      block and docs list, and any `game.md` cross-links pointing at the vocabulary.
      ⚠ Check `possession-flow.puml` for references too.

**Step 7 — the diagram and the harness**
- [ ] `possession-flow.puml`: the STOLEN node (~line 180) currently reads
      `TURNOVER — STOLEN / credit defender steal`. Update it to show the stealer landing
      on the **event**, with a note citing #041 A/B. ⚠ **Re-read the branch top-to-bottom
      as a cold reader afterwards** — CLAUDE.md's rule is that a correctly-added fork can
      still break the boxes already there. Validate with
      `plantuml -checkonly docs/possession-flow.puml`; render with
      **`-DPLANTUML_LIMIT_SIZE=16384 -tpng`** (a plain `-tpng` silently truncates).
- [ ] ⚠ **The harness steals row is ALREADY ADDED** (this design session, test-only —
      the standing backlog chore). Keep it; do not re-add. Close the backlog entry.
- [ ] `calibration.md`: the Steals row currently reads *"harness never prints it —
      engine ~7.67 by derivation only"*. Update it to **measured 7.72** (5 seeds,
      sd 0.148) and mark it **§3.19's**, per #041 F.
- [ ] ⚠ **`player.md` — the DOMAIN-DESIGN doc the plan nearly missed** *(user caught this,
      2026-08)*. Its **"Possession Event → Skills Used"** table (~line 348) is the only
      per-event reference outside the new `game-events.md`, and **two of its rows are
      §3.18's**: `Shot block attempt` (the blocker, picked by `rimProtection`/
      `shotContest`) and `Turnover / steal` (the stealer, picked by `stealing`). **No
      skill or formula changes** — §3.18 reads no new attribute — but both plays now
      record their creditor on the event, so add that to the two rows and **cross-link to
      `game-events.md`**. ⚠ **Two per-event tables that do not point at each other WILL
      drift** — the exact failure CLAUDE.md documents for `possession-flow.puml`.
      ⚠ **`coach.md` and `roster.md` need NOTHING** (checked 2026-08): §3.18 reads no
      coach attribute (coach influence reaches these plays through the parent event —
      coach.md's own row 11), and touches no roster/lineup concept. Stated here so the
      next pass does not re-check them.
      ⚠ See also backlog.md's open chore that the `project-docs` routing table **omits
      these four domain-design docs** — this step is a symptom of that gap.

**Reconciliation invariants this phase must leave green** *(reference — not checkboxes)*
- `count(TURNOVER/STOLEN events with opponentPlayerId = X) == box_score.steals(X)`, ∀X
- `count(SHOT/BLOCKED_* events with opponentPlayerId = X) == box_score.blocks(X)`, ∀X
- opponent's team ≠ primary's team, wherever opponent is non-null
- every existing assists/blocks/fouls reconciliation, **unchanged**

**⚠ Do NOT** *(reference — not checkboxes)*
- **Do NOT add a `PlayType.STEAL`** or a second event (#041 B/C — argued and rejected).
- **Do NOT migrate assists** onto the new column (#041 D — pursued and **reversed**).
  `assist_player_id` is **not** deprecated and has no removal phase.
- **Do NOT add an OFFENSE/DEFENSE side-indicator** (#041 E — the side is derivable).
- **Do NOT touch the turnover count, gate or cause weights** (#027 A, still frozen).
- **Do NOT tune the steal rate** (#041 F — §3.19's).
- **Do NOT add `DOUBLE_DRIBBLE`** here (#041 follow-up — it moves rates).
- **Do NOT edit changeset `1.04.1`** (checksum already fixed).

**Definition of done** *(reference — the phase is complete when ALL of these hold)*
- `mvn clean install` green (the per-package JaCoCo gate included), **and** the sim test
  classes green when run **alone** — the stricter check (CLAUDE.md).
- The pre-existing seeded expectations in `GameSimulatorIntegrationTest` pass
  **unchanged** — the proof the RNG stream did not move.
- A 1-seed `CalibrationHarness` run reproduces the §3.17 landing: points **110.0**,
  FG% **43.5**, 3PA **37.22**, blocks **4.80**, steals **7.72**. ⚠ **Any movement means a
  draw was consumed** — stop and find it; do not re-baseline.
- `plantuml -checkonly docs/possession-flow.puml` passes, and the rendered PNG is
  **taller than 4096px** (the `-DPLANTUML_LIMIT_SIZE=16384` flag is REQUIRED).
- `decisions.md` **#041** carries an implementation note; the roadmap **§3.18** bullet is
  flipped to `[x]` with a landing summary.
- ⚠ **Do NOT commit** — leave the work in the tree and wait for the user (CLAUDE.md).

**Open at execution** *(small, constrained)*
- Whether the `game-events.md` master table is hand-written or generated from the emit
  sites (a generator is more durable but is test-code the phase does not otherwise need).
- Exact javadoc wording for the counterparty contract on `GameEventEntity`.

### ⚠ Two traps that bite EVERY session — read these before trusting a doc

**1. SUB-PHASE NUMBERS HAVE BEEN REUSED.** Recalibration was §3.16, then briefly
§3.18, and is now **§3.19** (#038). **Every "§3.16" written before 2026-08 means
RECALIBRATION.** Read the phase NAME, never the number alone.

**2. A MECHANIC CHANGE CAN SILENTLY BREAK AN INSTRUMENT, and no test will catch it.**
⚠ **§3.17 is now the worked example of BOTH halves of this trap**, and it is worth
reading before touching the harness:
- §3.16 broke the fouled-three rows by changing what a foul *awards*; §3.17 Step 0
  fixed them (#040 G) — but **the fix itself moved a number**: `Stopped shots / team`
  reads **12.43** post against **7.65** pre and looks doubled. **It fell** — 7.39 → 6.24
  like-for-like on the raw visible tally. **Always ask "did the engine change, or did
  the measurement?" before tuning anything.**
- ⚠ **§3.17 also found a CLAMP doing what a constant appeared to do**: `PROB_FLOOR`
  (0.02) is 4× `sim.base-block-three` (0.005), so blocks did not fall when the mix went
  three-heavy and **`base-block-three` is inert**. A predicted number that does not move
  is as informative as one that moves wrongly. See backlog.md.

---

### Verified facts (measured 2026-08 against the tree — for whoever EXECUTES §3.18)

- **Post-§3.17 baseline (5 seeds, 102 games each, `local,baseline`)**: points **110.0** ·
  FG% **43.5** · 3P% **35.8** · FGA **92.28** · **3PA 37.22** · ast 26.1 · TO **13.9** ·
  FTA **19.76** · fouls **18.18** · blocks 4.80 · off reb 11.18 · def reb 30.48 ·
  foul-outs 0.304 · penalty **46.1%** · flagrants 0.141 · technicals 0.358.
  ⚠ **FG%, FGA and FTA are all §3.19's and all land worse ON PURPOSE** (#040 E/F) —
  do not read them as drift, and do not tune them in §3.18.
- **The harness needs the profile in the ENVIRONMENT**, not on the mvn command line.
  `-Dspring.profiles.active=...` does **not** reach the forked surefire JVM. Use:
  ```
  SPRING_PROFILES_ACTIVE=local,baseline JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -pl gametime-app test -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000 -DfailIfNoTests=false
  ```
  Run it from `gametime-service/`. **Confirm the report's `Profiles:` line** before
  trusting any number. Judge at **5 seeds** (1000–5000).
- **`SimConfig` now carries 62 tunables and 27 statics.** The count lives in FOUR places
  and all four move together: `SimConfig`'s header comment, `baseline()`'s javadoc and
  its error message, and `SimConfigProfileBindingTest.EXPECTED_TUNABLE` (which asserts
  **both** the constructor arity and the instance-field count).
- **The steal path**: `PossessionEngine:174–181` — `pickStealer` chooses, `recordSteal()`
  credits, and the `TURNOVER` event's `primaryPlayerId` is the **shooter**.
- **`PERSONAL_FOULS_PER_TEAM_GAME = 17.82`** (§3.17 re-measured it from 20.15). ⚠ It is a
  **shot-mix-derived** quantity now — any pass that moves the foul rate must re-measure
  it in writing (#034 G).

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved out
so this file can be rewritten each phase without losing it. **This is a ROUTING TABLE —
pointers only.** The reasoning lives at the destination; do not restate it here, and do
not add "done" entries (those are the `#NNN` entry's job).

**Deferred engine work — see the cited entry's follow-up list:**

- **Technicals** — bench/coach technicals; a technical's lack of causal input → **#032**
- **Foul trouble** — period-/time-awareness (#031 C); getting foul-outs below ~0.38
  (lever measured saturated); splitting `substitutionAggressiveness` (#031 B) → **#031**
- **Over-dispersion** — `pickDefender` concentration also reaches blocks, steals and
  shot contests → **#031 A**
- **Seams left open by their design pass** → §3.6 **#024** · §3.7 **#025** · §3.9
  **#027** · §3.10/§3.11 **#028/#029** (incl. `committing_team_id` not on the OpenAPI
  `GameEvent` — ⚠ **§3.18 will likely reopen this**) · §3.12 **#030** (~20 3PA/team/game
  — ✅ **CLOSED by §3.17**, landed 37.22) · §3.16 **#039** (the unsourced shooting-foul
  share — ⚠ now also **mispriced**, see #040) · **§3.17 #040** (FG%/2P%, FGA, FTA and
  the `PROB_FLOOR`-vs-`base-block-three` finding — all **§3.19's**)

**Other files own these outright:**

- **§3.19 (recalibration)**, and the phase sequence generally → [roadmap.md](roadmap.md)
- **Calibration targets** → [calibration.md](calibration.md), **the source of truth**.
  Update it *and* the `CalibrationHarness` `(target ~N)` strings together.
- **Infra/tooling/data-hygiene chores**, and **the `decisions.md` condense pass** (a
  **gate on starting Phase 4**) → [backlog.md](backlog.md)
- **Untriaged ideas**, incl. the parked cap 3→5 tuning idea and strategic substitution →
  [ideas.md](ideas.md)

**Standing facts (true every phase, not deferrals):**

- `CalibrationHarness` is disabled by default and lives in the `sim` test sources; run
  it with `-Dcalibration=true -DcalibrationSeed=NNNN`. **Re-run after any `SimConfig`
  change.** Since §3.15 the sim profile is selected by the ordinary Spring profile list —
  `SPRING_PROFILES_ACTIVE=local,baseline` (#035 F — ⚠ **the `-D` form does NOT reach the
  forked surefire JVM**). ⚠ **`baseline` must always
  be in the list** (it carries all **62** values), and **order is positional** — putting it
  last lets it win and silently neutralizes the era file. The **effective-config dump**
  in the report is what catches that, and it also catches a caller passing its own copy
  of a profilable value (the §3.15 harness-pace trap).
- **Do NOT touch `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`'s baseline value** without its
  own recalibration pass (#034 B) — though §3.15 makes it *profilable* (#035 C).
