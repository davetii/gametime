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

> **⚠ §3.17 NEEDS A DESIGN PASS FIRST — there is no plan here yet, and the roadmap
> bullet is a seam, not a plan.** Resolve the open questions below into a numbered
> `decisions.md` **#040** (Decisions A, B, C…) **plus** an execute-ready plan in this
> file. **Write no production code in that session.**
>
> **⚠ THE FIRST QUESTION IS WHETHER §3.17 IS AN ENGINE PHASE AT ALL** (roadmap.md,
> #036 D). `ShotSelector.pickShotType` draws on **per-player** `shotTypeWeight(type)`
> leaned by the coach's `shotMixLean` — **and that lean scales PERIMETER and THREE
> together**, so it cannot raise threes while lowering mid-range, which is the shape the
> real gap needs. If the gap traces to the generated player population, the fix is
> **upstream of the possession engine** and this is not a `SimConfig` phase. **Answer
> that before designing any knob.**

---

## What §3.16 left for §3.17 to know (it shipped 2026-08, decisions.md #039)

**⚠ FGA IS NOW THE BINDING CONSTRAINT, WITH ~0.3 OF HEADROOM** (88.80 vs 89.1 real).
§3.16 spent its entire FGA budget: #039 C made the common foul a **dead possession** —
deliberately wrong as basketball — purely to avoid the ~0.76 FGA a returning variant
would have added. **A shot-mix pass that changes attempt economics changes that budget
in both directions**, so:

- If §3.17 **buys FGA headroom**, #039 C's concession becomes revisitable — the common
  foul could return the ball, which is the basketball-correct behavior. That is an
  explicit §3.16 follow-up, not an idea.
- If §3.17 **consumes FGA headroom**, it is competing with a concession already paid
  for. Measure FGA before and after; do not discover it at the end.

**⚠ POINTS READS 109.6, NOT 118.3, AND THAT IS §3.19'S TO RECOVER** (#039 H). §3.16
removed ~10 FTA by design. **Do not tune §3.17 against points** — the same #038 rule
that governed §3.16 governs this phase. The live targets for §3.17 are **3PA (37.0)**
and, as tripwires, **FGA (89.1)**, **FG% (47.1)** and **FTA (23.5)**.

**⚠ `sim.non-shooting-foul-share` IS PRICED BY THE PENALTY RATE, so it is not stable.**
Each conversion removes 2 FTs *unless* the committing team is already in the bonus, in
which case it awards 2. §3.16's own charge fix moved team-periods-in-penalty
51.1% → 55.7%, which is why the share shipped at **0.50** rather than the **0.43** the
design predicted. **Anything that moves the penalty rate — or the foul rate, or pace —
re-prices it. Re-check FTA; do not assume the constant still lands** (calibration.md
carries this).

**⚠ `PERSONAL_FOULS_PER_TEAM_GAME` is 20.15 now, not 19.0** — the flagrant divisor,
re-measured deliberately by §3.16. **#034 G forbids both treating it as a tunable and
letting it drift**: a pass that moves the foul rate must decide it again, in writing.

---

## §3.17 open questions (resolve these into decisions.md #040)

1. **Is the 3PA gap an engine problem or a player-generation problem?** (#036 D's
   gating question.) ⚠ **A STRONG PROVISIONAL ANSWER ALREADY EXISTS — see Verified
   facts: it looks like the WEIGHT FORMULA, not the population.** `shotTypeWeight` gives
   `DRIVE` the **sum of two** skills while every other type gets **one**, which predicts
   a 20% three share at an average player; the engine shows **22.5%** against a real
   **41.5%**. Since the skill calculators all centre on ~10 by construction, no plausible
   population shift closes that gap. **Confirm this measurement yourself before relying
   on it** — but if it holds, the phase is an **engine** pass and question 2 is live.
2. **If it IS an engine problem: what shape does the lever take?** `shotMixLean` scaling
   PERIMETER and THREE together is the known blocker — does it split, does a new
   per-type lean appear, or does the base weight table move? ⚠ **A re-partition that
   holds total FGA by construction is the §3.9/§3.16 shape and the cheap answer**; a
   lever that adds attempts spends the 0.3 of FGA headroom.
3. **What does moving ~17 attempts from twos to threes do to FG% and points?** A three
   makes less often and scores more — FG% (47.1, currently correct) is a tripwire, and
   points is not this phase's to fix but *is* going to move. Predict both before
   building, the way #039 C predicted FGA.
4. **Do the foul multipliers need re-checking?** `foul-mult-three` is 0.133 and the
   fouled-three rate is anchored on **~2% of 3PA** — that rate is scale-free, but the
   *count* of 3-FT trips rises with 3PA, and FTA is a tripwire now.
   ⚠ **FIX THE INSTRUMENT FIRST — it has under-counted by ~2× since §3.16** (backlog
   chore, test-only). The harness classifies a stopped shot by its **free-throw run**,
   and a `COMMON_FOUL` awards 0 or 2 but never 3, so it now sees only about half of the
   fouled threes (1.50% measured vs ~3.0% true). **Do not re-tune `foul-mult-three`
   against the current rows, and do not read them as evidence either way** until it
   classifies by `ShotType`.
5. **Does the rebounding gap resolve itself?** Off/def rebounds are both low (9.9/27.4
   vs 11.3/32.4) and #036 already suspects that is a 3PA symptom — a three misses
   longer. **Check whether §3.17 fixes it for free** rather than designing a rebound pass.
6. **Is `shotTypeWeight`'s sum-vs-average asymmetry deliberate or an oversight?**
   `shotTypeWeight` uses `drive + finishing` (a **sum**) while `offenseSkillForShot`
   uses `(drive + finishing) / 2.0` (an **average**) — the same two skills, combined two
   different ways in adjacent methods (`PlayerGameState:323` / `:332`). One of them is
   the 3PA lever; the other is the accuracy path and **must not move** (3P% is already
   correct at 36.7 vs 36.0). ⚠ **Decide this explicitly** — if the sum was intentional
   ("a drive is genuinely the most common shot"), then the fix is a deliberate re-weight
   rather than a bug fix, and the entry should say so. This is the #030 F / #039 E
   naming-and-intent discipline applied to a formula.
7. **Does the fix belong to the weights, a new lean, or both — and does it hold FGA?**
   ⚠ **A re-partition of the shot MIX holds total FGA by construction** (the §3.9/§3.16
   shape) because every possession still takes one shot; only its *type* changes. That
   is the cheap and safe answer given FGA has ~0.3 of headroom. **Anything that changes
   the NUMBER of attempts spends a budget §3.16 already spent** (#039 C).

---

### Verified facts (measured 2026-08 against the tree — for whoever designs this)

- **`ShotSelector.pickShotType`** (`ShotSelector:34`) does a weighted draw over all four
  `ShotType`s using `leanedWeight` (`ShotSelector:50`), which is
  `shooter.shotTypeWeight(type)` scaled by `shotMixLean` **for PERIMETER and THREE
  together** (`ShotSelector:52`). **That single line is the known blocker** — it cannot
  raise threes while lowering mid-range.
- **⚠ THE WEIGHTS ARE NOT SYMMETRIC, AND THIS LOOKS LIKE THE ACTUAL CAUSE.**
  `PlayerGameState.shotTypeWeight` (`PlayerGameState:323`) is:
  **`DRIVE -> drive + finishing`** (a **SUM OF TWO** skills) · `PERIMETER -> perimeter` ·
  `POST -> post` · `THREE -> longRange` (each a **single** skill).
  So at an average player — every skill ≈ 10, which is what the calculators are built to
  produce — the draw is **DRIVE 40% · PERIMETER 20% · POST 20% · THREE 20%**. DRIVE gets
  double weight purely because its formula adds two skills together.
- **The engine's observed mix matches that structural prediction almost exactly**:
  3PA 20.0 of FGA 88.80 = **22.5% of attempts**, against a structural 20%. **Real is
  37.0 of 89.1 = 41.5%.** ⚠ **So question 1 looks ANSWERED: this is the weight formula,
  not the generated population.** The calculators all center on ~10 (`LongRangeSkillCalculator`
  and `DriveSkillCalculator` both build a ~10-centered value), so no plausible population
  shift closes a 20%→41.5% gap. **Re-confirm this measurement first, then design.**
- **`offenseSkillForShot`** (`PlayerGameState:332`) — the *accuracy* path — uses
  **`(drive + finishing) / 2.0`**, i.e. an **average**, where `shotTypeWeight` uses the
  **sum**. ⚠ **The same two skills are combined two different ways in adjacent methods.**
  That asymmetry is the cheapest thing to examine, and it is worth deciding whether the
  sum is deliberate (drive is genuinely the most common shot) or an oversight.
- **3P% is fine — this is VOLUME, not accuracy**: 36.7% against a sourced 36.0%. Do not
  touch the make rates.
- **Baseline landing to beat** (5-seed mean, seeds 1000–5000, profile `local,baseline`,
  post-§3.16): points **109.6** · FG% **46.68** · 3P% 36.7 · ast 26.9 · TO 13.6 ·
  FTA **23.60** · fouls **20.53** · **FGA 88.80** · 3PA **20.0** · foul-outs 0.517 ·
  penalty 55.7%. ⚠ **Points 109.6 is BY DESIGN and is §3.19's** (#039 H).
- **Rebounds are low and may be a symptom**: off 9.9 vs 11.3, def 27.4 vs 32.4. #036
  suspects the 3PA gap causes it (a three misses longer). Check before designing a fix.
- **`SimConfig` shot bases** (`application-baseline.properties`): `base-drive=0.5975`,
  `base-perimeter=0.4375`, `base-three=0.3375`, `base-post=0.4975`. ⚠ **These are
  MAKE probabilities, not shot-mix weights** — they do not select the shot type, so they
  are not the 3PA lever. The mix comes only from `shotTypeWeight` × `shotMixLean`.
- Harness: `-Dcalibration=true -DcalibrationSeed=NNNN`, 6 rounds × 17 matchups ≈ 102
  games per seed. Judge at **5 seeds**. Since §3.15 the sim profile rides the ordinary
  Spring list (`-Dspring.profiles.active=test,baseline`), and `baseline` must stay in it.

---

### ⚠ Do NOT (standing guardrails — carried forward into §3.17–§3.19)

These outlive any one phase. ⚠ **Re-centering is §3.19, not §3.17** (#038) — §3.16 and
§3.17 are mechanic passes that deliberately move composition, and §3.19 re-solves the
numbers afterwards.

- **Do NOT let the common foul return the ball** (#039 C) — measured: it caps the share
  at ~6% and blows FGA. ⚠ **Unless §3.17 buys FGA headroom**, in which case revisiting it
  is an explicit #039 follow-up rather than a violation.
- **Do NOT chase the points drop** (#039 H) — points reads ~110 on purpose; it is
  §3.19's, by #038's rule.
- **Do NOT cite the ~35% share, and do NOT cite 0.43 either** — both withdrawn (#039 D);
  it shipped at **0.50**, and it is **derived, not sourced**. ⚠ It is also **priced by
  the penalty rate**, so re-check FTA whenever that moves.
- **Do NOT move or duplicate `defender.recordFoul()`** (#039 A) — its position before all
  branching is what holds the foul total by construction.
- **Do NOT change the existing foul roll** — `isFoul` / `isAndOne` / `resolveReboundFoul`
  keep their rates, inputs and RNG draws (#034 A, user call).
- **Do NOT skill-weight the common-foul share** (#039 E) — `foulProne` already had its say
  at `pickDefender`; weighting the grade too applies one signal twice (#034 E).
- **Do NOT make the charge flagrant-eligible** (#039 G).
- **Do NOT exclude `COMMON_FOUL` or `OFFENSIVE_FOUL` from the bonus tally** (#039 B/G) —
  they are personal fouls. Only the **technical** is excluded (#032 E).
- **Do NOT trim `sim.base-no-basket-foul`** — measured 2026-08: fixes under half the FTA
  excess and breaks fouls (17.27) *and* FGA (91.2). ⚠ That run also **DISPROVED #028's
  wrong-way-lever claim** at the current config — do not cite #028 as live.
- **Do NOT re-tune and-1s** (5.8% of FTA, set on realism, #029) or
  **`sim.to-weight-offensive-foul`** (#027 A fixed the turnover count at the gate).
- **Do NOT add a fourth removal path in `RotationState`** — ejection is the **hard** tier
  and extends `isDisqualified(...)` (#031 H).
- **Do NOT re-tune §3.13's foul-trouble sit curve** — measured saturated (#031 note).
- **Do NOT re-open §3.12's `FOUL_MULT_*`** (#030 G) or `pickDefender`'s `individualDefense`
  weighting (#031 A).
- **Do NOT vary `PERSONAL_FOULS_PER_TEAM_GAME` as if it were a tunable** (#034 G) — and
  do NOT let it drift either. §3.16 re-measured it to **20.15**; ⚠ **any later pass that
  moves the foul rate must decide it again, in writing.**
- **Do NOT back-solve §3.14a's technicals constant** against the ~5% harness gap — that is
  #032 B2's nominal-vs-actual pace effect, not drift.
- **Do NOT add a stored `flagrant2` / `ejected` flag** (#034 F) — the predicate stays derived.
- **Do NOT exempt the flagrant retention from `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`,
  and do NOT raise the BASELINE cap** (#034 B). §3.15 makes it *profilable* (#035 C); the
  fence protects the **baseline value**.
- **Do NOT add 2 FTs on top of the underlying foul's award** (#034 C) — a flagrant
  **replaces** it. A flagrant stopped THREE is **2** FTs.
- **Do NOT add a `flagrant_fouls` box-score column** (#034 H).
- **Do NOT reuse `pickTechnicalFreeThrowShooter`** (#034 D) — its premise is that nobody
  was fouled; on a flagrant, and on a common foul, somebody was.

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
