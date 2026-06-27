# Workflow Protocol

How we work on the Gametime project.

## Branch Strategy

- `main` — stable, passing builds only
- Feature branches off main: `feature/<short-description>`
- Bug fix branches: `fix/<short-description>`
- Keep branches short-lived — merge and delete

## Build Verification

Before merging any change:
```bash
JAVA_HOME=/Users/dave/.sdkman/candidates/java/21.0.9-tem mvn clean test
```
All 78+ tests must pass. JaCoCo enforces 80% line coverage.

## Code Conventions

- **OpenAPI first**: API changes start in `gametime-api/yml/gametime.yaml`, never hand-edit generated code
- **Delegate pattern**: generated `V1ApiDelegate` interface, hand-written `V1ApiDelegateimpl` implements it
- **Entities**: Lombok `@Data`, `@Table(schema = "gametime")`, audit columns
- **Services**: interface + implementation pattern (`GametimeService` / `GametimeServiceImp`)
- **Skill calculators**: implement `SkillCalculator` interface, one class per skill, registered as `@Component`
- **Tests**: unit test every calculator and mapper, Cucumber integration tests for API endpoints

## Database Changes

1. Write Liquibase changeset in `src/main/resources/db/`
2. Use `dbms:postgresql` precondition for Postgres-specific SQL (triggers, plpgsql)
3. Ensure H2 compatibility for test suite (use column defaults for audit fields)
4. All tables in `gametime` schema

## AI Session Protocol

- CLAUDE.md at project root is the entry point for any AI session
- Each session should read CLAUDE.md first for build commands, module boundaries, and conventions
- Reference `docs/PROJECT_PLAN.md` for current phase and task context
- Reference `docs/DECISIONS.md` for past architectural choices before proposing alternatives
- Log new decisions in DECISIONS.md when significant choices are made
- Don't modify generated code in `gametime-api/target/`

## Testing Strategy

- **Unit tests**: all business logic (skill calculators, mappers, services)
- **Cucumber integration tests**: API endpoint behavior (happy path + error cases)
- **Manual testing**: IntelliJ `.http` files in `gametime-service/http/` (select "local" environment)
- **Database**: tests run against H2 in-memory, no Docker required for `mvn test`
