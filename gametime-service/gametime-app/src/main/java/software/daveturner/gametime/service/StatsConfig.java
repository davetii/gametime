package software.daveturner.gametime.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Leaderboard qualifiers (decisions.md #045 J). Values live only in application.properties. */
@ConfigurationProperties(prefix = "stats")
@Validated
public record StatsConfig(@NotNull @Valid Leaders leaders) {

    public record Leaders(@NotNull Double minGamesFraction,
                          @NotNull Double minFgmPerGame,
                          @NotNull Double min3pmPerGame,
                          @NotNull Double minFtmPerGame,
                          @NotNull Integer defaultLimit,
                          @NotNull Integer maxLimit) {
    }
}
