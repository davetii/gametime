# Calibration targets

**Source of truth for what the simulation is tuned toward.** Supersedes
`decisions.md` #022 D (§3.4), which is now historical on the target question.

Measured by `CalibrationHarness` — disabled by default, and it **reports, never
gates**:

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml test -pl gametime-app -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000 -DfailIfNoTests=false
```

**Judge by the mean of several `-DcalibrationSeed` runs, never one** — per-seed
noise is ±1.5 points, enough to bait an over-correction (#029 E).

> ⚠️ **EVERY TARGET IN THIS FILE BELONGS TO THE `baseline` PROFILE** *(from §3.15,
> `decisions.md` #035 I — shipped 2026-08)*. The sim profile is chosen by the ordinary
> Spring profile list (`-Dspring.profiles.active=test,baseline,nineties` — `baseline`
> must stay in it) beside the seed flag, and an era profile
> (a 1990s low-pace/high-foul style, a modern three-heavy one) is **SUPPOSED to miss
> most of these — that is the profile working, not a failure.** So on a non-baseline
> run the harness **suppresses the `(target ~N)` strings** and prints a **delta against
> the baseline landing** instead. **A delta is not a target**: it claims nothing about
> what a number should be, only what changed.
> **Nothing here is per-profile, and §3.15 did not add a second target set** — authoring
> era targets is real research and would create a second unsourced table beside the one
> §3.19 exists to fix (#017's don't-fabricate-a-constraint rule, applied to targets).

---

## Everything, in one table

All figures are **per team per game** unless noted. Current = the **5-seed mean, seeds
1000–5000** (2026-08) on the `baseline` profile — §3.15 reproduced §3.14b's landing
per-seed exactly, so the numbers are unchanged since §3.14b. **Target column: sourced
rows are Basketball-Reference league averages, per game, 2025-26** (#036).

**Type** — `TARGET` = calibrated, steer by it. `ballpark` = plausibility range,
judge against it but **do not calibrate to it** (#030 G, #017). `observed` = no
target, reported for visibility.

| Measure | Type | Target / range | Current | Status |
|---|---|---|---|---|
| **Points** | **TARGET (SOURCED)** | **115.6** | **110.0** | ⚠️ **−5.6 BY DESIGN — §3.19 recovers it** (#039 H). ⚠ §3.17 predicted ~113 and it stayed FLAT: FG points rose +3.8 as predicted, but the FTA loss was **−3.0 points**, not the −1.7 modelled (#040 I) |
| **FG%** | **TARGET (SOURCED)** | **47.1%** | **43.5%** | ⚠️ **WORSE ON PURPOSE, AND NOT A REGRESSION — §3.17 un-hid a 2P% error the old two-heavy mix was masking** (#040 F). Landed 43.5 vs a predicted ~44.5. **§3.19's**, via `base-drive`/`base-post`/`base-perimeter`. ⚠ **`base-three` must NOT move.** See below |
| **2P%** *(derived — not a harness row)* | **TARGET (SOURCED)** | **55.0%** | **~48.7%** | 🔴 **−6.3, and NOW VISIBLE** — at the 40.3% three share §3.17 landed, the aggregate FG% finally shows it. **§3.19's, and the main thing §3.17 hands on** (#040 F) |
| 3P% | **TARGET (SOURCED)** | **36.0%** | 35.8% | ✅ **held across a 1.9× volume change** — §3.17 touched no make rate, and this is the evidence (#040 F) |
| Assists | **TARGET (SOURCED)** | **26.7** | 26.1 | ✅ (−0.8 from §3.17's mix; inside the band) |
| Turnovers | **TARGET (SOURCED)** | **14.5** | 13.9 | ✅ |
| Blocks | **TARGET (SOURCED)** | **4.8** | 4.80 | ✅ ⚠️ **#040 E PREDICTED THIS WOULD FALL TO ~2.6 AND IT DID NOT — the prediction was WRONG, and the reason is a genuinely new finding.** `PROB_FLOOR` (**0.02**) is **4× `base-block-three` (0.005)**, so a three's block chance is **floored, not based**: shifting attempts to threes moves them 0.056 → 0.02, not → 0.005. Blocks fell only 4.94 → 4.80. ⚠ **The four `base-block-*` cannot be reasoned about without the floor** — a §3.19 note, not a §3.17 miss |
| Top-starter minutes | TARGET | ~34–36 | ~36.1 | ✅ (#023; §3.13 cost 0.5) |
| Minutes ceiling | TARGET | nobody over ~42 | ok | ✅ (#023) |
| Fouls | **TARGET (SOURCED)** | **19.9** | 18.18 | 🟡 **−2.35 from §3.17, WITHOUT TOUCHING A FOUL CONSTANT** — the mix moved ~17 draws/team/game from `foul-mult` 1.0 to 0.133. `PERSONAL_FOULS_PER_TEAM_GAME` was re-measured **20.15 → 17.82** in the same pass (#034 G, decided in writing). §3.19's |
| **FTA** | **TARGET (SOURCED)** | **23.5** | **19.76** | 🟡 **§3.17 UNDERSHOT IT by 3.7** — fewer stopped shots **and** a lower penalty rate (55.7% → 46.1%), so `sim.non-shooting-foul-share` (0.50) is **mispriced at the new foul rate**. ⚠ The share is priced by the penalty rate, not the foul rate alone. **§3.19's — do NOT re-tune it here** (#038, #040 F) |
| **3PA** | **TARGET (SOURCED)** | **37.0** | **37.22** | ✅ **§3.17 LANDED IT: 19.96 → 37.22 at 5 seeds** (#040 C/K), via `sim.shot-share-*` = 1.0 / 0.55 / 0.55 / **1.23**. **The one number §3.17 was judged on.** Charged three share **40.3%** vs a real 41.5% |
| FGA | **TARGET (SOURCED)** | **89.1** | 92.28 | 🟡 **OVER by 3.2, ON PURPOSE** (#040 E). A stopped shot charges no FGA and `foul-mult-three` is 0.133 vs DRIVE's 1.0, so more threes ⇒ fewer stopped shots ⇒ **more** FGA. ⚠ Predicted ~89.9; landed 92.28 — the mechanism was right, the **magnitude was under-modelled by ~2.4**. **§3.19's** (it owns pace). ⚠ **#039 C's dead-possession concession is therefore NOT reopened** |
| Off rebounds | **TARGET (SOURCED)** | **11.3** | 11.18 | ✅ **CLOSED by §3.17** — more misses at a lower make rate. Predicted ~10.6; landed better (#040 H) |
| Def rebounds | **TARGET (SOURCED)** | **32.4** | 30.48 | 🟡 **most of the way — 3.0 of the 5.0 gap closed by §3.17**, better than #040 H's predicted ~28.9. The residual ~1.9 is a **rate** question for §3.19, not a new sub-phase |
| Pace (poss/48) | **TARGET (SOURCED)** | **99.4** | ~100 nominal | ✅ |
| Steals | observed | **8.4** | *not in the report* | ⚪ **harness never prints it** — backlog chore; engine ~7.67 *by derivation only* |
| **Foul-outs** | **TARGET** (soft, **UNSOURCED**) | **~0.39** | **0.304** | ⚠️ **§3.17's lower foul rate took it back DOWN, 0.517 → 0.304** — a by-product of the shot mix, **not a re-tune**. The §3.13 sit curve is untouched and measured saturated (#031) |
| Players at 4 / 5 / 6 fouls | ballpark | *(no range yet)* | 1.31 / 0.63 / 0.52 | the real diagnostic for foul-outs |
| **Technicals** | ballpark | **~0.3–0.4** (~0.6–0.8 league-wide) | **0.350** | ⚠️ **judge at 5 SEEDS ONLY** — see below |
| **Flagrants** | ballpark (**UNSOURCED**) | **~0.13–0.20** (~0.25–0.40 league-wide) | **0.141** | ⚠️ **the COARSEST row here — 5 SEEDS ONLY**. ⚠ **§3.17 re-measured the divisor 20.15 → 17.82** (#034 G): left stale it ran **0.119 = 74% of target**, exactly the 17.82/20.15 ratio, and **nothing would have failed** |
| Flagrant-2s | *(no target — a 15% share)* | — | **0.035** | §3.14b; the ejection driver |
| **Ejections** | *(no target — an outcome)* | — | **0.044** | §3.14b; BOTH causes (§3.14a alone was 0.014) |
| Fouled-three rate | ballpark | **~2% of 3PA** | **2.93%** *(corrected)* | ✅ **INSTRUMENT FIXED by §3.17 Step 0** (#040 G). The rate held across a 1.9× volume change (2.92% pre → 2.93% post), which is the scale-free property `foul-mult-three` was built on. Raw visible 1.47% |
| 3-FT trips | ballpark | ~0.3–0.6 *here* | **1.09** *(corrected)* | ✅ the **count** roughly tripled with 3PA while the **rate** above held — exactly #040 G's prediction. The old 0.3–0.6 range was set at half the 3PA |
| And-1s | ballpark | ~4–6% of made FG | 1.88 (4.5%) | ✅ (#029 E) |
| Fouls / team / period | observed | — | 5.15 | bonus at 5 (#028) — ⚠️ **now AT the threshold**, which is why the penalty rate jumped |
| Team-periods in penalty | observed | — | 46.1% | a result, not a knob (#030 D) — ⚠️ **and it PRICES the §3.16 share**: §3.17 dropped it 55.7% → 46.1%, which is **why FTA undershot**. See below |
| Out of bounds | observed | — | 2.8 | (#026 D) |
| Turnover cause mix | observed | STOLEN dominant | 55.5% | no per-cause target (#027 E) |
| Period-by-period FG% | observed | flat, not sagging | flat | correct §3.5 behavior (#023 E) |

---

## ✅ RESOLVED — the points/FG% contest, and what it cost to leave it unsourced

**This section is HISTORY, kept because the lesson is the most expensive one in this
file.** Points and FG% were flagged CONTESTED in 2026-08 (§3.12) and **resolved by
sourcing in 2026-08** (#036 A). The resolution:

| | §3.4 target | §3.12's guess | **Sourced 2025-26** | Engine | Outcome |
|---|---|---|---|---|---|
| Points | ~112 | 114–117 | **115.6** | 118.3 | target was **too low**; a real +2.7 gap remains |
| FG% | ~47% | 47–48% | **47.1%** | 46.9% | ✅ **target was right; the ENGINE was already correct** |

**FG% was never actually contested.** The engine sits 0.2 below a sourced 47.1% —
**inside the ±0.14 standard error** of the 5-seed mean, with per-seed values spanning
46.5–47.3. There was nothing to fix, and no lever needed to be found.

**⚠ THE LESSON: three passes paid for an unsourced target.** §3.10 stopped at 113.8,
§3.11 took no trim, §3.12 took none — each declining the same shot-`BASE_*` trim because
it cost more calibrated FG% than the points miss was worth. **They were right, and the
reason is now known: the points target was ~3.6 too low, so most of the gap they were
being asked to close did not exist.** Three phases of judgement were spent working
around a number nobody had checked. **Source a target before tuning toward it.**

**The "one lever cannot fix both" framing is retired.** It was true given the false
targets — points wanting a trim while FG% wanted a raise, with `BASE_*` moving both the
same direction (~0.50–0.60% FG% per 1.0 point, #030 G). With FG% correct, **only points
need to move**, and the deadlock that justified a whole phase is gone.

**What replaced it is a bigger finding (#036 B): the totals are close, the COMPOSITION
is wrong.** Per team per game, the engine's points come from 2-pt **+10.9**, 3-pt
**−18.0**, FT **+7.1** against real — three large errors summing to ~zero. **The +2.7
points gap is largely an artifact of that cancellation.** A points-only re-solve would
have fixed the total and left the game shaped wrong. That is what §3.16 (FTA, **shipped
— the FT excess is gone**) and §3.17 (3PA, **shipped — 19.96 → 37.22**) existed to
correct. ✅ **Both composition passes have now landed**, so §3.19 finally has a final
shape to re-solve against. ⚠ **What it inherits is a 2P% error (~48.7 vs a real 55.0)
that §3.17 un-hid, FGA now OVER its target at 92.28, and FTA mispriced at 19.76.**

## Foul-outs — §3.13 landed them at ~0.39, ABOVE the ~0.1–0.25 ballpark, deliberately

| Configuration | Foul-outs |
|---|---|
| §3.11 foul reach (§3.12 multipliers zeroed, 5 seeds) | **0.425** |
| §3.12 as shipped | **0.60** |
| **§3.13 as shipped** (5 seeds, the foul-trouble bench rule) | **0.39** |
| Original ballpark | ~0.1–0.25 |

**§3.13 closed ~37% of the gap and then stopped, because the lever saturates.**
The soft foul-trouble bench rule (#031) is the only lever the phase was allowed to
touch, and it is genuinely exhausted at ~0.38–0.39 — **measured, not assumed**:
raising the sit curve from `{0.12, 0.50, 0.90}` to `{0.40, 0.90, 0.98}` produces
~60% more substitutions and moves foul-outs by **nothing** (0.377 → 0.382).

**Why it saturates, and it is a consequence of the design rather than a defect:**
#031 D specifies that a benched player **returns** through the ordinary freshness
path — no timer, no stored "benched for fouls" status. So he comes back into the
same over-dispersed defender draw (#031 A) that gave him the fouls. Benching him
harder just returns him sooner. Reaching ~0.25 needs a *different* lever than the
one §3.13 owns — see #031's implementation note for the three candidates.

**The distribution moved the right way, which is the real diagnostic:** players at
4 / 5 / 6 fouls went `0.83 / 0.49 / 0.62` → **`1.00 / 0.52 / 0.39`**. Players now
pile up at 4–5 and far fewer convert to 6 — exactly the "move them out of the
5-bucket before they reach 6" mechanism the rule was built for.

**It cost almost nothing in minutes**, which is why the #031 G stop condition never
fired: the top starter went **36.6 → 36.1** (still inside the calibrated ~34–36,
and *further* into the band than before), against a budgeted 1–2.5 minutes. The
whole slot curve shifted by ≤0.5 minutes. The §3.4 aggregates are unmoved.

**The ~0.39 target is SOFT and is a landing, not a benchmark.** It is promoted from
ballpark to TARGET per #031 H — because §3.13 deliberately tuned against it, so it
now has an owner — but the number describes where the engine sits, not where the
NBA does. The original ~0.1–0.25 remains unsourced (0.11 from #030 G, 0.15–0.25
from a search). **§3.19's benchmark-verification prerequisite should source the
real figure**; if it confirms ~0.1–0.25, that is a new phase with a new lever, not
a re-tune of #031's.

**The naive fix remains a trap, and it still is:** trimming the foul rate means
`BASE_NO_BASKET_FOUL`, a **wrong-way lever** — it *raises* points (#028, measured).
**Judge foul-outs by the 4/5/6 distribution, not the headline count** — they are a
tail phenomenon, and a benching rule works by moving players out of the 5-foul
bucket before they reach 6.

**The root cause, found by §3.13's design pass (#031 A), is still present and is
still correct realism.** Fouls are **over-dispersed**: `ShotSelector.pickDefender`
weights the defender draw by `individualDefense` (measured sd **4.11** over 359
players, a 3.2× spread) while `foulProne` is nearly flat (sd 1.22), so the best
defenders absorb far more fouls than a flat per-minute rate would give them. A
flat-rate Poisson over the engine's own minutes predicts **0.262** against the
then-measured **0.593** — **2.3×**. §3.13 added the missing coach who benches the
player; it deliberately did **not** flatten the concentration. **The foul rate
itself is NOT implicated** (19.0/team/game is inside its own ballpark), and neither
the defender weighting nor `foulProne` may be touched to fix this.

## Technicals — a ballpark on purpose, and the one row a single seed CANNOT resolve

Added by **§3.14a** (`decisions.md` #032 J). Two things make this row different from
every other one:

**It is a `ballpark`, not a TARGET, and deliberately so.** Nothing in the engine is
*tuned toward* it — `SimConfig.TECHNICAL_FOULS_PER_TEAM_GAME` is set **directly from**
the real-world figure (#032 B2), so the harness line is a **correctness check that the
constant is wired right**, not a calibration objective. A TARGET, by this file's own
rule, is a number a phase has deliberately tuned against. This one has nothing to tune.

**Judge it at 5 seeds only — this is not the usual "prefer the mean" advice, it is a
hard floor.** Computed before the rate was designed (#032 J):

| Sample | Events | Relative sd |
|---|---|---|
| 1 seed (102 games) | ~71 | **11.8%** |
| 5 seeds | ~357 | **5.3%** |

A single-seed reading **cannot resolve** the rate. And per #032 B2 the constant's
divisor is the **nominal** possession count while the real one is pace-scaled, so the
landing sits a few percent off the constant **by design** — a fast-paced game genuinely
takes more rotation checks and draws more technicals. **Do not back-solve the constant
against that gap**; it is behavior, not error.

**§3.14a landed at 0.367** (5-seed mean, seeds 1000–5000) against
`TECHNICAL_FOULS_PER_TEAM_GAME = 0.35` — **~5% high, which is exactly the
nominal-vs-actual gap above**, and the reason that gap was documented before the pass
ran. Per-seed spread was 0.338–0.392, consistent with the 11.8% single-seed sd
predicted. **The constant was not adjusted, and should not be.**

**Note the per-check probability is ~0.00175, not the "~0.0035" #032 B2 states.** That
estimate assumed ~100 rotation checks per team per game; the real count is ~**200**,
because both rotations advance on every possession (a team is checked on its defensive
possessions too). The constant in this table is unaffected — only the derived figure in
#032's prose was wrong. It also means `PROB_FLOOR` is **>10×** the per-check rate rather
than #032 H's stated ~6×, so the floor-free clamp requirement is stronger than argued.

**The §3.14a points budget is +0.26/team/game — below the ±1.5 per-seed noise band.**
So the §3.4 aggregates are an **invariant** for that pass, not a target to re-center:
measurable movement means the technical counter leaked into `getFouls()` or the bonus
tally (#032 E/I), which is a bug. **It held** — points moved +0.5 (117.0 → 117.5,
inside the band), the penalty rate stayed flat at **51.2%** (§3.13: 51.3%), and
foul-outs stayed inside their own seed spread. Nothing was re-centered.

**Ejections are an OUTCOME of the rate, not a target.** They landed at **0.014/team/game**
— roughly one per team per season. #032 F predicted 0.00 ("one every several simulated
seasons") and was an order of magnitude pessimistic; the arithmetic (0.367 spread over 5
players, P(a player reaches 2) ≈ 0.0026 × 5) matches the observation. Far too rare to
tune against either way — the rule is pinned by forced-counter unit tests.

**⚠ The harness's `Fouls / team / game` line is no longer comparable across §3.13.**
It tallies **all** `FOUL` events, so as of §3.14a it includes technicals: §3.13's 19.0
and §3.14a's ~19.4 differ by the technicals, **not** by any change in personal fouls.
Subtract the technicals line to compare.

## Flagrants — the coarsest row in this file (§3.14b, SHIPPED)

Added by **§3.14b** (`decisions.md` **#034 G/H**), landed 2026-08.

**A `ballpark`, not a TARGET**, for exactly §3.14a's reason: nothing is tuned toward it,
`FLAGRANT_FOULS_PER_TEAM_GAME` (= **0.16**, from a ~0.25–0.40 league-wide figure) is set
from the real-world number directly. **UNSOURCED** — §3.19's job (1); most other rows were sourced in 2026-08 (#036).

**§3.14b landed at 0.148** (5-seed mean, seeds 1000–5000) against the 0.16 configured,
with a per-seed spread of **0.123–0.186**. That spread is itself the argument below: at
17.4% single-seed relative sd, seed 4000's 0.186 and seed 1000's 0.123 are the **same
rate**, and either one alone would badly misinform a tuning decision. **The constant was
not adjusted, and should not be** on a reading this coarse.

**It IS the coarsest row in this file — 5 seeds is a hard floor, and even then it
only confirms an order of magnitude:**

| Sample | Events | Relative sd |
|---|---|---|
| 1 seed (102 games) | ~33 | **17.4%** |
| 5 seeds | ~166 | **7.8%** |

Compare technicals (11.8% / 5.3%). **A single-seed reading is useless, and even the
5-seed mean cannot resolve a 10% tuning move.**

**⚠ Its divisor is EMERGENT, which technicals' is not** (#034 G). §3.14a divides a
game-level constant by a **nominal, config-derived** check count; §3.14b divides by the
**measured personal-foul rate**. So **§3.16 (which moves the foul mix), §3.19, or any pass that moves the foul rate —
moves flagrants too**, without anyone touching `FLAGRANT_FOULS_PER_TEAM_GAME`.
⚠ **§3.17 proved the coupling reaches FURTHER than "a pass that moves the foul rate":**
it moved the SHOT MIX, touched no foul constant at all, and still dropped the personal-
foul rate 11.5% — running flagrants at 74% of target until the divisor was re-measured
to **17.82**. **Any pass that moves the shot mix inherits this obligation too.**
Directionally correct (more fouls, more chances for one to be excessive), but it means
the constant is **not a standalone dial** and a flagrant drift may be a foul-rate signal.

**The §3.14b points budget was +0.43/team/game** across two channels (FTs +0.24 gross,
retention +0.19 upper bound), **deliberately over-estimated** — the FT channel is largely
offset because the underlying foul already awarded 2–3 FTs. Sub-noise against ±1.5, so
**#032 I's inverted stop condition applied again**: measurable §3.4 movement at 5 seeds
would be a **bug** (double-awarded FTs, an uncapped retention loop, or a bonus-tally
leak), not a calibration result. **It held** — points moved **+0.76** (117.5 → 118.3,
inside the band and between the budget and §3.14a's own budget-vs-landing precedent), the
penalty rate stayed flat at **51.9%** (§3.14a: 51.2%), and foul-outs landed 0.358 inside
their seed spread. All three bug signatures are additionally pinned by tests rather than
inferred from the aggregates. Nothing was re-centered; **the contested pair was later
RESOLVED BY SOURCING (#036 A) — FG% needed no work at all, and re-centering is now §3.19.**

**Ejections are an OUTCOME here too, and §3.14b is where they became observable.** They
landed at **0.027/team/game** against #034 F's predicted ~0.024 — roughly **double**
§3.14a's 0.014, since a flagrant-2 ejects on the *first* one where a technical needs two.
The harness reports **both causes on one line** (#034 H: an ejection is an ejection; the
cause is recoverable from the event log). Still far too rare to tune against — the rule is
pinned by forced-counter unit tests.

**⚠ `Fouls / team / game` does NOT include flagrants — a correction to what this section
predicted before execution.** A flagrant **replaces** the underlying foul's event rather
than adding one (it upgrades a `SHOOTING_FOUL` / `AND_ONE` / `REBOUNDING_FOUL_*` into a
`FLAGRANT_FOUL_*`), so it contributes **nothing** to that total — measured 19.35 against
§3.14a's ~19.4. This is the opposite of §3.14a's technical, which is a genuinely new
event. **So subtract only the technicals line** to compare against §3.13's 19.0.

---

## ⚠ THE 2P% ERROR §3.17 UN-HID — FG% is now VISIBLY wrong, and that is the point

**Found by §3.17's design pass and CONFIRMED by its execution, 2026-08 (`decisions.md`
#040 F).** ⚠ **The engine's 2P% did not change. What changed is that it stopped being
hidden.** FG% landed **43.5%** against a predicted ~44.5 — the direction and cause were
right, the magnitude slightly under-modelled.

⚠ **DO NOT READ THIS AS A §3.17 REGRESSION.** §3.17 makes the *shape* final; **§3.19
re-solves the numbers**, via `base-drive` / `base-post` / `base-perimeter` (#038's
ordering rule). **`base-three` must NOT move — 3P% is correct at 35.8 vs a sourced
36.0, and it held across a 1.9× volume change.**

#036 A retired FG% as CONTESTED on a sound measurement — real 47.1%, engine 46.9%, inside
the ±0.14 standard error. **That reading is correct and also incomplete**, because it was
taken at a shot mix that is wrong by 20 points. Decomposed:

| | engine PRE-§3.17 | engine POST-§3.17 | real 2025-26 |
|---|---|---|---|
| 2PA | 69.1 | **55.1** | 52.1 |
| **2P%** | **49.2%** | **~48.7%** | **55.0%** |
| 3PA | 19.6 | **37.2** | 37.0 |
| 3P% | 37.8% | 35.8% | 36.0% |
| **FG% (the aggregate)** | **46.7%** | **43.5%** | **47.1%** |

⚠ **Read the middle column against the right one, not against the left one.** 2PA and
3PA are now both essentially correct; **the entire remaining FG% gap is 2P%**, which is
exactly the separation this pass existed to produce.

**At a 21% three share, FG% ≈ 2P% — so a 5.8-point 2P% deficit and a correct 3P% average
out to an aggregate that looks fine.** At a *correct* 41.5% three share the engine would
need **2P% 53.7%** to land FG% 47.1; it has ~48.7. ⚠ **§3.17 landed and FG% fell to
43.5%, confirming the prediction's direction and cause** (~44.5 was modelled — the
1.0-point miss is the same under-modelling that made FGA land 92.3 against ~89.9).

⚠ **This is #036 B's cancelling-errors finding recurring one level down, in the one place
#036 B did not look** — it decomposed *points* by source (2-pt / 3-pt / FT) but not
*percentages* by shot type. **The lesson generalizes: an aggregate that matches can be two
errors cancelling, at every level, and the only defense is to decompose before declaring a
row green.**

**Who owns it: §3.19.** The lever is `sim.base-drive` / `sim.base-post` /
`sim.base-perimeter`. ⚠ **`sim.base-three` must NOT move — 3P% is correct (37.8 vs a
sourced 36.0), and §3.17 changes how OFTEN a three is taken, never how often it goes in.**
**§3.17 must not tune any of the four**: while the mix and the rates are both moving, the
pass cannot tell "the mix moved" from "the rates were wrong" (#038's ordering rule).

---

## ✅ HISTORY — the fouled-three instrument was broken from §3.16 to §3.17, and is now FIXED

**FIXED by §3.17's Step 0, 2026-08 (`decisions.md` #040 G).** The rows now read
**2.93% of 3PA** and **1.09 3-FT trips**, both corrected. ⚠ **The fix diverged from what
#040 G specified, and the divergence is the interesting part — see the end of this
section.** Kept here because this is the cleanest worked example of CLAUDE.md's trap #2:
*a mechanic change silently broke an instrument, and no test caught it.*

**The engine was always fine. The measurement was not.** Do **not** re-tune
`sim.foul-mult-three` against these rows — the rate is scale-free by construction, and
§3.17 proved it: it held at 2.92% → 2.93% across a **1.9× volume change**.

<details><summary>The original diagnosis (kept — it is the worked example)</summary>

`CalibrationHarness.flushStoppedShot` infers *"was this a stopped THREE?"* from the
**free-throw count** — 3 FTs means a three, anything else a two — because before §3.16
every stopped shot awarded free throws, so the FT run was a faithful proxy for the shot
type.

**§3.16 broke that proxy.** A `COMMON_FOUL` (⚠ **renamed `NON_SHOOTING_FOUL` by §3.17**, #040 M) awards **0** free throws outside the
penalty and **2** inside it, and it never awards 3. So every fouled three that converts
to a common foul is either **invisible** (`freeThrowCount == 0` returns early) or
**miscounted as a two**. With the share at 0.50 the harness sees roughly half of them:

> measured **1.50%** ≈ true **3.0%** × (1 − 0.50) — the arithmetic matches to two
> decimals, which is what identifies this as an artifact rather than a rate change.

**Nothing in the engine changed here**: §3.16 re-partitions the *outcome* of a foul
that `isFoul` already rolled, and `foul-mult-three` still governs how often a three
draws contact at exactly the §3.12 rate (#030 A2). The rows read low because the
instrument lost its signal, not because the mechanic moved.

</details>

### ⚠ THE FIX IS NOT THE ONE THE PLAN SPECIFIED, AND THAT MATTERS

Both this section and #040 G called for *"classify the stopped shot from the `ShotType`
behind the event"*. ⚠ **That is not possible from the event log**, and §3.17's execution
found it immediately: a stopped shot emits **no SHOT event at all** (the foul branch
returns before `recordFieldGoalAttempt()`), and neither `SHOOTING_FOUL` nor
`NON_SHOOTING_FOUL` carries a type suffix the way `MADE_*` / `MISSED_*` / `BLOCKED_*` do.
Adding one would be an **engine** change to the permanent play-by-play vocabulary —
outside a step scoped as test-only.

**What shipped instead is an EXACT correction, not an estimate**, and it exists only
because of how §3.16 built the roll. `FoulResolver.isNonShootingFoul` is a **flat,
shot-type-independent** draw — #039 E deliberately refused to skill-weight or
type-weight it — so the fouls that stay `SHOOTING_FOUL` are an **unbiased sample** of
all stopped shots, taken at rate `(1 − share)`. The harness divides the visible tally by
that fraction and prints **both** figures. The divisor is read from the **active**
config, so an era profile that moves the share keeps the instrument honest.

⚠ **The arithmetic that identified the artifact is the one that now undoes it**:
`1.50 = 3.0 × (1 − 0.50)`. Measured post-fix: raw visible 1.47% → corrected 2.93%.

⚠ **The instrument change is itself a trap.** The `Stopped shots / team` row reads
**12.43** post-§3.17 against **7.65** pre — which looks like the number doubled and is
the exact shape of CLAUDE.md's trap #2. It did not: **like-for-like on the raw visible
tally it fell 7.39 → 6.24**, matching #040 E's predicted 7.93 → 6.41. The **measurement**
changed, not the engine. Always compare raw-to-raw across this boundary.

---

## ⚠ The §3.16 non-shooting-foul share — DERIVED, NOT SOURCED (§3.16, SHIPPED)

`sim.non-shooting-foul-share = 0.50` is the fraction of already-rolled fouls that
become a free-throw-free `NON_SHOOTING_FOUL` (⚠ renamed from `COMMON_FOUL` by §3.17,
#040 M) instead of a `SHOOTING_FOUL`. It is the lever that took FTA from 34.0 to 23.60.

⚠ **§3.17 MISPRICED IT WITHOUT TOUCHING IT, AND FTA NOW UNDERSHOOTS AT 19.76.** The
share is priced by the **penalty rate**, not the foul rate alone — inside the bonus the
foul still awards 2 FTs, so what each conversion actually removes depends on how often
teams are in the penalty. §3.17's shot mix dropped the penalty rate **55.7% → 46.1%**
(fewer fouls ⇒ fewer team-periods reaching the bonus), so each conversion now removes
*more* FTs than 0.50 was solved against. **§3.19's — do not re-tune it against a moving
mix** (#038's ordering rule). ⚠ **Any pass that moves the foul rate must re-check FTA
rather than assume 0.50 still lands.**

**⚠ IT IS BACK-SOLVED AGAINST A SOURCED TARGET, NOT MEASURED FROM BASKETBALL — and
that is a weaker claim than the other numbers in this file.** The real NBA
shooting-foul share is **not in a league-averages row**; deriving it needs
play-by-play data, and it could not be sourced in §3.16. What 0.50 means is *"the
value at which this engine lands on the sourced FTA of 23.5"* — which is exactly
the circularity that makes the foul-out target untrustworthy (see above), entered
knowingly and labelled. **Do not cite 0.50 as what the NBA does.** An earlier
~0.35 figure and a still earlier ~0.59 are both **withdrawn** (#036, #039 D).

**⚠ THE SHARE IS PRICED BY THE PENALTY RATE, so it is NOT a stable constant.**
Each converted foul removes 2 free throws *unless* the committing team is already
in the bonus, in which case it awards 2 instead — so the net FT removed per
conversion depends on the **team-periods-in-penalty** row (55.7%). §3.16's own
charge fix moved that row 51.1% → 55.7%, which is why the share shipped at **0.50**
rather than the **0.43** #039 D predicted from a pre-charge-fix measurement:
conversions remove ~1.47 FTs, not 1.664. **Anything that moves the penalty rate —
including §3.19's pace work — re-prices this share. Re-check FTA; do not assume
the constant still lands.**

**Tune it against FTA, never against points.** It moves both, and points is
§3.19's (#039 H). Buying points here costs accuracy against a *sourced* FTA
target — the one-rate-governing-two-numbers failure mode #036 C named.

---

## ✅ MOST OF THIS TABLE IS NOW SOURCED (2026-08)

**Source: Basketball-Reference NBA league averages, per game, 2025-26 regular
season.** The row was checked for internal consistency before use: FG/FGA = .4714,
3P/3PA = .3595, FT/FTA = .7830, and 2×(FG−3P) + 3×3P + FT = 115.7 ≈ the stated
115.6 PTS.

**What sourcing changed** (`decisions.md` #036):
- **FG% was never contested.** Real **47.1%** vs the engine's 46.9% — a gap *inside*
  the ±0.14 standard error of the 5-seed mean (per-seed spread 46.5–47.3). The
  **target** was wrong; the engine is correct and owes no work (#036 A).
- **Points retargeted ~112 → 115.6.** The remaining +2.7 is **largely an artifact of
  three cancelling composition errors** — 2-pt **+10.9**, 3-pt **−18.0**, FT **+7.1**
  (#036 B). A points-only re-solve would fix the total and leave the game shaped wrong.
- **Two composition gaps owned phases**: FTA at 145% of real (**§3.16 — SHIPPED, now
  23.60**) and 3PA at 54% of real (**§3.17**, still open — the largest remaining
  divergence). Recalibration is **§3.19** and goes last — §3.18 is the steal-attribution
  pass (#038). ⚠ **Points reads 109.6 rather than 118.3 because §3.16 removed ~10 FTA on
  purpose; §3.19 owns recovering it** (#039 H).

⚠️ **STILL UNSOURCED** — these are not in a league-averages row and need play-by-play
derivation or a specialist source: **foul-outs** (and its ~0.39 target is *circular* —
it came from §3.13's own landing), **technicals**, **flagrants**, the **minutes
distribution**, and **the real NBA shooting-foul share** (the number that sizes §3.16).
Deciding a row stays `ballpark` on purpose is a legitimate outcome.

The original two-jobs framing, for reference:

1. **Source the true constraints** — ✅ **largely DONE 2026-08**; what remains is the
   unsourced list above.
2. **Re-solve the constants** against whatever those turn out to be.

**The escalation rule (roadmap.md §3.19) — sourcing a number does not make it
§3.19 work.** §3.19 re-solves anything reachable by turning an **existing knob**.
A gap no existing knob can close is a **new mechanic ⇒ a new sub-phase**.
**Foul-outs are the live example**: §3.13's lever is measured *saturated*, so a
sourced ~0.35–0.45 means no work, while a sourced ~0.15 means escalate — never
re-tune the sit curve, which has been measured to do nothing.

Until job (1) lands, treat every target here as **provisional**: good enough to
steer by, not good enough to spend calibrated headroom defending.

---

## Keeping the numbers honest

| Location | Role |
|---|---|
| **this file** | **source of truth** for targets — **all of them baseline-profile** (#035 I) |
| `CalibrationHarness` `(target ~N)` strings | the operative copy a tuner reads mid-run; **printed on `baseline` only**, replaced by baseline-deltas elsewhere (#035 I) |
| `backlog.md` benchmark chore | **§3.19 job (1)** — largely DONE 2026-08 (#036); the unsourced remainder lives there |
| `roadmap.md` §3.16–§3.19 bullets | the four passes' GOALS + the escalation rule |
| `decisions.md` #022 D | historical — the original §3.4 agreement |
| `decisions.md` #025/#028/#029/#030/#031 | per-phase landings, historical by design |
| `roadmap.md`, `risks.md` | forward-looking guards; should match this table |

**When a target changes:** update this table **and** the harness strings in the
same commit. The per-phase landing notes in decisions.md are **history** — never
retro-edit them.

**When a target becomes SOURCED** (§3.19 job 1): record the **named source and
season** alongside it, and note whether it is a league average or a per-team mean
(they differ) and whether pace-adjusted — the standard the backlog chore sets.
