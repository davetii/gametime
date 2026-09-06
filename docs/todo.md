# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section.

Current focus: **NONE — Phase 3 is complete.** ✅ **§3.22 (the putback) shipped as
[decisions.md](decisions.md) #044**, and it was the **last engine sub-phase**. §3.7–§3.22
have all shipped.

> ⚠ **THERE IS NO CURRENT PHASE, AND THIS FILE IS DELIBERATELY EMPTY OF ONE.**
> What comes next is **not** a sub-phase: it is the **[roadmap.md](roadmap.md) *Phase 3 →
> Phase 4 pre-work* gate** — eight ordered items (todo.md rewrite, condense
> `decisions.md` + the Java-comment sweep as ONE pass, split `possession-flow.puml`,
> `game.md` dedup, backlog triage, adopt Beads, re-read the calibration verdicts).
> **That section is the authority on sequence and constraints** — it is a GATE, not
> Phase 4's first task, and Phase 4's design pass does not start until it is done.
>
> **This file is rewritten when a phase starts.** Phase 4's design pass owns the next
> rewrite; until then the routing table and standing facts below are the only live
> content here.

---

## §3.22 — the putback (✅ SHIPPED, #044)

Landed 2026-09 with **no divergence** from the design. `pickShooter` takes the offensive
rebounder as a **parameter** and doubles his `offensiveWeight` for that one draw; a
putback make is assisted at **half** the ordinary chance. Two constants — `sim.offensive-
rebounder-shot-weight = 2.0` (tunable) and `OFFENSIVE_REBOUNDER_ASSIST_LEAN = 0.5`
(static) — taking the count to **63 / 29**; `sim.base-no-basket-foul` 0.1753 → **0.178**
re-landed FGA at **89.40**.

⚠ **Two results worth carrying forward, both recorded in #044's implementation note:**
- **The design run reproduced to the DECIMAL** (Points 115.700, FG% 47.020, FGA 89.600,
  Assists 27.000, Fouls 19.220). When a design pass has already run a mechanic in a
  temporary tree, an **exact** match is the verification — a near-match means a
  divergence to find, not a row to tune.
- **All 167 seeded sim tests passed UNCHANGED** though the RNG stream moved — the first
  phase to collect on the instrumentation pass's batch-invariant rewrite.

**The full landing, the realized putback shares, and the open residuals live in
[decisions.md](decisions.md) #044 and [calibration.md](calibration.md).** Do not restate
them here.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved out
so this file can be rewritten each phase without losing it. **This is a ROUTING TABLE —
pointers only.** The reasoning lives at the destination; do not restate it here, and do
not add "done" entries (those are the `#NNN` entry's job).

**Deferred engine work — see the cited entry's follow-up list:**

- **Technicals** — bench/coach technicals; a technical's lack of causal input → **#032**
- **Foul trouble** — period-/time-awareness (#031 C); getting foul-outs below ~0.38
  (lever measured saturated); splitting `substitutionAggressiveness` (#031 B) → **#031**
- **Over-dispersion** — `pickDefender` concentration also reaches blocks, steals and
  shot contests → **#031 A**
- **Seams left open by their design pass** → §3.6 **#024** · §3.7 **#025** · §3.9
  **#027** · §3.10/§3.11 **#028/#029** (incl. `committing_team_id` not on the OpenAPI
  `GameEvent` — ✅ **CLOSED by §3.18**) · §3.12 **#030** (~20 3PA/team/game
  — ✅ **CLOSED by §3.17**, landed 37.22) · §3.16 **#039** (the unsourced shooting-foul
  share — ⚠ **still unsourced**, and §3.20 moved it to 0.3766) · **§3.17 #040**
  (FG%/2P%, FGA, FTA — ✅ **all CLOSED by §3.20**; the
  `PROB_FLOOR`-vs-`base-block-three` finding is ⚠ **RE-OPENED at a larger size** — §3.20
  measured the same clamp making `base-turnover` a 0.17-elasticity lever, see #042 D4)
- **§3.20's residuals** → **#042**'s implementation note. The rebound pool was closed by
  **§3.21 (#043)**; the rest (Fouls, `PROB_FLOOR`, 3P%'s band) are **sized and
  deliberately not scheduled** → [roadmap.md](roadmap.md). ⚠ **The `PROB_FLOOR` /
  `base-block-three` finding is due to move to [calibration.md](calibration.md)'s Blocks
  row** — pre-work step 6: it is an engine TRAP a tuner must hit when reaching for that
  knob, not a backlog chore

**Other files own these outright:**

- **The phase sequence and this phase's bullet** → [roadmap.md](roadmap.md). ⚠ Its
  **§3.20** bullet is `[x]` with the landing; the **pre-design argument under it is
  SUPERSEDED** (#042 A disproved the over-determination, and execution then disproved
  #042 B's FTA/FGA coupling in turn). It is **kept and ANNOTATED** rather than rewritten,
  because it is history. **#042 and its implementation note are authoritative.**
- **Calibration targets** → [calibration.md](calibration.md), **the source of truth**.
  Update it *and* the `CalibrationHarness` `(target ~N)` strings together.
- **Infra/tooling/data-hygiene chores**, and **the `decisions.md` condense pass** (a
  **gate on starting Phase 4**) → [roadmap.md](roadmap.md)'s *Phase 3 → Phase 4 pre-work*
- **Untriaged ideas**, incl. the parked cap 3→5 tuning idea and strategic substitution →
  [ideas.md](ideas.md)

**Standing facts (true every phase, not deferrals):**

- `CalibrationHarness` is disabled by default and lives in the `sim` test sources; run
  it with `-Dcalibration=true -DcalibrationSeed=NNNN`. **Re-run after any `SimConfig`
  change.** Since §3.15 the sim profile is selected by the ordinary Spring profile list —
  `SPRING_PROFILES_ACTIVE=local,baseline` (#035 F — ⚠ **the `-D` form does NOT reach the
  forked surefire JVM**). ⚠ **`baseline` must always
  be in the list** (it carries all **63** values), and **order is positional** — putting it
  last lets it win and silently neutralizes the era file. The **effective-config dump**
  in the report is what catches that, and it also catches a caller passing its own copy
  of a profilable value (the §3.15 harness-pace trap).
- **Do NOT touch `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`'s baseline value** without its
  own recalibration pass (#034 B) — though §3.15 makes it *profilable* (#035 C).
