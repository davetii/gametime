# Ideas

Unimplemented ideas: potential new features and game concepts, worth not losing, with no
phase home yet. Entries are **not planned work**, carry no commitment, and may be promoted,
reshaped or dropped.

Each entry says what the idea is, why it would matter, what blocks it, and **what would make
it real** (the consumer or pass that would justify building it).

Routing: a planned feature with a phase goes to [roadmap.md](roadmap.md) (promote it and
delete it from here). Infra, tooling and code-quality chores go to [backlog.md](backlog.md).
Active risks go to [risks.md](risks.md). A shipped idea is deleted, not annotated.

---

## Gameplay and simulation

- **Offense Opportunity Event** (good defense creating offense).
  A defensive play doesn't produce offense when it should. Today a steal or turnover just
  hands the other team a normal half-court possession, but it might have been a fast break.
  An event for "a defensive play creates an offensive opportunity" would be a way to reward
  good defenses.
  **Related gap:** possessions strictly alternate, so each team's possession count is fixed by
  config before tip-off. Defense improves *how well* a possession goes, never *how many*
  there are, and in real basketball a live-ball steal is worth more partly because of what it
  leads to. **What would make it real:** a transition play type, or letting possession count
  vary (pace as an outcome). Either is a large change that moves attempts, and attempts have
  no spare calibration lever (see [engine-traps.md](engine-traps.md)).

- **A game clock, or at least score-and-time awareness.**
  Time exists only as a possession count: shot-clock violations are a weighted draw, minutes
  are a possession-share projection, and overtime is "some more turns". Downstream of that:
  - no endgame (no intentional fouling, two-for-one or holding for the last shot); the last
    possession of a close game is simulated like the first
  - the `clutch` skill is inert, because there is no "late and close" to key off
  - no score awareness: a team down 20 and up 20 play identically
  - pace cannot be a strategy

  A real clock is a new axis, not a feature: a possession gains a duration, possessions stop
  being interchangeable, and possession count becomes a result, which moves every
  per-possession rate. **Most of the value is in awareness alone**: a possession that knows
  period, possessions remaining and margin lights up `clutch`, intentional fouling and
  late-game shot selection without variable-length possessions. Price the two halves
  separately.
  **What would make it real:** a user-facing consumer, such as a timestamped play-by-play or a
  late-game narrative (Phase 7 is the closest).

- **Strategic substitutions.**
  The engine substitutes for fatigue, foul trouble and forced removal. Missing: matchup or
  going small, hot hand, closing lineup and garbage time, score-aware rotation. They share one
  missing prerequisite: the rotation step has no game-situation awareness (it isn't passed the
  period, let alone the margin). The starting five also never change composition by choice;
  the coach swaps individuals out of a fixed lineup but never picks a lineup.
  **If promoted:** extend the foul-trouble sub's template (probabilistic, coach-scaled,
  player-value-weighted) rather than inventing a parallel mechanism. **What would make it
  real:** the game-situation plumbing above, which a design pass should start with.

- **Coach competence in rotation decisions.**
  The rotation reads only `rotationDepth` and `substitutionAggressiveness`, both style axes.
  There is no coach quality axis. It needs two things: a new `Coach` attribute (schema,
  `coach.csv`, `EntityMapper`, landing with its consumer) and a definition of what a *better*
  substitution is mechanically, since the model has no quality dimension to improve.
  **Natural home:** Phase 6 (coach progression and hiring).

- **Fights and altercations.**
  Technicals and flagrants exist; fights need a temperament trigger that doesn't. The engine
  consumes skills, not attributes, and `foulProne` already carries the aggression and
  composure composite. A dedicated temperament axis is the open alternative, but it would be
  an attribute with no consumer yet. The value is play-by-play texture, not aggregate fidelity.

- **Raise the offensive-retention cap from 3 to 5.**
  `sim.max-offensive-retentions-per-possession` bounds the second-chance loop. A genuine
  scramble can run longer than three retentions, and a hard wall makes that impossible rather
  than unlikely.
  **The first step is a measurement, not a tune:** nobody has measured how often the cap
  binds. Instrument how often `capReached` is true and how the retention count is
  distributed. If it binds rarely, the realism gain is invisible and the aggregate cost lands
  on every game, so the idea dies cheaply. If it binds often, the cap is doing calibration
  work and raising it is a bigger change than this entry assumes.
  **Why parked:** the cap fires on the common path across every retention channel, so raising
  it adds offensive rebounds, attempts and points to every game. FGA and offensive rebounds
  already sit at or past target and have no clean buy-back; see
  [calibration.md](calibration.md). A profile setting the cap does not unpark this, but
  raising the shipped default needs a calibration pass.

- **Talent spread as a profile axis.**
  A base rate says where the league sits; a sensitivity says how far apart players within it
  are (`probability = base + SENSITIVITY × (skillA − skillB)/10`). Low compresses the league
  (roster quality hardly matters), high stretches it (stars separate). A "superteam era" vs
  "parity era" is real, and this is the only lever that expresses it. Today the spread is
  spread across about 15 `*_SENSITIVITY` constants.
  **Why not just profile them:** turning one in isolation breaks it. The block sensitivity is
  deliberately small so blocks stay base-dominant, and the thin-base rare events need gentle
  slopes too.
  **What would make it real:** a design pass treating spread as one knob, most plausibly a
  single multiplier across the family that preserves the established ratios. It needs its own
  calibration, since it moves every rare-event rate at once.

- **A rim-protection era profile.**
  Dial blocks up to about 5.5 as a stylistic delta over `baseline`, like the parked 1990s and
  three-heavy profiles. **Do not do it by moving baseline**, which is tuned to sourced
  targets. Two traps: a block does **not** reduce FGA (the attempt is charged before the
  block fork, so a block moves FG% and 2P%, not attempts), and `base-block-three` is inert
  because `PROB_FLOOR` floors it. More blocked threes must route through
  `clampRareProbability` first. Values-only once the floor question is settled.

- **`DOUBLE_DRIBBLE` as a tenth turnover cause.**
  A real, common live-ball turnover that isn't among the nine causes. Structurally free: one
  enum constant, one weight, and the outcome rides the existing `TURNOVER` event as free
  text. Not numerically free: cause weights re-partition a frozen turnover count, so a tenth
  cause takes share from the nine, possibly from `STOLEN`, moving the steal rate.
  **What would make it real:** a pass that already owns rate movement and can absorb the
  re-partition in one place.

- **The charge drawer as a counterparty.**
  A charge has a real second participant, the defender who drew it, and he would be a natural
  `opponent_player_id`. The engine never picks him, so the column is deliberately null on
  both of the charge's events. Picking one needs a new RNG draw, which shifts the stream and
  re-baselines every seeded sim test, for a participant nothing consumes.
  **What would make it real:** a consumer, such as a "charges drawn" stat or a pass that
  wants the drawer's `acumen` to bend the charge rate. Then it is one draw, one existing
  column and one deliberate re-baselining.
