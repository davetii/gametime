# WIP — decisions.md #025 (§3.7 Blocked shots) — IN PROGRESS

> **Status: design pass paused mid-stream (2026-07). NOT finalized, NOT built.**
> Scratch pad for the §3.7 blocks design. When Decisions D + E are resolved, this
> becomes decisions.md #025 and this file is deleted. Diagram: `possession-flow.puml`
> (the `BLOCKED?` fork + `BlockResolver`, marked PROPOSED).
>
> **Progress: 3 of 5 decisions resolved (A, B, C). D + E open.**
> Fork shape already chosen: **v3** (one three-way MAKE/MISS/BLOCK draw) — rejecting
> v1 ("relabel a miss"; blocks not a real event) and v2 (a separate block gate before
> the make roll). See roadmap.md §3.7 for the six original open questions.

---

## RESOLVED

### Decision A — three-way split construction: **A1 (block-first)**
Build the MAKE/MISS/BLOCK draw as: carve a small `P(BLOCK)` off the top, then run the
**existing, already-§3.4-calibrated** offense-vs-defense make contest on the remainder:
```
P(BLOCK)  = small, defender-driven, shot-type-scaled
remainder = 1 − P(BLOCK)
P(MAKE)   = remainder × existing make contest (BASE_DRIVE etc., §3.4)
P(MISS)   = remainder − P(MAKE)
```
*Why:* preserves §3.4's calibration (~47% FG / ~112 pts) — blocks only skim a thin
slice, so recalibration is "nudge base rates up to refill the points blocks removed,"
not "recompute FG% from scratch" (which A2, a flat three-way logistic, would force).

### Decision B — `P(BLOCK)` drivers: **B2 (defender + shooter counter-factor)**
The block slice is a **contest**, not a defender-only rate: the defender's rim
disruption vs. the shooter's ability to avoid the swat. Ships in §3.7 (not deferred).
- Same avg-10 logistic-contest shape as every other resolver (#021):
  `p = base + SENSITIVITY × (defenderBlockSkill − shooter finishing) / 10`.
- **Defender block skill** = `rimProtection` (DRIVE/POST) / `shotContest` (PERIMETER).
- **Shooter counter-factor** = `finishing` (a real `PlayerGameState` getter;
  `getShotSkill()` does NOT exist — it's a raw attribute, not a resolver-exposed skill).
- So a great finisher vs. an average rim protector gets blocked less than a scrub —
  offense now touches BOTH the block slice and the make contest (accepted entanglement:
  that IS why a rim protector is felt twice, realistically).
- *Consequence:* two knobs in the thin block slice (defender strength + finisher
  counter-weight) → calibration balances both against the ~5/team target (C).

### Decision C — per-`ShotType` block rates + target: **RESOLVED**
- Block base-rate ordering `DRIVE ≥ POST > PERIMETER ≫ THREE`.
- `THREE` is **very-low, not flat-zero** — real threes get blocked on closeouts
  occasionally; flat-zero would make threes unblockable (an artifact for no benefit).
- **Aggregate target ~5 blocks/team/game** (modern-NBA-ish) — the harness reports
  actual next to it, like FG%/assists today.
- Actual constants (a `BASE_BLOCK_*` per shot type, or a single base × per-type factor)
  are tuned empirically by the harness during execution, same as §3.4's `BASE_DRIVE`.

---

## OPEN — resume here

### Decision D — `BlockResolver` recovery outcomes  ← **NEXT, in progress**
Where the ball goes after a block. Its own block-specific roll (NOT the
`offenseRebound`-vs-`defenseRebound` box-out contest — that distinction is the whole
reason BlockResolver exists).

**D1 — recovery split & lean:** defense-leaning (real blocks favor the blocking team):
`RECOVERED_DEFENSE` (plurality, possession over) > `RECOVERED_OFFENSE` (→ second-chance)
≈ `OUT_OF_BOUNDS`. Exact probabilities tune at calibration; the *ordering* is the
decision. **— not yet confirmed.**

**D2 — what drives it:** flat fixed probabilities (recovery = luck-of-the-swat) vs.
skill-driven (rebounding skill bends the offense/defense split). Lean **flat** — keeps
BlockResolver distinct from ReboundResolver (skill-driven would quietly re-introduce
the rebound contest we said this wasn't). **— not yet confirmed.**

**D3 — the OUT_OF_BOUNDS fork ⚠️ (raised, NOT resolved — currently silently defaulted).**
OOB is not one outcome — **who gets the ball depends on who it went out off of:**
- OOB off the **shooter/offense** → **defense's ball** (possession over)
- OOB off the **blocker/defense** → **offense retains** → second-chance
The diagram + earlier notes collapsed this to "all block-OOB = defense's ball"
(labelled "simplest") — that is basketball-wrong for the hard-swat-into-the-stands case
and needs a real call. Three ways to handle it:
  - **OOB-1 (current default):** all OOB → defense's ball. Simplest; under-credits
    offense second chances. *Not recommended — it's a silent inaccuracy.*
  - **OOB-2:** keep OOB as a distinct outcome, but give it a sub-roll for off-shooter
    (→ defense) vs. off-blocker (→ offense/second-chance). Correct; one extra prob.
  - **OOB-3 (lean):** dissolve OOB entirely — fold off-blocker→`RECOVERED_OFFENSE`,
    off-shooter→`RECOVERED_DEFENSE`, making `BlockResolver` a clean **two-way**
    (offense-ball / defense-ball). Loses the distinct OOB play-by-play label; gains a
    simpler resolver, no accuracy loss.
  **Deciding question:** do we want `OUT_OF_BOUNDS` visible as a distinct play-by-play
  event? No → OOB-3 (two-way). Yes → OOB-2 (three-way with the sub-roll).

### Decision E — offense-recovered-block loop re-entry
When the offense recovers a blocked ball it re-enters the second-chance path (counts
against `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`). Decide whether it **skips**
ReboundResolver's draw (recovery already decided by BlockResolver) or **falls through
it** — must not double-decide who got the ball. **— not yet discussed.**

---

## Calibration note (applies once D + E are built)
Blocks remove would-be-make points → **one recalibration pass** (nudge shot base rates
up to refill). The harness **reports**, does not gate the build. Re-run
`-Dcalibration=true` after wiring and re-agree the numbers, then add the ~5-blocks/team
target line to `CalibrationHarness`.
