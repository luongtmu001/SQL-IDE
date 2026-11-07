package app.services.theme;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T3: a small .vsix built here with the cases the real packages showed (GitHub Theme 6.3.5, Synthwave '84):
 * JSONC with a trailing comma, a theme that includes a parent, hc-light, a focusBorder that does not stand out,
 * no rule for plain "string", short hex colours.
 */
class VsixThemeImporterTest {

    private static final String PACKAGE = """
            { "name": "test-themes", "publisher": "acme", "version": "1.2.3", "displayName": "%ext.name%",
              "contributes": { "themes": [
                { "label": "Acme Neon", "uiTheme": "vs-dark", "path": "./themes/neon.json" },
                { "label": "Acme Paper HC", "uiTheme": "hc-light", "path": "./themes/paper-hc.json" } ] } }
            """;

    /** Synthwave-like: trailing comma, focusBorder almost the background, no plain "string" rule, no editor.foreground. */
    private static final String NEON = """
            {
              // comment allowed (JSONC)
              "name": "Acme Neon",
              "colors": {
                "editor.background": "#262335",
                "foreground": "#fff",
                "focusBorder": "#1f212b",
                "button.background": "#614D85",
                "textLink.foreground": "#f97e72",
                "sideBar.background": "#241b2f",
                "editorWidget.background": "#171520DC",
                "list.activeSelectionBackground": "#ffffff20",
              },
              "tokenColors": [
                { "scope": "comment", "settings": { "foreground": "#848bbd", "fontStyle": "italic" } },
                { "scope": ["keyword", "storage.type"], "settings": { "foreground": "#fede5d" } },
                { "scope": "string.quoted, punctuation.definition.string", "settings": { "foreground": "#ff8b39" } },
                { "scope": "constant.numeric", "settings": { "foreground": "#f97e72" } },
                { "scope": "support.function", "settings": { "foreground": "#36f9f6" } },
              ]
            }
            """;

    private static final String PAPER_BASE = """
            { "colors": { "editor.background": "#ffffff", "editor.foreground": "#0e1116", "sideBar.border": "#20252c" },
              "tokenColors": [ { "scope": "keyword", "settings": { "foreground": "#a0111f" } } ] }
            """;

    private static final String PAPER_HC = """
            { "include": "./paper-base.json",
              "colors": { "focusBorder": "#0349b4" },
              "tokenColors": [ { "scope": "keyword.other.DML.sql", "settings": { "foreground": "#123456" } } ] }
            """;

    @Test
    void importsEveryThemeOfThePackage(@TempDir Path dir) throws Exception {
        Path vsix = dir.resolve("test.vsix");
        zip(vsix, Map.of("extension/package.json", PACKAGE, "extension/themes/neon.json", NEON,
                "extension/themes/paper-base.json", PAPER_BASE, "extension/themes/paper-hc.json", PAPER_HC,
                "extension/package.nls.json", "{ \"ext.name\": \"Acme Themes\" }"));
        Path themes = dir.resolve("themes");
        List<VsixThemeImporter.Result> res = VsixThemeImporter.importVsix(vsix, themes);
        assertEquals(2, res.size());
        assertTrue(res.stream().allMatch(VsixThemeImporter.Result::ok), res.toString());

        VsixThemeImporter.Result neon = res.get(0);
        assertEquals("acme-neon", neon.id());
        assertEquals(ThemeDefinition.Kind.DARK, neon.kind());
        String css = Files.readString(themes.resolve("acme-neon/theme.css"));
        assertTrue(css.contains("-color-bg-default: #262335;"), css);
        assertTrue(css.contains("-color-fg-default: #ffffff;"), "falls back to 'foreground' (short hex expanded)");
        assertTrue(css.contains("-color-accent-emphasis: #614d85;"), "focusBorder too close to the background is skipped");
        assertTrue(css.contains("-ide-menu-bg: #"), "popup background with alpha is flattened: " + css);
        assertTrue(css.contains("-ide-list-selection-bg: rgba(255, 255, 255,"), "selection keeps its alpha");
        assertTrue(css.contains("-ide-syntax-string: #ff8b39;"), "string found through string.quoted.single.sql");

        JsonNode monaco = new ObjectMapper().readTree(Files.readString(themes.resolve("acme-neon/monaco.json")));
        assertEquals("vs-dark", monaco.path("base").asText());
        assertEquals("#ffffff", monaco.path("colors").path("editor.foreground").asText());
        assertEquals("ff8b39", rule(monaco, "string").path("foreground").asText());
        assertEquals("italic", rule(monaco, "comment").path("fontStyle").asText());

        VsixThemeImporter.Result paper = res.get(1);
        assertEquals(ThemeDefinition.Kind.LIGHT, paper.kind(), "hc-light is a light theme");
        assertTrue(paper.highContrast());
        String paperCss = Files.readString(themes.resolve("acme-paper-hc/theme.css"));
        assertTrue(paperCss.contains("-color-border-default: #20252c;"), "colour from the included parent");
        assertTrue(paperCss.contains("-color-accent-emphasis: #0349b4;"), "child colour");
        JsonNode paperMonaco = new ObjectMapper().readTree(Files.readString(themes.resolve("acme-paper-hc/monaco.json")));
        assertEquals("123456", rule(paperMonaco, "keyword").path("foreground").asText(), "child rule wins over the parent's");

        List<ThemeDefinition> listed = ThemeRegistry.scan(themes);
        assertEquals(2, listed.size());
        assertEquals("vsix:acme.test-themes@1.2.3", listed.get(0).source());
        assertEquals("Acme Themes", listed.get(0).family(), "T4: package display name from package.nls.json");
        List<ThemeRegistry.Group> groups = ThemeRegistry.group(listed);
        assertEquals(List.of("Tối · Acme Themes", "Sáng · Acme Themes"), groups.stream().map(ThemeRegistry.Group::title).toList());

        VsixThemeImporter.remove(themes, "acme-neon");
        assertEquals(1, ThemeRegistry.scan(themes).size());
        VsixThemeImporter.removeAll(themes, List.of("acme-paper-hc", "not-there"));
        assertEquals(0, ThemeRegistry.scan(themes).size(), "T4: a whole package removed at once");
    }

    @Test
    void darkOrLight() {
        assertEquals(ThemeDefinition.Kind.LIGHT, VsixThemeImporter.kindOf("hc-light", "", null));
        assertEquals(ThemeDefinition.Kind.DARK, VsixThemeImporter.kindOf("hc-black", "", null));
        assertEquals(ThemeDefinition.Kind.LIGHT, VsixThemeImporter.kindOf("", "light", null));
        assertEquals(ThemeDefinition.Kind.DARK, VsixThemeImporter.kindOf("", "", VsixThemeImporter.parse("#22272e")));
        assertEquals(ThemeDefinition.Kind.LIGHT, VsixThemeImporter.kindOf("", "", VsixThemeImporter.parse("#fafbfc")));
    }

    @Test
    void colourHelpers() {
        assertEquals(0xffffffff, VsixThemeImporter.parse("#fff"));
        assertEquals(0x171520dc, VsixThemeImporter.parse("#171520DC"));
        assertNull(VsixThemeImporter.parse("transparent"));
        assertEquals("rgba(255, 255, 255, 0.125)", VsixThemeImporter.cssColor(0xffffff20));
        assertTrue(VsixThemeImporter.contrast(0x1f212bff, 0x262335ff) < 1.5, "Synthwave focusBorder on its editor");
        assertEquals("github-dark-default", VsixThemeImporter.slug("GitHub Dark Default"));
        assertEquals("synthwave-84", VsixThemeImporter.slug("SynthWave '84"));
    }

    private static JsonNode rule(JsonNode monaco, String token) {
        for (JsonNode r : monaco.path("rules")) if (token.equals(r.path("token").asText())) return r;
        return fail("no rule " + token);
    }

    private static void zip(Path file, Map<String, String> entries) throws Exception {
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream z = new ZipOutputStream(out)) {
            for (var e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
    }
}
