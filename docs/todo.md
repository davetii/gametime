# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.11).

Current focus: **§3.11 — And-1 / shooting foul on a made basket**. The full
§3.7–§3.11 sequence, the calibration-blast-radius ordering, and what follows (Phase 4)
live in **roadmap.md's "Possession-fidelity completion" section** — not here
(todo.md is current-phase-only). §3.7, §3.8, §3.9, and §3.10 shipped; §3.11 is
**last** in the sequence and closes it out.

> **§3.11 needs a DESIGN PASS first — there is no `#029` yet.** Do not execute from
> this file until the open questions below are resolved into a `decisions.md #029`
> entry plus an execute-ready plan (the #025/#026/#027/#028 rhythm). §3.11 is the
> **biggest recalibration** of the five sub-phases: it restructures the possession
> branching so a foul can fire *alongside* a made shot rather than instead of a
> shot, and it adds free-throw volume on top of makes that already scored.
> **The substrate it needs already exists** — §3.10 (#028) built the team-foul/bonus
> derivation and the reusable FT-award block; §3.11 consumes them, it does not
> rebuild them.

---

## §3.11 design pass — open questions to resolve into `decisions.md #029`

Each of these becomes a Decision in #029. Where a call is genuinely the user's
(realism/feel, not mechanics), **surface it — don't guess** (the #027 taxonomy and
#028 A1/A2/D precedent).

**1. Branch restructuring — where does the and-1 roll live?**
Today `PossessionEngine`'s `// 2. Foul check` **returns before the shot** — a foul
and a shot are mutually exclusive. An and-1 requires the foul to be resolvable
*with* a made FG. The fork: (a) keep the existing pre-shot foul branch for the
"foul, no basket" case and add a **second, post-make roll** for the and-1; or
(b) restructure into one foul roll whose outcome depends on whether the shot went
in. Which shape keeps `BASE_FOUL`'s §3.4 calibration legible?

**2. Does an and-1 award ONE free throw, and is `FREE_THROWS_PER_FOUL` still right?**
A real and-1 is made FG + **one** FT, but `SimConfig.FREE_THROWS_PER_FOUL = 2` is a
flat constant the shooting foul and §3.10's bonus both use. Does §3.11 introduce a
per-situation FT count, or a separate `AND_ONE_FREE_THROWS = 1`?

**3. Event vocabulary — how is the and-1 represented?**
Candidates: a `FOUL` / `AND_ONE` outcome following the made `SHOT` (reusing
`PlayType.FOUL` + the §3.10 `committing_team_id`, the #025 F/#026 E reuse
discipline), vs. a new outcome on the `SHOT` itself. Must not collide with
`SHOOTING_FOUL` or §3.10's `REBOUNDING_FOUL_*`. **Does the and-1 FT reconcile
distinguishably from a bonus FT?** (§3.10 accepted that bonus and shooting-foul FTs
are indistinguishable except by the preceding `FOUL` — is that still acceptable at
three FT sources?)

**4. Assist interaction.** A made FG may carry an `assist_player_id` (#022 B). Does
an and-1 make still roll for an assist? (Real basketball: yes.) Confirm nothing in
the assist reconciliation breaks when a `FOUL` event lands between the `SHOT` and
the next possession.

**5. Recalibration scope — the honest one.** §3.11 adds FTs **on top of shots that
already scored**, so it is a **pure additive scoring source** with no offsetting
removal — unlike §3.10, where most of the lift turned out to be retained
possessions. Expect a larger, cleaner lift. **Read #028's implementation note first:**
`BASE_FOUL` is a **counter-intuitive lever that moves points the WRONG way**
(trimming it converts a possession-ending 2-FT trip back into a live shot worth
more), so the §3.7/§3.10 shot-`BASE_*` lever is likely the real knob again. Decide
the instrument (an and-1 rate + FT-source-split harness line) **before** tuning, per
the #026 D "build the instrument" discipline.

**6. Does the and-1 rate need its own `SimConfig` sensitivity?** Both §3.7 (blocks)
and §3.10 (rebounding fouls) discovered that the **global `SENSITIVITY = 0.5` swamps
a thin base rate**, and §3.10 additionally hit the **`PROB_FLOOR = 0.02` trap** (a
rare-event knob that could only be tuned upward — fixed with
`SimConfig.rareEventProbability`). If the and-1 rate is thin, it likely wants
`rareEventProbability` + its own sensitivity from the start rather than discovering
this a third time.

---

## Verified facts (the `sim` package map — confirmed against the code 2026-07, post-§3.10)

Paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`.
Trust these; re-check only if the code moved. All engine code is in the `sim/` package.

**The foul branch §3.11 must restructure — `sim/PossessionEngine.java`:**
- `// 2. Foul check` (~L129) calls `foulResolver.isFoul(shotType, shooter, defender,
  defensivePressure, rng)`, which fires **only** on contact shot types
  (`shotType.isContactType()`) and **returns before the shot** — this early return is
  exactly what §3.11 has to open up. It emits `FOUL` / `SHOOTING_FOUL` with
  `committingTeamId = defTeamId`, then calls `awardFreeThrows(...)`.
- `// 3. Shot` (~L150) runs the block carve (§3.7) then the make/miss contest; the
  made-FG branch records points, rolls the assist, and returns. **The and-1 seam is
  between the make being decided and that return.**
- `awardFreeThrows(data, shooter, shootingTeamId, offTeamId, defTeamId, period,
  sequence, rng)` (§3.10) is **already extracted and reusable** — it loops
  `FREE_THROWS_PER_FOUL`, records attempt/make, emits `MADE`/`MISSED` `FREE_THROW`
  events, and `addScore`s to `shootingTeamId`. §3.11 reuses it (possibly with a
  per-situation FT count — open question 2).

**The team-foul / bonus substrate — ALREADY BUILT (§3.10, #028 A1):**
- `GameData.isInBonus(teamId, period)` + `GameData.periodFoulCount(teamId, period)`
  derive the penalty from the `FOUL` event log — no stored counter, no reset logic,
  **emit-then-count** (the event is added first, so the Nth foul awards). §3.11
  reuses this **as-is**; an and-1 foul counts toward the tally like any other.
- `game_event.committing_team_id` (nullable `VARCHAR`) carries the committer on
  every `FOUL` event. An and-1 foul is on the **defense**, so it is `defTeamId` —
  the simple case, not §3.10's two-sided one.
- `SimConfig.BONUS_FOULS_PER_PERIOD = 5`.

**Rare-event probability (learned in §3.7 + §3.10 — don't rediscover):**
- `SimConfig.rareEventProbability(base, drivingSkill, opposingSkill, sensitivity)`
  clamps to **[0, PROB_CEILING]** with **no `PROB_FLOOR`**, so a thin base stays
  tunable downward and can be zeroed. `clampProbability` (floor 0.02) is for normal-
  frequency outcomes only.
- Rare events carry their own sensitivity: `BLOCK_SENSITIVITY = 0.12` (§3.7),
  `REBOUND_FOUL_SENSITIVITY = 0.10` (§3.10). The global `SENSITIVITY = 0.5` is for
  common contests.

**`test/.../sim/CalibrationHarness.java`:** disabled-by-default
(`-Dcalibration=true`), prints the §3.4 aggregates + §3.5 minutes/period-FG% + §3.7
blocks + §3.8 OOB + §3.9 turnover-cause mix + §3.10 team-fouls/bonus-FT lines.
Re-run after any `SimConfig` change. The §3.10 block already reports **fouls by
outcome** and **bonus vs. total FTA** — an and-1 line extends it rather than
starting fresh.

**§3.10 landing to recalibrate against (harness, 102 games):**
`113.8 pts / 46.9% FG / 37.6% 3P / 27.7 ast / 14.2 TO / 5.0 blk / 2.8 OOB`,
fouls 3.95/team/period, 37.1% of team-periods in the penalty, bonus FTA
0.8/team/game. §3.4 targets remain ~112/47/36/26/14.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md). *(Includes a **`decisions.md` condense pass,
  deliberately queued for AFTER §3.11 ships** — §3.11's design pass is the heaviest
  consumer of the very entries that would be compressed (#025/#026 D/#028), so it
  waits until §3.7–§3.11 is a complete arc. **Do not compress #028 while §3.11 is
  live** — question 5 below depends on its implementation note.)*
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
- **§3.10 seams left open by #028** (carry forward): `committing_team_id` is
  **populated and queryable but not surfaced on the OpenAPI `GameEvent`** — additive
  whenever a play-by-play or Phase-4 stats consumer wants it; and the observed
  off/def rebounding-foul split (**78/22**) drifts from the configured 75/25 because
  defensive fouls compound through retained possessions — back-solve only if a
  consumer needs the observed split to hit a target.
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true`) stays in the `sim` test sources; it reports the §3.4
  aggregates + the §3.5 minutes/period-FG% distributions + the §3.7 blocks line
  + the §3.8 OOB line + the §3.9 turnover-cause mix + the §3.10 team-fouls/bonus-FT
  lines. Re-run it after any `SimConfig` change.
