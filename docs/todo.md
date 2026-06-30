# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**§3.x Deferred sim-fidelity details**.

Current focus: **Phase 3.4 — Team Chemistry & Coaching Effects** (roadmap.md
§3.4). §3.1 Game Model, §3.2 Possession Engine, and §3.3 Rebounding are shipped;
§3.4 layers team/coach modifiers onto the existing possession + rebound flow and
calibrates the placeholder `SimConfig` constants empirically.

---

## Phase 3.4 — Team Chemistry & Coaching Effects

> The detailed task breakdown is written at the start of the §3.4 session
> (mirroring how §3.2/§3.3 were planned). The roadmap §3.4 deliverables are the
> starting scope:
> - `teamOffense`/`teamDefense` affect overall team efficiency
> - `passing` skill influences assist rate and ball movement (assists are still
>   0 from §3.2 — this is where they become real)
> - `acumen` influences shot-selection quality
> - Coach system modifies possession pace, shot distribution, defensive scheme
>   (the continuous coach attributes from decisions.md #018 are the input)
> - Rebalance skill-calculator formulas + the `SimConfig` base rates now that
>   possessions (and rebounds) exercise them — this is the empirical calibration
>   pass §3.2/§3.3 deferred (clutch/foulProne/transition, `BASE_OFFENSIVE_REBOUND`,
>   shot base rates, etc.)
>
> Seams already left for §3.4: `SimConfig` holds all constants in one place; the
> rebound model uses raw skills with room for a coach modifier multiplier; shot
> selection is a weighted draw that a coach shot-distribution modifier can bend.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md).
- **Deferred gameplay realism** (OOB-no-rebound, rebounding fouls, and-1, richer
  turnovers) → roadmap.md's **§3.x Deferred sim-fidelity details**, attached to
  the engine phase that will consume each.
