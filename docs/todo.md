# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.11).

Current focus: **§3.8 — Missed shot out of bounds (no rebound)**. The full
§3.7–§3.11 sequence, the calibration-blast-radius ordering, and what follows
(Phase 4) live in **roadmap.md's "Possession-fidelity completion" section** — not
here (todo.md is current-phase-only). §3.7 shipped; this file now holds the §3.8
design-pass kickoff below.

> **§3.7 — Blocked shots: SHIPPED** (decisions.md #025 + its 2026-07 implementation
> note). A blocked shot is now a first-class `SHOT`/`BLOCKED_*` event + a
> `recordBlock()` credit (mirroring steals); a three-way MAKE/MISS/BLOCK draw with
> `P(BLOCK)` carved off the top via a defender-vs-finisher contest (own
> `BLOCK_SENSITIVITY`), a flat four-way `BlockResolver` loose-ball recovery, and
> `BoxScore.blocks` real (was hardcoded 0). Recalibrated: **blocks 4.8/team (~5)**,
> §3.4/§3.5 aggregates recentered (112.9 / 47.2% / 36.0% / 27.1 / 13.5). New/changed
> `sim` classes at 99.4–100% line coverage. Flow: `possession-flow.puml`.

---

## §3.8 — Missed shot out of bounds (no rebound): DESIGN PASS NEEDED

**Do the design pass first — do NOT jump to code.** §3.7 proved the roadmap
one-liner under-specifies the real design (its "block only on misses" shortcut was
rejected). Follow the same three-session workflow the §3.4/§3.5/§3.6/§3.7 passes
used: **(1)** a design pass resolving the open questions into a numbered
`decisions.md` entry (#026), **(2)** an execute-ready, file-pathed plan rewritten
here, **(3)** execution against that plan.

**The seam (from roadmap.md §3.8 — a *seam, not a plan*).** A missed shot that
sails OOB untouched, or a rebound tipped OOB, ends the possession the same as a
defensive rebound — today folded into the `REBOUND — DEFENSIVE` outcome. The
sketch is: add it as a third branch off the rebound roll (retained inbound vs lost
inbound). **Pure event-log labeling** — the possession *outcome* is unchanged
(possession still ends / still continues), so the `CalibrationHarness` totals must
not move. This is the reason §3.8 is sequenced as a *free, no-recalibration* item.

**Open questions for the design pass to resolve (not yet decided):**

- **Event representation.** Is an OOB miss a new `outcome` on the existing
  `REBOUND` event (e.g. `REBOUND` / `OUT_OF_BOUNDS_DEFENSE` / `OUT_OF_BOUNDS_OFFENSE`),
  a new `outcome` on the `SHOT` event, or something else? (Mirror the #025 F
  discipline — reuse an existing `PlayType`, open-ended `outcome` free text, #020,
  so **no schema change**.) Note the §3.7 `BlockResolver` already models OOB for
  *blocked* balls (`OOB_OFFENSE`/`OOB_DEFENSE`); decide whether §3.8 reuses that
  vocabulary/pattern for *missed-shot* OOB or stays distinct.
- **Who/what decides it, and where.** Is OOB a pre-rebound roll (the ball goes OOB
  before any rebounder can touch it) or a branch *within* the rebound roll (a
  contested board tipped OOB)? Which resolver owns it — `ReboundResolver` gets a
  third branch, or a new seam?
- **Retention split.** OOB off the defense keeps the ball with the offense (a
  second-chance possession, like an offensive rebound → must respect
  `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`); OOB off the offense ends the
  possession (like a defensive rebound). Fixed weights, or skill-influenced? Keep it
  flat unless there's a real basketball reason otherwise (cf. #025 D's flat
  loose-ball recovery rationale).
- **Rebounder credit.** A ball that goes OOB untouched credits **no** rebound (it's
  not a rebound) — confirm the box-score/reconciliation consequence (the existing
  rebound reconciliation `count(REBOUND OFFENSIVE/DEFENSIVE) == Σ box rebounds` must
  still hold, so an OOB outcome must NOT be counted as a rebound).
- **Harness invariance.** Confirm (and assert) the design leaves the §3.4/§3.5
  aggregates untouched — §3.8 is labeling, not scoring. No recalibration pass.

**Verified facts to re-confirm at design time** (they moved with §3.7 — re-check
line anchors against the code before trusting them):

- `sim/PossessionEngine.java` `// 4. Rebound` branch — the OOB branch most likely
  attaches here (or just before it). Note the §3.7 block fork now sits at the top
  of `// 3. Shot`; the rebound branch is downstream of it.
- `sim/ReboundResolver.java` — the `@Component` that would grow the third branch.
- `sim/GameData.java` `addEvent(...)` — the 7-arg event emitter (OOB carries no
  assist, so 7-arg like a rebound/block).
- `docs/game.md` `play_type`/`outcome` vocabulary table + the possession-flow prose
  — the close-out doc targets (add the OOB `outcome` rows there, as §3.7 added the
  `BLOCKED_*` rows).

---

> All Maven commands set `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
> (JDK 21 — decisions.md #005; Homebrew JDK 25 breaks Lombok). Per-package coverage
> gate (80% line, target ~90%) only shows at
> `mvn -f gametime-service/pom.xml clean install`. The `CalibrationHarness` is
> disabled by default; run with `-Dcalibration=true`.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md).
- **Deferred gameplay realism** (OOB-no-rebound, richer turnovers, rebounding
  fouls, and-1) → **numbered sub-phases §3.8–§3.11** in roadmap.md
  ("Possession-fidelity completion"), scheduled before Phase 4. Each is its own
  design-pass + execution.
- **Untriaged future-improvement ideas** (no phase home, not chores) →
  [ideas.md](ideas.md).
- **§3.6 seams left open by #024** (carry into Phase 4/7): play-by-play pagination
  + a `period` filter (Decision D); the per-event time column's storage shape
  (Decision E — decided by the Phase 7 game view); a lean header-only `GameResult`
  projection (Decision A).
- **§3.7 seams left open by #025** (carry forward): a skilled-blocker recovery edge
  (Decision D — additive if a consumer ever wants it); shot-clock pressure on
  block-recovered second-chance possessions (Decision E cross-ref → §3.9).
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true`) stays in the `sim` test sources; it reports the §3.4
  aggregates + the §3.5 minutes/period-FG% distributions + the §3.7 blocks line.
  Re-run it after any `SimConfig` change.
