# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.16).

Current focus: **§3.13 — Foul trouble & foul-outs**. The full §3.7–§3.16
sequence and what follows (Phase 4) live in **roadmap.md's
"Possession-fidelity completion" section** — not here (todo.md is
current-phase-only). §3.7–§3.12 have all shipped; §3.13 is next, then §3.14
(flagrants), §3.15 (profiles), §3.16 (recalibration).

> **§3.13 is EXECUTE-READY — the design pass is done and resolved as
> `decisions.md` #031 (Decisions A–H).** Build exactly the plan below, add an
> implementation note to #031 recording any divergence, and flip the roadmap
> bullet to `[x]`.
>
> **Read #031 A before touching anything.** The design pass ran the decomposition
> #030 E's discipline demands, and it **overturned the hypothesis this phase was
> created under**. The brief assumed the problem was "no benching behavior";
> the measurement found the dominant cause is that fouls are **over-dispersed by
> defender selection** — a *correct* piece of realism the engine is missing the
> counter-pressure to. That changes what you are allowed to touch (see the
> guardrails), so it is not optional background reading.

---

## The §3.13 problem, in the numbers that matter

**Measured on seed 1000 (204 team-games) during the #031 design pass**, using two
temporary diagnostics that have since been **deleted** — this table is the durable
record, so anyone re-checking it must rebuild them.

| Quantity | Measured | Read |
|---|---|---|
| League fouls per 36 min | **2.85** | vs. real-NBA ~2.2–2.4 — modestly high, not the problem |
| Foul-outs, **actual** | **0.593** | the headline (ballpark ~0.1–0.25) |
| Foul-outs, **flat-rate Poisson prediction** at the engine's own minutes + 2.85/36 | **0.262** | **the load-bearing number** |
| Same, at a real-NBA 2.25/36 | 0.094 | |
| Same, at 2.85/36 but nobody over 30 min | 0.198 | |
| `individualDefense` spread (359 seeded players) | mean 10.37, **sd 4.11** (p10 5.2 → p90 16.4) | the concentration driver |
| `foulProne` spread | mean 9.66, **sd 1.22** | nearly flat — *not* the driver |
| Poisson + the measured `individualDefense` concentration | **0.403** | ≈ the 0.425 pre-§3.12 baseline ⇒ mechanism explained |
| Fouls/36 by minutes rank (slot 1 → slot 10) | **2.69, 2.98, 3.05, 3.04, 2.69, 2.83, 2.89, 2.81, 2.56, 2.75** | **flat** ⇒ concentration is *within* tiers, not across them |

**The mechanism**: `ShotSelector.pickDefender` draws the defender **weighted by
`individualDefense`** (a 3.2× spread across the league), and `FoulResolver.isFoul`
then scales that same defender's foul probability by their own `foulProne`. The best
defenders guard far more possessions and therefore absorb far more fouls. Foul-outs
are a **tail** event at the 6th foul, so they are hypersensitive to that
concentration — the actual rate is **2.3× the flat-rate prediction**.

**This is a feature with a missing counterweight, not a bug.** Real basketball
produces the same concentration; what it *also* has, and the engine does not, is a
coach who sits that player. §3.13 adds only the counterweight.

---

## §3.13 execution plan (decisions.md #031 A–H — resolved, ready to build)

Build order. Seam: **foul-trouble constants in `SimConfig`** + a **derived
foul-trouble level on `PlayerGameState`** (no stored flag, no RNG) + a **new soft
substitution step in `RotationState`**, sequenced between `replaceFouledOut()` and
`runFatigueSubs()`, reading the already-threaded
`CoachModifiers.subAggressivenessFactor()`. **No schema, no OpenAPI, no new
`PlayType`, no new `outcome` string, no new `FreeThrowSource`, and no new coach
attribute** (#018's five stand). `CalibrationHarness` needs **no new instrument** —
#030 G's foul-out line and 4/5/6 distribution are already the right ones. Mirror the
§3.7–§3.12 execution rhythm.

> All Maven commands set `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
> (JDK 21). Per-package coverage gate (80% line, target ~90%) only at
> `mvn -f gametime-service/pom.xml clean install`. Engine work in the `sim` package —
> no OpenAPI/schema change.

**The two commands you need** (both verified working, 2026-08 — run from the repo root):

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml test -pl gametime-app -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000 -DfailIfNoTests=false
```

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install
```

The first runs the harness (~30s, 102 games) — `@EnabledIfSystemProperty` disabled
without `-Dcalibration=true`, so it never runs in a normal build. Vary
`-DcalibrationSeed` for the multi-seed discipline (#029 E). The second is the full
gate including JaCoCo coverage.

**Measured pre-§3.13 baseline (seed 1000, 2026-08 — verified by actually running it):**

| | |
|---|---|
| Points / FG% / 3P% | **117.6** / 46.6% / 36.5% |
| Assists / Turnovers / Blocks | 27.1 / 13.4 / 4.8 |
| Fouls / team / game · per period | **19.03** · 4.85 (52.3% of team-periods in bonus) |
| **Foul-outs / team / game** | **0.593** |
| **Players at 4 / 5 / 6 fouls** | **0.84 / 0.45 / 0.59** (of 9.2 who played) |
| Minutes, slots 1–5 | **36.6 / 33.4 / 31.4 / 28.9 / 26.9** |

*(Single seed — the §3.12 5-seed mean was 117.0 points, so expect ±1.5 per-seed
noise. Use the mean, never one run, when tuning.)*

---

**Step 1 — the foul-trouble constants in `SimConfig` (#031 B).**
- [ ] Add the per-foul-count **sit probabilities** — the probability that a coach
  benches a player at foul count *f*, at an **average (10) `substitutionAggressiveness`
  coach and an average-value player**. This is the base curve; Steps 2–3 scale it.
- [ ] **Shape it to the user's stated intent (#031 B), which is the acceptance
  criterion for these numbers**: *a high-aggressiveness coach starts thinking about
  sitting at 3, a medium one at 4, a low one at 5.* Expressed as **one curve scaled
  by the coach factor**, not three thresholds — so the base at 3 fouls must be small
  enough that only a high-aggressiveness multiplier makes it fire often, and the base
  at 5 must be high enough that nearly everyone sits. Counts 0–2 are **zero** (no
  coach benches for 2 fouls); 6 is `FOUL_OUT_LIMIT` and is not this rule's business.
- [ ] Add the **value-scaling sensitivity** (Step 3) as its own constant, in the
  avg-10 deviation spirit the rest of `SimConfig` uses.
- [ ] **Units hazard, the #030 G lesson applied here**: make the javadoc say
  explicitly whether each number is *a probability* or *a multiplier*. §3.12 shipped
  a `FOUL_MULT_*` table that reads like a probability and is not, and the misreading
  happened once during that design pass. Do not repeat it one phase later.
- [ ] These are **uncalibrated numbers this pass invents** — they are the phase's
  own free lever and the thing Step 6 tunes. Do not treat any placeholder as settled.

**Step 2 — the derived foul-trouble level AND the derived value composite on `PlayerGameState` (#031 E/B).**
- [ ] Add a **derived** accessor over the existing `fouls` counter — a pure question
  about the player ("how deep in foul trouble is he"), mirroring `isFouledOut()`
  exactly in spirit (#023 F).
- [ ] Add the **derived value composite** (#031 B) — the user-set formula, over
  skills `PlayerGameState` already holds. **No new field, no new column:**

  ```
  value = (individualDefense + rimProtection + defenseRebound + offense + offense) / 5
  where offense = mean(drive, finishing, perimeter, post, longRange)
  ```

  Offense is **deliberately double-weighted** (~60/40 defense-leaning) — the right
  lean for foul trouble, which bites defenders and bigs hardest (#031 A). **The
  formula is NOT open at execution** — it is a user call.
- [ ] **Expected spread, measured over the 359 seeded players during the design
  pass** (diagnostic deleted; these are the durable numbers, use them as a sanity
  check): **mean 10.06, sd 3.10, p10 6.18 → p90 14.47**; **starters mean 11.95 vs.
  bench 8.36**; the top-value player is a starter in **34 of 34** teams. If your
  implementation lands far off these, the formula is wired wrong.
- [ ] **No stored flag.** No `inFoulTrouble` boolean, no `benchedForFouls` field —
  that is the #013/#015 duplicate-source trap, and #023 F / #028 A1 both refused it.
- [ ] **Keep `PlayerGameState` RNG-free and coach-free.** It must NOT gain a
  `shouldBench()` — that predicate would need the coach factor and a
  `RandomGenerator`, neither of which belongs in this class. The *decision* lives in
  `RotationState` (Step 3); only the *state question* lives here.
- [ ] Note in the javadoc that unlike `isFouledOut()` this predicate's **response is
  not monotonic** — a player can be benched, return, and be benched again — which is
  what #031 D's return cycle relies on.

**Step 3 — the soft foul-trouble substitution in `RotationState` (#031 B/D/F). The crux step.**
- [ ] Add the new step to `advancePossession()`, **strictly between**
  `replaceFouledOut()` and `runFatigueSubs()`. The final order is:
  1. drain / recover energy (unchanged)
  2. `replaceFouledOut()` — **hard** rule, unchanged, still reaches the **full** bench
  3. **foul-trouble sub — NEW, soft**
  4. `runFatigueSubs()` — unchanged, energy-only
- [ ] **Probability = base(foulCount) × coach factor × value factor.** The coach
  factor is `modifiers.subAggressivenessFactor()` — **already threaded through
  `TeamContext`, so no new plumbing** (verified). Higher aggressiveness ⇒ likelier to sit.
- [ ] **The value factor protects the BEST players MORE** (#031 B) — a star at 4
  fouls is *likelier* to sit than a 9th man at 4 fouls. Use Step 2's **value
  composite COMBINED with** the existing `isStarter()` / `rotationOrder` roster
  signal — **combined, not substituted** (user call). The two agree at the top in all
  34 seeded teams, so combining is safe; where they diverge mid-roster (a
  `rotationOrder`-1 bench player outranking a weak starter) the combination is
  exactly what gets that player real protection.
- [ ] ⚠️ **Do NOT use the player's index in the `squad` list.** It looks like a
  rotation-priority ordering and **is not**: starters carry a null `rotationOrder`
  (#014) and are appended in `team.getPlayers()` order, so **indices 0–4 are
  unordered among themselves** — only indices 5+ (bench, sorted by `rotationOrder`)
  carry real priority. This was #031 B's first draft and was corrected; the trap is
  live in the code, so don't rediscover it.
- [ ] **This inverts §3.5's fatigue rule and that is deliberate** —
  `STARTER_SUB_THRESHOLD_BONUS` lets starters tolerate *more* fatigue before being
  pulled, while foul trouble pulls them *sooner*. You ride your star when he's tired;
  you protect him when he's in foul trouble. **Put this in a comment** — it reads
  like an inconsistency and a later reader will otherwise "fix" it.
- [ ] **Draw from the `rotationDepth` window** (`benchWithinDepth()`), matching
  `runFatigueSubs`, **not** the full bench `replaceFouledOut` uses (#031 F). A
  foul-trouble sub is a rotation decision, not an emergency. This gives
  tight-rotation coaches more foul-outs than deep-rotation ones **for free** — a
  correct emergent behavior; watch for it, don't code it.
- [ ] **The rule YIELDS when the bench can't cover it** (#031 F): it fires only if a
  genuinely eligible replacement exists (`eligible(...)`, which already excludes
  fouled-out players). If none is available, **the rule does not fire** and the
  foul-troubled player keeps playing. A soft preference never weakens a hard invariant.
- [ ] **A fouled-out player NEVER returns — that bar is absolute and §3.13 does not
  touch it** (#031 F). Already enforced twice in shipped code: `replaceFouledOut()`
  forces him off and `eligible(...)` filters him from every candidate pool. Derived
  from a monotonic counter, so it cannot flip back. The soft rule must never be
  written in a way that appears to trade against this.
- [ ] **STICKY SIT (#031 D — the anti-oscillation mechanism, user call).** Once
  benched for foul trouble the player becomes an **ordinary bench player**: no
  "benched for fouls" status, no exclusion to lift, and — critically — **no competing
  foul-trouble roll to bring him back**. Re-entry runs **only** through the existing
  `runFatigueSubs` freshness path.
- [ ] **The return timer is energy recovery, and it's free.** Recovery (5.5/possession)
  is ~2× drain (2.6), so a sitting player becomes a progressively better
  `freshestEligibleBench` candidate over several possessions. **Do NOT add "sits for
  N possessions"** — the first stored timer in a deliberately clock-free,
  state-derived rotation system (#021 / #023 A/F).
- [ ] **Why stickiness is load-bearing, not stylistic**: substitution is re-decided
  **~100 times per team per game** (once per possession, ~29 game-seconds apart). A
  rule that re-rolled in *both* directions at that cadence would visibly flicker a
  4-foul player on and off. Only the **sit** is probabilistic; the **return** is earned.
- [ ] **Do NOT re-apply `substitutionAggressiveness` to the return decision** — it is
  used **once**, for the sit. Applying it twice double-counts one attribute across
  two opposed behaviors (#031 D).

**Step 4 — the RNG plumbing, and the determinism re-baseline (#031, status block). Read this fully before starting.**
- [ ] **`RotationState.advancePossession()` needs a `RandomGenerator` it does not
  take today.** Thread it from `PossessionEngine.simulate()`'s loop (both
  `home.rotation()` and `away.rotation()` calls).
- [ ] **This REVISES #023 C**, which made substitution deterministic and **RNG-free
  on purpose**. #031 B argues the reversal: two coaches facing an identical 4-foul
  situation genuinely make different calls, and #023 C's "subs are a decision, not
  chance" reasoning fits *fatigue* subs (measurable state trigger) far better than
  foul trouble (a judgement). **Say so in the code comment** — a future reader
  finding an RNG draw in the rotation step will otherwise think it's a bug against
  #023 C.
- [ ] **Take the draw UNCONDITIONALLY at a fixed point**, not only when a bench
  candidate exists. A conditional draw forks the stream on rotation state and makes
  the shift unreproducible.
- [ ] **Pass it as a METHOD parameter on `advancePossession()`, not a constructor
  field.** Verified 2026-08: `new RotationState(...)` has **8 call sites** — 2 in
  `GameSimulator` (:53, :55) and 6 in tests (`PossessionEngineTest` ×5,
  `RotationStateTest` ×1). A method parameter leaves all 8 untouched; a constructor
  field churns every one of them for no benefit.
- [ ] **Expect every seed-pinned assertion to re-baseline** — a shift comparable to
  §3.12's. This is a controlled structural change, the same one §3.7–§3.12 each
  took; structural determinism tests (same seed ⇒ same result) stay valid and must
  still pass.

**Step 5 — tests. INVOKE THE `test-coverage` SKILL FIRST** (the JaCoCo gate is
per-package and runs at `install`, not `test` — a green `mvn test` does not prove it
passes).

**Where the tests go — all four files already exist** (verified 2026-08, in
`src/test/java/software/daveturner/gametime/sim/`):
- **`RotationStateTest.java`** — the crux file: the sub rule, precedence, yield,
  oscillation, sticky sit. Has a `new RotationState(squad, mods, config)` helper at
  :40 to build from.
- **`PlayerGameStateTest.java`** — the derived foul-trouble level and value composite.
- **`SimConfigTest.java`** — the new constants and their helper.
- **`PossessionEngineTest.java`** — the RNG plumbing + the seed-pinned re-baselines.
- **`TestPlayerFactory.java`** — the shared builder for test players; use it rather
  than hand-rolling `PlayerGameState` instances, and extend it if the value composite
  needs skills it doesn't currently set.
- [ ] The **oscillation** case (#031 D/E): a foul-troubled player must not flip on and
  off the floor across consecutive possessions. Assert a minimum realistic gap
  between being benched and returning, over a run.
- [ ] The **sticky-sit** case (#031 D): once benched for foul trouble, the player's
  return must come through the freshness path only — assert he does **not** re-enter
  on the very next check while still tired.
- [ ] The **yield** case (#031 F): with a bench too thin / fully fouled out, the
  foul-trouble rule does **not** fire and the floor stays at exactly 5.
- [ ] **A fouled-out player never returns** (#031 F) — assert directly, including the
  case where the foul-trouble rule would otherwise want to swap him in. This is the
  invariant the user called out explicitly; pin it even though it is pre-existing.
- [ ] **Precedence**: a fouled-out player is still forced off even when the
  foul-trouble rule would also have something to say — hard beats soft.
- [ ] **The value composite** (#031 B): assert the formula's shape and that a
  high-value player is protected more than a low-value one at the same foul count.
- [ ] **The star-protection inversion** (#031 B): at equal foul counts, a
  high-value starter sits more often than a low-value deep-bench player — the
  *opposite* of the fatigue rule's starter tolerance. This is the decision most
  likely to be silently "fixed" by a later reader who reads it as a bug — pin it.
- [ ] **Coach scaling**: a high-`substitutionAggressiveness` coach benches at a lower
  foul count than a low one, over a run.
- [ ] **Determinism**: same seed ⇒ same game, still.
- [ ] Coverage: `RotationState`, `PlayerGameState`, `SimConfig` are all currently at
  or near 100% — keep them there; aim ~90%+ on the package, don't scrape the 80% floor.

**Step 6 — calibrate, and STOP at the stop condition (#031 G).**
- [ ] **Judge by the 4/5/6 distribution, not the headline count** (calibration.md).
  The rule works by moving players out of the 5-foul bucket before they reach 6 —
  `0.84 / 0.45 / 0.59` today.
- [ ] **Multiple `-DcalibrationSeed` runs, tuned to the MEAN** (#029 E). Per-seed
  noise is ±1.5 points and enough to bait an over-correction.
- [ ] **Budgeted minutes cost, modelled in #031 G** (directional — the harness settles it):

  | Rule | Foul-outs | slot 1 | slot 2 | slot 3 |
  |---|---|---|---|---|
  | none (today) | 0.39 *(model)* / **0.59** *(measured)* | — | — | — |
  | bench from 5 | 0.27 | −1.1 | −0.8 | −0.3 |
  | bench from 4 | **0.125** | **−2.4** | −1.8 | −1.0 |

- [ ] **⛔ STOP CONDITION (the #030 E shape, which fired correctly in §3.12).** Slot 1
  sits at **36.6** against a §3.5 target of **~34–36** — at the top of its band, so
  the headroom is ~1–2.5 minutes. **If landing foul-outs inside ~0.1–0.25 pushes slot
  1 below ~34, or moves any §3.4 aggregate materially: STOP and bring the user the
  trade.** Do not push through. A **calibrated** target (minutes) outranks a
  **ballpark** (foul-outs), and the ballpark's own numbers are unsourced (0.11 from
  #030 G, 0.15–0.25 from a search).
- [ ] **A partial landing (~0.3) that preserves the minutes distribution is an
  acceptable — and probably the correct — outcome.** Say so plainly in the close-out
  rather than quietly missing the ballpark.
- [ ] **Report points/FG%; do NOT chase them.** Benching starters lowers scoring.
  Re-centering is **§3.16** and its targets are CONTESTED ([calibration.md](calibration.md)).

**Step 7 — close-out (#031 H), including the DESCRIPTIVE doc updates. INVOKE THE
`project-docs` SKILL FIRST** — every item below edits a planning doc, and the skill
carries the house format, the cross-file routing rules, and the implementation-note
shape.
- [ ] **`docs/game.md` — the "Substitution + fatigue + foul-outs (§3.5)" paragraph.
  REQUIRED, not optional.** The §3.13 design pass **pre-flagged** this with a
  `> §3.13 will change this paragraph — not yet built` block and already fixed the
  pre-existing "for the team about to play" error (it runs for **both** teams). At
  execution you must:
  1. **Correct "deterministic given (energy, fouls, coach attrs) and consumes NO
     RNG"** — the headline claim, and **§3.13 makes it false** (Step 4 puts a draw in
     the rotation step, revising #023 C). The single most important line to fix.
  2. Add the **foul-trouble sub** to the step list, between the force-off and the
     fatigue sub, with the sticky-sit/earned-return shape (#031 D).
  3. **Delete the pre-flag block** once the paragraph describes shipped behavior —
     leaving a "not yet built" note on built behavior is its own doc rot.
- [ ] **`docs/game.md` — the calculation-sequence table** ("which resolver runs
  when", added by the §3.13 design pass). Row 0 is `RotationState.advancePossession()`
  and carries the same `† consumes no RNG today` footnote — **update it in the same
  change** as the paragraph above, or the two will disagree.
- [ ] **`docs/possession-flow.puml` — a JUDGMENT CALL, and it is the builder's
  (see the §3.13-on-the-diagram note below).** The possession *path* is unchanged, so
  no new branch is needed. **But** the top partition box enumerates the rotation
  steps and carries the note "**Consumes no RNG, emits no events**" — and §3.13
  falsifies the first half. **Either** add the foul-trouble step to that box *and*
  correct the note, **or** leave the box alone — but **do not** leave a box that
  claims the step consumes no RNG once it does.
- [ ] Add the **implementation note** to #031 — divergences, the resolved
  open-at-execution items, final constants, the landing (multi-seed mean), coverage.
- [ ] Flip the roadmap §3.13 bullet to `[x]` with a landing note.
- [ ] **Promote foul-outs from `ballpark` to `TARGET`** in calibration.md's table
  with the landed value (#031 H) — **and update the `CalibrationHarness`
  `(target ~N)` string in the same change** (calibration.md's own rule). Note the
  number stays soft until §3.16's benchmark-verification prerequisite sources it.
- [ ] Update the §3.5 minutes rows in calibration.md if the distribution moved.

---

### ⚠ Do NOT (guardrails — every one of these is a measured or settled call)

- **Do NOT trim `BASE_NO_BASKET_FOUL`.** A measured **wrong-way** lever (#028):
  trimming it *raises* points, because it controls a **swap** — a live shot for a
  ~1.5-point FT trip. And per #031 A the foul rate is not the problem anyway. The
  instinctive "fouls are high, turn the rate down" is exactly wrong, twice over.
- **Do NOT re-open §3.12's `FOUL_MULT_*`.** Settled on realism grounds with the user
  (#030 G).
- **Do NOT flatten `ShotSelector.pickDefender`'s `individualDefense` weighting.**
  This is the most tempting wrong answer #031 A exposes. It would "fix" the number by
  deleting a *correct* realism (good defenders guard more), and it would silently
  move blocks, steals, and shot contests — all calibrated.
- **Do NOT touch `foulProne`** — measured sd 1.22, it is not the driver.
- **Do NOT lower the league foul rate.** 19.0/team/game is inside its own ~19–20
  ballpark; trading a passing number for a failing one is not a fix.
- **Do NOT add a 6th coach attribute.** Settled (user call, 2026-08);
  `substitutionAggressiveness` already means the right thing (#018's five stand).
- **Do NOT add a period- or time-remaining-aware threshold.** Deliberately deferred
  (#031 C, user call: complexity). Yes, the engine will bench a 5-foul star in the
  final minute — that is a known, recorded, accepted cost.
- **Do NOT re-center points/FG%.** §3.16 owns it; the targets are contested.
- **Flagrants/technicals are §3.14, not this pass** — but leave `RotationState` with
  the clean three-tier structure (hard/forced · soft/preference · fatigue) that
  #031 H directs §3.14's ejections to extend.

### Open at execution (small, constrained)

- The exact per-foul-count base probabilities and the value-scaling shape —
  constrained by #031 B's user-specified intent (high aggressiveness thinks about it
  at 3, medium 4, low 5; better players managed more tightly), settled against the
  harness per Step 6.
- **How** the value composite and the roster signal are blended (#031 B fixes *that*
  they combine and fixes the composite's formula; the blend shape is a tuning call).
- Where exactly the RNG draw is taken inside `advancePossession()` — constrained:
  fixed point, unconditional (Step 4).
- The final landing and how much minutes cost is accepted — re-agreed with the user
  against multiple seeds (Step 6's stop condition).

**Not open**: the lever (#031 A — `RotationState` only), the no-new-coach-attribute
call (B), the derived-not-stored discipline (E), and the never-below-5 precedence (F).

### Does §3.13 belong on `possession-flow.puml`? — **no new branch, but the existing box may need a correction** (checked this pass)

**§3.13 adds nothing to the possession PATH.** The `.puml` traces turnover → foul →
block → make/miss → and-1 → rebound-foul → missed-shot outcome. Substitution is
**per-possession rotation state**, resolved in `advancePossession()` *before*
`resolvePossession()` runs; it adds no branch, no event, and no fork to the
possession itself. §3.5 correctly left it off the path, and §3.13 changes nothing
about that — so **no new node or decision diamond is required.**

**But the diagram is not entirely silent on rotation, and that is the catch.** It
opens with a `RotationState — between-possession sub (§3.5)` partition box that
**enumerates the three rotation steps** and carries the note:

> *Deterministic given (energy, fouls, coach attrs). **Consumes no RNG, emits no
> events.** onFloor() is always exactly 5.*

**§3.13 falsifies "consumes no RNG"** (Step 4, revising #023 C), and adds a fourth
step the box doesn't list. So the honest options at execution are: **(a)** add the
foul-trouble step to the box **and** correct the note, or **(b)** leave the box
untouched. **What is NOT acceptable is a box that still claims the step consumes no
RNG once it does** — the `.puml` is a living spec, and a determinism claim is
exactly the kind a future pass would rely on. Recorded here so the next design pass
doesn't re-litigate the "does substitution go on the diagram" question, and so the
builder doesn't miss the note.

---

## Verified facts (the anchors §3.13 needs — confirmed against the code 2026-08, post-§3.12)

Paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`.
Trust these; re-check only if the code moved.

**The foul-out mechanism as it exists today (all of it):**
- `SimConfig.FOUL_OUT_LIMIT = 6` — the only foul-related rotation constant.
- `PlayerGameState.isFouledOut()` — a **derived predicate** over the `fouls`
  counter (#023 F), not a stored flag. **Note: it is `isFouledOut()`, not
  `hasFouledOut()`** — #030 G and some older notes name it wrongly.
- `PlayerGameState.recordFoul()` / `getFouls()` — the counter every foul type
  increments (shooting, and-1, rebounding).
- `RotationState.replaceFouledOut()` — called from `advancePossession()`, forces
  off every on-floor player at the limit, replacing from the **full** bench (not
  the `rotationDepth` window) with the freshest eligible player. If no eligible
  replacement exists the fouled-out player **stays on** (the never-below-5 last
  resort).
- `RotationState.eligible(...)` — filters `isFouledOut()` out of any pool.
- **`RotationState.runFatigueSubs()` is the ONLY other substitution driver, and
  it considers energy alone — it does not look at `fouls`.** That is the gap §3.13 closes.
- `RotationState.benchWithinDepth()` — the `rotationDepth` window the foul-trouble
  sub draws from (#031 F).
- `PossessionEngine.simulate()` calls `home.rotation().advancePossession()` then
  `away.rotation().advancePossession()` once per possession, before
  `resolvePossession()`. **Neither call passes an RNG today** — Step 4's plumbing.

**Where the fouls actually come from (#031 A's mechanism):**
- `ShotSelector.pickDefender(...)` — draws the defender **weighted by
  `individualDefense`**. The concentration driver (sd 4.11 across 359 players).
- `FoulResolver.isFoul(...)` — scales that defender's foul probability by their own
  `foulProne` (sd 1.22 — nearly flat, not the driver).
- `FoulResolver.pickCommitter(...)` — the §3.10 rebounding-foul committer draw,
  weighted by `foulProne` alone.
- Foul mix (seed 1000): `SHOOTING` 77.4%, `AND_ONE` 10.2%,
  `REBOUNDING_FOUL_DEFENSE` 9.5%, `REBOUNDING_FOUL_OFFENSE` 3.0%.

**The coach attributes (#018) — all five, and the one §3.13 uses:**
- A coach has **exactly five** continuous 1–20 avg-10 attributes on
  `CoachEntity`: `pace`, `offensiveScheme`, `defensiveScheme` (all §3.4),
  `rotationDepth`, `substitutionAggressiveness` (both §3.5). **There is NO coach
  `acumen`** — acumen is one of the 23 *player* skills (`PlayerGameState`), and
  the two are easy to conflate.
- `CoachModifiers.from(coach, config)` turns them into multipliers, already
  threaded into `RotationState` via `TeamContext` — so **§3.13 needs no new
  plumbing for the coach factor**, just a new read of `subAggressivenessFactor()`.
- `SimConfig.rotationModifier(...)` is the avg-10 deviation helper the two
  rotation attributes use; `coachModifier(...)` is the §3.4 equivalent.
- Existing rotation constants: `BASE_SUB_ENERGY_THRESHOLD = 62.0`,
  `STARTER_SUB_THRESHOLD_BONUS = 8.0` (the fatigue rule §3.13 inverts),
  `BASE_ROTATION_DEPTH = 4`.

**The measurement (§3.12 harness — no new instrument needed):**
- The harness prints **foul-outs per team per game** plus the **players at 4 / 5 /
  6 fouls** distribution (added by §3.12, #030 G), and the **§3.5 per-slot minutes
  line** (the cost gauge). Those three answer every §3.13 question.
- Steer by **multiple `-DcalibrationSeed` runs to the MEAN** (#029 E).

**§3.12's shipped landing, for reference (5 seeds, to the mean):**
`117.0 pts / 46.4% FG / 36.7% 3P / 26.8 ast / 13.5 TO / 4.8 blk / 2.8 OOB`,
fouls **19.1**/team/game (4.87/team/period), **52.3%** of team-periods in the
penalty, and-1s 1.87, 3-FT trips 0.59. **Targets: see
[calibration.md](calibration.md)** — it is the source of truth, and points/FG%
are flagged CONTESTED pending §3.16.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **§3.14 (flagrant/technical fouls)** → roadmap.md's Possession-fidelity
  section, a numbered sub-phase needing its own design pass. Changes *who
  shoots*, adds a possession-retention path no current model has, and adds
  ejections — which **must extend §3.13's hard/forced removal tier** rather than
  add a fourth path (#031 H), and will need **real stored state** (an ejection is
  not derivable from a counter — the first genuine exception to #023 F, to be
  argued explicitly).
- **Period-/time-aware foul trouble** → #031 C's follow-up, deliberately deferred
  this pass. Needs a `gameProgress` notion `advancePossession()` doesn't have.
- **Splitting `substitutionAggressiveness`** → #031 B's follow-up: it now governs
  **two** behaviors (fatigue subs and foul-trouble subs) that need not correlate.
  An accepted cost of the reuse call; splitting is purely additive.
- **The over-dispersion finding (#031 A)** → recorded in #031's follow-up because it
  outlives this phase: the same `pickDefender` concentration applies to **blocks,
  steals, and shot contests**. Any future pass finding one of those over-dispersed
  should start there.
- **Strategic substitution as a category** → [ideas.md](ideas.md) (parked, not planned).
  §3.13 adds the engine's **first** strategic sub; matchup/small-ball/hot-hand/
  closing-lineup subs are all absent and all blocked on the same missing prerequisite
  — **game-situation awareness in the rotation step**. #031's rule is the reusable
  template. This is also the real home of C's deferred time-awareness.
- **§3.15 (`SimConfig` profiles)** → roadmap.md, promoted from a backlog chore by
  user call. The accumulated design reasoning (including the open
  full-replacement-vs-override question) stays in [backlog.md](backlog.md) as the
  design-pass input. Scope-fenced to the **developer-facing substrate**; the
  player-facing side (eras, difficulty, custom rules) is Phase 4+.
- **§3.16 (recalibration against verified targets)** → roadmap.md, the last
  Phase-3 sub-phase and a different *kind* of pass (it adds no mechanic; it
  re-solves numbers — a second §3.4). Owns the contested points/FG% targets and
  the efficiency-vs-volume lever question. **§3.13 hands it a moved baseline.** Its
  **prerequisite is a backlog chore**: verify the benchmarks with real sources.
- **Calibration targets** → [calibration.md](calibration.md), **the source of
  truth** (created §3.12, superseding `decisions.md` #022 D). Update it *and* the
  `CalibrationHarness` `(target ~N)` strings together whenever a target moves.
- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning, the `decisions.md` condense pass, harness self-verification) →
  [backlog.md](backlog.md).
- **Untriaged future-improvement ideas** (no phase home, not chores) →
  [ideas.md](ideas.md). *(Includes the parked cap 3→5 tuning idea — do NOT touch
  `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` without its own recalibration pass.)*
- **§3.6 seams left open by #024** (carry into Phase 4/7): play-by-play
  pagination + a `period` filter (Decision D); the per-event time column's storage
  shape (Decision E); a lean header-only `GameResult` projection (Decision A).
- **§3.7 seams left open by #025**: a skilled-blocker recovery edge (Decision D);
  shot-clock pressure on block-recovered second-chance possessions (Decision E →
  §3.9-E, still parked #027 D).
- **§3.9 seams left open by #027**: §3.7-E shot-clock pressure (still parked);
  finer turnover sub-types (add a weight + enum value when a consumer wants it).
- **§3.10/§3.11 seams left open by #028/#029**: `committing_team_id` is
  **populated and queryable but not surfaced on the OpenAPI `GameEvent`** —
  additive whenever a play-by-play or Phase-4 stats consumer wants it; the
  observed off/def rebounding-foul split (**78/22**) drifts from the configured
  75/25 because defensive fouls compound through retained possessions —
  back-solve only if a consumer needs the observed split to hit a target.
- **§3.12 seams left open by #030**: the engine shoots **~20 3PA/team/game against
  the NBA's ~35** — a pre-existing observation surfaced (not caused) by §3.12's
  fouled-three work, and relevant to §3.16; and the `sim` package now has **two**
  places where a rare-event probability must dodge `PROB_FLOOR`
  (`rareEventProbability`, and `isFoul`'s floor-free multiply) — if a third
  appears, that is a sign the clamp helpers want consolidating.
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true -DcalibrationSeed=NNNN`) stays in the `sim` test sources; it
  reports the §3.4 aggregates + §3.5 minutes/period-FG% + §3.7 blocks + §3.8 OOB +
  §3.9 turnover-cause mix + §3.10 team-fouls/bonus + §3.11 and-1 rate + FT-source
  split + §3.12 per-shot-type foul breakdown, 3-FT trips, and the foul-out line.
  Re-run it after any `SimConfig` change.
