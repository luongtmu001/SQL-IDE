package app.services.theme;

import app.services.ThemeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** T1: theme ids, old names, imported theme folders, light/dark twins, Monaco theme of a built-in theme. */
class ThemeRegistryTest {

    @Test
    void builtInIdsAndOldNames() {
        ThemeRegistry r = ThemeRegistry.getInstance();
        assertEquals("primer-dark", ThemeDefinition.builtIn(ThemeService.AppTheme.PRIMER_DARK).id());
        assertEquals("nord-light", r.find("Nord Light").id(), "old display name");
        assertEquals("cupertino-dark", r.find("CUPERTINO_DARK").id(), "enum name");
        assertEquals("dracula", r.find("dracula").id());
        assertEquals("primer-dark", r.find("no-such-theme").id(), "unknown -> Primer Dark");
        assertEquals("primer-light", r.counterpart(r.find("primer-dark")).id());
        assertEquals("nord-dark", r.counterpart(r.find("nord-light")).id());
    }

    @Test
    void importedFoldersAreRead(@TempDir Path dir) throws Exception {
        theme(dir, "github-dark-default", "GitHub Dark Default", "DARK");
        theme(dir, "github-light-default", "GitHub Light Default", "LIGHT");
        Files.createDirectories(dir.resolve("broken")); // no theme.json: ignored
        List<ThemeDefinition> list = ThemeRegistry.scan(dir);
        assertEquals(2, list.size());
        ThemeDefinition dark = list.get(0);
        assertEquals("github-dark-default", dark.id());
        assertTrue(dark.isDark());
        assertFalse(dark.isBuiltIn());
        assertEquals(ThemeService.AppTheme.PRIMER_DARK, dark.base());
        assertNotNull(dark.cssFile());
        assertNotNull(dark.monacoFile());
        assertEquals(ThemeService.AppTheme.PRIMER_LIGHT, list.get(1).base());
        assertEquals("GitHub Light Default", ThemeRegistry.swapDarkLight("GitHub Dark Default"));
    }

    @Test
    void groupedByKindThenFamily(@TempDir Path dir) throws Exception {
        theme(dir, "github-dark-default", "GitHub Dark Default", "DARK"); // no "family": taken from the source
        theme(dir, "github-light-default", "GitHub Light Default", "LIGHT");
        Path f = Files.createDirectories(dir.resolve("synthwave-84"));
        Files.writeString(f.resolve("theme.json"), "{\"id\":\"synthwave-84\",\"label\":\"SynthWave '84\",\"kind\":\"DARK\","
                + "\"source\":\"vsix:RobbOwen.synthwave-vscode@0.1.20\",\"family\":\"SynthWave '84\"}");
        List<ThemeDefinition> all = new java.util.ArrayList<>(ThemeRegistry.getInstance().builtIn());
        all.addAll(ThemeRegistry.scan(dir));
        List<String> titles = ThemeRegistry.group(all).stream().map(ThemeRegistry.Group::title).toList();
        assertEquals(List.of("Tối · Có sẵn", "Tối · github-vscode-theme", "Tối · SynthWave '84",
                "Sáng · Có sẵn", "Sáng · github-vscode-theme"), titles);
        ThemeRegistry.Group darkBuiltIn = ThemeRegistry.group(all).get(0);
        assertTrue(darkBuiltIn.themes().stream().allMatch(d -> d.isBuiltIn() && d.isDark()));
        assertEquals("github-vscode-theme", ThemeRegistry.familyFromSource("vsix:github.github-vscode-theme@6.3.5"));
        assertEquals("VS Code", ThemeRegistry.familyFromSource(null));
    }

    @Test
    void builtInMonacoThemeUsesTheThemePalette() throws Exception {
        JsonNode j = new ObjectMapper().readTree(MonacoThemes.builtIn(ThemeDefinition.builtIn(ThemeService.AppTheme.DRACULA), null));
        assertEquals("vs-dark", j.path("base").asText());
        assertEquals("ff79c6", j.path("rules").get(0).path("foreground").asText(), "Dracula pink keywords");
        assertTrue(j.path("colors").has("editor.background"));
        JsonNode light = new ObjectMapper().readTree(MonacoThemes.builtIn(ThemeDefinition.builtIn(ThemeService.AppTheme.PRIMER_LIGHT), null));
        assertEquals("vs", light.path("base").asText());
        assertEquals("#ffffff", light.path("colors").path("editor.background").asText());
    }

    private static void theme(Path dir, String id, String label, String kind) throws Exception {
        Path f = Files.createDirectories(dir.resolve(id));
        Files.writeString(f.resolve("theme.json"), "{\"id\":\"" + id + "\",\"label\":\"" + label + "\",\"kind\":\"" + kind
                + "\",\"source\":\"vsix:github.github-vscode-theme@6.3.5\"}");
        Files.writeString(f.resolve("theme.css"), ".root { -color-bg-default: #0d1117; }");
        Files.writeString(f.resolve("monaco.json"), "{\"base\":\"vs-dark\",\"inherit\":true,\"rules\":[],\"colors\":{}}");
    }
}
