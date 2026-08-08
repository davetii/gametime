package software.daveturner.gametime.sim;

/**
 * §3.10 (decisions.md #028 A2): a loose-ball / rebounding foul — which side
 * committed it and who. Rebounding fouls are <b>two-sided</b>: a defensive
 * box-out push fouls the offensive rebounder, an offensive over-the-back fouls
 * the defender. The committing side is therefore NOT recoverable from the
 * possession's offense/defense orientation the way a {@code SHOOTING_FOUL}'s is
 * (always the defender) — which is exactly why the committing team must be
 * carried explicitly, both here and onto the event
 * ({@code game_event.committing_team_id}, Decision D).
 *
 * <p>Produced by {@link FoulResolver#resolveReboundFoul} on the small slice of
 * misses carved off the top of the board contest (Decision C); {@code null}
 * there means no foul and the four-way {@link MissedShotResolver} draw runs as
 * usual.
 *
 * <p>The side drives the possession fork (Decision B): defense-committed leaves
 * the offense with the ball (retain, or bonus FTs), offense-committed ends the
 * possession for the offense (defense's ball, or bonus FTs for the defense).
 */
public record ReboundFoul(Side side, PlayerGameState committer) {

    /** Which side committed the foul. Defense-leaning in frequency (#028 A2). */
    public enum Side {
        /** Defensive box-out push — the offense is fouled. The dominant case. */
        DEFENSE("REBOUNDING_FOUL_DEFENSE"),
        /** Offensive over-the-back — the defense is fouled. The minority case. */
        OFFENSE("REBOUNDING_FOUL_OFFENSE");

        private final String outcome;

        Side(String outcome) {
            this.outcome = outcome;
        }

        /**
         * The {@code outcome} string on the {@link
         * software.daveturner.gametime.entity.PlayType#FOUL} event — the §3.8
         * {@code _OFFENSE}/{@code _DEFENSE} suffix discipline (#026 E), naming
         * the side that COMMITTED it. No new {@code PlayType}: a rebounding foul
         * is a kind of foul (#025 F / #026 E reuse discipline).
         */
        public String outcome() {
            return outcome;
        }

        /**
         * True when the OFFENSE committed — the possession ends for the offense
         * (an offensive foul is a turnover-like loss of the ball), so the
         * second-chance loop must never {@code continue} on it (#028 B).
         */
        public boolean endsPossession() {
            return this == OFFENSE;
        }
    }
}
