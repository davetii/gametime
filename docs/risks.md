# Risks & Concerns

Active risks and concerns only. Delete an item when it's resolved — git history
records when/why, and the substantive resolution lives in the relevant
decisions.md entry. Keep every line here a *live* concern.

---

### Simulation performance at scale
**Severity**: Medium  
**Description**: A full season is potentially 40 teams x 40+ games = 800+ games. Each game is ~200 possessions producing ~200–350 `GameEvent` rows (all persisted, decisions.md #020), so a season is 160,000+ possessions to compute and hundreds of thousands of event rows to write. The live concern is **season-scale batch throughput** — computing + persisting that volume across 800 games, arriving with **Phase 5 (season simulation)**. **Note (updated §3.6):** the *single-game* read path is settled and is NOT a concern — #024 D counted the actual event volume (~200–350/game, well under ~100KB JSON) and shipped `GET /{gameId}/play-by-play` **unpaginated by design**, retiring the earlier #019 "large result set / paginate at §3.6" worry for this endpoint. Pagination + a `period` filter remain a clean additive option if a future *season-log* or multi-game read ever needs it (#024 D seam), but that is not this risk.  
**Mitigation**: Benchmark the season batch when Phase 5 runs many games; keep the pure simulation side-effect-free (decisions.md #021) so batch/season runs can persist efficiently (bulk inserts, streamed writes). The per-game read endpoint needs no bounding at current scale.

### Skill formula balance
**Severity**: Low *(was Medium — reduced after §3.4)*  
**Description**: The skill calculators have hand-tuned threshold values. The blanket "we won't know until games are simulated" trigger has **fired**: §3.4's `CalibrationHarness` ran ~100 games and the **team-level aggregates land on target** (~112 pts / 47% FG / 36% 3P / 26 ast / 14 TO, decisions.md #022; held under §3.5 fatigue, #023). What remains unvalidated is narrower: **individual-player edge cases** (e.g. a maxed shotSkill + shotSelection player being unrealistically dominant) — the aggregate mean can be right while the tails are off.  
**Mitigation**: The harness already guards the aggregates — re-run it after any `SimConfig`/formula change (see the calibration-drift risk below). For the tails, add a per-player stat-distribution check (min/max/outlier box scores) when Phase 4 stats make individual lines easy to review; consider a normalization step only if a real outlier appears.

### Calibration drift is unguarded (harness reports, does not gate)
**Severity**: **Medium–High** *(raised 2026-08 — the engine is currently off-target **by design**, see below)*  
**Description**: The `CalibrationHarness` is **disabled by default** (`-Dcalibration=true`) and **reports** the aggregates — it does **not** fail the build. So any change to `SimConfig` base rates, a skill formula, or the possession flow can silently skew the game away from the targets in [calibration.md](calibration.md) and **nothing in CI catches it**. This is acute across the **§3.7–§3.16 sub-phases**: six of them (§3.7 blocks, §3.10 rebounding fouls, §3.11 and-1, §3.12 all-shot-type contact, §3.13 foul trouble, §3.14 flagrants) deliberately shift scoring and require a manual recalibration pass each — a forgotten or sloppy re-tune leaves the engine quietly miscalibrated, and the next sub-phase then tunes against a bad baseline (the exact "moving-target" failure the calibration-blast-radius sequencing exists to avoid).

**RESOLVED DIFFERENTLY THAN EXPECTED (2026-08, §3.12 execution) — the "deferred debt" framing was wrong, and the TARGET is the live problem.** This entry previously tracked a 3.9-point debt (points 115.9 vs. ~112) deferred from §3.11 for §3.12 to absorb, and named the failure mode as "§3.12 forgets to absorb it." §3.12 did not forget — **it found the premise unsound**, and the risk has changed shape rather than closed:

- **The re-centering was NOT taken, deliberately.** §3.12 landed **117.0 pts / 46.4% FG / 36.7% 3P** (5 seeds). Reaching ~112 would have cost **~2.5 points of FG%** (measured exchange rate on §3.12's own numbers: ~0.50% FG% per point), dragging FG% to ~43.9% against a ~47% target. #030 E's own stop condition fired.
- **#030 E's "free lever" was nearly weightless.** The new perimeter/three multipliers move points by only **~0.3 across their entire defensible range** (measured by sweep), so the cheapest-first order that was meant to make this re-centering nearly costless had almost nothing to spend.
- **The §3.4 TARGETS themselves are now contested.** Points (~112) and FG% (~47%) were set in #022 D from unsourced estimates and never revisited; current figures suggest **points ~114–117** and **FG% ~47–48**. Against a ~115 anchor, §3.11's "debt" was largely an artifact of a stale target.
- **The two contested targets cannot be reconciled by the available lever.** Shot `BASE_*` moves points and FG% **the same direction**, so points-too-high wants a trim while FG%-too-low wants a raise. This needs a lever separating **efficiency from volume** — a design question, not a knob turn.

**Where it now lives:** [`calibration.md`](calibration.md) is the **source of truth for targets** (created §3.12, superseding #022 D), with both contested rows flagged; the re-solve is **§3.16**, the last Phase-3 sub-phase, sequenced after §3.13/§3.14 because both move scoring. Its prerequisite — **verifying the benchmarks with sources** — is a backlog chore and is not engine work.

**The sharpened risk, and the reason this entry stays open:** **three consecutive passes have now declined the same `BASE_*` trim** (§3.10 stopped at 113.8, §3.11 took none, §3.12 took none), each because it cost more calibrated FG% than the points miss was worth. Three passes rejecting one lever is evidence about the **target**, not about the passes — but until §3.16 runs, the engine sits ~2 above a contested points target and ~0.6–1.6 below a contested FG% target, with **no verified benchmark to judge either against**. The drift-vs-intent distinction this entry exists to protect is now genuinely ambiguous, which is worse than a known gap.  
**Mitigation**: Re-run `-Dcalibration=true` after **every** scoring-affecting change and re-agree the aggregates with the user before moving on (baked into each §3.x execution plan's calibrate step). §3.11 added **`-DcalibrationSeed=NNNN`** so a config can be observed across several seeds and tuned to the **mean** — a single run carries enough per-seed noise (±1.5 pts observed) to bait an over-correction; use it. Longer-term option: a lightweight, always-on assertion test that fails if a small fixed-seed batch drifts outside a tolerance band around the targets — converting the manual report into a real gate. **Reconsidered at §3.12's close-out (2026-08) and deferred again, for a NEW reason**: a tolerance band needs an expected value, and the expected value is exactly what is now in dispute (see above). Setting a band around a contested target would harden the wrong number into CI. **Revisit after §3.16**, once the targets are verified — at which point a band becomes genuinely useful rather than premature.

### H2/Postgres divergence risk
**Severity**: Low  
**Description**: Tests run on H2, production on Postgres. As the schema grows (game events, stats tables, potentially JSON columns), the gap between H2 and Postgres behavior could cause test-passes-but-prod-fails scenarios.  
**Mitigation**: Consider Testcontainers for integration tests if H2 divergence becomes a problem. Keep Liquibase changesets simple.

### Seed data realism
**Severity**: Low  
**Description**: The ~420 pre-loaded players have manually-assigned attributes. Partially validated: §3.4's calibration (~100 games) shows the seed roster produces **realistic team-level outcomes** in aggregate (see Skill formula balance). Still open: whether the *attribute distributions themselves* (vs. the formulas on top of them) are realistic at the individual level — same tails concern as above, hard to separate from formula balance until Phase 4 surfaces per-player season lines.  
**Mitigation**: Compare per-player stat distributions to real-basketball benchmarks once Phase 4 stats exist; adjust seed data or formulas then. Related backlog.md chore: hand-tuning marquee/star players to 18–20 (backlog.md).
