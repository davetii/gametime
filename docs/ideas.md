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
  `PossessionEngine` — after N offense retentions (offensive rebound + §3.7
  offense-recovered block + §3.8 OOB-offense, all sharing the one cap) the next miss
  is *forced* to a possession-ending outcome so the loop terminates. It sits at a
  **§3.3-era calibrated 3**. The realism argument for a higher value is real: a
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

- ~~**Flagrant / technical fouls, and altercations (fights).**~~ **PROMOTED (2026-08)
  to §3.14, then SPLIT into §3.14a (technicals) + §3.14b (flagrants)** — *(originally
  promoted as §3.13; renumbered when foul-outs moved ahead of it by user call; split
  by decisions.md #032 A on the finding that the two share nothing but the word
  "foul")* — see roadmap.md's Possession-fidelity section. **§3.14a's design is
  RESOLVED (#032 A–J) and execute-ready in todo.md; §3.14b still needs its own design
  pass.** This is planned work, not a parked idea. Three notes from the reasoning that
  kept it here, **corrected against what the design pass actually found**:
  - **FTs *plus* retained possession** — still the hard part, and still unbuilt. It
    belongs entirely to **§3.14b**: a technical turned out to leave the possession
    **completely unchanged** (#032 D), which is why it went first.
  - **A chosen FT shooter** — resolved as a **deterministic highest-`freeThrows`
    on-the-floor pick**, a second rule beside the untouched `foulDrawing`-weighted
    draw (#032 G).
  - **The ejection needing stored state** — **this prediction was half wrong, and the
    correction is the design pass's most useful finding.** It holds for a
    **flagrant-2** (a severity grade with no counter behind it → §3.14b, argued via
    the #028 D pattern), but **not** for the two-technical case, which is
    `technicalFouls >= 2` — a monotonic counter, so #023 F's derive-don't-store
    discipline applies **unchanged** (#032 F). Either way it extends §3.13's
    hard/forced tier via `eligible(...)` rather than adding a parallel mechanism
    (#031 H).

  **Fights/altercations remain PARKED** and are in neither §3.14a nor §3.14b — they
  still want a temperament trigger that does not exist, and #032 C **declined to
  create one**: the engine consumes skills, not attributes, and `foulProne` already
  carries the aggression/composure composite. A dedicated temperament axis stays the
  genuine open alternative, blocked on the #014/#017 "no attribute ahead of its
  consumer" discipline. Note these are **rare events** whose value is play-by-play
  texture + the occasional star ejection, not aggregate fidelity — confirmed by
  measurement: §3.14a costs **+0.26 points/team/game against a ±1.5 noise band**, so
  it earns **no** recalibration pass the way §3.10/§3.11 did (#032 I).

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
  | Ejection | §3.14a (technicals) / §3.14b (flagrant-2) | rule, forced |
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
