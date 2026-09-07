# Backlog

Loose tactical chores with **no phase home** — infra/tooling/data-hygiene work that
isn't a product feature and so doesn't belong in a roadmap phase. Stable across phases
(unlike [todo.md](todo.md), which is rewritten each phase).

**Completed chores are REMOVED, not checked off** — a finished chore's record lives in
the `decisions.md` entry or roadmap phase that consumed it, so leaving it here duplicates
that and buries the open work. ⚠ **Gate work does not live here either**: the Phase 3 → 4
pre-work items are in [roadmap.md](roadmap.md) and [todo.md](todo.md), which are the
authority on their sequence and traps.

Deferred *gameplay* scope → [roadmap.md](roadmap.md) phase bullets. Untriaged *ideas* →
[ideas.md](ideas.md). Engine traps a tuner must hit → [engine-findings.md](engine-findings.md).

---

## Calibration

- [ ] **Source the five remaining unsourced benchmarks.** The §3.4 five were sourced in
      2026-08 (#036, Basketball-Reference 2025-26) and that half is closed. Still unsourced:
      **foul-outs**, **technicals**, **flagrants**, the **minutes distribution**, and the
      **shooting-foul share** behind `sim.non-shooting-foul-share` (#039 D — derived, never
      sourced, and the number that would say whether §3.16 is right or merely self-consistent).
      *Done* = a per-team-per-game figure with a named source and season, noting league-average
      vs per-team-mean and whether pace-adjusted; recorded in [calibration.md](calibration.md)
      with the `CalibrationHarness` `(target ~N)` strings updated in the same change.
      ⚠ **Capture points and FG% together** — the only lever (shot `BASE_*`) moves both the
      same direction, so verifying them separately then finding them mutually unreachable is
      the exact failure §3.16 existed to avoid.
      ⚠ **Do NOT promote the remaining ballparks to targets** just because they are written
      down — a landing promoted to a target is circular (#042 J demoted foul-outs for this).

## Engine

- [ ] **Audit which tunables sit under `PROB_FLOOR` (0.02) and are therefore INERT.**
      The known case (`base-block-three` at 0.005) is recorded in
      [engine-findings.md](engine-findings.md); **the open half is the general one** —
      `PROB_FLOOR` clamps every probability in the engine, so any constant below it is
      equally dead, and nothing says so at the declaration site. Annotate them, or
      reconsider whether one global floor suits rates that differ by an order of magnitude.
      ⚠ Sized and accepted for blocks (~half a blocked three per team-game, on an on-target
      row). **Any pass that tunes `base-block-*` must reroute through `clampRareProbability`
      first**, or it will tune a dead lever and conclude the knob does nothing.

- [ ] **A `BoxScoreReconciler`: derive from `game_event` what can be derived, diff it
      against the persisted `box_score` rows, report per column.** Today every column is an
      event-time copy of an in-memory accumulator; **nothing queries `game_event`**, so the
      counter and the event are siblings that cannot double-count and equally are not forced
      to agree. ⚠ **The box score is the EXCEPTION to the project's derive-don't-store rule**
      (#028 A1, #023 F, #034 F) — worth stating, because the codebase reads as if derivation
      were the norm. #020 already settled that the events are the source of truth and the row
      is a denormalized snapshot; the only real failure is an *undetected* divergence.
      **A reconciliation, not a replacement** — a full rewrite yields a hybrid (see below)
      that is worse than either pure design, and re-aggregating a growing log on every read
      reopens #019's pagination call.
      ⚠ **`minutes` is NOT derivable from any event** — it is a possession-share projection
      with no clock behind it (#023 A). A reconciler must say so explicitly, not skip it.
      ⚠ **`outcome` is free text and one value was renamed with no migration** —
      `COMMON_FOUL` → `NON_SHOOTING_FOUL` (#040 M/N), so pre-§3.17 rows hold the old string.
      #040 N judged that history disposable; **decide and record which**, or the reconciler
      silently under-counts fouls on old games.
      ⚠ Rebounds must exact-match `OFFENSIVE`/`DEFENSIVE` — the `OUT_OF_BOUNDS_*` rows are
      not rebounds (#026 E); fouls must exclude `TECHNICAL_FOUL` (#032 E). Deriving `points`
      re-implements `pointsFromEntity` — share it or cite it, don't copy it.
      **Timing: Phase 4**, when season aggregation makes a drifted row compound — not §3.19,
      which needs the numbers to hold still. Subsumes #041's `box_score.steals` follow-up.

## Tooling & infra

- [ ] **A constants-reference table GENERATED FROM SOURCE**, so docs link rather than
      restate — the §3.12 pass had to update one changed multiplier in **five places**.
      Decided out of §3.15 deliberately (#035 H). ⚠ **Re-scope before building**: #035 F's
      effective-config dump already serves the runtime half, and #035 C split the constants
      into two declared groups — measure what is still restated by hand first.

- [ ] **Add the four domain docs to the `project-docs` skill's routing table.**
      `game.md`, `player.md`, `coach.md` and `roster.md` are absent from it, so they are
      kept current by noticing rather than by rule — §3.17 had to update all four as an
      enumerated checklist item because no rule would have caught them.
      ⚠ **The failure mode is silent and asymmetric**: a stale *planning* doc gets caught by
      the next design pass reading it end to end; a stale *domain* doc is read by whoever is
      learning the model, who cannot know it is wrong. Both known drifts (`coach.md`'s
      `offensiveScheme` axis, `player.md`'s five-skills-for-four-types wording) were repaired
      by later phases — **the rule was not**, which is the point.
      **Fix**: a row per domain doc (what it owns, what it does not) plus a "Before you
      finish" check. ⚠ Make it grep-based — *"grep the domain docs for every identifier this
      phase renamed or re-specified"* — not a prose reminder to remember.

- [ ] **Clear the 6 open Dependabot alerts** — all `gametime-frontend` (`package-lock.json`),
      all **development**-scope, none in the Java service: `postcss` (high + medium), `vite`
      (high + medium), `brace-expansion` (high), `@babel/core` (low). Dev-scope means build
      tooling, not shipped runtime code. ⚠ Re-check the count before starting; it drifts.

- [ ] **Switch `spring.jpa.hibernate.ddl-auto` from `update` to `validate`** in the `local`
      and `test` profiles. Liquibase owns the schema, but `update` still runs its own DDL
      every boot and only WARNs — which masked a real entity↔schema mismatch
      (`PlayerEntity.weight` `Integer` vs a `VARCHAR` column, fixed in `3934b38`).
      `validate` turns future drift into a loud startup failure.
      ⚠ **A small audit, not a one-liner** — any other latent mismatch would then fail the
      boot. A live instance of the H2-tolerates / Postgres-rejects divergence in
      [risks.md](risks.md); overlaps the Testcontainers item below.

- [ ] **Evaluate Testcontainers as an alternative to H2** for integration tests.

- [ ] **Separate test seed data from production seed.** Both `local` and `test` load the
      same changelog, so tests assert against production seed rows and editing a seed file
      can break tests that hardcode team IDs or sizes. Point the test profile at a small
      fixed fixture so `main/resources/db/` can evolve independently. *Partly mitigated*:
      `RosterLineupDelegateTest` now signs its own players; the hardcoded team IDs remain.

## Data

- [ ] **Hand-tune marquee/star players to 18–20** where appropriate — the 1–20 rescale
      (#008) was mechanical. Deferred until the engine shows whether it matters.
