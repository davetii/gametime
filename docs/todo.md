# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.11).

Current focus: **§3.9 — Turnover sub-categories (richer causes)**. The full
§3.7–§3.11 sequence, the calibration-blast-radius ordering, and what follows
(Phase 4) live in **roadmap.md's "Possession-fidelity completion" section** — not
here (todo.md is current-phase-only). §3.7 and §3.8 shipped; §3.9 is next.

> **§3.9 needs a design pass FIRST — it is NOT execute-ready.** Unlike §3.8 (which
> arrived here already resolved as decisions.md #026), §3.9 is currently only a
> **roadmap seam** (roadmap.md §3.9). The seam names the model shape — each new
> turnover cause is a new **`outcome` value on a `TURNOVER` `GameEvent`** produced by
> a **probability roll**, exactly like the existing `BASE_TURNOVER` model (NOT a
> simulated 24-second clock or ball-tracking; do NOT reverse decisions.md #021), so
> **no schema change** (`outcome` is free text, #020) and the turnover *count* is
> unchanged (you're subdividing the ~14 the harness already likes → **free, no
> recalibration**). But the specifics still need deciding and writing up as a new
> decisions.md entry (#027) before execution, the same design-pass → #NNN →
> execute-ready-plan rhythm §3.7/§3.8 followed.

---

## §3.9 design-pass questions to resolve (→ decisions.md #027, then an execute plan here)

Work these into a design pass; each becomes a decision in #027. (The §3.8 verified
facts below are kept as a live map of the `sim` package for whoever picks this up.)

1. **Which sub-categories to model, and their `outcome` strings.** The roadmap lists
   a candidate taxonomy: offensive fouls / charges (`OFFENSIVE_FOUL`), **shot-clock
   violations** (`SHOT_CLOCK_VIOLATION`), bad passes, travels, out-of-bounds,
   3-seconds, 8-second/backcourt. Decide the set to ship now vs. defer, and the exact
   `TURNOVER` `outcome` vocabulary (mirroring the §3.7 `BLOCKED_*` / §3.8
   `OUT_OF_BOUNDS_*` naming discipline). Note `LOST_BALL` today is the catch-all
   unforced bucket — decide what it narrows to.
2. **How each sub-category's probability is scaled.** The seam suggests e.g. a
   shot-clock violation scaling with weak `teamOffense` / low `acumen` / a stalling
   `defensiveScheme`; a charge with the driver's profile. Decide which skills/coach
   attrs (if any) bend each, or whether some are flat base rates — keeping the total
   turnover rate fixed so no recalibration is needed (the "free" premise).
3. **Split mechanics — one roll or nested.** Today `TurnoverResolver.isTurnover`
   gates, then `isStolen` picks `STOLEN` vs `LOST_BALL`. Decide whether the richer
   taxonomy is a weighted draw over sub-causes on the existing turnover branch (the
   likely shape), and confirm it consumes the seeded RNG in a fixed order (determinism).
4. **Cross-refs.** §3.7 Decision E parked "shot-clock pressure on block-recovered /
   second-chance possessions" here (a second-chance possession starts with less clock
   → higher `SHOT_CLOCK_VIOLATION` chance). Decide whether §3.9 picks that up now or
   leaves it a further seam. **`OUT_OF_BOUNDS` as a turnover cause** overlaps §3.8's
   missed-shot OOB — keep them distinct: §3.8 is a missed *shot* leaving the court (a
   `REBOUND`/`OUT_OF_BOUNDS_*` event); a §3.9 turnover-OOB is a *live-ball handling*
   turnover (a `TURNOVER` event, e.g. stepping out, pass out of bounds). Name them so
   they don't read as the same thing.
5. **Harness / reconciliation.** Turnover sub-categories don't add a new box-score
   counter (they subdivide `TURNOVER` events, and `BoxScore.turnovers` already
   reconciles against `TURNOVER` event count). Confirm the existing reconciliation
   still holds and decide whether the `CalibrationHarness` prints a per-cause
   breakdown (visibility, like the §3.8 OOB line) even though the total is unchanged.

Seam (from roadmap): `TurnoverResolver` + `SimConfig`. **No schema, no OpenAPI change.**

---

## Verified facts (the `sim` package map — confirmed against the code 2026-07, post-§3.8)

Paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`.
Trust these; re-check only if the code moved. All engine code is in the `sim/` package.

**The turnover branch to extend — `sim/TurnoverResolver.java` + `sim/PossessionEngine.java`:**
- `PossessionEngine.resolvePossession` `// 1. Turnover check` is the first branch in
  the possession loop: `turnoverResolver.isTurnover(shooter, defense, defensivePressure,
  rng)` gates, then `isStolen(rng)` picks `STOLEN` (credits a stealer) vs `LOST_BALL`,
  emits a `TURNOVER` event naming the ball-loser, and `return sequence + 1`. This is the
  branch §3.9 subdivides.
- `TurnoverResolver` is the `@Component` that owns the rolls; base rates live in
  `SimConfig` (`BASE_TURNOVER`, etc.). New sub-cause weights/rates go in `SimConfig`.

**The §3.7/§3.8 templates to mirror (both shipped in `sim`):**
- **§3.7 blocks** — `ShotResolver.isBlocked` + `BlockResolver` (flat four-way) +
  `BlockRecovery` enum + `PlayerGameState.recordBlock()`: a defensive event as an
  outcome-flavor on the interrupted action + a separate credit accumulator, reusing an
  existing `PlayType`.
- **§3.8 missed-shot OOB** — `MissedShotResolver` (wraps `ReboundResolver`) +
  `MissedShotOutcome` enum: a four-way outcome, OOB reusing `PlayType.REBOUND` with
  `OUT_OF_BOUNDS_*` outcomes crediting no player, excluded from the rebound
  reconciliation. The `capReached`-passed-into-the-resolver tidy lives here.

**Reconciliation precedent — `GameSimulatorIntegrationTest`:** reconciles box-score
counters against event counts (points, rebounds, assists, blocks — #020/#022/#025/#026).
`BoxScore.turnovers` already reconciles against `TURNOVER` event count; §3.9 subdivides
those events without changing the total, so the invariant should hold unchanged — confirm it.

**`test/.../sim/CalibrationHarness.java`:** disabled-by-default (`-Dcalibration=true`),
prints the §3.4 aggregates + §3.5 minutes/period-FG% + §3.7 Blocks line + §3.8 OOB line.
The aggregate-print block is ~lines 219–237. Re-run after any `SimConfig` change; add a
per-cause turnover breakdown line if step 5 decides it's worth the visibility.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md).
- **Deferred gameplay realism** (rebounding fouls, and-1) → **numbered sub-phases
  §3.10–§3.11** in roadmap.md ("Possession-fidelity completion"), scheduled before
  Phase 4. Each is its own design-pass + execution.
- **Untriaged future-improvement ideas** (no phase home, not chores) →
  [ideas.md](ideas.md). *(Includes the parked cap 3→5 tuning idea — do NOT touch the
  `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` value without its own recalibration pass.)*
- **§3.6 seams left open by #024** (carry into Phase 4/7): play-by-play pagination
  + a `period` filter (Decision D); the per-event time column's storage shape
  (Decision E — decided by the Phase 7 game view); a lean header-only `GameResult`
  projection (Decision A).
- **§3.7 seams left open by #025** (carry forward): a skilled-blocker recovery edge
  (Decision D — additive if a consumer ever wants it); shot-clock pressure on
  block-recovered second-chance possessions (Decision E cross-ref → §3.9, item 4 above).
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true`) stays in the `sim` test sources; it reports the §3.4
  aggregates + the §3.5 minutes/period-FG% distributions + the §3.7 blocks line
  + the §3.8 OOB line. Re-run it after any `SimConfig` change.
