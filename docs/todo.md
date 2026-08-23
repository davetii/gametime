# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section.

Current focus: **§3.21 — the rebound pool**, which **needs a DESIGN PASS first**.
⚠ **RECALIBRATION WAS RENUMBERED FOUR TIMES** — §3.16 → §3.18 → §3.19 → **§3.20**.
**Read the phase NAME, never the number alone.** §3.7–§3.20 have all shipped.

> ⚠ **§3.21 NEEDS A DESIGN PASS — resolve it into `decisions.md` #043 plus an
> execute-ready plan BEFORE writing production code.** The bullet below is a seam, not a
> plan: §3.20's execution showed three of #042's own decisions were wrong in ways only
> measurement caught, and this phase starts from a symptom whose cause is **only half
> identified**.
>
> ✅ **§3.20 (RECALIBRATION) SHIPPED — [decisions.md](decisions.md) #042.** 8 of 12 rows
> landed: Points **114.86** · FG% **46.86** · 2P% **54.67** · FGA **89.22** · 3PA
> **36.64** · FTA **23.40** · **FT% 77.81** · TO **14.58**. The 2P% gap closed 48.8 → 55.0.
> Residuals: **DefReb −4.50, OffReb −1.40**, Fouls −1.06, 3P% −0.34.

---

## §3.21 — the rebound pool (DESIGN PASS NEEDED)

**§3.21 IN FOUR GOALS** *(the user's framing, 2026-08 — the detail is in the questions below)*:
1. **Rebound missed free throws** — the last FT of a trip, `SHOOTING`/`BONUS`/`AND_ONE`
   only. Worth **~2.60**/team-game. *(Question 1 — DECIDED by rule; the HOW is open.)*
2. **Credit a rebounder on a blocked shot recovered IN BOUNDS** — **3.41**/team-game.
   *(Question 2 — DECIDED by rule; WHO gets selected is the design work.)*
3. **Account for the REMAINING leak before trusting the arithmetic** — **1.97** on the FG
   path is unexplained. ⚠ **Goals 1+2 are sized against a decomposition with a hole in
   it**, and #042 B, F and H were each wrong in exactly that way. *(Question 3 + 5.)*
4. **Re-land calibration — §3.21 OWNS this, it does not just "understand" it.** More
   offensive rebounds means more second-chance possessions: FGA moves ~+1.3 against a row
   **landed at 89.22 (±0.45)**, with points and fouls following. *(Question 7 — and it is
   why there is no "§3.22".)*

⚠ **The tempting result to be suspicious of:** goals 1+2 model to a pool of 43.80 against
a target of 43.70 — DefReb 32.46, OffReb 11.35, both within 0.06. **That neatness partly
DEPENDS on the 1.97 staying unexplained.** Close goal 3 first.

---

**How to measure** (goal 3 requires it; this is the whole instrument). ⚠ **Java 21 or
Lombok breaks, and the profile MUST come from the ENVIRONMENT** — `-D` does not reach the
forked Surefire JVM and the failure looks like a database error:

```bash
cd gametime-service && for s in 1000 2000 3000 4000 5000; do SPRING_PROFILES_ACTIVE=local,baseline JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -q -pl gametime-app test -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=$s -DfailIfNoTests=false; done
```

⚠ **Confirm `Profiles: local,baseline` and all THREE reconciliation lines (`ast+blk`,
`ft-src`, `points`) read `OK` on every run before reading a number.** ⚠ **Judge at the
5-seed MEAN** — per-seed noise is ±1.5 points. ⚠ **The harness does NOT break rebounds out
by source**, so answering goal 3 will need either a throwaway probe or a new harness line —
decide which in the design pass (§3.20 used a throwaway `@SpringBootTest` probe deleted
before close-out).

**The code to read before designing** — all in `gametime-app/src/main/java/.../sim/`:
`PossessionEngine` (the foul block, the block branch ~line 400, `awardFreeThrows` ~line
929, `emitMissedShotEvent` ~line 960) · `BlockResolver.resolveRecovery` (the flat four-way
draw) · `MissedShotResolver` (the OOB-first carve) · `ReboundResolver` (the skill-weighted
board contest) · `RotationState`.

---

**The symptom.** Def rebounds **27.90 vs a sourced 32.4**; off rebounds **9.90 vs 11.3**.
⚠ **~14% low on a stat a reader sees directly on a box score** — the one §3.20 residual a
person would notice unaided.

⚠ **THE SPLIT IS NOT THE PROBLEM — DO NOT REACH FOR `base-offensive-rebound`.** The
realized offensive share is **0.264** against a real **0.259**. The contest is correct;
the **POOL** is short: 37.80 against the **43.70** the two sourced targets jointly need.
§3.20 measured this and deliberately left the knob at 0.27 (#042 D3). Moving it to land
DefReb would put OffReb at ~5.7 against a target of 11.3.

**What §3.20's execution already measured** (start here; do not re-derive):

| | per team-game |
|---|---|
| missed FG | 47.41 |
| − OOB off a miss (`oob-total-weight` 0.07) | 3.10 |
| − blocked shots (recovery is a loose-ball draw, **credits no rebounder**) | 4.54 |
| = should reach the pool | **39.77** |
| actual pool (27.90 + 9.90) | **37.80** |
| unexplained on the FG path | **−1.97** |

🆕 **ONE CAUSE IS IDENTIFIED AND VERIFIED: MISSED FREE THROWS ARE NEVER REBOUNDED.**
`PossessionEngine.awardFreeThrows` loops the free throws, emits each event, and returns —
**there is no rebound path at all**, so a missed final FT silently ends the possession.
Real basketball yields **~3.2 rebounds/team-game** off missed FTs (~2.6 def + ~0.6 off);
the engine yields **zero**. At the current FTA/FT% that is **~2.6 reboundable misses** —
**under half** of the 5.90 pool shortfall.
⚠ **VERIFIED TWO WAYS, so start from it rather than re-deriving it:** (1) all six
`awardFreeThrows` call sites `return` or end the possession — none tests for a missed
final FT, none reaches `MissedShotResolver`; (2) the measured pool (37.80) sits **below**
the FG-only expectation (39.77), where free-throw rebounds would put it ~2.6 **above**.

⚠ **SO THERE ARE AT LEAST TWO CAUSES AND ONLY ONE IS IDENTIFIED.** Free throws explain
**~2.6 of the 5.90** shortfall. The rest sits in the FG path: the **−1.97 unexplained**
plus the **4.54 blocked shots that credit no rebounder**. **Neither is answered, and the
design pass must account for the remainder BEFORE proposing a fix** — #042 B, F and H were
each wrong because a plausible mechanism was modelled instead of measured, and this phase
starts one level deeper in the same territory.

**⚠ THE TWO CANDIDATES BRACKET THE TARGET — measured, and it makes blocks LOAD-BEARING.**

| | pool | OffReb *(11.3)* | DefReb *(32.4)* |
|---|---|---|---|
| now | 37.80 | 9.90 | 27.90 |
| + missed-FT rebounds | 40.40 | 10.46 | 29.94 |
| **+ FT and blocked-shot recoveries** | **43.80** | **11.35** | **32.46** |
| target | 43.70 | 11.3 | 32.4 |

**Free throws alone leave it 3.3 short. FT *plus* crediting recovered blocks lands both
rows within 0.06** — ⚠ **treat that as a HYPOTHESIS TO TEST, not a plan.** It is arithmetic
over measured rates, which is precisely the form #042 B/F/H were each wrong in. But it does
settle one thing: **blocked-shot recovery is not a footnote here, it is load-bearing**, and
question 2 below is therefore central rather than optional.

⚠ **AND IT WILL UN-LAND FGA.** Offensive rebounds **are** second-chance possessions:
FT+blocks adds ~1.45 off rebounds → **~+1.3 FGA**, against a row landed at **89.22** with a
**±0.45** band. Points and the foul rate follow FGA. **Budget the re-tune into this phase**
(question 7) — that is why there is no "§3.22".

**Open questions for the design pass:**
1. ✅ **A missed last free throw MUST become a live rebound — this is DECIDED, not open**
   (user call, 2026-08): it is a rule of basketball, not a modelling choice. **What the
   design pass still owes is the HOW**, and none of it is obvious:
   - ⚠ **Only the LAST FT of a trip is live.** A missed first FT is a dead ball. The
     award loop does not currently distinguish them — it emits `count` identical
     iterations, so the branch needs the loop to know which shot is final.
   - ⚠ **An OFFENSIVE rebound off a missed FT RETURNS THE BALL**, so it must respect
     `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION` and re-enter the loop — the same
     `continue` shape the offensive rebound already uses (and the same shape §3.14b's
     flagrant used, #034 B).
   - ⚠ **The FT rebound leans MORE defensive than a field-goal miss** — the defense has
     inside position by rule. **Reusing the standard contest unmodified is probably
     wrong**, and this is a place a new tunable could sneak in (§3.20 held at 62; say
     explicitly whether this phase adds one).
   - ⚠ **New RNG draw in a new place** → seeded sim tests re-baseline, and
     `possession-flow.puml` gains the fork **in the same change**.
   - ⚠ **THE RULE IS NOT UNIFORM ACROSS THE FIVE `FreeThrowSource` VALUES** — three
     rebound, two do not. Checked per source:

     | source | FTA | live rebound on a missed last FT? |
     |---|---|---|
     | `SHOOTING` | 17.51 | **YES** — 2 FTs (3 on a fouled three); the last is live |
     | `BONUS` | 3.23 | **YES** — 2 bonus FTs; the last is live |
     | `AND_ONE` | 1.70 | **YES** — exactly 1, so that one IS the last |
     | `FLAGRANT` | 0.29 | **NO** — the offense RETAINS the ball by rule (already modelled, #034 B) |
     | `TECHNICAL` | 0.34 | **NO** — play resumes with the ball as it was; not contested |

     ⚠ **Excluding the two changes the sizing by almost nothing** (they are 0.63 FTA
     between them), so **~2.60 reboundable last-FT misses stands** — but building it
     uniformly would be wrong as basketball and would double-count the flagrant's
     existing retention path.
2. ✅ **A RECOVERED BLOCKED SHOT MUST CREDIT A REBOUND — DECIDED by the NBA rule** (user
   call, 2026-08), not a modelling preference. ⚠ **This is the phase's CENTRAL item** —
   it is the LARGER half of the shortfall, and the bracket above shows free throws alone
   cannot close the gap. Measured today, per team-game:

   | `BlockRecovery` | share | per team-game | rebound owed? |
   |---|---|---|---|
   | `RECOVERED_DEFENSE` | 45% | **2.04** | **YES — defensive** |
   | `RECOVERED_OFFENSE` | 30% | **1.36** | **YES — offensive** |
   | `OOB_DEFENSE` | 13% | 0.59 | no — out of bounds |
   | `OOB_OFFENSE` | 12% | 0.54 | no — out of bounds |

   **3.41/team-game are recovered in bounds and credited to NOBODY.** The OOB pair is
   already correct (no rebound is right there).
   ⚠ **THE POSSESSION OUTCOME IS ALREADY RIGHT — only the ATTRIBUTION is missing.** The
   engine knows which side got the ball and acts on it (offense-recovered re-enters the
   second-chance loop under the cap; defense-recovered ends the possession). What is
   absent is a `REBOUND` event and a `recordRebound()`. **That is a much smaller change
   than inventing a contest.**
   ⚠ **The real question is WHO** — crediting a rebounder means *selecting* one, and today
   no selection happens. #025 D chose the flat, skill-independent draw **deliberately**, so
   the recovery would not inherit the board-contest winner. **Keep that flatness and pick
   the rebounder some other way, or route recovery through `ReboundResolver`?** ⚠ A new
   skill-weighted pick is a **new RNG draw** (re-baselines seeded tests); reusing the
   existing four-way result and only adding the credit may not be.
   ⚠ **It need not move the block COUNT** (4.54 vs a sourced 4.8 — landed): this changes
   what else the play emits, not how often a block happens.
3. **Where do the remaining ~1.97 go?** Unexplained. **Measure before theorising.**
   The retention cap redistributes rather than leaks (it converts an offensive rebound to
   a defensive one), so it is not the answer.
4. **Is `oob-total-weight` (0.07) too high?** 3.10/team-game leave the court off a miss.
   Real basketball's equivalent is smaller. ⚠ It is a §3.8 constant (#026) with its own
   reasoning — check before moving.
5. **Of the 14.8 misses/team-game that never become a credited rebound, how many SHOULD
   have?** That number is the size of the fix, and getting it right is this pass's core
   deliverable — it is what says whether questions 1 and 2 genuinely close the gap or only
   appear to. Real basketball loses ~5.5 this way; the engine loses 14.8.

   **The test that sorts them: DID A PLAYER COME DOWN WITH THE BALL?** If yes it is a
   rebound and someone is credited — always, no exceptions. If the ball left play with
   nobody ever possessing it, possession is **awarded by rule rather than won**, no rebound
   happened, and no one is named. ⚠ **"Loose ball" is NOT the test** — a loose ball a
   player secures is an ordinary rebound with an owner.

   | | per team-game | verdict |
   |---|---|---|
   | OOB off a missed FG | 3.10 | ✅ **correctly un-owned** — nobody touched it |
   | OOB off a blocked shot | 1.14 | ✅ **correctly un-owned** — same |
   | **blocked shots recovered IN BOUNDS** | **3.41** | ❌ **a player secured it and got no credit** → question 2 |
   | **missed last free throws** | **~2.60** | ❌ **no rebound branch exists at all** → question 1 |
   | unexplained, FG path | 1.97 | ❓ → question 3 |

   ⚠ **THE TRAP TO AVOID: do not let "team rebound" absorb the two ❌ rows.** A *team
   rebound* is the scorekeeping entry for the ✅ rows — a possession change where **no
   rebound happened**, recorded only so `missed shots = rebounds + team rebounds` closes.
   It is **not** a rebound, and it is **not** a bucket for rebounds whose owner the engine
   failed to identify. **In the ❌ rows a rebound plainly occurred; the fix is the missing
   credit.** The engine already models the ✅ rows correctly as `REBOUND /
   OUT_OF_BOUNDS_OFFENSE|DEFENSE` — possession assigned, no rebounder, excluded from the
   rebound reconciliation.

6. ⛔ **`teamRebounds` as a box-score column — NOT NOW (user call, 2026-08). Do not build
   it in §3.21.** **There is no consumer** (#014/#017/#020), and #033 surfaced
   `technicalFouls` only once a parity argument existed — the same bar applies.
   ✅ **It stays DERIVABLE from the event log, so nothing is lost by waiting**: the
   ownerless cases are emitted as `REBOUND` with an `OUT_OF_BOUNDS_*` outcome and a null
   `primary_player_id` (`PossessionEngine`'s missed-shot emit), so the query is
   `count(REBOUND where outcome LIKE 'OUT_OF_BOUNDS%')` grouped by team.
   ⚠ **BUT THAT DERIVATION IS INCOMPLETE TODAY, AND §3.21 SHOULD CLOSE IT.** A blocked
   shot knocked out of bounds (`OOB_DEFENSE` / `OOB_OFFENSE`, **1.14/team-game**) is
   resolved **inside `BlockRecovery` and emits NO EVENT AT ALL** — same root gap as the
   missing block rebound. **Whatever fixes the credit for recovered blocks (question 2)
   should emit the OOB cases too**, after which the derivation is complete and the column
   remains unnecessary.
   ⚠ **If it is ever built it counts the OOB/buzzer cases ONLY** and must never become a
   bucket for rebounds whose owner the engine failed to identify — if a player got the
   ball, fix the credit (questions 1 and 2). **§3.21 does not need this to fix the pool.**
7. **What re-lands after the pool changes, and does §3.21 own that re-tune?** ⚠ Adding
   rebounds adds **second-chance possessions**, which adds FGA — and **FGA is now LANDED
   at 89.22**. More second chances also move points, FG% and the foul rate.
   ⚠ **§3.21 OWNS ITS OWN RE-LANDING — do NOT schedule a "§3.22 recalibration".**
   Recalibration was renumbered **four times** (§3.16 → §3.18 → §3.19 → §3.20) precisely
   because it kept being deferred behind one more fidelity phase, and there is always one
   more. A phase that moves the shape re-lands the numbers it moved, or **records the
   residual with its reason** — that is the Definition of done, not a following phase.
   ⚠ **The ONE thing that would justify a split** is a design-pass FINDING that the
   re-tune is a bigger job than the mechanic (§3.20 took two sessions of tuning). That is
   how §3.14 became §3.14a/§3.14b — **a finding, not a plan.** Decide it in the design
   pass, with a measurement, not now.

**⚠ Do NOT**
- **Do NOT tune `base-offensive-rebound`** to chase DefReb. The split is right; #042 D3
  measured it and left it deliberately.
- **Do NOT read #042 H** as current: it measured the share at 0.378 "running hot" *before*
  the 2P% fix. Both its sign and magnitude are superseded by D3.
- **Do NOT add a branch without updating `possession-flow.puml` in the same change**, and
  expect seeded sim tests to re-baseline.
- **Do NOT assume the FT fix alone closes it** — it is ~half the gap.
- **Do NOT defer the re-landing to a new phase.** See question 6: §3.21 lands what it
  moves. Recalibration has already been renumbered four times for exactly that habit.

---
---

## ⚠ Traps that bite EVERY session — read these before trusting a doc

**1. SUB-PHASE NUMBERS HAVE BEEN REUSED — FOUR TIMES FOR ONE PASS.** Recalibration was
§3.16, then §3.18, then §3.19, and is now **§3.20**. ⚠ **So BOTH numbers are ambiguous:**
every **"§3.16"** written before 2026-08 means RECALIBRATION (the §3.16 slot became
shooting-foul composition, shipped), and every **"§3.19"** written before this session
also means RECALIBRATION (the §3.19 slot is now **instrumentation**).
⚠ **The docs have NOT been swept**: ~60 stale "§3.19 = recalibration" references remain
in `decisions.md`, `roadmap.md`, `backlog.md` and `ideas.md`, left deliberately rather
than mass-edited, because retro-editing history is worse than a callout. **Read the phase
NAME, never the number alone.** roadmap.md carries the mapping.

**2. A NUMBER CAN MOVE — OR FAIL TO MOVE — FOR REASONS THAT ARE NOT THE ENGINE.**
⚠ **§3.20 hit this THREE MORE TIMES, and none of them was the engine** — a modelled
coupling that does not exist in the code (FTA/FGA), a wedge modelled with the wrong
*shape* (flat vs multiplicative), and `PROB_FLOOR` silently eating ~40% of a lever.
Before tuning anything, ask: **did the engine change, did the MEASUREMENT
change, or is a clamp holding it?** All three have happened:
- **The instrument broke** — a harness row classified a stopped shot by its free-throw
  count, and a phase that changed what a foul awards silently halved the row.
- **⚠ FIXING an instrument also moves numbers, and looks like a regression** — `Stopped
  shots / team` read 12.43 post-fix against 7.65 pre and appeared to double. **It fell**
  (7.39 → 6.24 like-for-like). **Compare raw-to-raw across an instrument change.**
- **⚠ A CLAMP can do what a constant appears to do, and the tell is a number that DOESN'T
  move** — blocks were predicted to fall to ~2.6 and stayed at 4.80. See Q1.

**3. THE HARNESS NEEDS ITS PROFILE IN THE ENVIRONMENT.** `-Dspring.profiles.active` does
**not** reach the forked Surefire JVM; the context comes up with `activeProfiles = []`
and fails with what looks like a database error. Use
`SPRING_PROFILES_ACTIVE=local,baseline`, run from `gametime-service/`, and **confirm the
report's `Profiles:` line before trusting any number.** This has cost two sessions.

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
  `GameEvent` — ✅ **CLOSED by §3.18**) · §3.12 **#030** (~20 3PA/team/game
  — ✅ **CLOSED by §3.17**, landed 37.22) · §3.16 **#039** (the unsourced shooting-foul
  share — ⚠ **still unsourced**, and §3.20 moved it to 0.3766) · **§3.17 #040**
  (FG%/2P%, FGA, FTA — ✅ **all CLOSED by §3.20**; the
  `PROB_FLOOR`-vs-`base-block-three` finding is ⚠ **RE-OPENED at a larger size** — §3.20
  measured the same clamp making `base-turnover` a 0.17-elasticity lever, see #042 D4)
- **§3.20's residuals** → **#042**'s implementation note. The rebound pool is **§3.21,
  above**; the rest (Fouls, `PROB_FLOOR`, 3P%'s band) are **sized and deliberately not
  scheduled** → [roadmap.md](roadmap.md)

**Other files own these outright:**

- **The phase sequence and this phase's bullet** → [roadmap.md](roadmap.md). ⚠ Its
  **§3.20** bullet is `[x]` with the landing; the **pre-design argument under it is
  SUPERSEDED** (#042 A disproved the over-determination, and execution then disproved
  #042 B's FTA/FGA coupling in turn). It is **kept and ANNOTATED** rather than rewritten,
  because it is history. **#042 and its implementation note are authoritative.**
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
  `SPRING_PROFILES_ACTIVE=local,baseline` (#035 F — ⚠ **the `-D` form does NOT reach the
  forked surefire JVM**). ⚠ **`baseline` must always
  be in the list** (it carries all **62** values), and **order is positional** — putting it
  last lets it win and silently neutralizes the era file. The **effective-config dump**
  in the report is what catches that, and it also catches a caller passing its own copy
  of a profilable value (the §3.15 harness-pace trap).
- **Do NOT touch `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`'s baseline value** without its
  own recalibration pass (#034 B) — though §3.15 makes it *profilable* (#035 C).
