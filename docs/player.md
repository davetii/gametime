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
| SG | Shooting Guard |
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

The 13 original skills plus 10 newer ones (finishing, transition, rimProtection,
stealing, shotContest, foulDrawing, foulProne, clutch, screenSetting,
offBallMovement) are listed with their primary inputs under "Derived Skills" above.
`foulProne` is inverted (higher = worse) but still centers at 10 for an average player.


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

> **Note (as implemented):** The formulas below were the *original proposal*. The
> actual derivation used during the 1–20 rescale is more nuanced (weighted, with
> percentile bonuses for some attributes). See "Attribute Derivation Formulas
> (as implemented)" below and [docs/TODO.md](TODO.md) for the authoritative formulas.

| New Attribute | Formula (as implemented, 1–20 scale) |
|---------------|---------|
| verticality | `floor((energy*2 + agility + strength) / 4)` + tiered str/agi freak bonus |
| wingspan | `floor((size*2 + energy + agility) / 4)` |
| composure | `floor((intelligence + determination + endurance + shotSelection) / 4)` + tiered intel/det bonus |
| aggression | `floor((ego + determination*2 + energy*2) / 5)` |
| awareness | `floor((intelligence + shotSelection) / 2)` + additive top-20% intel/agility bonuses |

3. Outputs the full updated SQL file with new columns appended to every INSERT

Values land in the 1–20 range (average ~10). Derived attributes carry no independent
signal — hand-tune individual players where their reach/explosiveness/etc. should
diverge from what their other attributes imply.

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

### Attribute Derivation Formulas (as implemented)

The new attributes were derived from existing ones on the 1–20 scale. Each is a
weighted floor-average, and three carry an additional percentile bonus that rewards
players elite in the contributing attributes. All results are clamped to 1–20.

```
verticality = floor((energy*2 + agility + strength) / 4)
              + tiered bonus: +2 if strength top-10% AND agility top-20%
                              +1 if strength top-20% AND agility top-20%   (max, not additive)

wingspan    = floor((size*2 + energy + agility) / 4)

composure   = floor((intelligence + determination + endurance + shotSelection) / 4)
              + tiered bonus: +2 if intelligence top-10% AND determination top-10%
                              +1 if intelligence top-25% AND determination top-25%  (max, not additive)

aggression  = floor((ego + determination*2 + energy*2) / 5)

awareness   = floor((intelligence + shotSelection) / 2)
              + additive bonus: +1 if intelligence top-20%, +1 if agility top-20%  (both can apply)
```

**Caveats:**
- These attributes are *derived* — they carry no independent signal. Hand-tune any
  player whose explosiveness/reach/etc. should diverge from their other attributes.
- The percentile bonuses were computed off a one-time data snapshot. If the existing
  attributes are re-tuned later, regenerate these from scratch (base + bonus
  together) rather than re-applying bonuses on top of already-bonused values.

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
