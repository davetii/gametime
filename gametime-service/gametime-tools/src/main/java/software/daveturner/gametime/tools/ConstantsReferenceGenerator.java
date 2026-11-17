package software.daveturner.gametime.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Renders docs/constants.md from SimConfig.java and the sim profiles. Run from the repo root (see GENERATE_COMMAND). */
public final class ConstantsReferenceGenerator {

    static final String GENERATE_COMMAND = "mvn -f gametime-service/pom.xml -pl gametime-tools compile exec:java";
    static final String OUTPUT = "docs/constants.md";
    static final String SIM_CONFIG =
            "gametime-service/gametime-app/src/main/java/software/daveturner/gametime/sim/SimConfig.java";
    static final String RESOURCES = "gametime-service/gametime-app/src/main/resources";
    static final String BASELINE = "baseline";

    private ConstantsReferenceGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path root = args.length > 0 ? Path.of(args[0]) : locateRepoRoot(Path.of(""));
        root = root.toAbsolutePath().normalize();
        String rendered = render(root);
        Path out = root.resolve(OUTPUT);
        boolean unchanged = Files.exists(out) && Files.readString(out, StandardCharsets.UTF_8).equals(rendered);
        if (!unchanged) {
            Files.writeString(out, rendered, StandardCharsets.UTF_8);
        }
        System.out.println((unchanged ? "Unchanged: " : "Wrote: ") + out);
    }

    static Path locateRepoRoot(Path start) {
        for (Path p = start.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve(SIM_CONFIG))) {
                return p;
            }
        }
        throw new IllegalStateException("No gametime repo root above " + start.toAbsolutePath());
    }

    static String render(Path root) {
        SimConfigSource simConfig = SimConfigSource.parse(read(root.resolve(SIM_CONFIG)));
        ProfileSource baseline = profile(root, BASELINE);
        List<ProfileSource> eras = eraProfiles(root);
        return render(simConfig, baseline, eras);
    }

    static String render(SimConfigSource simConfig, ProfileSource baseline, List<ProfileSource> eras) {
        Map<String, String> baselineValues = baseline.simEntries();
        validate(simConfig, baselineValues, eras);
        int tunableCount = baselineValues.size();
        int staticCount = simConfig.statics().size();

        StringBuilder md = new StringBuilder();
        md.append("# Simulation constants\n\n");
        md.append("> **GENERATED — do not edit.** Regenerate from the repo root:\n");
        md.append(">\n");
        md.append("> ```bash\n");
        md.append("> ").append(GENERATE_COMMAND).append('\n');
        md.append("> ```\n");
        md.append(">\n");
        md.append("> A stale copy fails `mvn install` (`ConstantsReferenceUpToDateTest` in gametime-tools).\n");
        md.append(">\n");
        md.append("> Sources: ").append(sourceLink(SIM_CONFIG));
        md.append(" · ").append(sourceLink(profilePath(BASELINE)));
        for (ProfileSource era : eras) {
            md.append(" · ").append(sourceLink(profilePath(era.name())));
        }
        md.append("\n>\n");
        md.append("> **").append(tunableCount).append(" tunables / ").append(staticCount).append(" statics.**\n\n");

        md.append("## Tunables\n\n");
        md.append("`private final` fields on SimConfig, bound from `sim.*` in the profiles. ")
                .append("Era columns are blank where the era keeps the baseline value.\n");
        for (ProfileSource.Section section : baseline.sections()) {
            List<Map.Entry<String, String>> rows = section.entries().entrySet().stream()
                    .filter(e -> e.getKey().startsWith("sim.")).toList();
            if (rows.isEmpty()) {
                continue;
            }
            md.append("\n### ").append(heading(section.title())).append("\n\n");
            md.append("| key | accessor | type | baseline |");
            eras.forEach(era -> md.append(' ').append(era.name()).append(" |"));
            md.append(" bounds |\n|---|---|---|---|");
            eras.forEach(era -> md.append("---|"));
            md.append("---|\n");
            for (Map.Entry<String, String> row : rows) {
                SimConfigSource.Tunable t = findTunable(simConfig, row.getKey());
                md.append("| ").append(code(row.getKey()))
                        .append(" | ").append(code(t.field() + "()"))
                        .append(" | ").append(cell(t.type()))
                        .append(" | ").append(code(row.getValue()))
                        .append(" |");
                for (ProfileSource era : eras) {
                    String v = era.simEntries().get(row.getKey());
                    md.append(' ').append(v == null ? "" : code(v)).append(" |");
                }
                md.append(' ').append(cell(t.bounds())).append(" |\n");
            }
        }

        md.append("\n## Statics\n\n");
        md.append("`public static final` on SimConfig: rules of basketball and the shape of the model. ")
                .append("No profile can set them.\n");
        String section = null;
        for (SimConfigSource.Static s : simConfig.statics()) {
            if (!s.section().equals(section)) {
                section = s.section();
                md.append("\n### ").append(heading(section)).append("\n\n");
                md.append("| name | type | value | source |\n|---|---|---|---|\n");
            }
            md.append("| ").append(code(s.name()))
                    .append(" | ").append(cell(s.type()))
                    .append(" | ").append(code(s.value()))
                    .append(" | [SimConfig.java:").append(s.line()).append("](")
                    .append(relative(SIM_CONFIG)).append("#L").append(s.line()).append(") |\n");
        }

        md.append("\n## Profiles\n\n");
        md.append("- `").append(BASELINE).append("`: all ").append(tunableCount).append(" tunables\n");
        for (ProfileSource era : eras) {
            md.append("- `").append(era.name()).append("`: overrides ").append(era.simEntries().size())
                    .append(" of ").append(tunableCount).append(" tunables\n");
        }
        return md.toString();
    }

    private static void validate(SimConfigSource simConfig, Map<String, String> baselineValues,
                                 List<ProfileSource> eras) {
        List<String> problems = new ArrayList<>();
        for (String field : simConfig.tunables().keySet()) {
            if (!baselineValues.containsKey("sim." + SimConfigSource.kebab(field))) {
                problems.add("tunable " + field + " has no sim." + SimConfigSource.kebab(field) + " in baseline");
            }
        }
        for (String key : baselineValues.keySet()) {
            if (findTunable(simConfig, key) == null) {
                problems.add("baseline key " + key + " matches no SimConfig tunable");
            }
        }
        for (ProfileSource era : eras) {
            for (String key : era.simEntries().keySet()) {
                if (!baselineValues.containsKey(key)) {
                    problems.add(era.name() + " key " + key + " is not in baseline");
                }
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Sources disagree:\n  " + String.join("\n  ", problems));
        }
    }

    private static SimConfigSource.Tunable findTunable(SimConfigSource simConfig, String key) {
        return simConfig.tunables().values().stream()
                .filter(t -> key.equals("sim." + SimConfigSource.kebab(t.field())))
                .findFirst().orElse(null);
    }

    private static List<ProfileSource> eraProfiles(Path root) {
        try (Stream<Path> files = Files.list(root.resolve(RESOURCES))) {
            return files.map(p -> p.getFileName().toString())
                    .filter(f -> f.startsWith("application-") && f.endsWith(".properties"))
                    .map(f -> f.substring("application-".length(), f.length() - ".properties".length()))
                    .filter(name -> !name.equals(BASELINE))
                    .sorted()
                    .map(name -> profile(root, name))
                    .filter(p -> !p.simEntries().isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ProfileSource profile(Path root, String name) {
        return ProfileSource.parse(name, read(root.resolve(profilePath(name))));
    }

    private static String profilePath(String name) {
        return RESOURCES + "/application-" + name + ".properties";
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sourceLink(String repoPath) {
        return "[" + Path.of(repoPath).getFileName() + "](" + relative(repoPath) + ")";
    }

    private static String relative(String repoPath) {
        return "../" + repoPath;
    }

    private static String heading(String title) {
        return title.isEmpty() ? "(no section)" : title;
    }

    private static String code(String value) {
        return "`" + value.replace("|", "\\|") + "`";
    }

    private static String cell(String value) {
        return value.replace("|", "\\|");
    }
}
