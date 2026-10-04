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
| endurance | Stamina over a game/season — §3.5 drives in-game fatigue: higher endurance drains `currentEnergy` slower |
| energy | Burst effort, motor — §3.5 speeds how fast a benched player **recovers** `currentEnergy`. (It does *not* seed starting energy: every player tips off at a full tank, a deliberate deviation recorded in decisions.md #023 B's implementation note. §3.13 leans on this recovery rate — it is what returns a foul-troubled player to the floor) |
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

Each skill is computed by a dedicated `SkillCalculator` Spring bean. 23 skills.

> **What drives each skill lives in the calculator, not here.** Every
> `*SkillCalculator` opens with a doc comment naming what it models and which
> attributes carry it, and the formula sits directly below — that is the
> authoritative, always-current answer. This table deliberately describes **what
> the skill means**, not its input list: most calculators read 5–8 attributes, so
> any short list here is a lossy subset that silently rots the moment a formula is
> tuned. (It did: an earlier version of this table listed `agility` for
> `offenseRebound` and `determination` for `teamDefense`, neither of which those
> calculators read.) To answer "what feeds skill X", open `XSkillCalculator`.

#### Offensive

| Skill | What it models |
|-------|----------------|
| **drive** | Attacking the basket off the dribble — handle/quickness carry it, verticality finishes through contact, big men and aging legs penalized |
| **freeThrows** | Free-throw shooting — mostly raw shot skill, with composure for the repetition/pressure element |
| **longRange** | Three-point shooting — shot selection and shot skill dominate; big men a touch less efficient from deep. ⚠ Since §3.17 (#040 C) it drives **both** halves separately: it **nudges** the THREE share off the league base (`sim.shot-share-three`) via `SHOT_MIX_SENSITIVITY`, and it decides accuracy unchanged. It no longer *sets* how often the league shoots threes |
| **perimeter** | Mid-range and perimeter shot-making — shot skill dominates, quickness contributes |
| **post** | Back-to-basket scoring — strength and size carry it, gated by combinations (skilled bigs and strong-willed bigs both score) |
| **teamOffense** | Playing within a system — ball movement, spacing, unselfish play; ego is a double-edged penalty |

#### Defensive

| Skill | What it models |
|-------|----------------|
| **individualDefense** | 1-on-1 defense — staying in front, contesting, forcing tough shots |
| **teamDefense** | Help defense, rotations, communication, scheme discipline; ego penalty (selfish defenders break the scheme) |

#### Rebounding

| Skill | What it models |
|-------|----------------|
| **offenseRebound** | Crashing the offensive glass for putbacks — effort plus size/strength, length helps, and a want-the-ball edge (ego) *helps* here |
| **defenseRebound** | Boxing out and securing the defensive board — size, strength, determination; length and want-the-ball edge help |

#### Playmaking / Intangibles

| Skill | What it models |
|-------|----------------|
| **acumen** | In-game basketball IQ — reading plays, smart real-time decisions; ego penalty, experience helps |
| **ballSecurity** | Protecting the ball / avoiding turnovers — composure steadies it, reckless energy and ego cost control |
| **passing** | Court vision, finding open teammates, pass accuracy; awareness sharpens the read, experience adds polish |

#### Finishing & Athleticism

| Skill | What it models |
|-------|----------------|
| **finishing** | Scoring at the rim — dunks, contested layups, lob catching, finishing through contact. Declines with age. ⚠ **§3.17 (#040 B/C) got it OUT of shot SELECTION**: it is a make-the-shot skill and entered selection only through #021 D's five-into-four collapse, where it gave DRIVE double weight and caused the 3PA gap. It now survives in selection only inside DRIVE's modifier, **halved** — `(drive + finishing) / 2`, the same form the accuracy path always used |
| **transition** | Fast-break scoring, decision-making and execution in the open court; big men lag |

#### Active Defense

| Skill | What it models |
|-------|----------------|
| **rimProtection** | Shot blocking, paint deterrence, interior defense — length and awareness sharpen it; small players can't protect the rim |
| **stealing** | Active hands, passing-lane disruption, on-ball pickpocketing; composure guards against reaching fouls |
| **shotContest** | Challenging shots **without** fouling — closing out, getting a hand up; composure avoids the closeout foul |

#### Fouling

| Skill | What it models |
|-------|----------------|
| **foulDrawing** | Getting to the free-throw line — initiating contact, selling fouls; refs call fewer fouls for big men |
| **foulProne** | Tendency to **commit** fouls — **higher = worse**. Aggression and reckless energy raise it; composure and awareness lower it. Baselined at the scale average rather than an attribute average (there is no "average of attributes" that means an average foul rate) |

#### Situational

| Skill | What it models |
|-------|----------------|
| **clutch** | Late-game / high-pressure performance — composure carries it, ego wants the moment; rookies wilt, veterans steady |
| **screenSetting** | Pick quality, screen angles, roll/pop timing; ego works *against* setting hard screens |
| **offBallMovement** | Cutting, spacing, relocating without the ball; ego (ball-watching) works against it |

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
- **Ego as double-edged sword**: High ego boosts individual/assertive skills but
  penalizes team-oriented ones. This is the most widely-threaded attribute in the
  model — **11 of the 23** calculators read `ego`, in both directions: it *helps*
  offenseRebound/defenseRebound (want-the-ball edge), foulDrawing, clutch, and
  individualDefense; it *hurts* teamOffense, teamDefense, acumen, ballSecurity,
  screenSetting, and offBallMovement.
- **Aggression as double-edged sword**: Helps foul drawing but increases foul proneness

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
skill, and each class carries a doc comment describing what it models and which
attributes carry it — the authoritative answer to "what feeds skill X".

All 23 skills are listed under "Derived Skills" above with what each one models;
the attributes feeding a given skill live in its calculator class.
`foulProne` is inverted (higher = worse) but still centers at 10 for an average player.


## How Attributes Map to Game Engine Needs

The design map of which skills back each possession decision. The table at the end of
this section is the per-event view of what the engine **actually** reads.

**What the engine wires:** shot selection, shot contest (`shotContest` /
`individualDefense` / `rimProtection`), blocks (`rimProtection` or `shotContest` vs the
shooter's `finishing`), turnovers (`ballSecurity` vs `stealing`, with `acumen` and
`teamOffense` leaning the cause mix), all fouls (`foulDrawing` vs the defender's
`foulProne`), free throws (`freeThrows`), rebounding (`offenseRebound` /
`defenseRebound`), assists (`passing`, scaled by `teamOffense`), shot efficiency (`acumen`,
`teamOffense` / `teamDefense`), and fatigue (`endurance` drains, `energy` speeds bench
recovery; one modest multiplier over a player's skills at contest time).

### Shot selection: two separate questions

- **How often a type is shot** is the league's job. The base mix is four tunables
  (`sim.shot-share-{drive,perimeter,post,three}`), and a player's skill only nudges it:
  `share(type) × (1 + SHOT_MIX_SENSITIVITY × (skill − 10) / 10)`. The skill is
  `(drive + finishing) / 2` for DRIVE, `perimeter`, `post`, `longRange` for the others.
  A `longRange`-19 sniper shoots more threes than a `longRange`-6 big, but neither moves
  what the league shoots.
- **How well it goes in** is purely the player's: `offenseSkillForShot`, the same four
  skills, `finishing` entering only through DRIVE's average.

Shooter selection is separate again: a draw weighted by the sum of the five offensive
skills (`drive + finishing + perimeter + post + longRange`). The defender is drawn by
`individualDefense`. The defender then fouls, blocks and contests; it is the
**defender's `foulProne`** that sets the foul rate.

### Skill-free mechanics

Three rolls take **no skill, coach or situation input**, each for a different reason:

- **Technical foul.** Behavioral: nothing in the possession caused it, so its rate is a
  flat per-team-game constant. `foulProne` weights only *who* of the five wears it, which
  is nearly uniform because `foulProne` has a small spread. Raw `aggression` /
  `composure` / `ego` are not threaded in: the engine reads skills, and `foulProne` is
  already that composite.
- **Flagrant grade** (flagrant vs. common, and flagrant-2). Layered on a foul whose
  contest has already resolved, with `foulProne` already in its rate. Grading it again
  would apply one signal twice, and the engine cannot tell excessive from ordinary
  contact. A disciplined veteran and a reckless rookie are equally likely to commit a
  flagrant-2 *given* a flagrant.
- **Shooting vs. non-shooting.** The same argument, plus a stronger one: what decides it
  in basketball is where on the floor the contact happened, and the engine has no floor
  position. So a foul's kind is independent of who committed it. Every player has the
  same league-wide `SHOOTING_FOUL`-to-`NON_SHOOTING_FOUL` split
  (`sim.non-shooting-foul-share`), where in the NBA a rim protector's fouls skew shooting.
  Lifting that ceiling needs a floor-position model, not a constant.

### The charge

A charge is a personal foul charged to the **ball-handler on offense**, and its frequency
leans on `teamOffense` (the turnover cause draw). It is the only foul whose rate responds
to an offensive skill: a high-usage creator on a poorly coordinated offense accumulates
personal fouls and can foul out on possessions his team had the ball.

### Rotation: the value composite

The foul-trouble bench rule scales the chance a coach sits a player by his value to the
team, a derived composite (`PlayerGameState.valueComposite()`, never stored):

```
value = (individualDefense + rimProtection + defenseRebound + offense + offense) / 5
where offense = mean(drive, finishing, perimeter, post, longRange)
```

Offense is double-weighted (~60/40 defense-leaning) because foul trouble bites defenders
and bigs hardest. A **higher** value makes a player **more** likely to be benched at a
given foul count: the coach protects an asset, the inverse of the fatigue rule, where
starters tolerate more tiredness. This `mean` is unrelated to shot selection.

### Skills nothing reads

**Four of the 23 skills are read by nothing in `sim`**: `transition`, `clutch`,
`screenSetting`, `offBallMovement`. Their table rows are the target, kept so attribute
coverage stays visible. `awareness`, `composure` and `aggression` still reach the engine
indirectly through calculators the engine does read (`stealing`, `shotContest`,
`foulProne`, ...).

> ⚠ **This table answers "which SKILLS does each event read?" — for "who is ON each
> event, and in which column?" see [`game-events.md`](game-events.md)**, the single
> per-event vocabulary reference. **The two
> cross-link deliberately: two per-event tables that do not point at each other WILL
> drift** — the failure mode `CLAUDE.md` documents for `possession-flow.puml`. Rows
> here stay about skills and formulas; participant columns stay there.

| Possession Event | Skills Used | Status |
|-----------------|-------------|--------|
| Transition or half-court? | transition, awareness | ⬜ not modeled |
| Ball handler decision | acumen, passing, teamOffense | ✅ §3.4 |
| Pick-and-roll action | screenSetting, offBallMovement | ⬜ not modeled |
| Drive to basket | drive, finishing | ✅ §3.2/§3.4 |
| Shot attempt (open) | longRange / perimeter / post | ✅ §3.2/§3.4 |
| Shot contest | shotContest, individualDefense | ✅ §3.2/§3.4 |
| Shot block attempt | rimProtection (rim) / shotContest (jumper) vs finishing (shooter) — **the blocker this picks now rides the EVENT** as `opponent_player_id` (§3.18, #041 A), not only `box_score.blocks`. No new skill and no new draw: the same `pickDefender` selection, carried onto the row | ✅ §3.7/§3.18 |
| Foul on attempt? | foulDrawing vs foulProne — all four shot types since §3.12, each with its own multiplier | ✅ §3.2/§3.4/§3.12 |
| And-1 (foul on a MADE shot)? | foulDrawing vs foulProne again — a **second, post-make** roll on its own thin rate; made DRIVE/POST only until §3.12 | ✅ §3.11 |
| Rebounding foul? | foulDrawing vs foulProne (two-sided — either team can commit; `foulProne` also weights *who* commits it) | ✅ §3.10 |
| **Was that foul FLAGRANT?** | **NONE — no skill, coach or situation input at all** (#034 A/E). A flat rate on **any** of the three fouls above, and a flat 15% severity sub-roll for flagrant-2. `foulProne` had its say **already**, in picking the committer — grading him again would apply one signal twice | ✅ §3.14b |
| **Was that foul NON-SHOOTING (`NON_SHOOTING_FOUL`)?** | **NONE — and here the missing input is the MODEL's, not the signal's** (#039 E). A flat `sim.non-shooting-foul-share` on the stopped-shot foul, rolled only after the flagrant question misses. `foulProne` has already had its say (as above), **and** what truly decides this is *floor position*, which the engine does not represent at all — so weighting it would fabricate a dimension. Consequence: **a foul's kind is independent of who committed it** | ✅ §3.16 |
| **Charge (offensive foul) → a personal foul** | `teamOffense`↓ leans the `OFFENSIVE_FOUL` turnover cause (#027 C); the committer is the **ball-handler** from `pickShooter`. ⚠ **The only foul in the model charged to an OFFENSIVE player, and the only one whose rate responds to an offensive skill** (#039 G) | ✅ §3.16 |
| Free throws | freeThrows; clutch (late game) | ✅ §3.2 (clutch ⬜) |
| Rebound | offenseRebound / defenseRebound | ✅ §3.3 |
| Turnover / steal | ballSecurity vs stealing (gate); §3.9 cause draw leans `SHOT_CLOCK_VIOLATION` on `acumen`↓ + defending `defensiveScheme`↑ and `OFFENSIVE_FOUL`/`BAD_PASS` on `teamOffense`↓. **The stealer `stealing` picks now rides the EVENT** as `opponent_player_id` (§3.18, #041 A/B) — the same `pickStealer` draw, no longer discarded after `recordSteal()`. ⚠ The steal RATE is **§3.19's**, not a skill question (#041 F) | ✅ §3.2/§3.9/§3.18 |
| Late-game pressure | clutch modifier on all actions | ⬜ not modeled |
| Off-ball movement | offBallMovement, awareness | ⬜ not modeled |

**Between possessions** — not a possession event, but the other place skills reach
the engine (the rotation step, `RotationState.advancePossession()`):

| Rotation decision | Skills / attributes used | Status |
|-----------------|-------------|--------|
| Fatigue drain / recovery | endurance (slows drain), energy (speeds recovery) | ✅ §3.5 |
| Fatigue sub | `currentEnergy` vs a coach-scaled threshold; starters tolerate MORE | ✅ §3.5 |
| Foul-out (forced off) | none — a derived predicate over the personal-foul counter (`fouls >= 6`). A charge is a personal foul, so a player can foul out on fouls committed on offense | ✅ §3.5 |
| **Foul-trouble sub** | the **value composite** (individualDefense, rimProtection, defenseRebound + the five offense skills) × foul count × coach × roster slot; better players benched **sooner** | ✅ §3.13 |
| **Technical foul** | **none for the RATE** — deliberately random, no causal model (#032 B); `foulProne` weights only **who** commits it, over the on-floor five (#032 C) | ✅ §3.14a |
| **Technical FT shooter** | `freeThrows` — a **deterministic** highest-on-the-floor pick, *not* the `foulDrawing`-weighted draw bonus FTs use (#032 G) | ✅ §3.14a |
| **Ejection (forced off)** | none — a **derived** predicate: `technicalFouls >= 2` over the separate technical counter, or `flagrantTwos >= 1` | ✅ §3.14a + §3.14b |
