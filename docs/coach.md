# Coach Domain

A coach is a team's **decision-maker model**: the inputs the engine reads to decide how a
team plays — pace, shot distribution, defensive posture, and how deep and early the bench
is used. The engine reads coach attributes and never writes them. A coach **bends** what
the players would do; he does not override it.

## Attributes

Five continuous attributes on `CoachEntity`, **1–20 with average 10**, the same scale as
player attributes ([player.md](player.md)). The model is never categorical; any display
archetype ("modern offense") is derived on read from the numbers. Seeded in `coach.csv`
(40 coaches sampled around 10) and mapped through `EntityMapper`; they are exposed on every
`Team` payload.

| Attribute | Drives | Read by |
|---|---|---|
| **pace** | Possessions per game (scales the possession **count**) | `PossessionEngine.simulate` |
| **offensiveScheme** | Shot mix: three-point lean vs. mid-range | `ShotSelector` |
| **defensiveScheme** | Defensive pressure on turnovers and fouls | `TurnoverResolver`, `FoulResolver` |
| **rotationDepth** | How far down the bench queue substitutions may reach | `RotationState` |
| **substitutionAggressiveness** | How early a tired player is pulled; foul-trouble sit chance | `RotationState` |

## Effects

Every effect is the avg-10 deviation multiplier, the same form the skill system uses:

```
f(attr) = 1 + COACH_SENSITIVITY × (attr − 10) / 10
```

So an attribute of 10 is ×1.0 (no effect). The five factors are held in `CoachModifiers`
(neutral when a team has no coach) and reach the engine through `TeamContext`.

```
basePace      × f(pace)                    → possessions per period
baseShotMix   × f(offensiveScheme)         → THREE vs. mid-range share
basePressure  × f(defensiveScheme)         → turnover / foul pressure
baseDepth     × f(rotationDepth)           → bench slots a substitution may draw from
subThreshold  × f(substitutionAggr.)       → energy at which a tired player is pulled
```

Rules to know before tuning:

- **`pace` is blended across both coaches.** The teams alternate possessions and share one
  count, so the engine averages the two `paceMultiplier`s. A fast coach against a slow one
  lands in between, and neither gets his own pace.
- **`offensiveScheme` splits `THREE` and `PERIMETER` in opposite directions.** `THREE` is
  scaled by the multiplier, `PERIMETER` by its **reciprocal**, `DRIVE`/`POST` are unscaled.
  A high-scheme coach shoots more threes and fewer mid-range jumpers. The axis is
  mid-range vs. three, **not** jumper vs. interior. `DRIVE`/`POST` move only because the
  weighted draw re-normalizes.
- **The coach does not set the league's mix**, only his team's tilt on it. The base mix is
  the four `sim.shot-share-*` tunables, and `offensiveScheme` is centred on 10 across the
  league, so the lean directions roughly cancel in aggregate.
- **`defensiveScheme` is the widest-reaching attribute.** Its `defensivePressure`
  multiplies the turnover gate, the stopped-shot foul roll, the and-1 roll and the
  rebounding-foul roll, and leans the shot-clock-violation turnover cause. An aggressive
  scheme forces more turnovers **and** concedes more fouls, and the effects multiply. A
  `defensiveScheme` change therefore moves more of the box score than any other coach
  attribute.
- **Some rolls read no coach attribute, and the coach reaches them through the parent
  foul.** The technical rate, the flagrant grade and the shooting vs. non-shooting split
  are flat. A flagrant exists only if a foul already happened, and `defensivePressure`
  scales all three parent fouls, so an aggressive defense also commits proportionally more
  flagrants and non-shooting fouls.
- **The charge is the exception to "the defense sets the fouls".** Its frequency leans on
  the offense's `teamOffense` through the turnover-cause draw, not on `defensiveScheme`, so
  `defensiveScheme` does not account for all of a team's fouls.
- **`substitutionAggressiveness` scales the threshold, and starters get a flat bonus on
  top.** `subEnergyThreshold` subtracts `sim.starter-sub-threshold-bonus` for starters, so a
  starter is pulled later than a bench player at the same energy regardless of the coach.
  The coach knob and the bonus are independent.
- **`rotationDepth`** sets the number of bench slots (`sim.base-rotation-depth` × the factor,
  minimum 1) that fatigue and foul-trouble subs may draw from. The forced removal of a
  fouled-out or ejected player may reach the whole bench.
- **A single `COACH_SENSITIVITY` serves all five effects.** It splits per-effect only if
  one knob cannot fit them all.

`rotationOrder` (the bench depth chart) is the roster's contribution
([roster.md](roster.md)); `rotationDepth` and `substitutionAggressiveness` are how the coach
uses that chart.

## Not yet modelled

- **`playerDevelopment`** (the rate players improve under this coach). It bends a season,
  not a possession, so it lands with Phase 6 progression rather than as a latent column.
- In-game adjustments, timeout usage, matchup targeting and clutch-time tweaks.
- A coach's temperament has no influence on technicals or flagrants (see above).

## Open questions

1. **Derived display archetype:** compute on read or store? (Lean: compute.)
2. **A dedicated coach endpoint** (`GET`/`PUT /v1/coach/…`) or team-embedded until a UI asks?
   (Lean: team-embedded until Phase 7.)
3. **GM attributes:** GM has the same name-only gap, with consumers (trades, draft) further
   off. Resolve separately.
