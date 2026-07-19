# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.11).

Current focus: **§3.8 — Missed shot out of bounds (no rebound)**. The full
§3.7–§3.11 sequence, the calibration-blast-radius ordering, and what follows
(Phase 4) live in **roadmap.md's "Possession-fidelity completion" section** — not
here (todo.md is current-phase-only). §3.7 shipped; this file holds the §3.8
execution plan below; when §3.8 ships it gets rewritten to §3.9's plan.

> **§3.8 design pass DONE — resolved as decisions.md #026 (A–E). Execute-ready.**
> The design is settled; the plan below is the execution session. Locked: **both**
> sail-out and tipped-OOB in scope; **A** one skill-weighted **four-way** missed-shot
> outcome (off reb / def reb / OOB-offense / OOB-defense) in a single draw — no
> separate "is it OOB?" sub-decision, OOB slices a fixed **defense-leaning** lean;
> **B** a new **`MissedShotResolver`** that **WRAPS** `ReboundResolver` (unchanged,
> still credits a rebounder on its two outcomes) and owns the four-way result;
> **C** skill-weighted (a real board contest) — the deliberate difference from
> §3.7's flat `BlockResolver`; **D** sail-out is **verified** harness-neutral, not
> assumed (add an OOB harness line, run it, recalibrate only if the aggregates
> move); **E** OOB **credits no rebounder** and must NOT be counted in the rebound
> reconciliation invariant. **No schema change** (`outcome` is free text, #020).
> Flow: `possession-flow.puml` — the OOB four-way branch is drawn there now,
> DESIGNED-tagged (as §3.7's block fork was); close-out flips the tag to built.

---

## Verified facts (confirmed against the code 2026-07, post-§3.7 — paths under `gametime-service/gametime-app/src/main/java/software/daveturner/gametime/`)

Trust these; re-check only if the code moved. All engine code is in the `sim/` package.

**The rebound branch to replace — `sim/PossessionEngine.java`:**
- The `// 4. Rebound` branch is at **~line 212** (it moved down when the §3.7 block
  fork landed at the top of `// 3. Shot`). Today it: picks an offensive rebounder
  (`pickOffensiveRebounder`, ~213) + defensive rebounder (`pickDefensiveRebounder`,
  ~214), checks the cap (`offensiveRebounds >= MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`,
  ~215), rolls `isOffensiveRebound` (~217), then **two branches**: `OFFENSIVE` →
  `recordOffensiveRebound()` + `REBOUND`/`OFFENSIVE` event + `offensiveRebounds++` +
  loop (~219–225); else `DEFENSIVE` → `recordDefensiveRebound()` + `REBOUND`/
  `DEFENSIVE` event + `return sequence + 1` (~226–231). **This whole block becomes a
  single `missedShotResolver` call + a four-way fork.**
- **The retain/return fork shape to reuse** is the §3.7 block recovery (the block
  fork in `// 3. Shot`): a retained outcome does `offensiveRebounds++` + `continue`
  (re-runs from the top = ShotSelector), respecting the cap; a possession-ending
  outcome does `return sequence + 1`. The §3.8 fork is identical, over the four-way
  `MissedShotResolver` result instead of the block recovery.
- The **cap** (`MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`, `SimConfig`) still bounds the
  second-chance loop: on the two **offense-retained** outcomes (`OFFENSIVE` rebound,
  OOB-offense), once the cap is hit the outcome must be **forced to a
  possession-ending one** (as `capReached` already forces a defensive rebound today).
- **Event emission:** `data.addEvent(offTeamId, defTeamId, period, sequence, PlayType,
  outcome, primaryPlayerId)` (7-arg — OOB carries no assist, like a rebound).
- **Injected resolvers** are constructor fields (`reboundResolver`, `blockResolver`,
  … ~lines 12–27). Add `missedShotResolver` the same way. Note the hand-written test
  ctor in `PossessionEngineTest` (~line 20) also needs the new arg (as it did for
  `blockResolver`).

**The resolver to WRAP — `sim/ReboundResolver.java`** (leave UNCHANGED):
- `@Component` (line 16). Public surface `MissedShotResolver` will call:
  `isOffensiveRebound(offRebounder, defRebounder, rng)` (~29), `pickOffensiveRebounder
  (offense, rng)` (~42), `pickDefensiveRebounder(defense, rng)` (~48). It stays a pure
  two-way skill contest that **always credits a rebounder** on its two outcomes.

**The §3.7 template to mirror — `sim/BlockResolver.java` + `sim/BlockRecovery.java`:**
- `BlockResolver` is a flat four-way `@Component` (a weighted roll over `SimConfig`
  weights, normalized by their sum). `MissedShotResolver` is the **skill-weighted**
  analogue — the ONE deliberate difference (#026 C): it wraps `ReboundResolver`'s
  skill contest for the rebound-vs-rebound balance, then applies the flat OOB lean.
- `BlockRecovery` is the four-value result enum with an `offenseRetains()` predicate
  the engine forks on. The §3.8 result enum mirrors it (see step 3).

**`sim/GameData.java` / reconciliation precedent:** `GameSimulatorIntegrationTest`
reconciles `count(REBOUND OFFENSIVE/DEFENSIVE) == Σ box (off+def) rebounds` (#020/#022).
**The §3.8 OOB outcomes must NOT break this** — an OOB event credits no rebounder, so
it must be excluded from both sides (Decision E). Extend the test with an OOB-specific
assertion (OOB events exist; they credit no rebound; the rebound invariant still holds).

**`sim/SimConfig.java`:** all base rates + weights live here (e.g. `BASE_OFFENSIVE_REBOUND`,
the §3.7 `BLOCK_*` recovery weights). Add the OOB lean weights here.

**`test/.../sim/CalibrationHarness.java`:** the aggregate-print block is ~lines 210–233
(now includes the §3.7 `Blocks / team / game` line). **Add an `OOB / team / game` line**
there (sum the OOB events per team-game), so the rate is visible for the Decision-D check.

---

## §3.8 execution plan (work in order) — decisions.md #026

> All Maven commands set `JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`
> (JDK 21). Per-package coverage gate (80% line, target ~90%) only at
> `mvn -f gametime-service/pom.xml clean install`. Engine work in the `sim` package
> — no OpenAPI/schema change. `CalibrationHarness` is disabled by default
> (`-Dcalibration=true`).

1. **Resolve the OOB event representation (#026 E — the one open-at-execution call).**
   - [ ] Decide the OOB `outcome` strings against the rebound reconciliation
     invariant (OOB must not read as a rebound). Leading candidate: `REBOUND`
     `PlayType` with `outcome` `OUT_OF_BOUNDS_OFFENSE` / `OUT_OF_BOUNDS_DEFENSE`,
     crediting **no** rebounder (mirrors #025 F's reuse-a-PlayType discipline + the
     `BlockResolver` `OOB_*` naming). Decide whether **sail-out vs. tipped-OOB** are
     distinguishable in the event or share one OOB outcome each (offense/defense).
     Record the choice in the step-6 close-out (game.md vocabulary) and, if it
     diverged from the candidate, as an implementation note on #026.
2. **`SimConfig` OOB lean weights (#026 A).**
   - [ ] Add the defense-leaning OOB weights (placeholders; the two OOB slices are
     smaller than the two rebound outcomes, and the OOB split favors defense). These
     lean the four-way draw toward OOB-defense over OOB-offense. Seed reasonable
     values; the harness (step 5) confirms the rate.
3. **`MissedShotResolver` (new `sim` @Component) + result enum (#026 A, B, C).**
   - [ ] New result enum (mirror `BlockRecovery`): the four outcomes
     (`OFFENSIVE_REBOUND` / `DEFENSIVE_REBOUND` / `OOB_OFFENSE` / `OOB_DEFENSE`) with
     an `offenseRetains()` predicate the engine forks on.
   - [ ] `MissedShotResolver` **wraps** `ReboundResolver`, returning one four-way
     result + the picked rebounder. **Mechanism (resolve the ambiguity in "carve"):**
     the two OOB slices carry a **fixed defensive lean INDEPENDENT of who won the
     board** (#026 A: "a fixed defense-leaning lean, NOT a separate OOB
     sub-decision"). Do **NOT** implement it as "run `isOffensiveRebound`, then flip
     some results to OOB on the *same* side" — that would make OOB inherit the
     skill-decided side (a dominant offensive rebounder's OOBs skewing offense),
     which contradicts the design. Instead: **first split off OOB vs. clean-rebound
     by the flat OOB-lean weights** (a small fixed share goes OOB, itself split
     defense-leaning by the `SimConfig` weights from step 2, skill-independent);
     **only on the clean-rebound branch** run `ReboundResolver`'s skill contest
     (`isOffensiveRebound` + `pickOffensive/DefensiveRebounder`) to decide
     OFFENSIVE vs DEFENSIVE. Net: the rebound-vs-rebound balance is skill-weighted
     (the #026 C difference from flat `BlockResolver`), while the OOB slices are flat
     + defense-leaning. `ReboundResolver` stays UNCHANGED. Return the picked rebounder
     so the engine credits it ONLY on the two rebound outcomes, never on OOB (E).
     *(If calibration later shows the OOB slices should scale with anything, that's a
     future tweak — seed them flat per #026 A, like the §3.7 recovery weights.)*
4. **Wire into `PossessionEngine` (#026 B, E).**
   - [ ] Inject `missedShotResolver` (constructor field; update the
     `PossessionEngineTest` ctor too).
   - [ ] Replace the `// 4. Rebound` block (~212–231) with a single
     `missedShotResolver` call + the four-way fork, reusing the §3.7 retain/return
     shape: on the two **retained** outcomes (`OFFENSIVE_REBOUND`, `OOB_OFFENSE`) do
     `offensiveRebounds++` + `continue`, cap-respecting (force a possession-ending
     outcome once the cap is hit); on the two **ending** outcomes
     (`DEFENSIVE_REBOUND`, `OOB_DEFENSE`) `return sequence + 1`. Credit the rebounder
     (`recordOffensiveRebound`/`recordDefensiveRebound`) ONLY on the two rebound
     outcomes; emit the `REBOUND`/`OFFENSIVE`|`DEFENSIVE` event as today, and the new
     OOB event (from step 1) with NO rebounder credit on the OOB outcomes.
   - [ ] **`MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` — now THREE retention paths, ONE
     cap.** The cap bounds the second-chance `while(true)` loop (a possession can't
     loop forever on offense retentions). §3.8 does NOT remove the cap — it **adds a
     third offense-retention path** (`OOB_OFFENSE`) alongside the existing offensive
     rebound (`SimConfig` line ~215 today) and the §3.7 offense-recovered block
     (~172). All three `offensiveRebounds++` + `continue`, so **all three must count
     against the same cap** or the loop's bound leaks. The cap lives in the
     **`PossessionEngine` loop, not in any resolver** (one place owns loop
     termination). **Recommended structuring** (execution-time, not a #026 change):
     pass the `capReached` flag INTO `missedShotResolver` so it simply never returns a
     retained outcome when capped (forcing `DEFENSIVE_REBOUND`/`OOB_DEFENSE`) — this
     keeps the engine's four-way fork uniform and tidies the now-scattered "force an
     ending outcome when capped" logic (today duplicated at the block fork + the
     rebound branch) into the resolver, with the loop still owning the `continue`
     vs `return`. Assert the cap holds across ALL retention paths in step 5.
     **Keep the cap VALUE at 3** — do NOT change it in §3.8. Raising it (3→5, a
     realism idea for long put-back scrambles) moves the aggregates and needs its own
     recalibration pass; it is deliberately parked in `ideas.md` so §3.8's sail-out
     harness check (step 6) is verified against a known-good baseline, one moving knob
     at a time. §3.8 only makes OOB-offense *count against* the existing cap, it does
     not retune it.
5. **Tests (target ~90% per package).**
   - [ ] `MissedShotResolver` unit tests: all four outcomes reachable; the rebound
     balance still tracks skill (elite off rebounders → more `OFFENSIVE_REBOUND`);
     OOB slices are defense-leaning and skill-independent in their OOB-vs-OOB split;
     no rebounder credited on OOB outcomes.
   - [ ] `PossessionEngine` / integration: an OOB event ends or continues the
     possession correctly; OOB-offense runs a second chance (counter bumps, cap
     respected); OOB credits no rebounder. **Cap test covers all THREE retention
     paths** — a single possession never exceeds `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION`
     retentions summed over offensive rebounds + offense-recovered blocks +
     OOB-offense (extend the existing `offensiveReboundsCappedPerPossession`-style
     test to count all three, not just rebounds).
   - [ ] **Reconciliation** (extend #020/#022): OOB events exist AND
     `count(REBOUND OFFENSIVE/DEFENSIVE) == Σ box (off+def) rebounds` STILL holds
     (OOB excluded from both sides — Decision E).
6. **Verify harness-neutrality (#026 D — the sail-out check, interactive).**
   - [ ] Add an `OOB / team / game` line to `CalibrationHarness` (~lines 210–233,
     next to the Blocks line; sum the OOB events per team-game).
   - [ ] Run `-Dcalibration=true`; **confirm** the §3.4/§3.5 aggregates
     (~112 / 47% / 36% / 26 / 14) and rebound totals still hold with OOB on. If
     sail-out dragged them, do a small recalibration pass (nudge shot/rebound base
     rates) and **re-agree the aggregates with the user** — same loop as §3.7. If
     they held, record "§3.8 confirmed free (harness-neutral)".
7. **Gate.**
   - [ ] `JAVA_HOME=…/21.0.9-tem mvn -f gametime-service/pom.xml clean install` green.
8. **Docs + close-out** *(shipped-reality docs — do these ONLY after the code works).*
   - [ ] **Update `game.md`** — add the OOB `outcome` rows to the `play_type`/
     `outcome` vocabulary table (from step 1), and add the OOB branch to the
     possession-flow prose + the pattern list (mirror how §3.7 added the `BLOCKED_*`
     rows + block patterns). Note OOB credits no rebounder.
   - [ ] **`player.md`** — likely **no change** (OOB reuses the existing
     `offenseRebound`/`defenseRebound` contest; no new skill wired). Confirm and skip
     if so; only touch if an attribute mapping actually changed.
   - [ ] **`roster.md`** — **no change** (roster/lineup domain, unaffected by OOB).
   - [ ] `possession-flow.puml` — the OOB four-way branch was added in the design
     pass (DESIGNED-tagged, as §3.7's block fork was). At close-out just **flip the
     "§3.8 DESIGNED not built" tags to built** (title + `MissedShotResolver`
     partition + legend), and reconcile the placeholder outcome strings
     (`OUT_OF_BOUNDS_OFFENSE`/`_DEFENSE`) with whatever step 1 finalized.
   - [ ] roadmap.md §3.8: check the box + add a "Shipped" note (mirror §3.7's), and
     record whether it landed **free** or needed a recalibration pass (Decision D).
   - [ ] Verify #026 matches the shipped code; add an implementation note if execution
     diverged (as #023/#024/#025 did).
   - [ ] Reset this file's focus to **§3.9 — Turnover sub-categories** (the next
     free/no-recalibration item per roadmap's ordering); strip the completed §3.8 plan.

> **What's next after §3.8** (§3.9–§3.11, then Phase 4) and the
> calibration-blast-radius ordering live in **roadmap.md's "Possession-fidelity
> completion" section** — not duplicated here, per current-phase-only.

---

## Where deferred work lives (not here)

todo.md is **current-phase-only**. Work that outlives the current phase has moved
out so this file can be rewritten each phase without losing it:

- **Infra/tooling/data-hygiene chores** (Testcontainers, seed-data split, star
  tuning) → [backlog.md](backlog.md).
- **Deferred gameplay realism** (richer turnovers, rebounding fouls, and-1) →
  **numbered sub-phases §3.9–§3.11** in roadmap.md ("Possession-fidelity
  completion"), scheduled before Phase 4. Each is its own design-pass + execution.
- **Untriaged future-improvement ideas** (no phase home, not chores) →
  [ideas.md](ideas.md).
- **§3.6 seams left open by #024** (carry into Phase 4/7): play-by-play pagination
  + a `period` filter (Decision D); the per-event time column's storage shape
  (Decision E — decided by the Phase 7 game view); a lean header-only `GameResult`
  projection (Decision A).
- **§3.7 seams left open by #025** (carry forward): a skilled-blocker recovery edge
  (Decision D — additive if a consumer ever wants it); shot-clock pressure on
  block-recovered second-chance possessions (Decision E cross-ref → §3.9).
- **Calibration harness** — `CalibrationHarness` (disabled-by-default, run with
  `-Dcalibration=true`) stays in the `sim` test sources; it reports the §3.4
  aggregates + the §3.5 minutes/period-FG% distributions + the §3.7 blocks line
  (+ the §3.8 OOB line, once added). Re-run it after any `SimConfig` change.
