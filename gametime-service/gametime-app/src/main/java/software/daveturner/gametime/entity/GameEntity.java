package software.daveturner.gametime.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * A single game: the matchup and its outcome. References home/away team only —
 * there is no season/schedule table yet (Phase 5), so no season FK
 * (decisions.md #020). The engine that fills this lands in §3.2+.
 */
@Entity
@Table(name = "game", schema = "gametime")
@Data
public class GameEntity {

    @Id
    private String id;

    @Column(name = "home_team_id", nullable = false)
    private String homeTeamId;

    @Column(name = "away_team_id", nullable = false)
    private String awayTeamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private GameStatus status;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(name = "periods")
    private Integer periods;

}
