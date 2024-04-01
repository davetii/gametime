package software.daveturner.gametime.entity;

import com.fasterxml.jackson.annotation.*;
import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "team")
@Cacheable
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

    public TeamEntity() { }

    public void setId(String id) {
        this.id = id;
    }
    public String getId() {
        return id;
    }

    public String getLocale() {
        return locale;
    }
    public void setLocale(String locale) {
        this.locale = locale;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public GMEntity getGm() { return gm; }
    public void setGm(GMEntity gm) { this.gm = gm; }

    public String getConference() {
        return conference;
    }

    public void setConference(String conference) {
        this.conference = conference;
    }


    public List<PlayerEntity> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerEntity> players) {
        this.players = players;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TeamEntity team = (TeamEntity) o;
        return id.equals(team.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "Team{" +
                "locale='" + locale + '\'' +
                ", name='" + name + '\'' +
                ", id='" + id + '\'' +
                ", coach=" + coach +
                '}';
    }

}
