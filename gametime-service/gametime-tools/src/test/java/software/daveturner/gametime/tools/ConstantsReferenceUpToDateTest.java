package software.daveturner.gametime.tools;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.fail;

/** Fails the build when docs/constants.md no longer matches the sources it is generated from. */
class ConstantsReferenceUpToDateTest {

    @Test
    void committedDocMatchesSources() throws IOException {
        Path root = ConstantsReferenceGenerator.locateRepoRoot(Path.of(""));
        Path doc = root.resolve(ConstantsReferenceGenerator.OUTPUT);
        String expected = ConstantsReferenceGenerator.render(root);

        if (!Files.exists(doc)) {
            fail(ConstantsReferenceGenerator.OUTPUT + " is missing. " + regenerate());
        }
        String actual = Files.readString(doc, StandardCharsets.UTF_8);
        if (!actual.equals(expected)) {
            fail(ConstantsReferenceGenerator.OUTPUT + " is stale (" + firstDifference(expected, actual) + "). "
                    + regenerate());
        }
    }

    private static String regenerate() {
        return "Regenerate it from the repo root: " + ConstantsReferenceGenerator.GENERATE_COMMAND;
    }

    static String firstDifference(String expected, String actual) {
        String[] e = expected.split("\n", -1);
        String[] a = actual.split("\n", -1);
        for (int i = 0; i < Math.min(e.length, a.length); i++) {
            if (!e[i].equals(a[i])) {
                return "line " + (i + 1) + ": expected \"" + e[i] + "\" but was \"" + a[i] + "\"";
            }
        }
        return "expected " + e.length + " lines but was " + a.length;
    }
}
