package software.daveturner.gametime.entity;

/**
 * The kind of event a {@link GameEventEntity} records. Intentionally small —
 * grows additively with the §3.2 possession engine (decisions.md #020).
 */
public enum PlayType {
    SHOT, TURNOVER, REBOUND, FOUL, FREE_THROW
}
