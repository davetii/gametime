# Plan — Skill Layer on the 1–20 Scale

Status: proposed (not yet started)
Owner: Dave
Scope: Phase 1 — Player Modeling, skills portion. See [docs/player.md](docs/player.md)
for the design and [docs/TODO.md](docs/TODO.md) for the task list.

---

## Goal

Make all **23 derived skills** correct and consistent on the new **1–20 attribute
scale (average = 10)**, so that skills become a trustworthy, position-fair measure of
player value. This unblocks every downstream "who is good" question (rankings,
rosters, the game engine), none of which can be answered from raw attributes alone.

Target: every skill outputs on **1–20, average ~10**, matching the attribute scale.

---

## Background / Why this is needed

The attribute foundation is done — all 21 attributes are differentiated on a
consistent 1–20 / average-10 scale, CSV-driven, building green. The skill layer is
the next and final piece of player modeling.

Three independent checks this session (top-20 ranking, PG representation, the
"expected stars" calibration) all came back ambiguous for the same reason: **there
is no position-fair way to rank players from raw attributes.** A flat attribute
average buries guards; a guard lens buries bigs. Only the skill layer — where each
skill weights the attributes relevant to that basketball action — measures every
position fairly. That is exactly what this plan delivers.

## Key findings from the existing code (what we're fixing)

1. **Calculator bases already self-scale.** Each calculator's base is a weighted
   *average* of attributes, so on 1–20 data it already yields ~10 for an average
   player. The base layer needs no change.
2. **The bonus layer is broken on the new scale.** Bonuses are large additive
   nudges (e.g. `+5` per attribute >10 in Acumen; `+6` for speed+strength combos in
   IndividualDefense) gated on thresholds like `>9` / `>7` calibrated for a 1–10
   world. On 1–20 data those gates fire for most of the league and stack, pushing
   outputs well past 20.
3. **Combination logic needs separate handling.** Some calculators threshold on the
   *sum* of two attributes (`strength + speed > 18`). That sum was 0–20 on the old
   scale and is 0–40 now — different math than single-attribute thresholds.
4. **`foulProne` is independently broken.** Its `+5` constant inside `/8` breaks the
   "average player = baseline" invariant every other calculator holds, regardless of
   scale.
5. **Tests will nearly all break.** All 13 existing test files assert hardcoded
   values computed under the old scale, and `BASE_PLAYER()` still uses
   `AVERAGE_ATTRIBUTE = 5` and does **not** set the 5 new attributes. Re-tuning
   forces a full test re-baseline.

## Decisions locked in

- **Scale:** skills output 1–20, average ~10 (matches attributes).
- **Architecture:** a single **shared, scale-aware helper** used by all 23
  calculators — not per-calculator manual rescaling, not end-only normalization.
  This keeps all skills consistent and makes the 10 new calculators easy to write
  correctly.
- **Order:** foundation first — fix the helper + existing 13 before/alongside
  building the 10 new ones, so new calculators are written against the corrected
  convention from the start.
- **Execution style:** incremental and verifiable — build the helper + one
  representative calculator first, lock the calibration, then propagate.

---

## Progress log

- **ALL PHASES COMPLETE.** All 23 skill calculators on the 1–20 scale, full reactor
  builds, 84/84 tests pass (was 78). Every skill centers at 10.0 for an average
  player; real-league distributions are sensible (means 9.6–10.3, full 1–20 range).
  Phases B+D and the OpenAPI/fixture prerequisites all landed.
- **Phase A — DONE.** Built the shared helper on `SkillCalculator` and validated it
  on `IndividualDefenseSkillCalculator` (refactored) with a re-baselined test (avg
  player → 10.0; real-data league distribution mean 10.36, full 1–20 range, minimal
  pinning). Helper API: `adj(attr[,factor])` (deviation from 10), `comboAdj(a,b[,factor])`
  (two-attribute, centered at 20), `experienceAdj(yearsPro)`, `clamp(value)`,
  constants `SINGLE_FACTOR=0.18`, `COMBO_FACTOR=0.10`. Pattern: weighted-average base
  (already ~10 on the new scale) + deviation adjustments + `round(clamp(value))`.
- **Sequencing change discovered:** the OpenAPI spec update (5 attrs + 10 skills) and
  the `BASE_PLAYER()` fixture update (AVERAGE_ATTRIBUTE=10 + new attrs) are
  *prerequisites*, not Phase D/E — calculators that read new attributes won't compile
  until the generated `Player` model has them. **Both already done during Phase A.**

## Phases

### Phase A — Shared scale-aware helper — DONE
Build the common math layer all calculators will use.
- Add helper methods (on the `SkillCalculator` interface as defaults, or a small
  `SkillMath` utility):
  - `thresholdAdj(attr, weight)` — single-attribute bonus/penalty, centered at 10,
    sized so a maxed attribute contributes a sane amount (not +5).
  - `comboAdj(sumOfTwo, ...)` — for two-attribute combinations on the 2–40 range.
  - `clampToScale(value)` — final guard so no skill exceeds 20 or drops below 1.
- Calibrate so an **all-10 player → ~10** on every skill, and a maxed player
  approaches but does not blow past 20.
- Validate calibration on ONE representative calculator (Acumen or
  IndividualDefense) before propagating.

### Phase B — Fix `foulProne` design
- Redesign so average → baseline (~10) like every other skill, using the Phase A
  helper convention. Removes the `+5`/`÷8` invariant break.

### Phase C — Refactor the existing 13 calculators
- Convert each to use the shared helper, preserving each skill's **character**
  (same primary inputs, same relative emphasis) but on the corrected 1–20 scale.
- Calculators: acumen, ballSecurity, passing, teamOffense, drive, freeThrows,
  longRange, perimeter, post, individualDefense, teamDefense, offenseRebound,
  defenseRebound.
- Fold in the `health` attribute where the design calls for it (individualDefense,
  defenseRebound) — closes a standing TODO item.
- Incorporate new attributes into existing calculators where the design suggests
  (e.g. drive ← verticality; offenseRebound ← verticality/wingspan).

### Phase D — Build the 10 new calculators + wire them
- New calculators (all via the shared helper): finishing, transition, rimProtection,
  stealing, shotContest, foulDrawing, foulProne, clutch, screenSetting,
  offBallMovement.
- Register each in `SkillMapper` (`@Autowired` field + `skills.setXxx(...)`).
- Add the 5 new attributes + 10 new skills to the OpenAPI spec
  (`gametime-api/yml/gametime.yaml`), regenerate stubs (`mvn clean compile`).

### Phase E — Tests + verification
- Update `BASE_PLAYER()`: `AVERAGE_ATTRIBUTE = 10`; set the 5 new attributes.
- Re-baseline all 13 existing test files to new expected values on the 1–20 scale.
- Write unit tests for the 10 new calculators (follow the existing per-calculator
  pattern; average player → ~10).
- Integration test: full player GET returns all 23 skills.
- `JAVA_HOME=...21 mvn clean test` green.

---

## Out of scope (deferred)

- Hand-tuning marquee/star players to 18–20 (attribute data task).
- The game simulation engine (Phase 3) — skills feed it but are not it.
- Roster/lineup logic (Phase 2).

## Affected areas (reference)

- `gametime-app/.../mapper/SkillCalculator.java` (helper)
- `gametime-app/.../mapper/*SkillCalculator.java` (13 existing + 10 new)
- `gametime-app/.../mapper/SkillMapper.java` (wiring)
- `gametime-api/yml/gametime.yaml` (spec: 5 attrs + 10 skills)
- `gametime-app/src/test/.../mapper/*` (base fixture + all calculator tests)
