package software.daveturner.gametime.sim;

import software.daveturner.gametime.entity.PlayType;

import java.util.List;
import java.util.random.RandomGenerator;

/** One possession's fixed context; each event it emits takes the next sequence number. */
final class Possession {

    private final GameData data;
    private final String offTeamId;
    private final String defTeamId;
    private final List<PlayerGameState> offense;
    private final List<PlayerGameState> defense;
    private final int period;
    private final RandomGenerator rng;
    private int sequence;

    Possession(GameData data, String offTeamId, String defTeamId,
               List<PlayerGameState> offense, List<PlayerGameState> defense,
               int period, int sequence, RandomGenerator rng) {
        this.data = data;
        this.offTeamId = offTeamId;
        this.defTeamId = defTeamId;
        this.offense = offense;
        this.defense = defense;
        this.period = period;
        this.sequence = sequence;
        this.rng = rng;
    }

    static Possession of(GameData data, TeamContext offense, TeamContext defense,
                         int period, int sequence, RandomGenerator rng) {
        return new Possession(data, offense.teamId(), defense.teamId(),
                offense.onFloor(), defense.onFloor(), period, sequence, rng);
    }

    GameData data() {
        return data;
    }

    String offTeamId() {
        return offTeamId;
    }

    String defTeamId() {
        return defTeamId;
    }

    List<PlayerGameState> offense() {
        return offense;
    }

    List<PlayerGameState> defense() {
        return defense;
    }

    int period() {
        return period;
    }

    RandomGenerator rng() {
        return rng;
    }

    /** The next free sequence number. */
    int sequence() {
        return sequence;
    }

    void emit(PlayType playType, String outcome, String primaryPlayerId) {
        data.addEvent(offTeamId, defTeamId, period, sequence++, playType, outcome,
                primaryPlayerId);
    }

    void emit(PlayType playType, String outcome, String primaryPlayerId,
              String assistPlayerId) {
        data.addEvent(offTeamId, defTeamId, period, sequence++, playType, outcome,
                primaryPlayerId, assistPlayerId);
    }

    void emit(PlayType playType, String outcome, String primaryPlayerId,
              String assistPlayerId, String committingTeamId) {
        data.addEvent(offTeamId, defTeamId, period, sequence++, playType, outcome,
                primaryPlayerId, assistPlayerId, committingTeamId);
    }

    void emit(PlayType playType, String outcome, String primaryPlayerId,
              String assistPlayerId, String committingTeamId, String opponentPlayerId) {
        data.addEvent(offTeamId, defTeamId, period, sequence++, playType, outcome,
                primaryPlayerId, assistPlayerId, committingTeamId, opponentPlayerId);
    }
}
