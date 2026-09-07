# Architecture Decisions

**This is an INDEX of decisions, not a narrative.** Each entry is reduced to its title,
date, and its Decision letters — the crux of each call in one or two sentences.
The full design-pass reasoning was cut in 2026-09 (Phase 3 closed; nothing consumes it).
⚠ **The measured findings that outlived those passes — traps, wrong-way levers,
saturations, elasticities, final constants — live in
[engine-findings.md](engine-findings.md). Read that, not this.**

**This file exists so the ~1,700 `#NNN` citations in the Java and the other docs resolve
to something.**

Entries are append-only and **NEVER renumbered or retro-edited**: 157 distinct
`#NNN <letter>` citations resolve into decision letters, so a later phase citing `#028 E`
must find what it cited. Correct a shipped entry in a *newer* entry (the way #038
supersedes #036's mapping), never in place.

- **#001–#020** — foundational. Small, stable, cited by the engine entries 172× while
  citing them 0×.
- **#021+** — engine sub-phases. Read only the one you are following a citation into.
- **§-numbering**: recalibration was §3.16 → §3.18 → **§3.19** (#038). #030/#031/#032/
  #034/#035 name it by its old number in their shipped text.

---

### 001 — Multi-module Maven structure
**Date**: 2026-05  
**Decision**: Split gametime-service into `gametime-api` (generated stubs) and `gametime-app` (hand-written code).  
**Rationale**: Keeps generated OpenAPI code isolated from application logic. The api module is a pure build artifact — no hand-written code lives there. The app module depends on it as a Maven dependency.

### 002 — OpenAPI delegate pattern
**Date**: 2026-05  
**Decision**: Use `delegatePattern=true` in openapi-generator-maven-plugin.  
**Rationale**: Generated controller delegates to a hand-written `V1ApiDelegate` implementation. This means we never touch generated controller code — the delegate is the integration point.

### 003 — Postgres for local dev, H2 for tests
**Date**: 2026-05  
**Decision**: Local development runs against Postgres in Docker. `mvn test` uses H2 in-memory with no Docker dependency.  
**Rationale**: Postgres for local dev gives realistic behavior (triggers, schemas, data types). H2 for tests keeps the test suite fast and CI-friendly with zero infrastructure.  
**Trade-off**: Liquibase changesets must be dual-compatible. Postgres-specific SQL gated with `dbms:postgresql`.

### 004 — Dedicated gametime schema
**Date**: 2026-05  
**Decision**: All application tables live in the `gametime` schema, not `public`.  
**Rationale**: Clean separation from system tables. H2 supports schemas, so this works in both environments.

### 005 — JDK 21 LTS via SDKMAN
**Date**: 2026-05  
**Decision**: Target Java 21 (SDKMAN: `21.0.9-tem`). Do not use Homebrew JDK 25.  
**Rationale**: Lombok 1.18.x is incompatible with JDK 25. JDK 21 is the current LTS release. All Maven commands must set `JAVA_HOME` explicitly.

### 006 — Attribute-driven skill calculation
**Date**: 2026-05 (pre-existing design)  
**Decision**: 16 raw player attributes feed into 13 derived skill calculations via weighted formulas with threshold bonuses/penalties.  
**Rationale**: Attributes are the "DNA" of a player — stable, slowly changing. Skills are the observable output — what a player can actually do on the court. This two-tier model allows the same attribute profile to produce different skill outcomes based on formula tuning.

### 007 — Cucumber on JUnit 5 Platform
**Date**: 2026-05  
**Decision**: Migrated from JUnit 4 vintage to JUnit 5 Platform (`@Suite` + `cucumber-junit-platform-engine`).  
**Rationale**: JUnit 4 vintage engine is deprecated. JUnit 5 Platform is the standard runner. Removed junit-vintage-engine dependency.

### 008 — Attribute scale: 1–20, average = 10
**Date**: 2026-06
**Decision**: attribute scale is **1–20, average 10**, all 21 attributes (rescaled by rank → inverse-normal, stdev ~3.5). ⚠ The 13 skill calculators' threshold branches were written against the old 1–10 range and had to be re-tuned.

### 009 — Player data is CSV-driven
**Date**: 2026-06
**Decision**: player seed data is `db/players.csv` (422 rows) loaded by a Liquibase `loadData` changeset, replacing ~420 inline SQL inserts. Teams stay in SQL (FK parent). ⚠ A schema change needs both a migration and a matching CSV column, and load order must stay teams → add-columns → players.

### 010 — Dropped fetchConference endpoint
**Date**: 2026-06
**Decision**: `GET /v1/conference/{confId}` removed — conference grouping is a client-side filter over the full-league payload. Reintroducible server-side if the league ever grows enough to make it wasteful.

### 011 — Player→team link: nullable team_id, decoupled from Team entity (SUPERSEDED by #012)
**Date**: 2026-06
**Superseded by #012** before it shipped. The interim step was a plain nullable `player.team_id` FK; its reasoning (create and assign are separate, so a "created but unassigned" state is needed) carried forward.

### 012 — Player↔team is a join model with history (player_team + player_team_hist)
**Date**: 2026-06
**Decision**: the player↔team link is two tables, decoupled from both entities:
- `player_team` — current assignment, `player_id` as PK (at most one team per player). **A free agent has no row.**
- `player_team_hist` — append-only transaction log, never updated or deleted.

`player.team_id` removed; `TeamEntity` has no JPA `players` association. `Player` exposes a derived read-only `currentTeamId`; `/history` returns the transaction list. ⚠ Two-table writes per assignment, kept atomic in one `@Transactional` method.

### 013 — Player status (availability) split from lineup role (team slot)
**Date**: 2026-06
**Decision**: two previously-conflated concepts become separate enums with no shared values:
- `Player.status` — intrinsic availability: `ACTIVE, INJURED, SUSPENDED`.
- `player_team.lineupRole` — slot on a team: `STARTER, ROTATION, BENCH, INACTIVE, MINORS`.

Roster membership stays DERIVED from `player_team` (no row = free agent), never encoded in status. ⚠ One field answering two questions is a duplicate source of truth waiting to disagree — the trap later entries cite as "the #013/#015 smell".

### 014 — Lineup is sticky persistent state; rotationOrder is the bench queue
**Date**: 2026-06
**Decision**: `PUT /lineup` is replace-all and sets **persistent** state (`lineup_role` + `rotation_order`); it is set-on-change, not per game. In-game substitutions are transient and never write back. Starters are an unordered set of 5 and carry **no** rotation order; `rotationOrder` is the **bench queue** (1 = first off the bench), unique among non-nulls.

### 015 — Team is the single source of roster state; `/roster` endpoint removed
**Date**: 2026-06
**Decision**: `GET /v1/team/{teamId}` is the ONE complete view of a team's player state; `Team.players` carries `RosterEntry` (player + lineupRole + rotationOrder). The separate `/roster` endpoint and `Roster` schema are removed. Same overload-elimination principle as #013.

### 016 — Roster size caps; signed players default to `INACTIVE`
**Date**: 2026-06
**Decision**: rosters cap at **15 active + 5 minors**. Signing defaults `lineupRole` to `INACTIVE` and returns **409** when the active roster is full; the lineup PUT re-checks both caps over the resulting roster and returns **400**. Enforced at both sites so a role shuffle cannot grow the roster past what signing allows.

### 017 — No position min/max roster rules (won't build)
**Date**: 2026-06
**Decision**: roster construction is **NOT** constrained by position — no per-position minimums or maximums. The seed data cannot support minimums (no team carries all 9 positions), and a lopsided roster should be punished by losing, not by an API rule. **Revisit if** playtesting shows the simulation does not correct degenerate rosters.

### 018 — Coach attributes are continuous (1–20, avg 10), not categorical enums
**Date**: 2026-06
**Decision**: coach decision-making is **continuous 1–20 / avg-10 attributes** (the #008 scale), never categorical style enums; any archetype label is derived on read. The engine wants numbers, so coach and player values compose by multiplication with no translation layer — an enum would have to carry a hidden coefficient per value, reinventing the scale with a second source of truth.

### 019 — No API pagination (not needed at current scale)
**Date**: 2026-06
**Decision**: no API pagination — not needed at current scale (40 teams, 422 players). Play-by-play is the one large result set, and it is addressed at its own read endpoint when it lands.

### 020 — Game domain model: persist every GameEvent
**Date**: 2026-06
**Decision**: five facets of one Game-model choice:
- **Every `GameEvent` is persisted** — play-by-play replays from stored rows, never re-simulated. The `BoxScore` accumulates during simulation and reconciles against the log, with **events as the source of truth**.
- **`GameStatus { SCHEDULED, IN_PROGRESS, FINAL }`** — no CANCELLED/POSTPONED until a consumer exists.
- **`Game` references home/away team only — no season FK** (no schedule table exists yet; not fabricated ahead of its consumer).
- **`BoxScore` keyed `(game_id, player_id)`, with NO `team_id`** — derivable from game + `player_team`; storing it would be a third copy.
- **`GameEvent` is minimal and additive**: `sequence` is monotonic across the whole game and does **not** restart per period; `outcome` is free text; no clock column.

⚠ The free-text `outcome` is what lets later phases add vocabulary (`BLOCKED_*`, `MADE_<SOURCE>`, `TECHNICAL_FOUL`) with no schema change — cited constantly downstream.

### 021 — Possession engine (§3.2)

**Date**: 2026-06

- **A** — seeded `RandomGenerator`; the seed is a `long` param on `simulate()`, threaded to every resolver.
- **B** — possession-by-possession, **no clock**; pace is a `simulate()` input, not a constant.
- **C** — logistic contest on the avg-10 deviation: `p = base + SENSITIVITY × (off − def)/10`, clamped `[0.02, 0.97]`.
- **D** — shooter picked by skill-weighted draw over the on-floor five, then shot type by their own skill weights.
- **E** — `simulate(home, away, seed, pace)` is one `@Transactional` method persisting Game + events + box scores atomically.

### 022 — Team chemistry & coaching effects (§3.4)

**Date**: 2026-06

- **A** — coach/chemistry bends a value by the avg-10 multiplier `1 + COACH_SENSITIVITY × (attr−10)/10`; attr 10 ⇒ ×1.0.
- **B** — assists are first-class and reconcilable, on their own `assist_player_id` column.
- **C** — `acumen` bends the make rate (not the shot-type draw); `teamOffense`/`teamDefense` layer as modest modifiers.
- **D** — build a `CalibrationHarness` and tune the `BASE_*` rates against real-basketball benchmarks.
- **E** — scope fence: §3.4 reads `pace`/`offensiveScheme`/`defensiveScheme` only; the rotation attrs are §3.5's.

### 023 — Minutes, fatigue & substitution (§3.5)

**Date**: 2026-07

- **A** — minutes are DERIVED from possession share; the engine stays clock-free (#021 B). No schema change.
- **B** — one `currentEnergy` per player → one fatigue multiplier over skills; `endurance` slows drain, `energy` speeds recovery.
- **C** — substitution is deterministic and between-possessions, consuming **NO RNG draw**; starters tolerate more fatigue and return first.
- **D** — `CoachModifiers` gains `rotationDepthFactor()` / `subAggressivenessFactor()` in the same avg-10 form.
- **E** — extend the existing harness (not a second one) with per-slot minutes + period-by-period FG%.
- **F** — within-game fatigue only (schema-free); foul-outs ARE in, as a **derived predicate** over the existing `fouls` counter — never a stored flag.

### 024 — Simulation APIs (§3.6)

**Date**: 2026-07

- **A** — `POST /simulate` and `GET /game/{id}` return the same `GameResult` with team-split box scores.
- **B** — seed is an optional input and IS persisted (reverses #021 A's non-persist).
- **C** — pace is not exposed on the API; the endpoint always uses the configured default.
- **D** — play-by-play is a flat list in `sequence` order, no pagination.
- **E** — no per-event time column; a display clock is derived on read from `period` + `sequence` + pace.
- **F** — 404 for unknown ids; a new **422** for the same-team guard.

### 025 — Blocked shots (§3.7)

**Date**: 2026-07

- **A1** — block-first: carve `P(BLOCK)` off the top, then run the already-calibrated make contest on the remainder.
- **B2** — block is a defender-vs-finisher contest, not a defender-only rate. ⚠ It needed its own `BLOCK_SENSITIVITY` (0.12) — see engine-findings.
- **C** — block base rates ordered by shot type, targeting ~5/team/game.
- **D** — loose-ball recovery is a flat four-way draw, defense-leaning.
- **E** — a block is a field-goal OUTCOME, not a new `PlayType` — a block is to a shot what a steal is to a turnover.
- **F** — attribution mirrors a steal: **(F1)** the event is a `SHOT` with `outcome = BLOCKED_*` and the SHOOTER as primary — no new `PlayType`; **(F2)** the blocker is credited by a `recordBlock()` accumulator and is NOT on the event; **(F3)** the shooter is charged a missed FGA, so a block counts against FG%; **(F4)** a blocked shot carries no assist. ⚠ Reconciliation invariant: `SHOT` events `LIKE 'BLOCKED%'` == sum of `BoxScore.blocks`.

### 026 — Missed shot out of bounds (§3.8)

**Date**: 2026-07

- **A** — a missed shot resolves to exactly one of four outcomes in a SINGLE weighted draw (off/def rebound × retain/OOB).
- **B** — a new `MissedShotResolver` WRAPS `ReboundResolver` (which is unchanged) and applies the OOB lean on top.
- **C** — converges in shape with §3.7's block recovery, but skill-weighted rather than flat.
- **D** — sail-out neutrality is VERIFIED on the harness, not assumed (corrects the roadmap's "free" label).
- **E** — an OOB credits NO rebounder; the rebound reconciliation invariant must exclude OOB events.

### 027 — Turnover sub-categories (§3.9)

**Date**: 2026-07

- **A** — the turnover gate is unchanged; a weighted cause-draw runs only AFTER a turnover is declared, so the count cannot move.
- **B** — a 9-outcome cause taxonomy, with STOLEN kept dominant (~56%).
- **C** — fixed tier base weights plus a modest avg-10 lean on four causes, normalized per turnover.
- **D** — `LOST_BALL_OUT_OF_BOUNDS` is spelled so it cannot collide with §3.8's `OUT_OF_BOUNDS_*`; §3.7-E left parked as a seam.
- **E** — reconciliation holds by construction; a per-cause harness line is added for visibility.

### 028 — Loose-ball / rebounding fouls + the team-foul/bonus substrate (§3.10)

**Date**: 2026-07

- **A1** — penalty status is DERIVED from the FOUL event log, never a stored team-foul counter.
- **B** — the fouled team benefits: retention under the bonus, bonus FTs once in the penalty; the fork depends on WHO fouled.
- **C** — the foul roll is layered on the rebound phase BEFORE the board contest, short-circuiting it (the §3.7 carve shape).
- **D** — a `FOUL` event plus a new `committing_team_id` column; the committer is the primary player.
- **E** — recalibration expected. ⚠ Produced the wrong-way-lever finding — since SUPERSEDED by #036; see engine-findings.

### 029 — And-1 / shooting foul on a made basket (§3.11)

**Date**: 2026-08

- **A1** — the and-1 is a SECOND, post-make foul roll; the pre-shot foul branch is untouched.
- **B** — reward is the made FG (already scored) plus exactly ONE free throw. ⚠ Superseded for the flagrant case only (#034 C).
- **C** — the and-1 rate is a rare-event probability with its own base + sensitivity, reusing `rareEventProbability` (avoiding the #028 `PROB_FLOOR` trap by design).
- **D** — free throws become SELF-DESCRIBING via a `FreeThrowSource` suffix on the outcome, retiring §3.10's FT ambiguity.
- **E** — recalibration is a pure-additive FT lift; tune the shot `BASE_*` lever, steering by multiple seeds.

### 030 — All-shot-type contact fouls (§3.12)

**Date**: 2026-08

- **A1** — `ShotType.isContactType()` is DELETED for a per-shot-type foul-multiplier table; no binary predicate survives.
- **A2** — the table is ANCHORED on the untouched `BASE_NO_BASKET_FOUL` (0.15), not re-derived.
- **B** — ONE shared multiplier table drives both the pre-shot and the and-1 roll; the two keep separate bases and sensitivities.
- **C** — the FT count is a rule on `ShotType.freeThrowsIfFouled()` (3 for a three); only the stopped-shot count graduates.
- **D** — `BONUS_FOULS_PER_PERIOD` stays 5 — a real NBA rule is honored, never used as a calibration lever.
- **E** — ONE re-centering, cheapest lever first, never `BASE_NO_BASKET_FOUL`. ⚠ Its stop condition FIRED; no trim was taken.
- **F** — `BASE_FOUL` RENAMED `BASE_NO_BASKET_FOUL` (value unchanged) — the old name invited tuning it as "the foul rate".
- **G** — a foul-out harness line is added; nothing had ever measured it.

### 031 — Foul trouble & foul-outs (§3.13)

**Date**: 2026-08

- **A** — decomposition FIRST, and it overturned the brief: the cause is fouls being **over-dispersed by defender selection**, not foul volume or missing subs.
- **B** — the bench rule is PROBABILISTIC, scaled by `substitutionAggressiveness` AND the player's value to the team.
- **C** — the threshold does NOT vary by period or time remaining (user call: keep it simple).
- **D** — a benched player returns through the ORDINARY freshness path; the sit is made sticky by existing energy state — no timer, no flag.
- **E** — foul trouble is a DERIVED predicate over `fouls`; no stored `inFoulTrouble`.
- **F** — when the bench is thin the SOFT rule yields; the foul-out bar is absolute — a fouled-out player NEVER returns.
- **G** — judge by the 4/5/6 DISTRIBUTION, not the headline count; the minutes cost is budgeted up front.
- **H** — foul-outs promoted from ballpark to TARGET; §3.14's ejection must reuse this removal machinery. ⚠ The lever is SATURATED — see engine-findings.

### 032 — Technical fouls (§3.14a)

**Date**: 2026-08

- **A** — §3.14 SPLITS: §3.14a technicals, §3.14b flagrants — they share nothing but the word "foul".
- **B** — the technical rate is PURELY RANDOM, rolled per team outside the possession flow, with NO causal model. ⚠ ~200 checks/team/game, not 100 — both rotations advance every possession.
- **C** — the committer is drawn from the on-floor five weighted by `foulProne`; the bench is excluded.
- **D** — the possession is UNCHANGED; play resumes from the point of interruption even when the offense commits.
- **E** — technicals get their OWN counter and do NOT feed the 6-foul limit (the NBA rule).
- **F** — the two-technical ejection extends `eligible(...)` and stays DERIVED; #023 F's exception is deferred to §3.14b.
- **G** — the technical FT shooter is a DETERMINISTIC best-shooter pick, beside the untouched weighted draw.
- **H** — #030's clamp-helper consolidation lands here at its fourth site.
- **I** — cost budgeted +0.26/team/game, below the noise floor, so the stop condition INVERTS: points must NOT move measurably.
- **J** — a new harness line is required; the rate is a `ballpark`, not a TARGET.

### 033 — Surface the `technicalFouls` counter on the box score

**Date**: 2026-08

- **A** — the counter is surfaced everywhere the other stat columns are (DB + entity + OpenAPI), not DB-only.
- **B** — the justification is PARITY, not a new consumer — which is what keeps it compatible with #014/#017.
- **C** — a denormalized convenience on the end-of-game snapshot; the events stay the source of truth (#020).
- **D** — the #032 E counter split is preserved at every layer: `fouls` and `technical_fouls` are never merged.

### 034 — Flagrant fouls (§3.14b)

**Date**: 2026-08

- **A** — a flagrant is an ADDITIONAL severity roll on a foul that already happened, at all three foul sites; no existing rate changes.
- **B** — possession retention IS the `continue` the offensive rebound already uses, under the same cap.
- **C** — the fork is decided by WHO committed, not which site rolled; four cases collapse to two rules.
- **D** — the FTs are shot by the player who was FOULED at every site; no new shooter-selection function.
- **E** — flagrant-1 vs flagrant-2 is a flat 15% severity sub-roll with no causal input.
- **F** — ⚠ the flagrant-2 ejection is a MONOTONIC COUNTER and stays DERIVED — #023 F's stored-state exception REFUSED a second time, and now retired.
- **G** — rate stored as ~0.16 per team per game, divided down by the FOUL count.
- **H** — a `ballpark` with its own harness line; no new box-score column.
- **I** — a flagrant IS a personal foul: `recordFoul()`, feeds the six, counts toward the bonus — the exact opposite of #032 E, deliberately.
- **J** — cost budgeted +0.43/team/game over two channels; retention is re-priced, not assumed free.

### 035 — `SimConfig` profiles (§3.15)

**Date**: 2026-08

- **A** — all 57 tunables move OUT to `application-baseline.properties` as their ONLY copy; no initializers in `SimConfig.java`.
- **B** — the constants become INSTANCE state, not static-mutable, because a profile must eventually be a per-simulation input.
- **C** — the profilable set is WIDE; only rules and model machinery stay `static final`.
- **D** — a missing or unbindable value is a STARTUP FAILURE, structural rather than hand-written (no defaults to fall back on).
- **E** — tests construct a baseline default and read it as an instance; no static aliases.
- **F** — one profile per harness invocation via the ordinary Spring profile list, and it prints the effective config. ⚠ That dump caught a hardcoded pace — see engine-findings.
- **G** — the validation gate: the baseline profile reproduces §3.14b's landing **per-seed identical**, not merely in aggregate.
- **H** — the doc-drift generator is OUT, decided deliberately rather than by omission.
- **I** — calibration targets are BASELINE-ONLY; off-baseline the harness prints profile-vs-baseline deltas.

### 036 — Phase 3's tail RESEQUENCED into §3.16 / §3.17 / §3.18

**Date**: 2026-08

- **A** — FG% is NOT contested and never needed engine work; **the TARGET was wrong** (real 47.1 vs engine 46.9, inside the standard error).
- **B** — the real finding: the totals are close but the COMPOSITION is wrong — 2-pt +10.9, 3-pt −18.0, FT +7.1, summing to ~zero.
- **C** — §3.16 becomes shooting-foul composition: 74.8% of fouls are `SHOOTING_FOUL`, producing 29.84 of 34.0 FTA.
- **D** — §3.17 becomes shot mix / 3PA, deliberately unscoped here; its first question is whether it is an engine problem at all.
- **E** — §3.18 (recalibration) goes LAST — the calibration-blast-radius principle; three passes already paid for a stale anchor.
- **F** — the phases RENUMBER rather than take a/b suffixes; the mapping is recorded here. ⚠ Superseded by #038.
- **G** — #034's "last new mechanic in Phase 3" is AMENDED, not quietly broken.
- ⚠ **Also here (in the cut Alternatives): the measured DISPROOF of #028's wrong-way lever — see engine-findings.**

### 037 — Charges are personal fouls by rule; folded into §3.16

**Date**: 2026-08

- **Decision** — a charge (`TurnoverCause.OFFENSIVE_FOUL`) is a personal foul by rule and was not one in the engine: `recordFoul()` was never called, so it counted toward nothing — not the six, not the bonus, not the box score. Fixed INSIDE §3.16 because it moves that phase's own baseline (fouls 19.61 → 20.87). ⚠ Consequences: two events for one occurrence; the committer is on OFFENSE so the DEFENSE gains the bonus; foul-outs rise as a correctness effect; not flagrant-eligible.

### 038 — §3.18 (the steal) added; recalibration moves to §3.19 and LAST

**Date**: 2026-08

- **Decision** — recalibration is **§3.19 and last BY RULE**: every pass before it settles the SHAPE of the game (foul mix, shot mix, event vocabulary); recalibration re-solves the numbers once that shape is final. A new §3.18 (the steal as a first-class event) is inserted before it.
- ⚠ **THE MAPPING, superseding #036 F**: recalibration was §3.16 (as named in the shipped text of #030/#031/#032/#034/#035), briefly §3.18, and is now **§3.19**. Those entries are not retro-edited; this is the current record.
- **Why §3.18** — a steal is the only contested defensive play the event log does not attribute: the stealer is picked, credited, then dropped, and the `TURNOVER` event names the player who LOST the ball.

### 039 — Shooting-foul composition (§3.16)

**Date**: 2026-08

- **A** — the re-partition is a SECOND roll on the already-charged foul; `isFoul` is untouched and bit-identical.
- **B** — the converted `COMMON_FOUL` IS a personal foul and DOES count toward the bonus.
- **C** — ⚠ the possession ENDS; the ball does not come back — reversing the user's reading, on measurement (the FGA tripwire caps a retaining variant below relevance).
- **D** — the ~35% share is withdrawn for ~43%: 18.5% of shooting fouls occur already in the penalty and award 2 bonus FTs. ⚠ Shipped at 0.50 — the share is COUPLED to the bonus rate.
- **E** — a flat `SimConfig` share with no skill input; `foulProne` has already had its say at `pickDefender`.
- **F** — the flagrant roll DOES apply to the common foul; its shooter is the fouled player.
- **G** — charges become personal fouls (#037): BOTH a `TURNOVER` and a `FOUL` event for one occurrence.
- **H** — ⚠ the ~8-point drop is HANDED to §3.19 deliberately — that is the whole reason §3.19 exists.

### 040 — Shot mix / the 3PA gap (§3.17)

**Date**: 2026-08

- **A** — ⚠ the 3PA gap is the ENGINE, not the population: `shotTypeWeight(DRIVE)` SUMS two skills while the other three types get one.
- **B** — that sum is an OVERSIGHT, not a deliberate call — #021 D's own wording is the evidence.
- **C** — the base mix becomes an explicit four-value SHARE TABLE in the properties file; skill MODULATES it rather than defining it.
- **D** — `shotMixLean` SPLITS into two independent leans. ⚠ Measured as a per-coach lever, not a 3PA lever — it moved the aggregate bit-for-bit not at all.
- **E** — ⚠ the pass does NOT hold FGA (the todo predicted it would): a three is stopped 7.5× less often than a drive.
- **F** — FG% and FGA both breach and are handed to §3.19 — the root cause is a 2P% error the old mix was HIDING. ⚠ `base-three` must NOT move.
- **G** — the foul multipliers are NOT re-tuned; the instrument is fixed first. The fouled-three rate is scale-free (2.92% → 2.93% across a 1.9× volume change).
- **H** — the rebounding gap does not resolve itself; §3.17 closes about a third.
- **I** — worth ~+4 points, and points is still not this phase's number.
- **J** — `SHOT_MIX_SENSITIVITY` stays a static; the four shot-share keys are tunables.
- **K** — baseline shares are set to land 3PA at the TARGET (37.0), not the middle of the stated band.
- **L** — `nineties` gets the four keys too, so the profile can finally mean what it says.
- **M** — `COMMON_FOUL` is RENAMED `NON_SHOOTING_FOUL`, reversing #039 E's config-key-vs-log divergence.
- **N** — the rename ships INSIDE §3.17; **there is no migration**.

### 041 — The steal as a first-class event (§3.18)

**Date**: 2026-08

- **A** — a GENERALIZED counterparty column `opponent_player_id`, not a steal-specific field.
- **B** — the steal rides the EXISTING `TURNOVER` event; no second event, no `PlayType.STEAL`.
- **C** — the rule that decides it: **second PARTICIPANT → a column; second ACCOUNTING → an event; neither → nothing.**
- **D** — assists stay on `assist_player_id`; an assister is a teammate, the opposite invariant.
- **E** — no side-indicator column: the side is already derivable from primary + the two team columns.
- **F** — the rate is measured at **7.72** (sd 0.148) and routed to §3.19 UNTOUCHED.
- **G** — the column buys a PER-CREDITOR reconciliation; the old total-based check passes even when the wrong player is credited.
- **H** — `game-events.md`: three rules, ONE master table, detail only where an event carries a trap.

### 042 — Recalibration (§3.20)

**Date**: 2026-08

- **A** — the over-determination is NOT real; the pass targets the full landing.
- **B** — ⚠ claims `non-shooting-foul-share` moves FTA and FGA as ONE lever. **This is DISPROVED in execution — see engine-findings.**
- **C** — FT% becomes a sourced TARGET (it was running 4.6 points hot and absent from calibration.md entirely).
- **D** — the 2P% lever is `base-drive`/`base-post`/`base-perimeter`, moved UNEQUALLY; `base-three` is frozen.
- **E** — 3PA is held at 37.0 with `shot-share-three` rather than left to fall.
- **F** — every lever is set by measure → adjust → re-measure, never by arithmetic. ⚠ The 2P wedge is MULTIPLICATIVE (pass-through 0.890).
- **G** — the stop condition is ±2 standard errors of the 5-seed MEAN, fixed before tuning; the docs' ±1.5 is per-seed.
- **H** — ⚠ INVERTED in execution: the rebound gap does not behave as modelled and `base-offensive-rebound` was NOT moved.
- **I** — steals are DERIVED and not tuned; the identity reproduces to 0.01.
- **J** — the sourcing sweep: every calibration.md row is now a sourced TARGET or deliberately not one; foul-outs demoted to `ballpark` as circular.

### 043 — The rebound pool (§3.21)

**Date**: 2026-08

- **A** — ⚠ the "1.97 unexplained" DOES NOT EXIST: it is the REBOUNDING FOUL (measured 2.337), already on the diagram and correct as basketball.
- **B** — consequently todo.md's bracket only closes because it splits both new slices at an aggregate share neither of them uses.
- **C** — a missed LAST free throw becomes a live rebound at the three sources where the rule says so, reusing `MissedShotResolver` whole.
- **D** — the FT board runs the ORDINARY contest; the defense's by-rule inside position is an existing static (0.68), not a new tunable.
- **E** — the in-bounds block recovery credits a rebounder by reusing the already-drawn recovery side; #025 D's flatness is preserved.
- **F** — a THROWAWAY probe, not a permanent harness line (a permanent row needs re-validating every phase).
- **G** — §3.21 owns its re-landing; expected damage is FGA. ⚠ Predicted ~2.5× too large — rebounds→FGA elasticity is well under 1.
- **H** — TWO LAYERS, not one method with a flag: `awardFreeThrows` keeps the shared trip; `awardLiveFreeThrows` owns the last-FT board.

### 044 — The putback (§3.22)

**Date**: 2026-09

- **A** — DOUBLE the weight: `M = 2.0`, a MULTIPLIER on the rebounder's `offensiveWeight`, for the next draw only. ⚠ The constant is the multiplier; the realized share (35.4%) is not the same number and was not back-solved.
- **B** — a tunable, `sim.offensive-rebounder-shot-weight = 2.0` (62 → 63); `1.0` means off.
- **C** — a `putbackCandidate` LOCAL read-and-cleared at the top of the iteration, set on exactly the three paths that identify a rebounder.
- **D** — NO decay across retentions; the second putback is as likely as the first.
- **E** — a putback's assist chance is HALVED, not zeroed (`OFFENSIVE_REBOUNDER_ASSIST_LEAN = 0.5`, 28 → 29 statics).
- **F** — expected movement little, and MEASURED rather than assumed: FG% goes **UP** (+0.24), correcting the prior.
- **G** — §3.22 re-lands its own calibration; the re-tune is one lever or none.
- **H** — `pickShooter` takes the rebounder as a PARAMETER — the rebounder is a participant, not a mode. Resolves the OTHER way from #043 H.
- **I** — the draw COUNT is unchanged, the stream still moves, and NO seeded test re-baselines.

