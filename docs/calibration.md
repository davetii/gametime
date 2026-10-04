# Calibration targets

**What the simulation is tuned toward, where it sits now, and the rules for reading and
moving each number.** This is the source of truth for targets. It is a reference, not a
history: how a target was argued or which pass landed it is in
[decisions.md](decisions.md); constants that bite are in [engine-traps.md](engine-traps.md).

Live constant values are in `application-baseline.properties`, not here, so they cannot
drift from the file that binds them. This doc names the knob and the rule.

## Measuring

`CalibrationHarness` is disabled by default and **reports, never gates**:

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml test -pl gametime-app -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000 -DfailIfNoTests=false
```

- **Profile comes from the environment**: `SPRING_PROFILES_ACTIVE=local,baseline`. A `-D`
  flag does not reach the forked Surefire JVM, and the failure looks like a database
  error. Confirm the report's `Profiles:` line before trusting any number.
- **Judge by the mean of several `-DcalibrationSeed` runs.** Per-seed noise is about ±1.5
  points. Technicals, flagrants and 3P% need 5 seeds as a hard floor.
- **Every target here belongs to the `baseline` profile.** Era profiles are supposed to
  miss most of them. On a non-baseline run the harness drops the `(target ~N)` strings and
  prints a delta against the baseline landing; a delta claims nothing about what a number
  should be. There is no per-profile target set.
- **The harness self-verifies four identities**: assists+blocks vs. the box score, FT
  sources summing to total FTs (empty `UNKNOWN` bucket), points (events = box score = final
  score), and the rebound pool (every `REBOUND` naming a player is a box-score rebound).
  A `MISMATCH` on any of them invalidates the rows it feeds: fix the instrument first.
- **The harness keeps its own copy of the penalty tally** (per team-period, for the
  bonus-rate line), separate from `GameData.isInBonus`. A foul kind excluded from one and
  not the other (today only `TECHNICAL_FOUL`) makes the instrument disagree with the engine.

**When a target changes**, update this table **and** the harness `(target ~N)` strings in
the same commit. **When a target becomes sourced**, record the named source and season,
whether it is a league average or per-team mean, and whether it is pace-adjusted.

## The table

Per team per game unless noted. **Current** is the 5-seed mean (seeds 1000–5000) on the
`baseline` profile at the putback landing (2026-09). Sourced rows are Basketball-Reference
league averages, per game, 2025-26.

**Type:** `TARGET` = calibrated, steer by it. `ballpark` = a plausibility range; judge
against it but do not calibrate to it. `observed` = no target, reported for visibility.

Four rows are **known residuals**, deliberately out of band: def rebounds, off rebounds,
fouls and 3P%. They are not drift and not a to-do list; the reason is in each row.

| Measure | Type | Target | Current | Rule |
|---|---|---|---|---|
| Points | TARGET (sourced) | 115.6 | 115.82 | Not independent: points track **FGA**, since threes and FTs sit on target. Tune FGA, not this |
| FG% | TARGET (sourced) | 47.1% | 47.06% | Set by `base-drive` / `base-post` / `base-perimeter`. **`base-three` must not move** |
| 2P% *(derived)* | TARGET (sourced) | 55.0% | 54.76% | `(FGA×FG% − 3PA×3P%) / (FGA − 3PA)`. See *2P%* below |
| 3P% | TARGET (sourced) | 36.0% | 36.14% | **Residual-class row.** `base-three` is frozen. The ±0.25 band is tighter than the row's run-to-run spread (about 0.15 sem), so it can fail by chance: judge the band before the number |
| Assists | TARGET (sourced) | 26.7 | 26.84 | A putback (`shooter == rebounder`) is assisted at `OFFENSIVE_REBOUNDER_ASSIST_LEAN` (0.5) × the ordinary chance. **Do not re-land with `sim.base-assist`** |
| Turnovers | TARGET (sourced) | 14.5 | 14.68 | `sim.base-turnover`, which is partly floored. See *Turnover floor*. Steals derive from this |
| Blocks | TARGET (sourced) | 4.8 | 4.54 | **Residual (−0.26)**, inside run-to-run spread. `PROB_FLOOR` (0.02) is 4× `base-block-three` (0.005), so a three's block chance is floored and that constant is inert. If a pass tunes `base-block-*`, reroute through `clampRareProbability` first |
| Top-starter minutes | TARGET | ~34–36 | ~36.1 | |
| Minutes ceiling | TARGET | nobody over ~42 | ok | |
| Fouls | TARGET (sourced) | 19.9 | 19.35 | **Residual (−0.55).** Lever is `base-no-basket-foul`. Over-determined with FGA: each extra foul costs **1.49 FGA**, so closing the gap would pull FGA out of band. It moves only as a side effect of re-landing FGA. `PERSONAL_FOULS_PER_TEAM_GAME` (the flagrant divisor) must be re-measured whenever the foul rate moves |
| FTA | TARGET (sourced) | 23.5 | 23.58 | `sim.non-shooting-foul-share`. See *Non-shooting-foul share* |
| FT% | TARGET (sourced) | 78.0% | 77.64% | `sim.ft-base`. Realized FT% is `ftBase + 0.20 × (freeThrows − 10)/10` and the roster mean `freeThrows` is ~13.8, so the base is not the landing |
| 3PA | TARGET (sourced) | 37.0 | 36.96 | `sim.shot-share-*`. Charged three share is 41.1% vs a real 41.5% |
| FGA | TARGET (sourced) | 89.1 | 89.40 | `sim.base-no-basket-foul`, **not** `non-shooting-foul-share` (both foul branches return before an attempt is charged). Bought against Fouls |
| Off rebounds | TARGET (sourced) | 11.3 | 11.90 | **Residual (+0.60), reported not tuned.** See *Rebound pool* |
| Def rebounds | TARGET (sourced) | 32.4 | 31.55 | **Residual (−0.85), reported not tuned.** The pool total itself is 43.45 vs 43.70. See *Rebound pool* |
| Pace (poss/48) | TARGET (sourced) | 99.4 | ~100 nominal | |
| Steals | observed | 8.4 | 8.24 | **Derived, not tuned**: turnovers × the `STOLEN` share. Do not chase 8.4; the nine `to-weight-*` are frozen and the share is not a lever |
| Foul-outs | ballpark (unsourced) | ~0.1–0.25 | 0.386 | Moves with every FGA re-landing on the foul lever. Do not chase. See *Foul-outs* |
| Players at 4 / 5 / 6 fouls | ballpark | none yet | 1.01 / 0.56 / 0.39 | The real diagnostic for foul-outs |
| Technicals | ballpark | ~0.3–0.4 | 0.335 | 5 seeds only. See *Technicals* |
| Flagrants | ballpark (unsourced) | ~0.13–0.20 | 0.159 | 5 seeds only; coarsest row. See *Flagrants* |
| Flagrant-2s | none | | 0.027 | The ejection driver (15% share of flagrants) |
| Ejections | none | | 0.037 | Both causes |
| Fouled-three rate | ballpark | ~2% of 3PA | 3.10% *(corrected)* | Scale-free. Judge the corrected figure, not the raw tally (1.46%) |
| 3-FT trips | ballpark | ~0.3–0.6 | 1.15 *(corrected)* | The range was set at half the current 3PA, so the count scales with 3PA |
| And-1s | ballpark | ~4–6% of made FG | 1.63 (3.9%) | |
| Fouls / team / period | observed | | 4.88 | The bonus threshold is 5, so the penalty rate is volatile |
| Team-periods in bonus | observed | | 52.0% | A result, not a knob, but it **prices** the FTA share |
| Out of bounds | observed | | 4.20 | |
| Turnover cause mix | observed | `STOLEN` dominant | 55.9% | No per-cause target |
| Period-by-period FG% | observed | flat | flat | Correct fatigue behavior |

**Still unsourced:** foul-outs, technicals, flagrants, the minutes distribution, and the
real shooting-foul share. A number that cannot be sourced must not masquerade as a target.

## Reading a number

A number that moves is not automatically an engine change. Before tuning, ask: **did the
engine change, did the measurement change, or is a clamp holding it?** All three have
happened here. Compare raw to raw across an instrument change; never compare a corrected
number to an uncorrected one.

The bar for the table is not "every row green". Every row is either a `TARGET` with a named
source and season, or is `observed` / `ballpark` and says so.

---

## 2P%

- **The bases do not pass through one-for-one.** Measured pass-through of weighted-base
  movement into realized 2P% is **0.89**, and the wedge grows with the level. Aim above the
  target and size each step from the measured pass-through, not a flat offset.
- **Keep the ordering** drive > post > perimeter, mirroring rim > post-up > mid-range. A
  uniform bump is right in aggregate and wrong in composition.
- **Per-shot-type realized make rates are not observable.** The harness reports FG% in
  aggregate only, so re-shaping this split tunes a composition it cannot see. Instrument
  first.

## Non-shooting-foul share

`sim.non-shooting-foul-share` is the value that lands FTA on target. It is **not** a
measurement of real basketball; the real shooting-foul share needs play-by-play derivation
and is unsourced.

- **Priced by the penalty rate, not the foul rate alone.** A non-shooting foul awards 2
  bonus FTs inside the penalty and none outside it. Any pass that moves the foul rate must
  re-check FTA rather than assume this value still lands it. It and `base-no-basket-foul`
  are a pair, not independent levers.
- **Tune against the FTA line, never against points.** The response is linear, about
  **−20.46 FTA per unit of share**.
- **The two FT sources move in opposite directions.** Lowering the share converts fouls to
  `SHOOTING_FOUL` (always FTs) and removes the non-shooting fouls that drew bonus FTs. Read
  the SHOOTING / BONUS split before deciding the lever is weak.
- **It does not move FGA.** Both foul branches return before an attempt is charged.

## Rebound pool

`sim.base-offensive-rebound` reaches **one of three slices** feeding the off/def rebound
rows:

| Slice | Offensive share | Set by |
|---|---|---|
| Ordinary board off a missed FG | 0.262 (real 0.259) | `sim.base-offensive-rebound`'s logistic contest, the only knob |
| A blocked shot recovered in bounds | 0.400 | the flat `sim.block-*` weights; not a contest, the side is drawn before any rebounder exists |
| A missed last free throw | ~0.17 | the same contest at `baseOffensiveRebound() × FREE_THROW_REBOUND_LEAN`, a `public static final` rule |

| | Offensive | Defensive | Pool |
|---|---|---|---|
| Engine | 11.90 | 31.55 | 43.45 |
| Real | 11.30 | 32.40 | 43.70 |

- **Both rebound rows are reported residuals. Do not re-open them with
  `base-offensive-rebound`**: it would distort a contest whose realized 0.262 is already
  right, to compensate for two slices it does not touch.
- **Do not re-weight the four `sim.block-*` values to chase the split.** They model where a
  swatted ball goes and are calibrated against the block rate.
- **The pool moves with FG%.** A shooting fix removes misses and shrinks it. Re-read it
  before touching the rebound knob.
- **Every actual rebound has an owner.** A "team rebound" is a possession change with no
  rebound (the `OUT_OF_BOUNDS_*` outcomes), never a bucket for unattributed rebounds.
- **A possession can end with no rebound and be correct.** The rebounding foul takes about
  2.34 per team-game off the top of the rebound phase.
- **After an offensive board** the rebounder carries `sim.offensive-rebounder-shot-weight`
  on the next shooter draw. The constant is the multiplier; the realized share of
  "rebounder is the next shooter" (35.4%) is measured and reported, **never back-solved
  into the constant**. The real figure is about 45–55% of immediate second-chance
  attempts; raising the multiplier waits on a sourced figure.

## Foul-outs

Judge by the **4/5/6 distribution**, not the headline count.

- The ballpark (~0.1–0.25) is unsourced. The engine sits above it, and the gap is sized and
  accepted.
- **The foul-trouble bench lever is saturated. Do not re-tune the sit curve.** Sitting a
  player prevents some sixth fouls but returns him to the floor later, trading one
  disqualification for another.
- **Do not trim the foul rate to reduce foul-outs.** It breaks FTA and points, which are
  sourced. The root cause is defender-selection over-dispersion (the same defenders are
  picked too often), not foul volume.

## Technicals

A `ballpark`. `SimConfig.TECHNICAL_FOULS_PER_TEAM_GAME` is set directly from the real-world
figure, so the harness line checks that the constant is wired right; it is not a
calibration objective.

- **Judge at 5 seeds only.** One seed (102 games) is ~71 events, relative sd 11.8%; five
  seeds is ~357 events, sd 5.3%.
- **The landing sits a few percent above the constant by design.** The constant's divisor
  is the nominal possession count while the real one is pace-scaled. Do not back-solve it.
- **The per-check probability is ~0.00175.** Both rotations advance every possession, so a
  team is checked ~200 times per game. `PROB_FLOOR` is more than 10× that rate, which is why
  this rate uses the floor-free clamp.

## Flagrants

A `ballpark`, configured from a real-world figure rather than tuned toward one.

- **5 seeds is a hard floor and the reading is still coarse**: ~33 events at 102 games,
  relative sd 17.4%.
- **Its divisor is a measured personal-foul rate** (`PERSONAL_FOULS_PER_TEAM_GAME`), so any
  pass that moves the foul rate, including one that only changed the shot mix, must
  re-measure it. Left stale the row reads low (74% of target once) and no test fails.
- `Fouls / team / game` does not include flagrants, so the two lines are not additive.

## Turnover floor

`PROB_FLOOR` (0.02) sits just below `sim.base-turnover` and pins about 40–45% of
possessions: `isTurnover` clamps through `clampProbability`, and the contest is
`base + 0.5 × (avgDefense − ballSecurity)/10`, so any possession where the ball-handler
outruns the defense by more than ~0.36 skill points is floored and insensitive to the
constant.

- **Measured elasticity is 0.17**, against ~1.0 for a flat per-possession probability.
  Tune against the measured line and expect to move the constant a long way for a small
  result.
- **It is backwards as basketball**: a good ball-handler facing average defense should be
  the lowest-turnover possession, and the floor raises it to 2%. The fix is to reroute to
  `clampRareProbability`, a Java change; same shape as the `base-block-three` finding.

---

See also: [game-events.md](game-events.md), the event vocabulary these numbers are counted
from.
