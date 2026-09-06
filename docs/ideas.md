# Ideas / Parking Lot

**Untriaged "future improvement" ideas** — thoughts worth not-losing that don't
(yet) have a phase home or a committed decision. This is the deliberately-informal
bucket: entries here are **not planned work**, carry no commitment, and may be
promoted, reshaped, or dropped later.

## How this differs from the other docs (keep the boundaries crisp)

- **[roadmap.md](roadmap.md)** — *planned* product features with a phase home
  (incl. §3.x deferred sim-fidelity, each attached to its consuming phase). Once an
  idea here has a clear consumer + a phase, **promote it to the roadmap** and delete
  it from here.
- **[backlog.md](backlog.md)** — infra/tooling/data-hygiene chores that aren't a
  product feature. Gameplay ideas do **not** go there.
- **[decisions.md](decisions.md)** — choices already made. An idea that becomes a
  real design choice graduates to a numbered decision.
- **ideas.md (this file)** — the "I had a thought" stage, *before* any of the above.

**Triage discipline:** an entry that sharpens into "feature X, consumed by phase Y"
belongs in the roadmap, not here. If an idea can't even name what would consume it,
that's a sign it's still a genuine parking-lot item (fine) — or too vague to be
actionable yet (also fine to leave, or drop). Prune ruthlessly so this doesn't rot
into a graveyard.

---

## Gameplay / simulation

- **Raise `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` from 3 → 5 (realism of long
  second-chance scrambles).** The cap bounds the second-chance `while(true)` loop in
  `PossessionEngine` — after N offense retentions the next miss is *forced* to a
  possession-ending outcome so the loop terminates. It sits at a **§3.3-era calibrated
  3**. **⚠ The number of paths sharing that one cap keeps growing, which raises this
  idea's blast radius every time:** the offensive rebound (§3.3), §3.7's
  offense-recovered block, §3.8's OOB-offense, §3.10's defensive rebounding foul, and —
  since §3.14b shipped (`decisions.md` #034 B) — **the flagrant retention, making five**.
  So a 3→5 change now moves five channels at once, not three. (**The constant was
  renamed `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` → `MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`
  in 2026-08** — a pure rename, value untouched at 3; that and the loop's growing
  re-entry count are a separate, behavior-free concern in its own entry below.) The realism argument for a higher value is real: a
  genuine scramble *can* produce a long chain of tip/put-back attempts — rare, but
  possible — and a hard wall at 3 makes it impossible rather than merely unlikely.
  **Why it's parked, not done:** the cap is not just a rare-tail guard — it fires on
  the common path, so raising it lets more second-chance possessions run to
  completion, adding **offensive rebounds, shot attempts, and points** across *every*
  game (not only the rare 8-tip possession). That moves the harness aggregates
  (§3.14b landing: **118.3 pts / 46.9% FG / 9.74 off reb**, with points and FG% both
  **CONTESTED** — [calibration.md](calibration.md) is the source of truth), so 3→5
  needs its **own recalibration pass** (re-center shot base rates for the added
  points). Deliberately
  **not folded into §3.8** so the OOB change is verified against a known-good
  baseline (one moving knob at a time — the §3.7 lesson). Also note the loop-
  termination *guarantee* is independent of the value (it holds at 3, 5, or 50); only
  the realism/aggregate trade-off is at stake. **Natural home if promoted:** a
  dedicated calibration/tuning pass (its own harness loop + re-agreed aggregates),
  slotting cleanly *between* sub-phases rather than inside one. **The arc now runs
  §3.7–§3.19**, and **recalibration against verified targets — now §3.19 (#038), NOT
  §3.16 — is the natural home**; it is already exactly this kind of pass.
  ⚠ **NUMBERING**: the note below said "it waits for §3.16" when §3.16 meant
  recalibration. **#038 renumbered that to §3.19.** It waits for **§3.19**.
  **Note (updated 2026-08, §3.16 close-out):** ✅ **the blocker named below has cleared** —
  the points/FG% targets were **SOURCED** by #036 (115.6 / 47.1%, Basketball-Reference
  2025-26), so they are no longer contested, and the three passes that declined the
  `BASE_*` trim were right to. **But this idea should still wait for §3.19**, for a
  *different* and now sharper reason: ⚠ **FGA has ~0.3 of headroom** (88.8 against 89.1
  real) and **§3.16 spent the rest of that budget** — #039 C made the common foul a dead
  possession purely to avoid ~0.76 FGA. **Raising the retention cap adds attempts**, so
  it now competes directly with a concession already paid for. Measure FGA first; it is
  the binding constraint, not points.
  **⚠ Note (2026-08, §3.15 design): the cap is PROFILABLE from §3.15, and that does NOT
  unpark this idea** (`decisions.md` #035 C). Excluding it from the profilable set was
  argued and rejected: **the fence protects the BASELINE value, and a profile cannot
  touch the baseline** — nothing is tuned against a non-baseline profile, and higher
  offensive-rebound rates are a genuine 1990s trait, so an era profile setting the cap
  is legitimate and needs no recalibration pass. **Raising the shipped default from 3
  still does**, exactly as described above.

- **Restructure `resolvePossession`'s second-chance loop — a BEHAVIOR-FREE cleanup,
  deliberately separate from the 3→5 tuning idea above.** *(The rename half of this
  entry is already done — see below.)* *(Raised 2026-08 after §3.14b, from reading `possession-flow.puml`: the
  diagram is a faithful 1:1 mirror of the engine — 16 branches against
  `resolvePossession`'s 17, 4 loops against its 5 `continue`s — so what looks like
  diagram complexity is the method reporting its own.)* **Two distinct pieces — the
  cheap one is DONE, the real one stays parked:**
  - ~~**Rename the cap.**~~ **✅ DONE 2026-08**, immediately after being raised, since it
    was mechanical and fully covered by the existing retention tests.
    `MAX_OFFENSIVE_REBOUNDS_PER_POSSESSION` → **`MAX_OFFENSIVE_RETENTIONS_PER_POSSESSION`**,
    with the loop counter `offensiveRebounds` → `offensiveRetentions` in the same change.
    The constant was never dead or mis-valued — it is the *only* bound on the
    `while(true)` loop and 3 is the §3.3-era calibrated number — **only the NAME was
    wrong**: of the five paths it bounds, four are not rebounds (§3.7 block recovery,
    §3.8 OOB-offense, §3.10's rebounding foul, §3.14b's flagrant retention). "Retention"
    is the word the rest of the engine already uses (`BlockRecovery.offenseRetains()`,
    `MissedShotOutcome.offenseRetains()`, `ReboundFoulResult.offenseRetains()`).
    **Verified behavior-free**: 542 tests green and the 5-seed calibration landing
    reproduced **byte-for-byte** (118.26 pts / 46.94% FG / 0.148 flagrants / 9.74 off
    reb — every per-seed value identical), so §3.15's validation gate is undisturbed.
    `PlayerGameState.offensiveRebounds` is the **box-score stat** — a different thing,
    deliberately untouched. *Rejected names:* `MAX_SECOND_CHANCE_POSSESSIONS` ("second
    chance" means offensive-rebound possessions in basketball usage, smuggling the same
    bias back in; it also undercounts, since a cap of 3 allows four attempts) and
    `MAX_LOOP_REENTRIES_PER_POSSESSION` (accurate but names the `while(true)`
    implementation rather than the domain fact — the old name's one virtue was being
    domain language, so the fix should keep that and just make it true). **Pairs naturally with the 3→5
    idea if that ever runs** — but must not wait for it, since that one is blocked on
    a contested target and this one changes no number at all.
  - **The loop restructure is the real item, and #034 B named its trigger: a SIXTH
    re-entry path.** Today there are five (offensive rebound §3.3, block recovery §3.7,
    OOB-offense §3.8, rebounding foul §3.10, flagrant §3.14b), each an
    `offensiveRebounds++; continue;` at a different depth in one long method. That the
    count grew every phase while nothing was ever removed is the signal — the same
    shape as #030's clamp-helper trigger (two sites is coincidence, a third is the
    signal). **Why it is parked rather than done now:** the loop is *correct*, every
    retention path is tested, and a restructure would touch shipped §3.7/§3.8/§3.10
    lines for zero behavior change — exactly the kind of churn that makes a future
    bisect harder. **What would make it real:** a sixth path, or a phase that needs to
    add one. **Explicitly NOT a §3.15 or recalibration job** — §3.15 was a config refactor
    gated on reproducing §3.14b's landing byte-for-byte, and recalibration (**§3.19**
    since #038, not §3.16) is a tuning pass; a control-flow restructure riding either
    would blur what moved a number.
    ⚠ **§3.16 (2026-08) added the fifth and sixth retention-adjacent sites and did NOT
    trigger this** — deliberately. Its two new branches (`COMMON_FOUL`, the charge's
    `FOUL`) both **end** the possession, so neither is a `continue` path: the count of
    *retention* sites is unchanged. **The "sixth path" trigger has not fired.** Worth
    knowing, because a reader counting new foul branches might think it had. If promoted it wants its own small pass with the existing retention tests
    as the safety net.

- ~~**Flagrant / technical fouls, and altercations (fights).**~~ **PROMOTED (2026-08)
  to §3.14, then SPLIT into §3.14a (technicals) + §3.14b (flagrants)** — *(originally
  promoted as §3.13; renumbered when foul-outs moved ahead of it by user call; split
  by decisions.md #032 A on the finding that the two share nothing but the word
  "foul")* — see roadmap.md's Possession-fidelity section. **BOTH HALVES ARE NOW
  SHIPPED (2026-08): §3.14a as #032 A–J and §3.14b as #034 A–J, each with its
  implementation note.** This is completed work, not a parked idea. Three notes from the
  reasoning that kept it here, **corrected against what the two passes actually found**:
  - **FTs *plus* retained possession** — belongs entirely to **§3.14b**: a technical
    turned out to leave the possession **completely unchanged** (#032 D), which is why
    it went first. **Now designed (#034 B) and much cheaper than predicted**: the
    second-chance `while(true)` loop already *is* the "same team, run it again"
    machine, so retention is the same `offensiveRebounds++; continue;` §3.7/§3.8/§3.10
    use, under the same cap — five lines, not a new mechanism. **Built exactly that way**,
    at all three foul sites.
  - **A chosen FT shooter** — resolved as a **deterministic highest-`freeThrows`
    on-the-floor pick**, a second rule beside the untouched `foulDrawing`-weighted
    draw (#032 G).
  - **The ejection needing stored state** — **the prediction was wrong OUTRIGHT, and
    saying so is the most useful thing these two design passes produced.** §3.14a found
    it false for the two-technical case (`technicalFouls >= 2`, a monotonic counter, so
    #023 F applies unchanged — #032 F) but still expected it to hold for a flagrant-2.
    **§3.14b found it false there too** (#034 F): a flagrant-2 **ejects immediately**, so
    there is no threshold to remember and `flagrantTwos >= 1` is monotonic just like the
    other two. **No stored flag anywhere; the #028 D-pattern argument was never needed.**
    The general finding: **every disqualification in this model is absorbing and
    monotonic**, which is precisely the shape #023 F's derivation was built for — so the
    exception should not be predicted again without a mechanic that is genuinely
    *non*-monotonic (the `inFoulTrouble` test, #031 E). All three causes extend §3.13's
    hard/forced tier via `eligible(...)` rather than adding a parallel mechanism (#031 H).

  **Fights/altercations remain PARKED** and are in neither §3.14a nor §3.14b — they
  still want a temperament trigger that does not exist, and #032 C **declined to
  create one**: the engine consumes skills, not attributes, and `foulProne` already
  carries the aggression/composure composite. A dedicated temperament axis stays the
  genuine open alternative, blocked on the #014/#017 "no attribute ahead of its
  consumer" discipline. Note these are **rare events** whose value is play-by-play
  texture + the occasional star ejection, not aggregate fidelity — confirmed by
  measurement: §3.14a was budgeted at **+0.26 points/team/game against a ±1.5 noise
  band** and landed inside it, so it earned **no** recalibration pass the way
  §3.10/§3.11 did (#032 I).

- **Strategic substitutions as a category — the engine now has exactly two, and
  §3.13 shipped the second.** Noticed during §3.13's design pass (user observation):
  it was striking that a *fatigue* sub existed while "sit him so he doesn't foul out"
  did not. Listing what a real coach actually decides makes the gap systematic
  rather than incidental:

  | Sub reason | Engine | Kind |
  |---|---|---|
  | Fatigue | ✅ §3.5 | reactive |
  | Foul-out | ✅ §3.5 | rule, forced |
  | **Foul trouble** | **✅ §3.13 (#031)** | **strategic — the first** |
  | Ejection | **✅ §3.14a (technicals, #032 F)** / §3.14b (flagrant-2) | rule, forced |
  | Matchup / going small | ✗ | strategic |
  | Riding a hot hand | ✗ | strategic |
  | Closing lineup / garbage time | ✗ | strategic |
  | Score-aware rotation | ✗ | strategic |

  **Why fatigue got built first is worth recording, because it was availability, not
  a judgement that it mattered most.** §3.5 already had `currentEnergy` as a
  continuous, always-present number, so a threshold rule fell out of it nearly free.
  Foul trouble needed the counter *plus* a notion of consequence, and nobody had
  measured the consequence — that is #030 G's whole story (the instrument came first,
  then the behavior). The remaining strategic subs are all blocked on the *same*
  missing prerequisite, which is what makes this one idea rather than four:
  **the rotation step has no game-situation awareness at all.** `advancePossession()`
  is not even passed the `period`, let alone the score margin. Add score + time to
  the rotation step and several of these become reachable at once — a better-shaped
  future phase than picking off matchup subs alone.

  **This is also where two deliberate §3.13 deferrals actually belong.** #031 C
  declined a period-/time-aware foul-trouble threshold and the reinsertion rule
  declined score-awareness — both framed there as foul-trouble limitations, but they
  are really symptoms of this same absence.

  **A deeper version of the gap:** the starting five come from `lineupRole` roster
  data (#014, sticky persistent state) and **never change composition by choice** —
  the coach swaps individuals out of a fixed lineup but never *picks* one. So
  "strategy subs" is missing at a level below any individual rule.

  **What §3.13 leaves behind that makes this cheaper later:** #031's rule is a
  reusable *template* — a probabilistic, coach-scaled, player-value-weighted sub
  decision with a sticky-sit/earned-return shape. A later strategic sub should extend
  that shape rather than invent a parallel one (the same discipline #031 H imposes on
  §3.14's ejections). **Natural home if promoted:** its own numbered sub-phase after
  the §3.7–§3.18 arc, or Phase 4+ alongside a richer coach model — it needs a design
  pass of its own, starting with the game-situation plumbing. **Not planned work.**

- **TALENT SPREAD as a profile axis — one conceptual knob currently spread across 15
  `*_SENSITIVITY` constants.** *(Raised 2026-08 during §3.15's design pass, from a user
  observation: playing with the sensitivities "would adjust the span of talent between
  bad, mediocre and great players." That is exactly what they do, and it is a better
  description than the "model machinery" label #035 C files them under.)*
  **The two axes are genuinely different.** A base rate says *where the league sits*
  (how often anyone blocks a shot); a sensitivity says *how far apart the players within
  it are* — `probability = base + SENSITIVITY × (skillA − skillB)/10`. Low sensitivity
  compresses the league (a 20-rated rim protector barely out-blocks a 10-rated one, so
  roster quality hardly matters); high sensitivity stretches it (stars separate, roster
  construction dominates). **"An era of superteams" vs. "a parity era" is a real
  distinction, and this is the only lever that expresses it** — player attributes come
  from seed data on a fixed 1–20 scale, so the talent span cannot be widened by changing
  players, only by changing how strongly the engine reads the gaps.
  **Why it is NOT profilable in §3.15** (#035 C, and the reason is the useful part):
  it is **one idea spread across 15 constants** (`SENSITIVITY`, `BLOCK_`, `REBOUND_FOUL_`,
  `AND_ONE_`, `TO_CAUSE_`, `COACH_`, `ASSIST_`, `ACUMEN_`, `TEAM_EFFICIENCY_`, `FT_`,
  `ENDURANCE_DRAIN_`, `FOUL_TROUBLE_VALUE_` …). Exposing them individually invites
  turning **one** in isolation, which is precisely the §3.7 mistake: at the global 0.5,
  a good rim protector (14) vs an average finisher (10) blocked **~23%** of shots against
  a real ~5–6%, so skill alone drove blocks 2–3× over target *regardless of the base
  rate* — which is why `BLOCK_SENSITIVITY = 0.12` exists at all. A profile author raising
  it to build a "defensive era" would not get more defense; they would break the
  base-dominant property the constant was created to protect. Same story for
  `REBOUND_FOUL_SENSITIVITY` and `AND_ONE_SENSITIVITY` (both 0.10) — thin bases need
  gentle slopes.
  **What would make it real:** a design pass that treats talent spread as **one knob** —
  most plausibly a single multiplier applied across the family, so the ratios the phases
  established (blocks gentler than the global, and-1s gentler still) are preserved while
  the whole spread widens or narrows together. That needs its own calibration, because
  widening the spread changes every rare-event rate at once. **Natural home if promoted:**
  its own sub-phase after the Phase-3 tail (i.e. after **§3.19**, not §3.16 — #038
  renumbered recalibration), or Phase 4+ alongside player-facing eras — it is a
  *gameplay* axis (leagues that feel different), not a tuning convenience. **Not planned
  work.** Until then the honest era knobs are the base rates, which §3.15 does profile:
  `BASE_BLOCK_*`, `BASE_NO_BASKET_FOUL`, `FOUL_MULT_*`, the shot `BASE_*`.

- **Coach competence in rotation decisions.** The §3.5 fatigue rotation reads only
  the coach's `rotationDepth` / `substitutionAggressiveness` (both *style* axes) —
  there is **no coach *quality* / wisdom axis**. This is faithful to decisions.md
  **#018** (coaching is modeled as continuous *style* attributes, not a scalar
  "how good is this coach" rating) and to §3.5's rule-based, deterministic sub logic
  (Decision C — subs read state, they don't roll; there is no notion of a sub being
  "right" or "wrong"). If "a wiser coach makes better substitution decisions" ever
  becomes desirable it needs **two** new things, not one: (a) a new `Coach` attribute
  (schema + `coach.csv` seed + `EntityMapper` wiring — and per the #014/#017 "no
  attribute ahead of its consumer" discipline, it should land *with* its consumer),
  and (b) a definition of what a *better* sub actually is mechanically (the current
  model has no quality dimension to improve). **Natural home if promoted:** Phase 6
  (coach progression/hiring), where a coach quality/development dimension first gets
  a real consumer — decisions.md #018 already notes the continuous model keeps
  progression open at zero cost today.

- **`DOUBLE_DRIBBLE` as a tenth `TurnoverCause`** *(raised 2026-08 during §3.18's design
  pass; parked by `decisions.md` #041's follow-up list)*. The §3.9 taxonomy has **nine**
  causes (#027 B) and double dribble is not among them, though it is a real and common
  live-ball turnover. **Structurally it is free**: one enum constant on `TurnoverCause`,
  one weight in the properties file, and the `outcome` string rides the existing
  `TURNOVER` event as free text (#020) — **no schema change, no OpenAPI change, no new
  event**, and under #041 C it earns no second event either (one actor, one fact, no
  second stat). ⚠ **It is NOT numerically free, which is why it is parked rather than
  scheduled.** The cause weights **re-partition a frozen turnover count** (#027 A: the
  gate is untouched, only the mix moves), so a tenth cause necessarily takes share from
  the existing nine — **possibly from `STOLEN`**, which would move the steal rate #041 F
  measured at **7.72** and routed to §3.19 as a derived quantity. **What would make it
  real:** a pass that already owns rate movement — §3.19 (recalibration) is the natural
  home, since it re-solves the turnover count and the cause mix together and would
  absorb the re-partition in one place rather than perturbing a settled number. Adding it
  in an attribution/parity pass is what #041 explicitly declined to do.

- **The charge-drawer as a counterparty** *(raised 2026-08 in §3.18's design pass;
  `decisions.md` #041 follow-up)*. A charge (`TurnoverCause.OFFENSIVE_FOUL`) has a real
  second participant in basketball — the defender who planted his feet and drew it — and
  under #041 A's contract he is a textbook `opponent_player_id` (opposite team from the
  committing ball-handler). **The engine never picks him**: `pickCause` returns
  `OFFENSIVE_FOUL` and no drawer is selected, so §3.18 leaves the column **deliberately
  null** on both of the charge's events. **Why it was not built:** populating it needs a
  new `pickChargeDrawer` RNG draw, which **shifts the RNG stream** and re-baselines every
  seeded sim test (CLAUDE.md's standing warning) — forfeiting §3.18's defining property
  that it moves no number, for a participant **nothing currently consumes** (the
  #014/#017/#020 discipline). **What would make it real:** a consumer — a "charges drawn"
  stat in Phase 4, or a defensive-fidelity pass that wants the drawer's `acumen` to bend
  the charge rate. It is then a clean standalone: one new draw, one column already in
  place, and a deliberate re-baselining of the seeded expectations.

- **The roster's FT-skill distribution** *(raised 2026-08 at §3.20's execution;
  `decisions.md` #042 C / follow-up)*. `sim.ft-base` had to come **down to 0.705** to land
  a realized FT% of 78%, because realized FT% is `ftBase + 0.20 × (freeThrows − 10)/10`
  and the **generated population's mean `freeThrows` sits near 13.8** — well above the
  avg-10 scale midpoint. **The constant is correcting a population effect**, and it does
  so **globally**: every free throw gets harder for every player, including the poor
  shooters who should be missing already. **This is player-generation, not sim-config**,
  which is why §3.20 fixed the symptom and parked the cause. ⚠ **It very likely is not
  confined to `freeThrows`** — if the generator's mean sits high on one skill it may on
  others, and every other `base-*` constant would be absorbing the same bias invisibly.
  **What would make it real:** a pass that owns player generation, or simply a
  measurement of the generated population's mean per skill against the avg-10 scale — that
  measurement is cheap and would say at once whether this is one skill or a systemic
  offset.

- **A per-shot-type FG% instrument in `CalibrationHarness`** *(raised 2026-08 at §3.20's
  execution)*. The harness reports FG% **in aggregate only**, so the three 2P bases
  (`base-drive` / `base-post` / `base-perimeter`) can be tuned **only against the
  aggregate** — their realized rim / post-up / mid-range make rates are invisible.
  §3.20 set their split against real-basketball separation (rim ~66%, mid-range ~42–45%)
  as an *argument*, and could not verify the result. ⚠ **This is exactly the blind spot
  §3.17 spent a whole pass fixing for the shot MIX** — the mix became a calibration
  surface only once the harness printed charged-vs-draw shares. The make rates are the
  same problem one level down. **What would make it real:** any pass that wants to
  re-shape the 2P split, or a "green aggregate hiding wrong composition" finding of the
  kind §3.17 produced. It is test-side only and adds no engine surface.

- **A rim-protection era profile** *(raised 2026-08 by the user during §3.20's execution)*.
  A profile that dials **blocks up to ~5.5** (baseline lands 4.58 against a sourced 4.8),
  as a deliberate stylistic delta over `baseline` — the same shape as the parked
  1990s/three-heavy era profiles (#035). ⚠ **It must NOT be done by moving baseline**:
  baseline is tuned to Basketball-Reference 2025-26 league averages, and bending a sourced
  target toward a preference makes every later pass read a target that is not one.
  ⚠ **Two mechanical traps** for whoever builds it. **(1) A block does NOT reduce FGA** —
  `recordFieldGoalAttempt()` fires *before* the block fork, so a block is a charged miss,
  not a removed attempt; it moves **FG%/2P%**, not the attempt count. Anyone reaching for
  blocks to shave FGA or points is reaching for the wrong lever. **(2) `base-block-three`
  is INERT** — `PROB_FLOOR` (0.02) is 4× it (0.005), so for the ~41% of attempts that are
  threes the rate is **floored, not based**, and raising the constant does nothing. A
  profile that wants more blocked threes must reroute through `clampRareProbability`
  first (`calibration.md`'s standing footnote). **What would make it real:** the era-profile
  work generally — it is a values-only delta once the floor question is settled.

- **Derive the box score FROM the event log, as the single source of truth**
  *(raised 2026-08 by the user, on §3.20's box-score bug; the risk write-up is in
  [risks.md](risks.md), and Phase 4 carries a pointer)*. Today every stat is written
  **twice** — `PlayerGameState.record*()` counters and the `GameEvent` for the same play
  — and **the two agree only by convention**, because each call site remembers to do
  both. Deriving one from the other collapses two write paths into one.
  ⚠ **The motivating consumer is SINGLE-GAME SUMMARIZATION, not leaderboards** (user's
  framing). A box score rendered for one game sits **next to the play-by-play the user
  can also read**, so a disagreement between the two is **visible to the user** rather
  than buried in a season aggregate — which is a sharper reason to want one source than
  career-stat correctness is.
  ⚠ **The blocker is `minutes`**: it has no event behind it — a possession-share
  projection (#023 A) — so a full derivation needs either substitution/possession events
  or minutes kept as the one deliberately non-derived field.
  **What would make it real:** a game-summary/recap consumer, or Phase 4's design pass
  choosing to take it on. ⚠ **Deliberately NOT scheduled and NOT a gate** — the acute bug
  is fixed, and whether this is worth a refactor is an open question, not a decided one.

- **⚠⚠ SKILL SENSITIVITY IS ~10× TOO STEEP, AND CALIBRATION CANNOT SEE IT — the harness
  only ever runs AVERAGE-vs-AVERAGE rosters** *(measured 2026-08 by a throwaway probe,
  after the user challenged a weaker claim; **the probe was deleted**, the numbers are
  below)*. Holding an average offense fixed at skill 10 and varying only the defense's
  skill, over 100 possessions × 300 runs:

  | defense skill | steals | blocks | forced TO | opp points | **opp FG%** |
  |---|---|---|---|---|---|
  | 4 (terrible) | 1.3 | 2.0 | 2.3 | 202 | **79.8%** |
  | 10 (average) | 3.4 | 3.4 | 6.1 | 125 | **47.7%** |
  | 16 (elite) | 20.8 | 6.3 | 36.9 | 31 | **16.2%** |

  ⚠ **A 63-point FG% swing across the skill range, against a real NBA team-defense spread
  of about 5 points (~44–49%).** Forced turnovers span 2.3 → 36.9, which is not
  basketball at either end. The response to skill is roughly an **order of magnitude too
  steep**.
  ⚠ **THIS IS WHY NO CALIBRATION PASS HAS EVER CAUGHT IT: every harness run uses
  `teamOf5(id, 10)` — average against average — which sits exactly at the MIDPOINT of the
  curve, where every number looks right** (47.7% FG, 6 turnovers). §3.4–§3.21 have all
  tuned the **intercept** and **never once tested the slope**. It is the same class of
  blind spot as §3.17's shot mix (a green aggregate hiding a wrong composition), one
  level up: a green *midpoint* hiding a wrong *gradient*.
  ⚠ **The consumer that will expose it is PHASE 5.** Season play puts real rosters with
  real skill spread against each other; good teams will beat bad teams by impossible
  margins and standings will be degenerate. Today nothing consumes the slope, which is
  precisely why it has stayed invisible.
  **What would make it real:** Phase 5, or any pass that wants team strength to mean
  something. ⚠ **It is a TUNING problem, not a rebuild** — the suspects are the global
  `SENSITIVITY` (0.5) and the per-contest sensitivities, all of which are `public static
  final` model machinery. ⚠ **It needs a new instrument first**: a harness mode that runs
  a skill LADDER and reports the response curve, because the existing report cannot show
  a slope at all. **Do not attempt to re-tune it against the average-vs-average rows —
  those are already landed and would not move.**

- **Defense gets EFFICIENCY but not VOLUME — possessions strictly ALTERNATE**
  *(same 2026-08 audit)*. `GameSimulator`'s loop is `homeOnOffense = (poss % 2 == 0)`, so
  each team's possession COUNT is fixed by config before tip-off and nothing in a
  possession changes it. Only the **offensive-retention loop** (cap 3) varies anyone's
  attempt count, so offense can extend itself and defense cannot generate a possession
  for itself: a steal, a defensive rebound and a made basket all yield the possession the
  defense was getting anyway.
  ⚠ **Deliberately recorded as the NARROW claim, because the broad one is FALSE.**
  Defensive skill absolutely does pay off — see the table above; it simply pays off
  through **how well each possession goes**, never through **how many** you get. In real
  basketball a live-ball steal in transition is worth more than a stop after a made
  basket, partly through what it leads to; here that second channel does not exist.
  ⚠ It is also why `Pace` is a config INPUT rather than an emergent result, and why FGA
  has only one real lever (`base-no-basket-foul`, via possessions ended *without* an
  attempt) — which is the mechanism behind the FGA/Fouls over-determination.
  **What would make it real:** a transition play type, or a pace-as-outcome model.
  ⚠ **Not a bug and not a gate** — alternation is a deliberate simplification (#023 B)
  and every calibrated row is landed under it. Recorded so nobody mistakes it for an
  oversight or tries to make defense pay off by tuning a rate.

- **⚠ A POSSESSION HAS NO CLOCK — "time" exists only as a possession COUNT, and this is
  the single largest modelling absence in the engine** *(found in the 2026-08 audit; the
  user's framing: "it seems very complicated to implement")*. `PossessionEngine` contains
  **no notion of seconds**. `SHOT_CLOCK_VIOLATION` is a weighted draw from
  `TurnoverCause`, not a clock expiring. Minutes are a **possession-share projection**
  (#023 A). Overtime is `otPossessionsPerPeriod` — *some more turns*, not five minutes.

  **What is missing, concretely** — all of it downstream of the same absence:
  - **No endgame.** No intentional fouling when trailing, no two-for-one, no holding for
    the last shot, no running out the clock with a lead. **The last possession of a close
    game is simulated identically to the first.**
  - **`clutch` is DEAD.** ⚠ **Verified: the skill is calculated (`ClutchSkillCalculator`),
    mapped and stored, and has ZERO references in the entire `sim` package.** It is a
    player attribute the engine cannot read, because there is no "late and close" for it
    to key off. ⚠ **This is the ROOT CAUSE of one of the four dead skills
    [player.md](player.md) already lists** (line ~340, "still read by nothing") — that
    file records the symptom, this entry records why, and the two should stay consistent.
  - **No score-awareness at all.** A team down 20 and a team up 20 play the same way.
  - **Pace cannot be a strategy** — it is a config input (see the alternation entry
    above), so a team cannot *choose* to slow the game down.

  ⚠ **THE USER IS RIGHT THAT IT IS COMPLICATED, AND THE REASON IS WORTH STATING: it is
  not one feature, it is a new AXIS.** Every phase so far has added a **branch** to a
  possession — a fork, an event, a credit — and the possession model absorbed it. A clock
  changes what a possession IS: it acquires a duration, possessions stop being
  interchangeable, and the possession COUNT stops being an input and becomes an
  emergent result. ⚠ **That reaches `calibration.md` directly** — `Pace (poss/48)` is
  currently a target the engine hits **by construction**, and under a clock it becomes a
  number the engine would have to *earn*. **Every rate expressed per-possession would
  need re-reading.** This is why it is not "add a timer".

  ⚠ **THE HONEST MIDDLE PATH, and the reason this entry exists rather than a flat "no":
  most of the VALUE is in score-and-time AWARENESS, not in a real clock.** A cheap
  version — a possession knowing *roughly* where it sits (period, possessions remaining,
  score margin) — would light up `clutch`, intentional fouling and late-game shot
  selection **without** making possessions variable-length or turning pace into an
  emergent quantity. **The expensive half is the continuous clock; the valuable half is
  the situational awareness.** ⚠ Whoever takes this on should price the two SEPARATELY
  and be explicit about which is being bought — conflating them is what makes it look
  like an all-or-nothing rebuild.

  **What would make it real:** a **user-facing consumer** is the honest trigger — a
  play-by-play view with timestamps, or a late-game/comeback narrative someone wants to
  read. Phase 7's "live game simulation with play-by-play feed" is the closest thing on
  the roadmap. ⚠ **Deliberately NOT scheduled and NOT a gate on anything.** #023 chose
  the possession as the unit deliberately, every calibrated row is landed under it, and
  nothing today consumes time. **It is recorded so that the next reader knows `clutch` is
  inert BY OMISSION rather than by bug**, and so nobody starts it believing it is a
  contained change.

- **⚠ THE OFFENSIVE REBOUNDER IS NO LIKELIER TO TAKE THE SECOND-CHANCE SHOT — there is
  no PUTBACK** *(same 2026-08 audit; **verified in code**, and the closest thing found to
  a genuine §3.21-shaped gap)*. ✅ **PROMOTED — it is §3.22, design-resolved as
  decisions.md #044 (2026-09)**; the entry below is the audit note as written and is
  superseded by #044 on every point it speculates about (⚠ "putbacks are high-percentage
  rim attempts" turned out to be true of the rebounder's *own* mix already — he is not
  forced to the rim, and 2P%/FG% move by ~0.1, not the phase-sized amount imagined here). An offensive rebound `continue`s the possession loop,
  which re-enters at `shotSelector.pickShooter(offense, rng)` — a weighted draw over all
  five by `offensiveWeight`, taking **no argument identifying who just got the board**.
  So the center who grabbed it hands the ball back out and is as likely to shoot as the
  point guard who was standing at the arc.
  ⚠ **Why this matters more now than it did before §3.21:** that pass raised offensive
  rebounds 9.90 → **11.78/team-game**, so the number of second-chance possessions
  resolved this way went up ~19%. **A putback is one of the most recognisable events in
  basketball** and the engine cannot produce one.
  ⚠ **It is a fidelity gap, not a correctness one** — the rebound is credited, the
  possession is right, and nothing reconciles wrong. It is exactly the "the ball goes to
  the right team, so nothing looks broken" shape §3.21's two gaps had.
  **What would make it real:** a fidelity pass that wants it. ⚠ It needs `pickShooter` to
  learn about the rebounder (a signature change on the engine's hottest path), it would
  **consume/shift RNG** and re-baseline every seeded sim test, and it would move **2P%
  and FG%** (putbacks are high-percentage rim attempts) — so it is a real design pass,
  not a tweak.

- **An assist is credited in exactly ONE place, and only on a made field goal**
  *(same audit — verified: `resolveAssist` has one call site, inside `if (made)`)*.
  Noted alongside the above because it is the same question asked of a different stat, and
  it appears **correct**: the and-1 rides the same made-shot block, and free throws
  correctly carry no assist. Recorded so the next auditor does not re-derive it.
