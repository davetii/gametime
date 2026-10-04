---
name: project-docs
description: Keep the gametime docs under docs/ in their established format. Use whenever writing or editing any doc there — logging a decision, running a design pass, writing an execute-ready plan, moving deferred work, parking an idea, filing a risk, or editing a domain doc (game, game-events, player, coach, roster, calibration). Follow this before adding a #NNN entry or rewriting a doc section so the docs stay short and consistent.
---

# Gametime doc house style

**The docs bloat. Every rule below exists to stop that.** `decisions.md` once reached 599k
characters, `roadmap.md` 113k against 13k of unbuilt future, and both were rebuilt in 2026.
The cause was never one long entry: it was *accumulation* (append-only entries nobody
pruned) and *lossy copies* (the same landing restated in the roadmap, `todo.md`, domain
docs and Java comments). A per-entry budget does not bound a file that only grows.

**The code and the existing entries are the source of truth.** Read a recent neighbor
(e.g. decisions.md #044, the latest todo.md plan) before writing.

## Which file does a note belong in? (routing — decide first)

Put a note in exactly one file.

| File | Holds | Does NOT hold |
|------|-------|---------------|
| **decisions.md** | An index of resolved decisions as numbered `#NNN` entries: the crux call per decision letter, final constants, traps. Append-only | Reasoning, alternatives, trade-offs, implementation narrative; open questions (those are todo.md) |
| **engine-traps.md** | Measured findings a tuner must hit: wrong-way levers, inert knobs, saturations, elasticities | Decisions, history |
| **todo.md** | **Current phase only**: the active design pass's open questions and reasoning, then its execute-ready plan | Anything that outlives the phase (route it out, see below) |
| **roadmap.md** | Phase structure, sequencing rationale, shipped (`[x]` + a few-line landing note) vs. pending (`[ ]`) | Tactical steps (todo.md), decision reasoning |
| **backlog.md** | Cross-phase infra, tooling, data-hygiene and code-quality chores with no phase home | Gameplay scope (roadmap), ideas |
| **ideas.md** | Unimplemented features and game concepts: what it is, why it matters, what blocks it, what would make it real | Planned work, decided things, chores |
| **risks.md** | Live risks: what could go wrong and the mitigation | Decisions, tasks, resolved items |
| **calibration.md** | Targets, where each number sits now, and the **operative rules** for reading and moving it | How a target was argued; which pass landed it |
| **game.md, game-events.md, player.md, coach.md, roster.md** | **Reference docs: how it works now.** Rules, columns, constraints, what is simplified | Decision history, phase narrative, `#NNN` citations, measured landings |
| **possession-flow*.puml** | The possession flow as diagrams, in engine order (an overview plus one file per resolver). A **living spec**: a new branch or event is drawn in the same change | Prose reasoning. Notes say what the flow does, not why a decision was made |

**todo.md is current-phase-only.** When a phase closes it is rewritten, so anything that
must survive has to live elsewhere *first*. Deferred gameplay → a roadmap sub-phase;
infra/tooling → backlog.md; an untriaged idea → ideas.md; a measured finding →
engine-traps.md; a seam left open by a decision → that entry's letter and, if it is the
next phase, todo.md.

## The three-session rhythm

Each numbered sub-phase moves through three sessions, and the docs reflect which you are in:

1. **Design pass.** Resolve the open questions. Output: a `decisions.md #NNN` in the shape
   below, an **execute-ready plan** in todo.md, and any new fork drawn into the diagrams.
   The alternatives and trade-offs you weighed go in the todo.md plan while the pass is
   live; they are not copied into the entry. NO production code.
2. **Execution.** Build it. Output: code and tests; if execution diverged from the design,
   **edit the affected decision letter's line** (there is no separate implementation note);
   a measured trap goes in engine-traps.md; flip the roadmap bullet to `[x]` with a landing
   note of at most a few lines; confirm the diagrams match what shipped.
3. The next design pass starts the cycle; todo.md is rewritten.

Don't write production code in a design session, and don't re-litigate a resolved decision
in an execution session. If a pass surfaces a choice that is genuinely the user's, surface
it; don't guess.

## decisions.md — the entry shape

`decisions.md` is an **index**. Append at the bottom, never renumber (citations in code and
docs resolve by number **and letter**). An entry:

```
### NNN — Short title (§X.Y)

**Date**: YYYY-MM

- **A** — the crux call, stated so a reader knows what was built. ⚠ a trap or caveat if one exists.
- **B** — …
```

**Budget: about 1.5k characters per entry, never over ~2k.** The longest entries today run
~2k; treat that as the ceiling. If you are past it you are restating.

- One line per decision letter. Bold the crux. State final constants in the letter that
  set them. Name a rejected alternative only when it is a **trap** someone will walk into.
- No Rationale / Trade-off / Alternatives / Status sections and no implementation note.
  If it matters, it is a letter's one-liner, a line in engine-traps.md, or the commit message.
- **Never compress away**: the crux decision, the final constants, and the traps (a measured
  wrong-way lever, a clamp that floors a rare rate, an emergent coupling).
- **Correct a shipped entry in a newer entry**, not in place, except to fix a letter that
  diverged during its own execution.

## Writing in the other docs

- **Don't copy a decision's content.** roadmap.md, todo.md and the reference docs link to
  `#NNN` and say nothing more. Two records of one fact will drift.
- **Reference docs carry no history.** Do not add `#NNN`, "added in §3.x" or "was X, now
  Y". Say what is true. If a doc needs the history to be understood, the doc is wrong.
- **Prefer a pointer to a restatement.** A reader should be able to find the single place
  that owns a fact.
- **Verify, don't assume.** When a change might be neutral, say so and name the instrument
  that checks it, rather than asserting it is free.
- **Don't fabricate ahead of a consumer.** No attribute, column or event without a real
  reader. Invoke the principle by name when it applies.
- **Event vocabulary naming.** New `outcome` strings mirror the existing prefixes
  (`BLOCKED_*`, `OUT_OF_BOUNDS_*`) and must not collide across play types.

## todo.md — structure

- **Header**: the current-focus line and whether the phase is a design pass (needs #NNN
  first) or execute-ready (design resolved as #NNN).
- **Design pass**: the numbered open questions, each becoming a decision letter, with the
  reasoning that resolves them. This reasoning is deleted when the phase closes.
- **Execute-ready**: a `## §X.Y execution plan` with a build preamble (JAVA_HOME and the
  coverage-gate reminder), numbered `**Step N — title**` blocks with `- [ ]` items, and
  reference blocks: the reconciliation invariant, "Do NOT" guardrails, open-at-execution items.
- **Verified facts** of the relevant package, confirmed against code.
- **Where deferred work lives**: routing pointers only, so the current-phase rule loses nothing.

## roadmap.md

- Phases and sub-phases as `- [ ]` / `- [x]`. A shipped item gets `[x]` and a landing note
  of **at most a few lines** pointing to the `#NNN`. Sub-phase bullets are **seams, not
  plans**: each needs its own design pass.
- A closed arc of sub-phases collapses into **one** section, not one bullet each.
- **The test before writing a line here:** does this fact live in a `#NNN`, in
  calibration.md, in a reference doc or in a Java comment? Then it does not belong here.
- What only this file holds, and a condense pass must never cut: the **phase structure**,
  the **sequencing rationale**, the **seams-not-plans rule**.

## ideas.md, backlog.md, risks.md, calibration.md

- **ideas.md**: a titled entry per idea. Say what it is, why it would matter, what blocks
  it, and **what would make it real** (its missing consumer). A shipped idea is deleted.
  No decision citations.
- **backlog.md**: a **completed chore is removed, not checked off**. Before deleting, confirm
  the content is recorded elsewhere, and `grep` `docs/` and `CLAUDE.md` for inbound
  references to the removed text.
- **risks.md**: delete an item when it is resolved. Keep each entry to what could go wrong
  and the mitigation.
- **calibration.md** is a reference: targets, where they sit, and the operative rules
  (*judge at 5 seeds*, *don't back-solve the constant*, *this share is priced by the penalty
  rate*, *this row is green because a clamp holds it*). Live constant values live in
  `application-baseline.properties`, not here. When a target changes, update the table and
  the `CalibrationHarness` `(target ~N)` strings together.

## Before you finish

- New `#NNN` appended (not renumbered), in the index shape, under ~2k characters.
- Nothing in a reference doc cites a decision or narrates history.
- The note is in the right file; nothing phase-surviving is left only in todo.md.
- backlog.md: completed chores removed, with inbound references checked.
- Design pass: no production code, execute-ready plan left in todo.md.
- Execution: diverged letters edited, traps in engine-traps.md, roadmap bullet `[x]` with a
  few-line note.
- **The diagrams reflect reality** if the possession path changed: a branch that is not on
  them is silent rot. Render with `plantuml -DPLANTUML_LIMIT_SIZE=16384 -tpng` and look at
  the image.
- **Do NOT commit.** Leave the edits in the working tree, summarize, and wait for the user
  to ask. See CLAUDE.md's git rule.
