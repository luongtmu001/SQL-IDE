package app.services.theme;

import app.services.ThemeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T1: the Monaco theme of every app theme (user decision 2026-11-06: the editor follows the chosen theme).
 * <ul>
 *   <li>Imported theme: its {@code monaco.json} (written by the importer from the VS Code theme), read once.</li>
 *   <li>Built-in theme: token colours from the theme's own palette (below), editor surface colours read from the
 *       live AtlantaFX variables of the scene, so the editor background is exactly the card background.</li>
 * </ul>
 */
public final class MonacoThemes {

    private static final Logger log = LoggerFactory.getLogger(MonacoThemes.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> IMPORTED_CACHE = new ConcurrentHashMap<>();

    private MonacoThemes() {
    }

    /** Monaco id of the theme: "app-" + the theme id. */
    public static String monacoId(ThemeDefinition d) {
        return "app-" + d.id().replaceAll("[^A-Za-z0-9_-]", "-");
    }

    /** JSON for {@code monaco.editor.defineTheme}; {@code scene} = where the editor is (for built-in themes). */
    public static String json(ThemeDefinition d, Scene scene) {
        if (!d.isBuiltIn() && d.monacoFile() != null) {
            String cached = IMPORTED_CACHE.get(d.id());
            if (cached != null) return cached;
            try {
                String j = Files.readString(d.monacoFile());
                MAPPER.readTree(j); // valid JSON only
                IMPORTED_CACHE.put(d.id(), j);
                return j;
            } catch (Exception e) {
                log.warn("Monaco theme of {} not readable ({}), built from the base theme", d.id(), e.getMessage());
            }
        }
        return builtIn(d, scene);
    }

    public static void forgetImported() {
        IMPORTED_CACHE.clear();
    }

    // ------------------------------------------------------------------ built-in themes

    /** SQL token colours (hex without '#'): keyword, predefined function, string, number, comment, variable, operator. */
    private record Tokens(String keyword, String predefined, String string, String number, String comment,
                          String variable, String operator, boolean italicComments) {
    }

    private static Tokens tokens(ThemeService.AppTheme t) {
        return switch (t) {
            // GitHub Primer (same colours as the GitHub VS Code themes)
            case PRIMER_DARK -> new Tokens("ff7b72", "79c0ff", "a5d6ff", "79c0ff", "8b949e", "ffa657", "ff7b72", false);
            case PRIMER_LIGHT -> new Tokens("cf222e", "0550ae", "0a3069", "0550ae", "6e7781", "953800", "cf222e", false);
            // Dracula palette (draculatheme.com)
            case DRACULA -> new Tokens("ff79c6", "8be9fd", "f1fa8c", "bd93f9", "6272a4", "ffb86c", "ff79c6", true);
            // Nord palette (nordtheme.com): nord9 keywords, nord8 functions, nord14 strings, nord15 numbers
            case NORD_DARK -> new Tokens("81a1c1", "88c0d0", "a3be8c", "b48ead", "616e88", "8fbcbb", "81a1c1", true);
            case NORD_LIGHT -> new Tokens("5e81ac", "3e7a8c", "5d8a3a", "8f5a8a", "7b8394", "4c566a", "5e81ac", true);
            // Xcode default dark / light
            case CUPERTINO_DARK -> new Tokens("fc5fa3", "67b7a4", "fc6a5d", "d0bf69", "6c7986", "5dd8ff", "fc5fa3", false);
            case CUPERTINO_LIGHT -> new Tokens("9b2393", "326d74", "c41a16", "1c00cf", "5d6c79", "0b4f79", "9b2393", false);
        };
    }

    static String builtIn(ThemeDefinition d, Scene scene) {
        ThemeService.AppTheme t = d.base() != null ? d.base() : ThemeService.AppTheme.PRIMER_DARK;
        Tokens k = tokens(t);
        ObjectNode theme = MAPPER.createObjectNode();
        theme.put("base", d.isDark() ? "vs-dark" : "vs");
        theme.put("inherit", true);
        ArrayNode rules = theme.putArray("rules");
        rule(rules, "keyword", k.keyword(), null);
        rule(rules, "operator", k.operator(), null);
        rule(rules, "predefined", k.predefined(), null);
        rule(rules, "string", k.string(), null);
        rule(rules, "number", k.number(), null);
        rule(rules, "comment", k.comment(), k.italicComments() ? "italic" : null);
        rule(rules, "variable", k.variable(), null);
        rule(rules, "variable.parameter", k.variable(), "bold");
        rule(rules, "type", k.keyword(), null);

        ObjectNode colors = theme.putObject("colors");
        Map<String, String> p = palette(scene, d.isDark());
        colors.put("editor.background", p.get("-color-bg-default"));
        colors.put("editor.foreground", p.get("-color-fg-default"));
        colors.put("editor.lineHighlightBackground", p.get("-color-bg-subtle"));
        colors.put("editorLineNumber.foreground", p.get("-color-fg-subtle"));
        colors.put("editorLineNumber.activeForeground", p.get("-color-fg-default"));
        colors.put("editorCursor.foreground", p.get("-color-accent-fg"));
        colors.put("editor.selectionBackground", withAlpha(p.get("-color-accent-emphasis"), 0x55));
        colors.put("editor.inactiveSelectionBackground", withAlpha(p.get("-color-accent-emphasis"), 0x30));
        colors.put("editorIndentGuide.background", p.get("-color-border-muted"));
        colors.put("editorWidget.background", p.get("-color-bg-overlay"));
        colors.put("editorWidget.border", p.get("-color-border-default"));
        colors.put("editorSuggestWidget.background", p.get("-color-bg-overlay"));
        colors.put("editorSuggestWidget.border", p.get("-color-border-default"));
        colors.put("editorSuggestWidget.selectedBackground", withAlpha(p.get("-color-accent-emphasis"), 0x40));
        return theme.toString();
    }

    private static void rule(ArrayNode rules, String token, String hex, String fontStyle) {
        ObjectNode r = rules.addObject();
        r.put("token", token);
        r.put("foreground", hex);
        if (fontStyle != null) r.put("fontStyle", fontStyle);
    }

    private static String withAlpha(String hex, int alpha) {
        String h = hex.length() >= 7 ? hex.substring(0, 7) : hex;
        return h + String.format("%02x", alpha);
    }

    private static final String[] VARS = {"-color-bg-default", "-color-fg-default", "-color-bg-subtle", "-color-fg-subtle",
            "-color-accent-fg", "-color-accent-emphasis", "-color-border-muted", "-color-border-default", "-color-bg-overlay"};

    /**
     * The scene's AtlantaFX colours, read with a hidden probe node (looked-up colours only resolve inside a scene);
     * Primer values when the scene cannot be probed.
     */
    static Map<String, String> palette(Scene scene, boolean dark) {
        Map<String, String> out = new LinkedHashMap<>(dark ? PRIMER_DARK_FALLBACK : PRIMER_LIGHT_FALLBACK);
        Parent root = scene != null ? scene.getRoot() : null;
        if (!(root instanceof Pane pane)) return out;
        Pane probe = new Pane();
        probe.setManaged(false);
        probe.setVisible(false);
        pane.getChildren().add(probe);
        try {
            for (String v : VARS) {
                probe.setStyle("-fx-background-color: " + v + ";");
                probe.applyCss();
                var bg = probe.getBackground();
                if (bg != null && !bg.getFills().isEmpty() && bg.getFills().get(0).getFill() instanceof Color c) {
                    out.put(v, hex(c));
                }
            }
        } catch (Exception e) {
            log.debug("Palette probe failed: {}", e.getMessage());
        } finally {
            pane.getChildren().remove(probe);
        }
        return out;
    }

    static String hex(Color c) {
        int r = (int) Math.round(c.getRed() * 255), g = (int) Math.round(c.getGreen() * 255), b = (int) Math.round(c.getBlue() * 255);
        int a = (int) Math.round(c.getOpacity() * 255);
        return a >= 255 ? String.format("#%02x%02x%02x", r, g, b) : String.format("#%02x%02x%02x%02x", r, g, b, a);
    }

    private static final Map<String, String> PRIMER_DARK_FALLBACK = Map.of(
            "-color-bg-default", "#0d1117", "-color-fg-default", "#e6edf3", "-color-bg-subtle", "#161b22",
            "-color-fg-subtle", "#6e7681", "-color-accent-fg", "#2f81f7", "-color-accent-emphasis", "#1f6feb",
            "-color-border-muted", "#21262d", "-color-border-default", "#30363d", "-color-bg-overlay", "#161b22");
    private static final Map<String, String> PRIMER_LIGHT_FALLBACK = Map.of(
            "-color-bg-default", "#ffffff", "-color-fg-default", "#1f2328", "-color-bg-subtle", "#f6f8fa",
            "-color-fg-subtle", "#6e7781", "-color-accent-fg", "#0969da", "-color-accent-emphasis", "#0969da",
            "-color-border-muted", "#d8dee4", "-color-border-default", "#d0d7de", "-color-bg-overlay", "#ffffff");
}
