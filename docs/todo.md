# TODO

Tactical task list for the **current phase only**. Check items off or remove
them as completed. For the big-picture phased roadmap and what's already
shipped, see [roadmap.md](roadmap.md). Homeless infra/tooling chores live in
[backlog.md](backlog.md); deferred *gameplay* scope lives in roadmap.md's
**Possession-fidelity completion** section (§3.7–§3.16).

Current focus: **§3.15 — `SimConfig` profiles**. The full §3.7–§3.16 sequence and
what follows (Phase 4) live in **roadmap.md's "Possession-fidelity completion"
section** — not here (todo.md is current-phase-only). §3.7–§3.13, **§3.14a** and
**§3.14b** have all shipped.

> **✅ §3.15 IS EXECUTE-READY — the design pass is DONE, resolved as `decisions.md`
> #035 (Decisions A–I).** The execution plan is below. Do not re-litigate A–I; add an
> implementation note to #035 recording any divergence, and flip the roadmap bullet.
>
> **The shape, in one line:** **57 of the 83 constants become INSTANCE state on the
> existing `SimConfig` bean**, bound by Spring from `application-baseline.properties` carrying
> ALL 57 (era profiles are deltas on top of it); **26 stay `public static final`** (9 rules + 16 model machinery +
> `PERSONAL_FOULS_PER_TEAM_GAME`). The invariant reads off the source: **`static final`
> means it is a rule or the shape of the model, not a knob.**
>
> **⚠ WHY INSTANCE AND NOT `static` NON-FINAL — read #035 B before touching anything.**
> A `public static final` primitive initialized from a literal is a JLS §4.12.4
> *constant variable*, so **javac inlines its value into every caller's bytecode** and
> `SimConfig` is never consulted at runtime. Consequences: a properties file cannot
> override such a field at all; **a `static final` "compatibility alias" left for tests
> would be baked into each test class at ITS compile time**, so a test could assert
> 0.3375 while the engine ran 0.31 with no error. **No aliases. Tests convert** (E).
> Instance (not static-mutable) because a profile must eventually be a **per-simulation**
> input in a concurrent Spring app — static-mutable cannot express that.
>
> **§3.15's validation gate is reproducing §3.14b's shipped landing EXACTLY on the
> BASELINE profile** — flagrants 0.148, points 118.3, FG% 46.9%, penalty rate 51.9%
> (5-seed mean, seeds 1000–5000), **per-seed identical, not merely in aggregate**. A
> refactor that changes any baseline number is a bug. **An era profile producing
> different numbers is the goal working, not a failure** (#035 G).
>
> **§3.15 adds NO mechanic** — no possession branch, no event, no rate.
> `possession-flow.puml` is structurally complete after #034 and **must not change**.
> If the work starts wanting a new branch, something has gone wrong.
>
> Numbering stays `a`/`b`: **§3.15 (profiles) and §3.16 (recalibration) do NOT
> renumber**, because §3.16 is named in the shipped, never-retro-edited text of
> #030 and #031 (#032 A).

---

## §3.15 execution plan (decisions.md #035 — resolved)

**Build preamble.** Java 21 via SDKMAN; Homebrew Maven defaults to JDK 25 and breaks
Lombok, so always:

```
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean test
```

The **JaCoCo gate is per-package and runs at `install`, not `test`** — a green
`mvn test` does not prove it passes. Invoke the **`test-coverage`** skill when adding
production code. §3.15 adds little new logic (a loader + accessors), so the coverage
risk is the loader's error paths, not the engine.

**⚠ This is a refactor with a total-reproduction gate. Work in the order below** —
Steps 1–4 must leave the engine bit-identical *before* any profile file exists, so a
gate failure points at the conversion rather than at a profile value.

### Verified facts (measured 2026-08 against the tree — for whoever executes)

- **83 constants** in `SimConfig`; **75 read from outside it**; **323 static call
  sites — 84 main, 239 test.**
- Test sites by file: `SimConfigTest` **80**, `PossessionEngineTest` **29**,
  `FoulResolverTest` **27**, `PlayerGameStateTest` **27**, `RotationStateTest` **25**,
  `GameSimulatorIntegrationTest` 10, `BlockResolverTest` 8, `GameDataTest` 8,
  `CalibrationHarness` 7, `TestPlayerFactory` 6, `ReboundResolverTest` 3,
  `MissedShotResolverTest` 3, `CoachModifiersTest` 3, `ShotResolverTest` 2,
  `TurnoverResolverTest` 1.
- **9 of 12 main-code readers ALREADY hold an injected `SimConfig config`**:
  `FoulResolver` (16 static reads), `TurnoverResolver` (15), `PossessionEngine` (10),
  `ShotResolver` (8), `GameSimulator` (4), `BlockResolver` (4), `RotationState` (2),
  `ReboundResolver` (1), plus `CoachModifiers`. For these the conversion is local.
- **The 3 that do NOT, plus one more to thread:**
  - `PlayerGameState` — **17 reads**, a `new`-per-player value object, **9 construction
    sites** (`GameSimulator` ×2, `TestPlayerFactory` ×3, `PlayerGameStateTest` ×4).
  - `GameData` — 2 reads, one method (`isInBonus`), plain object.
  - `MissedShotResolver` — 4 reads (`OOB_*`); a `@Component` but with **no
    `SimConfig` injected today**.
  - `GametimeServiceImp` — 1 read (`DEFAULT_POSSESSIONS_PER_PERIOD`), already a bean.
- **10 test files already do `new SimConfig()`** — the baseline-instance pattern
  exists rather than being invented (#035 E).
- `FOUL_TROUBLE_SIT_PROBABILITY` is the **only non-scalar** (`double[7]`).
- `SCALE_AVG` has **28 sites** — it is the 10-point attribute-scale anchor and stays
  static (#035 C).
- Harness: `-DcalibrationSeed` (default 1000), **6 rounds × 17 matchups ≈ 102 games**
  per run, every printed figure already a per-team-per-game mean.

---

**Step 1 — split `SimConfig`'s constants into the two declared groups (#035 C)**
- [ ] Keep `public static final` for the **26**: **rules (9)** — `PERIODS`,
      `MINUTES_PER_PERIOD`, `OT_MINUTES`, `FREE_THROWS_PER_FOUL`,
      `AND_ONE_FREE_THROWS`, `TECHNICAL_FREE_THROWS`, `FLAGRANT_FREE_THROWS`,
      `TECHNICAL_EJECTION_LIMIT`, `FLAGRANT_EJECTION_LIMIT`; **machinery (16)** —
      `SCALE_AVG`, **`MAX_ENERGY`**, `PROB_FLOOR`, `PROB_CEILING`, `SENSITIVITY`,
      `FT_SENSITIVITY`, `BLOCK_SENSITIVITY`, `REBOUND_FOUL_SENSITIVITY`,
      `AND_ONE_SENSITIVITY`, `TO_CAUSE_SENSITIVITY`, `COACH_SENSITIVITY`,
      `ASSIST_SENSITIVITY`, `ACUMEN_SENSITIVITY`, `TEAM_EFFICIENCY_SENSITIVITY`,
      `ENDURANCE_DRAIN_SENSITIVITY`, `FOUL_TROUBLE_VALUE_SENSITIVITY`; **measured (1)**
      — `PERSONAL_FOULS_PER_TEAM_GAME`.
      ⚠ **`SCALE_AVG` and `MAX_ENERGY` are the two SCALE DEFINITIONS** — each is the
      denominator giving its family meaning, not a knob. Raising `MAX_ENERGY` looks
      like "more stamina" but silently rescales the drain and the sub threshold, which
      are expressed in units of it (#035 C).
- [ ] Convert the other **57** to `private final` instance fields + public accessors.
      **Naming assumption**: `BASE_THREE` → `config.baseThree()` (open-at-execution).
- [ ] Group each set under an explicit banner comment naming the rule
      (`static final` = rule or model machinery), and give each profilable constant its
      property key in the javadoc.
- [ ] **Move NO javadoc** (#035 A) — the tuning history stays attached to its constant.
- [ ] ⚠ **The instance fields take NO initializers** (#035 A) — `private final double
      baseThree;`, not `= 0.3375`. The values move to `application-baseline.properties` and exist
      in exactly one place. Add a loader-backed **`SimConfig.baseline()`** factory; the
      ~10 test files doing `new SimConfig()` switch to it (Step 3).

**Step 2 — convert the 84 main-code call sites (#035 B)**
- [ ] The 9 readers that already hold `config`: `SimConfig.X` → `config.x()`.
- [ ] Thread `SimConfig` into **`MissedShotResolver`** (constructor injection beside
      the existing `ReboundResolver`), **`GameData`**, and **`GametimeServiceImp`**.
- [ ] Thread `SimConfig` into **`PlayerGameState`** — **only 6 of its 17 reads are
      profilable** (`FOUL_OUT_LIMIT` ×2, `ENERGY_DRAIN_PER_POSSESSION`,
      `ENERGY_RECOVERY_PER_POSSESSION`, `FATIGUE_MAX_PENALTY`, `MIN_DRAIN_SCALE`); the
      other 11 are `SCALE_AVG` ×5, `MAX_ENERGY` ×3 and the two ejection limits, all
      **static — leave them alone**. ✅ **None of the 6 is in the constructor**, so there
      is no field-initialization ordering problem (`this.currentEnergy =
      SimConfig.MAX_ENERGY` at line 107 stays exactly as-is). **Assumption: a field set
      via the constructor**; measure the 9 construction sites first.
- [ ] ⚠ **Change no value, no rounding, and no order of evaluation.** This step is
      pure provenance.

**Step 3 — convert the 239 test call sites (#035 E)**
- [ ] **NO `static final` aliases** — see the callout at the top of this file. Every
      test reads its own `SimConfig` instance.
- [ ] Largest first: `SimConfigTest` (80), `PossessionEngineTest` (29),
      `FoulResolverTest` (27), `PlayerGameStateTest` (27), `RotationStateTest` (25).
- [ ] `TestPlayerFactory` (6) and the `PlayerGameStateTest` construction sites take
      whatever shape Step 2 chose for `PlayerGameState`.
- [ ] Constants that stayed static (Step 1) keep reading `SimConfig.X` — unchanged.

**Step 4 — GATE CHECKPOINT: prove the conversion is inert BEFORE any profile exists**
- [ ] Full `mvn clean test` green; coverage gate green at `install`.
- [ ] Run the harness on all five seeds and confirm **per-seed byte-for-byte** equality
      with §3.14b: flagrants **0.148**, points **118.3**, FG% **46.9%**, penalty rate
      **51.9%**, 3P% 36.7%, assists 27.1, TO 13.6, foul-outs 0.358, fouls 19.35.
      ```
      JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn test -pl gametime-app \
        -Dtest=CalibrationHarness -Dcalibration=true -DcalibrationSeed=1000
      ```
      (repeat for 2000/3000/4000/5000).
- [ ] ⚠ **Do not proceed to Step 5 until this is exact.** A failure here is a
      conversion bug — a mistyped default or a changed evaluation order — and is far
      cheaper to find now than after profiles exist to blame.

**Step 5 — bind `SimConfig` from properties via Spring (#035 A/D)**
- [ ] Annotate `SimConfig` **`@ConfigurationProperties(prefix = "sim")`** + **`@Validated`**.
      **No custom loader and no new flag** — Spring's binder does the reading and the
      type conversion, and profile *composition* does the selecting.
- [ ] ⚠ **Completeness is STRUCTURAL, not a hand-written check** — the fields have no
      initializers (Step 1), so a constant missing from `application-baseline.properties`
      has no value and Spring fails the context at startup naming the property.
- [ ] Add `@Validated` constraints: rates in `[0,1]`, counts positive, and
      **`FOUL_TROUBLE_SIT_PROBABILITY` exactly length 7**.
- [ ] Add a test asserting **every profilable field appears in
      `application-baseline.properties`** — the guard against someone adding a field with
      a `= 0.0` initializer out of habit and silently reintroducing a second source of
      truth.
- [ ] Keys are **kebab-case** (`sim.base-no-basket-foul`) — Spring's recommended format,
      bound to the camelCase field by relaxed binding. **Stated in the file header, NOT
      enforced in code**: relaxed binding accepts camel/underscore too and applies them
      correctly, so a non-kebab key is a style inconsistency, not a wrong run (#035 A).
- [ ] ⚠ **No unknown-key rejecter** (#035 D) — Spring ignores unrecognized properties, and
      hand-enumerating the legal set would restore the duplication A removed. A stray
      `sim.personal-fouls-per-team-game` simply does nothing, visible in Step 7's dump.
- [ ] `FOUL_TROUBLE_SIT_PROBABILITY` binds as a list — comma-separated on one line is the
      assumption (Spring handles it natively).

**Step 6 — the two profile files (#035 A)**
- [ ] **`application-baseline.properties`** — **ALL 57 values, grouped in seven sections by
      what the knob does**: pace/game structure (2), shooting (5), fouls & free throws (9),
      rebounding & loose balls (16), turnovers (10), fatigue & rotation (12), rare
      events (3). Each value gets a **1–2 line pointer comment** — the trap, the units
      warning, the `#NNN` — with the full prose staying on the Java accessor javadoc.
      The header states **what is NOT in the file** (the 26 static rules/machinery).
      ⚠ **Transcribe the values EXACTLY from the current Java initializers** — it is the gate.
- [ ] **`application-nineties.properties`** — deltas only; Spring's **later-wins** order
      applies them over baseline. ⚠ **Baseline must ALWAYS be in the list** —
      `test,nineties` alone leaves ~50 properties unbound and fails the context at
      startup. Correct: `local,baseline` (modern) or `local,baseline,nineties` (90s).
      ⚠ **Order is positional, not semantic**: `local,nineties,baseline` puts baseline
      last, so it wins and the era file silently does nothing (Step 7's dump catches it).
      **Never stack two ERA profiles.** Low-pace / high-foul / post-heavy:
      `sim.default-possessions-per-period` ↓, `sim.base-three` ↓,
      `sim.base-post`/`sim.base-drive` ↑, `sim.base-no-basket-foul` ↑, `sim.foul-mult-*` ↑,
      `sim.base-offensive-rebound` ↑, `sim.rebound-foul-base` ↑, `sim.base-turnover` ↑.
- [ ] ⚠ **These files hold `sim.*` keys ONLY.** Nothing structurally stops an era file
      setting `spring.datasource.*` — that is discipline, and it is the one guard the
      rejected custom loader would have given (#035 A).
- [ ] ⚠ **NO `modern.properties`** — baseline IS the modern-calibrated config, so a third
      file would be a near-duplicate. The real modern *hypothesis* worth a file is the
      **3PA-volume gap** (engine ~20/team/game vs the NBA's ~35, #030's follow-up).
- [ ] ⚠ **`nineties`' values are ILLUSTRATIVE, not calibrated.** Nothing is tuned against
      a non-baseline profile and no target applies to one (#035 I).
- [ ] Set `spring.profiles.active=local,baseline` in `application.properties`. ⚠ **The
      tests have no `@ActiveProfiles`** and inherit that value, so every `@SpringBootTest`
      will load baseline — intended, but verify the context still starts.

**Step 7 — the harness: effective-config dump, baseline-only targets**
- [ ] Profile selection is the ordinary **`-Dspring.profiles.active=test,baseline,nineties`**
      beside the existing `-DcalibrationSeed=NNNN`; the default `test,baseline` keeps every
      current invocation working unchanged (#035 F). **No `-DcalibrationProfile` flag.**
      ⚠ **Baseline stays in the list** — an era file is deltas, not a replacement.
- [ ] Report header names the **active profile list**.
- [ ] **Effective-config block**: every profilable constant and its value. ⚠ **Load-bearing,
      not decoration** — it makes a run self-describing (#035 A), partially closes
      backlog.md's harness-self-verification chore, and is **the only thing that catches a
      mis-ordered profile list** (baseline last ⇒ the era file silently does nothing).
- [ ] **Baseline runs print the `(target ~N)` strings exactly as today.**
- [ ] **Non-baseline runs SUPPRESS the target strings** and print a delta against
      §3.14b's landing instead — `Points / team / game: 101.4 (baseline 118.3, −16.9)`
      (#035 I). Hold the baseline figures as a **named constant set**, not re-measured
      per run.
- [ ] ⚠ **Do NOT author per-profile targets** — a delta is not a target, and §3.16 owns
      target *sourcing*.

**Step 8 — close out**
- [ ] Re-run the 5-seed baseline; confirm the Step 4 landing still holds.
- [ ] Run `nineties` once — a smoke check that it loads, validates and produces
      *different* numbers. **Different is correct; do not tune it.**
- [ ] Implementation note on `#035` (**≲6k chars**): divergences, the resolved
      open-at-execution items (key convention, accessor naming, the array parse, the
      `PlayerGameState` shape), and the baseline landing **with its profile name beside
      the seeds** (#035 F).
- [ ] Flip roadmap.md's §3.15 bullet to `[x]` with a landing note.
- [ ] ⚠ **`possession-flow.puml` must NOT change** — verify it is untouched.
- [ ] **Do NOT commit** — leave the work in the tree (CLAUDE.md's git rule).

### Reconciliation invariant (what "done" means)

**The baseline profile through the new loader reproduces §3.14b per-seed, not merely
in aggregate.** §3.15 introduces no branch, conditional or loop, so **no RNG draw is
added, removed or reordered** — only the *provenance* of already-read values changes
(#035 G). ⚠ §3.14b's severity roll is drawn **per foul** and its flagrant-2 sub-roll
**only on a hit**, so draw count varies with how many fouls a game produced: a
mechanism changing *how many* draws a possession takes breaks reproduction, while one
changing the *order constants are read in* is safe (they are read before the rolls).
A failure therefore means a **value** changed — a wrong default, a mis-parsed
property, a units error — and surfaces as total failure rather than subtle drift.

### Open at execution (constrained calls left to the builder)

- Accessor naming for the 57 (`config.baseThree()` assumed). **Key format is settled: kebab-case** (#035 A).
- Accessor naming for the 57 (`config.baseThree()` assumed).
- The `FOUL_TROUBLE_SIT_PROBABILITY` list convention + length-7 validation.
- Where the loader reads profile files from on the test classpath.
- **Whether `PlayerGameState` takes a `SimConfig` field or is passed one per call** —
  17 reads across instance methods, so the field is the assumption; measure the 7
  construction sites first.

---

### ⚠ Do NOT (standing guardrails — carried forward into §3.15/§3.16)

These outlive any one phase. **§3.15 is a refactor, so it must change no number at
all**; §3.16 owns re-centering and is the only phase that may revisit the last one.

- **Do NOT add a fourth removal path in `RotationState`** — ejection is the **hard**
  tier and extends `isDisqualified(...)` (#031 H, built §3.14a).
- **Do NOT reuse §3.14a's counter split** — a flagrant **does** count toward the
  6-foul limit and the penalty tally (#032 E). Use `recordFoul()`.
- **Do NOT re-tune §3.13's foul-trouble sit curve** — measured saturated (#031 note).
- **Do NOT trim `BASE_NO_BASKET_FOUL`** — a measured **wrong-way** lever (#028):
  trimming it *raises* points.
- **Do NOT re-open §3.12's `FOUL_MULT_*`** (settled on realism, #030 G) or touch
  `pickDefender`'s `individualDefense` weighting (#031 A).
- **Do NOT re-center points/FG%** — §3.16 owns it and the targets are CONTESTED
  ([calibration.md](calibration.md)).
- **Do NOT change the existing foul roll** — a flagrant is an *additional* roll on
  a foul that already happened (user call, #034 A). `isFoul` / `isAndOne` /
  `resolveReboundFoul` keep their rates, inputs and RNG draws; a test pins that
  `isFoul`'s RNG consumption is unchanged.
- **Do NOT vary `PERSONAL_FOULS_PER_TEAM_GAME` as if it were a tunable** (#034 G) —
  it is a **measured** assumption about the engine's current foul rate, and it is the
  divisor behind the flagrant probability. §3.16 (or any pass moving the foul rate)
  must revisit it deliberately; a §3.15 profile must not vary it independently.
- **Do NOT back-solve §3.14a's technicals constant** against the ~5% harness gap —
  that is #032 B2's documented nominal-vs-actual pace effect, not drift.
- **Do NOT add a stored `flagrant2` / `ejected` flag** (#034 F) — `flagrantTwos >= 1`
  is a monotonic counter, so the predicate stays **derived**. The stored-state
  exception predicted by #031 H / #032 F does not arise and is **retired**.
- **Do NOT exempt the flagrant retention from `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`,
  and do NOT raise the BASELINE cap** (#034 B). 3→5 is a parked idea needing its own
  recalibration pass ([ideas.md](ideas.md)). ⚠ **§3.15 makes this constant PROFILABLE**
  (#035 C) — the fence protects the **baseline value**, which a profile cannot touch, so
  an era profile setting it is fine and does **not** unpark the tuning idea.
- **Do NOT add 2 FTs on top of the underlying foul's award** (#034 C) — the flagrant
  **replaces** it. A flagrant stopped THREE is **2** FTs, not 3 and not 5. This is
  the likeliest bug in the pass.
- **Do NOT add a `flagrant_fouls` box-score column** (#034 H) — a flagrant is already
  inside `fouls`, so #033's parity argument does **not** reach it.
- **Do NOT reuse `pickTechnicalFreeThrowShooter`** (#034 D) — its premise is that
  nobody was fouled (#032 G); on a flagrant somebody was.

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
  `GameEvent`) · §3.12 **#030** (~20 3PA/team/game vs the NBA's ~35, a §3.16 input)

**Other files own these outright:**

- **§3.16 (recalibration)**, and the phase sequence generally → [roadmap.md](roadmap.md)
- **Calibration targets** → [calibration.md](calibration.md), **the source of truth**.
  Update it *and* the `CalibrationHarness` `(target ~N)` strings together.
- **Infra/tooling/data-hygiene chores**, and **the `decisions.md` condense pass** (a
  **gate on starting Phase 4**, run after §3.16) → [backlog.md](backlog.md)
- **Untriaged ideas**, incl. the parked cap 3→5 tuning idea and strategic substitution →
  [ideas.md](ideas.md)

**Standing facts (true every phase, not deferrals):**

- `CalibrationHarness` is disabled by default and lives in the `sim` test sources; run
  it with `-Dcalibration=true -DcalibrationSeed=NNNN`. **Re-run after any `SimConfig`
  change.** From §3.15 the sim profile is selected by the ordinary Spring profile list —
  `-Dspring.profiles.active=test,nineties` (#035 F).
- **Do NOT touch `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`'s baseline value** without its
  own recalibration pass (#034 B) — though §3.15 makes it *profilable* (#035 C).
