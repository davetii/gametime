package software.daveturner.gametime.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.*;

@Entity
@Table(name = "player", schema = "gametime")
@Cacheable
@Data
public class PlayerEntity {

    @Id
    private String id;
    private String firstName;
    private String lastName;

    @Enumerated(EnumType.STRING)
    private Position position;

    @Enumerated(EnumType.STRING)
    private Status status;


    private String height;
    // VARCHAR column; the API's weight is an integer and EntityMapper converts.
    private String weight;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer yearsPro;
    private String draftSlot;
    private String origin;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer agility;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer charisma;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer cohesion;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer determination;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer ego;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer endurance;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer energy;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer handle;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer health;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer intelligence;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer luck;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer shotSelection;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer shotSkill;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer size;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer speed;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer strength;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer verticality;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer wingspan;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer composure;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer aggression;
    @JdbcTypeCode(SqlTypes.SMALLINT)
    private Integer awareness;

    // The player→team link lives in the player_team / player_team_hist tables,
    // not on the player. See PlayerTeamEntity. A free agent has no player_team row.

}
