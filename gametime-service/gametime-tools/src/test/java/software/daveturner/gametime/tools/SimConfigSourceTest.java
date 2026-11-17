package software.daveturner.gametime.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimConfigSourceTest {

    static final String FIXTURE = """
            package x;

            public class SimConfig {
                /*
                  public static final       a RULE, inside a block comment
                */

                // --- Pace (Decision B) ---
                private final int possessions;
                public static final int PERIODS = 4;

                /** Javadoc. */
                // --- Shooting ----------
                private final double baseThree;
                public static final double SENSITIVITY = 0.50; // trailing
                private final double[] sitProbabilities;

                public SimConfig(
                        @Positive int possessions,
                        @DecimalMin("0.0") @DecimalMax("1.0") double baseThree,
                        @NotNull @Size(min = 7, max = 7, message = "must have (7) entries") double[] sitProbabilities) {
                }

                public static final String RESOURCE = "application-baseline.properties";
            }
            """;

    @Test
    void readsStaticsWithSectionLineAndRawValue() {
        SimConfigSource source = SimConfigSource.parse(FIXTURE);

        assertThat(source.statics()).containsExactly(
                new SimConfigSource.Static("Pace (Decision B)", "PERIODS", "int", "4", 10),
                new SimConfigSource.Static("Shooting", "SENSITIVITY", "double", "0.50", 15));
    }

    @Test
    void readsTunablesInSourceOrderWithBounds() {
        SimConfigSource source = SimConfigSource.parse(FIXTURE);

        assertThat(source.tunables().values()).containsExactly(
                new SimConfigSource.Tunable("possessions", "int", "> 0"),
                new SimConfigSource.Tunable("baseThree", "double", "[0.0, 1.0]"),
                new SimConfigSource.Tunable("sitProbabilities", "double[]", "exactly 7 entries"));
    }

    @Test
    void failsWhenFieldsAndConstructorDisagree() {
        String broken = FIXTURE.replace("private final double baseThree;", "");

        assertThatThrownBy(() -> SimConfigSource.parse(broken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("do not match its constructor parameters");
    }

    @Test
    void rendersEachBoundForm() {
        assertThat(SimConfigSource.bounds("@DecimalMin(\"0.0\") ")).isEqualTo("≥ 0.0");
        assertThat(SimConfigSource.bounds("@DecimalMax(\"1.0\") ")).isEqualTo("≤ 1.0");
        assertThat(SimConfigSource.bounds("@Min(3) ")).isEqualTo("≥ 3");
        assertThat(SimConfigSource.bounds("@Min(value = 2) ")).isEqualTo("≥ 2");
        assertThat(SimConfigSource.bounds("@Size(min = 2, max = 5) ")).isEqualTo("2–5 entries");
        assertThat(SimConfigSource.bounds("@Positive @Min(1) ")).isEqualTo("≥ 1; > 0");
        assertThat(SimConfigSource.bounds("")).isEmpty();
    }

    @Test
    void kebabMatchesSpringRelaxedBinding() {
        assertThat(SimConfigSource.kebab("baseThree")).isEqualTo("base-three");
        assertThat(SimConfigSource.kebab("toWeightLostBallOob")).isEqualTo("to-weight-lost-ball-oob");
    }
}
