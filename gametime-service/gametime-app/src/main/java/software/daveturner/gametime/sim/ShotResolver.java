package software.daveturner.gametime.sim;

import org.springframework.stereotype.Component;

import java.util.random.RandomGenerator;

@Component
public class ShotResolver {

    private final SimConfig config;

    public ShotResolver(SimConfig config) {
        this.config = config;
    }

    public boolean isMade(ShotType shotType, PlayerGameState shooter,
                          PlayerGameState defender, RandomGenerator rng) {
        return isMade(shotType, shooter, defender, 1.0, rng);
    }

    /**
     * §3.7 (decisions.md #025 A1, B2): does the defender block the shot? Rolled
     * BEFORE the make/miss contest — {@code P(BLOCK)} is carved off the top of the
     * shot outcome (Decision A1), and the make contest then runs on the remainder.
     * A defender-vs-finisher contest ({@link SimConfig#blockProbability}): the
     * defender's block skill ({@code rimProtection} at the rim / {@code shotContest}
     * on jumpers) against the shooter's {@code finishing}, per-shot-type base rate,
     * each side scaled by fatigue (a tired defender contests worse, a tired shooter
     * finishes worse — same thumb-on-scale discipline as {@link #isMade}). Full
     * energy ⇒ ×1.0.
     */
    public boolean isBlocked(ShotType shotType, PlayerGameState shooter,
                             PlayerGameState defender, RandomGenerator rng) {
        double defenderSkill = defenderBlockSkillForShot(shotType, defender)
                * defender.fatigueFactor();
        double shooterFinishing = shooter.getFinishing() * shooter.fatigueFactor();
        double base = baseBlockProbability(shotType);
        double prob = config.blockProbability(base, defenderSkill, shooterFinishing);
        return rng.nextDouble() < prob;
    }

    /**
     * §3.4 (Decision C): {@code chemistryMultiplier} bends the contested make
     * probability — the shooter's {@code acumen} (shot-quality nudge) and the
     * team-efficiency edge ({@code teamOffense} − opponent {@code teamDefense}),
     * combined in {@link SimConfig#chemistryMakeMultiplier}. 1.0 = the raw
     * player-skill contest (a modest thumb on the scale, not a replacement).
     */
    public boolean isMade(ShotType shotType, PlayerGameState shooter,
                          PlayerGameState defender, double chemistryMultiplier,
                          RandomGenerator rng) {
        // §3.5: fatigue is a modest multiplier over each contestant's skill —
        // a tired shooter finishes worse, a tired defender contests worse. Composes
        // multiplicatively with the §3.4 chemistryMultiplier (same thumb-on-scale
        // discipline). Full energy ⇒ ×1.0 (no change to §3.4 behavior).
        double offense = shooter.offenseSkillForShot(shotType) * shooter.fatigueFactor();
        double defense = defenseSkillForShot(shotType, defender) * defender.fatigueFactor();
        double base = baseProbability(shotType);
        double prob = config.clampProbability(
                chemistryMultiplier * config.contestProbability(base, offense, defense));
        return rng.nextDouble() < prob;
    }

    double baseProbability(ShotType shotType) {
        return switch (shotType) {
            case DRIVE -> SimConfig.BASE_DRIVE;
            case PERIMETER -> SimConfig.BASE_PERIMETER;
            case POST -> SimConfig.BASE_POST;
            case THREE -> SimConfig.BASE_THREE;
        };
    }

    double defenseSkillForShot(ShotType shotType, PlayerGameState defender) {
        return switch (shotType) {
            case DRIVE -> defender.getRimProtection();
            case PERIMETER -> (defender.getIndividualDefense() + defender.getShotContest()) / 2.0;
            case POST -> defender.getIndividualDefense();
            case THREE -> defender.getShotContest();
        };
    }

    double baseBlockProbability(ShotType shotType) {
        return switch (shotType) {
            case DRIVE -> SimConfig.BASE_BLOCK_DRIVE;
            case PERIMETER -> SimConfig.BASE_BLOCK_PERIMETER;
            case POST -> SimConfig.BASE_BLOCK_POST;
            case THREE -> SimConfig.BASE_BLOCK_THREE;
        };
    }

    /**
     * §3.7 (decisions.md #025 B2): the defender's block skill for a shot type —
     * {@code rimProtection} at the rim (DRIVE/POST), {@code shotContest} on jumpers
     * (PERIMETER/THREE). Distinct from {@link #defenseSkillForShot} (the make
     * contest): a rim protector is felt twice — he blocks more shots AND makes the
     * ones he doesn't block harder — which is realistic (Decision B entanglement).
     */
    double defenderBlockSkillForShot(ShotType shotType, PlayerGameState defender) {
        return switch (shotType) {
            case DRIVE, POST -> defender.getRimProtection();
            case PERIMETER, THREE -> defender.getShotContest();
        };
    }
}
