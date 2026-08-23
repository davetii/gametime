# Calibration targets

**Source of truth for what the simulation is tuned toward.**

⚠ **Recalibration is §3.20.** It has been renumbered four times (§3.16 → §3.18 → §3.19 →
§3.20); **§3.19 is now the instrumentation pass.** Read the phase name, not the number.

Measured by `CalibrationHarness` — disabled by default, and it **reports, never gates**:

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml test -pl gametime-app -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000 -DfailIfNoTests=false
```

⚠ **The harness needs its profile from the ENVIRONMENT** — `SPRING_PROFILES_ACTIVE=local,baseline`.
A `-D` flag does not reach the forked Surefire JVM, and the failure looks like a database
error rather than a profile one. **Confirm the report's `Profiles:` line before trusting
any number.**

⚠ **Judge by the mean of several `-DcalibrationSeed` runs, never one** — per-seed noise is
±1.5 points, enough to bait an over-correction. Some rows need 5 seeds as a hard floor and
say so.

> ⚠️ **EVERY TARGET IN THIS FILE BELONGS TO THE `baseline` PROFILE.** The sim profile is
> chosen by the ordinary Spring profile list (`baseline` must stay in it), and an era
> profile — a 1990s low-pace/high-foul style, a modern three-heavy one — is **SUPPOSED to
> miss most of these; that is the profile working, not a failure.** On a non-baseline run
> the harness suppresses the `(target ~N)` strings and prints a **delta against the
> baseline landing** instead. **A delta is not a target**: it claims nothing about what a
> number should be, only what changed.
> **There is no second, per-profile target set** — authoring era targets is real research
> and would create a second unsourced table beside the one §3.20 exists to fix.

---

## Everything, in one table

All figures are **per team per game** unless noted. Current = the **5-seed mean, seeds
1000–5000**, re-measured 2026-08 at the end of §3.19 on the `baseline` profile — **this
is §3.20's input**. §3.19 was test-side only and moved no engine number (proven
same-seed before/after); the rows that moved against the previous reading were stale, not
new. **Target column: sourced rows are Basketball-Reference league averages, per game,
2025-26.**

✅ **The harness now self-verifies three identities, and all three read OK on every one
of those five seeds** — assists+blocks vs. the box score (pre-existing), **FT sources**
(the per-source counts sum to total FTs with an empty `UNKNOWN` bucket) and **points**
(events = box score = final score), both added by §3.19. A number below is therefore not
lying about *what it counted*; whether the engine should produce it is §3.20's question.
⚠ **A `MISMATCH(es)` on any of those lines invalidates the rows it feeds — fix the
instrument before reading anything.**

**Type** — `TARGET` = calibrated, steer by it. `ballpark` = a plausibility range: judge
against it but **do not calibrate to it**. `observed` = no target, reported for
visibility.

| Measure | Type | Target / range | Current | Status |
|---|---|---|---|---|
| **Points** | **TARGET (SOURCED)** | **115.6** | **110.0** | ⚠️ **−5.6 BY DESIGN** — the non-shooting foul removed ~10 FTA on purpose. **§3.20 recovers it** |
| **FG%** | **TARGET (SOURCED)** | **47.1%** | **43.5%** | ⚠️ **LOW ON PURPOSE, not a regression** — the three-heavy mix un-hid a 2P% error the old mix masked. **§3.20's**, via `base-drive` / `base-post` / `base-perimeter`. ⚠ **`base-three` must NOT move.** See *The 2P% gap* |
| **2P%** *(derived — not a harness row)* | **TARGET (SOURCED)** | **55.0%** | **~48.7%** | 🔴 **−6.3 — the largest open gap, and the main thing §3.20 owns** |
| 3P% | **TARGET (SOURCED)** | **36.0%** | 35.8% | ✅ held across a 1.9× volume change |
| Assists | **TARGET (SOURCED)** | **26.7** | 26.1 | ✅ inside the band |
| Turnovers | **TARGET (SOURCED)** | **14.5** | 13.9 | ✅ ⚠ steals depend on this — see the Steals row |
| Blocks | **TARGET (SOURCED)** | **4.8** | 4.80 | ✅ ⚠️ **GREEN FOR THE WRONG REASON — `PROB_FLOOR` (0.02) is 4× `base-block-three` (0.005), so a three's block chance is FLOORED, not based, and the constant is INERT.** ⚠ **Effect sized at ~half a blocked three per team-game (0.74 vs 0.19) — NOT worth chasing**, and closed on that basis. Carry only as a footnote: **if a pass ever tunes `base-block-*`, reroute through `clampRareProbability` first**, or that one lever reads dead |
| Top-starter minutes | TARGET | ~34–36 | ~36.1 | ✅ |
| Minutes ceiling | TARGET | nobody over ~42 | ok | ✅ |
| Fouls | **TARGET (SOURCED)** | **19.9** | 18.18 | 🟡 −2.35, a by-product of the shot mix (~17 draws/team/game moved from `foul-mult` 1.0 to 0.133). `PERSONAL_FOULS_PER_TEAM_GAME` re-measured to **17.82**. §3.20's |
| **FTA** | **TARGET (SOURCED)** | **23.5** | **19.76** | 🟡 **−3.7.** ⚠ **`sim.non-shooting-foul-share` (0.50) is priced by the PENALTY RATE, not the foul rate alone** — the penalty rate fell to 46.1%, so the share is now mispriced. **§3.20's — do NOT re-tune it before then** |
| **3PA** | **TARGET (SOURCED)** | **37.0** | **37.22** | ✅ via `sim.shot-share-*` = 1.0 / 0.55 / 0.55 / **1.23**. Charged three share **40.3%** vs a real 41.5% |
| FGA | **TARGET (SOURCED)** | **89.1** | 92.28 | 🟡 **OVER by 3.2, expected.** A stopped shot charges no FGA and `foul-mult-three` is 0.133 vs DRIVE's 1.0, so more threes ⇒ fewer stopped shots ⇒ **more** FGA. **§3.20's** (it owns pace) |
| Off rebounds | **TARGET (SOURCED)** | **11.3** | 11.18 | ✅ |
| Def rebounds | **TARGET (SOURCED)** | **32.4** | 30.48 | 🟡 residual ~1.9 is a **rate** question for §3.20 |
| Pace (poss/48) | **TARGET (SOURCED)** | **99.4** | ~100 nominal | ✅ |
| Steals | observed | **8.4** | **7.72** | 🔴 **−0.68 (10.3 se) — real, not seed noise.** ⚠ **A DERIVED quantity**: steals = turnovers × STOLEN share, and *both* terms are §3.20's (TO 13.90 vs 14.5; share 55.5%, inside the intended 55–60% band). **Fixing TO to 14.5 alone yields 8.05** — re-measure after it lands, do **not** tune the share independently |
| **Foul-outs** | **TARGET** (soft, **UNSOURCED**) | **~0.39** | **0.304** | ⚠ **A LANDING, not a benchmark** — see *Foul-outs* |
| Players at 4 / 5 / 6 fouls | ballpark | *(no range yet)* | 0.94 / 0.46 / 0.30 | **the real diagnostic for foul-outs** — judge by this, not the headline count |
| **Technicals** | ballpark | **~0.3–0.4** (~0.6–0.8 league-wide) | **0.358** | ⚠️ **judge at 5 SEEDS ONLY** — see *Technicals* |
| **Flagrants** | ballpark (**UNSOURCED**) | **~0.13–0.20** (~0.25–0.40 league-wide) | **0.141** | ⚠️ **the COARSEST row here — 5 SEEDS ONLY.** ⚠ Its divisor is a **measured foul rate**: any pass that moves fouls must re-measure it, or this row silently reads low |
| Flagrant-2s | *(no target — a 15% share)* | — | **0.023** | the ejection driver |
| **Ejections** | *(no target — an outcome)* | — | **0.033** | both causes (technicals alone: ~0.00) |
| Fouled-three rate | ballpark | **~2% of 3PA** | **2.93%** *(corrected)* | ✅ scale-free: held across a 1.9× volume change. ⚠ Raw visible tally reads **1.46%** — the corrected figure is the one to judge |
| 3-FT trips | ballpark | ~0.3–0.6 *here* | **1.09** *(corrected)* | the **count** tripled with 3PA while the **rate** above held. ⚠ The 0.3–0.6 range was set at half the current 3PA |
| And-1s | ballpark | ~4–6% of made FG | 1.55 (3.9%) | 🟡 just under the band; moves with the shot mix (§3.20's) |
| Fouls / team / period | observed | — | 4.58 | bonus at 5 — ⚠️ **AT the threshold**, which is why the penalty rate is volatile |
| Team-periods in penalty | observed | — | 46.1% | ⚠️ **a result, not a knob — but it PRICES the FTA share above** |
| Out of bounds | observed | — | 3.1 | |
| Turnover cause mix | observed | STOLEN dominant | 55.5% | no per-cause target |
| Period-by-period FG% | observed | flat, not sagging | flat | correct fatigue behavior |

---

## Foul-outs — a soft target, and the lever is saturated

**Current 0.304 against a soft ~0.39.** ⚠ **The ~0.39 is a LANDING promoted to a target,
not a benchmark** — it is unsourced and partly circular, so treat it as a direction, not
a number to hit. The real-basketball ballpark is ~0.1–0.25 and the engine sits above it.

⚠ **The foul-trouble bench lever is MEASURED SATURATED — do not re-tune the sit curve.**
Sitting a player in foul trouble prevents *some* sixth fouls but also returns him to the
floor later, so pushing the curve harder trades one disqualification for another.

⚠ **Judge foul-outs by the 4/5/6 distribution, not the headline count.** The count is one
number over a long tail; the distribution shows whether the shape is right.

⚠ **The naive fix is a trap: trimming the foul RATE to reduce foul-outs breaks FTA and
points**, which are sourced. The root cause is **defender selection over-dispersion** —
the same defenders are picked too often, so fouls concentrate — not foul volume.

---

## Technicals — a ballpark, and a row a single seed CANNOT resolve

**A `ballpark`, not a TARGET, deliberately.** Nothing is tuned toward it:
`SimConfig.TECHNICAL_FOULS_PER_TEAM_GAME` is set **directly from** the real-world figure,
so the harness line is a **correctness check that the constant is wired right**, not a
calibration objective.

⚠ **Judge at 5 seeds only — a hard floor, not the usual "prefer the mean" advice:**

| Sample | Events | Relative sd |
|---|---|---|
| 1 seed (102 games) | ~71 | **11.8%** |
| 5 seeds | ~357 | **5.3%** |

⚠ **The landing sits a few percent above the constant BY DESIGN.** The constant's divisor
is the **nominal** possession count while the real one is pace-scaled — a fast game takes
more rotation checks and draws more technicals. **Do not back-solve the constant against
that gap; it is behavior, not error.**

⚠ **The per-check probability is ~0.00175**, not ~0.0035: both rotations advance every
possession, so a team is checked ~200 times per game, not ~100. This also means
`PROB_FLOOR` is **>10×** the per-check rate, which is why this rate must use the
floor-free clamp.

---

## Flagrants — the coarsest row in this file

**A `ballpark`, not a TARGET**, for the same reason as technicals: the rate is configured
from a real-world figure rather than tuned toward one.

⚠ **5 seeds is a hard floor and even then the reading is coarse** — at 102 games this is
~33 events, a relative sd of 17.4%.

⚠ **Its divisor is EMERGENT, which is what makes this row fragile.** The flagrant rate is
expressed against a **measured personal-foul rate**, so **any pass that moves the foul
rate must re-measure that divisor.** Left stale it has run at **74% of target** — exactly
the ratio of the stale to the true foul rate — and **nothing would have failed**. The
coupling reaches further than "a pass that changes fouls": a pass that only changed the
*shot mix* moved the foul rate too.

⚠ **`Fouls / team / game` does NOT include flagrants**, so the two lines are not additive.

---

## The 2P% gap — FG% is visibly wrong, and that is the point

⚠ **The engine's 2P% did not get worse. It stopped being hidden.**

| | 2PA | 2P% | 3PA | 3P% | FG% (aggregate) |
|---|---|---|---|---|---|
| engine, old two-heavy mix | 69.1 | 49.2% | 19.6 | 37.8% | 46.7% |
| **engine now** | **55.1** | **~48.7%** | **37.2** | **35.8%** | **43.5%** |
| real 2025-26 | 52.1 | **55.0%** | 37.0 | 36.0% | **47.1%** |

⚠ **Read the middle row against the bottom one.** 2PA and 3PA are both essentially
correct now; **the entire remaining FG% gap is 2P%.**

**At a 21% three share, FG% ≈ 2P%** — so a ~6-point 2P% deficit and a correct 3P%
averaged out to an aggregate that looked fine. At the correct ~41% three share the error
is visible, which is exactly the separation the shot-mix work existed to produce.

⚠ **§3.20 fixes this via `base-drive` / `base-post` / `base-perimeter`. `base-three` must
NOT move** — 3P% is correct at 35.8 vs a sourced 36.0, and it held across a 1.9× volume
change.

---

## The non-shooting-foul share — DERIVED, not sourced

`sim.non-shooting-foul-share` = **0.50**. ⚠ **This is the value that lands FTA on a
sourced target — it is NOT a measurement of what real basketball does.** The real
shooting-foul share is not in a league-averages row and needs play-by-play derivation.

⚠ **It is priced by the PENALTY RATE, not the foul rate alone**: a non-shooting foul
awards 2 bonus FTs inside the penalty and none outside it, so what the knob removes per
conversion depends on how often teams are in the penalty. **Any pass that moves the foul
rate must re-check FTA rather than assume this value still lands it.** The penalty rate
has since fallen to 46.1%, so the share is currently mispriced — **§3.20's**.

⚠ **Tune it against the FTA line, never against points.**

---

## Keeping the numbers honest

| Location | Role |
|---|---|
| **this file** | **source of truth** for targets — all of them baseline-profile |
| `CalibrationHarness` `(target ~N)` strings | the operative copy a tuner reads mid-run; printed on `baseline` only |
| `roadmap.md` §3.20 bullet | the recalibration pass's goals and sequencing |
| `decisions.md` | **the history** — why each target is what it is, and each phase's landing |

**When a target changes:** update this table **and** the harness strings in the same
commit.

**When a target becomes SOURCED:** record the **named source and season** alongside it,
and note whether it is a league average or a per-team mean (they differ) and whether
pace-adjusted.

⚠ **Still UNSOURCED**, and flagged as such above: **foul-outs** (its ~0.39 came from a
prior landing — circular), **technicals**, **flagrants**, the **minutes distribution**,
and the real **shooting-foul share**. Everything marked `TARGET (SOURCED)` is a
Basketball-Reference league average, per game, 2025-26.

⚠ **A number that moves is not automatically an engine change.** Before tuning anything,
ask: **did the engine change, did the MEASUREMENT change, or is a clamp holding it?** All
three have happened here — an instrument that miscounted a row, a corrected instrument
that made a falling number look like it doubled, and `PROB_FLOOR` holding a rate that a
constant appeared to set. **Compare raw-to-raw across an instrument change; never compare
a corrected number to an uncorrected one.**

- **`docs/game-events.md`** — the event vocabulary these numbers are counted from.
