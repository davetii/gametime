package software.daveturner.gametime.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConstantsReferenceGeneratorTest {

    private static final String BINDING_TEST =
            "gametime-service/gametime-app/src/test/java/software/daveturner/gametime/sim/SimConfigProfileBindingTest.java";

    private static final String BASELINE = """
            # --- Pace ---
            sim.possessions=25
            # --- Shooting ---
            sim.base-three=0.3375
            sim.sit-probabilities=0.0,0.5
            """;

    @Test
    void realTreeCountsMatchTheBindingTest() throws IOException {
        Path root = ConstantsReferenceGenerator.locateRepoRoot(Path.of(""));
        String bindingTest = Files.readString(root.resolve(BINDING_TEST), StandardCharsets.UTF_8);
        SimConfigSource simConfig = SimConfigSource.parse(
                Files.readString(root.resolve(ConstantsReferenceGenerator.SIM_CONFIG), StandardCharsets.UTF_8));

        Matcher tunable = Pattern.compile("EXPECTED_TUNABLE = (\\d+);").matcher(bindingTest);
        assertThat(tunable.find()).isTrue();
        int expectedTunables = Integer.parseInt(tunable.group(1));
        List<String> expectedStatics = expectedStaticNames(bindingTest);

        String doc = ConstantsReferenceGenerator.render(root);
        assertThat(simConfig.tunables()).hasSize(expectedTunables);
        assertThat(simConfig.statics()).extracting(SimConfigSource.Static::name)
                .containsExactlyInAnyOrderElementsOf(expectedStatics);
        assertThat(rows(doc, "| `sim.")).isEqualTo(expectedTunables);
        assertThat(rows(doc, "| `") - rows(doc, "| `sim.")).isEqualTo(expectedStatics.size());
        assertThat(doc).contains("**" + expectedTunables + " tunables / " + expectedStatics.size() + " statics.**");
    }

    @Test
    void renderIsDeterministic() {
        Path root = ConstantsReferenceGenerator.locateRepoRoot(Path.of(""));

        assertThat(ConstantsReferenceGenerator.render(root)).isEqualTo(ConstantsReferenceGenerator.render(root));
    }

    @Test
    void eraColumnIsFilledOnlyWhereTheEraOverrides() {
        String doc = render(BASELINE, "sim.base-three=0.3200\n");

        assertThat(doc)
                .contains("| key | accessor | type | baseline | nineties | bounds |")
                .contains("| `sim.base-three` | `baseThree()` | double | `0.3375` | `0.3200` | [0.0, 1.0] |")
                .contains("| `sim.possessions` | `possessions()` | int | `25` |  | > 0 |")
                .contains("- `nineties`: overrides 1 of 3 tunables")
                .contains("### Pace (Decision B)\n\n| name | type | value | source |")
                .contains("| `SENSITIVITY` | double | `0.50` | [SimConfig.java:15](");
    }

    @Test
    void rejectsSourcesThatDisagree() {
        assertThatThrownBy(() -> render(BASELINE.replace("sim.possessions=25\n", ""), ""))
                .hasMessageContaining("tunable possessions has no sim.possessions in baseline");
        assertThatThrownBy(() -> render(BASELINE + "sim.orphan=1\n", ""))
                .hasMessageContaining("baseline key sim.orphan matches no SimConfig tunable");
        assertThatThrownBy(() -> render(BASELINE, "sim.unknown=1\n"))
                .hasMessageContaining("nineties key sim.unknown is not in baseline");
    }

    @Test
    void mainWritesTheDocThenReportsItUnchanged(@TempDir Path root) throws IOException {
        Path resources = root.resolve(ConstantsReferenceGenerator.RESOURCES);
        Files.createDirectories(resources);
        Files.createDirectories(root.resolve("docs"));
        Path simConfig = root.resolve(ConstantsReferenceGenerator.SIM_CONFIG);
        Files.createDirectories(simConfig.getParent());
        Files.writeString(simConfig, SimConfigSourceTest.FIXTURE);
        Files.writeString(resources.resolve("application-baseline.properties"), BASELINE);
        Files.writeString(resources.resolve("application-nineties.properties"), "sim.base-three=0.32\n");
        Files.writeString(resources.resolve("application-local.properties"), "spring.x=1\n");
        Files.writeString(resources.resolve("application.properties"), "sim.ignored=1\n");

        assertThat(runMain(root)).startsWith("Wrote: ");
        assertThat(runMain(root)).startsWith("Unchanged: ");
        String doc = Files.readString(root.resolve(ConstantsReferenceGenerator.OUTPUT));
        assertThat(doc).contains("| baseline | nineties | bounds |").doesNotContain("local");
        assertThat(ConstantsReferenceGenerator.locateRepoRoot(resources)).isEqualTo(root.toAbsolutePath().normalize());
    }

    @Test
    void locateRepoRootFailsOutsideTheRepo(@TempDir Path elsewhere) {
        assertThatThrownBy(() -> ConstantsReferenceGenerator.locateRepoRoot(elsewhere))
                .hasMessageContaining("No gametime repo root");
    }

    @Test
    void firstDifferenceNamesTheLine() {
        assertThat(ConstantsReferenceUpToDateTest.firstDifference("a\nb\n", "a\nc\n"))
                .isEqualTo("line 2: expected \"b\" but was \"c\"");
        assertThat(ConstantsReferenceUpToDateTest.firstDifference("a", "a\nb"))
                .isEqualTo("expected 1 lines but was 2");
    }

    private static String render(String baseline, String nineties) {
        return ConstantsReferenceGenerator.render(
                SimConfigSource.parse(SimConfigSourceTest.FIXTURE),
                ProfileSource.parse("baseline", baseline),
                List.of(ProfileSource.parse("nineties", nineties)));
    }

    private static String runMain(Path root) throws IOException {
        PrintStream original = System.out;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            ConstantsReferenceGenerator.main(new String[] {root.toString()});
        } finally {
            System.setOut(original);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static List<String> expectedStaticNames(String bindingTest) {
        int start = bindingTest.indexOf("EXPECTED_STATIC = List.of(");
        int end = bindingTest.indexOf(");", start);
        String body = bindingTest.substring(start, end).replaceAll("//[^\n]*", "");
        Matcher m = Pattern.compile("\"([A-Z][A-Z0-9_]*)\"").matcher(body);
        return m.results().map(r -> r.group(1)).toList();
    }

    private static long rows(String doc, String prefix) {
        return Arrays.stream(doc.split("\n")).filter(l -> l.startsWith(prefix)).count();
    }
}
