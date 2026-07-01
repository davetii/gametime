# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**§3.x Deferred sim-fidelity details**.

Current focus: **Phase 3.5 — Minutes, Fatigue & Substitution** (roadmap.md §3.5).
§3.1 Game Model, §3.2 Possession Engine, §3.3 Rebounding, and §3.4 Team Chemistry
& Coaching Effects are shipped. §3.5 is the first phase where **who is on the
floor changes during a game**: the bench (`rotationOrder`) enters the rotation,
players accumulate fatigue and degrade, and the coach's `rotationDepth` /
`substitutionAggressiveness` (the two attributes §3.4 deliberately did **not**
read — decisions.md #022 E) drive substitutions.

---

## Phase 3.5 — Minutes, Fatigue & Substitution

**Goal** (roadmap §3.5): produce realistic playing-time distributions and in-game
fatigue, layered onto the existing possession flow. This is *gameplay* state
produced by games being played, not roster state — its inputs are the bench
`rotationOrder` depth chart (decisions.md #014, latent until now) + the two §3.5
coach attributes + the `endurance`/`energy` player attributes.

Roadmap §3.5 deliverables:
- **Minutes allocation**: bench `rotationOrder` → distribution of playing time
- **Per-player energy tracking** within a game (`endurance` ↔ minutes/possessions
  played)
- **Skill degradation** as energy drops
- **Automatic substitution triggers** based on fatigue thresholds
- **Coach rotation style** determines when subs happen (`rotationDepth` /
  `substitutionAggressiveness`)

> ✅ **Decisions resolved.** All six §3.5 design decisions (A–F) are settled with
> the user (see the decisions section below). The §3.5 session can proceed straight
> to implementation: record A–F in decisions.md (**#023**, mirroring #021/#022),
> then work the task sequence in order. Notable outcomes: **A1** minutes are a
> derived possession-share projection (engine stays clock-free); **F** keeps
> fatigue within-game (no cross-game stamina — **schema-free for energy**) **but
> pulls foul-outs IN** as a second substitution cause — so §3.5 *does* add a
> foul-out rule (a SimConfig constant + forced-sub path), though still **no
> Liquibase migration** (`box_score.minutes` already exists).

---

## Verified facts about the current code (confirmed this planning pass)

A cold session can trust these without re-deriving them (re-verify only if the
code has moved — paths are under `gametime-app/src/.../sim/` unless noted):

- **The engine plays a FIXED five all game.** `GameSimulator.buildStarters(team)`
  filters `lineupRole == STARTER` and builds exactly 5 `PlayerGameState`s; that
  list becomes `TeamContext.players()` and **never changes** for the whole game.
  `PossessionEngine.simulate()` / `resolvePossession()` pick the shooter / defender
  / rebounder from that same five every possession. **Making the on-floor five
  dynamic (substitution) is §3.5's central structural change.**
- **The bench is already hydrated but dropped.** `Team.getPlayers()` returns
  `List<RosterEntry>` with **every** roster player's `lineupRole` **and**
  `rotationOrder` (the #014 bench queue) — `getRotationOrder()` exists on
  `RosterEntry` but is **used nowhere in `sim/`**. §3.5 is `rotationOrder`'s first
  consumer (the "correct-but-latent plumbing" #014 laid ahead of its use). So the
  data is already in hand; `buildStarters` just throws the non-starters away.
- **There is NO game clock — the engine is an abstract possession count.** A game
  is `PERIODS (4)` periods × `pacedPossessions` possessions each (pace-scaled in
  §3.4), with OT periods of `OT_POSSESSIONS_PER_PERIOD (5)`. Events carry
  `period` + a global `sequence`, **no time column** (decisions.md #020/#021;
  per-event time is §3.6, deferred). **Consequence: "minutes" cannot be read off a
  clock — they must be derived from possession share (or a possession→minutes
  mapping). This is the crux of Decision A.**
- **`endurance` and `energy` are raw attributes, NOT extracted into the engine.**
  Both live on `PlayerEntity` / the `Player` model (`getEndurance()`,
  `getEnergy()`) and feed several skill calculators, but `PlayerGameState` extracts
  only the 19 *skills* — not these two attributes. §3.5 extracts what it needs the
  same way §3.3/§3.4 added rebound/chemistry skills (see commit `c509f87` for the
  `PlayerGameState` + `TestPlayerFactory`-overload pattern).
- **A per-player foul counter already exists.** `PlayerGameState.fouls`
  (`recordFoul()`, `getFouls()`) is incremented on the defender when a shooting foul
  fires (`PossessionEngine` line ~112) and persists to `box_score.fouls`. §3.5's
  foul-out is a **derived predicate** over this counter (`getFouls() >=
  FOUL_OUT_LIMIT`) — no new state (Decision F). The counter lives for the whole
  game, so the disqualification is permanent for free.
- **`BoxScore.minutes` already exists as a column — NO schema change needed.**
  `box_score.minutes SMALLINT` is in the (still-unreleased) `release.1.0.4.game.sql`,
  and `GameSimulator` writes `bs.setMinutes(0)` today. §3.5 makes it real (Decision
  A) — but unlike §3.4's `assist_player_id`, **§3.5 needs no Liquibase migration**.
  Decision F confirmed fatigue stays within-game (no persisted energy column), so
  **§3.5 is schema-free** — foul counts already persist via `box_score.fouls`.
- **`CoachModifiers` reads scheme/pace ONLY.** It exposes `paceMultiplier()` /
  `shotMixLean()` / `defensivePressure()` from `pace`/`offensiveScheme`/
  `defensiveScheme`. `rotationDepth` / `substitutionAggressiveness` are **unread**
  — §3.5 extends `CoachModifiers` (or adds a sibling) to expose them.
- **`TeamContext` is the seam built for exactly this.** It bundles `players` +
  `CoachModifiers` and is threaded through `PossessionEngine` — §3.4 introduced it
  "so future coach effects don't re-churn the signature." A substitution/fatigue
  model extends what it carries (or what it can mutate) rather than re-plumbing.
- **Determinism is seed-pinned and gate-critical.** All randomness comes from one
  seeded `RandomGenerator` threaded from `simulate()`; ~46 sim tests assert
  reproducibility, and the JaCoCo gate (80% line per package) only shows at
  `clean install`. Any new RNG draw (e.g. a fatigue jitter, a substitution roll)
  consumes from that same stream and will shift seed-pinned values → re-baseline.

---

## Decisions — ALL RESOLVED (2026-06-30)

**Status: ALL SIX RESOLVED with the user.** Record them as decisions.md **#023**
(mirror #021/#022) and proceed straight to implementation — no user decisions
remain. Each notes what its resolution changes in the code.

### Decision A — Minutes model *(RESOLVED = A1)*

**Decided: A1 — possession-share → derived minutes.** The engine has no
second-clock (decisions.md #021) — it stays that way. Track each player's on-floor
possessions; map the game's total possessions to 48 (+5 per OT) minutes by ratio
and attribute each player their share. **Minutes are a display projection of
possession share**, computed at box-score-write time — consistent with #021's
"event time is derived, not stored." No schema change (`box_score.minutes` already
exists). **Resolution changes**: `PlayerGameState` gains an on-floor-possession
accumulator; `GameSimulator` computes `setMinutes(...)` from the ratio at write.

### Decision B — Fatigue model *(RESOLVED — single energy → one skill multiplier)*

**Decided**: a single per-player **`currentEnergy`** on `PlayerGameState`. It
starts full (derived from the `endurance`/`energy` attributes), **drains per
on-floor possession** (scaled by `endurance` — high endurance drains slower), and
**recovers while benched**. Its effect is **one fatigue multiplier over the
player's skills** at contest time (`effectiveSkill = skill × fatigueFactor(energy)`)
— the same avg-10 "modify a baseline, don't replace it" discipline #021/#022 use,
kept a **modest thumb on the scale** (not a sharp cliff, applied to the player's
skills broadly rather than a physical-only subset). All constants in `SimConfig`,
tuned via the calibration harness. **Resolution changes**: the `PlayerGameState`
energy field + drain/recover methods, and a fatigue multiplier at the resolver
seams.

### Decision C — Substitution trigger *(RESOLVED — deterministic, between-possessions, starter-priority)*

**Decided**: a **between-possessions** substitution check (never mid-possession),
**deterministic given (energy, coach attrs, lineupRole, rotationOrder)** so it does
**not** consume the RNG stream (§3.4 determinism tests stay stable). Each check:
drain on-floor energy, recover benched energy, then swap the most-tired eligible
on-floor player below a `substitutionAggressiveness`-scaled energy threshold for
the freshest eligible bench player, drawing down the `rotationOrder` queue only as
far as `rotationDepth` allows. Rested players become eligible to return.

**Star/starter retention (the key refinement)**: **starters are pulled later and
return first.** A player's `lineupRole` (STARTER) + `rotationOrder` position gives
them *sub priority* — starters tolerate more fatigue before being pulled (a lower
effective threshold) and get first priority to return off the bench. This reuses
existing #014 roster data (no new attribute), and naturally produces the
real-basketball pattern where stars log the most minutes and role players rotate
around them. **Resolution changes**: the possession loop gains a between-possession
sub step, and the on-floor five becomes dynamic (see Risk — the structural change).

### Decision D — Coach attribute mapping *(RESOLVED — extend CoachModifiers, avg-10 form)*

**Decided**: extend `CoachModifiers` with `rotationDepthFactor()` /
`subAggressivenessFactor()`, both the §3.4 avg-10 deviation form
(`1 + COACH_SENSITIVITY × (attr − 10) / 10`), reusing the single
`COACH_SENSITIVITY` discipline (split only if calibration needs it):
- **`rotationDepth`** → how far down the `rotationOrder` bench queue the engine
  goes (tight 7 vs. deep 10).
- **`substitutionAggressiveness`** → how early a tired starter is pulled (scales the
  energy threshold).
**Resolution changes**: `CoachModifiers` gains two readers; `SimConfig` gains the
related sensitivities + a base substitution threshold.

### Decision E — Calibration targets *(RESOLVED — extend the harness, guard §3.4)*

**Decided**: **extend the existing `CalibrationHarness`** (don't build a second
one) to also report the **minutes distribution** (per-slot) and a
**period-by-period FG%** so the fatigue effect is visible. Agree the §3.5 minutes
targets with the user before declaring calibrated (starters ~32–38, no one over
~42, bench scaled by rotationDepth), **and re-confirm the §3.4 aggregates
(112/47/36/26/14) still hold with fatigue on** — fatigue must not drag them
off-target. **Resolution changes**: harness reporting additions + a §3.5 tuning
task that also guards the §3.4 numbers.

### Decision F — Scope fence + foul-outs *(RESOLVED — within-game fatigue, foul-outs IN)*

**Decided**: fatigue is **within-game only** — nothing persists to `player_team`,
no cross-game/season stamina, energy resets each game (**keeps §3.5 schema-free for
energy**). BUT **foul-outs are pulled IN** as a **second substitution cause**
alongside fatigue:
- A player who reaches **6 fouls** (a `SimConfig` constant, `FOUL_OUT_LIMIT`) is
  **disqualified** and forced off the floor — a forced sub independent of fatigue/
  priority.
- **How a foul-out is "remembered": it is NOT stored as a new flag — it's a derived
  predicate over the existing foul counter.** `PlayerGameState.fouls` already exists
  (incremented via `recordFoul()`; persisted to `box_score.fouls`) and lives for the
  whole game whether the player is on-floor or benched. So `fouledOut(p) ==
  p.getFouls() >= FOUL_OUT_LIMIT` is evaluated fresh at each between-possession sub
  check, and because the counter only ever grows, **the disqualification is
  permanent for the rest of the game for free** — no `disqualified` boolean (that
  would be a second source of truth for the same fact, the #013/#015 duplicate-state
  trap). The rotation manager treats "fouled-out" as an **eligibility filter** (a
  player with ≥ `FOUL_OUT_LIMIT` fouls is ineligible to be on the floor), the same
  way "on-floor" / "benched" are derived from rotation state rather than stored.
- **Timing**: fouls are recorded on the *defender* mid-possession, but the foul-out
  check runs at the **between-possession** sub step — so a player who commits their
  6th foul finishes the current possession (a dead-ball sequence, realistic) and is
  pulled at the next check. No mid-possession removal.
- On a foul-out (or when fatigue subs run the bench dry), replace from the **full
  roster** (starters + entire bench, not just the `rotationDepth` window).
- **Last resort**: if every eligible player is exhausted (whole roster fouled out),
  the least-fouled available player stays on — **the floor never drops below 5**
  (so shooter/defender/rebounder picks always have 5 to draw from; no short-handed
  handling needed).

Still out of scope (the fence holds otherwise):
- **No injuries / DNPs** as sub causes — foul-out + fatigue only.
- **No §3.6 API surface** — engine + box-score population only; no `/simulate`
  endpoint, no OpenAPI beyond the existing `BoxScore.minutes` column.
- **No mid-possession subs** — substitution is between possessions only.
- **No cross-game / season stamina** — fatigue does not persist or carry over.

**Resolution changes**: a `FOUL_OUT_LIMIT` constant + foul accumulation → forced
sub in the substitution step; the rotation manager must handle full-roster
fallback + the never-below-5 invariant.

---

## ⚠️ Scope discipline — what §3.5 is and is NOT

§3.5 adds **minutes + fatigue + substitution (incl. foul-outs)** onto the existing
flow. Keep out:

- **§3.6 endpoints / API surface** — engine + box-score population only.
- **Injuries / DNPs** — the only sub causes are fatigue, rotation, and **foul-outs
  (Decision F — IN scope)**.
- **Cross-game stamina / season fatigue** — within-game only; nothing persists to
  `player_team` or carries to the next game (Decision F).
- **The deferred sim-fidelity items** (OOB, rebounding fouls, and-1, richer
  turnovers, blocked shots) stay in roadmap.md's §3.x.

---

## ⚠️ Risks / gaps (significance for the implementing session)

- **Dynamic on-floor five is a structural loop change (bigger than §3.4's).**
  §3.4 threaded read-only modifiers; §3.5 must make *who plays* change mid-game.
  `buildStarters` → "build full rotation"; `TeamContext.players()` (currently the
  fixed five) becomes an on-floor *view* over a larger squad, mutated between
  possessions. **Significance**: plan the on-floor-set abstraction once, up front
  (an `OnFloorState` / rotation manager owned per team), so the possession loop and
  every shooter/defender/rebounder pick reads the *current* five. Budget for
  touching `PossessionEngine.simulate()`'s loop + the `~46` sim tests' call sites
  (same blast radius §3.4 hit; the neutral-wrapper trick from `PossessionEngineTest`
  applies again).
- **Determinism must survive substitution + fatigue.** New per-possession state
  (energy drain, sub checks) shifts behavior; if any of it consumes RNG, seed-pinned
  assertions move. **Significance**: prefer **deterministic-given-state** substitution
  (energy + coach attrs decide, no new RNG draw) so the §3.4 determinism tests stay
  stable; if a fatigue jitter *must* be random, it uses the one threaded RNG and you
  re-baseline. Never create a second RNG.
- **Fatigue must not break §3.4's calibration.** Skill degradation lowers makes; if
  over-tuned it drags the #022 aggregates (112/47/36/26/14) off-target.
  **Significance**: Decision E must re-run the harness and confirm the §3.4 numbers
  hold *with fatigue on* — §3.5 calibrates its own minutes/fatigue targets **and**
  guards the §3.4 ones. Keep fatigue a modest multiplier (the #021/#022 discipline).
- **Box-score reconciliation must still hold.** §3.2–§3.4 reconcile box scores
  against the event log (points, rebounds, assists). With subs, **every roster player
  who played** gets a box-score row, not just 10 — and minutes across a team should
  sum to `5 × game-minutes`. **Significance**: the integration test extends to
  (a) bench players appearing in box scores, (b) minutes summing correctly,
  (c) the existing points/rebound/assist reconciliation still passing, (d) no
  on-floor player exceeding `FOUL_OUT_LIMIT` fouls while still playing (foul-out
  actually removes them).
- **Foul-outs + the never-below-5 invariant.** Decision F pulls foul-outs in; the
  rotation manager must always keep 5 on the floor even when the roster is
  exhausted (least-disqualified stays). **Significance**: every shooter / defender /
  rebounder pick assumes exactly 5 — the invariant protects those; test the
  degenerate "everyone fouled out" path explicitly (a small all-foul-prone roster
  over many possessions).
- **§3.5 is schema-free.** `box_score.minutes` already exists, and Decision F keeps
  fatigue within-game (no persisted energy). **Significance**: **no Liquibase
  migration this phase** — the H2/Postgres divergence risk (risks.md) doesn't apply.
  (Foul-outs and fatigue are pure engine state; foul counts already persist via the
  existing `box_score.fouls` column.)
- **Coverage gate is 80% line per package at `clean install`** (not `mvn test`).
  New fatigue/substitution branches need seeded-RNG tests. Target ~90%.
- **JDK 21 only** — every Maven command sets
  `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`.

---

## Task sequence

> All decisions (A–F) are resolved, so this is the working sequence. Run
> `mvn -f gametime-service/pom.xml clean install` (JDK 21) after engine + tests
> land — the per-package gate only shows there. Tick `[ ]` as each lands. Order:
> record decisions → docs → constants → state extraction → coach readers →
> rotation abstraction → substitution+foul-out loop → fatigue → minutes/box scores
> → calibrate → test → gate → close out.

- [ ] **1. Record Decisions A–F in decisions.md (#023).** One entry capturing the
  minutes model (A1), fatigue model (B), substitution trigger + starter-priority
  (C), coach-attribute mapping (D), calibration targets (E), and the scope fence +
  foul-outs (F) — mirroring how #021/#022 captured their phases. No user decisions
  remain; this is transcription.

- [ ] **2. Update docs for the agreed model.** coach.md: mark `rotationDepth` /
  `substitutionAggressiveness` as now-consumed (§3.5), fill in their `f(...)`
  effects. game.md: note `BoxScore.minutes` is now real (derived from possession
  share, Decision A), bench players now appear in box scores, and describe the
  substitution + foul-out step in the possession flow. player.md: note
  `endurance`/`energy` now drive in-game fatigue. *Files: docs/.*

- [ ] **3. Add §3.5 constants to `SimConfig`.** Fatigue drain/recovery rates, the
  energy→skill multiplier sensitivity, the base substitution threshold, the
  `rotationDepth` / `substitutionAggressiveness` sensitivities (single
  `COACH_SENSITIVITY` reused unless calibration splits it), and **`FOUL_OUT_LIMIT`
  (6)** (Decision F). *File: sim/SimConfig.java.*

- [ ] **4. Extract `endurance`/`energy` + add fatigue/minutes state to
  `PlayerGameState`.** Add the two attributes (double, null-safe `toDouble`), a
  `currentEnergy` field + drain/recover methods, and an on-floor-possession
  accumulator for the Decision-A minutes ratio. Update `TestPlayerFactory` with the
  new overload (the §3.3/§3.4 pattern — see commit `c509f87`). *Files:
  sim/PlayerGameState.java, test/.../sim/TestPlayerFactory.java.*

- [ ] **5. Extend `CoachModifiers` for rotation attributes (Decision D).** Add
  `rotationDepthFactor()` / `subAggressivenessFactor()` (avg-10 form), reads
  `rotationDepth` / `substitutionAggressiveness`. Seeded unit tests. *File:
  sim/CoachModifiers.java.*

> ### Structural approach for tasks 6–8 (the hard part — read before starting)
>
> The on-floor five is consumed at a **single seam**, not scattered — this makes
> the change smaller than it looks. Verified against the current code:
>
> - **`resolvePossession` reads the five exactly once**: lines ~72–73 do
>   `List<PlayerGameState> offense = offenseCtx.players();` /
>   `... = defenseCtx.players();` into locals, and *everything* downstream
>   (`pickShooter` / `pickDefender` / `pickStealer` / `pickOffensiveRebounder` /
>   `pickDefensiveRebounder`) reads those two locals. So making the five dynamic is
>   **changing what those two lines resolve to**, not touching every pick site.
> - **`TeamContext` is a `record`** (immutable). Do **not** try to mutate it or
>   re-plumb its signature. Instead have it **hold a mutable `RotationState`**
>   (a record can hold a reference to a mutable object) and expose the current five
>   via a method — e.g. replace the `players` component with a `rotation`
>   component and add `default List<PlayerGameState> onFloor() { return
>   rotation.onFloor(); }`. `resolvePossession` then reads `offenseCtx.onFloor()`
>   instead of `offenseCtx.players()`. Every downstream pick is unchanged.
> - **`RotationState`** (new, per team) holds the full squad + who is currently
>   on-floor; `onFloor()` always returns exactly 5 (Decision F invariant). It owns
>   `substitute()`, energy drain/recover, and the fouled-out eligibility filter.
> - **Loop ordering in `simulate()` (determinism-critical, Decision C):** the sub
>   check is a **between-possessions** step. Run it in the `for (poss...)` loop
>   **before** each `resolvePossession` call (or once per full home+away cycle —
>   pick one and document it), operating on the team **about to be on offense/
>   defense**. Drain energy for players who were on-floor the prior possession,
>   recover the benched, apply foul-out + fatigue subs — **all deterministic given
>   (energy, fouls, coach attrs), consuming NO RNG** so the §3.4 seed-pinned tests
>   only shift if you deliberately change draw order. Keep the energy-drain →
>   sub-decision order fixed and documented so it's reproducible.
>
> With this seam, tasks 6/7/8 are: build `RotationState` (6), wire the between-
> possession sub call + `onFloor()` swap (7), apply the fatigue multiplier at the
> resolver contest (8). The `~46` sim-test call sites use the `PossessionEngineTest`
> neutral-wrapper trick (a full-rotation-of-5 with neutral coach = today's behavior).

- [ ] **6. Build the on-floor / rotation abstraction (the structural change).** A
  per-team rotation manager (a new `RotationState`) that holds the **full squad**
  (starters + bench ordered by `rotationOrder`), exposes the current on-floor five
  via `onFloor()` (always exactly 5), applies substitutions, and **guarantees ≥5 on
  the floor** (Decision F last-resort). Eligibility to be on-floor is **derived**,
  not stored: a player is ineligible if fouled-out (`getFouls() >= FOUL_OUT_LIMIT`)
  — no `disqualified` flag. `GameSimulator.buildStarters` → `buildRotation` (full
  squad, ordered, with `lineupRole`/`rotationOrder`). `TeamContext` holds the
  `RotationState` and exposes `onFloor()` (see structural note above — the record
  holds a mutable reference, its signature is not otherwise re-plumbed). *Files:
  new sim/RotationState.java, sim/TeamContext.java, sim/GameSimulator.java.*

- [ ] **7. Thread substitution + foul-outs into the possession loop (Decisions C/F).**
  Add the between-possession sub step in `PossessionEngine.simulate()` per the
  structural note above (fixed, RNG-free ordering): drain on-floor energy, recover
  benched, force off any player at `FOUL_OUT_LIMIT` fouls, then run the fatigue sub
  check (starter-priority threshold, `rotationDepth`-gated bench draw, rested-
  return) — all via `RotationState.substitute(...)`. Change `resolvePossession`'s
  two seam lines to read `offenseCtx.onFloor()` / `defenseCtx.onFloor()`. Update all
  sim test call sites (the neutral-wrapper approach from `PossessionEngineTest` — a
  neutral `RotationState` of exactly the 5 = today's fixed-five behavior). *File:
  sim/PossessionEngine.java.*

- [ ] **8. Apply fatigue to resolution (Decision B).** One fatigue multiplier over
  the on-floor player's skills at contest time (shot make / defense / rebound),
  modest, via the resolver seams + `SimConfig`. Ensure it composes cleanly with the
  §3.4 coach/chemistry modifiers (multiplicative, same clamp discipline). *Files:
  sim/ShotResolver.java + the other resolvers per Decision B.*

- [ ] **9. Populate `BoxScore.minutes` + bench box scores (Decision A).** Replace
  `bs.setMinutes(0)` with minutes derived from the player's on-floor-possession
  share × total game minutes; ensure **every** roster player who took the floor
  gets a box-score row (not just the 10 starters). *File: sim/GameSimulator.java.*

- [ ] **10. Extend the calibration harness + tune (Decision E).** Add per-slot
  minutes distribution + period-by-period FG% reporting to `CalibrationHarness`;
  tune the §3.5 constants to the agreed minutes targets **and confirm the §3.4
  aggregates (112/47/36/26/14) still hold with fatigue on**. Confirm final numbers
  with the user before declaring calibrated. *Files: test/.../sim/CalibrationHarness.java,
  sim/SimConfig.java.*

- [ ] **11. Tests.** Seeded-RNG unit tests for fatigue accumulation/recovery, the
  energy→skill multiplier, the rotation manager (sub in/out, `rotationDepth`
  gating, starter-priority retention, rested-return, foul-out forced sub, and the
  degenerate all-fouled-out ≥5 invariant), and `CoachModifiers` rotation readers.
  `PossessionEngineTest` additions for substitution/foul-out flow.
  `GameSimulatorIntegrationTest`: bench players appear in box scores, minutes sum
  to `5 × game-minutes`, no on-floor player exceeds `FOUL_OUT_LIMIT`, and the
  existing points/rebound/assist reconciliation still passes. Re-baseline any §3.4
  determinism tests whose seeded values shifted. Keep `sim` ≥80% (target ~90%).

- [ ] **12. Verify the gate.**
  `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install`
  → `All coverage checks have been met.` + `BUILD SUCCESS`.

- [ ] **13. Update docs/diagrams + close out.** Check §3.5 boxes in roadmap.md + a
  "Shipped" note (mirror §3.2/§3.3/§3.4 style); update possession-flow.puml with the
  substitution + foul-out step and the fatigue multiplier; verify the decisions.md
  #023 entry matches what shipped; reset this "Current focus" to **§3.6 —
  Simulation APIs**; strip completed §3.5 content (keep focus line + the pointers to
  backlog.md / roadmap.md deferred sections).

---

## Patterns to follow (quick reference)

- **Resolver / modifier pattern**: `sim/FoulResolver.java`, `sim/SimConfig.java`,
  `sim/CoachModifiers.java` — `@Component` or value object, pure math, seeded RNG,
  all constants in `SimConfig`. Fatigue multiplier mirrors the §3.4 `coachModifier`
  / `chemistryMakeMultiplier` shape.
- **Skill/attribute extraction + factory churn**: how §3.3 added rebound skills and
  §3.4 added chemistry skills to `PlayerGameState` + the `TestPlayerFactory`
  overload ladder (`git show c509f87` for §3.3; the §3.4 commit for chemistry) —
  the same approach for `endurance`/`energy` + fatigue state.
- **Breaking-signature change handled once**: §3.4's `TeamContext` introduction +
  the `PossessionEngineTest` neutral-wrapper helpers (so existing call sites don't
  all churn) — reuse that approach for the on-floor-set change.
- **Coach hydration / rotation data**: `EntityMapper.entityToCoach` + `entityToTeam`
  put the coach + the full `RosterEntry` list (with `rotationOrder`) on `Team`;
  `Team.getPlayers()` already returns the bench — §3.5 just stops dropping it.
- **Calibration**: extend `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true`); don't build a second one.
- **Decision record**: decisions.md #021 (§3.2) and #022 (§3.4) are the model for
  capturing the §3.5 engine decisions as a single #023 entry.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md).
- **Deferred gameplay realism** (OOB-no-rebound, rebounding fouls, and-1, richer
  turnovers, blocked shots) → roadmap.md's **§3.x Deferred sim-fidelity details**.
- **§3.4 calibration harness** — `CalibrationHarness` (disabled-by-default) stays
  in the `sim` test sources; §3.5 extends it (Decision E) rather than replacing it.
