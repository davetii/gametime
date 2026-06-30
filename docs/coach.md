# Coach Domain *(attribute model + §3.4 effects built; §3.5 rotation effects pending)*

The Coach is a team's **decision-maker model**: the inputs the game engine reads
to decide how a team plays — pace, shot distribution, defensive posture, and how
deep/early the bench gets used.

`CoachEntity` carries **5 continuous decision attributes** (was name-only).
**Design Decision #3** (continuous vs. categorical) is resolved as decisions.md
**#018**, and the §3.4 coaching effects (`pace`, `offensiveScheme`,
`defensiveScheme`) are **implemented end-to-end** — entity, mapper, and the
possession engine all consume them (decisions.md #022). The §3.5 rotation
attributes (`rotationDepth`, `substitutionAggressiveness`) are modeled and seeded
but **not yet read by any engine** — their consumers are §3.5 (minutes/fatigue/
substitution).

> **Scope discipline** (cf. decisions.md #014): the *attribute model* shipped
> ahead of its consumers, but each effect's *formula* landed with the engine phase
> that consumes it — §3.4 for the scheme/pace effects, §3.5 for the rotation ones.
> No attribute carries a latent, never-read formula.

---

## Continuous, not categorical *(decided — decisions.md #018)*

Coach decision-making is modeled as **continuous 1–20 attributes, average = 10 —
the same scale as player attributes** ([player.md](player.md)), **not** as
categorical style enums (`FAST | BALANCED | SLOW`). Any categorical "archetype"
label is **derived on read** from the numbers for UI; the model itself is never
categorical. decisions.md #018 holds the full rationale and rejected alternatives;
in brief, continuous wins on:

- **Consistency.** One scale across the domain means the engine combines coach and
  player numbers without translation, and the skill-calculator deviation helper is
  reusable. The §3.4 engine confirmed this: every coach effect is the same avg-10
  deviation multiplier the skill system uses (#022).
- **Blending.** A *somewhat* up-tempo coach is "pace 13," not forced into a bucket;
  the engine scales effects smoothly.
- **Progression-friendly.** Phase 6 coach development can nudge a number rather
  than jump across enum buckets.
- **Seed-friendly.** 40 coaches are seeded by sampling around 10, like players — no
  hand-authoring of style categories.

The one enum advantage — human legibility ("this is a defensive coach") — is
recovered for free by a **derived display archetype** (e.g. high pace + high
three-point lean → "modern offense") without the model being categorical.

---

## Attributes (1–20, avg 10)

All five are concrete: columns on `CoachEntity`, seeded in `coach.csv` (40 rows),
and mapped through `EntityMapper`. The "Consumed by" column shows which engine
phase reads each — **§3.4 effects are live**; **§3.5 effects are pending** (the
attributes exist and are read by nothing until §3.5 builds minutes/fatigue).

| Attribute | Drives | Consumed by | Status |
|-----------|--------|-------------|--------|
| **pace** | Possessions per game (scales the possession **count**) | §3.4 possession flow | ✅ read |
| **offensiveScheme** | Shot distribution — perimeter/3pt lean vs. inside/post | §3.4 `ShotSelector` lean | ✅ read |
| **defensiveScheme** | Aggressiveness — turnover/foul pressure vs. contain | §3.4 turnover/foul pressure | ✅ read |
| **rotationDepth** | How many players see real minutes (tight 7 vs. deep 10) | §3.5 minutes allocation | ⏳ unread |
| **substitutionAggressiveness** | How early/eagerly fatigued starters are pulled | §3.5 sub triggers | ⏳ unread |

These form two coherent pairs plus pace: the **§3.4** schemes (what shots happen
on each end) and the **§3.5** rotation knobs (who is on the floor) — mapping to
the two things a coach controls during a game.

**Deferred until a consumer is live:**

- **playerDevelopment** (rate players improve under this coach) — its only
  consumer is **Phase 6 progression**, not Phase 3. Unlike the five above it
  bends a *season*, not a possession, so by the #014/#017 "no attribute ahead of
  its consumer" discipline it lands with Phase 6 (accepting a second migration
  then rather than fabricating a latent column now).
- In-game adjustments, timeout usage, matchup/iso targeting, clutch-time tweaks —
  add when §3.x asks.

---

## Engine-facing interface

The engine reads coach attributes; it never writes them. Effects are **modifiers
on a baseline**, not absolute values — a coach bends what the players would do,
they don't override it. Every `f(attr)` is the **avg-10 deviation multiplier**
(decisions.md #022, Decision A — the same form the skill calculator and #021 use):

```
f(attr) = 1 + COACH_SENSITIVITY × (attr − 10) / 10
```

so an attribute of 10 ⇒ ×1.0 (no effect), and coach numbers compose with player
numbers on one scale with no translation layer. Concretely:

```
basePace        × f(pace)                 → team possessions  (scales the possession COUNT, §3.4)
baseShotMix     × f(offensiveScheme)      → perimeter vs. interior shot share  (§3.4 ShotSelector lean)
basePressure    × f(defensiveScheme)      → turnover/foul pressure on defense   (§3.4)
rotationOrder   + f(rotationDepth)        → who plays, how many minutes  (input: #014; §3.5)
fatigueThreshold× f(substitutionAggr.)    → when a sub fires             (§3.5)
```

**§3.4 reads the first three only** (`pace`, `offensiveScheme`, `defensiveScheme`
— decisions.md #022 Decision E); `rotationDepth` / `substitutionAggressiveness`
are §3.5 (minutes/fatigue) and are not read until then. `pace` scales the
**possession count** (a faster coach runs more possessions, not merely quicker
shots — Decision A, settled while building). The single `COACH_SENSITIVITY` lives
in `SimConfig` and is tuned in §3.4 calibration; it splits per-effect only if one
knob can't fit all effects.

`rotationOrder` (the bench depth chart, already shipped in #014) is the roster's
contribution; `rotationDepth` / `substitutionAggressiveness` are how the coach
*uses* that chart. That's the clean seam between the roster domain and gameplay.

---

## Implementation status

The attribute model and its §3.4 effects are built; §3.5 rotation effects are the
remaining work.

| Step | Status |
|------|--------|
| 1. **Decide #3** — continuous, not categorical | ✅ decisions.md #018 |
| 2. **Attributes** — finalize the 5-attribute set | ✅ all five (see table above) |
| 3. **Schema** — Liquibase columns on `coach` (H2-compatible + Postgres triggers) | ✅ `release.1.0.1.sql` |
| 4. **Entity** — fields on `CoachEntity` (Lombok `@Data`) | ✅ |
| 5. **Seed** — `coach.csv`, 40 rows sampled around 10 | ✅ |
| 6. **Mapping** — wire through `EntityMapper` | ✅ `entityToCoach` (API exposure still open — see below) |
| 7. **Tests** — entity + mapping coverage | ✅ `EntityMapperTest` |
| 8. **§3.4 effects** — `pace`/`offensiveScheme`/`defensiveScheme` → engine | ✅ decisions.md #022 (`CoachModifiers` + `TeamContext`) |
| 9. **§3.5 effects** — `rotationDepth`/`substitutionAggressiveness` → minutes/fatigue | ⏳ pending §3.5 |

The §3.4 effects (`f(...)` in the interface above) are implemented as the
`CoachModifiers` value object, threaded through `PossessionEngine` via
`TeamContext`. The §3.5 effects await the minutes/fatigue/substitution model.

---

## Open questions

- **Derived archetype for display** — compute on read, or store? (Lean: compute.)
- **Does the API expose coach attributes**, or are they engine-internal until
  there's a UI consumer? (Lean: internal until Phase 7 needs them.)
- **GM attributes** — same name-only gap exists for GM, but its consumers
  (Phase 6.4 trades/draft) are further off; resolve GM separately, later.
