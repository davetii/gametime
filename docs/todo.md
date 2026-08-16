# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.19).

Current focus: **§3.16 — shooting-foul composition**. Phase 3's tail was
**resequenced** (`decisions.md` **#036**) into **§3.16 shooting fouls → §3.17 shot mix /
3PA → §3.18 steals → §3.19 recalibration**. ⚠ **The old "§3.16 = recalibration" is now §3.19** — see
#036 F for the mapping. §3.7–§3.13, §3.14a, §3.14b and §3.15 have all shipped.

> **⚠ §3.16 NEEDS A DESIGN PASS FIRST — there is no execute-ready plan here yet.**
> Resolve the open questions below into a new `decisions.md #039` (Decisions A, B, C…)
> **plus** an execute-ready plan in this file. **Write no production code in that
> session.**
>
> **The one-line goal (user's words):** *fewer fouls that award free throws, more that
> don't, with the total held constant.*
>
> **The measured problem.** Total fouls **look** right — 19.35 vs 19.9 real — ⚠ **but
> that reading is partly an artifact of Q7: charges are not counted as fouls at all, and
> counting them puts the real figure at 20.87.** But
> FTA is **34.0 vs 23.5**, i.e. **145% of real**, because **74.8% of the engine's fouls
> are `SHOOTING_FOUL`** (14.67 of 19.61) and those alone produce **29.84 of the 34.0
> FTA**. The only free-throw-free foul category is offensive rebounding fouls at
> **0.58/game**. `FoulResolver.isFoul` is a **boolean** — when it fires the outcome is
> always `SHOOTING_FOUL` — so **there is no "what kind of foul" branch to tune.**
>
> **⚠ THIS IS A MECHANIC PASS.** It adds a possession branch, so
> `possession-flow.puml` **must** change in the same session (#036 G amends #034's
> "last new mechanic" claim). That is the opposite of §3.15's fence.
>
> **⚠ ITS VALIDATION GATE IS NOT "REPRODUCE THE LANDING".** §3.16 is *supposed* to move
> FTA. What must NOT move: **total fouls (19.35)**, **FG% (46.9, already correct)**, and
> **FGA (88.4 vs 89.1 real)**. Those three are the tripwires.

---

## §3.16 — shooting-foul composition: the open questions for the design pass

**Read first** — these are the input, not this list:
- **`decisions.md` #036** — why the phase exists, the sourced row, and the measured
  experiment that ruled out the obvious knob.
- **`docs/calibration.md`** — the sourced targets (2025-26). FTA 23.5 is the number
  this phase is aimed at.
- **`docs/possession-flow.puml`** — the `FoulResolver` partition and the flagrant
  branch this would sit beside. **Read it before proposing a branch.**
- **`PossessionEngine`** — the five `PlayType.FOUL` emission sites (lines ~212, 456,
  539, 581, 622). §3.16 adds a sixth or re-partitions the first.

> **✅ ALREADY SETTLED — do not re-litigate:**
>
> **(i) `sim.base-no-basket-foul` is NOT the lever.** Measured 2026-08 at 0.15 → 0.11,
> 3 seeds: FTA 34.0 → 29.1 ✅ and points 118.3 → 117.1 ✅, but **fouls 19.35 → 17.27** ❌
> and **FGA 88.4 → 91.2** ❌. Fixes under half the excess, breaks two correct numbers.
> ⚠ **That run also DISPROVED #028's wrong-way-lever claim at the current config** —
> trimming *lowered* points. #028 was measured pre-§3.12. Do not cite it as live.
>
> **(ii) And-1s are not the lever.** 1.96 of 34.0 FTA (5.8%); #029 set the rate on
> realism at 4.8% of made FG. Leave it.
>
> **(iii) `sim.to-weight-offensive-foul` cannot add fouls.** #027 A fixed the turnover
> *count* at the gate — those weights only re-partition turnovers, and raising this one
> disturbs the deliberately-protected STOLEN share.
>
> **(iv) FG% needs NO work** (#036 A) — real 47.1%, engine 46.9%, inside the noise band.

### Verified facts (measured 2026-08 against the tree — for whoever designs this)

- **`FoulResolver.isFoul` is a boolean** and its hit is emitted at
  **`PossessionEngine:212`** as `"SHOOTING_FOUL"`, then `awardFreeThrows(...)` then
  **`return`** — the possession ends and **no FGA is charged** (the branch returns
  before `recordFieldGoalAttempt()`). That last point is why FGA is 88.4 while ~103
  shot attempts are made.
- **`defender.recordFoul()` is called BEFORE any branching** (line ~187). So foul-outs,
  `foulTroubleLevel()` and the bonus tally keep working **whichever branch is taken** —
  this is what makes a re-partition hold the total *by construction* rather than by
  tuning.
- **⚠ THE PRECEDENT ALREADY EXISTS IN THIS EXACT BLOCK: `isFlagrant(RandomGenerator)`**
  (`FoulResolver:171`) is a **second roll layered on a foul that has already happened
  and been charged**, re-partitioning severity without touching the parent rate (#034
  A). §3.16's roll would be its sibling. **Read that pair before designing a new shape**
  — `isFlagrantTwo` (`FoulResolver:193`) shows the flat-share form too.
- **Five `PlayType.FOUL` emission sites**: `212` SHOOTING_FOUL · `456`
  REBOUNDING_FOUL_* · `539` flagrant grades · `581` AND_ONE · `622` TECHNICAL_FOUL.
- **Foul mix today** (seed 1000): SHOOTING 14.67 (74.8%) · AND_ONE 1.96 · REBOUND_DEF
  1.84 · REBOUND_OFF 0.58 · TECHNICAL 0.39 · FLAGRANT 0.16 = **19.61 total**.
- **FTA by source**: SHOOTING **29.84** (87.8%) · AND_ONE 1.96 · BONUS 1.51 ·
  TECHNICAL 0.39 · FLAGRANT 0.33 = **34.0**.
- **`ShotType.freeThrowsIfFouled()`** is `THREE ? 3 : 2` — a **rule on the enum**, not a
  `SimConfig` knob (#030 C). Stopped shots split 14.16 two-pt / 0.51 three-pt.
- **The bonus** is derived, never stored: `GameData.isInBonus(teamId, period, config)`
  counts FOUL events by `committingTeamId`, **excluding** `TECHNICAL_FOUL` (#032 E).
  Currently **51.9%** of team-periods reach it.
- **Baseline landing to beat** (5-seed mean, seeds 1000–5000, profile `local,baseline`):
  points 118.3 · FG% 46.9 · 3P% 36.7 · ast 27.1 · TO 13.6 · **FTA 34.0** · **fouls
  19.35** · **FGA 88.4** · foul-outs 0.358 · penalty 51.9%.
- Harness: `-Dcalibration=true -DcalibrationSeed=NNNN`, 6 rounds × 17 matchups ≈ 102
  games; every printed figure is already a per-team-per-game mean. Era/scratch profiles
  are ordinary Spring profiles — `-Dspring.profiles.active=local,baseline,<name>` with
  **`baseline` kept in the list**.

1. **Where does the new branch go?** The leading candidate is a **re-partition inside
   `FoulResolver.isFoul`'s hit** — between `defender.recordFoul()` and the
   `SHOOTING_FOUL` emission — so the total is held **by construction** — the shape #027 A
   used for turnover causes, and the shape **`isFlagrant` already uses three lines
   above** (#034 A). Alternatives: a separate roll earlier in the possession
   (models off-ball contact more honestly, but adds fouls rather than re-partitioning
   them, so the total moves); or extending the rebounding-foul path. ⚠ **Whichever is
   chosen, say what happens to the SHOT** — see Q3.
2. **What share converts, and is it a constant or a contest?** ⚠ **The ~35% figure is
   ARITHMETIC from one 3-seed experiment, not a measurement** — it lands FTA at 23.5 and
   FTA/foul at 1.20 vs real 1.18, but the design pass must measure it. Is it a flat
   `SimConfig` share (the #034 E flagrant-share shape), or does it take skill input?
   **The real NBA shooting-foul share is still UNSOURCED** and is the number that sizes
   this phase — an earlier ~59% estimate was a bad back-derivation and is withdrawn.
3. **Does a non-shooting foul stop the shot?** ⚠ **This is the crux and it decides
   whether the pass breaks FGA.** If the shot still happens, the possession continues
   and gains an FGA — FGA is currently 88.4 vs 89.1 real, so there is ~0.7 of headroom
   and no more. If play stops with no free throws, points fall hard and the possession
   ends. Neither is obviously right; **both need measuring, not reasoning.**
4. **How does it interact with the bonus?** In the penalty a common foul **does** award
   2 FTs, which partly undoes the effect late in periods. `sim.bonus-fouls-per-period`
   is 5 and the derivation is `GameData.isInBonus`. Does the new outcome count toward
   the penalty tally? (#032 E made that an explicit per-outcome decision — a technical
   does not count, a flagrant does.)
5. **What is the outcome string, and does it feed the foul-out limit?** Naming follows
   the established vocabulary (#027 D — no collisions across phases). It is a personal
   foul, so it presumably feeds `FOUL_OUT_LIMIT` and `foulTroubleLevel()` — but say so,
   because §3.14a's technical deliberately does **not** (#032 E), and foul-outs
   (0.358) would move if this lands.
6. **Does the flagrant roll apply to a non-shooting foul?** ⚠ **A question the code
   forces and the phase cannot dodge**, because `isFlagrant` sits *between*
   `recordFoul()` and the `SHOOTING_FOUL` emission — whatever branch is added lands
   beside it. Real basketball has away-from-play flagrants, so "yes" is defensible; but
   **`awardFlagrant` assumes the FOULED PLAYER shoots the free throws** (#034 D
   explicitly refused to reuse `pickTechnicalFreeThrowShooter` because "on a flagrant
   somebody was fouled"), and on an off-ball foul there may be no shooter to name.
   Cheapest defensible answer is to keep the flagrant roll on the shooting branch only —
   but **say it explicitly**, because silence here is a latent bug.
7. **⚠ CHARGES ARE PERSONAL FOULS BY RULE, AND THE ENGINE DOES NOT MODEL THAT — this
   is a CORRECTNESS BUG, not a tuning question. Routed and justified in `decisions.md`
   **#037**; it needs its own Decision letter in this phase's entry (#039).**
   `TurnoverCause.OFFENSIVE_FOUL` (~**1.26**/team/game, javadoc *"Charge / illegal
   screen"*) is emitted at `PossessionEngine:178–181` as `shooter.recordTurnover()` +
   a **`PlayType.TURNOVER`** event, and **`recordFoul()` is never called**. So a charge
   today counts toward *nothing*: not the player's six, not the team-foul/bonus tally,
   not the box-score `fouls` column, not foul-trouble benching. A player can commit
   unlimited charges and never foul out.
   ✅ **Checked (#037): an OVERSIGHT, not a deliberate simplification.** #027 designed
   the nine-cause taxonomy as a pure re-partition of an already-decided turnover and
   **never mentions `recordFoul`, personal fouls or `FOUL_OUT_LIMIT` at all** — the foul
   consequence simply never came up.
   **⚠ IT MOVES THIS PHASE'S OWN BASELINE, which is why it cannot be deferred:** fouls
   go **19.61 → 20.87** (vs 19.9 real), i.e. from slightly under to slightly over. §3.16
   is tuning *against* the foul total, so it must design against the corrected number or
   it will land on the wrong one and be re-broken by the fix.
   **What the design pass must settle:**
   - **Two events for one occurrence** — a charge would emit **both** `TURNOVER` and
     `FOUL`. Every reconciliation that counts events must tolerate that, and the
     harness's `Fouls / team / game` line steps up ~1.26 **for this reason alone** —
     do not misread it as the re-partition misfiring.
   - **The committer is on OFFENSE**, so the *defensive* team goes into the bonus.
     `GameData.isInBonus` reads `committingTeamId` so it should handle this, but it has
     never been exercised with an offensive committer outside the rebounding-foul path.
   - **Foul-outs will rise** from 0.358 — a calibrated (if unsourced) number.
   - **Does it also become flagrant-eligible?** Almost certainly not; say so.

---

### What holds regardless of how the questions resolve

**⚠ §3.16 HAS NO REPRODUCTION GATE — it is SUPPOSED to move FTA.** What replaces it is
a set of tripwires: numbers that are already correct and must stay correct. Read them
off the harness in the same run.

| Must move | from | toward |
|---|---|---|
| **FTA / team / game** | 34.0 | **23.5** |
| FTA per foul | 1.73 | ~1.18 |

| Must NOT move (tripwires) | current | real | tolerance |
|---|---|---|---|
| **Fouls / team / game** | 19.35 → **20.87 once Q7 lands** | 19.9 | ⚠ **Q7 raises it by ~1.26 BEFORE the re-partition runs** — design against the corrected number. The re-partition itself should hold whatever the total is *exactly*. |
| **FGA / team / game** | 88.4 | 89.1 | **~0.7 of headroom — Q3 can blow this** |
| **FG%** | 46.9% | 47.1% | already correct; ±0.14 is seed noise |
| Points | 118.3 | 115.6 | may improve; must not overshoot downward |
| Foul-outs | 0.358 | *unsourced* | Q5 moves this if the new foul counts |
| Penalty rate | 51.9% | *unsourced* | Q4 moves this if it counts toward the bonus |

- **The harness already breaks fouls down by outcome** (`foulsByOutcome`), so a new
  outcome string shows up in the report **automatically** — no instrument work needed
  to see the re-partition land.
- **A scratch profile is the cheap way to test a share before committing to it** —
  `application-scratch.properties` with the one key, run
  `-Dspring.profiles.active=local,baseline,scratch`, then **delete it**. §3.15 made this
  free; the `base-no-basket-foul` finding above came from exactly that.
- **`possession-flow.puml` MUST be updated in the same session** — this pass adds a
  branch (#036 G), and it is the first §3.x since §3.14b to do so.
- ⚠ **Judge the coarse rows at 5 SEEDS ONLY** — foul-outs, technicals, flagrants.
- **`calibration.md` and the harness `(target ~N)` strings move together**, always.

---

### ⚠ Do NOT (standing guardrails — carried forward into §3.16–§3.19)

These outlive any one phase. **§3.15 shipped as a refactor and changed no number at
all** (its gate reproduced §3.14b per-seed). ⚠ **Re-centering is now §3.19, not §3.16**
(#036 F) — §3.16 and §3.17 are mechanic passes that deliberately move composition, and
§3.19 re-solves the numbers afterwards.

- **Do NOT add a fourth removal path in `RotationState`** — ejection is the **hard**
  tier and extends `isDisqualified(...)` (#031 H, built §3.14a).
- **Do NOT reuse §3.14a's counter split** — a flagrant **does** count toward the
  6-foul limit and the penalty tally (#032 E). Use `recordFoul()`.
- **Do NOT re-tune §3.13's foul-trouble sit curve** — measured saturated (#031 note).
- **Do NOT trim `BASE_NO_BASKET_FOUL`** — a measured **wrong-way** lever (#028):
  trimming it *raises* points.
- **Do NOT re-open §3.12's `FOUL_MULT_*`** (settled on realism, #030 G) or touch
  `pickDefender`'s `individualDefense` weighting (#031 A).
- **Do NOT re-center points/FG%** — §3.16 owns it and the targets are CONTESTED
  ([calibration.md](calibration.md)).
- **Do NOT change the existing foul roll** — a flagrant is an *additional* roll on
  a foul that already happened (user call, #034 A). `isFoul` / `isAndOne` /
  `resolveReboundFoul` keep their rates, inputs and RNG draws; a test pins that
  `isFoul`'s RNG consumption is unchanged.
- **Do NOT vary `PERSONAL_FOULS_PER_TEAM_GAME` as if it were a tunable** (#034 G) —
  it is a **measured** assumption about the engine's current foul rate, and it is the
  divisor behind the flagrant probability. §3.16 (or any pass moving the foul rate)
  must revisit it deliberately; a §3.15 profile must not vary it independently.
- **Do NOT back-solve §3.14a's technicals constant** against the ~5% harness gap —
  that is #032 B2's documented nominal-vs-actual pace effect, not drift.
- **Do NOT add a stored `flagrant2` / `ejected` flag** (#034 F) — `flagrantTwos >= 1`
  is a monotonic counter, so the predicate stays **derived**. The stored-state
  exception predicted by #031 H / #032 F does not arise and is **retired**.
- **Do NOT exempt the flagrant retention from `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`,
  and do NOT raise the BASELINE cap** (#034 B). 3→5 is a parked idea needing its own
  recalibration pass ([ideas.md](ideas.md)). ⚠ **§3.15 makes this constant PROFILABLE**
  (#035 C) — the fence protects the **baseline value**, which a profile cannot touch, so
  an era profile setting it is fine and does **not** unpark the tuning idea.
- **Do NOT add 2 FTs on top of the underlying foul's award** (#034 C) — the flagrant
  **replaces** it. A flagrant stopped THREE is **2** FTs, not 3 and not 5. This is
  the likeliest bug in the pass.
- **Do NOT add a `flagrant_fouls` box-score column** (#034 H) — a flagrant is already
  inside `fouls`, so #033's parity argument does **not** reach it.
- **Do NOT reuse `pickTechnicalFreeThrowShooter`** (#034 D) — its premise is that
  nobody was fouled (#032 G); on a flagrant somebody was.

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
  `GameEvent`) · §3.12 **#030** (~20 3PA/team/game vs the NBA's ~35, a §3.16 input)

**Other files own these outright:**

- **§3.16 (recalibration)**, and the phase sequence generally → [roadmap.md](roadmap.md)
- **Calibration targets** → [calibration.md](calibration.md), **the source of truth**.
  Update it *and* the `CalibrationHarness` `(target ~N)` strings together.
- **Infra/tooling/data-hygiene chores**, and **the `decisions.md` condense pass** (a
  **gate on starting Phase 4**, run after §3.16) → [backlog.md](backlog.md)
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
