# Backlog

Loose tactical chores with **no phase home** — infra/tooling/data-hygiene work
that isn't a product feature and so doesn't belong in a roadmap phase. This file
is stable across phases (unlike [todo.md](todo.md), which is rewritten each phase
to track only the current one).

**Completed chores are REMOVED, not checked off.** A finished chore has a phase home
by definition — its reasoning and landing live in the `decisions.md` entry or roadmap
phase that consumed it, so leaving it here duplicates that record and buries the open
work. (Seven done items were cleared in 2026-08; the file was 30% completed work.)

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
      someone owns it, **not** because it became sourced. ⚠ **§3.20 (#042 J) DEMOTED IT
      BACK TO A BALLPARK** *(noted here 2026-09)* — the ~0.39 was a **landing promoted to
      a target**, i.e. circular, and real basketball sits ~0.1–0.25, *below* the engine.
      So this chore no longer has a target to settle, only a ballpark to source; and
      calibration.md's rule is to **judge by the 4/5/6-foul distribution, not the headline
      count**. Both competing figures remain
      unsourced (0.11 from #030 G, 0.15–0.25 from a search), so this chore should settle
      it alongside the five. **Then apply §3.16's escalation rule** (roadmap.md): a sourced
      ~0.35–0.45 means no work at all, but a sourced ~0.15 means **escalate to a new
      sub-phase, do not tune** — §3.13's lever is measured *saturated* (a ~3× stronger sit
      curve moves the number by nothing), so closing that gap needs a new mechanic (the
      benched-player timer + #031 C's period-awareness), which is outside what §3.16 does.
      **Two more UNSOURCED rare-event rates joined the list at §3.14** *(added 2026-08)*:
      **technicals** (~0.6–0.8 league-wide / ~0.3–0.4 per team — user-supplied, #032 J,
      shipped at 0.367; **reads 0.335 at the §3.22 landing**) and **flagrants**
      (~0.25–0.40 league-wide / ~0.16 per team — #034 G/H, shipped at 0.148; **reads 0.159
      at the §3.22 landing**). ⚠ Both drift with the emergent foul rate rather than being
      re-tuned — their divisor is `PERSONAL_FOULS_PER_TEAM_GAME`, a measured quantity. **Both are `ballpark`s, not targets** — nothing is tuned toward
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

- [ ] **Condense `decisions.md` — a GATE ON STARTING PHASE 4 (user call, 2026-08).**
      ⚠ **The gate and its ORDERING now live in [roadmap.md](roadmap.md)'s
      *Phase 3 → Phase 4 pre-work* section** (step 2, paired with the Java-comment
      sweep) — that is the authority on sequence and constraints; this entry keeps the
      detail. Move it out of this file when that gate is worked (step 6).
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
      ⚠ **RE-MEASURED 2026-09 after §3.22 shipped:** the file is **2,520 lines / 600k
      chars** across **44 entries** — up from 445k/1,066 lines at §3.16 and 369k at §3.14b.
      `#001`–`#020` still average ~1.3k; the **twenty-three** §3.x engine entries average
      **24.6k** and are **94%** of the file. The five largest are unchanged — **#030
      (53.8k), #031 (47.6k), #032 (44.9k), #034 (44.7k), #040 (35.9k)**.
      ⚠ **The mean entry has held at ~22–25k across three measurements — growth is MORE
      entries, not fatter ones.** So the cap is working and is not the lever: **this pass
      targets the five pre-cap 35–54k entries** (~113k, ~19% of the file), not new work.
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
      2026-08): **thinning `possession-flow.puml`** (**64k, 52% of it inside `note`
      blocks** — measured 2026-09; growing ~20% per two phases) and
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
- [ ] **A `BoxScoreReconciler`: derive from `game_event` what CAN be derived, and diff it
      against the persisted `box_score` rows** *(user question, 2026-08, raised right
      after §3.18 shipped — "does anything compute from events? should it?")*.
      **The finding that prompted it:** the project already has the derive-don't-store
      discipline as an established principle — **penalty/bonus status is computed on
      demand from the FOUL log with no stored counter at all** (#028 A1, *"could only
      ever disagree"*), foul-outs and ejections are derived predicates (#023 F, #034), and
      #023 F's stored-state exception has been **refused twice**. ⚠ **The box score is the
      EXCEPTION to that principle, not the rule** — and it is worth stating plainly,
      because the codebase reads as if derivation were the norm.
      **How it works today** (`GameSimulator`, confirmed 2026-08): every column is a
      **populate-at-event-time copy** of an in-memory `PlayerGameState` accumulator —
      `bs.setBlocks(p.getBlocks())` and so on. **Nothing queries `game_event`.** The
      counter and the event are written as *siblings* from the same engine moment, so
      neither derives from the other: that is why they cannot double-count (the question
      that started this), and equally why **nothing structurally forces them to agree**.
      **⚠ THE ANSWER TO "SHOULD IT ALL COMPUTE FROM EVENTS?" IS *PARTLY*, AND THE
      *PARTLY* IS THE WHOLE FINDING.** A full rewrite is the wrong shape:
      - **`minutes` is NOT derivable from any event** — it is a possession-share
        projection in `GameSimulator` (`onFloorPossessions / teamPossessions × 5 ×
        gameMinutes`, §3.5 A) and there is **no game clock and no event behind it**.
        So "compute the box score from events" cannot be uniform: it yields a **hybrid**
        where some columns derive and some do not, which is **worse than either pure
        design** because no reader can tell which is which. **A reconciler that states
        the split per column is the fix; a rewrite that hides it is not.**
      - **Volume**: `isInBonus` scans a few hundred in-memory `EventRecord`s during one
        sim. Deriving a box score means aggregating the persisted log per player per game
        on **every read**, against a table that grows without bound once Phase 5 adds a
        season. #019 declined pagination at current scale — this is the change that would
        reopen that.
      - **#020 already settled the ownership**: the box score is a *denormalized
        convenience on the end-of-game snapshot* (the exact framing #033 used to surface
        `technicalFouls`), **the events are the source of truth**, and a cache with a
        defined source of truth is a legitimate pattern. The failure mode is only ever an
        **undetected** divergence.
      **So the deliverable is a RECONCILIATION, not a replacement.** Derive every
      derivable column for a `gameId`, diff against the stored rows, report per column.
      That buys three things nothing has today: **(1)** one place that records, per
      column, whether it is derivable at all — documenting the hybrid instead of hiding
      it; **(2)** a **repair path** (`game_event` → rebuild the row), which does not exist
      — ⚠ **a box score cannot self-heal**: nothing recomputes, so a row written wrong
      stays wrong on every future read; **(3)** the **per-creditor** form on the columns
      that support it, not just totals.
      ⚠ **§3.18 is what made blocks and steals derivable AT ALL** — before
      `opponent_player_id`, the log could not name the creditor, so a wrong `blocks` value
      was **unrecoverable from the events**. Arguably the phase's most durable outcome.
      **Sketch of the per-column split** (verify before building — 17 columns, all but
      `minutes` event-time copies):
      - **Per-creditor derivable today**: `steals` (`TURNOVER`/`STOLEN` by
        `opponent_player_id`), `blocks` (`SHOT`/`BLOCKED_*` by `opponent_player_id`) —
        **both already asserted** by §3.18's tests, `assists` (`SHOT` by
        `assist_player_id`), `fouls` (`FOUL` by `primary_player_id`, ⚠ **excluding
        `TECHNICAL_FOUL`** — #032 E), `technicalFouls` (that exclusion's other half),
        `turnovers`, `offensiveRebounds`/`defensiveRebounds` (⚠ **exact-match
        `OFFENSIVE`/`DEFENSIVE` — the §3.8 `OUT_OF_BOUNDS_*` rows are NOT rebounds**,
        #026 E), `points`, and the FG/3P/FT attempt+make pairs from the outcome strings.
      - ⚠ **NOT derivable**: `minutes` (above). Any reconciler must **say so explicitly**
        rather than silently skipping it.
      ⚠ **Two traps this must not walk into.** First, it reads `outcome` strings, which are
      **free text — and one has been renamed with NO migration**: §3.17 changed
      `COMMON_FOUL` → `NON_SHOOTING_FOUL` (#040 M/N), so persisted rows hold the OLD
      string before §3.17 and the new one after. ⚠ **Scope this precisely rather than
      over-engineering it**: the engine emits only `NON_SHOOTING_FOUL` today (there is no
      `COMMON_FOUL` left in engine logic), so **only a query reading persisted HISTORY is
      affected** — and #040 N judged the pre-§3.17 rows to be **test data**, so the dual
      match may be unnecessary if that history is declared disposable. **Decide which,
      and say so**; the wrong outcome is a reconciler that silently under-counts fouls on
      old games because nobody made the call. Second, deriving
      `points` re-implements `pointsFromEntity`'s prefix reads; **a second copy of that
      logic is exactly the drift `game-events.md` exists to prevent** — share it or cite it.
      **Timing: Phase 4, NOT §3.19.** Phase 4's stats model aggregates box scores into
      season totals, which is when a drifted row starts **compounding** — that is the
      consumer that makes this real (#014/#017/#020: do not build it ahead of one).
      ⚠ **§3.19 must not touch this** — recalibration needs the numbers to hold still.
      Related: `decisions.md` **#041**'s open follow-up on the `box_score.steals`
      denormalization, which this chore subsumes and generalizes to all 17 columns.

- [ ] **The `project-docs` skill's routing table omits the four DOMAIN-DESIGN docs, so
      they are kept current by noticing rather than by rule.** *(found 2026-08 by the user
      while reviewing §3.17's execution plan, and confirmed during that phase's execution —
      §3.17 had to update `coach.md`, `player.md`, `game.md` and `roster.md` as an
      explicitly-enumerated checklist item because no rule would have caught them.)*
      The skill routes decisions.md / todo.md / roadmap.md / backlog.md / ideas.md /
      risks.md, plus `possession-flow.puml` as a living spec. **`game.md`, `player.md`,
      `coach.md` and `roster.md` are not in the table at all** — yet every engine sub-phase
      touches at least one of them.
      ⚠ **Both worked examples below were REPAIRED by later phases; the chore was not**
      (checked 2026-09) — which is the point: `coach.md:171` said *"`offensiveScheme`
      multiplies only the `PERIMETER` + `THREE` shot weights"*, true from §3.4 until §3.17
      made it exactly false (#040 D), and `player.md:214` carried #021 D's *five skills for
      four shot types* wording — **the origin of the bug §3.17 spent a phase fixing**.
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
      ⚠ **Fixing the docs without fixing the RULE means the next phase rediscovers this.**

- [ ] **`possession-flow.puml` has outgrown one page — thin the PROSE before splitting the
      FLOW.** *(raised 2026-08 by the user; **post-Phase-3**, alongside the `decisions.md`
      condense pass below — both are "the docs outgrew their format" problems and both are
      cheapest once the possession path stops changing.)*
      ⚠ **MEASURE FIRST, AND THE MEASUREMENT ALREADY CONTRADICTS THE OBVIOUS FIX.**
      Re-counted after §3.17 (2026-08) at **846 lines / 11,212px** rendered: **notes 369 ·
      legend 208 · actual flow 269.** **68% of the file is prose** — and note that ratio
      held exactly across a phase that added a fork and a rename, so it is structural, not
      a one-off. (Prior count: 822 lines / 349 · 208 · 265, same 68%.) ⚠ **§3.21 (#043) added a
      fork and two credit sites and it now measures 1,104 lines / 62,000 chars /
      13,342px rendered** — still complete under the required
      ``-DPLANTUML_LIMIT_SIZE=16384``, but **within 19% of that ceiling**, and the flag is
      the only thing preventing a silent truncation at 4096px. **The render height is now
      a reason to do this, not just the prose ratio.** The flow — the part a split
      would divide — **is not the problem**, so splitting first produces four files that are
      each still 68% notes. Do it in this order:
      1. **Thin the notes.** Most re-argue the decision rather than describe the branch —
         the §3.16 note is ~40 lines restating #039 C's FGA arithmetic, which already lives
         in #039 C in better prose. **The diagram needs the pointer, not the argument**:
         phase + decision letters + the one-line crux + the ⚠ trap. Expect this alone to
         roughly halve the file.
      2. **Move the legend out** — 208 lines of probability formulas and clamp rules that
         are identical on every branch. It is reference material, not flow. ⚠ **Send it to
         its OWN small `.puml` or a new reference doc, NOT into `game.md`** — see the
         companion measurement below; `game.md` is already the other half of this problem
         and must not absorb more.
      3. **Re-measure.** Steps 1–2 may well be enough.
      ⚠ **THE OTHER HALF OF THE DUPLICATION IS `game.md`, MEASURED 2026-08** *(user
      question during §3.18: "game.md has a lot of decision references as well — do we
      need this?")*. `game.md` is **45.6k**, and its **`### The calculation sequence`
      subsection alone is 22.5k — half the file** — walking the possession branch-by-branch
      in prose. **That is the same branch order this diagram draws**, so the two are ~72k
      of combined description of one flow, which is exactly the drift failure CLAUDE.md
      names for this file. **Thin them TOGETHER or not at all** — fixing one side alone
      leaves the duplicate authoritative-looking.
      ⚠ **The 253 `#NNN`/§X.Y references in `game.md` are NOT the problem, and this is the
      distinction that matters** *(contrast `calibration.md`, cut 35k → 13k in the same
      session because its citations marked HISTORY sitting in a targets reference)*. In
      `game.md` they mostly annotate **live mechanics** — *"the gate is unchanged, so the
      draw re-partitions the label without moving the count (#027 A)"* is a current fact,
      and the citation is how a reader finds the argument. **Cutting citations there would
      remove navigation, not bloat.** The job is de-duplication against the diagram.
      ⚠ **The hard part, and why this is a RESTRUCTURE rather than a trim:** decide what
      only prose can carry — **the WHY behind an ordering, which CLAUDE.md explicitly calls
      load-bearing** — versus what the picture already shows. Deleting an ordering
      rationale is the one irreversible mistake available here.
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
      **Not §3.17's** (blocks are a §3.19 row, #038's rule) — but §3.19 must not re-tune
      the four `base-block-*` without holding this, or it will tune a constant that does
      nothing and conclude the lever is dead.
      ⚠ **NOT resolved by §3.19 (instrumentation) — that phase was test-side only and
      deliberately did not reroute `blockProbability`.** The effect was SIZED and closed on
      that basis: **~half a blocked three per team-game**, on a row already on target, with
      no consumer for the per-type split. **Carry it as a footnote for §3.20**: if a pass
      ever tunes `base-block-*`, reroute through `clampRareProbability` FIRST, or that lever
      reads dead. The broader half — auditing which other tunables sit under the floor —
      is still open and still homeless.
