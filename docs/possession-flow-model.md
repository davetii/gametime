# Possession flow — model reference

The probability machinery, the per-sub-phase mechanics, and what each pass
inherited. **[possession-flow.puml](possession-flow.puml) owns the branch ORDER**;
this file owns the *maths and the mechanics* behind the boxes it draws.

⚠ **The flow is now SEVEN diagrams, not one** — an overview plus one per
resolver; see the table in [possession-flow.puml](possession-flow.puml)'s header,
or CLAUDE.md's doc map. **[possession-flow.puml](possession-flow.puml) is the
entry point**: the whole possession on one screen, and the only place the
second-chance loop is visible as a loop.

⚠ **This was the diagram's 279-line `legend`.** It was a third of the rendered
height (4,635px of 13,800) and no part of it was flow, so it moved here whole
in 2026-09. Nothing was rewritten in the move — corrections and superseded
framings are kept exactly as they were written, which is why several entries
argue with themselves.

**See also:** [game-events.md](game-events.md) — the event vocabulary ·
[calibration.md](calibration.md) — the targets ·
[engine-traps.md](engine-traps.md) — the traps ·
[decisions.md](decisions.md) — the decision index.

---

### Probability formula (all resolvers)
p = base(path) + SENSITIVITY × (offense − defense) / 10
Clamped [PROB_FLOOR 0.02, PROB_CEILING 0.97] for
NORMAL-frequency outcomes (contestProbability).
Average vs average → base rate. All constants in SimConfig.

### RARE events use a different clamp (§3.7 / §3.10 / §3.11)
rareEventProbability(base, driving, opposing, sensitivity)
clamps to **[0, ceiling] with NO floor** — on a thin base the
0.02 floor would act as a FLOOR and make the knob tunable only
UPWARD (found the hard way in §3.10, #028 impl note). Each rare
event also carries its OWN sensitivity, far below the global 0.5,
which would otherwise swamp a thin base: BLOCK_SENSITIVITY 0.12,
REBOUND_FOUL_SENSITIVITY 0.10, AND_ONE_SENSITIVITY 0.10.
NB a zero base does **not** disable a rare event — the skill term
alone stays positive off an even contest; only the caller's gate
truly switches one off. **§3.12 deletes the isContactType gate**,
so from then on the only off-switch is a **zero foulMultiplier**,
which scales the whole probability, skill term included (#030 A1).

### §3.4 coach / chemistry modifiers (decisions.md #022)
Each is the avg-10 deviation multiplier
m(attr) = 1 + COACH_SENSITIVITY × (attr − 10) / 10
applied as a thumb on the scale over the contest formula above.

### §3.5 fatigue / substitution (decisions.md #023)
Between-possession sub (top, RNG-free) changes WHO is on
the floor; each contest skill is × fatigueFactor(energy),
×1.0 at a full tank. Minutes are a possession-share
projection (no game clock). Foul-out = getFouls() >= 6.

### §3.7 blocked shots (decisions.md #025)
BLOCKED? is one leg of a three-way MAKE/MISS/BLOCK draw inside
ShotResolver (v3): P(BLOCK) carved off the top, defender-vs-
finisher contest (own BLOCK_SENSITIVITY). A block is a SHOT/
BLOCKED_* event (a field-goal outcome, exactly as a steal is a
TURNOVER outcome) — missed FGA on the shooter + a BLK via
recordBlock() on the defender, no assist. BlockResolver runs a
flat four-way loose-ball recovery (offense/defense × in-bounds/
OOB). Recalibrated: ~5 blocks/team, §3.4/§3.5 aggregates held.

### §3.8 missed-shot OOB (decisions.md #026)
A missed shot resolves to ONE of FOUR outcomes in a single
skill-weighted draw: OFFENSIVE / DEFENSIVE rebound, or OOB
offense / OOB defense. MissedShotResolver WRAPS ReboundResolver
(the two-way skill contest, unchanged): it carves the OOB share
off the top by a flat defense-leaning lean FIRST (skill-
independent), then runs the board contest only on the clean-
rebound remainder — NOT a separate OOB sub-decision, so OOB never
inherits the board winner. Both sail-out (shot flies OOB
untouched) and tipped-OOB (a board contest, last-touch decides)
land here, sharing one OOB outcome each (offense/defense). OOB is
a REBOUND event with an OUT_OF_BOUNDS_* outcome that credits NO
rebounder and stays out of the rebound reconciliation (#026 E).
Skill-weighted — the deliberate difference from §3.7's flat
BlockResolver (#026 C). OOB-offense is a third offense-retention
path against the one MAX_OFFENSIVE_RETENTIONS cap. Sail-out is a
new outcome (fewer second chances) → VERIFIED harness-neutral
(#026 D): OOB ~3/team/game, §3.4/§3.5 aggregates held.

### §3.9 turnover causes (decisions.md #027)
The turnover GATE is untouched — same BASE_TURNOVER, same contest,
same coach/fatigue scaling — so the turnover COUNT never moves
(#027 A). A post-declaration weighted draw only picks WHICH of
nine causes it was, replacing §3.2's binary STOLEN / LOST_BALL.
STOLEN stays dominant and keeps the pickStealer + recordSteal()
path; every cause charges the ball-handler. Modest avg-10 leans on
four causes (#027 C). Note LOST_BALL_OUT_OF_BOUNDS is on TURNOVER
and must NOT be confused with §3.8's OUT_OF_BOUNDS_* on REBOUND —
different play types, deliberately distinct vocabulary (#027 D).
Free by construction → no recalibration.

### §3.10 rebounding fouls + the team-foul/bonus substrate (#028)
A non-shooting foul during the rebound phase, carved off the TOP
of the board contest and short-circuiting it (C — the §3.7 shape).
TWO-SIDED (A2): defensive box-out push (dominant) or offensive
over-the-back, so the committing team is NOT implied by the
possession orientation — hence game_event.committing_team_id, the
one additive column this arc took (D). The penalty is **DERIVED**
from the FOUL log, never stored (A1), emit-then-count so the Nth
foul awards the bonus itself. Bonus FTs reuse awardFreeThrows
verbatim (B). NOT free: +2.7 pts before tuning, of which only
~1.1 was bonus FTs — **the bigger channel was retained
possessions**, which the design did not anticipate. BASE_FOUL
proved the WRONG lever (trimming it RAISES points); the fix was
the shot BASE_* rates trimmed ~1.2%.

### §3.11 and-1 (decisions.md #029)
A defensive foul on a shot that STILL GOES IN: made FG + 1 FT.
Modeled as a SECOND, post-make foul roll (A1) BESIDE the assist
(A3) inside SHOT — MADE — the pre-shot foul branch + BASE_FOUL
are untouched, so the and-1 rate stays independently tunable
(the §3.7/§3.10 carve, a third time). Scoped to made DRIVE/POST
(A2 — §3.12 widens to all shot types + fouled-three = 3 FTs).
Awards exactly ONE FT via a per-situation count through the reused
awardFreeThrows (B); never forks the possession (the make already
ended it). Rate = rareEventProbability(AND_ONE_BASE, …,
AND_ONE_SENSITIVITY) — reuse the machine, own dials, avoiding the
PROB_FLOOR/global-SENSITIVITY traps §3.7+§3.10 hit (C). Free throws
become SELF-DESCRIBING (D): each FREE_THROW carries its source
(SHOOTING / BONUS / AND_ONE), retiring §3.10's accepted ambiguity
now that there are three FT sources. No schema / OpenAPI / new
PlayType (AND_ONE is a FOUL outcome, committing_team_id = defTeamId).
PURE-ADDITIVE points lift → recalibrate the shot BASE_* lever, NOT
BASE_FOUL (wrong-way, #028 note), by multiple runs to the mean (E).

### §3.12 all-shot-type contact fouls (decisions.md #030) **SHIPPED**
— the two §3.12 notes on the diagram mark the forks it changed. The binary ShotType.isContactType() (DRIVE||POST)
is DELETED (A1) — it gated the WHOLE foul model, so today a jump
shot cannot be fouled at all. It is replaced by a graduated
per-shot-type **foulMultiplier** (the §3.7 BASE_BLOCK_* shape):
DRIVE 1.0 anchor ≈ POST > PERIMETER > THREE. The base constant is
UNTOUCHED at 0.15 and still means the DRIVE rate (A2) — though it is
RENAMED `BASE_FOUL` → `BASE_NO_BASKET_FOUL` (F), since "no basket"
is what it actually encodes and the old name kept inviting misuse as
a points lever. So drive/
post behavior is bit-identical to §3.11 and the entire §3.12 delta
is attributable to perimeter/three. ONE shared table drives BOTH
the pre-shot roll and the and-1 roll (B). The same widening
ACTIVATES the latent fouled-three bug and fixes it: a stopped
THREE awards **3** FTs via a freeThrowsIfFouled() rule on ShotType
(C) through #029 D's parameterized count — but an and-1 stays
**1** FT for every type, a made three included. No schema, no
OpenAPI, no new PlayType/FreeThrowSource/outcome string.
BONUS_FOULS_PER_PERIOD stays 5 — a real NBA rule, not a lever (D).
NOT free, and the LARGEST RNG shift of the arc (perimeter/three
possessions now consume foul + and-1 draws). Both new stopped-shot
cases are net points-POSITIVE (a stopped THREE is the biggest
single-event gain in the model: −1.01 expected, +2.25 in FTs).
**SHIPPED VALUES:** DRIVE/POST 1.0, PERIMETER **0.30**, THREE
**0.133** (⇒ a 2% stopped-three rate, the user-agreed anchor).
**LANDING (5 seeds, to the mean): 117.0 pts / 46.4% FG / 36.7% 3P**,
fouls 19.1/team/game, and-1s 1.87, 3-FT trips 0.59 (2.9% of 3PA).
The lift was **+1.1**, HALF what the design sized: stopped-shot
+1.6, and-1 **+0.15** (B's "close to a non-event" was exactly
right), possession-ending −0.6.
**The planned re-centering to ~112 was NOT taken.** E's lever order
(cheapest-first) had almost nothing to spend — the new perimeter/
three multipliers move points by only ~0.3 across their whole
defensible range (measured) — and the shot BASE_* lever costs
**~0.50% FG% per point** on §3.12's own numbers, so reaching ~112
would have dragged FG% to ~43.9% against a ~47% target. E's own
stop condition fired. The ~112 TARGET is itself now contested
(points may be ~114–117, FG% ~47–48): see **docs/calibration.md**,
which is the source of truth for targets, and **§3.16**, the
recalibration sub-phase this finding created.
The no-basket-foul base was **never** touched — a WRONG-WAY lever,
because it controls a SWAP (a live shot worth >1.5 pts once its
rebound/and-1 continuations are counted, vs. a 2-FT trip worth
~1.5), so trimming it moves points UP (measured, #028: 0.15→0.138
raised points to 116.1).
**§3.12's foul-out instrument (G) found a PRE-EXISTING problem:**
0.60 foul-outs/team/game vs. a ~0.1–0.25 ballpark, of which 0.425
predates this pass (measured with both new multipliers zeroed).
Triaged separately per G → **§3.13**, ahead of flagrants.

### §3.14b flagrant fouls (decisions.md #034)

**The three FLAGRANT? forks on the diagram.** A severity roll **LAYERED ON TOP** of a
foul that already happened, at **all three** existing foul sites
(stopped shot · and-1 · rebounding foul) — the §3.7/§3.10/§3.11
"carve" shape inverted: nothing is re-partitioned, so **no
existing foul rate moves by construction** (A). ONE shared roll,
ONE constant — the sites differ in what the foul WAS, not in how
likely it was excessive, and three rates would be unreadable at
~33 events/run.
**THE STRUCTURAL CRUX (B):** a flagrant awards FTs **and returns
the ball** — the first FT path that does not end the possession,
breaking #030 B's invariant. It is expressed as **the same
`offensiveRebounds++; continue;`** the offensive rebound has
used since §3.3, under the **same cap**: the loop already IS the
"same team, run it again" machine, so the crux is five lines, not
a new mechanism. The cap is a runaway-possession guard, not a
rebounding rule — exempting the rarest path from the only bound
would be backwards.
**The fork is decided by WHO COMMITTED, not by which site** (C):
defensive ⇒ offense retains (the stopped-shot and and-1 sites are
ALWAYS defensive); offensive ⇒ 2 FTs to the defense and the ball
flips (only the rebounding site can produce it, ~22%).
**FTs REPLACE the underlying award, never add** (C) — flat 2,
shot by the player who was FOULED (D — §3.14a's best-shooter rule
is NOT reused; its premise is that nobody was fouled).
**Flagrant-1 vs -2 is a flat 15% sub-roll** (E), causally inert
— the committer was already chosen, so foulProne has had its say.
A flagrant-2 **ejects immediately**, extending isDisqualified to
a THIRD cause, still DERIVED (F — see the rotation note).
**A flagrant IS a personal foul** (I): recordFoul(), feeds the
6-foul limit, §3.13's sit curve AND the period bonus tally —
§3.14a's counter split does NOT apply here.
Rate ~**0.16/team/game** (~0.25–0.40 league-wide), stored
game-level and divided by the **emergent foul rate** — so §3.16
moves flagrants without touching the constant (G). Floor-free
via clampRareProbability (#032 H's FIFTH site); PROB_FLOOR is
~2.4× the rate — decisive, but thinner than §3.14a's >10×.
Budgeted **+0.43 pts/team/game** over TWO channels (FTs +0.24
gross, retention +0.19 upper bound), deliberately OVER-estimated
— the FT channel is largely offset (the underlying foul already
awarded 2–3). Sub-noise vs ±1.5, so **#032 I's stop condition
INVERTS again: measurable aggregate movement is a BUG** — most
likely double-awarded FTs, an uncapped loop, or a bonus-tally
leak. But retention is a channel §3.14a did not have, and #028
is the precedent (its retained possessions outweighed its FTs).
**Judge at 5 SEEDS ONLY — the coarsest row in calibration.md**
(17.4% relative sd at 102 games, 7.8% at 5 seeds).
⚠ **THIS LINE WAS WRITTEN AT §3.14b AND WAS WRONG TWICE OVER —
kept, corrected, as a worked example of CLAUDE.md's trap 1.**
It read "the LAST new mechanic in Phase 3 — §3.15 (profiles) and
§3.16 (recalibration) add no branch". Both halves failed:
(a) **the NUMBER moved** — recalibration was renumbered §3.16 ->
§3.18 -> **§3.20**, and the §3.16 slot was REUSED for shooting-foul
composition, so "§3.16 (recalibration)" now names a different phase
that DID add a branch; and (b) **three passes added or changed
branches afterwards** — §3.16 (the non-shooting-foul fork + the
charge's second FOUL event), §3.17 (the shot-share table), §3.18
(the counterparty column on three existing events).
**Read the phase NAME, never the number alone.**

## SEE ALSO
**docs/game-events.md** — the event VOCABULARY: every
(play_type, outcome) pair the flow emits, with who is on each
row (primary / opponent / assist / committing_team). ⚠ **This
diagram owns the branch ORDER; that file owns the EVENTS.** A new
outcome, or a change to who rides an event, belongs there — and
in the same change as the fork drawn on the diagram.

## WHAT §3.20 (RECALIBRATION) INHERITS
⚠ **Renumbered AGAIN in 2026-08: recalibration was §3.16, then
§3.18, then §3.19, and is now §3.20** — the §3.19 slot became the
INSTRUMENTATION pass (harness self-verification + seeded-test
trustworthiness), which runs first and hands this pass a clean
5-seed measurement. **Read the phase NAME, never the number.**
**§3.20 is the last Phase-3 sub-phase and adds NO mechanic** — it
re-solves NUMBERS now that the shape is final. So the diagram
should be structurally complete going into it; if §3.20 finds
itself adding a branch, that is a signal the pass has grown
beyond recalibration.
✅ **§3.20 SHIPPED (decisions.md #042).** It added no branch and no
event — the flow is unchanged by it. **8 of 12 rows landed**,
including the 2P% gap (48.8 -> 55.0), FGA (89.22), FTA (23.40) and a
NEW sourced FT% row (77.81). Live values: calibration.md.
⚠ **Its OVER-DETERMINATION framing above was DISPROVED** (#042 A):
it held FGA and FT% fixed and neither should have been. Kept as the
question §3.20 was asked, not as an answer.
⚠ **Three findings that touch THIS diagram's reasoning:**
* **A foul charges NO FGA on EITHER branch.** Both the
  NON_SHOOTING_FOUL and SHOOTING_FOUL paths return before
  `recordFieldGoalAttempt()`, so re-partitioning between them
  moves FTA and **not FGA** — disproving #042 B, which had modelled
  -0.62 FGA per 0.05 of share. FGA was landed instead by
  `base-no-basket-foul` (0.15 -> 0.1687), which raises the foul
  COUNT and therefore removes live attempts. ⚠ Each extra foul
  costs **1.49 FGA**, because a foul ends the possession and
  forfeits the offensive rebound the miss would sometimes produce.
* **PROB_FLOOR reaches the TURNOVER gate too**, not just blocks:
  ~40-45% of possessions are pinned at 0.02, making
  `base-turnover` a 0.17-elasticity lever. The footnote below is
  therefore LARGER than "half a blocked three" — see #042 D4.
* ✅ **§3.21 (#043) CLOSED BOTH REBOUND GAPS THIS LEGEND USED TO FLAG.**
  A recovered blocked shot now credits a rebounder, the block-OOB
  slices now emit their event, and a missed LAST free throw is now
  a live rebound. Both are drawn on the diagram. ⚠ **The design pass also
  RETIRED a third row that was never real:** the "**1.97 unexplained
  on the FG path**" is the **REBOUNDING FOUL** (measured **2.34**
  /team-game), which carves off the TOP of the rebound phase so the
  board contest never runs — **correct as basketball**, on this
  diagram since §3.10, and simply omitted from the arithmetic that
  "found" the leak. **Three independent counts agree to ~0.04.**
  ⚠ **The generalizable lesson, and it is the THIRD consecutive pass
  to hit it** (#042 D1, D3): *a modelled quantity turned out to be a
  known branch nobody had subtracted.* **Measure the decomposition
  before theorising about a residual.**
* ⚠ **THE REBOUND SPLIT NOW HAS THREE DIFFERENT OFFENSIVE SHARES
  FEEDING ONE POOL, and only one of them has a knob** (#043 B):
  the ordinary board contest **0.262** (`base-offensive-rebound`,
  already correct vs a real 0.259), the block recovery **0.400**
  (FIXED by the flat `block-*` weights — not a contest at all),
  and the free-throw board **~0.19** (a rule). **DefReb and OffReb
  therefore end as REPORTED RESIDUALS in opposite directions** —
  that is structural, not a mis-tune. ⚠ **Do NOT reach for
  `base-offensive-rebound`: neither new slice passes through it.**
