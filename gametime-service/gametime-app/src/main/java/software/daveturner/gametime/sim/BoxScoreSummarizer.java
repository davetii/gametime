package software.daveturner.gametime.sim;

import software.daveturner.gametime.entity.PlayType;

import java.util.*;
import java.util.function.ToIntFunction;

/** Builds each player's box-score line from a game's event log (decisions.md #045 A). */
public final class BoxScoreSummarizer {

    static final String OFFENSIVE_REBOUND_OUTCOME = "OFFENSIVE";
    static final String DEFENSIVE_REBOUND_OUTCOME = "DEFENSIVE";

    private static final String MADE_PREFIX = "MADE_";
    private static final String BLOCKED_PREFIX = "BLOCKED_";
    private static final String THREE_SUFFIX = "_3PT";

    private BoxScoreSummarizer() {
    }

    /** Each derived column paired with the counter it must equal (#045 B). */
    public enum Column {
        POINTS("points", Line::points, PlayerGameState::getPoints),
        FIELD_GOALS_ATTEMPTED("field_goals_attempted", Line::fieldGoalsAttempted, PlayerGameState::getFieldGoalsAttempted),
        FIELD_GOALS_MADE("field_goals_made", Line::fieldGoalsMade, PlayerGameState::getFieldGoalsMade),
        THREE_POINTERS_ATTEMPTED("three_pointers_attempted", Line::threePointersAttempted, PlayerGameState::getThreePointersAttempted),
        THREE_POINTERS_MADE("three_pointers_made", Line::threePointersMade, PlayerGameState::getThreePointersMade),
        FREE_THROWS_ATTEMPTED("free_throws_attempted", Line::freeThrowsAttempted, PlayerGameState::getFreeThrowsAttempted),
        FREE_THROWS_MADE("free_throws_made", Line::freeThrowsMade, PlayerGameState::getFreeThrowsMade),
        OFFENSIVE_REBOUNDS("offensive_rebounds", Line::offensiveRebounds, PlayerGameState::getOffensiveRebounds),
        DEFENSIVE_REBOUNDS("defensive_rebounds", Line::defensiveRebounds, PlayerGameState::getDefensiveRebounds),
        ASSISTS("assists", Line::assists, PlayerGameState::getAssists),
        STEALS("steals", Line::steals, PlayerGameState::getSteals),
        BLOCKS("blocks", Line::blocks, PlayerGameState::getBlocks),
        TURNOVERS("turnovers", Line::turnovers, PlayerGameState::getTurnovers),
        FOULS("fouls", Line::fouls, PlayerGameState::getFouls),
        TECHNICAL_FOULS("technical_fouls", Line::technicalFouls, PlayerGameState::getTechnicalFouls);

        private final String columnName;
        private final ToIntFunction<Line> derived;
        private final ToIntFunction<PlayerGameState> counted;

        Column(String columnName, ToIntFunction<Line> derived, ToIntFunction<PlayerGameState> counted) {
            this.columnName = columnName;
            this.derived = derived;
            this.counted = counted;
        }

        public String columnName() { return columnName; }
        public int derived(Line line) { return derived.applyAsInt(line); }
        public int counted(PlayerGameState player) { return counted.applyAsInt(player); }
    }

    public record Line(String playerId, int points,
                       int fieldGoalsAttempted, int fieldGoalsMade,
                       int threePointersAttempted, int threePointersMade,
                       int freeThrowsAttempted, int freeThrowsMade,
                       int offensiveRebounds, int defensiveRebounds,
                       int assists, int steals, int blocks, int turnovers,
                       int fouls, int technicalFouls) {

        public static Line empty(String playerId) {
            return new Line(playerId, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    public record Summary(Map<String, Line> lines) {

        public Line lineFor(String playerId) {
            Line line = lines.get(playerId);
            return line != null ? line : Line.empty(playerId);
        }

        // Team comes from the caller: a FREE_THROW event doesn't say which side the shooter is on.
        public int points(Collection<String> playerIds) {
            int total = 0;
            for (String id : playerIds) {
                total += lineFor(id).points();
            }
            return total;
        }
    }

    public static Summary summarize(List<GameData.EventRecord> events) {
        Map<String, Tally> tallies = new LinkedHashMap<>();
        for (GameData.EventRecord e : events) {
            switch (e.playType()) {
                case SHOT -> shot(e, tallies);
                case FREE_THROW -> freeThrow(e, tallies);
                case REBOUND -> rebound(e, tallies);
                case TURNOVER -> turnover(e, tallies);
                case FOUL -> foul(e, tallies);
            }
        }
        Map<String, Line> lines = new LinkedHashMap<>();
        tallies.forEach((id, t) -> lines.put(id, t.toLine(id)));
        return new Summary(Collections.unmodifiableMap(lines));
    }

    private static void shot(GameData.EventRecord e, Map<String, Tally> tallies) {
        String outcome = e.outcome();
        boolean three = outcome.endsWith(THREE_SUFFIX);
        Tally shooter = tally(tallies, e.primaryPlayerId());
        shooter.fieldGoalsAttempted++;
        if (three) {
            shooter.threePointersAttempted++;
        }
        if (outcome.startsWith(MADE_PREFIX)) {
            shooter.fieldGoalsMade++;
            shooter.points += three ? 3 : 2;
            if (three) {
                shooter.threePointersMade++;
            }
        }
        if (e.assistPlayerId() != null) {
            tally(tallies, e.assistPlayerId()).assists++;
        }
        if (outcome.startsWith(BLOCKED_PREFIX) && e.opponentPlayerId() != null) {
            tally(tallies, e.opponentPlayerId()).blocks++;
        }
    }

    private static void freeThrow(GameData.EventRecord e, Map<String, Tally> tallies) {
        Tally shooter = tally(tallies, e.primaryPlayerId());
        shooter.freeThrowsAttempted++;
        if (e.outcome().startsWith(MADE_PREFIX)) {
            shooter.freeThrowsMade++;
            shooter.points++;
        }
    }

    private static void rebound(GameData.EventRecord e, Map<String, Tally> tallies) {
        if (OFFENSIVE_REBOUND_OUTCOME.equals(e.outcome())) {
            tally(tallies, e.primaryPlayerId()).offensiveRebounds++;
        } else if (DEFENSIVE_REBOUND_OUTCOME.equals(e.outcome())) {
            tally(tallies, e.primaryPlayerId()).defensiveRebounds++;
        }
    }

    private static void turnover(GameData.EventRecord e, Map<String, Tally> tallies) {
        tally(tallies, e.primaryPlayerId()).turnovers++;
        if (TurnoverCause.STOLEN.outcome().equals(e.outcome()) && e.opponentPlayerId() != null) {
            tally(tallies, e.opponentPlayerId()).steals++;
        }
    }

    private static void foul(GameData.EventRecord e, Map<String, Tally> tallies) {
        Tally committer = tally(tallies, e.primaryPlayerId());
        if (GameData.TECHNICAL_FOUL_OUTCOME.equals(e.outcome())) {
            committer.technicalFouls++;
        } else {
            committer.fouls++;
        }
    }

    private static Tally tally(Map<String, Tally> tallies, String playerId) {
        if (playerId == null) {
            throw new IllegalStateException("A crediting event names no player");
        }
        return tallies.computeIfAbsent(playerId, id -> new Tally());
    }

    private static final class Tally {
        int points;
        int fieldGoalsAttempted;
        int fieldGoalsMade;
        int threePointersAttempted;
        int threePointersMade;
        int freeThrowsAttempted;
        int freeThrowsMade;
        int offensiveRebounds;
        int defensiveRebounds;
        int assists;
        int steals;
        int blocks;
        int turnovers;
        int fouls;
        int technicalFouls;

        Line toLine(String playerId) {
            return new Line(playerId, points, fieldGoalsAttempted, fieldGoalsMade,
                    threePointersAttempted, threePointersMade, freeThrowsAttempted,
                    freeThrowsMade, offensiveRebounds, defensiveRebounds, assists,
                    steals, blocks, turnovers, fouls, technicalFouls);
        }
    }
}
