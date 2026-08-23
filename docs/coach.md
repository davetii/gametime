# Coach Domain *(attribute model + all five effects built — §3.4 scheme/pace, §3.5 rotation)*

The Coach is a team's **decision-maker model**: the inputs the game engine reads
to decide how a team plays — pace, shot distribution, defensive posture, and how
deep/early the bench gets used.

`CoachEntity` carries **5 continuous decision attributes** (was name-only).
**Design Decision #3** (continuous vs. categorical) is resolved as decisions.md
**#018**, and the §3.4 coaching effects (`pace`, `offensiveScheme`,
`defensiveScheme`) are **implemented end-to-end** — entity, mapper, and the
possession engine all consume them (decisions.md #022). The §3.5 rotation
attributes (`rotationDepth`, `substitutionAggressiveness`) are now **also consumed
end-to-end** — the §3.5 minutes/fatigue/substitution model reads them through
`CoachModifiers` (decisions.md #023). Since then §3.9–§3.11 have each widened the
*reach* of `defensiveScheme` without adding any coach-side code (see the note under
the attribute table).

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
phase reads each — **all five are now live**: §3.4 wired the scheme/pace trio and
§3.5 wired the two rotation knobs (decisions.md #022 / #023).

| Attribute | Drives | Consumed by | Status |
|-----------|--------|-------------|--------|
| **pace** | Possessions per game (scales the possession **count**) | §3.4 possession flow | ✅ read |
| **offensiveScheme** | Shot distribution — **three-point lean vs. mid-range** (⚠ §3.17 split the lean: `THREE` × the multiplier, `PERIMETER` × its **reciprocal**, `DRIVE`/`POST` unscaled — #040 D. It is NOT jumper-vs-interior) | §3.4 `ShotSelector` lean, split by §3.17 | ✅ read |
| **defensiveScheme** | Aggressiveness — turnover/foul pressure vs. contain | §3.4 turnover/foul pressure; §3.9 also leans the shot-clock **turnover cause**; §3.10 scales **rebounding fouls** (→ bonus exposure); §3.11 scales **and-1s**. ⚠ §3.16 scales the **parent** foul but not its composition, and **not** the charge (which leans on the offense's `teamOffense` instead) | ✅ read |
| **rotationDepth** | How many players see real minutes (tight 7 vs. deep 10) | §3.5 minutes allocation | ✅ read |
| **substitutionAggressiveness** | How early/eagerly fatigued starters are pulled | §3.5 sub triggers | ✅ read |

These form two coherent pairs plus pace: the **§3.4** schemes (what shots happen
on each end) and the **§3.5** rotation knobs (who is on the floor) — mapping to
the two things a coach controls during a game.

**`defensiveScheme` has quietly become the widest-reaching attribute.** It was
wired in §3.4 as one `defensivePressure` multiplier on the turnover and foul
gates, but every foul-adjacent sub-phase since has scaled *its* new roll by the
same number: §3.9 scales the shot-clock-violation turnover cause by it, §3.10
scales rebounding fouls (and so a team's bonus exposure), and §3.11 scales
and-1s. That is the intended pressure/breakdown trade-off compounding — an
aggressive scheme forces more turnovers *and* concedes more fouls — but it
means **a `defensiveScheme` change now moves more of the box score than any other
coach attribute**, and §3.12 (all-shot-type contact fouls) widened it again.
Worth watching in calibration: the effects multiply rather than add.

**§3.14a broke the streak deliberately, and that is worth recording as a precedent.**
Technical fouls are the first foul-adjacent sub-phase that does **not** scale by
`defensiveScheme` — or by any coach attribute at all. The rate is a flat constant with
no causal input whatsoever (decisions.md #032 B): a technical is behavioral, not a
by-product of defensive pressure, so scaling it by an aggression knob would assert a
causal link the real event does not have. **The five coach attributes stand unchanged
at five** — §3.14a adds none and reads none. The honest cost, accepted: a coach's
temperament has no influence on his team's technicals, which is a real fidelity
ceiling that no tuning can lift (it needs the game-situation awareness the rotation
step does not have).

**§3.14b (flagrants, `decisions.md` #034) reads no coach
attribute either — but it is NOT coach-independent, and the distinction matters.** The
flagrant *severity roll* is a flat constant with no coach input (#034 A/E), exactly like
§3.14a's. **But a flagrant only exists if a foul happened first, and all three fouls it
rides on ARE scaled by `defensiveScheme`** (`defensivePressure` multiplies the
stopped-shot roll, the and-1 roll and the rebounding-foul roll). So an aggressive,
gambling defense concedes more fouls and therefore commits **proportionally more
flagrants** — the coach's influence flows through the parent event rather than through
the new roll. That is the right shape: pressure makes contact more likely, but it does
not make a given contact more *excessive*. **The five coach attributes stand unchanged
at five** for the fourth sub-phase running — §3.14b adds none and reads none directly.
Accepted cost, the same as §3.14a's: a coach's temperament has no influence on whether
his players cross the line.

**§3.16 (shooting-foul composition, `decisions.md` #039) makes it three passes running,
by the same argument — and adds one asymmetry worth knowing.** The composition roll
(`sim.non-shooting-foul-share`, "was this foul a `NON_SHOOTING_FOUL` rather than a
`SHOOTING_FOUL`?") is a flat constant with **no coach input and no skill input at all**
(#039 E), for §3.14b's exact reason: it is a *grade* on a foul whose committer was
already chosen by a `foulProne`-weighted `pickDefender`, so weighting it again would
apply one signal twice. There is also nothing honest to weight it *by* — what really
decides shooting-vs-non-shooting is **where on the floor the contact happened**, which this
engine does not represent. **The coach's influence still flows through the parent
foul**, which `defensivePressure` scales as always: an aggressive scheme concedes more
fouls, so it concedes proportionally more of both kinds.

⚠ **But §3.16's other half runs the other way, and it is the first foul in the engine
that an OFFENSIVE coach's attribute reaches.** The **charge** (#039 G) is now a personal
foul, and its frequency is set by the `OFFENSIVE_FOUL` turnover-cause weight — which
§3.9 leans on **`teamOffense`**, not on the defending coach's `defensiveScheme` (#027
C). So a poorly-coordinated offense commits more charges, and therefore more personal
fouls, **independently of how the defense plays**. Every other foul in the model is
scaled by the defense. Worth watching in calibration for the same reason the paragraph
above flags: **`defensiveScheme` no longer accounts for all of a team's foul total.**

**The five coach attributes stand unchanged at five** for the fifth sub-phase running —
§3.16 adds none and reads none directly.

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
baseShotMix     × f(offensiveScheme)      → THREE vs. MID-RANGE shot share      (§3.4 lean, SPLIT by §3.17)
basePressure    × f(defensiveScheme)      → turnover/foul pressure on defense   (§3.4, §3.9–§3.11)
benchDepth      × f(rotationDepth)        → how far down rotationOrder the bench plays  (input: #014; §3.5)
subThreshold    × f(substitutionAggr.)    → energy level at which a tired starter is pulled  (§3.5)
```

Three mechanical details worth knowing before tuning any of these:

- **`pace` is BLENDED across both coaches, not applied per-team.** The two teams
  alternate possessions and therefore share one possession count, so
  `PossessionEngine` averages the two `paceMultiplier`s — a fast coach against a
  slow one lands in between, and *neither* coach gets their own pace. There is no
  per-team possession count to scale.
- **⚠ `offensiveScheme` SPLITS `THREE` and `PERIMETER` IN OPPOSITE DIRECTIONS
  (§3.17, `decisions.md` #040 D). The axis is mid-range-vs-three, NOT
  jumper-vs-interior.** `THREE` is scaled by `shotMixLean` and `PERIMETER` by its
  **reciprocal**; `DRIVE` and `POST` are unscaled. So a high-`offensiveScheme` coach
  shoots more threes **and fewer mid-range jumpers**, and a low one does the inverse —
  the actual modern-vs-traditional axis.
  ⚠ **Until §3.17 this scaled `PERIMETER` and `THREE` TOGETHER**, so a jump-shooting
  coach raised mid-range and threes in lockstep — the one shape the real game forbids,
  and #036 D's named blocker on the 3PA gap. **If you are reading an older doc or
  decision that says "perimeter vs. interior", that is the pre-§3.17 behavior.**
  There is still no separate inside/post lean: `DRIVE`/`POST` move only because the
  weighted draw re-normalizes, which is an emergent effect, not a second knob.
- **⚠ The coach no longer sets the LEAGUE's mix — only their own team's tilt on it**
  (§3.17, #040 C). The base distribution is now the four `sim.shot-share-*` tunables
  in `application-baseline.properties`, and `offensiveScheme` is centred on 10 across
  the league, so the two lean directions roughly cancel in the aggregate. The split
  exists so a *given* coach means something, not to move league 3PA.
- **`substitutionAggressiveness` scales the threshold, but starters get a flat
  bonus on top.** `subEnergyThreshold` subtracts `STARTER_SUB_THRESHOLD_BONUS`
  (8.0) for starters, so a starter is always pulled later than a bench player at
  the same energy regardless of the coach (§3.5 Decision C star retention). The
  coach knob and the starter bonus are independent.

All five are now read. **§3.4 reads the first three** (`pace`, `offensiveScheme`,
`defensiveScheme` — decisions.md #022 Decision E); **§3.5 reads the last two**
(`rotationDepth`, `substitutionAggressiveness` — decisions.md #023). Concretely
(§3.5, Decision D): `rotationDepthFactor()` sets how far down the `rotationOrder`
bench queue substitutions may draw (a tighter rotation leaves the deep bench on
the pine); `subAggressivenessFactor()` scales the between-possession energy
threshold at which a fatigued starter is pulled (a more aggressive coach subs
earlier). `pace` scales the **possession count** (a faster coach runs more
possessions, not merely quicker shots — Decision A, settled while building). The
single `COACH_SENSITIVITY` lives in `SimConfig` and is reused by all five effects
(tuned in calibration); it splits per-effect only if one knob can't fit all.

`rotationOrder` (the bench depth chart, already shipped in #014) is the roster's
contribution; `rotationDepth` / `substitutionAggressiveness` are how the coach
*uses* that chart. That's the clean seam between the roster domain and gameplay.

---

## Implementation status

The attribute model and all five effects are built — §3.4 scheme/pace and §3.5
rotation. Nothing in the coach model is unbuilt; the remaining items are the
display/API open questions below, not engine work.

| Step | Status |
|------|--------|
| 1. **Decide #3** — continuous, not categorical | ✅ decisions.md #018 |
| 2. **Attributes** — finalize the 5-attribute set | ✅ all five (see table above) |
| 3. **Schema** — Liquibase columns on `coach` (H2-compatible + Postgres triggers) | ✅ `release.1.0.1.sql` |
| 4. **Entity** — fields on `CoachEntity` (Lombok `@Data`) | ✅ |
| 5. **Seed** — `coach.csv`, 40 rows sampled around 10 | ✅ |
| 6. **Mapping** — wire through `EntityMapper` | ✅ `entityToCoach` maps all five; exposed transitively wherever a `Team` is returned (`fetchTeam`, league, §3.6 game endpoints). A *dedicated* coach endpoint is still open — see below |
| 7. **Tests** — entity + mapping coverage | ✅ `EntityMapperTest` |
| 8. **§3.4 effects** — `pace`/`offensiveScheme`/`defensiveScheme` → engine | ✅ decisions.md #022 (`CoachModifiers` + `TeamContext`) |
| 9. **§3.5 effects** — `rotationDepth`/`substitutionAggressiveness` → minutes/fatigue | ✅ decisions.md #023 (`CoachModifiers.rotationDepthFactor()`/`subAggressivenessFactor()` + `RotationState`) |
| 10. **§3.9–§3.11 reach** — `defensiveScheme` extended to the new foul/turnover rolls | ✅ no new coach code; each sub-phase scales its own roll by the existing `defensivePressure` (#027 C, #028 C, #029 C) |
| 11. **§3.14a/§3.14b/§3.16/§3.18 — four passes that read NO coach attribute** | ✅ deliberate, not an oversight: a technical is behavioral (#032 B), a flagrant grade is inert (#034 E), a foul's *kind* has no floor-position signal to weight it by (#039 E), and **§3.18 is pure attribution — it records WHO was on an event a coach-influenced roll already decided** (#041). Coach influence reaches all four through the **parent** event |
| 12. **§3.17 — `offensiveScheme`'s reach CHANGED (the one post-§3.5 coach-side change)** | ✅ the attribute is unchanged, but **what it scales is not**: the lean now splits `THREE` × the multiplier against `PERIMETER` × its **reciprocal**, with `DRIVE`/`POST` unscaled (#040 D). ⚠ **It also stopped setting the LEAGUE's mix** — four `sim.shot-share-*` tunables do that now (#040 C), and the coach only tilts a given team off it. See the attribute table |

All five effects (`f(...)` in the interface above) are implemented as the
`CoachModifiers` value object, threaded through `PossessionEngine` via
`TeamContext`. The §3.4 scheme/pace effects bend the possession flow; the §3.5
rotation effects drive the between-possession substitution check in
`RotationState` (who is on the floor, and when a tired starter is pulled).
**The seam has held: no pass since §3.5 has needed a NEW coach attribute.** §3.9–§3.11
each reused `defensivePressure` as-is; §3.14a, §3.14b, §3.16 and §3.18 each deliberately
read nothing. ⚠ **One thing did change, and it is worth not mis-reading as "nothing
happened": §3.17 rewired what `offensiveScheme` scales** (rows 11–12 above) — the
attribute and its range are untouched, but it now splits `THREE` against `PERIMETER`
rather than leaning jumper-vs-interior, and it no longer sets the league's shot mix at
all. **A coach attribute can be re-pointed without being re-designed**, and only the
attribute table records that.

---

## Open questions

- **Derived archetype for display** — compute on read, or store? (Lean: compute.)
- **A dedicated coach API** — coach attributes are already exposed transitively on
  every `Team` payload (`entityToCoach` maps all five), so the open question is
  narrower: does coach warrant its *own* endpoint (`GET`/`PUT /v1/coach/…`), or is
  team-embedded enough until a UI consumer asks? (Lean: team-embedded until Phase 7.)
- **GM attributes** — same name-only gap exists for GM, but its consumers
  (Phase 6.4 trades/draft) are further off; resolve GM separately, later.
