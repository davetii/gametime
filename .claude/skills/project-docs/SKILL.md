---
name: project-docs
description: Keep the gametime planning docs (decisions.md, todo.md, roadmap.md, ideas.md, backlog.md, risks.md) in their established house format and voice. Use whenever writing or editing any doc under docs/ — logging an architecture decision, running a design pass, writing an execute-ready plan, moving deferred work, parking an idea, or filing a risk. Follow this before adding a #NNN entry or rewriting a doc section so the docs stay consistent with the 27 decisions already there.
---

# Gametime planning-doc house style

The `docs/` planning files have a strong, consistent format built up over 27
architecture decisions. When you write or edit any of them, match the existing
shape and voice — don't invent a new structure. **The code and the existing
entries are the source of truth; this skill describes the pattern, read a recent
neighbor (e.g. decisions.md #025/#026, the latest todo.md plan) before writing.**

## Which file does a note belong in? (routing — decide first)

The six docs partition cleanly. Put a note in exactly one:

| File | Holds | Does NOT hold |
|------|-------|---------------|
| **decisions.md** | Resolved architecture/design decisions as numbered `#NNN` entries, append-only | Open questions (those are a design pass in todo.md until resolved), tactical tasks |
| **todo.md** | **Current phase/sub-phase ONLY** — the active design pass's open questions, then its execute-ready plan | Anything that outlives the current phase (route it out, see below) |
| **roadmap.md** | The phased plan: shipped (`[x]` + a landing note) vs. pending (`[ ]`) phases and numbered sub-phases | Tactical steps (those are todo.md), decision rationale (that's a #NNN) |
| **backlog.md** | Cross-phase infra/tooling/data-hygiene chores with no phase home | Gameplay scope (→ roadmap sub-phase), untriaged ideas (→ ideas.md) |
| **ideas.md** | Untriaged future-improvement ideas, no phase home, not chores | Planned work, decided things |
| **risks.md** | Known active risks and concerns | Decisions, tasks |

**The cardinal rule: todo.md is current-phase-only.** When a phase closes, todo.md
is rewritten for the next one — so anything that must survive that rewrite has to
live elsewhere *before* you rewrite. Deferred **gameplay** → a numbered roadmap
sub-phase; deferred **infra/tooling** → backlog.md; **untriaged idea** → ideas.md;
**seam left open by a decision** → that decision's "follow-up (carry forward)" list
AND, if it's the next phase, todo.md. When in doubt, leave a one-line pointer in
todo.md's "Where deferred work lives" section rather than losing the thread.

## The three-session rhythm (how a sub-phase moves)

Each numbered phase/sub-phase (§3.4, §3.7, §3.8, §3.9 …) moves through three
sessions, and the docs reflect which session you're in:

1. **Design pass** — resolve the open questions. Output: a new `decisions.md #NNN`
   (Decisions A–E…) + an **execute-ready plan** in todo.md. NO production code.
2. **Execution** — build it. Output: code + tests, an **implementation note** on
   #NNN recording any divergence, and flip the roadmap bullet to `[x]` with a
   landing summary.
3. (The next phase's design pass starts the cycle again; todo.md is rewritten.)

Don't write resolver/production code in a design session; don't re-litigate a
resolved decision in an execution session. If a design pass surfaces a choice that
is genuinely the user's, surface it — don't guess (that's how #027's taxonomy and
STOLEN-share calls were made).

## decisions.md — the `#NNN` entry format

Append at the bottom (before the `*Template for new entries:*` block), never
renumber. Two entry shapes exist:

**Minimal** (small/early decisions, #001–#010 style):
```
### NNN — Short title
**Date**: YYYY-MM
**Decision**: What was decided.
**Rationale**: Why this choice was made.
**Alternatives considered**: What else was evaluated (optional).
```

**Full design-pass entry** (the #021–#027 style — the standard for a phase design
pass). Structure:
```
### NNN — Long descriptive title naming the phase (§X.Y) and the key calls
**Date**: YYYY-MM
**Scope**: What this phase does, what kind of work it is (engine / API / schema),
what it explicitly is NOT. State "no schema change" / "no OpenAPI change" if true,
and why (cite #020 for free-text outcome, etc.). End with "NOT YET BUILT — this
entry is the resolved design; the execute-ready plan is todo.md's §X.Y plan."

**Decision A — <name the call> (<A1/A2 if a variant was chosen>).** The decision,
stated so a reader knows exactly what to build. Bold the crux decision.
**Decision B — …** through as many as the pass resolves (typically A–E/F).

**Rationale**: *(overall)* … *(A)* … *(B)* … — one clause per decision, keyed by
letter, explaining WHY this and not the alternative.
**Trade-off**: *(A)* … *(B)* … — the honest cost of each call, keyed by letter.
**Alternatives considered**: *(fork/A)* **name** — rejected: reason. … Cover the
real forks, keyed by the decision they bear on.
**Status of §X.Y decisions**: A–E all resolved by this entry. Net schema change:
… New engine pieces: … Determinism note if RNG order changed. "The execute-ready
task sequence is todo.md's §X.Y execution plan." Open-at-execution: the small,
constrained calls left to the builder.
**§X.Y follow-up (carry forward)**: bulleted seams this pass deliberately parked.
```

After execution, add:
```
**Implementation note (from execution, YYYY-MM).** Shipped exactly as A–E specify
[or: with one divergence — …]. Resolved the open-at-execution items: … Final
constants: … Landing (harness, N games): <aggregates>. Coverage: … gate green.
```

## The house voice (match it — this is what makes the entries good)

- **Cross-reference by number.** "(#020)", "the #014/#017 discipline", "reverses
  #021 A". Every non-obvious choice cites the prior decision it follows or revises.
- **Name and reject alternatives with the reason**, not just a list — "rejected:
  fabricates a participant column ahead of a consumer (the #014/#017/#020 trap)."
- **Verify, don't assume.** When a change *might* be neutral, say so explicitly and
  add the instrument to check (the #026 D "free pending harness confirmation"
  pattern), rather than asserting it's free.
- **Naming discipline for event vocabulary.** New `outcome`/enum strings mirror the
  established prefixing (`BLOCKED_*`, `OUT_OF_BOUNDS_*`) and must not collide across
  phases (#027 D: `LOST_BALL_OUT_OF_BOUNDS` on `TURNOVER` vs. §3.8's
  `OUT_OF_BOUNDS_*` on `REBOUND`). Defer exact spellings to execution when cleaner,
  constrained by an invariant.
- **Don't fabricate ahead of a consumer** (#014/#017/#020) — the recurring
  principle; invoke it by name when it applies.
- **Bold the crux**, use `*(letter)*` keying so Rationale/Trade-off/Alternatives
  line up with the Decisions. Dense argued prose over bullet lists for the reasoning.

## todo.md — structure

- **Header**: current-focus line + a callout box stating whether the current phase
  is a design pass (needs #NNN first) or execute-ready (design resolved as #NNN).
- **Design-pass phase**: a numbered list of open questions to resolve (each becomes
  a Decision in #NNN).
- **Execute-ready phase**: a `## §X.Y execution plan (decisions.md #NNN — resolved)`
  section — a build preamble (JAVA_HOME/coverage-gate reminder), then numbered
  `**Step N — title (#NNN ref)**` headers each with `- [ ]` checkbox sub-items for
  the actionable work. Plus un-checkboxed reference blocks: the reconciliation
  invariant, "Do NOT" guardrails, and open-at-execution items. Mirror the most
  recent shipped plan's shape (check git history for the prior §X.Y plan).
- **Verified facts** map of the relevant package (real anchors, confirmed against
  code) — kept for whoever executes.
- **"Where deferred work lives (not here)"** — the routing pointers so the
  current-phase-only rule doesn't lose anything.

## roadmap.md — bullets

- Phases and numbered sub-phases as `- [ ]` / `- [x]`. A shipped item gets `[x]` +
  an indented italic landing note (`_Shipped (decisions.md #NNN): … <final
  aggregates/coverage>._`). Sub-phase bullets are **seams, not plans** — note that
  each needs its own design pass (the "do not execute a bullet without its design
  pass" warning), and keep the calibration-blast-radius sequencing rationale.

## ideas.md / backlog.md / risks.md

Lighter — a titled bullet or short block per item with enough context to act on
later, and a note on the trigger/consumer that would promote it. Don't over-format;
match the existing entries. A parked idea should say what would make it real (its
missing consumer), the same "ahead of a consumer" discipline the decisions use.

## Before you finish

- New `#NNN` appended (not renumbered), cross-refs resolve to real entries.
- The note lives in the right file (routing table above); nothing phase-surviving
  left only in todo.md.
- Rationale / Trade-off / Alternatives keyed to the Decisions; crux bolded.
- If a design pass: no production code, execute-ready plan left in todo.md.
- If execution: implementation note on #NNN, roadmap bullet flipped to `[x]`.
