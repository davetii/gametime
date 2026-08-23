# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section.

Current focus: **§3.20 — recalibration**, which **needs a DESIGN PASS first**.
Phase 3's tail is **§3.18 steals (SHIPPED) → §3.19 instrumentation (SHIPPED) → §3.20
recalibration**.
⚠ **RECALIBRATION HAS BEEN RENUMBERED A FOURTH TIME** — it was §3.16, then §3.18, then
§3.19, and is now **§3.20**. **Read the phase NAME, never the number alone.**
§3.7–§3.19 have all shipped.

> **⚠ §3.20 NEEDS A DESIGN PASS, AND IT OPENS WITH A MEASUREMENT RATHER THAN WITH
> QUESTIONS — SO START BY RUNNING THE HARNESS.** The open questions are in the **§3.20
> section below**, but ⚠ **do not start arguing them until Step 0's 5-seed run is in
> hand**: this pass adds no mechanic, so the measurement is an INPUT, not a check
> afterwards. §3.19 left a 5-seed mean in [calibration.md](calibration.md)'s Current
> column — **that is a reference to reproduce and diff against, not a substitute for
> measuring.** Write no production code this session; the output is `#042` plus an
> execute-ready plan replacing that section.
>
> ✅ **§3.19 (instrumentation) SHIPPED 2026-08 — no `#NNN`, because it resolved no design
> question.** All three tasks landed as planned, and **the phase moved no engine number**
> (same-seed harness run before/after, identical line for line). The full landing note is
> on **roadmap.md's §3.19 bullet**; what §3.20 needs to know from it is short:
> - **The harness now self-verifies THREE identities**, all reading `OK` on all five
>   seeds: `ast+blk` (pre-existing), **`ft-src`** (per-source FT counts sum to total FTs
>   **and** the `UNKNOWN` bucket is empty) and **`points`** (events = box score = final
>   score). ⚠ **A `MISMATCH(es)` on any of them invalidates the rows it feeds — fix the
>   instrument before reading a number.** That is the whole point of the phase: a wrong
>   reading looks like a calibration gap, so you tune a constant to chase a measurement
>   error.
> - **The seeded tests are trustworthy now.** The technicals test asserts its identity
>   over ten seeds at the realistic 25 possessions/period with the coin-flip precondition
>   deleted, so **no §3.20 change can break it by shifting the RNG stream**. And
>   `V1ApiDelegateimplTest` is `@Transactional`, so **a green `mvn clean install` finally
>   does mean the sim tests pass in isolation** — run them alone anyway, it is cheap.
> - **Some calibration.md rows moved against the previous reading. They were STALE, not
>   new** (4/5/6 fouls 0.94/0.46/0.30, and-1s 1.55, fouls/period 4.58, OOB 3.1, ejections
>   0.033). ⚠ Do not read them as drift and do not tune toward the old values.

---

## §3.20 (recalibration) — THE OPEN QUESTIONS, and the order to take them in

⚠ **This is a DESIGN PASS. Write no production code in this session.** Resolving these
questions IS the session; tuning happens in the **execution** session that follows.

**How to run it — the shape of a good design session here:**
1. ⚠ **Invoke the `project-docs` skill first.** It carries the `#NNN` entry format
   (Decisions A/B/C…, then Rationale / Trade-off / Alternatives **keyed by letter**), the
   ~15–20k budget, and the routing rules. **Do not invent a structure.**
2. **Run Step 0's harness measurement** (below) before arguing anything.
3. **Work Q1–Q7 in order**, against the numbers you took. ⚠ **Q1 is a call for the USER,
   not for you — see the box under it.**
4. **Write `decisions.md #042`** — append at the bottom, never renumber. One Decision per
   resolved question; cite the `#NNN` each constraint comes from rather than re-arguing it.
5. **Replace this §3.20 section with the execute-ready plan** — a build preamble, then
   `**Step N — title (#042 ref)**` headers with `- [ ]` sub-items, a **Definition of
   done**, and a **⚠ Do NOT** block. For the shape, read the last one:
   `git show f98459b^:docs/todo.md` (§3.19's plan, which used `Task N`).
6. **Leave the roadmap bullet `[ ]`** — it flips at execution, not here.

⚠ **What makes THIS pass different from the fifteen before it**: every one of those
reasoned about a mechanic that did not exist yet, so the argument came first and the
harness confirmed it afterwards. **§3.20 adds no mechanic.** The numbers already exist,
so the measurement leads and the argument is about **which target yields** — a
prioritization pass, not a modelling one.

### ⚠ STEP 0 — RUN THE HARNESS BEFORE ARGUING ANYTHING

**§3.20's design pass opens with a MEASUREMENT, not with questions — this inverts every
prior phase.** §3.4–§3.19 each added a mechanic, so design reasoned about behavior that
did not exist yet and the harness ran afterwards. **§3.20 adds no mechanic, so the
measurement is an INPUT.** Argue every question below against numbers you took yourself,
in this session.

```bash
cd gametime-service && for s in 1000 2000 3000 4000 5000; do SPRING_PROFILES_ACTIVE=local,baseline JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -q -pl gametime-app test -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=$s -DfailIfNoTests=false; done
```

- ⚠ **Take the 5-SEED MEAN, seeds 1000–5000.** Per-seed noise is ±1.5 points, enough to
  bait an over-correction; technicals and flagrants need 5 seeds as a **hard floor**.
- ⚠ **Confirm the `Profiles:` line reads `local,baseline`** on every run before trusting a
  number (Trap 3 below — this has cost two sessions).
- ⚠ **Confirm all THREE reconciliation lines read `OK`** — `ast+blk`, `ft-src`, `points`.
  §3.19 built the latter two for this pass. **A `MISMATCH(es)` invalidates the rows it
  feeds: fix the instrument before reading a number.**
- **Then diff your mean against [calibration.md](calibration.md)'s Current column.** Those
  values are §3.19's run (2026-08) and should reproduce. ⚠ **If a row disagrees, that is a
  finding, not a nuisance** — ask Trap 2's question before tuning it.

### Step 0 → the questions: how the measurement picks your starting point

**The expected cadence is: run the harness, then work the biggest deviations.** The list
below is already ordered that way against §3.19's numbers, so **if your run reproduces
calibration.md, take Q1 first and work down.** What the run adds is a check on that
ordering:

- **The run reproduces (expected).** Q1 is the pass's real work — **2P% and FTA are the
  two rows under review**, and Q2 constrains how they can be fixed. Q3–Q7 are smaller and
  several of them *depend* on Q1's answer, so resolving them first wastes the work.
- **A row disagrees with calibration.md.** ⚠ **That is a finding, and it comes BEFORE
  Q1** — not because it is bigger, but because an unexplained number means you do not yet
  know which of Q1–Q7 you are actually looking at. Ask Trap 2's question (*engine,
  measurement, or clamp?*), then resume the order.
- ⚠ **Do not re-rank the list on one seed.** Per-seed noise is ±1.5 points; a row that
  looks worse than Q1 on a single run may be inside the noise band. **Re-rank on the
  5-seed mean or not at all.**

### The questions to resolve (each becomes a Decision in #042)

⚠ **Q1 is the pass's real work; the rest are smaller.** Full argument, levers and sizing
are in **[roadmap.md](roadmap.md)'s §3.20 bullet** — do not re-derive them here, but do
not skip them either.

1. **⚠ THE CORE PROBLEM — 2P%, FTA (free-throw attempts) and points are
   OVER-DETERMINED. Which target yields?** ⚠ **These are THE two rows under review —
   two-point shooting and free throws** — and they are **one problem in three rows, not
   three problems.** **2P% is ~6.3 low** (~48.7 vs a sourced 55.0) and **FTA ~3.7 low**
   (19.76 vs 23.5); both must go **UP**. But the 2P% fix is worth **~+7 points** and the
   FTA fix **~+2.8**, against a points gap of only **5.6** — **combined ~+9.8,
   overshooting points to ~120 vs 115.6.** They cannot all be hit independently.
   **Deciding which one yields, and by how much, is this pass.**

   > ⚠ **STOP — Q1's ANSWER IS THE USER'S CALL, NOT THE SESSION'S.** Which target yields
   > is a **product** question — what the simulated game should feel like — and the
   > numbers cannot settle it: all three rows are sourced, so arithmetic says only that
   > they conflict, never which one matters least. **Present the trade-off and ASK; do
   > not resolve it alone.** Precedent: #027's turnover taxonomy and STOLEN share were
   > both user calls for the same reason, and `project-docs` states the rule — *if a
   > design pass surfaces a choice that is genuinely the user's, surface it, don't guess.*
   > **What to bring them**: the three candidate landings (points on target with 2P%
   > short · 2P% on target with points over · a split), each with its cost stated in the
   > row that gives ground. **Q2–Q7 you can resolve yourself** within the answer they pick.
2. **Where does the recovery come from?** ⚠ **From MAKING more shots, not TAKING more** —
   **FGA is the one row already too high** (92.28 vs 89.1), so a pace bump is the
   obvious-looking lever and the wrong one. ⚠ **`base-three` must NOT move** (3P% is
   correct at 35.8 vs 36.0 and held across a 1.9× volume change, #040 F); the 2P% lever is
   `base-drive` / `base-post` / `base-perimeter`.
3. **FTA's mechanism** — does `sim.non-shooting-foul-share` move, the foul rate, or both?
   ⚠ **The share is priced by the PENALTY RATE, not the foul rate alone** (46.1%, down
   from 55.7%), and **moving the foul rate stales the flagrant divisor**.
4. **Def rebounds** — 30.48 vs 32.4. A residual **rate** question (`sim.base-offensive-
   rebound` and the paths that divert misses from the rebound draw), **not a new
   sub-phase** (#040 H).
5. **Steals** — 7.72 vs 8.4, but ⚠ **DERIVED**: steals = turnovers × STOLEN share.
   **Fixing turnovers to 14.5 alone yields ~8.05.** Re-measure after turnovers land; **do
   not tune the share independently** (#041 F).
6. **Sourcing** — which still-unsourced rows get a real source, and which are declared
   `ballpark`/`observed` on purpose: foul-outs (⚠ its ~0.39 is **circular**, promoted from
   a prior landing), technicals, flagrants, the minutes distribution, the real
   shooting-foul share.
7. **A stop condition per row** — given ±1.5-point noise, what reading counts as "landed"
   and stops the tuning? **Decide this BEFORE tuning**, or the pass has no exit.

### ⚠ Frozen — do NOT reopen (each already argued and closed)

- **§3.13's foul-trouble sit curve** — measured **saturated** (#031).
- **The turnover count, gate and cause weights** — frozen (#027 A).
- **#039 C's dead-possession concession** — asked and answered **no** (#040 E).
- **`PROB_FLOOR` / `base-block-three`** — **closed, not deferred**: sized at ~half a
  blocked three per team-game on a row already on target. **A footnote**: only if this
  pass tunes `base-block-*`, reroute through `clampRareProbability` first or that lever
  reads dead.
- ⚠ **If the pass finds itself adding a BRANCH, it has grown beyond recalibration.**
  §3.20 is the last Phase-3 sub-phase and **adds no mechanic** (#038).

### Exit condition

**Not "every row green".** It is that **every `calibration.md` row is either a `TARGET`
with a named source and season, or deliberately `observed` / `ballpark`.**

### Read these first, in this order

1. **[roadmap.md](roadmap.md)'s §3.20 bullet** — the full argument behind Q1–Q7, the
   levers, the sizing, the frozen decisions. **The questions above are an index over it.**
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
