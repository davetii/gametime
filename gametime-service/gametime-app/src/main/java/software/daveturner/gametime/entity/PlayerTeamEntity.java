package software.daveturner.gametime.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.*;

/**
 * Current roster assignment: at most one team per player (player_id is the PK).
 * A free agent simply has no row here.
 */
@Entity
@Table(name = "player_team", schema = "gametime")
@Data
public class PlayerTeamEntity {

    @Id
    @Column(name = "player_id")
    private String playerId;

    @Column(name = "team_id", nullable = false)
    private String teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "assigned_date")
    private LocalDateTime assignedDate;

}
