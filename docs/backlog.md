# Backlog

Loose tactical chores with **no phase home** — infra/tooling/data-hygiene work
that isn't a product feature and so doesn't belong in a roadmap phase. This file
is stable across phases (unlike [todo.md](todo.md), which is rewritten each phase
to track only the current one).

For deferred *gameplay* scope (sim-fidelity events the engine doesn't model yet),
see the **§3.x Deferred sim-fidelity details** section of [roadmap.md](roadmap.md)
— those have a phase home and live with the phase that will consume them. For
untriaged *future-improvement ideas* with no phase home yet (not chores, not
planned features), see [ideas.md](ideas.md).

---

- [ ] **Condense `decisions.md` — UNBLOCKED (§3.11 shipped 2026-08).** The file is 443
      lines / **~170k chars** and has become hard to track. The cause is a size split,
      not entry count: `#001`–`#020` (platform/domain/schema/roster/API) average **~1.3k**
      chars each, while the nine §3.x engine design passes `#021`+ average **~16k** — a
      **12×** gap. The five largest are `#029` (26k), `#028` (23k), `#027` (19k), `#026`
      (15k), `#024` (13k). One file serves two readers — looking up "can I add a column?"
      (a `#014`/`#017`/`#020` one-liner) means scrolling past ~120k chars of engine
      reasoning.
      **The trend is the argument**: each successive design pass has produced a larger
      entry than the last, and `#029` — written *after* this item was filed, warning of
      exactly this — is now the biggest in the file. Left alone, `#030` (§3.12) will beat
      it. (Figures measured 2026-08; re-measure rather than trusting them if time passes.)
      **The §3.11 gate has now cleared** — §3.7–§3.11 is a complete arc that goes
      historical at once, and which cross-refs §3.11 actually reached for is **known**
      rather than guessed. For the record, §3.11 execution *did* lean on all four
      entries this deferral protected: `#028`'s implementation note (the `BASE_FOUL`
      wrong-way lever and the lift-channel decomposition — both directly reused in
      §3.11's recalibration), `#025`'s `BLOCK_SENSITIVITY` reasoning + `#028`'s
      `PROB_FLOOR` trap (§3.11 C reused the machine rather than rediscovering it a third
      time), and `#026 D`'s build-the-instrument discipline. **Preserve those four
      findings** through any compression — they are the ones the next foul phase (§3.12)
      will reach for again. Note `#029` is now the other 24k-class entry alongside `#028`.
      **What to compress** (highest-value first): the **Alternatives considered**
      sections arguing against settled options nobody will reopen; **Trade-off** prose
      restating costs already stated in the decision; and **implementation notes**,
      which are read once right after execution (`#028`'s is ~10k chars for maybe four
      bullets of lasting value). **What to keep**: each entry's crux decision, final
      constants, and the traps worth not rediscovering.
      **Also worth doing regardless**: a scannable index table at the top (one line per
      decision + an explicit `<a id="NNN">` anchor per entry, since the headings are
      long enough that auto-generated slugs are fragile), and a short preamble
      collecting the recurring principles the entries constantly cite (events-are-truth
      `#020`, derive-don't-store `#023 F`/`#028 A1`, don't-fabricate-ahead-of-a-consumer
      `#014`/`#017`, reuse-the-play-type-vocabulary `#025 F`/`#026 E`, verify-neutrality
      `#026 D`, rare-events-need-their-own-sensitivity `#025`/`#028`).
      **Constraints**: keep it to **ONE file** (a `decisions.md` / `decisions-sim.md`
      split was tried 2026-07 and reverted — user wants one file; the split also moved
      volume around without reducing it). Never renumber; `#NNN` refs are cited from
      prose *and* Java comments (e.g. `decisions.md #026 E` in `MissedShotResolverTest`),
      and they cite the number, not a path. Update the `project-docs` skill in the same
      pass — it still says "append at the bottom, never renumber" with **no size
      guidance** (re-checked 2026-08), so entries will re-grow the same way; add the
      "keep implementation notes proportionate" rule there.
      **That prediction has now been tested and held**: `#029`'s implementation note was
      written under the unchanged skill and came out the largest in the file. Compressing
      the history without fixing the skill that generates it just resets the clock — treat
      the skill edit as **part of this item, not a nice-to-have**.
- [ ] Hand-tune marquee/star players to 18–20 where appropriate (the rescale was
      mechanical). Deferred until the game engine shows whether it matters.
- [ ] Switch `spring.jpa.hibernate.ddl-auto` from `update` to `validate` (or `none`)
      in the `local` + `test` profiles. Liquibase owns the schema (per CLAUDE.md), but
      Hibernate `ddl-auto=update` still runs its own DDL on every boot — quietly, and
      as a WARN it doesn't fail the app. That masked a real entity↔schema type
      disagreement: `PlayerEntity.weight` was `Integer` vs. the `VARCHAR` column, so
      Postgres startup logged a `CommandAcceptanceException` on the illegal
      varchar→integer ALTER every boot (fixed in commit `3934b38` by making the field
      `String`; the fix was to the mismatch, not to `ddl-auto`). `validate` would turn
      any future drift into a **loud startup failure** instead of a silent WARN.
      Caveat: flipping to `validate` needs the current entities to fully match the
      Liquibase schema first (any other latent mismatch would then fail the boot / the
      H2 test context) — so it's a small audit, not a one-liner. Related: this is a live
      instance of the H2-tolerates / Postgres-rejects divergence noted in
      [risks.md](risks.md) (the gate stayed green because H2 accepted the mismatch),
      and it overlaps the Testcontainers item below (real-Postgres integration tests
      would have caught it).
- [ ] **Harness self-verification — assert the instrument's own invariants.** The
      known risk is that `CalibrationHarness` *reports* and doesn't gate
      ([risks.md](risks.md)); this is the narrower, cheaper problem underneath it: **the
      harness can confidently print a wrong baseline**, and nothing catches that. §3.11
      hit it twice in one session:
      (1) a `-DandOneBaseOverride=0` flag was passed to disable the new feature for a
      baseline run — `AND_ONE_BASE` is a compile-time constant, so the flag was silently
      ignored and three "baseline" runs measured the *shipped* config;
      (2) the assumption that a zero base disables a rare event turned out false — a
      zeroed `AND_ONE_BASE` still produced ~0.44 and-1s/team/game, because
      `rareEventProbability`'s skill term alone stays positive off an even contest
      (now recorded in `#029`'s implementation note and guarded by a unit test).
      Both were caught only because the numbers looked *odd* — a recalibration steered
      off either would have been silently wrong, and the §3.x cadence tunes each phase
      against the previous phase's baseline.
      **The pattern to generalize** is already proven in-tree: §3.11's FT-source split
      carries an `UNKNOWN` bucket, so an untagged free throw shows up as a visible row
      instead of being mis-attributed to a real source. Do the same for the harness's own
      assumptions — assert, per batch, that the FT-source counts **sum to** total FTs with
      no `UNKNOWN`; that points reconcile with the event log; and above all that a
      **"baseline" run's config is the config it claims** — have the harness *print the
      constants it actually ran with*, so a run is self-describing the way an event is.
      That last one is the load-bearing fix: it catches misfire (1) directly, and it is
      the honest form of the check, because misfire (2) proved that "feature off" is
      **not** the same as "base = 0" for a rare event (only the caller's gate truly
      disables one). A run that prints `AND_ONE_BASE = 0.11` when the operator believed
      they had zeroed it is immediately visible; asserting "zero events" would instead
      have baked in the very assumption that turned out false. Cheap, and it would have
      caught both §3.11 misfires.
      *Distinct from the risks.md tolerance-band gate* — that one asks "are the aggregates
      still on target"; this asks "is the harness measuring what it says it is." The
      tolerance-band gate is reconsidered at §3.12's close-out; this is worth doing
      **before** §3.12's recalibration, since that pass steers off a 115.9 baseline.
- [ ] Evaluate Testcontainers as an alternative to H2 for integration tests.
- [ ] Separate test seed data from production seed. Today both the `local`
      (Postgres) and test (H2) profiles load the *same* Liquibase changelog
      (`db/changelog.yml` → `players.csv`, `roster.csv`, `release.1.0.1.dataload.sql`),
      so tests assert against production seed rows. Runtime league changes (trades,
      new signings) only touch Postgres and don't affect tests, but *editing the seed
      files* can break tests that hardcode team IDs / sizes. Introduce a small fixed
      test-only fixture (e.g. `test/resources/db/` changelog the test profile points
      at) so `main/resources/db/` can evolve for production independently. Interim
      mitigation done: roster-rule tests in `RosterLineupDelegateTest` now sign their
      own players instead of assuming seed roster sizes; remaining brittleness is the
      hardcoded team IDs themselves.
