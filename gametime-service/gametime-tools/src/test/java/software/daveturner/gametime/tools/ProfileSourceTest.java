package software.daveturner.gametime.tools;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfileSourceTest {

    @Test
    void keepsSectionsKeysAndRawValuesInFileOrder() {
        ProfileSource profile = ProfileSource.parse("baseline", """
                # header comment
                # --- Pace / game structure ---------------------
                sim.b=25
                # 0.140 -> points 114.7
                sim.a = 0.3375
                ! bang comment

                # --- Shot mix (§3.17) ---
                sim.c:1.0,0.5
                spring.other=x
                """);

        assertThat(profile.sections()).extracting(ProfileSource.Section::title)
                .containsExactly("Pace / game structure", "Shot mix (§3.17)");
        assertThat(profile.sections().get(0).entries()).containsExactly(
                Map.entry("sim.b", "25"), Map.entry("sim.a", "0.3375"));
        assertThat(profile.simEntries()).containsOnlyKeys("sim.b", "sim.a", "sim.c")
                .containsEntry("sim.c", "1.0,0.5");
    }

    @Test
    void entriesBeforeAnySectionGetAnUntitledOne() {
        ProfileSource profile = ProfileSource.parse("p", "sim.a=1\n");

        assertThat(profile.sections()).singleElement()
                .extracting(ProfileSource.Section::title).isEqualTo("");
    }

    @Test
    void rejectsContinuationLines() {
        assertThatThrownBy(() -> ProfileSource.parse("p", "sim.a=1,\\\n  2\n"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("p line 1");
    }
}
