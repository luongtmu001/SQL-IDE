package app.services.theme;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * T3 (theme analysis 2026-11-06): turns every theme of a VS Code {@code .vsix} into an app theme:
 * {@code <themesDir>/<id>/theme.json} (what the registry lists), {@code theme.css} (the AtlantaFX {@code -color-*}
 * palette and the app's {@code -ide-*} region colours on {@code .root}) and {@code monaco.json} (the editor theme).
 * <ul>
 *   <li>Dark / light: {@code uiTheme} of package.json, then the theme's {@code type}, then the brightness of
 *       {@code editor.background} ({@code hc-light} is light, unlike LLTool).</li>
 *   <li>Theme files are JSONC (comments, trailing commas: Synthwave '84 has one) and may {@code include} a parent.</li>
 *   <li>Every variable takes the first key of its chain that the theme has; a colour that does not stand out
 *       from the background (Synthwave's {@code focusBorder} is almost the editor colour) is skipped; what is still
 *       missing is mixed from the theme's own background and text colours.</li>
 *   <li>Popup / menu backgrounds with alpha are flattened on the editor background (JavaFX popups are opaque).</li>
 *   <li>Editor token colours: TextMate rules looked up with full SQL scopes ({@code string.quoted.single.sql}),
 *       since a theme may have no rule for the short name ({@code string}).</li>
 * </ul>
 * Explorer object icons keep the app's own palette (user decision: {@code symbolIcon.*} would lose the
 * difference between tables and columns).
 */
public final class VsixThemeImporter {

    private static final Logger log = LoggerFactory.getLogger(VsixThemeImporter.class);
    private static final JsonMapper JSONC = JsonMapper.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS, JsonReadFeature.ALLOW_TRAILING_COMMA,
                    JsonReadFeature.ALLOW_SINGLE_QUOTES, JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
            .build();

    /** One imported theme, or one that could not be imported ({@code error} set). */
    public record Result(String id, String label, ThemeDefinition.Kind kind, boolean highContrast, Path folder,
                         String error) {
        public boolean ok() {
            return error == null;
        }
    }

    private VsixThemeImporter() {
    }

    // ================================================================== import

    /** Imports every theme of the package into {@code themesDir}; a theme imported before is replaced. */
    public static List<Result> importVsix(Path vsix, Path themesDir) throws IOException {
        List<Result> out = new ArrayList<>();
        try (ZipFile zip = new ZipFile(vsix.toFile(), StandardCharsets.UTF_8)) {
            ZipEntry pkgEntry = zip.getEntry("extension/package.json");
            if (pkgEntry == null) throw new IOException("Không phải gói extension VS Code (thiếu extension/package.json).");
            JsonNode pkg = JSONC.readTree(zip.getInputStream(pkgEntry));
            JsonNode themes = pkg.path("contributes").path("themes");
            if (!themes.isArray() || themes.isEmpty()) throw new IOException("Gói không có theme màu nào (contributes.themes).");
            String source = "vsix:" + pkg.path("publisher").asText("unknown") + "." + pkg.path("name").asText("theme")
                    + "@" + pkg.path("version").asText("0");
            String family = familyName(zip, pkg);
            for (JsonNode t : themes) {
                String label = t.path("label").asText(t.path("id").asText("Theme"));
                try {
                    String path = "extension/" + stripDot(t.path("path").asText(""));
                    ObjectNode theme = loadTheme(zip, path, 0);
                    Map<String, Integer> colors = colors(theme);
                    String ui = t.path("uiTheme").asText("");
                    ThemeDefinition.Kind kind = kindOf(ui, theme.path("type").asText(""), colors.get("editor.background"));
                    boolean hc = ui.startsWith("hc") || theme.path("type").asText("").equalsIgnoreCase("hc");
                    String id = slug(label);
                    Path folder = themesDir.resolve(id);
                    Files.createDirectories(folder);
                    Palette p = Palette.of(colors, kind, hc);
                    write(folder.resolve("theme.css"), css(label, source, p, tokenColors(theme)));
                    write(folder.resolve("monaco.json"), monaco(theme, colors, kind, hc, p));
                    ObjectNode meta = JSONC.createObjectNode();
                    meta.put("id", id);
                    meta.put("label", label);
                    meta.put("kind", kind.name());
                    meta.put("highContrast", hc);
                    meta.put("source", source);
                    meta.put("family", family);
                    meta.put("uiTheme", ui);
                    meta.put("importedAt", Instant.now().toString());
                    meta.put("css", "theme.css");
                    meta.put("monaco", "monaco.json");
                    write(folder.resolve("theme.json"), JSONC.writerWithDefaultPrettyPrinter().writeValueAsString(meta));
                    out.add(new Result(id, label, kind, hc, folder, null));
                    log.info("Imported theme '{}' ({}{}) from {}", label, kind, hc ? ", high contrast" : "", vsix.getFileName());
                } catch (Exception e) {
                    log.warn("Theme '{}' of {} not imported: {}", label, vsix.getFileName(), e.getMessage());
                    out.add(new Result(null, label, null, false, null, e.getMessage()));
                }
            }
        }
        ThemeRegistry.getInstance().reload();
        MonacoThemes.forgetImported();
        return out;
    }

    /** Removes an imported theme's folder. */
    public static void remove(Path themesDir, String id) throws IOException {
        removeAll(themesDir, List.of(id));
    }

    /** T4: removes several themes (a whole package from Settings), then reloads the list once. */
    public static void removeAll(Path themesDir, java.util.Collection<String> ids) throws IOException {
        IOException first = null;
        for (String id : ids) {
            Path folder = themesDir.resolve(slug(id));
            if (!Files.isDirectory(folder)) continue;
            try (var walk = Files.walk(folder)) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            } catch (IOException e) {
                if (first == null) first = e;
            }
        }
        ThemeRegistry.getInstance().reload();
        MonacoThemes.forgetImported();
        if (first != null) throw first;
    }

    /**
     * Name of the package the themes are grouped under: {@code displayName} ("GitHub Theme"), resolved from
     * {@code package.nls.json} when it is a "%key%" placeholder; else {@code name}.
     */
    static String familyName(ZipFile zip, JsonNode pkg) {
        String name = pkg.path("displayName").asText("").trim();
        if (name.length() > 2 && name.startsWith("%") && name.endsWith("%")) {
            String key = name.substring(1, name.length() - 1);
            name = "";
            ZipEntry nls = zip.getEntry("extension/package.nls.json");
            if (nls != null) {
                try {
                    JsonNode v = JSONC.readTree(zip.getInputStream(nls)).path(key);
                    name = (v.isObject() ? v.path("message") : v).asText("").trim();
                } catch (Exception ignored) {
                    name = "";
                }
            }
        }
        if (name.isEmpty()) name = pkg.path("name").asText("VS Code").trim();
        return name.length() > 60 ? name.substring(0, 60) : name;
    }

    /** Reads a theme file and its {@code include} chain (parent first, child wins). */
    static ObjectNode loadTheme(ZipFile zip, String path, int depth) throws IOException {
        if (depth > 5) throw new IOException("include lồng quá sâu: " + path);
        ZipEntry e = zip.getEntry(normalize(path));
        if (e == null) throw new IOException("Không tìm thấy file theme " + path);
        if (path.toLowerCase(Locale.ROOT).endsWith(".tmtheme")) throw new IOException("Theme dạng .tmTheme chưa được hỗ trợ");
        JsonNode node = JSONC.readTree(zip.getInputStream(e));
        if (!(node instanceof ObjectNode theme)) throw new IOException("File theme không phải đối tượng JSON: " + path);
        String include = theme.path("include").asText(null);
        if (include == null || include.isBlank()) return theme;
        String parentPath = parentDir(path) + stripDot(include);
        ObjectNode parent = loadTheme(zip, parentPath, depth + 1);
        return merge(parent, theme);
    }

    static ObjectNode merge(ObjectNode parent, ObjectNode child) {
        ObjectNode out = parent.deepCopy();
        child.fields().forEachRemaining(f -> {
            if (f.getKey().equals("colors") && f.getValue().isObject() && out.get("colors") instanceof ObjectNode pc) {
                pc.setAll((ObjectNode) f.getValue());
            } else if (f.getKey().equals("tokenColors") && f.getValue().isArray() && out.get("tokenColors") instanceof ArrayNode pt) {
                pt.addAll((ArrayNode) f.getValue()); // child rules after the parent's: they win
            } else if (!f.getKey().equals("include")) {
                out.set(f.getKey(), f.getValue());
            }
        });
        out.remove("include");
        return out;
    }

    // ================================================================== dark / light

    static ThemeDefinition.Kind kindOf(String uiTheme, String type, Integer editorBg) {
        String u = uiTheme == null ? "" : uiTheme.toLowerCase(Locale.ROOT);
        if (u.equals("vs") || u.equals("hc-light")) return ThemeDefinition.Kind.LIGHT;
        if (u.equals("vs-dark") || u.equals("hc-black")) return ThemeDefinition.Kind.DARK;
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (t.equals("light") || t.equals("hc-light")) return ThemeDefinition.Kind.LIGHT;
        if (t.equals("dark") || t.equals("hc") || t.equals("hc-black")) return ThemeDefinition.Kind.DARK;
        if (editorBg != null) {
            int r = (editorBg >>> 24) & 255, g = (editorBg >>> 16) & 255, b = (editorBg >>> 8) & 255;
            return 0.299 * r + 0.587 * g + 0.114 * b < 128 ? ThemeDefinition.Kind.DARK : ThemeDefinition.Kind.LIGHT;
        }
        return ThemeDefinition.Kind.DARK;
    }

    // ================================================================== colours

    /** All colours of the theme, as RGBA ints; values that are not hex colours are dropped. */
    static Map<String, Integer> colors(JsonNode theme) {
        Map<String, Integer> out = new LinkedHashMap<>();
        theme.path("colors").fields().forEachRemaining(f -> {
            Integer c = parse(f.getValue().asText(null));
            if (c != null) out.put(f.getKey(), c);
        });
        return out;
    }

    /** {@code #rgb}, {@code #rgba}, {@code #rrggbb}, {@code #rrggbbaa} -> 0xRRGGBBAA; null otherwise. */
    static Integer parse(String s) {
        if (s == null) return null;
        String h = s.trim();
        if (!h.startsWith("#")) return null;
        h = h.substring(1);
        if (!h.matches("[0-9a-fA-F]+")) return null;
        if (h.length() == 3 || h.length() == 4) {
            StringBuilder b = new StringBuilder();
            for (char c : h.toCharArray()) b.append(c).append(c);
            h = b.toString();
        }
        if (h.length() == 6) h = h + "ff";
        if (h.length() != 8) return null;
        return (int) Long.parseLong(h, 16);
    }

    static int r(int c) { return (c >>> 24) & 255; }
    static int g(int c) { return (c >>> 16) & 255; }
    static int b(int c) { return (c >>> 8) & 255; }
    static int a(int c) { return c & 255; }

    static int rgba(int r, int g, int b, int a) {
        return (r & 255) << 24 | (g & 255) << 16 | (b & 255) << 8 | (a & 255);
    }

    static int withAlpha(int c, double alpha) {
        return rgba(r(c), g(c), b(c), (int) Math.round(alpha * 255));
    }

    /** {@code top} drawn over an opaque {@code bottom}. */
    static int over(int top, int bottom) {
        double t = a(top) / 255.0;
        return rgba((int) Math.round(r(top) * t + r(bottom) * (1 - t)), (int) Math.round(g(top) * t + g(bottom) * (1 - t)),
                (int) Math.round(b(top) * t + b(bottom) * (1 - t)), 255);
    }

    /** {@code share} of {@code a} mixed into {@code b} (opaque result). */
    static int mix(int a, int b, double share) {
        return rgba((int) Math.round(r(a) * share + r(b) * (1 - share)), (int) Math.round(g(a) * share + g(b) * (1 - share)),
                (int) Math.round(b(a) * share + b(b) * (1 - share)), 255);
    }

    static double luminance(int c) {
        double[] v = {r(c) / 255.0, g(c) / 255.0, b(c) / 255.0};
        for (int i = 0; i < 3; i++) v[i] = v[i] <= 0.03928 ? v[i] / 12.92 : Math.pow((v[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * v[0] + 0.7152 * v[1] + 0.0722 * v[2];
    }

    /** WCAG contrast of {@code fg} (alpha flattened on {@code bg}) against {@code bg}. */
    static double contrast(int fg, int bg) {
        double l1 = luminance(over(fg, bg)), l2 = luminance(bg | 255);
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }

    /** CSS value: {@code #rrggbb}, or {@code rgba(...)} when the colour has alpha. */
    static String cssColor(int c) {
        if (a(c) == 255) return String.format("#%02x%02x%02x", r(c), g(c), b(c));
        return String.format(Locale.ROOT, "rgba(%d, %d, %d, %.3f)", r(c), g(c), b(c), a(c) / 255.0);
    }

    /** Monaco value: {@code #rrggbb} or {@code #rrggbbaa}. */
    static String hex(int c) {
        return a(c) == 255 ? String.format("#%02x%02x%02x", r(c), g(c), b(c)) : String.format("#%02x%02x%02x%02x", r(c), g(c), b(c), a(c));
    }

    // ================================================================== palette

    /** The variables written to theme.css, computed once per theme. */
    static final class Palette {
        final Map<String, Integer> vars = new LinkedHashMap<>();
        final Map<String, String> from = new LinkedHashMap<>(); // variable -> VS Code key or "derived"

        static Palette of(Map<String, Integer> c, ThemeDefinition.Kind kind, boolean hc) {
            Palette p = new Palette();
            boolean dark = kind == ThemeDefinition.Kind.DARK;
            int bg = opaque(c.get("editor.background"), dark ? 0x0d1117ff : 0xffffffff, dark ? 0x0d1117ff : 0xffffffff);
            int fgDefault = dark ? 0xe6edf3ff : 0x1f2328ff;
            p.put("-color-bg-default", bg, "editor.background");
            int fg = p.pick("-color-fg-default", c, bg, 4.5, fgDefault, "editor.foreground", "foreground", "sideBar.foreground");
            p.pick("-color-fg-muted", c, bg, 3.0, mix(fg, bg, 0.65), "descriptionForeground", "icon.foreground", "tab.inactiveForeground");
            p.pick("-color-fg-subtle", c, bg, 2.0, mix(fg, bg, 0.45), "input.placeholderForeground", "editorLineNumber.foreground");
            int inset = p.pickBg("-color-bg-inset", c, bg, mix(fg, bg, dark ? 0.0 : 0.03), dark ? darker(bg) : mix(fg, bg, 0.03),
                    "sideBar.background", "panel.background", "editorGroupHeader.tabsBackground", "activityBar.background");
            int overlay = p.pickBg("-color-bg-overlay", c, bg, bg, mix(fg, bg, 0.05),
                    "editorWidget.background", "dropdown.background", "notifications.background", "quickInput.background");
            p.put("-color-bg-subtle", mix(fg, bg, 0.05), "derived");
            p.put("-color-bg-muted", mix(fg, bg, 0.10), "derived");
            p.pick("-color-border-default", c, bg, 1.2, mix(fg, bg, 0.20),
                    "sideBar.border", "editorGroup.border", "input.border", "dropdown.border", "panel.border", "tab.border");
            p.pick("-color-border-muted", c, bg, 1.1, mix(fg, bg, 0.12), "tree.indentGuidesStroke", "editorIndentGuide.background");
            p.put("-color-border-subtle", withAlpha(fg, 0.10), "derived");
            int accentFg = p.pick("-color-accent-fg", c, bg, 3.0, dark ? 0x2f81f7ff : 0x0969daff,
                    "textLink.foreground", "list.highlightForeground", "editorCursor.foreground", "focusBorder", "progressBar.background");
            int accent = p.pick("-color-accent-emphasis", c, bg, 2.0, accentFg,
                    "focusBorder", "button.background", "badge.background", "progressBar.background", "activityBarBadge.background");
            p.put("-color-accent-muted", withAlpha(accent, 0.40), "derived");
            p.put("-color-accent-subtle", withAlpha(accent, 0.15), "derived");
            int onAccent = p.pick("-color-fg-emphasis", c, accent | 255, 3.0,
                    contrast(0xffffffff, accent | 255) >= contrast(0x000000ff, accent | 255) ? 0xffffffff : 0x000000ff,
                    "button.foreground", "badge.foreground", "activityBarBadge.foreground");
            int neutral = p.vars.get("-color-fg-subtle");
            p.put("-color-neutral-emphasis", neutral, "derived");
            p.put("-color-neutral-muted", withAlpha(neutral, 0.40), "derived");
            p.put("-color-neutral-subtle", withAlpha(neutral, 0.10), "derived");
            p.status("success", c, bg, dark ? 0x3fb950ff : 0x1a7f37ff,
                    "gitDecoration.addedResourceForeground", "terminal.ansiGreen", "editorGutter.addedBackground");
            p.status("warning", c, bg, dark ? 0xd29922ff : 0x9a6700ff,
                    "editorWarning.foreground", "notificationsWarningIcon.foreground", "gitDecoration.modifiedResourceForeground", "terminal.ansiYellow");
            p.status("danger", c, bg, dark ? 0xf85149ff : 0xcf222eff,
                    "errorForeground", "editorError.foreground", "notificationsErrorIcon.foreground", "gitDecoration.deletedResourceForeground", "terminal.ansiRed");

            // ---- regions (-ide-*)
            p.put("-ide-app-bg", inset, p.from.get("-color-bg-inset"));
            p.put("-ide-editor-bg", bg, "editor.background");
            p.put("-ide-grid-bg", bg, "editor.background");
            int side = p.pickBg("-ide-explorer-bg", c, bg, bg, bg, "sideBar.background");
            p.pickBg("-ide-explorer-header-bg", c, bg, side, side, "sideBarSectionHeader.background", "sideBar.background");
            p.pickBg("-ide-rail-bg", c, bg, side, side, "activityBar.background", "sideBar.background");
            int tabs = p.pickBg("-ide-tab-strip-bg", c, bg, inset, inset, "editorGroupHeader.tabsBackground", "tab.inactiveBackground");
            p.pickBg("-ide-toolbar-bg", c, bg, bg, bg, "tab.activeBackground", "editor.background");
            int title = p.pickBg("-ide-titlebar-bg", c, bg, tabs, tabs, "titleBar.activeBackground");
            p.pick("-ide-titlebar-fg", c, title, 4.5, fg, "titleBar.activeForeground", "foreground");
            int tabActive = p.pickBg("-ide-tab-active-bg", c, bg, bg, bg, "tab.activeBackground", "editor.background");
            p.pickAlpha("-ide-tab-hover-bg", c, withAlpha(fg, 0.08), "tab.hoverBackground");
            p.pick("-ide-tab-active-fg", c, tabActive, 4.5, fg, "tab.activeForeground", "foreground");
            p.pick("-ide-tab-inactive-fg", c, tabs, 3.0, p.vars.get("-color-fg-muted"), "tab.inactiveForeground", "descriptionForeground");
            p.pick("-ide-tab-active-border", c, tabs, 1.5, accent, "tab.activeBorderTop", "tab.activeBorder", "focusBorder");
            p.pick("-ide-sash-hover", c, bg, 1.5, accent, "sash.hoverBorder", "focusBorder");
            p.pickAlpha("-ide-list-hover-bg", c, withAlpha(fg, 0.08), "list.hoverBackground");
            int sel = p.pickAlpha("-ide-list-selection-bg", c, withAlpha(accent, 0.25), "list.activeSelectionBackground", "list.inactiveSelectionBackground");
            p.put("-ide-grid-row-alt-bg", mix(fg, bg, 0.03), "derived");
            p.pickBg("-ide-grid-header-bg", c, bg, inset, inset, "editorGroupHeader.tabsBackground", "sideBarSectionHeader.background", "panel.background");
            p.pick("-ide-grid-rownum-fg", c, bg, 2.0, p.vars.get("-color-fg-muted"), "editorLineNumber.foreground");
            p.put("-ide-grid-selection-bg", sel, p.from.get("-ide-list-selection-bg"));
            p.put("-ide-grid-focus-border", accent, p.from.get("-color-accent-emphasis"));
            p.put("-ide-grid-null-fg", p.vars.get("-color-fg-muted"), "derived");
            p.pickBg("-ide-panel-header-bg", c, bg, inset, inset, "panel.background", "editorGroupHeader.tabsBackground");
            p.pick("-ide-panel-tab-active-border", c, bg, 1.5, accent, "panelTitle.activeBorder", "focusBorder");
            p.put("-ide-menu-bg", flatten(p.firstOf(c, "menu.background", "editorWidget.background", "dropdown.background"), bg, overlay),
                    p.lastKey);
            p.pickAlpha("-ide-menu-selection-bg", c, withAlpha(accent, 0.25), "menu.selectionBackground", "list.activeSelectionBackground");
            p.put("-ide-toast-bg", flatten(p.firstOf(c, "notifications.background", "editorWidget.background"), bg, overlay), p.lastKey);
            int status = p.pickBg("-ide-statusbar-bg", c, bg, bg, bg, "statusBar.background");
            p.pick("-ide-statusbar-fg", c, status, 4.5,
                    contrast(0xffffffff, status) >= contrast(0x000000ff, status) ? 0xffffffff : 0x000000ff, "statusBar.foreground");
            p.put("-ide-statusbar-busy", p.vars.get("-color-warning-fg"), "derived");
            int term = p.pickBg("-ide-terminal-bg", c, bg, inset, inset, "terminal.background", "panel.background");
            p.pick("-ide-terminal-fg", c, term, 4.5, fg, "terminal.foreground", "foreground");
            p.put("-ide-terminal-input-bg", mix(fg, term, 0.06), "derived");
            p.put("-ide-terminal-border", p.vars.get("-color-border-default"), "derived");
            p.put("-ide-placeholder-bg", bg, "editor.background");
            if (hc) p.put("-ide-grid-focus-border", accent, "focusBorder"); // explicit in high contrast
            return p;
        }

        private String lastKey = "derived";

        void put(String var, int value, String source) {
            vars.put(var, value);
            from.put(var, source);
        }

        /** First key whose colour stands out from {@code bg} by at least {@code minContrast}; else {@code fallback}. */
        int pick(String var, Map<String, Integer> c, int bg, double minContrast, int fallback, String... keys) {
            for (String k : keys) {
                Integer v = c.get(k);
                if (v != null && contrast(v, bg | 255) >= minContrast) {
                    put(var, v, k);
                    return v;
                }
            }
            put(var, fallback, "derived");
            return fallback;
        }

        /** A background: first key present, alpha flattened on {@code bg}; else {@code fallback}. */
        int pickBg(String var, Map<String, Integer> c, int bg, int unused, int fallback, String... keys) {
            for (String k : keys) {
                Integer v = c.get(k);
                if (v != null) {
                    int o = over(v, bg | 255);
                    put(var, o, k);
                    return o;
                }
            }
            put(var, fallback | 255, "derived");
            return fallback | 255;
        }

        /** An overlay drawn on varying backgrounds (hover, selection): alpha kept. */
        int pickAlpha(String var, Map<String, Integer> c, int fallback, String... keys) {
            for (String k : keys) {
                Integer v = c.get(k);
                if (v != null) {
                    put(var, v, k);
                    return v;
                }
            }
            put(var, fallback, "derived");
            return fallback;
        }

        Integer firstOf(Map<String, Integer> c, String... keys) {
            for (String k : keys) {
                if (c.containsKey(k)) {
                    lastKey = k;
                    return c.get(k);
                }
            }
            lastKey = "derived";
            return null;
        }

        void status(String name, Map<String, Integer> c, int bg, int fallback, String... keys) {
            int fg = pick("-color-" + name + "-fg", c, bg, 3.0, fallback, keys);
            int solid = over(fg, bg | 255);
            put("-color-" + name + "-emphasis", solid, "derived");
            put("-color-" + name + "-muted", withAlpha(solid, 0.40), "derived");
            put("-color-" + name + "-subtle", withAlpha(solid, 0.15), "derived");
        }

        private static int opaque(Integer v, int under, int fallback) {
            return v == null ? fallback : over(v, under);
        }

        private static int flatten(Integer v, int bg, int fallback) {
            return v == null ? fallback : over(v, bg | 255);
        }

        private static int darker(int c) {
            return mix(0x000000ff, c, 0.35);
        }
    }

    // ================================================================== editor tokens

    /** Monaco SQL token -> TextMate scopes tried in order (full SQL scopes first). */
    static final Map<String, String[]> TOKEN_SCOPES = new LinkedHashMap<>();

    static {
        TOKEN_SCOPES.put("keyword", new String[]{"keyword.other.DML.sql", "keyword.other.sql", "keyword.control.sql", "keyword.control", "keyword"});
        TOKEN_SCOPES.put("operator", new String[]{"keyword.operator.comparison.sql", "keyword.operator.sql", "keyword.operator", "keyword"});
        TOKEN_SCOPES.put("predefined", new String[]{"support.function.aggregate.sql", "support.function.sql", "support.function", "entity.name.function"});
        TOKEN_SCOPES.put("string", new String[]{"string.quoted.single.sql", "string.quoted.single", "string.quoted", "string"});
        TOKEN_SCOPES.put("number", new String[]{"constant.numeric.sql", "constant.numeric", "constant"});
        TOKEN_SCOPES.put("comment", new String[]{"comment.line.double-dash.sql", "comment.line", "comment"});
        TOKEN_SCOPES.put("variable", new String[]{"variable.other.sql", "variable.other", "variable"});
        TOKEN_SCOPES.put("type", new String[]{"storage.type.sql", "storage.type", "support.type"});
        TOKEN_SCOPES.put("constant", new String[]{"constant.language.sql", "constant.language", "constant"});
    }

    /** A TextMate rule's colour and style. */
    record TokenStyle(Integer foreground, String fontStyle) {
    }

    /** Scope selector -> style, in file order (later rules win on equal specificity). */
    static List<Map.Entry<String, TokenStyle>> rules(JsonNode theme) {
        List<Map.Entry<String, TokenStyle>> out = new ArrayList<>();
        for (JsonNode r : theme.path("tokenColors")) {
            JsonNode s = r.path("settings");
            TokenStyle st = new TokenStyle(parse(s.path("foreground").asText(null)), s.path("fontStyle").asText(null));
            JsonNode scope = r.get("scope");
            List<String> sel = new ArrayList<>();
            if (scope == null) sel.add(""); // global defaults
            else if (scope.isArray()) scope.forEach(x -> sel.add(x.asText()));
            else for (String x : scope.asText().split(",")) sel.add(x);
            for (String x : sel) out.add(Map.entry(x.trim(), st));
        }
        return out;
    }

    /** Best rule for a scope: the longest matching selector (prefix by dots), the later one on a tie. */
    static TokenStyle resolve(List<Map.Entry<String, TokenStyle>> rules, String scope) {
        TokenStyle best = null;
        int bestLen = -1;
        for (var e : rules) {
            String sel = e.getKey();
            if (sel.isEmpty() || sel.contains(" ") || e.getValue().foreground() == null) continue;
            if (scope.equals(sel) || scope.startsWith(sel + ".")) {
                int len = sel.split("\\.").length;
                if (len >= bestLen) {
                    bestLen = len;
                    best = e.getValue();
                }
            }
        }
        return best;
    }

    static Map<String, TokenStyle> tokenColors(JsonNode theme) {
        var rules = rules(theme);
        Map<String, TokenStyle> out = new LinkedHashMap<>();
        TOKEN_SCOPES.forEach((token, scopes) -> {
            for (String s : scopes) {
                TokenStyle st = resolve(rules, s);
                if (st != null) {
                    out.put(token, st);
                    break;
                }
            }
        });
        return out;
    }

    static String monaco(JsonNode theme, Map<String, Integer> c, ThemeDefinition.Kind kind, boolean hc, Palette p) {
        ObjectNode m = JSONC.createObjectNode();
        // vs / vs-dark only: "hc-light" is missing in older Monaco builds and would make defineTheme fail;
        // the high-contrast colours come from the theme's own keys anyway
        m.put("base", kind == ThemeDefinition.Kind.DARK ? "vs-dark" : "vs");
        m.put("inherit", true);
        ArrayNode rules = m.putArray("rules");
        for (var e : rules(theme)) { // global default colour (rule without scope)
            if (e.getKey().isEmpty() && e.getValue().foreground() != null) {
                ObjectNode r = rules.addObject();
                r.put("token", "");
                r.put("foreground", hex(e.getValue().foreground() | 255).substring(1));
            }
        }
        tokenColors(theme).forEach((token, st) -> {
            ObjectNode r = rules.addObject();
            r.put("token", token);
            r.put("foreground", hex(st.foreground() | 255).substring(1, 7));
            if (st.fontStyle() != null && !st.fontStyle().isBlank()) r.put("fontStyle", st.fontStyle().trim());
            if (token.equals("variable")) {
                ObjectNode param = rules.addObject();
                param.put("token", "variable.parameter");
                param.put("foreground", hex(st.foreground() | 255).substring(1, 7));
                param.put("fontStyle", "bold");
            }
        });
        ObjectNode colors = m.putObject("colors");
        c.forEach((k, v) -> {
            if (k.startsWith("editor") || k.startsWith("scrollbarSlider.") || k.startsWith("minimap")
                    || k.startsWith("list.") || k.startsWith("input.") || k.equals("focusBorder") || k.equals("foreground")
                    || k.startsWith("widget.")) {
                colors.put(k, hex(v));
            }
        });
        colors.put("editor.background", hex(p.vars.get("-color-bg-default")));
        if (!c.containsKey("editor.foreground")) colors.put("editor.foreground", hex(p.vars.get("-color-fg-default")));
        if (!c.containsKey("editor.selectionBackground")) colors.put("editor.selectionBackground", hex(withAlpha(p.vars.get("-color-accent-emphasis"), 0.30)));
        if (!c.containsKey("editorWidget.background")) colors.put("editorWidget.background", hex(p.vars.get("-color-bg-overlay")));
        return m.toString();
    }

    // ================================================================== css

    static String css(String label, String source, Palette p, Map<String, TokenStyle> tokens) {
        StringBuilder sb = new StringBuilder();
        sb.append("/* Imported VS Code theme: ").append(label.replace("*/", "")).append("\n   ").append(source.replace("*/", ""))
                .append("\n   Generated by SQL-IDE (VsixThemeImporter). Re-import the .vsix to regenerate. */\n.root {\n");
        p.vars.forEach((k, v) -> sb.append("    ").append(k).append(": ").append(cssColor(v)).append(";")
                .append("  /* ").append(p.from.get(k)).append(" */\n"));
        // grid values and the fallback editor's SQL colours follow the editor token colours
        tokenVar(sb, tokens, "number", "-ide-grid-number-fg");
        tokenVar(sb, tokens, "constant", "-ide-grid-true-fg");
        tokenVar(sb, tokens, "constant", "-ide-grid-false-fg");
        tokenVar(sb, tokens, "keyword", "-ide-syntax-keyword");
        tokenVar(sb, tokens, "type", "-ide-syntax-type");
        tokenVar(sb, tokens, "predefined", "-ide-syntax-function");
        tokenVar(sb, tokens, "string", "-ide-syntax-string");
        tokenVar(sb, tokens, "comment", "-ide-syntax-comment");
        tokenVar(sb, tokens, "number", "-ide-syntax-number");
        sb.append("}\n");
        return sb.toString();
    }

    private static void tokenVar(StringBuilder sb, Map<String, TokenStyle> tokens, String token, String var) {
        TokenStyle st = tokens.get(token);
        if (st == null || st.foreground() == null) return;
        sb.append("    ").append(var).append(": ").append(cssColor(st.foreground() | 255)).append(";  /* tokenColors ").append(token).append(" */\n");
    }

    // ================================================================== helpers

    /** Lower-case id from a label: "GitHub Dark Default" -> "github-dark-default". */
    static String slug(String label) {
        String s = label == null ? "" : label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return s.isEmpty() ? "theme" : s;
    }

    private static String stripDot(String p) {
        String s = p.replace('\\', '/');
        while (s.startsWith("./")) s = s.substring(2);
        while (s.startsWith("/")) s = s.substring(1);
        return s;
    }

    private static String parentDir(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? "" : path.substring(0, i + 1);
    }

    /** Resolves "a/b/../c" inside the zip. */
    static String normalize(String path) {
        List<String> parts = new ArrayList<>();
        for (String s : path.replace('\\', '/').split("/")) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) {
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
            } else {
                parts.add(s);
            }
        }
        return String.join("/", parts);
    }

    private static void write(Path target, String content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
