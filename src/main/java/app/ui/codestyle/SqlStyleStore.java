package app.ui.codestyle;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * DLG-009 styles: one read-only system style + user styles, the active (default) style id.
 * Stored in {@code sql-styles.json} next to settings.json.
 */
public final class SqlStyleStore {

    public static final String SYSTEM_ID = "system";

    /** A code style: values = option id -> value (missing ids use the metadata defaults). */
    public static final class Style {
        public String id;
        public String name;
        public boolean system;
        public Map<String, Object> values = new LinkedHashMap<>();

        public Style() {
        }

        Style(String id, String name, boolean system, Map<String, Object> values) {
            this.id = id;
            this.name = name;
            this.system = system;
            this.values = new LinkedHashMap<>(values);
        }

        public Style copy(String newId, String newName) {
            return new Style(newId, newName, false, values);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final class Data {
        public String activeStyleId = SYSTEM_ID;
        public List<Style> styles = new ArrayList<>();
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static java.nio.file.Path file() {
        java.nio.file.Path settings = app.services.SettingsService.getInstance().getSettingsFilePath();
        java.nio.file.Path dir = settings != null && settings.getParent() != null ? settings.getParent()
                : java.nio.file.Path.of(System.getProperty("user.home"), ".lltool", "config");
        return dir.resolve("sql-styles.json");
    }

    private SqlStyleStore() {
    }

    public static Style systemStyle() {
        return new Style(SYSTEM_ID, "Mặc định (Hệ thống)", true, SqlStyleMetadata.get().defaults());
    }

    public static Data load() {
        Data d = new Data();
        try {
            java.nio.file.Path f = file();
            if (java.nio.file.Files.exists(f)) d = MAPPER.readValue(f.toFile(), Data.class);
        } catch (Exception ignored) {
            d = new Data();
        }
        d.styles.removeIf(s -> s.system || SYSTEM_ID.equals(s.id));
        d.styles.add(0, systemStyle());
        Map<String, Object> defaults = SqlStyleMetadata.get().defaults();
        for (Style s : d.styles) defaults.forEach(s.values::putIfAbsent);
        final Data result = d;
        if (result.styles.stream().noneMatch(s -> s.id.equals(result.activeStyleId))) result.activeStyleId = SYSTEM_ID;
        return result;
    }

    public static void save(Data d) {
        Data out = new Data();
        out.activeStyleId = d.activeStyleId;
        for (Style s : d.styles) if (!s.system) out.styles.add(s);
        try {
            java.nio.file.Path f = file();
            java.nio.file.Files.createDirectories(f.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(f.toFile(), out);
        } catch (Exception ignored) {
        }
    }

    public static String toJson(Style s) throws Exception {
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(s);
    }

    public static Style fromJson(String json) throws Exception {
        Style s = MAPPER.readValue(json, new TypeReference<Style>() {
        });
        s.system = false;
        s.id = UUID.randomUUID().toString();
        if (s.name == null || s.name.isBlank()) s.name = "Style nhập";
        SqlStyleMetadata.get().defaults().forEach(s.values::putIfAbsent);
        return s;
    }

    /** Style currently used by "Format" (Gate D: the real formatter reads it). */
    public static Style activeStyle() {
        Data d = load();
        return d.styles.stream().filter(s -> s.id.equals(d.activeStyleId)).findFirst().orElse(systemStyle());
    }
}
