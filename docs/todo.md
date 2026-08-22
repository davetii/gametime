# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.19).

Current focus: **§3.18 — the steal as a first-class event**. Phase 3's tail is
**§3.17 shot mix (SHIPPED) → §3.18 steals → §3.19 recalibration**
(`decisions.md` **#036**, resequenced by **#038**). ⚠ **The old "§3.16 =
recalibration" is now §3.19** — see #038 for the current mapping. §3.7–§3.13,
§3.14a, §3.14b, §3.15, §3.16 and **§3.17** have all shipped.

> **⚠ §3.18 NEEDS A DESIGN PASS FIRST.** There is no `#NNN` for it yet. Resolve the
> open questions below into a numbered `decisions.md` entry **plus** an execute-ready
> plan in this file, and **write no production code in that session**. The roadmap
> bullet is a **seam, not a plan** — every phase so far has found real design questions
> the one-liner hid.
>
> **The gap, in one line:** a steal is the **only contested defensive play the event
> log does not attribute**. `PossessionEngine` picks the stealer (`pickStealer`),
> credits the box score (`recordSteal()`), and then **drops them** — the emitted
> `TURNOVER` event carries `primaryPlayerId = shooter`, *the player who LOST the ball*.
> An assist rides `assistPlayerId`; a block gets its own `BLOCKED_*` event and a
> reconciliation invariant. **A steal gets neither.**

---

## §3.18 — open questions for the design pass

1. **Does the stealer ride the existing `TURNOVER` event, or get its own event?**
   The block precedent (§3.7, #025 F1) gave the play its own `BLOCKED_*` SHOT event;
   the assist precedent rode an existing event via a second participant column. ⚠ **A
   `TURNOVER` event already exists for every steal** — a second event would double-count
   unless the vocabulary makes the pair explicit, and the harness's turnover-cause mix
   reads `STOLEN` off the outcome today.
2. **Is it worth a schema change?** ⚠ **Every sub-phase since §3.10 has avoided one**,
   and §3.17 explicitly declined a migration for the `NON_SHOOTING_FOUL` rename (#040 N).
   `GameEventEntity` has `primaryPlayerId`, `assistPlayerId` and `committingTeamId` —
   **check whether `assistPlayerId` can carry the stealer**, or whether that overloads a
   column whose javadoc says "null on every non-SHOT event". Check #028 D's
   `committing_team_id` precedent **and its still-open OpenAPI follow-up**.
3. **What reconciliation invariant does it buy?** §3.7 got
   `count(BLOCKED_* events) == sum(BoxScore.blocks)`. The steal equivalent is
   `count(STOLEN events crediting player X) == BoxScore.steals(X)` — ⚠ **note a
   count-based version already works today** (`STOLEN` events vs total box-score steals);
   what is missing is the **per-creditor** check.
4. **Does the harness need a steals row first?** ⚠ **It still prints none** — a
   backlog chore, test-only, no engine change. calibration.md carries steals as
   **8.4 observed** against an engine ~7.67 **by derivation only**. **Do the cheap
   measurement before designing the phase**: it may show the rate is fine and this is
   pure parity work, which changes the phase's shape.
5. **Does anything else read `primaryPlayerId` on a `TURNOVER` event** and assume it is
   the loser? Grep before changing the meaning of that column for one cause.

---

### ⚠ Two traps that bite EVERY session — read these before trusting a doc

**1. SUB-PHASE NUMBERS HAVE BEEN REUSED.** Recalibration was §3.16, then briefly
§3.18, and is now **§3.19** (#038). **Every "§3.16" written before 2026-08 means
RECALIBRATION.** Read the phase NAME, never the number alone.

**2. A MECHANIC CHANGE CAN SILENTLY BREAK AN INSTRUMENT, and no test will catch it.**
⚠ **§3.17 is now the worked example of BOTH halves of this trap**, and it is worth
reading before touching the harness:
- §3.16 broke the fouled-three rows by changing what a foul *awards*; §3.17 Step 0
  fixed them (#040 G) — but **the fix itself moved a number**: `Stopped shots / team`
  reads **12.43** post against **7.65** pre and looks doubled. **It fell** — 7.39 → 6.24
  like-for-like on the raw visible tally. **Always ask "did the engine change, or did
  the measurement?" before tuning anything.**
- ⚠ **§3.17 also found a CLAMP doing what a constant appeared to do**: `PROB_FLOOR`
  (0.02) is 4× `sim.base-block-three` (0.005), so blocks did not fall when the mix went
  three-heavy and **`base-block-three` is inert**. A predicted number that does not move
  is as informative as one that moves wrongly. See backlog.md.

---

### Verified facts (measured 2026-08 against the tree — for whoever designs §3.18)

- **Post-§3.17 baseline (5 seeds, 102 games each, `local,baseline`)**: points **110.0** ·
  FG% **43.5** · 3P% **35.8** · FGA **92.28** · **3PA 37.22** · ast 26.1 · TO **13.9** ·
  FTA **19.76** · fouls **18.18** · blocks 4.80 · off reb 11.18 · def reb 30.48 ·
  foul-outs 0.304 · penalty **46.1%** · flagrants 0.141 · technicals 0.358.
  ⚠ **FG%, FGA and FTA are all §3.19's and all land worse ON PURPOSE** (#040 E/F) —
  do not read them as drift, and do not tune them in §3.18.
- **The harness needs the profile in the ENVIRONMENT**, not on the mvn command line.
  `-Dspring.profiles.active=...` does **not** reach the forked surefire JVM. Use:
  ```
  SPRING_PROFILES_ACTIVE=local,baseline JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -pl gametime-app test -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000 -DfailIfNoTests=false
  ```
  Run it from `gametime-service/`. **Confirm the report's `Profiles:` line** before
  trusting any number. Judge at **5 seeds** (1000–5000).
- **`SimConfig` now carries 62 tunables and 27 statics.** The count lives in FOUR places
  and all four move together: `SimConfig`'s header comment, `baseline()`'s javadoc and
  its error message, and `SimConfigProfileBindingTest.EXPECTED_TUNABLE` (which asserts
  **both** the constructor arity and the instance-field count).
- **The steal path**: `PossessionEngine:174–181` — `pickStealer` chooses, `recordSteal()`
  credits, and the `TURNOVER` event's `primaryPlayerId` is the **shooter**.
- **`PERSONAL_FOULS_PER_TEAM_GAME = 17.82`** (§3.17 re-measured it from 20.15). ⚠ It is a
  **shot-mix-derived** quantity now — any pass that moves the foul rate must re-measure
  it in writing (#034 G).

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved out
so this file can be rewritten each phase without losing it. **This is a ROUTING TABLE —
pointers only.** The reasoning lives at the destination; do not restate it here, and do
not add "done" entries (those are the `#NNN` entry's job).

**Deferred engine work — see the cited entry's follow-up list:**

- **Technicals** — bench/coach technicals; a technical's lack of causal input → **#032**
- **Foul trouble** — period-/time-awareness (#031 C); getting foul-outs below ~0.38
  (lever measured saturated); splitting `substitutionAggressiveness` (#031 B) → **#031**
- **Over-dispersion** — `pickDefender` concentration also reaches blocks, steals and
  shot contests → **#031 A**
- **Seams left open by their design pass** → §3.6 **#024** · §3.7 **#025** · §3.9
  **#027** · §3.10/§3.11 **#028/#029** (incl. `committing_team_id` not on the OpenAPI
  `GameEvent` — ⚠ **§3.18 will likely reopen this**) · §3.12 **#030** (~20 3PA/team/game
  — ✅ **CLOSED by §3.17**, landed 37.22) · §3.16 **#039** (the unsourced shooting-foul
  share — ⚠ now also **mispriced**, see #040) · **§3.17 #040** (FG%/2P%, FGA, FTA and
  the `PROB_FLOOR`-vs-`base-block-three` finding — all **§3.19's**)

**Other files own these outright:**

- **§3.19 (recalibration)**, and the phase sequence generally → [roadmap.md](roadmap.md)
- **Calibration targets** → [calibration.md](calibration.md), **the source of truth**.
  Update it *and* the `CalibrationHarness` `(target ~N)` strings together.
- **Infra/tooling/data-hygiene chores**, and **the `decisions.md` condense pass** (a
  **gate on starting Phase 4**) → [backlog.md](backlog.md)
- **Untriaged ideas**, incl. the parked cap 3→5 tuning idea and strategic substitution →
  [ideas.md](ideas.md)

**Standing facts (true every phase, not deferrals):**

- `CalibrationHarness` is disabled by default and lives in the `sim` test sources; run
  it with `-Dcalibration=true -DcalibrationSeed=NNNN`. **Re-run after any `SimConfig`
  change.** Since §3.15 the sim profile is selected by the ordinary Spring profile list —
  `SPRING_PROFILES_ACTIVE=local,baseline` (#035 F — ⚠ **the `-D` form does NOT reach the
  forked surefire JVM**). ⚠ **`baseline` must always
  be in the list** (it carries all **62** values), and **order is positional** — putting it
  last lets it win and silently neutralizes the era file. The **effective-config dump**
  in the report is what catches that, and it also catches a caller passing its own copy
  of a profilable value (the §3.15 harness-pace trap).
- **Do NOT touch `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`'s baseline value** without its
  own recalibration pass (#034 B) — though §3.15 makes it *profilable* (#035 C).
