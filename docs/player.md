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

## Base Attributes (1–10 scale, stored in DB)

### Current (16 attributes)

| Attribute | Description |
|-----------|-------------|
| agility | Quickness, lateral movement, body control |
| charisma | Leadership, media presence |
| cohesion | Willingness to play within a team system |
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

### New Attributes (5 additions)

| Attribute | Description | Why It Can't Be Derived |
|-----------|-------------|------------------------|
| **verticality** | Explosiveness, leaping ability, above-the-rim play | Physical trait distinct from speed or size — a player can be fast without being bouncy |
| **wingspan** | Arm length relative to height, physical reach | Body measurement — some players are long but not tall (Kawhi) |
| **composure** | Mental steadiness under pressure, consistency | Mental trait distinct from intelligence or determination |
| **aggression** | Physical assertiveness, willingness to initiate contact | Behavioral tendency distinct from ego (self-belief vs. physicality) |
| **awareness** | Spatial/reactive sense, anticipation, off-ball IQ | Distinct from general intelligence — reading passing lanes, cutting timing |

### Other Stored Fields

- `yearsPro` — experience modifier used in skill calculations
- `height`, `weight`, `draftSlot`, `origin` — biographical
- `position`, `status`, `team_id` — roster placement

---

## Derived Skills (calculated at read time, not stored)

Each skill is computed by a dedicated `SkillCalculator` Spring bean.

### Current Skills (13)

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

### New Skills (10 additions)

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
| BG | Two Guard |
| W | Wing |
| SF | Small Forward |
| F | Forward |
| PF | Power Forward |
| FC | Forward Center |
| C | Center |

## Status

| Status | Meaning |
|--------|---------|
| STARTER | Starting lineup |
| BENCH | Active bench player |
| ROTATION | In rotation but not primary bench |
| MINORS | Development league / end of roster |
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

Each calculator follows this structure:
1. **Base formula**: Weighted average of primary attributes (denominator = sum of weights)
2. **Threshold bonuses**: If an attribute exceeds a threshold, add/subtract a fixed value
3. **Conditional modifiers**: Position-dependent or combination-based adjustments

Output is a `BigDecimal` rounded to 1 decimal place via `round()`.

### Existing Calculators (13)

#### Acumen

**What it models**: In-game basketball IQ — reading plays, making smart decisions in real-time.

```
Base = (intelligence×2 + handle + luck + cohesion×2) / 6

Bonuses:
  agility > 8: +1
  speed > 8: +1

Ego penalty:
  ego > 9: -3 | > 8: -2 | > 7: -1

Threshold adjustments (applied to handle, intelligence, luck, cohesion each):
  > 10: +5 | > 9: +3 | > 8: +2 | > 7: +1
  < 1: -4 | < 2: -3 | < 3: -2 | < 4: -1

Shot selection bonus:
  > 10: +3 | > 9: +2 | > 8: +1

Experience (yearsPro):
  > 11: +4 | > 10: +3.5 | > 9: +3 | > 8: +2.5 | > 7: +2 | > 5: +1
  == 1: -2 | == 2: -1
```

#### Ball Security

**What it models**: Ability to protect the ball and avoid turnovers.

```
Base = (determination×2 + handle×4 + intelligence×3 + luck) / 10

Energy penalty (reckless play at high energy):
  energy > 9: -2 | > 8: -1

Ego penalty:
  ego > 9: -2 | > 8: -1

Endurance bonus (maintains composure):
  endurance > 9: +3 | > 8: +2 | > 7: +1
```

#### Defense Rebound

**What it models**: Boxing out and securing defensive boards.

```
Base = (determination×3 + energy + intelligence + size×2 + strength×2) / 9

Agility bonus:
  > 9: +3.5 | > 8: +2.5 | > 7: +1.5 | > 6: +1

Size bonus:
  > 9: +3 | > 8: +2 | > 7: +1

Speed bonus:
  > 8: +2 | > 6: +1

Ego (want-the-ball factor):
  > 8: +3 | > 6: +1
  < 2: -3 | < 4: -1

Endurance penalty:
  < 2: -3 | < 3: -2 | < 4: -1

Size (additional):
  < 2: -3 | < 3: -2 | > 7: +1 | > 9: +2
```

#### Drive

**What it models**: Ability to attack the basket off the dribble.

```
Base = (agility×3 + determination + handle×2 + speed) / 7

Threshold adjustments (applied to ego, shotSkill, speed, strength each):
  > 9: +3 | > 7: +2 | > 6: +1
  < 2: -3 | < 3: -2 | < 4: -1

Size penalty (big men can't drive):
  > 9: -3 | > 7: -2 | > 6: -1

Energy penalty:
  < 1: -4 | < 3: -3 | < 4: -1

Age decline (yearsPro):
  > 14: -4 | > 12: -3 | > 10: -2 | > 8: -1
```

#### Free Throws

**What it models**: Free throw shooting accuracy.

```
Base = (shotSkill×4 + shotSelection + luck + intelligence) / 7

Experience bonus (yearsPro):
  > 11: +3 | > 9: +2.5 | > 7: +2 | > 6: +1.5 | > 5: +1
```

#### Individual Defense

**What it models**: 1-on-1 defensive ability — staying in front, contesting, forcing tough shots.

```
Base = (agility×3 + determination×3 + intelligence×2 + ego + endurance×2 + handle + luck) / 13

Combination bonus (speedStrength = strength + speed):
  > 18: +6 | > 17: +4.5 | > 16: +3.5 | > 15: +2 | > 14: +1
  (else check strengthSize = strength + size):
  > 18: +3.5 | > 17: +2.5 | > 16: +1.5 | > 15: +1 | > 14: +0.5
  (else check speed alone):
  > 9: +3 | > 8: +2 | > 7: +2

Combination penalty (speedStrength):
  < 2: +5 | < 3: +4 | < 4: +3 | < 5: +2 | < 6: +1

Big but weak penalty (size > 7):
  strength < 4: +5 | < 5: +4 | < 6: +3 | < 7: +2

Small but slow penalty (size < 4):
  speed < 2: +5 | < 3: +4 | < 4: +3 | < 5: +2

Age decline (yearsPro):
  > 12: -2.5 | > 9: -2 | > 6: -1
```

#### Long Range

**What it models**: Three-point shooting ability.

```
Base = (shotSkill×3 + shotSelection×4 + luck×2 + intelligence) / 10

Shot selection bonus/penalty:
  > 9: +6 | > 8: +4 | > 7: +3 | > 6: +2.5 | > 5: +1.5
  < 1: -5 | < 2: -4 | < 3: -3 | < 4: -1.5

Shot skill bonus/penalty:
  > 9: +5 | > 8: +4 | > 7: +3 | > 6: +2
  < 1: -4 | < 2: -3 | < 3: -2 | < 4: -1.5 | < 5: -0.5

Size penalty (big men shoot less efficiently from range):
  > 9: -2.5 | > 8: -2 | > 7: -1.5 | > 6: -1
```

#### Offense Rebound

**What it models**: Crashing the offensive glass for putbacks and second chances.

```
Base = (ego + determination×3 + energy×2 + intelligence×2 + size×2 + strength×2) / 12

Threshold up (applied to determination, agility, size each):
  > 9: +3.5 | > 8: +2.5 | > 7: +1.5 | > 6: +1

Speed bonus:
  > 8: +2 | > 6: +1

Ego (want-it factor):
  > 9: +3 | > 7: +1
  < 2: -3 | < 3: -1.5

Threshold down (applied to endurance, size, determination each):
  < 1: -4 | < 2: -3.5 | < 3: -2 | < 4: -1
```

#### Passing

**What it models**: Court vision, finding open teammates, pass accuracy.

```
Base = (intelligence×2 + handle×3 + luck) / 6

Threshold adjustments (applied to handle, intelligence each):
  > 9: +5 | > 8: +4 | > 7: +3 | > 6: +2 | > 5: +1.5
  < 1: -4 | < 2: -3 | < 3: -2 | < 4: -1

Experience bonus (yearsPro):
  > 14: +4 | > 12: +3 | > 10: +2.5 | > 9: +2 | > 8: +1
```

#### Perimeter Scoring

**What it models**: Mid-range and perimeter shot-making ability.

```
Base = (shotSkill×4 + shotSelection×2 + luck + intelligence + agility + speed) / 10

Shot skill threshold:
  > 9: +5 | > 8: +4 | > 7: +3 | > 6: +2 | > 5: +1.5
  < 1: -4 | < 2: -3 | < 3: -2.5 | < 4: -1.5

Shot selection bonus:
  > 9: +4 | > 8: +3 | > 7: +2

Size penalty:
  > 9: -2 | > 8: -1.5 | > 7: -1
```

#### Post Scoring

**What it models**: Scoring in the low post — back-to-basket moves, hooks, turnarounds.

```
Base = (size×2 + strength×4 + shotSelection×3 + intelligence) / 10

Determination bonus:
  > 8: +1.5 | > 7: +1

Size + agility combo (nimble big man):
  size > 7 AND agility > 7: +2.5
  size > 7 AND agility > 6: +1.5

Size + determination combo (dominant will):
  size > 6 AND determination > 8: +4
  size > 6 AND determination > 7: +2.5
  size > 6 AND determination > 6: +1.5

Size + shot skill combo (skilled big):
  size > 5 AND shotSkill > 8: +4
  size > 5 AND shotSkill > 7: +2.5
  size > 5 AND shotSkill > 6: +1.5

Size + low luck penalty:
  size > 6 AND luck < 3: -2.5
  size > 6 AND luck < 6: -1.5
  size > 6 AND luck < 8: -0.5

Shot skill penalty:
  < 1: -4 | < 2: -3 | < 3: -2 | < 4: -1

Determination penalty:
  < 2: -3.5 | < 3: -2 | < 4: -1

Size penalty:
  < 2: -3 | < 3: -2
```

#### Team Defense

**What it models**: Help defense, rotations, communication, team defensive schemes.

```
Base = (intelligence×2 + strength + speed + cohesion×2) / 6

Ego modifier:
  > 9: -2 | > 6: -1
  < 2: +2 | < 4: +1

Threshold adjustments (applied to cohesion, intelligence, energy, strength, speed each):
  > 9: +6 | > 8: +4 | > 7: +3 | > 6: +2 | > 5: +1.5
  < 1: -6 | < 2: -4 | < 3: -2 | < 4: -1.5 | < 5: -1
```

#### Team Offense

**What it models**: Playing within a system, ball movement, spacing, unselfish play.

```
Base = (intelligence×2 + handle + energy + shotSkill + shotSelection + determination + cohesion×2) / 9

Ego penalty:
  > 9: -3 | > 8: -2 | > 7: -1

Intelligence bonus:
  > 9: +2 | > 8: +1.5 | > 7: +1
```

---

### New Calculators (10) — Proposed Formulas

These follow the same structural patterns as existing calculators. Tune thresholds after observing player distributions.

#### Finishing

**What it models**: Scoring at the rim — dunks, contested layups, lob catching, finishing through contact.

```
Base = (verticality×3 + agility×2 + strength×2 + handle) / 8

Verticality bonus:
  > 9: +4 | > 8: +3 | > 7: +2 | > 6: +1

Speed bonus (attacking in transition):
  > 8: +2 | > 7: +1

Size + strength combo (powerful finisher):
  size > 6 AND strength > 8: +2
  size > 6 AND strength > 7: +1

Energy factor:
  < 2: -3 | < 3: -2 | < 4: -1

Age decline (yearsPro):
  > 12: -3 | > 10: -2 | > 8: -1
```

#### Transition

**What it models**: Fast-break play — scoring, decision-making, and execution in the open court.

```
Base = (speed×3 + energy×2 + awareness×2 + handle) / 8

Speed bonus:
  > 9: +4 | > 8: +3 | > 7: +2

Awareness bonus (reading the break):
  > 8: +2 | > 7: +1

Handle bonus (pushing the ball):
  > 8: +2 | > 7: +1

Size penalty:
  > 9: -2 | > 8: -1

Endurance penalty:
  < 3: -2 | < 4: -1

Age decline (yearsPro):
  > 12: -3 | > 10: -2 | > 8: -1
```

#### Rim Protection

**What it models**: Shot blocking, paint deterrence, interior defense verticality.

```
Base = (size×2 + verticality×3 + awareness×2 + intelligence) / 8

Verticality bonus:
  > 9: +5 | > 8: +3.5 | > 7: +2.5 | > 6: +1.5

Size bonus:
  > 9: +3 | > 8: +2 | > 7: +1

Wingspan bonus:
  > 8: +2.5 | > 7: +1.5 | > 6: +1

Speed penalty (too slow to recover):
  < 3: -2 | < 4: -1

Small player penalty:
  size < 4: -4 | < 5: -2 | < 6: -1

Age decline (yearsPro):
  > 12: -2 | > 10: -1
```

#### Stealing

**What it models**: Active hands, passing lane disruption, on-ball pickpocketing.

```
Base = (awareness×3 + speed×2 + agility×2 + wingspan) / 8

Awareness bonus:
  > 9: +4 | > 8: +3 | > 7: +2 | > 6: +1

Wingspan bonus:
  > 8: +2.5 | > 7: +1.5 | > 6: +1

Intelligence bonus:
  > 8: +2 | > 7: +1

Aggression bonus (risk-taking):
  > 8: +1.5 | > 7: +1
  (but too aggressive is reckless):
  > 9: -1

Composure penalty (impatient = reaching fouls):
  < 3: -2 | < 4: -1

Experience (yearsPro):
  > 10: +2 | > 8: +1.5 | > 6: +1
```

#### Shot Contest

**What it models**: Ability to challenge shots without fouling — closing out, getting a hand up.

```
Base = (verticality×2 + wingspan×3 + speed×2 + awareness) / 8

Wingspan bonus:
  > 9: +4 | > 8: +3 | > 7: +2 | > 6: +1

Verticality bonus:
  > 8: +2 | > 7: +1.5 | > 6: +1

Composure bonus (avoids fouling on closeout):
  > 8: +2 | > 7: +1
  < 3: -2 | < 4: -1

Agility bonus (closing out quickly):
  > 8: +1.5 | > 7: +1

Experience (yearsPro):
  > 10: +1.5 | > 8: +1
```

#### Foul Drawing

**What it models**: Getting to the free throw line — initiating contact, selling fouls.

```
Base = (aggression×3 + agility×2 + intelligence×2 + speed) / 8

Aggression bonus:
  > 9: +4 | > 8: +3 | > 7: +2 | > 6: +1

Handle bonus (driving into contact):
  > 8: +2 | > 7: +1

Composure bonus (sells the foul calmly):
  > 8: +1.5 | > 7: +1

Size penalty (refs less likely to call fouls for big players):
  > 8: -1.5 | > 7: -1

Ego bonus (fearlessness attacking):
  > 8: +1.5 | > 7: +1
```

#### Foul Prone

**What it models**: Tendency to commit fouls. HIGHER = WORSE (more fouls committed).

```
Base = (aggression×3 + energy×2 + 5 - composure - awareness) / 8
  Note: composure and awareness REDUCE the value (subtract from numerator)
  The +5 constant centers the baseline for average players

Aggression penalty (more fouls):
  > 9: +4 | > 8: +3 | > 7: +2 | > 6: +1

Composure reduction (fewer fouls):
  > 9: -3 | > 8: -2 | > 7: -1
  < 3: +3 | < 4: +2 | < 5: +1

Awareness reduction (fewer fouls):
  > 8: -1.5 | > 7: -1

Experience helps (yearsPro):
  > 10: -2 | > 8: -1.5 | > 6: -1
  < 2: +2 | < 3: +1
```

#### Clutch

**What it models**: Performance under pressure — late game, playoffs, close-score situations.

```
Base = (composure×3 + determination×2 + shotSkill×2 + intelligence) / 8

Composure bonus:
  > 9: +4 | > 8: +3 | > 7: +2 | > 6: +1

Ego bonus (wants the big moment):
  > 8: +2.5 | > 7: +1.5 | > 6: +1

Determination bonus:
  > 8: +2 | > 7: +1

Experience bonus (yearsPro):
  > 11: +3 | > 9: +2.5 | > 7: +2 | > 5: +1

Composure penalty (chokes):
  < 2: -5 | < 3: -3 | < 4: -2 | < 5: -1

Rookie penalty (yearsPro):
  == 0: -3 | == 1: -2 | == 2: -1
```

#### Screen Setting

**What it models**: Pick quality, screen angles, roll/pop timing.

```
Base = (strength×3 + size×2 + cohesion×2 + awareness) / 8

Strength bonus:
  > 9: +3.5 | > 8: +2.5 | > 7: +1.5 | > 6: +1

Size bonus:
  > 8: +2 | > 7: +1.5 | > 6: +1

Awareness bonus (timing the roll/pop):
  > 8: +2 | > 7: +1

Cohesion bonus (unselfish screening):
  > 8: +1.5 | > 7: +1
  < 3: -2 | < 4: -1

Intelligence bonus (reading defensive switches):
  > 8: +1 | > 7: +0.5

Ego penalty (selfish players don't set hard screens):
  > 9: -2 | > 8: -1.5 | > 7: -1
```

#### Off-Ball Movement

**What it models**: Cutting, spacing, finding open spots, relocating without the ball.

```
Base = (awareness×3 + speed×2 + intelligence×2 + cohesion) / 8

Awareness bonus:
  > 9: +4 | > 8: +3 | > 7: +2 | > 6: +1

Intelligence bonus:
  > 8: +2 | > 7: +1.5 | > 6: +1

Speed bonus (getting open):
  > 8: +1.5 | > 7: +1

Cohesion bonus (playing within the system):
  > 8: +1.5 | > 7: +1
  < 3: -2 | < 4: -1

Ego penalty (ball-watching, standing still):
  > 9: -2 | > 8: -1.5 | > 7: -1

Experience (yearsPro):
  > 10: +2 | > 8: +1.5 | > 6: +1
```

---

## How New Attributes Map to Game Engine Needs

Every possession in the Phase 3 game engine resolves through decisions that now have attribute coverage:

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

---

## Implementation Plan

### Summary of Changes

- **5 new base attributes** added to: DB schema, CSV data, entity, API spec, mapper
- **10 new skill calculators** added to: skill calculator classes, SkillMapper, API spec, tests
- **Existing calculators updated** to incorporate new attributes where relevant

### Step 1: Database Schema Migration

Create `release.1.0.2.sql` Liquibase changeset to add 5 columns to the player table:

```sql
ALTER TABLE gametime.player ADD COLUMN verticality SMALLINT;
ALTER TABLE gametime.player ADD COLUMN wingspan SMALLINT;
ALTER TABLE gametime.player ADD COLUMN composure SMALLINT;
ALTER TABLE gametime.player ADD COLUMN aggression SMALLINT;
ALTER TABLE gametime.player ADD COLUMN awareness SMALLINT;
```

Use nullable columns initially so existing data isn't broken. After CSV reload, add NOT NULL constraints.

### Step 2: Update Data Load SQL (420 players)

The player data lives in `release.1.0.1.dataload.sql` as ~420 inline INSERT statements. Each INSERT must be updated to:
1. Add the 5 new columns to the column list
2. Append 5 computed values to each VALUES clause

Write a script that:
1. Parses each existing INSERT to extract `agility`, `energy`, `size`, `strength`, `intelligence`, `determination`, `shotSelection`
2. Computes new values using these formulas:

| New Attribute | Formula |
|---------------|---------|
| verticality | `ceil((agility + energy) / 2)` |
| wingspan | `ceil((size + strength) / 2)` |
| composure | `ceil((intelligence + determination) / 2)` |
| aggression | `ceil((determination + energy) / 2)` |
| awareness | `ceil((intelligence + shotSelection) / 2)` |

3. Outputs the full updated SQL file with new columns appended to every INSERT

Values will naturally stay in existing 1–15 range. Hand-tune star players after generation if needed.

### Step 4: Update PlayerEntity

Add 5 new fields to `PlayerEntity.java`:
```java
private Integer verticality;
private Integer wingspan;
private Integer composure;
private Integer aggression;
private Integer awareness;
```

### Step 5: Update OpenAPI Spec

Add to `gametime.yaml` Player schema:
```yaml
verticality:
  type: integer
  format: int32
wingspan:
  type: integer
  format: int32
composure:
  type: integer
  format: int32
aggression:
  type: integer
  format: int32
awareness:
  type: integer
  format: int32
```

Add to PlayerSkills schema:
```yaml
finishing:
  type: number
transition:
  type: number
rimProtection:
  type: number
stealing:
  type: number
shotContest:
  type: number
foulDrawing:
  type: number
foulProne:
  type: number
clutch:
  type: number
screenSetting:
  type: number
offBallMovement:
  type: number
```

### Step 6: Implement New Skill Calculators

Create 10 new classes implementing `SkillCalculator`:

1. `FinishingSkillCalculator`
2. `TransitionSkillCalculator`
3. `RimProtectionSkillCalculator`
4. `StealingSkillCalculator`
5. `ShotContestSkillCalculator`
6. `FoulDrawingSkillCalculator`
7. `FoulProneSkillCalculator`
8. `ClutchSkillCalculator`
9. `ScreenSettingSkillCalculator`
10. `OffBallMovementSkillCalculator`

### Step 7: Update EntityMapper

Wire new attributes through `EntityMapper` so the 5 new fields map from entity to model.

### Step 8: Update SkillMapper

Register all 10 new calculators in `SkillMapper` and map outputs to the PlayerSkills model.

### Step 9: Update Existing Calculators (optional enhancements)

Consider incorporating new attributes into existing calculators:
- `individualDefense` ← add `awareness`, `wingspan`
- `drive` ← add `verticality` (finishing through contact)
- `offenseRebound` ← add `verticality`, `wingspan`
- `defenseRebound` ← add `wingspan`
- `freeThrows` ← add `composure` (pressure factor)

### Step 10: Tests

- Unit tests for each of the 10 new calculators (follow existing pattern in `SkillSetCalculatorUnitTest`)
- Update `BASE_PLAYER()` factory to include the 5 new attributes
- Integration test: verify full player GET returns all 23 skills
- Verify existing tests still pass (new attributes need defaults in test fixtures)

### Step 11: Regenerate API Stubs

Run `mvn clean compile` from parent to regenerate the OpenAPI delegate and model classes with new fields.

---

## Execution Order

```
1. OpenAPI spec update              (generates new model stubs)
2. DB migration SQL (release.1.0.2) (schema adds 5 columns with defaults)
3. PlayerEntity update              (Java model matches DB)
4. EntityMapper update              (wires new fields)
5. Update dataload SQL              (add 5 values to all 420 INSERTs)
6. Register migration in changelog  (changelog.yml includes new file)
7. Skill calculators (10 new)       (business logic)
8. SkillMapper wiring               (connects calculators to API)
9. Existing calculator tweaks       (incorporate new attrs)
10. Tests                           (verify everything)
11. Build verification              (mvn clean test passes)
```

Steps 1–6 are schema/data work. Steps 7–11 are logic/test work. Step 5 (updating 420 INSERT statements) is the most mechanical piece — use a script to parse existing values, compute new ones, and regenerate the file.

---

## Implementation Reference

This section contains concrete code patterns, file paths, and structural details needed to implement the changes above without re-exploring the codebase.

### Key File Paths

```
gametime-service/
├── gametime-api/yml/gametime.yaml                              # OpenAPI spec (source of truth for API)
├── gametime-app/src/main/java/software/daveturner/gametime/
│   ├── entity/PlayerEntity.java                                # JPA entity (Lombok @Data)
│   ├── entity/Position.java                                    # Position enum
│   ├── entity/Status.java                                      # Status enum
│   ├── mapper/SkillCalculator.java                             # Interface all calculators implement
│   ├── mapper/SkillMapper.java                                 # Wires calculators → PlayerSkills model
│   ├── mapper/EntityMapper.java                                # Maps PlayerEntity → Player model
│   ├── mapper/DriveSkillCalculator.java                        # Example calculator (use as template)
│   ├── mapper/[OtherSkill]SkillCalculator.java                 # 12 more existing calculators
│   ├── repo/PlayerRepo.java                                    # CrudRepository<PlayerEntity, String>
│   ├── service/GametimeServiceImp.java                         # Service layer
│   └── api/V1ApiDelegateimpl.java                              # REST delegate
├── gametime-app/src/main/resources/db/
│   ├── changelog.yml                                           # Liquibase master changelog
│   ├── release.1.0.1.sql                                       # Player table DDL
│   ├── release.1.0.1.dataload.sql                              # ACTIVE player data (420 INSERT statements)
│   └── player.csv                                              # CSV file (NOT in active changelog)
├── gametime-app/src/test/java/software/daveturner/gametime/
│   ├── mapper/SkillSetCalculatorUnitTest.java                  # Base test class with BASE_PLAYER()
│   └── mapper/DriveSkillCalculatorTest.java                    # Example test (use as template)
```

### IMPORTANT: Data Load Mechanism

The player data is loaded via **inline SQL INSERT statements** in `release.1.0.1.dataload.sql`, NOT the CSV file. The CSV file exists but is NOT referenced in `changelog.yml`. When adding new attributes, you must update ALL ~420 INSERT statements in that SQL file.

The `changelog.yml` include order is:
```yaml
databaseChangeLog:
- include:
    comment: Create gametime schema
    file: classpath:db/release.0.0.1.sql
- include:
    comment: Create audit rows
    file: classpath:db/release.1.0.0.sql
- include:
    comment: Create table layout for release 1
    file: classpath:db/release.1.0.1.sql
- include:
    comment: Load Coach table
    file: classpath:db/release.1.0.1.coach.dataload.xml
- include:
    comment: Load GM table
    file: classpath:db/release.1.0.1.gm.dataload.yml
- include:
    comment: Load Gametime tables
    file: classpath:db/release.1.0.1.dataload.sql
```

New changesets should be added AFTER the existing entries. For schema migration, add a new SQL file (e.g., `release.1.0.2.sql`) and register it in `changelog.yml`.

### SkillCalculator Interface

```java
package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public interface SkillCalculator {
    BigDecimal calc(Player player);

    default BigDecimal round(double d) {
        BigDecimal bd = new BigDecimal(Double.toString(d));
        bd = bd.setScale(1, RoundingMode.HALF_UP);
        return bd;
    }
}
```

### Example Calculator: DriveSkillCalculator

This is the pattern to follow for all new calculators:

```java
package software.daveturner.gametime.mapper;

import org.springframework.stereotype.Component;
import software.daveturner.gametime.model.Player;
import java.math.BigDecimal;

@Component
public class DriveSkillCalculator implements SkillCalculator {

    @Override
    public BigDecimal calc(Player player) {
        // Step 1: Weighted average of primary attributes
        double value = ((player.getAgility() * 3) + player.getDetermination()
                        + (player.getHandle() * 2) + player.getSpeed()) / 7d;

        // Step 2: Threshold bonuses/penalties from secondary attributes
        value = calc(player.getEgo(), value);
        value = calc(player.getShotSkill(), value);
        value = calc(player.getSpeed(), value);
        value = calc(player.getStrength(), value);

        // Step 3: Conditional modifiers (size, energy, experience)
        if(player.getSize() > 9) { value -= 3;}
        else if(player.getSize() > 7) { value -= 2;}
        else if(player.getSize() > 6) { value -= 1;}

        if(player.getEnergy() < 1) { value -= 4;}
        else if(player.getEnergy() < 3) { value -= 3;}
        else if(player.getEnergy() < 4) { value -= 1;}

        if(player.getYearsPro() > 14) { value -= 4; }
        else if(player.getYearsPro() > 12) { value -= 3; }
        else if(player.getYearsPro() > 10) { value -= 2; }
        else if(player.getYearsPro() > 8) { value -= 1; }

        return round(value);
    }

    // Reusable threshold helper: >9 = +3, >7 = +2, >6 = +1, <2 = -3, <3 = -2, <4 = -1
    private double calc(int a, double currentVal) {
        if(a > 9) { currentVal += 3;}
        else if(a > 7) { currentVal += 2;}
        else if(a > 6) { currentVal += 1;}
        else if(a < 2) { currentVal -= 3;}
        else if(a < 3) { currentVal -= 2;}
        else if(a < 4) { currentVal -= 1;}
        return currentVal;
    }
}
```

**Key patterns:**
1. Start with a weighted average of primary inputs (denominator = sum of weights)
2. Apply threshold bonuses via a helper method (the `calc(int, double)` pattern)
3. Apply conditional adjustments for edge cases (size, energy, yearsPro)
4. Always return `round(value)` — this gives a BigDecimal with 1 decimal place

### SkillMapper Wiring Pattern

Each calculator is `@Autowired` by concrete type, then called in `mapSkills()`:

```java
@Component
public class SkillMapper {

    @Autowired
    private DriveSkillCalculator driveSkillCalculator;
    // ... one field per calculator ...

    public PlayerSkills mapSkills(Player player) {
        PlayerSkills skills = new PlayerSkills();
        skills.setDrive(driveSkillCalculator.calc(player));
        // ... one setter per calculator ...
        return skills;
    }
}
```

To add new calculators: add `@Autowired` field + `skills.setXxx(xxxCalculator.calc(player))` line.

### EntityMapper Pattern

The `mapEntityToPlayer` method maps each entity field to the generated model. New attributes need one line each:

```java
// Existing pattern in EntityMapper.mapEntityToPlayer():
player.setAgility(e.getAgility());
player.setCharisma(e.getCharisma());
// ... etc ...

// Add these for new attributes:
player.setVerticality(e.getVerticality());
player.setWingspan(e.getWingspan());
player.setComposure(e.getComposure());
player.setAggression(e.getAggression());
player.setAwareness(e.getAwareness());

// Skills are computed LAST (after all attributes are set on the model):
player.setSkills(skillMapper.mapSkills(player));
```

### PlayerEntity Pattern

Uses Lombok `@Data` so just adding fields is enough (getters/setters are generated):

```java
@Entity
@Table(name = "player", schema = "gametime")
@Cacheable
@Data
public class PlayerEntity {
    // ... existing fields ...
    private Integer strength;

    // Add after existing attribute fields:
    private Integer verticality;
    private Integer wingspan;
    private Integer composure;
    private Integer aggression;
    private Integer awareness;

    // Team relationship stays at bottom
    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name="team_id", nullable=true)
    private TeamEntity team;
}
```

### Test Base Class Pattern

```java
package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import software.daveturner.gametime.model.Player;

public class SkillSetCalculatorUnitTest {

    protected static final int AVERAGE_ATTRIBUTE = 5;
    protected static final int DEFAULT_YEARS_PRO = 5;
    protected static final double AVERAGE_SKILLSET = 5D;

    public Player player;
    SkillCalculator calc;

    @BeforeEach
    public void setup() {
        player = BASE_PLAYER();
    }

    protected Player BASE_PLAYER() {
        Player player = new Player();
        player.setId("330eb324-3382-4fca-b20e-2b4c7e278047");
        player.setFirstName("firstname");
        player.setLastName("lastname");
        player.setYearsPro(DEFAULT_YEARS_PRO);
        player.setAgility(AVERAGE_ATTRIBUTE);
        player.setCharisma(AVERAGE_ATTRIBUTE);
        player.setDetermination(AVERAGE_ATTRIBUTE);
        player.setEgo(AVERAGE_ATTRIBUTE);
        player.setEndurance(AVERAGE_ATTRIBUTE);
        player.setEnergy(AVERAGE_ATTRIBUTE);
        player.setHandle(AVERAGE_ATTRIBUTE);
        player.setHealth(AVERAGE_ATTRIBUTE);
        player.setIntelligence(AVERAGE_ATTRIBUTE);
        player.setLuck(AVERAGE_ATTRIBUTE);
        player.setCohesion(AVERAGE_ATTRIBUTE);
        player.setShotSelection(AVERAGE_ATTRIBUTE);
        player.setShotSkill(AVERAGE_ATTRIBUTE);
        player.setSize(AVERAGE_ATTRIBUTE);
        player.setSpeed(AVERAGE_ATTRIBUTE);
        player.setStrength(AVERAGE_ATTRIBUTE);
        player.setYearsPro(4);
        // NEW: must add these for new attributes
        player.setVerticality(AVERAGE_ATTRIBUTE);
        player.setWingspan(AVERAGE_ATTRIBUTE);
        player.setComposure(AVERAGE_ATTRIBUTE);
        player.setAggression(AVERAGE_ATTRIBUTE);
        player.setAwareness(AVERAGE_ATTRIBUTE);
        return player;
    }

    public void assertPlayer(double d, SkillCalculator calc) {
        Assertions.assertEquals(calc.round(d), calc.calc(player));
    }
}
```

### Example Test: DriveSkillCalculatorTest

```java
package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DriveSkillCalculatorTest extends SkillSetCalculatorUnitTest {

    @BeforeEach
    public void setup() {
        calc = new DriveSkillCalculator();
        player = BASE_PLAYER();
    }

    @Test
    public void ensureAverageDriveReturnsExpected() {
        assertPlayer(AVERAGE_SKILLSET, calc);
    }

    @Test
    public void ensureBigEgoReturnsExpected() {
        player.setEgo(9); assertPlayer(7, calc);
    }

    @Test
    public void ensureLargeSizeReturnsExpected() {
        player.setSize(9); assertPlayer(3, calc);
    }
}
```

**Test conventions:**
- Extend `SkillSetCalculatorUnitTest`
- Override `setup()` to instantiate the specific calculator
- First test: average player (all 5s) should produce `AVERAGE_SKILLSET` (5.0)
- Additional tests: tweak one attribute, assert expected output
- Use `assertPlayer(expectedValue, calc)` helper

### OpenAPI Spec Structure

Player attributes go in the `Player` schema (top-level integer fields). Skills go in `PlayerSkills` schema (number fields). Example location in `gametime.yaml`:

```yaml
    Player:
      type: object
      properties:
        # ... existing attributes ...
        strength:
          type: integer
          format: int32
        # ADD NEW ATTRIBUTES HERE (before skills ref):
        verticality:
          type: integer
          format: int32
        wingspan:
          type: integer
          format: int32
        composure:
          type: integer
          format: int32
        aggression:
          type: integer
          format: int32
        awareness:
          type: integer
          format: int32
        skills:
          $ref: "#/components/schemas/PlayerSkills"

    PlayerSkills:
      type: object
      properties:
        # ... existing skills ...
        defenseRebound:
          type: number
        # ADD NEW SKILLS HERE:
        finishing:
          type: number
        transition:
          type: number
        rimProtection:
          type: number
        stealing:
          type: number
        shotContest:
          type: number
        foulDrawing:
          type: number
        foulProne:
          type: number
        clutch:
          type: number
        screenSetting:
          type: number
        offBallMovement:
          type: number
```

### Data Load: SQL INSERT Format

Each INSERT in `release.1.0.1.dataload.sql` follows this exact column order:

```sql
insert into gametime.player (id, first_name, last_name, team_id, draft_slot, years_pro,
  height, weight, status, position, size, strength, intelligence, shot_skill, shot_selection,
  endurance, agility, handle, speed, energy, health, determination, luck, charisma, ego, cohesion)
values ('uuid', 'First', 'Last', 'TEAM', 'slot', 'yearsPro',
  'height', 'weight', 'STATUS', 'POS', size, str, int, ss, ssel,
  end, agi, han, spd, ene, hea, det, luk, cha, ego, coh);
```

To add new attributes, extend BOTH the column list and values. Add the 5 new columns at the end (before the closing paren):

```sql
insert into gametime.player (id, first_name, last_name, team_id, draft_slot, years_pro,
  height, weight, status, position, size, strength, intelligence, shot_skill, shot_selection,
  endurance, agility, handle, speed, energy, health, determination, luck, charisma, ego, cohesion,
  verticality, wingspan, composure, aggression, awareness)
values ('uuid', 'First', 'Last', 'TEAM', 'slot', 'yearsPro',
  'height', 'weight', 'STATUS', 'POS', size, str, int, ss, ssel,
  end, agi, han, spd, ene, hea, det, luk, cha, ego, coh,
  vert, wing, comp, aggr, awar);
```

There are ~420 INSERT statements that ALL need updating. A script should:
1. Parse each existing INSERT to extract position + existing attributes
2. Derive new attribute values using the formulas in the plan
3. Output the updated INSERT statements

### Attribute Derivation Formulas (for script)

Simple ceiling of the average of two existing attributes. No position adjustments — keeps values honest to what's already hand-tuned.

```
verticality = ceil((agility + energy) / 2)
wingspan    = ceil((size + strength) / 2)
composure   = ceil((intelligence + determination) / 2)
aggression  = ceil((determination + energy) / 2)
awareness   = ceil((intelligence + shotSelection) / 2)
```

These will naturally land in the same 1–15 range as existing attributes since inputs are already in that range.

### Build Command

Always use SDKMAN JDK 21:
```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean test
```

### Schema Migration: Adding to changelog.yml

Add new entry at the bottom:
```yaml
- include:
    comment: Add new player attributes (verticality, wingspan, composure, aggression, awareness)
    file: classpath:db/release.1.0.2.sql
```

The new SQL file (`release.1.0.2.sql`) should use Liquibase formatted SQL:
```sql
-- liquibase formatted sql

-- changeset dave:1.02.1 failOnError:true splitStatements:true
ALTER TABLE gametime.player ADD COLUMN verticality SMALLINT DEFAULT 5;
ALTER TABLE gametime.player ADD COLUMN wingspan SMALLINT DEFAULT 5;
ALTER TABLE gametime.player ADD COLUMN composure SMALLINT DEFAULT 5;
ALTER TABLE gametime.player ADD COLUMN aggression SMALLINT DEFAULT 5;
ALTER TABLE gametime.player ADD COLUMN awareness SMALLINT DEFAULT 5;
```

**NOTE:** Use DEFAULT 5 on the ALTER so existing rows get a baseline value. The dataload SQL will set real values on fresh installs. For existing dev databases, run `docker compose down -v && docker compose up -d` to reset.
