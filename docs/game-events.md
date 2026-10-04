# Game events — the vocabulary the engine emits

Every event `PossessionEngine` writes to `game_event`, as a `(play_type, outcome)`
pair with **who is on the row**. This is the single per-event reference. `game.md` keeps
the models, the possession order and the API; the `possession-flow*.puml` diagrams draw
the branches; [player.md](player.md) says which *skills* each event reads.

> **This documents what the engine DOES.** Every row is derived from an `addEvent` call
> site in `PossessionEngine`, and the participant columns are checked against real
> simulated events by `GameSimulatorIntegrationTest`. Do not start a second per-event
> table: it will drift.

> **Data caveat — one renamed outcome.** `COMMON_FOUL` was renamed `NON_SHOOTING_FOUL`
> with no migration, so `game_event.outcome` holds `COMMON_FOUL` on games simulated
> before that rename. The engine emits only the new spelling, so only a query over
> persisted history is affected. Those rows are disposable test data, so no dual match is
> needed today. If real games are ever retained across the boundary, a reader spanning it
> must match both spellings.

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

### Why a shooting foul is not folded into the `SHOT` event

Two proposals recur: a `SHOT` with `outcome = SHOOTING_FOUL`, and a `SHOT` with
`outcome = AND_ONE`. Both are rejected.

- **A stopped shot is not an attempt.** An FGA is charged on release. The engine treats
  every shooting foul as contact before release, so it charges **no** FGA: the foul roll
  runs before `recordFieldGoalAttempt()` and returns. Every `SHOT` event is therefore an
  attempt, which is what makes `count(SHOT) == Σ fieldGoalsAttempted` hold. Putting a
  non-attempt on a `SHOT` row would break that identity or force it to decode `outcome`.
- **An and-1 already has a `SHOT` row.** Replacing `MADE_2PT_DRIVE` with `AND_ONE` loses
  the shot type the shot-mix calibration reads, and a second `SHOT` double-counts FGA.
- **The foul would leave the `FOUL` log.** Penalty status, the six-foul limit and the
  flagrant fork all count `FOUL` events. A shooting foul has a second *accounting*, and
  accounting earns an event (rule 1).

| Case | FGA? | FGM? | Engine |
|---|---|---|---|
| Fouled **before release** (contact stopped the shot) | no | no | `FOUL`/`SHOOTING_FOUL` + FTs. The only case modelled |
| Fouled **during** the motion, released and missed | yes | no | not modelled (see `game.md`, Simplifications and gaps) |
| Fouled on a **made** shot | yes | yes | `SHOT`/`MADE_*` + `FOUL`/`AND_ONE` + 1 FT |


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

`outcome` is an unconstrained `VARCHAR`, so **a new outcome string is free**: no
migration, no API change. `play_type` is a **closed OpenAPI enum**
(`[SHOT, TURNOVER, REBOUND, FOUL, FREE_THROW]`), so **a new play type is expensive**: an
API change plus an unhandled case for every exhaustive consumer. That asymmetry is why
nine turnover causes, technicals, flagrants and the non-shooting foul cost nothing, and
why a `PlayType.STEAL` was rejected (every reader counting `TURNOVER` events would
under-count by about 55%).

A renamed outcome leaves old rows behind, so a rename needs a stated decision about
history (see the data caveat above).

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
| `SHOT` | `MADE_2PT_DRIVE` · `MADE_2PT_PERIMETER` · `MADE_2PT_POST` · `MADE_3PT` | shooter | — | assister, if the roll hit — ⚠ **§3.22 (#044 E): the roll runs at HALF the ordinary chance when the shooter is the player who just took the offensive rebound** (a putback; `OFFENSIVE_REBOUNDER_ASSIST_LEAN`). Not zero, and not "any second-chance shot" — a kick-out three off an offensive board is assisted as usual | — | Made FG. The assister is a **teammate** — not a counterparty. An and-1 `FOUL` may follow for the same shot. **A putback is an ordinary `SHOT` — §3.22 adds no outcome**; what it changes is *who* is likely to be the shooter after an `OFFENSIVE` `REBOUND` (#044 A) and the assist condition here |
| `SHOT` | `MISSED_2PT_DRIVE` · `MISSED_2PT_PERIMETER` · `MISSED_2PT_POST` · `MISSED_3PT` | shooter | — | — | — | Missed FG. A `REBOUND` event follows, **unless a rebounding foul intervenes** — that `FOUL` replaces the board and no rebound is credited |
| `SHOT` | `BLOCKED_2PT_DRIVE` · `BLOCKED_2PT_PERIMETER` · `BLOCKED_2PT_POST` · `BLOCKED_3PT` | shooter (the **victim**) | **the blocker** | — | — | Charges a missed FGA (no FGM; `+3PA` on a blocked three) and carries **no** assist. `BLOCKED_3PT` is rare (a closeout swat). Mirrors the steal: a defender-credit accumulator (`recordBlock()`) plus the counterparty column. **A `REBOUND` event follows** — since §3.21 (#043 E), exactly as off a `MISSED_*` shot. The flat four-way `BlockRecovery` draw (#025 D) picks the **side**; the `REBOUND` row then names **which of that side's five** secured it |
| `TURNOVER` | `STOLEN` | ball-handler (the **victim**) | **the stealer** | — | — | ~56% of turnovers, kept dominant. |
| `TURNOVER` | `OFFENSIVE_FOUL` | ball-handler | — *(the drawer is **not modelled**)* | — | — | The charge. ⚠ **Also emits a `FOUL` row below — two events, one occurrence.** ⚠ **`opponent` is null DELIBERATELY**: a charge has a real counterparty (the defender who drew it), but the engine never picks one and doing so requires a new RNG draw. See *The charge* |
| `TURNOVER` | `SHOT_CLOCK_VIOLATION` · `BAD_PASS` · `TRAVELLING` · `LOST_BALL_OUT_OF_BOUNDS` · `3_SECONDS_VIOLATION` · `8_SECONDS_BACKCOURT_VIOLATION` · `OVER_AND_BACK` | ball-handler | — *(no counterparty exists)* | — | — | The seven unforced causes: one actor, one fact, nothing owed (rule 1). ⚠ `LOST_BALL_OUT_OF_BOUNDS` is **distinct** from `OUT_OF_BOUNDS_*` on `REBOUND` |
| `FOUL` | `SHOOTING_FOUL` | **the defender** who committed it | **the fouled shooter** | — | defense | Stops a shot of **any** type; FTs follow — **3 if it stopped a `THREE`**, else 2. ⚠ **Note the orientation**: primary is the *committer* here, so the counterparty is on **offense**. A second roll re-partitions some of these into `NON_SHOOTING_FOUL`. ⚠ **Charges NO field-goal attempt** — see *Why a shooting foul is not folded into the `SHOT` event* |
| `FOUL` | `NON_SHOOTING_FOUL` | the defender | **the fouled shooter** | — | defense | The same stopped-shot roll, re-partitioned by a second flat roll on the **already-charged** foul, so the foul total holds by construction. **No FTs outside the penalty, 2 bonus FTs inside it.** ⚠ The possession **ENDS either way** — deliberately wrong as basketball, and what protects the FGA budget |
| `FOUL` | `OFFENSIVE_FOUL` | ball-handler (charged `recordFoul()`) | — *(as above)* | — | **offense** | The charge as a foul, emitted **in addition to** the `TURNOVER` row. ⚠ **`committing` = the OFFENSE** — with `REBOUNDING_FOUL_OFFENSE` the only two such sites, so the only paths that move the *defense* toward the bonus. Counts toward the bonus and the 6-foul limit; **not** flagrant-eligible; no FTs (the possession already ended) |
| `FOUL` | `REBOUNDING_FOUL_DEFENSE` | the defender who pushed | — *(**no individual victim is identified**)* | — | defense | Box-out push in the rebound phase — the **offense** is fouled, so it retains, or shoots **bonus** FTs in the penalty |
| `FOUL` | `REBOUNDING_FOUL_OFFENSE` | the offensive player | — *(as above)* | — | **offense** | Over-the-back — the **defense** is fouled and the possession **ends**. The two-sidedness of this pair is why `committing_team_id` exists |
| `FOUL` | `AND_ONE` | the defender | **the fouled shooter** | — | defense | ⚠ **The FGA and FGM are already charged** on the preceding `SHOT` event — an and-1 is a made basket, so unlike a stopped shot it *is* an attempt. Foul on **any** made shot — the basket counts and **one** FT follows, a made three included. Never consults the bonus. The FT is the trip's last, so it is **live**: a miss is rebounded, and an offensive board returns the ball. An ordinary and-1 otherwise ends the possession |
| `FOUL` | `TECHNICAL_FOUL` | the committer | — *(the FT shooter is **not** a counterparty)* | — | committer's team | Rolled **between possessions** in `RotationState`, off the possession path. **One** FT to the other team, **possession unchanged**. Charged to a separate `technicalFouls` counter: **excluded** from the 6-foul limit and from the bonus tally — the only such exclusion |
| `FOUL` | `FLAGRANT_FOUL_1` · `FLAGRANT_FOUL_2` | the committer | **the fouled player**, at the two shot sites — **null** at the rebounding site | — | committer's team | A severity roll **on top of** a foul that already happened, at all three foul sites. ⚠ **The counterparty follows the underlying foul**: at the shot sites the fouled shooter is identified, so it is carried; at the rebounding site no individual victim exists (see below), so it is null. **Always exactly 2 FTs, which REPLACE the underlying award** (a flagrant stopped three is 2, not 3 and not 5). Unlike a technical it **is** a personal foul and **counts** toward the bonus. A defensive one **returns the ball**; `_2` (flat 15%) ejects immediately |
| `FREE_THROW` | `MADE_SHOOTING` · `MISSED_SHOOTING` | the shooter | — *(belongs to the `FOUL`)* | — | — | From a foul that **stopped** the shot — 2 per trip, **3 if the stopped shot was a `THREE`**. ⚠ **A missed LAST attempt is followed by a `REBOUND` event** (#043 C) — see *Rebound sources* |
| `FREE_THROW` | `MADE_BONUS` · `MISSED_BONUS` | the shooter | — *(belongs to the `FOUL`)* | — | — | A **penalty** trip: after a rebounding foul, or a `NON_SHOOTING_FOUL` committed in the penalty. 2 per trip. ⚠ **A missed LAST attempt is followed by a `REBOUND`** (#043 C), and at the rebounding site the shooter's team may be the DEFENSE — the board is resolved for the **possession's** offense either way |
| `FREE_THROW` | `MADE_AND_ONE` · `MISSED_AND_ONE` | the shooter | — *(belongs to the `FOUL`)* | — | — | The single FT riding a made basket. ⚠ Being the trip's only attempt it is **always the last, so always live** — a missed one is rebounded, and an offensive board yields a live second-chance possession **after a made basket** (#043 C, reversing #029 B a second time) |
| `FREE_THROW` | `MADE_TECHNICAL` · `MISSED_TECHNICAL` | the shooter | — *(belongs to the `FOUL`)* | — | — | ⚠ **NO `REBOUND` follows a miss** — play resumes with the ball as it was (#032 G). **The only FT source where nobody was fouled**, so the shooter is a deterministic highest-`freeThrows` pick from the on-floor five — *not* the `foulDrawing`-weighted draw. Expect one player to shoot essentially all of them |
| `FREE_THROW` | `MADE_FLAGRANT` · `MISSED_FLAGRANT` | the shooter | — *(belongs to the `FOUL`)* | — | — | ⚠ **NO `REBOUND` follows a miss** — the offense retains **by rule** either way (#034 B), so rebounding it would double-count that path. The **two** FTs from a flagrant, shot by **the player who was fouled** — the best-shooter rule's premise (nobody was fouled) does not hold here |
| `REBOUND` | `OFFENSIVE` | the rebounder | — *(a contest against four, no named loser)* | — | — | Ball stays with the shooting team for a second chance. ⚠ **Emitted from THREE sites** — a missed `SHOT`, a **blocked** shot recovered in bounds (#043 E), and a missed **last** `FREE_THROW` (#043 C) — and the three have different offensive shares. See *Rebound sources* |
| `REBOUND` | `DEFENSIVE` | the rebounder | — *(a contest against four, no named loser)* | — | — | Possession ends. Same three sites as `OFFENSIVE` above |
| `REBOUND` | `OUT_OF_BOUNDS_OFFENSE` · `OUT_OF_BOUNDS_DEFENSE` | — *(**no rebounder**)* | — | — | — | The attempt left the court — off a missed `SHOT`, a **blocked** shot knocked out (#043 E2), or a missed last `FREE_THROW`. ⚠ Reuses the `REBOUND` play type but is **excluded from the rebound reconciliation**, which matches on a **non-null `primary_player_id`**, so it never counts as a box-score rebound |

> A recovered block is a rebound for whoever comes up with the ball. `BlockRecovery`
> draws the **side** from flat weights (`sim.block-recovered-*`, `sim.block-oob-*`); a
> skill-weighted pick then names **which of that side's five** secured it.
> `isOffensiveRebound` is never called on this path. The two OOB slices emit a `REBOUND`
> with no rebounder (`primary_player_id` null). A `team_rebounds` column is not built:
> nothing consumes it and it stays derivable.

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
every foul site, and it is test-enforced.

#### The deliberate nulls, and why each one is null

⚠ **A blank is a decision. Do not "fix" one by populating a reachable player** — the
test asserts these stay empty. Three distinct reasons:

| Reason | Events |
|---|---|
| **No individual victim is identified** — the foul is against the TEAM contesting the board. The bonus/flagrant FT shooter is a weighted **draw standing in for the award**, not the player who was pushed; using them would attribute the foul to someone the engine never decided was fouled | `REBOUNDING_FOUL_DEFENSE`, `REBOUNDING_FOUL_OFFENSE`, and a flagrant at the rebounding site |
| **No counterparty exists at all** | `TECHNICAL_FOUL` (behavioral, no contest — the same premise that makes its FT shooter a best-shooter pick), every `REBOUND` (a contest against four, with a winner but no named loser), every `FREE_THROW` (uncontested; the other side belongs to the `FOUL` that caused it), and the seven unforced turnover causes |
| **One exists but is not modelled** | `OFFENSIVE_FOUL` (both its events) — the charge-drawer is real, but the engine never picks one and doing so requires a new RNG draw |

---


## Notes

Only for events carrying something the tables cannot hold.

### The charge: two events for one occurrence

A charge emits a `TURNOVER`/`OFFENSIVE_FOUL` and a `FOUL`/`OFFENSIVE_FOUL`, sharing the
outcome string so they read as one word. It earns the second event under rule 1: a charge
**is** a personal foul (bonus tally, six-foul limit), and accounting cannot ride a column
because the penalty tally counts `FOUL` events. Any reconciliation that counts events must
tolerate the pair. Both rows carry a null `opponent_player_id` on purpose.

### Rebound sources

A `REBOUND` is emitted from three places: a missed `SHOT`, a **blocked** shot recovered
in bounds, and a missed **last** `FREE_THROW`. Each has a different offensive share and
only the first has a tunable; the shares and the rule against re-tuning
`sim.base-offensive-rebound` to move the aggregate split live in
[calibration.md](calibration.md). Every actual rebound has an owner: an
`OUT_OF_BOUNDS_*` row is a possession change with **no** rebound, never a bucket for
rebounds the engine failed to attribute.

### Free throws

- **Self-describing.** Each `FREE_THROW` outcome carries the result and its source
  (`MADE_SHOOTING`, `MISSED_BONUS`, `MADE_AND_ONE`, ...). The `MADE`/`MISSED` prefix leads
  so a prefix check reads the result.
- **Count is independent of source.** A stopped three is 3, a stopped other shot 2, a
  bonus trip 2, a flagrant 2, an and-1 1 (a made three included), a technical 1. The 3 is
  a rule on `ShotType.freeThrowsIfFouled()`, not a `SimConfig` value. A fouled three
  needs no special vocabulary: it is three `FREE_THROW` rows after one `FOUL`.
- **Only the last attempt of a trip is live.** A missed earlier attempt is a dead ball and
  emits nothing; a missed last attempt is followed by a `REBOUND`. This applies to
  `SHOOTING`, `BONUS` and `AND_ONE`. `FLAGRANT` and `TECHNICAL` are excluded because their
  possession consequence is fixed by rule.

### The penalty tally is derived

"Team T is in the penalty in period P" is
`count(FOUL events with committing_team_id = T and period = P and outcome <> 'TECHNICAL_FOUL') >= sim.bonus-fouls-per-period`,
with no stored counter. The event is written **before** the count is read, so the foul
that reaches the threshold sends its own team to the line. A technical is the only
exclusion; any new foul type must decide whether it counts and pin that with a test.
`CalibrationHarness` computes its own copy of this tally; the two must agree (see
[calibration.md](calibration.md)).

### Reconciliation identities

The box-score identities (assists, blocks, steals per creditor, technical fouls, rebounds
with a non-null primary) are listed in [game.md](game.md), under `BoxScore`. A per-creditor
check (`count(STOLEN with opponent = X) == steals(X)`) is the one that catches crediting
the wrong player, which a total-level check cannot.

---

## See also

- [game.md](game.md): the models, the possession order, the API, and the simplifications.
- [possession-flow.puml](possession-flow.puml): the flow as a diagram. Branch order within a
  partition is often load-bearing.
- [player.md](player.md): which skills each event reads. It is the only other per-event table.
- [calibration.md](calibration.md): what these events are tuned toward.
- [decisions.md](decisions.md): why each rule is the way it is.
