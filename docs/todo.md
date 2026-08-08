# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.16).

Current focus: **§3.13 — Foul trouble & foul-outs**. The full §3.7–§3.16
sequence and what follows (Phase 4) live in **roadmap.md's
"Possession-fidelity completion" section** — not here (todo.md is
current-phase-only). §3.7–§3.12 have all shipped; §3.13 is next, then §3.14
(flagrants), §3.15 (profiles), §3.16 (recalibration).

> **§3.13 needs a DESIGN PASS first — there is no execute-ready plan below, and
> no `decisions.md` entry yet.** Resolve the open questions into a new numbered
> `decisions.md #031` (Decisions A, B, C…) **plus** an execute-ready plan in this
> file. **Write no production code in that session.**
>
> **Why this phase exists.** §3.12 added a foul-out instrument (#030 G) and
> measured the rate for the **first time** — the mechanism has been live since
> §3.5 but no calibration run had ever reported it. It landed at **0.60
> foul-outs/team/game against a ~0.1–0.25 plausibility ballpark**, and — crucially
> — **~70% of that predates §3.12**: with §3.12's new multipliers zeroed (i.e.
> §3.11's exact foul reach) it is already **0.425** across 5 seeds. #030 G
> explicitly directed that a pre-existing §3.5 problem be **triaged separately,
> not absorbed into §3.12's recalibration**, which is what happened.
>
> **This is §3.5/rotation work, not foul-model work** — see the trap below.

---

## §3.13 open questions (resolve these into `decisions.md #031`)

**The core insight to design around: nothing in the engine reacts to foul
*trouble*, only to foul-*out*.** A player accumulates fouls with no consequence
whatsoever until the 6th, at which point they are forced off. Real coaches bench
a player at 4–5 fouls (especially early), which is *the* mechanism suppressing
real-world foul-outs. That absence is the leading hypothesis for the overshoot,
and it is a **behavioral** gap, not a rate problem.

1. **At what foul count does a coach bench a player?** (5? 4? does it depend on
   how much game is left?) This is the crux decision.
2. **Does the threshold vary by period?** 5 fouls in Q2 is a crisis; 5 fouls in
   the last two minutes of Q4 is often played through. A period- or
   time-remaining-aware rule is more realistic but adds a dimension; a flat
   threshold is simpler. Which, and why?
3. **How does `substitutionAggressiveness` scale the threshold?** **The WHICH is
   already settled (user call, 2026-08): reuse the existing
   `substitutionAggressiveness` coach attribute — do NOT add a 6th coach
   attribute, and do NOT make the threshold flat/coach-blind.** It already means
   "how readily does this coach pull a player," which is precisely the
   foul-trouble decision: a cautious coach sits a 4-foul player, a gambler rides
   him. It costs no schema change (#018's five attributes stand), and inventing a
   sixth for a sibling behavior would fabricate a field ahead of its consumer
   (#014/#017). **What is still open is the SHAPE**: does it shift the threshold
   itself (4 vs. 5 fouls), or the *probability* of sitting at a given count, or
   how long the player stays down? Use the established avg-10 deviation form
   (#022 / `SimConfig.rotationModifier`), which is what `CoachModifiers` already
   applies to this attribute.
   **Note the coupling this creates, and say something about it in #031:**
   `substitutionAggressiveness` currently drives **fatigue** subs
   (`runFatigueSubs`), so after §3.13 one number governs two behaviors that need
   not correlate in reality (quick with tired legs, stubborn about foul trouble).
   That is an accepted cost of the reuse call, not an oversight — record it as a
   trade-off, and note that splitting it later is additive if a consumer ever
   wants the distinction.
4. **Derived predicate or stored state?** The house discipline (#023 F for
   foul-outs, #028 A1 for the penalty) is to **derive** from the existing `fouls`
   counter rather than store a `inFoulTrouble` flag — the #013/#015
   duplicate-source-of-truth trap. Confirm this holds here.
5. **What happens when a benched player is needed?** If foul trouble pulls
   players and the bench is thin, does the rule yield (the existing
   `replaceFouledOut` already has a "never below 5" last resort)? Late-game, does
   a coach re-insert a 5-foul star?
6. **Is the foul RATE also too high, independent of the benching gap?** Fouls
   sit at **19.1/team/game**, which is *inside* the ~19–20 ballpark — so the
   aggregate looks right while foul-outs do not. That suggests distribution, not
   volume (a few players absorbing too many). **Check the per-player foul
   distribution before concluding** — the §3.12 harness line already prints
   players at 4 / 5 / 6 fouls.
7. **How much of the 0.425 baseline is actually §3.5 substitution behavior vs.
   the foul model?** Worth decomposing before choosing a lever, the same
   decompose-before-you-trim discipline #030 E imposed.

### ⚠ The trap this design pass must not fall into

**Do NOT reach for `BASE_NO_BASKET_FOUL` to reduce fouls.** It is a **wrong-way
lever** (#028, measured): trimming it *raises* points, because it controls a
**swap** — when it fires, a live shot is replaced by a 2-FT trip worth ~1.5
points; when it doesn't, the possession keeps a live shot worth *more* than 1.5
once its offensive-rebound, and-1, and rebounding-foul continuations are counted.
§3.12 made this wrong-way effect apply to jump shots too. The instinctive fix
("fouls are high, turn the foul rate down") is exactly the wrong move.

### Scope fence (set at §3.12's close-out)

- **§3.13 is engine work in the `sim` package** — rotation/substitution behavior.
  Expect **no schema and no OpenAPI change**, consistent with every sub-phase
  since §3.10. If the design pass finds it needs one, that is a conscious
  reversal to argue explicitly (as #028 D did).
- **Flagrants/technicals are §3.14, NOT this pass.** But note the interaction:
  an ejection is a *second* way a player leaves early, so §3.14 should build on
  whatever §3.13 establishes rather than adding a parallel removal mechanism.
- **Do not re-open §3.12's multipliers.** `FOUL_MULT_*` landed on realism
  grounds with the user (#030 G + the implementation note); if §3.13 wants fewer
  fouls, the answer is benching behavior, not re-tuning a settled table.
- **Points/FG% recalibration is §3.16, not here.** §3.13 will move minutes and
  therefore scoring; report the harness aggregates, but **do not** try to
  re-center them — the targets themselves are contested
  ([calibration.md](calibration.md)).

---

## Verified facts (the anchors §3.13 needs — confirmed against the code 2026-08, post-§3.12)

Paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`.
Trust these; re-check only if the code moved.

**The foul-out mechanism as it exists today (all of it):**
- `SimConfig.FOUL_OUT_LIMIT = 6` — the only foul-related rotation constant.
- `PlayerGameState.isFouledOut()` — a **derived predicate** over the `fouls`
  counter (#023 F), not a stored flag. **Note: it is `isFouledOut()`, not
  `hasFouledOut()`** — #030 G and some older notes name it wrongly.
- `PlayerGameState.recordFoul()` / `getFouls()` — the counter every foul type
  increments (shooting, and-1, rebounding).
- `RotationState.replaceFouledOut()` — called from `advancePossession()`, forces
  off every on-floor player at the limit, replacing from the **full** bench (not
  the `rotationDepth` window) with the freshest eligible player. If no eligible
  replacement exists the fouled-out player **stays on** (the never-below-5 last
  resort).
- `RotationState.eligible(...)` — filters `isFouledOut()` out of any pool.
- **`RotationState.runFatigueSubs()` is the ONLY other substitution driver, and
  it considers energy alone — it does not look at `fouls`.** That is the gap.

**The coach attributes (#018) — all five, and the one §3.13 uses:**
- A coach has **exactly five** continuous 1–20 avg-10 attributes on
  `CoachEntity`: `pace`, `offensiveScheme`, `defensiveScheme` (all §3.4),
  `rotationDepth`, `substitutionAggressiveness` (both §3.5). **There is NO coach
  `acumen`** — acumen is one of the 23 *player* skills (`PlayerGameState`), and
  the two are easy to conflate.
- `CoachModifiers.from(coach, config)` turns them into multipliers, already
  threaded into `RotationState` via `TeamContext` — so **§3.13 needs no new
  plumbing**, just a new read of `subAggressivenessFactor()`.
- `SimConfig.rotationModifier(...)` is the avg-10 deviation helper the two
  rotation attributes use; `coachModifier(...)` is the §3.4 equivalent.

**The measurement (§3.12 harness, and how to reproduce it):**
```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml test -pl gametime-app -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000 -DfailIfNoTests=false
```
- The harness prints **foul-outs per team per game** plus the **players at 4 / 5 /
  6 fouls** distribution (added by §3.12, #030 G). The distribution is the more
  useful line — it shows pressure building *below* the threshold.
- **§3.12 landing: 0.60 foul-outs/team/game**; 0.84 / 0.45 / 0.59 players at
  4 / 5 / 6 fouls, of ~9.2 who played.
- **The pre-§3.12 baseline is 0.425** (5 seeds), reproduced by setting
  `FOUL_MULT_PERIMETER` and `FOUL_MULT_THREE` to `0.0` — which reproduces §3.11's
  exact foul reach. That is the honest "how much did §3.12 add" measurement.
- Steer by **multiple `-DcalibrationSeed` runs to the MEAN** (#029 E) — per-seed
  noise is ±1.5 points and enough to bait an over-correction.

**§3.12's shipped landing, for reference (5 seeds, to the mean):**
`117.0 pts / 46.4% FG / 36.7% 3P / 26.8 ast / 13.5 TO / 4.8 blk / 2.8 OOB`,
fouls **19.1**/team/game (4.87/team/period), **52.3%** of team-periods in the
penalty, and-1s 1.87, 3-FT trips 0.59. **Targets: see
[calibration.md](calibration.md)** — it is the source of truth, and points/FG%
are flagged CONTESTED pending §3.16.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **§3.14 (flagrant/technical fouls)** → roadmap.md's Possession-fidelity
  section, a numbered sub-phase needing its own design pass. Changes *who
  shoots*, adds a possession-retention path no current model has, and adds
  ejections — which should build on §3.13's foul-trouble handling.
- **§3.15 (`SimConfig` profiles)** → roadmap.md, promoted from a backlog chore by
  user call. The accumulated design reasoning (including the open
  full-replacement-vs-override question) stays in [backlog.md](backlog.md) as the
  design-pass input. Scope-fenced to the **developer-facing substrate**; the
  player-facing side (eras, difficulty, custom rules) is Phase 4+.
- **§3.16 (recalibration against verified targets)** → roadmap.md, the last
  Phase-3 sub-phase and a different *kind* of pass (it adds no mechanic; it
  re-solves numbers — a second §3.4). Owns the contested points/FG% targets and
  the efficiency-vs-volume lever question. Its **prerequisite is a backlog
  chore**: verify the benchmarks with real sources.
- **Calibration targets** → [calibration.md](calibration.md), **the source of
  truth** (created §3.12, superseding `decisions.md` #022 D). Update it *and* the
  `CalibrationHarness` `(target ~N)` strings together whenever a target moves.
- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning, the `decisions.md` condense pass, harness self-verification) →
  [backlog.md](backlog.md).
- **Untriaged future-improvement ideas** (no phase home, not chores) →
  [ideas.md](ideas.md). *(Includes the parked cap 3→5 tuning idea — do NOT touch
  `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` without its own recalibration pass.)*
- **§3.6 seams left open by #024** (carry into Phase 4/7): play-by-play
  pagination + a `period` filter (Decision D); the per-event time column's storage
  shape (Decision E); a lean header-only `GameResult` projection (Decision A).
- **§3.7 seams left open by #025**: a skilled-blocker recovery edge (Decision D);
  shot-clock pressure on block-recovered second-chance possessions (Decision E →
  §3.9-E, still parked #027 D).
- **§3.9 seams left open by #027**: §3.7-E shot-clock pressure (still parked);
  finer turnover sub-types (add a weight + enum value when a consumer wants it).
- **§3.10/§3.11 seams left open by #028/#029**: `committing_team_id` is
  **populated and queryable but not surfaced on the OpenAPI `GameEvent`** —
  additive whenever a play-by-play or Phase-4 stats consumer wants it; the
  observed off/def rebounding-foul split (**78/22**) drifts from the configured
  75/25 because defensive fouls compound through retained possessions —
  back-solve only if a consumer needs the observed split to hit a target.
- **§3.12 seams left open by #030**: the engine shoots **~20 3PA/team/game against
  the NBA's ~35** — a pre-existing observation surfaced (not caused) by §3.12's
  fouled-three work, and relevant to §3.16; and the `sim` package now has **two**
  places where a rare-event probability must dodge `PROB_FLOOR`
  (`rareEventProbability`, and `isFoul`'s floor-free multiply) — if a third
  appears, that is a sign the clamp helpers want consolidating.
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true -DcalibrationSeed=NNNN`) stays in the `sim` test sources; it
  reports the §3.4 aggregates + §3.5 minutes/period-FG% + §3.7 blocks + §3.8 OOB +
  §3.9 turnover-cause mix + §3.10 team-fouls/bonus + §3.11 and-1 rate + FT-source
  split + §3.12 per-shot-type foul breakdown, 3-FT trips, and the foul-out line.
  Re-run it after any `SimConfig` change.
