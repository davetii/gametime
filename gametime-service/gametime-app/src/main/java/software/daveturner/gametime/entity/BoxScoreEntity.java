package software.daveturner.gametime.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;

/**
 * Per-player stat line for a single game, one row per (game, player). Built from
 * the game_event log at game end (decisions.md #045 A); teamId is the team the
 * player played for in this game (#045 D).
 */
@Entity
@Table(name = "box_score", schema = "gametime")
@Data
public class BoxScoreEntity {

    @Id
    private String id;

    @Column(name = "game_id", nullable = false)
    private String gameId;

    @Column(name = "player_id", nullable = false)
    private String playerId;

    @Column(name = "team_id")
    private String teamId;

    @Column(name = "started")
    private Boolean started;

    @Column(name = "points")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer points;

    @Column(name = "offensive_rebounds")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer offensiveRebounds;

    @Column(name = "defensive_rebounds")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer defensiveRebounds;

    @Column(name = "assists")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer assists;

    @Column(name = "steals")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer steals;

    @Column(name = "blocks")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer blocks;

    @Column(name = "turnovers")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer turnovers;

    /** PERSONAL fouls only — technicals are counted separately below (#032 E). */
    @Column(name = "fouls")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer fouls;

    /**
     * §3.14a (decisions.md #032 E): technical fouls, kept apart from {@link #fouls}
     * because a technical does not count toward the six-foul disqualification.
     */
    @Column(name = "technical_fouls")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer technicalFouls;

    @Column(name = "minutes")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer minutes;

    @Column(name = "field_goals_attempted")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer fieldGoalsAttempted;

    @Column(name = "field_goals_made")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer fieldGoalsMade;

    @Column(name = "three_pointers_attempted")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer threePointersAttempted;

    @Column(name = "three_pointers_made")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer threePointersMade;

    @Column(name = "free_throws_attempted")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer freeThrowsAttempted;

    @Column(name = "free_throws_made")
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer freeThrowsMade;

}
