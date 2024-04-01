package software.daveturner.gametime.entity;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Entity
@Table(name = "team")
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

    @OneToMany(fetch = FetchType.LAZY, mappedBy="team")
    private List<PlayerEntity> players = new ArrayList<>();


}
