package software.daveturner.gametime.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "team", schema = "gametime")
@Cacheable
@Data
public class TeamEntity {
    @Column(nullable = false)
    private String locale;
    @Column(nullable = false)
    private String name;

    @Id
    @Column(name = "id", nullable = false)
    private String id;


    @Column(name = "conference", nullable = false)
    private String conference;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "coach_id", referencedColumnName = "id")
    private CoachEntity coach;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "gm_id", referencedColumnName = "id")
    private GMEntity gm;

    public CoachEntity getCoach() {
        return coach;
    }
    public void setCoach(CoachEntity coach) {
        this.coach = coach;
    }

    // Roster is no longer a JPA association on the team. The player→team link
    // lives in player_team; the service sources a team's roster via
    // PlayerTeamRepo.findByTeamId and the EntityMapper composes it.

}
