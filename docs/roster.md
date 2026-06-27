# Roster & Lineup Domain

The player↔team **relationship**: how players are assigned to teams, how a
team's active lineup (starting 5 + rotation) is set, how players are released,
and how all of this is tracked over time.

This is distinct from two neighboring domains:
- **[player.md](player.md)** — the player *entity* (attributes, derived skills).
  A player's intrinsic model; says nothing about which team they're on.
- **team.md** *(future)* — the team as a *competitive unit* (chemistry,
  records, standings, home-court). Not yet written; arrives with Phase 3.4/4/5.

Roster sits between them: it owns the `player_team` link, not the player and
not the team's identity.

## Overview

A player belongs to at most one team at a time. The current assignment lives in
`player_team` (player_id is the PK — a free agent simply has no row). Every
assignment change is also appended to `player_team_hist` (append-only), so a
player's full team history is queryable. The `Team` entity holds no JPA roster
association; a team's roster is sourced via `PlayerTeamRepo.findByTeamId` and
composed by `EntityMapper`. See DECISIONS.md #012 for why the link was
decoupled from the entities.

```
Player (entity)        player_team (current, 1 row/player)      Team (entity)
  - 21 attributes  ───<  - player_id (PK)                  >───   - id, name
  - 23 skills            - team_id                                 - conference
                         - transaction_type                        - coach, gm
                         - assigned_date
                         - lineup_role     (Phase 2, new)
                         - rotation_order  (Phase 2, new)

                       player_team_hist (append-only audit)
                         - every assignment + release over time
```

## Current state (pre-Phase 2)

Built and tested:
- `addPlayerToTeam` — POST `/v1/team/{teamId}/{playerId}`. 404 if team/player
  missing; 409 if the player is already on a team.
- `GET /v1/player/{playerId}/history` — a player's assignment history, newest first.
- `Player.currentTeamId` — derived, read-only, from the current `player_team` row.

`TransactionType` enum already exists: SEED, DRAFT, TRADE, FREE_AGENCY, WAIVER,
SIGN, RELEASE. (`SEED` = the initial CSV backfill, provenance unknown.)

## Phase 2 — Rosters & Lineups

Goal: add the **lineup** concept (starting 5 + rotation order) the game engine
needs, plus roster read and player release. Three endpoints (PROJECT_PLAN.md §2.1).

### Locked decisions (2026-06-27)

1. **Lineup storage** — extend `player_team` with `lineup_role` +
   `rotation_order` columns. Lineup is a property of the *current* assignment,
   so it lives on the row that already exists per rostered player. Avoids a
   second team↔player join table that would duplicate the link `player_team`
   already owns.

2. **Release behavior** (`DELETE`) — remove the `player_team` row AND append a
   `RELEASE` record to `player_team_hist`. Player returns to free agency
   (`currentTeamId` becomes null); past stints stay queryable via `/history`.
   Mirrors `addPlayerToTeam` in reverse and reuses the existing `RELEASE`
   transaction type.

### Open question — lineup_role enum

A `Status` enum already exists (STARTER, BENCH, ROTATION, MINORS, INJURED,
SUSPENDED). Two of those (STARTER/BENCH/ROTATION) overlap with lineup intent,
but Status also carries availability states (INJURED/SUSPENDED) that aren't
lineup roles. **Decision pending**: either (a) introduce a focused
`LineupRole` enum (STARTER / ROTATION / BENCH / INACTIVE) for the lineup column,
or (b) reuse `Status`. Leaning (a) — lineup role and availability are different
concerns and conflating them will bite once the engine reads lineups. To be
logged in DECISIONS.md when settled.

### Endpoints

| Method | Path | Purpose | Responses |
|--------|------|---------|-----------|
| GET | `/v1/team/{teamId}/roster` | Roster with lineup slots | 200 / 404 |
| PUT | `/v1/team/{teamId}/lineup` | Set starting 5 + rotation order | 200 / 400 / 404 |
| DELETE | `/v1/team/{teamId}/{playerId}` | Release player to free agency | 200 / 404 |

- **GET roster** → a `Roster` schema: list of entries, each = player +
  `lineup_role` + `rotation_order`.
- **PUT lineup** → a `LineupRequest`: ordered list of {playerId, role, slot}.
- **DELETE** → releases the player (see decision #2 above).

### Validation rules (PUT lineup)

- Exactly **5** STARTER entries.
- Every `playerId` must belong to `{teamId}` → else 404.
- No duplicate players in the request.
- `rotation_order` unique among non-bench entries.

### Roster rules (later in Phase 2)

Not in the first endpoint pass; tracked for the back half of the phase:
- Roster size limits (e.g. 15 active, 5 minors).
- Position minimums/maximums per roster.
- Enforce those on add/lineup operations.

## Looking ahead (not Phase 2)

These touch the roster domain but belong to later phases — listed so the
boundary is clear:
- **Minutes & fatigue** (Phase 3.5) — rotation_order + `endurance` drive minutes
  allocation and in-game substitution.
- **Coach rotation influence** (Phase 3.5) — gated on the Coach model (parked).
- **Trades / free agency / waivers** (Phase 6.4) — more `TransactionType` paths
  through the same `player_team` / `player_team_hist` machinery.
