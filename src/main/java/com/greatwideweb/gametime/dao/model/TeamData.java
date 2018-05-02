package com.greatwideweb.gametime.dao.model;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "team")
public class TeamData {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String location;
    private String logo;
    private String logoImage;
    private String logoThumb;
    private String arena;

    @ManyToOne
    @JoinColumn(name = "conference_id")
    private ConferenceData conferenceData;

    @OneToMany(mappedBy = "team")
    private Set<PlayerData> players = new HashSet<>();

    public TeamData(ConferenceData conferenceData, String location, String logo) {
        this.location = location;
        this.logo = logo;
        this.conferenceData = conferenceData;
    }

    public TeamData() { }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }

    public String getLogoImage() { return logoImage; }
    public void setLogoImage(String logoImage) { this.logoImage = logoImage; }

    public String getLogoThumb() { return logoThumb; }
    public void setLogoThumb(String logoThumb) { this.logoThumb = logoThumb; }

    public ConferenceData getConferenceData() { return conferenceData; }
    public void setConferenceData(ConferenceData conferenceData) { this.conferenceData = conferenceData; }

    public String getArena() { return arena; }
    public void setArena(String arena) { this.arena = arena; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TeamData teamData = (TeamData) o;

        return id != null ? id.equals(teamData.id) : teamData.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}
