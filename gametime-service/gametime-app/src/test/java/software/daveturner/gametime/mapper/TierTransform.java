package software.daveturner.gametime.mapper;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Scripted tiering transform (run on demand, not a unit assertion).
 *
 * Reshapes the top of players.csv into a superstar/star structure while leaving
 * the crowd and tail untouched. Players are ranked by raw "talent" (mean of the
 * core on-court attributes); the natural top N are lifted with a STRENGTH-WEIGHTED
 * boost (raise scales with each attribute's current value) so each lands in its
 * tier's talent band while SHARPENING the player's archetype — strengths climb,
 * weaknesses barely move.
 *
 *   - LIFTED:    the 14 non-physical core attributes
 *   - FROZEN:    size, wingspan (no growing players)
 *   - UNTOUCHED: intangibles (luck, charisma, ego, cohesion) + non-attr columns
 *
 * Output is written to players.tiered.csv next to the source so the diff is
 * reviewable before it replaces players.csv. Re-run MarqueeRankingAnalysis on
 * the output to verify the tiers appear.
 *
 * Run: mvn -Dtest=TierTransform test   (from gametime-service)
 */
@Disabled("On-demand tiering tool — writes players.tiered.csv. Run explicitly via -Dtest=TierTransform, never in CI.")
public class TierTransform {

    // ---- tier config (the only real inputs) ----
    private static final int SUPERSTARS = 5;
    private static final int STARS = 25;
    private static final double SUPERSTAR_TARGET = 17.5; // raw-talent band ~17-18
    private static final double STAR_TARGET = 15.5;      // raw-talent band ~15-16

    private static final double ATTR_MAX = 20d;

    /** Attributes that define on-court talent and get lifted. */
    private static final List<String> LIFT_ATTRS = List.of(
            "strength", "intelligence", "shot_skill", "shot_selection", "endurance",
            "agility", "handle", "speed", "energy", "health", "determination",
            "verticality", "composure", "aggression", "awareness");
    /** Core attrs used to MEASURE talent (includes frozen physicals for a fair overall). */
    private static final List<String> TALENT_ATTRS;
    static {
        List<String> t = new ArrayList<>(LIFT_ATTRS);
        t.add("size");
        t.add("wingspan");
        TALENT_ATTRS = List.copyOf(t);
    }

    private record Row(String[] cells) {}

    @Test
    public void transform() throws Exception {
        URL src = getClass().getResource("/db/players.csv");
        String[] header;
        Map<String, Integer> col = new LinkedHashMap<>();
        List<Row> rows = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(src.openStream(), StandardCharsets.UTF_8))) {
            header = br.readLine().split(",", -1);
            for (int i = 0; i < header.length; i++) col.put(header[i].trim(), i);
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                rows.add(new Row(line.split(",", -1)));
            }
        }

        // rank by current raw talent
        rows.sort(Comparator.comparingDouble((Row r) -> talent(r, col)).reversed());

        System.out.println("\n================ TIER TRANSFORM ================");
        System.out.printf("Players: %d   Superstars: %d (target %.1f)   Stars: %d (target %.1f)%n%n",
                rows.size(), SUPERSTARS, SUPERSTAR_TARGET, STARS, STAR_TARGET);
        System.out.printf("%-4s %-26s %7s %7s%n", "tier", "player", "before", "after");

        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            double target;
            String tier;
            if (i < SUPERSTARS) { target = SUPERSTAR_TARGET; tier = "SUP"; }
            else if (i < SUPERSTARS + STARS) { target = STAR_TARGET; tier = "STAR"; }
            else continue; // crowd + tail untouched

            double before = talent(r, col);
            if (before >= target) { // already at/above band — leave as-is
                System.out.printf("%-4s %-26s %7.2f %7.2f  (already)%n",
                        tier, name(r, col), before, before);
                continue;
            }
            lift(r, col, target);
            double after = talent(r, col);
            System.out.printf("%-4s %-26s %7.2f %7.2f%n", tier, name(r, col), before, after);
        }

        writeCsv(src, header, rows);
        System.out.println("\nWrote players.tiered.csv (review the diff, then rename to players.csv).");
        System.out.println("Verify: mvn -Dtest=MarqueeRankingAnalysis test  (after pointing it at the new file)");
        System.out.println("================================================\n");
    }

    /**
     * Strength-weighted lift: each attribute is boosted by k*(v/MAX)*v, i.e. the
     * raise scales with how high the attribute ALREADY is. A player's strengths
     * (high v) climb fast while weaknesses (low v) barely move, so the archetype
     * SHARPENS instead of flattening — a perimeter wing stays non-strong, a
     * cerebral guard stays non-bruising. k is solved per player so the talent
     * mean reaches `target`; the soft-clamp downstream handles any over-spike.
     */
    private void lift(Row r, Map<String, Integer> col, double target) {
        int count = TALENT_ATTRS.size();
        double frozenSum = val(r, col, "size") + val(r, col, "wingspan");
        double liftSumBefore = 0, weight = 0;
        for (String a : LIFT_ATTRS) {
            double v = val(r, col, a);
            liftSumBefore += v;
            weight += (v / ATTR_MAX) * v;   // boost weight ~ v^2 : favors strengths
        }
        // need: (frozenSum + liftSumBefore + k*weight) / count = target
        double k = (target * count - frozenSum - liftSumBefore) / weight;
        k = Math.max(0, k);                 // never lower an attribute
        for (String a : LIFT_ATTRS) {
            double v = val(r, col, a);
            double nv = v + k * (v / ATTR_MAX) * v;
            set(r, col, a, (int) Math.round(Math.min(ATTR_MAX, nv)));
        }
    }

    private double talent(Row r, Map<String, Integer> col) {
        double sum = 0;
        for (String a : TALENT_ATTRS) sum += val(r, col, a);
        return sum / TALENT_ATTRS.size();
    }

    private double val(Row r, Map<String, Integer> col, String a) {
        return Double.parseDouble(r.cells()[col.get(a)].trim());
    }

    private void set(Row r, Map<String, Integer> col, String a, int v) {
        r.cells()[col.get(a)] = String.valueOf(v);
    }

    private String name(Row r, Map<String, Integer> col) {
        return r.cells()[col.get("first_name")] + " " + r.cells()[col.get("last_name")];
    }

    private void writeCsv(URL src, String[] header, List<Row> rows) throws Exception {
        // write next to the source resource on disk (src/main/resources/db)
        String path = src.getPath().replace("/target/classes/", "/src/main/resources/")
                .replace("players.csv", "players.tiered.csv");
        try (FileWriter w = new FileWriter(path, StandardCharsets.UTF_8)) {
            w.write(String.join(",", header));
            w.write("\n");
            for (Row r : rows) {
                w.write(String.join(",", r.cells()));
                w.write("\n");
            }
        }
        System.out.println("Output path: " + path);
    }
}
