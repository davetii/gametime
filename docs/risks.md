# Risks & Concerns

Active risks and concerns. Remove items as they're resolved (move to the Resolved section at the bottom).

---

## Active

### Simulation performance at scale
**Severity**: Medium  
**Description**: A full season is potentially 40 teams x 40+ games = 800+ games. Each game is ~200 possessions producing ~200–350 `GameEvent` rows (all persisted, decisions.md #020), so a season is 160,000+ possessions to compute and hundreds of thousands of event rows to write. The live concern is **season-scale batch throughput** — computing + persisting that volume across 800 games, arriving with **Phase 5 (season simulation)**. **Note (updated §3.6):** the *single-game* read path is settled and is NOT a concern — #024 D counted the actual event volume (~200–350/game, well under ~100KB JSON) and shipped `GET /{gameId}/play-by-play` **unpaginated by design**, retiring the earlier #019 "large result set / paginate at §3.6" worry for this endpoint. Pagination + a `period` filter remain a clean additive option if a future *season-log* or multi-game read ever needs it (#024 D seam), but that is not this risk.  
**Mitigation**: Benchmark the season batch when Phase 5 runs many games; keep the pure simulation side-effect-free (decisions.md #021) so batch/season runs can persist efficiently (bulk inserts, streamed writes). The per-game read endpoint needs no bounding at current scale.

### Skill formula balance
**Severity**: Low *(was Medium — reduced after §3.4)*  
**Description**: The skill calculators have hand-tuned threshold values. The blanket "we won't know until games are simulated" trigger has **fired**: §3.4's `CalibrationHarness` ran ~100 games and the **team-level aggregates land on target** (~112 pts / 47% FG / 36% 3P / 26 ast / 14 TO, decisions.md #022; held under §3.5 fatigue, #023). What remains unvalidated is narrower: **individual-player edge cases** (e.g. a maxed shotSkill + shotSelection player being unrealistically dominant) — the aggregate mean can be right while the tails are off.  
**Mitigation**: The harness already guards the aggregates — re-run it after any `SimConfig`/formula change (see the calibration-drift risk below). For the tails, add a per-player stat-distribution check (min/max/outlier box scores) when Phase 4 stats make individual lines easy to review; consider a normalization step only if a real outlier appears.

### Calibration drift is unguarded (harness reports, does not gate)
**Severity**: Medium  
**Description**: The `CalibrationHarness` is **disabled by default** (`-Dcalibration=true`) and **reports** the aggregates — it does **not** fail the build. So any change to `SimConfig` base rates, a skill formula, or the possession flow can silently skew the game away from the ~112/47/36/26/14 targets and **nothing in CI catches it**. This becomes acute in the **§3.7–§3.11 sub-phases**: three of them (§3.7 blocks, §3.10 rebounding fouls, §3.11 and-1) deliberately shift scoring and require a manual recalibration pass each — a forgotten or sloppy re-tune leaves the engine quietly miscalibrated, and the next sub-phase then tunes against a bad baseline (the exact "moving-target" failure the calibration-blast-radius sequencing exists to avoid).  
**Mitigation**: Re-run `-Dcalibration=true` after **every** scoring-affecting change and re-agree the aggregates with the user before moving on (baked into each §3.x execution plan's calibrate step, e.g. todo.md §3.7 step 7). Longer-term option: a lightweight, always-on assertion test that fails if a small fixed-seed batch drifts outside a tolerance band around the targets — converting the manual report into a real gate. Deferred until the §3.x recalibration cadence proves the manual step insufficient.

### H2/Postgres divergence risk
**Severity**: Low  
**Description**: Tests run on H2, production on Postgres. As the schema grows (game events, stats tables, potentially JSON columns), the gap between H2 and Postgres behavior could cause test-passes-but-prod-fails scenarios.  
**Mitigation**: Consider Testcontainers for integration tests if H2 divergence becomes a problem. Keep Liquibase changesets simple.

### Seed data realism
**Severity**: Low  
**Description**: The ~420 pre-loaded players have manually-assigned attributes. Partially validated: §3.4's calibration (~100 games) shows the seed roster produces **realistic team-level outcomes** in aggregate (see Skill formula balance). Still open: whether the *attribute distributions themselves* (vs. the formulas on top of them) are realistic at the individual level — same tails concern as above, hard to separate from formula balance until Phase 4 surfaces per-player season lines.  
**Mitigation**: Compare per-player stat distributions to real-basketball benchmarks once Phase 4 stats exist; adjust seed data or formulas then. Related backlog.md chore: hand-tuning marquee/star players to 18–20 (backlog.md).

---

## Resolved

### Health attribute unused
**Resolved**: 2026-06 — `health` is now consumed by the `individualDefense` and `defenseRebound` skill calculators (wired in during the 1–20 skills pass). No longer an orphaned attribute.

### No roster/trade history (player→team was current-state only)
**Resolved**: 2026-06 — Built the `player_team` (current) + `player_team_hist` (append-only) model; `player.team_id` removed. See decisions.md #012. History is now first-class: `addPlayerToTeam` appends a transaction row and `GET /v1/player/{id}/history` exposes it. (A normalized multi-player-trade `transaction` table is still a future option but not needed until trades exist.)

### Coach attribute design was undefined (Phase 3 dependency)
**Resolved**: 2026-06-28 — Coach is no longer name-only. Design Decision #3 resolved as decisions.md **#018** (continuous 1–20/avg-10, not enums); 5 decision attributes (`pace`, `offensiveScheme`, `defensiveScheme`, `rotationDepth`, `substitutionAggressiveness`) modeled end-to-end and seeded. The Phase 3 engine now has a defined interface to read. **GM attributes** remain name-only but are *not* a Phase 3 blocker — their consumers are Phase 6.3/6.4, to be modeled with the same continuous approach (roadmap.md Deferred + §6.4; coach.md open-Q #3).

*Move resolved items here with a note on the resolution.*

<!-- Example:
### Item title
**Resolved**: 2026-05 — Description of resolution.
-->
