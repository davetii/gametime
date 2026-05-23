package software.daveturner.gametime.entity;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Entity
@Table(name = "coach")
@Data
public class CoachEntity {

    public CoachEntity() { }

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

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
