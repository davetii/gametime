# Calibration targets

**Source of truth for what the simulation is tuned toward.**

⚠ **Recalibration was §3.20, and it has SHIPPED** (`decisions.md` #042). It was renumbered
four times (§3.16 → §3.18 → §3.19 → §3.20), so **read the phase name, not the number** —
§3.19 is the instrumentation pass. **§3.21 (the rebound pool) is next** and owns the two
rebound rows below.

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
1000–5000**, re-measured 2026-08 at the **§3.20 recalibration landing** on the `baseline`
profile. **Target column: sourced rows are Basketball-Reference league averages, per game,
2025-26.**

⚠ **Four rows are KNOWN RESIDUALS, deliberately left out of band by §3.20** — def
rebounds, off rebounds, fouls and 3P%. Each is marked below with its reason. **They are
not drift, and not a to-do list for the next tuner**: the two rebound rows have **no lever
at all** (the *pool* is short, not the split), 3P% is a **band problem rather than an
engine one**, and **Fouls is half of an over-determined pair with FGA** — one lever, and
FGA is the half that landed. See `decisions.md` #042's implementation note (D3, D6).

✅ **The harness now self-verifies three identities, and all three read OK on every one
of those five seeds** — assists+blocks vs. the box score (pre-existing), **FT sources**
(the per-source counts sum to total FTs with an empty `UNKNOWN` bucket) and **points**
(events = box score = final score), both added by §3.19. A number below is therefore not
lying about *what it counted*; whether the engine should produce it is a calibration
question, not an instrument one.
⚠ **A `MISMATCH(es)` on any of those lines invalidates the rows it feeds — fix the
instrument before reading anything.**

**Type** — `TARGET` = calibrated, steer by it. `ballpark` = a plausibility range: judge
against it but **do not calibrate to it**. `observed` = no target, reported for
visibility.

| Measure | Type | Target / range | Current | Status |
|---|---|---|---|---|
| **Points** | **TARGET (SOURCED)** | **115.6** | **114.86** | ✅ **§3.20 LANDED IT** (−0.74, band ±0.9). ⚠ It is **not an independent row** — points track **FGA**, because threes and FTs sit on target and the swing is two-point *volume*. Tune FGA, not this |
| **FG%** | **TARGET (SOURCED)** | **47.1%** | **46.86%** | ✅ **§3.20 LANDED IT** (43.5 → 47.0) via `base-drive` / `base-post` / `base-perimeter`. ⚠ **`base-three` must NOT move** (#040 F) |
| **2P%** *(derived — not a harness row)* | **TARGET (SOURCED)** | **55.0%** | **54.67%** | ✅ **§3.20 LANDED IT** (48.8 → 55.0) — the file's largest open gap, closed. Compute it as `(FGA×FG% − 3PA×3P%) / (FGA − 3PA)`. ⚠ **The bases pass through at ~0.89, not 1.0** — see *The 2P% gap* |
| 3P% | **TARGET (SOURCED)** | **36.0%** | 35.66% | 🟡 **RESIDUAL −0.52.** `base-three` is frozen (#040 F) and was **never touched** by §3.20; the row simply wanders. ⚠ **Its ±0.25 band is TIGHTER THAN THE ROW'S OWN RUN-TO-RUN SPREAD** (it ranged 35.48–36.08 across the pass at a fixed constant, sem ~0.15), so this row can fail its band by chance — §3.19's own 35.76 would have. **Judge the band before judging the number** |
| Assists | **TARGET (SOURCED)** | **26.7** | 27.14 | 🟡 **+1.16, ACCEPTED (user call, §3.20)** — a by-product of the 2P% lift: more made twos, more assist opportunities. `sim.base-assist` was **not** touched (not one of #042's eight). **Revisit at ~+4 (≈30.7)**, not before |
| Turnovers | **TARGET (SOURCED)** | **14.5** | 14.58 | ✅ **§3.20 LANDED IT** via `sim.base-turnover` 0.038 → **0.0527**. ⚠ **That knob is PARTLY FLOORED and is far weaker than it looks** — see *The turnover floor*. ⚠ Steals derive from this row |
| Blocks | **TARGET (SOURCED)** | **4.8** | 4.80 | ✅ ⚠️ **GREEN FOR THE WRONG REASON — `PROB_FLOOR` (0.02) is 4× `base-block-three` (0.005), so a three's block chance is FLOORED, not based, and the constant is INERT.** ⚠ **Effect sized at ~half a blocked three per team-game (0.74 vs 0.19) — NOT worth chasing**, and closed on that basis. Carry only as a footnote: **if a pass ever tunes `base-block-*`, reroute through `clampRareProbability` first**, or that one lever reads dead |
| Top-starter minutes | TARGET | ~34–36 | ~36.1 | ✅ |
| Minutes ceiling | TARGET | nobody over ~42 | ok | ✅ |
| Fouls | **TARGET (SOURCED)** | **19.9** | 18.84 | 🟡 **RESIDUAL −1.06** (was −1.80; `base-no-basket-foul` 0.15 → 0.1687 closed 40% of it). ⚠ **Cannot close further without un-landing FGA** — each extra foul costs 1.49 FGA and the FGA row is now in band at 89.22. **The pair is over-determined through one lever; a design pass must pick which yields** (roadmap §3.21). `PERSONAL_FOULS_PER_TEAM_GAME` re-measured to **18.52** |
| **FTA** | **TARGET (SOURCED)** | **23.5** | **23.40** | ✅ **§3.20 LANDED IT** via `sim.non-shooting-foul-share` 0.50 → **0.317**. ⚠ See *The non-shooting-foul share* — the response is linear, the two FT **sources move in opposite directions**, and it does **not** move FGA |
| **FT%** | **TARGET (SOURCED)** | **78.0%** | **77.81%** | ✅ **NEW ROW, added by §3.20 (#042 C).** Basketball-Reference league average, per game, **2025-26**. It had been running **82.56%** — measured by the harness, **absent from this file**, and donating ~1.1 points/team/game no target was watching. Landed via `sim.ft-base` 0.75 → **0.705**. ⚠ **The base is not the landing**: realized FT% is `ftBase + 0.20 × (freeThrows − 10)/10` and the roster's mean `freeThrows` is ~13.8 — a **global knob correcting a population effect** (parked in `ideas.md`) |
| **3PA** | **TARGET (SOURCED)** | **37.0** | **36.64** | ✅ via `sim.shot-share-*` = 1.0 / 0.55 / 0.55 / **1.256** (§3.20 bumped only the three, #040 C, to offset the attempts its turnover rise removed). Charged three share **41.1%** vs a real 41.5% |
| FGA | **TARGET (SOURCED)** | **89.1** | 89.22 | ✅ **LANDED (+0.12)** via `sim.base-no-basket-foul` 0.15 → **0.1687** — ⚠ **NOT via `non-shooting-foul-share`**, which does not move FGA at all (both foul branches return before an attempt is charged). ⚠ **It is bought against Fouls**: each extra foul costs **1.49 FGA** (a foul ends the possession, forfeiting the offensive rebound the miss would sometimes have produced), so the two rows are **over-determined through one lever** — landing Fouls 19.9 would drop FGA to ~87.8. See the Fouls row |
| Off rebounds | **TARGET (SOURCED)** | **11.3** | 9.90 | 🟡 **RESIDUAL −1.20** — see *The rebound pool*. Not a split problem |
| Def rebounds | **TARGET (SOURCED)** | **32.4** | 27.90 | 🟡 **RESIDUAL −4.22, and `sim.base-offensive-rebound` CANNOT fix it** (left at 0.27 by §3.20, user call). The realized split is already right; **the POOL is short** — see *The rebound pool* |
| Pace (poss/48) | **TARGET (SOURCED)** | **99.4** | ~100 nominal | ✅ |
| Steals | observed | **8.4** | **8.24** | ⚠ **DERIVED, NOT TUNED** — steals = turnovers × STOLEN share. §3.20 landed TO on 14.5 and steals followed to 8.10, reproducing #042 I's prediction (8.05) **to 0.05** with the share untouched. **Do NOT chase 8.4**: the nine `to-weight-*` are frozen (#027 A) and the STOLEN share is not a lever (#041 F) |
| **Foul-outs** | **ballpark** (**UNSOURCED**) | **~0.1–0.25** *(real)* | **0.368** | ⚠ **DEMOTED FROM `TARGET` BY §3.20 (#042 J)** — the old ~0.39 was a **landing promoted to a target**, i.e. circular, and real basketball sits *below* where the engine does. **Judge by the 4/5/6 distribution**, not this count — see *Foul-outs* |
| Players at 4 / 5 / 6 fouls | ballpark | *(no range yet)* | 1.06 / 0.52 / 0.37 | **the real diagnostic for foul-outs** — judge by this, not the headline count |
| **Technicals** | ballpark | **~0.3–0.4** (~0.6–0.8 league-wide) | **0.344** | ⚠️ **judge at 5 SEEDS ONLY** — see *Technicals* |
| **Flagrants** | ballpark (**UNSOURCED**) | **~0.13–0.20** (~0.25–0.40 league-wide) | **0.145** | ⚠️ **the COARSEST row here — 5 SEEDS ONLY.** ⚠ Its divisor is a **measured foul rate**: any pass that moves fouls must re-measure it, or this row silently reads low. §3.20 re-measured it to **17.77** (drift only −0.31%) |
| Flagrant-2s | *(no target — a 15% share)* | — | **0.022** | the ejection driver |
| **Ejections** | *(no target — an outcome)* | — | **0.031** | both causes (technicals alone: ~0.00) |
| Fouled-three rate | ballpark | **~2% of 3PA** | **2.93%** *(corrected)* | ✅ scale-free: held across a 1.9× volume change. ⚠ Raw visible tally reads **1.46%** — the corrected figure is the one to judge |
| 3-FT trips | ballpark | ~0.3–0.6 *here* | **1.09** *(corrected)* | the **count** tripled with 3PA while the **rate** above held. ⚠ The 0.3–0.6 range was set at half the current 3PA |
| And-1s | ballpark | ~4–6% of made FG | 1.70 (4.0%) | ✅ inside the band since §3.20's 2P% lift (more made shots to ride) |
| Fouls / team / period | observed | — | 4.76 | bonus at 5 — ⚠️ **AT the threshold**, which is why the penalty rate is volatile |
| Team-periods in penalty | observed | — | 46.4% | ⚠️ **a result, not a knob — but it PRICES the FTA share above** |
| Out of bounds | observed | — | 3.1 | |
| Turnover cause mix | observed | STOLEN dominant | 56.2% | no per-cause target |
| Period-by-period FG% | observed | flat, not sagging | flat | correct fatigue behavior |

---

## Foul-outs — a ballpark since §3.20, and the lever is saturated

**Current 0.299 against a real-basketball ballpark of ~0.1–0.25** — the engine sits
*above* the real range, not below it.

⚠ **§3.20 DEMOTED THIS FROM `TARGET` TO `ballpark` (#042 J).** The old ~0.39 was a prior
landing promoted to a target — **circular**, and it pointed the wrong way. A number that
cannot be sourced must not masquerade as one. Sourcing it properly needs play-by-play
derivation; until someone does that, `ballpark` is the honest label.

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

✅ **§3.20 FIXED IT.** `base-drive` 0.5975 → **0.6801**, `base-post` 0.4975 → **0.6131**,
`base-perimeter` 0.4375 → **0.4603**; 2P% landed **54.97**, FG% **46.96**. `base-three`
did **not** move (#040 F).

⚠ **THE BASES DO NOT PASS THROUGH ONE-FOR-ONE — the wedge is MULTIPLICATIVE.** Measured
**pass-through of weighted-base movement into realized 2P% is 0.890**, and the wedge
*grows* with the level (−4.4 points at the landing). **Aim above the target and size each
step off the measured pass-through, not off a flat offset** — a flat-offset model
under-shoots every increment it predicts (#042 F modelled one, and landed 0.30 short).

⚠ **The ordering is deliberate and realistic**: drive 0.680 > post 0.613 > perimeter 0.460,
mirroring real rim > post-up > mid-range separation. A **uniform** bump is rejected (#042
D) — right in aggregate, wrong in composition, the exact error §3.17 spent a pass un-hiding.

⚠ **The per-shot-type realized make rates are NOT OBSERVABLE** — the harness reports FG%
in aggregate only. Any future re-shaping of this split is therefore tuning a composition
it cannot see; instrument first.

---

## The non-shooting-foul share — DERIVED, not sourced

`sim.non-shooting-foul-share` = **0.317** (§3.20, from 0.50). ⚠ **This is the value that
lands FTA on a sourced target — it is NOT a measurement of what real basketball does.**
The real shooting-foul share is not in a league-averages row and needs play-by-play
derivation. It remains **unsourced**, and now sits a long way from 0.5.

⚠ **It is priced by the PENALTY RATE, not the foul rate alone**: a non-shooting foul
awards 2 bonus FTs inside the penalty and none outside it, so what the knob removes per
conversion depends on how often teams are in the penalty. **Any pass that moves the foul
rate must re-check FTA rather than assume this value still lands it.**

⚠ **Tune it against the FTA line, never against points.** Measured at §3.20: the response
is **dead linear, −20.46 FTA per unit share**.

⚠ **THE TWO FT SOURCES MOVE IN OPPOSITE DIRECTIONS, so the headline understates the
lever.** Lowering the share converts fouls to `SHOOTING_FOUL` (FTs always) *and* removes
the non-shooting fouls that would have drawn **bonus** FTs. Across §3.20's move: SHOOTING
12.97 → 17.51, BONUS 4.61 → 3.23, net FTA 19.76 → 23.08. **Read the split before deciding
the lever is weak.**

⚠ **IT DOES NOT MOVE FGA — do not expect the by-product.** #042 B modelled −0.62 FGA per
0.05 of share; **measured is +0.03, i.e. nothing.** Both foul branches `return` before the
engine charges an attempt, so re-partitioning between them cannot touch FGA. The
possession was already being consumed without an attempt either way. **This is why FGA is
a residual that #042's own eight constants cannot reach. ⚠ §3.20's FOLLOW-UP then landed
FGA with a NINTH constant (`base-no-basket-foul`) — see the FGA and Fouls rows.**

---

## The rebound pool — why `base-offensive-rebound` cannot land def rebounds

⚠ **The split is already right; the POOL is short.** Measured at the §3.20 landing:

| | offensive | defensive | pool |
|---|---|---|---|
| engine | 10.10 (**0.264**) | 28.18 | **38.28** |
| real (11.3 / 32.4) | 11.3 (**0.259**) | 32.4 | **43.70** |

The realized offensive share is within ~0.005 of real basketball. **Nothing about the
contest is miscalibrated.** But `base-offensive-rebound` only *splits* the pool — it
cannot create rebounds — so landing DefReb 32.4 out of 38.28 would need the offensive
share down at ~0.15, putting **OffReb at ~5.7 against a target of 11.3**. That trades a
−1.2 miss for a −5.6 one and distorts a correct split. **§3.20 therefore left the knob at
0.27** and recorded both rows as residuals (user call).

⚠ **The pool MOVES with FG%** — a shooting fix removes misses and shrinks it. Any pass
that changes FG% must re-read this table before touching the rebound knob. (#042 H's
pre-fix reading is superseded; see its implementation note.)

⚠ **WHERE THE MISSING REBOUNDS GO — the actual finding, and it is §3.21's.** The engine
generates the *right number of misses* (~52.6/team/game including free throws) but only
**37.80** become a recorded rebound — **~14.8 lost against reality's ~5.5.** Sorted:

| | per team-game | verdict |
|---|---|---|
| OOB off a missed FG (`oob-total-weight` 0.07) | 3.10 | ✅ correct — nobody touched it |
| OOB off a blocked shot | 1.14 | ✅ correct — same |
| **blocked shots recovered IN BOUNDS** | **3.41** | ❌ **a player secured the ball and got NO credit** |
| **missed last free throws** | **~2.60** | ❌ **no rebound branch exists at all** |
| unexplained, FG path | 1.97 | ❓ |

⚠ **EVERY ACTUAL REBOUND HAS AN OWNER.** The two ❌ rows are **missing credits**, not
"team rebounds" — a team rebound is the scorekeeping entry for a possession change where
**no rebound happened** (ball out untouched, buzzer), which is what the ✅ rows already
model correctly. Closing the ❌ rows is a **mechanic** question (#038 territory), not a
rate one, and it is **§3.21's**.

---

## The turnover floor — `base-turnover` is far weaker than it looks

`sim.base-turnover` = **0.0527** (§3.20, from 0.038 — a +39% move for a +0.84 result).

⚠ **`PROB_FLOOR` (0.02) sits just below this base, and pins ~40–45% of possessions.**
`isTurnover` clamps through `clampProbability`, and the contest is
`base + 0.5 × (avgDefense − ballSecurity)/10`. At base 0.038 any possession where the
ball-handler's security exceeds the average defense by more than ~0.36 skill points is
**floored at 0.02 and completely insensitive to this constant**.

**Measured elasticity is 0.17** — 0.17% of turnover movement per 1% of base — against the
~1.0 a flat per-possession probability implies. **Tune against the measured line and
expect to move this constant a long way for a small result.**

⚠ **It is also backwards as basketball**: a *good* ball-handler facing average defense
should be the *lowest*-turnover possession on the floor, and the floor is raising it to
2%. ⚠ **This is the same shape as the `base-block-three` finding** — but **sized
differently and therefore not closed the same way**: that one was worth ~half a blocked
three on an on-target row; this one suppresses a **sourced** row by 0.82. The fix (reroute
to `clampRareProbability`) is a **Java change** and was out of §3.20's scope.

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

⚠ **Still UNSOURCED**, and flagged as such above: **foul-outs** (demoted to `ballpark` by
§3.20 — its ~0.39 came from a prior landing, i.e. circular), **technicals**,
**flagrants**, the **minutes distribution**, and the real **shooting-foul share**.
Everything marked `TARGET (SOURCED)` is a Basketball-Reference league average, per game,
2025-26.

✅ **The exit condition is MET as of §3.20**: every row in the table above is either a
`TARGET` with a named source and season, or is deliberately `observed` / `ballpark` and
says so. **That is the bar — not "every row green."** Five rows are knowingly out of band
and are recorded as residuals with their reasons.

⚠ **A number that moves is not automatically an engine change.** Before tuning anything,
ask: **did the engine change, did the MEASUREMENT change, or is a clamp holding it?** All
three have happened here — an instrument that miscounted a row, a corrected instrument
that made a falling number look like it doubled, and `PROB_FLOOR` holding a rate that a
constant appeared to set. **Compare raw-to-raw across an instrument change; never compare
a corrected number to an uncorrected one.**

- **`docs/game-events.md`** — the event vocabulary these numbers are counted from.
