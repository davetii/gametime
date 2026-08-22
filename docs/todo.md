# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.19).

Current focus: **§3.17 — shot mix / the 3PA gap**. Phase 3's tail is
**§3.17 shot mix → §3.18 steals → §3.19 recalibration** (`decisions.md` **#036**,
resequenced by **#038**). ⚠ **The old "§3.16 = recalibration" is now §3.19** — see
#038 for the current mapping. §3.7–§3.13, §3.14a, §3.14b, §3.15 and **§3.16** have
all shipped.

> **✅ §3.17'S DESIGN IS RESOLVED AS `decisions.md` #040 (Decisions A–N).** The
> execution plan is below. Do not re-litigate A–N; add an implementation note to #040
> recording any divergence, and flip the roadmap bullet.
>
> **The shape, in one line:** the shot mix stops being an emergent property of the
> player calculators and becomes **four tunable shares in `application-baseline.properties`
> modulated by skill**, with `shotMixLean` **split** so THREE and PERIMETER move in
> opposite directions. 3PA **19.6 → ~37.0**.
>
> **⚠ #036 D'S GATING QUESTION IS SETTLED: THIS IS AN ENGINE PASS, NOT A PLAYER-
> GENERATION ONE** (#040 A, re-measured this design pass). `shotTypeWeight` gave DRIVE
> the **sum of two** skills while the other three types got **one each**, predicting a
> 20% three share at an average player; the engine measures **20.9%** against a real
> **41.5%**. The calculators all centre on ~10 by construction, so no population shift
> closes that. **The gap is the formula.**
>
> **⚠ THE TODO'S OLD PREDICTION THAT A RE-PARTITION HOLDS FGA IS WRONG, AND #040 E IS
> THE CORRECTION.** A stopped shot charges **no FGA**, and `foul-mult-three` is **0.133**
> against DRIVE's **1.0** — so a draw moved to a three is **7.5× less likely to be
> stopped** and more likely to become a charged attempt. **FGA rises ~88.7 → ~89.9,
> past its sourced 89.1.** §3.17 SPENDS headroom rather than buying it, so **#039 C's
> dead-possession concession is NOT revisited by this phase** — that is now §3.19's.
>
> **⚠ FG% IS PREDICTED TO FALL ~46.7 → ~44.5 AND THAT IS NOT A REGRESSION** (#040 F).
> The engine's **2P% is 49.2 against a real 55.0** — an error the two-heavy mix has
> been hiding, because at a 21% three share FG% ≈ 2P%. §3.17 makes the shape final;
> **§3.19 re-solves `base-drive`/`base-post`/`base-perimeter`.** ⚠ **`base-three` must
> NOT move — 3P% is correct (37.8 vs a sourced 36.0).**
>
> **The ONE number §3.17 is judged on is 3PA (37.0).** FGA, FG% and blocks are all
> **predicted** to move and are recorded below so the landing is not misread as drift.
>
> **§3.17 adds NO new possession branch and NO new RNG draw** — `pickShotType` still
> takes exactly one `nextDouble()` and still returns one of four types. What changes is
> how the four weights are computed. `possession-flow.puml`'s ShotSelector node and its
> new note were updated by the design pass and **already describe the target state**.

---

## §3.17 execution plan (decisions.md #040 — resolved)

**Build preamble.** Java 21 via SDKMAN; Homebrew Maven defaults to JDK 25 and breaks
Lombok, so always:

```
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean test
```

The **JaCoCo gate is per-package and runs at `install`, not `test`** — a green
`mvn test` does not prove it passes. Invoke the **`test-coverage`** skill when adding
production code.

**⚠ The harness needs the profile in the ENVIRONMENT, not on the mvn command line.**
`-Dspring.profiles.active=...` does **not** reach the forked surefire JVM (measured this
design pass — the context came up with `activeProfiles = []` and tried to reach Postgres).
Use:

```
SPRING_PROFILES_ACTIVE=local,baseline JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -pl gametime-app test -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000 -DfailIfNoTests=false
```

Run it from `gametime-service/`, not the repo root. Confirm the report's
`Profiles: local,baseline` line before trusting any number. Judge at **5 seeds**
(1000–5000).

**Work in the order below.** Step 0 is a prerequisite the phase does not own but is
blocked on; Steps 1–2 must be measured *separately* so the landing is attributable.

### Step 0 — fix the fouled-three instrument FIRST (backlog chore, test-only, #040 G)

- [ ] `CalibrationHarness.flushStoppedShot` classifies a stopped shot by its
      **free-throw run** (3 FTs ⇒ a THREE). §3.16's `COMMON_FOUL` awards 0 or 2 but
      **never 3**, so the fouled-three and 3-FT-trip rows under-count by ~2×
      (**1.50% of 3PA measured against ~3.0% true**). Read the `ShotType` off the
      event instead — `shotTypeOf` already exists for the and-1 channel.
- [ ] Re-run the harness and record the **corrected pre-§3.17 baseline** for both rows.
      ⚠ **This is the number §3.17's landing is compared against** — the current rows
      are evidence of nothing in either direction.
- [ ] ⚠ **Do NOT re-tune `sim.foul-mult-three` in this phase** (#030 G's fence stands,
      #040 G). The rate is anchored on ~2% of 3PA and is **scale-free**, so it stays
      correct as 3PA doubles; only the *count* of 3-FT trips rises with it.
- [ ] Tick the matching bullet in [backlog.md](backlog.md) and update
      [calibration.md](calibration.md)'s "BROKEN INSTRUMENT" section — it becomes history.

### Step 1 — the share table + skill modifier (#040 B/C/J)

- [ ] Four new tunables in `application-baseline.properties`, raw weights normalized at
      the call site (the `to-weight-*` / `block-*` convention — **only ratios matter**,
      so a profile author never has to make them sum to 1):
      `sim.shot-share-drive` · `sim.shot-share-perimeter` · `sim.shot-share-post` ·
      `sim.shot-share-three`. **Starting values 1.0 / 0.55 / 0.55 / 1.30** (#040 K).
- [ ] ⚠ **No Java initializers** (#035): `private final` + accessor on `SimConfig`,
      bound by `@ConfigurationProperties`. A default silently creates a second value,
      since javac inlines constants into each caller. **No `public static final` alias.**
- [ ] `public static final double SHOT_MIX_SENSITIVITY` on `SimConfig` — **model
      machinery, NOT a tunable, NOT in the properties file** (the class of
      `BLOCK_SENSITIVITY` / `AND_ONE_SENSITIVITY`). Start at **0.5**.
- [ ] Rewrite `PlayerGameState.shotTypeWeight` (`PlayerGameState:323`) as
      **`share(type) × (1 + SHOT_MIX_SENSITIVITY × (skill − 10) / 10)`**, over
      `longRange` (THREE) · `perimeter` (PERIMETER) · `post` (POST) ·
      **`(drive + finishing) / 2.0`** (DRIVE).
- [ ] ⚠ **`offenseSkillForShot` (`PlayerGameState:332`) IS NOT TOUCHED.** It is the
      **accuracy** path and it is already correct — 3P% reads 37.8 against a sourced
      36.0. The `/2.0` there is the *right* form; the **sum** in `shotTypeWeight` was
      the bug (#040 B). After this step the two methods finally agree on how `drive`
      and `finishing` combine. **`finishing` no longer decides how OFTEN a drive is
      attempted — only how well it goes in, plus a half-weight nudge on selection.**
- [ ] `PlayerGameState` must now read `SimConfig` for the shares — it **already holds an
      injected `config`** (it reads `config.fatigueMaxPenalty()`, `minDrainScale()`,
      `energyDrainPerPossession()`), so this is a local change with **no new
      construction-site plumbing**.
- [ ] ⚠ **Tunable count 58 → 62.** It lives in **FOUR** places and all four move
      together (verified this design pass): `SimConfig:137` (the header comment),
      `SimConfig:1497` and `:1512` (`baseline()`'s javadoc + its error message),
      `SimConfig:1522`, and `SimConfigProfileBindingTest:47`
      (`EXPECTED_TUNABLE`, which asserts BOTH the constructor arity and the instance-field
      count, so a missed accessor fails loudly). The §3.16 precedent moved 57 → 58.
- [ ] **Measure this step ALONE at 5 seeds before Step 2.** With shares 1/0.55/0.55/1.30
      and the lean still conflated, 3PA should already move most of the way — record it,
      because Step 2's contribution is otherwise unattributable.

### Step 2 — split the lean (#040 D)

- [ ] `ShotSelector.leanedWeight` (`ShotSelector:52`) currently scales **PERIMETER and
      THREE by the same `shotMixLean`** — #036 D's named blocker, since it cannot raise
      threes while lowering mid-range. It becomes: **THREE × `shotMixLean`; PERIMETER ×
      `1 / shotMixLean`; DRIVE and POST unscaled.**
- [ ] Update `ShotSelector.pickShotType`'s javadoc — it currently says the lean scales
      "PERIMETER and THREE" together, which becomes false.
- [ ] ⚠ **`CoachModifiers.shotMixLean()` is NOT touched** — it stays the `offensiveScheme`
      avg-10 multiplier (#022). The conflation lives in `ShotSelector` and the split
      lives there too. **No new coach attribute** (#018's five are not reopened) and
      **no new `SimConfig` value** — the reciprocal is free.
- [ ] `ShotSelectorTest:98` asserts a high lean produces more *jumpers* (PERIMETER **or**
      THREE together). **That premise is now false by design** — rewrite it as two
      assertions: a high lean raises THREE **and lowers PERIMETER**.
- [ ] Note that this step is **mix-neutral in the aggregate**: coach `offensiveScheme` is
      centred on 10 across the league, so the two directions roughly cancel over 102
      games. Its purpose is that a *given* coach finally means something, not a 3PA move.

### Step 3 — land 3PA, then read the tripwires (#040 E/F/I/K)

- [ ] Tune the four shares against the **measured 3PA line at 5 seeds** until it lands
      **~37.0**. ⚠ **The LANDING is the target, not the constants** (the §3.16 lesson —
      #039's share came out 0.50 against a predicted 0.43).
- [ ] ⚠ **The three share of DRAWS must exceed the target share of ATTEMPTS**, because
      threes are stopped less often and therefore convert to charged attempts at a higher
      rate. Do not set the shares to 41.5% and expect 41.5%.
- [ ] The user's stated tolerance is **37–44% of non-free-throw shots**; the sourced
      2025-26 figure (41.5%) sits inside it. **`baseline` aims at the sourced 37.0**; the
      band is what makes a landing acceptable, not what sets the aim (#040 K).
- [ ] Record the landing against the **predictions** below, and say in the implementation
      note where the model was wrong. Predicted at 3PA 37.0: **FGA ~89.9** · **FG% ~44.5**
      · **blocks ~2.6** · **def reb ~28.9** · **off reb ~10.6** · **stopped shots
      7.93 → ~6.4** · **points ~113**.
- [ ] ⚠ **Do NOT chase points** (#038's rule, #039 H, #040 I). It is expected to rise
      ~110 → ~113 as a *by-product*; that is a happy accident, not evidence the pass is
      right. **3PA is the evidence.**
- [ ] ⚠ **Do NOT tune FG%, FGA or blocks back** (#040 E/F). All three are predicted to
      breach and all three are **§3.19's**. Record them; do not fix them.
- [ ] Check FTA — it should fall ~1–2 (fewer stopped shots ⇒ fewer shooting-foul FTs).
      ⚠ **`sim.non-shooting-foul-share` is priced by the penalty rate** (#039's execution
      finding), and this pass moves the foul rate, so **re-check FTA rather than assuming
      0.50 still lands**. If FTA drifts materially off 23.5, say so and leave it to §3.19.
- [ ] **`PERSONAL_FOULS_PER_TEAM_GAME` (20.15, the flagrant divisor):** #034 G forbids
      both treating it as a tunable and letting it drift. This pass **lowers the foul
      rate** (fewer stopped shots ⇒ fewer shooting fouls). **Re-measure it and decide in
      writing** — even if the decision is "unchanged, the move is inside noise."
- [ ] Re-read the **corrected** fouled-three rows from Step 0. The 3-FT-trip *count*
      should roughly double with 3PA while the *rate* (~2% of 3PA) holds. **A rate that
      moved means something other than the mix changed.**

### Step 3b — rename `COMMON_FOUL` → `NON_SHOOTING_FOUL` (#040 M/N)

⚠ **A PURE RENAME — no behavior, no rate, no RNG draw, no branch.** It must be
**landing-neutral at every seed**; if any number moves, something else was changed.
**Do it in the same commit as Step 0**, or immediately after: both touch
`CalibrationHarness`'s foul classification, and a rename split across two passes is the
#032 engine/harness drift risk for no gain (#040 N).

- [ ] `PossessionEngine:54` — `COMMON_FOUL_OUTCOME = "COMMON_FOUL"` becomes
      `NON_SHOOTING_FOUL_OUTCOME = "NON_SHOOTING_FOUL"`. **This is the only real string**;
      the other 5 main-code references are comments.
- [ ] 11 test references across `PossessionEngineTest`, `FoulResolverTest`,
      `SimConfigTest` and `CalibrationHarness` (measured this design pass).
- [ ] ⚠ **`CalibrationHarness` matches this string when tallying `foulsByOutcome`** — if
      it is missed, the harness silently reports 0 non-shooting fouls while the engine
      keeps emitting them. **That is the #032 failure mode exactly.** Grep for the literal
      before declaring the rename done.
- [ ] ⚠ **NO schema change and NO migration** (#040 N, user call): games simulated before
      this pass keep `COMMON_FOUL` in `game_event.outcome` forever. The game has not
      launched and those rows are test data. **Record the cutover in #040's implementation
      note** so whoever first queries across the boundary is not surprised.
- [ ] Update the docs that name the outcome: `possession-flow.puml`, `calibration.md`,
      `game.md`, `roadmap.md`. ⚠ **Do NOT retro-edit #039** — it is shipped, and #040 M is
      the record of the reversal (the same discipline the §3.16-numbering remaps use).
- [ ] Re-run the harness and confirm the landing is **byte-identical** to pre-rename.

### Step 4 — the era profile and the docs (#040 L)

- [ ] Add the same four keys to `application-nineties.properties` with a genuinely
      two-heavy mix. The file already claims *"low pace, **few threes**, more post play"*
      and **has never been able to express the first clause** — it can only lower
      `base-three` (how often a three GOES IN), which is a different thing entirely.
- [ ] ⚠ It stays **uncalibrated and un-targeted** (#035 D) — a demonstration that the
      mechanism works, not a claim about 1990s basketball. Do not tune it, and do not
      read a run under it as a result.
- [ ] `calibration.md`: 3PA row to ✅ with the landing; **FG% row from ✅ to ⚠️ with the
      2P%-49.2-vs-55.0 finding and the §3.19 hand-off**; FGA row over its ceiling; a new
      **blocks** row (4.8 → ~2.6, a new §3.19 job); rebound rows partially closed. Update
      the `CalibrationHarness` `(target ~N)` strings in the same change.
- [ ] `roadmap.md`: flip the §3.17 bullet to `[x]` with the landing, and **say plainly
      that FG% and FGA land worse on purpose** — the #039 H precedent, where a phase
      shipping a visibly worse number needed the roadmap to say so.
- [ ] `possession-flow.puml`: the design pass already drew the target state. **Confirm it
      matches what shipped** and adjust if the placement moved. Validate with
      `plantuml -checkonly`, then render with **`-DPLANTUML_LIMIT_SIZE=16384`** and confirm
      the PNG is taller than 4096px (it is ~10,900px today; a plain `-tpng` silently truncates).
- [ ] Implementation note on **#040** — divergences, the resolved open-at-execution items,
      final constants, the landing, and **where the #040 E/F predictions were wrong**.

### The reconciliation invariant (unchanged by this phase)

`count(SHOT events with a 3PT outcome) == sum(BoxScore.threePointersAttempted)`, and
the existing assist/block reconciliation the harness already runs each game. **§3.17
adds no event and changes no accumulator**, so a mismatch means the mix change reached
something it should not have.

### ⚠ Do NOT (standing guardrails — carried forward into §3.17–§3.19)

These outlive any one phase. ⚠ **Re-centering is §3.19, not §3.17** (#038) — §3.16 and
§3.17 are mechanic passes that deliberately move composition, and §3.19 re-solves the
numbers afterwards.

- **Do NOT touch `offenseSkillForShot`** (#040 B) — it is the accuracy path, it is
  correct, and its `/2.0` is the *right* form. Only `shotTypeWeight` moves.
- **Do NOT move `sim.base-three`** (#040 F) — 3P% is 37.8 against a sourced 36.0. This
  phase changes how OFTEN a three is taken, never how often it goes in.
- **Do NOT tune `base-drive` / `base-post` / `base-perimeter` here** (#040 F) — the
  2P%-vs-55.0 error is real and is **§3.19's**; the pass cannot tell "the mix moved"
  from "the rates were wrong" while both are moving.
- **Do NOT re-tune `sim.foul-mult-three`** (#030 G, #040 G) — scale-free by construction,
  and the instrument that would judge it was broken until Step 0.
- **Do NOT re-tune the four `sim.base-block-*`** — blocks falling to ~2.6 is a predicted
  §3.19 row (#040 E), not a §3.17 miss.
- **Do NOT let the common foul return the ball** (#039 C) — ⚠ and §3.17 **does not** buy
  the FGA headroom that would have reopened it (#040 E). **That §3.16 follow-up is
  answered NO.** It passes to §3.19.
- **Do NOT chase points** (#039 H, #040 I) — it rises ~110 → ~113 as a by-product and the
  remainder is §3.19's, by #038's rule.
- **Do NOT add a coach attribute for the mid-range/three axis** (#040 D) — the reciprocal
  expresses it and #018's five are not reopened.
- **Do NOT scale `longRange` in `LongRangeSkillCalculator`** (#040 A/C) — the population
  answer, ruled out; it would also break 3P%, and it puts sim calibration on the wrong
  side of #022's scope fence.
- **Do NOT vary `PERSONAL_FOULS_PER_TEAM_GAME` as if it were a tunable** (#034 G) — and
  do NOT let it drift. ⚠ **This pass moves the foul rate, so it must be decided in writing.**
- **Do NOT move or duplicate `defender.recordFoul()`** (#039 A), **do NOT change the
  existing foul roll** (#034 A), **do NOT skill-weight the common-foul share** (#039 E),
  **do NOT exclude `COMMON_FOUL`/`OFFENSIVE_FOUL` from the bonus tally** (#039 B/G),
  **do NOT make the charge flagrant-eligible** (#039 G).
- **Do NOT trim `sim.base-no-basket-foul`** — measured 2026-08: fixes under half the FTA
  excess and breaks fouls (17.27) *and* FGA (91.2). ⚠ That run also **DISPROVED #028's
  wrong-way-lever claim** at the current config — do not cite #028 as live.
- **Do NOT re-tune and-1s** (#029) or **`sim.to-weight-offensive-foul`** (#027 A).
- **Do NOT add a fourth removal path in `RotationState`** (#031 H); **do NOT re-tune
  §3.13's foul-trouble sit curve** (measured saturated); **do NOT re-open §3.12's
  `FOUL_MULT_*`** (#030 G) or `pickDefender`'s `individualDefense` weighting (#031 A).
- **Do NOT back-solve §3.14a's technicals constant** against the ~5% harness gap (#032 B2).
- **Do NOT add a stored `flagrant2` / `ejected` flag** (#034 F), **a `flagrant_fouls`
  box-score column** (#034 H), or **reuse `pickTechnicalFreeThrowShooter`** (#034 D).
- **Do NOT exempt the flagrant retention from `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`,
  and do NOT raise the BASELINE cap** (#034 B).

### Open at execution (small, constrained — #040)

- **The four share values.** #040 K gives 1.0 / 0.55 / 0.55 / 1.30 as a start; measure at
  5 seeds and adjust until 3PA lands ~37.0. **The landing is the target, not the constant.**
- **`SHOT_MIX_SENSITIVITY`.** Start at **0.5** (the engine's general sensitivity). Sanity-
  check that a `longRange`-19 specialist shoots visibly more threes than a `longRange`-6
  big **without either reaching a degenerate share**. ⚠ It is an **unsourced feel constant
  whose error is invisible in the aggregates by construction** (the shares are normalized),
  so the check is per-player, not aggregate.
- **`PERSONAL_FOULS_PER_TEAM_GAME`** — re-measure and decide in writing (#034 G).
- **The `nineties` share values** — illustrative, uncalibrated, un-targeted.

---

### Verified facts (measured 2026-08 against the tree — for whoever executes)

- **Baseline to beat** (seed 1000, 102 games, `local,baseline`, post-§3.16, re-measured
  by this design pass): points **110.2** · FG% **46.7** · 3P% **37.8** · **FGA 88.7** ·
  **3PA 19.6** · ast 26.6 · TO 13.5 · **FTA 24.3** · fouls **20.74** · blocks **4.8** ·
  off reb **10.0** · def reb **27.4** · foul-outs 0.593 · penalty **55.4%** · stopped
  shots **7.65** (instrument) · COMMON_FOUL 34.6% / SHOOTING_FOUL 36.9%.
  ⚠ **Single-seed — the 5-seed means in calibration.md are the reference.** Points 110.2
  is **§3.19's** (#039 H).
- **`ShotSelector.pickShotType`** (`ShotSelector:34`) does a weighted draw over all four
  `ShotType`s using `leanedWeight` (`ShotSelector:50`), which scales `shotTypeWeight(type)`
  by `shotMixLean` **for PERIMETER and THREE together** (`ShotSelector:52`) — the blocker
  Step 2 removes.
- **`PlayerGameState.shotTypeWeight`** (`:323`) is **`DRIVE -> drive + finishing`** (a
  **SUM**) · `PERIMETER -> perimeter` · `POST -> post` · `THREE -> longRange`. At an
  average player the draw is **DRIVE 40 · PERIMETER 20 · POST 20 · THREE 20**.
- **`offenseSkillForShot`** (`:332`) uses **`(drive + finishing) / 2.0`** — an **average**.
  ⚠ **This one is CORRECT and does not move** (#040 B).
- **The sum is an OVERSIGHT and the evidence is #021 D's own wording**: it specifies the
  draw over *"`drive`/`finishing`/`perimeter`/`post`/`longRange`"* — **five skills for
  four shot types**. The code collapsed two onto DRIVE by adding them, exactly as
  `offensiveWeight()` (three lines up, same commit `bdb5609`) sums all five for the
  *shooter* draw, where summing is correct because there is no per-type normalization.
  **Argued nowhere in #021 or #022.**
- **Every `SkillCalculator` centres on ~10 by construction** — `adj()` contributes 0 at
  attribute 10, `comboAdj()` at 20, and `SCALE_AVG = 10`. ⚠ **This is why the population
  cannot be the cause** (#040 A).
- **MEASURED DRAW SHARE, correcting for stopped shots** (which charge no FGA): draws ≈
  FGA + stopped = 88.7 + 7.65 = **96.35**; threes drawn ≈ 3PA + true stopped threes
  (0.29 ÷ 0.50, correcting the broken instrument) = **20.2**, i.e. **20.9%** against the
  structural prediction of **20%**. Real is **41.5%**.
- **⚠ THE FGA MECHANISM, in one line:** a stopped shot charges **no FGA** (the foul branch
  returns before `recordFieldGoalAttempt()`), and `foul-mult-three` is **0.133** vs DRIVE's
  **1.0**, so **more threes ⇒ fewer stopped shots ⇒ MORE FGA.** Per-draw stop rate at
  multiplier 1.0 back-solves to **0.1199** from the measured totals.
- **⚠ THE HIDDEN 2P% ERROR:** engine 2P% is **49.2%**; real 2025-26 is **55.0%** (28.65
  made of 52.1). At a correct 41.5% three share the engine would need **2P% 53.7%** to
  land FG% 47.1. **#036 A's "FG% is inside the noise band" was true only AT THE WRONG SHOT
  MIX** — #036 B's cancelling-errors finding recurring one level down.
- **`SimConfig` shot bases** (`application-baseline.properties`): `base-drive=0.5975`,
  `base-perimeter=0.4375`, `base-three=0.3375`, `base-post=0.4975`. ⚠ **These are MAKE
  probabilities, not mix weights** — they do not select the shot type.
- **Foul multipliers**: `foul-mult-drive=1.0`, `foul-mult-post=1.0`,
  `foul-mult-perimeter=0.30`, **`foul-mult-three=0.133`**. **Block bases**:
  `base-block-drive=0.056`, `base-block-post=0.048`, `base-block-perimeter=0.023`,
  **`base-block-three=0.005`**.
- **The harness prints NO shot-type mix line** — the four-way draw share had to be
  back-solved this pass. A backlog chore, not this phase (#040 follow-up).
- Harness: `-Dcalibration=true -DcalibrationSeed=NNNN`, 6 rounds × 17 matchups ≈ 102
  games per seed. Judge at **5 seeds**. ⚠ **Pass the profile via `SPRING_PROFILES_ACTIVE`,
  not `-Dspring.profiles.active`** — the latter does not reach the forked surefire JVM
  (measured this pass). `baseline` must stay in the list and order is positional.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved out
so this file can be rewritten each phase without losing it. **This is a ROUTING TABLE —
pointers only.** The reasoning lives at the destination; do not restate it here, and do
not add "done" entries (those are the `#NNN` entry's job).

**Deferred engine work — see the cited entry's follow-up list:**

- **Technicals** — bench/coach technicals; a technical's lack of causal input → **#032**
- **Foul trouble** — period-/time-awareness (#031 C); getting foul-outs below ~0.38
  (lever measured saturated); splitting `substitutionAggressiveness` (#031 B) → **#031**
- **Over-dispersion** — `pickDefender` concentration also reaches blocks, steals and
  shot contests → **#031 A**
- **Seams left open by their design pass** → §3.6 **#024** · §3.7 **#025** · §3.9
  **#027** · §3.10/§3.11 **#028/#029** (incl. `committing_team_id` not on the OpenAPI
  `GameEvent`) · §3.12 **#030** (~20 3PA/team/game vs the NBA's ~37, the **§3.17** input)
  · §3.16 **#039** (the unsourced shooting-foul share; the dead-possession concession,
  revisitable if §3.17 buys FGA headroom)

**Other files own these outright:**

- **§3.19 (recalibration)**, and the phase sequence generally → [roadmap.md](roadmap.md)
- **Calibration targets** → [calibration.md](calibration.md), **the source of truth**.
  Update it *and* the `CalibrationHarness` `(target ~N)` strings together.
- **Infra/tooling/data-hygiene chores**, and **the `decisions.md` condense pass** (a
  **gate on starting Phase 4**) → [backlog.md](backlog.md)
- **Untriaged ideas**, incl. the parked cap 3→5 tuning idea and strategic substitution →
  [ideas.md](ideas.md)

**Standing facts (true every phase, not deferrals):**

- `CalibrationHarness` is disabled by default and lives in the `sim` test sources; run
  it with `-Dcalibration=true -DcalibrationSeed=NNNN`. **Re-run after any `SimConfig`
  change.** Since §3.15 the sim profile is selected by the ordinary Spring profile list —
  `-Dspring.profiles.active=test,baseline,nineties` (#035 F). ⚠ **`baseline` must always
  be in the list** (it carries all 57 values), and **order is positional** — putting it
  last lets it win and silently neutralizes the era file. The **effective-config dump**
  in the report is what catches that, and it also catches a caller passing its own copy
  of a profilable value (the §3.15 harness-pace trap).
- **Do NOT touch `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`'s baseline value** without its
  own recalibration pass (#034 B) — though §3.15 makes it *profilable* (#035 C).
