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
> `decisions.md` #035 I — design resolved, not yet built)*. Once profiles exist, the
> sim profile is chosen by the ordinary Spring profile list (`-Dspring.profiles.active=test,nineties`)
> beside the seed flag, and an era profile
> (a 1990s low-pace/high-foul style, a modern three-heavy one) is **SUPPOSED to miss
> most of these — that is the profile working, not a failure.** So on a non-baseline
> run the harness **suppresses the `(target ~N)` strings** and prints a **delta against
> the baseline landing** instead. **A delta is not a target**: it claims nothing about
> what a number should be, only what changed.
> **Nothing here is per-profile, and §3.15 did not add a second target set** — authoring
> era targets is real research and would create a second unsourced table beside the one
> §3.16 exists to fix (#017's don't-fabricate-a-constraint rule, applied to targets).

---

## Everything, in one table

All figures are **per team per game** unless noted. Current = **§3.14b landing,
5-seed mean, seeds 1000–5000** (2026-08) — the `baseline` profile.

**Type** — `TARGET` = calibrated, steer by it. `ballpark` = plausibility range,
judge against it but **do not calibrate to it** (#030 G, #017). `observed` = no
target, reported for visibility.

| Measure | Type | Target / range | Current | Status |
|---|---|---|---|---|
| **Points** | TARGET | ~112 | **118.3** | ⚠️ **CONTESTED** — see below |
| **FG%** | TARGET | ~47% | **46.9%** | ⚠️ **CONTESTED** — see below |
| 3P% | TARGET | ~36% | 36.7% | ✅ |
| Assists | TARGET | ~26 | 27.1 | ✅ |
| Turnovers | TARGET | ~14 | 13.6 | ✅ |
| Blocks | TARGET | ~5 | 5.0 | ✅ (#025 C) |
| Top-starter minutes | TARGET | ~34–36 | ~36.1 | ✅ (#023; §3.13 cost 0.5) |
| Minutes ceiling | TARGET | nobody over ~42 | ok | ✅ (#023) |
| Fouls | ballpark | ~19–20 | 19.35 | ✅ (was 16.8 pre-§3.12) |
| **Foul-outs** | **TARGET** (soft, **UNSOURCED**) | **~0.39** | **0.358** | ⚠️ the target came FROM §3.13's landing — see below |
| Players at 4 / 5 / 6 fouls | ballpark | *(no range yet)* | 1.00 / 0.52 / 0.39 | the real diagnostic for foul-outs |
| **Technicals** | ballpark | **~0.3–0.4** (~0.6–0.8 league-wide) | **0.367** | ⚠️ **judge at 5 SEEDS ONLY** — see below |
| **Flagrants** | ballpark (**UNSOURCED**) | **~0.13–0.20** (~0.25–0.40 league-wide) | **0.148** | ⚠️ **the COARSEST row here — 5 SEEDS ONLY** — see below |
| Flagrant-2s | *(no target — a 15% share)* | — | **0.023** | §3.14b; the ejection driver |
| **Ejections** | *(no target — an outcome)* | — | **0.027** | §3.14b; BOTH causes (§3.14a alone was 0.014) |
| Fouled-three rate | ballpark | **~2% of 3PA** | 3.0% | ✅ anchor on the RATE, not the count |
| 3-FT trips | ballpark | ~0.3–0.6 *here* | 0.59 | ✅ (real NBA ~0.7 off ~35 3PA; we shoot ~20) |
| And-1s | ballpark | ~4–6% of made FG | 1.88 (4.6%) | ✅ (#029 E) |
| Fouls / team / period | observed | — | 4.85 | bonus at 5 (#028) |
| Team-periods in penalty | observed | — | 51.9% | a result, not a knob (#030 D) |
| Out of bounds | observed | — | 2.8 | (#026 D) |
| Turnover cause mix | observed | STOLEN dominant | 56.8% | no per-cause target (#027 E) |
| 3PA / team / game | observed | — | 20.1 | vs the NBA's ~35 — a §3.16 input (#030 follow-up) |
| Period-by-period FG% | observed | flat, not sagging | flat | correct §3.5 behavior (#023 E) |

---

## ⚠️ Points and FG% are contested (2026-08, §3.12)

Both were set in §3.4 from **unsourced estimates** and never revisited. Checked
against current figures during §3.12:

| | §3.4 target | Apparent modern-NBA | Direction |
|---|---|---|---|
| Points | ~112 | 114–117 | target may be too LOW |
| FG% | ~47% | 47–48% | target may be too LOW |

**Neither range is verified** — both are search summaries, the same standard
§3.4's own numbers were set by. Evidence the targets need scrutiny, *not* new
targets.

**One lever cannot fix both.** Shot `BASE_*` moves points and FG% the **same
direction** (~0.50% FG% per 1.0 point, measured on §3.12's numbers). Points are
~1–2 high (wants a trim); FG% is ~0.6–1.6 low (wants a raise). Reconciling them
needs a lever separating **efficiency from volume** — that is **§3.16**, not a
foul phase's re-centering step.

**Three passes running have declined the same trim** — §3.10 stopped at 113.8,
§3.11 took none, §3.12 took none, each because it cost more calibrated FG% than
the points miss was worth. That is evidence about the *target*, not the passes.
Tracked in risks.md.

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
from a search). **§3.16's benchmark-verification prerequisite should source the
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
from the real-world number directly. **UNSOURCED**, like every row here — §3.16's job (1).

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
**measured personal-foul rate**. So **§3.16 — or any pass that moves the foul rate —
moves flagrants too**, without anyone touching `FLAGRANT_FOULS_PER_TEAM_GAME`.
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
inferred from the aggregates. Nothing was re-centered; **§3.16 owns the contested pair.**

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

## ⚠️ NOTHING IN THIS TABLE IS SOURCED YET — and that is §3.16's job (1)

**Every `TARGET` above was set from an unsourced estimate**, including the ones
marked ✅. The ✅ means "the engine is where we said we wanted it," **not** "we
verified that is where the NBA is." §3.16 is explicitly **two jobs in sequence**:

1. **Source the true constraints** — the [backlog.md](backlog.md) research chore.
   In scope: **points, FG%** (the CONTESTED pair), **foul-outs** (soft, added by
   §3.13), **technicals** (ballpark, added by §3.14a — the ~0.6–0.8 league figure is
   user-supplied and unverified), and the load-bearing ballparks (fouls ~19–20,
   blocks ~5).
2. **Re-solve the constants** against whatever those turn out to be.

**The escalation rule (roadmap.md §3.16) — sourcing a number does not make it
§3.16 work.** §3.16 re-solves anything reachable by turning an **existing knob**.
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
| `backlog.md` benchmark chore | **§3.16 job (1)** — where the sourced figures will come from |
| `roadmap.md` §3.16 bullet | the pass's GOALS + the escalation rule |
| `decisions.md` #022 D | historical — the original §3.4 agreement |
| `decisions.md` #025/#028/#029/#030/#031 | per-phase landings, historical by design |
| `roadmap.md`, `risks.md` | forward-looking guards; should match this table |

**When a target changes:** update this table **and** the harness strings in the
same commit. The per-phase landing notes in decisions.md are **history** — never
retro-edit them.

**When a target becomes SOURCED** (§3.16 job 1): record the **named source and
season** alongside it, and note whether it is a league average or a per-team mean
(they differ) and whether pace-adjusted — the standard the backlog chore sets.
