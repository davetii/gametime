# Player Domain

## Overview

A Player is the core domain object in Gametime. Players have **base attributes** (stored in the database) that are combined through **skill calculators** to produce derived **skills** used in gameplay.

## Architecture

```
PlayerEntity (DB)  →  EntityMapper  →  Player (API model)
     attributes          SkillMapper       attributes + derived skills
                         calculators
```

---

## Base Attributes (1–20 scale, average = 10, stored in DB)

21 attributes:

| Attribute | Description |
|-----------|-------------|
| agility | Quickness, lateral movement, body control |
| aggression | Physical assertiveness, willingness to initiate contact (distinct from ego) |
| awareness | Spatial/reactive sense, anticipation, off-ball IQ |
| charisma | Leadership, media presence |
| cohesion | Willingness to play within a team system |
| composure | Mental steadiness under pressure, consistency |
| determination | Effort, hustle, willingness to grind |
| ego | Self-confidence (high values penalize team play) |
| endurance | Stamina over a game/season |
| energy | Burst effort, motor |
| handle | Ball-handling ability |
| health | Durability, injury resistance |
| intelligence | Basketball IQ, reading the game |
| luck | Random variance factor |
| shotSelection | Discipline in shot choice |
| shotSkill | Raw shooting mechanics |
| size | Physical size relative to position |
| speed | Straight-line speed |
| strength | Physical power |
| verticality | Explosiveness, leaping ability, above-the-rim play |
| wingspan | Arm length relative to height, physical reach |

### Other Stored Fields

- `yearsPro` — experience modifier used in skill calculations
- `height`, `weight`, `draftSlot`, `origin` — biographical
- `position` — positional slot; `status` — intrinsic availability (see Status
  below). **No `team_id`** — the player↔team link is decoupled into
  `player_team` / `player_team_hist` (decisions.md #012); see [roster.md](roster.md).

---

## Derived Skills (calculated at read time, not stored)

Each skill is computed by a dedicated `SkillCalculator` Spring bean. 23 skills:

#### Offensive

| Skill | Primary Inputs | Notes |
|-------|---------------|-------|
| **drive** | handle, speed, agility, determination | Attack the basket off the dribble |
| **freeThrows** | shotSkill | Concentration/pressure modifiers |
| **longRange** | intelligence, shotSkill, shotSelection | yearsPro adjustments |
| **perimeter** | shotSkill, shotSelection, speed, agility, ego | Mid-range scoring |
| **post** | strength, size, intelligence | Physicality gates effectiveness |
| **teamOffense** | intelligence, handle, ego | Team coordination |

#### Defensive

| Skill | Primary Inputs | Notes |
|-------|---------------|-------|
| **individualDefense** | speed, handle, intelligence, health, determination, ego | 1-on-1 defense |
| **teamDefense** | intelligence, determination, cohesion | Help defense, rotations; ego penalties |

#### Rebounding

| Skill | Primary Inputs | Notes |
|-------|---------------|-------|
| **offenseRebound** | size, strength, agility, determination | Energy factor |
| **defenseRebound** | size, strength, determination, health | Agility bonus |

#### Playmaking / Intangibles

| Skill | Primary Inputs | Notes |
|-------|---------------|-------|
| **acumen** | intelligence, handle, luck, cohesion | In-game IQ; ego penalties; yearsPro bonuses |
| **ballSecurity** | handle, strength, ego | Turnover avoidance |
| **passing** | handle, intelligence, luck | Court vision; yearsPro bonuses |

#### Finishing & Athleticism

| Skill | Primary Inputs | Purpose |
|-------|---------------|---------|
| **finishing** | verticality, agility, strength, handle | Scoring at the rim — dunks, layups through contact, lob catching |
| **transition** | speed, energy, awareness, handle | Fast-break scoring and decision-making |

#### Active Defense

| Skill | Primary Inputs | Purpose |
|-------|---------------|---------|
| **rimProtection** | size, verticality, awareness, intelligence | Shot blocking, paint deterrence |
| **stealing** | awareness, speed, agility, wingspan | Passing lane disruption, active hands |
| **shotContest** | verticality, wingspan, speed, awareness | Challenging shots without fouling |

#### Fouling

| Skill | Primary Inputs | Purpose |
|-------|---------------|---------|
| **foulDrawing** | aggression, agility, intelligence, composure | Getting to the free throw line |
| **foulProne** | aggression (positive), composure (inverse), awareness (inverse) | Likelihood of committing fouls — higher = worse |

#### Situational

| Skill | Primary Inputs | Purpose |
|-------|---------------|---------|
| **clutch** | composure, determination, shotSkill, ego | Late-game / high-pressure performance modifier |
| **screenSetting** | strength, size, cohesion, awareness | Pick quality, roll/pop timing |
| **offBallMovement** | awareness, speed, intelligence, cohesion | Cutting, spacing, finding open spots |

---

## Positions

9 positions model the modern positional spectrum:

| Code | Name |
|------|------|
| PG | Point Guard |
| CG | Combo Guard |
| SG | Shooting Guard |
| W | Wing |
| SF | Small Forward |
| F | Forward |
| PF | Power Forward |
| FC | Forward Center |
| C | Center |

## Status

`Player.status` is the player's **intrinsic availability** only — independent of
any team (decisions.md #013). The roster *slot* (STARTER / BENCH / ROTATION /
INACTIVE / MINORS) is a separate axis, `player_team.lineupRole`, owned by the
roster domain ([roster.md](roster.md)) — it is **not** a player status.

| Status | Meaning |
|--------|---------|
| ACTIVE | Available to play |
| INJURED | Currently injured |
| SUSPENDED | Suspended from play |

---

## Design Patterns

- **Strategy pattern**: Each skill calculator is an independent Spring bean implementing `SkillCalculator`
- **Separation of concerns**: Raw attributes stored in DB; skills derived at read time
- **Ego as double-edged sword**: High ego boosts individual skills but penalizes team-oriented ones
- **Aggression as double-edged sword**: Helps foul drawing and finishing but increases foul proneness

---

## Skill Calculator Specifications

> **Authoritative source: the code.** Each skill is computed by a dedicated
> `*SkillCalculator` Spring bean in
> `gametime-app/src/main/java/software/daveturner/gametime/mapper/`. The exact
> formulas live there (and are short and self-documenting); this doc describes the
> *design*, not the arithmetic, to avoid the two drifting out of sync.

All 23 calculators share a common structure on the **1–20 / average-10 scale**:

1. **Weighted-average base** of the skill's primary attributes. Because attributes
   average ~10, the base already yields ~10 for an average player.
2. **Deviation adjustments** via the shared helpers on `SkillCalculator`:
   - `adj(attr[, factor])` — single-attribute emphasis; contributes 0 at the league
     average (10) and scales toward the 1–20 bounds. Subtract it to model a
     *negative* influence (e.g. ego on team play).
   - `comboAdj(a, b[, factor])` — two-attribute combination (e.g. size+strength),
     centered so two average attributes contribute 0.
   - `experienceAdj(yearsPro)` — shared veteran/rookie curve.
   - `clamp(value)` — bounds the result to 1–20.
3. **Output**: `round(clamp(value))` — a `BigDecimal` to one decimal place.

Every calculator is calibrated so an all-average (10) player scores ~10.0 on the
skill, and each class carries a doc comment describing what it models and its inputs.

All 23 skills are listed with their primary inputs under "Derived Skills" above.
`foulProne` is inverted (higher = worse) but still centers at 10 for an average player.


## How Attributes Map to Game Engine Needs

This is the **design map** of which skills *should* back each possession decision —
it is broader than what the engine resolves today. As of §3.4, the engine wires:
shot selection (`drive`/`finishing`/`perimeter`/`post`/`longRange`), shot contest
(`shotContest`/`individualDefense`/`rimProtection`), turnovers (`ballSecurity` vs
`stealing`), shooting fouls (`foulDrawing` vs `foulProne`), free throws
(`freeThrows`), rebounding (`offenseRebound`/`defenseRebound`), assists (`passing`,
scaled by `teamOffense`), and shot-quality/efficiency (`acumen`,
`teamOffense`/`teamDefense`). Rows below not in that list — transition,
pick-and-roll (`screenSetting`/`offBallMovement`), off-ball movement, and the
`clutch` late-game modifier — are **not modeled yet** (future §3.x); they stay
here as the target so the attribute coverage is visible.

| Possession Event | Skills Used |
|-----------------|-------------|
| Transition or half-court? | transition, awareness |
| Ball handler decision | acumen, passing, teamOffense |
| Pick-and-roll action | screenSetting, offBallMovement |
| Drive to basket | drive, finishing |
| Shot attempt (open) | longRange / perimeter / post |
| Shot contest | shotContest, individualDefense |
| Shot block attempt | rimProtection |
| Foul on attempt? | foulDrawing vs foulProne |
| Free throws | freeThrows, clutch (late game) |
| Rebound | offenseRebound / defenseRebound |
| Turnover / steal | ballSecurity vs stealing |
| Late-game pressure | clutch modifier on all actions |
| Off-ball movement | offBallMovement, awareness |
