package software.daveturner.gametime.exception;

/**
 * A well-formed request that violates a business rule the server understood —
 * mapped to 422 Unprocessable Entity (#024 F). First raised by the same-team
 * guard on {@code POST /v1/game/simulate} (homeTeamId == awayTeamId): valid
 * JSON, both ids resolve, but a cross-field rule rejects it — distinct from 400
 * "malformed", 404 "missing", and 409 "state conflict".
 */
public class ResourceUnprocessableException extends RuntimeException {
    public ResourceUnprocessableException() {
        super("ResourceUnprocessableException");
    }
}
