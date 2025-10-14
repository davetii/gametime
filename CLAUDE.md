# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Basketball simulator Spring Boot application built with:
- Spring Boot 3.1.5
- Java 17
- OpenAPI code generation
- Liquibase for database migrations
- Multi-profile support (local/test)
- Cucumber for BDD testing

## Build and Test Commands

### Basic Build
```bash
mvn install
```

### Run Application
```bash
mvn spring-boot:run
```

### Run Tests
```bash
# Run all tests (unit + integration)
mvn verify

# Run only unit tests
mvn test

# Run specific test
mvn test -Dtest=ClassName

# Run Cucumber integration tests only
mvn failsafe:integration-test
```

### Code Coverage
```bash
mvn jacoco:report
```
Note: Build enforces 80% code coverage threshold. Report generated at `target/site/jacoco/index.html`.

### Docker Build
```bash
mvn spring-boot:build-image
```

## Profiles

The application uses Spring profiles for different environments:

- **local** (default): Uses H2 in-memory database
  - H2 Console: http://127.0.0.1:8080/h2-console
  - Liquibase changelog: `db/local/changelog.yml`

- **test**: Uses PostgreSQL via Docker
  - Automatically starts/stops docker-compose during build when profile is active
  - Adminer: http://localhost:8083/
  - Connection: localhost:5433, user: postgres, password: turner
  - Liquibase changelog: `db/test/changelog.yml`

To activate test profile: Set in `application.properties` or use `-Ptest` with Maven commands.

## API Documentation

Swagger UI: http://localhost:8080/swagger-ui/index.html

The API is defined in `yml/gametime.yaml` and code is generated via openapi-generator-maven-plugin during build. Generated code goes to `target/generated-sources/openapi/`.

## Architecture

### Code Generation Flow

1. OpenAPI spec (`yml/gametime.yaml`) defines the API contract
2. Maven plugin generates:
   - API interfaces: `software.daveturner.gametime.api.V1Api`
   - Delegates: `software.daveturner.gametime.api.V1ApiDelegate`
   - Models: `software.daveturner.gametime.model.*`
3. Implementation: `V1ApiDelegateimpl` implements the delegate pattern

**Important**: Never modify generated code directly. Changes must be made to the OpenAPI spec, then regenerate.

### Layer Architecture

```
Controller (V1ApiDelegateimpl)
    -> Service (GametimeService/GametimeServiceImp)
    -> Repository (TeamRepo, PlayerRepo, etc.)
    -> Entity (TeamEntity, PlayerEntity, etc.)
```

### Player Skills Calculation System

Player skills are dynamically calculated from base attributes using a strategy pattern:

- `SkillMapper` orchestrates 13 different skill calculators
- Each calculator implements `SkillCalculator` interface
- Skills calculated on-demand when mapping PlayerEntity -> Player model
- Examples: `AcumenSkillCalculator`, `DriveSkillCalculator`, `LongRangeSkillCalculator`

Skills are derived from base player attributes (agility, strength, speed, intelligence, etc.) using domain-specific formulas in each calculator.

### Mapping Strategy

- `EntityMapper`: Converts JPA entities to API models
- `SkillMapper`: Calculates player skills from base attributes
- Entity-to-model mapping happens at service layer before returning to API layer

### Testing Structure

- **Unit tests**: Standard JUnit tests in `src/test/java`
- **Integration tests**: Cucumber BDD tests
  - Features: `src/test/resources/features/*.feature`
  - Step definitions: `cucumber/CucumberStepDefs.java`
  - Runner: `CucumberRunner.java` (picked up by maven-failsafe-plugin)
  - WireMock used for mocking external dependencies

## Lombok Configuration

Project uses Lombok with annotation processor configured in `pom.xml`. Entities use Lombok annotations for boilerplate reduction. Lombok is excluded from final build image.

## JaCoCo Exclusions

The following are excluded from code coverage:
- Generated OpenAPI code (`ApiUtil`, `V1ApiController`, `V1ApiDelegate`, `V1Api`, model classes)
- Application entry point (`GametimeApplication`)
- Enums (`ConferenceEnum`)

## Database Migrations

Liquibase manages schema versions:
- Migrations are profile-specific (local vs test)
- SQL files in `src/main/resources/db/{profile}/`
- Data loading handled via SQL scripts: `release.1.0.1.dataload.sql`

## Key Entities

- **TeamEntity**: Represents a basketball team with players, coach, GM
- **PlayerEntity**: Player with physical attributes, position, status, and stats
- **CoachEntity/GMEntity**: Team management roles
- **Position**: Enum for player positions (PG, CG, BG, W, SF, F, PF, FC, C)
- **Status**: Enum for player status (STARTER, BENCH, ROTATION, MINORS, INJURED, SUSPENDED)
- **ConferenceEnum**: EAST, NORTH, SOUTH, WEST
