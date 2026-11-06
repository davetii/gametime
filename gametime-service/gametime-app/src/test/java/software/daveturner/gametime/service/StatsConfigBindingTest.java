package software.daveturner.gametime.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StatsConfigBindingTest {

    @Autowired
    StatsConfig config;

    @Test
    void everyLeaderPropertyIsBound() {
        StatsConfig.Leaders l = config.leaders();
        assertEquals(0.70, l.minGamesFraction());
        assertEquals(3.66, l.minFgmPerGame());
        assertEquals(1.0, l.min3pmPerGame());
        assertEquals(1.52, l.minFtmPerGame());
        assertEquals(10, l.defaultLimit());
        assertEquals(100, l.maxLimit());
    }

    @EnableConfigurationProperties(StatsConfig.class)
    static class Bind {
    }

    @Test
    void aMissingPropertyFailsStartup() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(Bind.class)
                .withPropertyValues(
                        "stats.leaders.min-games-fraction=0.70",
                        "stats.leaders.min-fgm-per-game=3.66",
                        "stats.leaders.min-3pm-per-game=1.0",
                        "stats.leaders.default-limit=10",
                        "stats.leaders.max-limit=100")
                .run(ctx -> assertNotNull(ctx.getStartupFailure()));
    }
}
