# TODO

Tactical task list. Check items off or remove them as completed. For the big-picture phased roadmap, see [PROJECT_PLAN.md](PROJECT_PLAN.md).

---

## Immediate (Pre-Phase 1)

- [x] Delete orphaned `gametime-service/src/` directory — already gone (no longer present)
- [x] Verify IntelliJ `.http` files — `http-client.env.json` defines `local`+`test` environments; the `.http` files use `{{host}}{{basePath}}` correctly. (Runtime check is a manual IDE action: select "local" in the dropdown and run.) NOTE: the create/update player sample bodies omit the 21 attributes — update them when `createPlayer`/`updatePlayer` land in Phase 1b.
- [x] Confirm tests pass on clean checkout — 84/84 green (was 78 before the skills work)

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
- [x] Update `player.md`: scale label → 1–20/avg-10, and corrected the attribute derivation formula sections to match what was actually implemented (was the old `ceil(.../2)` proposal). The skill *calculator specs* section was later replaced with a pointer to the `*SkillCalculator` classes (code is authoritative for exact formulas).

### Skills — DONE
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
- [x] Update player.md §"Skill Calculator Specifications" — replaced the ~530 lines of stale 1–10 pseudocode with a concise design pointer to the `*SkillCalculator` classes (code is now the authoritative source for exact formulas; doc owns the design). Conceptual content (skill list, possession mapping, design principles) retained.

### Cleanup
- [x] Delete stale `player.csv` (superseded by `players.csv`) — also removed its orphaned, unregistered loader `release.1.0.1.player.dataload.yml`. Source of truth is now `players.csv` via `release.1.0.2.player.dataload.yml`.

## Phase 1b — Foundation (CRUD / Coach / GM)

- [x] Implement `createPlayer` endpoint — server-generates UUID if id absent; returns the created Player (with derived skills) in the 200 body.
- [x] Implement `updatePlayer` endpoint — 404 if id missing/unknown; preserves the existing team assignment (team is owned by `addPlayerToTeam`, not updates).
- [x] Implement `addPlayerToTeam` endpoint — 404 if team/player missing; 409 (`ResourceConflictException`) if the player is already on a team.
- [x] ~~Implement `fetchConference` endpoint~~ — DROPPED. Removed from the spec; clients filter the `/v1/league` response by conference. See DECISIONS.md #010.
- [x] Decouple player from team in the data model AND the API — `player_team` (current) + `player_team_hist` (append-only history) tables; `player.team_id` removed; `Player.currentTeamId` (derived, read-only) + `GET /v1/player/{id}/history`. Migrations consolidated from scratch into `release.1.0.1.sql` (no users/data yet). See DECISIONS.md #012.
- [x] Update the `.http` create/update player sample bodies with the 21 attributes (flagged in the Pre-Phase-1 note above) now that `createPlayer`/`updatePlayer` exist; added a player-history request.
- [ ] Design coach attribute model (see DECISIONS.md for logging the choice)
- [ ] Design GM attribute model
- [x] Add tests for the 3 new endpoints — `V1ApiDelegateimplTest` (happy + 404/409 cases) and an `EntityMapper` reverse-mapping test. 93/93 green.
- [ ] Update OpenAPI spec if Coach/GM models change

## Backlog

- [ ] Evaluate Testcontainers as alternative to H2 for integration tests
- [ ] Add API pagination (parameters already defined in OpenAPI spec but not wired)
- [ ] Consider adding player age field (currently only yearsPro, no birth date)
- [ ] Review skill calculator formulas for balance after game engine exists
- [ ] Set up React project in `gametime-frontend/`
