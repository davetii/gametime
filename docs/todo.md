# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
phase bullets.

Current focus: **the Phase 3 → Phase 4 pre-work GATE.** Phase 3 is complete
(§3.22 shipped as [decisions.md](decisions.md) #044, the last engine sub-phase).

> ⚠ **This is a GATE, not Phase 4's first task** — Phase 4's design pass does not
> start until all eight are done. [roadmap.md](roadmap.md) lists the items and their
> order; **this file holds the how and the traps**, and is rewritten when Phase 4's
> design pass starts.

---

## The gate — eight ordered items

**Order is dependency-driven, not size-driven.** Steps 2+3 are one pass; 5 depends
on 4; 8 is a snapshot check and must be last.

- [x] **1. Rewrite `todo.md`.** ✅ Done 2026-09: §3.22's finished execution plan (33
      completed checkboxes) removed, and this gate is now the active work here.
- [x] **2. Condense `decisions.md`** ✅ *(2026-09)* — **the premise changed during the work
      and the outcome is a SPLIT, not a condense.** Measured: *Alternatives* + *Trade-off* were
      only ~15% of the file, so halving them recovered ~7% — the plan below targeted the wrong
      sections. The real division is by **audience**: the operative findings (traps, wrong-way
      levers, saturations, elasticities, final constants) are ~18k and are what anyone actually
      reads; the rest was design-pass narrative for a phase that is now closed.
      **What shipped**: findings extracted to **[engine-traps.md](engine-traps.md)**;
      every `#NNN` cut to its title, date and decision letters. **599k → 38k + 20k.**
      ⚠ This is **not** the 2026-07 split that was reverted — no entry moved file, no number
      changed. ⚠ **Never renumber**: ~1,900 citations resolve by number **and letter** (157
      distinct entry+letter pairs; `#025 F` alone is cited 15×).
- [ ] **3. Sweep the `sim` package's Java comments.** ⚠ **NOT the same pass as 2** — that
      coupling was asserted, then tested and found false: condensing never breaks a citation,
      only renumbering would. Scope is narrow — rename stories, "before it, X couldn't happen"
      descriptions of code that no longer exists, and design-pass narrative. ⚠ **Leave every ⚠
      warning where it is**; the comments are mostly operative warnings sitting on the field they
      protect. ⚠ **One known bug to fix**: `SimConfig.java` (~L75, ~L176) still repeats #028's
      wrong-way-lever warning for `base-no-basket-foul`; #036 disproved it at the current
      configuration and `application-baseline.properties` already says the opposite.
- [ ] **4. Split `possession-flow.puml`** into a high-level outline + sub-section diagrams.
      It is **64k, 52% inside `note` blocks, and renders at 13,800px against a 16,384
      ceiling** — roughly one phase of headroom left. ⚠ **THIN THE PROSE FIRST, THEN
      SPLIT** — measured across two phases, ~68% of the file is notes + legend and only
      ~25% is the flow, so splitting first yields four files that are each still mostly
      notes. Most notes re-argue their decision instead of describing the branch; the
      diagram needs **the pointer, not the argument** (phase + decision letters + the
      one-line crux + the ⚠ trap). The 208-line legend is reference material, not flow —
      move it to its own file, ⚠ **not into `game.md`**, which is the other half of this
      duplication (step 5). Re-measure after; steps 1–2 may be enough on their own. ⚠ **Cut at PARTITION boundaries** —
      branch order *within* a partition is load-bearing and this file is where it is
      recorded (the §3.14b/§3.16 drift happened inside one flow). ⚠ **Keep the render check
      per sub-diagram**: `plantuml -DPLANTUML_LIMIT_SIZE=16384 -tpng` and confirm the
      height — a plain `-tpng` truncates silently at 4096px, and `-checkonly` does not lay
      out, so a green check does not prove the PNG is whole.
- [ ] **5. `game.md` dedup against the new diagrams** — the two describe the same flow
      twice. **After step 4**: it is cheaper once each sub-diagram's ownership is settled.
      Measured 2026-08: `game.md` is **47.7k**, and its `### The calculation sequence`
      subsection alone is ~22.5k — half the file — walking the same branch order the
      diagram draws. ~70k of combined description of one flow. **Thin them TOGETHER or
      not at all**; fixing one side leaves the duplicate looking authoritative.
      ⚠ **Its ~253 `#NNN` citations are NOT the bloat** — unlike calibration.md's (which
      marked history in a targets reference), these annotate **live mechanics** and are how
      a reader finds the argument. Cutting them removes navigation. The job is
      de-duplication against the diagram.
      ⚠ **The hard part**: decide what only prose can carry — **the WHY behind an ordering**
      — versus what the picture already shows. Deleting an ordering rationale is the one
      irreversible mistake here.
- [ ] **6. Triage `backlog.md`.** Move the three items that are already gates here out of
      it (condense `decisions.md`, thin the `.puml`, the Java-comment sweep). Move the
      **`PROB_FLOOR` / `base-block-three` finding** to `calibration.md`'s Blocks row or
      #040's note — it is an **engine trap a future tuner must hit at the moment they
      reach for that knob**, not a chore. Condense the four large remainders to a summary
      plus a pointer.
- [ ] **7. Adopt Beads, and cut over for Phase 4 work.** ⚠ `bd` is installed and the
      `SessionStart` hook (`bd prime --hook-json`) is already in `.claude/settings.json`,
      but **there is no `.beads/` database** — it currently primes nothing. Initialize it,
      then migrate the ~9 clean chores left after step 6. **Migrate the POINTER, not the
      prose** — several entries carry 6–7k of argument, and moving that into an issue body
      relocates the bloat somewhere less readable.
      ⚠ **`decisions.md`, `calibration.md`, `game-events.md` and `possession-flow.puml` do
      NOT migrate** — append-only reference cited by number; they are not work items.
- [ ] **8. Re-read every `calibration.md` verdict against its own Current number.** ⚠ **Not
      a re-run — a re-READ**, and it must be **last** so it reflects final state. The
      Current column is updated each landing; the status prose beside it is not, so a row
      can carry a stale verdict indefinitely and **nothing fails**. Found 2026-09: four
      rows disagreed with their own numbers — 3P% marked `🟡 −0.52` while reading **+0.14
      (green)**, def rebounds marked `−0.48` while sitting at **−0.85**. Phase 4 reads this
      table as its input the way §3.20 did, so a wrong verdict here is a wrong premise
      there.

**⚠ The roadmap ↔ Beads boundary** (decided 2026-09 so step 7 does not re-litigate
it). Beads holds **actionable work with state**; roadmap.md holds the phase
structure and the seams. **Phases 5–8's open bullets do NOT migrate** — a seam
turned into a ticket lies about its readiness. **The gate itself stays in
Markdown**: it is a sequenced dependency chain, which reads better as an ordered
list than as eight issues with dependency edges, and Beads starts clean with Phase
4 work. ⚠ **When work becomes a bead, the BEAD is the only record and the doc entry
is DELETED** — a pointer to a bead id is fine, a duplicated description is not
(the convention backlog.md already uses). **Two records of the same work WILL drift.**

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
