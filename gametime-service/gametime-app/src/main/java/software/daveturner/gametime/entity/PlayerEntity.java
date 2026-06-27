package software.daveturner.gametime.entity;

import jakarta.persistence.*;
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
    private Integer weight;
    private Integer yearsPro;
    private String draftSlot;
    private String origin;
    private String originDetails;

    private Integer agility;
    private Integer charisma;
    private Integer cohesion;
    private Integer determination;
    private Integer ego;
    private Integer endurance;
    private Integer energy;
    private Integer handle;
    private Integer health;
    private Integer intelligence;
    private Integer luck;
    private Integer shotSelection;
    private Integer shotSkill;
    private Integer size;
    private Integer speed;
    private Integer strength;

    private Integer verticality;
    private Integer wingspan;
    private Integer composure;
    private Integer aggression;
    private Integer awareness;

    // The player→team link lives in the player_team / player_team_hist tables,
    // not on the player. See PlayerTeamEntity. A free agent has no player_team row.

}
