package software.daveturner.gametime.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One possession-by-possession event in a game's log. Every event is persisted
 * (decisions.md #020). {@code sequence} is monotonic across the WHOLE game and
 * does not restart per period, so play-by-play is a single ordered read. The
 * shape is intentionally minimal (one primary player, free-text outcome) and
 * grows additively with the §3.2 possession engine.
 */
@Entity
@Table(name = "game_event", schema = "gametime")
@Data
public class GameEventEntity {

    @Id
    private String id;

    @Column(name = "game_id", nullable = false)
    private String gameId;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "period", nullable = false)
    private Integer period;

    @Column(name = "offense_team_id", nullable = false)
    private String offenseTeamId;

    @Column(name = "defense_team_id", nullable = false)
    private String defenseTeamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "play_type", nullable = false)
    private PlayType playType;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "primary_player_id")
    private String primaryPlayerId;

    /**
     * Optional assister on a made-FG SHOT event (§3.4 ball movement). Null on
     * unassisted makes and on every non-SHOT event. {@code BoxScore.assists}
     * reconciles against the count of SHOT events carrying this (decisions.md
     * #022 / #020 — events are the source of truth).
     */
    @Column(name = "assist_player_id")
    private String assistPlayerId;

}
