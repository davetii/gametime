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
  (currently on-target 112 pts / 47% FG / ~11 off reb), so 3→5 needs its **own
  recalibration pass** (re-center shot base rates for the added points). Deliberately
  **not folded into §3.8** so the OOB change is verified against a known-good
  baseline (one moving knob at a time — the §3.7 lesson). Also note the loop-
  termination *guarantee* is independent of the value (it holds at 3, 5, or 50); only
  the realism/aggregate trade-off is at stake. **Natural home if promoted:** a
  dedicated calibration/tuning pass (its own harness loop + re-agreed aggregates),
  slotting cleanly *between* sub-phases rather than inside one. **The arc now runs
  §3.7–§3.16**, and **§3.16 (recalibration against verified targets) is the natural
  home** — it is already exactly this kind of pass. **Note (updated 2026-08, §3.13
  design):** the earlier note here ("re-center in §3.12") is superseded — §3.12
  **declined** the trim and the points/FG% **targets themselves are now CONTESTED**
  ([calibration.md](calibration.md) is the source of truth). Three consecutive passes
  have declined the same trim. Do **not** stack this idea on top of an unresolved
  target question: it waits for §3.16, which owns both.

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
    add one. **Explicitly NOT a §3.15 or §3.16 job** — §3.15 is a config refactor whose
    validation gate is reproducing §3.14b's landing byte-for-byte, and §3.16 is a
    recalibration; a control-flow restructure riding either would blur what moved a
    number. If promoted it wants its own small pass with the existing retention tests
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
  the §3.7–§3.16 arc, or Phase 4+ alongside a richer coach model — it needs a design
  pass of its own, starting with the game-situation plumbing. **Not planned work.**

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
