---
name: test-coverage
description: Ensure new/changed Java code in gametime-app ships with tests. Use when adding or modifying production code under gametime-service/gametime-app/src/main/java — new endpoints, service methods, mappers, entities, calculators — before considering the task done. Aim to clear the JaCoCo gate with margin (target ~90%), not just scrape the 80% floor.
---

# Test coverage for gametime-app

When you add or change production code under
`gametime-service/gametime-app/src/main/java/`, add tests for it in the same
change. Don't treat a feature as done until it's covered and the coverage gate
passes.

## The gate (what the build actually enforces)

`gametime-app/pom.xml` runs `jacoco:check` with **80% LINE coverage per
PACKAGE** (`element=PACKAGE`, `counter=LINE`, `minimum=0.80`). It is a hard
build gate.

- The check runs at the `verify`/`install` phase, **not** `mvn test`. A green
  `mvn test` does NOT prove the gate passes. Always confirm with:
  ```
  JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn -f gametime-service/pom.xml clean install
  ```
  Look for `All coverage checks have been met.`
- It is **per package**, so small packages are fragile: one untested
  constructor, branch, or class can fail the whole build even when overall
  coverage is high (e.g. an unused no-arg exception constructor once dropped the
  `exception` package to 66%). When you add a class to a small package, make
  sure every line you add is reached by a test — or drop genuinely dead code
  rather than leaving it uncovered.

## Target, not just the floor

The 80% gate is the floor for the whole team. For code *you* add, aim higher
(~90% lines, and exercise the meaningful branches) so the package keeps margin
and the next small change doesn't tip it under. Prefer deleting dead code over
writing a token test that only exists to color a line.

## Where tests live (match existing conventions)

Mirror the package under `gametime-app/src/test/java/...`:

- **Endpoints** (`V1ApiDelegateimpl`): `api/V1ApiDelegateimplTest` —
  `@SpringBootTest`, autowire `V1ApiDelegate`, assert status codes and bodies,
  and cover the error paths (404 `ResourceNotFoundException`, 409
  `ResourceConflictException`), not just the happy path.
- **Service** (`GametimeServiceImp`): covered via the delegate tests above, or a
  dedicated service test where logic warrants it.
- **Mappers** (`EntityMapper`, `SkillMapper`): `mapper/...Test` — assert each
  mapped field; include the tricky round-trips (e.g. `Position` "W"↔WING).
- **Entities / enums**: small focused tests like the existing
  `entity/*EntityTest`.
- **Skill calculators**: one `mapper/*SkillCalculatorTest` per calculator,
  asserting the average-player baseline and the deviation cases.

## Checklist before declaring a code change done

1. Added/updated tests covering the new lines and their error/edge branches.
2. Ran `mvn clean install` (JDK 21) — tests pass AND
   `All coverage checks have been met.`
3. No new dead code left uncovered (delete it instead).

Build notes: always set `JAVA_HOME` to the SDKMAN JDK 21
(`/Users/dave/.sdkman/candidates/java/21.0.9-tem`) — see CLAUDE.md. The JaCoCo
excludes (generated API stubs, `model/*`, `GametimeApplication`,
`ConferenceEnum`) are already configured in `gametime-app/pom.xml`; you don't
need to test generated code.
