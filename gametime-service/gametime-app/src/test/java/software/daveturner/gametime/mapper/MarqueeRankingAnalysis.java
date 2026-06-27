package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.daveturner.gametime.model.Player;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Throwaway analysis (not an assertion test). Loads players.csv, runs the real
 * 23 skill calculators, ranks each player by a "peak skills" overall = mean of
 * their top-5 skills, and prints the league-gap breakdown requested.
 * Run: mvn -Dtest=MarqueeRankingAnalysis test
 */
@Disabled("On-demand analysis tooling — not a build test. Run explicitly via -Dtest=MarqueeRankingAnalysis.")
public class MarqueeRankingAnalysis {

    /** Mean of a player's top-N skills. */
    private static final int PEAK_N = 5;

    private final List<SkillCalculator> calcs = List.of(
            new AcumenSkillCalculator(), new BallSecuritySkillCalculator(),
            new DefenseReboundSkillCalculator(), new DriveSkillCalculator(),
            new FreeThrowSkillCalculator(), new IndividualDefenseSkillCalculator(),
            new LongRangeSkillCalculator(), new OffenseReboundSkillCalculator(),
            new PassingSkillCalculator(), new PerimeterScoringSkillCalculator(),
            new PostScoringSkillCalculator(), new TeamDefenseSkillCalculator(),
            new TeamOffenseSkillCalculator(), new FinishingSkillCalculator(),
            new TransitionSkillCalculator(), new RimProtectionSkillCalculator(),
            new StealingSkillCalculator(), new ShotContestSkillCalculator(),
            new FoulDrawingSkillCalculator(), new FoulProneSkillCalculator(),
            new ClutchSkillCalculator(), new ScreenSettingSkillCalculator(),
            new OffBallMovementSkillCalculator());

    private record Ranked(String name, String pos, double peak, double mean) {}

    /** position string kept alongside the Player (enum wire-values differ from CSV names). */
    private final Map<Player, String> positions = new IdentityHashMap<>();

    /** Raw attribute columns we treat as on-court "talent" (excludes luck/charisma/ego/cohesion intangibles). */
    private static final List<String> CORE_ATTRS = List.of(
            "size", "strength", "intelligence", "shot_skill", "shot_selection",
            "endurance", "agility", "handle", "speed", "energy", "health",
            "determination", "verticality", "wingspan", "composure", "aggression", "awareness");
    private static final List<String> ALL_ATTRS = List.of(
            "size", "strength", "intelligence", "shot_skill", "shot_selection",
            "endurance", "agility", "handle", "speed", "energy", "health",
            "determination", "luck", "charisma", "ego", "cohesion",
            "verticality", "wingspan", "composure", "aggression", "awareness");
    /** raw attribute rows, keyed by attr name -> list of values across the league. */
    private final Map<String, List<Integer>> attrValues = new LinkedHashMap<>();
    /** per-player core-attribute mean (raw talent), for distribution analysis. */
    private final List<Double> rawTalent = new ArrayList<>();

    @Test
    public void rankAndReport() throws Exception {
        List<Player> players = loadCsv();
        List<Ranked> ranked = players.stream()
                .map(this::score)
                .sorted(Comparator.comparingDouble(Ranked::peak).reversed())
                .collect(Collectors.toList());

        double[] peaks = ranked.stream().mapToDouble(Ranked::peak).toArray();
        int n = peaks.length;

        System.out.println("\n================ MARQUEE / PEAK-SKILL RANKING ================");
        System.out.printf("Players: %d   Overall = mean of top-%d skills%n%n", n, PEAK_N);

        System.out.println("---- Top 15 ----");
        System.out.printf("%-4s %-26s %-4s %6s %6s%n", "#", "Player", "Pos", "PEAK", "mean");
        for (int i = 0; i < 15 && i < n; i++) {
            Ranked r = ranked.get(i);
            System.out.printf("%-4d %-26s %-4s %6.2f %6.2f%n",
                    i + 1, r.name(), r.pos(), r.peak(), r.mean());
        }

        System.out.println("\n---- Distribution (peak rating) ----");
        int[] marks = {1, 5, 10, 20, 50, 100, 200, n / 2, n};
        for (int m : marks) {
            if (m >= 1 && m <= n) {
                System.out.printf("  #%-4d -> %6.2f%n", m, peaks[m - 1]);
            }
        }

        // --- tier histogram on peak rating ---
        System.out.println("\n---- Peak-rating histogram (how clustered is the league?) ----");
        int[] buckets = new int[21];
        for (double v : peaks) buckets[Math.min(20, (int) Math.floor(v))]++;
        for (int b = 20; b >= 5; b--) {
            if (buckets[b] == 0) continue;
            System.out.printf("  %2d.x  %-4d %s%n", b, buckets[b], "#".repeat(buckets[b]));
        }
        long atCeiling = Arrays.stream(peaks).filter(v -> v >= 19.5).count();
        System.out.printf("  players >= 19.5 (clamped cluster): %d%n", atCeiling);

        // --- same analysis on MEAN (un-saturated) for contrast ---
        double[] means = ranked.stream().mapToDouble(Ranked::mean)
                .sorted().toArray();
        // means sorted ascending; reverse view via index from top
        System.out.println("\n---- For contrast: MEAN-rating distribution ----");
        int[] mMarks = {1, 5, 10, 20, 50, 100, 200, n};
        for (int m : mMarks) {
            if (m <= n) System.out.printf("  #%-4d -> %6.2f%n", m, means[n - m]);
        }
        System.out.printf("  mean rating min %.2f / max %.2f / spread %.2f%n",
                means[0], means[n - 1], means[n - 1] - means[0]);

        System.out.println("\n---- Requested gaps ----");
        gap(peaks, n, "Top-10 cluster spread (#1 vs #10)", 1, 10);
        gap(peaks, n, "5th vs 20th", 5, 20);
        gap(peaks, n, "50th vs 100th", 50, 100);
        gap(peaks, n, "Elite vs replacement (#1 vs median)", 1, (n + 1) / 2);

        double top10mean = Arrays.stream(peaks).limit(10).average().orElse(0);
        double restMean = Arrays.stream(peaks).skip(10).average().orElse(0);
        System.out.printf("%nTop-10 mean peak:        %6.2f%n", top10mean);
        System.out.printf("Everyone-else mean peak: %6.2f%n", restMean);
        System.out.printf("Top-10 stand above field by: %5.2f pts (%.0f%% higher)%n",
                top10mean - restMean, (top10mean / restMean - 1) * 100);
        reportRawAttributes();
        System.out.println("=============================================================\n");
    }

    /** Does the RAW attribute data already contain a star tier, or is it a flat bell curve? */
    private void reportRawAttributes() {
        System.out.println("\n############ RAW ATTRIBUTE LAYER (source data) ############");

        System.out.println("\n---- Per-attribute spread (1-20 scale) ----");
        System.out.printf("%-15s %4s %4s %6s %6s%n", "attribute", "min", "max", "mean", "p95");
        for (String a : ALL_ATTRS) {
            List<Integer> v = new ArrayList<>(attrValues.get(a));
            Collections.sort(v);
            double mean = v.stream().mapToInt(Integer::intValue).average().orElse(0);
            int p95 = v.get((int) Math.floor(0.95 * (v.size() - 1)));
            System.out.printf("%-15s %4d %4d %6.1f %6d%n",
                    a, v.get(0), v.get(v.size() - 1), mean, p95);
        }

        // raw-talent (core-attribute mean) distribution + histogram
        double[] t = rawTalent.stream().mapToDouble(Double::doubleValue)
                .sorted().toArray();
        int n = t.length;
        System.out.println("\n---- Raw 'talent' (mean of " + CORE_ATTRS.size()
                + " core attrs) distribution ----");
        int[] marks = {1, 5, 10, 20, 50, 100, 200, n};
        for (int m : marks) {
            if (m <= n) System.out.printf("  #%-4d -> %6.2f%n", m, t[n - m]);
        }
        System.out.printf("  spread: min %.2f / mean %.2f / max %.2f%n",
                t[0], Arrays.stream(t).average().orElse(0), t[n - 1]);

        System.out.println("\n---- Raw-talent histogram (is there a star tier in the DATA?) ----");
        int[] b = new int[21];
        for (double v : t) b[Math.min(20, (int) Math.round(v))]++;
        for (int k = 20; k >= 5; k--) {
            if (b[k] == 0) continue;
            System.out.printf("  %2d   %-4d %s%n", k, b[k], "#".repeat(b[k]));
        }
        System.out.println("##########################################################");
    }

    private void gap(double[] peaks, int n, String label, int a, int b) {
        if (a > n || b > n) return;
        double va = peaks[a - 1], vb = peaks[b - 1];
        System.out.printf("  %-38s %6.2f -> %6.2f   gap %5.2f%n", label, va, vb, va - vb);
    }

    private Ranked score(Player p) {
        double[] skills = calcs.stream()
                .map(c -> c.calc(p))
                .mapToDouble(BigDecimal::doubleValue)
                .sorted().toArray();
        // top-N are the largest -> tail of the sorted ascending array
        double peak = 0;
        for (int i = skills.length - PEAK_N; i < skills.length; i++) peak += skills[i];
        peak /= PEAK_N;
        double mean = Arrays.stream(skills).average().orElse(0);
        return new Ranked(p.getFirstName() + " " + p.getLastName(), positions.get(p), peak, mean);
    }

    private List<Player> loadCsv() throws Exception {
        // -Dcsv=players.tiered.csv to analyze the transformed data; defaults to source.
        String csv = System.getProperty("csv", "players.csv");
        System.out.println("(analyzing /db/" + csv + ")");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/db/" + csv), StandardCharsets.UTF_8))) {
            String[] header = br.readLine().split(",");
            Map<String, Integer> col = new HashMap<>();
            for (int i = 0; i < header.length; i++) col.put(header[i].trim(), i);
            List<Player> out = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] f = line.split(",", -1);
                Player p = new Player();
                p.setFirstName(f[col.get("first_name")]);
                p.setLastName(f[col.get("last_name")]);
                positions.put(p, f[col.get("position")].trim());
                p.setYearsPro(Integer.parseInt(f[col.get("years_pro")].trim()));
                p.setSize(i(f, col, "size"));
                p.setStrength(i(f, col, "strength"));
                p.setIntelligence(i(f, col, "intelligence"));
                p.setShotSkill(i(f, col, "shot_skill"));
                p.setShotSelection(i(f, col, "shot_selection"));
                p.setEndurance(i(f, col, "endurance"));
                p.setAgility(i(f, col, "agility"));
                p.setHandle(i(f, col, "handle"));
                p.setSpeed(i(f, col, "speed"));
                p.setEnergy(i(f, col, "energy"));
                p.setHealth(i(f, col, "health"));
                p.setDetermination(i(f, col, "determination"));
                p.setLuck(i(f, col, "luck"));
                p.setCharisma(i(f, col, "charisma"));
                p.setEgo(i(f, col, "ego"));
                p.setCohesion(i(f, col, "cohesion"));
                p.setVerticality(i(f, col, "verticality"));
                p.setWingspan(i(f, col, "wingspan"));
                p.setComposure(i(f, col, "composure"));
                p.setAggression(i(f, col, "aggression"));
                p.setAwareness(i(f, col, "awareness"));
                out.add(p);

                // capture raw attribute layer for distribution analysis
                double coreSum = 0;
                for (String a : ALL_ATTRS) {
                    int v = i(f, col, a);
                    attrValues.computeIfAbsent(a, k -> new ArrayList<>()).add(v);
                }
                for (String a : CORE_ATTRS) coreSum += i(f, col, a);
                rawTalent.add(coreSum / CORE_ATTRS.size());
            }
            return out;
        }
    }

    private int i(String[] f, Map<String, Integer> col, String name) {
        return Integer.parseInt(f[col.get(name)].trim());
    }
}
