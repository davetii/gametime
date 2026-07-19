package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §3.8 (decisions.md #026): unit tests for {@link MissedShotResolver} — the
 * skill-weighted four-way missed-shot outcome that wraps {@link ReboundResolver}
 * and applies the flat, defense-leaning OOB lean.
 */
class MissedShotResolverTest {

    private final SimConfig config = new SimConfig();
    private final ReboundResolver reboundResolver = new ReboundResolver(config);
    private final MissedShotResolver resolver = new MissedShotResolver(reboundResolver);

    private RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(seed);
    }

    /** Five players with the given offenseRebound / defenseRebound, everything else average. */
    private List<PlayerGameState> teamWithReboundSkill(String teamId,
                                                       double offReb, double defReb) {
        List<PlayerGameState> players = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            players.add(TestPlayerFactory.create(teamId + i, teamId,
                    10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, offReb, defReb));
        }
        return players;
    }

    private Map<MissedShotOutcome, Integer> tally(List<PlayerGameState> offense,
                                                  List<PlayerGameState> defense,
                                                  boolean capReached, int trials) {
        Map<MissedShotOutcome, Integer> counts = new EnumMap<>(MissedShotOutcome.class);
        for (MissedShotOutcome o : MissedShotOutcome.values()) {
            counts.put(o, 0);
        }
        for (long seed = 1; seed <= trials; seed++) {
            MissedShotResolver.Result r = resolver.resolve(offense, defense, capReached, rng(seed));
            counts.merge(r.outcome(), 1, Integer::sum);
        }
        return counts;
    }

    @Test
    void allFourOutcomesAreReachable() {
        // A balanced board contest over many seeds should exercise every outcome.
        List<PlayerGameState> offense = teamWithReboundSkill("O", 10, 10);
        List<PlayerGameState> defense = teamWithReboundSkill("D", 10, 10);
        Map<MissedShotOutcome, Integer> counts = tally(offense, defense, false, 5000);

        for (MissedShotOutcome o : MissedShotOutcome.values()) {
            assertTrue(counts.get(o) > 0, o + " should be reachable, got " + counts.get(o));
        }
    }

    @Test
    void reboundBalanceTracksSkill() {
        // Elite offensive rebounders vs. hopeless defensive rebounders should win the
        // board (OFFENSIVE_REBOUND) far more than the reverse matchup — the wrapped
        // ReboundResolver skill contest still drives the rebound-vs-rebound split.
        List<PlayerGameState> eliteOff = teamWithReboundSkill("O", 20, 10);
        List<PlayerGameState> weakDef = teamWithReboundSkill("D", 10, 1);
        int eliteOffReb = tally(eliteOff, weakDef, false, 5000)
                .get(MissedShotOutcome.OFFENSIVE_REBOUND);

        List<PlayerGameState> weakOff = teamWithReboundSkill("O", 1, 10);
        List<PlayerGameState> eliteDef = teamWithReboundSkill("D", 10, 20);
        int weakOffReb = tally(weakOff, eliteDef, false, 5000)
                .get(MissedShotOutcome.OFFENSIVE_REBOUND);

        assertTrue(eliteOffReb > weakOffReb * 3,
                "elite off rebounders should grab far more offensive boards: elite="
                        + eliteOffReb + " weak=" + weakOffReb);
    }

    @Test
    void oobSplitIsDefenseLeaning() {
        // The two OOB slices lean defensive (#026 A): OOB_DEFENSE > OOB_OFFENSE.
        List<PlayerGameState> offense = teamWithReboundSkill("O", 10, 10);
        List<PlayerGameState> defense = teamWithReboundSkill("D", 10, 10);
        Map<MissedShotOutcome, Integer> counts = tally(offense, defense, false, 8000);

        int oobDef = counts.get(MissedShotOutcome.OOB_DEFENSE);
        int oobOff = counts.get(MissedShotOutcome.OOB_OFFENSE);
        assertTrue(oobDef > oobOff,
                "OOB should lean defensive: oobDef=" + oobDef + " oobOff=" + oobOff);
    }

    @Test
    void oobSplitIsSkillIndependent() {
        // The OOB-vs-OOB split is a FIXED lean, NOT a skill contest (#026 A): a
        // dominant offensive rebounder must NOT skew the OOB slices toward offense.
        // Compare the OOB-offense share of OOB events between an elite-offense board
        // and an elite-defense board — it should be ~the same (within noise), even
        // though the REBOUND split swings hard with skill.
        int trials = 20000;

        List<PlayerGameState> eliteOff = teamWithReboundSkill("O", 20, 10);
        List<PlayerGameState> weakDef = teamWithReboundSkill("D", 10, 1);
        Map<MissedShotOutcome, Integer> a = tally(eliteOff, weakDef, false, trials);
        double oobOffShareA = oobOffenseShare(a);

        List<PlayerGameState> weakOff = teamWithReboundSkill("O", 1, 10);
        List<PlayerGameState> eliteDef = teamWithReboundSkill("D", 10, 20);
        Map<MissedShotOutcome, Integer> b = tally(weakOff, eliteDef, false, trials);
        double oobOffShareB = oobOffenseShare(b);

        // If OOB inherited the board winner, the elite-offense board's OOB-offense
        // share would be much higher. It must not — the two shares track the flat
        // OOB_OFFENSE_WEIGHT ratio, independent of rebounding skill.
        assertEquals(oobOffShareA, oobOffShareB, 0.05,
                "OOB offense share must be skill-independent: eliteOff=" + oobOffShareA
                        + " eliteDef=" + oobOffShareB);
        // And it should sit near the configured lean, not near the board result.
        double expected = SimConfig.OOB_OFFENSE_WEIGHT
                / (SimConfig.OOB_OFFENSE_WEIGHT + SimConfig.OOB_DEFENSE_WEIGHT);
        assertEquals(expected, oobOffShareA, 0.05,
                "OOB offense share should track the flat lean, got " + oobOffShareA);
    }

    private double oobOffenseShare(Map<MissedShotOutcome, Integer> counts) {
        int oobOff = counts.get(MissedShotOutcome.OOB_OFFENSE);
        int oobDef = counts.get(MissedShotOutcome.OOB_DEFENSE);
        return (double) oobOff / (oobOff + oobDef);
    }

    @Test
    void oobOutcomesCreditNoRebounder() {
        List<PlayerGameState> offense = teamWithReboundSkill("O", 10, 10);
        List<PlayerGameState> defense = teamWithReboundSkill("D", 10, 10);
        int oobSeen = 0;
        for (long seed = 1; seed <= 5000; seed++) {
            MissedShotResolver.Result r = resolver.resolve(offense, defense, false, rng(seed));
            if (r.outcome() == MissedShotOutcome.OOB_OFFENSE
                    || r.outcome() == MissedShotOutcome.OOB_DEFENSE) {
                oobSeen++;
                assertNull(r.rebounder(), "OOB credits no rebounder (E): " + r.outcome());
                assertFalse(r.outcome().creditsRebounder());
            } else {
                assertNotNull(r.rebounder(), "a rebound outcome names a rebounder");
                assertTrue(r.outcome().creditsRebounder());
            }
        }
        assertTrue(oobSeen > 0, "expected some OOB outcomes to assert on");
    }

    @Test
    void capForcesOnlyPossessionEndingOutcomes() {
        // When capped, the resolver must never return an offense-retained outcome —
        // OFFENSIVE_REBOUND is forced to DEFENSIVE_REBOUND and OOB_OFFENSE to
        // OOB_DEFENSE (family preserved: a rebound stays a rebound, OOB stays OOB).
        // Use an elite-offense board that would otherwise retain almost every time.
        List<PlayerGameState> eliteOff = teamWithReboundSkill("O", 20, 10);
        List<PlayerGameState> weakDef = teamWithReboundSkill("D", 10, 1);
        for (long seed = 1; seed <= 5000; seed++) {
            MissedShotResolver.Result r = resolver.resolve(eliteOff, weakDef, true, rng(seed));
            assertFalse(r.outcome().offenseRetains(),
                    "capped resolve must not retain: " + r.outcome() + " (seed " + seed + ")");
        }
    }

    @Test
    void cappedStillProducesBothOobAndReboundFamilies() {
        // The cap forces ENDING outcomes but must preserve the family split — both
        // DEFENSIVE_REBOUND and OOB_DEFENSE should still appear when capped.
        List<PlayerGameState> offense = teamWithReboundSkill("O", 10, 10);
        List<PlayerGameState> defense = teamWithReboundSkill("D", 10, 10);
        Map<MissedShotOutcome, Integer> counts = tally(offense, defense, true, 5000);

        assertTrue(counts.get(MissedShotOutcome.DEFENSIVE_REBOUND) > 0,
                "capped: defensive rebounds still occur");
        assertTrue(counts.get(MissedShotOutcome.OOB_DEFENSE) > 0,
                "capped: OOB-defense still occurs");
        assertEquals(0, counts.get(MissedShotOutcome.OFFENSIVE_REBOUND));
        assertEquals(0, counts.get(MissedShotOutcome.OOB_OFFENSE));
    }
}
