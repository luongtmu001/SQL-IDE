package app.services;

import app.models.IdeSettings;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** File helpers of {@link SettingsService}: safe writes and the one-time import of LLTool's settings (S1/S2). */
final class SettingsFiles {

    private static final ObjectMapper MAPPER = IdeSettings.jsonMapper();

    private SettingsFiles() {
    }

    /**
     * LLTool's settings.json has the same sections as {@link IdeSettings}. Version / update fields stay the Java
     * app's own, and LLTool theme names (github-light, dark, nord…) become the nearest Java theme.
     */
    static IdeSettings fromLltoolJson(JsonNode root) throws IOException {
        if (root == null || !root.isObject()) return IdeSettings.createDefault();
        ObjectNode copy = ((ObjectNode) root).deepCopy();
        copy.remove("updates");
        copy.remove("installedVersion");
        String theme = copy.path("appearance").path("theme").asText(copy.path("theme").asText(null));
        copy.remove("theme");
        if (copy.get("appearance") instanceof ObjectNode ap) ap.remove("theme");
        IdeSettings s = MAPPER.treeToValue(copy, IdeSettings.class);
        s.setTheme(mapLltoolTheme(theme));
        return s;
    }

    static String mapLltoolTheme(String name) {
        if (name == null || name.isBlank()) return ThemeService.AppTheme.PRIMER_DARK.getDisplayName();
        for (ThemeService.AppTheme t : ThemeService.AppTheme.values()) {
            if (t.getDisplayName().equalsIgnoreCase(name) || t.name().equalsIgnoreCase(name)) return t.getDisplayName();
        }
        String n = name.toLowerCase(Locale.ROOT);
        boolean light = n.contains("light") || n.equals("win-nt") || n.equals("win-xp");
        ThemeService.AppTheme t;
        if (n.contains("nord")) t = light ? ThemeService.AppTheme.NORD_LIGHT : ThemeService.AppTheme.NORD_DARK;
        else if (n.contains("dracula") || n.contains("monokai")) t = ThemeService.AppTheme.DRACULA;
        else if (n.contains("cupertino") || n.contains("mac")) t = light ? ThemeService.AppTheme.CUPERTINO_LIGHT : ThemeService.AppTheme.CUPERTINO_DARK;
        else t = light ? ThemeService.AppTheme.PRIMER_LIGHT : ThemeService.AppTheme.PRIMER_DARK;
        return t.getDisplayName();
    }

    /** Write to a temp file next to the target, then rename: a crash never leaves a half-written settings file. */
    static void writeAtomically(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
