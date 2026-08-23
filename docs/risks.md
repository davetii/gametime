# Risks & Concerns

Active risks and concerns only. Delete an item when it's resolved — git history
records when/why, and the substantive resolution lives in the relevant
decisions.md entry. Keep every line here a *live* concern.

---

### Simulation performance at scale
**Severity**: Medium  
**Description**: A full season is potentially 40 teams x 40+ games = 800+ games. Each game is ~200 possessions producing ~200–350 `GameEvent` rows (all persisted, decisions.md #020), so a season is 160,000+ possessions to compute and hundreds of thousands of event rows to write. The live concern is **season-scale batch throughput** — computing + persisting that volume across 800 games, arriving with **Phase 5 (season simulation)**. **Note (updated §3.6):** the *single-game* read path is settled and is NOT a concern — #024 D counted the actual event volume (~200–350/game, well under ~100KB JSON) and shipped `GET /{gameId}/play-by-play` **unpaginated by design**, retiring the earlier #019 "large result set / paginate at §3.6" worry for this endpoint. Pagination + a `period` filter remain a clean additive option if a future *season-log* or multi-game read ever needs it (#024 D seam), but that is not this risk.  
**Mitigation**: Benchmark the season batch when Phase 5 runs many games; keep the pure simulation side-effect-free (decisions.md #021) so batch/season runs can persist efficiently (bulk inserts, streamed writes). The per-game read endpoint needs no bounding at current scale.

### Skill formula balance
**Severity**: Low *(was Medium — reduced after §3.4)*  
**Description**: The skill calculators have hand-tuned threshold values. The blanket "we won't know until games are simulated" trigger has **fired**: §3.4's `CalibrationHarness` ran ~100 games and the **team-level aggregates landed on the targets as they stood then** (~112 pts / 47% FG / 36% 3P / 26 ast / 14 TO, decisions.md #022; held under §3.5 fatigue, #023). ⚠ **Those targets were unsourced and the points figure was WRONG** — #036 later sourced it at **115.6** — so read this as "the formulas produce coherent team aggregates," not as a validated landing. What remains unvalidated is narrower: **individual-player edge cases** (e.g. a maxed shotSkill + shotSelection player being unrealistically dominant) — the aggregate mean can be right while the tails are off.  
**Mitigation**: The harness already guards the aggregates — re-run it after any `SimConfig`/formula change (see the calibration-drift risk below). For the tails, add a per-player stat-distribution check (min/max/outlier box scores) when Phase 4 stats make individual lines easy to review; consider a normalization step only if a real outlier appears.

### Calibration drift is unguarded (harness reports, does not gate)
**Severity**: **Medium–High** *(raised 2026-08 — the engine is currently off-target **by design**, see below)*  
**Description**: The `CalibrationHarness` is **disabled by default** (`-Dcalibration=true`) and **reports** the aggregates — it does **not** fail the build. So any change to `SimConfig` base rates, a skill formula, or the possession flow can silently skew the game away from the targets in [calibration.md](calibration.md) and **nothing in CI catches it**. This is acute across the **§3.7–§3.18 sub-phases**: six of them (§3.7 blocks, §3.10 rebounding fouls, §3.11 and-1, §3.12 all-shot-type contact, §3.13 foul trouble, §3.14 fouls-and-ejections) deliberately shift scoring and require a manual recalibration pass each — a forgotten or sloppy re-tune leaves the engine quietly miscalibrated, and the next sub-phase then tunes against a bad baseline (the exact "moving-target" failure the calibration-blast-radius sequencing exists to avoid).

**Update (§3.14a/b, 2026-08): the last two sub-phases invert this risk rather than carrying it, which is a genuinely different failure mode.** Both §3.14a (#032 I) and §3.14b (#034 J) — **now both shipped** — were budgeted **below** the ±1.5 per-seed noise band (+0.26 and +0.43, the latter deliberately over-estimated). So their stop condition is **inverted**: measurable movement in the §3.4 aggregates is a **bug**, not a calibration result. **The risk here is the mirror image of drift — an instrument that cannot tell "the mechanic works" from "the mechanic never fires",** because absence of aggregate movement is consistent with both. That is why each pass builds a **dedicated count line** before its rate is confirmed (#032 J, #034 H), and why those lines are load-bearing rather than decorative. **§3.14b's is the coarsest number in [calibration.md](calibration.md)** — 17.4% relative sd at 102 games, 7.8% at 5 seeds — so it can confirm an order of magnitude and essentially nothing more. **A single-seed reading of it is not evidence**, and §3.14b's own 5-seed spread proves the point: 0.123–0.186 around a 0.148 mean, i.e. the extreme seeds differ by ~50% and are the *same rate*. **Both passes held** (§3.14a +0.5, §3.14b +0.76, each inside the band), but note what that does and does not establish — it rules out a gross leak, not a subtle one, which is exactly why each pass's dedicated count line is load-bearing and why §3.14b additionally **pinned all three of its named bug signatures with tests** rather than inferring their absence from flat aggregates.

**RESOLVED DIFFERENTLY THAN EXPECTED (2026-08, §3.12 execution) — the "deferred debt" framing was wrong, and the TARGET is the live problem.** This entry previously tracked a 3.9-point debt (points 115.9 vs. ~112) deferred from §3.11 for §3.12 to absorb, and named the failure mode as "§3.12 forgets to absorb it." §3.12 did not forget — **it found the premise unsound**, and the risk has changed shape rather than closed:

- **The re-centering was NOT taken, deliberately.** §3.12 landed **117.0 pts / 46.4% FG / 36.7% 3P** (5 seeds). Reaching ~112 would have cost **~2.5 points of FG%** (measured exchange rate on §3.12's own numbers: ~0.50% FG% per point), dragging FG% to ~43.9% against a ~47% target. #030 E's own stop condition fired.
- **#030 E's "free lever" was nearly weightless.** The new perimeter/three multipliers move points by only **~0.3 across their entire defensible range** (measured by sweep), so the cheapest-first order that was meant to make this re-centering nearly costless had almost nothing to spend.
- **The §3.4 TARGETS themselves are now contested.** Points (~112) and FG% (~47%) were set in #022 D from unsourced estimates and never revisited; current figures suggest **points ~114–117** and **FG% ~47–48**. Against a ~115 anchor, §3.11's "debt" was largely an artifact of a stale target.
- **The two contested targets cannot be reconciled by the available lever.** Shot `BASE_*` moves points and FG% **the same direction**, so points-too-high wants a trim while FG%-too-low wants a raise. This needs a lever separating **efficiency from volume** — a design question, not a knob turn.

**✅ THE CONTESTED-TARGET HALF IS RESOLVED (2026-08, `decisions.md` #036).** The five §3.4 targets were **sourced** — Basketball-Reference league averages, 2025-26: **points 115.6, FG% 47.1, 3P% 36.0, assists 26.7, turnovers 14.5** — and calibration.md names the season per row. **The three passes that declined the `BASE_*` trim were right**: against a real 115.6 anchor there was never a 3.9-point debt to absorb, and #030 E's stop condition fired correctly each time. What remains unsourced is a shorter list (foul-outs, technicals, flagrants, the minutes distribution, and §3.16's shooting-foul share).

**Where it now lives:** [`calibration.md`](calibration.md) is the **source of truth for targets** (created §3.12, superseding #022 D). ⚠ **NUMBERING: the re-solve was §3.16 when this entry was written; #038 renumbered it to §3.19**, and the §3.16 slot became foul composition (shipped). The re-solve is **§3.19, sequenced LAST** — after §3.17 (shot mix) and §3.18 (steals) — because #038 made "recalibration re-solves the numbers once the SHAPE is final" a rule: re-centering before the composition passes land would tune against a baseline they are about to invalidate.

**A second-order risk this entry now also carries, surfaced by §3.13 (2026-08): sourcing a target does not guarantee the engine can REACH it.** §3.13 tuned foul-outs to 0.39 against an unsourced ~0.1–0.25 ballpark and found its lever **saturated** — a ~3× stronger sit curve moves the number by *nothing*, because the benched player's earned return puts him back into the same over-dispersed defender draw. So a verified benchmark can land outside what any existing knob can deliver, and the honest response is a **new sub-phase, not a harder tune**. §3.19's bullet carries this as an explicit **escalation rule**; the failure mode it guards against is a future pass reading a sourced number as a mandate and over-tuning a saturated lever until something else breaks.

**⚠ A THIRD FORM OF THIS RISK, ADDED BY §3.16 (2026-08): a calibrated constant can be PRICED BY ANOTHER NUMBER, so it goes stale when that number moves — silently, with no drift and no forgotten re-tune.** `sim.non-shooting-foul-share` (0.50) sets how many fouls award no free throws — but a converted foul awards **2 bonus FTs** if the committing team is already in the penalty and **none** otherwise, so its effect depends on the **penalty rate**. §3.16's own charge fix moved team-periods-in-penalty **51.1% → 55.7%**, which is why the constant shipped at 0.50 rather than the **0.43** its design pass derived from a pre-fix measurement. **Nothing was done wrong and nothing drifted** — the arithmetic was correct against inputs that the same phase then changed. ⚠ **THE PREDICTION CAME TRUE ONE PHASE EARLIER THAN EXPECTED (§3.17, 2026-08).** §3.17 moved the shot mix, not any foul constant — but shifting draws from `foul-mult` 1.0 to 0.133 dropped the penalty rate **55.7% → 46.1%**, and FTA fell to **19.76 against a sourced 23.5**. **0.50 is now mispriced**, exactly as this entry warned, and by a pass that never touched it. ⚠ **§3.19 must re-solve it**, and any future pass that moves the foul OR shot rate inherits the same obligation. The general shape, worth carrying: **when a phase changes the conditions a constant was solved under, re-solving is part of that phase, not the next one's problem** — and ⚠ **a phase can change those conditions without touching anything nearby**, which is what makes this class hard to catch.

**⚠ AND A FOURTH, FROM §3.16 — RESOLVED BY §3.17, BUT THE SHAPE STAYS: a mechanic change can silently break an INSTRUMENT.** `CalibrationHarness` inferred "was this a stopped three?" from the **free-throw count** (3 FTs ⇒ a three) — sound while every stopped shot awarded FTs. A `COMMON_FOUL` (now `NON_SHOOTING_FOUL`) awards 0 or 2 but never 3, so the fouled-three rows under-counted by ~2×. **The engine was unaffected; the measurement lost its signal.** ✅ **FIXED by §3.17 Step 0** (#040 G) — the corrected rate reads **2.93% of 3PA** and held across a 1.9× volume change. It was caught only by re-reading calibration.md row by row against a fresh harness run — *not* by any test, because **no test asserts an instrument's meaning**, which remains true.
⚠ **Fixing an instrument MOVES ITS NUMBERS, and the move looks like a regression.** `Stopped shots / team` now reads **12.43** against a pre-§3.17 **7.65** and appears doubled; **it fell** (7.39 → 6.24 raw-to-raw). **Never compare a corrected number to an uncorrected one across an instrument change.**

**⚠ AND A FIFTH, FROM §3.17 (2026-08) — THE MOST DANGEROUS OF THE SET, BECAUSE THE ROW LOOKS GREEN: a CLAMP can do what a CONSTANT appears to do, and the tell is a number that DOESN'T move.** §3.17 predicted blocks would fall 4.8 → ~2.6 as the mix went three-heavy. They stayed at **4.80**. `PROB_FLOOR` (0.02) is **4× `sim.base-block-three` (0.005)**, so a three's block probability is **floored, not based** — and `base-block-three` is **inert**: changing it does nothing. ⚠ **The blocks row is on target for the WRONG REASON** — the floor propping up threes and the skill term's positive tail on two-pointers cancel to land 4.8. That is #036 B's cancelling-errors pattern, the same one §3.17 spent a phase un-hiding for FG%, recurring in a row nobody was worried about. ⚠ **`PROB_FLOOR` applies to EVERY probability in the engine**, so any constant set below 0.02 is equally inert and **nothing says so at its declaration site**. §3.19 must not tune the four `base-block-*` without holding this, or it will move a dead lever and conclude the lever is dead. Backlog chore filed. **The general shape: a green row is not evidence a mechanism is right — check that the number is produced by what you think produces it.**

**The sharpened risk, and the reason this entry stays open:** the ambiguity that made this worst — *"is the engine drifting or is the target wrong?"* — is **gone**, because #036 sourced the targets. But the entry stays open in a **new** shape: the engine is now **deliberately off-target and will stay that way until §3.19**. Points sit at **110.0 against 115.6**, ~5.6 low, because §3.16 removed ~10 FTA **on purpose** (#039 H) and §3.17 has now moved the shot mix on top of it. ⚠ **§3.17 left THREE more rows deliberately worse** — FG% **43.5** (vs 47.1), FGA **92.28** (vs 89.1, now OVER), FTA **19.76** (vs 23.5) — all recorded as predicted-and-not-tuned (#040 E/F). ⚠ **So "off-target" is currently the expected state, and that is exactly the condition under which real drift is hardest to notice** — the drift-vs-intent distinction this entry protects now depends entirely on each phase **writing down what it expects to move and by how much**, since the aggregates alone no longer distinguish the two. §3.16 did (its roadmap bullet and #039 H both state the ~110 landing as designed); ✅ **§3.17 did too** — #040 E/F predicted each breach *before* execution and its implementation note records where the predictions were **wrong** (blocks, points, rebounds), which is the part that makes the record trustworthy rather than self-congratulatory. **§3.18 must too.**  
**Mitigation**: Re-run `-Dcalibration=true` after **every** scoring-affecting change and re-agree the aggregates with the user before moving on (baked into each §3.x execution plan's calibrate step). §3.11 added **`-DcalibrationSeed=NNNN`** so a config can be observed across several seeds and tuned to the **mean** — a single run carries enough per-seed noise (±1.5 pts observed) to bait an over-correction; use it. Longer-term option: a lightweight, always-on assertion test that fails if a small fixed-seed batch drifts outside a tolerance band around the targets — converting the manual report into a real gate. **Reconsidered at §3.12's close-out (2026-08) and deferred again, for a NEW reason**: a tolerance band needs an expected value, and the expected value is exactly what is now in dispute (see above). Setting a band around a contested target would harden the wrong number into CI. ⚠ **That specific objection is now GONE — the targets were sourced by #036** — but the deferral still holds for a **different** reason: the engine is deliberately off-target until §3.19 re-centers, so a band set today would fail on numbers that are *correct for now*. **Revisit after §3.19**, when the shape is final and the landing is meant to be the target — at which point a band becomes genuinely useful rather than premature.

### H2/Postgres divergence risk
**Severity**: Low  
**Description**: Tests run on H2, production on Postgres. As the schema grows (game events, stats tables, potentially JSON columns), the gap between H2 and Postgres behavior could cause test-passes-but-prod-fails scenarios.  
**Mitigation**: Consider Testcontainers for integration tests if H2 divergence becomes a problem. Keep Liquibase changesets simple.

### Seed data realism
**Severity**: Low  
**Description**: The ~420 pre-loaded players have manually-assigned attributes. Partially validated: §3.4's calibration (~100 games) shows the seed roster produces **realistic team-level outcomes** in aggregate (see Skill formula balance). Still open: whether the *attribute distributions themselves* (vs. the formulas on top of them) are realistic at the individual level — same tails concern as above, hard to separate from formula balance until Phase 4 surfaces per-player season lines.  
**Mitigation**: Compare per-player stat distributions to real-basketball benchmarks once Phase 4 stats exist; adjust seed data or formulas then. Related backlog.md chore: hand-tuning marquee/star players to 18–20 (backlog.md).

## A scoring player can be dropped from the box score entirely (found 2026-08, §3.20)

⚠ **`points` on the box score can under-count the real score, and the FT/assist
reconciliations do NOT catch it** — only §3.19's **points** identity does, and only on the
~1-game-in-1200 where it fires.

**Symptom.** `CalibrationHarness`'s points reconciliation reported `1 MISMATCH(es)`:
event log **240**, final score **240**, **box score 237**. The event log and the final
score agree, so **the game's scoring is correct** — a player's box-score row is missing,
taking his points with it. Isolated to one player who hit a `MADE_3PT` and had **no box
score row at all**.

**Cause.** `GameSimulator` skips any player whose `onFloorPossessions == 0`, on the stated
assumption that they "never checked in" and so have "nothing to reconcile". **That
assumption is false.** `RotationState.advancePossession` calls `drainForPossession()` —
the only thing that increments `onFloorPossessions` — for the players on the floor **at
the top of the call**, and runs substitutions **afterwards**. A player substituted in can
therefore take the floor, participate in a scoring play, and still finish with a counter
of 0, at which point `GameSimulator` silently drops him.

**Severity: low frequency, but it corrupts a PERSISTED stat and it is silent.** Measured
at ~**1 game in 1200+** (12 sweeps of 102 games across two configs). ⚠ **It is
config-sensitive in appearance only** — it did not reproduce on the pre-§3.20 constants or
on the §3.20 landing, and *did* on an experimental two-lever config, purely because the
constants change which games reach the path. **Do not read it as caused by a constant.**

**Why it matters more from Phase 4 on.** Today only the harness reads box scores in
aggregate. **Phase 4 builds leaderboards and season totals on this table** — a silently
missing player-game is a wrong career stat that nothing will flag.

**The fix is not simply "drop the guard".** `onFloorPossessions` is also the **minutes
denominator** (minutes are a possession-share projection), so a row with 0 would project 0
minutes while showing points — visibly odd. The honest fix is to make the counter mean
"took the floor", which is an ordering question in `advancePossession`. **Needs its own
design pass** — it touches the rotation step, which is RNG-adjacent (#031 B), so a naive
reorder risks re-baselining every seeded test.

## ⚠ Stats are written TWICE, and the two paths agree only by convention

**The architectural concern behind the box-score bug above** *(raised 2026-08 by the
user)*. Every stat in this engine is recorded by **two independent mechanisms**:

| path | what it is |
|---|---|
| `PlayerGameState.record*()` | counters incremented as the possession runs |
| `data.addEvent(...)` | the `GameEvent` emitted for the same play |

**Nothing structurally guarantees they agree.** They agree because each call site
remembers to do both. That is a **convention, not an invariant**, and a convention that
holds across ~14 sub-phases of engine work by discipline alone.

⚠ **§3.20's dropped-box-score-row bug is one realisation of this, and there is no reason
to assume it is the only one.** There, the event was emitted correctly *and* the counter
was correct — but the row carrying the counter was never written, so the event log and
final score stayed right while the box score silently lost a player. **A box score
derived from the event log cannot fail that way**: one write path instead of two.

⚠ **The corroborating evidence is §3.19 itself.** That pass had to *add* three
reconciliation identities — `ast+blk`, `ft-src`, `points` — whose entire purpose is to
detect the two sources disagreeing. **In a derived model those identities are
tautologies, not tests.** Their existence is the measurement of this risk.

**Why it is not fixed yet, and what blocks it.** It is a real refactor across every
`record*` call site plus persistence. ⚠ **The specific blocker is `minutes`**: it has no
event behind it — it is a possession-share projection (#023 A) — so deriving everything
means either emitting substitution/possession events or keeping minutes as the one
deliberately non-derived field.

⚠ **The sharpest consumer is SINGLE-GAME SUMMARIZATION, not leaderboards** (the user's
framing, 2026-08). A box score rendered for one game sits **beside the play-by-play the
user can also read** — so a disagreement between the two is **visible to the user**,
not buried in a season aggregate. Career-stat correctness is the weaker argument; "these
two views of the same game disagree" is the strong one.

**Status: NOT scheduled, and NOT a gate on anything.** The acute bug above is fixed.
Whether this is worth the refactor is genuinely open. Parked in `ideas.md`;
`roadmap.md`'s Phase 4 carries a pointer so its design pass sees it, since that phase
would already be touching every stat path. **If it is ever taken on it wants a design
pass and a `#NNN`** — it is an architecture call, not a chore.

---

## ⚠ Skill sensitivity is ~10× too steep, and every calibration pass so far was blind to it

*(Measured 2026-08, by a throwaway probe run after §3.21 while auditing for
§3.21-shaped gaps. The probe was deleted; the numbers and the reasoning are in
[ideas.md](ideas.md).)*

**The risk.** The engine's contests respond to player skill roughly an **order of
magnitude more steeply than real basketball**. Holding an average offense fixed and
varying only the defense's skill from 4 to 16, opponent FG% swings **79.8% → 16.2%** — a
63-point spread against a real NBA team-defense spread of about **5 points**. Forced
turnovers swing 2.3 → 36.9 over the same range.

⚠ **THE REASON IT HAS NEVER BEEN CAUGHT IS STRUCTURAL, AND IT IS THE ACTUAL RISK:
`CalibrationHarness` runs `teamOf5(id, 10)` — AVERAGE against AVERAGE.** Every target in
`calibration.md` is measured at a single point that sits exactly at the **midpoint** of
the response curve, where the numbers are correct and reassuring (47.7% FG, 6 turnovers).
**Eighteen sub-phases have tuned the INTERCEPT and not one has tested the SLOPE.** A
green harness says nothing whatsoever about it.

**Why it is latent rather than active.** Nothing today consumes the slope. Games are
simulated between rosters the harness makes identical, so the steepness never expresses
itself. **Phase 5 is the consumer that will expose it**: season play puts real rosters
with real skill spread against each other, and good teams will beat bad teams by
impossible margins, producing degenerate standings — a symptom that will look like a
*standings* or *scheduling* bug and will not obviously point back here.

**Why this is worth a risk entry and not just an idea.** It is the same shape as §3.17's
finding (a green aggregate hiding a wrong composition) one level up — **a green midpoint
hiding a wrong gradient** — and that shape has now cost two passes. The cost of finding
it in Phase 5 is debugging it through the wrong subsystem.

**Mitigation, and it is cheap.** It is a **tuning** problem, not a rebuild: the suspects
are the global `SimConfig.SENSITIVITY` (0.5) and the per-contest sensitivities, all
`public static final` model machinery. ⚠ **But it needs an INSTRUMENT first** — a harness
mode that runs a skill LADDER and reports the response curve, because the current report
cannot display a slope at all. ⚠ **Do not attempt to re-tune it against the existing
average-vs-average rows**: they are landed, and flattening the slope would not move them.

**Status: NOT scheduled, NOT a gate on closing Phase 3** — every calibrated row is landed
and the engine is correct at the point it is measured. ⚠ **It SHOULD be a gate on Phase
5**, and `roadmap.md`'s Phase 5 carries a pointer so its design pass sees it before
standings exist to be confused by.
