package software.daveturner.gametime.entity;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "player")
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


    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name="team_id", nullable=true)
    private TeamEntity team;

}
