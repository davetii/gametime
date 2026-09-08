# Ideas / Parking Lot

**Untriaged "future improvement" ideas** — thoughts worth not-losing that don't
(yet) have a phase home or a committed decision. Entries here are **not planned
work**, carry no commitment, and may be promoted, reshaped, or dropped.

Routing: planned features with a phase → [roadmap.md](roadmap.md) (promote and delete
from here); infra/tooling chores → [backlog.md](backlog.md); choices already made →
[decisions.md](decisions.md); active risks → [risks.md](risks.md). An entry that
sharpens into "feature X, consumed by phase Y" belongs in the roadmap. **Prune
ruthlessly so this doesn't rot into a graveyard** — a shipped idea is deleted, not
annotated.

⚠ **Phase 3 is fully shipped (§3.1–§3.22).** Older notes here pointed at "§3.19
recalibration" as a natural home; that pass has landed (#042). Anything still parked
now needs its own sub-phase or a Phase 4+ home.

---

## Gameplay / simulation

- **Offense Opportunity Event**
  The notion i am touching at here is whats been discussed in other places. A defensive play doesnt produce offense when it should.  
  For example a steal or or turn over MIGHT result in a fastbreak. 
  Instead today a steal or turnover results in moving the team with posession to the top of an offensive posession.
  but what about a fastbreak, this about this concept some more the offense opportuntiy event ( good defense creating offense). 
  Its a good way to reward good defenses.

- **Raise the offensive-retention cap from 3 → 5.**
  `sim.max-offensive-retentions-per-possession` bounds `PossessionEngine`'s second-chance
  `while(true)` loop. The realism argument: a genuine scramble can run longer than three
  retentions, and a hard wall makes that **impossible** rather than merely unlikely.

  **⚠ FIRST STEP IS A MEASUREMENT, NOT A TUNE — nobody has ever measured how often the
  cap actually BINDS.** A cap of 3 already permits **four** attempts in one possession, so
  the wall may be hit on a fraction of a percent of possessions. Instrument how often
  `capReached` is true and how the retention count is distributed, **before** costing
  anything. The measurement decides the idea both ways:
  - **If it binds rarely**, the realism gain is invisible while the aggregate cost lands on
    every game — a bad trade at any calibration budget, and the idea dies cheaply.
  - **If it binds often**, that is itself a finding: the cap would be doing **calibration
    work**, not guarding a tail, and raising it is a bigger change than this entry assumes.

  **Why it is parked — the cap fires on the COMMON path, across EIGHT retention channels**
  (the eight sites enumerated in the loop-restructure entry below), so raising it adds
  offensive rebounds, **attempts and points** to every game, not just to the rare long
  scramble.
  ⚠ **The calibration budget is spent, and FGA is over-determined.** FGA sits at **89.40
  against a sourced 89.1** — landed, but **+0.30 OVER**, with §3.20/§3.21/§3.22 each
  re-landing it on the same lever (`base-no-basket-foul`, now 0.178). There is no reserve
  to spend. Worse, **FGA and Fouls are over-determined through that ONE lever**: each extra
  foul costs **1.49 FGA**, Fouls already sit **−0.55 under** target and cannot be closed
  without un-landing FGA, and pulling the lever again moves **foul-outs** (0.386 against a
  ∼0.1–0.25 ballpark — sized, accepted, *do not chase*). So the usual
  "raise it, then buy the attempts back" move has **no clean buy-back**.
  ⚠ **Offensive rebounds are already long**: **11.90 against 11.3** (residual **+0.60**,
  reported not tuned). This idea pushes the row that is already the wrong side of target.
  ⚠ **A profile setting the cap is fine and does NOT unpark this** (#035 C): the fence
  protects the *baseline* value, nothing is tuned against a non-baseline profile, and higher
  offensive-rebound rates are a real 1990s trait. Raising the shipped **default** still
  needs the pass. Loop termination is guaranteed at any value.

- **Restructure `resolvePossession`'s second-chance loop — behavior-free.**
  **⚠ THIS IS A CODE-QUALITY IDEA, NOT A GAMEPLAY OR FIDELITY ONE.** It fixes nothing a
  player or an API consumer could observe: no event changes, no rate moves, no number in
  [calibration.md](calibration.md) shifts. The entire payoff is **maintainability** — the
  cost of adding the *next* retention path, and the odds of getting it wrong. Judge it on
  that, and never against a fidelity item competing for the same pass; they are not
  comparable goods.
  ⚠ **The #034 B trigger (a sixth retention path) HAS FIRED — there are now EIGHT**,
  added across §3.21/§3.22. The entry previously said five; that was written before the
  live-free-throw paths existed.

  **The eight sites, each an `offensiveRetentions++` at a different depth of one method:**
  1. **Ordinary offensive rebound** (§3.3) — the common path, off a missed shot.
  2. **Block recovery** (§3.7) — the offense gathers its own blocked shot.
  3. **OOB-offense** (§3.8) — ball out of bounds, offense retains. *(Shares site 2's
     branch; sets no putback candidate — no board ran.)*
  4. **Rebounding foul** (§3.10) — an offensive board off the bonus trip's live last FT.
  5. **Flagrant on a stopped shot** (§3.14b) — the ball returns **by rule**.
  6. **Flagrant on a made shot** (§3.14b) — same, on the made-basket branch.
  7. **Bonus free throws** (§3.21) — offensive board off the missed last FT.
  8. **Shooting-foul FTs / and-1** (§3.21) — same, off the shooting-foul and and-1 trips.

  **What the repetition actually costs — two concrete leaks, not just aesthetics:**
  - ⚠ **The cap is enforced two different ways.** Five sites guard with
    `if (result.offenseRetains())` — the cap having been pushed *down* into the helper via
    a `capReached` argument — while sites 2, 5 and 6 test `!capReached` inline at the call
    site. Both are correct today, but **a ninth path has two conflicting precedents to
    copy**, and only one of them keeps the cap decision near the helper that already takes it.
  - ⚠ **§3.22 had to touch six of the eight to thread ONE new variable.** `putbackCandidate`
    (#044 C) is set at sites 1, 2, 4, 7 and 8, and must be **deliberately left null** at the
    two flagrant sites — the ball comes back by rule, no board ran, nobody secured it. That
    is a per-site invariant a reader has to verify eight times, and each site carries a
    comment explaining why it does or doesn't set it. **A single retention point would state
    it once.**

  **The benefit, stated plainly:** one place where "the offense kept the ball" is expressed
  — one cap check, one putback-candidate rule, one `continue` — so the next retention path
  is a call, not a new copy of a four-line protocol with two precedents and an invariant to
  re-derive.

  **Why it stayed parked, and what still holds:** the loop is correct and every path is
  tested, so the rewrite touches shipped §3.7/§3.8/§3.10 lines for **zero behavior change**
  — the churn a future `git bisect` has to walk through. That cost is unchanged; what has
  changed is the benefit side, which has grown by three paths and a threaded variable.
  ⚠ **Verify it moved nothing the CLAUDE.md way**: run the harness, `git stash`, run again
  on the **same seed**, diff — identical line for line, or the claim is false.
  ⚠ **§3.16's two foul branches did NOT add sites** — both *end* the possession, so they are
  not `continue` paths. Worth knowing, because a reader counting new foul branches might
  think they did.
  ⚠ **Must not ride a tuning pass** — a control-flow change landing alongside a
  recalibration blurs what moved a number.

- **Fights / altercations.** *(Technicals and flagrants shipped as §3.14a/§3.14b.)*
  Still wants a temperament trigger that does not exist, and #032 C **declined to create
  one**: the engine consumes skills, not attributes, and `foulProne` already carries the
  aggression/composure composite. A dedicated temperament axis is the genuine open
  alternative, blocked on the #014/#017 "no attribute ahead of its consumer" discipline.
  Value is play-by-play texture, not aggregate fidelity.

- **Strategic substitutions — the engine has exactly two.** Built: fatigue (§3.5),
  foul-out (§3.5, forced), foul trouble (§3.13/#031 — the first strategic one), ejection
  (§3.14a/b, forced). Missing: matchup/going small, hot hand, closing lineup/garbage
  time, score-aware rotation. **They are one idea, not four, because they share one
  missing prerequisite: the rotation step has no game-situation awareness** —
  `advancePossession()` isn't even passed the `period`, let alone the margin. Add score +
  time and several become reachable at once. Fatigue got built first only because §3.5
  already had `currentEnergy`, not because it mattered most.
  **A deeper version of the gap:** the starting five come from `lineupRole` roster data
  (#014) and never change composition *by choice* — the coach swaps individuals out of a
  fixed lineup but never picks one. #031 C's declined period-aware threshold and the
  reinsertion rule's declined score-awareness are symptoms of the same absence.
  **If promoted:** extend #031's template (probabilistic, coach-scaled,
  player-value-weighted, sticky-sit/earned-return) rather than inventing a parallel
  mechanism (#031 H). Needs a design pass starting with the game-situation plumbing.

- **TALENT SPREAD as a profile axis — one knob currently spread across 15
  `*_SENSITIVITY` constants.** A base rate says *where the league sits*; a sensitivity
  says *how far apart players within it are*
  (`probability = base + SENSITIVITY × (skillA − skillB)/10`). Low compresses the league
  (roster quality hardly matters), high stretches it (stars separate). **"Superteam era"
  vs "parity era" is real and this is the only lever that expresses it** — attributes come
  from seed data on a fixed 1–20 scale.
  **Why not profilable (#035 C):** exposing 15 constants invites turning one in
  isolation — the §3.7 mistake. At the global 0.5, a 14-rated rim protector vs a 10-rated
  finisher blocked **~23%** of shots against a real ~5–6%, which is why
  `BLOCK_SENSITIVITY = 0.12` exists at all; a profile author raising it for a "defensive
  era" would break the base-dominant property it was created to protect. Same for
  `REBOUND_FOUL_`/`AND_ONE_SENSITIVITY` (0.10) — thin bases need gentle slopes.
  **What would make it real:** a design pass treating spread as **one knob** — most
  plausibly a single multiplier across the family, preserving the established ratios.
  Needs its own calibration (it moves every rare-event rate at once). Until then the
  honest era knobs are the profiled base rates.

- **Coach competence in rotation decisions.** The §3.5 rotation reads only
  `rotationDepth` / `substitutionAggressiveness` — both *style* axes; there is **no coach
  quality axis**, faithful to #018 (coaching is continuous style, not a scalar rating)
  and to §3.5 C (subs read state, they don't roll). Needs **two** things, not one: a new
  `Coach` attribute (schema + `coach.csv` + `EntityMapper`, landing *with* its consumer
  per #014/#017), and a definition of what a *better* sub is mechanically — the model has
  no quality dimension to improve. **Natural home:** Phase 6 (coach progression/hiring).

- **`DOUBLE_DRIBBLE` as a tenth `TurnoverCause`** *(#041 follow-up)*. The §3.9 taxonomy
  has nine causes (#027 B) and this real, common live-ball turnover isn't among them.
  **Structurally free**: one enum constant, one weight, `outcome` rides the existing
  `TURNOVER` event as free text (#020) — no schema, OpenAPI, or new event, and no second
  event under #041 C. ⚠ **Not numerically free**: cause weights re-partition a *frozen*
  turnover count (#027 A), so a tenth cause takes share from the nine — possibly from
  `STOLEN`, moving the steal rate (7.72, #041 F). **What would make it real:** a pass that
  already owns rate movement and can absorb the re-partition in one place.

- **The charge-drawer as a counterparty** *(#041 follow-up)*. A charge
  (`TurnoverCause.OFFENSIVE_FOUL`) has a real second participant — the defender who drew
  it — and under #041 A he is a textbook `opponent_player_id`. The engine never picks
  him, so the column is **deliberately null** on both of the charge's events.
  **Why not built:** a new `pickChargeDrawer` draw **shifts the RNG stream** and
  re-baselines every seeded sim test, for a participant nothing consumes
  (#014/#017/#020). **What would make it real:** a consumer — a "charges drawn" stat, or
  a pass wanting the drawer's `acumen` to bend the charge rate. Then it is a clean
  standalone: one draw, one existing column, one deliberate re-baselining.

- **The roster's FT-skill distribution** *(#042 C follow-up)*. `sim.ft-base` had to come
  down to **0.705** to land 78% realized FT%, because realized FT% is
  `ftBase + 0.20 × (freeThrows − 10)/10` and the generated population's mean
  `freeThrows` sits near **13.8** — well above the avg-10 scale midpoint. **The constant
  is correcting a population effect, globally**, making every FT harder for every player.
  This is player-generation, not sim-config, so §3.20 fixed the symptom and parked the
  cause. ⚠ **Likely not confined to `freeThrows`** — every other `base-*` may be
  absorbing the same bias invisibly. **What would make it real:** a player-generation
  pass, or simply measuring the generated population's mean per skill against the avg-10
  scale — cheap, and it would say at once whether this is one skill or systemic.

- **A per-shot-type FG% instrument in `CalibrationHarness`.** The harness reports FG% **in
  aggregate only**, so `base-drive` / `base-post` / `base-perimeter` can be tuned only
  against the aggregate — realized rim / post-up / mid-range make rates are invisible.
  §3.20 set their split against real separation (rim ~66%, mid-range ~42–45%) as an
  *argument* and could not verify it. ⚠ **This is §3.17's blind spot one level down** —
  the shot MIX only became a calibration surface once the harness printed its shares; make
  rates are the same problem. Test-side only, no engine surface.

- **A rim-protection era profile.** Dial blocks up to ~5.5 (baseline lands 4.58 against a
  sourced 4.8) as a stylistic delta over `baseline`, same shape as the parked 1990s /
  three-heavy profiles (#035). ⚠ **Must NOT be done by moving baseline** — it is tuned to
  Basketball-Reference 2025-26, and bending a sourced target makes every later pass read
  a target that is not one. ⚠ **Two mechanical traps:** (1) **a block does NOT reduce
  FGA** — `recordFieldGoalAttempt()` fires *before* the block fork, so a block is a
  charged miss; it moves FG%/2P%, not attempts. (2) **`base-block-three` is INERT** —
  `PROB_FLOOR` (0.02) is 4× it (0.005), so for the ~41% of attempts that are threes the
  rate is **floored, not based**; more blocked threes must reroute through
  `clampRareProbability` first. Values-only once the floor question is settled.

- **Derive the box score FROM the event log, as the single source of truth**
  *(risk write-up in [risks.md](risks.md); Phase 4 carries a pointer)*. Every stat is
  written **twice** — `PlayerGameState.record*()` and the `GameEvent` for the same play —
  agreeing **only by convention**, because each call site remembers to do both.
  ⚠ **The motivating consumer is SINGLE-GAME SUMMARIZATION, not leaderboards**: a box
  score sits next to the play-by-play the user can also read, so a disagreement is
  *visible* rather than buried in a season aggregate. ⚠ **The blocker is `minutes`** — it
  has no event behind it (a possession-share projection, #023 A), so full derivation needs
  substitution/possession events or minutes kept deliberately non-derived.
  ⚠ **Not scheduled and not a gate** — the acute bug is fixed; whether the refactor is
  worth it is open.

- **⚠⚠ SKILL SENSITIVITY IS ~10× TOO STEEP, AND CALIBRATION CANNOT SEE IT — the harness
  only ever runs AVERAGE-vs-AVERAGE rosters** *(measured 2026-08 by a throwaway probe,
  since deleted). Average offense fixed at skill 10, defense varied, 100 possessions ×
  300 runs:*

  | defense skill | steals | blocks | forced TO | opp points | **opp FG%** |
  |---|---|---|---|---|---|
  | 4 (terrible) | 1.3 | 2.0 | 2.3 | 202 | **79.8%** |
  | 10 (average) | 3.4 | 3.4 | 6.1 | 125 | **47.7%** |
  | 16 (elite) | 20.8 | 6.3 | 36.9 | 31 | **16.2%** |

  ⚠ **A 63-point FG% swing against a real NBA team-defense spread of ~5 points (44–49%)**;
  forced turnovers span 2.3 → 36.9. The response to skill is roughly an order of magnitude
  too steep.
  ⚠ **Why no calibration pass caught it: every harness run uses `teamOf5(id, 10)`** —
  average vs average, exactly the MIDPOINT of the curve, where every number looks right.
  §3.4–§3.22 tuned the **intercept** and never once tested the **slope**. Same class as
  §3.17's blind spot: a green midpoint hiding a wrong gradient.
  ⚠ **The consumer that will expose it is PHASE 5** — season play puts real spread against
  real spread; good teams will beat bad teams by impossible margins and standings will be
  degenerate.
  ⚠ **It is a TUNING problem, not a rebuild** (suspects: the global `SENSITIVITY` 0.5 and
  the per-contest ones), but **it needs a new instrument first**: a harness mode running a
  skill LADDER and reporting the response curve. **Do not re-tune against the
  average-vs-average rows — those are landed and would not move.**

- **⚠ FGA HAS NO LEVER OF ITS OWN — it is bought against Fouls, and the pair is
  OVER-DETERMINED THROUGH ONE KNOB.** Not a mis-tune: **eight of #042's constants cannot
  reach FGA at all.** #042 B modelled `non-shooting-foul-share` at −0.62 FGA per 0.05 and
  **measured +0.03 — nothing**; both foul branches `return` before
  `recordFieldGoalAttempt()`, so re-partitioning fouls between them cannot touch attempts.
  A **ninth** constant (`sim.base-no-basket-foul`) was pressed in to land FGA, and it works
  only by changing how often a possession ends with **no attempt at all**.
  ⚠ **That same constant sets the foul rate, so two targets share ONE degree of freedom** —
  ~**1.49 FGA per foul**. It is arithmetically impossible to land both: FGA is landed at
  **89.40 (+0.30)** *because* Fouls is accepted at **19.35 (−0.55)**; closing Fouls would
  drop FGA to ~87.8. Pulling the lever also moves **foul-outs** (0.386 vs a ~0.1–0.25
  ballpark). **One of the two rows is permanently a residual, and which one is a CHOICE.**
  ⚠ **Why it is structural, not an oversight — it follows from the entry below.**
  Possession count is fixed by config before tip-off, so there are only two ways to vary
  attempts: **end a possession without one** (this lever) or **run a possession twice** (the
  retention loop). That is the whole set. It is why the retention-cap idea above has no
  clean buy-back, and why **three consecutive phases (§3.20/§3.21/§3.22) all spent this same
  knob**.
  **What would make it real:** a **second, independent FGA lever** — realistically
  pace-as-outcome (letting possession count vary), which is the alternation entry below and
  a large change. ⚠ **Not a gate and not a bug** — the engine is correct and the residual is
  sized and accepted. **Recorded because it PRICES EVERY FUTURE PASS THAT MOVES ATTEMPTS**
  (a retention-cap raise, more putbacks, a transition play type): each arrives with no clean
  buy-back and must pay in Fouls or foul-outs. Do not rediscover this by re-modelling a
  coupling that does not exist — the facts are in
  [engine-traps.md](engine-traps.md); this entry records that the *fix* is unowned.

- **Defense gets EFFICIENCY but not VOLUME — possessions strictly ALTERNATE.**
  `GameSimulator`'s loop is `homeOnOffense = (poss % 2 == 0)`, so each team's possession
  COUNT is fixed by config before tip-off. Only the retention loop varies anyone's attempt
  count, so offense can extend itself and defense cannot generate a possession: a steal, a
  defensive rebound and a made basket all yield the possession the defense was getting
  anyway. ⚠ **Recorded as the NARROW claim, because the broad one is false** — defensive
  skill pays off hard (see the table above), just through *how well* each possession goes,
  never *how many*. In real basketball a live-ball steal in transition is worth more
  partly through what it leads to; that second channel does not exist here.
  ⚠ It is also why `Pace` is a config INPUT rather than an emergent result, and why FGA
  has only one real lever (`base-no-basket-foul`, via possessions ended without an
  attempt) — the mechanism behind the FGA/Fouls over-determination.
  **What would make it real:** a transition play type, or pace-as-outcome.
  ⚠ **Not a bug and not a gate** — alternation is a deliberate simplification (#023 B) and
  every calibrated row is landed under it.

- **⚠ A POSSESSION HAS NO CLOCK — "time" exists only as a possession COUNT, and this is
  the single largest modelling absence in the engine.** `PossessionEngine` contains no
  notion of seconds: `SHOT_CLOCK_VIOLATION` is a weighted draw, minutes are a
  possession-share projection (#023 A), and overtime is `otPossessionsPerPeriod` — *some
  more turns*, not five minutes. Downstream of that one absence:
  - **No endgame** — no intentional fouling when trailing, no two-for-one, no holding for
    the last shot. **The last possession of a close game is simulated identically to the
    first.**
  - **`clutch` is DEAD.** ⚠ Verified: calculated (`ClutchSkillCalculator`), mapped, stored,
    and **zero references in the entire `sim` package** — there is no "late and close" to
    key off. ⚠ **This is the ROOT CAUSE of one of the four dead skills
    [player.md](player.md) lists**; that file records the symptom, this one the why — keep
    the two consistent.
  - **No score-awareness** — a team down 20 and up 20 play identically.
  - **Pace cannot be a strategy** — it is a config input (see above).

  ⚠ **It is complicated because it is not one feature, it is a new AXIS.** Every phase so
  far added a *branch* to a possession and the model absorbed it. A clock changes what a
  possession **is**: it acquires a duration, possessions stop being interchangeable, and
  the possession COUNT stops being an input and becomes an emergent result. ⚠ **That
  reaches [calibration.md](calibration.md) directly** — `Pace (poss/48)` is hit **by
  construction** today and would have to be *earned*; every per-possession rate would need
  re-reading.
  ⚠ **THE HONEST MIDDLE PATH: most of the VALUE is in score-and-time AWARENESS, not a real
  clock.** A possession knowing roughly where it sits (period, possessions remaining,
  margin) lights up `clutch`, intentional fouling and late-game shot selection **without**
  variable-length possessions or emergent pace. **Price the two halves SEPARATELY** —
  conflating them is what makes this look like an all-or-nothing rebuild.
  **What would make it real:** a user-facing consumer — a timestamped play-by-play, or a
  late-game narrative someone wants to read (Phase 7 is the closest). ⚠ **Not scheduled
  and not a gate.** Recorded so the next reader knows `clutch` is inert **by omission,
  not by bug**.
