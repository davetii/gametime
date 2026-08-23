package software.daveturner.gametime.sim;

import software.daveturner.gametime.model.LineupRole;
import software.daveturner.gametime.model.Player;
import software.daveturner.gametime.model.PlayerSkills;
import software.daveturner.gametime.model.RosterEntry;

import java.math.BigDecimal;

public class PlayerGameState {

    private final String playerId;
    private final String teamId;

    // Rotation identity (§3.5): the roster slot + bench-queue position drive
    // substitution priority (starters pulled later / return first) and the order
    // bench players enter. STARTER carries a null rotationOrder (#014).
    private final boolean starter;
    private final Integer rotationOrder;

    // Fatigue attributes (§3.5): endurance slows the per-possession energy drain;
    // energy seeds a player's starting in-game currentEnergy above/below full.
    private final double endurance;
    private final double energy;

    // Offensive skills (converted to double at engine boundary)
    private final double drive;
    private final double finishing;
    private final double perimeter;
    private final double post;
    private final double longRange;
    private final double ballSecurity;
    private final double freeThrows;
    private final double foulDrawing;

    // Defensive skills
    private final double individualDefense;
    private final double rimProtection;
    private final double shotContest;
    private final double stealing;
    private final double foulProne;

    // Rebounding skills (§3.3)
    private final double offenseRebound;
    private final double defenseRebound;

    // Team chemistry skills (§3.4)
    private final double teamOffense;
    private final double teamDefense;
    private final double passing;
    private final double acumen;

    // Box score accumulators
    private int points;
    private int fieldGoalsAttempted;
    private int fieldGoalsMade;
    private int threePointersAttempted;
    private int threePointersMade;
    private int freeThrowsAttempted;
    private int freeThrowsMade;
    private int turnovers;
    private int steals;
    private int blocks;
    private int fouls;
    // §3.14a (decisions.md #032 E): technicals are counted SEPARATELY and do NOT
    // feed the 6-foul disqualification — that is the NBA rule, and keeping them
    // apart is what preserves `fouls` meaning exactly "personal fouls" for
    // isFouledOut(), foulTroubleLevel() and the §3.10 penalty derivation. Merging
    // the two counters would have silently pushed players toward foul-outs and into
    // §3.13's foul-trouble bench curve, moving two calibrated numbers (foul-outs
    // ~0.39 and the 4/5/6 distribution) for an unrelated cause.
    private int technicalFouls;
    // §3.14b (decisions.md #034 F/I): FLAGRANT-2 fouls — the count of EJECTION
    // CAUSES, not a parallel foul counter. The flagrant itself is already in `fouls`
    // via recordFoul() (a flagrant IS a personal foul, unlike §3.14a's technical), so
    // summing this with `fouls` DOUBLE-COUNTS — the #033 D trap in reverse.
    private int flagrantTwos;
    private int offensiveRebounds;
    private int defensiveRebounds;
    private int assists;

    // Fatigue / minutes state (§3.5). currentEnergy drains on-floor and recovers
    // benched; onFloorPossessions accumulates the possession share that maps to
    // minutes at box-score-write time (Decision A). Within-game only — resets each
    // game because a fresh PlayerGameState is built per simulation.
    private double currentEnergy;
    private int onFloorPossessions;

    // §3.20: DID THIS PLAYER EVER TAKE THE FLOOR? Deliberately SEPARATE from
    // onFloorPossessions, which is the minutes DENOMINATOR and must not be inflated
    // to answer a different question.
    //
    // ⚠ THE TWO ARE NOT THE SAME FACT, and assuming they were dropped a scoring
    // player from the box score entirely. RotationState.advancePossession drains
    // (incrementing onFloorPossessions) for the five on the floor at the TOP of the
    // call and substitutes AFTERWARDS, so a substituted-in player can take the
    // floor, score, and still hold a count of 0 — at which point GameSimulator's
    // "never checked in" filter skipped his row and his points vanished from the
    // box score. Found by §3.19's points identity (events = box = final), the only
    // instrument that catches it; no test failed.
    //
    // Starters are seeded true at construction of the rotation; a substitution sets
    // it on the player coming in. It never resets — "took the floor" is monotonic.
    private boolean tookFloor;

    // This class reads BOTH kinds of SimConfig constant: the tunable ones through
    // this field, and SCALE_AVG / MAX_ENERGY / the ejection limits as statics below.
    private final SimConfig config;

    public PlayerGameState(String playerId, String teamId, RosterEntry entry, SimConfig config) {
        this.playerId = playerId;
        this.teamId = teamId;
        this.config = config;
        Player player = entry.getPlayer();
        // A null lineupRole ⇒ not a starter (the correct default). In production
        // GameSimulator.buildRotation only ever passes STARTER entries or bench
        // entries with a rotationOrder, so the null path is test-only.
        this.starter = entry.getLineupRole() == LineupRole.STARTER;
        this.rotationOrder = entry.getRotationOrder();
        this.endurance = attrToDouble(player.getEndurance());
        this.energy = attrToDouble(player.getEnergy());
        // Everyone tips off with a FULL tank (Decision B: within-game fatigue only,
        // energy resets each game — so all players start equally fresh). The two
        // attributes act per possession, NOT at start: endurance slows the on-floor
        // drain, energy speeds the benched recovery. (This is a deliberate deviation
        // from Decision B's "starting energy derived from endurance/energy" wording
        // — a flat start keeps ×1.0 = a full tank for everyone; see decisions.md
        // #023 Decision B note.)
        this.currentEnergy = SimConfig.MAX_ENERGY;
        PlayerSkills skills = player.getSkills();
        this.drive = toDouble(skills.getDrive());
        this.finishing = toDouble(skills.getFinishing());
        this.perimeter = toDouble(skills.getPerimeter());
        this.post = toDouble(skills.getPost());
        this.longRange = toDouble(skills.getLongRange());
        this.ballSecurity = toDouble(skills.getBallSecurity());
        this.freeThrows = toDouble(skills.getFreeThrows());
        this.foulDrawing = toDouble(skills.getFoulDrawing());
        this.individualDefense = toDouble(skills.getIndividualDefense());
        this.rimProtection = toDouble(skills.getRimProtection());
        this.shotContest = toDouble(skills.getShotContest());
        this.stealing = toDouble(skills.getStealing());
        this.foulProne = toDouble(skills.getFoulProne());
        this.offenseRebound = toDouble(skills.getOffenseRebound());
        this.defenseRebound = toDouble(skills.getDefenseRebound());
        this.teamOffense = toDouble(skills.getTeamOffense());
        this.teamDefense = toDouble(skills.getTeamDefense());
        this.passing = toDouble(skills.getPassing());
        this.acumen = toDouble(skills.getAcumen());
    }

    private static double toDouble(BigDecimal bd) {
        return bd == null ? SimConfig.SCALE_AVG : bd.doubleValue();
    }

    // endurance/energy are Integer *attributes* on Player (not skills); null-safe
    // to league average (10), matching the skill toDouble discipline.
    private static double attrToDouble(Integer attr) {
        return attr == null ? SimConfig.SCALE_AVG : attr.doubleValue();
    }

    // --- Fatigue / rotation state (§3.5) ---

    public boolean isStarter() { return starter; }
    public Integer getRotationOrder() { return rotationOrder; }
    public double getEndurance() { return endurance; }
    public double getEnergy() { return energy; }
    public double getCurrentEnergy() { return currentEnergy; }
    public int getOnFloorPossessions() { return onFloorPossessions; }

    /**
     * §3.20: true once this player has been on the floor at all — whether or not a
     * possession was ever drained against him. <b>The box-score predicate</b>: a
     * player who took the floor gets a row even if {@link #getOnFloorPossessions()}
     * is 0, because he may have scored on the possession he was substituted into.
     */
    public boolean tookFloor() { return tookFloor; }

    /** Mark this player as having taken the floor (starter, or substituted in). */
    public void markTookFloor() { this.tookFloor = true; }

    /**
     * A player is fouled out (Decision F) once their foul count reaches
     * {@link SimConfig#FOUL_OUT_LIMIT}. A derived predicate over the existing
     * counter — no stored flag; permanent for the game because fouls only grow.
     */
    public boolean isFouledOut() {
        return fouls >= config.foulOutLimit();
    }

    /**
     * §3.14a (decisions.md #032 F): this player has been <b>ejected</b> — two
     * technical fouls in one game is an automatic ejection. Like {@link
     * #isFouledOut()} this is a <b>derived predicate over a monotonic counter</b>,
     * with no stored flag: #023 F's derive-don't-store discipline applies here
     * unchanged, and permanence is free because {@code technicalFouls} only grows.
     *
     * <p><b>This is the finding that shaped §3.14a.</b> #031 H, roadmap.md and
     * todo.md all predicted §3.14 would need real stored state, on the reasoning that
     * "an ejection is not derivable from a counter the way {@code fouls >= 6} is."
     * That is <b>false of the two-technical case</b>, which is structurally identical
     * to the foul-out, so the #023 F exception was NOT taken here.
     *
     * <p><b>§3.14b UPDATE — it was not taken there either, and this entry's original
     * prediction was wrong.</b> §3.14a wrote that the exception "moves to §3.14b,
     * where the flagrant-2 genuinely forces it", on the reasoning that a flagrant-2 is
     * "a severity grade with no counter behind it". It has one: a flagrant-2 ejects
     * <b>immediately</b>, so there is no accumulation threshold to remember and {@link
     * #isEjectedForFlagrant()} derives from {@code flagrantTwos >= 1} exactly as this
     * predicate derives from {@code technicalFouls >= 2} (#034 F). The prediction is
     * <b>retired</b>, not deferred again.
     *
     * <p>{@link RotationState#eligible} filters on {@code isFouledOut() ||
     * isEjected() || isEjectedForFlagrant()} — one filter, the <b>hard/forced</b>
     * tier, not a fourth removal path (#031 H).
     *
     * <p><b>Expect this to fire essentially never.</b> At ~0.35 technicals per
     * team-game spread over five players, two on the same player in one game is on
     * the order of one occurrence every several simulated seasons. The harness
     * reading 0.00 ejections is a <b>correct result, not a failure</b> — which is why
     * the tests force the counter directly rather than waiting for the event.
     */
    public boolean isEjected() {
        return technicalFouls >= SimConfig.TECHNICAL_EJECTION_LIMIT;
    }

    /**
     * §3.14b (decisions.md #034 F): this player has been <b>ejected for a flagrant-2</b>
     * — the third disqualification cause, and the third <b>derived predicate over a
     * monotonic counter</b> in this class. No stored flag: #023 F's derive-don't-store
     * discipline applies here unchanged.
     *
     * <p><b>This reverses three shipped documents, and the reversal is §3.14b's most
     * useful finding.</b> #031 H, roadmap.md and {@link #isEjected()}'s own javadoc
     * (following #032 F) all predicted that the flagrant-2 is where stored state
     * finally becomes unavoidable, because it is "a severity grade with no counter
     * behind it, so it is not derivable". <b>That is wrong.</b> A flagrant-2 ejects
     * <b>immediately and permanently</b> — one is enough — so there is no accumulation
     * threshold to remember, and the fact is captured by a counter that starts at 0 and
     * only grows. The limit being 1 rather than 6 or 2 changes nothing about the shape.
     *
     * <p><b>The distinction from the {@code inFoulTrouble} flag #031 E refused</b>, and
     * the test to apply if the exception is ever predicted again: that flag's underlying
     * fact was <b>non-monotonic</b> (a foul-troubled player can be benched, recover,
     * return, and be benched again), so it would have needed invalidation logic and
     * would have drifted. A disqualification is the opposite — it is <b>absorbing</b>.
     * Once true it stays true, so a counter can never disagree with a flag, which makes
     * the flag nothing but a second thing to keep in sync (the #013/#015 duplicate-source
     * trap). <b>Every disqualification this model has is that shape.</b>
     *
     * <p>Feeds {@link RotationState}'s {@code isDisqualified(...)} as a <b>third
     * cause</b> behind the single {@code eligible(...)} filter — not a fourth removal
     * path (#031 H, held for the third pass running).
     *
     * <p><b>Unlike {@link #isEjected()}, this one actually fires.</b> At ~0.16
     * flagrants × 15% it lands around <b>0.024 per team per game</b> — roughly double
     * §3.14a's measured 0.014 — so §3.14b is the pass where the hard tier is genuinely
     * exercised. Still far too rare to tune against; the forced-counter unit tests
     * remain the validation (#032 F's discipline).
     */
    public boolean isEjectedForFlagrant() {
        return flagrantTwos >= SimConfig.FLAGRANT_EJECTION_LIMIT;
    }

    /**
     * §3.13 (decisions.md #031 E): how deep in foul trouble this player is — a
     * <b>pure question about the player</b>, derived fresh from the existing {@code
     * fouls} counter, with no stored {@code inFoulTrouble} flag (the #013/#015
     * duplicate-source trap, refused the same way {@link #isFouledOut()} refuses it).
     *
     * <p>Today this is simply the foul count, capped at {@link
     * SimConfig#FOUL_OUT_LIMIT}; it exists as a named accessor because it is a
     * different <i>question</i> from "how many fouls has he committed" (a box-score
     * fact) — this one is the rotation's input, and a later pass may curve it.
     *
     * <p><b>Unlike {@link #isFouledOut()}, the RESPONSE to this is not monotonic.</b>
     * A fouled-out player is gone for good, but a foul-troubled player can be
     * benched, recover energy, return, and be benched again — which is exactly what
     * #031 D's sticky-sit / earned-return cycle relies on. The predicate itself is
     * stable and only grows; it is the rotation's <i>decision</i> that is re-made
     * every check (~100 times per team per game).
     *
     * <p>Deliberately NOT a {@code shouldBench()}: that would need the coach factor
     * and a {@code RandomGenerator}, and this class is (and stays) RNG-free and
     * coach-free. The decision lives in {@link RotationState}.
     */
    public int foulTroubleLevel() {
        return Math.min(fouls, config.foulOutLimit());
    }

    /**
     * §3.13 (decisions.md #031 B): this player's <b>value to his team</b>, on the
     * same 1–20 avg-10 scale as the skills it is built from — the input that makes
     * the foul-trouble rule protect a star more tightly than a role player.
     *
     * <pre>
     *   value = (individualDefense + rimProtection + defenseRebound
     *            + offense + offense) / 5
     *   where offense = mean(drive, finishing, perimeter, post, longRange)
     * </pre>
     *
     * <p>Offense is <b>deliberately double-weighted</b>, putting the composite at
     * roughly 60/40 defense-leaning — the right lean for a foul-trouble rule, since
     * fouls concentrate on defenders and bigs (#031 A). <b>The formula is a user
     * call and was not open at execution.</b>
     *
     * <p>Derived, never stored — no new field and no new column, built from skills
     * this class already holds (#014/#017: don't fabricate a field ahead of its
     * consumer). Measured over the 359 seeded players during the #031 design pass:
     * mean 10.06, sd 3.10, starters 11.95 vs. bench 8.36.
     */
    public double valueComposite() {
        double offense = (drive + finishing + perimeter + post + longRange) / 5.0;
        return (individualDefense + rimProtection + defenseRebound + offense + offense) / 5.0;
    }

    /**
     * §3.5 (Decision B): the fatigue multiplier over this player's skills, from
     * currentEnergy. Full energy ⇒ ×1.0, declining modestly as energy drops.
     */
    public double fatigueFactor() {
        double frac = Math.max(0.0, Math.min(1.0, currentEnergy / SimConfig.MAX_ENERGY));
        return 1.0 - config.fatigueMaxPenalty() * (1.0 - frac);
    }

    /**
     * Drain energy for one on-floor possession (endurance slows the drain) and
     * accumulate the possession share used to derive minutes (Decision A). Called
     * for every on-floor player once per possession by the rotation step.
     */
    public void drainForPossession() {
        double scale = 1.0 - SimConfig.ENDURANCE_DRAIN_SENSITIVITY
                * (endurance - SimConfig.SCALE_AVG) / SimConfig.SCALE_AVG;
        scale = Math.max(config.minDrainScale(), scale);
        currentEnergy = Math.max(0.0,
                currentEnergy - config.energyDrainPerPossession() * scale);
        onFloorPossessions++;
        tookFloor = true;
    }

    /** Recover energy for one possession spent benched (energy attribute speeds it). */
    public void recoverForPossession() {
        double scale = energy / SimConfig.SCALE_AVG;
        currentEnergy = Math.min(SimConfig.MAX_ENERGY,
                currentEnergy + config.energyRecoveryPerPossession() * scale);
    }

    public double offensiveWeight() {
        return drive + finishing + perimeter + post + longRange;
    }

    /**
     * §3.17 (decisions.md #040 B/C/J): this shooter's weight for one {@link ShotType}
     * in {@link ShotSelector#pickShotType}'s four-way draw — <b>the league's base share
     * from the properties file, bent by the shooter's own skill</b>:
     *
     * <pre>{@code share(type) * (1 + SHOT_MIX_SENSITIVITY * (skill - 10) / 10)}</pre>
     *
     * <p><b>⚠ WHAT THIS REPLACED, AND WHY IT WAS WRONG.</b> Until §3.17 this returned
     * the raw skills — {@code DRIVE -> drive + finishing} (a <b>SUM</b>) against one
     * skill each for the other three. That double weight was never argued: #021 D
     * specified the draw over <i>"drive/finishing/perimeter/post/longRange"</i>,
     * <b>five skills for four shot types</b>, and the code collapsed two onto DRIVE by
     * adding them — exactly as {@link #offensiveWeight()} three lines up sums all five
     * for the <i>shooter</i> draw, where summing is correct because there is no
     * per-type normalization. At an average player (every skill ≈ 10 by construction —
     * every {@code SkillCalculator} centres there) that structurally predicts
     * <b>DRIVE 40 / PERIMETER 20 / POST 20 / THREE 20</b>, and the engine measured a
     * 20.9% three draw share against a real 41.5%. <b>The gap was the formula, not the
     * player population</b> (#040 A/B).
     *
     * <p>⚠ <b>{@code finishing} NO LONGER DECIDES HOW OFTEN A DRIVE IS ATTEMPTED.</b>
     * It is a <i>make-the-shot</i> skill — dunks, layups, finishing through contact —
     * and it entered the selection path only through that 5-into-4 collapse. It
     * survives here <b>only inside DRIVE's modifier, halved</b>, because a drive's
     * quality genuinely blends attacking and finishing. How <i>often</i> a drive is
     * attempted is {@code sim.shot-share-drive}'s job now.
     *
     * <p>⚠ <b>Do not confuse this with {@link #offenseSkillForShot}</b>, which is the
     * <b>ACCURACY</b> path and is <b>untouched</b> (#040 B): its {@code /2.0} was always
     * the right form, and 3P% reads 37.8 against a sourced 36.0. After §3.17 the two
     * methods finally agree on how {@code drive} and {@code finishing} combine.
     *
     * <p>The modifier is what keeps shot selection reading the player model: without it
     * every player on the floor shoots the identical league mix. <b>A shooter's skills
     * still decide who shoots what; they no longer decide what the LEAGUE shoots</b>
     * (#040 C). Floored at zero so an extreme low-skill deviation can never produce a
     * negative weight and corrupt the draw.
     */
    public double shotTypeWeight(ShotType type) {
        double modifier = 1.0 + SimConfig.SHOT_MIX_SENSITIVITY
                * (shotSelectionSkill(type) - SimConfig.SCALE_AVG) / SimConfig.SCALE_AVG;
        return config.shotShare(type) * Math.max(0.0, modifier);
    }

    /**
     * §3.17 (#040 C): the skill that bends this shooter's share of one {@link ShotType}.
     * Deliberately the <b>identical expression</b> {@link #offenseSkillForShot} uses —
     * the sum-vs-average asymmetry between selection and accuracy was #040 B's bug, and
     * the two agreeing is the fix. Kept as its own method rather than calling
     * {@code offenseSkillForShot} directly because the two answer different questions
     * (how often vs how well) and a future phase may legitimately move one without the
     * other; the shared value today is a finding, not a coincidence to be collapsed.
     */
    private double shotSelectionSkill(ShotType type) {
        return switch (type) {
            case DRIVE -> (drive + finishing) / 2.0;
            case PERIMETER -> perimeter;
            case POST -> post;
            case THREE -> longRange;
        };
    }

    public double offenseSkillForShot(ShotType type) {
        return switch (type) {
            case DRIVE -> (drive + finishing) / 2.0;
            case PERIMETER -> perimeter;
            case POST -> post;
            case THREE -> longRange;
        };
    }

    // Getters
    public String getPlayerId() { return playerId; }
    public String getTeamId() { return teamId; }
    public double getDrive() { return drive; }
    public double getFinishing() { return finishing; }
    public double getPerimeter() { return perimeter; }
    public double getPost() { return post; }
    public double getLongRange() { return longRange; }
    public double getBallSecurity() { return ballSecurity; }
    public double getFreeThrows() { return freeThrows; }
    public double getFoulDrawing() { return foulDrawing; }
    public double getIndividualDefense() { return individualDefense; }
    public double getRimProtection() { return rimProtection; }
    public double getShotContest() { return shotContest; }
    public double getStealing() { return stealing; }
    public double getFoulProne() { return foulProne; }
    public double getOffenseRebound() { return offenseRebound; }
    public double getDefenseRebound() { return defenseRebound; }
    public double getTeamOffense() { return teamOffense; }
    public double getTeamDefense() { return teamDefense; }
    public double getPassing() { return passing; }
    public double getAcumen() { return acumen; }

    // Box score
    public int getPoints() { return points; }
    public int getFieldGoalsAttempted() { return fieldGoalsAttempted; }
    public int getFieldGoalsMade() { return fieldGoalsMade; }
    public int getThreePointersAttempted() { return threePointersAttempted; }
    public int getThreePointersMade() { return threePointersMade; }
    public int getFreeThrowsAttempted() { return freeThrowsAttempted; }
    public int getFreeThrowsMade() { return freeThrowsMade; }
    public int getTurnovers() { return turnovers; }
    public int getSteals() { return steals; }
    public int getBlocks() { return blocks; }
    /** Personal fouls ONLY — technicals are counted by {@link #getTechnicalFouls()}. */
    public int getFouls() { return fouls; }
    /**
     * §3.14a (#032 E): technical fouls, kept apart from {@link #getFouls()} because a
     * technical does not count toward the six-foul disqualification. Surfaced on the
     * box score and the API since #033, on a parity argument — the twelfth accumulator
     * in a set whose other eleven were already exposed.
     */
    public int getTechnicalFouls() { return technicalFouls; }
    /**
     * §3.14b (#034 F/I): how many FLAGRANT-2 fouls this player has committed — i.e.
     * whether he has been ejected for one, since the limit is 1.
     *
     * <p><b>NOT a foul counter, and NOT surfaced on the box score.</b> The flagrant
     * itself is a personal foul and is already inside {@link #getFouls()} (Decision I),
     * so this counts <b>ejection causes</b>; adding the two together double-counts.
     * #033's parity argument deliberately does <b>not</b> reach here (#034 H): a
     * flagrant is already a member of an exposed accumulator, so there is no missing
     * member and no asymmetry to fix. The count stays queryable from the event log with
     * one {@code WHERE} (#020), exactly as technicals were before #033.
     */
    public int getFlagrantTwos() { return flagrantTwos; }
    public int getOffensiveRebounds() { return offensiveRebounds; }
    public int getDefensiveRebounds() { return defensiveRebounds; }
    public int getAssists() { return assists; }

    public void recordFieldGoalAttempt() { fieldGoalsAttempted++; }
    public void recordFieldGoalMade(int pts) { fieldGoalsMade++; points += pts; }
    public void recordThreePointAttempt() { threePointersAttempted++; }
    public void recordThreePointMade() { threePointersMade++; }
    public void recordFreeThrowAttempt() { freeThrowsAttempted++; }
    public void recordFreeThrowMade() { freeThrowsMade++; points += 1; }
    public void recordTurnover() { turnovers++; }
    public void recordSteal() { steals++; }
    // §3.7 (decisions.md #025 F2): the blocker's credit, mirroring recordSteal().
    // The blocker is not on the SHOT/BLOCKED_* event — this separate accumulator
    // maps to BoxScore.blocks (replacing the setBlocks(0) hardcode).
    public void recordBlock() { blocks++; }
    public void recordFoul() { fouls++; }
    /**
     * §3.14a (#032 E): charge a TECHNICAL foul. Deliberately NOT {@code recordFoul()}
     * — a technical does not count toward the six-foul limit, so it must not touch
     * the {@code fouls} counter that {@link #isFouledOut()}, {@link
     * #foulTroubleLevel()} and §3.10's penalty derivation all read.
     */
    public void recordTechnicalFoul() { technicalFouls++; }
    /**
     * §3.14b (#034 F/I): charge a FLAGRANT-2 — the ejection cause behind {@link
     * #isEjectedForFlagrant()}.
     *
     * <p><b>This is called IN ADDITION to {@code recordFoul()}, never instead of it</b>
     * — the exact opposite of {@link #recordTechnicalFoul()} above. A flagrant IS a
     * personal foul (Decision I), so the committer wears it through the ordinary path
     * and it feeds {@link #isFouledOut()}, {@link #foulTroubleLevel()} and §3.10's
     * penalty derivation like any other foul. This counter exists only to make the
     * ejection derivable; it is not a second foul tally.
     */
    public void recordFlagrantTwo() { flagrantTwos++; }
    public void recordOffensiveRebound() { offensiveRebounds++; }
    public void recordDefensiveRebound() { defensiveRebounds++; }
    public void recordAssist() { assists++; }
}
