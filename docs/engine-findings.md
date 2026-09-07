# Engine findings & traps

**Read this before touching the simulation engine.** One line per fact that no source
file holds: the traps, the measured elasticities and saturation results, the wrong-way
levers, the "do not reach for this knob" refusals, and the final constants.

Each line points at its `#NNN` entry in [decisions.md](decisions.md) — that file is the
**archive** the ~1,700 `#NNN` citations in the Java and docs resolve into. You should
rarely need to open it; read this instead, and follow a pointer only when you need the
full reasoning behind one decision.

⚠ **These findings are load-bearing.** They are what later phases actually reach for, and
several were discovered only by a phase measuring something an earlier phase asserted.
Where two records disagree, the newer `#NNN` wins and the line below says so.

---

## The findings


### ⚠ Wrong-way levers and inert knobs — reach for these and you get the opposite

- **⚠ `base-no-basket-foul` (ex-`BASE_FOUL`): the wrong-way finding is SUPERSEDED — the
  sign FLIPPED, and the engine's own comments still disagree about it.**
  **#028 E (§3.10, 2026-07)** measured 0.15 → 0.138 moving points *up* 115.6 → 116.1 —
  a shooting foul ENDS a possession for ~1.5 expected FT points, worth less than the live
  shot it replaces — and #030/#031 re-flagged it as "do not reach for it".
  **#036 C (§3.16, 2026-08) then DISPROVED it at the current configuration**: 0.15 → 0.11
  over 3 seeds *lowered* points 118.3 → 117.1 (and FTA 34.0 → 29.1), because #028 was
  measured **pre-§3.12**, before perimeter and three shots could draw fouls.
  ⚠ **The live comments contradict each other**: `application-baseline.properties`
  ("Raising base-no-basket-foul LOWERS points") matches #036, while `SimConfig.java`
  (~L75, ~L176) still repeats #028's wrong-way warning. **#036 is the current record.**
  ⚠ **The knob is still not a clean lever** — the same trim moved fouls 19.35 → 17.27
  (away from 19.9) and FGA 88.4 → 91.2 (overshooting 89.1): one rate governs both.
- **`base-block-three` (0.005) is INERT — `PROB_FLOOR` (0.02) is 4× it**, so a three's
  block probability is *floored, not based*. Predicted blocks 4.8 → ~2.6 on a 1.9×
  three-volume shift; measured **4.80, flat**. ⚠ The four `base-block-*` cannot be
  reasoned about without the floor. → **#040 impl note, wrong prediction 1**
- **`base-turnover` is ~40–45% FLOORED; its elasticity is 0.17, not ~1.0.** `isTurnover`
  clamps through `clampProbability`, and `PROB_FLOOR` sits just below the base, so the
  possessions where a good handler faces average defense are pinned and insensitive.
  Landed by tuning against the measured line (0.0396 → **0.0527**, +39% over plan). The
  real fix — reroute to `clampRareProbability` — is a Java change, still **not taken**.
  → **#042 D4**
- **`PROB_FLOOR` floors any deliberately-rare carve, making the knob one-directional.**
  First hit in §3.10: halving `REBOUND_FOUL_BASE` 0.030 → 0.015 **silently did nothing**
  (points moved 0.1). Fixed by `rareEventProbability` (#028), reused by design in #029,
  and taken again in a new shape by #030 (`FOUL_MULT_THREE` targets *exactly* 2% — on the
  floor — so a `0.0` multiplier would have floored back up, destroying A1's off-switch).
  → **#028 impl note · #030 unplanned fix 1 · #032 H**
- **`BLOCK_SENSITIVITY` exists because the global `SENSITIVITY = 0.5` is wrong for rare
  events.** At 0.5 a rim protector (14) vs an average finisher (10) blocked ~23% of shots
  (elite NBA ~5–6%); the base was already near zero, so the *contest term* was the driver.
  → **#025 impl note** — the same class of problem `PROB_FLOOR` causes.

### ⚠ Emergent drifts — the configured number is not the observed one

- **The rebounding-foul split is configured 75/25 but OBSERVES 78/22.** Defensive fouls
  compound: each retained possession generates another miss and another roll. Left
  emergent — **do not back-solve the weights** unless a consumer needs the observed split
  to hit a target. → **#028**
- **Turnovers rose 13.5 → 14.2 in §3.10 and it was CORRECT.** Not drift: more glass
  scrambles → more possessions → more fatigue → worse `ballSecurity`, via `isTurnover`'s
  `fatigueFactor()` dependency. A real §3.5 emergent effect, and it moved *toward* the
  ~14 target. → **#028**
- **A player tips off at a FULL TANK, which contradicts #023 B's own wording.** The entry
  says energy is "derived from" endurance/energy at construction; as built every player
  starts at `MAX_ENERGY`, and the two attributes act per-possession instead (`endurance`
  slows drain, `energy` speeds recovery). Deliberate, user-approved — recorded so code and
  decision do not silently disagree. **`fatigueFactor(MAX_ENERGY) == ×1.0` must mean "full
  tank" for everyone**, which a per-player max would break. → **#023 impl note**
- **An and-1 that turns FLAGRANT produces THREE scoring channels on one possession** —
  basket counts, 2 FTs awarded, offense keeps the ball. Nothing else in the engine does
  this; **it looks exactly like a double-count bug in a play-by-play and is not one.**
  The flagrant's 2 FTs *replace* the and-1's 1. #029 B's "never forks the possession" is
  superseded for this case only. → **#029 follow-up · #034 C**

### ⚠ Saturated levers — more of the input moves nothing

- **The foul-trouble sit curve is SATURATED.** Raising it to `{0.25,0.75,0.95}` gives
  0.407 foul-outs and `{0.40,0.90,0.98}` gives 0.382 — **~60% more substitutions move
  the number by nothing**. The binding constraint is that an earned return sends the
  player back into the same over-dispersed defender draw that gave him the fouls;
  benching harder returns him *sooner*. **Do not simply raise the sit curve.**
  → **#031 impl note + follow-up**
- **`FOUL_TROUBLE_FRESHNESS_MARGIN`: a LARGER margin makes foul-outs WORSE.** Sweep:
  12 → 0.53 (vetoed ~69% of fired rolls), 8 → 0.46, 4 → 0.51, 2 → 0.40, **1 → 0.377**,
  0 → 0.40. Both ends bad. **1.0 is optimal and sits BELOW one possession's drain (2.6)** —
  the anti-oscillation guarantee comes from the margin *plus* a returning player entering
  at a full tank, not from the constant being large. → **#031 divergence**
- **§3.12's per-shot-type foul multipliers are worth ~0.3 points across their ENTIRE
  defensible range** (swept 0.25/0.30/0.35). `PERIMETER` was therefore set on **realism,
  not points**. A supposedly-free re-centering lever that is nearly weightless.
  → **#030 impl note (3) + Recalibration E**
- **`REBOUND_FOUL_BASE` alone cannot absorb §3.10's lift** — ~1 point across its useful
  range. → **#028 E**

### ⚠ Over-determined / coupled pairs — one knob, two rows

- **FGA and Fouls move through ONE knob.** `non-shooting-foul-share` re-partitions an
  already-charged foul; `base-no-basket-foul` moves the foul rate and pulls FGA with it.
  Three consecutive phases saw the same shape: nudging `base-no-basket-foul` fixed FGA
  *and* pulled Fouls toward target as a free side effect. → **#042 · #043 · #044**
- **⚠ #042's headline: Decision B's FTA/FGA coupling DOES NOT EXIST.** B claimed
  `non-shooting-foul-share` "raises FTA and lowers FGA in the same motion" at ≈ −0.62 FGA
  per 0.05. **Measured ≈ +0.03 — FGA does not move at all.** In `PossessionEngine`'s foul
  block *both* branches `return` before `recordFieldGoalAttempt()`, so neither charges an
  FGA and re-partitioning between them cannot touch it. **A modelling error, not the
  engine, instrument or a clamp.** Consequence: **FGA has no lever and ends a residual**,
  and the points surplus is entirely two-point volume. → **#042 D1**
- **The §3.16 share is coupled to the bonus rate.** The charge fix moves the penalty rate
  that *prices* the re-partition, so the two steps are not independent — anything moving
  the penalty rate re-prices `non-shooting-foul-share`. → **#039 impl note + follow-up**
- **`FOUL_TROUBLE_FRESHNESS_MARGIN` couples foul trouble to the §3.5 energy constants.**
  Retuning `ENERGY_DRAIN_/RECOVERY_PER_POSSESSION` silently retunes both the return timing
  *and* how often the soft rule can fire. Keep them together in a profile. → **#031 follow-up**
- **`PERSONAL_FOULS_PER_TEAM_GAME` is an EMERGENT, shot-mix-derived divisor**, not a
  constant. 19.0 → 20.15 (#039) → **17.82** (#040, −11.5%, the largest drift — §3.17 touched
  no foul constant at all) → 18.52 (#042) → **19.08** (#043) → measured 19.015 and
  **deliberately left** (#044, a −0.34% drift the flagrants row cannot resolve).
  ⚠ **Re-measure it after any foul-rate or shot-mix move.** The drift is visible in the
  flagrants row *before* you look: at 17.82/20.15 flagrants ran 74% of ballpark.
  → **#034 G · #039 · #040 · #042 · #043 · #044**

- **⚠ The harness is NOT "five identical skill-10 players"** — a premise several design
  passes rested on, and it is false. The seeded league has **422 distinct attribute rows**
  and every skill is computed from them at load. The five on the floor differ; an offensive
  rebounder is drawn by `offenseRebound` and so is usually a big, **and a big's own shot mix
  is interior**. Any reasoning that assumes an average, undifferentiated lineup is wrong at
  the start. → **#044**

### ⚠ Instruments that lie — fixing one moves the numbers

- **A literal in the harness silently shadowed the profile's biggest lever.**
  `CalibrationHarness` passed its own `25` to `simulate()`, so
  `sim.default-possessions-per-period` **never reached the engine on a harness run**.
  Baseline was unaffected (same value) so nothing failed — the first `nineties` run came
  back **+0.7 points on a profile that had cut pace to 21**. Caught only by the effective-
  config dump. **A caller passing its own copy of a profilable value is invisible to the
  properties file.** → **#035 impl note**
- **⚠ `Out of bounds` jumped 3.1 → 4.12 and the RATE did not change** — the block-OOB
  slices and the FT board's OOB carve began *emitting* events they had previously resolved
  silently. The instrument became complete; the engine did not move. **This is why
  `oob-total-weight` must not be tuned against that row.** → **#043**
- **⚠ Compare raw-to-raw across an instrument change.** §3.17's `Stopped shots / team` reads
  12.43 post vs 7.65 pre and *looks* doubled; like-for-like on raw visible it **FELL**,
  7.39 → 6.24. → **#040**
- **§3.16 silently broke a harness instrument**: `flushStoppedShot` classifies a stopped
  shot by its free-throw run (3 FTs ⇒ a THREE), a faithful proxy only while every stopped
  shot awarded FTs — which `COMMON_FOUL` ended. → **#039 follow-up**
- **The harness's `Fouls / team / game` counts technicals too**, so §3.13's 19.0 and
  §3.14a's ~19.4 are **not like-for-like**. → **#032**
- **The harness computes its own per-team-period foul tally**, independent of
  `GameData.isInBonus` — so the bonus exclusion had to be applied **twice** or the
  instrument would disagree with the engine in the direction of the change it measures.
  → **#032**
- **A latent points-reconciliation bug in `PossessionEngineTest`, twice.** The helper
  buckets event points by `offTeamId`, wrong for a free throw shot by the team that did
  *not* commit the foul. The engine was always right (`awardFreeThrows` takes an explicit
  `shootingTeamId`); the helper could not tell. Latent since §3.10, exposed by §3.12's RNG
  shift, then again by §3.14b's — fixed the second time by generalizing to the actual rule
  ("the FTs score for whoever did NOT commit the foul"). → **#030 unplanned fix 2 · #034 (4)**
- **Definition-of-done figures are 5-SEED MEANS, not what one seed prints.** Before/after
  **on the same seed** is the check that means something. → **#041**

### ⚠ Refusals — measured, and deliberately NOT done

- **#040 N — there is no migration.** §3.17 is properties-only.
- **#042 A — no population shift could close the 3PA gap.** The cause is CONFIRMED as the
  weight formula's 5-skill/4-type collapse, not the player population. → **#040**
- **`base-three` must NOT move.** 3P% is 35.8 vs a sourced 36.0 and **held across a 1.9×
  volume change** — the whole remaining FG% gap is 2P%. → **#040 F follow-up**
- **`base-offensive-rebound` was NOT moved (user call), and #043 H is INVERTED.** H
  predicted the contest runs hot (0.378) and DefReb would overshoot; measured **0.2614**,
  slightly cold, and landing DefReb would *undershoot* by −5.2. **The binding constraint is
  the POOL, not the split** — the split (0.264 vs a real 0.259) is already correct. Both
  rows left as **reported residuals**. ⚠ The real finding: the engine generates the right
  number of misses (~49.9 vs ~49.2) but only 38.3 become a rebound — **~11.6 leak per
  team-game against reality's ~5.5**, from blocked shots and OOB. Mechanic, not rate.
  → **#042 D3 · #043**
- **`pickDefender`'s over-dispersion was NOT reduced** — it is where the 2.3× foul
  concentration actually lives, but touching it would delete correct realism and silently
  move calibrated blocks/steals/contests. → **#031 follow-up (2)**
- **Three consecutive passes declined the shot-`BASE_*` trim** (§3.10 stopped at 113.8,
  §3.11 and §3.12 took none) — each because it cost more calibrated FG% than the points
  miss was worth. **That is evidence about the TARGET, not about the passes**, and it is
  what created `calibration.md`. The `BASE_*` rates move points and FG% the **same
  direction**, so points-too-high and FG%-too-low cannot be reconciled by that lever.
  → **#030 E · #036**
- **The "§3.14 needs real stored state" prediction is RETIRED, not deferred.** Every
  disqualification in the model is absorbing, monotonic and per-player — the shape #023 F's
  derivation was built for. Do not predict the exception again without a genuinely
  non-monotonic mechanic. → **#034 (F) · reverses #031 H · #032 F**
- **#023 F / #028 A1: a disqualification is a DERIVED predicate, never a stored flag.**
  `isFouledOut()` / `isEjectedForFlagrant()` read monotonic counters. → **#023 · #034**

### Measured elasticities and exchange rates

- **Shot-`BASE_*` exchange rate: ~0.50% FG% per 1.0 point** (§3.12's own numbers; #029's
  was 0.6%). Reaching ~112 from 117.0 would have cost ~2.5 points of FG%, landing 43.9%
  against a ~47% target — E's stop condition fired and **no trim was taken**. → **#030**
- **The 2P% wedge is MULTIPLICATIVE, not a flat offset — pass-through 0.890.** Modelling
  it flat left 2P% 0.30 short; re-sizing off the measured pass-through landed it. At the
  landing the wedge is −4.4 and **grows with the level**. → **#042 D2**
- **rebounds→FGA elasticity is well under 1.** Predicted FGA damage +1.7, measured **+0.54**:
  an extra offensive rebound does not buy a full extra attempt (the cap and the
  foul/turnover branches consume some). → **#043**
- **Steals are DERIVED from turnovers and were not tuned**: TO landed 14.50 and steals
  followed to 8.10 against a predicted 8.05. → **#042 D5 · #041**
- **The fouled-three rate is SCALE-FREE**: 2.92% → 2.93% of 3PA across a 1.9× volume
  change — measured, not argued. → **#040 G**
- **A technical's per-check probability is ~0.00175, not ~0.0035** — the roll runs ~200×
  per team-game, not 100, because `simulate()` advances **both** rotations on **every**
  possession. A divisor of 100 would have doubled technicals. → **#032 arithmetic correction**

### ⚠ Engine invariants and structural triggers

- **No second RNG, ever; new state must be deterministic given state.** Draws are
  **unconditional and fixed-point** — a conditional draw forks the stream on its own
  outcome and makes the count state-dependent (#031, #032 divergence 2, which took the
  count 1 → 3). The load-bearing assertion is that **the count does not depend on rotation
  state**, not the count itself. → **#021 A · #023 C · #031 · #032**
- **⚠ Hoist the DECLARATION, never the call.** `pickStealer` runs inside `if (cause ==
  STOLEN)`; hoisting the *call* would consume a draw on all nine causes and move every
  number. The code carries a ⚠ comment because it is the one edit where a plausible
  simplification breaks the premise. → **#041**
- **`resolvePossession` has FIVE loop re-entry paths** (offensive rebound, block recovery,
  OOB-offense, rebounding foul, flagrant). **A sixth is the signal to restructure the
  loop.** Parked in ideas.md on that trigger. → **#034 B**
- **A key/field mismatch in `SimConfig` does NOT fail loudly** — it binds `null`, and the
  first symptom was **184 NPEs**, not a bind error. Key and field name are kept in step by
  hand. Conversely, A's "no initializers" rule is enforced by **javac** (a `final` field
  with an initializer is a compile error under constructor binding), not by a test.
  → **#035**
- **⚠ A YAML description that wraps onto a line starting with `- ` breaks OpenAPI codegen**
  — it parses as a block sequence and fails with a snakeyaml error pointing at the
  *previous* property. Use a `>-` block scalar for any description long enough to wrap.
  → **#041**
- **`calibration.md` is the source of truth for targets**, superseding #022 D. Update it
  **and** the `CalibrationHarness` `(target ~N)` strings in the same change. The per-phase
  landing notes here are history — **do not retro-edit them**. → **#030**

### Final constants (current baseline — `calibration.md` holds the targets)

The live values are in `application-baseline.properties` (63 tunables) and `SimConfig`
(29 statics) since **#035**; these are the landings each phase recorded, and the entry to
read when you want to know *why* a value is what it is.

- **Blocks** `base-block-drive/post/perimeter/three = 0.056/0.048/0.023/0.005`,
  `BLOCK_SENSITIVITY = 0.12` → **#025** ⚠ see the inert-`base-block-three` line above.
- **OOB** `OOB_TOTAL_WEIGHT = 0.07`, split defense/offense `0.60/0.40`, skill-independent
  by construction → **#026**
- **Turnover causes** `56/10/9/8/6/4/3/2/2` (STOLEN dominant), `TO_CAUSE_SENSITIVITY = 0.20`,
  leans floored ≥ 0.05 → **#027**; `base-turnover = 0.0527` → **#042**
- **Rebound fouls** `REBOUND_FOUL_BASE = 0.055`, defense/offense `0.75/0.25`,
  `BONUS_FOULS_PER_PERIOD = 5` → **#028**
- **And-1** `AND_ONE_BASE = 0.055`, `AND_ONE_SENSITIVITY = 0.10`, `AND_ONE_FREE_THROWS = 1`
  → **#029**
- **Foul multipliers** `DRIVE/POST = 1.0`, `PERIMETER = 0.30`, `THREE = 0.133` → **#030**
- **Foul trouble** sit curve `{0,0,0,0.120,0.500,0.900,0}`, `VALUE_SENSITIVITY = 0.55`,
  `STARTER_BONUS = 0.15`, `BENCH_DISCOUNT_PER_SLOT = 0.05`, `MIN_ROSTER_FACTOR = 0.70`,
  `FRESHNESS_MARGIN = 1.0` → **#031**
- **Technicals** `TECHNICAL_FOULS_PER_TEAM_GAME = 0.35` → **#032**;
  **Flagrants** `FLAGRANT_FOULS_PER_TEAM_GAME = 0.16`, `FLAGRANT_EJECTION_LIMIT = 1` → **#034**
- **Shot bases** `base-drive/post/perimeter = 0.6801/0.6131/0.4603`, `base-three` untouched;
  `ft-base = 0.705`; `non-shooting-foul-share = 0.4123`; `base-no-basket-foul = 0.178`;
  `shot-share-drive/perimeter/post/three = 1.0/0.55/0.55/1.256`,
  `SHOT_MIX_SENSITIVITY = 0.5` → **#040 · #042 · #043 · #044**
- **Free-throw rebound** `FREE_THROW_REBOUND_LEAN = 0.68` (realized offensive share 0.1695)
  → **#043**
- **Putback** `offensive-rebounder-shot-weight = 2.0`,
  `OFFENSIVE_REBOUNDER_ASSIST_LEAN = 0.5` → **#044**
- **Rebound split** `base-offensive-rebound = 0.27`, **unchanged and deliberately so** → **#042 D3**

### Current landing (§3.22, #044 — 5-seed mean, seeds 1000–5000)

Points **115.82** · FG% **47.06** · 3P% **36.14** · FGA **89.40** · 3PA **36.96** ·
Assists **26.84** · FTA **23.58** · TO **14.68** · Off/Def reb **11.91 / 31.62** ·
Fouls **19.35** · Blocks **4.60** · foul-outs ~0.37. **Open residuals**: DefReb, OffReb
(the rebound-pool leak above), Fouls, 3P%. `calibration.md` holds the targets and the
rules for reading them.

---

