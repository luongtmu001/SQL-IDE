package app.services;

import app.models.SqlSnippet;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service for managing SQL snippets:
 * - Reads/writes ~/.lltool/data/snippets.json with atomic file writes.
 * - Auto-initializes from bundled classpath /data/snippets.json on first launch.
 * - Provides snippet CRUD, pinning, search, and JSON export/import.
 * - Synchronizes snippet updates to Monaco Editor instances.
 */
public class SnippetService {

    private static final Logger LOGGER = Logger.getLogger(SnippetService.class.getName());
    private static final SnippetService INSTANCE = new SnippetService();

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path userSnippetsPath;
    private final List<SqlSnippet> snippets = new ArrayList<>();
    private final List<Consumer<List<SqlSnippet>>> listeners = new CopyOnWriteArrayList<>();

    private SnippetService() {
        this.userSnippetsPath = Path.of(System.getProperty("user.home"), ".lltool", "data", "snippets.json");
        loadSnippets();
    }

    public static SnippetService getInstance() {
        return INSTANCE;
    }

    public Path getUserSnippetsPath() {
        return userSnippetsPath;
    }

    /**
     * Loads snippets from user storage (~/.lltool/data/snippets.json), falling back to bundled resources.
     */
    public synchronized List<SqlSnippet> loadSnippets() {
        snippets.clear();
        boolean loaded = false;

        // 1. Try loading from user directory
        if (Files.exists(userSnippetsPath)) {
            try {
                byte[] bytes = Files.readAllBytes(userSnippetsPath);
                if (bytes.length > 0) {
                    List<SqlSnippet> list = mapper.readValue(bytes, new TypeReference<List<SqlSnippet>>() {});
                    if (list != null && !list.isEmpty()) {
                        snippets.addAll(list);
                        loaded = true;
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load snippets from " + userSnippetsPath + ", falling back to bundled data", e);
            }
        }

        // 2. Fallback to bundled resource
        if (!loaded) {
            try (InputStream is = getClass().getResourceAsStream("/data/snippets.json")) {
                if (is != null) {
                    List<SqlSnippet> list = mapper.readValue(is, new TypeReference<List<SqlSnippet>>() {});
                    if (list != null && !list.isEmpty()) {
                        snippets.addAll(list);
                        saveSnippetsToFile(); // Persist bundled defaults to user folder
                        loaded = true;
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to load bundled snippets from /data/snippets.json", e);
            }
        }

        // 3. Fallback defaults if still empty
        if (snippets.isEmpty()) {
            snippets.add(new SqlSnippet("ssf", "SELECT * FROM fragment", "SELECT * FROM $table$"));
            snippets.add(new SqlSnippet("sfb", "SELECT * FROM WHERE fragment", "SELECT * FROM $table$ WHERE $condition$"));
            snippets.add(new SqlSnippet("ins", "INSERT INTO template", "INSERT INTO $table$ ($columns$)\nVALUES ($values$);"));
            snippets.add(new SqlSnippet("sel", "SELECT template", "SELECT $columns$ FROM $table$"));
            saveSnippetsToFile();
        }

        sortSnippets();
        notifyListeners();
        return getAllSnippets();
    }

    /**
     * Returns an unmodifiable copy of all snippets sorted by pinned status, then prefix.
     */
    public synchronized List<SqlSnippet> getAllSnippets() {
        List<SqlSnippet> copy = new ArrayList<>(snippets.size());
        for (SqlSnippet s : snippets) {
            copy.add(s.clone());
        }
        return Collections.unmodifiableList(copy);
    }

    public synchronized Optional<SqlSnippet> getSnippetByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return Optional.empty();
        String p = prefix.trim().toLowerCase();
        return snippets.stream()
                .filter(s -> s.getPrefix().equalsIgnoreCase(p))
                .findFirst()
                .map(SqlSnippet::clone);
    }

    public synchronized Optional<SqlSnippet> getSnippetById(String id) {
        if (id == null) return Optional.empty();
        return snippets.stream()
                .filter(s -> id.equals(s.getId()))
                .findFirst()
                .map(SqlSnippet::clone);
    }

    public synchronized boolean saveSnippet(SqlSnippet snippet) {
        if (snippet == null || snippet.getPrefix().isBlank()) {
            return false;
        }

        int foundIndex = -1;
        for (int i = 0; i < snippets.size(); i++) {
            if (snippets.get(i).getId().equals(snippet.getId())) {
                foundIndex = i;
                break;
            }
        }

        if (foundIndex >= 0) {
            snippets.set(foundIndex, snippet.clone());
        } else {
            snippets.add(snippet.clone());
        }

        sortSnippets();
        boolean ok = saveSnippetsToFile();
        notifyListeners();
        return ok;
    }

    public synchronized boolean deleteSnippet(String id) {
        if (id == null) return false;
        boolean removed = snippets.removeIf(s -> id.equals(s.getId()));
        if (removed) {
            saveSnippetsToFile();
            notifyListeners();
        }
        return removed;
    }

    public synchronized boolean togglePin(String id) {
        for (SqlSnippet s : snippets) {
            if (id.equals(s.getId())) {
                s.setPinned(!s.isPinned());
                sortSnippets();
                saveSnippetsToFile();
                notifyListeners();
                return true;
            }
        }
        return false;
    }

    public synchronized List<SqlSnippet> resetToDefaults() {
        try (InputStream is = getClass().getResourceAsStream("/data/snippets.json")) {
            if (is != null) {
                List<SqlSnippet> list = mapper.readValue(is, new TypeReference<List<SqlSnippet>>() {});
                if (list != null && !list.isEmpty()) {
                    snippets.clear();
                    snippets.addAll(list);
                    sortSnippets();
                    saveSnippetsToFile();
                    notifyListeners();
                    return getAllSnippets();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to reset snippets to defaults", e);
        }
        return getAllSnippets();
    }

    public synchronized boolean importSnippets(File file) {
        if (file == null || !file.exists()) return false;
        try {
            List<SqlSnippet> imported = mapper.readValue(file, new TypeReference<List<SqlSnippet>>() {});
            if (imported == null || imported.isEmpty()) return false;

            for (SqlSnippet newSnip : imported) {
                boolean replaced = false;
                for (int i = 0; i < snippets.size(); i++) {
                    if (snippets.get(i).getPrefix().equalsIgnoreCase(newSnip.getPrefix())) {
                        snippets.set(i, newSnip);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    snippets.add(newSnip);
                }
            }
            sortSnippets();
            saveSnippetsToFile();
            notifyListeners();
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to import snippets from " + file, e);
            return false;
        }
    }

    public synchronized boolean exportSnippets(File file) {
        if (file == null) return false;
        try {
            if (file.getParentFile() != null) {
                file.getParentFile().mkdirs();
            }
            mapper.writeValue(file, snippets);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to export snippets to " + file, e);
            return false;
        }
    }

    /**
     * Serializes all snippets to a JSON array ready for Monaco bridge (with auto-converted tabstops).
     */
    public synchronized String getSnippetsAsJson() {
        try {
            List<Map<String, Object>> list = new ArrayList<>();
            for (SqlSnippet s : snippets) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("prefix", s.getPrefix());
                map.put("description", s.getDescription());
                map.put("body", s.getBody());
                map.put("monacoBody", s.toMonacoSnippetBody());
                map.put("dbType", s.getDbType());
                map.put("isPinned", s.isPinned());
                map.put("tags", s.getTags());
                list.add(map);
            }
            return mapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private void sortSnippets() {
        snippets.sort((a, b) -> {
            if (a.isPinned() != b.isPinned()) {
                return a.isPinned() ? -1 : 1;
            }
            return a.getPrefix().compareToIgnoreCase(b.getPrefix());
        });
    }

    private boolean saveSnippetsToFile() {
        try {
            if (userSnippetsPath.getParent() != null) {
                Files.createDirectories(userSnippetsPath.getParent());
            }
            Path tempPath = Path.of(userSnippetsPath.toString() + ".tmp");
            byte[] bytes = mapper.writeValueAsBytes(snippets);
            Files.write(tempPath, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            Files.move(tempPath, userSnippetsPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save snippets to " + userSnippetsPath, e);
            return false;
        }
    }

    public void addChangeListener(Consumer<List<SqlSnippet>> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeChangeListener(Consumer<List<SqlSnippet>> listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        List<SqlSnippet> list = getAllSnippets();
        for (Consumer<List<SqlSnippet>> listener : listeners) {
            try {
                listener.accept(list);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error notifying snippet listener", e);
            }
        }
    }
}
