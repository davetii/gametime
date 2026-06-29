package software.daveturner.gametime.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Per-player stat line for a single game, one row per (game, player). No
 * team_id: the player's team is derivable from the game + player_team, so
 * storing it here would duplicate that fact (decisions.md #020, cf. #013/#015).
 * Accumulated during simulation and reconciled against the game_event log.
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

    @Column(name = "points")
    private Integer points;

    @Column(name = "offensive_rebounds")
    private Integer offensiveRebounds;

    @Column(name = "defensive_rebounds")
    private Integer defensiveRebounds;

    @Column(name = "assists")
    private Integer assists;

    @Column(name = "steals")
    private Integer steals;

    @Column(name = "blocks")
    private Integer blocks;

    @Column(name = "turnovers")
    private Integer turnovers;

    @Column(name = "fouls")
    private Integer fouls;

    @Column(name = "minutes")
    private Integer minutes;

    @Column(name = "field_goals_attempted")
    private Integer fieldGoalsAttempted;

    @Column(name = "field_goals_made")
    private Integer fieldGoalsMade;

    @Column(name = "three_pointers_attempted")
    private Integer threePointersAttempted;

    @Column(name = "three_pointers_made")
    private Integer threePointersMade;

    @Column(name = "free_throws_attempted")
    private Integer freeThrowsAttempted;

    @Column(name = "free_throws_made")
    private Integer freeThrowsMade;

}
