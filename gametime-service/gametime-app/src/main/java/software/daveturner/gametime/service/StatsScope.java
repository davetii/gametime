package software.daveturner.gametime.service;

import software.daveturner.gametime.entity.GameStatus;

/** The games every aggregate reads (#045 I). Today all FINAL games; Phase 5 adds the season. */
public record StatsScope(GameStatus status) {

    public static StatsScope allFinalGames() {
        return new StatsScope(GameStatus.FINAL);
    }
}
