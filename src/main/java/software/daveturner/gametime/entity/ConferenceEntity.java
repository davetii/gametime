package software.daveturner.gametime.entity;

import jakarta.persistence.*;

import java.util.*;

@Entity
@Table(name = "conference")
@Cacheable
public class ConferenceEntity {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(nullable = false)
    private String name;

    @OneToMany(fetch = FetchType.LAZY, mappedBy="conference")
    private Set<TeamEntity> teams = new HashSet<>();

    public ConferenceEntity() { }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<TeamEntity> getTeams() {
        return teams;
    }

    public void setTeams(Set<TeamEntity> teams) {
        this.teams = teams;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ConferenceEntity that = (ConferenceEntity) o;

        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
