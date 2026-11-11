package app.services.theme;

import app.services.ThemeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * T1: every theme the app can show: the 7 AtlantaFX themes, then the themes imported from {@code .vsix}
 * files (folders {@code <data>/themes/<id>/theme.json}). The folder is read once and cached
 * (performance rules); {@link #reload()} after an import or a removal.
 */
public final class ThemeRegistry {

    private static final Logger log = LoggerFactory.getLogger(ThemeRegistry.class);
    private static final ThemeRegistry INSTANCE = new ThemeRegistry();

    private volatile List<ThemeDefinition> imported;
    private final List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Themes listed under one header: a kind (Tối / Sáng) and a family ("Có sẵn", "GitHub Theme"). */
    public record Group(ThemeDefinition.Kind kind, String family, List<ThemeDefinition> themes) {
        public String title() {
            return (kind == ThemeDefinition.Kind.DARK ? "Tối" : "Sáng") + " · " + family;
        }
    }

    private ThemeRegistry() {
    }

    public static ThemeRegistry getInstance() {
        return INSTANCE;
    }

    /** {@code %APPDATA%\LLTool-Desktop\themes}; null when the app folders are not known (tests). */
    public static Path themesDir() {
        try {
            return app.framework.bootstrap.DesktopBootstrap.getInstance().getDirectoryManager().getRootDir().resolve("themes");
        } catch (Exception e) {
            return null;
        }
    }

    public List<ThemeDefinition> builtIn() {
        List<ThemeDefinition> out = new ArrayList<>();
        for (ThemeService.AppTheme t : ThemeService.AppTheme.values()) out.add(ThemeDefinition.builtIn(t));
        return out;
    }

    public List<ThemeDefinition> imported() {
        List<ThemeDefinition> list = imported;
        if (list == null) {
            synchronized (this) {
                if (imported == null) imported = scan(themesDir());
                list = imported;
            }
        }
        return list;
    }

    /** Built-in first, then imported; dark ones before light ones inside each group. */
    public List<ThemeDefinition> all() {
        List<ThemeDefinition> out = new ArrayList<>(builtIn());
        out.addAll(imported());
        return out;
    }

    /** After an import / a removal: the folder is read again on next use, and listeners are told (any thread). */
    public void reload() {
        imported = null;
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (Exception e) {
                log.warn("Theme list listener failed: {}", e.getMessage());
            }
        }
    }

    /** Called on the thread that changed the list; UI listeners hop to the FX thread themselves. */
    public void addChangeListener(Runnable r) {
        if (r != null) listeners.add(r);
    }

    public void removeChangeListener(Runnable r) {
        listeners.remove(r);
    }

    /**
     * T4: dark groups first, then light; inside a kind the built-in themes, then each imported family by name
     * (themes of one .vsix stay together, sorted by label).
     */
    public List<Group> grouped() {
        return group(all());
    }

    static List<Group> group(List<ThemeDefinition> themes) {
        List<Group> out = new ArrayList<>();
        for (ThemeDefinition.Kind kind : ThemeDefinition.Kind.values()) {
            java.util.Map<String, List<ThemeDefinition>> byFamily = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            List<ThemeDefinition> builtIns = new ArrayList<>();
            for (ThemeDefinition d : themes) {
                if (d.kind() != kind) continue;
                if (d.isBuiltIn()) builtIns.add(d);
                else byFamily.computeIfAbsent(familyOf(d), f -> new ArrayList<>()).add(d);
            }
            if (!builtIns.isEmpty()) out.add(new Group(kind, ThemeDefinition.BUILT_IN_FAMILY, builtIns));
            byFamily.forEach((f, list) -> {
                list.sort(Comparator.comparing(d -> d.label().toLowerCase(Locale.ROOT)));
                out.add(new Group(kind, f, list));
            });
        }
        return out;
    }

    /** Imported themes by family (both kinds), for the Settings list of packages. */
    public java.util.Map<String, List<ThemeDefinition>> importedFamilies() {
        java.util.Map<String, List<ThemeDefinition>> out = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (ThemeDefinition d : imported()) out.computeIfAbsent(familyOf(d), f -> new ArrayList<>()).add(d);
        return out;
    }

    static String familyOf(ThemeDefinition d) {
        if (d.isBuiltIn()) return ThemeDefinition.BUILT_IN_FAMILY;
        return d.family() != null && !d.family().isBlank() ? d.family() : familyFromSource(d.source());
    }

    /** "vsix:github.github-vscode-theme@6.3.5" -> "github-vscode-theme" (themes imported before T4). */
    static String familyFromSource(String source) {
        if (source == null || source.isBlank()) return "VS Code";
        String s = source.startsWith("vsix:") ? source.substring(5) : source;
        int at = s.lastIndexOf('@');
        if (at > 0) s = s.substring(0, at);
        int dot = s.indexOf('.');
        if (dot >= 0 && dot < s.length() - 1) s = s.substring(dot + 1);
        return s.isBlank() ? "VS Code" : s;
    }

    /** By id, label or old display / enum name ("Primer Dark", "PRIMER_DARK"); Primer Dark when unknown. */
    public ThemeDefinition find(String key) {
        ThemeDefinition d = findOrNull(key);
        return d != null ? d : ThemeDefinition.builtIn(ThemeService.AppTheme.PRIMER_DARK);
    }

    public ThemeDefinition findOrNull(String key) {
        if (key == null || key.isBlank()) return null;
        String k = key.trim();
        for (ThemeDefinition d : all()) {
            if (d.id().equalsIgnoreCase(k) || d.label().equalsIgnoreCase(k)) return d;
        }
        for (ThemeService.AppTheme t : ThemeService.AppTheme.values()) {
            if (t.name().equalsIgnoreCase(k)) return ThemeDefinition.builtIn(t);
        }
        // Fallback for LLTool aliases (e.g. "ayu-mirage-bordered" -> "ayu-mirage")
        if (k.endsWith("-bordered") || k.endsWith("-unbordered")) {
            String stripped = k.replace("-bordered", "").replace("-unbordered", "");
            for (ThemeDefinition d : all()) {
                if (d.id().equalsIgnoreCase(stripped) || d.label().equalsIgnoreCase(stripped)) return d;
            }
        }
        return null;
    }

    /**
     * The light / dark twin for the title-bar toggle: same source, the label with Dark and Light swapped
     * ("GitHub Dark Default" -> "GitHub Light Default"); otherwise Primer Light / Dark.
     */
    public ThemeDefinition counterpart(ThemeDefinition d) {
        if (d.base() != null && d.isBuiltIn()) {
            ThemeService.AppTheme twin = switch (d.base()) {
                case PRIMER_DARK -> ThemeService.AppTheme.PRIMER_LIGHT;
                case PRIMER_LIGHT -> ThemeService.AppTheme.PRIMER_DARK;
                case NORD_DARK -> ThemeService.AppTheme.NORD_LIGHT;
                case NORD_LIGHT -> ThemeService.AppTheme.NORD_DARK;
                case CUPERTINO_DARK -> ThemeService.AppTheme.CUPERTINO_LIGHT;
                case CUPERTINO_LIGHT -> ThemeService.AppTheme.CUPERTINO_DARK;
                case DRACULA -> ThemeService.AppTheme.PRIMER_LIGHT;
            };
            return ThemeDefinition.builtIn(twin);
        }
        String want = swapDarkLight(d.label());
        for (ThemeDefinition o : imported()) {
            if (o != d && o.kind() != d.kind() && o.source().equals(d.source()) && o.label().equalsIgnoreCase(want)) return o;
        }
        for (ThemeDefinition o : imported()) { // any theme of the other kind from the same package
            if (o.kind() != d.kind() && o.source().equals(d.source())) return o;
        }
        return ThemeDefinition.builtIn(d.isDark() ? ThemeService.AppTheme.PRIMER_LIGHT : ThemeService.AppTheme.PRIMER_DARK);
    }

    static String swapDarkLight(String label) {
        if (label == null) return "";
        if (label.contains("Dark")) return label.replace("Dark", "Light");
        if (label.contains("Light")) return label.replace("Light", "Dark");
        return label;
    }

    /** Reads {@code <dir>/<id>/theme.json} of every imported theme (small files, once). */
    static List<ThemeDefinition> scan(Path dir) {
        List<ThemeDefinition> out = new ArrayList<>();
        if (dir == null || !Files.isDirectory(dir)) return out;
        ObjectMapper mapper = new ObjectMapper();
        try (Stream<Path> folders = Files.list(dir)) {
            for (Path folder : folders.filter(Files::isDirectory).toList()) {
                Path meta = folder.resolve("theme.json");
                if (!Files.isRegularFile(meta)) continue;
                try {
                    JsonNode j = mapper.readTree(meta.toFile());
                    ThemeDefinition.Kind kind = "LIGHT".equalsIgnoreCase(j.path("kind").asText()) ? ThemeDefinition.Kind.LIGHT : ThemeDefinition.Kind.DARK;
                    Path css = folder.resolve(j.path("css").asText("theme.css"));
                    Path monaco = folder.resolve(j.path("monaco").asText("monaco.json"));
                    out.add(new ThemeDefinition(
                            j.path("id").asText(folder.getFileName().toString()),
                            j.path("label").asText(folder.getFileName().toString()),
                            kind,
                            j.path("highContrast").asBoolean(false),
                            j.path("source").asText("vsix"),
                            kind == ThemeDefinition.Kind.LIGHT ? ThemeService.AppTheme.PRIMER_LIGHT : ThemeService.AppTheme.PRIMER_DARK,
                            Files.isRegularFile(css) ? css : null,
                            Files.isRegularFile(monaco) ? monaco : null,
                            j.path("family").asText(familyFromSource(j.path("source").asText("vsix")))));
                } catch (Exception e) {
                    log.warn("Imported theme {} skipped: {}", folder.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not read imported themes in {}: {}", dir, e.getMessage());
        }
        out.sort(Comparator.comparing((ThemeDefinition d) -> d.kind() == ThemeDefinition.Kind.LIGHT)
                .thenComparing(d -> d.label().toLowerCase(Locale.ROOT)));
        return out;
    }
}
