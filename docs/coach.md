# Coach Domain *(attribute model built; effects are Phase 3)*

The Coach is a team's **decision-maker model**: the inputs the game engine reads
to decide how a team plays — pace, shot distribution, defensive posture, and how
deep/early the bench gets used.

`CoachEntity` now carries **5 continuous decision attributes** (was name-only).
This doc resolved **Design Decision #3** (continuous vs. categorical — see
decisions.md #018) and defines the attribute set + the engine-facing interface,
so Phase 3 (§3.4 coaching effects, §3.5 rotation/substitution) has something
concrete to build against.

> **Scope discipline** (cf. decisions.md #014): build the *attribute model* now;
> the *coaching effects* (how an attribute bends a possession) land with the
> engine that consumes them. We define the interface, not the formulas, ahead of
> a consumer that doesn't exist yet.

---

## Decision #3 — continuous, not categorical *(decided — see decisions.md #018)*

**Recommendation: continuous 1–20 attributes, average = 10 — the same scale as
player attributes.**

Why continuous over an enum of styles:

- **Consistency.** Players already use 1–20/avg-10 ([player.md](player.md)). One
  scale across the domain means the engine combines coach and player numbers
  without translation, and the skill-calculator deviation helper is reusable.
- **Blending.** A coach who is *somewhat* up-tempo is "pace 13," not forced into
  a `FAST | BALANCED | SLOW` bucket. The engine can scale effects smoothly.
- **Progression-friendly.** Phase 6 coach development can nudge a number;
  promoting/regressing across enum buckets is lumpier.
- **Seed-friendly.** 40 coaches can be seeded by sampling around 10, exactly like
  players — no hand-authoring of style categories.

Trade-off: enums read more intuitively ("this is a defensive coach"). Mitigated
by deriving a **display archetype** from the numbers (e.g. high pace + high
three-point lean → "modern offense") for UI, without the model itself being
categorical. Rejected the enum approach as the primary model for the lumpiness
and translation cost above.

---

## Proposed attributes (1–20, avg 10)

Each attribute exists **because a named Phase 3 consumer needs it** — no
attribute without a *live* consumer (the #014 discipline). **Five to start**,
all read by the Phase 3 engine on a possession:

| Attribute | Drives | Consumed by |
|-----------|--------|-------------|
| **pace** | Possessions per game / shot-clock urgency | §3.2 possession flow |
| **offensiveScheme** | Shot distribution — perimeter/3pt lean vs. inside/post | §3.4 shot distribution |
| **defensiveScheme** | Aggressiveness — pressure/steal-seeking vs. contain | §3.4 defensive scheme |
| **rotationDepth** | How many players see real minutes (tight 7 vs. deep 10) | §3.5 minutes allocation |
| **substitutionAggressiveness** | How early/eagerly fatigued starters are pulled | §3.5 sub triggers |

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
they don't override it. Concretely (formulas TBD with the engine):

```
basePace        × f(pace)                 → team possessions
baseShotMix     × f(offensiveScheme)      → perimeter vs. interior shot share
baseStealRate   × f(defensiveScheme)      → pressure vs. foul/breakdown risk
rotationOrder   + f(rotationDepth)        → who plays, how many minutes  (input: #014)
fatigueThreshold× f(substitutionAggr.)    → when a sub fires             (§3.5)
```

`rotationOrder` (the bench depth chart, already shipped in #014) is the roster's
contribution; `rotationDepth` / `substitutionAggressiveness` are how the coach
*uses* that chart. That's the clean seam between the roster domain and gameplay.

---

## Implementation outline *(when this task starts)*

1. **Decide #3** — confirm continuous; record in decisions.md.
2. **Attributes** — finalize the set above (add/cut with consumer justification).
3. **Schema** — Liquibase changeset adds the columns to `coach`; H2-compatible
   defaults + Postgres triggers per the audit-column convention.
4. **Entity** — add fields to `CoachEntity` (Lombok `@Data`).
5. **Seed** — extend `coach.csv` (40 rows) with sampled-around-10 values.
6. **Mapping** — wire through `EntityMapper`; expose on the coach in API models if
   `GET /team` should surface them.
7. **Tests** — entity + mapping coverage (JaCoCo gate).

No engine code. The effects (`f(...)` above) are Phase 3.

---

## Open questions

- **Derived archetype for display** — compute on read, or store? (Lean: compute.)
- **Does the API expose coach attributes**, or are they engine-internal until
  there's a UI consumer? (Lean: internal until Phase 7 needs them.)
- **GM attributes** — same name-only gap exists for GM, but its consumers
  (Phase 6.4 trades/draft) are further off; resolve GM separately, later.
