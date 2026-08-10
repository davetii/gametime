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

- [ ] **VERIFY THE CALIBRATION BENCHMARKS — find sourced modern-NBA figures for the
      five §3.4 targets, PLUS the foul-out figure §3.13 promoted.** *(Filed 2026-08 from
      §3.12's close-out; foul-outs added 2026-08 at §3.13's close-out. **This is
      job (1) of roadmap.md §3.16** — that pass is "source the constraints, then re-solve
      the numbers," and this chore is the sourcing half. It is research, not engine work,
      so it can happen any time, independent of §3.14–§3.15.)*
      **The problem, stated plainly: every number on both sides of the current argument
      is unsourced.** The §3.4 targets (~112 pts / ~47% FG / ~36% 3P / ~26 ast / ~14 TO)
      were set in `decisions.md` #022 D as "agreed target benchmarks (modern NBA)" with
      no citation, and have never been revisited across nine engine sub-phases. During
      §3.12 the user checked two of them and found figures suggesting **points ~114–117**
      and **FG% ~47–48** — but those came from search summaries, which is *the same
      standard* the originals were set by. Replacing one unsourced number with another
      is not progress.
      **What "done" looks like:** a per-team-per-game figure for each of the five, with a
      **named source and season**, plus a note on whether it is a league average or a
      per-team mean (they differ) and whether pace-adjusted. Record them in
      [calibration.md](calibration.md) — the source-of-truth table created by §3.12 —
      and update the `CalibrationHarness` `(target ~N)` strings in the same change.
      **Two traps worth naming.** (1) **Points and FG% are not independent** — the only
      lever the engine has (shot `BASE_*`) moves both the same direction, so verifying
      them separately and then discovering they are mutually unreachable is the failure
      mode §3.16 exists to avoid; capture *both* before designing the re-solve.
      (2) **Do not promote the remaining plausibility ballparks** (fouls ~19–20, blocks
      ~5) into targets while doing this — #030 G kept them ranges deliberately, and #017's
      don't-fabricate-a-constraint rule applies to targets too.
      **Foul-outs are now IN SCOPE, and are the one number where sourcing may not be
      enough** *(added at §3.13's close-out)*. §3.13 promoted it from ballpark to a **soft
      TARGET (~0.39)** per #031 H — because a phase deliberately tuned against it, so
      someone owns it, **not** because it became sourced. Both competing figures remain
      unsourced (0.11 from #030 G, 0.15–0.25 from a search), so this chore should settle
      it alongside the five. **Then apply §3.16's escalation rule** (roadmap.md): a sourced
      ~0.35–0.45 means no work at all, but a sourced ~0.15 means **escalate to a new
      sub-phase, do not tune** — §3.13's lever is measured *saturated* (a ~3× stronger sit
      curve moves the number by nothing), so closing that gap needs a new mechanic (the
      benched-player timer + #031 C's period-awareness), which is outside what §3.16 does.
      **Two more UNSOURCED rare-event rates joined the list at §3.14** *(added 2026-08)*:
      **technicals** (~0.6–0.8 league-wide / ~0.3–0.4 per team — user-supplied, #032 J,
      shipped at 0.367) and **flagrants** (~0.25–0.40 league-wide / ~0.16 per team —
      #034 G/H, designed). **Both are `ballpark`s, not targets** — nothing is tuned toward
      them, each constant is set from the real-world figure directly — so sourcing them is
      cheaper than the five: a wrong figure means a wrong constant, not a mis-tuned engine.
      ⚠ **But they are the least verifiable rows to check even once sourced**: technicals
      resolve at 11.8%/5.3% relative sd (1 seed / 5 seeds) and **flagrants at 17.4%/7.8%,
      the coarsest in the table**. A sourced figure within ~15% of the landing is not
      distinguishable from the current one at any seed count we run — so **do not spend
      effort re-tuning either against a new source unless it moves by more than that.**
      **Why it matters now:** three consecutive passes (§3.10/§3.11/§3.12) have declined
      the same `BASE_*` trim because it cost more calibrated FG% than the points miss was
      worth. That is evidence the target is wrong, but nobody can *act* on it until the
      real number is known.

- [x] **PROMOTED OUT OF THE BACKLOG → `roadmap.md` §3.15** *(user call, 2026-08:
      "profiles should be a numbered phase")*. **This is no longer a chore** — it is a
      numbered Phase-3 sub-phase needing its own design pass. **Everything below stays
      here as the design-pass input** (it is the accumulated reasoning, not a plan), so
      read it before designing §3.15; the roadmap bullet points back at it.
      **What changed beyond the promotion:** §3.15 is scope-fenced to the
      **developer-facing substrate** (engine + harness, no schema/OpenAPI, consistent
      with every §3.x sub-phase since §3.10). The **player-facing** side the note below
      calls "a separable extension" — selectable **eras**, difficulty, custom rules — now
      has a stated consumer (the user wants it) and is **Phase 4+**, not §3.15.
      **The timing argument below still holds and got stronger:** §3.15 sits after the
      fidelity arc (§3.13 foul trouble, §3.14 flagrants) but **before §3.16**, the
      recalibration — which is the largest multi-config sweep the project will run and
      is exactly what profiles are for. **Validation gate:** §3.15 must reproduce
      **§3.14b's** shipped landing *exactly* before any §3.16 number is read off it (the
      check that validated the §3.11 harness against §3.10's numbers).
      ⚠ **That gate got harder to satisfy at §3.14b, and the reason is worth knowing
      before designing §3.15** (`decisions.md` #034, Status block): §3.14b's severity roll
      is drawn **per foul, and its flagrant-2 sub-roll only on a hit** — a deliberate
      exception to the unconditional-draw discipline §3.13/§3.14a follow, permissible
      because it is nested inside an already-conditional branch. So "reproduce the landing
      exactly" means reproducing a stream whose **draw count varies with how many fouls a
      game happened to produce**. A profile mechanism that changes the *order* constants
      are read in is still safe (they are read before the rolls); one that changes **how
      many** draws a possession takes is not — and it would surface as a total
      reproduction failure rather than a subtle drift, which is the good outcome.
      **A third pass has now paid the cost:** §3.12 ran its `PERIMETER` sweep, its
      `THREE` verification, its `BASE_*` exchange-rate measurement, and a
      multipliers-zeroed triage baseline **all by hand** — editing constants and
      rebuilding between every reading, ~28 harness runs. That is the evidence.

- [ ] **Load the `SimConfig` constants from flat properties files, as SWAPPABLE
      PROFILES the harness can be run against.** *(User direction, 2026-08 —
      **now roadmap.md §3.15**, see above. The text below is the design-pass input.)*
      **The profile framing is the point, and it is a bigger win than "avoid a
      rebuild".** It turns tuning from *sequential edits* (change a constant,
      rebuild, run, write the number down, change it again — the previous config now
      gone unless someone remembered it) into **comparable experiments**: several
      named profiles coexist as files, and the harness runs against each.
      **It composes with the instrument that already exists:** `CalibrationHarness`
      takes **`-DcalibrationSeed=NNNN`** (§3.11) precisely so one config can be
      observed across seeds and tuned to the **mean** (#029 E). A
      `-DcalibrationProfile=<name>` beside it yields the full **profile × seed
      matrix** — which is exactly the sweep §3.11 ran *by hand* ("3-config × 5-seed")
      and §3.12 will have to run again for the foul multipliers. That hand-run sweep
      is the concrete evidence this chore has a real consumer.
      **Natural profiles to start with:** the shipped baseline (whatever `SimConfig`
      currently holds, so a landing stays reproducible), plus one per hypothesis
      under test (e.g. a `THREE = 0.133` vs. `0.20` pair — the exact open question
      §3.12 carries).
      **A separable extension worth not foreclosing:** the same mechanism is how
      **eras** (1990s low-pace/high-foul vs. modern three-heavy) or **difficulty
      settings** would eventually be expressed. That is a *gameplay feature* needing
      a consumer, not this chore (see the "not to be confused with" note below) — but
      the tuning design shouldn't paint it out.
      **The single-location goal is already met and should not be disturbed:** all
      **75** tunable constants live in `SimConfig.java`, with **zero** defined
      anywhere else in the `sim` package (re-verified 2026-08 post-§3.13, which added
      six foul-trouble constants). That invariant has held from §3.2 through §3.13 —
      protect it. What's missing is not a *location* but a
      **workflow**: every calibration change (a `BASE_*` trim, a foul multiplier)
      currently costs an edit + rebuild + re-run, which is real friction in a pass
      that sweeps several values across several seeds.
      **The open design question**, when this is picked up, is whether a profile is
      a **full replacement** (each file carries all 65 constants — self-contained and
      unambiguous, but 65 lines to change one knob, and a new constant must be added
      to every file) or an **override layer** (the Java constants stay the defaults;
      a profile lists only its deltas — far more readable as an experiment, "this
      profile is baseline except `FOUL_MULT_THREE`", at the cost that a profile alone
      no longer tells you the effective config). **The override shape looks better
      for the stated purpose** — comparing hypotheses is exactly a deltas problem —
      but decide it then, with the code in front of you.
      Worth weighing at the same time: `SimConfig`'s javadoc carries the tuning
      *history* — the `BASE_NO_BASKET_FOUL` wrong-way-lever finding (#028), the
      `PROB_FLOOR` trap (#028), the per-rare-event sensitivity reasoning (#025/#029)
      — and those comments have repeatedly stopped real mistakes, so any shape that
      separates a knob from its reasoning, or gives up compile-time key safety, is
      paying something for the convenience. (The override shape keeps the javadoc
      intact by construction, which is a further point in its favour.)
      **Record which profile produced a landing.** Once profiles exist, a harness
      result is only meaningful paired with the profile that generated it — the
      implementation notes in `decisions.md` should name it alongside the seed.
      **A second, separable piece of the same problem: documentation drift.** Values
      are currently restated by hand across `todo.md`, `decisions.md`, and
      `possession-flow.puml` — the §3.12 design pass alone had to update a single
      changed multiplier in five places. A reference table **generated from the
      source** would make that drift structurally impossible; docs would link rather
      than restate. Independent of the properties work and can land separately.
      **Timing — do NOT do this mid-arc.** The window is **§3.15's own design pass**
      (this entry was promoted there — see above): after §3.14 closes the fidelity arc
      and before §3.16 reads any number off it. Changing how constants load *while*
      actively tuning them would forfeit the ability to reproduce a prior landing
      exactly — which is what validated the §3.11 harness (seed 1000 reproduced
      §3.10's shipped numbers before any §3.11 figure was read off it). Same
      one-moving-knob-at-a-time discipline the cap 3→5 idea cites.
      **Not to be confused with** player-facing league/rules settings (difficulty,
      custom rules, selectable eras) — those are *gameplay features* needing a
      consumer, and belong in [ideas.md](ideas.md)/roadmap.md, not this chore. The
      distinction is the audience: this chore serves **the developer at the harness**
      (a real consumer today, evidenced by §3.11's hand-run 3-config sweep); a
      player-facing profile picker serves an end user and has no consumer yet. The
      mechanism could later be shared, which is why the extension is noted above —
      but building for the second audience now would be fabricating ahead of a
      consumer (#014/#017).

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
