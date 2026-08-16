# Game Domain *(model shipped §3.1; engine fills it through §3.14b)*

The Game domain is the **data a simulated game produces**: the matchup and its
result (`Game`), the event log that records how it unfolded (`GameEvent`), and
the per-player stat line for that game (`BoxScore`). These three are one cohesive
shape — a `Game` *has* `GameEvent`s and *produces* a `BoxScore` — so they live in
one doc, the way [roster.md](roster.md) holds player↔team + lineups + transactions
together.

The **model** shipped in §3.1; the **possession engine** that fills it is built
through §3.14b and this doc now documents both:
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

**Phase 3's possession model is now feature-complete**: §3.15 (`SimConfig` profiles)
and §3.16 (recalibration) add **no new mechanic and no new possession branch**, so
`possession-flow.puml` is structurally final.

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
| 1 | **`ShotSelector`** | picks the shooter, then the shot type | — |
| 2 | **`TurnoverResolver`** | turnover? then a 9-way cause draw | possession **ends** |
| 3 | **`FoulResolver.isFoul`** | foul that **stops** the shot (no basket), then **§3.14b: was it flagrant?** | FTs, possession **ends** — *unless flagrant: 2 FTs and the offense **RETAINS*** |
| 4 | **`BlockResolver`** (via `ShotResolver`) | block carved off the top | loose-ball recovery |
| 5 | **`ShotResolver.isMade`** | make / miss | — |
| 6 | **`FoulResolver.isAndOne`** — *on a make only* | foul the shot **survived**, then **§3.14b: was it flagrant?** | +1 FT, possession ends — *unless flagrant: basket **+** 2 FTs **+** the offense **RETAINS*** |
| 7 | **`MissedShotResolver`** — *on a miss only* | wraps `ReboundResolver`; the §3.10 **rebounding foul** is carved off first (then **§3.14b: was it flagrant?**), then a single four-way board/OOB draw | ends, or a second-chance possession |

† **Steps 2–7 are the `resolvePossession()` path**; step 0 runs in `PossessionEngine`'s
loop *before* it, for **both** rotations (both teams are on the floor, so both tire).
Step 1 is easy to overlook but is load-bearing: the shot type it picks is what step 3
multiplies by (§3.12's per-shot-type foul multiplier), so it must precede the foul roll.

**Four things this ordering makes clear that the event list below does not:**
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
- **"No basket" ≠ "non-shooting".** Step 3 *is* a shooting foul (`SHOOTING_FOUL`) — it
  is named for stopping the shot, not for being non-shooting. The engine's genuinely
  non-shooting fouls are step 7's rebounding fouls and step 2's `OFFENSIVE_FOUL`
  turnover cause. (This is exactly why §3.12 renamed the constant to
  `BASE_NO_BASKET_FOUL`, #030 F.)

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
     Possession ends after free throws.
   - **This branch is only the "contact STOPPED the shot" case** — hence the
     constant's name (`BASE_NO_BASKET_FOUL`, renamed from `BASE_FOUL` in §3.12,
     #030 F; value unchanged at 0.15). A foul on a shot that still goes in is the
     **and-1**, rolled after the make in step 3 (§3.11), and it is **not** reachable
     from here — this branch returns.
3. **Shot** — if no turnover and no foul. The shooter is charged an FGA (+3PA if a
   THREE), then the outcome is a **three-way MAKE/MISS/BLOCK draw** (§3.7):
   - **Block check first (§3.7)** — `P(BLOCK)` is carved off the top: a
     defender-vs-finisher contest (`rimProtection` at the rim / `shotContest` on
     jumpers, vs the shooter's `finishing`), shot-type-scaled (rim ≫ three). If
     blocked: a `SHOT` / `BLOCKED_*` event (primary_player = shooter, the victim),
     the blocker credited a BLK via `recordBlock()` (not on the event), a missed
     FGA on the shooter, no assist. A flat four-way `BlockResolver` then resolves
     the loose ball — a **defense recovery** (in-bounds or OOB) ends the
     possession; an **offense recovery** re-enters the second-chance loop at the
     shot selector (skipping the rebound step), capped like an offensive rebound.
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
   (derived from the `FOUL` log — see the vocabulary section) decides whether bonus
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
- **`offensiveScheme`** leans the shot-type draw (perimeter/three vs. inside/post)
  in `ShotSelector`.
- **`defensiveScheme`** scales the defense's turnover/foul pressure.
- Team chemistry skills also enter resolution: **`acumen`** is a small make-rate
  nudge in `ShotResolver`, and **`teamOffense`/`teamDefense`** a single
  possession-level efficiency multiplier. All are a modest thumb on the scale over
  the player-skill contest, not a replacement. `rotationDepth` /
  `substitutionAggressiveness` are **not** read in §3.4 — they belong to §3.5
  minutes/fatigue (Decision E).

### `play_type` values and their `outcome` vocabulary

| `play_type` | `outcome` | Meaning |
|---|---|---|
| `SHOT` | `MADE_2PT_DRIVE` | Made 2-point field goal (drive/finish at rim) |
| `SHOT` | `MADE_2PT_PERIMETER` | Made 2-point field goal (mid-range / perimeter) |
| `SHOT` | `MADE_2PT_POST` | Made 2-point field goal (post move) |
| `SHOT` | `MADE_3PT` | Made 3-point field goal (long range) |
| `SHOT` | `MISSED_2PT_DRIVE` | Missed 2-point field goal (drive/finish at rim) |
| `SHOT` | `MISSED_2PT_PERIMETER` | Missed 2-point field goal (mid-range / perimeter) |
| `SHOT` | `MISSED_2PT_POST` | Missed 2-point field goal (post move) |
| `SHOT` | `MISSED_3PT` | Missed 3-point field goal (long range) |
| `SHOT` | `BLOCKED_2PT_DRIVE` | Blocked 2-point drive; `primary_player` = shooter (victim), blocker credited a BLK separately (§3.7) |
| `SHOT` | `BLOCKED_2PT_PERIMETER` | Blocked 2-point perimeter shot; shooter on the event, blocker credited separately |
| `SHOT` | `BLOCKED_2PT_POST` | Blocked 2-point post shot; shooter on the event, blocker credited separately |
| `SHOT` | `BLOCKED_3PT` | Blocked 3-point attempt (rare — closeout swat); shooter on the event, blocker credited separately |
| `TURNOVER` | `STOLEN` | Live-ball steal; ball-handler on the event, defender credited a steal separately (§3.9 — kept dominant, ~56% of turnovers) |
| `TURNOVER` | `SHOT_CLOCK_VIOLATION` | Failed to get a shot off in time; charged to the ball-handler (§3.9) |
| `TURNOVER` | `OFFENSIVE_FOUL` | Charge / illegal screen charged as a turnover (§3.9) |
| `TURNOVER` | `BAD_PASS` | Pass thrown away (unforced handling error) (§3.9) |
| `TURNOVER` | `TRAVELLING` | Travelling violation (§3.9) |
| `TURNOVER` | `LOST_BALL_OUT_OF_BOUNDS` | Lost the handle / stripped, ball out of bounds off the offense (live ball) — **distinct** from §3.8's `OUT_OF_BOUNDS_*` on `REBOUND` (§3.9) |
| `TURNOVER` | `3_SECONDS_VIOLATION` | Offensive three-seconds-in-the-lane violation (§3.9) |
| `TURNOVER` | `8_SECONDS_BACKCOURT_VIOLATION` | Failed to advance the ball past half-court in time (§3.9) |
| `TURNOVER` | `OVER_AND_BACK` | Ball returned to the backcourt after crossing half (§3.9 — a sliver, ~2%) |
| `FOUL` | `SHOOTING_FOUL` | Defensive foul that **stopped** a shot of **any** type (§3.12); free throws follow — **3 if it stopped a `THREE`**, else 2. `committing_team_id` = the defense (§3.10) |
| `FOUL` | `REBOUNDING_FOUL_DEFENSE` | Defensive box-out push during the rebound phase — the **offense** is fouled, so it retains for a second chance, or shoots **bonus** FTs if the defense is in the penalty. `committing_team_id` = the defense (§3.10) |
| `FOUL` | `REBOUNDING_FOUL_OFFENSE` | Offensive over-the-back during the rebound phase — the **defense** is fouled and the **possession ends** for the offense (defense's ball, or defense's **bonus** FTs if the offense is in the penalty). `committing_team_id` = the **offense** (§3.10) |
| `FOUL` | `AND_ONE` | Defensive foul on **any** made shot (§3.12) — the basket **counts** and **one** free throw follows, a made three included. One-sided (always the defender), so `committing_team_id` = the defense (§3.11) |
| `FOUL` | `TECHNICAL_FOUL` | A **behavioral** foul with no contest behind it (§3.14a) — rolled **between possessions** in `RotationState`, not on the possession path. `primary_player` = the committer, drawn `foulProne`-weighted from the **on-floor five**; `committing_team_id` = his team. **One** free throw to the other team and **the possession is UNCHANGED** — no fork, no switch, even when the offense commits it. Charged to a **separate `technicalFouls` counter**: it does **not** feed the 6-foul limit and is **excluded from the period bonus tally** (#032 D/E) |
| `FOUL` | `FLAGRANT_FOUL_1` / `FLAGRANT_FOUL_2` | An **excessive-contact** foul (§3.14b, `decisions.md` #034), rolled as a severity question **on top of** a foul that already happened, at **all three** foul sites (stopped shot · and-1 · rebounding foul) — so no existing foul rate moves (#034 A). **Always exactly 2 free throws, which REPLACE the underlying foul's award rather than adding to it** (#034 C — a flagrant stopped `THREE` is **2** FTs, not 3, and not 5). **Unlike a technical it IS a personal foul**: `recordFoul()`, the 6-foul limit, the foul-trouble curve **and** the period bonus tally, all normally (#034 I). **The possession forks on WHO committed, not on which site**: a **defensive** flagrant awards the FTs **and returns the ball** (the first FT path in the model that does not end the possession — #030 B's invariant, broken); an **offensive** one (only possible at the rebounding site) sends the **defense** to the line and flips the possession. `_2` is a flat **15%** of flagrants and **ejects the committer immediately** (#034 E/F) |
| `FREE_THROW` | `MADE_SHOOTING` / `MISSED_SHOOTING` | Free throw from a shooting foul that **stopped** the shot — **2 per trip, or 3 if the stopped shot was a `THREE`** (§3.11 D, §3.12 C) |
| `FREE_THROW` | `MADE_BONUS` / `MISSED_BONUS` | Free throw from a **bonus (penalty)** trip after a rebounding foul (2 per trip) (§3.11 D) |
| `FREE_THROW` | `MADE_AND_ONE` / `MISSED_AND_ONE` | The single free throw riding a made basket (§3.11 D) |
| `FREE_THROW` | `MADE_TECHNICAL` / `MISSED_TECHNICAL` | The single free throw from a technical (§3.14a). **The only FT source where nobody was fouled**, so the shooter is a **deterministic highest-`freeThrows` pick from the on-floor five** — *not* the `foulDrawing`-weighted draw the bonus uses (#032 G). Consequence to expect: the same player shoots essentially all of his team's technical FTs all game |
| `FREE_THROW` | `MADE_FLAGRANT` / `MISSED_FLAGRANT` | The **two** free throws from a flagrant (§3.14b, #034), at any of the three sites. Shot by **the player who was fouled** — *not* §3.14a's best-shooter pick, whose premise (nobody was fouled) does not hold here (#034 D). A distinct source rather than reusing `SHOOTING` because the harness reads FT source off this suffix (#029 D), so reuse would inflate a real source's share on the very line §3.14b is judged by — and it would be factually wrong at the rebounding site, which is not a shooting foul at all |
| `REBOUND` | `OFFENSIVE` | Offensive rebound; ball stays with the shooting team for a second-chance possession |
| `REBOUND` | `DEFENSIVE` | Defensive rebound; possession ends, ball goes to the other team |
| `REBOUND` | `OUT_OF_BOUNDS_OFFENSE` | Missed shot left the court, offense retains → second chance; **no rebounder** (`primary_player` null) (§3.8) |
| `REBOUND` | `OUT_OF_BOUNDS_DEFENSE` | Missed shot left the court, defense's ball → possession ends; **no rebounder** (`primary_player` null) (§3.8) |

After a missed `SHOT`, §3.3 + §3.8 roll a **single four-way missed-shot outcome**
(`MissedShotResolver`, decisions.md #026): a real rebound or an out-of-bounds ball.
On a rebound the `REBOUND` event's `primary_player_id` is the rebounder — an
`OFFENSIVE` rebound keeps the ball with the shooting team (a second-chance
possession runs through the full flow again); a `DEFENSIVE` rebound ends the
possession. On an OOB outcome the ball left the court and **no rebounder is
credited** (`primary_player_id` is null) — `OUT_OF_BOUNDS_OFFENSE` retains for a
second chance, `OUT_OF_BOUNDS_DEFENSE` ends the possession. OOB events reuse the
`REBOUND` play type but are **excluded from the rebound reconciliation** (which
exact-matches `OFFENSIVE`/`DEFENSIVE`), so they never count as a box-score
rebound (#026 E). See the possession-flow section below.

**Rebounding fouls + the team-foul / bonus (penalty) model (§3.10, decisions.md
#028).** Before that four-way board draw runs, §3.10 rolls a **rebounding-foul
chance carved off the top** (the §3.7 block-carve shape): on a hit the whistle
stopped play, so the board contest **never runs** and no rebound is credited. The
foul is **two-sided** — a defensive box-out push or an offensive over-the-back,
defense-leaning — which is why the committing team is stored explicitly rather than
inferred from the possession's orientation.

**Penalty status is DERIVED from this event log, never stored** (#028 A1). "Team T
is in the bonus in period P" is computed on demand as

> `count(FOUL events where committing_team_id = T and period = P` <br>
> `        and outcome <> 'TECHNICAL_FOUL') >= BONUS_FOULS_PER_PERIOD`

with **no `teamFouls` column, field, or reset logic** — the same derived-predicate
discipline #023 F used for foul-*outs*, carried to the team level, because the fact
already lives in the events (#020) and a stored counter could only ever disagree
with them. The count is **emit-then-count**: the current `FOUL` is written to the log
*first*, so the **Nth foul itself** (the one that reaches the threshold) sends the
fouled team to the line.

**⚠ §3.14a (#032 E) gave this its FIRST outcome-aware exclusion, and #028 A1's
"one unified derivation over all `FOUL` events" no longer holds literally.** A
`TECHNICAL_FOUL` does **not** put a team in the penalty, so it is filtered out above
(`GameData.countsTowardBonus`). Every other foul kind still counts, and the exclusion
is written explicitly rather than left incidental — because the consequence is
permanent: **any future foul type must now consciously decide whether it counts.**
§3.14b's flagrant was the immediate next case, and it **does** count — **so §3.14b
added nothing here, and #034 I required that non-change be pinned by a test rather than
left to inspection**, since "we changed nothing" and "we forgot" are otherwise
indistinguishable. That test exists (`flagrantFoulsCountTowardTheBonusTallyUnlikeTechnicals`),
and the penalty rate duly stayed flat (51.9% of team-periods, against §3.14a's 51.2%).

**⚠ The exclusion lives in TWO places, and they must agree.** `GameData.isInBonus` is
the engine's derivation; `CalibrationHarness` computes its **own** per-team-period
tally over the same events for the bonus-rate line. A foul type excluded from one and
not the other makes the instrument silently disagree with the engine — an instrument
wrong in the direction of the change it is measuring.

The possession then **forks on who fouled**:

- **Defense committed** → the offense is fouled → **under the bonus** it retains for
  a second chance (respecting `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`); **in the
  bonus** it shoots bonus FTs and the possession ends.
- **Offense committed** → the defense is fouled → the **possession always ends** for
  the offense (an offensive foul is a turnover-like loss of the ball); in the bonus
  the defense shoots its bonus FTs first.

Bonus free throws reuse the shooting foul's award block verbatim, so FT and points
reconciliation is automatic. They carry the `MADE_BONUS`/`MISSED_BONUS` outcome —
§3.10 originally emitted a bare `MADE`/`MISSED` and accepted that a bonus FT was
indistinguishable from a shooting-foul FT except by the `FOUL` preceding it; **§3.11
retired that ambiguity** once a third FT source (the and-1) arrived (see
*Free-throw sources* below). A rebounding
foul increments the committing player's `fouls` exactly like any foul, feeding
foul-outs (#023 F). **No new box-score counter**, and `committing_team_id` is a raw
event fact rather than derived state, so §3.10 adds **no new reconciliation
obligation** of its own.

**And-1 — a foul rolled ALONGSIDE a made basket (§3.11, decisions.md #029).** Before
§3.11 a foul and a made basket were mutually exclusive: the pre-shot foul branch
returned before the make/miss roll, so contact always *stopped* the shot. An **and-1**
is the missing case — the defender fouls, the shot goes in anyway, and the basket
counts **plus one** free throw.

It is modeled as a **second, post-make foul roll** carved **beside the assist roll**,
inside the `if (made)` block (#029 A1/A3). The pre-shot foul branch and `BASE_FOUL`
are left **untouched** — that branch keeps meaning exactly "P(contact stopped the
shot)", and the and-1 is an independent slice with its own rate, the same
carve-off-the-top shape §3.7 used for blocks and §3.10 for rebounding fouls. The
assist and the and-1 coexist and do not interact: the assist is already stamped on
the `SHOT` event before the and-1 rolls, so a made basket can be both assisted **and**
an and-1.

Two properties distinguish it from every other foul:

- **It never forks the possession.** A made basket already ended the offense's
  possession — the FT is simply tacked on before the ball changes hands. There is no
  retain/end branch at all.
- **It is always exactly ONE free throw**, by rule — it **never consults the bonus**,
  in the penalty or not. (The and-1 foul still *counts toward* the committing team's
  period tally like any other foul; it just doesn't read it.)

§3.11 was deliberately scoped to made **DRIVE/POST** (`isContactType`), because the
whole foul model gated there. **§3.12 (#030) removed that fence** — see
"All-shot-type contact fouls" below.

**Free-throw sources (§3.11 D).** Every `FREE_THROW` event is **self-describing**: its
outcome carries both the result and the **source** that sent the shooter to the line
— `MADE_SHOOTING`, `MISSED_BONUS`, `MADE_AND_ONE`, and so on. §3.10 accepted a
backward join ("look at the preceding `FOUL`") when there were two sources; at
**three** that became fragile, so the source moved onto the event itself — the
derive-nothing-at-read-time discipline (#020), with a real consumer (per-source FT%
in Phase 4). The `MADE`/`MISSED` prefix **leads** so made-vs-missed stays readable by
a prefix check, and the source strings cannot collide with the `FOUL` outcomes
(`SHOOTING_FOUL` / `REBOUNDING_FOUL_*` / `AND_ONE`), which live on a different
`play_type`. The FT **count** is a separate per-situation value (and-1 = 1;
shooting foul and bonus = 2) — count and source are independent, which is the seam
§3.12 reuses to award 3 on a fouled three. **No schema change:** both are the
existing free-text `outcome` (#020).

**All-shot-type contact fouls (§3.12, decisions.md #030).** Until §3.12 the *entire*
foul model gated on a binary `ShotType.isContactType()` (`DRIVE || POST`): a
perimeter or three-point attempt could not draw a shooting foul, and could not draw
an and-1, **at any rate**. §3.12 **deleted** that predicate and replaced it with a
**graduated per-shot-type foul multiplier** in `SimConfig`, so every shot type can be
fouled — post/drive frequent, perimeter uncommon, three rare (a closeout on a
three-point shooter is a real foul). The rate now carries the information the gate
used to carry: one mechanism instead of a gate plus a rate.

**A shooting foul therefore applies to ALL four shot types**, and one shared
multiplier table drives **both** foul rolls — the pre-shot "the contact stopped the
shot" roll and the post-make and-1 roll. A shot type's propensity to draw contact is
a property of *the shot*, not of which roll is asking. An and-1 on a three being
rarer than a foul on a three needs no extra modeling: the two rolls are independent,
so the shot must *also* go in, and a three both makes less often and fouls less
often.

**A fouled `THREE` awards 3 free throws** — the latent bug §3.12 activated and fixed.
The pre-shot branch used to pass a flat count of 2; it never misfired only because a
`THREE` could not be fouled at all, so widening contact made the path reachable. The
count is a **rule** on the enum (`ShotType.freeThrowsIfFouled()` — 3 for `THREE`, 2
for the rest), deliberately *not* a `SimConfig` value: `SimConfig` is the tuning
surface, and housing a rule there invites someone to tune it. It rides #029 D's
already-parameterized per-situation FT count, so this was a call-site change, not new
plumbing.

> **§3.15 generalizes this argument into a structural rule** (`decisions.md` #035 C —
> design resolved, not yet built). Profiles make `SimConfig`'s tuning surface literal:
> **57 of the 83 constants become instance state loadable from a profile, and 26 stay
> `public static final`** — 9 rules, 16 model-machinery entries (the sensitivities plus
> the two **scale definitions**, `SCALE_AVG` and `MAX_ENERGY`), and the measured
> `PERSONAL_FOULS_PER_TEAM_GAME`. The invariant reads straight off the source: **`static
> final` means it is a rule or the shape of the model, not a knob.** So this section's
> instinct — keep a rule off the tuning surface — becomes **structural**: a non-profilable
> constant has no property key at all, so a profile simply cannot reach it. *(A stray
> `sim.free-throws-per-foul` in a profile is ignored by Spring rather than rejected —
> §3.15 takes no unknown-key check, #035 D — and shows up as a no-op in the harness's
> effective-config dump.)*
> ⚠ **Note the FT counts landed on the static side**, so `FREE_THROWS_PER_FOUL`,
> `AND_ONE_FREE_THROWS`, `TECHNICAL_FREE_THROWS` and `FLAGRANT_FREE_THROWS` are not
> profilable — consistent with housing the fouled-three count on the enum. Some things
> that *read* like rules are profilable, though, because they genuinely vary by era:
> `BONUS_FOULS_PER_PERIOD` and `FOUL_OUT_LIMIT` are both on the profilable side.

**An and-1 stays exactly ONE free throw for every shot type, a made three included** —
a made 3 plus a foul is 3 points and 1 FT, not 3. Only the *stopped*-shot count
graduates. (These two counts sitting side by side is the easy wrong turn in this
area; it is guarded by an explicit test.)

**The event vocabulary is UNCHANGED, which is worth stating explicitly.** §3.12 added
**no** new `PlayType`, **no** new `FreeThrowSource`, **no** new `outcome` string, and
**no** schema or OpenAPI change. A fouled three emits the existing `SHOOTING_FOUL`
`FOUL` followed by **three** existing `MADE_SHOOTING`/`MISSED_SHOOTING` `FREE_THROW`
events — three-FT-ness is carried by *there being three of them*, not by a tag. The
`FreeThrowSource` records **why** the shooter is at the line (it is a shooting foul),
which is unchanged; nothing today asks to distinguish a 3-FT trip, so nothing was
fabricated ahead of a consumer (#014/#017/#020). A consumer that ever wants "3-FT
trips" counts `SHOOTING` FTs per preceding `FOUL`.

**The two foul rolls remain mutually exclusive** — by control flow, not by a check.
The pre-shot branch *returns*, so a shot that drew a stopped-shot foul never reaches
the make/miss roll and therefore never reaches the and-1 roll. One shot attempt emits
**at most one** of `SHOOTING_FOUL` / `AND_ONE`, never both, and sharing one multiplier
table does not apply it twice to a single shot.

**Steal / block symmetry (§3.7, decisions.md #025).** A **block is a field-goal
outcome exactly as a steal is a turnover outcome** — a defensive event modeled as
an outcome-flavor on the offensive action it interrupts, plus a separate
defender-credit accumulator. The two are deliberately parallel:

| | Steal (§3.2) | Block (§3.7) |
|---|---|---|
| Event | `TURNOVER` / `STOLEN` | `SHOT` / `BLOCKED_*` |
| `primary_player_id` | the ball-loser (victim) | the shooter (victim) |
| Separate credit | `recordSteal()` → `BoxScore.steals` | `recordBlock()` → `BoxScore.blocks` |
| New `PlayType`? | no (a kind of turnover) | no (a kind of missed shot) |
| Reconciliation | `count(STOLEN) == Σ steals` | `count(outcome LIKE 'BLOCKED%') == Σ blocks` |

A blocked shot is **carved off the top** of the shot outcome (a three-way
MAKE/MISS/BLOCK draw): `P(BLOCK)` is a defender-vs-finisher contest
(`rimProtection` at the rim / `shotContest` on jumpers, vs the shooter's
`finishing`), then the make/miss contest runs on the remainder. It charges the
shooter a **missed FGA** (no FGM; `+3PA` on a blocked THREE) and carries **no
assist**. A separate flat four-way `BlockResolver` then decides the loose ball
(offense/defense × in-bounds/OOB); an offense-recovered block re-enters the
second-chance loop at `ShotSelector`, skipping `ReboundResolver`. See the
possession-flow section below and `docs/possession-flow.puml`.

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
  comes from the coach `pace` attribute (#022 A). *(§3.15 makes that default a
  **profilable** value — `sim.default-possessions-per-period`, read from the active
  profile rather than `SimConfig.DEFAULT_POSSESSIONS_PER_PERIOD` — so the deployment's
  era sets league pace while the request still cannot. #024 C's call is unchanged.)*

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
