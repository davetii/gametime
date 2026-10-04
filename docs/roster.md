# Roster & Lineup Domain

The player↔team **relationship**: how players are assigned to teams, how a
team's lineup (starting 5 + rotation) is set, how players are released, and how
all of this is tracked over time.

This is distinct from two neighboring domains:
- **[player.md](player.md)** — the player *entity* (attributes, derived skills).
  A player's intrinsic model; says nothing about which team they're on.
- **gameplay docs** *(future)* — the team's *competitive behavior* (chemistry,
  records, standings, home-court) is produced by games being played, so it lives
  with the gameplay systems that generate it (game-engine.md / stats.md /
  season.md), not here.

Roster sits between them: it owns the `player_team` link, not the player and
not the team's identity.

## Data model

A player belongs to at most one team at a time. The current assignment lives in
`player_team` (player_id is the PK — a free agent simply has no row). Every
assignment change is also appended to `player_team_hist` (append-only), so a
player's full team history is queryable. The `Team` entity holds no JPA roster
association; a team's roster is **assembled at read time** by
`TeamQueryService.toTeamWithRoster` — it fetches the assignments
(`PlayerTeamRepo.findByTeamId`), loads those players in one batch, and hands all
three to `EntityMapper.entityToTeam` to map. The link is decoupled from both
entities.

> **`TeamQueryService` is a deliberate seam, not a helper.** It is the single
> read path for "a team with its roster," and both `GametimeServiceImp` (the
> top-level application service) and `sim.GameSimulator` (the engine) depend on
> **it** rather than on each other. That is what breaks a Spring constructor
> cycle: the engine used to reach into the whole `GametimeService` just for
> `getTeam`, while the service depended on the engine for `simulateGame`.
> Depending on this small read-only service points the dependency arrow one way.
> **Don't reintroduce that edge** — roster reads go through `TeamQueryService`.

```
Player (entity)        player_team (current, 1 row/player)      Team (entity)
  - 21 attributes  ───<  - player_id (PK)                  >───   - id, name
  - 23 skills            - team_id                                 - conference
                         - transaction_type                        - coach, gm
                         - assigned_date
                         - lineup_role
                         - rotation_order

                       player_team_hist (append-only audit)
                         - every assignment + release over time
```

`TransactionType` values: `SEED, DRAFT, TRADE, FREE_AGENCY, WAIVER, SIGN,
RELEASE` (`SEED` = the initial CSV backfill, provenance unknown).

## Player status vs lineup role

Two independent axes with no shared values:

- `Player.status` — player-**intrinsic availability**, independent of any team:
  `ACTIVE, INJURED, SUSPENDED`. Roster membership is *not* encoded here — a free
  agent simply has no `player_team` row.
- `player_team.lineupRole` — the player's **slot on a specific team**: `STARTER,
  ROTATION, BENCH, INACTIVE, MINORS`.

## Assignment & release

A player is added to a team with `POST /v1/team/{teamId}/{playerId}`: it inserts
the `player_team` row (with `lineupRole = INACTIVE` — on the active roster, not
yet slotted) and appends a `SIGN` record to `player_team_hist`. It is 404 if the
team or player is missing, and **409** if the player is already on a team **or
the active roster is full** (see Roster rules below).

`DELETE /v1/team/{teamId}/{playerId}` releases a player to free agency: it
removes the `player_team` row and appends a `RELEASE` record to
`player_team_hist`. The player's `currentTeamId` becomes null; past stints stay
queryable via `/history`. This mirrors the add in reverse.

`Player.currentTeamId` is derived (read-only) from the current `player_team`
row, and `GET /v1/player/{playerId}/history` returns the player's assignment
history, newest first.

## Lineups

A team's lineup is **sticky persistent state** on `player_team`, set with
`PUT /v1/team/{teamId}/lineup`. The request is replace-all:
it describes every roster player's `lineupRole` + `rotationOrder` and overwrites
those fields. The lineup is set-on-change, not per-game — once set it persists,
and every team is seeded with a valid 5-starter lineup. In-game substitutions
are transient game-simulation state and never write back here.

- **Starters** are an unordered set of 5 and carry **no** `rotationOrder`.
- **`rotationOrder`** is the bench substitution queue: first off the bench = 1,
  then 2, 3…; unique among all non-null values.

## Endpoints

A team's roster is **part of the team resource** — there is no separate roster
endpoint. `GET /v1/team/{teamId}` returns the `Team` with
`players: [RosterEntry]`, each entry = player + `lineupRole` + `rotationOrder`.

| Method | Path | Purpose | Responses |
|--------|------|---------|-----------|
| GET | `/v1/team/{teamId}` | Team incl. roster with lineup slots | 200 / 404 |
| POST | `/v1/team/{teamId}/{playerId}` | Sign a free agent to the team | 200 / 404 / 409 |
| DELETE | `/v1/team/{teamId}/{playerId}` | Release player to free agency | 200 / 404 |
| PUT | `/v1/team/{teamId}/lineup` | Set starting 5 + bench order; returns the Team | 200 / 400 / 404 |

`PUT /lineup` takes a `LineupRequest`: a list of `{playerId, lineupRole,
rotationOrder}`. It is valid when:

- Exactly **5** entries are `STARTER`.
- No `STARTER` carries a `rotationOrder`.
- Every `playerId` belongs to `{teamId}` (else 404).
- No player appears twice.
- `rotationOrder` is unique among all non-null values.
- The resulting roster (request overlaid on current; omitted players keep their
  role) has **≤ 15 active** (non-`MINORS`) and **≤ 5 `MINORS`** (else 400).

## Roster rules

Size caps (`MAX_ACTIVE_ROSTER = 15`, `MAX_MINORS = 5` in `GametimeServiceImp`):

- **Active cap (15)** = count of every `lineupRole` except `MINORS`. Enforced on
  sign (409) — a signed player lands `INACTIVE`, which is active — and re-checked
  on the lineup PUT (400) so role shuffles can't grow it past the sign limit.
- **Minors cap (5)** = count of `MINORS`. A sign never lands in `MINORS`, so this
  is purely a lineup-PUT invariant (400).
- **Position** — intentionally unconstrained: there are no position minimums or
  maximums. A team may carry any positional mix; a lopsided roster is punished by
  the game engine, not an API rule.

## How gameplay consumes the roster

**The bridge is `TeamQueryService`** (see Data model). At the start of a simulation
`GameSimulator` calls `teamQueryService.getTeam(...)` for each side, the same read path
`GET /v1/team/{teamId}` uses. It splits the returned `RosterEntry` list into starters
(`lineupRole == STARTER`) and a bench of the remaining players that carry a
`rotationOrder`, sorted by it, and turns each into a `PlayerGameState` inside a
`RotationState` + `TeamContext`. A non-starter with no `rotationOrder` (an `INACTIVE` or
`MINORS` player not yet slotted) never enters the squad and never plays.

The two fields this domain owns cross into gameplay at exactly this point:
`lineupRole == STARTER` becomes `PlayerGameState.isStarter()`, and `rotationOrder` becomes
the bench queue `RotationState` draws from. The engine's rotation rules (fatigue subs,
foul-trouble subs, disqualification, the on-floor five as the pool for technicals) are in
[game.md](game.md); how the coach uses the bench queue is in [coach.md](coach.md).

- **The squad list is not ordered by priority.** Starters are appended in whatever order
  `team.getPlayers()` returns, and starters carry a null `rotationOrder`, so squad indices
  0–4 are unordered among themselves; only indices 5+ carry real priority. Anything that
  needs "how good is this player" must use a skill composite
  (`PlayerGameState.valueComposite()`), never a position in this list.
- **Simulation never writes back to the roster.** In-game substitutions, fatigue, foul-outs
  and ejections are transient game state: nothing mutates `player_team`. An ejection lasts
  the game, not the season.
- **Every player the engine names is drawn from the on-floor five** this domain supplies.
  Charges, flagrants, technicals and the counterparty column add no new pool and no
  write-back.

## Not yet built

- **Trades / free agency / waivers** (Phase 6.4) — more `TransactionType` paths
  (`TRADE` / `FREE_AGENCY` / `WAIVER` / `DRAFT`) through the same `player_team` /
  `player_team_hist` machinery that `SIGN` / `RELEASE` already use. A normalized
  multi-player-trade `transaction` table is a future option, not needed until
  trades exist.
