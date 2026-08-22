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

- [ ] **MOSTLY DONE — VERIFY THE REMAINING UNSOURCED CALIBRATION BENCHMARKS.**
      ⚠ **The five §3.4 targets were SOURCED in 2026-08 (`decisions.md` #036)** —
      Basketball-Reference league averages, 2025-26 regular season, recorded in
      calibration.md with the season named per row. **That half of this chore is closed.**
      What remains unsourced: **foul-outs** (a soft target), **technicals**, **flagrants**,
      the **minutes distribution**, and — added by §3.16 — the **shooting-foul share**
      behind `sim.non-shooting-foul-share` (see the end of this entry).
      *(Filed 2026-08 from §3.12's close-out; foul-outs added at §3.13's close-out;
      narrowed at §3.16's close-out once #036 landed the five.)*
      ⚠ **NUMBERING**: every "§3.16" below was written when §3.16 meant **recalibration**.
      **#038 renumbered that to §3.19**; the §3.16 slot became foul composition, which has
      **shipped**. Read the phase name, not the number.
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
      #034 G/H, shipped at 0.148). **Both are `ballpark`s, not targets** — nothing is tuned toward
      them, each constant is set from the real-world figure directly — so sourcing them is
      cheaper than the five: a wrong figure means a wrong constant, not a mis-tuned engine.
      ⚠ **But they are the least verifiable rows to check even once sourced**: technicals
      resolve at 11.8%/5.3% relative sd (1 seed / 5 seeds) and **flagrants at 17.4%/7.8%,
      the coarsest in the table**. A sourced figure within ~15% of the landing is not
      distinguishable from the current one at any seed count we run — so **do not spend
      effort re-tuning either against a new source unless it moves by more than that.**
      **Why it mattered:** three consecutive passes (§3.10/§3.11/§3.12) declined the same
      `BASE_*` trim because it cost more calibrated FG% than the points miss was worth.
      ✅ **RESOLVED by #036's sourcing**: the real points target is **115.6**, not the
      unsourced ~112 — so those three passes were right to decline, and the "engine is 2
      points high" reading was an artifact of a wrong target. The composition work that
      followed (§3.16 FTA, §3.17 3PA) is the actual fix.
      **⚠ ONE NEW UNSOURCED NUMBER ARRIVED WITH §3.16 (2026-08), and it is the weakest
      in the file**: `sim.non-shooting-foul-share` = **0.50**, the fraction of stopped-shot
      fouls that award no free throws. It is **BACK-SOLVED against a sourced FTA (23.5),
      not measured from basketball** — the real NBA shooting-foul share is not in a
      league-averages row and needs **play-by-play derivation**, a different and harder
      research job than reading a league-averages table. calibration.md labels it
      *derived, not sourced*. ⚠ **It is also priced by the penalty rate**, so it is not a
      stable constant: anything moving team-periods-in-penalty re-prices it (§3.16's own
      charge fix did exactly that, moving the value from a predicted 0.43 to 0.50).
      Sourcing it is the only thing that would tell us whether §3.16 is *right* or merely
      *self-consistent*.

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
      check that validated the §3.11 harness against §3.10's numbers). **That landing is
      now on the board** (5-seed mean, seeds 1000–5000): flagrants **0.148**, points
      **118.3**, FG% **46.9%**, 3P% **36.7%**, assists **27.1**, TO **13.6**, penalty rate
      **51.9%**, foul-outs **0.358**, ejections **0.027**.
      ⚠ **One §3.14b constant is NOT a tunable and must not be profiled as one:**
      `PERSONAL_FOULS_PER_TEAM_GAME` (19.0) is a **measured** assumption about the
      engine's current foul rate, used as the divisor that turns the game-level flagrant
      rate into a per-foul probability (#034 G). A profile that varies it independently of
      the actual foul rate silently breaks the flagrant rate. If §3.15 groups constants by
      "what a tuner may vary", this one sits outside that set.
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

- [x] **SHIPPED → `decisions.md` #035 (A–I) + its implementation note.** *(Design pass
      and execution both 2026-08.)* **The gate held: the baseline profile reproduces
      §3.14b's landing per-seed byte-for-byte.** Two things this chore's text did not
      foresee, both worth carrying: `@Validated` needed a **Bean Validation
      implementation added to the POM** (the project had none), and the harness's own
      hardcoded `POSSESSIONS_PER_PERIOD = 25` **silently shadowed** the profile's pace
      key — a caller passing its own copy of a tunable value is invisible to the
      properties file, and the effective-config dump is the only instrument that shows
      it. **The text below is the design-pass INPUT and is
      now historical** — read #035 for what was actually decided. Where the two differ,
      #035 wins. **Three things below were resolved differently or more sharply than the
      entry anticipated:** (1) the entry frames this as a *file-format* problem; the real
      obstacle was that `static final` primitives are **compile-time inlined into every
      caller** (JLS §4.12.4), so the constants had to become **instance state** — 323 call
      sites, #035 B; (2) the shape is **override-layer** as the entry leaned, but over
      **57 profilable constants** (26 static), with only rules + model machinery + the
      measured `PERSONAL_FOULS_PER_TEAM_GAME` left static (#035 C); (3) the doc-drift generator
      below was decided **OUT** and remains a separate chore (#035 H).

- [x] **Load the `SimConfig` constants from flat properties files, as SWAPPABLE
      PROFILES the harness can be run against.** *(User direction, 2026-08 — **shipped
      as roadmap.md §3.15**, see above. The text below is the design-pass input,
      superseded by #035 and kept only as the accumulated reasoning.)*
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
      **83** tunable constants live in `SimConfig.java`, with **zero** defined
      anywhere else in the `sim` package (re-verified 2026-08 post-§3.14b, which added
      five flagrant constants; §3.13 added six foul-trouble ones). That invariant has
      held from §3.2 through §3.14b — protect it. *(The only other `public static
      final` in the package is `GameData.TECHNICAL_FOUL_OUTCOME` — an event-vocabulary
      **string**, not a tunable, and §3.14b's two `FLAGRANT_FOUL_*` outcome strings sit
      on `PossessionEngine` for the same reason. Event vocabulary is not profilable and
      is out of scope.)* ⚠ **They are `public static final`
      and read STATICALLY** from the engine, the resolvers and the tests — while
      `SimConfig` is *also* already a Spring bean injected in 17 places for its 20
      instance methods. **That split is the real design problem** (todo.md's Q2), not
      the file format. What's missing is not a *location* but a
      **workflow**: every calibration change (a `BASE_*` trim, a foul multiplier)
      currently costs an edit + rebuild + re-run, which is real friction in a pass
      that sweeps several values across several seeds.
      **The open design question**, when this is picked up, is whether a profile is
      a **full replacement** (each file carries all 83 constants — self-contained and
      unambiguous, but 83 lines to change one knob, and a new constant must be added
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

- [ ] **A constants-reference table GENERATED FROM SOURCE, so docs link rather than
      restate.** *(Split out of the profiles entry above at §3.15's design pass, 2026-08 —
      **explicitly decided OUT of §3.15** by `decisions.md` #035 H, deliberately rather
      than by omission, so it needs a home of its own.)*
      **The problem:** constant values are restated by hand across `todo.md`,
      `decisions.md` and `possession-flow.puml` — the §3.12 design pass alone had to update
      a single changed multiplier in **five places**. A generated table would make that
      drift structurally impossible.
      **Why it was kept out of §3.15:** the profile shape does not make it nearly free, and
      folding a doc-generation tool into a pass gated on **byte-for-byte reproduction**
      adds surface for no gate-relevant benefit. **Independent; can land any time.**
      ⚠ **Re-scope it after §3.15 ships.** #035 F's *effective-config dump* (the harness
      printing every profilable constant with its value and origin) serves the cheaper half
      of this need from the runtime side, and #035 C splits the constants into two declared
      groups — both change what a generated table should contain, so measure what is
      actually still restated by hand before building it.

- [ ] **Condense `decisions.md` — NOW A GATE ON STARTING PHASE 4 (user call, 2026-08).**
      **This entry is the plan; no design pass is needed.** It is scheduled as
      **Phase 4 pre-work** — after the Phase-3 tail, before Phase 4 — and roadmap.md
      carries the gate (deliberately *not* numbered as a §3.x: every §3.x is an engine
      mechanic). Two reasons for that slot: **recalibration is the heaviest consumer of
      this file**, so compressing before it risks cutting what it needs; and **Phase 4 is
      when the second reader arrives** — a stats/consumer phase whose "can I add a
      column?" question is a `#014`/`#017`/`#020` one-liner buried under engine reasoning.
      ⚠ **NUMBERING**: this entry originally said "after §3.16," meaning **recalibration**.
      **#038 renumbered that to §3.19** and §3.16 became foul composition, which has now
      shipped. The gate is **after §3.19**, not after §3.16 — three sub-phases later than
      a literal reading suggests.
      ⚠ **RE-MEASURED 2026-08 after §3.16 shipped:** the file is **1,066 lines / 445k
      chars**, `#001`–`#020` still average ~1.3k, and the **nineteen** §3.x engine entries
      average **22.0k** — still ~17× the early entries, and ~94% of the file. The five
      largest remain **#030 (53.8k), #031 (47.6k), #032 (44.9k), #034 (44.4k), #035
      (35.3k)**, all written *after* this entry was filed.
      **✅ THE SIZE CAP IS HOLDING, now across three consecutive entries** — the
      `project-docs` proportionality rule (~15–20k per entry, implementation note ≲6k):
      **#035 at 22.3k**, then **#036/#037/#038 small**, then **#039 (§3.16) at 21.4k**
      *including* its implementation note. Both large ones are over budget but land at
      **~half the 44k trend** of the four entries before the rule, and the engine-entry
      average has started **falling** (24.4k → 22.0k) for the first time. The rule is
      doing what it was written to do; this pass is still needed for the pre-rule five.
      *(Historical, as filed:)* The file is 443
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
      ⚠ **TWO SIBLING CHORES ARE NOW PARKED ABOVE AND SHARE THIS ONE'S CAUSE** (added
      2026-08): **thinning `possession-flow.puml`** (68% of it is prose, not flow) and
      **sweeping the `sim` package's Java comments**. All three are the same problem —
      reasoning restated at every site instead of cited once — and the Java sweep in
      particular should run in the **same pass** as this one, because Java comments cite
      `decisions.md` **by number** and the never-renumber constraint below protects both.
      **Constraints**: keep it to **ONE file** (a `decisions.md` / `decisions-sim.md`
      split was tried 2026-07 and reverted — user wants one file; the split also moved
      volume around without reducing it). Never renumber; `#NNN` refs are cited from
      prose *and* Java comments (e.g. `decisions.md #026 E` in `MissedShotResolverTest`),
      and they cite the number, not a path. ~~Update the `project-docs` skill in the
      same pass — it still says "append at the bottom, never renumber" with **no size
      guidance**~~ — **DONE AHEAD OF THE PASS (2026-08)**, deliberately, because
      waiting would have let #035 and #036 re-grow at the 44k trend and enlarged this
      chore by ~90k before it ever ran. The skill now carries a per-entry budget
      (~15–20k, implementation note ≲6k), names the three sections that bloat
      (Alternatives / Trade-off / the implementation note) and the three things that
      must never be compressed away (the crux, the final constants, the traps), and
      the "Before you finish" checklist now checks size. **What remains for this pass is
      the compression itself** plus the index table + principles preamble above.
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
- [ ] **Harness: track and report STEALS, with a reconciliation check.** ⚠ **Steals are
      the only contested credit the harness never prints.** It accumulates fga/fgm/tpa/
      tpm/assists/turnovers/offReb/defReb/blocks — **but not steals** — and the
      reconciliation invariant covers assists and blocks and **not** steals. Meanwhile
      `SimConfig`'s `TO_WEIGHT_STOLEN = 56.0` javadoc says the weight is held at ~56% of
      the cause mix precisely so **`BoxScore.steals` does not drift** — i.e. the constant
      is protecting a number nothing has ever measured.
      **The data is all there**: `PlayerGameState.recordSteal()` is called at
      `PossessionEngine:176`, the `steals` column is populated, and a `STOLEN` TURNOVER
      event is emitted. **A count-based reconciliation works TODAY with no engine
      change** — `STOLEN` events vs. summed box-score steals, exactly the shape §3.7 uses
      for blocks (which also name the victim, not the creditor, and reconcile by count).
      **The real figure is 8.4/team/game** (sourced 2025-26, in calibration.md as
      `observed`). The engine's value is **~7.67 by derivation** (13.8 turnovers × 55.6%
      STOLEN) — ⚠ **a derivation, not a measurement**, which is the point of this chore.
      Cheap: one accumulator, one report line, one reconciliation check. **Test-only, no
      engine change.** Do it in whatever phase next touches the harness.

- [ ] **The `project-docs` skill's routing table omits the four DOMAIN-DESIGN docs, so
      they are kept current by noticing rather than by rule.** *(found 2026-08 by the user
      while reviewing §3.17's execution plan.)*
      The skill routes decisions.md / todo.md / roadmap.md / backlog.md / ideas.md /
      risks.md, plus `possession-flow.puml` as a living spec. **`game.md`, `player.md`,
      `coach.md` and `roster.md` are not in the table at all** — yet every engine sub-phase
      touches at least one of them, and §3.17 alone makes **`coach.md:171` factually wrong**
      (*"`offensiveScheme` multiplies only the `PERIMETER` + `THREE` shot weights"* — #040 D
      makes THREE take the lean and PERIMETER its reciprocal) and leaves **`player.md:214`**
      still describing shot selection with #021 D's five-skills-for-four-types wording,
      **the very phrasing that caused the bug §3.17 fixes**.
      ⚠ **The failure mode is silent and asymmetric**: a stale planning doc gets caught
      because the next design pass reads it start to finish; a stale *domain* doc is read
      by whoever is learning the model, who has no way to know it is wrong. **`coach.md` is
      the sharpest case** — it is the reference for what the five coach attributes mean, and
      a reader who trusts line 171 after §3.17 would build against an axis that no longer
      exists.
      **Fix**: add a row per domain doc to the routing table (what each owns, what it does
      NOT), and extend the skill's "Before you finish" checklist with a domain-doc check
      alongside the existing `possession-flow.puml` one. ⚠ **Worth pairing with a
      grep-based reality check** — the anchors above were all found by grepping for
      `shotTypeWeight` / `offensiveScheme` / `COMMON_FOUL`, which suggests the rule should
      be *"grep the domain docs for every identifier the phase renamed or re-specified"*
      rather than a prose reminder to remember.
      **Do NOT fix this inside a phase's execution** — a skill edit mid-execution is scope
      creep. §3.17's plan carries the doc updates it owns and files this separately.

- [ ] **`possession-flow.puml` has outgrown one page — thin the PROSE before splitting the
      FLOW.** *(raised 2026-08 by the user; **post-Phase-3**, alongside the `decisions.md`
      condense pass below — both are "the docs outgrew their format" problems and both are
      cheapest once the possession path stops changing.)*
      ⚠ **MEASURE FIRST, AND THE MEASUREMENT ALREADY CONTRADICTS THE OBVIOUS FIX.**
      Re-counted after §3.17 (2026-08) at **846 lines / 11,212px** rendered: **notes 369 ·
      legend 208 · actual flow 269.** **68% of the file is prose** — and note that ratio
      held exactly across a phase that added a fork and a rename, so it is structural, not
      a one-off. (Prior count: 822 lines / 349 · 208 · 265, same 68%.) The flow — the part a split
      would divide — **is not the problem**, so splitting first produces four files that are
      each still 68% notes. Do it in this order:
      1. **Thin the notes.** Most re-argue the decision rather than describe the branch —
         the §3.16 note is ~40 lines restating #039 C's FGA arithmetic, which already lives
         in #039 C in better prose. **The diagram needs the pointer, not the argument**:
         phase + decision letters + the one-line crux + the ⚠ trap. Expect this alone to
         roughly halve the file.
      2. **Move the legend out** — 208 lines of probability formulas and clamp rules that
         are identical on every branch. It is reference material, not flow; it belongs in
         `game.md` or its own small `.puml`.
      3. **Re-measure.** Steps 1–2 may well be enough.
      ⚠ **STEP 0, ADDED 2026-08 AFTER THE USER FOUND A REAL DEFECT: AUDIT THE BOXES FOR
      DRIFT BEFORE TOUCHING ANY PROSE. This chore is about SIZE and would NOT have caught
      it.** The pre-shot foul drew `FOUL — SHOOTING_FOUL` as a step at the TOP — accurate
      when it was the only outcome of that roll. §3.14b then inserted a flagrant fork above
      it and §3.16 a non-shooting fork below; **each was added correctly and in the same
      change**, and `SHOOTING_FOUL` silently became the **fall-through** while still being
      drawn as the entry step. The picture claimed one foul could emit up to three events;
      the engine emits exactly one. Fixed during §3.17 (the top box is now
      `defender.recordFoul()` — the charge, which genuinely has no fork — and
      `SHOOTING_FOUL` sits at the bottom labelled as the fall-through).
      **The audit**: walk every partition where a LAYERED roll was added later — the three
      foul sites are the known family — and ask *"does this still read as ONE event coming
      out?"* The and-1 and rebounding sites were checked and are correct; the pre-shot site
      was the only one carrying **two** stacked forks, which is why it drifted.
      ⚠ **AND THIS CHANGES STEP 1, WHICH IS CURRENTLY WRONG ABOUT ITS OWN TARGET.** Step 1
      treats notes that "re-argue the decision rather than describe the branch" as pure
      verbosity. **Some of them are load-bearing corrections to WRONG BOXES** — the foul
      branch's "REPLACES the 2/3 FTs above" and "the two never compose" existed precisely
      because the boxes said otherwise. **Thinning those without fixing the boxes deletes
      the only thing telling a reader the picture is wrong**, making the file smaller and
      less correct. So: **fix the box, THEN cut the note that was compensating for it.**
      **The general tell, worth keeping**: when a note is busy explaining that the boxes do
      not mean what they appear to mean, that is a defect signal, not a clarification —
      and it is the cheapest way to find drift like this. (Now also in CLAUDE.md.)
      4. **Only then split**, and **split by PHASE OF POSSESSION, not by sub-phase number** —
         the seams the engine already has: setup/rotation · turnover+foul · shot+block ·
         rebound+retention. A reader hunting "where does the and-1 fire" then knows which
         file without opening all four.
      ⚠ **THE COST OF SPLITTING, AND WHY IT IS LAST:** CLAUDE.md makes this diagram the
      place that records **branch ORDER within a partition**, which is load-bearing (the
      flagrant roll precedes the common-foul roll; the block roll is carved off the top).
      **Split across files and the CROSS-FILE ordering becomes invisible** — you would need
      an index diagram just to restore what one file gives for free. The second risk is the
      living-spec rule ("a new branch must be drawn in the same change"): one file makes
      that unambiguous, four files make a design pass guess which — **and that failure is
      silent**, the same shape as the §3.16 harness-instrument break.
      **Keep whatever shape ships**: the render command with `-DPLANTUML_LIMIT_SIZE=16384`
      and the truncation warning (a plain `-tpng` silently cuts at 4096px, and `-checkonly`
      does not catch it), and the rule that a new branch or event is drawn in the same change.

- [ ] **Sweep the Java comments in the `sim` package for the same bloat `decisions.md` has.**
      *(raised 2026-08 by the user; **post-Phase-3**, and best run in the SAME pass as the
      `decisions.md` condense below — the two share a cause and, more importantly, a
      constraint.)*
      Every sub-phase since §3.7 has left long block comments in the engine re-arguing its
      decision at the call site — `PossessionEngine`'s foul block alone carries ~40 lines of
      §3.16 prose restating #039 C, and `FoulResolver` / `SimConfig` / `ShotResolver` are
      comparable. **Measure the ratio before deciding anything** (the `.puml` chore above
      turned out to be 68% prose, which changed the recommended fix — do the same here).
      ⚠ **DO NOT RUN THIS AS A BLANKET STRIP, AND THE PROJECT ALREADY HAS EVIDENCE WHY.**
      `SimConfig`'s javadoc carries the tuning *history* — the `BASE_NO_BASKET_FOUL`
      wrong-way-lever finding (#028), the `PROB_FLOOR` trap (#028), the per-rare-event
      sensitivity reasoning (#025/#029) — and **those comments have repeatedly stopped real
      mistakes** (the profiles entry above says so in its own words). The §3.12 `clampRare`
      comment is the clearest case: it explains why the floor is deliberately absent, and a
      reader who "tidied" it would re-break a fixed bug.
      **The rule to apply is the same one the `project-docs` skill now applies to entries:**
      keep the **crux**, the **final constants** and the **traps**; cut the **re-argued
      alternatives**, the **restated trade-offs**, and the **narration of what the code
      plainly does**. A call site should say *what invariant holds here and what breaks if
      you move it*, then cite `#NNN` for the argument. **The `#NNN` citation is what makes
      the cut safe** — the reasoning is not being deleted, it is being de-duplicated against
      the entry that owns it.
      ⚠ **Java comments cite `decisions.md` by NUMBER, not by path** (e.g. `#026 E` in
      `MissedShotResolverTest`) — so the condense pass's never-renumber constraint protects
      this chore too, and running them together means one person holds both halves of that
      contract. **Behavior-neutral by definition: no test should change.**

- [x] **Harness: print the four-way SHOT-TYPE MIX, including stopped shots.**
      **DONE by §3.17's execution (2026-08, `decisions.md` #040 C).** The report now
      carries a `Shot-type mix` block printing BOTH shares per type: **CHARGED** (the
      share of FGA, which is what the 3PA/FGA target reads) and **DRAW** (charged plus
      the corrected stopped shots, which is what `sim.shot-share-*` actually sets). It
      was promoted out of "not a §3.17 blocker" during execution: tuning four share
      values against 3PA alone makes a landing unattributable, which is the expensive
      failure this phase was warned about. ⚠ **One honest limit, labelled in the report:**
      the stopped-TWO tally is not resolvable per type from the event log, so it is
      apportioned across the three two-point types in their charged proportion. **The
      THREE row — the one being tuned — is exact.** ⚠ **§3.17's
      design pass had to BACK-SOLVE the number its whole phase turns on** — the report has
      no shot-mix row, so the draw share was reconstructed as
      `(3PA + true stopped threes) ÷ (FGA + stopped shots)` = 20.9%, using a stopped-three
      figure that itself needed the broken-instrument correction below. **A four-way row
      (DRIVE / PERIMETER / POST / THREE) would have made it a reading.**
      ⚠ **It must count DRAWS, not attempts** — a stopped shot charges no FGA (the foul
      branch returns before `recordFieldGoalAttempt()`), so an attempts-only row understates
      the drive/post share by exactly the amount that matters. Read the `ShotType` off the
      SHOT event and off the stopped-shot FOUL event (the same fix the chore below makes).
      **Test-only, no engine change.** Not a §3.17 blocker — that phase is tuned against
      3PA/FGA, which is sufficient — but the next mix question should be measurable
      directly. Composes with the chore below; do them together.

- [x] **Harness: classify a stopped shot by `ShotType`, not by its free-throw count —
      the fouled-three rows have under-counted by ~2× since §3.16.** ⚠ **A BROKEN
      INSTRUMENT, NOT A BROKEN ENGINE — do not re-tune `sim.foul-mult-three` against
      these rows.**
      `CalibrationHarness.flushStoppedShot` infers *"was this a stopped THREE?"* from the
      **free-throw run** (3 FTs ⇒ a three, else a two). That was a faithful proxy while
      every stopped shot awarded free throws. **§3.16 broke it**: a `COMMON_FOUL` awards
      **0** FTs outside the penalty and **2** inside it, never 3, so a fouled three that
      converts is either invisible (`freeThrowCount == 0` returns early) or miscounted as
      a two. At the shipped 0.50 share the harness sees about half of them — measured
      **1.50%** of 3PA against a true **~3.0%**, and `1.50 ≈ 3.0 × (1 − 0.50)` matches to
      two decimals, which is what identifies it as an artifact rather than a rate change.
      **Fix**: read the `ShotType` off the shot/foul event instead of counting FTs — the
      harness already has `shotTypeOf(outcome)` for the shot vocabulary. **Test-only, no
      engine change.**
      ✅ **DONE by §3.17's Step 0 (2026-08), BUT NOT THE WAY THIS CHORE OR #040 G
      SPECIFIED — and the divergence is worth reading.** Both said *"read the `ShotType`
      off the shot/foul event"*. ⚠ **That is not possible from the event log.** A stopped
      shot emits **no SHOT event at all** (the foul branch returns before
      `recordFieldGoalAttempt()`), and neither `SHOOTING_FOUL` nor `NON_SHOOTING_FOUL`
      carries a type suffix the way `MADE_*` / `MISSED_*` / `BLOCKED_*` do. Adding one
      would be an **engine** change to the permanent play-by-play vocabulary — outside a
      step scoped test-only, and outside the single rename #040 M authorises.
      **What shipped instead is an EXACT correction, not an estimate**, and it is
      available because of how §3.16 built the roll: `FoulResolver.isNonShootingFoul` is a
      **flat, shot-type-independent** draw (#039 E deliberately refused to skill- or
      type-weight it), so the fouls that stay `SHOOTING_FOUL` are an **unbiased sample**
      of all stopped shots taken at rate `(1 − share)`. The harness divides the visible
      tally by that fraction, reading the divisor from the **active** config so an era
      profile that moves the share keeps the instrument honest. Both the raw visible and
      the corrected figure are printed. Measured: raw 1.58% of 3PA → corrected 3.16%,
      the predicted ~2×.

- [ ] **Harness self-verification — assert the instrument's own invariants.**
      ⚠ **PARTIALLY DONE by §3.15 (#035 A/F): the load-bearing half below — "have the
      harness print the constants it actually ran with" — is BUILT.** The report now
      names the active profile list and dumps every tunable constant's effective value.
      It has already earned its place: it caught §3.15's own harness bug, where a
      hardcoded `POSSESSIONS_PER_PERIOD = 25` shadowed the profile's pace key, which is
      exactly misfire (1) below in a new guise. **What remains open:** the FT-source
      counts summing to total FTs with no `UNKNOWN`, and points reconciling with the
      event log. The original text follows.
      The
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
- [ ] **Clear the 5 open Dependabot alerts — all in `gametime-frontend`, none in the
      Maven service.** *(Surfaced 2026-08 on a `git push`; GitHub reports them against
      the default branch. Filed here rather than in a phase: it is dependency hygiene,
      not product work.)*
      **The whole set is npm dev/build tooling and every one is a TRANSITIVE dependency
      — `package.json` declares none of them directly.** Measured 2026-08 against
      `main`'s `package-lock.json`:

      | Sev | Package | Locked | Fixed in | Advisory |
      |---|---|---|---|---|
      | high | `vite` | 8.0.14 | **8.0.16** | `server.fs.deny` bypass on Windows alternate paths (GHSA-fx2h-pf6j-xcff) |
      | high | `postcss` | 8.5.15 | **8.5.18** | path traversal via `sourceMappingURL` auto-loading (GHSA-r28c-9q8g-f849) |
      | high | `brace-expansion` | 5.0.6 | **5.0.7** | DoS via exponential-time expansion (GHSA-3jxr-9vmj-r5cp) |
      | med | `vite` (`launch-editor`) | 8.0.14 | **8.0.16** | NTLMv2 hash disclosure via UNC paths on Windows (GHSA-v6wh-96g9-6wx3) |
      | low | `@babel/core` | 7.29.0 | **7.29.6** | arbitrary file read via `sourceMappingURL` (GHSA-4x5r-pxfx-6jf8) |

      **This is very likely a lockfile refresh, not a manifest change** — each locked
      version is exactly one patch behind its fix, and the only one declared in
      `package.json` (`vite`, `^8.0.12`) already permits `8.0.16`. So `npm audit fix`
      (or `npm update`) plus a `npm run build` + `npm run lint` check is the probable
      whole job. **Verify that before assuming it** — a transitive bump can still be
      pinned by an intermediate package.
      **Real severity here is lower than "3 high" suggests, and that is worth stating so
      nobody either panics or dismisses it.** All five are **build-time / dev-server**
      tooling, two of the highs are **Windows-specific** (this project builds on
      darwin), and the frontend is **not yet deployed** — CLAUDE.md still describes it as
      a "future React frontend" and **Phase 7** owns it (a scaffold exists from an
      `initial front end application` commit, but nothing builds or ships from it as part
      of the service). There is no running service exposed by any of these today. **But it is cheap to clear and it will otherwise sit in the
      repo's security tab flagging every push**, so the cost of leaving it is the
      alert-fatigue cost of a permanently non-green signal.
      ⚠ **Do not fold this into an engine PR.** It touches no Java, no `SimConfig`, and
      nothing the `CalibrationHarness` measures — a lockfile bump riding a phase branch
      would make the phase's "reproduces the landing exactly" claim harder to read, for
      no benefit. Its own small PR.
      **Worth deciding at the same time**: whether to enable Dependabot *version* updates
      (not just security alerts) for `gametime-frontend`, so a dormant frontend does not
      accumulate a fresh batch of these before Phase 7 ever starts.
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

- [ ] **The `project-docs` skill's routing table omits the FOUR domain-design docs, so
      every phase has kept them current by NOTICING rather than by RULE.** Found 2026-08
      by the user while reviewing §3.17's plan, and confirmed during that phase's
      execution — §3.17 had to update `coach.md`, `player.md`, `game.md` and `roster.md`
      as an explicitly-enumerated checklist item because no rule would have caught them.
      The skill's table lists the six planning docs (`decisions.md`, `todo.md`,
      `roadmap.md`, `backlog.md`, `ideas.md`, `risks.md`) plus `possession-flow.puml`,
      and **names `game.md` / `player.md` / `coach.md` / `roster.md` nowhere**.
      ⚠ **The failure mode is silent and it has a worked example.** `coach.md` line 171
      said *"`offensiveScheme` multiplies only the `PERIMETER` + `THREE` shot weights"* —
      **true from §3.4 until §3.17 made it exactly false** (#040 D split them in opposite
      directions). Nothing would have flagged it. `player.md`'s line 214 was worse: it
      carried #021 D's *five skills for four shot types* wording, **which is the origin
      of the bug §3.17 spent a phase fixing** — the stale doc was not just wrong after
      the fact, it had been describing the defect as the design for four phases.
      **The fix**: add the four docs to the skill's routing table with the trigger that
      routes to each — a shot-selection or coach-modifier change touches `coach.md` +
      `player.md`, an event-vocabulary change touches `game.md` + `roster.md`, a
      possession-branch change already routes to the `.puml`.
      ⚠ **Deliberately NOT fixed inside §3.17** — a skill edit mid-execution is scope
      creep, and the phase filed it rather than absorbing it. **Fixing the docs without
      fixing the rule means the next phase rediscovers this**, which is the whole reason
      it is written down here.

- [ ] **`PROB_FLOOR` (0.02) silently dominates `sim.base-block-three` (0.005) — the
      constant is INERT, and the same clamp may be flooring other rare rates.** Found
      2026-08 by §3.17's execution (`decisions.md` #040 implementation note), where #040 E
      predicted blocks would fall 4.8 → ~2.6 as the shot mix went three-heavy and they
      **stayed at 4.80**. `SimConfig.blockProbability` computes
      `base + BLOCK_SENSITIVITY × (blockSkill − finishing) / 10` and then **clamps to
      `PROB_FLOOR`** — which is **4× larger than the THREE base**. So a three's block
      probability is **floored, not based**: moving attempts onto threes takes them
      0.056 → **0.02**, never → 0.005.
      ⚠ **Two consequences, and the second is the broader one.** (1) `base-block-three`
      cannot currently be tuned at all — lowering it changes nothing, raising it does
      nothing until it clears 0.02. (2) **`PROB_FLOOR` is applied to every probability in
      the engine**, so any other constant set below 0.02 is equally inert, and nothing
      says so at the declaration site. **Audit which tunables sit under the floor** and
      either annotate them or reconsider whether a single global floor is right for rates
      that legitimately differ by an order of magnitude.
      **Not §3.17's** (blocks are a §3.19 row, #038's rule) and **not a blocker** — but
      §3.19 must not re-tune the four `base-block-*` without holding this, or it will tune
      a constant that does nothing and conclude the lever is dead.

- [ ] **A test asserts a ~13% RANDOM EVENT on a pinned seed, and it is ALSO order-dependent
      — so a green local `mvn install` does not prove it passes.** Found 2026-08 when CI
      failed on §3.17's branch after two clean local full builds.
      `GameSimulatorIntegrationTest.technicalFoulsArePersistedOnTheBoxScoreAndReconcileWithTheEvents`
      opens with a **precondition** — *"a 40-possession game must produce at least one
      technical"* — that keeps its reconciliation from passing vacuously. ⚠ **That is not
      an invariant.** The per-check technical probability is **~0.00175**, so 40
      possessions × 2 teams expects **~0.14** technicals: "at least one" fires on roughly
      **one seed in eight**. It has now broken **twice** on passes that touched neither
      technicals nor fouls — §3.14b (flagrant roll consumed an extra draw, seed 7 → 9) and
      §3.17 (the shot mix changed `pickShotType`'s result on nearly every possession, seed
      9 → 12). Each time the fix was to re-measure and re-pin, which works and does not
      scale.
      ⚠ **THE ORDER-DEPENDENCE IS THE BIGGER HALF, AND IT DEFEATS THE LOCAL GATE.**
      Measured: the test **fails in isolation and passes in the full suite on the same
      seed**. `V1ApiDelegateimplTest` is **not** `@Transactional` and commits roster rows;
      that changes who is on the floor, which changes the RNG consumption pattern
      downstream. **So the suite passing is the lucky ordering, not the honest result** —
      exactly how §3.17 shipped a red branch after `mvn clean install` reported
      `BUILD SUCCESS` twice.
      **The durable fix (pick one, do not keep re-pinning):** raise the possession count
      until at least one technical is near-certain; or assert the reconciliation identity
      over a **batch of seeds** and drop the precondition entirely — the identity
      (`events == box-score column`) is what the test is actually for and it holds at zero
      technicals too, it just proves nothing there. **Also worth fixing independently:**
      make `V1ApiDelegateimplTest` `@Transactional`, or give the sim tests their own
      fixture, so test order stops changing simulation output.
      ⚠ **Audit for siblings before closing**: any other fixed-seed test whose assertion
      depends on a rare event firing. Grep for seed literals in `sim` tests.
