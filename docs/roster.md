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
association; a team's roster is sourced via `PlayerTeamRepo.findByTeamId` and
composed by `EntityMapper`. The link is decoupled from both entities — see
decisions.md #012.

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

Two independent axes, with no shared values (decisions.md #013):

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
`PUT /v1/team/{teamId}/lineup` (decisions.md #014). The request is replace-all:
it describes every roster player's `lineupRole` + `rotationOrder` and overwrites
those fields. The lineup is set-on-change, not per-game — once set it persists,
and every team is seeded with a valid 5-starter lineup. In-game substitutions
are transient game-simulation state and never write back here.

- **Starters** are an unordered set of 5 and carry **no** `rotationOrder`.
- **`rotationOrder`** is the bench substitution queue: first off the bench = 1,
  then 2, 3…; unique among all non-null values.

## Endpoints

A team's roster is **part of the team resource** — there is no separate roster
endpoint (decisions.md #015). `GET /v1/team/{teamId}` returns the `Team` with
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
  the game engine, not an API rule. See decisions.md #017.

## Not yet built

These touch the roster domain but are not implemented yet:

- **Minutes & fatigue** (Phase 3.5) — `rotationOrder` + `endurance` drive minutes
  allocation and in-game substitution.
- **Coach rotation influence** (Phase 3.5) — the Coach model is built and its §3.4
  effects (pace / shot / defensive scheme) ship; the rotation attributes
  (`rotationDepth` / `substitutionAggressiveness`) are seeded but unread until §3.5
  wires them to the substitution model. See [coach.md](coach.md).
- **Trades / free agency / waivers** (Phase 6.4) — more `TransactionType` paths
  through the same `player_team` / `player_team_hist` machinery.
