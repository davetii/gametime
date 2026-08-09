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

All figures are **per team per game** unless noted. Current = §3.12 landing,
5-seed mean (2026-08).

**Type** — `TARGET` = calibrated, steer by it. `ballpark` = plausibility range,
judge against it but **do not calibrate to it** (#030 G, #017). `observed` = no
target, reported for visibility.

| Measure | Type | Target / range | Current | Status |
|---|---|---|---|---|
| **Points** | TARGET | ~112 | **117.0** | ⚠️ **CONTESTED** — see below |
| **FG%** | TARGET | ~47% | **46.4%** | ⚠️ **CONTESTED** — see below |
| 3P% | TARGET | ~36% | 36.7% | ✅ |
| Assists | TARGET | ~26 | 26.8 | ✅ |
| Turnovers | TARGET | ~14 | 13.5 | ✅ (~0.5 low) |
| Blocks | TARGET | ~5 | 4.8 | ✅ (#025 C) |
| Top-starter minutes | TARGET | ~34–36 | ~36.6 | ✅ (#023) |
| Minutes ceiling | TARGET | nobody over ~42 | ok | ✅ (#023) |
| Fouls | ballpark | ~19–20 | 19.1 | ✅ (was 16.8 pre-§3.12) |
| **Foul-outs** | ballpark | **~0.1–0.25** | **0.60** | ❌ **~2.4× high — see below** |
| Players at 4 / 5 / 6 fouls | ballpark | *(no range yet)* | 0.84 / 0.45 / 0.59 | the real diagnostic for foul-outs |
| Fouled-three rate | ballpark | **~2% of 3PA** | 2.9% | ✅ anchor on the RATE, not the count |
| 3-FT trips | ballpark | ~0.3–0.6 *here* | 0.59 | ✅ (real NBA ~0.7 off ~35 3PA; we shoot ~20) |
| And-1s | ballpark | ~4–6% of made FG | 1.87 (4.6%) | ✅ (#029 E) |
| Fouls / team / period | observed | — | 4.87 | bonus at 5 (#028) |
| Team-periods in penalty | observed | — | 52.3% | a result, not a knob (#030 D) |
| Out of bounds | observed | — | 2.8 | (#026 D) |
| Turnover cause mix | observed | STOLEN dominant | 55.0% | no per-cause target (#027 E) |
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

## ⚠️ Foul-outs run high, and it PREDATES §3.12

| Configuration | Foul-outs |
|---|---|
| §3.11 foul reach (§3.12 multipliers zeroed, 5 seeds) | **0.425** |
| §3.12 as shipped | **0.60** |
| Ballpark | ~0.1–0.25 |

**~70% of the overshoot predates §3.12** (it adds only +0.18), so per #030 G it
was **triaged separately** → **§3.13**, ahead of flagrants.

**The naive fix is a trap:** trimming the foul rate means `BASE_NO_BASKET_FOUL`,
a **wrong-way lever** — it *raises* points (#028, measured). **Judge §3.13 by the
4/5/6 distribution, not the headline count** — foul-outs are a tail phenomenon, and
a benching rule works by moving players out of the 5-foul bucket before they reach 6.

**§3.13's design pass decomposed it (#031 A), and the cause is not what the
headline suggests.** Fouls are **over-dispersed**: `ShotSelector.pickDefender`
weights the defender draw by `individualDefense` (measured sd **4.11** over 359
players, a 3.2× spread) while `foulProne` is nearly flat (sd 1.22), so the best
defenders absorb far more fouls than a flat per-minute rate would give them. A
flat-rate Poisson over the engine's own minutes predicts **0.262** against the
measured **0.593** — **2.3×**. That concentration is *correct realism*; what is
missing is the coach who benches the player, which is what §3.13 adds. **The foul
rate itself is NOT implicated** (19.0/team/game is inside its own ballpark), and
neither the defender weighting nor `foulProne` may be touched to fix this.

**Promotion to a TARGET is a §3.13 close-out decision** — once a phase has
deliberately tuned against it, someone owns it, and **#031 H commits §3.13 to making
that promotion** (updating this row *and* the harness `(target ~N)` string in the
same change). The number stays **soft** even then, because it is unsourced (0.11
from #030 G, 0.15–0.25 from a search) — verifying it belongs with §3.16's
benchmark-verification prerequisite.

**A landing near ~0.3 may be the right answer.** #031 G budgets the minutes cost:
reaching ~0.1–0.25 costs roughly 1–2.5 minutes off the top starter, who sits at
**36.6** against a **calibrated** ~34–36 target. A calibrated target outranks an
unsourced ballpark, so §3.13 carries an explicit stop condition rather than chasing
the low end.

---

## Keeping the numbers honest

| Location | Role |
|---|---|
| **this file** | **source of truth** for targets |
| `CalibrationHarness` `(target ~N)` strings | the operative copy a tuner reads mid-run |
| `decisions.md` #022 D | historical — the original §3.4 agreement |
| `decisions.md` #025/#028/#029/#030 | per-phase landings, historical by design |
| `roadmap.md`, `risks.md` | forward-looking guards; should match this table |

**When a target changes:** update this table **and** the harness strings in the
same commit. The per-phase landing notes in decisions.md are **history** — never
retro-edit them.
