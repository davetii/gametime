package software.daveturner.gametime.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** SimConfig.java read as text: statics with their section and line, tunable fields and their bounds. */
record SimConfigSource(List<Static> statics, Map<String, Tunable> tunables) {

    record Static(String section, String name, String type, String value, int line) {}

    record Tunable(String field, String type, String bounds) {}

    private static final Set<String> PRIMITIVES =
            Set.of("byte", "short", "int", "long", "float", "double", "boolean", "char");

    private static final Pattern SECTION = Pattern.compile("^\\s*//\\s*---\\s*(.*?)\\s*-*\\s*$");
    private static final Pattern STATIC = Pattern.compile(
            "^\\s*public static final ([\\w\\[\\]]+) ([A-Z][A-Z0-9_]*)\\s*=\\s*(.+?);\\s*(?://.*)?$");
    private static final Pattern FIELD = Pattern.compile("^\\s*private final ([\\w\\[\\]]+) (\\w+);");
    private static final Pattern CTOR_START = Pattern.compile("^\\s*public SimConfig\\(\\s*$");
    private static final Pattern PARAM = Pattern.compile("([\\w\\[\\]]+)\\s+(\\w+)\\s*(,|\\)\\s*\\{)\\s*$");
    private static final Pattern DECIMAL_MIN = Pattern.compile("@DecimalMin\\(\"([^\"]*)\"\\)");
    private static final Pattern DECIMAL_MAX = Pattern.compile("@DecimalMax\\(\"([^\"]*)\"\\)");
    private static final Pattern MIN = Pattern.compile("@Min\\((?:value\\s*=\\s*)?(-?\\d+)L?\\)");
    private static final Pattern POSITIVE = Pattern.compile("@Positive\\b");
    private static final Pattern SIZE = Pattern.compile("@Size\\(min\\s*=\\s*(\\d+),\\s*max\\s*=\\s*(\\d+)");

    static SimConfigSource parse(String source) {
        List<Static> statics = new ArrayList<>();
        Map<String, String> fieldTypes = new LinkedHashMap<>();
        Map<String, String> bounds = new LinkedHashMap<>();
        String section = "";
        boolean inBlockComment = false;
        boolean inConstructor = false;

        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (inBlockComment) {
                inBlockComment = !line.contains("*/");
                continue;
            }
            String trimmed = line.strip();
            if (trimmed.startsWith("/*")) {
                inBlockComment = !trimmed.substring(2).contains("*/");
                continue;
            }
            Matcher m;
            if ((m = SECTION.matcher(line)).matches()) {
                section = m.group(1);
            } else if ((m = STATIC.matcher(line)).matches()) {
                if (PRIMITIVES.contains(m.group(1))) {
                    statics.add(new Static(section, m.group(2), m.group(1), m.group(3), i + 1));
                }
            } else if ((m = FIELD.matcher(line)).find()) {
                fieldTypes.put(m.group(2), m.group(1));
            } else if (CTOR_START.matcher(line).matches()) {
                inConstructor = true;
            } else if (inConstructor && (m = PARAM.matcher(line)).find()) {
                bounds.put(m.group(2), bounds(line.substring(0, m.start())));
                inConstructor = m.group(3).equals(",");
            }
        }

        if (!fieldTypes.keySet().equals(bounds.keySet())) {
            throw new IllegalStateException("SimConfig's private final fields " + fieldTypes.keySet()
                    + " do not match its constructor parameters " + bounds.keySet());
        }
        Map<String, Tunable> tunables = new LinkedHashMap<>();
        fieldTypes.forEach((name, type) -> tunables.put(name, new Tunable(name, type, bounds.get(name))));
        return new SimConfigSource(List.copyOf(statics), tunables);
    }

    static String bounds(String annotations) {
        List<String> parts = new ArrayList<>();
        Matcher min = DECIMAL_MIN.matcher(annotations);
        Matcher max = DECIMAL_MAX.matcher(annotations);
        boolean hasMin = min.find();
        boolean hasMax = max.find();
        if (hasMin && hasMax) {
            parts.add("[" + min.group(1) + ", " + max.group(1) + "]");
        } else if (hasMin) {
            parts.add("≥ " + min.group(1));
        } else if (hasMax) {
            parts.add("≤ " + max.group(1));
        }
        Matcher m;
        if ((m = MIN.matcher(annotations)).find()) {
            parts.add("≥ " + m.group(1));
        }
        if (POSITIVE.matcher(annotations).find()) {
            parts.add("> 0");
        }
        if ((m = SIZE.matcher(annotations)).find()) {
            parts.add(m.group(1).equals(m.group(2))
                    ? "exactly " + m.group(1) + " entries"
                    : m.group(1) + "–" + m.group(2) + " entries");
        }
        return String.join("; ", parts);
    }

    static String kebab(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }
}
