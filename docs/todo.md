# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section.

Current focus: **§3.22 — the putback**, ⚠ **NEEDS A DESIGN PASS**.
⚠ **RECALIBRATION WAS RENUMBERED FOUR TIMES** — §3.16 → §3.18 → §3.19 → **§3.20**.
**Read the phase NAME, never the number alone.** §3.7–§3.21 have all shipped.

> ⚠ **§3.22 NEEDS ITS DESIGN PASS FIRST — resolve the questions below into a numbered
> `decisions.md #044` (Decisions A, B, C…) PLUS an execute-ready plan here. WRITE NO
> PRODUCTION CODE IN THAT SESSION.**
>
> ✅ **§3.21 (THE REBOUND POOL) SHIPPED — [decisions.md](decisions.md) #043 A–H.** Rebound
> pool **37.80 → 43.72** against a real 43.70. FGA **89.16** ✅ · Points **115.04** ✅ ·
> FTA **23.76** ✅ · Fouls **19.40**. Off/def rebounds land **+0.48 / −0.46** as
> **reported residuals** — three slices, three offensive shares, one knob.

---

## §3.22 design pass — the putback

**Build preamble** — ⚠ **Java 21 or Lombok breaks:**
`JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`. A design pass writes **no
production code**, but it **does measure**, so it needs the harness:

```bash
cd gametime-service && for s in 1000 2000 3000 4000 5000; do SPRING_PROFILES_ACTIVE=local,baseline JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -q -pl gametime-app test -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=$s -DfailIfNoTests=false; done
```

⚠ **The profile MUST come from the ENVIRONMENT** — `-D` does not reach the forked
Surefire JVM and the failure looks like a database error. **This has cost two sessions.**
⚠ **Confirm `Profiles: local,baseline` and all FOUR reconciliation lines read `OK` before
reading any number**, and **judge at the 5-seed MEAN** — per-seed noise is ±1.5 points.

**⚠ THE STARTING POINT, measured at the §3.21 landing** (5-seed mean, seeds 1000–5000,
profiles confirmed — reproduce this before trusting anything else):

| row | now | target | |
|---|---|---|---|
| Off rebounds | **11.78** | 11.3 | reported residual (+0.48) |
| Def rebounds | **31.94** | 32.4 | reported residual (−0.46) |
| **Assists** | **27.10** | 26.7 | **+0.40 — the row Q5 moves; sem 0.217, so this is NOISE-SCALE** |
| FGA | 89.16 | 89.1 | ✅ |
| Points | 115.04 | 115.6 | ✅ |
| FG% | 46.78 | 47.1 | Q6 expects a slight FALL |
| Fouls | 19.40 | 19.9 | residual |

**Throwaway probes are fine and are the house pattern** (#043's `ReboundPoolProbe`,
§3.22's own sizing) — ⚠ **but DELETE them at close-out**, and do not leave a permanent
harness row answering a one-time question (#043 F).

**The gap, in one line:** an offensive rebound `continue`s the possession loop, which
re-enters at `pickShooter(offense, rng)` — **a draw over all five that does not know who
just got the board** — so the rebounder is no likelier to shoot than anyone else, and a
putback cannot happen.

**⚠ THE APPROACH IS ALREADY DECIDED (user call, 2026-08). Do not re-open it:**
**a WEIGHT on the rebounder's `offensiveWeight` for the next shot only — NOT a boolean
"does a putback happen", and NOT a forced rim shot.** The reasoning, and why a builder
will be tempted to "improve" it, is in roadmap.md's §3.22 bullet — read it before
starting. ⚠ **ONE RULE, ALL OFFENSIVE-REBOUND PATHS** (user call): do not split the
constant per site without a measured reason.

**⚠ ALL SEVEN QUESTIONS ARE ANSWERED (user, 2026-08). The design pass RECORDS them as
Decisions A–G in `#044` and resolves only the MECHANICAL items listed after them.**
⚠ **This is unusual and deliberate — do NOT treat the answers as suggestions to
re-derive.** Each was a user call; the pass's job is to write them up with their
reasoning, decide the small implementation questions left, and produce the execute-ready
plan. **If measurement contradicts one of them, STOP AND ASK** — that is what happened in
#043 A, and it is the one thing that should re-open a decision.

1. ✅ **DOUBLE THE WEIGHT — `M = 2.0` on the rebounder's `offensiveWeight`** (user call,
   confirmed explicitly when the share-vs-weight ambiguity was raised). ⚠ **The
   CONSTANT is the multiplier; the share is what it REALIZES, and the two are not the
   same number.** At five equal weights M=2.0 yields **33.3% for the rebounder and 16.7%
   for each of the other four** — *not* the 40/15 an earlier sketch of this question
   floated, which would have needed M≈2.67. **40/15 is not the intent; 2.0 is.**
   ⚠ **33.3/16.7 is itself only the EQUAL-WEIGHT case** — real rosters skew it, and the
   user expects the realized split to differ "based on player and shot type", which is a
   property of the weighted draw rather than something to correct for.
   ⚠ **So: SET M = 2.0, then MEASURE and REPORT the realized share. Do not back-solve M
   from a target share** (#043 D's discipline, and the reason `FREE_THROW_REBOUND_LEAN`
   was set once from a real figure and its 0.1695 outcome simply reported).
   ⚠ **Note it lands BELOW real basketball's ~45–55% of immediate second-chance
   attempts** — that is a deliberate, conservative first step on a mechanic that did not
   exist at all, not an oversight. **Say so in the entry rather than quietly tuning
   upward.**
2. ✅ **TUNABLE** (user call), not a static. **62 → 63.** ⚠ The count lives in **FOUR**
   places plus `SimConfigProfileBindingTest` (CLAUDE.md) — and a tunable needs a line in
   `application-baseline.properties`, which is its ONLY copy: **no Java initializer, no
   `public static final` alias** (the double-value trap).
3. ✅ **A `putbackCandidate` local, declared outside the loop, READ-AND-CLEARED at the top
   in one step** (recommended and accepted). ⚠ **Clearing at the USE site, not at each
   `continue`, is the whole correctness** — it is one line that cannot be forgotten
   instead of four that can, and it makes a stale candidate structurally impossible.
   ⚠ **`null` is MEANINGFUL, not a sentinel to defend against**: it means "no identified
   rebounder" (OOB, flagrant retention, rebounding foul), and
   `pickShooter(offense, null, rng)` **must behave exactly as today**. Two plumbing
   changes fall out, both the record shape §3.21 already used twice:
   `emitBlockRecoveryEvent` returns a `BlockRecoveryResult(int, PlayerGameState)` instead
   of a bare `int`, and `FreeThrowResult` gains a `rebounder` component.
4. ✅ **NO DECAY across retentions** (user call) — "introduces too much complexity". The
   second putback in a possession is as likely as the first.
5. ✅ **REDUCE the assist chance on a putback — do NOT zero it (user call, 2026-08).**
   A putback is a player going straight back up with his own rebound: **nobody passed him
   the ball**, so the engine crediting a teammate ~65% of the time is wrong. But the
   correction has to be **partial**, and the reason is measured, not aesthetic:

   | | assists | miss vs 26.7 | statistically |
   |---|---|---|---|
   | today | 27.10 | **+0.40** | **1.8 sem — noise-scale** |
   | zero assists on putbacks | 25.7–25.9 | **−0.8 to −1.0** | **3.7–4.6 sem — REAL** |
   | ⭐ **reduced (~half)** | **~26.5** | **~−0.2** | **closer to target than today** |

   ⚠ **THE HEADROOM IS THE WHOLE ARGUMENT, AND IT CUTS BOTH WAYS.** Assists ARE already
   high (+0.40), so there is room to give back — that is what makes a partial cut land.
   But the room is only ~0.4, so a **full-zero rule overshoots it** and trades an
   **unmeasurable** overshoot for a **measurable** undershoot. **A residual you can
   EXPLAIN beats one you CREATED** (#043 B's shape). ⚠ Per-seed the row runs
   26.6/27.7/27.0/27.5/26.7, sem **0.217** — judge it at 5 seeds and against that spread,
   never off one run.
   ⚠ **REJECTED, and why, so the design pass does not re-open them:** *zero + re-land with
   `sim.base-assist`* — spends a knob deliberately left alone through two recalibrations,
   to correct an overshoot the new rule itself caused; *leave it untouched* — knowingly
   ships ~2 bogus assists/team-game once putbacks exist.

   **What the design pass still owes on this one:**
   - **The reduction's VALUE, MEASURED not assumed.** "About half" is the sizing
     hypothesis that made (b) win, **not the answer** — set it once, measure the realized
     assist row, report it (#043 D's discipline).
   - **Its name and form** — a multiplier on `assistProbability` at the putback site is
     the obvious shape, mirroring how `FREE_THROW_REBOUND_LEAN` scales
     `baseOffensiveRebound()` for one site only.
   - ⚠ **TUNABLE OR STATIC — and note the interaction with Q2.** If both this and the
     putback weight are tunables the count goes **62 → 64**, and the count lives in FOUR
     places plus `SimConfigProfileBindingTest`. **A rule-of-basketball argument exists for
     making THIS one a static** (nobody assists a putback, in any era) while the putback
     *tendency* stays tunable. **Decide explicitly; do not let the count drift.**
   - ⚠ **WHICH MAKES IS IT?** The rule keys off "this shot was taken by the player who
     just rebounded" — i.e. it needs the same `putbackCandidate` identity Q3 carries, at
     the assist site. **It is NOT "every second-chance shot"**: a kick-out three off an
     offensive rebound is a perfectly ordinary assisted basket, and taxing it would be the
     blunt rule this decision just rejected. ✅ **VERIFIED IMPLEMENTABLE**: at the assist
     site (~line 488) both `shooter` and the loop-scoped candidate are in scope, so
     `shooter == putbackCandidate` distinguishes the two cases with no extra plumbing —
     **but only if Q3's read-and-clear keeps the candidate readable that far down the
     iteration.** ⚠ **That is a REAL constraint on Q3's shape**: clearing it at the top
     means capturing it into a local (`rebounder`) that stays live for the whole
     iteration, which the recommended form already does. **Do not "simplify" Q3 by
     clearing it right after `pickShooter` — the assist rule needs it later.**

6. ✅ **Expected movement (user): little; a slight FG% dip because rebounders are worse
   shooters, plus whatever Q5 does to assists.** ⚠ **Note the mechanic only moves
   ~1.6 attempts/team-game** — M=2.0 shifts the rebounder from 20% to 33.3% of
   11.78 rebounds, so the *incremental* putback attempts are **1.57**, not 12. That small
   number is the reason "little movement" is a reasonable prior — and the reason the
   assist row (Q5) is the only thing at real risk. ⚠ **The FG% direction is worth stating
   because it is the OPPOSITE of the forced-rim sketch's**, which predicted FG% *up* on
   rim attempts. Under a weight the shooter is simply a worse one, so **FG% should fall
   slightly** — and in the harness (five identical skill-10 players) it should move
   **not at all**, which is the cheapest possible check that the mechanic is wired.
   ⚠ **MEASURE IT; do not assume it.** Three passes running have had a modelled quantity
   turn out to be something else (#042 D1, D3; #043 A).
7. ✅ **YES — §3.22 re-lands its own calibration** (user call), the #043 G precedent. If
   the measured move is inside every band, **say so explicitly** rather than leaving it
   unstated.

**⚠ STILL OPEN — all SEVEN questions are now ANSWERED, so what is left is MECHANICAL,
not design.** The pass writes these up as `#044` and resolves:
- **The assist reduction's VALUE** (Q5) — "about half" is the sizing hypothesis that won
  the argument, not the answer. Set it once, **measure the realized assist row, report
  it.** And decide **tunable vs static** for it: with the putback weight already a
  tunable, two tunables take the count **62 → 64**, while a rule-of-basketball argument
  would keep this one static at 63/29. **State the count explicitly either way.**
- **Both constants' NAMES** (`sim.putback-weight`? `sim.offensive-rebounder-shot-weight`?
  and the assist one).
- **Whether the weight is a MULTIPLIER on `offensiveWeight` or an additive share.** The
  multiplier is the obvious fit for a weighted draw and is what `M = 2.0` presumes —
  but it composes with a real roster's spread differently than the equal-weight
  33.3/16.7 arithmetic suggests. **State which, and why.**
- **Whether `pickShooter` takes the rebounder as a parameter or `ShotSelector` gains a
  second method.** A parameter changes the engine's hottest call site; a second method
  duplicates the draw. ⚠ #043 H's lesson favours **two layers over one method with a
  flag** — but here the "flag" is a real participant, not a mode, so the parameter is
  probably right. Decide explicitly.
- **The RNG question, which the pass must answer before it measures anything:** does the
  weighted draw consume the same number of draws as today? ⚠ **If it does, this pass
  MOVES NO NUMBER in the harness** (all five players are identical skill-10, so a
  re-weighting among equals changes nothing) — which would make it the cheapest possible
  check that the mechanic is wired at all, and would mean **the seeded tests do NOT
  re-baseline.** ⚠ **Verify that; do not assume it** — #043 E3 predicted a re-baseline
  and got one, and the opposite prediction deserves the same scrutiny.

**What this design pass must OUTPUT** (the house three-session rhythm — invoke the
**`project-docs`** skill before writing any of it):
- **`decisions.md #044`** — Decisions A–G recording the seven answers WITH their
  reasoning, plus the mechanical calls above. ⚠ Budget **~15–20k chars**; `decisions.md`
  is under a condense gate that is a **gate on starting Phase 4**.
- **An execute-ready plan in this file**, replacing the questions above — numbered
  `**Step N**` headers with `- [ ]` items, the shape §3.21's plan had (see git history).
- **`possession-flow.puml`** — ⚠ **the design pass DRAWS the new fork; execution only
  confirms it.** §3.22 changes *who is picked* at the top of the second-chance loop, so
  the shot-selector node needs the putback weight and the assist node needs the reduced
  chance. ⚠ Validate with `plantuml -checkonly`, render with
  **`plantuml -DPLANTUML_LIMIT_SIZE=16384 -tpng`** — **the size flag is REQUIRED** (a
  plain `-tpng` silently truncates at 4096px) — and **confirm the height after
  rendering**; it is currently 13,342px against that 16,384 ceiling.
- **`game-events.md`** — ⚠ **only if a row's PARTICIPANTS change.** §3.22 emits **no new
  event and no new outcome**: a putback is an ordinary `SHOT`. But **the assist column on
  the made-`SHOT` rows gains a condition**, and that file is the single per-event
  reference — **do not start a second table anywhere.**
- **`game.md`** — its possession-flow walkthrough says an offense recovery "re-enters the
  second-chance loop at the shot selector" without saying **the rebounder gets no
  preference**, which is exactly §3.22's premise. ⚠ It also still says a block recovery
  **skips the rebound step**, which **§3.21 changed** — fix that in passing.
- **`calibration.md`** — only if a target row moves; update it **and** the
  `CalibrationHarness` `(target ~N)` strings **together**.

**⚠ Do NOT**
- **Do NOT force the shot type.** `pickShotType` already bends by the shooter's own
  skills (#040 C), so a big leans interior on his own.
- **Do NOT weight anyone on the four paths with no rebounder** — `OOB_OFFENSE`, both
  flagrant retentions, and the rebounding foul. The engine never chose a player there,
  and inventing one is the #014/#017/#020 trap.
- **Do NOT touch `base-offensive-rebound` or the `block-*` weights.** Both are fenced by
  #043 B/E4; this phase changes **who shoots next**, never how often a board happens.
- **Do NOT commit** without the user's say-so (CLAUDE.md).

**Verified facts** (confirmed against the code, 2026-08):
- `ShotSelector.pickShooter(List, RandomGenerator)` weights by `offensiveWeight()` =
  `drive + finishing + perimeter + post + longRange`. **No rebounder parameter.**
- `PossessionEngine` has **seven** `continue` retention paths; only **three** have an
  identified rebounder (missed shot, block recovered by offense, missed last FT).
- `recordOffensiveRebound()` fires at exactly **two** sites — `emitMissedShotEvent` and
  `emitBlockRecoveryEvent` — and the free-throw board reaches the first of them.
- `MissedShotResolver.Result` already carries `rebounder`; `FreeThrowResult` and
  `emitBlockRecoveryEvent` do **not**.

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
