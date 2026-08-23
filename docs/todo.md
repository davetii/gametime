# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section.

Current focus: **§3.19 — instrumentation**. Phase 3's tail is now
**§3.18 steals (SHIPPED) → §3.19 instrumentation → §3.20 recalibration**.
⚠ **RECALIBRATION HAS BEEN RENUMBERED A FOURTH TIME** — it was §3.16, then §3.18, then
§3.19, and is now **§3.20**. **Read the phase NAME, never the number alone.**
§3.7–§3.18 have all shipped.

> **✅ §3.19 IS A SMALL EXECUTION PHASE. IT NEEDS NO DESIGN PASS.**
> **Three test-side tasks, all decided** — the only open detail is a seed count, picked
> while writing the test. Expect **one sitting**, not a phase's worth of work: two
> assertions added to an existing harness mechanism, one test rewritten to loop seeds, and
> one annotation. **Do not run a design session for this**, and do not expand the scope to
> justify the phase number. Add an implementation note only if something diverges, and
> flip the roadmap bullet to `[x]`.
>
> ⚠ **THE REST OF THIS FILE'S CONTEXT IS ABOUT §3.20 (RECALIBRATION) AND IS DELIBERATELY
> PARKED — DO NOT START IT.** §3.20 is the big pass: it needs **its own design session,
> which opens with a MEASUREMENT rather than with questions**, and that measurement is
> **this phase's exit condition.** So the order is:
> **finish §3.19 → run the 5-seed harness → record the mean → THEN open §3.20's design
> pass** (in a new session, with this file rewritten for it).
> ⚠ **Do not bundle them.** §3.19 makes the instruments honest; §3.20 tunes against them.
> Doing both at once is what this split exists to prevent — and it would put code changes
> inside a design session, breaking the design→execution rhythm fifteen sub-phases have
> held. **§3.20's content lives in [roadmap.md](roadmap.md)'s §3.20 bullet**, not here, so
> it survives this file's rewrite; the thin pointer section at the bottom is intentional.
>
> **Why it is a phase and not a footnote:** §3.20 reads **every** number it tunes from
> `CalibrationHarness`, and a wrong reading does not announce itself — it looks like a
> calibration gap, so you tune a constant to chase a measurement error. §3.11 hit exactly
> that twice in one session and both were caught only because the numbers looked *odd*.
>
> ⚠ **ALL THREE TASKS ARE TEST-SIDE AND MUST MOVE NO ENGINE NUMBER.** That is the phase's
> defining property, exactly as §3.18's was. **Verify it the cheap way: run the harness on
> one seed, `git stash` the tree, run it again, and diff — it must be identical line for
> line.** If anything moved, stop and find out why.

---

## §3.19 execution plan

**Build preamble.** Java 21 or Lombok breaks:
`JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`. The **JaCoCo gate is
per-package and runs at `install`, not `test`** — invoke the `test-coverage` skill.
Invoke `project-docs` before touching any doc. ⚠ **A green `mvn clean install` does not
prove CI will pass** — run the sim classes **alone** too (CLAUDE.md); task 3 exists
precisely because of that.

**Task 1 — finish harness self-verification**
- [ ] The harness already self-checks two things — `Reconciliation (ast+blk)`, per game
      across all 102 (`CalibrationHarness`, the `Agg.reconciliationMismatches` counter).
      **Extend that same mechanism; do not build a parallel one.**
- [ ] **FT-source counts must sum to total FTs, with zero `UNKNOWN`.** Sources are read
      off an outcome suffix (`MADE_SHOOTING`, `MISSED_BONUS`, …), so an untagged or
      mis-tagged outcome lands in `UNKNOWN` and **silently distorts the FT-source
      percentages §3.20 reads.** Nothing asserts that bucket is empty today.
- [ ] **Points must reconcile with the event log** — sum 2/3 per made FG and 1 per made
      FT from the events and check it against the box-score total. **Points is a headline
      §3.20 target and nothing currently verifies the harness computes it consistently.**
- [ ] Report the result on the existing reconciliation line (or beside it), in the same
      `OK` / `N MISMATCH(es)` form.

**Task 2 — fix the brittle technicals test**
- [ ] `GameSimulatorIntegrationTest.technicalFoulsArePersistedOnTheBoxScoreAndReconcile...`
      asserts a **~13% random event** on a pinned seed (`assertTrue(technicals > 0)`), and
      has broken **twice** on passes that touched neither technicals nor fouls.
- [ ] **Assert `technicalEvents == boxTechnicals` over a batch of ~10 seeds and DELETE the
      precondition.** The identity holds for **every** seed — including zero-technical
      ones — so a batch is non-vacuous by construction with nothing to re-pin.
- [ ] **Drop the fixture to the realistic 25 possessions/period** (the baseline) from
      today's inflated 40. ⚠ The parameter is possessions **per period**, not per game.
- [ ] ⚠ **Rejected alternatives, do not revisit:** *raising the possession count* only
      makes the coin flip a better bet while pushing the fixture further from a real game;
      *re-pinning the seed* has already failed three times.
- [ ] ⚠ **Audit for siblings**: any other fixed-seed sim test whose assertion depends on a
      rare event firing. Grep for seed literals.

**Task 3 — `@Transactional` on `V1ApiDelegateimplTest`**
- [ ] It is not `@Transactional` and **commits roster rows**, changing who is on the floor
      and therefore RNG consumption downstream — so **a green local `mvn clean install`
      does NOT prove CI passes.** That is how §3.17 shipped a red branch after two clean
      local builds.
- [ ] ✅ **The "is it deliberately non-transactional?" question was CHECKED (2026-08):
      no.** No `@Transactional`, no `@DirtiesContext`, no ordering annotation, no
      explanatory comment, across 15 independent methods — an oversight.
- [ ] ⚠ **Watch for**: a method silently depending on committed state from an earlier one
      will start failing. That is the fix **surfacing** a latent coupling, not causing it.
      If it happens, give the sim tests their own fixture instead.

**Definition of done**
- `mvn clean install` green (JaCoCo gate included), **and** the sim classes green run
  **alone** — which task 3 should now make equivalent.
- ⚠ **Proven to move no engine number** — same-seed harness run before/after, identical.
- **A clean 5-seed harness run**, profile from the **environment**
  (`SPRING_PROFILES_ACTIVE=local,baseline`, confirm the `Profiles:` line). ⚠ **Record the
  mean in `calibration.md`'s Current column — it is §3.20's input.**
- Roadmap §3.19 bullet flipped to `[x]`; an implementation note on a new `#042` **only if
  something diverged** (this phase resolves no design questions).
- ⚠ **Do NOT commit** — leave the work in the tree and wait (CLAUDE.md).

**⚠ Do NOT** *(reference)*
- **Do NOT tune anything.** No `SimConfig` change, no constant, no rate. This phase makes
  instruments honest; **§3.20 does the tuning.**
- **Do NOT reroute `blockProbability`** — closed as not worth it (~half a blocked three
  per team-game on an on-target row). Footnote only, in the §3.20 bullet.
- **Do NOT start §3.20's design pass here** — it opens with a measurement, and that
  measurement is this phase's exit condition.

---

## §3.20 (recalibration) — NOT THIS PHASE. Pointers only.

⚠ **Do not start any of this until §3.19 above is done and the 5-seed harness run is
recorded.** That run is §3.19's exit condition and §3.20's first input.
⚠ **This section is deliberately thin, and that is not an omission.** The content lives
in **roadmap.md's §3.20 bullet** so it survives this file's rewrite — todo.md is
current-phase-only, and it gets rewritten for §3.20 when that pass opens.

**When you get there, read in this order:**
1. **[roadmap.md](roadmap.md)'s §3.20 bullet** — the core problem (**2P%, FTA and points
   are over-determined**: ~+9.8 points of lift available against a 5.6-point gap, so they
   cannot all be hit independently), the levers, the frozen decisions, the exit condition.
2. **[calibration.md](calibration.md)** — live numbers and the operative rules.
3. **[decisions.md](decisions.md)** — the **"⚠ WHAT §3.20 INHERITS"** handoff block at the
   end: every gap, the `#NNN` that owns it, and the constraint on each that is easy to
   miss. An index over #036 / #039 / #040 / #041, so you need not read five entries.

---

## ⚠ Traps that bite EVERY session — read these before trusting a doc

**1. SUB-PHASE NUMBERS HAVE BEEN REUSED — FOUR TIMES FOR ONE PASS.** Recalibration was
§3.16, then §3.18, then §3.19, and is now **§3.20**. ⚠ **So BOTH numbers are ambiguous:**
every **"§3.16"** written before 2026-08 means RECALIBRATION (the §3.16 slot became
shooting-foul composition, shipped), and every **"§3.19"** written before this session
also means RECALIBRATION (the §3.19 slot is now **instrumentation** — this phase).
⚠ **The docs have NOT been swept**: ~60 stale "§3.19 = recalibration" references remain
in `decisions.md`, `roadmap.md`, `backlog.md` and `ideas.md`, left deliberately rather
than mass-edited, because retro-editing history is worse than a callout. **Read the phase
NAME, never the number alone.** roadmap.md carries the mapping.

**2. A NUMBER CAN MOVE — OR FAIL TO MOVE — FOR REASONS THAT ARE NOT THE ENGINE.**
⚠ **This is §3.20's single most important trap, because recalibration's whole job is
moving numbers** — but it applies here too: **§3.19 must move NO number**, so the same
question catches a test-side change that unexpectedly did. Before tuning anything, ask: **did the engine change, did the MEASUREMENT
change, or is a clamp holding it?** All three have happened:
- **The instrument broke** — a harness row classified a stopped shot by its free-throw
  count, and a phase that changed what a foul awards silently halved the row.
- **⚠ FIXING an instrument also moves numbers, and looks like a regression** — `Stopped
  shots / team` read 12.43 post-fix against 7.65 pre and appeared to double. **It fell**
  (7.39 → 6.24 like-for-like). **Compare raw-to-raw across an instrument change.**
- **⚠ A CLAMP can do what a constant appears to do, and the tell is a number that DOESN'T
  move** — blocks were predicted to fall to ~2.6 and stayed at 4.80. See Q1.

**3. THE HARNESS NEEDS ITS PROFILE IN THE ENVIRONMENT.** `-Dspring.profiles.active` does
**not** reach the forked Surefire JVM; the context comes up with `activeProfiles = []`
and fails with what looks like a database error. Use
`SPRING_PROFILES_ACTIVE=local,baseline`, run from `gametime-service/`, and **confirm the
report's `Profiles:` line before trusting any number.** This has cost two sessions.

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
  `GameEvent` — ✅ **CLOSED by §3.18**) · §3.12 **#030** (~20 3PA/team/game
  — ✅ **CLOSED by §3.17**, landed 37.22) · §3.16 **#039** (the unsourced shooting-foul
  share — ⚠ now also **mispriced**, see #040) · **§3.17 #040** (FG%/2P%, FGA, FTA and
  the `PROB_FLOOR`-vs-`base-block-three` finding — all **§3.20's**)

**Other files own these outright:**

- **The phase sequence, this phase's bullet, and §3.20 (recalibration) — including its
  core over-determination problem** → [roadmap.md](roadmap.md)
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
