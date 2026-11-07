package app.framework.workspace;

import app.framework.bootstrap.DesktopBootstrap;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Saves what the workspace DTO cannot hold: the text of tabs that are not saved to a file, the connection /
 * database / schema of each tab, pinned state and the order of the tabs. Stored as {@code tab-snapshots.json}
 * next to settings.json and written atomically, so a crash never leaves a half file.
 */
public final class TabSnapshotStore {

    private static final Logger log = LoggerFactory.getLogger(TabSnapshotStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** One tab. {@code content} is null for a clean tab backed by a file (re-read from disk). */
    public record Snap(String title, String filePath, String content, boolean dirty,
                       String connection, String database, String schema, boolean pinned, boolean active) {}

    public record Snapshot(int version, List<Snap> tabs) {}

    private final Path file;

    public TabSnapshotStore() {
        this(defaultFile());
    }

    public TabSnapshotStore(Path file) {
        this.file = file;
    }

    private static Path defaultFile() {
        try {
            return DesktopBootstrap.getInstance().getDirectoryManager().getConfigDir().resolve("tab-snapshots.json");
        } catch (Throwable t) {
            return Path.of(System.getProperty("user.home"), ".lltool", "config", "tab-snapshots.json");
        }
    }

    public String toJson(List<Snap> tabs) throws IOException {
        return MAPPER.writeValueAsString(new Snapshot(1, tabs));
    }

    public void writeJson(String json) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, json);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicNotSupported) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("Could not save tab snapshots: {}", e.getMessage());
        }
    }

    /** Empty list when there is no file or it cannot be read. */
    public List<Snap> read() {
        try {
            if (!Files.exists(file)) return List.of();
            Snapshot s = MAPPER.readValue(Files.readString(file), Snapshot.class);
            return s.tabs() != null ? s.tabs() : List.of();
        } catch (Exception e) {
            log.warn("Could not read tab snapshots: {}", e.getMessage());
            return List.of();
        }
    }
}
