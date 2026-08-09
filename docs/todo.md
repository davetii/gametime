# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.16).

Current focus: **§3.14a — Technical fouls**. The full §3.7–§3.16 sequence and what
follows (Phase 4) live in **roadmap.md's "Possession-fidelity completion"
section** — not here (todo.md is current-phase-only). §3.7–§3.13 have all shipped.

> **§3.14 SPLIT IN TWO (decisions.md #032 A, user call).** The design pass found
> that technicals and flagrants share **nothing but the word "foul"** — different
> trigger, different location in the engine, different FT count, different possession
> effect, different disqualification accounting, different committer pool. They are
> two mechanics, so they are two passes:
>
> - **§3.14a — technical fouls.** Design **RESOLVED as `decisions.md` #032 A–J**.
>   The execute-ready plan is below. **This is the current phase.**
> - **§3.14b — flagrant fouls.** **Needs its own DESIGN PASS** — the open questions
>   are preserved at the bottom of this file. It owns the possession-retention fork
>   and the genuine stored-state exception. **Do not execute it from those questions.**
>
> Numbering is `a`/`b` deliberately: §3.15 (profiles) and §3.16 (recalibration) do
> **not** renumber, because §3.16 is named in the shipped, never-retro-edited text of
> #030 and #031 (#032 A).
>
> **The one finding that changes what §3.14a builds:** #031 H, roadmap.md, and this
> file all predicted §3.14 would need **real stored state** for ejections — the first
> exception to #023 F. **That is true of a flagrant-2 and FALSE of the two-technical
> case**, which is `technicalFouls >= 2`: a monotonic counter, structurally identical
> to `fouls >= 6`. So **§3.14a keeps the ejection DERIVED** and the #023 F exception
> moves to §3.14b (#032 F).

---

## §3.13 close-out (SHIPPED 2026-08 — kept only as the handoff §3.14a needs)

Landed as `decisions.md` **#031 A–H** + its implementation note; roadmap bullet is
`[x]`. What §3.14a needs to know:

- **The removal machinery it must extend**: `RotationState.advancePossession(rng)`
  now runs **drain/recover → `replaceFouledOut()` (hard) → `runFoulTroubleSub()`
  (soft) → `runFatigueSubs()` (fatigue)**. `eligible(...)` is the single filter every
  candidate pool passes through — that is the seam an ejection extends.
- **The rotation step CONSUMES RNG now** (one unconditional draw per call, revising
  #023 C). `advancePossession` takes a `RandomGenerator` **method parameter**;
  constructor call sites are untouched. **§3.14a need not re-argue this** — but it
  changes the *number* of draws per call, which re-baselines any seed-pinned stream
  expectation.
- **Foul-outs landed at 0.388**, 4/5/6 at `1.00 / 0.52 / 0.39`. Promoted to a soft
  TARGET (~0.39). The lever is **saturated** — do not re-tune the sit curve.
- **§3.4 aggregates are unmoved** (117.0 pts / 46.6% FG / 26.7 ast / 13.8 TO /
  5.0 blk / 19.0 fouls, 5-seed mean).

---

## §3.14a execution plan (decisions.md #032 A–J — resolved, ready to build)

Build order. Seam: **a game-level rate constant in `SimConfig`** divided down to a
per-check probability, **rolled in `RotationState.advancePossession(rng)`** (not in
the possession flow — a technical has no contest to hang off, #032 B), a
**`foulProne`-weighted committer draw over the on-floor five**, **one free throw**
by a **deterministic best-shooter** pick, and a **separate `technicalFouls` counter**
that does **not** feed the 6-foul limit. **No schema, no OpenAPI, no new `PlayType`,
no new `FreeThrowSource`, no new coach attribute, no new player attribute or skill.**
**One new `outcome` string** (`TECHNICAL_FOUL`, free text since #020). Mirror the
§3.7–§3.13 execution rhythm.

> All Maven commands set `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
> (JDK 21). Per-package coverage gate (80% line, target ~90%) only at
> `mvn -f gametime-service/pom.xml clean install`. Engine work in the `sim` package —
> no OpenAPI/schema change.

**The two commands you need** (both verified working, 2026-08 — run from the repo root):

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml test -pl gametime-app -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000 -DfailIfNoTests=false
```

```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install
```

The first runs the harness (~30s, 102 games) — `@EnabledIfSystemProperty` disabled
without `-Dcalibration=true`, so it never runs in a normal build. Vary
`-DcalibrationSeed` for the multi-seed discipline (#029 E). The second is the full
gate including JaCoCo coverage.

**The §3.13 baseline this pass starts from (5-seed mean):**
`117.0 pts / 46.6% FG / 36.6% 3P / 26.7 ast / 13.8 TO / 5.0 blk`, fouls
**19.0**/team/game, **foul-outs 0.388**, minutes `36.1 / 33.2 / 31.1 / …`.

**⚠️ THE STOP CONDITION IS INVERTED THIS PASS (#032 I) — read this before tuning.**
The budgeted cost is **+0.26 points/team/game** (0.35 technicals × 1 FT × ~75%)
against a per-seed noise band of **±1.5 points**. It is **below the noise floor**.
So the check is not "did points move too far" but its opposite: **if the §3.4
aggregates move measurably at 5 seeds, that is a BUG, not a calibration result** —
most likely the technical counter leaking into `getFouls()` (Step 2) or into the
bonus tally (Step 6). **Do not re-center anything** — §3.16 owns that and the
points/FG% targets are CONTESTED.

---

**Step 1 — the rate constant + the shared floor-free clamp helper (#032 B2, H).**
- [ ] Add **`TECHNICAL_FOULS_PER_TEAM_GAME`** to `SimConfig` — the **per-team-per-game
  rate** (~0.35, from the user's ~0.6–0.8 league-wide figure), **not** the per-check
  probability. This is the number a tuner reasons about and the one comparable to
  calibration.md.
- [ ] Add a helper deriving the **per-check probability** by dividing by the nominal
  check count (`DEFAULT_POSSESSIONS_PER_PERIOD × PERIODS × 2`). Expect **~0.0035**.
- [ ] **Document the nominal-vs-actual gap in the javadoc (#032 B2).** The real
  possession count is pace-scaled (`PossessionEngine.simulate()` blends both coaches'
  `paceMultiplier`) and OT adds more, so a fast game draws **slightly more** technicals
  than the constant nominally says. **This is correct behavior, not drift** — a harness
  landing a few percent off the constant is **not** a bug. Say so in the javadoc, or
  the next tuner will back-solve against noise.
- [ ] **Add the shared floor-free clamp helper** (`clampRareProbability` or similar,
  spelling open) beside `clampProbability`, and **route all four sites through it**:
  `rareEventProbability` (§3.10), `foulTroubleSitProbability` (§3.13),
  `FoulResolver.isFoul`'s floor-free multiply (§3.12), and the new technical rate.
- [ ] **`PROB_FLOOR` (0.02) must NOT apply** — it is roughly **6× the technical rate
  itself** (0.0035), so flooring would inflate technicals ~6-fold and make the constant
  tunable only upward (the failure `rareEventProbability`'s javadoc already documents).
- [ ] **This consolidation is behavior-neutral, and the proof is that §3.10's and
  §3.13's existing tests must stay green UNCHANGED.** If any of them move, the
  refactor is wrong. Do not "fix" the test.

**Step 2 — the separate counter + the derived ejection predicate on `PlayerGameState` (#032 E, F).**
- [ ] Add a **`technicalFouls` counter** with `recordTechnicalFoul()`, **separate from
  `fouls`**. A technical does **not** count toward the 6-foul limit.
- [ ] **Do NOT reuse `recordFoul()`.** Merging them would silently feed `isFouledOut()`,
  `foulTroubleLevel()`, and the §3.10 penalty count — moving **two §3.13-calibrated
  numbers** (foul-outs ~0.39 and the 4/5/6 distribution) for an unrelated cause.
  `getFouls()` must keep meaning exactly "personal fouls".
- [ ] Add **`TECHNICAL_EJECTION_LIMIT = 2`** to `SimConfig` and a **derived**
  `isEjected()` predicate — `technicalFouls >= TECHNICAL_EJECTION_LIMIT`.
- [ ] **NO stored flag.** This is a monotonic counter exactly like `fouls`, so #023 F's
  derive-don't-store discipline applies **unchanged**. The #028 D-style stored-state
  argument belongs to **§3.14b's flagrant-2**, which genuinely forces it — do not take
  the exception a phase early (#032 F).
- [ ] **Keep `PlayerGameState` RNG-free and coach-free**, as #031 E established.

**Step 3 — the technical roll + committer draw in `RotationState` (#032 B, C).**
- [ ] Roll the technical inside **`advancePossession(RandomGenerator)`** — one roll per
  team per call, so both teams can independently draw one in the same possession.
- [ ] **The draw must be UNCONDITIONAL and at a FIXED point**, for the same reason
  #031's is: a conditional draw forks the seed-pinned stream on rotation state. The
  natural site is beside §3.13's existing draw.
- [ ] On a hit, pick the committer by a **`foulProne`-weighted draw over the ON-FLOOR
  FIVE**, mirroring the weighted-selection shape used throughout (`pickDefender`,
  `pickFreeThrowShooter`).
- [ ] **The bench is excluded (#032 C).** Not realism — measurement bias: the bench pool
  is ~10 against the floor's 5, so ~2/3 of technicals would land on players who are not
  playing and whose ejections have **no engine consequence**.
- [ ] **Do NOT thread `aggression`/`composure`/`ego` into `PlayerGameState`.** They are
  **attributes**, and the engine consumes **skills** — `foulProne` already *is* that
  composite by construction (player.md:122: *"Aggression and reckless energy raise it;
  composure and awareness lower it"*). A raw-attribute path would be a second route to
  an influence already flowing (#013/#015).
- [ ] **Expect the committer draw to look near-uniform** — `foulProne` is nearly flat
  (sd **1.22** against a ~9.66 mean, #031 A), so a high-`foulProne` player is only
  ~2× likelier than a low one. **This is accepted (#032 C)**: the weighting moves no
  aggregate and nothing observable at 33–71 events per run. Do not "improve" it.
- [ ] **No clamp on the committer draw** — it is a weighted selection, not a
  probability. Every on-floor player with non-zero `foulProne` keeps a non-zero chance;
  no player is exempt by construction (#032 H).

**Step 4 — the event + the free throw (#032 D, G).**
- [ ] Emit a **`PlayType.FOUL`** event with the new `outcome` string **`TECHNICAL_FOUL`**
  (mirroring `SHOOTING_FOUL`/`AND_ONE`; verify non-collision with the §3.8/§3.9/§3.10
  vocabularies — the #027 D discipline). **No new `PlayType`.**
- [ ] **Populate `committingTeamId`** like every other `FOUL` event (#028 D), so the
  column stays uniform — even though Step 6 excludes the event from the bonus count.
- [ ] Award **exactly one** free throw to the **other** team, reusing `awardFreeThrows`.
  Pick the `FreeThrowSource` — **no new source type**; decide at execution whether an
  existing value fits or the outcome string alone carries it.
- [ ] **The shooter is a DETERMINISTIC "highest `freeThrows` among the on-floor five"
  pick — a SECOND function beside `pickFreeThrowShooter`, which stays UNTOUCHED (#032 G).**
  The existing draw weights by `foulDrawing` (modelling *being fouled*); nobody is
  fouled on a technical. **Do not merge them** — merging gets one of the two rules wrong.
- [ ] **Two consequences that look like bugs and are not**: it consumes **no RNG**, and
  **the same player shoots essentially every technical FT for his team all game**
  (changing only when the lineup changes). That is what the real rule produces. Say so
  in the javadoc so a later reader does not "fix" it.
- [ ] **The possession is UNCHANGED (#032 D).** The roll happens between possessions, so
  there is **nothing to fork** — no retention, no switch, even when the offense commits
  it. §3.14a adds **no branch to the possession path**.

**Step 5 — extend the ejection seam in `RotationState` (#032 F, #031 H).**
- [ ] Extend **`eligible(...)`** to filter `isFouledOut() || isEjected()`. This is the
  **single filter** every candidate pool passes through.
- [ ] **Do NOT add a fourth removal path.** §3.13 left a clean three-tier structure
  (hard/forced · soft/preference · fatigue); an ejection is unambiguously the **hard**
  tier and extends the existing filter (#031 H).
- [ ] Make sure an ejected player is **forced off** if he is on the floor, via the same
  hard-tier path `replaceFouledOut()` uses, and that the **never-below-5 last resort**
  still holds (#023 F) — if literally nobody is eligible, a player stays on.
- [ ] **Expect this to fire essentially never in the harness (#032 F).** Two technicals
  on the same player in one game is ~one occurrence every several simulated seasons, so
  **0.00 ejections on most seeds is a CORRECT result, not a failure.** Test it by
  **forcing the counter directly** — do not wait for the event.

**Step 6 — exclude technicals from the bonus tally (#032 E).**
- [ ] Add an **outcome-aware exclusion** in `GameData.isInBonus` so a `TECHNICAL_FOUL`
  event does **not** count toward `BONUS_FOULS_PER_PERIOD`. A technical does not put a
  team in the penalty.
- [ ] **This is #028 A1's first exception**, and it must be explicit rather than
  incidental: "one unified derivation over all `FOUL` events, not split per foul type"
  no longer holds literally. Note it in the javadoc — **any future foul type must now
  consciously decide whether it counts**, and §3.14b's flagrant is the immediate next
  case (**it does** count).
- [ ] Guard it with a test: technicals must not move the penalty rate (§3.13 landing:
  51.3% of team-periods).

**Step 7 — the harness instrument (#032 J).**
- [ ] Add a **technicals/team/game line** to `CalibrationHarness` (plus an ejection
  count, which will read 0.00 — decide at execution whether printing it earns its keep).
- [ ] **The existing foul-mix line does NOT suffice** — it breaks down *shooting* fouls
  by shot type, so a technical would never appear in it.
- [ ] **Judge this line at 5 SEEDS ONLY.** Computed resolvability: at 102 games a
  0.7/game rate gives ~71 events, relative sd **11.8%**; at 5 seeds ~357 events, **5.3%**.
  A single-seed reading **cannot resolve it**.
- [ ] Add the row to [calibration.md](calibration.md) as a **`ballpark`** (~0.6–0.8
  league-wide / ~0.3–0.4 per team) — **not a TARGET**: nothing is tuned toward it (the
  constant is set from it directly), which is not what calibration.md means by TARGET.
  **Update the harness `(target ~N)` string in the same change** (calibration.md's rule),
  and mark it **UNSOURCED** like every other row.

**Step 8 — docs, diagram, close-out.**
- [ ] Add the **implementation note** to `decisions.md` #032 recording any divergence
  and resolving the open-at-execution items (the final rate value, the clamp-helper and
  outcome-string spellings, the draw site, whether the ejection line prints).
- [ ] Update **`possession-flow.puml`** — **only the rotation-step block at the top.**
  §3.14a adds **no possession branch** (#032 D), and it is worth saying so **on the
  diagram** so a reader does not go looking for one. Validate with
  `plantuml -checkonly docs/possession-flow.puml`.
- [ ] Flip the **roadmap** §3.14a bullet to `[x]` with a landing note.
- [ ] **Retire the clamp-helper trigger** — #030's follow-up and the "⚠️ THE CLAMP-HELPER
  TRIGGER HAS FIRED" note below are **done** as of this pass (#032 H). A fifth floor-free
  site now costs one call, not a fourth copy.
- [ ] Update `player.md`'s "Between possessions" table with the technical-foul row.
- [ ] Rewrite this file for **§3.14b's design pass** (its open questions are below).

---

### Reconciliation invariants (must hold after §3.14a)

- **`getFouls()` is unchanged in meaning** — personal fouls only. Foul-outs (~0.39) and
  the 4/5/6 distribution must **not move**.
- **The penalty rate must not move** (51.3% of team-periods) — technicals are excluded.
- **The §3.4 aggregates must not move measurably at 5 seeds** — see the inverted stop
  condition above. Movement is a leak, not a landing.
- **FT/points reconciliation stays automatic** — `awardFreeThrows` is reused verbatim.
- **`onFloor()` is always exactly 5**, ejections included (the never-below-5 last resort).

### ⚠ Do NOT (guardrails carried into §3.14a)

- **Do NOT add a fourth removal path in `RotationState`** — ejection is the **hard**
  tier and extends `eligible(...)` (#031 H).
- **Do NOT take #023 F's stored-state exception this pass** — the two-technical
  ejection is derivable from a counter (#032 F). It belongs to §3.14b's flagrant-2.
- **Do NOT thread attributes into `PlayerGameState`** — the engine consumes skills;
  `foulProne` already carries aggression/composure (#032 C).
- **Do NOT re-tune §3.13's foul-trouble sit curve** — measured saturated (#031 note).
- **Do NOT trim `BASE_NO_BASKET_FOUL`** — a measured **wrong-way** lever (#028):
  trimming it *raises* points.
- **Do NOT re-open §3.12's `FOUL_MULT_*`** (settled on realism, #030 G) or touch
  `pickDefender`'s `individualDefense` weighting (#031 A).
- **Do NOT re-center points/FG%** — §3.16 owns it and the targets are CONTESTED
  ([calibration.md](calibration.md)).
- **Do NOT model *why* a technical occurs** — no game situation, no coach trait, no
  temperament. It is deliberately random (#032 B); making it situational needs the
  game-situation awareness the rotation step does not have (#031's follow-up).

---

## §3.14b — Flagrant fouls: the open questions for ITS design pass

**NOT execute-ready.** These become `decisions.md` #033. **Write no production code
in that session.** §3.14a (above) must ship first — §3.14b builds on its ejection seam.

**What the user has already specified** (carry into the design pass, do not re-litigate):
- A flagrant requires a foul to have occurred — **no change to the existing foul roll**.
  It is an **additional roll** on top: *was that foul a super-rare flagrant?*
- The penalty is **always two free throws**, and it **counts toward the 6-foul limit**
  (unlike a technical — #032 E's counter split does **not** apply).
- **Offensive player commits** → possession flips to the defense + 2 FTs.
- **Defensive player commits on a shooter, shot MISSES** → offense gets 2 FTs **and
  retains** the ball.
- **Defensive player commits on a shooter, shot GOES IN** → basket counts, shooter gets
  2 FTs, **offense retains**.
- Rate: **~0.25–0.40 per game** league-wide (5–10 per team per 82-game season).
- A **flagrant-2** is grounds for ejection, guarded the same way a foul-out is.

**The open questions:**

1. **POSSESSION RETENTION — the structural crux, and the reason §3.14b is the hard
   half.** A flagrant awards FTs **and** returns the ball. **Every existing FT path ends
   the possession (#030 B).** Does this reuse §3.7's block-recovery retain/return seam
   (`offenseRetains()` + the `continue` into the second-chance loop) or need its own
   fork? Does it respect `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`, or is a flagrant
   retention exempt from the second-chance cap?
2. **The flagrant-2 stored state — the GENUINE #023 F exception (#032 F).** Unlike the
   two-technical case, a flagrant-2 is a **severity grade with no counter behind it**,
   so it is **not derivable**. Argue the stored field explicitly the way **#028 D** argued
   its column, and say what makes it different from the `inFoulTrouble` flag #031 E
   refused. It extends **`eligible(...)`** — the seam §3.14a already built (#031 H).
3. **Flagrant-1 vs flagrant-2 — one grade or two?** Two FTs either way; the grade only
   decides ejection. Is a second severity roll worth the tuning surface, or does a single
   "flagrant" with a sub-rate for ejection suffice?
4. **Where the roll hangs.** Which of the existing foul sites can turn flagrant — the
   pre-shot stopped-shot foul, the and-1, the §3.10 rebounding foul, or all three? The
   and-1 case is the one that produces the "basket counts + 2 FTs + retain" path.
5. **Rate + calibration.** ~0.16 committed per team per game. **Resolvability, already
   computed (#032 J's method):** ~33 events at 102 games (relative sd **17.4%**), ~166 at
   5 seeds (**7.8%**) — **coarser than the technical line, and single-seed readings are
   useless.** Does it need its own harness line, or can it share §3.14a's?
6. **The points cost.** Budgeted at **+0.43/team/game** — FTs (+0.24) plus retention
   (+0.19, an upper bound assuming every flagrant is defensive *and* retained). Still
   below the ±1.5 noise band, so **the inverted stop condition (#032 I) likely applies
   again** — but retention is a second channel and should be re-priced, not assumed.
7. **Naming.** New `outcome` strings must not collide across phases (#027 D). Candidates
   mirror the established suffix discipline (`FLAGRANT_FOUL_*`?), and must sit alongside
   §3.14a's `TECHNICAL_FOUL` on the same `PlayType.FOUL`.

---

## Verified facts (the anchors §3.14a needs — confirmed against the code 2026-08, post-§3.13)

Paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`.
Trust these; re-check only if the code moved.

**How in-game stats are tracked (asked during the §3.14a design pass — the answer that
makes #032 E/F cheap):** **both, and events are authoritative.** `PlayerGameState`
(`sim/PlayerGameState.java:53-68`) holds **live per-player accumulators** — `points`,
`fouls`, `assists`, `blocks`, `steals`, `turnovers`, rebounds, FG/3P/FT attempts and
makes — incremented mid-game (`recordFoul()`, `recordAssist()`, …) and written to the
box score at the end. The persisted `GameEvent` log is the **source of truth** (#020),
and the box score is reconciled against it. **So a `technicalFouls` counter is a
twelfth accumulator on an object that already has eleven — not a new mechanism**, which
is exactly why the two-technical ejection stays derived (#032 F).

**The removal machinery §3.14a's ejections must extend (#031 H) — all of it:**
- `SimConfig.FOUL_OUT_LIMIT = 6` — the disqualification limit.
- `PlayerGameState.isFouledOut()` — a **derived predicate** over the `fouls` counter
  (#023 F). **Note: it is `isFouledOut()`, not `hasFouledOut()`.**
- `PlayerGameState.foulTroubleLevel()` — §3.13's derived rotation input. Also derived.
- `PlayerGameState.recordFoul()` / `getFouls()` — the single **personal-foul** counter
  (shooting, and-1, rebounding). **§3.14a does NOT join it** (#032 E).
- `RotationState.advancePossession(RandomGenerator)` — the three tiers, in order:
  drain/recover → `replaceFouledOut()` (**hard**) → `runFoulTroubleSub()` (**soft**) →
  `runFatigueSubs()` (**fatigue**). Consumes **exactly one unconditional draw per call**.
- **`RotationState.eligible(...)` (`RotationState.java:264`) — the single filter every
  candidate pool passes through.** It filters `isFouledOut()` today; **this is the seam
  the ejection extends** (#031 H).
- `RotationState.replaceFouledOut()` (`:211`) — forces off every on-floor player at the
  limit, replacing from the **full** bench. If no eligible replacement exists the player
  **stays on** (the never-below-5 last resort).
- `RotationState.benchWithinDepth()` (`:291`) — the `rotationDepth` window the **soft**
  rule draws from.
- `PossessionEngine.simulate()` calls `home.rotation().advancePossession(rng)` then
  `away.rotation().advancePossession(rng)` once per possession, before
  `resolvePossession()`.

**The free-throw + foul machinery §3.14a reuses:**
- `PossessionEngine.awardFreeThrows(...)` — **the single FT block, shared by all three
  situations** (shooting foul, §3.10 bonus, §3.11 and-1) since #028 B. Takes `count` and
  `source` as parameters (#029 D). **Reuse it verbatim** — FT/points reconciliation is
  automatic.
- `PossessionEngine.pickFreeThrowShooter(...)` — the **`foulDrawing`-weighted** draw
  (#028 B). **§3.14a leaves it UNTOUCHED** and adds a second, deterministic rule (#032 G).
- `SimConfig.FREE_THROWS_PER_FOUL = 2`, `AND_ONE_FREE_THROWS = 1`. A technical is **1**.
- `GameData.isInBonus(committingTeamId, period)` — the #028 A1 derivation over `FOUL`
  events. **Step 6 gives it its first outcome-aware exclusion.**
- `data.addEvent(off, def, period, seq, PlayType.FOUL, outcome, playerId, null, committingTeamId)`
  — the 9-arg overload carrying `committingTeamId` (#028 D).

**The clamp sites Step 1 consolidates (#030's trigger, fired at §3.13, fourth here):**
- `SimConfig.clampProbability` (`:710`) — `max(PROB_FLOOR, min(PROB_CEILING, p))`. The
  **normal** helper. `PROB_FLOOR = 0.02`, `PROB_CEILING = 0.97` (`:122-123`).
- `SimConfig.rareEventProbability` (`:729`) — hand-rolled `max(0.0, min(PROB_CEILING, p))`.
- `SimConfig.foulTroubleSitProbability` (`:674`) — the **same expression**, hand-rolled
  again. Its javadoc (`:655-658`) explains why the floor is wrong for rare events.
- `FoulResolver.isFoul` — the §3.12 floor-free multiply.

**Where the fouls actually come from (#031 A's mechanism — still true, still off limits):**
- `ShotSelector.pickDefender(...)` — draws the defender **weighted by
  `individualDefense`** (sd **4.11** across 359 players). The concentration driver.
- `FoulResolver.isFoul(...)` — scales that defender's foul probability by their own
  `foulProne` (sd **1.22** — nearly flat; the number that makes §3.14a's committer draw
  near-uniform, and why that is affordable).
- `FoulResolver.pickCommitter(...)` — the §3.10 rebounding-foul committer draw, weighted
  by `foulProne` alone. **The closest existing analogue to §3.14a's Step 3 draw.**

**Attributes vs. skills (settled in #032 C — the correction that shaped the pass):**
- `PlayerGameState`'s constructor reads **only** `player.getSkills()` (the 23 skills).
  **It has never read `player.getAttributes()`.**
- `aggression`, `composure`, and `ego` **do exist** — as **attributes** (player.md:24/28/30,
  and the OpenAPI spec). They reach the engine **only** through the calculators that
  derive skills from them (player.md:255).
- **`foulProne` is documented as exactly that composite** (player.md:122): *"Aggression
  and reckless energy raise it; composure and awareness lower it."*

**The coach attributes (#018) — all five:** `pace`, `offensiveScheme`, `defensiveScheme`,
`rotationDepth`, `substitutionAggressiveness`. **There is NO coach `acumen`.**
**§3.14a adds none and reads none** — the technical rate is coach-independent (#032 B).

**The measurement:**
- `CalibrationHarness` prints the §3.4 aggregates, the §3.5 per-slot minutes line, the
  foul mix, foul-outs/team/game and the 4/5/6 distribution. **Step 7 adds the technicals
  line — the foul-mix line will NOT show them** (it is per-shot-type shooting fouls).
- Steer by **multiple `-DcalibrationSeed` runs to the MEAN** (#029 E), never one. For the
  technicals line specifically, **5 seeds is the minimum that resolves it** (#032 J).

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **§3.14b (flagrants)** → its open questions are **in this file** (above) because it is
  the immediate next design pass; the roadmap bullet is the durable home.
- **Bench and coach technicals** → #032's follow-up. Not modelled (#032 C): a coach is
  not a `PlayerGameState`, so a coach technical would be a committer-less FT.
- **A technical's total lack of causal input** → #032's follow-up. Making it situational
  needs the **same** missing prerequisite as every parked strategic-sub idea:
  game-situation awareness in the rotation step (#031's follow-up, [ideas.md](ideas.md)).
- **The `technicalFouls` counter is not surfaced** (box score, OpenAPI, API) → #032's
  follow-up. No consumer yet (#014/#017); additive when one appears, exactly like
  `committing_team_id`'s parked state.
- **Period-/time-aware foul trouble** → #031 C's follow-up, deferred by §3.13.
- **Getting foul-outs below ~0.38** → #031's follow-up. The lever is **measured
  saturated**; none of the three remaining candidates belong to §3.14.
- **Splitting `substitutionAggressiveness`** → #031 B's follow-up.
- **The over-dispersion finding (#031 A)** → #031's follow-up: the same `pickDefender`
  concentration applies to **blocks, steals, and shot contests**.
- **Strategic substitution as a category** → [ideas.md](ideas.md) (parked, not planned).
- **§3.15 (`SimConfig` profiles)** → roadmap.md. The design-pass input is in
  [backlog.md](backlog.md). **Its validation gate is reproducing §3.14b's landing
  exactly** — which is part of why the clamp consolidation was folded into §3.14a
  instead (#032 H).
- **§3.16 (recalibration against verified targets)** → roadmap.md, the last Phase-3
  sub-phase. Owns the contested points/FG% targets. Its **prerequisite is a backlog
  chore**: verify the benchmarks with real sources — including §3.13's unsourced
  foul-out figure **and now §3.14a's unsourced technicals ballpark** (#032 J).
- **Calibration targets** → [calibration.md](calibration.md), **the source of truth**.
  Update it *and* the `CalibrationHarness` `(target ~N)` strings together.
- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star tuning,
  the `decisions.md` condense pass, harness self-verification) → [backlog.md](backlog.md).
- **Untriaged future-improvement ideas** → [ideas.md](ideas.md). *(Includes the parked
  cap 3→5 tuning idea — do NOT touch `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` without its
  own recalibration pass.)*
- **§3.6 seams left open by #024**: play-by-play pagination + a `period` filter; the
  per-event time column's storage shape; a lean header-only `GameResult` projection.
- **§3.7 seams left open by #025**: a skilled-blocker recovery edge; shot-clock pressure
  on block-recovered second-chance possessions (→ §3.9-E, still parked #027 D).
- **§3.9 seams left open by #027**: §3.7-E shot-clock pressure; finer turnover sub-types.
- **§3.10/§3.11 seams left open by #028/#029**: `committing_team_id` is **populated and
  queryable but not surfaced on the OpenAPI `GameEvent`**; the observed off/def
  rebounding-foul split (**78/22**) drifts from the configured 75/25.
- **§3.12 seams left open by #030**: the engine shoots **~20 3PA/team/game against the
  NBA's ~35** — a §3.16 input.
- **✅ THE CLAMP-HELPER CONSOLIDATION IS SCHEDULED** — #030 set the trigger (two sites is
  coincidence, a third is the signal); §3.13 added the third; **§3.14a adds the fourth and
  folds in the consolidation** (#032 H, Step 1). Retire this note at close-out.
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true -DcalibrationSeed=NNNN`) stays in the `sim` test sources.
  Re-run it after any `SimConfig` change.
