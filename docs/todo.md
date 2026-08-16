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
> **§3.16 IS TWO JOBS (roadmap.md):** **(1) source the true constraints**, and **(2)
> re-solve the numbers against whatever they turn out to be.** Job (1) is research
> rather than engine work — **every number on both sides is currently unsourced**,
> including §3.4's originals. ⚠ **Whether the design pass BLOCKS on job (1) is Q1
> below, not a settled matter**: roadmap.md notes the sourcing chore can run
> independently of §3.13–§3.15.
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

## §3.16 — recalibration: the open questions for the design pass

§3.16 is the **second non-mechanic sub-phase** and the **last of Phase 3**. It adds no
possession branch, no event, no rate — it **re-solves numbers**. Resolve each question
below into a Decision letter in a new `decisions.md` **#036**.

**Read first** — these are the design-pass input, not this list:
- **[calibration.md](calibration.md)** — the **source of truth for targets**, its
  CONTESTED section, and its own statement of the two jobs. ⚠ **No target in it is
  sourced yet**; the current-value column is §3.15's baseline landing.
- **roadmap.md's §3.16 bullet** — the four goals, the NON-goals, and the **escalation
  rule** in full. Do not work from the paraphrase below.
- **backlog.md's target-sourcing chore** — job (1)'s actual scope.
- The findings §3.16 will reach for: **#028** (the wrong-way lever), **#030 G** (the
  `BASE_*` exchange rate), **#031** (the saturated foul-trouble lever), **#034 G** (the
  emergent flagrant divisor).

> **✅ ALREADY SETTLED — do not re-litigate:**
>
> **(i) The escalation rule governs scope** (roadmap.md). §3.16 re-solves anything
> reachable by an **existing knob**; a gap no existing knob can close is a **new
> mechanic ⇒ a new sub-phase**. This is a fence on the pass, not a question in it.
>
> **(ii) Targets are BASELINE-ONLY** (#035 I). §3.16 must not author per-profile
> targets — a delta is not a target.
>
> **(iii) NON-goals** (roadmap.md): no mechanic, no fidelity change, and no reopening
> `FOUL_MULT_*` (#030 G) or `pickDefender`'s weighting (#031 A).
>
> **(iv) §3.15 shipped the instrument.** Profiles, the effective-config dump, and
> baseline-only targets all exist. §3.16 uses them; it does not build them.

**⚠ Q2 AND Q3 ARE ONE QUESTION WEARING TWO HATS, and that is the crux of the pass.**
The lever that separates efficiency from volume is *either* pace (an **existing knob**,
so §3.16 work) *or* shot mix (probably a **new mechanic**, so a new sub-phase). Answer
them **together and against the escalation rule**, not independently — answering Q2
"shot mix" while answering Q3 "in scope" quietly converts §3.16 into a mechanic pass.

1. **Does the design pass BLOCK on sourcing, or run in parallel with it?** Job (1) is
   research, not engine work, and roadmap.md says it "can happen any time, independent
   of §3.13–§3.15" — so this is a real sequencing choice, not a foregone one.
   Candidates: **(a) block** — source everything first, then design against real
   numbers, at the cost of stalling on research that may take a while; **(b) design the
   lever against the *current* figures and re-solve once sourcing lands** — risks
   designing for a gap that turns out not to exist; **(c) split** — block only on the
   CONTESTED pair (points/FG%), proceed on the rest. ⚠ **Whichever is chosen, #036 must
   say what happens if a sourced number contradicts the design.**
2. **What is the efficiency-vs-volume lever?** ⚠ **The reason the pass exists.** The
   only knob that moves points is the shot `BASE_*` rates, and it moves points and FG%
   **the same direction** (~0.50–0.60% FG% per 1.0 point, measured #030 G). Points want
   a trim and FG% wants a raise, so **no setting of that one lever satisfies both** —
   three consecutive passes declined the trim for this reason. Candidates: **(a) pace**
   (`sim.default-possessions-per-period`, now profilable — fewer possessions cut points
   without touching FG%); **(b) shot mix** (reweighting toward threes
   lifts points-per-shot at a *lower* FG%, the modern-NBA shape, and separates the two
   directly). ⚠ **Measured 2026-08: `ShotSelector` holds NO `SimConfig` reference at
   all** — shot mix is driven purely by the shooter's skill weights
   (`PlayerGameState.shotTypeWeight`), so (b) means **introducing a knob that does not
   exist**, which the escalation rule likely makes a new sub-phase. (a) turns a knob
   that already exists. **This asymmetry is what ties Q2 to Q3.** **(c) a
   combination**, which needs both consequences stated.
3. **Is the 3PA-volume gap in scope?** The engine takes **~20 3PA/team/game against the
   NBA's ~35** (#030's follow-up, a named §3.16 input). Candidates: **(a) in scope**, if
   Q2 picks shot mix and the reweight is judged an existing knob; **(b) escalate** to a
   shot-selection sub-phase; **(c) source it but leave it**, recording the gap in
   calibration.md as `observed`. ⚠ Note this gap is **evidence for Q2(b)**: closing it
   would itself move points and FG% in opposite directions.
4. **Which numbers does the pass COMMIT to landing, and which does it route out?**
   #036 must name both sets explicitly. **Foul-outs are the worked example** and the
   reason the rule exists: §3.13's lever is measured **saturated**, so a sourced
   ~0.35–0.45 means *no work*, while a sourced ~0.15 means *escalate* — the same number
   is in or out of scope depending on where it lands. **Exit condition**: calibration.md
   fully sourced — every row either a `TARGET` with a named source and season, or
   deliberately marked `observed`/`ballpark`.
5. **Is `PERSONAL_FOULS_PER_TEAM_GAME` re-measured, and what does that do to
   flagrants?** It is a **measured static** (19.0), deliberately not profilable, and the
   divisor turning the game-level flagrant rate into a per-foul probability (#034 G). It
   is an assumption about what the engine *currently does*, so **any pass that moves the
   foul rate invalidates it — and §3.16 is that pass.** ⚠ Re-measuring it **silently
   moves the flagrant rate** without touching `sim.flagrant-fouls-per-team-game`. Decide
   whether that is accepted, compensated, or escalated.
6. **Which non-targets stay non-targets?** Technicals and flagrants are deliberately
   **ballparks, not targets** (#032 J / #034 H) — nothing is tuned toward them. Confirm
   they stay that way rather than being promoted by proximity, and say the same for the
   4/5/6-foul distribution, which calibration.md calls the real diagnostic behind
   foul-outs.
7. **What replaces the reproduction gate?** ⚠ **Every §3.x so far had one; §3.16 is the
   first pass EXPECTED to move the numbers**, so "reproduce the prior landing" is not
   available and its absence must be filled deliberately rather than by default. What
   distinguishes an intended re-solve from a bug? Candidates: **(a) attribution** —
   every move traceable to a named knob, with the before/after recorded; **(b) a
   one-knob-at-a-time discipline** with a harness reading between each; **(c) a
   tolerance band** on the numbers §3.16 is *not* re-solving, so an unintended
   side-effect surfaces. ⚠ Note the §3.15 lesson: **a caller holding its own copy of a
   tunable value is invisible to the properties file** — the effective-config dump is
   the instrument that catches it, and §3.16 should read it every run.

### What holds regardless of how the questions resolve

**Unlike every §3.x before it, §3.16 is EXPECTED to move the numbers** — that is its
purpose, and a reproduction gate would defeat it. **Q7 owns what replaces that gate**;
the items here are fixed either way and are not open questions:

- **No mechanic changes** — no new branch, event or rate, and
  `possession-flow.puml` must not change.
- **The final landing is recorded against a SOURCED target**, not an estimate. A pass
  that re-solves numbers against unsourced targets has done job (2) without job (1).
- ⚠ **Judge the coarse rows at 5 SEEDS ONLY** — technicals, flagrants and foul-outs
  are the noisiest in calibration.md, and flagrants are the coarsest row in it.
- **`calibration.md` and the harness's `(target ~N)` strings move together**, in the
  same change, whenever a target moves.

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
