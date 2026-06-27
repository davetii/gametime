# TODO

Tactical task list. Check items off or remove them as completed. For the big-picture phased roadmap, see [PROJECT_PLAN.md](PROJECT_PLAN.md).

---

## Immediate (Pre-Phase 1)

- [ ] Delete orphaned `gametime-service/src/` directory (empty leftover from module restructuring)
- [ ] Verify IntelliJ `.http` files work (likely need to select "local" environment in dropdown)
- [ ] Confirm all 78 tests still pass on clean checkout

## Phase 1 — Player Modeling (current focus)

See [player.md](player.md) for the full design. Goal: harden the player model
(21 attributes, 23 skills) on a consistent 1–20 scale before building rosters/engine.

### Attribute scale & data — DONE
- [x] Decide canonical attribute range — **1–20, average = 10**
- [x] Make player data CSV-driven (`players.csv` is source of truth; loaded via Liquibase `loadData`)
- [x] Generate canonical `players.csv` (422 players) from authoritative SQL
- [x] Add 5 new attribute columns (verticality, wingspan, composure, aggression, awareness) — schema, entity, CSV
- [x] Rescale existing 16 attributes to mean ~10 / stdev ~3.5 across 1–20 (distribution-based)
- [x] Fix data bugs in rescale pass: `ego` scale (was 0–10), 0-floor artifacts (luck, shot_selection)

### Attribute data — remaining
- [x] Author/derive real `endurance` values — was a near-constant (394 players = 5). Set placeholder-10 players to `floor((strength + energy + health + determination) / 4)`. NOTE: endurance is now derived, carries no independent signal — personalize per-player by hand where a player should tire differently than their physical/mental traits imply.
- [x] Derive the 5 new attributes from existing ones on the 1–20 scale. Formulas (all `floor`, clamped 1–20):
      verticality = `(energy*2 + agility + strength)/4` + tiered str/agi freak bonus (+2 top10%str&top20%agi, +1 top20%str&top20%agi);
      wingspan = `(size*2 + energy + agility)/4`;
      composure = `(intel + determination + endurance + shot_selection)/4` + tiered bonus (+2 top10%intel&det, +1 top25%intel&det);
      aggression = `(ego + determination*2 + energy*2)/5`;
      awareness = `(intel + shot_selection)/2` + additive bonuses (+1 top20%intel, +1 top20%agility).
      NOTE: derived (no independent signal) AND percentile bonuses are baked off the current snapshot — if existing attrs are re-tuned later, regenerate these from scratch (base + bonus together).
- [ ] Hand-tune marquee/star players to 18–20 where appropriate (rescale is mechanical)
- [x] Update `player.md`: scale label → 1–20/avg-10, and corrected the attribute derivation formula sections to match what was actually implemented (was the old `ceil(.../2)` proposal). NOTE: the skill *calculator specs* (player.md §"Skill Calculator Specifications") are still on the old scale — defer those to the skills re-tune (see [plan.md](../plan.md)), so the doc matches the real code rather than a second guess.

### Skills — DONE (see [plan.md](../plan.md))
All 23 skill calculators now run on the 1–20 / avg-10 scale via a shared deviation
helper on `SkillCalculator` (`adj`, `comboAdj`, `experienceAdj`, `clamp`). 84/84 tests
pass; every skill centers at 10.0 for an average player.
- [x] Redesign `foulProne` formula — now baseline 10 + deviation adjustments (inverted: aggression up, composure/awareness/experience down); average→10 invariant restored
- [x] Implement the 10 new skill calculators (finishing, transition, rimProtection, stealing, shotContest, foulDrawing, foulProne, clutch, screenSetting, offBallMovement)
- [x] Wire new calculators into `SkillMapper`; added 5 new attrs + 10 new skills to OpenAPI spec; EntityMapper maps the 5 new attrs before skill computation
- [x] Wire up `health` — now consumed by `individualDefense` and `defenseRebound` (no longer an orphaned attribute)
- [x] Unit tests for the 10 new calculators; `BASE_PLAYER()` fixture updated (AVERAGE_ATTRIBUTE=10 + 5 new attrs); all 13 existing tests re-baselined
- [x] Re-tune existing 13 calculators for the 1–20 scale (replaced the old `>9`/`>7` threshold ladders with the deviation helper)

### Skills — remaining
- [ ] Update player.md §"Skill Calculator Specifications" to document the new 1–20 formulas (was deferred until the calculators were re-tuned — now they are)

### Cleanup
- [x] Delete stale `player.csv` (superseded by `players.csv`) — also removed its orphaned, unregistered loader `release.1.0.1.player.dataload.yml`. Source of truth is now `players.csv` via `release.1.0.2.player.dataload.yml`.

## Phase 1b — Foundation (CRUD / Coach / GM)

- [ ] Implement `createPlayer` endpoint
- [ ] Implement `updatePlayer` endpoint
- [ ] Implement `addPlayerToTeam` endpoint
- [ ] Implement `fetchConference` endpoint
- [ ] Design coach attribute model (see DECISIONS.md for logging the choice)
- [ ] Design GM attribute model
- [ ] Add tests for all new endpoints
- [ ] Update OpenAPI spec if Coach/GM models change

## Backlog

- [ ] Evaluate Testcontainers as alternative to H2 for integration tests
- [ ] Add API pagination (parameters already defined in OpenAPI spec but not wired)
- [ ] Consider adding player age field (currently only yearsPro, no birth date)
- [ ] Review skill calculator formulas for balance after game engine exists
- [ ] Set up React project in `gametime-frontend/`
