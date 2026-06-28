package software.daveturner.gametime.entity;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "coach", schema = "gametime")
@Data
public class CoachEntity {

    public CoachEntity() { }

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    // Decision-making attributes (1-20, avg 10) read by the Phase 3 engine.
    // Modifiers on a baseline, not absolutes (decisions.md #018, coach.md).
    @Column(name = "pace")
    private Integer pace;

    @Column(name = "offensive_scheme")
    private Integer offensiveScheme;

    @Column(name = "defensive_scheme")
    private Integer defensiveScheme;

    @Column(name = "rotation_depth")
    private Integer rotationDepth;

    @Column(name = "substitution_aggressiveness")
    private Integer substitutionAggressiveness;

    @JsonBackReference
    @OneToOne(cascade = CascadeType.ALL, mappedBy = "coach")
    private TeamEntity team;

    public TeamEntity getTeam() {
        return team;
    }
    public void setTeam(TeamEntity team) {
        this.team = team;
    }

    @Id
    @Column(name = "id", nullable = false)
    private String id;
}
