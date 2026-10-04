# Risks & Concerns

Active risks and concerns only. Delete an item when it is resolved; git history records
when and why. Keep every line here a live concern.

---

### Simulation performance at scale
**Severity**: Medium
**Description**: A full season is potentially 40 teams × 40+ games = 800+ games. Each game is
~200 possessions producing ~200–350 persisted `GameEvent` rows, so a season is 160,000+
possessions to compute and hundreds of thousands of event rows to write. The live concern is
season-scale batch throughput, arriving with Phase 5. The single-game read path is not a
concern: `GET /{gameId}/play-by-play` is unpaginated by design at current volume.
**Mitigation**: Benchmark the season batch when Phase 5 runs many games. Keep the pure
simulation side-effect-free so batch runs can persist efficiently (bulk inserts, streamed
writes). Pagination plus a `period` filter remains an additive option if a multi-game read
ever needs it.

### Skill formula balance
**Severity**: Low
**Description**: The skill calculators have hand-tuned thresholds. Team-level aggregates land
on the sourced targets, so the formulas produce coherent team numbers. What is unvalidated is
**individual-player edge cases**: a maxed `shotSkill` + `shotSelection` player could be
unrealistically dominant while the aggregate mean stays right.
**Mitigation**: Re-run the harness after any `SimConfig` or formula change. When Phase 4 makes
individual lines easy to review, add a per-player distribution check (min/max/outlier box
scores). Consider a normalization step only if a real outlier appears.

### Calibration drift is unguarded (the harness reports, it does not gate)
**Severity**: Medium–High
**Description**: `CalibrationHarness` is disabled by default (`-Dcalibration=true`) and
reports the aggregates; it does not fail the build. Any change to `SimConfig` base rates, a
skill formula or the possession flow can silently skew the engine away from the targets in
[calibration.md](calibration.md), and nothing in CI catches it. The ways a number can move or
stay put without the engine being right:
- **A constant is priced by another number.** `sim.non-shooting-foul-share` sets how many
  fouls award no free throws, but a converted foul awards 2 bonus FTs inside the penalty and
  none outside it, so its effect depends on the penalty rate. A pass that moves the foul or
  shot rate silently mispriced it without touching it. When a phase changes the conditions a
  constant was solved under, re-solving is part of that phase.
- **A mechanic change breaks an instrument.** The harness once inferred "stopped three" from
  a 3-FT count, which stopped working when a foul kind awarded 0 or 2 FTs. The engine was
  unaffected; the measurement lost its signal. No test asserts an instrument's meaning, and
  fixing an instrument moves its numbers (a falling number can look like it doubled). Never
  compare a corrected number to an uncorrected one.
- **A clamp does what a constant appears to do.** `PROB_FLOOR` (0.02) is 4× `base-block-three`
  (0.005), so a three's block chance is floored, not based, and that constant is inert. The
  tell is a number that does not move. A green row is not evidence a mechanism is right: check
  that the number is produced by what you think produces it.
- **A sourced target can be unreachable.** A verified benchmark can land outside what any
  existing knob delivers (the foul-trouble sit curve is saturated). The response is a new
  sub-phase, not a harder tune.
- **Rare-event rows cannot be read from one seed.** Technicals and flagrants need 5 seeds;
  flagrants at 5 seeds still carry a relative sd near 8%. Absence of aggregate movement cannot
  distinguish "the mechanic works" from "the mechanic never fires", so each such pass needs a
  dedicated count line and tests pinning its named bug signatures.

**Mitigation**: Re-run the harness after every scoring-affecting change and agree the
aggregates before moving on. Use `-DcalibrationSeed=NNNN` and tune to the mean of several
seeds, since a single run carries about ±1.5 points of per-seed noise. Each pass should write
down what it expects to move and by how much, so intended movement is distinguishable from
drift. A lightweight always-on test that fails if a fixed-seed batch leaves a tolerance band
around the targets would turn the report into a gate; targets are now sourced and the shape is
final, so it is viable but not built.

### H2/Postgres divergence
**Severity**: Low
**Description**: Tests run on H2, production on Postgres. As the schema grows (events, stats
tables, possibly JSON columns), behavior gaps could cause tests that pass and production that
fails.
**Mitigation**: Consider Testcontainers if the divergence bites. Keep Liquibase changesets
simple.

### Seed data realism
**Severity**: Low
**Description**: The ~420 pre-loaded players have manually assigned attributes. Aggregate
team outcomes are realistic. Still open: whether the attribute distributions themselves are
realistic at the individual level, which is hard to separate from formula balance until
Phase 4 surfaces per-player season lines.
**Mitigation**: Compare per-player stat distributions to real benchmarks once Phase 4 stats
exist; adjust seed data or formulas then. Related chore in beads: `gametime-9up` (hand-tune
marquee players to 18–20).

---

## Stats are written twice, and the two paths agree only by convention

Every stat is recorded by two independent mechanisms:

| Path | What it is |
|---|---|
| `PlayerGameState.record*()` | counters incremented as the possession runs |
| `data.addEvent(...)` | the `GameEvent` emitted for the same play |

Nothing structurally guarantees they agree. They agree because each call site remembers to do
both, which is a convention, not an invariant. The harness carries four reconciliation
identities (assists+blocks, FT sources, points, rebound pool) whose entire purpose is to catch
the two disagreeing; in a derived model they would be tautologies, so their existence measures
this risk. A box score derived from the event log would have one write path instead of two.

**The blocker is `minutes`**: it has no event behind it (it is a possession-share projection),
so full derivation means either emitting substitution or possession events, or keeping minutes
as the one deliberately non-derived field.

**The sharpest consumer is single-game summarization**, not leaderboards: a box score rendered
beside the play-by-play makes any disagreement between the two visible to the user.

**Status**: not scheduled and not a gate. Tracked in beads as `gametime-fwy`
(`BoxScoreReconciler`); Phase 4's design pass should see it, since it would already be
touching every stat path. It is an architecture call, not a chore.

---

## Skill sensitivity is ~10× too steep, and every calibration pass so far was blind to it

*(Measured 2026-08 by a throwaway probe, since deleted.)*

**The risk.** The engine's contests respond to player skill roughly an order of magnitude more
steeply than real basketball. Holding an average offense fixed and varying only the defense's
skill from 4 to 16, opponent FG% swings **79.8% → 16.2%**, a 63-point spread against a real
NBA team-defense spread of about 5 points. Forced turnovers swing 2.3 → 36.9 over the same
range.

**Why it has never been caught is structural, and it is the actual risk:** `CalibrationHarness`
runs `teamOf5(id, 10)`, average against average. Every target in `calibration.md` is measured
at a single point at the midpoint of the response curve, where the numbers are correct. Every
calibration pass has tuned the intercept and none has tested the slope. A green harness says
nothing about it.

**Why it is latent.** Nothing today consumes the slope, since games are simulated between
rosters the harness makes identical. **Phase 5 will expose it**: season play puts real skill
spread against real spread, good teams beat bad teams by impossible margins, and the symptom
looks like a standings or scheduling bug that does not point back here.

**Mitigation.** It is a tuning problem, not a rebuild: the suspects are the global
`SimConfig.SENSITIVITY` (0.5) and the per-contest sensitivities. It needs an **instrument
first**, a harness mode that runs a skill ladder and reports the response curve, because the
current report cannot display a slope. Do not re-tune against the average-vs-average rows:
they are landed and flattening the slope would not move them.

**Status**: not scheduled, not a gate on Phase 3, and it **should** be a gate on Phase 5.
`roadmap.md`'s Phase 5 carries a pointer so its design pass sees this before standings exist
to be confused by.
