# Architecture Decisions

Log of significant design and architecture decisions. Append new entries at the bottom.

---

### 001 — Multi-module Maven structure
**Date**: 2026-05  
**Decision**: Split gametime-service into `gametime-api` (generated stubs) and `gametime-app` (hand-written code).  
**Rationale**: Keeps generated OpenAPI code isolated from application logic. The api module is a pure build artifact — no hand-written code lives there. The app module depends on it as a Maven dependency.  
**Alternatives considered**: Single module with generated sources in target/ (simpler but generated and hand-written code mixed in IDE).

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
**Decision**: Canonical player attribute scale is **1–20, with 10 = league average**. Applies to all 21 attributes (16 original + 5 new). The existing 16 attributes were rescaled to mean ~10 / stdev ~3.5 via a distribution-based (rank → inverse-normal) transform.  
**Rationale**: The original 1–10 scale was too coarse to separate ~420 players — values compressed around 5–6, and stars had to be hand-bumped past 10 (the data had drifted to a nominal 1–15 with most values still in 3–8). The real fix was widening the *distribution*, not just the ceiling. Centering average at 10 leaves deliberate headroom for genuine outliers (stars 16–18, generational 19–20) and below-average players (3–5), and matches how rating systems keep "average" well below max.  
**Trade-off**: The existing 13 skill calculators' threshold branches (`>9`, `>7`, etc.) were written against the old ~1–10 range and must be re-tuned for 1–20, or they mis-fire (an "above average" bonus now triggers for most of the league).  
**Alternatives considered**: 1–15 (keep current data — marginal variability gain); 1–100 (false precision, can't meaningfully assign across 420 hand-curated players); linear shift+stretch instead of distribution-based (preserves exact gaps but amplifies existing data artifacts).

### 009 — Player data is CSV-driven
**Date**: 2026-06  
**Decision**: Player seed data is the source-of-truth CSV `db/players.csv` (422 players), loaded via a Liquibase `loadData` changeset. Replaced the ~420 inline SQL `INSERT` statements previously in `release.1.0.1.dataload.sql`.  
**Rationale**: A CSV is far easier to inspect, sort, bulk-transform (rescale/re-center), and eyeball for outliers than 420 single-line inserts. Follows the existing `gm.csv` / `loadData` precedent. The old `player.csv` had drifted stale (421 rows, missing a player); the canonical CSV was regenerated from the authoritative SQL. Team inserts stay in SQL (FK parent — must load before players).  
**Trade-off**: Schema changes now require both a migration (add column) and a matching CSV column; load order in `changelog.yml` must keep teams → add-columns → players. Existing dev DBs need `docker compose down -v` to pick up schema changes.  
**Alternatives considered**: Keep editing inline SQL (error-prone at 420 rows); append columns to the stale `player.csv` (would bake in the old compressed distribution and the missing player).

---

*Template for new entries:*
```
### NNN — Short title
**Date**: YYYY-MM  
**Decision**: What was decided.  
**Rationale**: Why this choice was made.  
**Alternatives considered**: What else was evaluated (optional).
```
