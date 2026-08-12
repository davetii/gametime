# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.16).

Current focus: **§3.15 — `SimConfig` profiles**. The full §3.7–§3.16 sequence and
what follows (Phase 4) live in **roadmap.md's "Possession-fidelity completion"
section** — not here (todo.md is current-phase-only). §3.7–§3.13, **§3.14a** and
**§3.14b** have all shipped.

> **⚠ §3.15 NEEDS A DESIGN PASS FIRST — there is no execute-ready plan below.**
> The roadmap bullet is a **seam, not a plan** (every phase so far has found real
> design questions the one-liner hid). **The open questions are the numbered list
> below** ("§3.15 — the open questions for the design pass"); the design-pass *input*
> is [backlog.md](backlog.md)'s parked profiles entry. The next session resolves those
> questions into a numbered `decisions.md #035` (Decisions A, B, C…) **plus** an
> execute-ready plan here. **Write no production code in that session.**
>
> **Q2 is the one with teeth** — the 83 constants are `public static final` and read
> statically from the engine, the resolvers **and the tests**, while `SimConfig` is
> *also* already an injected bean. Loading from a file means giving up `static final`,
> and the blast radius of that is the difference between a small pass and a large one.
>
> **§3.15's validation gate is reproducing §3.14b's shipped landing EXACTLY** —
> flagrants 0.148, points 118.3, FG% 46.9%, penalty rate 51.9% (5-seed mean, seeds
> 1000–5000). A profile refactor that changes any number is a bug, since it is
> meant to move constants out of compile-time storage, not to retune them.
>
> Numbering stays `a`/`b`: **§3.15 (profiles) and §3.16 (recalibration) do NOT
> renumber**, because §3.16 is named in the shipped, never-retro-edited text of
> #030 and #031 (#032 A).

---

## §3.14b close-out (SHIPPED 2026-08 — the handoff §3.15 needs)

Landed as `decisions.md` **#034 A–J** + its implementation note; roadmap bullet is
`[x]`; the flagrants row is in [calibration.md](calibration.md). What §3.15 needs
to know:

- **The landing §3.15 must reproduce exactly** (5-seed mean): flagrants **0.148**,
  flagrant-2s 0.023, ejections **0.027**, points 118.3, FG% 46.9%, 3P% 36.7%,
  assists 27.1, TO 13.6, penalty rate **51.9%**, foul-outs 0.358, fouls 19.35.
- **Five new `SimConfig` constants** for a profile to carry:
  `FLAGRANT_FOULS_PER_TEAM_GAME` (0.16), `FLAGRANT_TWO_SHARE` (0.15),
  `PERSONAL_FOULS_PER_TEAM_GAME` (19.0), `FLAGRANT_EJECTION_LIMIT` (1),
  `FLAGRANT_FREE_THROWS` (2).
- **⚠ `PERSONAL_FOULS_PER_TEAM_GAME` IS NOT A TUNABLE — it is a MEASURED
  assumption** (#034 G). It is the divisor that turns the game-level flagrant rate
  into a per-foul probability, so it describes what the engine *currently does*, not
  what anyone wants it to do. **A profile that varies it independently of the actual
  foul rate silently breaks the flagrant rate.** If §3.15 groups constants by "what a
  tuner may vary", this one belongs outside that set.
- **`resolvePossession` now has FIVE loop re-entry paths** (#034 B) — offensive
  rebound, block recovery, OOB-offense, rebounding foul, flagrant. **A sixth is the
  signal to restructure the loop** — tracked in [ideas.md](ideas.md), and explicitly
  **not** a §3.15 job (a control-flow restructure riding a config refactor would blur
  what moved a number).
- **✅ The cap was RENAMED** (2026-08, post-§3.14b): `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`
  → **`MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`**, with the loop counter
  `offensiveRebounds` → `offensiveRetentions`. Four of the five paths it bounds are not
  rebounds; "retention" is the word the rest of the engine already uses. **A pure
  rename — the value stays 3 and the 5-seed landing reproduced byte-for-byte**, so it
  does not disturb §3.15's validation gate. `PlayerGameState.offensiveRebounds` (the
  box-score stat) is a different thing and was deliberately untouched.
- **`possession-flow.puml` is now structurally COMPLETE** (#034's follow-up) —
  §3.15 and §3.16 add no possession branch, so the diagram should not change again
  in Phase 3.

---

## §3.15 — `SimConfig` profiles: the open questions for the design pass

§3.15 is the **first non-mechanic sub-phase in the arc** — it adds no possession
branch, no event, no rate. It changes **how the 83 tunable constants are loaded** so
that tuning becomes *comparable experiments* rather than sequential edits. Resolve
each of these into a Decision letter in a new `decisions.md` **#035**.

**Read first**: [backlog.md](backlog.md)'s parked profiles entry (the design-pass
input, promoted to this phase by user call) and roadmap.md's §3.15 bullet (the scope
fence). The backlog entry states the *problem* well and even leans an answer on Q1 —
it is **the starting point, not a resolved plan**, and its constant counts are stale
(it says 75 and 65; the real number today is **83**).

> **✅ TWO THINGS ARE ALREADY RESOLVED BY USER CALL (2026-08) — do not re-litigate:**
>
> **(i) Profiles are FLAT PROPERTIES FILES.** Not YAML, not JSON, not Java classes.
> This was already the backlog entry's wording and is now confirmed. Q1 below is
> therefore only about *what a file contains* (all 83 values vs. deltas), **not** about
> the format.
>
> **(ii) THE GOAL IS COMPARING LEAGUE STYLES ACROSS THE HARNESS.** Author several
> named profiles — e.g. a **1990s** low-pace/high-foul style, a **modern**
> three-heavy style, plus the shipped baseline — run the harness against each, and
> **compare the generated stat lines between them**. This is the phase's *purpose*, and
> it is a stronger consumer argument than the one the backlog entry gives (§3.11's
> one-off hand-run 3-config sweep): it is an ongoing workflow, not a single past event.
> **It sits INSIDE the developer-facing scope fence** — engine + harness only, still no
> schema and no OpenAPI. The **player-facing** era picker (an API, persisting which
> profile a league runs) stays Phase 4+.
>
> ⚠ **But it directly pressures Q3, and that is the useful part.** A real 1990s profile
> wants to vary things Q3 might otherwise fence off as immutable "rules" — pace, foul
> rates, and plausibly the bonus threshold or free-throw counts if older rules are ever
> modelled. **So Q3 cannot be answered "lock all rules down" without first asking which
> era knobs this goal needs.** Answer Q3 *against a concrete 1990s-vs-modern profile
> pair*, not in the abstract.
>
> ⚠ **And it raises a NEW question — Q8 below**: a 1990s profile will miss every
> calibration.md target *by design*, so what does a "target" even mean off-baseline?

1. **Full-replacement or override-layer profiles?** *(The backlog entry leans
   **override** — "comparing hypotheses is exactly a deltas problem" — but says to
   decide it with the code in front of you.)* A full replacement file carries all 83
   constants: self-contained and unambiguous, but 83 lines to change one knob, and
   every new constant must be added to every file or the profile is silently
   incomplete. An override layer keeps the Java values as defaults and lists only
   deltas: far more readable as an experiment, but a profile alone no longer tells you
   the effective config. **Whichever wins must answer: how does a reader determine the
   EFFECTIVE config of a run?** (An "effective config" dump from the harness may be the
   thing that makes the override shape safe.)

2. **What happens to `static final`, and is that acceptable?** ⚠ **This is the
   question the backlog entry does not ask, and it is the one with teeth.** All 83
   constants are `public static final` and read *statically* (`SimConfig.PROB_FLOOR`,
   `SimConfig.MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`) from resolvers, the engine
   **and the tests** — while `SimConfig` is *also* already a Spring bean injected in 17
   places for its 20 instance methods. Load-from-file means those fields can no longer
   be `static final`, which costs **compile-time key safety** and touches every call
   site in the `sim` package and its tests. Options to weigh: instance fields on the
   existing bean (call sites change, type safety kept), a parallel profile object the
   constants delegate to, or profiles that only override a **declared subset**. **State
   the blast radius honestly** — this is the difference between a small pass and a
   large one, and it is why the phase needs a design session at all.

3. **Which constants are legally profilable?** Not all 83 are tunables. ⚠
   **`PERSONAL_FOULS_PER_TEAM_GAME` (19.0) is a MEASURED assumption**, not a knob — it
   is the divisor turning the flagrant game-rate into a per-foul probability (#034 G),
   so a profile varying it independently of the actual foul rate silently breaks the
   flagrant rate. Others are **rules**, not tunables: `FOUL_OUT_LIMIT` (6),
   `TECHNICAL_EJECTION_LIMIT` (2), `FLAGRANT_EJECTION_LIMIT` (1),
   `FREE_THROWS_PER_FOUL` (2), `FLAGRANT_FREE_THROWS` (2), `PERIODS`. Decide whether
   §3.15 **enforces** the distinction (a declared allow-list) or merely documents it.
   ⚠ **Answer this against the concrete 1990s-vs-modern profile pair the league-styles
   goal needs, not in the abstract** — that goal is exactly the case where some
   "rules" legitimately vary (pace and foul rates certainly; the bonus threshold and
   FT counts if older rules are ever modelled), so an allow-list drawn without it will
   be drawn too tight. `PERSONAL_FOULS_PER_TEAM_GAME` remains the clear
   **non**-tunable in every era, because it describes the engine rather than the rules.

4. **Where does the tuning history live?** `SimConfig`'s javadoc carries findings that
   have repeatedly stopped real mistakes — the `BASE_NO_BASKET_FOUL` wrong-way lever
   (#028), the `PROB_FLOOR` trap, the per-rare-event sensitivity reasoning (#025/#029),
   §3.14b's emergent-divisor coupling (#034 G). **Any shape that separates a knob from
   its reasoning is paying something real.** The override shape keeps the javadoc intact
   by construction — a further point in its favour, and worth stating explicitly rather
   than leaving implicit.

5. **How is a landing tied to the profile that produced it?** Once profiles exist, a
   harness result is meaningless without knowing which profile generated it. Decide
   whether the harness **prints** the active profile in its report (and whether
   `decisions.md` implementation notes must name it beside the seed, the way they now
   name seeds 1000–5000).

6. **Does the substrate foreclose player-facing eras?** The scope fence is a user call:
   §3.15 is **developer-facing** — engine + harness, **no schema, no OpenAPI**,
   consistent with every sub-phase since §3.10. Player-facing eras/difficulty need an
   API surface, persistence of which profile a league runs, and a rule about what a
   profile may legally contain — **Phase 4+**. The question here is only: does the
   chosen shape *paint that out*? Do not build for it (#014/#017), but do not block it.

7. **Is the doc-drift generator in or out?** The backlog entry pairs a second, separable
   idea with this one: a constants reference table **generated from source**, since
   values are currently restated by hand across todo.md, decisions.md and the `.puml`
   (§3.12's design pass updated one multiplier in five places). It can land separately.
   **Recommend deciding it OUT** unless the profile shape makes it nearly free — but
   decide it deliberately rather than by omission.

8. **What does a calibration TARGET mean when the profile is not the baseline?** *(New,
   forced by the league-styles goal.)* [calibration.md](calibration.md) is the source of
   truth for targets, and the harness prints them inline — `(target ~112 — CONTESTED)`,
   `(target ~26)`, `(target ~0.39)`. **A 1990s profile is SUPPOSED to miss most of
   them**: that is the profile working, not failing. But as it stands its report reads
   as a wall of misses, which is an instrument that lies about the very comparison this
   phase exists to enable. Options to weigh: targets belong only to the baseline profile
   and are **suppressed** elsewhere; each profile **carries its own** expected ranges
   (honest, but authoring a 1990s target set is real research and edges into §3.16's
   job); or the harness prints targets but labels them *baseline-relative*. **Cheapest
   defensible answer is probably to print targets only for the baseline and print
   profile-vs-profile deltas otherwise** — but decide it, because "compare the generated
   stats between styles" is the goal and this is the line the comparison is read off.
   ⚠ Keep it clear of §3.16: that phase owns **sourcing and re-solving the baseline
   targets**, and §3.15 must not quietly invent a second target set on the way past.

**⚠ The validation gate is the hard constraint on all eight** — see the callout at the
top of this file. §3.15 must reproduce §3.14b's landing **byte-for-byte**, so any
answer that changes a value, a rounding, or the **order/count of RNG draws** is wrong
by construction. ⚠ **Read the gate as applying to the BASELINE profile specifically**:
a 1990s profile is *supposed* to produce different numbers — that is the league-styles
goal working. The gate says the **mechanism** is transparent, i.e. running the baseline
profile through the new loader reproduces today's landing exactly. Both halves matter:
without the first, profiles are useless; without the second, §3.16 cannot trust the
baseline it recalibrates from. The cap rename (2026-08) is the worked example of the bar: pure
rename, 5-seed landing identical per seed. Note the specific hazard #034's determinism
note leaves behind — §3.14b's severity roll is drawn **per foul**, and its flagrant-2
sub-roll **only on a hit**, so the draw count varies with how many fouls a game
produced. A profile mechanism that changes the *order* constants are read in is safe
(they are read before the rolls); one that changes **how many draws a possession takes**
is not, and would surface as total reproduction failure rather than subtle drift —
which is the good outcome.

---

### ⚠ Do NOT (standing guardrails — carried forward into §3.15/§3.16)

These outlive any one phase. **§3.15 is a refactor, so it must change no number at
all**; §3.16 owns re-centering and is the only phase that may revisit the last one.

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
  and do NOT raise the cap** (#034 B). 3→5 is a parked idea needing its own
  recalibration pass ([ideas.md](ideas.md)).
- **Do NOT add 2 FTs on top of the underlying foul's award** (#034 C) — the flagrant
  **replaces** it. A flagrant stopped THREE is **2** FTs, not 3 and not 5. This is
  the likeliest bug in the pass.
- **Do NOT add a `flagrant_fouls` box-score column** (#034 H) — a flagrant is already
  inside `fouls`, so #033's parity argument does **not** reach it.
- **Do NOT reuse `pickTechnicalFreeThrowShooter`** (#034 D) — its premise is that
  nobody was fouled (#032 G); on a flagrant somebody was.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Bench and coach technicals** → #032's follow-up. Not modelled (#032 C): a coach is
  not a `PlayerGameState`, so a coach technical would be a committer-less FT.
- **A technical's total lack of causal input** → #032's follow-up. Making it situational
  needs the **same** missing prerequisite as every parked strategic-sub idea:
  game-situation awareness in the rotation step (#031's follow-up, [ideas.md](ideas.md)).
- **✅ The `technicalFouls` counter IS surfaced** → **`decisions.md` #033** (done
  2026-08, immediately after §3.14a): `box_score.technical_fouls`, `BoxScoreEntity`,
  and the OpenAPI `BoxScore`. Unparked on a **parity** argument — the twelfth
  accumulator in a set whose other eleven were already exposed. **`committing_team_id`
  stays parked**; #033's argument does not extend to it.
- **Period-/time-aware foul trouble** → #031 C's follow-up, deferred by §3.13.
- **Getting foul-outs below ~0.38** → #031's follow-up. The lever is **measured
  saturated**; none of the three remaining candidates belong to §3.14.
- **Splitting `substitutionAggressiveness`** → #031 B's follow-up.
- **The over-dispersion finding (#031 A)** → #031's follow-up: the same `pickDefender`
  concentration applies to **blocks, steals, and shot contests**.
- **Strategic substitution as a category** → [ideas.md](ideas.md) (parked, not planned).
- **§3.15 (`SimConfig` profiles)** → roadmap.md. The design-pass input is in
  [backlog.md](backlog.md). **Its validation gate is reproducing §3.14b's landing
  exactly** — which is part of why the clamp consolidation was folded into §3.14a
  instead (#032 H).
- **§3.16 (recalibration against verified targets)** → roadmap.md, the last Phase-3
  sub-phase. Owns the contested points/FG% targets. Its **prerequisite is a backlog
  chore**: verify the benchmarks with real sources — including §3.13's unsourced
  foul-out figure and §3.14a's unsourced technicals ballpark (#032 J).
- **Calibration targets** → [calibration.md](calibration.md), **the source of truth**.
  Update it *and* the `CalibrationHarness` `(target ~N)` strings together.
- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star tuning,
  the `decisions.md` condense pass, harness self-verification) → [backlog.md](backlog.md).
- **Untriaged future-improvement ideas** → [ideas.md](ideas.md). *(Includes the parked
  cap 3→5 tuning idea — do NOT touch `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION` without its
  own recalibration pass.)*
- **§3.6 seams left open by #024**: play-by-play pagination + a `period` filter; the
  per-event time column's storage shape; a lean header-only `GameResult` projection.
- **§3.7 seams left open by #025**: a skilled-blocker recovery edge; shot-clock pressure
  on block-recovered second-chance possessions (→ §3.9-E, still parked #027 D).
- **§3.9 seams left open by #027**: §3.7-E shot-clock pressure; finer turnover sub-types.
- **§3.10/§3.11 seams left open by #028/#029**: `committing_team_id` is **populated and
  queryable but not surfaced on the OpenAPI `GameEvent`**; the observed off/def
  rebounding-foul split (**78/22**) drifts from the configured 75/25.
- **§3.12 seams left open by #030**: the engine shoots **~20 3PA/team/game against the
  NBA's ~35** — a §3.16 input.
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true -DcalibrationSeed=NNNN`) stays in the `sim` test sources.
  Re-run it after any `SimConfig` change.
