package app.framework.history;

import app.models.HistoryDocument;
import app.models.HistoryEntry;
import app.models.HistoryTag;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.Function;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * SQLite Storage Engine for Query History (Phase 2 - Feature 10).
 * Implements WAL mode, SHA-256 deduplication, zlib compression,
 * consecutive run coalescing (RunCount), multi-tagging, favorites,
 * and retention policies with full parity with Python history.
 */
public class SqliteHistoryStore {

    private static final Logger log = LoggerFactory.getLogger(SqliteHistoryStore.class);
    public static final int SCHEMA_VERSION = 3;

    private static final Pattern COMMENT_LINE = Pattern.compile("--[^\\n]*");
    private static final Pattern COMMENT_BLOCK = Pattern.compile("/\\*[\\s\\S]*?\\*/");

    private final Path dbPath;
    private final String jdbcUrl;
    private final ReentrantLock lock = new ReentrantLock();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SqliteHistoryStore(Path dbPath) {
        this.dbPath = dbPath.toAbsolutePath().normalize();
        this.jdbcUrl = "jdbc:sqlite:" + this.dbPath.toString().replace('\\', '/');
        try {
            if (this.dbPath.getParent() != null) {
                Files.createDirectories(this.dbPath.getParent());
            }
        } catch (IOException e) {
            log.error("Failed to create directory for history DB: {}", e.getMessage(), e);
        }
        initDatabase();
    }

    private Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try {
            Function.create(conn, "decompress_sql", new Function() {
                @Override
                protected void xFunc() throws SQLException {
                    try {
                        byte[] blob = value_blob(0);
                        if (blob == null || blob.length == 0) {
                            result("");
                        } else {
                            result(decompressText(blob));
                        }
                    } catch (Exception e) {
                        result("");
                    }
                }
            });
        } catch (Exception e) {
            log.trace("Note: decompress_sql function registration in SQLite: {}", e.getMessage());
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
            stmt.execute("PRAGMA auto_vacuum = INCREMENTAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
        }
        return conn;
    }

    private void initDatabase() {
        lock.lock();
        try (Connection conn = getConnection()) {
            int currentVersion = 0;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("PRAGMA user_version;")) {
                if (rs.next()) {
                    currentVersion = rs.getInt(1);
                }
            }

            if (currentVersion < 1) {
                try (Statement st = conn.createStatement()) {
                    // 1. Document table
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS Document (
                            DocId        TEXT PRIMARY KEY,
                            DisplayName  TEXT NOT NULL,
                            FilePath     TEXT NULL,
                            IsStarred    INTEGER NOT NULL DEFAULT 0,
                            CreatedAt    INTEGER NOT NULL,
                            LastRunAt    INTEGER NOT NULL,
                            LastServer   TEXT NULL,
                            LastDatabase TEXT NULL,
                            IsDeleted    INTEGER NOT NULL DEFAULT 0
                        );
                    """);
                    st.execute("CREATE INDEX IF NOT EXISTS IX_Document_LastRunAt ON Document(LastRunAt DESC);");
                    st.execute("CREATE INDEX IF NOT EXISTS IX_Document_FilePath ON Document(FilePath);");
                    st.execute("CREATE INDEX IF NOT EXISTS IX_Document_Starred ON Document(IsStarred, LastRunAt DESC);");

                    // 2. Content table (dedup by SHA-256)
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS Content (
                            Hash       TEXT PRIMARY KEY,
                            Body       BLOB NOT NULL,
                            RawSize    INTEGER NOT NULL,
                            RefCount   INTEGER NOT NULL DEFAULT 0
                        );
                    """);

                    // 3. HistoryEntry table
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS HistoryEntry (
                            EntryId      INTEGER PRIMARY KEY AUTOINCREMENT,
                            DocId        TEXT NOT NULL REFERENCES Document(DocId) ON DELETE CASCADE,
                            ContentHash  TEXT NOT NULL REFERENCES Content(Hash),
                            EntryType    INTEGER NOT NULL,
                            ExecutedAt   INTEGER NOT NULL,
                            Server       TEXT NOT NULL,
                            DatabaseName TEXT NOT NULL,
                            LoginName    TEXT NULL,
                            IsSelection  INTEGER NOT NULL DEFAULT 0,
                            DurationMs   INTEGER NULL,
                            Status       INTEGER NULL,
                            ErrorSummary TEXT NULL,
                            RunCount     INTEGER NOT NULL DEFAULT 1,
                            Note         TEXT NULL,
                            Tag          TEXT NULL,
                            IsFavorite   INTEGER NOT NULL DEFAULT 0
                        );
                    """);
                    st.execute("CREATE INDEX IF NOT EXISTS IX_History_Doc_Time ON HistoryEntry(DocId, ExecutedAt DESC);");
                    st.execute("CREATE INDEX IF NOT EXISTS IX_History_ExecutedAt ON HistoryEntry(ExecutedAt DESC);");
                    st.execute("CREATE INDEX IF NOT EXISTS IX_History_ContentHash ON HistoryEntry(ContentHash);");
                    st.execute("CREATE INDEX IF NOT EXISTS IX_History_Server_Db ON HistoryEntry(Server, DatabaseName);");
                    st.execute("CREATE INDEX IF NOT EXISTS IX_History_Tag ON HistoryEntry(Tag);");
                }
            }

            if (currentVersion < 3) {
                try (Statement st = conn.createStatement()) {
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS Tag (
                            TagId     INTEGER PRIMARY KEY AUTOINCREMENT,
                            TagName   TEXT UNIQUE NOT NULL,
                            Color     TEXT NULL,
                            CreatedAt INTEGER NOT NULL
                        );
                    """);
                    st.execute("""
                        CREATE TABLE IF NOT EXISTS HistoryTag (
                            EntryId   INTEGER NOT NULL REFERENCES HistoryEntry(EntryId) ON DELETE CASCADE,
                            TagId     INTEGER NOT NULL REFERENCES Tag(TagId) ON DELETE CASCADE,
                            CreatedAt INTEGER NOT NULL,
                            PRIMARY KEY (EntryId, TagId)
                        );
                    """);
                    st.execute("CREATE INDEX IF NOT EXISTS IX_History_IsFavorite ON HistoryEntry(IsFavorite, ExecutedAt DESC);");
                    st.execute("CREATE INDEX IF NOT EXISTS IX_HistoryTag_TagId ON HistoryTag(TagId);");
                }
            }

            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA user_version = " + SCHEMA_VERSION + ";");
            }
        } catch (SQLException e) {
            log.error("Failed to initialize History SQLite DB: {}", e.getMessage(), e);
            throw new RuntimeException("Cannot initialize History database", e);
        } finally {
            lock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Text and Hash Utilities
    // ─────────────────────────────────────────────────────────────────────────────

    public static String normalizeSqlText(String sql) {
        if (sql == null) return "";
        return sql.replace("\r\n", "\n").replace("\r", "\n");
    }

    public static boolean isTrivialQuery(String sql) {
        if (sql == null || sql.trim().isEmpty()) return true;
        String clean = COMMENT_LINE.matcher(sql).replaceAll("");
        clean = COMMENT_BLOCK.matcher(clean).replaceAll("");
        return clean.trim().isEmpty();
    }

    public static String computeSha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encoded = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : encoded) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    public static byte[] compressText(String text) {
        if (text == null || text.isEmpty()) return new byte[0];
        byte[] input = text.getBytes(StandardCharsets.UTF_8);
        Deflater deflater = new Deflater(6);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(input.length);
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        deflater.end();
        return outputStream.toByteArray();
    }

    public static String decompressText(byte[] blob) {
        if (blob == null || blob.length == 0) return "";
        Inflater inflater = new Inflater();
        inflater.setInput(blob);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(blob.length * 2);
        byte[] buffer = new byte[1024];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) break;
                outputStream.write(buffer, 0, count);
            }
            inflater.end();
            return outputStream.toString(StandardCharsets.UTF_8);
        } catch (DataFormatException e) {
            log.warn("Failed to decompress SQL blob: {}", e.getMessage());
            return "";
        }
    }

    public static String normalizeFilePath(String path) {
        if (path == null || path.isBlank()) return null;
        try {
            return Path.of(path).normalize().toString().replace('\\', '/');
        } catch (Exception e) {
            return path.replace('\\', '/');
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Core Logging Operations
    // ─────────────────────────────────────────────────────────────────────────────

    public RecordResult recordExecution(
            String docId,
            String displayName,
            String sql,
            String server,
            String database,
            String filePath,
            String login,
            boolean isSelection,
            int entryType,
            Long durationMs,
            Integer status,
            String errorSummary,
            Long executedAt,
            int maxEntrySizeBytes,
            String note,
            String tag,
            boolean isFavorite
    ) {
        String normalized = normalizeSqlText(sql);
        if (isTrivialQuery(normalized)) {
            return null;
        }

        byte[] utf8Bytes = normalized.getBytes(StandardCharsets.UTF_8);
        if (utf8Bytes.length > maxEntrySizeBytes) {
            normalized = normalized.substring(0, Math.min(normalized.length(), 65536))
                    + "\n-- [TRUNCATED: Script vượt quá giới hạn " + (maxEntrySizeBytes / 1024) + " KB] --\n";
            utf8Bytes = normalized.getBytes(StandardCharsets.UTF_8);
        }

        String contentHash = computeSha256(normalized);
        long nowMs = executedAt != null ? executedAt : System.currentTimeMillis();
        String cleanError = errorSummary != null ? (errorSummary.length() > 300 ? errorSummary.substring(0, 300) : errorSummary) : null;
        String cleanNote = note != null && !note.isBlank() ? note.trim() : null;
        String cleanTag = tag != null && !tag.isBlank() ? tag.trim() : null;
        int isSelVal = isSelection ? 1 : 0;
        int isFavVal = isFavorite ? 1 : 0;
        String cleanFp = normalizeFilePath(filePath);
        String finalDisplayName = displayName != null && !displayName.isBlank() ? displayName : "Untitled.sql";

        lock.lock();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            // 1. Upsert Document
            try (PreparedStatement ps = conn.prepareStatement("SELECT DocId, FilePath, DisplayName FROM Document WHERE DocId = ?;")) {
                ps.setString(1, docId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        try (PreparedStatement ins = conn.prepareStatement("""
                            INSERT INTO Document (
                                DocId, DisplayName, FilePath, IsStarred, CreatedAt,
                                LastRunAt, LastServer, LastDatabase, IsDeleted
                            ) VALUES (?, ?, ?, 0, ?, ?, ?, ?, 0);
                        """)) {
                            ins.setString(1, docId);
                            ins.setString(2, finalDisplayName);
                            ins.setString(3, cleanFp);
                            ins.setLong(4, nowMs);
                            ins.setLong(5, nowMs);
                            ins.setString(6, server);
                            ins.setString(7, database);
                            ins.executeUpdate();
                        }
                    } else {
                        String currentFp = rs.getString("FilePath");
                        String newFp = cleanFp != null ? cleanFp : currentFp;
                        try (PreparedStatement upd = conn.prepareStatement("""
                            UPDATE Document
                            SET DisplayName = ?,
                                FilePath = ?,
                                LastRunAt = ?,
                                LastServer = ?,
                                LastDatabase = ?,
                                IsDeleted = 0
                            WHERE DocId = ?;
                        """)) {
                            upd.setString(1, finalDisplayName);
                            upd.setString(2, newFp);
                            upd.setLong(3, nowMs);
                            upd.setString(4, server);
                            upd.setString(5, database);
                            upd.setString(6, docId);
                            upd.executeUpdate();
                        }
                    }
                }
            }

            // 2. Coalesce identical consecutive executions
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT EntryId, ContentHash, Server, DatabaseName, IsSelection, EntryType, RunCount
                FROM HistoryEntry
                WHERE DocId = ?
                ORDER BY ExecutedAt DESC, EntryId DESC
                LIMIT 1;
            """)) {
                ps.setString(1, docId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String lastHash = rs.getString("ContentHash");
                        String lastServer = rs.getString("Server");
                        String lastDb = rs.getString("DatabaseName");
                        int lastIsSel = rs.getInt("IsSelection");
                        int lastType = rs.getInt("EntryType");

                        if (Objects.equals(lastHash, contentHash)
                                && Objects.equals(lastServer, server)
                                && Objects.equals(lastDb, database)
                                && lastIsSel == isSelVal
                                && lastType == entryType) {
                            long targetEntryId = rs.getLong("EntryId");
                            int newRunCount = rs.getInt("RunCount") + 1;

                            try (PreparedStatement upd = conn.prepareStatement("""
                                UPDATE HistoryEntry
                                SET RunCount = ?,
                                    ExecutedAt = ?,
                                    DurationMs = COALESCE(?, DurationMs),
                                    Status = COALESCE(?, Status),
                                    ErrorSummary = COALESCE(?, ErrorSummary),
                                    Note = COALESCE(?, Note),
                                    Tag = COALESCE(?, Tag)
                                WHERE EntryId = ?;
                            """)) {
                                upd.setInt(1, newRunCount);
                                upd.setLong(2, nowMs);
                                if (durationMs != null) upd.setLong(3, durationMs); else upd.setNull(3, Types.INTEGER);
                                if (status != null) upd.setInt(4, status); else upd.setNull(4, Types.INTEGER);
                                upd.setString(5, cleanError);
                                upd.setString(6, cleanNote);
                                upd.setString(7, cleanTag);
                                upd.setLong(8, targetEntryId);
                                upd.executeUpdate();
                            }

                            if (cleanTag != null) {
                                syncTags(conn, targetEntryId, cleanTag, nowMs);
                            }

                            conn.commit();
                            return new RecordResult(targetEntryId, docId, contentHash, false, newRunCount, nowMs);
                        }
                    }
                }
            }

            // 3. Insert or update Content (SHA-256 deduplication)
            try (PreparedStatement ps = conn.prepareStatement("SELECT Hash FROM Content WHERE Hash = ?;")) {
                ps.setString(1, contentHash);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        byte[] compressed = compressText(normalized);
                        try (PreparedStatement ins = conn.prepareStatement("""
                            INSERT INTO Content (Hash, Body, RawSize, RefCount)
                            VALUES (?, ?, ?, 1);
                        """)) {
                            ins.setString(1, contentHash);
                            ins.setBytes(2, compressed);
                            ins.setInt(3, utf8Bytes.length);
                            ins.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement upd = conn.prepareStatement("""
                            UPDATE Content
                            SET RefCount = RefCount + 1
                            WHERE Hash = ?;
                        """)) {
                            upd.setString(1, contentHash);
                            upd.executeUpdate();
                        }
                    }
                }
            }

            // 4. Insert new HistoryEntry
            long newEntryId;
            try (PreparedStatement ins = conn.prepareStatement("""
                INSERT INTO HistoryEntry (
                    DocId, ContentHash, EntryType, ExecutedAt, Server,
                    DatabaseName, LoginName, IsSelection, DurationMs,
                    Status, ErrorSummary, RunCount, Note, Tag, IsFavorite
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?);
            """, Statement.RETURN_GENERATED_KEYS)) {
                ins.setString(1, docId);
                ins.setString(2, contentHash);
                ins.setInt(3, entryType);
                ins.setLong(4, nowMs);
                ins.setString(5, server != null ? server : "");
                ins.setString(6, database != null ? database : "");
                ins.setString(7, login);
                ins.setInt(8, isSelVal);
                if (durationMs != null) ins.setLong(9, durationMs); else ins.setNull(9, Types.INTEGER);
                if (status != null) ins.setInt(10, status); else ins.setNull(10, Types.INTEGER);
                ins.setString(11, cleanError);
                ins.setString(12, cleanNote);
                ins.setString(13, cleanTag);
                ins.setInt(14, isFavVal);
                ins.executeUpdate();

                try (ResultSet gk = ins.getGeneratedKeys()) {
                    if (gk.next()) {
                        newEntryId = gk.getLong(1);
                    } else {
                        throw new SQLException("Failed to retrieve generated EntryId");
                    }
                }
            }

            if (cleanTag != null) {
                syncTags(conn, newEntryId, cleanTag, nowMs);
            }

            conn.commit();
            return new RecordResult(newEntryId, docId, contentHash, true, 1, nowMs);

        } catch (SQLException e) {
            log.error("Failed to record history entry: {}", e.getMessage(), e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    private void syncTags(Connection conn, long entryId, String tagString, long timestampMs) throws SQLException {
        if (tagString == null || tagString.isBlank()) return;
        String[] parts = tagString.split("[,;]");
        for (String p : parts) {
            String tagName = p.trim();
            if (tagName.isEmpty()) continue;
            try (PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO Tag (TagName, CreatedAt) VALUES (?, ?);")) {
                ins.setString(1, tagName);
                ins.setLong(2, timestampMs);
                ins.executeUpdate();
            }
            long tagId = -1;
            try (PreparedStatement sel = conn.prepareStatement("SELECT TagId FROM Tag WHERE TagName = ?;")) {
                sel.setString(1, tagName);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) tagId = rs.getLong(1);
                }
            }
            if (tagId != -1) {
                try (PreparedStatement insHt = conn.prepareStatement("INSERT OR IGNORE INTO HistoryTag (EntryId, TagId, CreatedAt) VALUES (?, ?, ?);")) {
                    insHt.setLong(1, entryId);
                    insHt.setLong(2, tagId);
                    insHt.setLong(3, timestampMs);
                    insHt.executeUpdate();
                }
            }
        }
    }

    public boolean updateEntryResult(long entryId, Long durationMs, Integer status, String errorSummary) {
        String cleanError = errorSummary != null ? (errorSummary.length() > 300 ? errorSummary.substring(0, 300) : errorSummary) : null;
        lock.lock();
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("""
                UPDATE HistoryEntry
                SET DurationMs = COALESCE(?, DurationMs),
                    Status = COALESCE(?, Status),
                    ErrorSummary = ?
                WHERE EntryId = ?;
            """)) {
                if (durationMs != null) ps.setLong(1, durationMs); else ps.setNull(1, Types.INTEGER);
                if (status != null) ps.setInt(2, status); else ps.setNull(2, Types.INTEGER);
                ps.setString(3, cleanError);
                ps.setLong(4, entryId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            log.error("Failed to update entry result {}: {}", entryId, e.getMessage(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean updateDocument(String docId, String displayName, String filePath) {
        String normFp = normalizeFilePath(filePath);
        lock.lock();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            if (normFp != null) {
                // Check if another document already owns this file path
                try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT DocId, DisplayName FROM Document
                    WHERE (LOWER(FilePath) = ? OR REPLACE(LOWER(FilePath), '\\\\', '/') = ?)
                      AND DocId != ? AND IsDeleted = 0
                    LIMIT 1;
                """)) {
                    ps.setString(1, normFp.toLowerCase());
                    ps.setString(2, normFp.toLowerCase());
                    ps.setString(3, docId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            String targetId = rs.getString("DocId");
                            // Merge entries from docId to targetId
                            try (PreparedStatement upd = conn.prepareStatement("UPDATE HistoryEntry SET DocId = ? WHERE DocId = ?;")) {
                                upd.setString(1, targetId);
                                upd.setString(2, docId);
                                upd.executeUpdate();
                            }
                            try (PreparedStatement upd = conn.prepareStatement("""
                                UPDATE Document
                                SET DisplayName = COALESCE(?, DisplayName),
                                    FilePath = ?,
                                    LastRunAt = MAX(LastRunAt, COALESCE((SELECT MAX(ExecutedAt) FROM HistoryEntry WHERE DocId = ?), LastRunAt))
                                WHERE DocId = ?;
                            """)) {
                                upd.setString(1, displayName);
                                upd.setString(2, normFp);
                                upd.setString(3, targetId);
                                upd.setString(4, targetId);
                                upd.executeUpdate();
                            }
                            // Delete source doc
                            try (PreparedStatement del = conn.prepareStatement("DELETE FROM Document WHERE DocId = ?;")) {
                                del.setString(1, docId);
                                del.executeUpdate();
                            }
                            conn.commit();
                            return true;
                        }
                    }
                }
            }

            // Normal update or insert
            try (PreparedStatement ps = conn.prepareStatement("SELECT DocId FROM Document WHERE DocId = ?;")) {
                ps.setString(1, docId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        long now = System.currentTimeMillis();
                        try (PreparedStatement ins = conn.prepareStatement("""
                            INSERT INTO Document (
                                DocId, DisplayName, FilePath, IsStarred, CreatedAt,
                                LastRunAt, LastServer, LastDatabase, IsDeleted
                            ) VALUES (?, ?, ?, 0, ?, ?, NULL, NULL, 0);
                        """)) {
                            ins.setString(1, docId);
                            ins.setString(2, displayName != null ? displayName : "Untitled.sql");
                            ins.setString(3, normFp);
                            ins.setLong(4, now);
                            ins.setLong(5, now);
                            ins.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement upd = conn.prepareStatement("""
                            UPDATE Document
                            SET DisplayName = COALESCE(?, DisplayName),
                                FilePath = COALESCE(?, FilePath)
                            WHERE DocId = ?;
                        """)) {
                            upd.setString(1, displayName);
                            upd.setString(2, normFp);
                            upd.setString(3, docId);
                            upd.executeUpdate();
                        }
                    }
                }
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error("Failed to update document {}: {}", docId, e.getMessage(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean toggleStar(String docId) {
        lock.lock();
        try (Connection conn = getConnection()) {
            int currentStarred = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT IsStarred FROM Document WHERE DocId = ?;")) {
                ps.setString(1, docId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentStarred = rs.getInt("IsStarred");
                    } else {
                        return false;
                    }
                }
            }
            int newStarred = currentStarred == 1 ? 0 : 1;
            try (PreparedStatement upd = conn.prepareStatement("UPDATE Document SET IsStarred = ? WHERE DocId = ?;")) {
                upd.setInt(1, newStarred);
                upd.setString(2, docId);
                upd.executeUpdate();
            }
            return newStarred == 1;
        } catch (SQLException e) {
            log.error("Failed to toggle star for doc {}: {}", docId, e.getMessage(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean setEntryFavorite(long entryId, Boolean isFavorite) {
        lock.lock();
        try (Connection conn = getConnection()) {
            int currentVal = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT IsFavorite FROM HistoryEntry WHERE EntryId = ?;")) {
                ps.setLong(1, entryId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        currentVal = rs.getInt("IsFavorite");
                    } else {
                        return false;
                    }
                }
            }
            int newVal = isFavorite != null ? (isFavorite ? 1 : 0) : (currentVal == 1 ? 0 : 1);
            try (PreparedStatement upd = conn.prepareStatement("UPDATE HistoryEntry SET IsFavorite = ? WHERE EntryId = ?;")) {
                upd.setInt(1, newVal);
                upd.setLong(2, entryId);
                upd.executeUpdate();
            }
            return newVal == 1;
        } catch (SQLException e) {
            log.error("Failed to set favorite for entry {}: {}", entryId, e.getMessage(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean updateEntryNote(long entryId, String note) {
        String cleanNote = note != null && !note.isBlank() ? note.trim() : null;
        lock.lock();
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("UPDATE HistoryEntry SET Note = ? WHERE EntryId = ?;")) {
                ps.setString(1, cleanNote);
                ps.setLong(2, entryId);
                return ps.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            log.error("Failed to update note for entry {}: {}", entryId, e.getMessage(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean addEntryTag(long entryId, String tagName) {
        if (tagName == null || tagName.isBlank()) return false;
        String cleanName = tagName.trim();
        long now = System.currentTimeMillis();
        lock.lock();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ins = conn.prepareStatement("INSERT OR IGNORE INTO Tag (TagName, CreatedAt) VALUES (?, ?);")) {
                ins.setString(1, cleanName);
                ins.setLong(2, now);
                ins.executeUpdate();
            }
            long tagId = -1;
            try (PreparedStatement sel = conn.prepareStatement("SELECT TagId FROM Tag WHERE TagName = ?;")) {
                sel.setString(1, cleanName);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) tagId = rs.getLong(1);
                }
            }
            if (tagId == -1) return false;

            try (PreparedStatement insHt = conn.prepareStatement("INSERT OR IGNORE INTO HistoryTag (EntryId, TagId, CreatedAt) VALUES (?, ?, ?);")) {
                insHt.setLong(1, entryId);
                insHt.setLong(2, tagId);
                insHt.setLong(3, now);
                insHt.executeUpdate();
            }

            // Sync denormalized Tag column
            updateDenormalizedTag(conn, entryId);
            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error("Failed to add tag {} to entry {}: {}", tagName, entryId, e.getMessage(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean removeEntryTag(long entryId, String tagName) {
        if (tagName == null || tagName.isBlank()) return false;
        lock.lock();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            long tagId = -1;
            try (PreparedStatement sel = conn.prepareStatement("SELECT TagId FROM Tag WHERE LOWER(TagName) = LOWER(?);")) {
                sel.setString(1, tagName.trim());
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) tagId = rs.getLong(1);
                }
            }
            if (tagId == -1) return false;

            try (PreparedStatement del = conn.prepareStatement("DELETE FROM HistoryTag WHERE EntryId = ? AND TagId = ?;")) {
                del.setLong(1, entryId);
                del.setLong(2, tagId);
                del.executeUpdate();
            }

            updateDenormalizedTag(conn, entryId);
            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error("Failed to remove tag {} from entry {}: {}", tagName, entryId, e.getMessage(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    private void updateDenormalizedTag(Connection conn, long entryId) throws SQLException {
        try (PreparedStatement sel = conn.prepareStatement("""
            SELECT GROUP_CONCAT(t.TagName, ', ')
            FROM HistoryTag ht
            JOIN Tag t ON t.TagId = ht.TagId
            WHERE ht.EntryId = ?;
        """)) {
            sel.setLong(1, entryId);
            try (ResultSet rs = sel.executeQuery()) {
                String tags = rs.next() ? rs.getString(1) : null;
                try (PreparedStatement upd = conn.prepareStatement("UPDATE HistoryEntry SET Tag = ? WHERE EntryId = ?;")) {
                    upd.setString(1, tags);
                    upd.setLong(2, entryId);
                    upd.executeUpdate();
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Query & Fetch Operations
    // ─────────────────────────────────────────────────────────────────────────────

    public List<HistoryDocument> getDocuments(
            String search,
            boolean favoritesOnly,
            boolean includeDeleted,
            String server,
            String database,
            Long fromTimestamp,
            Long toTimestamp,
            int limit
    ) {
        StringBuilder query = new StringBuilder("""
            SELECT 
                d.DocId,
                d.DisplayName,
                d.FilePath,
                d.IsStarred,
                d.CreatedAt,
                d.LastRunAt,
                d.LastServer,
                d.LastDatabase,
                d.IsDeleted,
                COUNT(h.EntryId) AS EntryCount
            FROM Document d
            LEFT JOIN HistoryEntry h ON h.DocId = d.DocId
            WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (!includeDeleted) {
            query.append(" AND d.IsDeleted = 0");
        }

        if (favoritesOnly) {
            query.append(" AND d.IsStarred = 1");
        }

        if (server != null && !server.isBlank()) {
            query.append(" AND (LOWER(COALESCE(d.LastServer, '')) = LOWER(?) OR d.DocId IN (SELECT DISTINCT DocId FROM HistoryEntry WHERE LOWER(Server) = LOWER(?)))");
            params.add(server);
            params.add(server);
        }

        if (database != null && !database.isBlank()) {
            query.append(" AND (LOWER(COALESCE(d.LastDatabase, '')) = LOWER(?) OR d.DocId IN (SELECT DISTINCT DocId FROM HistoryEntry WHERE LOWER(DatabaseName) = LOWER(?)))");
            params.add(database);
            params.add(database);
        }

        if (fromTimestamp != null) {
            query.append(" AND d.LastRunAt >= ?");
            params.add(fromTimestamp);
        }

        if (toTimestamp != null) {
            query.append(" AND d.LastRunAt <= ?");
            params.add(toTimestamp);
        }

        if (search != null && !search.isBlank()) {
            String s = "%" + search.trim().toLowerCase() + "%";
            query.append("""
                AND (
                    LOWER(d.DisplayName) LIKE ? 
                    OR LOWER(COALESCE(d.FilePath, '')) LIKE ? 
                    OR LOWER(COALESCE(d.LastServer, '')) LIKE ? 
                    OR LOWER(COALESCE(d.LastDatabase, '')) LIKE ?
                    OR d.DocId IN (
                        SELECT DISTINCT h_sub.DocId 
                        FROM HistoryEntry h_sub 
                        JOIN Content c_sub ON h_sub.ContentHash = c_sub.Hash 
                        WHERE LOWER(decompress_sql(c_sub.Body)) LIKE ?
                    )
                )
            """);
            params.add(s);
            params.add(s);
            params.add(s);
            params.add(s);
            params.add(s);
        }

        query.append("""
            GROUP BY d.DocId
            ORDER BY d.IsStarred DESC, d.LastRunAt DESC
            LIMIT ?;
        """);
        params.add(limit > 0 ? limit : 200);

        lock.lock();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            List<HistoryDocument> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new HistoryDocument(
                            rs.getString("DocId"),
                            rs.getString("DisplayName"),
                            rs.getString("FilePath"),
                            rs.getInt("IsStarred") == 1,
                            rs.getLong("CreatedAt"),
                            rs.getLong("LastRunAt"),
                            rs.getString("LastServer"),
                            rs.getString("LastDatabase"),
                            rs.getInt("IsDeleted") == 1,
                            rs.getInt("EntryCount")
                    ));
                }
            }
            return list;
        } catch (SQLException e) {
            log.error("Failed to fetch documents: {}", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            lock.unlock();
        }
    }

    public List<HistoryEntry> getEntries(
            String docId,
            Integer statusFilter,
            String search,
            String server,
            String database,
            Long fromTimestamp,
            Long toTimestamp,
            String tag,
            Integer entryType,
            boolean favoriteOnly,
            int limit
    ) {
        StringBuilder query = new StringBuilder("""
            SELECT 
                h.EntryId, h.DocId, h.ContentHash, h.EntryType, h.ExecutedAt,
                h.Server, h.DatabaseName, h.LoginName, h.IsSelection,
                h.DurationMs, h.Status, h.ErrorSummary, h.RunCount,
                h.Note, h.Tag, h.IsFavorite,
                (
                    SELECT GROUP_CONCAT(t.TagName, '||')
                    FROM HistoryTag ht
                    JOIN Tag t ON t.TagId = ht.TagId
                    WHERE ht.EntryId = h.EntryId
                ) AS TagList
            FROM HistoryEntry h
            WHERE h.DocId = ?
        """);

        List<Object> params = new ArrayList<>();
        params.add(docId);

        if (statusFilter != null) {
            query.append(" AND h.Status = ?");
            params.add(statusFilter);
        }

        if (favoriteOnly) {
            query.append(" AND h.IsFavorite = 1");
        }

        if (server != null && !server.isBlank()) {
            query.append(" AND LOWER(h.Server) = LOWER(?)");
            params.add(server);
        }

        if (database != null && !database.isBlank()) {
            query.append(" AND LOWER(h.DatabaseName) = LOWER(?)");
            params.add(database);
        }

        if (fromTimestamp != null) {
            query.append(" AND h.ExecutedAt >= ?");
            params.add(fromTimestamp);
        }

        if (toTimestamp != null) {
            query.append(" AND h.ExecutedAt <= ?");
            params.add(toTimestamp);
        }

        if (tag != null && !tag.isBlank()) {
            query.append("""
                AND (
                    LOWER(COALESCE(h.Tag, '')) = LOWER(?)
                    OR h.EntryId IN (
                        SELECT ht_sub.EntryId FROM HistoryTag ht_sub
                        JOIN Tag t_sub ON t_sub.TagId = ht_sub.TagId
                        WHERE LOWER(t_sub.TagName) = LOWER(?)
                    )
                )
            """);
            params.add(tag);
            params.add(tag);
        }

        if (entryType != null) {
            query.append(" AND h.EntryType = ?");
            params.add(entryType);
        }

        if (search != null && !search.isBlank()) {
            String s = "%" + search.trim().toLowerCase() + "%";
            query.append("""
                AND (
                    LOWER(COALESCE(h.ErrorSummary, '')) LIKE ?
                    OR LOWER(COALESCE(h.Server, '')) LIKE ?
                    OR LOWER(COALESCE(h.DatabaseName, '')) LIKE ?
                    OR LOWER(COALESCE(h.Note, '')) LIKE ?
                    OR LOWER(COALESCE(h.Tag, '')) LIKE ?
                    OR h.ContentHash IN (
                        SELECT Hash FROM Content WHERE LOWER(decompress_sql(Body)) LIKE ?
                    )
                )
            """);
            params.add(s);
            params.add(s);
            params.add(s);
            params.add(s);
            params.add(s);
            params.add(s);
        }

        query.append(" ORDER BY h.ExecutedAt DESC, h.EntryId DESC LIMIT ?;");
        params.add(limit > 0 ? limit : 200);

        lock.lock();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            List<HistoryEntry> entries = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    HistoryEntry e = new HistoryEntry();
                    e.setEntryId(rs.getLong("EntryId"));
                    e.setDocId(rs.getString("DocId"));
                    e.setContentHash(rs.getString("ContentHash"));
                    e.setEntryType(rs.getInt("EntryType"));
                    e.setExecutedAt(rs.getLong("ExecutedAt"));
                    e.setServer(rs.getString("Server"));
                    e.setDatabaseName(rs.getString("DatabaseName"));
                    e.setLoginName(rs.getString("LoginName"));
                    e.setSelection(rs.getInt("IsSelection") == 1);
                    long dur = rs.getLong("DurationMs");
                    if (!rs.wasNull()) e.setDurationMs(dur);
                    int stat = rs.getInt("Status");
                    if (!rs.wasNull()) e.setStatus(stat);
                    e.setErrorSummary(rs.getString("ErrorSummary"));
                    e.setRunCount(rs.getInt("RunCount"));
                    e.setNote(rs.getString("Note"));
                    e.setTag(rs.getString("Tag"));
                    e.setFavorite(rs.getInt("IsFavorite") == 1);

                    String tagListStr = rs.getString("TagList");
                    List<String> tagList = new ArrayList<>();
                    if (tagListStr != null && !tagListStr.isBlank()) {
                        for (String t : tagListStr.split("\\|\\|")) {
                            if (!t.isBlank()) tagList.add(t);
                        }
                    } else if (e.getTag() != null && !e.getTag().isBlank()) {
                        for (String t : e.getTag().split("[,;]")) {
                            if (!t.isBlank()) tagList.add(t.trim());
                        }
                    }
                    e.setTags(tagList);
                    entries.add(e);
                }
            }
            return entries;
        } catch (SQLException e) {
            log.error("Failed to fetch history entries for doc {}: {}", docId, e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            lock.unlock();
        }
    }

    public HistoryEntry getEntryDetail(long entryId) {
        lock.lock();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 SELECT 
                     h.EntryId, h.DocId, h.ContentHash, h.EntryType, h.ExecutedAt,
                     h.Server, h.DatabaseName, h.LoginName, h.IsSelection,
                     h.DurationMs, h.Status, h.ErrorSummary, h.RunCount,
                     h.Note, h.Tag, h.IsFavorite,
                     c.RawSize, c.Body
                 FROM HistoryEntry h
                 JOIN Content c ON c.Hash = h.ContentHash
                 WHERE h.EntryId = ?;
             """)) {
            ps.setLong(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    HistoryEntry e = new HistoryEntry();
                    e.setEntryId(rs.getLong("EntryId"));
                    e.setDocId(rs.getString("DocId"));
                    e.setContentHash(rs.getString("ContentHash"));
                    e.setEntryType(rs.getInt("EntryType"));
                    e.setExecutedAt(rs.getLong("ExecutedAt"));
                    e.setServer(rs.getString("Server"));
                    e.setDatabaseName(rs.getString("DatabaseName"));
                    e.setLoginName(rs.getString("LoginName"));
                    e.setSelection(rs.getInt("IsSelection") == 1);
                    long dur = rs.getLong("DurationMs");
                    if (!rs.wasNull()) e.setDurationMs(dur);
                    int stat = rs.getInt("Status");
                    if (!rs.wasNull()) e.setStatus(stat);
                    e.setErrorSummary(rs.getString("ErrorSummary"));
                    e.setRunCount(rs.getInt("RunCount"));
                    e.setNote(rs.getString("Note"));
                    e.setTag(rs.getString("Tag"));
                    e.setFavorite(rs.getInt("IsFavorite") == 1);
                    e.setRawSize(rs.getLong("RawSize"));
                    byte[] bodyBlob = rs.getBytes("Body");
                    e.setSql(decompressText(bodyBlob));

                    // Load tags
                    try (PreparedStatement tagPs = conn.prepareStatement("""
                        SELECT t.TagName
                        FROM HistoryTag ht
                        JOIN Tag t ON t.TagId = ht.TagId
                        WHERE ht.EntryId = ?
                        ORDER BY t.TagName;
                    """)) {
                        tagPs.setLong(1, entryId);
                        List<String> tags = new ArrayList<>();
                        try (ResultSet tr = tagPs.executeQuery()) {
                            while (tr.next()) {
                                tags.add(tr.getString(1));
                            }
                        }
                        if (tags.isEmpty() && e.getTag() != null) {
                            for (String p : e.getTag().split("[,;]")) {
                                if (!p.isBlank()) tags.add(p.trim());
                            }
                        }
                        e.setTags(tags);
                    }
                    return e;
                }
            }
            return null;
        } catch (SQLException e) {
            log.error("Failed to get entry detail {}: {}", entryId, e.getMessage(), e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    public String getContent(String contentHash) {
        lock.lock();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT Body FROM Content WHERE Hash = ?;")) {
            ps.setString(1, contentHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return decompressText(rs.getBytes("Body"));
                }
            }
            return null;
        } catch (SQLException e) {
            log.error("Failed to get content for hash {}: {}", contentHash, e.getMessage(), e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    public List<String> getDistinctServers() {
        lock.lock();
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT Server FROM HistoryEntry WHERE Server IS NOT NULL AND TRIM(Server) != '' ORDER BY Server;")) {
            List<String> list = new ArrayList<>();
            while (rs.next()) list.add(rs.getString(1));
            return list;
        } catch (SQLException e) {
            log.error("Failed to get distinct servers: {}", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            lock.unlock();
        }
    }

    public List<String> getDistinctDatabases() {
        lock.lock();
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT DatabaseName FROM HistoryEntry WHERE DatabaseName IS NOT NULL AND TRIM(DatabaseName) != '' ORDER BY DatabaseName;")) {
            List<String> list = new ArrayList<>();
            while (rs.next()) list.add(rs.getString(1));
            return list;
        } catch (SQLException e) {
            log.error("Failed to get distinct databases: {}", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            lock.unlock();
        }
    }

    public List<HistoryTag> getAllTags() {
        lock.lock();
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("""
                 SELECT t.TagId, t.TagName, t.Color, COUNT(ht.EntryId) AS UsageCount
                 FROM Tag t
                 LEFT JOIN HistoryTag ht ON ht.TagId = t.TagId
                 GROUP BY t.TagId
                 ORDER BY UsageCount DESC, t.TagName ASC;
             """)) {
            List<HistoryTag> list = new ArrayList<>();
            while (rs.next()) {
                list.add(new HistoryTag(
                        rs.getLong("TagId"),
                        rs.getString("TagName"),
                        rs.getString("Color"),
                        rs.getInt("UsageCount")
                ));
            }
            return list;
        } catch (SQLException e) {
            log.error("Failed to get all tags: {}", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            lock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Deletion & Retention
    // ─────────────────────────────────────────────────────────────────────────────

    public boolean deleteEntry(long entryId) {
        lock.lock();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            String contentHash = null;
            String docId = null;

            try (PreparedStatement ps = conn.prepareStatement("SELECT ContentHash, DocId FROM HistoryEntry WHERE EntryId = ?;")) {
                ps.setLong(1, entryId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        contentHash = rs.getString("ContentHash");
                        docId = rs.getString("DocId");
                    } else {
                        return false;
                    }
                }
            }

            try (PreparedStatement del = conn.prepareStatement("DELETE FROM HistoryEntry WHERE EntryId = ?;")) {
                del.setLong(1, entryId);
                del.executeUpdate();
            }

            if (contentHash != null) {
                try (PreparedStatement upd = conn.prepareStatement("UPDATE Content SET RefCount = RefCount - 1 WHERE Hash = ?;")) {
                    upd.setString(1, contentHash);
                    upd.executeUpdate();
                }
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM Content WHERE Hash = ? AND RefCount <= 0;")) {
                    del.setString(1, contentHash);
                    del.executeUpdate();
                }
            }

            if (docId != null) {
                try (PreparedStatement chk = conn.prepareStatement("SELECT COUNT(1) FROM HistoryEntry WHERE DocId = ?;")) {
                    chk.setString(1, docId);
                    try (ResultSet rs = chk.executeQuery()) {
                        if (rs.next() && rs.getInt(1) == 0) {
                            try (PreparedStatement del = conn.prepareStatement("DELETE FROM Document WHERE DocId = ? AND FilePath IS NULL AND IsStarred = 0;")) {
                                del.setString(1, docId);
                                del.executeUpdate();
                            }
                        }
                    }
                }
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error("Failed to delete entry {}: {}", entryId, e.getMessage(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public boolean deleteDocument(String docId, boolean softDelete) {
        lock.lock();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            if (softDelete) {
                try (PreparedStatement upd = conn.prepareStatement("UPDATE Document SET IsDeleted = 1 WHERE DocId = ?;")) {
                    upd.setString(1, docId);
                    boolean ok = upd.executeUpdate() > 0;
                    conn.commit();
                    return ok;
                }
            }

            List<String> hashes = new ArrayList<>();
            try (PreparedStatement sel = conn.prepareStatement("SELECT ContentHash FROM HistoryEntry WHERE DocId = ?;")) {
                sel.setString(1, docId);
                try (ResultSet rs = sel.executeQuery()) {
                    while (rs.next()) hashes.add(rs.getString(1));
                }
            }

            try (PreparedStatement del = conn.prepareStatement("DELETE FROM HistoryEntry WHERE DocId = ?;")) {
                del.setString(1, docId);
                del.executeUpdate();
            }

            try (PreparedStatement del = conn.prepareStatement("DELETE FROM Document WHERE DocId = ?;")) {
                del.setString(1, docId);
                del.executeUpdate();
            }

            for (String h : new HashSet<>(hashes)) {
                int count = Collections.frequency(hashes, h);
                try (PreparedStatement upd = conn.prepareStatement("UPDATE Content SET RefCount = RefCount - ? WHERE Hash = ?;")) {
                    upd.setInt(1, count);
                    upd.setString(2, h);
                    upd.executeUpdate();
                }
            }
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM Content WHERE RefCount <= 0;");
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            log.error("Failed to delete document {}: {}", docId, e.getMessage(), e);
            return false;
        } finally {
            lock.unlock();
        }
    }

    public PurgeResult purgeOldEntries(int maxAgeDays, int maxEntriesPerDoc, int maxDbSizeMb) {
        long cutoffMs = System.currentTimeMillis() - ((long) maxAgeDays * 86400000L);
        int purgedCount = 0;

        lock.lock();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);

            // 1. Expired entries (protect IsStarred=1 and IsFavorite=1)
            List<Map.Entry<Long, String>> expired = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                SELECT h.EntryId, h.ContentHash
                FROM HistoryEntry h
                JOIN Document d ON d.DocId = h.DocId
                WHERE d.IsStarred = 0 AND h.IsFavorite = 0 AND h.ExecutedAt < ?;
            """)) {
                ps.setLong(1, cutoffMs);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        expired.add(new AbstractMap.SimpleEntry<>(rs.getLong(1), rs.getString(2)));
                    }
                }
            }

            for (Map.Entry<Long, String> item : expired) {
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM HistoryEntry WHERE EntryId = ?;")) {
                    del.setLong(1, item.getKey());
                    del.executeUpdate();
                }
                try (PreparedStatement upd = conn.prepareStatement("UPDATE Content SET RefCount = RefCount - 1 WHERE Hash = ?;")) {
                    upd.setString(1, item.getValue());
                    upd.executeUpdate();
                }
                purgedCount++;
            }

            // 2. Limit overflow entries per doc (protect IsFavorite=1)
            List<String> docIds = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT DocId FROM Document;")) {
                while (rs.next()) docIds.add(rs.getString(1));
            }

            for (String dId : docIds) {
                List<Map.Entry<Long, String>> overflow = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT EntryId, ContentHash
                    FROM HistoryEntry
                    WHERE DocId = ? AND IsFavorite = 0
                    ORDER BY ExecutedAt DESC, EntryId DESC
                    LIMIT -1 OFFSET ?;
                """)) {
                    ps.setString(1, dId);
                    ps.setInt(2, maxEntriesPerDoc);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            overflow.add(new AbstractMap.SimpleEntry<>(rs.getLong(1), rs.getString(2)));
                        }
                    }
                }

                for (Map.Entry<Long, String> item : overflow) {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM HistoryEntry WHERE EntryId = ?;")) {
                        del.setLong(1, item.getKey());
                        del.executeUpdate();
                    }
                    try (PreparedStatement upd = conn.prepareStatement("UPDATE Content SET RefCount = RefCount - 1 WHERE Hash = ?;")) {
                        upd.setString(1, item.getValue());
                        upd.executeUpdate();
                    }
                    purgedCount++;
                }
            }

            // 3. Delete orphan content
            try (Statement st = conn.createStatement()) {
                st.execute("DELETE FROM Content WHERE RefCount <= 0;");
            }

            // 4. Delete orphan documents
            try (Statement st = conn.createStatement()) {
                st.execute("""
                    DELETE FROM Document
                    WHERE DocId NOT IN (SELECT DISTINCT DocId FROM HistoryEntry)
                      AND FilePath IS NULL
                      AND IsStarred = 0;
                """);
            }

            conn.commit();

            // 5. Incremental vacuum
            try (Statement st = conn.createStatement()) {
                st.execute("PRAGMA incremental_vacuum;");
            }

            return new PurgeResult(purgedCount);
        } catch (SQLException e) {
            log.error("Failed to purge old entries: {}", e.getMessage(), e);
            return new PurgeResult(0);
        } finally {
            lock.unlock();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Export and Import
    // ─────────────────────────────────────────────────────────────────────────────

    public void exportJson(File targetFile) throws IOException {
        List<HistoryDocument> docs = getDocuments(null, false, false, null, null, null, null, 10000);
        List<Map<String, Object>> exportData = new ArrayList<>();

        for (HistoryDocument doc : docs) {
            Map<String, Object> docMap = new LinkedHashMap<>();
            docMap.put("docId", doc.getDocId());
            docMap.put("displayName", doc.getDisplayName());
            docMap.put("filePath", doc.getFilePath());
            docMap.put("isStarred", doc.isStarred());
            docMap.put("createdAt", doc.getCreatedAt());
            docMap.put("lastRunAt", doc.getLastRunAt());
            docMap.put("lastServer", doc.getLastServer());
            docMap.put("lastDatabase", doc.getLastDatabase());

            List<HistoryEntry> entries = getEntries(doc.getDocId(), null, null, null, null, null, null, null, null, false, 10000);
            List<Map<String, Object>> entriesList = new ArrayList<>();
            for (HistoryEntry e : entries) {
                HistoryEntry detail = getEntryDetail(e.getEntryId());
                if (detail != null) {
                    Map<String, Object> eMap = new LinkedHashMap<>();
                    eMap.put("entryId", detail.getEntryId());
                    eMap.put("entryType", detail.getEntryType());
                    eMap.put("executedAt", detail.getExecutedAt());
                    eMap.put("server", detail.getServer());
                    eMap.put("databaseName", detail.getDatabaseName());
                    eMap.put("loginName", detail.getLoginName());
                    eMap.put("isSelection", detail.isSelection());
                    eMap.put("durationMs", detail.getDurationMs());
                    eMap.put("status", detail.getStatus());
                    eMap.put("errorSummary", detail.getErrorSummary());
                    eMap.put("runCount", detail.getRunCount());
                    eMap.put("note", detail.getNote());
                    eMap.put("tag", detail.getTag());
                    eMap.put("tags", detail.getTags());
                    eMap.put("isFavorite", detail.isFavorite());
                    eMap.put("sql", detail.getSql());
                    entriesList.add(eMap);
                }
            }
            docMap.put("entries", entriesList);
            exportData.add(docMap);
        }

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, exportData);
    }

    public int importJson(File sourceFile) throws IOException {
        if (!sourceFile.exists()) return 0;
        List<Map<String, Object>> importData = objectMapper.readValue(sourceFile, new TypeReference<>() {});
        int count = 0;
        for (Map<String, Object> docMap : importData) {
            String docId = (String) docMap.get("docId");
            String displayName = (String) docMap.get("displayName");
            String filePath = (String) docMap.get("filePath");
            boolean isStarred = Boolean.TRUE.equals(docMap.get("isStarred"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> entries = (List<Map<String, Object>>) docMap.get("entries");
            if (entries != null) {
                for (Map<String, Object> eMap : entries) {
                    String sql = (String) eMap.get("sql");
                    String server = (String) eMap.get("server");
                    String database = (String) eMap.get("databaseName");
                    String login = (String) eMap.get("loginName");
                    boolean isSelection = Boolean.TRUE.equals(eMap.get("isSelection"));
                    int entryType = eMap.get("entryType") instanceof Number n ? n.intValue() : 1;
                    Long durationMs = eMap.get("durationMs") instanceof Number n ? n.longValue() : null;
                    Integer status = eMap.get("status") instanceof Number n ? n.intValue() : null;
                    String errorSummary = (String) eMap.get("errorSummary");
                    Long executedAt = eMap.get("executedAt") instanceof Number n ? n.longValue() : null;
                    String note = (String) eMap.get("note");
                    String tag = (String) eMap.get("tag");
                    boolean isFavorite = Boolean.TRUE.equals(eMap.get("isFavorite"));

                    recordExecution(docId, displayName, sql, server, database, filePath, login,
                            isSelection, entryType, durationMs, status, errorSummary, executedAt,
                            1048576, note, tag, isFavorite);
                    count++;
                }
            }
            if (isStarred) {
                toggleStar(docId);
            }
        }
        return count;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Result Records
    // ─────────────────────────────────────────────────────────────────────────────

    public record RecordResult(long entryId, String docId, String contentHash, boolean isNew, int runCount, long executedAt) {}
    public record PurgeResult(int purgedCount) {}
}
