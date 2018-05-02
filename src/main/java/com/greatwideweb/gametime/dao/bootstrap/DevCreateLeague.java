package com.greatwideweb.gametime.dao.bootstrap;

import com.greatwideweb.gametime.dao.model.ConferenceData;
import com.greatwideweb.gametime.dao.model.TeamData;
import com.greatwideweb.gametime.dao.repo.ConferenceRepo;
import com.greatwideweb.gametime.dao.repo.PlayerRepo;
import com.greatwideweb.gametime.dao.repo.TeamRepo;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class DevCreateLeague implements ApplicationListener<ContextRefreshedEvent> {

    private ConferenceRepo conferenceRepo;
    private TeamRepo teamRepo;
    private PlayerRepo playerRepo;

    public DevCreateLeague(ConferenceRepo conferenceRepo, TeamRepo teamRepo, PlayerRepo playerRepo) {
        this.conferenceRepo = conferenceRepo;
        this.teamRepo = teamRepo;
        this.playerRepo = playerRepo;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent) {
        initData();
    }

    private void initData() {
        ConferenceData east = new ConferenceData("East");
        ConferenceData north = new ConferenceData("North");
        ConferenceData south = new ConferenceData("South");
        ConferenceData west = new ConferenceData("West");
        this.conferenceRepo.save(east);
        this.conferenceRepo.save(north);
        this.conferenceRepo.save(south);
        this.conferenceRepo.save(west);

        TeamData newYork = new TeamData(east, "New York", "Fastbacks");
        TeamData philadelphia = new TeamData(east, "Philadelphia", "Flames");
        this.teamRepo.save(newYork);
        this.teamRepo.save(philadelphia);
    }

}
