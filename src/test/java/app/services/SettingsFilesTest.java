package app.services;

import app.models.IdeSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** S1/S2 (2026-11-06): IDE settings survive a save + reload, and LLTool's settings.json is taken over once. */
class SettingsFilesTest {

    /** Shortened copy of a real LLTool settings.json (%APPDATA%\LLTool\settings.json). */
    private static final String LLTOOL = """
            {
              "theme": "github-light",
              "editor": { "fontFamily": "Cascadia Mono Light", "fontSize": 12, "lineHeight": 1.3, "minimap": true },
              "appearance": { "theme": "github-light", "iconSize": 12, "uiFontFamily": "Inter", "uiFontSize": 13 },
              "grid": { "fontFamily": "Inter", "fontSize": 12 },
              "sql": { "maxRows": 0, "timeoutSeconds": 30, "sqlprotection": 1, "stopOnError": true, "perTabSession": true },
              "session": { "restoreOnStartup": true, "autoSaveIntervalSeconds": 3 },
              "updates": { "installedVersion": "1.3.2" },
              "installedVersion": "1.3.2",
              "typography": { "editor": { "family": "Monaspace Neon Light", "fontFamily": "Monaspace Neon Light", "size": 13 } },
              "somethingNew": { "x": 1 }
            }
            """;

    @Test
    void lltoolSettingsAreTakenOver() throws Exception {
        IdeSettings s = SettingsFiles.fromLltoolJson(new ObjectMapper().readTree(LLTOOL));
        assertEquals("Primer Light", s.getTheme(), "github-light becomes the nearest Java theme");
        assertEquals("Primer Light", s.getAppearance().getTheme());
        assertEquals(12, s.getEditorFontSize());
        assertTrue(s.isMinimap());
        assertEquals(1, s.getSql().getSqlprotection());
        assertEquals(30, s.getSql().getTimeoutSeconds());
        assertEquals(IdeSettings.createDefault().getInstalledVersion(), s.getInstalledVersion(), "version stays the Java app's");
    }

    @Test
    void lltoolThemeNames() {
        assertEquals("Primer Dark", SettingsFiles.mapLltoolTheme("dark"));
        assertEquals("Primer Light", SettingsFiles.mapLltoolTheme("light"));
        assertEquals("Nord Dark", SettingsFiles.mapLltoolTheme("nord"));
        assertEquals("Dracula", SettingsFiles.mapLltoolTheme("monokai"));
        assertEquals("Primer Light", SettingsFiles.mapLltoolTheme("win-xp"));
        assertEquals("Cupertino Dark", SettingsFiles.mapLltoolTheme("Cupertino Dark"));
        assertEquals("Primer Dark", SettingsFiles.mapLltoolTheme(null));
    }

    @Test
    void savedSettingsReadBackUnchanged(@TempDir Path dir) throws Exception {
        IdeSettings s = IdeSettings.createDefault();
        s.setNullDisplayString("(null)");
        s.setGridFontSize(15);
        s.getSql().setMaxRows(500);
        s.getAppearance().setPanelStyle("flat");
        Path file = dir.resolve("config").resolve("ide-settings.json");
        ObjectMapper mapper = IdeSettings.jsonMapper();
        SettingsFiles.writeAtomically(file, mapper.writeValueAsString(s));
        SettingsFiles.writeAtomically(file, mapper.writeValueAsString(s)); // replacing an existing file works too

        assertFalse(Files.exists(file.resolveSibling("ide-settings.json.tmp")), "no temp file left behind");
        IdeSettings back = IdeSettings.fromJsonString(Files.readString(file));
        assertEquals("(null)", back.getNullDisplayString());
        assertEquals(15, back.getGridFontSize());
        assertEquals(500, back.getSql().getMaxRows());
        assertTrue(back.isFlatPanels());
    }

    /** sql.maxRows 0 = unlimited; the getMaxRows() convenience value (5000) must not be written and read back. */
    @Test
    void unlimitedRowsStayUnlimited() throws Exception {
        IdeSettings s = IdeSettings.createDefault();
        s.getSql().setMaxRows(0);
        s.getSql().setTimeoutSeconds(30);
        s.getQuery().setTimeoutSeconds(60);
        String json = IdeSettings.jsonMapper().writeValueAsString(s);
        assertFalse(json.contains("\"queryTimeoutSeconds\""), "no top-level convenience keys: " + json);
        IdeSettings back = IdeSettings.fromJsonString(json);
        assertEquals(0, back.getSql().getMaxRows());
        assertEquals(30, back.getSql().getTimeoutSeconds());
        assertEquals(60, back.getQuery().getTimeoutSeconds());
        assertEquals(s.getTheme(), back.getTheme());
    }
}
