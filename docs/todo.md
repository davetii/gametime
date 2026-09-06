# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section.

Current focus: **§3.22 — the putback**, ✅ **EXECUTE-READY — design resolved as
[decisions.md](decisions.md) #044 (A–I), 2026-09.**
⚠ **RECALIBRATION WAS RENUMBERED FOUR TIMES** — §3.16 → §3.18 → §3.19 → **§3.20**.
**Read the phase NAME, never the number alone.** §3.7–§3.21 have all shipped.

> ✅ **§3.22 IS EXECUTE-READY.** Build the plan below exactly; add an implementation note
> to #044 recording any divergence; flip the roadmap bullet. **The design pass already ran
> the mechanic in a temporary tree and measured its landing** — execution reproduces the
> table in the preamble, it does not discover one. ⚠ **Two premises were corrected by
> measurement (#044 Step 0): the harness is NOT five identical players, and FG% goes UP
> (rebounders already shoot interior and better), not down.**
>
> ✅ **§3.21 (THE REBOUND POOL) SHIPPED — [decisions.md](decisions.md) #043 A–H.** Rebound
> pool **37.80 → 43.72** against a real 43.70. FGA **89.16** ✅ · Points **115.04** ✅ ·
> FTA **23.76** ✅ · Fouls **19.40**. Off/def rebounds land **+0.48 / −0.46** as
> **reported residuals** — three slices, three offensive shares, one knob.

---

## §3.22 execution plan (decisions.md #044 — resolved)

**Build preamble.** Java 21 or Lombok breaks:
`JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem`. The **JaCoCo gate is
per-package and runs at `install`, not `test`** — invoke the `test-coverage` skill; a
green `mvn test` does not prove it passes. Invoke `project-docs` before touching any doc.
After any change touching `PossessionEngine`/`ShotSelector`, run the sim classes
**alone** (CLAUDE.md). The harness:

```bash
cd gametime-service && for s in 1000 2000 3000 4000 5000; do SPRING_PROFILES_ACTIVE=local,baseline JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -q -pl gametime-app test -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=$s -DfailIfNoTests=false; done
```

⚠ **The profile MUST come from the ENVIRONMENT** — `-D` does not reach the forked
Surefire JVM and the failure looks like a database error. **Confirm `Profiles:
local,baseline` and all FOUR reconciliation lines read `OK` before reading any number**,
and **judge at the 5-seed MEAN**.

**⚠ THE NUMBERS TO REPRODUCE — the design pass already ran this exact mechanic (a
temporary tree, reverted), so execution has a landing to hit, not a hypothesis to test**
(5-seed means, seeds 1000–5000, #044 Step 0):

| row | §3.21 (now) | **§3.22 design run** | target |
|---|---|---|---|
| Points | 115.04 | **115.70** | 115.6 ✅ |
| FG% | 46.78 | **47.02** | 47.1 ✅ — ⚠ **UP**, not the dip Q6 expected (#044 F) |
| 3P% | 35.68 | **36.08** | 36.0 ✅ |
| **FGA** | 89.16 | **89.60** | 89.1 — ⚠ **+0.50 on a ±0.45 band; Step 7 decides** |
| 3PA | 36.92 | 36.92 | 37.0 ✅ |
| **Assists** | 27.10 | **27.00** | 26.7 (+0.30 — closer than today) |
| Off / Def reb | 11.78 / 31.94 | 11.91 / 31.62 | reported residuals (#043 B) |
| Fouls | 19.40 | **19.22** | 19.9 (−0.68) |
| FTA | 23.76 | 23.46 | 23.5 ✅ |
| rebounder = next shooter-pick | 22.4% | **35.4%** | *(reported, #044 A — not a target)* |
| assisted share of the rebounder's makes | 66.7% | **33.4%** | *(reported, #044 E)* |

**If the shipped code reproduces these to within per-seed noise (~±0.2 on the rates), the
mechanic is wired as designed. If a row lands somewhere else, the build diverged from the
design — find out where before tuning anything.** ⚠ **Expected blast radius:** exactly
**two** `SimConfig` constants (63 tunables / 29 statics); the stream MOVES and **no test
should need re-baselining** (#044 I) — a test that does is a finding, not a chore.

**Step 1 — the two constants (#044 B, E)**
- [ ] `application-baseline.properties`: add `sim.offensive-rebounder-shot-weight=2.0`
      under *Rebounding and loose balls* (or a new *Second chance* block) with a comment
      in the file's house style: it multiplies the rebounder's `offensiveWeight` for the
      NEXT draw only; `1.0` = off; the REALIZED share (35.4% on this league) is what it
      produces, not what it sets — **do not back-solve it from a target share**.
- [ ] `SimConfig`: `private final double offensiveRebounderShotWeight;` (no initializer),
      the constructor parameter (`@DecimalMin("0.0")`), the assignment, and the accessor
      `offensiveRebounderShotWeight()` with a javadoc citing #044 A/B.
- [ ] `SimConfig`: `public static final double OFFENSIVE_REBOUNDER_ASSIST_LEAN = 0.5;`
      beside `baseAssist`, javadoc in the `FREE_THROW_REBOUND_LEAN` shape (#044 E): a
      RULE, static by design, **NO properties line, NO calibration row**, applied AFTER
      `clampProbability` at one site; the cost (not profilable) stated.
- [ ] ⚠ **The tunable count lives in FOUR places** — the `SimConfig` header comment
      (62 → **63**, 28 → **29**), `baseline()`'s javadoc, its error message, and
      `BASELINE_PROFILE_RESOURCE`'s javadoc — plus `SimConfigProfileBindingTest`:
      `EXPECTED_TUNABLE` 62 → **63**, `EXPECTED_STATIC` gains
      `OFFENSIVE_REBOUNDER_ASSIST_LEAN` in the *rules* group (10 → 11), and the method
      `onlyTheTwentyEightRulesAndMachineryConstantsAreStatic` renames to `...TwentyNine...`.
      CLAUDE.md's "Currently 62 tunables / 28 statics" line and todo.md's standing-facts
      "all **62** values" move with it.

**Step 2 — `ShotSelector.pickShooter` gains the rebounder as a PARAMETER (#044 A, H)**
- [ ] `pickShooter(List<PlayerGameState>, PlayerGameState rebounder, RandomGenerator)`:
      each player's weight is `offensiveWeight()`, the rebounder's is
      `offensiveWeight() × config.offensiveRebounderShotWeight()`. ⚠ `ShotSelector` is a
      `@Component` with no `SimConfig` today — inject it (constructor), the way the
      resolvers do. **Still exactly ONE `nextDouble()`.**
- [ ] The existing two-arg `pickShooter` **delegates with `null`** — every caller and
      every test compiles unchanged, and `null` means "no identified rebounder" (#044 C),
      not a sentinel: the draw with `null` must be **bit-identical** to today's.
- [ ] Javadoc: a weight, not a branch; the type is NOT forced (#040 C does that by
      skill); the parameter is a participant, not a mode — and why that is the opposite
      resolution from #043 H's flag (#044 H).
- [ ] `ShotSelectorTest`: (a) **same seed, `null` vs the two-arg form, identical picks
      over ~1,000 draws**; (b) five equal-weight players, rebounder picked **≈ 1/3**
      (bounds ~0.30–0.37 over 20k draws, `SimConfig.baseline()`'s 2.0); (c) the
      rebounder's *relative* standing survives — a low-weight rebounder doubled is still
      below a high-weight teammate.

**Step 3 — the `putbackCandidate` local and its three carriers (#044 C)**
- [ ] `resolvePossession`: `PlayerGameState putbackCandidate = null;` declared beside
      `offensiveRetentions`. At the top of `while (true)`, ONE step:
      `PlayerGameState rebounder = putbackCandidate; putbackCandidate = null;` then
      `shotSelector.pickShooter(offense, rebounder, rng)`. ⚠ **`rebounder` must stay in
      scope for the whole iteration** — Step 4 reads it at the assist site. Do NOT clear
      it after the pick and do NOT clear at the `continue`s.
- [ ] **Missed-shot board (4b, ~line 597)**: after `missedShotResolver.resolve(...)`,
      inside the `offenseRetains()` block, `if (miss.outcome() == OFFENSIVE_REBOUND)
      putbackCandidate = miss.rebounder();` — the `OUT_OF_BOUNDS_OFFENSE` retention has
      no rebounder and sets nothing.
- [ ] **Block recovery (~line 463)**: `emitBlockRecoveryEvent` returns a
      `record BlockRecoveryResult(int sequence, PlayerGameState rebounder)` instead of an
      `int` (the `ReboundFoulResult` / `FreeThrowResult` shape). Set the candidate only
      inside `recovery.offenseRetains() && !capReached` — a capped `RECOVERED_OFFENSE`
      still credits its rebounder (#043 E) and then `return`s.
- [ ] **Missed last free throw**: `FreeThrowResult` gains a `PlayerGameState rebounder`
      component (null on a made or defensively-rebounded FT), filled in
      `awardLiveFreeThrows` from `miss.rebounder()` on `OFFENSIVE_REBOUND`. Set the
      candidate at the **four** sites that read `offenseRetains()`: `SHOOTING` (~382),
      `BONUS` in the penalty (~402), `AND_ONE` via `awardAndOne` (~546), and ⚠ **the
      rebounding-foul `BONUS` trip (~732) — which reaches the loop through
      `ReboundFoulResult`, so that record gains the same `rebounder` component** (null on
      its by-rule retain path, where no board ran). One record shape, three records.
- [ ] ⚠ **The four no-rebounder retentions set NOTHING** — `OOB_OFFENSE`, both flagrant
      retentions (#034 B), the rebounding foul's defense-committed retain. Add a one-line
      comment at each so a later reader does not "complete" them (#014/#017/#020).
- [ ] Update the javadocs that describe the loop's re-entry ("re-enters at
      ShotSelector") to say the rebounder now carries the weight — `PossessionEngine`'s
      class/loop comments, `emitBlockRecoveryEvent`, `awardLiveFreeThrows`.

**Step 4 — the assist rule at the ONE assist site (#044 E)**
- [ ] `resolveAssist(offense, shooter, rng)` → add a `boolean putback` parameter (or a
      probability multiplier — builder's call, one site). At the call (~line 488):
      `resolveAssist(offense, shooter, shooter == rebounder, rng)`. Inside: `double p =
      config.assistProbability(avgPassing); if (putback) p *= SimConfig
      .OFFENSIVE_REBOUNDER_ASSIST_LEAN;` — **after** the clamp, not before.
- [ ] ⚠ **Same roll count** — the `nextDouble()` for "assisted?" is taken exactly as
      today; only the threshold moves. A flip to unassisted then skips the assister draw,
      which is expected (#044 I).
- [ ] Comment at the site: keys off `shooter == rebounder`, NOT "any second-chance shot";
      why half and not zero (the assists headroom, #044 E); the static's role.
- [ ] `PossessionEngineTest`: (a) with a scripted offensive rebound and equal players, the
      next iteration's shooter is the rebounder at ≈ 1/3 over many possessions (or a
      scripted-RNG pick that lands on the rebounder only under the doubled weight);
      (b) a putback make rolls the assist at **half** the ordinary chance — measurable
      as an assisted share ≈ 0.5 × the non-putback share over many makes; (c) a
      non-putback second-chance make (shooter ≠ rebounder) assists at the ordinary rate;
      (d) an `OOB_OFFENSE` / flagrant retention gives NO weight (the pick distribution
      after it is flat over five equals). The existing assist reconciliation tests keep
      passing untouched.

**Step 5 — the stream check and the seeded tests (#044 I) — the real verification**
- [ ] Run the sim classes **alone**: `GameSimulatorIntegrationTest`,
      `PossessionEngineTest`, `ShotSelectorTest`, `MissedShotResolverTest`,
      `SimConfigProfileBindingTest`. **All 167 existing tests must pass UNCHANGED** — the
      design run proved they do with this exact mechanic. ⚠ If one needs a new value,
      **stop**: something consumed a draw the design did not.
- [ ] Prove the stream MOVED (it must — #044 I): the harness on seed 1000 must NOT
      reproduce §3.21's per-seed line (Points 113.8 → ~114.9). A line-for-line identical
      seed-1000 report means the weight is not reaching the draw (an env/profile miss, or
      the two-arg overload still being called).
- [ ] `mvn clean install` — the JaCoCo gate (`test-coverage` skill). New branches: the
      rebounder weight, the three candidate sets, the assist factor, the null path.

**Step 6 — measure and report the realized share (#044 A/E) — then DELETE the probe**
- [ ] Run `PutbackProbe` (already in the test tree from the design pass) at seeds
      1000–5000 with `-DputbackProbe=true`; record the realized **rebounder share** (design:
      35.4%) and **assisted share of his makes** (design: 33.4%) in #044's implementation
      note. **Report; do not back-solve `M` or the lean from them.**
- [ ] ⚠ **Delete `PutbackProbe.java`** (#043 F: a throwaway answering a one-time question
      is not a harness row). Do NOT add a permanent putback line to `CalibrationHarness`.

**Step 7 — re-land (#044 G) — one lever or none**
- [ ] Harness at 5 seeds on the shipped code; confirm profiles + reconciliation; compare
      to the table above.
- [ ] **FGA**: the design read **89.60 (+0.50) on a ±0.45 band**. If execution's 5-seed
      mean is inside the band, **say so explicitly in the implementation note and touch
      nothing**. If not, nudge `sim.base-no-basket-foul` UP (0.1753 → ~0.178; **1.49 FGA
      per foul**, #042 D6) — Fouls move TOWARD 19.9 as a side effect; hold FTA with
      `sim.non-shooting-foul-share` only if it leaves its band (−20.46 FTA per unit share).
- [ ] ⚠ **Re-measure `PERSONAL_FOULS_PER_TEAM_GAME`** (19.08) — the foul rate moved
      (19.40 → 19.22 at the design) and moves again if the lever is touched; calibration.md's
      standing rule, and the fourth consecutive phase to trip it.
- [ ] ⚠ **Do NOT touch** `base-offensive-rebound`, the four `block-*` weights (#043
      B/E4), `base-assist` (#044 E), or `offensive-rebounder-shot-weight` itself to chase
      a row.

**Step 8 — docs close-out**
- [ ] `decisions.md #044`: the **implementation note** (≤ 6k): divergences, the resolved
      open-at-execution items (the `base-no-basket-foul` value or "untouched", the
      `ReboundFoulResult` carrier choice), the realized shares from Step 6, the landing
      table, coverage. Then delete the probe.
- [ ] `roadmap.md`: flip the §3.22 bullet to `[x]` with the landing note.
- [ ] `calibration.md`: **no target moves.** Update the *Current* column for every row the
      landing moved; add the operative rule under **Assists**: *a putback (shooter ==
      rebounder) is assisted at `OFFENSIVE_REBOUNDER_ASSIST_LEAN` × the ordinary chance;
      the row's +0.4 headroom is what priced it at 0.5 — do not re-land it with
      `base-assist`*; and under the rebound pool, that the rebounder's next-shot share is
      **reported** (35.4% vs a real ~45–55%), not a target. If Step 7 moved
      `base-no-basket-foul`, update its rows and the `(target ~N)` strings together.
- [ ] `possession-flow.puml`: **the fork is ALREADY DRAWN** (this design pass — the
      ShotSelector node and the Assist node). Confirm it matches what shipped (the
      parameter, the read-and-clear, the four FT sites), adjust the note if the placement
      moved, `plantuml -checkonly`, render with
      **`plantuml -DPLANTUML_LIMIT_SIZE=16384 -tpng`** (the size flag is REQUIRED) and
      confirm the height — it is **13,718 px** after this pass, against 16,384.
- [ ] `game-events.md` and `game.md`: **already updated by the design pass** (the assist
      condition on the made-`SHOT` row; the walkthrough's "no preference" premise and the
      §3.22 mechanic). Re-read both against the shipped code.
- [ ] `CLAUDE.md`: the tunable-count sentence (63 / 29, naming §3.22's two constants) and
      the todo.md line in the tree; then **rewrite this file for the next phase.**

**Do NOT**
- **Do NOT force the shot type.** `pickShotType` already bends by the shooter's skills
  (#040 C) — measured, the rebounder already leans interior and shoots 54.8% (#044 Step 0).
- **Do NOT weight anyone on the four paths with no rebounder** (#044 C).
- **Do NOT back-solve `M` from a target share** — set 2.0, report what it realizes.
- **Do NOT add a second `ShotSelector` method** (#044 H) or an engine FIELD for the
  candidate (the design experiment's shortcut; rejected for `main` — #044 alternatives).
- **Do NOT leave the probe or add a harness row for it** (#043 F).
- **Do NOT commit** without the user's say-so (CLAUDE.md).

**Verified facts** (confirmed against the code, 2026-09):
- `ShotSelector.pickShooter(List, RandomGenerator)` takes ONE `nextDouble()`, weights by
  `offensiveWeight()` = `drive + finishing + perimeter + post + longRange`, no rebounder
  parameter, no `SimConfig` injected.
- `PossessionEngine.resolvePossession` has **eight** `continue` statements serving six
  retention paths (the miss-board `continue` serves both the offensive rebound and
  OOB-offense; the rebounding-foul one serves both its by-rule retain and its FT board);
  **three path kinds** identify a rebounder (missed-shot board, in-bounds block recovery,
  missed last FT — the last reached from FOUR call sites incl. the rebounding-foul bonus
  trip). ⚠ The pre-§3.21 count of "seven" in older notes is stale.
- `resolveAssist` has ONE call site, inside `if (made)`, and rolls `nextDouble() >=
  config.assistProbability(avgPassing)` then a weighted assister draw — so a flip to
  unassisted skips one draw.
- `MissedShotResolver.Result` already carries `rebounder`; `FreeThrowResult` and
  `ReboundFoulResult` are `(int sequence, boolean offenseRetains)`; `emitBlockRecoveryEvent`
  returns a bare `int`.
- ⚠ **The harness league is NOT five identical players** — `players.csv` has 422 distinct
  attribute rows and skills are computed by the `mapper` calculators (#044 premise 1).
- All 167 tests in the five sim classes pass with the mechanic wired in (design run).

---

## ⚠ Traps that bite EVERY session — read these before trusting a doc

**1. SUB-PHASE NUMBERS HAVE BEEN REUSED — FOUR TIMES FOR ONE PASS.** Recalibration was
§3.16, then §3.18, then §3.19, and is now **§3.20**. ⚠ **So BOTH numbers are ambiguous:**
every **"§3.16"** written before 2026-08 means RECALIBRATION (the §3.16 slot became
shooting-foul composition, shipped), and every **"§3.19"** written before this session
also means RECALIBRATION (the §3.19 slot is now **instrumentation**).
⚠ **The docs have NOT been swept**: ~60 stale "§3.19 = recalibration" references remain
in `decisions.md`, `roadmap.md`, `backlog.md` and `ideas.md`, left deliberately rather
than mass-edited, because retro-editing history is worse than a callout. **Read the phase
NAME, never the number alone.** roadmap.md carries the mapping.

**2. A NUMBER CAN MOVE — OR FAIL TO MOVE — FOR REASONS THAT ARE NOT THE ENGINE.**
⚠ **§3.20 hit this THREE MORE TIMES, and none of them was the engine** — a modelled
coupling that does not exist in the code (FTA/FGA), a wedge modelled with the wrong
*shape* (flat vs multiplicative), and `PROB_FLOOR` silently eating ~40% of a lever.
Before tuning anything, ask: **did the engine change, did the MEASUREMENT
change, or is a clamp holding it?** All three have happened:
- **The instrument broke** — a harness row classified a stopped shot by its free-throw
  count, and a phase that changed what a foul awards silently halved the row.
- **⚠ FIXING an instrument also moves numbers, and looks like a regression** — `Stopped
  shots / team` read 12.43 post-fix against 7.65 pre and appeared to double. **It fell**
  (7.39 → 6.24 like-for-like). **Compare raw-to-raw across an instrument change.**
- **⚠ A CLAMP can do what a constant appears to do, and the tell is a number that DOESN'T
  move** — blocks were predicted to fall to ~2.6 and stayed at 4.80. See Q1.

**3. THE HARNESS NEEDS ITS PROFILE IN THE ENVIRONMENT.** `-Dspring.profiles.active` does
**not** reach the forked Surefire JVM; the context comes up with `activeProfiles = []`
and fails with what looks like a database error. Use
`SPRING_PROFILES_ACTIVE=local,baseline`, run from `gametime-service/`, and **confirm the
report's `Profiles:` line before trusting any number.** This has cost two sessions.

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
  `GameEvent` — ✅ **CLOSED by §3.18**) · §3.12 **#030** (~20 3PA/team/game
  — ✅ **CLOSED by §3.17**, landed 37.22) · §3.16 **#039** (the unsourced shooting-foul
  share — ⚠ **still unsourced**, and §3.20 moved it to 0.3766) · **§3.17 #040**
  (FG%/2P%, FGA, FTA — ✅ **all CLOSED by §3.20**; the
  `PROB_FLOOR`-vs-`base-block-three` finding is ⚠ **RE-OPENED at a larger size** — §3.20
  measured the same clamp making `base-turnover` a 0.17-elasticity lever, see #042 D4)
- **§3.20's residuals** → **#042**'s implementation note. The rebound pool is **§3.21,
  above**; the rest (Fouls, `PROB_FLOOR`, 3P%'s band) are **sized and deliberately not
  scheduled** → [roadmap.md](roadmap.md)

**Other files own these outright:**

- **The phase sequence and this phase's bullet** → [roadmap.md](roadmap.md). ⚠ Its
  **§3.20** bullet is `[x]` with the landing; the **pre-design argument under it is
  SUPERSEDED** (#042 A disproved the over-determination, and execution then disproved
  #042 B's FTA/FGA coupling in turn). It is **kept and ANNOTATED** rather than rewritten,
  because it is history. **#042 and its implementation note are authoritative.**
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
