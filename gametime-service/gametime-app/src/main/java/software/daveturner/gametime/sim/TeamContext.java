package software.daveturner.gametime.sim;

import java.util.List;

/**
 * Everything the possession engine needs to know about one team for a game: its
 * on-floor players and its coach's modifiers (decisions.md #022). Bundling these
 * into one value object means future coach effects can be added to the engine
 * without re-churning {@link PossessionEngine}'s method signatures (the §3.3
 * lesson — a single structural change, not a per-effect parameter).
 *
 * @param teamId    the team's id
 * @param players   the five on-floor players (starters in §3.4 — no subs yet)
 * @param modifiers the coach's Decision-A multipliers (neutral if no coach)
 */
public record TeamContext(String teamId,
                          List<PlayerGameState> players,
                          CoachModifiers modifiers) {
}
