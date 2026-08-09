# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.16).

Current focus: **§3.14 — Flagrant / technical fouls**. The full §3.7–§3.16
sequence and what follows (Phase 4) live in **roadmap.md's
"Possession-fidelity completion" section** — not here (todo.md is
current-phase-only). §3.7–§3.13 have all shipped; §3.14 is next, then §3.15
(profiles), §3.16 (recalibration).

> **§3.14 needs a DESIGN PASS first.** There is no execute-ready plan below —
> resolve the open questions into a new numbered `decisions.md #032` (Decisions
> A, B, C…) **plus** an execute-ready plan here. **Write no production code in
> that session.**
>
> **Read #031 H and #031's implementation note before starting.** §3.13 left
> `RotationState` with a deliberate **three-tier** structure — **hard/forced**
> (`replaceFouledOut`), **soft/preference** (foul trouble), **fatigue** — and #031 H
> directs §3.14's ejections to **extend the hard tier** rather than add a fourth
> path (two parallel removal mechanisms is the #013/#015 smell). §3.14 also hits the
> first genuine exception to #023 F's derive-don't-store discipline: **an ejection is
> not derivable from a counter** the way `fouls >= 6` is, so it needs real stored
> state — argue it explicitly the way #028 D argued its column.

---

## §3.13 close-out (SHIPPED 2026-08 — kept only as the handoff §3.14 needs)

Landed as `decisions.md` **#031 A–H** + its implementation note; roadmap bullet is
`[x]`. What §3.14 needs to know:

- **The removal machinery it must extend**: `RotationState.advancePossession(rng)`
  now runs **drain/recover → `replaceFouledOut()` (hard) → `runFoulTroubleSub()`
  (soft) → `runFatigueSubs()` (fatigue)**. `eligible(...)` is the single filter every
  candidate pool passes through — that is the seam an ejection extends.
- **The rotation step CONSUMES RNG now** (one unconditional draw per call, revising
  #023 C). `advancePossession` takes a `RandomGenerator` **method parameter**;
  constructor call sites are untouched.
- **Foul-outs landed at 0.388** (from 0.616), 4/5/6 at `1.00 / 0.52 / 0.39`, top
  starter 36.6 → 36.1. **Promoted to a soft TARGET (~0.39)** in
  [calibration.md](calibration.md) + the harness string. It **misses the old
  ~0.1–0.25 ballpark on purpose** — the lever is saturated (a ~3× stronger curve
  moves the number by nothing), because #031 D's earned return puts the player back
  into the same over-dispersed defender draw. **Do not re-tune the sit curve to
  chase it**; the remaining levers are in #031's follow-up.
- **§3.4 aggregates are unmoved** (117.0 pts / 46.6% FG / 26.7 ast / 13.8 TO /
  5.0 blk / 19.0 fouls, 5-seed mean) — so §3.16 inherits essentially §3.12's
  baseline, not a moved one.

---

## §3.14 — Flagrant / technical fouls: the open questions for the design pass

§3.14 is the eighth possession-fidelity sub-phase and the **last new mechanic**
before §3.15 (profiles) and §3.16 (recalibration). Unlike §3.10–§3.13 it is not a
tuning pass — it adds a foul *sub-system* with paths no current model has. Resolve
these into `decisions.md` #032, then write the execute-ready plan here.

1. **The taxonomy, and whether technicals and flagrants are one mechanic or two.**
   A flagrant is a contact foul with a severity grade; a technical is usually not a
   contact foul at all (and can be called on a player who is on the bench). Do they
   share one resolver and one `PlayType`, or are they genuinely separate? Mirror the
   #027 D naming discipline — new `outcome` strings must not collide across phases.
2. **Possession retention — the path no current model has.** A flagrant awards free
   throws **and** returns the ball to the offense. Every existing FT path ends the
   possession (#030 B); this one does not. Where does that fork live, and does it
   reuse §3.7's block-recovery retain/return seam or need its own?
3. **Who shoots.** A technical FT is shot by a player the offense *chooses* — the
   engine has no notion of a designated shooter, only `pickFreeThrowShooter`'s
   weighted draw. Is that draw good enough, or does this need a real "best free-throw
   shooter on the floor" pick?
4. **Ejections — and the stored state they force.** Two technicals (or one flagrant-2)
   is an ejection. Per #031 H it **must extend the hard/forced tier** and the
   `eligible(...)` filter, not add a fourth removal path. But an ejection is **not
   derivable from a counter** — it is the first genuine exception to #023 F. Argue the
   stored field explicitly (the #028 D pattern), and say what makes it different from
   the `inFoulTrouble` flag #031 E refused.
5. **Do flagrant/technical fouls count toward the 6-foul disqualification?** (In the
   NBA a flagrant does, a technical does not.) This decides whether `recordFoul()` is
   reused or whether the counters split — and a split touches `isFouledOut()`,
   `foulTroubleLevel()`, and the box score.
6. **Rate + calibration.** These are genuinely rare (a few per team per *season*, not
   per game). Does the harness need a new instrument, or is the existing foul-mix
   line enough? Note the §3.13 lesson: a rare-event probability must dodge
   `PROB_FLOOR`. **§3.13 was the third such site, so #030's consolidation trigger has
   already fired** — §3.14 would be the fourth. Decide in the design pass whether to
   fold the clamp-helper consolidation in here or leave it to §3.15 (see "Where
   deferred work lives"); either is defensible, but do not add a fourth hand-rolled
   clamp without naming the choice.
7. **The points cost.** Extra FTs are pure-additive with no offsetting removal (the
   §3.11 shape, #029 E) — but possession retention adds a second channel on top.
   Budget it up front (#030 E / #031 G discipline) with an explicit stop condition,
   and remember **§3.16 owns re-centering** — do not re-center here.

---

### ⚠ Do NOT (guardrails carried into §3.14)

- **Do NOT add a fourth removal path in `RotationState`.** §3.13 left a clean
  three-tier structure (hard/forced · soft/preference · fatigue); an ejection is
  unambiguously the **hard** tier and extends `eligible(...)` (#031 H).
- **Do NOT re-tune §3.13's foul-trouble sit curve** to chase the old ~0.1–0.25
  foul-out ballpark. Measured saturated (#031 implementation note): a ~3× stronger
  curve moves the number by nothing. The remaining levers are listed in #031's
  follow-up and none of them belong to §3.14.
- **Do NOT trim `BASE_NO_BASKET_FOUL`** — a measured **wrong-way** lever (#028):
  trimming it *raises* points.
- **Do NOT re-open §3.12's `FOUL_MULT_*`** (settled on realism, #030 G) or touch
  `pickDefender`'s `individualDefense` weighting (#031 A — it would delete correct
  realism and silently move calibrated blocks/steals/contests).
- **Do NOT re-center points/FG%** — §3.16 owns it and the targets are CONTESTED
  ([calibration.md](calibration.md)).

---

## Verified facts (the anchors §3.14 needs — confirmed against the code 2026-08, post-§3.13)

Paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`.
Trust these; re-check only if the code moved.

**The removal machinery §3.14's ejections must extend (#031 H) — all of it:**
- `SimConfig.FOUL_OUT_LIMIT = 6` — the disqualification limit.
- `PlayerGameState.isFouledOut()` — a **derived predicate** over the `fouls`
  counter (#023 F), not a stored flag. **Note: it is `isFouledOut()`, not
  `hasFouledOut()`** — #030 G and some older notes name it wrongly.
- `PlayerGameState.foulTroubleLevel()` — §3.13's derived rotation input (the foul
  count capped at the limit). Also derived, also no flag.
- `PlayerGameState.recordFoul()` / `getFouls()` — the **single** counter every foul
  type increments today (shooting, and-1, rebounding). **Open question 5 asks whether
  §3.14's fouls join it or split from it.**
- `RotationState.advancePossession(RandomGenerator)` — the three tiers, in order:
  drain/recover → `replaceFouledOut()` (**hard**) → `runFoulTroubleSub()` (**soft**,
  §3.13) → `runFatigueSubs()` (**fatigue**). **Takes an RNG as a METHOD parameter**
  and consumes **exactly one unconditional draw per call** (§3.13, revising #023 C).
- **`RotationState.eligible(...)` — the single filter every candidate pool passes
  through.** It filters `isFouledOut()` out today; **this is the seam an ejection
  extends** rather than adding a fourth removal path (#031 H).
- `RotationState.replaceFouledOut()` — forces off every on-floor player at the limit,
  replacing from the **full** bench with the freshest eligible player. If no eligible
  replacement exists the player **stays on** (the never-below-5 last resort).
- `RotationState.benchWithinDepth()` — the `rotationDepth` window the **soft** rule
  draws from (foul-out force-offs reach the full bench; foul-trouble subs do not).
- `PossessionEngine.simulate()` calls `home.rotation().advancePossession(rng)` then
  `away.rotation().advancePossession(rng)` once per possession, before
  `resolvePossession()`.

**§3.13's foul-trouble constants (do NOT re-tune them for §3.14 — see the guardrails):**
- `FOUL_TROUBLE_SIT_PROBABILITY = {0, 0, 0, 0.120, 0.500, 0.900, 0}` — indexed by
  foul count; a **probability**, not a multiplier. **Saturated** (#031 note).
- `FOUL_TROUBLE_VALUE_SENSITIVITY = 0.55`, `FOUL_TROUBLE_STARTER_BONUS = 0.15`,
  `FOUL_TROUBLE_BENCH_DISCOUNT_PER_SLOT = 0.05`,
  `FOUL_TROUBLE_MIN_ROSTER_FACTOR = 0.70` — the value + roster blend.
- `FOUL_TROUBLE_FRESHNESS_MARGIN = 1.0` — the anti-oscillation guard. **Larger is
  worse**, measured; it is coupled to the §3.5 energy constants.
- `SimConfig.foulTroubleSitProbability(...)` deliberately **dodges `PROB_FLOOR`** —
  the third such site in the package. A fourth is #030's consolidation signal.

**Where the fouls actually come from (#031 A's mechanism — still true, still off limits):**
- `ShotSelector.pickDefender(...)` — draws the defender **weighted by
  `individualDefense`**. The concentration driver (sd 4.11 across 359 players).
- `FoulResolver.isFoul(...)` — scales that defender's foul probability by their own
  `foulProne` (sd 1.22 — nearly flat, not the driver).
- `FoulResolver.pickCommitter(...)` — the §3.10 rebounding-foul committer draw,
  weighted by `foulProne` alone.
- `PossessionEngine.pickFreeThrowShooter(...)` — the weighted FT-shooter draw open
  question 3 asks about.

**The coach attributes (#018) — all five:**
- A coach has **exactly five** continuous 1–20 avg-10 attributes on
  `CoachEntity`: `pace`, `offensiveScheme`, `defensiveScheme` (all §3.4),
  `rotationDepth`, `substitutionAggressiveness` (both §3.5, and §3.13 reuses the
  second). **There is NO coach `acumen`** — acumen is one of the 23 *player* skills.
- `CoachModifiers.from(coach, config)` turns them into multipliers, already threaded
  into `RotationState` via `TeamContext`.
- Existing rotation constants: `BASE_SUB_ENERGY_THRESHOLD = 62.0`,
  `STARTER_SUB_THRESHOLD_BONUS = 8.0` (the fatigue tolerance §3.13 deliberately
  inverts), `BASE_ROTATION_DEPTH = 4`.

**The measurement:**
- `CalibrationHarness` prints the §3.4 aggregates, the §3.5 per-slot minutes line,
  the foul mix, **foul-outs/team/game** and the **4 / 5 / 6 foul distribution**.
- Steer by **multiple `-DcalibrationSeed` runs to the MEAN** (#029 E), never one.

**§3.13's shipped landing, for reference (5 seeds, to the mean):**
`117.0 pts / 46.6% FG / 36.6% 3P / 26.7 ast / 13.8 TO / 5.0 blk`, fouls
**19.0**/team/game, **foul-outs 0.388**, players at 4/5/6 `1.00 / 0.52 / 0.39`,
minutes `36.1 / 33.2 / 31.1 / 28.7 / 26.8 / 24.6 / 22.6 / 20.2 / 16.7`.
**Targets: see [calibration.md](calibration.md)** — it is the source of truth;
points/FG% are flagged CONTESTED pending §3.16, and foul-outs are now a **soft
TARGET** owned by §3.13.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Period-/time-aware foul trouble** → #031 C's follow-up, deliberately deferred
  by §3.13. Needs a `gameProgress` notion `advancePossession()` doesn't have. The
  engine will bench a 5-foul star in the final minute — a known, accepted cost.
- **Getting foul-outs below ~0.38** → #031's follow-up. §3.13's lever is **measured
  saturated**; the three remaining candidates (relax the earned return, reduce the
  over-dispersion at source, or re-verify the benchmark) are all out of §3.14's
  scope, and the second is explicitly forbidden on realism grounds.
- **Splitting `substitutionAggressiveness`** → #031 B's follow-up: it now governs
  **two** behaviors (fatigue subs and foul-trouble subs) that need not correlate.
  An accepted cost of the reuse call; splitting is purely additive.
- **The over-dispersion finding (#031 A)** → recorded in #031's follow-up because it
  outlives this phase: the same `pickDefender` concentration applies to **blocks,
  steals, and shot contests**. Any future pass finding one of those over-dispersed
  should start there.
- **Strategic substitution as a category** → [ideas.md](ideas.md) (parked, not planned).
  §3.13 adds the engine's **first** strategic sub; matchup/small-ball/hot-hand/
  closing-lineup subs are all absent and all blocked on the same missing prerequisite
  — **game-situation awareness in the rotation step**. #031's rule is the reusable
  template. This is also the real home of C's deferred time-awareness.
- **§3.15 (`SimConfig` profiles)** → roadmap.md, promoted from a backlog chore by
  user call. The accumulated design reasoning (including the open
  full-replacement-vs-override question) stays in [backlog.md](backlog.md) as the
  design-pass input. Scope-fenced to the **developer-facing substrate**; the
  player-facing side (eras, difficulty, custom rules) is Phase 4+.
- **§3.16 (recalibration against verified targets)** → roadmap.md, the last
  Phase-3 sub-phase and a different *kind* of pass (it adds no mechanic; it
  re-solves numbers — a second §3.4). Owns the contested points/FG% targets and
  the efficiency-vs-volume lever question. **§3.13 handed it an essentially
  UNMOVED baseline** (points 117.0 → 117.0, FG% 46.4% → 46.6%) — the predicted
  scoring drop from benching starters did not materialize, because #031 D's return
  cycle gives the minutes back. Its **prerequisite is a backlog chore**: verify the
  benchmarks with real sources — **including the foul-out figure §3.13 promoted to a
  soft target without sourcing it** (#031 H).
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
  fouled-three work, and relevant to §3.16.
- **⚠️ THE CLAMP-HELPER TRIGGER HAS FIRED (§3.13).** #030 said "the `sim` package now
  has **two** places where a rare-event probability must dodge `PROB_FLOOR`
  (`rareEventProbability`, and `isFoul`'s floor-free multiply) — **if a third appears,
  that is a sign the clamp helpers want consolidating**." §3.13 added the third:
  `SimConfig.foulTroubleSitProbability` clamps to `[0, PROB_CEILING]` by hand, because
  the floor would give a clean player a 2% chance of being benched on every one of
  ~100 checks a game. **The consolidation is now warranted rather than speculative** —
  three hand-rolled floor-free clamps with no shared helper. **§3.14 will likely be the
  fourth** (a flagrant/technical rate is rarer still). Fold it into §3.14's design pass
  or §3.15's `SimConfig` work; it is a small refactor, not a phase.
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true -DcalibrationSeed=NNNN`) stays in the `sim` test sources; it
  reports the §3.4 aggregates + §3.5 minutes/period-FG% + §3.7 blocks + §3.8 OOB +
  §3.9 turnover-cause mix + §3.10 team-fouls/bonus + §3.11 and-1 rate + FT-source
  split + §3.12 per-shot-type foul breakdown, 3-FT trips, and the foul-out line.
  Re-run it after any `SimConfig` change.
