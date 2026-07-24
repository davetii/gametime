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

- [ ] **Condense `decisions.md` — AFTER §3.11 ships (not before).** The file is 403
      lines / ~150k chars and has become hard to track. The cause is a size split, not
      entry count: `#001`–`#020` (platform/domain/schema/roster/API) average ~2k chars
      each, while the §3.x engine design passes `#021`–`#028` average ~14k (`#028`
      alone is 24k). One file serves two readers — looking up "can I add a column?"
      (a `#014`/`#017`/`#020` one-liner) means scrolling past ~100k chars of engine
      reasoning.
      **Deliberately deferred until §3.11 lands**, because §3.11's design pass is the
      heaviest consumer of exactly the entries that would be compressed: `#028`'s
      implementation note (todo.md tells that session to read it first — the
      `BASE_FOUL` wrong-way lever and the retained-possession lift channel), `#025`'s
      `BLOCK_SENSITIVITY` reasoning + `#028`'s `PROB_FLOOR` trap (its open question 6),
      and `#026 D`'s build-the-instrument discipline (its question 5). Condensing now
      would guess at what's still load-bearing right before finding out. After §3.11,
      §3.7–§3.11 is a **complete arc** that goes historical at once, and which
      cross-refs §3.11 actually reached for is *known* rather than guessed.
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
      pass — it currently says "append at the bottom, never renumber" with no size
      guidance, so entries will re-grow the same way; add the "keep implementation
      notes proportionate" rule there.
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
