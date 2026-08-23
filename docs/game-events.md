# Game events — the vocabulary the engine emits

Every event `PossessionEngine` writes to `game_event`, as a `(play_type, outcome)`
pair with **who is on the row**. This is the single per-event reference; `game.md`
keeps the models (`Game`, `GameEvent`, `BoxScore`), the possession flow and the API
surface, and `docs/possession-flow.puml` draws the branch order.

> **⚠ This documents what the engine DOES, never what would be nice.** Every row is
> derived from the `addEvent` call sites in `PossessionEngine`. An outcome listed here
> but never emitted makes this a spec nobody implemented. The participant columns are
> checked against real simulated events by `GameSimulatorIntegrationTest`, so they
> cannot silently drift.

> **⚠ Do not start a second per-event table.** `player.md`'s *"Possession Event →
> Skills Used"* table is the only other one, it answers a different question (which
> *skills* each event reads), and the two cross-link deliberately.

---

## The three rules

Most of this file is derivable from three rules. Read them first.

### 1. The emission rule — what earns an event

> **SECOND PARTICIPANT → A COLUMN. SECOND ACCOUNTING → AN EVENT. NEITHER → NOTHING.**

This describes the engine as it stands, and it settles the recurring question
("shouldn't a steal be its own event?"). It explains all nine turnover causes at once:

- **`STOLEN`** has a second *participant* — a defender acted. → **a column**
  (`opponent_player_id`).
- **`OFFENSIVE_FOUL`** (the charge) has a second *accounting* — it **is** a personal
  foul: `recordFoul()`, the bonus tally via `GameData.isInBonus`, the six-foul limit.
  Accounting cannot ride a column, because `isInBonus` counts **`FOUL` events**. → **two
  events**.
- **The other seven causes** (`TRAVELLING`, `BAD_PASS`, `LOST_BALL_OUT_OF_BOUNDS`,
  `SHOT_CLOCK_VIOLATION`, `3_SECONDS_VIOLATION`, `8_SECONDS_BACKCOURT_VIOLATION`,
  `OVER_AND_BACK`) have one actor, one fact, no second stat. → **nothing owed.** A second
  event would restate the first row with no new fact, at ~12 duplicate rows per game.

⚠ **The trigger is a second FACT, not the pre-existence of a box-score column.**
`box_score.steals` is a separate accumulator and has always been correct; the steal
needs `opponent_player_id` so that **the event log can answer "who stole it?"**.

### 2. The counterparty invariant — who may sit in which column

> **`opponent_player_id` is the player on the OTHER SIDE of the play from
> `primary_player_id`, and is therefore ALWAYS on the opposite team.**

That narrow contract is what makes one generic column safe. A reader resolves the
opponent's team as *"whichever of `offense_team_id` / `defense_team_id` primary is not
on"* — **with no need to decode the `outcome` string**. It holds even at the two-sided
`REBOUNDING_FOUL_*`, whose *possession orientation* flips: committer and fouled player
are opponents either way.

**A teammate never goes here.** An assister rides `assist_player_id`. The two columns
hold different **kinds** of fact — collaboration vs. opposition — and merging them would
break the invariant with a same-team exception, reintroducing at the player grain
exactly the ambiguity `committing_team_id` exists to remove.

⚠ **There is deliberately NO offense/defense side flag.** The side is already derivable
from primary plus the two team columns, and a flag written by the same emit site from
the same locals would fail *together* with `primary_player_id` and agree wrongly. The
control is the invariant **test** instead, whose expectation comes from this spec rather
than from the emit site.

### 3. `outcome` is free text; `play_type` is a closed enum

`outcome` is an unconstrained `VARCHAR`, so **a new outcome string is free** — no
migration, no API change. `play_type` is a **closed OpenAPI enum**
(`[SHOT, TURNOVER, REBOUND, FOUL, FREE_THROW]`), so **a new play type is expensive**: an
API change plus an unhandled case for every exhaustive consumer.

This asymmetry decides real questions. It is why nine turnover causes, technicals,
flagrants and the non-shooting foul all cost nothing as `outcome` strings, and why a
`PlayType.STEAL` was rejected: beyond the API cost, every existing reader counting
`TURNOVER` events would silently under-count by ~55%.

⚠ **Free text has a cost of its own: a rename leaves no migration behind.** An outcome
string that changes does **not** update rows already written, so a query spanning games
simulated before and after such a change must match both spellings — or treat the older
rows as disposable. Decide which; do not leave it implicit.

⚠ **THIS HAS HAPPENED ONCE, AND THE VOCABULARY HAS A CUTOVER DATE.** §3.17 renamed
`COMMON_FOUL` → **`NON_SHOOTING_FOUL`** (#040 M/N) with **no migration**, so
`game_event.outcome` holds **`COMMON_FOUL` on games simulated before §3.17** and
`NON_SHOOTING_FOUL` after. **The engine emits only the new spelling** — there is no
`COMMON_FOUL` left in engine logic — so **only a query reading persisted HISTORY is
affected.** The call was made (#040 N, user): the pre-§3.17 rows are **test data and
disposable**, nothing has launched, so a dual match is **not** required. ⚠ **If that ever
stops being true** — real games retained across the boundary — **a reader spanning it must
match both spellings.** #040's trade-off flagged exactly this: it must be *stated*, not
discovered by whoever first queries across the boundary.

---

## The master table

One row per `(play_type, outcome)` pair the engine emits. **The participant columns are
the rules**; everything else is in `notes`.

Column key — `primary` = `primary_player_id`, `opponent` = `opponent_player_id` (the
counterparty, always the other team), `assist` = `assist_player_id` (a teammate),
`committing` = `committing_team_id`. A dash means **null by contract**, not
unpopulated.

| `play_type` | `outcome` | primary | opponent | assist | committing | notes |
|---|---|---|---|---|---|---|
| `SHOT` | `MADE_2PT_DRIVE` · `MADE_2PT_PERIMETER` · `MADE_2PT_POST` · `MADE_3PT` | shooter | — | assister, if the roll hit | — | Made FG. The assister is a **teammate** — not a counterparty. An and-1 `FOUL` may follow for the same shot |
| `SHOT` | `MISSED_2PT_DRIVE` · `MISSED_2PT_PERIMETER` · `MISSED_2PT_POST` · `MISSED_3PT` | shooter | — | — | — | Missed FG. A `REBOUND` event follows |
| `SHOT` | `BLOCKED_2PT_DRIVE` · `BLOCKED_2PT_PERIMETER` · `BLOCKED_2PT_POST` · `BLOCKED_3PT` | shooter (the **victim**) | **the blocker** | — | — | Charges a missed FGA (no FGM; `+3PA` on a blocked three) and carries **no** assist. `BLOCKED_3PT` is rare (a closeout swat). See *Steal / block symmetry*. ⚠ **NO `REBOUND` EVENT FOLLOWS — unlike the `MISSED_*` row above.** Recovery is a flat four-way `BlockRecovery` draw (#025 D) that forks the possession but **emits nothing and credits no rebounder** |
| `TURNOVER` | `STOLEN` | ball-handler (the **victim**) | **the stealer** | — | — | ~56% of turnovers, kept dominant. See *The steal* |
| `TURNOVER` | `OFFENSIVE_FOUL` | ball-handler | — *(the drawer is **not modelled**)* | — | — | The charge. ⚠ **Also emits a `FOUL` row below — two events, one occurrence.** ⚠ **`opponent` is null DELIBERATELY**: a charge has a real counterparty (the defender who drew it), but the engine never picks one and doing so requires a new RNG draw. See *The charge* |
| `TURNOVER` | `SHOT_CLOCK_VIOLATION` · `BAD_PASS` · `TRAVELLING` · `LOST_BALL_OUT_OF_BOUNDS` · `3_SECONDS_VIOLATION` · `8_SECONDS_BACKCOURT_VIOLATION` · `OVER_AND_BACK` | ball-handler | — *(no counterparty exists)* | — | — | The seven unforced causes: one actor, one fact, nothing owed (rule 1). ⚠ `LOST_BALL_OUT_OF_BOUNDS` is **distinct** from `OUT_OF_BOUNDS_*` on `REBOUND` |
| `FOUL` | `SHOOTING_FOUL` | **the defender** who committed it | **the fouled shooter** | — | defense | Stops a shot of **any** type; FTs follow — **3 if it stopped a `THREE`**, else 2. ⚠ **Note the orientation**: primary is the *committer* here, so the counterparty is on **offense**. A second roll re-partitions some of these into `NON_SHOOTING_FOUL`. ⚠ **Charges NO field-goal attempt** — see *Why a stopped shot is not a field-goal attempt* |
| `FOUL` | `NON_SHOOTING_FOUL` | the defender | **the fouled shooter** | — | defense | The same stopped-shot roll, re-partitioned by a second flat roll on the **already-charged** foul, so the foul total holds by construction. **No FTs outside the penalty, 2 bonus FTs inside it.** ⚠ The possession **ENDS either way** — deliberately wrong as basketball, and what protects the FGA budget |
| `FOUL` | `OFFENSIVE_FOUL` | ball-handler (charged `recordFoul()`) | — *(as above)* | — | **offense** | The charge as a foul, emitted **in addition to** the `TURNOVER` row. ⚠ **`committing` = the OFFENSE** — with `REBOUNDING_FOUL_OFFENSE` the only two such sites, so the only paths that move the *defense* toward the bonus. Counts toward the bonus and the 6-foul limit; **not** flagrant-eligible; no FTs (the possession already ended) |
| `FOUL` | `REBOUNDING_FOUL_DEFENSE` | the defender who pushed | — *(**no individual victim is identified**)* | — | defense | Box-out push in the rebound phase — the **offense** is fouled, so it retains, or shoots **bonus** FTs in the penalty |
| `FOUL` | `REBOUNDING_FOUL_OFFENSE` | the offensive player | — *(as above)* | — | **offense** | Over-the-back — the **defense** is fouled and the possession **ends**. The two-sidedness of this pair is why `committing_team_id` exists |
| `FOUL` | `AND_ONE` | the defender | **the fouled shooter** | — | defense | ⚠ **The FGA and FGM are already charged** on the preceding `SHOT` event — an and-1 is a made basket, so unlike a stopped shot it *is* an attempt. Foul on **any** made shot — the basket counts and **one** FT follows, a made three included. Never forks the possession, never consults the bonus |
| `FOUL` | `TECHNICAL_FOUL` | the committer | — *(the FT shooter is **not** a counterparty)* | — | committer's team | Rolled **between possessions** in `RotationState`, off the possession path. **One** FT to the other team, **possession unchanged**. Charged to a separate `technicalFouls` counter: **excluded** from the 6-foul limit and from the bonus tally — the only such exclusion |
| `FOUL` | `FLAGRANT_FOUL_1` · `FLAGRANT_FOUL_2` | the committer | **the fouled player**, at the two shot sites — **null** at the rebounding site | — | committer's team | A severity roll **on top of** a foul that already happened, at all three foul sites. ⚠ **The counterparty follows the underlying foul**: at the shot sites the fouled shooter is identified, so it is carried; at the rebounding site no individual victim exists (see below), so it is null. **Always exactly 2 FTs, which REPLACE the underlying award** (a flagrant stopped three is 2, not 3 and not 5). Unlike a technical it **is** a personal foul and **counts** toward the bonus. A defensive one **returns the ball**; `_2` (flat 15%) ejects immediately |
| `FREE_THROW` | `MADE_SHOOTING` · `MISSED_SHOOTING` | the shooter | — *(belongs to the `FOUL`)* | — | — | From a foul that **stopped** the shot — 2 per trip, **3 if the stopped shot was a `THREE`** |
| `FREE_THROW` | `MADE_BONUS` · `MISSED_BONUS` | the shooter | — *(belongs to the `FOUL`)* | — | — | A **penalty** trip: after a rebounding foul, or a `NON_SHOOTING_FOUL` committed in the penalty. 2 per trip |
| `FREE_THROW` | `MADE_AND_ONE` · `MISSED_AND_ONE` | the shooter | — *(belongs to the `FOUL`)* | — | — | The single FT riding a made basket |
| `FREE_THROW` | `MADE_TECHNICAL` · `MISSED_TECHNICAL` | the shooter | — *(belongs to the `FOUL`)* | — | — | **The only FT source where nobody was fouled**, so the shooter is a deterministic highest-`freeThrows` pick from the on-floor five — *not* the `foulDrawing`-weighted draw. Expect one player to shoot essentially all of them |
| `FREE_THROW` | `MADE_FLAGRANT` · `MISSED_FLAGRANT` | the shooter | — *(belongs to the `FOUL`)* | — | — | The **two** FTs from a flagrant, shot by **the player who was fouled** — the best-shooter rule's premise (nobody was fouled) does not hold here |
| `REBOUND` | `OFFENSIVE` | the rebounder | — *(a contest against four, no named loser)* | — | — | Ball stays with the shooting team for a second chance |
| `REBOUND` | `DEFENSIVE` | the rebounder | — *(a contest against four, no named loser)* | — | — | Possession ends |
| `REBOUND` | `OUT_OF_BOUNDS_OFFENSE` · `OUT_OF_BOUNDS_DEFENSE` | — *(**no rebounder**)* | — | — | — | The missed shot left the court. ⚠ Reuses the `REBOUND` play type but is **excluded from the rebound reconciliation**, which exact-matches `OFFENSIVE`/`DEFENSIVE`, so it never counts as a box-score rebound |

> ⚠ **A BLOCKED SHOT PRODUCES NO `REBOUND` ROW AT ALL, AND BY THE NBA RULE IT SHOULD.**
> A recovered block is a rebound for whoever comes up with the ball. The engine resolves
> *which side* recovers — `BlockRecovery` is `RECOVERED_DEFENSE` 45% / `RECOVERED_OFFENSE`
> 30% / `OOB_DEFENSE` 13% / `OOB_OFFENSE` 12%, and the possession forks correctly on it —
> but **emits no event and credits no player**. Measured: **3.41 recovered in bounds per
> team-game, credited to nobody** (2.04 defensive, 1.36 offensive); the 1.14 that go out of
> bounds are correctly rebound-less.
> ⚠ **The BLOCK-OOB slices emit nothing either.** `OOB_DEFENSE` (0.59) and `OOB_OFFENSE`
> (0.54) are resolved inside `BlockRecovery` with **no event at all** — where the identical
> situation off a *missed shot* does emit `REBOUND / OUT_OF_BOUNDS_*`. So the event log
> under-counts ownerless possession changes by **~1.14/team-game**, which is what would
> make a derived "team rebound" figure wrong today.
> ⚠ **This is a KNOWN GAP, owned by §3.21** (the rebound pool — def rebounds run 27.90
> against a sourced 32.4). **Do not "fix" it by adding a row here first**: crediting a
> rebounder means *selecting* one, and #025 D made the recovery draw flat and
> skill-independent **deliberately**, so the blocked ball would not inherit the board
> contest. The vocabulary changes only once that design question is resolved — and when it
> does, **the OOB slices should start emitting too**, so the log becomes complete.
> *(Found 2026-08 while closing §3.20.)*

### What `opponent_player_id` holds

**It is populated exactly where a real contest identified an individual victim** — and
nowhere else. Every populated value is a player an existing draw already selected.

| Site | primary | opponent |
|---|---|---|
| `TURNOVER` / `STOLEN` | the ball-loser | the **stealer** |
| `SHOT` / `BLOCKED_*` | the shooter | the **blocker** |
| `FOUL` / `SHOOTING_FOUL` | the **committer** | the fouled **shooter** |
| `FOUL` / `AND_ONE` | the **committer** | the fouled **shooter** |
| `FOUL` / `NON_SHOOTING_FOUL` | the **committer** | the fouled **shooter** |
| `FOUL` / `FLAGRANT_FOUL_*` *(shot sites)* | the **committer** | the fouled **shooter** |

⚠ **Note the relationship INVERTS between play types, and that is by design.** On
`SHOT` and `TURNOVER`, primary is the **victim** and the opponent is the actor. On every
`FOUL`, primary is the **committer** and the opponent is the victim. The column is
defined as *"the other side of the play"*, not as "the defender" — which is what lets
one rule cover both and keeps the always-opposite-team invariant true.

**On `FOUL`, `primary_player_id` is ALWAYS the committer** — the player charged
`recordFoul()`, counted toward the six-foul limit, and whose team is
`committing_team_id`. That is a stronger rule than the general invariant, it holds at
all seven foul sites, and it is test-enforced.

#### The deliberate nulls, and why each one is null

⚠ **A blank is a decision. Do not "fix" one by populating a reachable player** — the
test asserts these stay empty. Three distinct reasons:

| Reason | Events |
|---|---|
| **No individual victim is identified** — the foul is against the TEAM contesting the board. The bonus/flagrant FT shooter is a weighted **draw standing in for the award**, not the player who was pushed; using them would attribute the foul to someone the engine never decided was fouled | `REBOUNDING_FOUL_DEFENSE`, `REBOUNDING_FOUL_OFFENSE`, and a flagrant at the rebounding site |
| **No counterparty exists at all** | `TECHNICAL_FOUL` (behavioral, no contest — the same premise that makes its FT shooter a best-shooter pick), every `REBOUND` (a contest against four, with a winner but no named loser), every `FREE_THROW` (uncontested; the other side belongs to the `FOUL` that caused it), and the seven unforced turnover causes |
| **One exists but is not modelled** | `OFFENSIVE_FOUL` (both its events) — the charge-drawer is real, but the engine never picks one and doing so requires a new RNG draw |

---

## Detail sections

Only for the events carrying something a table cannot hold.

### The steal

One row: `play_type = TURNOVER`, `outcome = STOLEN`, `primary_player_id` = the
ball-loser, `opponent_player_id` = **the stealer**.

**What the column buys is the per-creditor reconciliation.** A total-level check —
`count(STOLEN events) == Σ box_score.steals` — passes even when the engine credits the
**wrong player**, because the sums still match. With the stealer on the event:

> `count(TURNOVER/STOLEN events with opponent_player_id = X) == box_score.steals(X)`, ∀X

with the same shape for blocks against `box_score.blocks(X)`.

⚠ **The steal RATE is a separate question and is not this file's.** It is a **derived**
quantity — steals = turnovers × the `STOLEN` share — so it is re-measured after the
turnover count is calibrated, never tuned independently. See `calibration.md`.

### Why a stopped shot is not a field-goal attempt

**`SHOOTING_FOUL` and `NON_SHOOTING_FOUL` charge no FGA.** The foul roll runs *before*
`recordFieldGoalAttempt()` and the branch returns, so a stopped shot never reaches the
shot accounting at all. This is correct by rule, and the reasoning is worth having
written down because it recurs.

**The real-basketball rule is a RELEASE test, not a foul test.** A field goal attempt is
charged when the shooter **releases** the ball toward the basket. So a foul on a shot
produces one of three outcomes, and they differ:

| Case | FGA? | FGM? | What the engine does |
|---|---|---|---|
| Fouled **before release** — the contact stopped the shot | **no** | no | ✅ `FOUL`/`SHOOTING_FOUL` + FTs. **This is the only case modelled.** |
| Fouled **during** the motion, ball **released and missed** | **yes** (a missed FGA) | no | ⬜ **NOT MODELLED** |
| Fouled on a **made** shot (and-1) | yes | yes | ✅ `SHOT`/`MADE_*` + `FOUL`/`AND_ONE` + 1 FT |

⚠ **The middle case is a real gap, deliberately noted rather than hidden.** The engine
treats every shooting foul as the no-release case, which is what the code means by *"the
contact stopped the shot"*. A real league charges an FGA on some shooting fouls;
this engine never does. Consequence: for a given number of shot possessions the engine's
FGA runs **lower** than a real league's by roughly the shooting-foul rate. *(It does not
show up as a deficit today — FGA currently reads over its target for unrelated
shot-mix reasons — but it is a shape difference, not noise.)*

#### Why this settles two recurring redesign proposals

**"Fold the foul into the shot: `SHOT` with `outcome = SHOOTING_FOUL`, inferring a
miss."** ⚠ **Rejected — it encodes something the rulebook denies.** A stopped shot is
not a miss; it is a **non-attempt**. Every `SHOT` event is an attempt, which is what
makes `count(SHOT events) == Σ box_score.fieldGoalsAttempted` hold. Putting a
non-attempt on a `SHOT` event either breaks that reconciliation or forces it to decode
the `outcome` string to subtract the exceptions.

⚠ **And it is the shape that does NOT extend.** If the middle case above is ever
modelled, it emits a genuine `SHOT`/`MISSED_*` **plus** a `FOUL` — exactly today's
shape. Merging them now would have to be unpicked to add it.

**"`SHOT` with `outcome = AND_ONE`, inferring a make."** Coherent — an and-1 really is a
made shot — but a made shot **already has** a `SHOT` event. Replacing `MADE_2PT_DRIVE`
with `AND_ONE` loses the **shot type**, which the shot-mix calibration depends on;
emitting a second `SHOT` double-counts FGA. Preserving both needs
`MADE_2PT_DRIVE_AND_ONE` × every shot type — a combinatorial vocabulary for a fact that
is independent of shot type.

**The cost both proposals share, and the decisive one:** the foul would leave the `FOUL`
log. Penalty status is derived by counting **`FOUL` events** by committing team, and the
six-foul limit, the foul-mix instrumentation and the flagrant fork all key off the same
play type. Moving a foul onto a `SHOT` outcome means teaching every one of those to look
in two places — to save one row out of ~200 per team-game. Under rule 1, a shooting foul
has a second **accounting** (bonus tally, foul-out, disqualification), and accounting
earns an event.

### The charge — the two-event trap

A charge emits **two events for one occurrence**: a `TURNOVER`/`OFFENSIVE_FOUL` and a
`FOUL`/`OFFENSIVE_FOUL`, sharing the outcome string deliberately so they read as the
same word. **Every reconciliation that counts events must tolerate that** — the
harness's `Fouls / team / game` line carries ~1.26 per team-game from this alone.

It earns the second event under rule 1 because the second fact is an *accounting* one: a
charge **is** a personal foul by rule, and accounting cannot ride a column, because
`isInBonus` counts `FOUL` **events**.

⚠ **Both of its rows carry a null `opponent_player_id`, deliberately.** The charge has a
real counterparty — the defender who drew it — and the engine **never picks one**.
Populating it requires a new RNG draw. **A blank here is a decision, not an oversight.**

### Steal / block symmetry

A **block is a field-goal outcome exactly as a steal is a turnover outcome** — a
defensive event modeled as an outcome-flavor on the offensive action it interrupts, plus
a separate defender-credit accumulator. The two are deliberately parallel:

| | Steal | Block |
|---|---|---|
| Event | `TURNOVER` / `STOLEN` | `SHOT` / `BLOCKED_*` |
| `primary_player_id` | the ball-loser (victim) | the shooter (victim) |
| `opponent_player_id` | the stealer | the blocker |
| Separate credit | `recordSteal()` → `BoxScore.steals` | `recordBlock()` → `BoxScore.blocks` |
| New `PlayType`? | no (a kind of turnover) | no (a kind of missed shot) |
| Reconciliation | `count(STOLEN with opponent = X) == steals(X)`, ∀X | `count(BLOCKED% with opponent = X) == blocks(X)`, ∀X |

A blocked shot is **carved off the top** of the shot outcome (a three-way
MAKE/MISS/BLOCK draw): `P(BLOCK)` is a defender-vs-finisher contest (`rimProtection` at
the rim / `shotContest` on jumpers, vs the shooter's `finishing`), then the make/miss
contest runs on the remainder. It charges the shooter a **missed FGA** (no FGM; `+3PA`
on a blocked THREE) and carries **no assist**. A separate flat four-way `BlockResolver`
then decides the loose ball (offense/defense × in-bounds/OOB); an offense-recovered
block re-enters the second-chance loop at `ShotSelector`, skipping `ReboundResolver`.

⚠ **The credit and the column are SIBLINGS, not parent and child.** `recordBlock()`
increments an in-memory accumulator that is copied to `box_score.blocks` at persist
time; `opponent_player_id` is written onto the event row. Neither derives from the
other, so they cannot double-count — and equally, **nothing structurally forces them to
agree.** The per-creditor check above is what detects a divergence.

### The missed-shot outcome and the rebound

After a missed `SHOT`, a **single four-way missed-shot outcome** (`MissedShotResolver`)
decides a real rebound or an out-of-bounds ball. On a rebound the `REBOUND` event's
`primary_player_id` is the rebounder — an `OFFENSIVE` rebound keeps the ball with the
shooting team (a second-chance possession runs through the full flow again); a
`DEFENSIVE` rebound ends the possession. On an OOB outcome the ball left the court and
**no rebounder is credited** (`primary_player_id` is null) — `OUT_OF_BOUNDS_OFFENSE`
retains for a second chance, `OUT_OF_BOUNDS_DEFENSE` ends the possession.

⚠ **OOB events reuse the `REBOUND` play type but are excluded from the rebound
reconciliation**, which exact-matches `OFFENSIVE`/`DEFENSIVE`, so they never count as a
box-score rebound.

### Rebounding fouls, and why `committing_team_id` exists

Before the four-way board draw runs, a **rebounding-foul chance is carved off the top**:
on a hit the whistle stopped play, so the board contest **never runs** and no rebound is
credited. The foul is **two-sided** — a defensive box-out push or an offensive
over-the-back, defense-leaning — **which is why the committing team is stored explicitly
rather than inferred from the possession's orientation.**

**Penalty status is DERIVED from this event log, never stored.** "Team T is in the bonus
in period P" is computed on demand as

> `count(FOUL events where committing_team_id = T and period = P` <br>
> `        and outcome <> 'TECHNICAL_FOUL') >= BONUS_FOULS_PER_PERIOD`

with **no `teamFouls` column, field, or reset logic** — the fact already lives in the
events, and a stored counter could only ever disagree with them. The count is
**emit-then-count**: the current `FOUL` is written to the log *first*, so the **Nth foul
itself** (the one that reaches the threshold) sends the fouled team to the line.

⚠ **A `TECHNICAL_FOUL` is the ONE outcome excluded from this tally**
(`GameData.countsTowardBonus`). Every other foul kind counts, including flagrants, the
charge and the non-shooting foul. **Any future foul type must consciously decide whether
it counts** — and pin that decision with a test, since "we decided it counts" and "we
forgot to exclude it" are otherwise indistinguishable.

⚠ **The exclusion lives in TWO places, and they must agree.** `GameData.isInBonus` is
the engine's derivation; `CalibrationHarness` computes its **own** per-team-period tally
over the same events for the bonus-rate line. A foul type excluded from one and not the
other makes the instrument silently disagree with the engine.

The possession then **forks on who fouled**:

- **Defense committed** → the offense is fouled → **under the bonus** it retains for a
  second chance (respecting `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`); **in the bonus**
  it shoots bonus FTs and the possession ends.
- **Offense committed** → the defense is fouled → the **possession always ends** for the
  offense; in the bonus the defense shoots its bonus FTs first.

Bonus free throws reuse the shooting foul's award block verbatim, so FT and points
reconciliation is automatic. A rebounding foul increments the committing player's
`fouls` exactly like any foul, feeding foul-outs.

### And-1 — a foul rolled ALONGSIDE a made basket

An **and-1** is the defender fouling, the shot going in anyway, and the basket counting
**plus one** free throw. It is a **second, post-make foul roll** carved **beside the
assist roll**, inside the `if (made)` block. The pre-shot foul branch keeps meaning
exactly "P(contact stopped the shot)", and the and-1 is an independent slice with its
own rate.

The assist and the and-1 coexist and do not interact: the assist is already stamped on
the `SHOT` event before the and-1 rolls, so a made basket can be both assisted **and** an
and-1.

Two properties distinguish it from every other foul:

- **It never forks the possession.** A made basket already ended the offense's
  possession — the FT is tacked on before the ball changes hands.
- **It is always exactly ONE free throw**, by rule — it **never consults the bonus**. (It
  still *counts toward* the committing team's period tally; it just doesn't read it.)

### Free-throw sources — why the outcome is self-describing

Every `FREE_THROW` event is **self-describing**: its outcome carries both the result and
the **source** that sent the shooter to the line — `MADE_SHOOTING`, `MISSED_BONUS`,
`MADE_AND_ONE`, and so on. A backward join ("look at the preceding `FOUL`") is fragile
once there are several sources, so the source lives on the event itself.

The `MADE`/`MISSED` prefix **leads** so made-vs-missed stays readable by a prefix check,
and the source strings cannot collide with the `FOUL` outcomes (`SHOOTING_FOUL` /
`REBOUNDING_FOUL_*` / `AND_ONE`), which live on a different `play_type`.

⚠ **The FT count is a SEPARATE per-situation value from the source** (and-1 = 1;
shooting foul and bonus = 2; a stopped three = 3; flagrant = 2). Count and source are
independent.

### Contact fouls apply to ALL shot types

Every shot type can be fouled, via a **graduated per-shot-type foul multiplier** in
`SimConfig` — post/drive frequent, perimeter uncommon, three rare (a closeout on a
three-point shooter is a real foul). One shared multiplier table drives **both** foul
rolls — the pre-shot "the contact stopped the shot" roll and the post-make and-1 roll —
because a shot type's propensity to draw contact is a property of *the shot*, not of
which roll is asking.

**A fouled `THREE` awards 3 free throws.** The count is a **rule** on the enum
(`ShotType.freeThrowsIfFouled()` — 3 for `THREE`, 2 for the rest), deliberately *not* a
`SimConfig` value: `SimConfig` is the tuning surface, and housing a rule there invites
someone to tune it.

> ⚠ **The general rule this follows**: in `SimConfig`, **`public static final` means a
> rule of basketball or the shape of the model; `private final` + accessor means a
> tunable knob** bound from the active profile. A non-profilable constant has no property
> key at all, so a profile cannot reach it. The FT counts are all on the static side.

⚠ **An and-1 stays exactly ONE free throw for every shot type, a made three included** —
a made 3 plus a foul is 3 points and 1 FT. Only the *stopped*-shot count graduates.
These two counts sitting side by side is the easy wrong turn in this area, and is
guarded by an explicit test.

**The two foul rolls are mutually exclusive by control flow, not by a check.** The
pre-shot branch *returns*, so a shot that drew a stopped-shot foul never reaches the
make/miss roll and therefore never reaches the and-1 roll. One shot attempt emits **at
most one** of `SHOOTING_FOUL` / `AND_ONE`, never both.

**A fouled three needs no special vocabulary**: it emits the existing `SHOOTING_FOUL`
followed by **three** existing `MADE_SHOOTING`/`MISSED_SHOOTING` events —
three-FT-ness is carried by *there being three of them*, not by a tag. A consumer that
wants "3-FT trips" counts `SHOOTING` FTs per preceding `FOUL`.

---

## See also

- **`docs/game.md`** — the models (`Game`, `GameEvent`, `BoxScore`), the possession flow
  / calculation sequence, and the API surface.
- **`docs/possession-flow.puml`** — the flow as a diagram: every branch in engine order.
  ⚠ **Branch ORDER within a partition is often load-bearing**, and the diagram is the
  place that records why.
- **`docs/player.md`** — the *"Possession Event → Skills Used"* table: which skills each
  of these events reads. ⚠ **It is the only other per-event table in the docs**, and the
  two cross-link deliberately so they cannot drift apart.
- **`docs/calibration.md`** — what these events are tuned toward, and the single source
  of truth for the targets.
- **`docs/decisions.md`** — **the history**: why each of these rules and outcomes is the
  way it is, as numbered `#NNN` entries. This file describes the vocabulary as it stands
  today; that one records how it got here.
