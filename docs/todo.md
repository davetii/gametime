# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.13).

Current focus: **§3.12 — All-shot-type contact fouls + graduated foul rate +
fouled-three = 3 FTs**. The full §3.7–§3.13 sequence, the
calibration-blast-radius ordering, and what follows (Phase 4) live in
**roadmap.md's "Possession-fidelity completion" section** — not here (todo.md is
current-phase-only). §3.7–§3.11 have all shipped; §3.12 is next, then §3.13
(flagrants/technicals).

> **§3.12 needs a DESIGN PASS first — there is no decisions.md entry for it yet.**
> Do **not** start writing resolvers. The next session resolves the open questions
> below into a new `decisions.md #030` (Decisions A, B, C…) plus an execute-ready
> plan in this file — the same rhythm §3.7–§3.11 each followed.
>
> The shape: today the **entire foul model** gates on the binary
> `ShotType.isContactType()` (`DRIVE || POST`). §3.12 replaces that binary with a
> **graduated per-shot-type foul rate** so *every* shot type can draw a foul and an
> and-1 (post frequent → perimeter/three rare — a closeout on a three-point shooter
> is a real foul). The same root cause carries the **latent fouled-three bug**: a
> shooting foul on a `THREE` must award **3** free throws, and today it never fires
> only because `THREE` isn't a contact type — so widening contact **activates** the
> bug and must fix it in the same pass. §3.11's per-situation FT count (#029 D) is
> the seam that fixes it: pass `3`.
>
> **This one moves scoring twice** (more fouls, and more 3-FT trips), and it also
> inherits **§3.11's deliberately deferred re-centering** — points sit at **115.9**
> vs. the ~112 §3.4 target because §3.11 took no shot-`BASE_*` trim. §3.12 should
> re-center **once**, from that 115.9 baseline. **Read decisions.md #029 (all of it,
> including the implementation note) and #028's implementation note before
> designing.**

---

## §3.12 open questions (resolve these into decisions.md #030)

1. **What replaces the binary `isContactType()`?** A per-shot-type contact/foul
   weight table (like `BASE_BLOCK_*`, §3.7) is the obvious shape — `DRIVE`/`POST`
   frequent, `PERIMETER` uncommon, `THREE` rare. Does `isContactType()` survive at
   all (as a "does this type foul at a non-trivial rate" convenience), or is it
   deleted outright in favor of the rate lookup? Note **three** call sites read it
   today: `FoulResolver.isFoul` (the pre-shot branch), `PossessionEngine`'s and-1
   gate (#029 A2), and `ShotType` itself.
2. **Does the pre-shot foul rate and the and-1 rate graduate on the SAME curve?**
   They are separate rolls with separate bases (`BASE_FOUL` vs. `AND_ONE_BASE`).
   One shared per-shot-type multiplier applied to both is simplest; two independent
   tables is more tunable but doubles the knobs. (An and-1 on a three is *rarer*
   than a foul on a three, since the shot must also go in — but that falls out of
   the two rolls being independent, so it may need no extra modeling.)
3. **Free-throw count per situation — where does the `3` come from?** #029 D
   parameterized the count, so the mechanism exists: `awardFreeThrows` already
   takes a `count`, and the pre-shot foul branch passes a constant
   `FREE_THROWS_PER_FOUL` (2). The open question is only *where the number is
   decided* — a `switch` on `ShotType` at the call site, a method on `ShotType`
   (`freeThrowsIfFouled()`), or a `SimConfig` lookup.
   **The rule this must encode (not an open question — real basketball):**
   | Situation | FTs |
   |---|---|
   | Foul stops a 2 (`DRIVE`/`POST`/`PERIMETER`) | 2 |
   | Foul stops a `THREE` | **3** ← the latent bug §3.12 activates |
   | Foul on a **made** shot (and-1), **any** shot type | **1** |
   So **only the stopped-shot count graduates by shot type**;
   `AND_ONE_FREE_THROWS = 1` stays 1 for *every* shot type, including a made three
   (a made 3 + foul is 3 points + 1 FT, not 3 FTs). Do **not** make the and-1 count
   graduate in parallel with the shooting-foul count — that is the easy wrong turn
   here, and it would silently inflate scoring on top of §3.12's real lift.
4. **Does `BASE_FOUL` keep its meaning, and does it need re-deriving?** §3.11 A1
   deliberately protected `BASE_FOUL` as "P(a contact foul stops the shot)" *on
   drive/post*. Once perimeter/three can be fouled, is `BASE_FOUL` the drive/post
   rate with other types scaled off it, or a new league-wide base with every type
   scaled? The first keeps §3.4's calibration legible; the second is cleaner but
   reopens a calibrated number (the trap #029 A1 refused).
5. **How much does this move fouls per period / the bonus?** §3.11 already pushed
   fouls to 4.41/team/period and team-periods-in-the-penalty to 43.4% (bonus at 5).
   Widening contact to all shot types pushes both up again — is the penalty rate
   still plausible, or does `BONUS_FOULS_PER_PERIOD` need revisiting? **Build the
   harness view before claiming a number** (#026 D); the §3.10/§3.11 foul lines
   already report both, so this may need no new instrument — confirm.
6. **The recalibration plan (and the §3.11 debt).** Two new scoring sources at once
   (more fouls → more FT trips; 3-FT trips on fouled threes) *plus* the inherited
   +3.9 over target. Confirm the lever is the shot `BASE_*` (the §3.10/§3.11
   finding), that `BASE_FOUL` is **not** used to remove points (#028: wrong way),
   and that steering is by **multiple seeds to the mean** (#029 E; the harness takes
   `-DcalibrationSeed`). Decide the target: back to ~112, or a re-agreed landing.
   **Decompose the lift by channel BEFORE picking a trim** — both prior foul passes
   proved the headline number hides the mechanism, and §3.12's lift is the first
   that is genuinely *mixed*:
   - **FT channel** (up) — more fouls drawn, and 3-FT trips on fouled threes.
   - **Possession-ending channel** (*down*) — a foul that **stops** a perimeter or
     three attempt removes a live shot and replaces it with ~1.5–2.3 expected FT
     points. That is the #028 `BASE_FOUL` wrong-way effect, and §3.12 is the pass
     where it starts applying to **jump shots** — where it bites hardest, because a
     stopped `THREE` removes a 3-point attempt but awards 3 FTs at ~75%, which is
     roughly a wash rather than a clear gain. **A wider foul model may move points
     LESS than expected, or even down in places.** Do not assume the lift is
     additive the way §3.11's was.
   Because the two channels partly cancel, §3.11's ~0.6% FG% per point exchange
   rate does **not** carry over — re-measure it on §3.12's own numbers before
   trading any FG% away. Note the FG% mechanics: the pre-shot foul branch returns
   **before** `recordFieldGoalAttempt()`, so a stopped shot charges **no FGA** —
   it removes points without moving FG% directly. But it does change the *shot mix*
   FG% is computed over (fouls would now remove perimeter/three attempts, not just
   drive/post ones), so **3P% and FG% can drift even though no individual shot
   probability changed**. Watch both on the harness rather than assuming they hold.

---

## Verified facts (the `sim` package map — confirmed against the code 2026-08, post-§3.11)

Paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`.
Trust these; re-check only if the code moved. All engine code is in the `sim/` package.

**The binary gate §3.12 replaces:**
- `ShotType.isContactType()` = `DRIVE || POST`. Read in exactly three places:
  `FoulResolver.isFoul` (returns `false` immediately for non-contact types),
  `PossessionEngine`'s and-1 gate in the `if (made)` block (#029 A2), and tests.
- `SimConfig.BASE_FOUL = 0.15` — the pre-shot foul rate, meaning "P(contact stopped
  the shot)" **on drive/post**. §3.4-calibrated; §3.11 deliberately left it alone.
- `SimConfig.AND_ONE_BASE = 0.055` + `AND_ONE_SENSITIVITY = 0.10` — the §3.11 and-1
  rate, landing 1.67 and-1s/team/game (6.4% of made contact FG).

**The FT count/source seam — ALREADY BUILT (§3.11, #029 B/D):**
- `PossessionEngine.awardFreeThrows(data, shooter, shootingTeamId, offTeamId,
  defTeamId, period, sequence, count, source, rng)` takes a **per-situation count**
  and a **`FreeThrowSource`**. Three call sites today: shooting foul
  (`FREE_THROWS_PER_FOUL` = 2, `SHOOTING`), §3.10 bonus (2, `BONUS`), and-1
  (`AND_ONE_FREE_THROWS` = 1, `AND_ONE`). **§3.12's fouled-three passes `3` here** —
  this is the seam, no new machinery needed.
- `FreeThrowSource` enum owns its `outcome(boolean made)` string —
  `MADE_SHOOTING` / `MISSED_BONUS` / `MADE_AND_ONE`. The `MADE`/`MISSED` prefix
  **leads** so `startsWith("MADE")` reads keep working; a new source (if §3.12 or
  §3.13 needs one) is a new enum constant, no schema change (#020).

**The rare-event probability machine (§3.7 + §3.10 + §3.11):**
- `SimConfig.rareEventProbability(base, drivingSkill, opposingSkill, sensitivity)`
  clamps to **[0, PROB_CEILING]** with **no floor** — a thin base stays tunable
  downward. `clampProbability` (floor `PROB_FLOOR = 0.02`) is for normal-frequency
  outcomes only. **Each rare event carries its OWN sensitivity**:
  `BLOCK_SENSITIVITY = 0.12`, `REBOUND_FOUL_SENSITIVITY = 0.10`,
  `AND_ONE_SENSITIVITY = 0.10`. The global `SENSITIVITY = 0.5` swamps a thin base —
  three phases have now hit that; don't hit it a fourth.
- **A zero base does NOT switch a rare event off** (measured in §3.11): the formula
  is `base + sensitivity × (driving − opposing)/10`, so the skill term alone keeps
  the rate positive whenever the driving side is favored. Only the **caller's gate**
  disables a feature. Relevant to §3.12 if any shot type is meant to be foul-free.
- `FoulResolver.isFoul` (pre-shot) and `FoulResolver.isAndOne` (post-make) share the
  same avg-10 wiring: shooter `foulDrawing × fatigueFactor` vs. defender
  `(SCALE_AVG*2 − foulProne) × fatigueFactor`, scaled by `defensivePressure`.

**The team-foul / bonus substrate (§3.10, #028) — reused unchanged by §3.11:**
- `GameData.isInBonus(teamId, period)` + `periodFoulCount` derive the penalty from
  the `FOUL` event log — no stored counter, emit-then-count.
- `game_event.committing_team_id` carries the committer on every `FOUL`.
  One-sided fouls (`SHOOTING_FOUL`, `AND_ONE`) pass `defTeamId`; the two-sided
  `REBOUNDING_FOUL_*` passes whichever side the roll picked.
- `SimConfig.BONUS_FOULS_PER_PERIOD = 5`, `FREE_THROWS_PER_FOUL = 2`.

**`test/.../sim/CalibrationHarness.java`:** disabled-by-default
(`-Dcalibration=true`), and takes **`-DcalibrationSeed=NNNN`** (§3.11) so the
multiple-run discipline is runnable. Prints the §3.4 aggregates + §3.5
minutes/period-FG% + §3.7 blocks + §3.8 OOB + §3.9 turnover-cause mix + §3.10
team-fouls/bonus + §3.11 and-1 rate + FT-source split. The FT split is read off the
self-describing outcome, with an `UNKNOWN` bucket that surfaces an untagged FT.
Re-run after any `SimConfig` change.

**§3.11 landing to recalibrate against (harness, ~102 games × 5 seeds, to the mean):**
`115.9 pts / 46.8% FG / 37.6% 3P / 27.5 ast / 13.9 TO / 4.9 blk / 2.8 OOB`,
and-1s 1.67/team/game (6.4% of made contact FG), FT split SHOOTING 90.6% /
AND_ONE 5.8% / BONUS 3.6%, fouls 4.41/team/period, 43.4% of team-periods in the
penalty. §3.4 targets remain ~112/47/36/26/14 — **points are knowingly 3.9 high**,
deferred to §3.12 (see roadmap + #029's follow-up).

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md). *(The **`decisions.md` condense pass** is now
  **UNBLOCKED** — §3.11 shipped, so §3.7–§3.11 is a complete arc. Preserve the four
  findings §3.11 actually reached for; see the backlog entry.)*
- **Untriaged future-improvement ideas** (no phase home, not chores) →
  [ideas.md](ideas.md). *(Includes the parked cap 3→5 tuning idea — do NOT touch the
  `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` value without its own recalibration pass.)*
- **§3.13 (flagrant/technical fouls)** → roadmap.md's Possession-fidelity section, a
  numbered sub-phase needing its own design pass (decisions.md #029 follow-up).
  **Not this pass** — it changes *who shoots*, adds a possession-retention path no
  current model has, and adds ejections.
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
- **§3.10/§3.11 seams left open by #028/#029** (carry forward):
  `committing_team_id` is **populated and queryable but not surfaced on the OpenAPI
  `GameEvent`** (the and-1 `FOUL` populates it too) — additive whenever a
  play-by-play or Phase-4 stats consumer wants it; the observed off/def
  rebounding-foul split (**78/22**) drifts from the configured 75/25 because
  defensive fouls compound through retained possessions — back-solve only if a
  consumer needs the observed split to hit a target.
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true -DcalibrationSeed=NNNN`) stays in the `sim` test sources; it
  reports the §3.4 aggregates + the §3.5 minutes/period-FG% distributions + the §3.7
  blocks line + the §3.8 OOB line + the §3.9 turnover-cause mix + the §3.10
  team-fouls/bonus lines + the §3.11 and-1 rate + FT-source split. Re-run it after
  any `SimConfig` change.
