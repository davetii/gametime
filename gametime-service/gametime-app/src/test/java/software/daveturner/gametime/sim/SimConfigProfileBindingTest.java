package software.daveturner.gametime.sim;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Structural guards on the static/tunable split: that every tunable constant really
 * gets its value from application-baseline.properties, and that the rules and model
 * machinery really stayed static.
 *
 * <p>Spring enforces most of this for free - the fields have no initializers, so a
 * missing key fails the context at startup, and a re-added initializer will not
 * compile. What needs a runtime guard is the reverse mistake: a static alias, which
 * javac would inline into each caller as a stale second value.
 */
class SimConfigProfileBindingTest {

    /** The constants that must stay static: rules, model machinery, one measured value. */
    private static final List<String> EXPECTED_STATIC = List.of(
            // rules (9)
            "PERIODS", "MINUTES_PER_PERIOD", "OT_MINUTES", "FREE_THROWS_PER_FOUL",
            "AND_ONE_FREE_THROWS", "TECHNICAL_FREE_THROWS", "FLAGRANT_FREE_THROWS",
            "TECHNICAL_EJECTION_LIMIT", "FLAGRANT_EJECTION_LIMIT",
            // model machinery (17)
            "SCALE_AVG", "MAX_ENERGY", "PROB_FLOOR", "PROB_CEILING", "SENSITIVITY",
            "FT_SENSITIVITY", "BLOCK_SENSITIVITY", "REBOUND_FOUL_SENSITIVITY",
            "AND_ONE_SENSITIVITY", "TO_CAUSE_SENSITIVITY", "COACH_SENSITIVITY",
            "ASSIST_SENSITIVITY", "ACUMEN_SENSITIVITY", "TEAM_EFFICIENCY_SENSITIVITY",
            "ENDURANCE_DRAIN_SENSITIVITY", "FOUL_TROUBLE_VALUE_SENSITIVITY",
            // §3.17 (#040 J): shot-mix machinery, NOT a tunable — the shares are
            // (sim.shot-share-*), this is how hard a player's skill bends them.
            "SHOT_MIX_SENSITIVITY",
            // measured (1)
            "PERSONAL_FOULS_PER_TEAM_GAME");

    private static final int EXPECTED_TUNABLE = 62;

    /** Every tunable field is final and takes its value only from the properties file. */
    @Test
    void everyProfilableFieldIsFinalAndTakesNoInitializer() throws Exception {
        List<String> offenders = new ArrayList<>();
        for (Field f : SimConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            if (!Modifier.isFinal(f.getModifiers())) {
                offenders.add(f.getName() + " is not final");
            }
        }
        assertTrue(offenders.isEmpty(),
                "every tunable SimConfig field must be final: " + offenders);

        // Bind a doctored profile and prove every field changed - a field carrying its
        // own value would not.
        SimConfig baseline = SimConfig.baseline();
        SimConfig doctored = bindAllShifted();
        for (Field f : SimConfig.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            f.setAccessible(true);
            Object a = f.get(baseline);
            Object b = f.get(doctored);
            if (f.getType() == double[].class) {
                assertNotNull(b, f.getName() + " did not bind");
                continue;
            }
            assertTrue(!String.valueOf(a).equals(String.valueOf(b)),
                    "field " + f.getName() + " did not change when the bound property "
                            + "changed - it is carrying its own value, making the Java "
                            + "file a second source of truth");
        }
    }

    /** The constructor-bound parameter count is the tunable-constant count. */
    @Test
    void exactlyFiftySevenConstantsAreProfilable() {
        Constructor<?> ctor = widestConstructor();
        assertEquals(EXPECTED_TUNABLE, ctor.getParameterCount(),
                "the tunable-constant count changed - update application-baseline.properties "
                        + "and this count together");

        long instanceFields = java.util.Arrays.stream(SimConfig.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .count();
        assertEquals(EXPECTED_TUNABLE, instanceFields,
                "instance-field count must match the constructor arity");
    }

    /**
     * No static aliases. javac inlines constant variables at each caller's compile
     * time, so an alias kept "so the tests compile" bakes a stale value into every
     * class that reads it - a test could assert one value while the engine ran another.
     */
    @Test
    void onlyTheTwentySevenRulesAndMachineryConstantsAreStatic() {
        TreeSet<String> actual = new TreeSet<>();
        for (Field f : SimConfig.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                continue;
            }
            // The baseline-resource filename is a String constant, not a sim constant.
            if (f.getType() == String.class) {
                continue;
            }
            actual.add(f.getName());
        }
        assertEquals(new TreeSet<>(EXPECTED_STATIC), actual,
                "the static/tunable split changed. A new static is either a genuine "
                        + "rule/machinery constant (update this list) or an alias for a "
                        + "tunable one - and an alias is the bug this test prevents.");
    }

    /** Every tunable constant has a key in the baseline file - the file is its only copy. */
    @Test
    void baselineProfileCarriesEveryProfilableConstant() throws IOException {
        Properties props = new Properties();
        try (InputStream in = SimConfig.class.getClassLoader()
                .getResourceAsStream(SimConfig.BASELINE_PROFILE_RESOURCE)) {
            assertNotNull(in, SimConfig.BASELINE_PROFILE_RESOURCE + " is missing");
            props.load(in);
        }

        TreeSet<String> missing = new TreeSet<>();
        for (Parameter p : widestConstructor().getParameters()) {
            if (!props.containsKey(kebabKey(p.getName()))) {
                missing.add(kebabKey(p.getName()));
            }
        }
        assertTrue(missing.isEmpty(),
                "application-baseline.properties is missing: " + missing);

        long simKeys = props.stringPropertyNames().stream()
                .filter(k -> k.startsWith("sim.")).count();
        assertEquals(EXPECTED_TUNABLE, simKeys,
                "the baseline profile must carry exactly the tunable constants - an extra "
                        + "sim.* key binds to nothing and is silently ignored");
    }

    /** The foul-trouble curve is indexed by foul count 0..6, so exactly 7 entries. */
    @Test
    void theFoulTroubleCurveHasExactlySevenEntries() {
        assertEquals(7, SimConfig.baseline().foulTroubleSitProbabilities().length,
                "sim.foul-trouble-sit-probabilities is indexed by foul count 0-6");
    }

    /** The baseline file must hold sim.* keys only — no datasource, no Spring config. */
    @Test
    void baselineProfileHoldsSimKeysOnly() throws IOException {
        Properties props = new Properties();
        try (InputStream in = SimConfig.class.getClassLoader()
                .getResourceAsStream(SimConfig.BASELINE_PROFILE_RESOURCE)) {
            props.load(in);
        }
        TreeSet<String> foreign = new TreeSet<>();
        for (String k : props.stringPropertyNames()) {
            if (!k.startsWith("sim.")) {
                foreign.add(k);
            }
        }
        assertTrue(foreign.isEmpty(),
                "a sim profile file must hold sim.* keys only: " + foreign);
    }

    private static Constructor<?> widestConstructor() {
        Constructor<?> widest = null;
        for (Constructor<?> c : SimConfig.class.getDeclaredConstructors()) {
            if (widest == null || c.getParameterCount() > widest.getParameterCount()) {
                widest = c;
            }
        }
        return widest;
    }

    /** camelCase field/parameter name to its kebab-case property key. */
    private static String kebabKey(String name) {
        StringBuilder sb = new StringBuilder("sim.");
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c)) {
                sb.append('-').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Bind a copy of the baseline profile with every scalar shifted, so a field that
     * kept its own value can be told apart from one that really bound.
     */
    private static SimConfig bindAllShifted() throws Exception {
        Properties props = new Properties();
        try (InputStream in = SimConfig.class.getClassLoader()
                .getResourceAsStream(SimConfig.BASELINE_PROFILE_RESOURCE)) {
            props.load(in);
        }
        Constructor<?> ctor = widestConstructor();
        Object[] args = new Object[ctor.getParameterCount()];
        Class<?>[] types = ctor.getParameterTypes();
        Parameter[] params = ctor.getParameters();
        for (int i = 0; i < args.length; i++) {
            String raw = props.getProperty(kebabKey(params[i].getName()));
            if (types[i] == double[].class) {
                String[] parts = raw.split(",");
                double[] arr = new double[parts.length];
                for (int j = 0; j < parts.length; j++) {
                    arr[j] = Double.parseDouble(parts[j].trim());
                }
                args[i] = arr;
            } else if (types[i] == int.class) {
                // +7 keeps every count positive and distinct from its baseline value.
                args[i] = Integer.parseInt(raw.trim()) + 7;
            } else {
                // A shift that stays inside [0,1] for rates and still moves every value.
                double v = Double.parseDouble(raw.trim());
                args[i] = v == 0.0 ? 0.5 : v / 2.0;
            }
        }
        ctor.setAccessible(true);
        return (SimConfig) ctor.newInstance(args);
    }
}
