# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.11).

Current focus: **§3.10 — Loose-ball / rebounding fouls + the shared team-foul/bonus
substrate**. The full §3.7–§3.11 sequence, the calibration-blast-radius ordering, and
what follows (Phase 4) live in **roadmap.md's "Possession-fidelity completion"
section** — not here (todo.md is current-phase-only). §3.7, §3.8, and §3.9 shipped;
§3.10 is next.

> **§3.10 is now execute-ready — design resolved as decisions.md #028 (A–E).** The
> shape: a **non-shooting foul during the rebound phase** (box-out push, over-the-back)
> becomes a real `FOUL` event, and §3.10 builds the **team-foul / bonus (penalty)
> substrate shared with §3.11**. The crux (Decision A, user call): **penalty status is
> DERIVED from the FOUL event log** — `count(FOUL events for team T in period P) >=
> BONUS_FOULS_PER_PERIOD` — **no stored counter, no schema column** (the #023-F
> derive-don't-store discipline; #020 events-as-truth). The committing player is
> `primary_player_id` on the `FOUL` event (as the shooting foul already logs). A
> rebounding foul retains possession under the bonus and goes **straight to bonus free
> throws once IN the penalty** (Decision B, user requirement), reusing the existing FT
> block. **This one is NOT free** — bonus FTs add points, so §3.10 earns a small
> **recalibration pass** (Decision E), unlike §3.8/§3.9. Read #028 before executing.

---

## §3.10 execution plan (decisions.md #028 A–E — resolved, ready to build)

Build order. Seam: the rebound-phase foul roll (in/around `MissedShotResolver` +
`FoulResolver`) + a **derived penalty predicate** over the `FOUL` event log +
`SimConfig` + `PossessionEngine`'s `// 4. Rebound` block + `CalibrationHarness`.
**No schema, no OpenAPI, no new `PlayType`** (rebound foul is a `PlayType.FOUL`
`outcome`; the penalty is derived, not stored). Mirror the §3.7/§3.8/§3.9 execution
rhythm.

> All Maven commands set `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
> (JDK 21). Per-package coverage gate (80% line, target ~90%) only at
> `mvn -f gametime-service/pom.xml clean install`. Engine work in the `sim` package —
> no OpenAPI/schema change. `CalibrationHarness` is disabled by default
> (`-Dcalibration=true`).

**Step 1 — the derived penalty predicate over the FOUL event log (#028 A).**
- [ ] A helper (leading candidate: on `GameData`, next to `getEvents()`, or a small
  `sim` utility) that answers **"is team T in the bonus in period P?"** as
  `count(FOUL events where the committing team == T and period == P) >=
  SimConfig.BONUS_FOULS_PER_PERIOD`. **No stored `teamFouls` field, no reset logic** —
  it reads the events (#020) exactly as #023-F's foul-out predicate reads the `fouls`
  counter.
- [ ] Both `SHOOTING_FOUL` and the new rebounding foul count toward the tally (all
  defensive fouls, #028 A). Determine the *committing team* from the `FOUL` event — the
  fouling player is `primary_player_id`; confirm the event carries enough to attribute
  the foul to the defending team (it names off/def team ids today — the committer is on
  the defense).
- [ ] `SimConfig.BONUS_FOULS_PER_PERIOD = 5` (modern-NBA placeholder, harness-tunable).

**Step 2 — the rebound-phase foul roll, carved off the top (#028 C).**
- [ ] In the miss-resolution flow (`MissedShotResolver`, #026), roll a **rebound-foul
  chance FIRST** — before the four-way board draw. On a foul, **short-circuit** the
  board contest (the whistle stopped play). Same "carve off the top, then run the
  existing contest on the remainder" shape as §3.7's `P(BLOCK)`.
- [ ] The foul probability is a small base scaled by the discipline/pressure skills the
  shooting foul already uses (`foulProne` / `defensivePressure`, the coach.md
  pressure/breakdown trade-off), in the avg-10 form (#021 C / #022). Keep it
  **independent of the rebound weights** (that independence is why the roll is carved
  off the top, not a fifth draw outcome — #028 C).
- [ ] Whether this lives in an extended `FoulResolver` method or a small sibling
  resolver is an execution call (#028 status). New base rate → `SimConfig`.

**Step 3 — reward: possession-retention under the bonus, bonus FTs in the penalty (#028 B).**
- [ ] On a rebounding foul, evaluate the Step-1 predicate for the **committing team**:
  - **Not in the bonus** → award the fouled (offensive-rebounding) team the ball; the
    possession forks like the other retain/end outcomes. **No free throws.**
  - **In the bonus** → **bonus free throws**, reusing the existing FT-award block
    verbatim (`PossessionEngine` ~L136–144: the `for` over `FREE_THROWS_PER_FOUL`,
    `recordFreeThrowAttempt()`/`recordFreeThrowMade()`, `MADE`/`MISSED` `FREE_THROW`
    events, `addScore`). The FT shooter is a player on the fouled team.
- [ ] Emit the `FOUL` event **regardless** of bonus (Step 4) — the predicate reads it,
  and the foul must count toward the tally *before* the next check.

**Step 4 — wire into `PossessionEngine`'s `// 4. Rebound` + emit the FOUL event (#028 C/D).**
- [ ] The rebound-foul roll runs at the miss-resolution site (line ~224,
  `missedShotResolver.resolve(...)`). `period` is already in scope (the derivation needs
  team + period).
- [ ] Emit a `PlayType.FOUL` event, `outcome = "REBOUNDING_FOUL"` (final spelling an
  execution call), `primary_player_id` = the committing player; **increment that
  player's `fouls`** (feeds foul-outs, #023-F) exactly as the shooting foul does.
- [ ] Fixed RNG order in the miss flow: the rebound-foul roll consumes the seed
  **before** the board draw — existing seed-pinned rebound assertions re-baseline (a
  controlled structural shift, as §3.7/§3.9 did).

**Step 5 — `CalibrationHarness` team-fouls / bonus-FT line + recalibration (#028 E).**
- [ ] Add a line printing **team fouls / period** and the **bonus-FT rate** next to the
  existing aggregates (the §3.8-OOB / §3.9-cause-mix precedent) so the new FT source is
  visible.
- [ ] Run the harness (`-Dcalibration=true`). **§3.10 is NOT free** — bonus FTs add
  points, so expect points to lift. **Recalibrate** (nudge `BASE_FOUL` / the rebound-foul
  base down, or accept the lift) and **re-agree the numbers with the user** against the
  ~112/47/36/26/14 (+5 blk, +3 OOB) baseline — like §3.7's pass, not §3.8/§3.9's
  confirm-green.
- [ ] Eyeball the team-fouls/period so it's plausible (roughly a handful per period, not
  0 and not 20).

**Step 6 — tests + close-out.**
- [ ] Unit-test the penalty predicate (below/at/above threshold; only in-period fouls
  count; both foul types count), the rebound-foul roll (a fixed seed drives a foul; a
  high-`foulProne`/pressure input raises the rate), and the reward fork (retain under
  bonus vs. FTs in the penalty). Plus a determinism/count test.
- [ ] Confirm `GameSimulatorIntegrationTest` still reconciles — the new `FOUL`/
  `FREE_THROW` events must not break points or the foul reconciliation. Bonus FTs add to
  points; confirm points still reconcile with the event log (#020).
- [ ] New/changed `sim` classes to ~90%+ line coverage (JaCoCo gate), matching
  §3.7/§3.8/§3.9; full `mvn clean install` gate green.
- [ ] **Doc close-out (the §3.7/§3.8/§3.9 pattern — do all of these):**
  - [ ] `game.md` — add the `FOUL` / `REBOUNDING_FOUL` row to the vocabulary table;
    add a **penalty/bonus** note to the possession-flow narrative (fouls counted from
    the event log per period; the bonus sends non-shooting fouls to FTs). This is the
    living event-vocabulary reference.
  - [ ] `roadmap.md` — flip the §3.10 bullet to `[x]` with an indented italic landing
    summary (the recalibration result — final aggregates incl. the FT lift, team-fouls/
    period, coverage), matching the §3.7/§3.8/§3.9 shipped bullets.
  - [ ] `decisions.md #028` — add the **implementation note** (`from execution,
    YYYY-MM`): the resolved open-at-execution items (final `outcome` spelling, the
    rebound-foul base + `BONUS_FOULS_PER_PERIOD`, resolver placement, the recalibration
    size/direction), the landing aggregates, and coverage. Note any divergence from A–E.
  - [ ] `coach.md` (optional) — the `defensiveScheme` pressure/foul row could note that
    scheme now also drives rebounding fouls + bonus exposure. `player.md` — the
    `foulProne`/`foulDrawing` foul row could note rebounding fouls.

**Reconciliation invariant:** §3.10 adds `FOUL` (`REBOUNDING_FOUL`) and, in the bonus,
`FREE_THROW` events. Points must still reconcile with the event log (#020) — bonus FTs
`addScore` exactly as shooting-foul FTs do. The rebounding foul feeds the per-player
`fouls` counter (foul-outs, #023-F) like any foul. No new box-score counter.

**Do NOT (guardrails from #028 / #023-F / #020):**
- Do **not** add a stored `teamFouls` field / schema column — the penalty is **derived
  from the FOUL event log** (#028 A). A stored counter is the #013/#015 duplicate-state
  trap #023-F rejected.
- Do **not** introduce a new `PlayType` — a rebounding foul is a **kind of `FOUL`**
  (#025 F / #026 E); it reuses `PlayType.FOUL` with a new `outcome`.
- Do **not** pick up §3.11 (and-1) — it restructures the possession branching (foul
  *alongside* the shot) and is the bigger recalibration; it gets its own design pass on
  **this** substrate (#028 scope call).
- Do **not** couple the rebound-foul rate to the rebound weights — carve it off the top
  so it stays independently tunable (#028 C).

**Open-at-execution (small, constrained — #028 status):** the exact `REBOUNDING_FOUL`
outcome spelling; the rebound-foul base rate + `BONUS_FOULS_PER_PERIOD` numbers
(placeholders, settled by the Step-5 harness line); whether the foul roll lives in an
extended `FoulResolver` method or a small sibling resolver; and the size/direction of
the recalibration (E), re-agreed with the user against the harness.

---

## Verified facts (the `sim` package map — confirmed against the code 2026-07, post-§3.9)

Paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`.
Trust these; re-check only if the code moved. All engine code is in the `sim/` package.

**The foul model to extend — `sim/FoulResolver.java` + `sim/PossessionEngine.java`:**
- `FoulResolver.isFoul(shotType, shooter, defender, defensivePressure, rng)` fires
  **only** on contact shot types (`shotType.isContactType()`), returns before the shot,
  and `PossessionEngine`'s `// 2. Foul check` emits a `FOUL` / `SHOOTING_FOUL` event
  (`primary_player_id` = the fouling **defender**), then the FT-award loop (~L136–144).
  §3.10 adds a **rebound-phase** foul with the same event shape + committer logging.
- The rebound contest (`MissedShotResolver.resolve(offense, defense, capReached, rng)`,
  line ~224, `// 4. Rebound`) is **foul-free today**. §3.10 carves a foul roll off the
  top of it (#028 C).
- `period` is threaded through `resolvePossession` (param) to every `addEvent` — the
  penalty derivation (team + period) has it in scope.

**The team-foul / bonus substrate (new, #028 A):**
- **No per-team-per-period foul count exists.** `PlayerGameState.fouls` is a per-player,
  whole-game counter (foul-outs, #023-F). The penalty is a **derived predicate over the
  `FOUL` events** (team + period + `BONUS_FOULS_PER_PERIOD`), NOT a stored counter.
- `GameData.getEvents()` already exposes the event list (the harness reads it); the
  derivation reads FOUL events from there.

**The §3.7/§3.8/§3.9 templates to mirror (all shipped in `sim`):**
- **§3.7 blocks** — the "carve a slice off the top, run the existing contest on the
  remainder" shape (`P(BLOCK)` before make/miss). §3.10's foul roll mirrors this.
- **§3.9 turnover causes** — a derived/relabel pass; and the discipline of reusing an
  existing `PlayType` + an open-ended `outcome`, no schema.
- **#023-F foul-outs** — the **derived-predicate, no-stored-flag** discipline the
  penalty derivation copies to the team level.

**Reconciliation precedent — `GameSimulatorIntegrationTest`:** reconciles box-score
counters against event counts (points, rebounds, assists, blocks — #020/#022/#025/#026).
§3.10 adds `FOUL`/`FREE_THROW` events; points must still reconcile (bonus FTs `addScore`
like shooting-foul FTs) — confirm it.

**`test/.../sim/CalibrationHarness.java`:** disabled-by-default (`-Dcalibration=true`),
prints the §3.4 aggregates + §3.5 minutes/period-FG% + §3.7 Blocks line + §3.8 OOB line
+ §3.9 turnover-cause mix. Re-run after any `SimConfig` change; add the §3.10 team-fouls/
bonus-FT line (Step 5).

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md).
- **Deferred gameplay realism** (and-1) → **numbered sub-phase §3.11** in roadmap.md
  ("Possession-fidelity completion"), scheduled before Phase 4. It builds on §3.10's
  team-foul/bonus substrate (#028 follow-up) and gets its own design-pass + execution.
- **Untriaged future-improvement ideas** (no phase home, not chores) →
  [ideas.md](ideas.md). *(Includes the parked cap 3→5 tuning idea — do NOT touch the
  `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` value without its own recalibration pass.)*
- **§3.6 seams left open by #024** (carry into Phase 4/7): play-by-play pagination
  + a `period` filter (Decision D); the per-event time column's storage shape
  (Decision E — decided by the Phase 7 game view); a lean header-only `GameResult`
  projection (Decision A).
- **§3.7 seams left open by #025** (carry forward): a skilled-blocker recovery edge
  (Decision D — additive if a consumer ever wants it); shot-clock pressure on
  block-recovered second-chance possessions (Decision E → §3.9-E, still parked #027 D).
- **§3.9 seams left open by #027** (carry forward): §3.7-E shot-clock pressure (still
  parked); finer turnover sub-types (`DOUBLE_DRIBBLE`/`CARRYING`/`PALMING`/etc. — add a
  weight + enum value when a consumer wants the granularity).
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true`) stays in the `sim` test sources; it reports the §3.4
  aggregates + the §3.5 minutes/period-FG% distributions + the §3.7 blocks line
  + the §3.8 OOB line + the §3.9 turnover-cause mix. Re-run it after any `SimConfig`
  change.
