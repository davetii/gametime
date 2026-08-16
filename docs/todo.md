# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.16).

Current focus: **§3.16 — recalibration against verified targets**. The full
§3.7–§3.16 sequence and what follows (Phase 4) live in **roadmap.md's
"Possession-fidelity completion" section** — not here (todo.md is
current-phase-only). §3.7–§3.13, **§3.14a**, **§3.14b** and **§3.15** have all shipped.

> **⚠ §3.16 NEEDS A DESIGN PASS FIRST — there is no execute-ready plan here yet.**
> Resolve the open questions below into a new `decisions.md #036` (Decisions A, B,
> C…) **plus** an execute-ready plan in this file. **Write no production code in that
> session.**
>
> **§3.16 is a different KIND of pass: it adds no mechanic, it re-solves numbers.** A
> second §3.4, not a fidelity sub-phase. It is **the LAST Phase-3 sub-phase**.
>
> **§3.16 IS TWO JOBS, AND THEY ARE SEQUENCED (roadmap.md):** **(1) source the true
> constraints**, then **(2) re-solve the numbers against whatever they turn out to
> be.** Job (1) is research, not engine work, and it is the prerequisite — **every
> number on both sides is currently unsourced**, including §3.4's originals.
>
> **⚠ THE ESCALATION RULE.** §3.16 may re-solve any number reachable by turning an
> **existing knob**. If sourcing reveals a gap no existing knob can close, that is a
> **new mechanic and therefore a new sub-phase** — do not force it into this pass.
> **Foul-outs are the live example**: §3.13 measured that lever **saturated**.
>
> **§3.15 shipped the instrument this pass runs on** (#035). Profiles are ordinary
> Spring profiles; the harness selects one with
> `-Dspring.profiles.active=test,baseline[,era]` beside `-DcalibrationSeed=NNNN`, and
> prints an **effective-config dump**. ⚠ **Targets are BASELINE-ONLY (#035 I)** —
> §3.16 must not author per-profile targets, and a delta is not a target.

---

## §3.16 open questions (resolve these into `decisions.md #036`)

1. **Sourcing: what are the real targets?** Points (~112) and FG% (~47%) are flagged
   **CONTESTED** in [calibration.md](calibration.md) — both set in §3.4 from unsourced
   estimates, with current figures suggesting points ~114–117 and FG% ~47–48. The
   baseline landing is **118.3 / 46.9%**. Also owed a source: **foul-outs (~0.39)**, a
   soft target only because §3.13 tuned against it, and **fouls/team/game (~19–20)**,
   the next most load-bearing `ballpark`. Which figures, from which season, cited how?
2. **The lever that separates efficiency from volume.** ⚠ **This is the crux, and the
   reason the pass exists.** The only knob that moves points is the shot `BASE_*`
   rates, and it moves points and FG% **the same direction** (~0.50–0.60% FG% per 1.0
   point). Points want a trim and FG% wants a raise, so **no setting of that one lever
   satisfies both**. Identifying a lever that separates them — pace/possession count,
   or shot mix — **is** the design pass. ⚠ Note §3.15 made
   `sim.default-possessions-per-period` profilable, so pace is now a first-class knob.
3. **The 3PA-volume gap (#030's follow-up, a named §3.16 input).** The engine takes
   **~20 3PA/team/game against the NBA's ~35**. Is closing it in scope here, or is it
   a shot-mix mechanic and therefore a new sub-phase under the escalation rule?
4. **What does "done" mean, given the escalation rule?** #036 must state which numbers
   this pass commits to landing and which it explicitly routes out — and
   [calibration.md](calibration.md) must end the pass **fully sourced**: every row
   either a `TARGET` with a named source and season, or deliberately marked
   `observed`/`ballpark`.
5. **Does `PERSONAL_FOULS_PER_TEAM_GAME` get re-measured?** It is a **measured static**
   (19.0), not profilable, and it is the divisor behind the flagrant rate (#034 G). Any
   pass that moves the foul rate invalidates it. §3.16 is that pass — so re-measuring it
   is in scope, and doing so silently moves flagrants.
6. **Which of `calibration.md`'s non-targets stay non-targets?** The plausibility
   ballparks (technicals, flagrants) are deliberately **not** targets (#032 J / #034 H).
   Confirm they stay that way rather than being promoted by proximity.

---

### Reconciliation invariant (what "done" means)

**Unlike every §3.x before it, §3.16 is EXPECTED to move the numbers** — that is its
purpose, and a reproduction gate would defeat it. What must hold instead: **no
mechanic changes** (no new branch, event or rate — `possession-flow.puml` must not
change), **every move is attributable to a named knob**, and the final landing is
recorded against a **sourced** target rather than an estimate. ⚠ Judge the coarse rows
(technicals, flagrants, foul-outs) at **5 seeds only**.

---

### ⚠ Do NOT (standing guardrails — carried forward into §3.16)

These outlive any one phase. **§3.15 shipped as a refactor and changed no number at
all** (its gate reproduced §3.14b per-seed). **§3.16 owns re-centering and is the only
phase that may revisit the last one** — but only within the escalation rule: a number
no existing knob can reach is a new sub-phase, not §3.16 work.

- **Do NOT add a fourth removal path in `RotationState`** — ejection is the **hard**
  tier and extends `isDisqualified(...)` (#031 H, built §3.14a).
- **Do NOT reuse §3.14a's counter split** — a flagrant **does** count toward the
  6-foul limit and the penalty tally (#032 E). Use `recordFoul()`.
- **Do NOT re-tune §3.13's foul-trouble sit curve** — measured saturated (#031 note).
- **Do NOT trim `BASE_NO_BASKET_FOUL`** — a measured **wrong-way** lever (#028):
  trimming it *raises* points.
- **Do NOT re-open §3.12's `FOUL_MULT_*`** (settled on realism, #030 G) or touch
  `pickDefender`'s `individualDefense` weighting (#031 A).
- **Do NOT re-center points/FG%** — §3.16 owns it and the targets are CONTESTED
  ([calibration.md](calibration.md)).
- **Do NOT change the existing foul roll** — a flagrant is an *additional* roll on
  a foul that already happened (user call, #034 A). `isFoul` / `isAndOne` /
  `resolveReboundFoul` keep their rates, inputs and RNG draws; a test pins that
  `isFoul`'s RNG consumption is unchanged.
- **Do NOT vary `PERSONAL_FOULS_PER_TEAM_GAME` as if it were a tunable** (#034 G) —
  it is a **measured** assumption about the engine's current foul rate, and it is the
  divisor behind the flagrant probability. §3.16 (or any pass moving the foul rate)
  must revisit it deliberately; a §3.15 profile must not vary it independently.
- **Do NOT back-solve §3.14a's technicals constant** against the ~5% harness gap —
  that is #032 B2's documented nominal-vs-actual pace effect, not drift.
- **Do NOT add a stored `flagrant2` / `ejected` flag** (#034 F) — `flagrantTwos >= 1`
  is a monotonic counter, so the predicate stays **derived**. The stored-state
  exception predicted by #031 H / #032 F does not arise and is **retired**.
- **Do NOT exempt the flagrant retention from `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`,
  and do NOT raise the BASELINE cap** (#034 B). 3→5 is a parked idea needing its own
  recalibration pass ([ideas.md](ideas.md)). ⚠ **§3.15 makes this constant PROFILABLE**
  (#035 C) — the fence protects the **baseline value**, which a profile cannot touch, so
  an era profile setting it is fine and does **not** unpark the tuning idea.
- **Do NOT add 2 FTs on top of the underlying foul's award** (#034 C) — the flagrant
  **replaces** it. A flagrant stopped THREE is **2** FTs, not 3 and not 5. This is
  the likeliest bug in the pass.
- **Do NOT add a `flagrant_fouls` box-score column** (#034 H) — a flagrant is already
  inside `fouls`, so #033's parity argument does **not** reach it.
- **Do NOT reuse `pickTechnicalFreeThrowShooter`** (#034 D) — its premise is that
  nobody was fouled (#032 G); on a flagrant somebody was.

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
  `GameEvent`) · §3.12 **#030** (~20 3PA/team/game vs the NBA's ~35, a §3.16 input)

**Other files own these outright:**

- **§3.16 (recalibration)**, and the phase sequence generally → [roadmap.md](roadmap.md)
- **Calibration targets** → [calibration.md](calibration.md), **the source of truth**.
  Update it *and* the `CalibrationHarness` `(target ~N)` strings together.
- **Infra/tooling/data-hygiene chores**, and **the `decisions.md` condense pass** (a
  **gate on starting Phase 4**, run after §3.16) → [backlog.md](backlog.md)
- **Untriaged ideas**, incl. the parked cap 3→5 tuning idea and strategic substitution →
  [ideas.md](ideas.md)

**Standing facts (true every phase, not deferrals):**

- `CalibrationHarness` is disabled by default and lives in the `sim` test sources; run
  it with `-Dcalibration=true -DcalibrationSeed=NNNN`. **Re-run after any `SimConfig`
  change.** Since §3.15 the sim profile is selected by the ordinary Spring profile list —
  `-Dspring.profiles.active=test,baseline,nineties` (#035 F). ⚠ **`baseline` must always
  be in the list** (it carries all 57 values), and **order is positional** — putting it
  last lets it win and silently neutralizes the era file. The **effective-config dump**
  in the report is what catches that, and it also catches a caller passing its own copy
  of a profilable value (the §3.15 harness-pace trap).
- **Do NOT touch `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`'s baseline value** without its
  own recalibration pass (#034 B) — though §3.15 makes it *profilable* (#035 C).
