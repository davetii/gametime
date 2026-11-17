# Simulation constants

> **GENERATED — do not edit.** Regenerate from the repo root:
>
> ```bash
> mvn -f gametime-service/pom.xml -pl gametime-tools compile exec:java
> ```
>
> A stale copy fails `mvn install` (`ConstantsReferenceUpToDateTest` in gametime-tools).
>
> Sources: [SimConfig.java](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java) · [application-baseline.properties](../gametime-service/gametime-app/src/main/resources/application-baseline.properties) · [application-nineties.properties](../gametime-service/gametime-app/src/main/resources/application-nineties.properties)
>
> **64 tunables / 29 statics.**

## Tunables

`private final` fields on SimConfig, bound from `sim.*` in the profiles. Era columns are blank where the era keeps the baseline value.

### Pace / game structure

| key | accessor | type | baseline | nineties | bounds |
|---|---|---|---|---|---|
| `sim.default-possessions-per-period` | `defaultPossessionsPerPeriod()` | int | `25` | `21` | > 0 |
| `sim.ot-possessions-per-period` | `otPossessionsPerPeriod()` | int | `5` |  | > 0 |

### Shooting

| key | accessor | type | baseline | nineties | bounds |
|---|---|---|---|---|---|
| `sim.base-drive` | `baseDrive()` | double | `0.6801` | `0.5875` | [0.0, 1.0] |
| `sim.base-perimeter` | `basePerimeter()` | double | `0.4603` |  | [0.0, 1.0] |
| `sim.base-three` | `baseThree()` | double | `0.3375` | `0.3200` | [0.0, 1.0] |
| `sim.base-post` | `basePost()` | double | `0.6131` | `0.5150` | [0.0, 1.0] |
| `sim.base-assist` | `baseAssist()` | double | `0.62` | `0.5700` | [0.0, 1.0] |

### Fouls and free throws

| key | accessor | type | baseline | nineties | bounds |
|---|---|---|---|---|---|
| `sim.base-no-basket-foul` | `baseNoBasketFoul()` | double | `0.178` | `0.175` | [0.0, 1.0] |
| `sim.foul-mult-drive` | `foulMultDrive()` | double | `1.0` |  | ≥ 0.0 |
| `sim.foul-mult-post` | `foulMultPost()` | double | `1.0` |  | ≥ 0.0 |
| `sim.foul-mult-perimeter` | `foulMultPerimeter()` | double | `0.30` | `0.42` | ≥ 0.0 |
| `sim.foul-mult-three` | `foulMultThree()` | double | `0.133` | `0.160` | ≥ 0.0 |
| `sim.and-one-base` | `andOneBase()` | double | `0.055` |  | [0.0, 1.0] |
| `sim.ft-base` | `ftBase()` | double | `0.705` |  | [0.0, 1.0] |
| `sim.bonus-fouls-per-period` | `bonusFoulsPerPeriod()` | int | `5` |  | > 0 |
| `sim.foul-out-limit` | `foulOutLimit()` | int | `6` |  | > 0 |
| `sim.non-shooting-foul-share` | `nonShootingFoulShare()` | double | `0.427` |  | [0.0, 1.0] |
| `sim.foul-trouble-keep-probabilities` | `foulTroubleKeepProbabilities()` | double[] | `1.0,1.0,1.0,0.90,0.70,0.40,1.0` |  | exactly 7 entries |

### Rebounding and loose balls

| key | accessor | type | baseline | nineties | bounds |
|---|---|---|---|---|---|
| `sim.base-offensive-rebound` | `baseOffensiveRebound()` | double | `0.27` | `0.31` | [0.0, 1.0] |
| `sim.max-offensive-retentions-per-possession` | `maxOffensiveRetentionsPerPossession()` | int | `3` |  | > 0 |
| `sim.oob-total-weight` | `oobTotalWeight()` | double | `0.07` |  | [0.0, 1.0] |
| `sim.oob-defense-weight` | `oobDefenseWeight()` | double | `0.60` |  | ≥ 0.0 |
| `sim.oob-offense-weight` | `oobOffenseWeight()` | double | `0.40` |  | ≥ 0.0 |
| `sim.base-block-drive` | `baseBlockDrive()` | double | `0.084` |  | [0.0, 1.0] |
| `sim.base-block-post` | `baseBlockPost()` | double | `0.072` |  | [0.0, 1.0] |
| `sim.base-block-perimeter` | `baseBlockPerimeter()` | double | `0.0345` |  | [0.0, 1.0] |
| `sim.base-block-three` | `baseBlockThree()` | double | `0.0075` |  | [0.0, 1.0] |
| `sim.block-recovered-defense` | `blockRecoveredDefense()` | double | `0.45` |  | ≥ 0.0 |
| `sim.block-recovered-offense` | `blockRecoveredOffense()` | double | `0.30` |  | ≥ 0.0 |
| `sim.block-oob-defense` | `blockOobDefense()` | double | `0.13` |  | ≥ 0.0 |
| `sim.block-oob-offense` | `blockOobOffense()` | double | `0.12` |  | ≥ 0.0 |
| `sim.rebound-foul-base` | `reboundFoulBase()` | double | `0.055` | `0.062` | [0.0, 1.0] |
| `sim.rebound-foul-defense-weight` | `reboundFoulDefenseWeight()` | double | `0.75` |  | ≥ 0.0 |
| `sim.rebound-foul-offense-weight` | `reboundFoulOffenseWeight()` | double | `0.25` |  | ≥ 0.0 |
| `sim.offensive-rebounder-shot-weight` | `offensiveRebounderShotWeight()` | double | `2.0` |  | ≥ 0.0 |

### Turnovers

| key | accessor | type | baseline | nineties | bounds |
|---|---|---|---|---|---|
| `sim.base-turnover` | `baseTurnover()` | double | `0.062` | `0.043` | [0.0, 1.0] |
| `sim.to-weight-stolen` | `toWeightStolen()` | double | `56.0` |  | ≥ 0.0 |
| `sim.to-weight-shot-clock` | `toWeightShotClock()` | double | `10.0` |  | ≥ 0.0 |
| `sim.to-weight-offensive-foul` | `toWeightOffensiveFoul()` | double | `9.0` |  | ≥ 0.0 |
| `sim.to-weight-bad-pass` | `toWeightBadPass()` | double | `8.0` |  | ≥ 0.0 |
| `sim.to-weight-travelling` | `toWeightTravelling()` | double | `6.0` |  | ≥ 0.0 |
| `sim.to-weight-lost-ball-oob` | `toWeightLostBallOob()` | double | `4.0` |  | ≥ 0.0 |
| `sim.to-weight-three-seconds` | `toWeightThreeSeconds()` | double | `3.0` |  | ≥ 0.0 |
| `sim.to-weight-eight-seconds-backcourt` | `toWeightEightSecondsBackcourt()` | double | `2.0` |  | ≥ 0.0 |
| `sim.to-weight-over-and-back` | `toWeightOverAndBack()` | double | `2.0` |  | ≥ 0.0 |

### Fatigue and rotation

| key | accessor | type | baseline | nineties | bounds |
|---|---|---|---|---|---|
| `sim.energy-drain-per-possession` | `energyDrainPerPossession()` | double | `2.6` |  | ≥ 0.0 |
| `sim.energy-recovery-per-possession` | `energyRecoveryPerPossession()` | double | `5.5` |  | ≥ 0.0 |
| `sim.min-drain-scale` | `minDrainScale()` | double | `0.25` |  | [0.0, 1.0] |
| `sim.fatigue-max-penalty` | `fatigueMaxPenalty()` | double | `0.28` |  | [0.0, 1.0] |
| `sim.base-sub-energy-threshold` | `baseSubEnergyThreshold()` | double | `62.0` |  | ≥ 0.0 |
| `sim.starter-sub-threshold-bonus` | `starterSubThresholdBonus()` | double | `8.0` |  | ≥ 0.0 |
| `sim.base-rotation-depth` | `baseRotationDepth()` | int | `4` |  | > 0 |
| `sim.foul-trouble-sit-probabilities` | `foulTroubleSitProbabilities()` | double[] | `0.0,0.0,0.0,0.120,0.500,0.900,0.0` |  | exactly 7 entries |
| `sim.foul-trouble-starter-bonus` | `foulTroubleStarterBonus()` | double | `0.15` |  | ≥ 0.0 |
| `sim.foul-trouble-bench-discount-per-slot` | `foulTroubleBenchDiscountPerSlot()` | double | `0.05` |  | ≥ 0.0 |
| `sim.foul-trouble-min-roster-factor` | `foulTroubleMinRosterFactor()` | double | `0.70` |  | ≥ 0.0 |
| `sim.foul-trouble-freshness-margin` | `foulTroubleFreshnessMargin()` | double | `1.0` |  | ≥ 0.0 |

### Rare events

| key | accessor | type | baseline | nineties | bounds |
|---|---|---|---|---|---|
| `sim.technical-fouls-per-team-game` | `technicalFoulsPerTeamGame()` | double | `0.35` |  | ≥ 0.0 |
| `sim.flagrant-fouls-per-team-game` | `flagrantFoulsPerTeamGame()` | double | `0.16` |  | ≥ 0.0 |
| `sim.flagrant-two-share` | `flagrantTwoShare()` | double | `0.15` |  | [0.0, 1.0] |

### Shot mix (§3.17, decisions.md #040 C/K)

| key | accessor | type | baseline | nineties | bounds |
|---|---|---|---|---|---|
| `sim.shot-share-drive` | `shotShareDrive()` | double | `1.0` | `1.0` | ≥ 0.0 |
| `sim.shot-share-perimeter` | `shotSharePerimeter()` | double | `0.55` | `1.15` | ≥ 0.0 |
| `sim.shot-share-post` | `shotSharePost()` | double | `0.55` | `0.95` | ≥ 0.0 |
| `sim.shot-share-three` | `shotShareThree()` | double | `1.256` | `0.30` | ≥ 0.0 |

## Statics

`public static final` on SimConfig: rules of basketball and the shape of the model. No profile can set them.

### Possession count (Decision B)

| name | type | value | source |
|---|---|---|---|
| `PERIODS` | int | `4` | [SimConfig.java:68](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L68) |

### Probability sensitivity (Decision C)

| name | type | value | source |
|---|---|---|---|
| `SENSITIVITY` | double | `0.5` | [SimConfig.java:72](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L72) |
| `PROB_FLOOR` | double | `0.02` | [SimConfig.java:73](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L73) |
| `PROB_CEILING` | double | `0.97` | [SimConfig.java:74](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L74) |
| `SCALE_AVG` | double | `10.0` | [SimConfig.java:75](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L75) |

### Free throw

| name | type | value | source |
|---|---|---|---|
| `FT_SENSITIVITY` | double | `0.20` | [SimConfig.java:166](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L166) |
| `FREE_THROWS_PER_FOUL` | int | `2` | [SimConfig.java:167](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L167) |

### Rebounding and loose balls

| name | type | value | source |
|---|---|---|---|
| `FREE_THROW_REBOUND_LEAN` | double | `0.68` | [SimConfig.java:214](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L214) |
| `OFFENSIVE_REBOUNDER_ASSIST_LEAN` | double | `0.5` | [SimConfig.java:263](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L263) |

### Blocks

| name | type | value | source |
|---|---|---|---|
| `BLOCK_SENSITIVITY` | double | `0.12` | [SimConfig.java:312](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L312) |

### Turnover causes

| name | type | value | source |
|---|---|---|---|
| `TO_CAUSE_SENSITIVITY` | double | `0.20` | [SimConfig.java:371](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L371) |

### Rebounding fouls and the bonus

| name | type | value | source |
|---|---|---|---|
| `REBOUND_FOUL_SENSITIVITY` | double | `0.10` | [SimConfig.java:404](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L404) |

### And-1

| name | type | value | source |
|---|---|---|---|
| `AND_ONE_SENSITIVITY` | double | `0.10` | [SimConfig.java:440](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L440) |
| `AND_ONE_FREE_THROWS` | int | `1` | [SimConfig.java:446](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L446) |

### Coach modifiers

| name | type | value | source |
|---|---|---|---|
| `COACH_SENSITIVITY` | double | `0.20` | [SimConfig.java:456](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L456) |

### Minutes, fatigue and substitution

| name | type | value | source |
|---|---|---|---|
| `MINUTES_PER_PERIOD` | int | `12` | [SimConfig.java:472](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L472) |
| `OT_MINUTES` | int | `5` | [SimConfig.java:473](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L473) |
| `MAX_ENERGY` | double | `100.0` | [SimConfig.java:480](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L480) |
| `ENDURANCE_DRAIN_SENSITIVITY` | double | `0.6` | [SimConfig.java:487](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L487) |

### Foul trouble

| name | type | value | source |
|---|---|---|---|
| `FOUL_TROUBLE_VALUE_SENSITIVITY` | double | `0.55` | [SimConfig.java:555](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L555) |

### Technical fouls

| name | type | value | source |
|---|---|---|---|
| `TECHNICAL_EJECTION_LIMIT` | int | `2` | [SimConfig.java:627](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L627) |
| `TECHNICAL_FREE_THROWS` | int | `1` | [SimConfig.java:635](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L635) |

### Shot mix

| name | type | value | source |
|---|---|---|---|
| `SHOT_MIX_SENSITIVITY` | double | `0.5` | [SimConfig.java:758](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L758) |

### Flagrant rules

| name | type | value | source |
|---|---|---|---|
| `FLAGRANT_EJECTION_LIMIT` | int | `1` | [SimConfig.java:769](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L769) |
| `FLAGRANT_FREE_THROWS` | int | `2` | [SimConfig.java:782](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L782) |
| `PERSONAL_FOULS_PER_TEAM_GAME` | double | `19.08` | [SimConfig.java:811](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L811) |

### Assists

| name | type | value | source |
|---|---|---|---|
| `ASSIST_SENSITIVITY` | double | `0.30` | [SimConfig.java:819](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L819) |

### Coach effects on shot-making and efficiency

| name | type | value | source |
|---|---|---|---|
| `ACUMEN_SENSITIVITY` | double | `0.05` | [SimConfig.java:826](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L826) |
| `TEAM_EFFICIENCY_SENSITIVITY` | double | `0.08` | [SimConfig.java:831](../gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java#L831) |

## Profiles

- `baseline`: all 64 tunables
- `nineties`: overrides 15 of 64 tunables
