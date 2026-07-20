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
  slots cleanly after any of §3.8–§3.11 rather than inside one.

- **Flagrant / technical fouls, and altercations (fights).** §3.10 (decisions.md
  #028) models **personal** fouls only — shooting fouls and rebounding fouls, both
  ordinary `PlayType.FOUL` events that feed the team-foul/bonus derivation and the
  per-player foul-out counter (#023-F). Real basketball also has **non-personal
  fouls**: a **technical** (unsportsmanlike conduct, arguing) awards the *other* team
  **1 FT and keeps possession** with the fouled team; a **flagrant** (excessive/
  dangerous contact) awards **2 FTs + possession**; and at the extreme an **altercation
  / fight** ejects players (a foul-out-like removal independent of the 6-foul count)
  and can swing a game by pulling a star. None of these exist in the engine.
  **Why it's parked, not designed:** each needs mechanics the sim has no substrate for
  yet — a technical/flagrant is **FTs *plus* retained possession** (a shape neither the
  shooting-foul path, which ends the possession, nor §3.10's bonus path models), and a
  fight needs an **ejection** (a removal that isn't a foul-count derivation — it would
  want a trigger, likely tied to a `Player`/`Coach` temperament axis that doesn't exist,
  the #014/#017 "no attribute ahead of its consumer" discipline). They're also **rare
  events** whose main value is play-by-play texture + the occasional star ejection, not
  aggregate fidelity — so they don't earn a calibration pass the way §3.10's bonus FTs
  do. **Natural home if promoted:** its own numbered sub-phase *after* §3.11 (it reuses
  §3.10's team-foul/bonus substrate + FT machinery but adds the retain-possession-with-FTs
  fork and an ejection path), or a later "player temperament / discipline" pass that
  gives the fight trigger a real attribute to read. Until then it's genuine parking-lot
  texture, not planned work.

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
