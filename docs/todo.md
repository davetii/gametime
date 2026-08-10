# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.16).

Current focus: **§3.14b — Flagrant fouls**. The full §3.7–§3.16 sequence and what
follows (Phase 4) live in **roadmap.md's "Possession-fidelity completion"
section** — not here (todo.md is current-phase-only). §3.7–§3.13 and **§3.14a**
have all shipped.

> **⚠️ §3.14b NEEDS A DESIGN PASS FIRST — write NO production code this session.**
> Per the three-session rhythm in CLAUDE.md, this session resolves the open
> questions below into a new numbered `decisions.md` **#034** (Decisions A, B, C…)
> **plus** an execute-ready plan in this file. The *next* session builds it.
>
> **§3.14b is the hard half of the §3.14 split** (#032 A). §3.14a took the
> self-contained mechanic; §3.14b owns the two things that were deliberately
> isolated into it:
>
> - **The possession-retention fork** — a flagrant awards free throws **and**
>   returns the ball, which breaks #030 B's invariant that *every* FT path ends
>   the possession. No existing path does this.
> - **The genuine #023 F stored-state exception** — a flagrant-2 is a **severity
>   grade with no counter behind it**, so unlike §3.14a's two-technical case it is
>   **not derivable**. #032 F established this by showing the exception does *not*
>   arise for technicals; §3.14b is where it does.
>
> Numbering stays `a`/`b`: **§3.15 (profiles) and §3.16 (recalibration) do NOT
> renumber**, because §3.16 is named in the shipped, never-retro-edited text of
> #030 and #031 (#032 A).

---

## §3.14a close-out (SHIPPED 2026-08 — kept only as the handoff §3.14b needs)

Landed as `decisions.md` **#032 A–J** + its implementation note; roadmap bullet is
`[x]`. What §3.14b needs to know:

- **The ejection seam it builds on.** `RotationState.eligible(...)` now filters
  through a shared private predicate **`isDisqualified(p)` = `isFouledOut() ||
  isEjected()`**, used by `eligible(...)`, `replaceFouledOut()` **and**
  `mostFoulTroubledCandidate()`. §3.14b's flagrant-2 ejection extends **that
  predicate**, not a fourth removal path (#031 H). The three-tier structure
  (hard/forced · soft/preference · fatigue) is intact.
- **`PlayerGameState.isEjected()` is DERIVED** — `technicalFouls >= TECHNICAL_EJECTION_LIMIT`,
  no stored flag. **A flagrant-2 cannot copy this shape**: there is no counter to
  derive from. That is question 2 below, and it is the real one.
- **The counter split (#032 E) does NOT apply to flagrants.** `technicalFouls` is
  separate from `fouls` because a technical does not count toward the six-foul
  limit. **A flagrant DOES count** — so it uses `recordFoul()`, the ordinary path,
  and feeds `isFouledOut()` / `foulTroubleLevel()` / the penalty tally normally.
- **`GameData.isInBonus` now has an outcome-aware exclusion** and a
  `countsTowardBonus(e)` helper. `TECHNICAL_FOUL` is the only excluded outcome.
  **A flagrant counts**, so §3.14b adds nothing here — but see the doubled-tally
  warning below.
- **⚠ THE BONUS EXCLUSION EXISTS IN TWO PLACES.** `GameData.isInBonus` and
  `CalibrationHarness`'s **independent** per-team-period tally are two separate
  derivations over the same events. A foul type that does not count must be
  excluded in **both**, or the instrument silently disagrees with the engine.
  (Found during §3.14a execution.) A flagrant counts, so §3.14b touches neither —
  but do not let that be an accident.
- **`advancePossession(rng)` now RETURNS the technical committer** (or null) and
  consumes **three** unconditional draws (foul-trouble roll, technical roll,
  committer draw). The event + FT are emitted by `PossessionEngine.simulate()`,
  which owns `GameData`/team ids/period/sequence. **§3.14b's roll does NOT go
  here** — a flagrant rides an existing foul inside the possession flow.
- **`FreeThrowSource.TECHNICAL` was added** (a divergence from #032's Status
  block — see the implementation note). §3.14b's FT source decision is therefore
  unconstrained by a "no new source" rule; decide it on the merits.
- **The clamp-helper consolidation is DONE** — `SimConfig.clampRareProbability`
  owns all four floor-free sites. A fifth costs one call.
- **`technicalFouls` IS surfaced on the box score (#033)** — column + entity +
  OpenAPI, on a **parity** argument (twelfth of twelve accumulators). **§3.14b
  needs no new stat column**: a flagrant is a personal foul and already feeds the
  existing `fouls`. But if §3.14b stores a flagrant-2 severity flag (question 2),
  #033's parity argument does **not** cover it — that is stored *state*, not a
  stat, and needs its own justification.
- **§3.4 aggregates unmoved** (5-seed mean): 117.5 pts / 46.6% FG / 37.0% 3P /
  26.8 ast / 13.5 TO, penalty rate **51.2%**, foul-outs **0.409**, technicals
  **0.367**, ejections **0.014**.

---

## §3.14b — Flagrant fouls: the open questions for THIS design pass

**NOT execute-ready.** These become `decisions.md` #034 — **note #033 is already
taken** by the box-score surfacing done immediately after §3.14a. **Write no production
code in this session.**

**What the user has already specified** (carry into the design pass, do not re-litigate):
- A flagrant requires a foul to have occurred — **no change to the existing foul roll**.
  It is an **additional roll** on top: *was that foul a super-rare flagrant?*
- The penalty is **always two free throws**, and it **counts toward the 6-foul limit**
  (unlike a technical — #032 E's counter split does **not** apply).
- **Offensive player commits** → possession flips to the defense + 2 FTs.
- **Defensive player commits on a shooter, shot MISSES** → offense gets 2 FTs **and
  retains** the ball.
- **Defensive player commits on a shooter, shot GOES IN** → basket counts, shooter gets
  2 FTs, **offense retains**.
- Rate: **~0.25–0.40 per game** league-wide (5–10 per team per 82-game season).
- A **flagrant-2** is grounds for ejection, guarded the same way a foul-out is.

**The open questions:**

1. **POSSESSION RETENTION — the structural crux, and the reason §3.14b is the hard
   half.** A flagrant awards FTs **and** returns the ball. **Every existing FT path ends
   the possession (#030 B).** Does this reuse §3.7's block-recovery retain/return seam
   (`offenseRetains()` + the `continue` into the second-chance loop) or need its own
   fork? Does it respect `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`, or is a flagrant
   retention exempt from the second-chance cap?
2. **The flagrant-2 stored state — the GENUINE #023 F exception (#032 F).** Unlike the
   two-technical case, a flagrant-2 is a **severity grade with no counter behind it**,
   so it is **not derivable**. Argue the stored field explicitly the way **#028 D** argued
   its column, and say what makes it different from the `inFoulTrouble` flag #031 E
   refused. It extends **`isDisqualified(...)`** — the seam §3.14a already built (#031 H).
3. **Flagrant-1 vs flagrant-2 — one grade or two?** Two FTs either way; the grade only
   decides ejection. Is a second severity roll worth the tuning surface, or does a single
   "flagrant" with a sub-rate for ejection suffice?
4. **Where the roll hangs.** Which of the existing foul sites can turn flagrant — the
   pre-shot stopped-shot foul, the and-1, the §3.10 rebounding foul, or all three? The
   and-1 case is the one that produces the "basket counts + 2 FTs + retain" path.
5. **Rate + calibration.** ~0.16 committed per team per game. **Resolvability, already
   computed (#032 J's method):** ~33 events at 102 games (relative sd **17.4%**), ~166 at
   5 seeds (**7.8%**) — **coarser than the technical line, and single-seed readings are
   useless.** Does it need its own harness line, or can it share §3.14a's?
6. **The points cost.** Budgeted at **+0.43/team/game** — FTs (+0.24) plus retention
   (+0.19, an upper bound assuming every flagrant is defensive *and* retained). Still
   below the ±1.5 noise band, so **the inverted stop condition (#032 I) likely applies
   again** — but retention is a second channel and should be re-priced, not assumed.
   **Note §3.14a's precedent**: its budgeted +0.26 landed as an observed +0.5, still
   inside the band. Retention is the channel that could push §3.14b past it.
7. **Naming.** New `outcome` strings must not collide across phases (#027 D). Candidates
   mirror the established suffix discipline (`FLAGRANT_FOUL_*`?), and must sit alongside
   §3.14a's `TECHNICAL_FOUL` on the same `PlayType.FOUL`. The FT source is an open call
   (§3.14a added `FreeThrowSource.TECHNICAL`, so a fourth value is not precluded — but
   a flagrant may reasonably reuse `SHOOTING`).

---

### ⚠ Do NOT (guardrails carried into §3.14b)

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
  a foul that already happened (user call).
- **Do NOT back-solve §3.14a's technicals constant** against the ~5% harness gap —
  that is #032 B2's documented nominal-vs-actual pace effect, not drift.

---

## Verified facts (the anchors §3.14b needs — confirmed against the code 2026-08, post-§3.14a)

Paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`.
Trust these; re-check only if the code moved.

**The disqualification seam §3.14b's flagrant-2 must extend:**
- `RotationState.isDisqualified(PlayerGameState)` — the shared private predicate,
  `isFouledOut() || isEjected()`. **This is the single place a third cause is added.**
- `RotationState.eligible(...)` — every candidate pool passes through it; calls
  `isDisqualified`.
- `RotationState.replaceFouledOut()` — the **hard** tier; forces off every
  disqualified on-floor player, replacing from the **full** bench. If no eligible
  replacement exists the player **stays on** (the never-below-5 last resort, #023 F).
- `RotationState.mostFoulTroubledCandidate()` — the **soft** tier's candidate
  filter, also via `isDisqualified`.
- `PlayerGameState.isFouledOut()` / `isEjected()` — both **derived** predicates over
  monotonic counters. **A flagrant-2 has no counter — that is question 2.**
- `PlayerGameState.recordFoul()` / `getFouls()` — the **personal-foul** counter.
  **A flagrant DOES join it** (unlike §3.14a's technical).
- `SimConfig.FOUL_OUT_LIMIT = 6`, `TECHNICAL_EJECTION_LIMIT = 2`.

**The possession-retention seams question 1 must choose between:**
- `PossessionEngine.resolvePossession(...)` — the `while (true)` second-chance loop.
  `offensiveRebounds` counts retentions against
  `SimConfig.MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` (3).
- **§3.7 block recovery**: `BlockRecovery.offenseRetains()` + `continue` into the
  loop, gated on `capReached`.
- **§3.10 rebounding foul**: `PossessionEngine.ReboundFoulResult(sequence, offenseRetains)`
  — the closest analogue, a foul that forks the possession **both** ways.
- **§3.8 missed-shot**: `MissedShotOutcome.offenseRetains()`, cap passed **in** to the
  resolver.
- **⚠ Every FT path currently ENDS the possession (#030 B)** — that is the invariant
  a flagrant breaks, and no existing code does it.

**The foul + FT machinery §3.14b reuses:**
- `PossessionEngine.awardFreeThrows(...)` — the single FT block shared by **four**
  situations since §3.14a (shooting, §3.10 bonus, §3.11 and-1, §3.14a technical).
  Takes `count` and `source` as parameters (#029 D). **Reuse verbatim** — FT/points
  reconciliation is automatic. A flagrant is `count = 2`.
- `PossessionEngine.pickFreeThrowShooter(...)` — the **`foulDrawing`-weighted** draw
  (#028 B). Untouched by §3.14a.
- `PossessionEngine.pickTechnicalFreeThrowShooter(...)` — §3.14a's **deterministic**
  best-`freeThrows` pick (#032 G). **A flagrant's shooter is the player who was
  fouled**, so it needs neither of these on the shooting path.
- `FoulResolver.isFoul` / `isAndOne` / `resolveReboundFoul` — the three existing foul
  sites question 4 must choose among.
- `GameData.addEvent(..., committingTeamId)` — the 9-arg overload (#028 D).
- `GameData.isInBonus` / `periodFoulCount` / `countsTowardBonus` — §3.14a's
  outcome-aware exclusion. **A flagrant counts**, so it needs no entry there.
- `GameData.TECHNICAL_FOUL_OUTCOME` — the outcome-string constant lives on `GameData`
  (the class that must recognise it), not on the engine that emits it.

**The clamp helper (consolidated §3.14a, #032 H):**
- `SimConfig.clampRareProbability(p)` — `max(0.0, min(PROB_CEILING, p))`, the
  **floor-free** helper. All four rare-event sites route through it. **A flagrant
  rate is rarer still — use this, never `clampProbability`.**
- `SimConfig.clampProbability(p)` — the normal helper, with `PROB_FLOOR = 0.02`.

**The measurement:**
- `CalibrationHarness` prints the §3.4 aggregates, per-slot minutes, the foul mix,
  fouls/bonus, and/1 + FT sources, the §3.12 per-shot-type breakdown, foul-outs +
  the 4/5/6 distribution, and **§3.14a's technicals + ejections line**.
- **⚠ `Fouls / team / game` tallies ALL FOUL events**, so it includes technicals as
  of §3.14a (~19.4 vs §3.13's 19.0 personal-only). Not comparable across the phase.
- Steer by **multiple `-DcalibrationSeed` runs to the MEAN** (#029 E), never one.

**The two commands** (both verified working, 2026-08 — run from the repo root):

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml test -pl gametime-app -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000 -DfailIfNoTests=false
```

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install
```

The first runs the harness (~30s, 102 games) — `@EnabledIfSystemProperty` disabled
without `-Dcalibration=true`, so it never runs in a normal build. The second is the
full gate including JaCoCo coverage (per-package, 80% line floor, `sim` currently
**99.2%**).

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Bench and coach technicals** → #032's follow-up. Not modelled (#032 C): a coach is
  not a `PlayerGameState`, so a coach technical would be a committer-less FT.
- **A technical's total lack of causal input** → #032's follow-up. Making it situational
  needs the **same** missing prerequisite as every parked strategic-sub idea:
  game-situation awareness in the rotation step (#031's follow-up, [ideas.md](ideas.md)).
- **✅ The `technicalFouls` counter IS surfaced** → **`decisions.md` #033** (done
  2026-08, immediately after §3.14a): `box_score.technical_fouls`, `BoxScoreEntity`,
  and the OpenAPI `BoxScore`. Unparked on a **parity** argument — the twelfth
  accumulator in a set whose other eleven were already exposed. **`committing_team_id`
  stays parked**; #033's argument does not extend to it.
- **Period-/time-aware foul trouble** → #031 C's follow-up, deferred by §3.13.
- **Getting foul-outs below ~0.38** → #031's follow-up. The lever is **measured
  saturated**; none of the three remaining candidates belong to §3.14.
- **Splitting `substitutionAggressiveness`** → #031 B's follow-up.
- **The over-dispersion finding (#031 A)** → #031's follow-up: the same `pickDefender`
  concentration applies to **blocks, steals, and shot contests**.
- **Strategic substitution as a category** → [ideas.md](ideas.md) (parked, not planned).
- **§3.15 (`SimConfig` profiles)** → roadmap.md. The design-pass input is in
  [backlog.md](backlog.md). **Its validation gate is reproducing §3.14b's landing
  exactly** — which is part of why the clamp consolidation was folded into §3.14a
  instead (#032 H).
- **§3.16 (recalibration against verified targets)** → roadmap.md, the last Phase-3
  sub-phase. Owns the contested points/FG% targets. Its **prerequisite is a backlog
  chore**: verify the benchmarks with real sources — including §3.13's unsourced
  foul-out figure and §3.14a's unsourced technicals ballpark (#032 J).
- **Calibration targets** → [calibration.md](calibration.md), **the source of truth**.
  Update it *and* the `CalibrationHarness` `(target ~N)` strings together.
- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star tuning,
  the `decisions.md` condense pass, harness self-verification) → [backlog.md](backlog.md).
- **Untriaged future-improvement ideas** → [ideas.md](ideas.md). *(Includes the parked
  cap 3→5 tuning idea — do NOT touch `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` without its
  own recalibration pass.)*
- **§3.6 seams left open by #024**: play-by-play pagination + a `period` filter; the
  per-event time column's storage shape; a lean header-only `GameResult` projection.
- **§3.7 seams left open by #025**: a skilled-blocker recovery edge; shot-clock pressure
  on block-recovered second-chance possessions (→ §3.9-E, still parked #027 D).
- **§3.9 seams left open by #027**: §3.7-E shot-clock pressure; finer turnover sub-types.
- **§3.10/§3.11 seams left open by #028/#029**: `committing_team_id` is **populated and
  queryable but not surfaced on the OpenAPI `GameEvent`**; the observed off/def
  rebounding-foul split (**78/22**) drifts from the configured 75/25.
- **§3.12 seams left open by #030**: the engine shoots **~20 3PA/team/game against the
  NBA's ~35** — a §3.16 input.
- **✅ THE CLAMP-HELPER CONSOLIDATION IS DONE** — #030 set the trigger (two sites is
  coincidence, a third is the signal); §3.13 added the third; **§3.14a added the fourth
  and folded in the consolidation** (#032 H). `SimConfig.clampRareProbability` now owns
  all four. A fifth floor-free site costs one call, not a fourth copy. *(This note is
  retired — kept one phase for the handoff, delete at §3.14b's close-out.)*
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true -DcalibrationSeed=NNNN`) stays in the `sim` test sources.
  Re-run it after any `SimConfig` change.
