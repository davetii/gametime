# TODO

Tactical task list. Check items off or remove them as completed. For the big-picture phased roadmap, see [PROJECT_PLAN.md](PROJECT_PLAN.md).

---

## Immediate (Pre-Phase 1)

- [ ] Delete orphaned `gametime-service/src/` directory (empty leftover from module restructuring)
- [ ] Verify IntelliJ `.http` files work (likely need to select "local" environment in dropdown)
- [ ] Confirm all 78 tests still pass on clean checkout

## Phase 1 — Foundation

- [ ] Implement `createPlayer` endpoint
- [ ] Implement `updatePlayer` endpoint
- [ ] Implement `addPlayerToTeam` endpoint
- [ ] Implement `fetchConference` endpoint
- [ ] Design coach attribute model (see DECISIONS.md for logging the choice)
- [ ] Design GM attribute model
- [ ] Decide how `health` attribute integrates — skill modifier vs injury probability input
- [ ] Add tests for all new endpoints
- [ ] Update OpenAPI spec if Coach/GM models change

## Backlog

- [ ] Evaluate Testcontainers as alternative to H2 for integration tests
- [ ] Add API pagination (parameters already defined in OpenAPI spec but not wired)
- [ ] Consider adding player age field (currently only yearsPro, no birth date)
- [ ] Review skill calculator formulas for balance after game engine exists
- [ ] Set up React project in `gametime-frontend/`
