# Risks & Concerns

Active risks and concerns. Remove items as they're resolved (move to the Resolved section at the bottom).

---

## Active

### Simulation performance at scale
**Severity**: Medium  
**Description**: A full season is potentially 40 teams x 40+ games = 800+ games. If each game simulates possession-by-possession (~200 possessions per game), that's 160,000+ possessions to compute and potentially persist. Need to decide early whether to store play-by-play events or only box scores.  
**Mitigation**: Design the game engine with configurable detail level. Benchmark early.

### Coach/GM attribute design is undefined
**Severity**: Medium  
**Description**: Coaches and GMs are currently name-only entities. The game engine needs coach attributes to influence gameplay (pace, defensive scheme, rotation depth), but the attribute model hasn't been designed yet. This is a dependency for Phase 3.  
**Mitigation**: Resolve in Phase 1.2/1.3 before starting game engine work.

### Skill formula balance
**Severity**: Medium  
**Description**: The 13 skill calculators have hand-tuned threshold values. Until games are simulated and stats reviewed, we won't know if the formulas produce realistic distributions. A player with maxed shotSkill + shotSelection might be unrealistically dominant.  
**Mitigation**: Plan for a formula tuning pass after the game engine produces its first batch of box scores. Consider adding a normalization step.

### H2/Postgres divergence risk
**Severity**: Low  
**Description**: Tests run on H2, production on Postgres. As the schema grows (game events, stats tables, potentially JSON columns), the gap between H2 and Postgres behavior could cause test-passes-but-prod-fails scenarios.  
**Mitigation**: Consider Testcontainers for integration tests if H2 divergence becomes a problem. Keep Liquibase changesets simple.

### Health attribute unused
**Severity**: Low  
**Description**: The `health` attribute is defined, stored, and exposed via the API but doesn't feed into any skill calculator or game mechanic. It's dead weight until the injury system is built (Phase 6).  
**Mitigation**: Acceptable for now. Wire it in during Phase 6 or earlier if a fatigue/injury model is needed for game simulation (Phase 3).

### Seed data realism
**Severity**: Low  
**Description**: The ~420 pre-loaded players have attributes that were manually assigned. Their attribute distributions may not produce realistic game outcomes once simulation is running.  
**Mitigation**: After game engine produces stats, compare distributions to real basketball benchmarks and adjust seed data or formulas.

---

## Resolved

*Move resolved items here with a note on the resolution.*

<!-- Example:
### Item title
**Resolved**: 2026-05 — Description of resolution.
-->
