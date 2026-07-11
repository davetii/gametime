package software.daveturner.gametime.sim;

import java.util.List;

/**
 * Everything the possession engine needs to know about one team for a game: its
 * rotation (the full squad + who is on the floor) and its coach's modifiers
 * (decisions.md #022/#023). Bundling these into one value object means engine
 * effects can be added without re-churning {@link PossessionEngine}'s method
 * signatures (the §3.3 lesson — a single structural change, not a per-effect
 * parameter).
 *
 * <p>§3.5 made the on-floor five <b>dynamic</b>: the record now holds a mutable
 * {@link RotationState} (a record may hold a reference to a mutable object) rather
 * than a fixed player list. {@link #onFloor()} returns the current five, which the
 * substitution step mutates between possessions — so the engine reads
 * {@code ctx.onFloor()} at the single seam and every downstream pick follows.
 *
 * @param teamId    the team's id
 * @param rotation  the team's rotation (full squad + current on-floor five)
 * @param modifiers the coach's multipliers (neutral if no coach)
 */
public record TeamContext(String teamId,
                          RotationState rotation,
                          CoachModifiers modifiers) {

    /** The current on-floor five (always exactly 5) — the single seam §3.5 reads. */
    public List<PlayerGameState> onFloor() {
        return rotation.onFloor();
    }
}
