package software.daveturner.gametime.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.*;

/**
 * Append-only history of roster transactions (assignments). Rows are never
 * updated or deleted — every move appends a new row.
 */
@Entity
@Table(name = "player_team_hist", schema = "gametime")
@Data
public class PlayerTeamHistEntity {

    @Id
    private String id;

    @Column(name = "player_id", nullable = false)
    private String playerId;

    @Column(name = "team_id", nullable = false)
    private String teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

}
