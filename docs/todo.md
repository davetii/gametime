# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.16).

Current focus: **§3.14b — Flagrant fouls**. The full §3.7–§3.16 sequence and what
follows (Phase 4) live in **roadmap.md's "Possession-fidelity completion"
section** — not here (todo.md is current-phase-only). §3.7–§3.13 and **§3.14a**
have all shipped.

> **✅ §3.14b IS EXECUTE-READY — the design is resolved as `decisions.md` #034.**
> The design pass ran 2026-08 and produced #034 (Decisions A–J) plus the plan
> below plus the three new `possession-flow.puml` branches. **This session BUILDS
> it.** Do not re-litigate A–J; the small calls left open are listed under
> "Open at execution" at the end of the plan.
>
> **§3.14b is the hard half of the §3.14 split** (#032 A), and the design pass
> found that only ONE of its two predicted hard problems is real:
>
> - **The possession-retention fork IS real** — a flagrant awards free throws
>   **and** returns the ball, breaking #030 B's invariant that every FT path ends
>   the possession. **But it is FIVE LINES**, not a new mechanism: the
>   second-chance `while (true)` loop already is the "same team, run it again"
>   machine, so retention is the same `offensiveRebounds++; continue;` that §3.7,
>   §3.8 and §3.10 already use, under the same cap (#034 B).
> - **The #023 F stored-state exception is NOT real, for the second time.** #032 F
>   predicted a flagrant-2 would force it ("a severity grade with no counter
>   behind it"). **It doesn't**: a flagrant-2 ejects *immediately*, so there is no
>   threshold to remember and `flagrantTwos >= 1` is a monotonic counter exactly
>   like `fouls >= 6` and `technicalFouls >= 2` (#034 F). **No stored flag, no
>   `#028 D`-style column argument, no exception. The prediction is RETIRED.**
>
> Numbering stays `a`/`b`: **§3.15 (profiles) and §3.16 (recalibration) do NOT
> renumber**, because §3.16 is named in the shipped, never-retro-edited text of
> #030 and #031 (#032 A).

---

## §3.14a close-out (SHIPPED 2026-08 — kept only as the handoff §3.14b needs)

Landed as `decisions.md` **#032 A–J** + its implementation note; roadmap bullet is
`[x]`. What §3.14b needs to know:

- **The ejection seam it builds on.** `RotationState.eligible(...)` now filters
  through a shared private predicate **`isDisqualified(p)` = `isFouledOut() ||
  isEjected()`**, used by `eligible(...)`, `replaceFouledOut()` **and**
  `mostFoulTroubledCandidate()`. §3.14b's flagrant-2 ejection extends **that
  predicate**, not a fourth removal path (#031 H). The three-tier structure
  (hard/forced · soft/preference · fatigue) is intact.
- **`PlayerGameState.isEjected()` is DERIVED** — `technicalFouls >= TECHNICAL_EJECTION_LIMIT`,
  no stored flag. **A flagrant-2 cannot copy this shape**: there is no counter to
  derive from. That is question 2 below, and it is the real one.
- **The counter split (#032 E) does NOT apply to flagrants.** `technicalFouls` is
  separate from `fouls` because a technical does not count toward the six-foul
  limit. **A flagrant DOES count** — so it uses `recordFoul()`, the ordinary path,
  and feeds `isFouledOut()` / `foulTroubleLevel()` / the penalty tally normally.
- **`GameData.isInBonus` now has an outcome-aware exclusion** and a
  `countsTowardBonus(e)` helper. `TECHNICAL_FOUL` is the only excluded outcome.
  **A flagrant counts**, so §3.14b adds nothing here — but see the doubled-tally
  warning below.
- **⚠ THE BONUS EXCLUSION EXISTS IN TWO PLACES.** `GameData.isInBonus` and
  `CalibrationHarness`'s **independent** per-team-period tally are two separate
  derivations over the same events. A foul type that does not count must be
  excluded in **both**, or the instrument silently disagrees with the engine.
  (Found during §3.14a execution.) A flagrant counts, so §3.14b touches neither —
  but do not let that be an accident.
- **`advancePossession(rng)` now RETURNS the technical committer** (or null) and
  consumes **three** unconditional draws (foul-trouble roll, technical roll,
  committer draw). The event + FT are emitted by `PossessionEngine.simulate()`,
  which owns `GameData`/team ids/period/sequence. **§3.14b's roll does NOT go
  here** — a flagrant rides an existing foul inside the possession flow.
- **`FreeThrowSource.TECHNICAL` was added** (a divergence from #032's Status
  block — see the implementation note). §3.14b's FT source decision is therefore
  unconstrained by a "no new source" rule; decide it on the merits.
- **The clamp-helper consolidation is DONE** — `SimConfig.clampRareProbability`
  owns all four floor-free sites. A fifth costs one call.
- **`technicalFouls` IS surfaced on the box score (#033)** — column + entity +
  OpenAPI, on a **parity** argument (twelfth of twelve accumulators). **§3.14b
  needs no new stat column**: a flagrant is a personal foul and already feeds the
  existing `fouls`. But if §3.14b stores a flagrant-2 severity flag (question 2),
  #033's parity argument does **not** cover it — that is stored *state*, not a
  stat, and needs its own justification.
- **§3.4 aggregates unmoved** (5-seed mean): 117.5 pts / 46.6% FG / 37.0% 3P /
  26.8 ast / 13.5 TO, penalty rate **51.2%**, foul-outs **0.409**, technicals
  **0.367**, ejections **0.014**.

---

## §3.14b execution plan (decisions.md #034 — resolved)

**Build preamble.** Always `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
(Homebrew Maven defaults to JDK 25, which breaks Lombok). **The JaCoCo gate is
per-package and runs at `install`, not `test`** — a green `mvn test` does not prove it
passes; invoke the **`test-coverage`** skill when adding production code. The `sim`
package is currently at **99.2% line**; keep the margin, don't scrape the 80% floor.

**✅ Verified baseline before you start** *(re-run at the §3.14b design pass, 2026-08, on
commit `c76c4d2`)*: `mvn test -pl gametime-app` is **GREEN at 513 tests, 0 failures**, and
the `JAVA_HOME` path above exists. **So any red you see is yours** — do not inherit-debug.
The largest test file is `PossessionEngineTest` (**82 tests**), which is also the one
Step 8's re-baselining touches.

**What #034 resolved, in one paragraph** (read the entry itself before starting): a
flagrant is an **additional severity roll layered on top of a foul that already
happened**, at **all three** existing foul sites, changing no existing foul rate (A).
It awards a **flat 2 FTs that REPLACE the underlying award** (C), shot by **the player
who was fouled** (D). A **defensive** flagrant returns the ball via the existing
second-chance `continue` under the existing cap (B); an **offensive** one (only
possible at the rebounding site) flips the possession (C). A flat **15%** of flagrants
are **flagrant-2** and eject the committer immediately (E), via a **derived**
predicate over a monotonic counter — **no stored state** (F). A flagrant **is a
personal foul**: `recordFoul()`, six-foul limit, foul-trouble curve, and the period
bonus tally, all unchanged (I).

---

**Step 1 — `SimConfig`: the two constants + the derivation helper (#034 G)**
- [ ] Add `FLAGRANT_FOULS_PER_TEAM_GAME` (≈ **0.16**) with the #032 B2-style comment:
      why the **game-level** figure is stored rather than the per-foul probability
      (~0.0084 is uninterpretable and not comparable to calibration.md).
- [ ] Add `FLAGRANT_TWO_SHARE = 0.15` — the flat conditional severity share (E). Note
      in the comment that it is **NOT a clamped probability** and needs no clamp.
- [ ] Add `flagrantFoulProbability()` deriving the per-foul probability by dividing by
      the **personal-foul rate**. **Route it through `clampRareProbability`** — #032 H's
      **fifth** site, one call, never a fifth hand-rolled copy.
      **The divisor value: ~19.0 personal fouls/team/game**, which is §3.14a's measured
      **19.4 minus its 0.367 technicals** (the harness's `Fouls / team / game` line tallies
      *all* `FOUL` events — see the caveat in Verified facts). `0.16 / 19.0 ≈ **0.0084**`.
      ⚠ **The divisor is a NAMED CONSTANT, not a magic number** — it is an *assumption
      about the engine's current behavior*, so it must be greppable when §3.16 invalidates
      it. This is exactly what G's emergent-coupling warning is about.
- [ ] ⚠ **Document the emergent-divisor coupling in the javadoc (G's honest cost):**
      unlike §3.14a's nominal, config-derived divisor, this one is a **measured**
      quantity, so **§3.16 (or any pass that moves the foul rate) moves flagrants too**
      without touching the constant. Directionally correct, but it must be *stated*.
- [ ] Note the floor margin explicitly: `PROB_FLOOR` (0.02) is **~2.4×** the per-foul
      rate — decisive, but **thinner than §3.14a's >10×**, so the floor-free choice is
      argued rather than assumed.

**Step 2 — `PlayerGameState`: the flagrant-2 counter + derived predicate (#034 F/I)**
- [ ] Add a `flagrantTwos` counter + `recordFlagrantTwo()`.
- [ ] Add `isEjectedForFlagrant()` = `flagrantTwos >= 1` — a **derived predicate**, no
      stored flag, in the `isFouledOut()` / `isEjected()` mould.
- [ ] ⚠ **Javadoc the finding, because it reverses three shipped documents.** #031 H,
      roadmap.md and #032 F all predicted a flagrant-2 forces stored state. It does not:
      an immediate ejection has **no accumulation to remember**, so the fact is
      absorbing and monotonic — the exact shape #023 F's derivation was built for.
      Contrast with `inFoulTrouble` (#031 E), which was refused because its underlying
      fact is **non-monotonic** (sit → recover → return → sit again).
- [ ] ⚠ **`flagrantTwos` is NOT a parallel foul counter** (I). The flagrant itself is
      already in `fouls` via `recordFoul()`; this counts **ejection causes**. Say so in
      the javadoc — summing them double-counts, the #033 D trap in reverse.

**Step 3 — `RotationState`: the third disqualification cause (#034 F, #031 H)**
- [ ] Extend `isDisqualified(p)` to `isFouledOut() || isEjected() || isEjectedForFlagrant()`.
      **That is the whole change** — one line, behind the single `eligible(...)` filter.
- [ ] ⚠ **Do NOT add a fourth removal path** (#031 H, held for the third pass running).
- [ ] Note in the javadoc that this tier is finally *exercised*: flagrant ejections land
      ~**0.024**/team/game against §3.14a's measured 0.014.

**Step 4 — `FreeThrowSource.FLAGRANT` + the two outcome strings (#034, Status block)**
- [ ] Add `FLAGRANT("FLAGRANT")`, with §3.14a's argument restated: the harness reads FT
      source off the outcome suffix (#029 D), so reusing `SHOOTING` would **silently
      inflate a real source's share on the very line §3.14b is judged by** — and would be
      factually wrong at the rebounding site (not a shooting foul at all).
- [ ] Add `FLAGRANT_FOUL_1_OUTCOME` / `FLAGRANT_FOUL_2_OUTCOME` — **on `GameData`**,
      beside `TECHNICAL_FOUL_OUTCOME`, only if `GameData` must recognise them. **It must
      not** (a flagrant counts toward the bonus, so `countsTowardBonus` needs no entry),
      so **prefer `PossessionEngine`/`FoulResolver`** — the class that emits them. Note
      the deliberate contrast with `TECHNICAL_FOUL_OUTCOME`'s placement and why.
- [ ] Non-collision check (#027 D): `FLAGRANT_FOUL_1` / `_2` against the §3.8/§3.9/§3.10
      vocabularies and §3.14a's `TECHNICAL_FOUL`.

**Step 5 — `FoulResolver`: the shared severity roll (#034 A/E)**
- [ ] Add **one** `isFlagrant(rng)` (rate from Step 1) and **one** severity call
      (`isFlagrantTwo(rng)` on `FLAGRANT_TWO_SHARE`). **One definition, three callers —
      never three copies** (the open-at-execution constraint).
- [ ] ⚠ **No skill inputs at all.** Unlike every other roll in this class, neither takes
      a `PlayerGameState`: the engine cannot distinguish excessive from ordinary contact,
      and **`foulProne` has already had its say** in who was selected as committer (E).
      Javadoc it, because the symmetry with #032 C's weighted draw is tempting and wrong.
- [ ] **Do NOT touch `isFoul` / `isAndOne` / `resolveReboundFoul`** — their rates,
      inputs and RNG draws are unchanged (A). This is layered *on top*, not carved *out*.

**Step 6 — `PossessionEngine`: the three call sites + the retention fork (#034 B/C/D)**

*Site 1 — the stopped-shot foul (always defensive → always retains):*
- [ ] After the existing `isFoul` branch charges the defender and emits, roll `isFlagrant`.
      On a hit: emit `FLAGRANT_FOUL_1`/`_2` **instead of** `SHOOTING_FOUL`, award
      `awardFreeThrows(..., count = 2, FreeThrowSource.FLAGRANT)` with `shooter` as the
      FT shooter, then `offensiveRebounds++; continue;` if the cap allows.
- [ ] ⚠ **2 FTs REPLACE `shotType.freeThrowsIfFouled()`, they do not add to it** (C).
      A flagrant stopped THREE awards **2, not 3, and not 5**. Correct by rule and
      counter-intuitive — it is the single likeliest bug in this pass.
- [ ] ⚠ **`capReached` IS NOT IN SCOPE HERE — you must compute it** *(verified against
      the code at design time; see the scope note below)*. The existing `boolean
      capReached` locals are declared at the **block-recovery** branch and again at the
      **rebound phase**, both *after* this site. Compute it inline from
      `offensiveRebounds >= SimConfig.MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`, or hoist
      one declaration to the top of the loop body and let all sites read it — **hoisting
      is preferred** (one definition, and it removes the existing duplicate pair), but it
      touches shipped §3.7/§3.8 lines, so keep it a pure move with no behavior change.

*Site 2 — the and-1 (always defensive → always retains):*
- [ ] Inside the existing `isAndOne` branch, roll `isFlagrant`. On a hit the basket has
      **already** been recorded (points, FGM, assist) — do **not** re-score it — then
      2 FTs to the `shooter` and `offensiveRebounds++; continue;` if the cap allows.
- [ ] ⚠ **`awardAndOne(...)` is a SEPARATE METHOD that returns only a sequence, so the
      fork CANNOT live inside it** *(verified against the code)*. Roll the flagrant at
      the **call site** in `resolvePossession` — where `offensiveRebounds` and the
      `continue` actually are — and leave `awardAndOne` for the ordinary path. Do **not**
      widen its signature to return a retention flag; that would put a possession fork
      inside a method whose javadoc correctly states it never forks one.
- [ ] ⚠ **`capReached` is not in scope here either** — same fix as site 1.
- [ ] ⚠ **This is the case that most visibly breaks #030 B**: made FG **+** 2 FTs **+**
      the ball back — three scoring channels on one possession, which no path has ever
      produced. **#029 B's "the and-1 never forks the possession" is superseded for the
      FLAGRANT case only**; the ordinary and-1 is untouched. Comment it, or a future
      reader will "fix" it.
- [ ] ⚠ 2 FTs, **not** `AND_ONE_FREE_THROWS` (1). Replaces, doesn't add.

*Site 3 — the rebounding foul (the ONLY two-sided site):*
- [ ] Inside `resolveReboundFoul`, after the committer is charged and the event emitted,
      roll `isFlagrant`. On a hit, **the bonus is NOT consulted** (2 FTs by rule, in the
      penalty or not — the #029 B shape).
- [ ] **Defense committed (~78%)** → the fouled **offensive** player shoots (via the
      existing `pickFreeThrowShooter` `foulDrawing`-weighted draw over the offense, #028 B)
      → `ReboundFoulResult(sequence, offenseRetains = !capReached)`.
- [ ] **Offense committed (~22%)** → the fouled **defensive** player shoots (same draw
      over the defense) → `ReboundFoulResult(sequence, false)` — the possession flips.
- [ ] `committingTeamId` is **load-bearing here**, not merely uniform (#028 D): the fork
      depends on which side committed.

*Shared across all three:*
- [ ] `recordFoul()` on the committer — **the ordinary path** (I). On a flagrant-2 also
      `recordFlagrantTwo()`.
- [ ] ⚠ **Do NOT copy §3.14a's counter split** (#032 E). A flagrant counts toward the
      six-foul limit, `foulTroubleLevel()`, and the period bonus tally.
- [ ] ⚠ **Do NOT reuse `pickTechnicalFreeThrowShooter`** (D). Its premise is that
      *nobody was fouled* (#032 G); on a flagrant somebody was.
- [ ] Retention always goes through `offensiveRebounds++; continue;` under the **existing**
      `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` (B). **Do NOT exempt it and do NOT raise
      the cap** (3→5 is a parked idea needing its own recalibration pass — ideas.md).
      Cap-reached degrades exactly as §3.10's does: FTs still awarded, possession ends.

**Step 7 — `CalibrationHarness`: the instrument (#034 H)**
- [ ] Add a **flagrants/team/game** line and a **flagrant-2** count. **Its own line, NOT
      shared with §3.14a's technicals** (user call): the two mechanics share nothing
      (#032 A) and cannot even be judged at the same confidence.
- [ ] Flagrant ejections join the **existing** ejection line (an ejection is an ejection;
      the cause is recoverable from the event log).
- [ ] ⚠ Print `(ballpark ~0.16 per team / ~0.25–0.40 league-wide — **5 SEEDS ONLY**)`.
      **17.4% relative sd at 102 games, 7.8% at 5 seeds — the coarsest row in
      calibration.md.** A single-seed reading is useless.
- [ ] ⚠ `Fouls / team / game` now includes technicals **and** flagrants. Update the
      existing caveat comment so §3.13's 19.0 / §3.14a's ~19.4 / §3.14b's figure stay
      comparable only after subtracting both.

**Step 8 — tests**
- [ ] **Forced-counter unit tests for the ejection** (#032 F's discipline): drive
      `recordFlagrantTwo()` directly rather than waiting for the event, and assert
      `isEjectedForFlagrant()`, `isDisqualified` exclusion from `eligible(...)`, and the
      hard-tier forced substitution.
- [ ] **The replace-don't-add FT count**, at all three sites — explicitly assert a
      flagrant stopped THREE awards **2**, and a flagrant and-1 awards **2** (the §3.12 C
      guard-test shape, which exists for exactly this class of bug).
- [ ] **The retention fork**: a defensive flagrant re-enters the loop; an offensive one
      does not; **the cap is respected** (force `offensiveRebounds` to the cap and assert
      the possession ends with the FTs still awarded).
- [ ] ⚠ **Assert the bonus tally is UNCHANGED — do not leave it to inspection** (I,
      #032's follow-up). The exclusion lives in **two independent derivations**
      (`GameData.isInBonus` and the harness's own per-team-period tally); §3.14b touches
      neither, and that non-change must be pinned by a test, since "we changed nothing"
      and "we forgot" are otherwise indistinguishable.
- [ ] Re-baseline seed-pinned expectations downstream of a foul (see the determinism note).
      **Where they actually are** *(surveyed at design time — do not go hunting)*:
      - **`PossessionEngineTest`** — the big one. It builds real seeded generators
        (`RandomGeneratorFactory.of("L64X128MixRandom").create(seed)`) at **~85 call
        sites**. Any test whose scenario reaches a **foul** consumes one extra draw from
        that point on, so its downstream assertions shift. **Tests that never reach a foul
        are unaffected** — do not blanket-re-baseline the file.
      - **`RotationStateTest`** — **should NOT need re-baselining.** §3.14b adds **no**
        draw to `advancePossession`; its draw-count test was re-baselined 1 → 3 by §3.14a
        and **stays 3**. If that test breaks, you have put the roll in the wrong place
        (#034 A: a flagrant rides a foul *inside* the possession, it is not a rotation
        event) — fix the code, not the test.
      - **`GameSimulatorIntegrationTest`** — asserts *invariants* over seeds
        (`{1, 7, 42, 99, 123}`), e.g. fouls never exceed the DQ limit, not exact values.
        These should stay green **unchanged**; a break here is a real bug, not a re-baseline.
      - `FoulResolverTest` / `ShotResolverTest` / the other resolver tests drive their unit
        directly, so they are unaffected unless you changed that unit's signature.
      ⚠ **Re-baseline by re-deriving the expected value, never by pasting in whatever the
      code now prints** — that turns a regression test into a tautology. If an assertion's
      *meaning* is unclear, leave it failing and ask rather than "fixing" it.

---

### Reconciliation invariant (unchanged, and it must stay unchanged)

`awardFreeThrows(...)` is reused **verbatim** — the **fifth** situation to share it since
#029 D — so FT/points reconciliation is automatic. Box-score FTA/FTM must continue to
reconcile exactly against `FREE_THROW` events, and personal fouls against `FOUL` events
**excluding `TECHNICAL_FOUL`** (a flagrant is included — it is a personal foul).

### Determinism note (#034, Status block)

The severity roll adds **one `nextDouble()` per FOUL** (not per possession) at each site,
and the flagrant-2 sub-roll adds a second **only on a hit**. This is a **deliberate
exception** to §3.14a's unconditional-draw discipline, and it is permissible for a
specific reason: the draw is nested *inside* an already-conditional branch (the foul), so
it **cannot fork the stream on rotation state** the way #031's would have — it forks only
on the foul's own outcome, which the stream already forked on. Every seed-pinned
expectation downstream of a foul re-baselines **once**.

### The stop condition is INVERTED (#034 J, as in #032 I)

Budget: **+0.43 pts/team/game** (FTs +0.24 gross, retention +0.19 upper bound) against a
**±1.5** per-seed noise band. **Deliberately over-estimated** — the FT channel is largely
offset (the underlying foul already awarded 2–3 FTs, so a flagrant adds 0 at the
stopped-shot site and 1 at the and-1 site), and the retention bound assumes every flagrant
is defensive and never cap-refused. **The real figure is likely nearer +0.3.**

**So: if the §3.4 aggregates move measurably at 5 seeds, that is a BUG, not a calibration
result.** The three candidates, in order of likelihood:
1. **Double-awarded free throws** (C's replace-don't-add).
2. **An uncapped retention loop** (B).
3. **A flagrant leaking out of the period bonus tally** (I).

**Two calibration cautions.** §3.14a's precedent: its +0.26 budget landed as an observed
**+0.5** — still inside the band but nearly double, so a §3.14b landing near +0.9 is
still not evidence of a bug. And #028's precedent, which is why retention is priced
separately at all: §3.10 budgeted its bonus FTs and found the **bigger** channel was
**retained possessions**, which its design had not anticipated.

### Close-out (after the build is green)

- [ ] Add the **implementation note** to #034 recording any divergence + the 5-seed landing.
- [ ] Flip the §3.14b roadmap bullet to `[x]` with a landing summary.
- [ ] Add the flagrants row to **calibration.md** (a `ballpark`, **UNSOURCED**, 5-seed
      only) *and* the harness `(target ~N)` string **in the same change**.
- [ ] Confirm `possession-flow.puml` matches what actually shipped (the three branches
      were drawn in the design pass; adjust if placement moved). Validate with
      `plantuml -checkonly docs/possession-flow.puml`. ⚠ To actually *look* at it use
      **`plantuml -DPLANTUML_LIMIT_SIZE=16384 -tpng docs/possession-flow.puml`** — the
      diagram is ~9,600px tall and a plain `-tpng` **silently truncates it at 4096px**.
- [ ] **Delete the retired clamp-trigger note** from "Where deferred work lives" (it was
      kept one phase for the handoff — #032 H). §3.14b is its fifth site.
- [ ] **Do NOT commit** — leave the work in the tree and wait (CLAUDE.md's git rule).

### Open at execution (small, constrained — #034's Status block)

- The exact `FLAGRANT_FOULS_PER_TEAM_GAME` value — **constrained**: back-solved from the
  ~0.25–0.40 league figure, confirmed against Step 7's line at **5 seeds**.
- Whether the three sites share one `FoulResolver.isFlagrant(...)` or the roll is taken in
  `PossessionEngine` beside each award — **constrained**: one roll definition, three
  callers, never three copies.
- The exact divisor expression for the Step-1 derivation — **constrained**: the
  personal-foul rate, carrying the emergent-coupling comment.
- Whether the flagrant-2 line is worth printing separately once it reads ~0.02.

**Not open** (resolved by #034 A–J): the layered-roll shape and three-site coverage (A),
retention as the existing `continue` under the existing cap (B), the committer-determined
fork and replace-don't-add count (C), the fouled player shoots (D), the flat 15% severity
share (E), the ejection stays **derived** (F), the game-level constant shape (G), the
separate instrument and **no new box-score column** (H), `recordFoul()` and the untouched
bonus tally (I), and the no-re-centering fence (J).

---

### ⚠ Do NOT (guardrails carried into §3.14b)

- **Do NOT add a fourth removal path in `RotationState`** — ejection is the **hard**
  tier and extends `isDisqualified(...)` (#031 H, built §3.14a).
- **Do NOT reuse §3.14a's counter split** — a flagrant **does** count toward the
  6-foul limit and the penalty tally (#032 E). Use `recordFoul()`.
- **Do NOT re-tune §3.13's foul-trouble sit curve** — measured saturated (#031 note).
- **Do NOT trim `BASE_NO_BASKET_FOUL`** — a measured **wrong-way** lever (#028):
  trimming it *raises* points.
- **Do NOT re-open §3.12's `FOUL_MULT_*`** (settled on realism, #030 G) or touch
  `pickDefender`'s `individualDefense` weighting (#031 A).
- **Do NOT re-center points/FG%** — §3.16 owns it and the targets are CONTESTED
  ([calibration.md](calibration.md)).
- **Do NOT change the existing foul roll** — a flagrant is an *additional* roll on
  a foul that already happened (user call, #034 A). `isFoul` / `isAndOne` /
  `resolveReboundFoul` keep their rates, inputs and RNG draws.
- **Do NOT back-solve §3.14a's technicals constant** against the ~5% harness gap —
  that is #032 B2's documented nominal-vs-actual pace effect, not drift.
- **Do NOT add a stored `flagrant2` / `ejected` flag** (#034 F) — `flagrantTwos >= 1`
  is a monotonic counter, so the predicate stays **derived**. The stored-state
  exception predicted by #031 H / #032 F does not arise and is **retired**.
- **Do NOT exempt the flagrant retention from `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`,
  and do NOT raise the cap** (#034 B). 3→5 is a parked idea needing its own
  recalibration pass ([ideas.md](ideas.md)).
- **Do NOT add 2 FTs on top of the underlying foul's award** (#034 C) — the flagrant
  **replaces** it. A flagrant stopped THREE is **2** FTs, not 3 and not 5. This is
  the likeliest bug in the pass.
- **Do NOT add a `flagrant_fouls` box-score column** (#034 H) — a flagrant is already
  inside `fouls`, so #033's parity argument does **not** reach it.
- **Do NOT reuse `pickTechnicalFreeThrowShooter`** (#034 D) — its premise is that
  nobody was fouled (#032 G); on a flagrant somebody was.

---

## Verified facts (the anchors §3.14b needs — confirmed against the code 2026-08, post-§3.14a)

Paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`.
Trust these; re-check only if the code moved.

**The disqualification seam §3.14b's flagrant-2 must extend:**
- `RotationState.isDisqualified(PlayerGameState)` — the shared private predicate,
  `isFouledOut() || isEjected()`. **This is the single place a third cause is added.**
- `RotationState.eligible(...)` — every candidate pool passes through it; calls
  `isDisqualified`.
- `RotationState.replaceFouledOut()` — the **hard** tier; forces off every
  disqualified on-floor player, replacing from the **full** bench. If no eligible
  replacement exists the player **stays on** (the never-below-5 last resort, #023 F).
- `RotationState.mostFoulTroubledCandidate()` — the **soft** tier's candidate
  filter, also via `isDisqualified`.
- `PlayerGameState.isFouledOut()` / `isEjected()` — both **derived** predicates over
  monotonic counters. **#034 F adds a third of the same shape** (`flagrantTwos >= 1`):
  a flagrant-2 ejects immediately, so there is no threshold to remember and the
  predicted stored-state exception does **not** arise.
- `PlayerGameState.recordFoul()` / `getFouls()` — the **personal-foul** counter.
  **A flagrant DOES join it** (unlike §3.14a's technical).
- `SimConfig.FOUL_OUT_LIMIT = 6`, `TECHNICAL_EJECTION_LIMIT = 2`.

**The possession-retention seam (#034 B CHOSE the first — the others are context):**
- ✅ **`PossessionEngine.resolvePossession(...)` — the `while (true)` second-chance
  loop.** `offensiveRebounds++; continue;` **is** the retention mechanism, and
  `offensiveRebounds` counts against `SimConfig.MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`
  (3). **The flagrant reuses it verbatim and respects the cap** — no new fork, no new
  record type. **This makes the "structural crux" a five-line change.**
- **§3.10 rebounding foul**: `PossessionEngine.ReboundFoulResult(sequence, offenseRetains)`
  — the closest analogue, a foul that forks the possession **both** ways. **The
  flagrant's rebounding-site fork returns through this same record** (#034 C).
- **§3.7 block recovery**: `BlockRecovery.offenseRetains()` + `continue`, gated on
  `capReached` — the same shape, for reference.
- **§3.8 missed-shot**: `MissedShotOutcome.offenseRetains()`, cap passed **in** to the
  resolver.
- **⚠ Every FT path currently ENDS the possession (#030 B)** — that is the invariant
  a flagrant breaks, and **no existing code does it**. §3.14b is the first.

**The foul + FT machinery §3.14b reuses:**
- `PossessionEngine.awardFreeThrows(...)` — the single FT block shared by **four**
  situations since §3.14a (shooting, §3.10 bonus, §3.11 and-1, §3.14a technical).
  Takes `count` and `source` as parameters (#029 D). **Reuse verbatim** — FT/points
  reconciliation is automatic. A flagrant is `count = 2`.
- `PossessionEngine.pickFreeThrowShooter(...)` — the **`foulDrawing`-weighted** draw
  (#028 B). Untouched by §3.14a.
- `PossessionEngine.pickTechnicalFreeThrowShooter(...)` — §3.14a's **deterministic**
  best-`freeThrows` pick (#032 G). **A flagrant's shooter is the player who was
  fouled**, so it needs neither of these on the shooting path.
- `FoulResolver.isFoul` / `isAndOne` / `resolveReboundFoul` — the three existing foul
  sites. **#034 A uses ALL THREE**, with one shared roll: `isFoul` and `isAndOne` are
  always defensive (→ always retain), and `resolveReboundFoul` is the **only** site
  that can be committed by the offense (→ the only source of the flipping case).
- `GameData.addEvent(..., committingTeamId)` — the 9-arg overload (#028 D).
- `GameData.isInBonus` / `periodFoulCount` / `countsTowardBonus` — §3.14a's
  outcome-aware exclusion. **A flagrant counts**, so it needs no entry there.
- `GameData.TECHNICAL_FOUL_OUTCOME` — the outcome-string constant lives on `GameData`
  (the class that must recognise it), not on the engine that emits it.

**The clamp helper (consolidated §3.14a, #032 H):**
- `SimConfig.clampRareProbability(p)` — `max(0.0, min(PROB_CEILING, p))`, the
  **floor-free** helper. All four rare-event sites route through it. **A flagrant
  rate is rarer still — use this, never `clampProbability`.**
- `SimConfig.clampProbability(p)` — the normal helper, with `PROB_FLOOR = 0.02`.

**The measurement:**
- `CalibrationHarness` prints the §3.4 aggregates, per-slot minutes, the foul mix,
  fouls/bonus, and/1 + FT sources, the §3.12 per-shot-type breakdown, foul-outs +
  the 4/5/6 distribution, and **§3.14a's technicals + ejections line**.
- **⚠ `Fouls / team / game` tallies ALL FOUL events**, so it includes technicals as
  of §3.14a (~19.4 vs §3.13's 19.0 personal-only). Not comparable across the phase.
- Steer by **multiple `-DcalibrationSeed` runs to the MEAN** (#029 E), never one.

**The two commands** (both verified working, 2026-08 — run from the repo root):

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml test -pl gametime-app -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000 -DfailIfNoTests=false
```

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install
```

The first runs the harness (~30s, 102 games) — `@EnabledIfSystemProperty` disabled
without `-Dcalibration=true`, so it never runs in a normal build. The second is the
full gate including JaCoCo coverage (per-package, 80% line floor, `sim` currently
**99.2%**).

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Bench and coach technicals** → #032's follow-up. Not modelled (#032 C): a coach is
  not a `PlayerGameState`, so a coach technical would be a committer-less FT.
- **A technical's total lack of causal input** → #032's follow-up. Making it situational
  needs the **same** missing prerequisite as every parked strategic-sub idea:
  game-situation awareness in the rotation step (#031's follow-up, [ideas.md](ideas.md)).
- **✅ The `technicalFouls` counter IS surfaced** → **`decisions.md` #033** (done
  2026-08, immediately after §3.14a): `box_score.technical_fouls`, `BoxScoreEntity`,
  and the OpenAPI `BoxScore`. Unparked on a **parity** argument — the twelfth
  accumulator in a set whose other eleven were already exposed. **`committing_team_id`
  stays parked**; #033's argument does not extend to it.
- **Period-/time-aware foul trouble** → #031 C's follow-up, deferred by §3.13.
- **Getting foul-outs below ~0.38** → #031's follow-up. The lever is **measured
  saturated**; none of the three remaining candidates belong to §3.14.
- **Splitting `substitutionAggressiveness`** → #031 B's follow-up.
- **The over-dispersion finding (#031 A)** → #031's follow-up: the same `pickDefender`
  concentration applies to **blocks, steals, and shot contests**.
- **Strategic substitution as a category** → [ideas.md](ideas.md) (parked, not planned).
- **§3.15 (`SimConfig` profiles)** → roadmap.md. The design-pass input is in
  [backlog.md](backlog.md). **Its validation gate is reproducing §3.14b's landing
  exactly** — which is part of why the clamp consolidation was folded into §3.14a
  instead (#032 H).
- **§3.16 (recalibration against verified targets)** → roadmap.md, the last Phase-3
  sub-phase. Owns the contested points/FG% targets. Its **prerequisite is a backlog
  chore**: verify the benchmarks with real sources — including §3.13's unsourced
  foul-out figure and §3.14a's unsourced technicals ballpark (#032 J).
- **Calibration targets** → [calibration.md](calibration.md), **the source of truth**.
  Update it *and* the `CalibrationHarness` `(target ~N)` strings together.
- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star tuning,
  the `decisions.md` condense pass, harness self-verification) → [backlog.md](backlog.md).
- **Untriaged future-improvement ideas** → [ideas.md](ideas.md). *(Includes the parked
  cap 3→5 tuning idea — do NOT touch `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` without its
  own recalibration pass.)*
- **§3.6 seams left open by #024**: play-by-play pagination + a `period` filter; the
  per-event time column's storage shape; a lean header-only `GameResult` projection.
- **§3.7 seams left open by #025**: a skilled-blocker recovery edge; shot-clock pressure
  on block-recovered second-chance possessions (→ §3.9-E, still parked #027 D).
- **§3.9 seams left open by #027**: §3.7-E shot-clock pressure; finer turnover sub-types.
- **§3.10/§3.11 seams left open by #028/#029**: `committing_team_id` is **populated and
  queryable but not surfaced on the OpenAPI `GameEvent`**; the observed off/def
  rebounding-foul split (**78/22**) drifts from the configured 75/25.
- **§3.12 seams left open by #030**: the engine shoots **~20 3PA/team/game against the
  NBA's ~35** — a §3.16 input.
- **✅ THE CLAMP-HELPER CONSOLIDATION IS DONE** — #030 set the trigger (two sites is
  coincidence, a third is the signal); §3.13 added the third; **§3.14a added the fourth
  and folded in the consolidation** (#032 H). `SimConfig.clampRareProbability` now owns
  all four. A fifth floor-free site costs one call, not a fourth copy. *(This note is
  retired — kept one phase for the handoff, delete at §3.14b's close-out.)*
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true -DcalibrationSeed=NNNN`) stays in the `sim` test sources.
  Re-run it after any `SimConfig` change.
