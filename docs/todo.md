# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**§3.x Deferred sim-fidelity details**.

Current focus: **Phase 3.5 — Minutes, Fatigue & Substitution** (roadmap.md §3.5).
§3.1 Game Model, §3.2 Possession Engine, §3.3 Rebounding, and §3.4 Team Chemistry
& Coaching Effects are shipped. §3.5 layers a minutes/fatigue/substitution model
onto the existing possession flow: who is on the floor stops being "starters play
the whole game" and starts following the bench `rotationOrder` + the coach's
`rotationDepth` / `substitutionAggressiveness` (the two coach attributes §3.4
deliberately did **not** read).

---

## Phase 3.5 — Minutes, Fatigue & Substitution

**Goal** (roadmap §3.5): produce realistic playing-time distributions and in-game
fatigue. This is *gameplay* state produced by games being played, not roster
state — its input is the `rotationOrder` bench depth chart already shipped in the
roster domain (decisions.md #014) and the two §3.5 coach attributes.

Roadmap §3.5 deliverables:
- Minutes allocation: bench `rotationOrder` → distribution of playing time
- Per-player energy tracking within a game (`endurance` ↔ minutes played)
- Skill degradation as energy drops
- Automatic substitution triggers based on fatigue thresholds
- Coach rotation style determines when subs happen (reads `rotationDepth` /
  `substitutionAggressiveness` — the attrs §3.4 left for §3.5, decisions.md #022 E)

> **No decisions resolved yet.** A §3.5 planning pass (mirror of §3.2/§3.4) should
> surface the design decisions — e.g. how minutes are modeled (continuous clock
> vs. possession-share), how fatigue degrades skills, where the substitution
> trigger lives in the possession loop, and whether `BoxScore.minutes` (currently
> hardcoded 0) becomes real — before building.

### Seams §3.4 leaves for §3.5

- **`PlayerGameState`** carries box-score accumulators per player but no energy/
  minutes field yet; `BoxScore.minutes` is hardcoded 0 in `GameSimulator`.
- **`buildStarters(team)`** in `GameSimulator` is starters-only; §3.5 is where the
  bench (`rotationOrder`) enters the on-floor set.
- **`TeamContext`** (players + `CoachModifiers`) is the value object threaded
  through `PossessionEngine`; a fatigue/minutes model extends what it carries (it
  was built in §3.4 precisely so coach/rotation effects don't re-churn signatures).
- **`CoachModifiers`** reads `pace`/`offensiveScheme`/`defensiveScheme` only;
  `rotationDepth`/`substitutionAggressiveness` are unread and waiting for §3.5.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md).
- **Deferred gameplay realism** (OOB-no-rebound, rebounding fouls, and-1, richer
  turnovers) → roadmap.md's **§3.x Deferred sim-fidelity details**, attached to
  the engine phase that will consume each.
- **§3.4 calibration harness** — `CalibrationHarness` (disabled-by-default) stays
  in the `sim` test sources for re-tuning `SimConfig` whenever rates need a
  refresh; run it with `-Dcalibration=true`.
