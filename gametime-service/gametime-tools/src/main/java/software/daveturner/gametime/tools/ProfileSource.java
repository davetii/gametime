package software.daveturner.gametime.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A sim profile (.properties) read as text, keeping its "# --- Section ---" headers and raw values. */
record ProfileSource(String name, List<Section> sections) {

    record Section(String title, Map<String, String> entries) {}

    private static final Pattern SECTION = Pattern.compile("^#\\s*---\\s*(.*?)\\s*-*\\s*$");
    private static final Pattern ENTRY = Pattern.compile("^([^#!\\s=:][^=:]*?)\\s*[=:]\\s*(.*?)\\s*$");

    static ProfileSource parse(String name, String text) {
        List<Section> sections = new ArrayList<>();
        Section current = null;
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].stripTrailing();
            Matcher m;
            if ((m = SECTION.matcher(line)).matches()) {
                current = new Section(m.group(1), new LinkedHashMap<>());
                sections.add(current);
            } else if ((m = ENTRY.matcher(line)).matches()) {
                if (line.endsWith("\\")) {
                    throw new IllegalStateException(name + " line " + (i + 1) + ": continuation lines are not supported");
                }
                if (current == null) {
                    current = new Section("", new LinkedHashMap<>());
                    sections.add(current);
                }
                current.entries().put(m.group(1), m.group(2));
            }
        }
        return new ProfileSource(name, List.copyOf(sections));
    }

    Map<String, String> entries() {
        Map<String, String> all = new LinkedHashMap<>();
        sections.forEach(s -> all.putAll(s.entries()));
        return all;
    }

    Map<String, String> simEntries() {
        Map<String, String> sim = new LinkedHashMap<>();
        entries().forEach((k, v) -> {
            if (k.startsWith("sim.")) {
                sim.put(k, v);
            }
        });
        return sim;
    }
}
