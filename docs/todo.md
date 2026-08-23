# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section.

Current focus: **§3.20 — recalibration**, which is **EXECUTE-READY — design resolved as
[decisions.md](decisions.md) #042**.
Phase 3's tail is **§3.18 steals (SHIPPED) → §3.19 instrumentation (SHIPPED) → §3.20
recalibration (execute-ready)**.
⚠ **RECALIBRATION HAS BEEN RENUMBERED A FOURTH TIME** — it was §3.16, then §3.18, then
§3.19, and is now **§3.20**. **Read the phase NAME, never the number alone.**
§3.7–§3.19 have all shipped.

> **✅ §3.20's DESIGN PASS IS DONE — the plan below is execute-ready
> ([decisions.md](decisions.md) #042, Decisions A–J).** ⚠ **It is an ITERATIVE TUNING
> pass, not a patch**: every constant in it is a *starting point*, because #042 F measured
> a **−3.71-point wedge** between the weighted 2P base and realized 2P%. **Set → measure →
> adjust → re-measure**, and stop on #042 G's bands, which are in the plan.
>
> **What the design pass found, and what it changes about the inherited framing:**
> - ⚠ **THE OVER-DETERMINATION WAS AN ARTIFACT.** The "2P% +7 and FTA +2.8 against a
>   5.6-point gap" conflict held **FGA and FT% fixed**, and neither should be. With all
>   four levers counted, **every sourced row lands at once** (modelled: points 115.8, FG%
>   47.05, FTA 23.5, FGA 89.5, 3PA 37.0, 2P% 55.0). **The user took the full landing.**
> - **FTA and FGA are ONE lever** (#042 B) — `non-shooting-foul-share` raises FTA and
>   lowers FGA in the same motion, so **pace is not touched** (and could not help: the
>   knob is an integer).
> - 🆕 **FT% was running at 82.56% against a real ~78%, and is ABSENT from
>   `calibration.md`** — an unmeasured row donating ~1.1 points/team/game. **It becomes a
>   sourced TARGET** (#042 C, user call).
> - ⚠ **Def rebounds get WORSE before better** (#042 H): fixing 2P% removes ~4.9
>   misses/team/game, widening −1.9 to −4.8. **Predicted, not a regression.**
> - **Steals are DERIVED and are not tuned** (#042 I) — the identity reproduces to 0.01.
>
> **Step 0's measurement reproduced `calibration.md` exactly, row for row**, with
> `Profiles: local,baseline` and all three reconciliation lines `OK` on all five seeds.
> There was no Step-0 finding. That baseline is the plan's first table.

---

## §3.20 execution plan (decisions.md #042 — resolved)

**Build preamble.** Java 21 or Lombok breaks:
`JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`. Invoke `project-docs` before
touching any doc. ⚠ **This phase changes only VALUES in
`application-baseline.properties`** — no new tunable, no Java change, no schema change,
no new branch or event. The `test-coverage` skill is therefore **not** in play (no new
production code), but `mvn clean install` must still be green.

⚠ **THIS IS AN ITERATIVE TUNING PASS, NOT A PATCH.** #042 F: **the base is not the
landing.** Every constant below is a *starting point for iteration one*, not an answer —
a −3.71-point wedge sits between the weighted 2P base and realized 2P%, and the
offensive-rebound contest runs ~10 points hot against its base. **Set → measure →
adjust → re-measure.** #042 G says when to stop.

**The measurement loop, every iteration** (this is the whole job):
```bash
cd gametime-service && for s in 1000 2000 3000 4000 5000; do SPRING_PROFILES_ACTIVE=local,baseline JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -q -pl gametime-app test -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=$s -DfailIfNoTests=false; done
```
⚠ **Confirm `Profiles: local,baseline` and all THREE reconciliation lines (`ast+blk`,
`ft-src`, `points`) read `OK` on every run before reading a number.** A `MISMATCH`
invalidates the rows it feeds.

### ⚠ ONE STEP AT A TIME — CHANGE ONE CONSTANT GROUP, THEN RE-MEASURE

**Do NOT batch the steps below.** Change one step's constants, run the 5-seed loop, read
the result, *then* move on. This is a hard rule, not a preference, for three reasons:

- **The steps move each other's DENOMINATOR.** Step 1 takes FGA 92.28 → ~89.5, and FGA is
  what 2P%, 3PA and the rebound pool are all measured *per attempt* against. Tune Step 2
  against the old FGA and its arithmetic is stale before you finish it. **This is why
  Step 1 runs FIRST** — it settles the denominator everything downstream reads.
- **A batched change cannot be ATTRIBUTED.** If four constants move and three rows land
  wrong, nothing tells you which lever did it. Every historic trap in these docs — the
  broken instrument, the corrected instrument that looked like a doubling, `PROB_FLOOR`
  holding a rate — was found by isolating one change.
- **The starting values are MODELLED, not measured** (#042 F). They are extrapolations
  from measured per-event rates. Where a response curve bends, one isolated step tells you
  immediately; four at once do not.

⚠ **Expect a step to land its OWN rows and leave others visibly wrong.** That is the plan
working. The clearest case: **after Step 1 alone, points barely move and may DIP.** The
FTA gain is worth ~+2.6 points, but converting live shots into stopped shots removes real
attempts. **Points stay near 110 until Step 2's 2P% lift lands — do not "fix" it in
Step 1.**

**§3.20's baseline (Step 0 of the design pass, 5-seed mean, seeds 1000–5000).** All three
identities OK on all five seeds; it reproduced `calibration.md` exactly.

| Points | FG% | 2P% | 3P% | FGA | 3PA | FTA | **FT%** | DefReb | OffReb | TO | Steals | Fouls |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 110.00 | 43.52 | 48.77 | 35.76 | 92.28 | 37.22 | 19.76 | **82.56** | 30.48 | 11.18 | 13.90 | 7.72 | 18.18 |

**The stop condition — fixed BEFORE tuning (#042 G).** ±2 sem of the 5-seed mean. **A row
inside its band is LANDED; stop tuning it** even if the point estimate is not the target.

| Points | FG% / 2P% | 3P% | FGA | 3PA | FTA | DefReb | OffReb | TO | Fouls |
|---|---|---|---|---|---|---|---|---|---|
| **±0.9** | **±0.5** | **±0.25** | **±0.45** | **±0.45** | **±0.5** | **±0.4** | **±0.3** | **±0.3** | **±0.2** |

⚠ **Technicals, flagrants and foul-outs are NOT tuning objectives** — watch them for
breakage only.

---

**Step 1 — FTA and FGA together, via `sim.non-shooting-foul-share` (#042 B)**
**⚠ RUN THIS STEP FIRST AND ALONE.** It moves FGA, the denominator every later step is
measured against.
- [ ] `sim.non-shooting-foul-share` **0.50 → 0.28** (starting point). **One line in
      `application-baseline.properties`; nothing else changes in this step.**
- [ ] ⚠ **It does NOT change the foul COUNT.** It is a second roll on a foul already
      rolled and charged (#039 A) — which is why foul-outs, `foulTroubleLevel()` and the
      bonus tally survive it untouched. **What it changes is what each foul BECOMES.**
- [ ] ⚠ **ONE lever, FOUR rows moving at once** — read all four, not just the headline:
      **FTA** (headline) **up** 19.76 → **23.5** · **FGA down** 92.28 → **~89.5** (a
      stopped shot charges no FGA) · FTA **SHOOTING** source up 12.97 → ~18.2 · FTA
      **BONUS** source **down** 4.61 → ~2.6.
- [ ] ⚠ **The two FT SOURCES move in OPPOSITE directions**, so net FTA rises by *less*
      than the SHOOTING source alone. **A weak-looking headline is not a weak lever** —
      check the split before adjusting the constant.
- [ ] Target: **FTA 23.5 ±0.5**. Watch **FGA** fall toward 89.1 as a by-product.
- [ ] ⚠ **Expect points to stay near 110, possibly DIPPING.** The +2.6 points of FTA are
      offset by live attempts becoming stopped shots. **Points are Step 2's job — do not
      chase them here.**
- [ ] ⚠ **0.28 is MODELLED, and the bonus term is the shakier half of the model** — the
      penalty rate (46.1%) sits right at the 5-foul threshold, which calibration.md flags
      as volatile. **If the run says 0.32 or 0.24 lands FTA, take the measurement.** The
      knob is **DERIVED, not sourced** — there is no principled value to defend.
- [ ] ⚠ **Confirm `ft-src` still reconciles `OK`.** That identity is exactly what catches
      a mis-tagged FT source after a re-partition like this one.
- [ ] ⚠ **Do NOT touch `sim.default-possessions-per-period`.** It is an **integer** (25);
      the smallest step is −4%, which overshoots the −3.5% FGA gap and lands 88.6.
- [ ] ⚠ Tune against the **FTA line, never against points** (calibration.md's standing rule).

**Step 2 — 2P% via `base-drive` / `base-post` / `base-perimeter` (#042 D)**
- [ ] Starting split, deliberately **UNEQUAL** — weight the lift to the rim:
      `sim.base-drive` **0.5975 → 0.6875** (+0.09) ·
      `sim.base-post` **0.4975 → 0.5675** (+0.07) ·
      `sim.base-perimeter` **0.4375 → 0.4575** (+0.02).
- [ ] ⚠ **`sim.base-three` must NOT move** (#040 F) — 3P% is 35.76 vs a sourced 36.0 and
      held across a 1.9× volume change.
- [ ] ⚠ **Aim the bases ABOVE the target row** (#042 F): the weighted 2P base is 52.48%
      but realized 2P% is 48.77%. Blocked twos alone are ~4.41% of 2PA.
- [ ] Target: **2P% 55.0 ±0.5**, **FG% 47.1 ±0.5**. 2P% is derived, not a harness row —
      compute it as `(FGA×FG% − 3PA×3P%) / (FGA − 3PA)`.
- [ ] ⚠ **Rejected, do not revisit**: a *uniform* bump across the three bases (right in
      aggregate, wrong in composition — the exact error §3.17 spent a pass un-hiding).
- [ ] ⚠ **STOP HERE AND RE-MEASURE** before Step 3. This step changes the miss pool that
      Step 5 reads, and FG%/2P% are the rows most likely to need a second iteration.

**Step 3 — hold 3PA at 37.0 via `sim.shot-share-three` (#042 E)**
- [ ] Step 1 lowers FGA, which drags 3PA to ~36.1 with the share table fixed. Bump
      `sim.shot-share-three` **1.23 → ~1.28** (three share 40.3% → ~41.3%).
- [ ] Target: **3PA 37.0 ±0.45**. Verified free — points and FG% barely move, because a
      three at 36% and a two at 55% are worth ~1.08 vs ~1.10 points.
- [ ] ⚠ Only `shot-share-three` moves; the other three `sim.shot-share-*` stay (#040 C).
- [ ] ⚠ **STOP HERE AND RE-MEASURE** before Step 4.

**Step 4 — FT% becomes a sourced target, via `sim.ft-base` (#042 C)**
- [ ] Realized FT% is **82.56%** against a real ~78% — **a row absent from
      `calibration.md` entirely**, donating ~1.1 points/team/game.
- [ ] Lower `sim.ft-base` **0.75 → ~0.705** (starting point) and measure.
- [ ] ⚠ **The base is not the landing**: realized FT% is
      `ftBase + 0.20 × (freeThrows − 10)/10`, and the roster's mean `freeThrows` ≈ 13.8.
- [ ] Target: **FT% 78.0**. Derive it from the FT-source lines
      (Σ pts ÷ Σ FTA), which is how the design pass measured it.
- [ ] ⚠ **STOP HERE AND RE-MEASURE** before Step 5. ⚠ **This is the step that moves
      POINTS most predictably** — it is pure scoring efficiency with no attempt-side
      side-effect. Check points against its ±0.9 band here.

**Step 5 — def rebounds via `sim.base-offensive-rebound` (#042 H)**
- [ ] ⚠ **RUN THIS AFTER STEPS 1–2 HAVE LANDED, AND EXPECT THE GAP TO HAVE WIDENED.**
      Fixing 2P% removes ~4.9 misses/team/game, so def rebounds fall to ~27.6 first —
      **the −1.9 gap becomes −4.8. That is predicted, not a regression.**
- [ ] `sim.base-offensive-rebound` **0.27 → ~0.19** (starting point). ⚠ The **realized**
      share is 0.378 against a base of 0.27 — the contest runs hot, so the base must come
      down **more** than the naive delta.
- [ ] Target: **DefReb 32.4 ±0.4**.
- [ ] ⚠ **Off rebounds are the constraint and will NOT also be exact** — one knob, two
      rows. At the realized share that lands DefReb, OffReb models to ~12.0 vs a target of
      11.3. **Tune until DefReb enters its band, then accept OffReb and RECORD the
      residual** in the implementation note.
- [ ] ⚠ **STOP HERE AND RE-MEASURE** before Step 6.

**Step 6 — turnovers via `sim.base-turnover`; steals are DERIVED (#042 I)**
- [ ] `sim.base-turnover` **0.038 → ~0.0396**. Target: **TO 14.5 ±0.3**.
- [ ] ⚠ **Do NOT tune steals.** steals = TO × STOLEN share; the identity reproduces to
      0.01 (13.90 × 0.555 = 7.71 vs a measured 7.72). At TO 14.5 steals ≈ **8.05**.
- [ ] ⚠ **The nine `sim.to-weight-*` cause weights are FROZEN** (#027 A) and the STOLEN
      share is **not** touched (#041 F). **Re-measure steals and REPORT it; do not chase
      8.4.**
- [ ] ⚠ **STOP HERE AND RE-MEASURE.** ⚠ Raising turnovers removes possessions that would
      have ended in a shot, so **FGA falls again here** — re-check FGA against its band
      before declaring Step 1 done.

**Step 7 — re-measure the flagrant divisor (#034 G, #042 follow-up)**
- [ ] ⚠ **The flagrant rate's divisor is an EMERGENT measured foul rate.** Steps 1–2 move
      the foul mix, so **re-measure it or the flagrants row silently reads low — and
      NOTHING FAILS.** It has run at 74% of target before, for exactly this reason.
- [ ] Confirm `PERSONAL_FOULS_PER_TEAM_GAME` still matches the measured foul rate; update
      it if not.

**Step 8 — the docs, in the same change**
- [ ] **`calibration.md`**: update every `Current` value to the final 5-seed mean; **add
      the new FT% row as `TARGET (SOURCED)`** with its named source and season; **demote
      foul-outs from `TARGET` to `ballpark`** (#042 J — its ~0.39 is circular). Keep
      `non-shooting-foul-share`'s "DERIVED, not sourced" note **and its new value**.
- [ ] ⚠ **Update the `CalibrationHarness` `(target ~N)` strings in the SAME change** —
      calibration.md's standing rule. Add the FT% line to the harness report.
- [ ] **`decisions.md #042`**: add the implementation note — final constants, the landing,
      the OffReb residual (Step 5), and any divergence from the modelled landing.
- [ ] **`roadmap.md`**: flip the §3.20 bullet to `[x]` with a landing note.
- [ ] Park the **roster FT-skill distribution** question in `ideas.md` (#042 follow-up) —
      it is player-generation, not sim-config.

---

**Definition of done**
- `mvn clean install` green, **and** the sim classes green run **alone** (cheap; CLAUDE.md).
- A clean **5-seed** run, profile from the **environment**, `Profiles:` line confirmed and
  all **three** reconciliation lines `OK`.
- **Every row in #042 G's table inside its band, or the residual explicitly recorded** in
  the implementation note with the reason it was accepted.
- **The exit condition (#042 J)**: every `calibration.md` row is either a `TARGET` with a
  named source and season, or deliberately `observed` / `ballpark`. **Not "every row
  green".**
- **Each step was measured on its own 5-seed run** before the next began, and the
  intermediate readings are recorded in #042's implementation note — they are what makes
  a later "which lever did that?" answerable.
- ⚠ **Do NOT commit** — leave the work in the tree and wait (CLAUDE.md).

**⚠ Do NOT**
- **Do NOT batch the steps.** One step's constants, then the 5-seed loop, then read it.
  The steps move each other's denominator (Step 1 → FGA) and a batched change **cannot be
  attributed** when a row lands wrong.
- **Do NOT add a mechanic.** If a row seems to need a new branch, event or tunable, the
  pass has **grown beyond recalibration** (#038) — stop and raise it. §3.20 adds none, and
  the tunable count stays **62 / 27**.
- **Do NOT move `sim.base-three`** (#040 F) or the **`sim.to-weight-*`** weights (#027 A).
- **Do NOT re-tune §3.13's foul-trouble sit curve** — measured **saturated** (#031).
- **Do NOT reopen #039 C's dead-possession concession** — asked and answered no (#040 E).
- **Do NOT touch `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`'s baseline value** (#034 B).
- **Do NOT tune `sim.base-block-*`.** `PROB_FLOOR` (0.02) is 4× `base-block-three` (0.005),
  so that lever reads **dead** — **closed, not deferred** (sized at ~half a blocked three
  on a row already on target). If a later pass ever does, reroute through
  `clampRareProbability` **first**.
- **Do NOT read a moved number as an engine change without asking which** — *did the
  engine change, did the MEASUREMENT change, or is a clamp holding it?* **Compare
  raw-to-raw across an instrument change.**
- **Do NOT judge on one seed.** Per-seed noise is ±1.5 points; the bands above are for the
  **5-seed mean** and mean nothing on a single run.

**Open at execution**
- The **exact final value of all eight constants**. #042 B–E give starting points, #042 F
  says they will not be right first time, #042 G says when to stop.
- ✅ **Iteration order is now FIXED, not open**: Steps 1→6 in order, one at a time, with a
  5-seed run between each (see the cadence rule in the preamble). **Step 1 runs first
  because it settles FGA**, the denominator everything downstream is measured against.
  What remains open is **how many iterations each step needs** — #042 F says the modelled
  starting values will not be right first time.
- **Whether OffReb's residual (Step 5) is acceptable** or wants a follow-up filed.

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

- **The phase sequence and this phase's bullet** → [roadmap.md](roadmap.md). ⚠ **Its
  §3.20 bullet's "over-determination" framing is SUPERSEDED by #042 A** — the conflict was
  an artifact of holding FGA and FT% fixed. The bullet is left as written (it is the
  pre-design argument, and Q1–Q7 were an index over it); **#042 is authoritative.**
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
