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
