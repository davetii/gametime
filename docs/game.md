# Game Domain *(model shipped §3.1; engine fills it through §3.16)*

The Game domain is the **data a simulated game produces**: the matchup and its
result (`Game`), the event log that records how it unfolded (`GameEvent`), and
the per-player stat line for that game (`BoxScore`). These three are one cohesive
shape — a `Game` *has* `GameEvent`s and *produces* a `BoxScore` — so they live in
one doc, the way [roster.md](roster.md) holds player↔team + lineups + transactions
together.

The **model** shipped in §3.1; the **possession engine** that fills it is built
through §3.16 and this doc now documents both:
- **§3.2** possession flow — shot selection / turnover / foul / shot outcome
- **§3.3** rebounding — the second-chance loop after a missed shot
- **§3.4** coaching + chemistry — coach modifiers on the flow, and real assists
- **§3.5** minutes / fatigue / substitution — the on-floor five changes during a
  game; `BoxScore.minutes` is now real (derived from possession share)
- **§3.7** blocked shots — a three-way MAKE/MISS/BLOCK draw; `BoxScore.blocks` is
  now real (a `SHOT`/`BLOCKED_*` event + a BLK credit, mirroring steals)
- **§3.8** missed-shot out of bounds — a missed shot resolves to one of four
  outcomes (off/def rebound, or OOB offense/defense) in a single skill-weighted
  draw (`MissedShotResolver` wraps `ReboundResolver`); OOB reuses the `REBOUND`
  play type with an `OUT_OF_BOUNDS_*` outcome and credits no rebounder
- **§3.9** turnover sub-causes — a weighted cause draw labels each turnover as one
  of nine causes on the unchanged turnover gate
- **§3.10** rebounding fouls + the team-foul/bonus substrate — a two-sided foul in
  the rebound phase, `committing_team_id`, and a penalty derived from the `FOUL` log
- **§3.11** and-1 — a defensive foul on a shot that still goes in (made FG + 1 FT),
  rolled beside the assist; free throws become **self-describing** (each carries its
  source: `SHOOTING` / `BONUS` / `AND_ONE` / `TECHNICAL` (§3.14a) / `FLAGRANT` (§3.14b))
- **§3.12** all-shot-type contact fouls — the binary contact gate deleted for a
  per-shot-type foul-multiplier table, so a perimeter shot or a three can draw a
  foul or an and-1 at a graduated rate
- **§3.13** foul trouble — a probabilistic "sit him before he fouls out" bench rule
  in the rotation step, which makes that step an **RNG consumer**
- **§3.14a** technical fouls — the first foul with **no contest behind it**, rolled
  per team **between possessions** (`RotationState`, not the possession path), one FT
  by a deterministic best-shooter pick, its **own counter** that feeds neither the
  6-foul limit nor the penalty tally, and a **derived** two-technical ejection.
  **Adds no branch to the possession flow at all** (#032 D)
- **§3.14b** flagrant fouls — a severity roll layered on top of an existing foul at
  all three foul sites; 2 FTs that **replace** the underlying award; a **personal**
  foul (6-foul limit and bonus tally, unlike a technical); and **the first free-throw
  path that returns the ball to the offense** — breaking #030 B's invariant, via the
  second-chance loop's existing `continue` under its existing cap. A flagrant-2 (a
  flat 15%) ejects immediately, still as a **derived** predicate

- **§3.16** shooting-foul composition + the charge fix — a second roll re-partitions
  the stopped-shot foul into a free-throw-free **`NON_SHOOTING_FOUL`**, and the **charge**
  becomes a real personal foul emitting its own `FOUL` event (decisions.md #039)

⚠ **THIS DOC ONCE CLAIMED PHASE 3'S POSSESSION MODEL WAS FEATURE-COMPLETE AFTER
§3.14b. IT WAS NOT — and the claim was stale in two ways worth naming**, because the
same trap is still live. It read *"§3.15 (profiles) and §3.16 (recalibration) add no new
mechanic and no new possession branch, so `possession-flow.puml` is structurally
final."*

1. **The numbering moved underneath it.** "§3.16 = recalibration" was true when
   written; **#038 renumbered recalibration to §3.19**, and the §3.16 slot became the
   foul-composition pass — which added **two** branches. ⚠ **Every "§3.16" in #030,
   #031, #032, #034 and #035 still means RECALIBRATION**; roadmap.md carries the
   mapping callout. Read the phase *name*, never the number alone.
2. **"Structurally final" was never safe to assert forward.** §3.16 added the
   `NON_SHOOTING_FOUL` fork and the charge's `FOUL`; §3.17 and §3.18 may add more.

`possession-flow.puml` **is** current as of §3.16 — both new forks are drawn — but it is
current because it was updated, not because the flow is finished.

The "Possession flow" section below reflects what the engine actually does today.

> **Scope discipline** (cf. decisions.md #014, #017, #020): the entities shipped
> §3.1 shaped for their consumers, not guessing the algorithm; each piece of
> simulation logic + any event-shape change (e.g. §3.4's `assist_player_id`)
> landed *with* the engine phase that consumes it, additively.

---

## Event persistence — RESOLVED *(decisions.md #020)*

The headline §3.1 choice (roadmap Design Decision #9) is settled: **every
`GameEvent` is persisted.** Play-by-play (§3.6 `GET /{gameId}/play-by-play`)
replays from stored rows rather than re-simulating, and the Phase 4 stats model
reconciles against the actual event log. The trade-off is row volume
(~200–350 events/game) — served **unpaginated** as a flat ordered list at the §3.6
read endpoint (#024 D found the volume modest; pagination deferred, not dropped),
not by dropping the data. See
decisions.md #020 for the full call (status lifecycle, box-score keying, event
shape).

---

## `Game`

The matchup and its outcome.

- `homeTeam` / `awayTeam` — references to `Team` (`home_team_id` / `away_team_id`
  FKs). **No season FK** — no schedule/season table exists yet (Phase 5); don't
  fabricate the link ahead of its consumer (#014/#017).
- quarter structure — period scores, regulation vs. overtime
- final score
- **status** — `GameStatus { SCHEDULED, IN_PROGRESS, FINAL }` (decisions.md
  #020). Minimal on purpose: no CANCELLED/POSTPONED until a consumer needs them.

---

## `GameEvent`

The possession-by-possession event log: possessions, plays, outcomes. One game
is an ordered sequence of these. **Every event is persisted** (event-persistence
section above; decisions.md #020).

### Columns

- `game_id` — FK to `game`
- `sequence` — monotonic ordering **across the whole game; does not restart per
  period**, so play-by-play is a single `ORDER BY sequence` read
- `period`
- `offense_team_id` / `defense_team_id`
- `play_type` — enum: `SHOT, TURNOVER, REBOUND, FOUL, FREE_THROW`
- `outcome` — free text (vocabulary below)
- `primary_player_id` — the one player the event is about (shooter, turnover
  committer, fouler, free-throw shooter)
- `assist_player_id` — **(§3.4)** the assister on a made-FG `SHOT` event, or
  `null`. Nullable because not every made shot is assisted, and non-`SHOT` events
  never carry one (decisions.md #022). It is a *second participant* on the SHOT
  event, not its own event — so `BoxScore.assists` reconciles against the count of
  `SHOT` events whose `assist_player_id` is set (events are the source of truth,
  #020). *Surfaced in the OpenAPI `GameEvent` model as `assistPlayerId` in §3.6
  (#024 D — see the API surface section below).*
- `committing_team_id` — **(§3.10)** the team that **committed** this event, or
  `null`. Set on **every `FOUL` event and only on `FOUL` events**: `SHOOTING_FOUL`
  and §3.11's `AND_ONE` carry the defender's team (both one-sided), the
  two-sided `REBOUNDING_FOUL_*` carries whichever side the roll picked, §3.14a's
  `TECHNICAL_FOUL` carries the committer's team — **populated even though a technical
  is excluded from the penalty tally** (#032 D) — and §3.14b's `FLAGRANT_FOUL_*` carries
  it too, where it is **load-bearing rather than merely uniform**: a flagrant's
  possession fork is decided by *which side committed it* (#034 C), so it is the first
  foul kind that genuinely **reads** this column rather than only filling it in.
  **§3.16 adds two more** (#039): `NON_SHOOTING_FOUL` carries the defender's team like a
  shooting foul, and the charge's `OFFENSIVE_FOUL` carries the **offense** — making it
  the **second** outcome after `REBOUNDING_FOUL_OFFENSE` where the committer is on
  offense, and the second path that can put the **defensive** team in the bonus.
  The column stays uniform across
  every foul and no consumer needs a special case. It exists
  because a rebounding foul can be committed by the **offense** (an over-the-back),
  so unlike a shooting foul the
  committer is **not** recoverable from `defense_team_id` (decisions.md #028 A2/D).
  This is the **one additive schema column** §3.10 took — a conscious, user-approved
  reversal of the schema-free stance §3.7–§3.9 held, earned because it has a real
  day-one consumer (the penalty derivation below) rather than being fabricated ahead
  of one (the #014/#017/#020 discipline). It stores a **raw fact** about the event
  (who committed it), *not* derived state — a per-period team-foul *count* column
  was explicitly rejected as exactly the duplicate-state trap #023 F avoids. Mirrors
  `assist_player_id` in shape (nullable `VARCHAR`, a second participant on an
  existing event, no new `PlayType`). *Not yet surfaced in the OpenAPI `GameEvent`
  model* — §3.10 took no API change; it is queryable in the DB and additive to the
  spec whenever a play-by-play or Phase-4 stats consumer wants it.
- *No in-game clock column.* §3.2 models time as an **abstract, configurable
  possession count** (decisions.md #021), and event time is **derived on read**
  (pace + `period` + `sequence`) for play-by-play display — not stored. §3.6
  resolved #021 B's open "single value vs. range" question by keeping time
  **derived, not stored** (#024 E); the storage shape is revisited only if the
  Phase 7 game view needs a per-event clock. `period` + `sequence` give full
  ordering today.

### Possession flow (§3.2–§3.3) — see also [possession-flow.puml](possession-flow.puml)

### The calculation sequence (which resolver runs when)

Before the event-by-event detail below, the **order the engine actually executes**.
Two phases per possession: a **rotation phase** (state only) and the **possession
phase** (the branching path). The rotation phase is the one that is easy to forget —
it is not on `possession-flow.puml`'s path and emits no events, but it runs first and
decides *who* the possession is played with.

| # | Phase / resolver | What it decides | On a hit |
|---|---|---|---|
| 0 | **`RotationState.advancePossession()`** — **both teams**, before every possession | drain/recover energy, **roll a technical (§3.14a)**, force off fouled-out **or ejected**, **foul-trouble sub (§3.13)**, fatigue sub | a `FOUL`/`TECHNICAL_FOUL` + **1 FT** on a technical hit — emitted by `PossessionEngine`, which the step returns the committer to (**three RNG draws**†) |
| 1 | **`ShotSelector`** | picks the shooter, then the shot type — **§3.17: the type is a per-type SHARE TABLE (`sim.shot-share-*`) × an avg-10 skill modifier, with the coach's lean applied to THREE and its RECIPROCAL to PERIMETER** (#040 C/D) | — |
| 2 | **`TurnoverResolver`** | turnover? then a 9-way cause draw; **§3.16: an `OFFENSIVE_FOUL` cause also charges a personal foul** | possession **ends** — and on a charge a **second** `FOUL` event is emitted for the same occurrence (§3.16) |
| 3 | **`FoulResolver.isFoul`** | foul that **stops** the shot (no basket), then **§3.14b: was it flagrant?**, then **§3.16: was it a non-shooting foul?** | FTs, possession **ends** — *unless flagrant: 2 FTs and the offense **RETAINS***; *if non-shooting: **no FTs at all** outside the penalty, 2 bonus FTs inside it, possession ends either way* |
| 4 | **`BlockResolver`** (via `ShotResolver`) | block carved off the top | loose-ball recovery |
| 5 | **`ShotResolver.isMade`** | make / miss | — |
| 6 | **`FoulResolver.isAndOne`** — *on a make only* | foul the shot **survived**, then **§3.14b: was it flagrant?** | +1 FT, possession ends — *unless flagrant: basket **+** 2 FTs **+** the offense **RETAINS*** |
| 7 | **`MissedShotResolver`** — *on a miss only* | wraps `ReboundResolver`; the §3.10 **rebounding foul** is carved off first (then **§3.14b: was it flagrant?**), then a single four-way board/OOB draw | ends, or a second-chance possession |

† **Steps 2–7 are the `resolvePossession()` path**; step 0 runs in `PossessionEngine`'s
loop *before* it, for **both** rotations (both teams are on the floor, so both tire).
Step 1 is easy to overlook but is load-bearing: the shot type it picks is what step 3
multiplies by (§3.12's per-shot-type foul multiplier), so it must precede the foul roll.

**Six things this ordering makes clear that the event list below does not:**
- **Steps 3 and 6 are mutually exclusive by control flow.** Step 3 *returns*, so a
  shot that draws a stopped-shot foul never reaches make/miss and therefore never
  reaches the and-1. One shot cannot be fouled twice (#030 B — the thing most likely
  to be misread in the foul model).
- **§3.14b's flagrant is a fourth question asked at three of these steps, not a fourth
  step.** It is *layered on top of* a foul that steps 3, 6 or 7 already resolved, so it
  re-partitions nothing and **no existing foul rate moves by construction** (#034 A).
  Its two consequences are what break the reading above: **a free-throw path can now
  RETURN the ball** (steps 3 and 6 no longer always end the possession — #030 B's
  invariant, deliberately broken via the second-chance loop's existing `continue`), and
  **the flagrant's 2 FTs REPLACE the step's own award rather than adding to it** (#034 C
  — a flagrant stopped `THREE` is 2 FTs, not 3 and not 5; a flagrant and-1 is 2, not 1).
  The and-1 case produces a made basket **plus** 2 FTs **plus** the ball back — three
  scoring channels on one possession, which nothing else in this engine does, and which
  looks exactly like a double-count bug in a play-by-play. It is the rule.
- **Steps 4 and 7 are not separate "rebound" stages.** `MissedShotResolver` **wraps**
  `ReboundResolver` into one four-way draw (#026); the rebounding foul is carved off
  the top *inside* step 7, before the board contest.
- **"No basket" ≠ "non-shooting", and since §3.16 step 3 produces BOTH.** The roll at
  step 3 is named for *stopping the shot*, not for being non-shooting — which is why
  §3.12 renamed the constant to `BASE_NO_BASKET_FOUL` (#030 F). **§3.16 then split its
  outcome**: a second roll (`sim.non-shooting-foul-share`, **0.3766 since §3.20**;
  §3.16 shipped it at 0.50)
  re-partitions the already-charged foul into a `SHOOTING_FOUL` (free throws follow) or
  a **`NON_SHOOTING_FOUL`** (no free throws outside the penalty). The engine's non-shooting
  fouls are therefore now **step 3's `NON_SHOOTING_FOUL`**, step 7's rebounding fouls, and
  step 2's `OFFENSIVE_FOUL` charge — the last of which §3.16 also made a real personal
  foul (#039 G).
- **§3.16's composition roll is the flagrant's twin, and the ORDER is load-bearing**
  (#039 F). Both are layered on a foul that `isFoul` already rolled and
  `recordFoul()` already charged, so **the foul total holds by construction** and no
  existing rate moves (#039 A — #034 A's argument, reused). Flagrant is asked **first**:
  a flagrant common foul is simply a flagrant, so the two never compose and there is no
  "flagrant that awards nothing" case.
- **⚠ A `NON_SHOOTING_FOUL` ENDS THE POSSESSION — the ball does NOT come back**, and that is
  **deliberately wrong as basketball** (#039 C). A real common foul is a side inbound
  and the offense keeps the ball. It is not modelled that way because every returning
  variant re-enters the loop at `ShotSelector` and yields a live attempt worth ~0.76
  FGA where the stopped shot charged none — and FGA is 88.8 against 89.1 real, ~0.3 of
  headroom. That caps a retaining variant at a ~6% share, which moves FTA by less than
  one attempt: the retention reading and the phase's goal are arithmetically
  incompatible. **Revisit only if §3.17's shot-mix work buys FGA headroom.**

† **`advancePossession()` consumes exactly THREE RNG draws per call** — §3.13's
foul-trouble sit roll (decisions.md #031, revising #023 C's RNG-free substitution),
plus §3.14a's technical roll and its committer draw (#032 B). **All three are taken
unconditionally at a fixed point**, whether or not anyone is in foul trouble and
whether or not the technical fires, so the seed stream never forks on rotation state.
The committer draw is deliberately taken even on the ~99.8% of calls that discard it,
for exactly that reason — a draw conditioned on the roll's own outcome would make the
count vary per call. Everything else in step 0 is still state-derived.

**Step 0 no longer emits nothing.** Since §3.14a a technical hit produces a `FOUL`
and a `FREE_THROW` — but **not from the rotation**, which has no `GameData`, team
ids, period or sequence. `advancePossession()` **returns the committer** (or null) and
`PossessionEngine.simulate()` emits both events. The roll and the committer draw stay
in the rotation; only the plumbing lives in the engine. See the substitution
paragraph below.

---

Each possession produces **one or more** `GameEvent` rows in this order:

1. **Turnover check** — rolled before the shot. If triggered:
   - A single weighted **cause draw** (§3.9, `TurnoverResolver.pickCause`) labels
     the turnover as one of **nine** causes — `STOLEN` (kept dominant),
     `SHOT_CLOCK_VIOLATION`, `OFFENSIVE_FOUL`, `BAD_PASS`, `TRAVELLING`,
     `LOST_BALL_OUT_OF_BOUNDS`, `3_SECONDS_VIOLATION`,
     `8_SECONDS_BACKCOURT_VIOLATION`, `OVER_AND_BACK`. The **gate is unchanged** —
     the draw runs only *after* a turnover is declared, so it re-partitions the
     label without moving the count (#027 A). Every cause charges the ball-handler;
     `STOLEN` also credits a stealer.
   - `TURNOVER` event → possession ends, ball goes to the other team.
2. **Foul check** — rolled on **every** shot type (§3.12, #030 A1). The old
   `DRIVE`/`POST`-only gate (`ShotType.isContactType()`) is **deleted**: the
   graduation now lives in the **rate**, not a gate, via a per-shot-type multiplier
   on `BASE_NO_BASKET_FOUL` (DRIVE 1.0 the anchor ≈ POST > PERIMETER > THREE). A
   closeout on a three-point shooter is a real foul. If triggered:
   - `FOUL` event (primary_player = fouling defender) → free throws follow.
   - `FREE_THROW` events (primary_player = shooter), each with its own make/miss
     outcome, tagged `*_SHOOTING` (§3.11 D). **The count comes from
     `ShotType.freeThrowsIfFouled()`: 3 for a fouled `THREE`, otherwise 2** (§3.12,
     #030 C — a rule of basketball, so it lives on the enum, not in `SimConfig`).
     ⚠ **§3.21 (#043 C): the possession does NOT simply end after free throws any more.**
     The **LAST** attempt of a trip is **live** — a missed one is rebounded through
     `MissedShotResolver` (at a defence-leaning base, `FREE_THROW_REBOUND_LEAN`) and an
     **offensive** board returns the ball for a second chance under the same cap. Applies
     at `SHOOTING`, `BONUS` and `AND_ONE` **only**: `FLAGRANT` and `TECHNICAL` are
     excluded by rule (#034 B, #032 G). ⚠ **Only the LAST attempt is live** — a missed
     first FT is a dead ball. In code this is a second layer, `awardLiveFreeThrows`,
     wrapping an **unchanged** `awardFreeThrows` (#043 H).
   - **This branch is only the "contact STOPPED the shot" case** — hence the
     constant's name (`BASE_NO_BASKET_FOUL`, renamed from `BASE_FOUL` in §3.12,
     #030 F — §3.12 left the value at 0.15; **§3.20 raised it to 0.1687 to land FGA**,
     #042 D6). A foul on a shot that still goes in is the
     **and-1**, rolled after the make in step 3 (§3.11), and it is **not** reachable
     from here — this branch returns.
3. **Shot** — if no turnover and no foul. The shooter is charged an FGA (+3PA if a
   THREE), then the outcome is a **three-way MAKE/MISS/BLOCK draw** (§3.7):
   - **Block check first (§3.7)** — `P(BLOCK)` is carved off the top: a
     defender-vs-finisher contest (`rimProtection` at the rim / `shotContest` on
     jumpers, vs the shooter's `finishing`), shot-type-scaled (rim ≫ three). If
     blocked: a `SHOT` / `BLOCKED_*` event (primary_player = shooter, the victim),
     the blocker credited a BLK via `recordBlock()` **and riding the event as the
     counterparty** (§3.18), a missed FGA on the shooter, no assist. A flat four-way
     `BlockResolver` then resolves the loose ball — a **defense recovery** (in-bounds or
     OOB) ends the possession; an **offense recovery** re-enters the second-chance loop at
     the shot selector, capped like an offensive rebound.
     ⚠ **§3.21 (#043 E): a recovery now EMITS a `REBOUND` event and credits a rebounder**
     on the two in-bounds outcomes — it no longer "skips the rebound step". The flat roll
     still picks the **side**; a skill-weighted draw then picks **which of that side's
     five**. The two OOB outcomes emit a `REBOUND / OUT_OF_BOUNDS_*` with no rebounder.
   - Otherwise `SHOT` event → made or missed. On a make, points are scored and the
     possession ends. On a made FG, an **assist** may be attributed (§3.4): a roll
     (scaled by the other on-floor offensive players' `passing` / team
     `teamOffense`) decides whether the make was assisted; if so, an assister is
     picked by a weighted `passing` draw over the other four offensive players
     (the shooter excluded) and stamped on the SHOT event's `assist_player_id`.
     Not every make is assisted.
   - **And-1 check (§3.11, #029; widened §3.12, #030)** — on **any** made shot, a
     second, independent foul roll runs **after** the assist and before the
     possession ends. (§3.11 rolled this only on a made DRIVE/POST; §3.12 deleted
     that gate — the graduation now lives in the rate, not a gate.) On a hit: the defender is charged a foul, a `FOUL` / `AND_ONE` event is
     emitted (`committing_team_id` = the defense), and **one** `FREE_THROW`
     (`*_AND_ONE`) follows for the shooter. The FG points/FGM/assist are **not**
     re-rolled or re-scored, and the possession is **never forked** — the make
     already ended it. An and-1 is always exactly 1 FT and never consults the bonus.
     On a **miss**, a rebound is resolved (§3.3):
4. **Rebounding foul** (§3.10, #028) — rolled after a missed `SHOT` but **before**
   the board draw below, and **short-circuiting it** on a hit (the whistle stopped
   play, so nobody rebounds). A small, independently-tunable slice carved off the
   top, scaled by `foulProne` / `foulDrawing` / `defensivePressure`. On a hit,
   a defense-leaning side draw picks the committer and emits `FOUL` /
   `REBOUNDING_FOUL_DEFENSE` or `REBOUNDING_FOUL_OFFENSE` with `committing_team_id`
   set; the possession then forks on who fouled, and the **penalty predicate**
   (derived from the `FOUL` log — see [game-events.md](game-events.md)) decides whether bonus
   `FREE_THROW`s follow. Otherwise the miss falls through to:
5. **Missed-shot outcome** (§3.3 + §3.8) — rolled only after a missed `SHOT`.
   `MissedShotResolver` (which wraps `ReboundResolver`) resolves the miss to
   **one of four outcomes in a single draw** (decisions.md #026), all carried on
   the `REBOUND` play type:
   - `REBOUND` / `DEFENSIVE` (primary_player = defensive rebounder) → possession
     ends, ball goes to the other team; **or**
   - `REBOUND` / `OFFENSIVE` (primary_player = offensive rebounder) → the shooting
     team retains the ball and runs a **second-chance possession** through the
     full flow above (turnover → foul → shot → miss-outcome); **or**
   - `REBOUND` / `OUT_OF_BOUNDS_DEFENSE` — the ball left the court, defense's ball
     → possession ends. **No rebounder credited** (#026 E); **or**
   - `REBOUND` / `OUT_OF_BOUNDS_OFFENSE` — the ball left the court, offense retains
     → second-chance possession. **No rebounder credited** (#026 E).

   The rebound-vs-rebound balance is a **skill-weighted** board contest
   (`offenseRebound` vs `defenseRebound`); the two OOB slices are carved off the
   top FIRST by a **flat, defense-leaning lean** that is skill-independent (not a
   second contest, not inheriting the board winner). Both **sail-out** (the shot
   flies OOB untouched) and **tipped-OOB** (a board contest whose ball deflects
   out, last-touch decides) resolve here and **share one OOB outcome each**
   (offense/defense) — the distinction is not recorded on the event (nothing
   consumes it; the last-touch team is implied by the offense/defense suffix).
   **OOB-offense is a third offense-retention path** alongside the offensive
   rebound and the §3.7 offense-recovered block: all three bump the offensive-
   rebound counter and count against the same `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`
   cap; after the cap a retained outcome is forced to its possession-ending
   sibling (`OFFENSIVE`→`DEFENSIVE`, `OUT_OF_BOUNDS_OFFENSE`→`OUT_OF_BOUNDS_DEFENSE`)
   so the possession terminates.

A single possession therefore emits one of these patterns (a missed shot is
always resolved — by a `REBOUND` event, an actual rebound or an OOB, **or** by a
`REBOUNDING_FOUL_*` that stopped play, §3.10):
- `TURNOVER`
- `TURNOVER` (`OFFENSIVE_FOUL`) → `FOUL` (`OFFENSIVE_FOUL`) — a **charge**: TWO events
  for ONE occurrence (§3.16, #039 G). The foul carries
  `committing_team_id` = the **offense**, so it moves the **defense** toward the bonus
- `FOUL` (`NON_SHOOTING_FOUL`) — **no free throws at all**; the non-shooting foul outside the
  penalty, and the point of §3.16. Possession ends (the ball does **not** come back)
- `FOUL` (`NON_SHOOTING_FOUL`) → `FREE_THROW` ×**2** (`*_BONUS`) — the same foul committed
  **in the penalty**; possession ends (§3.16)
- `FOUL` (`SHOOTING_FOUL`) → `FREE_THROW` ×**2** (both `*_SHOOTING`) — any shot type
  except a three (§3.12)
- `FOUL` (`SHOOTING_FOUL`) → `FREE_THROW` ×**3** (all `*_SHOOTING`) — a fouled
  `THREE`; the 3-FT trip (§3.12, #030 C)
- `SHOT` (made) — possession ends
- `SHOT` (made, **any** shot type) → `FOUL` (`AND_ONE`) → `FREE_THROW` (`*_AND_ONE`) —
  the and-1: the basket counts, **one** FT follows (even on a made three — 3 pts + 1
  FT, never 3 FTs), possession still ends (§3.11; widened to all types by §3.12)
- `SHOT` (missed) → `REBOUND` (`DEFENSIVE`) — possession ends
- `SHOT` (missed) → `REBOUND` (`OFFENSIVE`) → … second-chance possession …
- `SHOT` (missed) → `REBOUND` (`OUT_OF_BOUNDS_DEFENSE`) — possession ends (§3.8)
- `SHOT` (missed) → `REBOUND` (`OUT_OF_BOUNDS_OFFENSE`) → … second-chance … (§3.8)
- `SHOT` (`BLOCKED_*`) — defense recovers (in-bounds/OOB), possession ends
- `SHOT` (`BLOCKED_*`) → … second-chance possession … — offense recovers the block
- `SHOT` (missed) → `FOUL` (`REBOUNDING_FOUL_DEFENSE`) → … second-chance … — under
  the bonus, the offense retains (§3.10)
- `SHOT` (missed) → `FOUL` (`REBOUNDING_FOUL_DEFENSE`) → `FREE_THROW` ×2 (`*_BONUS`) — in
  the bonus, the offense shoots; possession ends (§3.10)
- `SHOT` (missed) → `FOUL` (`REBOUNDING_FOUL_OFFENSE`) — possession ends, defense's
  ball (§3.10)
- `SHOT` (missed) → `FOUL` (`REBOUNDING_FOUL_OFFENSE`) → `FREE_THROW` ×2 (`*_BONUS`) — in
  the bonus, the **defense** shoots; possession still ends (§3.10)

All events in a possession share the same `offense_team_id` / `defense_team_id`
and `period`. `sequence` increments globally (not per possession).

**Substitution + fatigue + foul-outs (§3.5)** run as a **between-possession** step
that changes *who* is on the floor without altering the possession flow's shape
(decisions.md #023) — step 0 of the calculation sequence above. Before each
possession the engine, **for BOTH teams** (both are on the floor, so both tire —
`PossessionEngine.simulate()` advances the home and away rotations before
`resolvePossession()`): drains the on-floor five's `currentEnergy` (drain scaled by `endurance`),
recovers the benched players' energy, **rolls §3.14a's technical foul**, **forces off
any player who is DISQUALIFIED** — fouled out (`getFouls() >= FOUL_OUT_LIMIT`),
ejected on technicals (`technicalFouls >= TECHNICAL_EJECTION_LIMIT`) **or, since
§3.14b, ejected on a flagrant-2** (`flagrantTwos >= FLAGRANT_EJECTION_LIMIT`, i.e. one
is enough), **all three** derived predicates over monotonic counters, none a stored
flag (#023 F, #032 F, #034 F) — then runs
**§3.13's soft foul-trouble sub**, then a fatigue substitution (pull the most-tired
starter below a `substitutionAggressiveness`-scaled threshold for the freshest
eligible bench player, drawing down the `rotationOrder` queue only as far as
`rotationDepth` allows; starters tolerate more fatigue and return first). The
on-floor five (`RotationState.onFloor()`) is **always exactly 5**: if the roster is
exhausted (everyone fouled out **or ejected**), a disqualified player stays on so the
floor never drops below 5. **All THREE disqualification causes run through ONE
filter** — `RotationState.isDisqualified(p)`, behind the single `eligible(...)` gate
every candidate pool passes through — so each new ejection cause extends the existing
**hard/forced** tier rather than adding a removal path (#031 H, held for three passes
running). **§3.14b is where that tier stopped being dead-but-correct code**: ejections
measure **0.027 per team-game** across both causes, roughly double §3.14a's 0.014 alone,
because a flagrant-2 ejects on the *first* one where a technical needs two. A fatigue **multiplier** over each on-floor player's
skills (`effectiveSkill = skill × fatigueFactor(energy)`) then bends shot/defense/
rebound contests — a modest thumb on the scale composed multiplicatively with the
§3.4 coach/chemistry modifiers.

**The step is reproducible from the seed but is NOT RNG-free** (§3.13, decisions.md
**#031**, which deliberately revises #023 C). It consumes **exactly one draw per
call** — the foul-trouble sit roll — taken **unconditionally at a fixed point**, so
the stream advances identically whether or not anyone is in foul trouble. #023 C's
"subs are a coaching decision, not chance" reasoning still fits the *fatigue* sub,
whose trigger is a measurable state; it fits foul trouble poorly, because two coaches
facing the same 4-foul situation genuinely make different calls. The step still
produces no `GameEvent` rows.

**The foul-trouble sub (§3.13)**, sequenced between the force-off and the fatigue
sub, is the engine's **first strategic substitution** and the only thing in it that
reacts to foul *trouble* rather than foul-*out*:
- **Probabilistic, not a threshold**: `base(foulCount) × coach × value × roster`.
  The base curve is zero below 3 fouls, rises to 5, and is zero at 6 (a foul-out is
  the hard rule's business). One curve scaled by `substitutionAggressiveness`
  expresses "an aggressive coach thinks about it at 3, an average one at 4, a passive
  one at 5" without three thresholds.
- **It protects the BEST players MORE** — the deliberate *inverse* of the fatigue
  rule, where `STARTER_SUB_THRESHOLD_BONUS` lets starters tolerate more fatigue. You
  ride your star when he's tired; you protect him when he's in foul trouble. Value is
  a **derived** defense-leaning skill composite (`PlayerGameState.valueComposite()`),
  combined with — not replacing — the `lineupRole`/`rotationOrder` roster signal.
- **It yields**: it draws only from the `rotationDepth` window (a rotation decision,
  not the emergency full-bench reach the foul-out force-off gets) and fires only if a
  genuinely eligible, meaningfully fresher replacement exists. If the bench can't
  cover it, the foul-troubled player keeps playing — a soft preference never weakens
  a hard invariant.
- **Sticky sit, earned return**: a player benched here becomes an *ordinary* bench
  player, with no "benched for fouls" status and **no competing foul-trouble roll to
  bring him back**. He returns only through the fatigue rule's freshness path, and
  the return timer is energy recovery itself. Only the *sit* is probabilistic; the
  *return* is earned. That asymmetry is what keeps a 4-foul player from flickering
  on and off across consecutive possessions at ~100 checks per team per game.
- **A fouled-out player still NEVER returns** — that bar is absolute and §3.13 does
  not touch it.

**Coach modifiers (§3.4)** bend this flow without changing its shape (decisions.md
#022, all effects via the avg-10 deviation multiplier
`base × (1 + COACH_SENSITIVITY·(attr−10)/10)`):
- **`pace`** scales the **possession count** — a faster coach runs more
  possessions per game (more events), a slower coach fewer.
- **`offensiveScheme`** leans the shot-type draw in `ShotSelector`. ⚠ **§3.17 (#040 D)
  changed the AXIS: it is now three vs. MID-RANGE, not jumper vs. interior.** `THREE` is
  scaled by the multiplier and `PERIMETER` by its **reciprocal**; `DRIVE`/`POST` are
  unscaled. A high-`offensiveScheme` coach shoots more threes **and fewer mid-range
  jumpers**. ⚠ **It no longer sets the LEAGUE's mix** — that is the four
  `sim.shot-share-*` tunables (#040 C) — only a given team's tilt on it.
- **`defensiveScheme`** scales the defense's turnover/foul pressure.
- Team chemistry skills also enter resolution: **`acumen`** is a small make-rate
  nudge in `ShotResolver`, and **`teamOffense`/`teamDefense`** a single
  possession-level efficiency multiplier. All are a modest thumb on the scale over
  the player-skill contest, not a replacement. `rotationDepth` /
  `substitutionAggressiveness` are **not** read in §3.4 — they belong to §3.5
  minutes/fatigue (Decision E).

### `play_type` values and their `outcome` vocabulary

> **→ The event vocabulary lives in [`game-events.md`](game-events.md).**

**[`docs/game-events.md`](game-events.md) is the single per-event reference**: every
`(play_type, outcome)` pair the engine emits, as a master table with **explicit
participant columns** (`primary` · `opponent` · `assist` · `committing_team`), plus the
three rules that let you derive most of it —

1. **the emission rule**: *second participant → a column, second accounting → an event,
   neither → nothing* — why a steal is one row and a charge is two;
2. **the counterparty invariant**: `opponent_player_id` is **always** on the opposite
   team, and why assists keep their own column;
3. **`outcome` is free text while `play_type` is a closed OpenAPI enum** — why new
   causes are free and new play types are expensive.

…and detail sections for the events with a trap (the steal, the charge's two events,
`BLOCKED_*`, the two-sided `REBOUNDING_FOUL_*`, the technical, and the free-throw
sources).

⚠ **Do not restate the vocabulary here.** A second per-event table will drift from that
one — the failure mode `CLAUDE.md` documents for `possession-flow.puml`. This file keeps
the **models**, the **possession flow / calculation sequence** and the **API surface**.

### Shot types → skill matchups (decisions.md #021, Decision C)

| Shot type | Offensive skill(s) | Defensive skill(s) | Points |
|---|---|---|---|
| Drive / finish | `drive`, `finishing` | `rimProtection` | 2 |
| Perimeter / mid-range | `perimeter` | `individualDefense`, `shotContest` | 2 |
| Post | `post` | `individualDefense` | 2 |
| Long range / three | `longRange` | `shotContest` | 3 |

---

## `BoxScore`

Per-player stat line for a single game (the Phase 4 stats model aggregates these
into season totals).

- one row per **`(game_id, player_id)`** — FK to `game` and `player`. **No
  `team_id` on the row**: the player's team is derivable (`game` home/away +
  `player_team`), so storing it here would be a third copy of "what team is this
  player on" (the duplicate-source trap of #013/#015). If "team in *this* game"
  ever needs to survive a mid-season trade, derive it from `player_team_hist` by
  date, or denormalize then with a real consumer — not now.
- **Every player who took the floor gets a row (§3.5), not just the 5 starters.**
  With substitutions live, bench players who entered the game accumulate stats and
  minutes and so get their own box-score row. A player who never checked in gets no
  row (nothing to reconcile).
- per-player counters: points, rebounds (off/def), assists, steals, **blocks**,
  turnovers, fouls, **technical fouls**, FGA/FGM, 3PA/3PM, FTA/FTM (cf. roadmap §4.1).
- **accumulated during simulation**, then reconciled against the persisted
  `GameEvent` log (events are the source of truth — decisions.md #020).
- **Every counter is now real** — the last placeholder, `blocks`, became real in
  §3.7 (below); `assists` became real in §3.4 (Decision B1), `minutes` in §3.5.

- **`blocks` is real as of §3.7 (decisions.md #025).** A block is a **field-goal
  outcome the way a steal is a turnover outcome** — there is deliberately **no
  `PlayType.BLOCK`**. A blocked shot is a `SHOT` event with a `BLOCKED_*` outcome
  naming the **shooter** (the victim) as `primaryPlayerId`, exactly as a steal is a
  `TURNOVER`/`STOLEN` event naming the ball-loser. The **blocker** is credited
  separately via `recordBlock()` (mirroring `recordSteal()`) and is **not** on the
  event; that accumulator maps to `BoxScore.blocks`. The shooter is charged a
  **missed FGA** (`+1 FGA`, `+1 3PA` if a blocked THREE, `0 FGM` — a block counts
  against FG%), and a blocked shot carries **no `assist_player_id`**. Reconciliation
  invariant: count of `SHOT` events with `outcome LIKE 'BLOCKED%'` **==** sum of
  `BoxScore.blocks` (same shape steals/assists/rebounds use). See the `play_type` /
  `outcome` vocabulary below for the `BLOCKED_*` strings.

- **`technical_fouls` is a SECOND, separate foul counter as of §3.14a (decisions.md
  #032 E, surfaced by #033).** `fouls` means **personal fouls only** — a technical
  does **not** count toward the six-foul disqualification, so the two are never
  summed and never merged. That split is what keeps `fouls` meaning what
  `isFouledOut()`, `foulTroubleLevel()` and §3.10's penalty derivation already assume
  it means; merging them would silently have moved two §3.13-calibrated numbers
  (foul-outs ~0.39 and the 4/5/6 distribution). **The column is a denormalized
  convenience, not the authority**: the fact was already queryable as `FOUL` events
  with `outcome = 'TECHNICAL_FOUL'`, and if the two ever disagree the events win
  (#020). Reconciliation invariant, and it is two-sided: count of `TECHNICAL_FOUL`
  events **==** sum of `BoxScore.technicalFouls`, **and** count of **non**-technical
  `FOUL` events **==** sum of `BoxScore.fouls`. Surfaced on the DB column, the entity
  and the OpenAPI `BoxScore` on a **parity** argument — it is the twelfth per-player
  accumulator in a set whose other eleven were already exposed (#033 B). Rows written
  before that changeset are `null`, not 0.
  **§3.16 stress-tested that invariant and it held** (#039 G): the **charge** now emits
  a `FOUL` event *and* charges `recordFoul()`, so both sides of the identity increment
  together — the existing test passes unchanged. ⚠ **The thing to know is that a charge
  produces TWO events for ONE occurrence** (a `TURNOVER` *and* a `FOUL`), so any
  reconciliation that counts events per possession, rather than per side of this
  identity, must tolerate that.

- **An EJECTION is not a counter, an event, or a column.** Two technicals in one game
  disqualifies a player, and that is a **derived predicate** (`technicalFouls >= 2`)
  in the #023 F mould — no stored flag, permanent because the counter only grows
  (#032 F). Nothing announces it in the event log; it is reconstructible by counting a
  player's `TECHNICAL_FOUL` events. Expect it to be **rare** (~0.014 per team-game,
  roughly one per team per season).
  **§3.14b added a SECOND ejection cause, and it is derived too** (`decisions.md`
  #034 F, shipped 2026-08 — ~~"§3.14b's flagrant-2 is the case that genuinely
  cannot be derived this way and will force the stored-state question"~~). A
  **flagrant-2 ejects immediately**, so there is no threshold to remember and
  `flagrantTwos >= 1` is a monotonic counter exactly like the other two. **No stored
  flag, no #023 F exception** — the prediction carried by #031 H and #032 F is
  **retired**, on the general finding that *every* disqualification in this model is
  absorbing and monotonic, which is the shape #023 F's derivation was built for.
  Consequence for a consumer: "was this player ejected, and why" is now a **two-cause**
  question (`TECHNICAL_FOUL` count vs. the limit, **or** any `FLAGRANT_FOUL_2`) — which
  is the strongest argument yet for the single `EJECTION` event #033's follow-up
  anticipated, if one is ever wanted. **Measured, ejections are no longer negligible:
  0.027 per team-game across both causes** (§3.14b's flagrant-2s contribute ~0.023 of
  it, roughly double the technical kind's 0.014), so the forced-substitution path is
  now genuinely exercised rather than dead-but-correct code.

- **`minutes` is real as of §3.5 (decisions.md #023, Decision A).** The engine has
  no game clock (#021), so minutes are a **derived possession-share projection**,
  not a clocked measurement: each on-floor player accumulates a possession counter,
  and at box-score-write time the game's total possessions map to
  `PERIODS × 12` (+5 per OT) minutes by ratio, attributing each player their share.
  Team minutes sum to `5 × game-minutes` by construction.

---

## API surface — §3.6 *(decisions.md #024)*

§3.6 exposes the persisted game engine over OpenAPI. It is **API + read-projection +
entity→model mapping only** — no new engine logic (the engine is done through §3.5).
Spec lives in `gametime-api/yml/gametime.yaml`; the hand-written delegate is
`api/V1ApiDelegateimpl.java`.

### Operations

| Verb | Path | Request | Response | Errors |
|------|------|---------|----------|--------|
| `POST` | `/v1/game/simulate` | `SimulateGameRequest` | `200 GameResult` | `404` unknown team · `422` same-team |
| `GET`  | `/v1/game/{gameId}` | — | `200 GameResult` | `404` unknown game |
| `GET`  | `/v1/game/{gameId}/play-by-play` | — | `200 [GameEvent]` | `404` unknown game |

### Models

- **`GameResult`** *(the shared "here's your game" object — same shape for simulate
  and get; #024 A)*: `game: Game`, `homeBoxScore: [BoxScore]`, `awayBoxScore:
  [BoxScore]`. The box scores are **split home/away server-side** — the mapper
  resolves each `box_score` row's team via `player_team` (the row has no `team_id`,
  #020). The **event log is not included** — it's the separate play-by-play endpoint,
  so the payload stays bounded (~30 stat lines, not hundreds of events).
- **`Game`** *(the header)*: `id`, `homeTeamId`, `awayTeamId`, `status`, `homeScore`,
  `awayScore`, `periods`, **`seed`**. No per-period line scores — a period line is
  derivable from the event log (#020), so it isn't stored. Only `FINAL` status is
  produced by the engine.
- **`GameEvent`** *(one per play-by-play row)*: `sequence`, `period`, `offenseTeamId`,
  `defenseTeamId`, `playType`, `outcome`, `primaryPlayerId`, **`assistPlayerId`**
  (the §3.4 column, #022 B — surfaced in the API here per #024 D). **No time field**
  (#024 E — see below).
- **`BoxScore`**: `playerId` + the per-player counters (see the `BoxScore` section
  above). No `teamId` (#020).
- **`SimulateGameRequest`**: `homeTeamId` (required), `awayTeamId` (required),
  **`seed`** (optional `int64`). **No `possessionsPerPeriod`** — pace is not exposed
  (#024 C); the endpoint always uses the configured default, and per-game tempo variety
  comes from the coach `pace` attribute (#022 A). *(Since §3.15 that default is a
  **profilable** value — `sim.default-possessions-per-period`, read from the active
  profile — so the deployment's era sets league pace while the request still cannot.
  #024 C's call is unchanged.)*

### Seed — optional in, and PERSISTED *(#024 B, revises #021 A)*

`POST /simulate` accepts an **optional** seed: omitted ⇒ a fresh random `long` (real
games vary, per #021 A); provided ⇒ a reproducible run. The seed is now **persisted**
(`game.seed BIGINT`, appended to the unreleased `release.1.0.4.game.sql`) and
**echoed back** on the `Game` header — so a game is reproducible from its own record.
This revises #021 A's original "seed not persisted" stance; the variation rule is
unchanged (random default), we simply record what was rolled.

### Play-by-play — a plain ordered list, no pagination *(#024 D)*

`GET /{gameId}/play-by-play` returns the events as a **flat `[GameEvent]` in
`sequence` order** (reusing `GameEventRepo.findByGameIdOrderBySequenceAsc`). Event
volume is modest (~200–350 events/game), and a play-by-play UI wants the whole game;
pagination is deferred (the orphaned `pageNumber`/`pageSize` params, #019, stay
orphaned — a `period` filter is the likelier first future need). This supersedes the
"bounded/paginated at the read endpoint" language in the earlier sections — at the
current scale, unpaginated is correct.

### Event time — no column, derived on read *(#024 E, closes #021 B)*

§3.6 ships **no per-event time field and no time column**. `period` + `sequence`
suffice to render an ordered, period-grouped play-by-play; any display clock is
**computed on read** from `period` + `sequence` + pace. #021 B left the single-vs-range
column shape "for §3.6 to decide" — §3.6 resolves it as *derived, not stored*. The
revisit trigger is the **Phase 7 game view**: if that UI shows a per-event clock, it
decides the shape and adds a column only if computed-on-read proves insufficient.
Consequence: **`game.seed` is the only §3.6 schema change.**

### Errors *(#024 F)*

404 for an unknown `gameId` (both gets) or an unknown team on simulate (the engine
already throws `ResourceNotFoundException`, #021 E). **422 Unprocessable Entity** for
a same-team request (`homeTeamId == awayTeamId`) — a well-formed request that violates
a business rule, distinct from 400 "malformed"; a **new `ResourceUnprocessableException`**
+ `ApiExceptionHandler` mapping, the first 422 in the API. Error bodies stay empty,
matching the existing handler convention.
