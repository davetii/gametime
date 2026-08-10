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
entities — see decisions.md #012.

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

## How gameplay consumes the roster (built)

The roster domain feeds the game engine, and all three consumers are now live.

**The bridge is `TeamQueryService`** (see Data model above). At the start of a
simulation `GameSimulator` calls `teamQueryService.getTeam(...)` for each side —
the *same* read path the `GET /v1/team/{teamId}` endpoint uses — then splits the
returned `RosterEntry` list into starters (`lineupRole == STARTER`) and a bench
sorted by `rotationOrder`, turning each into a `PlayerGameState` and wrapping the
squad in a `RotationState` + `TeamContext`. The two fields this domain owns
therefore cross into gameplay at exactly one point, as ordinary API-model data:
`lineupRole == STARTER` becomes `PlayerGameState.isStarter()` (driving sub
priority, rested-return, and the starter fatigue tolerance of §3.5 Decision C),
and `rotationOrder` becomes the bench queue order `RotationState` draws from.

> ⚠️ **The `squad` list is NOT ordered by rotation priority — do not read it that
> way.** `GameSimulator.buildRotation` appends the five starters **in whatever order
> `team.getPlayers()` returns**, then the bench sorted by `rotationOrder`. Because
> starters carry a **null `rotationOrder`** (see Lineups above), **squad indices 0–4
> are unordered among themselves**; only indices 5+ carry real priority. A §3.13
> design draft derived a player's importance from his squad index and would have made
> "the most protected player" a database-ordering accident — caught and corrected in
> `decisions.md` #031 B. **The trap is still live in the code**, so anything needing
> "how good is this player" must use a skill composite (§3.13's
> `PlayerGameState.valueComposite()`), not a position in this list.

The four live consumers:

- **Minutes & fatigue** (§3.5, decisions.md #023) — the lineup this domain owns
  (`STARTER` set + `rotationOrder` bench queue) drives the engine's dynamic
  rotation: `rotationOrder` + a player's `endurance` govern minutes allocation and
  the between-possession substitution check (`RotationState`). In-game subs stay
  transient — they never write back to `player_team` (see Lineups above), so a
  simulation never mutates roster state.
- **Coach rotation influence** (§3.5, decisions.md #023) — the coach's
  `rotationDepth` / `substitutionAggressiveness` (built and read; see
  [coach.md](coach.md)) decide how far down the `rotationOrder` queue the bench
  plays and how eagerly tired starters are pulled. `rotationOrder` is the roster's
  contribution; the coach knobs are how that chart is *used* — the clean seam
  between this domain and gameplay.
- **Foul-trouble benching** (§3.13, decisions.md #031) — a **soft** substitution
  rule that sits a player carrying fouls before he fouls out. It reads this
  domain's two fields as a *protection* signal, **combined with** (not replaced by)
  a skill composite: a `STARTER` is managed slightly more tightly, and a bench
  player is discounted progressively down the `rotationOrder` queue, floored so a
  deep reserve is protected less but never exempt.
  **⚠️ The direction is the opposite of the fatigue rule, deliberately.** §3.5 lets
  starters tolerate *more* fatigue before being pulled; §3.13 pulls the better
  player *sooner*. You ride your star when he's tired, you protect him when he's in
  foul trouble. It reads like an inconsistency between the two consumers of the same
  field and is not — do not "fix" it into agreement.
  Like the fatigue rule, it draws only within `rotationDepth` and **never writes
  back to `player_team`**; and it can never bring a fouled-out player back.
- **Technical fouls & ejections** (§3.14a, decisions.md #032) — the **on-floor five**
  is the committer pool for a technical: the draw is `foulProne`-weighted over
  whoever this domain's lineup currently has playing, and **the bench is excluded**.
  That is a *measurement* call, not a realism one (#032 C): the bench pool is ~10
  against the floor's 5, so ~2/3 of technicals would land on players who are not
  playing and whose ejections have no engine consequence. **The accepted fidelity
  loss: bench and coach technicals are not modelled** — a coach is not a
  `PlayerGameState` at all. Two technicals ejects a player, which extends the same
  hard-tier disqualification filter as a foul-out (`RotationState.isDisqualified`),
  so an ejected player is forced off and never selected again. Like every other
  consumer here it is **transient — no write-back to `player_team`**: an ejection
  lasts the game, not the season.

## Not yet built

- **Trades / free agency / waivers** (Phase 6.4) — more `TransactionType` paths
  (`TRADE` / `FREE_AGENCY` / `WAIVER` / `DRAFT`) through the same `player_team` /
  `player_team_hist` machinery that `SIGN` / `RELEASE` already use. A normalized
  multi-player-trade `transaction` table is a future option, not needed until
  trades exist.
