package app.services;

import app.framework.history.SqliteHistoryStore;
import app.models.HistoryDocument;
import app.models.HistoryEntry;
import app.models.HistoryTag;
import app.models.IdeSettings;
import com.lltool.infrastructure.filesystem.AppDirectoryManager;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.*;

/**
 * High-performance background history logging service (Phase 2 - Feature 10).
 * Handles asynchronous background execution recording, document metadata synchronization,
 * periodic retention cleanup, and live UI notifications.
 */
public class HistoryLoggingService {

    private static final Logger log = LoggerFactory.getLogger(HistoryLoggingService.class);

    public static final String HISTORY_DB_NAME = "history_java.db";

    private static volatile HistoryLoggingService instance;

    public static HistoryLoggingService getInstance() {
        if (instance == null) {
            synchronized (HistoryLoggingService.class) {
                if (instance == null) {
                    Path defaultDb = AppDirectoryManager.createDefault().getDataDir().resolve(HISTORY_DB_NAME);
                    instance = new HistoryLoggingService(defaultDb);
                }
            }
        }
        return instance;
    }

    /** For testing with isolated database files. */
    public static void setCustomInstance(HistoryLoggingService custom) {
        synchronized (HistoryLoggingService.class) {
            instance = custom;
        }
    }

    private final SqliteHistoryStore store;
    private final ExecutorService backgroundExecutor;
    private final ScheduledExecutorService retentionScheduler;
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();
    private int writeCounter = 0;

    public HistoryLoggingService(Path dbPath) {
        this.store = new SqliteHistoryStore(dbPath);
        this.backgroundExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "history-worker");
            t.setDaemon(true);
            return t;
        });
        this.retentionScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "history-retention-worker");
            t.setDaemon(true);
            return t;
        });

        // Run initial retention after 30 seconds, then every 6 hours
        this.retentionScheduler.schedule(this::runRetentionTask, 30, TimeUnit.SECONDS);
        this.retentionScheduler.scheduleAtFixedRate(this::runRetentionTask, 6, 6, TimeUnit.HOURS);
    }

    public SqliteHistoryStore getStore() {
        return store;
    }

    public void addChangeListener(Runnable listener) {
        if (listener != null && !changeListeners.contains(listener)) {
            changeListeners.add(listener);
        }
    }

    public void removeChangeListener(Runnable listener) {
        if (listener != null) {
            changeListeners.remove(listener);
        }
    }

    private void notifyListeners() {
        if (changeListeners.isEmpty()) return;
        Platform.runLater(() -> {
            for (Runnable r : changeListeners) {
                try {
                    r.run();
                } catch (Exception e) {
                    log.warn("History change listener error: {}", e.getMessage());
                }
            }
        });
    }

    private IdeSettings.HistorySettings getHistoryConfig() {
        var settings = SettingsService.getInstance().getCurrentSettings();
        if (settings != null && settings.getHistory() != null) {
            return settings.getHistory();
        }
        return new IdeSettings.HistorySettings();
    }

    public boolean isHistoryEnabled() {
        return getHistoryConfig().isEnabled();
    }

    /**
     * Records a query execution on F5 / Run.
     * Synchronously logs to SQLite WAL (<0.5ms) and returns the generated entryId
     * so that completion status/duration can be updated afterwards.
     */
    public Long recordExecution(
            String docId,
            String displayName,
            String sql,
            String server,
            String database,
            String filePath,
            String login,
            boolean isSelection
    ) {
        if (!isHistoryEnabled()) {
            return null;
        }

        var cfg = getHistoryConfig();
        int maxSizeBytes = cfg.getMaxEntrySizeKb() * 1024;
        long now = System.currentTimeMillis();

        try {
            var res = store.recordExecution(
                    docId != null ? docId : "default-doc",
                    displayName != null ? displayName : "Untitled.sql",
                    sql,
                    server != null ? server : "localhost",
                    database != null ? database : "master",
                    filePath,
                    login,
                    isSelection,
                    HistoryEntry.TYPE_EXECUTION,
                    null,
                    null,
                    null,
                    now,
                    maxSizeBytes,
                    null,
                    null,
                    false
            );

            writeCounter++;
            if (writeCounter % 100 == 0) {
                backgroundExecutor.submit(this::runRetentionTask);
            }

            notifyListeners();
            return res != null ? res.entryId() : null;
        } catch (Exception e) {
            log.error("Failed to record query execution: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Updates an existing entry's execution result asynchronously.
     */
    public void updateExecutionResult(Long entryId, Long durationMs, Integer status, String errorSummary) {
        if (entryId == null) return;
        backgroundExecutor.submit(() -> {
            try {
                store.updateEntryResult(entryId, durationMs, status, errorSummary);
                notifyListeners();
            } catch (Exception e) {
                log.warn("Failed to update execution result {}: {}", entryId, e.getMessage());
            }
        });
    }

    /**
     * Records a script file snapshot (EntryType = 2) on save.
     */
    public void recordSnapshot(
            String docId,
            String displayName,
            String filePath,
            String sql,
            String server,
            String database
    ) {
        if (!isHistoryEnabled()) return;
        var cfg = getHistoryConfig();
        int maxSizeBytes = cfg.getMaxEntrySizeKb() * 1024;

        backgroundExecutor.submit(() -> {
            try {
                store.recordExecution(
                        docId != null ? docId : "default-doc",
                        displayName != null ? displayName : "Untitled.sql",
                        sql,
                        server != null ? server : "",
                        database != null ? database : "",
                        filePath,
                        null,
                        false,
                        HistoryEntry.TYPE_SNAPSHOT,
                        0L,
                        HistoryEntry.STATUS_OK,
                        null,
                        System.currentTimeMillis(),
                        maxSizeBytes,
                        null,
                        null,
                        false
                );
                notifyListeners();
            } catch (Exception e) {
                log.warn("Failed to record snapshot: {}", e.getMessage());
            }
        });
    }

    /**
     * Updates document metadata (e.g. rename or save).
     */
    public void updateDocument(String docId, String displayName, String filePath) {
        if (docId == null) return;
        backgroundExecutor.submit(() -> {
            try {
                store.updateDocument(docId, displayName, filePath);
                notifyListeners();
            } catch (Exception e) {
                log.warn("Failed to update document {}: {}", docId, e.getMessage());
            }
        });
    }

    public void runRetentionTask() {
        try {
            var cfg = getHistoryConfig();
            store.purgeOldEntries(cfg.getMaxAgeDays(), cfg.getMaxEntriesPerDoc(), cfg.getMaxDbSizeMb());
            log.debug("History retention task executed successfully.");
        } catch (Exception e) {
            log.warn("Error running history retention: {}", e.getMessage());
        }
    }

    // Direct store pass-throughs for UI queries
    public List<HistoryDocument> getDocuments(String search, boolean favoritesOnly, String server, String database, Long fromMs, Long toMs) {
        return store.getDocuments(search, favoritesOnly, false, server, database, fromMs, toMs, 300);
    }

    public List<HistoryEntry> getEntries(String docId, Integer statusFilter, String search, String server, String database, Long fromMs, Long toMs, String tag, Integer entryType, boolean favoriteOnly) {
        return store.getEntries(docId, statusFilter, search, server, database, fromMs, toMs, tag, entryType, favoriteOnly, 300);
    }

    public HistoryEntry getEntryDetail(long entryId) {
        return store.getEntryDetail(entryId);
    }

    public boolean toggleDocStar(String docId) {
        boolean res = store.toggleStar(docId);
        notifyListeners();
        return res;
    }

    public boolean toggleEntryFavorite(long entryId) {
        boolean res = store.setEntryFavorite(entryId, null);
        notifyListeners();
        return res;
    }

    public boolean updateEntryNote(long entryId, String note) {
        boolean res = store.updateEntryNote(entryId, note);
        notifyListeners();
        return res;
    }

    public boolean addEntryTag(long entryId, String tagName) {
        boolean res = store.addEntryTag(entryId, tagName);
        notifyListeners();
        return res;
    }

    public boolean removeEntryTag(long entryId, String tagName) {
        boolean res = store.removeEntryTag(entryId, tagName);
        notifyListeners();
        return res;
    }

    public boolean deleteEntry(long entryId) {
        boolean res = store.deleteEntry(entryId);
        notifyListeners();
        return res;
    }

    public boolean deleteDocument(String docId) {
        boolean res = store.deleteDocument(docId, false);
        notifyListeners();
        return res;
    }

    public List<String> getDistinctServers() {
        return store.getDistinctServers();
    }

    public List<String> getDistinctDatabases() {
        return store.getDistinctDatabases();
    }

    public List<HistoryTag> getAllTags() {
        return store.getAllTags();
    }

    public void exportJson(File file) throws IOException {
        store.exportJson(file);
    }

    public int importJson(File file) throws IOException {
        int count = store.importJson(file);
        notifyListeners();
        return count;
    }
}
