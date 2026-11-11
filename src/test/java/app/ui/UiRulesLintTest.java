package app.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI consistency linter - section 8.2 / H-4 of rules/07-ui-consistency-rules.md.
 *
 * Scans every Java file under app/ui and every stylesheet under css/.
 * <ul>
 *   <li>Phase 3 screens and the shared workspace components must have 0 violations.</li>
 *   <li>Older files are held to a per-file baseline (src/test/resources/ui-lint-baseline.txt): the
 *       number of violations may only go down. A file that is not in the baseline - any new screen -
 *       must have 0.</li>
 * </ul>
 * A line can be excused with a trailing comment {@code // ui-lint:allow <reason>} (Java) or
 * {@code /* ui-lint:allow <reason> *}{@code /} (CSS). Regenerate the baseline, after lowering counts
 * only, with {@code -Dui.lint.writeBaseline=true}.
 */
public class UiRulesLintTest {

    private record Rule(String id, Pattern pattern) {}

    private static final List<Rule> JAVA_RULES = List.of(
            new Rule("R-UI-10 hardcoded color", Pattern.compile("Color\\.web\\(|Color\\.rgb\\(|Color\\.[A-Z][A-Z_]{2,}\\b|\"#[0-9a-fA-F]{3,8}\"")),
            new Rule("R-UI-11 Font.font", Pattern.compile("Font\\.font\\(")),
            new Rule("R-UI-12 fixed size in px", Pattern.compile("\\.set(Pref|Min|Max)(Height|Width)\\(\\s*[1-9]|\\.setFixedCellSize\\(\\s*\\d")),
            new Rule("R-UI-15 setIconSize(number)", Pattern.compile("setIconSize\\(\\s*\\d")),
            new Rule("R-UI-19 setStyle inline", Pattern.compile("\\.setStyle\\(")),
            new Rule("R-UI-11 TextArea (code must use EmbeddedMonaco)", Pattern.compile("new TextArea\\("))
    );

    /** Emoji, arrows, dingbats and box symbols used as icons inside a string literal (R-UI-15). */
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern SYMBOL = Pattern.compile("[\\u2190-\\u2BFF\\uD83C-\\uDBFF]");

    private static final List<Rule> CSS_RULES = List.of(
            new Rule("R-UI-10 hex color", Pattern.compile("#[0-9a-fA-F]{3,8}\\b")),
            new Rule("R-UI-10 rgb/rgba", Pattern.compile("rgba?\\(")),
            new Rule("R-UI-11 font-size in px/pt", Pattern.compile("-fx-font-size:\\s*[\\d.]+(px|pt)")),
            new Rule("R-UI-12 fixed height in px", Pattern.compile("-fx-(pref|min|max)-height:\\s*\\d+(\\.\\d+)?(px)?\\s*;")),
            new Rule("R-UI-15 -fx-icon-size", Pattern.compile("-fx-icon-size:\\s*\\d"))
    );

    /** Token definitions: the only stylesheets where colour literals and the base font size belong. */
    private static final Set<String> TOKEN_SHEETS = Set.of(
            "ide-vars.css", "light-theme.css", "dark-theme.css", "base.css");

    /** Must be clean - no baseline allowed. */
    private static final List<String> STRICT = List.of(
            "app/ui/designer/", "app/ui/dataeditor/", "app/ui/search/", "app/ui/compare/",
            "app/ui/dependency/", "app/ui/visualizer/",
            "app/ui/dialogs/SaveDesignDialog.java", "app/ui/dialogs/PreviewDataChangesDialog.java",
            "app/ui/components/WorkspaceToolbar.java", "app/ui/components/WorkspaceFooter.java",
            "app/ui/components/PaneHeader.java", "app/ui/components/SegmentedToggle.java",
            "app/ui/components/FilterChip.java", "app/ui/components/AppButton.java",
            "app/ui/components/IconButton.java", "app/ui/components/ZoomableGraphPane.java",
            "css/table-designer.css", "css/table-data-editor.css", "css/object-content-search.css",
            "css/schema-compare.css", "css/dependency-graph.css", "css/sql-visualizer.css");

    private static final Path JAVA_ROOT = Path.of("src/main/java/app/ui");
    private static final Path CSS_ROOT = Path.of("src/main/resources/css");
    private static final Path BASELINE = Path.of("src/test/resources/ui-lint-baseline.txt");

    @Test
    void uiFilesFollowTheConsistencyRules() throws IOException {
        Map<String, List<String>> found = new TreeMap<>();
        scan(JAVA_ROOT, ".java", found);
        scan(CSS_ROOT, ".css", found);

        if (Boolean.getBoolean("ui.lint.writeBaseline")) {
            writeBaseline(found);
        }
        Map<String, Integer> baseline = readBaseline();

        List<String> failures = new ArrayList<>();
        List<String> canLower = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : found.entrySet()) {
            String file = e.getKey();
            int count = e.getValue().size();
            int allowed = isStrict(file) ? 0 : baseline.getOrDefault(file, 0);
            if (count > allowed) {
                failures.add(file + ": " + count + " violations (allowed " + allowed + ")");
                e.getValue().stream().limit(40).forEach(v -> failures.add("    " + v));
            } else if (count < allowed) {
                canLower.add(file + ": " + allowed + " -> " + count);
            }
        }
        for (Map.Entry<String, Integer> b : baseline.entrySet()) {
            if (!found.containsKey(b.getKey()) && b.getValue() > 0) canLower.add(b.getKey() + ": " + b.getValue() + " -> 0");
        }
        if (!canLower.isEmpty()) {
            System.out.println("UI lint: baseline can be lowered (run with -Dui.lint.writeBaseline=true):");
            canLower.forEach(s -> System.out.println("  " + s));
        }
        if (!failures.isEmpty()) {
            System.err.println("=== UI consistency rule violations (rules/07-ui-consistency-rules.md) ===");
            failures.forEach(System.err::println);
        }
        assertTrue(failures.isEmpty(), "UI consistency violations:\n" + String.join("\n", failures));
    }

    private static boolean isStrict(String file) {
        for (String s : STRICT) {
            if (file.contains(s)) return true;
        }
        return false;
    }

    private static void scan(Path root, String ext, Map<String, List<String>> out) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path p : (Iterable<Path>) stream.filter(Files::isRegularFile).filter(f -> f.toString().endsWith(ext))::iterator) {
                String key = p.toString().replace('\\', '/');
                key = key.substring(key.indexOf("src/main/") + "src/main/".length());
                key = key.replaceFirst("^(java|resources)/", "");
                if (ext.equals(".css") && TOKEN_SHEETS.contains(p.getFileName().toString())) continue;
                List<String> violations = ext.equals(".java") ? checkJava(p) : checkCss(p);
                if (!violations.isEmpty()) out.put(key, violations);
            }
        }
    }

    private static List<String> checkJava(Path p) throws IOException {
        List<String> out = new ArrayList<>();
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String line = raw.trim();
            if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) continue;
            if (raw.contains("ui-lint:allow")) continue;
            String code = stripTrailingComment(raw);
            for (Rule r : JAVA_RULES) {
                if (r.pattern().matcher(code).find()) out.add((i + 1) + " [" + r.id() + "] " + line);
            }
            var lit = STRING_LITERAL.matcher(code);
            while (lit.find()) {
                if (SYMBOL.matcher(lit.group()).find()) {
                    out.add((i + 1) + " [R-UI-15 symbol or emoji used as icon/text] " + line);
                    break;
                }
            }
        }
        return out;
    }

    private static List<String> checkCss(Path p) throws IOException {
        List<String> out = new ArrayList<>();
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        boolean inComment = false;
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i);
            String line = raw.trim();
            if (inComment) {
                if (line.contains("*/")) inComment = false;
                continue;
            }
            if (line.startsWith("/*")) {
                if (!line.contains("*/")) inComment = true;
                continue;
            }
            if (raw.contains("ui-lint:allow")) continue;
            if (line.contains("#e81123")) continue; // approved exception: window Close button
            for (Rule r : CSS_RULES) {
                if (r.pattern().matcher(line).find()) out.add((i + 1) + " [" + r.id() + "] " + line);
            }
        }
        return out;
    }

    /** Drops a trailing line comment so a commented-out example does not count (string contents are kept). */
    private static String stripTrailingComment(String line) {
        boolean inString = false;
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '"') inString = !inString;
            if (!inString && c == '/' && line.charAt(i + 1) == '/') return line.substring(0, i);
        }
        return line;
    }

    private static Map<String, Integer> readBaseline() throws IOException {
        Map<String, Integer> map = new LinkedHashMap<>();
        if (!Files.exists(BASELINE)) return map;
        for (String l : Files.readAllLines(BASELINE, StandardCharsets.UTF_8)) {
            String s = l.trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            int eq = s.lastIndexOf('=');
            if (eq > 0) map.put(s.substring(0, eq).trim(), Integer.parseInt(s.substring(eq + 1).trim()));
        }
        return map;
    }

    private static void writeBaseline(Map<String, List<String>> found) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# UI lint baseline - legacy files only. Counts may only go down; never add a file by hand.");
        lines.add("# Regenerate: mvnw -o test -Dtest=UiRulesLintTest \"-DargLine=-Dui.lint.writeBaseline=true\"");
        for (Map.Entry<String, List<String>> e : found.entrySet()) {
            if (isStrict(e.getKey())) continue;
            lines.add(e.getKey() + "=" + e.getValue().size());
        }
        Files.createDirectories(BASELINE.getParent());
        Files.write(BASELINE, lines, StandardCharsets.UTF_8);
    }
}
