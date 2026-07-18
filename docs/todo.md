# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**§3.x Deferred sim-fidelity details**.

Current focus: **§3.7 — Blocked shots** (roadmap.md §3.7), the first of the
**possession-fidelity completion** sub-phases (§3.7–§3.11) now scheduled **before
Phase 4** (decision 2026-07). Phase 3's engine simulates + persists + exposes a
full game (§3.1–§3.6 shipped), but deliberately omits five possession-level
realism items; we decided each **should be in the game** and belongs to Phase 3
(finish the engine before Phase 4 measures it). Each is its **own numbered
sub-phase with its own design pass** (the §3.4/§3.5/§3.6 three-session workflow),
sequenced by calibration blast radius so scoring-affecting changes land one at a
time against a known-good baseline.

> **Design NOT yet done for §3.7.** No decisions.md entry yet. The roadmap bullet
> is a *seam, not a plan* — and its original "block only on misses" shortcut is
> already **rejected** (a block must prevent a would-be make; see the §3.7 bullet's
> design fork). Next step is the **design pass**, not engine code: resolve the fork
> (block-gate-before-make vs. three-way MAKE/MISS/BLOCK), the double-count split,
> the shooter counter-factor, and the block-rate target into a numbered
> decisions.md entry + an execute-ready plan here.

---

## §3.7–§3.11 sequence (roadmap.md — work in order, one design pass each)

Full seams + design forks live in roadmap.md's "Possession-fidelity completion"
section. Ordering is by calibration cost, NOT roadmap number order:

| # | Item | Recalibration | Notes |
|---|------|---------------|-------|
| **§3.7** | Blocked shots | one pass | The pilot — half-designed already. Block gates the make roll; defensive block skill first-class; watch the double-count trap. |
| **§3.8** | Missed shot OOB (no rebound) | none (free) | Event-log labeling only; possession outcome unchanged. |
| **§3.9** | Richer turnover taxonomy | none (free) | New `outcome` values on `TURNOVER` events (no schema change, #021); turnover count unchanged. |
| **§3.10** | Rebounding / loose-ball fouls | small | **Builds the team-foul/bonus model** (shared with §3.11). |
| **§3.11** | And-1 (foul on a made basket) | biggest | Foul rolls *alongside* the shot; adds bonus-FT points → re-tune scoring. Reuses §3.10's substrate. |

**Cross-cutting:** §3.10 + §3.11 both need a **team-foul / bonus model** (per-team
per-period foul counts → bonus FTs) that doesn't exist yet — designed/built once in
§3.10. The `CalibrationHarness` **reports** (doesn't gate the build); "recalibration"
= re-run `-Dcalibration=true`, re-agree the numbers. Re-run it after every
scoring-affecting item.

### After §3.11 → Phase 4 — Statistics & Box Scores (roadmap.md §4)

Deferred until the engine is complete. Mostly **aggregation** of the per-game
`BoxScoreEntity` rows the engine already persists (#020) — season averages/totals,
team rollups, league leaders — plus stats APIs (`fetchTeam`/§3.6 endpoints are the
copy-me template). Open questions to resolve in *its* design pass: compute-on-read
vs. a materialized season-stats table (#013/#015/#020 discipline); how "season" is
scoped before a schedule/season table exists (Phase 5); leaders/rankings shape +
whether the orphaned `pageNumber`/`pageSize` params (#019) finally get a consumer.

---

> All Maven commands set `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
> (JDK 21 — decisions.md #005; Homebrew JDK 25 breaks Lombok). Per-package coverage
> gate (80% line, target ~90%) only shows at
> `mvn -f gametime-service/pom.xml clean install`.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md).
- **Deferred gameplay realism** (blocks, OOB-no-rebound, richer turnovers,
  rebounding fouls, and-1) → **promoted to numbered sub-phases §3.7–§3.11** in
  roadmap.md ("Possession-fidelity completion"), scheduled before Phase 4. No
  longer a catch-all deferred bucket; each is its own design-pass + execution.
- **Untriaged future-improvement ideas** (no phase home, not chores) →
  [ideas.md](ideas.md).
- **§3.6 seams left open by #024** (carry into Phase 4/7): play-by-play pagination
  + a `period` filter (Decision D); the per-event time column's storage shape
  (Decision E — decided by the Phase 7 game view); a lean header-only `GameResult`
  projection (Decision A).
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true`) stays in the `sim` test sources; it reports the §3.4
  aggregates + the §3.5 minutes/period-FG% distributions. Re-run it after any
  `SimConfig` change.
