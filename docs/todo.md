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

> **§3.9 is now execute-ready — design resolved as decisions.md #027 (A–E).** The
> shape: the turnover **gate is untouched** (`isTurnover` fires exactly as today, same
> `BASE_TURNOVER`, so the turnover *count* never moves); **only once a turnover is
> declared** does a new weighted draw pick *which of 9 causes* it was. This is a pure
> **relabel of an event that already fires** — no new events, no other flow touched —
> so §3.9 is **free by construction** (contrast §3.8, whose freeness had to be
> verified). **No schema change** (`outcome` free text, #020), **no OpenAPI change**.
> Read #027 before executing.

---

## §3.9 execution plan (decisions.md #027 A–E — resolved, ready to build)

Build order. Seam: `TurnoverResolver` + `SimConfig` + `PossessionEngine`'s
`// 1. Turnover check` block + `CalibrationHarness`. **No schema, no OpenAPI, no new
`PlayType`** (all 9 causes are `TURNOVER` `outcome` strings). Mirror the §3.7/§3.8
execution rhythm.

> All Maven commands set `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
> (JDK 21). Per-package coverage gate (80% line, target ~90%) only at
> `mvn -f gametime-service/pom.xml clean install`. Engine work in the `sim` package
> — no OpenAPI/schema change. `CalibrationHarness` is disabled by default
> (`-Dcalibration=true`).

**Step 1 — `TurnoverCause` enum (9 values) + outcome strings (#027 B).**
- [ ] New `sim` enum, one value per cause, each carrying its `TURNOVER` `outcome`
  string: `STOLEN`, `SHOT_CLOCK_VIOLATION`, `OFFENSIVE_FOUL`, `BAD_PASS`,
  `TRAVELLING`, `LOST_BALL_OUT_OF_BOUNDS`, `3_SECONDS_VIOLATION`,
  `8_SECONDS_BACKCOURT_VIOLATION`, `OVER_AND_BACK`.
- [ ] Enum constants can't start with a digit — name them e.g.
  `THREE_SECONDS_VIOLATION` / `EIGHT_SECONDS_BACKCOURT_VIOLATION` and let the
  *outcome string* carry the leading-digit form if desired, or use the letter form
  as the string too (small execution call).
- [ ] `LOST_BALL` is **retired** as the catch-all — its old ~40% share is split
  across the eight non-STOLEN causes (no generic unforced bucket survives).

**Step 2 — `SimConfig` per-cause base weights + four lean sensitivities (#027 B/C).**
- [ ] Static tier base weights (relative magnitudes): **STOLEN dominant (~55–60% of
  the turnover mix — #027 B, keeps `BoxScore.steals` from drifting)**; SHOT_CLOCK /
  OFF_FOUL high; BAD_PASS / TRAVELLING mid; LOST_BALL_OUT_OF_BOUNDS / 3_SECONDS low;
  8_SECONDS_BACKCOURT / OVER_AND_BACK super-low.
- [ ] Modest avg-10 lean sensitivities for the four scaled causes:
  `SHOT_CLOCK_VIOLATION` (ball-handler `acumen` ↓ + defending coach `defensiveScheme`
  ↑), `OFFENSIVE_FOUL` and `BAD_PASS` (offense `teamOffense` ↓).
- [ ] All placeholders — settled by the Step-5 harness line (no hard per-cause
  target). Document them the way the OOB/block weights are documented in `SimConfig`.

**Step 3 — `TurnoverResolver.pickCause(...)` (weighted draw, replaces `isStolen`) (#027 A/C).**
- [ ] New method taking the ball-handler, the defense (for the coach
  `defensiveScheme` / `pickStealer`), and `rng`. Compute each cause's weight (base ×
  its avg-10 lean where applicable), **normalize to 1.0**, and draw one cause from
  the seeded RNG via the cumulative-sum walk the codebase already uses (`pickStealer`
  / `pickShooter`).
- [ ] Remove `isStolen`. The leans shift the *relative* shares only; normalization
  runs per-turnover so **no lean can change the turnover count** (#027 C).

**Step 4 — wire into `PossessionEngine`'s `// 1. Turnover check` (#027 A/B).**
- [ ] Leave the gate call (`turnoverResolver.isTurnover(...)`) **unchanged**.
- [ ] Inside the `if (isTurnover)` block, replace the `isStolen`→`STOLEN`/`LOST_BALL`
  binary with `TurnoverCause cause = turnoverResolver.pickCause(shooter, defense,
  rng);` — if `STOLEN`, keep today's `pickStealer` + `stealer.recordSteal()` path.
- [ ] For **all 9**, `shooter.recordTurnover()` and emit ONE `TURNOVER` event with
  `outcome = cause.outcome()` and `primaryPlayerId = shooter` (unchanged attribution
  — every cause credits the ball-handler, #027 B). Still `return sequence + 1`.
- [ ] Fixed RNG order: `isTurnover` → `pickCause` → (`pickStealer` iff `STOLEN`).

**Step 5 — `CalibrationHarness` per-cause breakdown line (#027 E).**
- [ ] Add a line printing the 9 causes as a share of turnovers (next to the existing
  TO/team aggregate), the §3.8-OOB-line precedent.
- [ ] Run the harness (`-Dcalibration=true`) and **confirm the §3.4/§3.5 aggregates
  (112/47/36/26/14) are byte-unchanged** — they must be (the count is fixed by
  construction); if they move, a wiring bug changed the gate or the event count.
- [ ] Eyeball the mix so no share is absurd (e.g. `OVER_AND_BACK` should be a
  sliver). Tune the Step-2 placeholders only to make the *mix* plausible — **not** to
  hit the aggregates (those are free).

**Step 6 — tests + close-out.**
- [ ] Unit-test `pickCause`: a fixed seed drives a specific cause; weights normalize;
  a strong-`teamOffense`/`acumen`/`defensiveScheme` input shifts the *mix* in the
  expected direction — plus a determinism/count test confirming the **turnover count
  is unchanged** vs. the pre-§3.9 baseline.
- [ ] Confirm `GameSimulatorIntegrationTest`'s reconciliation still passes untouched
  — `Σ BoxScore.turnovers == count(TURNOVER events)` and `Σ BoxScore.steals ==
  count(TURNOVER outcome == 'STOLEN')` hold by construction (#027 E): a
  *confirm-green*, not new reconciliation.
- [ ] New `sim` classes to ~90%+ line coverage (JaCoCo gate), matching §3.7/§3.8;
  full `mvn clean install` gate green.
- [ ] Record the resolved open-at-execution items (below) as an implementation note
  on #027 if any diverged from the plan, and flip roadmap.md's §3.9 bullet to `[x]`
  with the landing summary (the §3.7/§3.8 close-out pattern).

**Reconciliation invariant (holds by construction, #027 E):** §3.9 emits exactly one
`TURNOVER` event per declared turnover, same as today — only the `outcome` string
changes. `BoxScore.turnovers` (vs. `TURNOVER` event count) and `BoxScore.steals`
(vs. `STOLEN` outcome count) are byte-unchanged. No new counter, no new invariant.

**Do NOT (guardrails from #027 / #021):**
- Do **not** touch `isTurnover` / `BASE_TURNOVER` / the turnover gate — the count
  must stay fixed (that is the entire "free" premise, Decision A).
- Do **not** simulate a 24-second clock or track ball position (#021) — every cause,
  including `SHOT_CLOCK_VIOLATION` / `8_SECONDS_BACKCOURT_VIOLATION` / `OVER_AND_BACK`,
  is a **weight in the draw**, not a modeled clock.
- Do **not** pick up §3.7-E (second-chance shot-clock pressure) — parked as a further
  seam (#027 D); a possession-context-dependent weight is the one way this pass could
  disturb the aggregates.
- Do **not** name the live-ball OOB turnover `OUT_OF_BOUNDS` — it must stay
  `LOST_BALL_OUT_OF_BOUNDS` (a `TURNOVER` event), distinct from §3.8's
  `OUT_OF_BOUNDS_*` (a `REBOUND` event) (#027 D).

**Open-at-execution (small, constrained — #027 status):** the exact per-cause
base-weight numbers + the four lean sensitivities (placeholders, settled by the
Step-5 harness line); whether `pickCause` returns the enum or the outcome string; the
enum-constant spelling for the digit-leading causes.

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
