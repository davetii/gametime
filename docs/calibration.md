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

---

## Everything, in one table

All figures are **per team per game** unless noted. Current = **§3.13 landing,
5-seed mean** (2026-08).

**Type** — `TARGET` = calibrated, steer by it. `ballpark` = plausibility range,
judge against it but **do not calibrate to it** (#030 G, #017). `observed` = no
target, reported for visibility.

| Measure | Type | Target / range | Current | Status |
|---|---|---|---|---|
| **Points** | TARGET | ~112 | **117.0** | ⚠️ **CONTESTED** — see below |
| **FG%** | TARGET | ~47% | **46.6%** | ⚠️ **CONTESTED** — see below |
| 3P% | TARGET | ~36% | 36.6% | ✅ |
| Assists | TARGET | ~26 | 26.7 | ✅ |
| Turnovers | TARGET | ~14 | 13.8 | ✅ |
| Blocks | TARGET | ~5 | 5.0 | ✅ (#025 C) |
| Top-starter minutes | TARGET | ~34–36 | ~36.1 | ✅ (#023; §3.13 cost 0.5) |
| Minutes ceiling | TARGET | nobody over ~42 | ok | ✅ (#023) |
| Fouls | ballpark | ~19–20 | 19.0 | ✅ (was 16.8 pre-§3.12) |
| **Foul-outs** | **TARGET** (soft, **UNSOURCED**) | **~0.39** | **0.39** | ⚠️ the target IS the landing — see below |
| Players at 4 / 5 / 6 fouls | ballpark | *(no range yet)* | 1.00 / 0.52 / 0.39 | the real diagnostic for foul-outs |
| Fouled-three rate | ballpark | **~2% of 3PA** | 3.0% | ✅ anchor on the RATE, not the count |
| 3-FT trips | ballpark | ~0.3–0.6 *here* | 0.59 | ✅ (real NBA ~0.7 off ~35 3PA; we shoot ~20) |
| And-1s | ballpark | ~4–6% of made FG | 1.88 (4.6%) | ✅ (#029 E) |
| Fouls / team / period | observed | — | 4.85 | bonus at 5 (#028) |
| Team-periods in penalty | observed | — | 51.3% | a result, not a knob (#030 D) |
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

---

## ⚠️ NOTHING IN THIS TABLE IS SOURCED YET — and that is §3.16's job (1)

**Every `TARGET` above was set from an unsourced estimate**, including the ones
marked ✅. The ✅ means "the engine is where we said we wanted it," **not** "we
verified that is where the NBA is." §3.16 is explicitly **two jobs in sequence**:

1. **Source the true constraints** — the [backlog.md](backlog.md) research chore.
   In scope: **points, FG%** (the CONTESTED pair), **foul-outs** (soft, added by
   §3.13), and the load-bearing ballparks (fouls ~19–20, blocks ~5).
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
| **this file** | **source of truth** for targets |
| `CalibrationHarness` `(target ~N)` strings | the operative copy a tuner reads mid-run |
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
