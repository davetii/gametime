package software.daveturner.gametime.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One possession-by-possession event in a game's log. Every event is persisted
 * (decisions.md #020). {@code sequence} is monotonic across the WHOLE game and
 * does not restart per period, so play-by-play is a single ordered read. The
 * shape is intentionally minimal (one primary player, free-text outcome) and
 * grows additively with the §3.2 possession engine.
 */
@Entity
@Table(name = "game_event", schema = "gametime")
@Data
public class GameEventEntity {

    @Id
    private String id;

    @Column(name = "game_id", nullable = false)
    private String gameId;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "period", nullable = false)
    private Integer period;

    @Column(name = "offense_team_id", nullable = false)
    private String offenseTeamId;

    @Column(name = "defense_team_id", nullable = false)
    private String defenseTeamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "play_type", nullable = false)
    private PlayType playType;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "primary_player_id")
    private String primaryPlayerId;

    /**
     * Optional assister on a made-FG SHOT event (§3.4 ball movement). Null on
     * unassisted makes and on every non-SHOT event. {@code BoxScore.assists}
     * reconciles against the count of SHOT events carrying this (decisions.md
     * #022 / #020 — events are the source of truth).
     *
     * <p>⚠ This is the TEAMMATE slot, and it is deliberately NOT the same column as
     * {@link #opponentPlayerId} (§3.18, #041 D — a migration was pursued and
     * REVERSED). The two hold different KINDS of fact: an assister collaborates
     * with {@link #primaryPlayerId} and is on the SAME team; a counterparty opposes
     * them and is always on the OTHER team. Folding assists in would break the
     * counterparty invariant with its one same-team exception, and would force a
     * reader to decode {@code outcome} to answer "which team is this player on?".
     * {@code assistPlayerId} is NOT deprecated and has no removal phase.
     */
    @Column(name = "assist_player_id")
    private String assistPlayerId;

    /**
     * §3.10 (decisions.md #028 D): which team COMMITTED this event. Set on every
     * FOUL event — {@code SHOOTING_FOUL} carries the defender's team, and the
     * two-sided {@code REBOUNDING_FOUL_*} carries whichever side the roll picked
     * (a rebounding foul can be an OFFENSIVE over-the-back, so the committer is
     * NOT implied by {@link #defenseTeamId}). Null on every non-foul event. The
     * day-one consumer is the derived penalty/bonus predicate: count FOUL events
     * by committing team within a period (#028 A1 — derived from the log, never a
     * stored counter).
     */
    @Column(name = "committing_team_id")
    private String committingTeamId;

    /**
     * §3.18 (decisions.md #041 A): the COUNTERPARTY — the player on the OTHER SIDE
     * of this play from {@link #primaryPlayerId}.
     *
     * <p><b>The contract, and the whole reason a generic column is safe: whoever is
     * here is ALWAYS on the opposite team from {@code primaryPlayerId}.</b> A reader
     * therefore resolves their team as "whichever of {@link #offenseTeamId} /
     * {@link #defenseTeamId} primary is not on", with no need to decode {@code
     * outcome}. A TEAMMATE never goes here — an assister rides
     * {@link #assistPlayerId} (#041 D).
     *
     * <p>Populated at the three sites where the engine already held the second
     * participant in scope and discarded them:
     * <ul>
     *   <li>{@code TURNOVER} / {@code STOLEN} — the STEALER (the §3.9 {@code
     *       pickStealer} draw, credited by {@code recordSteal()}); primary is the
     *       ball-loser.</li>
     *   <li>{@code SHOT} / {@code BLOCKED_*} — the BLOCKER (credited by {@code
     *       recordBlock()}, §3.7 #025 F2); primary is the shooter (the victim).</li>
     *   <li>{@code FOUL} / {@code SHOOTING_FOUL} — the FOULED SHOOTER; primary is
     *       the defender who committed it.</li>
     * </ul>
     *
     * <p>Null on every other event. ⚠ Deliberately null — not overlooked — on
     * {@code OFFENSIVE_FOUL} (the charge): the drawer is not modelled, and picking
     * one would need a new RNG draw (#041 follow-up).
     *
     * <p>Day-one consumer: the PER-CREDITOR reconciliation (#041 G) — {@code
     * count(TURNOVER/STOLEN with opponentPlayerId = X) == box_score.steals(X)} for
     * every X, where the old total-count check passed even when the engine credited
     * the wrong player.
     */
    @Column(name = "opponent_player_id")
    private String opponentPlayerId;

}
