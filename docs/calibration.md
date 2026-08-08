# Calibration targets

**This file is the source of truth for what the simulation is tuned toward.** The
numbers here supersede the originals set in `decisions.md` #022 D (§3.4) — that
entry records what was first agreed, this table records what is current. When they
disagree, **this file wins**; #022 D is historical on the target question.

The `CalibrationHarness` is the only instrument that measures these. It is
disabled by default and **reports** — it never fails the build:

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml test -pl gametime-app -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000 -DfailIfNoTests=false
```

Vary `-DcalibrationSeed` and **judge by the mean of several runs, never one**:
per-seed noise is **±1.5 points**, enough to bait an over-correction (#029 E).

---

## The five §3.4 targets

The primary aggregates, per team per game. These are what "calibrated" means.

| Measure | Target | §3.12 landing | Status |
|---|---|---|---|
| **Points** | **~112** | **117.0** | ⚠️ **CONTESTED** — see below. ~5 high vs. the stated target |
| **FG%** | **~47%** | **46.4%** | ⚠️ **CONTESTED** — see below. Slightly low |
| **3P%** | ~36% | 36.7% | ✅ on target |
| **Assists** | ~26 | 26.8 | ✅ on target |
| **Turnovers** | ~14 | 13.5 | ✅ on target (~0.5 low) |

*§3.12 landing = 5-seed mean (seeds 1000–5000, ~102 games each), 2026-08.*

### ⚠️ The points and FG% targets are contested (2026-08, §3.12)

**Both were set in §3.4 from unsourced estimates and neither has been revisited
since.** During §3.12's calibration the user checked them against current figures:

| Measure | §3.4 target | Apparent modern-NBA range | Direction |
|---|---|---|---|
| Points | ~112 | **114–117** | target may be ~2–5 too LOW |
| FG% | ~47% | **47–48%** | target may be ~0–1 too LOW |

**Neither range is verified** — both came from search summaries, not a cited
source, which is exactly the standard §3.4's own numbers were set by. They are
recorded here as *evidence that the targets deserve scrutiny*, *not* as new
targets.

**Why this is not fixable by turning one knob, and why §3.12 did not try.** The
only lever that moves points is the shot `BASE_*` rates, and it moves points and
FG% **the same direction** (measured on §3.12's numbers: **~0.50% FG% per 1.0
point**). So against the revised ranges:

- points are ~1–2 **high** → wants a `BASE_*` **trim**
- FG% is ~0.6–1.6 **low** → wants a `BASE_*` **raise**

One lever cannot satisfy both. Reconciling them needs a §3.4-style recalibration
pass against **verified** benchmarks, with a lever that changes efficiency
independently of volume — **not** a foul sub-phase's re-centering step. §3.12
therefore took **no trim** and recorded the finding (decisions.md #030's
implementation note).

**This has now happened three passes running.** §3.10 stopped at 113.8 rather than
112.9, §3.11 took no trim at all, and §3.12 took none — each time because the trim
cost more calibrated FG% than the points miss was worth. Three consecutive passes
declining the same lever is evidence about the **target**, not about the passes.
Tracked in risks.md.

---

## Secondary targets

### §3.5 minutes distribution (user-agreed)

| Measure | Target |
|---|---|
| Top starter | ~34–36 min |
| Ceiling (anyone) | no one over ~42 min |
| Bench | scaled down by `rotationDepth` |

Also watched: **period-by-period FG%**, which correctly stays roughly *flat*
rather than sagging — substitution cycles fresh legs in, so fatigue shows up as
*who is on the floor*, not as a late-game efficiency collapse (#023 E).

### Per-phase rates (§3.7–§3.12)

| Measure | Target / ballpark | §3.12 landing | Source |
|---|---|---|---|
| Blocks / team / game | ~5 | 4.8 | #025 C — a real target |
| OOB / team / game | no target | 2.8 | #026 D — visible, untargeted |
| Turnover cause mix | STOLEN dominant (~55–60%) | 55.0% | #027 E — no per-cause target |
| Fouls / team / period | (bonus at 5) | 4.87 | #028 E |
| Team-periods in penalty | observation, not a knob | 52.3% | #028 D / #030 D |
| And-1s / team / game | ~4–6% of made FG | 1.87 (4.6%) | #029 E |
| Fouled-three rate | **~2% of 3PA** | 2.9% | #030 G — anchor on the RATE |

---

## Plausibility ballparks — NOT targets

**These are ranges to judge plausibility against, deliberately *not* calibration
targets** (#030 G). Promoting them would fabricate constraints ahead of a consumer
(#017) and over-constrain a five-target calibration that is already hard to
satisfy. Judge against them; **do not calibrate to them.**

| Measure | Ballpark | §3.12 landing | Verdict |
|---|---|---|---|
| Fouls / team / game | ~19–20 | **19.1** | ✅ in range (was 16.8 pre-§3.12) |
| **Foul-outs / team / game** | **~0.1–0.25** | **0.60** | ❌ **~2.4× high — see below** |
| 3-FT trips / team / game | ~0.3–0.6 expected here | 0.59 | ✅ in range |

**On the 3-FT trips row:** the real-NBA count is ~0.7, but this engine shoots
**~20 3PA/team/game against the NBA's ~35**, so an identical *rate* necessarily
yields fewer *trips*. **Anchor on the rate (~2% of 3PA), never the count** — a
builder who "corrects" 0.59 up to 0.7 silently undoes #030 G's agreed multiplier.
(The sub-NBA 3PA volume is a separate pre-existing observation, not §3.12's.)

### ⚠️ Foul-outs run ~2.4× high, and it PREDATES §3.12

Measured for the first time in §3.12 (#030 G added the instrument — the mechanism
has been live since §3.5 but nobody had ever looked).

| Configuration | Foul-outs / team / game |
|---|---|
| §3.11 foul reach (§3.12 multipliers zeroed, 5 seeds) | **0.425** |
| §3.12 as shipped (5 seeds) | **0.60** |
| Plausibility ballpark | ~0.1–0.25 |

**~70% of the overshoot predates §3.12** — the pass adds only +0.18. Per #030 G's
explicit direction, this was **triaged separately, not absorbed into §3.12's
recalibration**. It is now its own sub-phase (**§3.13**, ahead of flagrants).

**The naive fix is a trap:** "fouls are high, trim the foul rate" reaches for
`BASE_NO_BASKET_FOUL`, which is a **wrong-way lever** — trimming it *raises* points
(#028, measured). The likely real fix is behavioral — a coach benching a player in
foul trouble — which is §3.5/rotation territory, not the foul rate.

---

## Where the numbers live (don't let them drift)

| Location | Role |
|---|---|
| **`docs/calibration.md`** (this file) | **source of truth** for targets |
| `CalibrationHarness.java` (the `(target ~N)` strings) | the operative copy — what a tuner reads mid-run |
| `decisions.md` #022 D | **historical** — the original §3.4 agreement |
| `decisions.md` #025/#028/#029/#030 | per-phase landings, historical by design |
| `roadmap.md`, `risks.md` | forward-looking guards; should match this table |

**When a target changes:** update this table **and** the harness strings together.
The per-phase landing notes in decisions.md are **history** — do not retro-edit
them; they record what was true when each phase shipped.
