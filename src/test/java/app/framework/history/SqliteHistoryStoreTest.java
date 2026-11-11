package app.framework.history;

import app.models.HistoryDocument;
import app.models.HistoryEntry;
import app.models.HistoryTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqliteHistoryStoreTest {

    @TempDir
    Path tempDir;

    private SqliteHistoryStore store;
    private Path dbFile;

    @BeforeEach
    void setUp() {
        dbFile = tempDir.resolve("test_history.db");
        store = new SqliteHistoryStore(dbFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        // TempDir handles cleanup
    }

    @Test
    void testRecordExecutionAndDedup() {
        String docId = "doc-1";
        String sql = "SELECT * FROM Users WHERE active = 1;";

        // First run
        var res1 = store.recordExecution(docId, "users.sql", sql, "SRV1", "AppDb", "/path/to/users.sql", "admin", false,
                HistoryEntry.TYPE_EXECUTION, 120L, HistoryEntry.STATUS_OK, null, System.currentTimeMillis(), 1048576, null, null, false);
        assertNotNull(res1);
        assertTrue(res1.isNew());
        assertEquals(1, res1.runCount());

        // Second run with identical SQL, server, database -> should coalesce!
        var res2 = store.recordExecution(docId, "users.sql", sql, "SRV1", "AppDb", "/path/to/users.sql", "admin", false,
                HistoryEntry.TYPE_EXECUTION, 115L, HistoryEntry.STATUS_OK, null, System.currentTimeMillis() + 1000, 1048576, null, null, false);
        assertNotNull(res2);
        assertFalse(res2.isNew());
        assertEquals(2, res2.runCount());
        assertEquals(res1.entryId(), res2.entryId());

        // Detail check
        HistoryEntry detail = store.getEntryDetail(res1.entryId());
        assertNotNull(detail);
        assertEquals(2, detail.getRunCount());
        assertEquals(sql, detail.getSql());
        assertEquals("SRV1", detail.getServer());
        assertEquals("AppDb", detail.getDatabaseName());
        assertEquals(HistoryEntry.STATUS_OK, detail.getStatus());
    }

    @Test
    void testTrivialQueryIgnored() {
        var res1 = store.recordExecution("doc-2", "empty.sql", "   \n\t  ", "SRV1", "AppDb", null, null, false,
                HistoryEntry.TYPE_EXECUTION, null, null, null, null, 1048576, null, null, false);
        assertNull(res1);

        var res2 = store.recordExecution("doc-2", "comment.sql", "-- just a comment\n/* multi\nline */", "SRV1", "AppDb", null, null, false,
                HistoryEntry.TYPE_EXECUTION, null, null, null, null, 1048576, null, null, false);
        assertNull(res2);
    }

    @Test
    void testUpdateEntryResult() {
        var res = store.recordExecution("doc-3", "query.sql", "SELECT 42;", "SRV1", "Db", null, null, false,
                HistoryEntry.TYPE_EXECUTION, null, null, null, null, 1048576, null, null, false);
        assertNotNull(res);

        boolean updated = store.updateEntryResult(res.entryId(), 45L, HistoryEntry.STATUS_OK, null);
        assertTrue(updated);

        HistoryEntry detail = store.getEntryDetail(res.entryId());
        assertNotNull(detail);
        assertEquals(45L, detail.getDurationMs());
        assertEquals(HistoryEntry.STATUS_OK, detail.getStatus());
        assertNull(detail.getErrorSummary());

        // Test error status update
        boolean updatedErr = store.updateEntryResult(res.entryId(), 500L, HistoryEntry.STATUS_ERROR, "Table 'foo' does not exist");
        assertTrue(updatedErr);

        HistoryEntry detailErr = store.getEntryDetail(res.entryId());
        assertNotNull(detailErr);
        assertEquals(HistoryEntry.STATUS_ERROR, detailErr.getStatus());
        assertEquals("Table 'foo' does not exist", detailErr.getErrorSummary());
    }

    @Test
    void testDocumentMergeOnFilePathUpdate() {
        String doc1 = "doc-uuid-1";
        String doc2 = "doc-uuid-2";
        String filePath = "/work/project/script.sql";

        // doc1 has an execution with filePath
        store.recordExecution(doc1, "script.sql", "SELECT 1;", "SRV", "DB", filePath, null, false,
                HistoryEntry.TYPE_EXECUTION, 10L, 0, null, 1000L, 1048576, null, null, false);

        // doc2 is an unsaved scratch tab with another execution
        store.recordExecution(doc2, "Untitled-2", "SELECT 2;", "SRV", "DB", null, null, false,
                HistoryEntry.TYPE_EXECUTION, 20L, 0, null, 2000L, 1048576, null, null, false);

        // Now doc2 is saved over the same filePath!
        boolean merged = store.updateDocument(doc2, "script.sql", filePath);
        assertTrue(merged);

        // doc2 entries should now be merged into doc1
        List<HistoryEntry> entries = store.getEntries(doc1, null, null, null, null, null, null, null, null, false, 100);
        assertEquals(2, entries.size());

        // doc2 should be gone
        List<HistoryEntry> doc2Entries = store.getEntries(doc2, null, null, null, null, null, null, null, null, false, 100);
        assertTrue(doc2Entries.isEmpty());
    }

    @Test
    void testFavoritesAndTags() {
        var res = store.recordExecution("doc-fav", "fav.sql", "SELECT * FROM ImportantTable;", "SRV", "DB", null, null, false,
                HistoryEntry.TYPE_EXECUTION, 30L, 0, null, null, 1048576, "Initial note", "InitialTag", false);
        assertNotNull(res);

        // Document star toggle
        boolean starred = store.toggleStar("doc-fav");
        assertTrue(starred);
        List<HistoryDocument> favDocs = store.getDocuments(null, true, false, null, null, null, null, 10);
        assertEquals(1, favDocs.size());
        assertEquals("doc-fav", favDocs.get(0).getDocId());

        // Entry favorite toggle
        boolean entryFav = store.setEntryFavorite(res.entryId(), true);
        assertTrue(entryFav);
        List<HistoryEntry> favEntries = store.getEntries("doc-fav", null, null, null, null, null, null, null, null, true, 10);
        assertEquals(1, favEntries.size());

        // Add / remove tags
        boolean tagAdded = store.addEntryTag(res.entryId(), "Release");
        assertTrue(tagAdded);

        List<HistoryTag> allTags = store.getAllTags();
        assertFalse(allTags.isEmpty());

        HistoryEntry detail = store.getEntryDetail(res.entryId());
        assertTrue(detail.getTags().contains("Release"));

        boolean tagRemoved = store.removeEntryTag(res.entryId(), "Release");
        assertTrue(tagRemoved);
        HistoryEntry detailAfter = store.getEntryDetail(res.entryId());
        assertFalse(detailAfter.getTags().contains("Release"));
    }

    @Test
    void testDeleteEntryAndOrphanCleanup() {
        var res1 = store.recordExecution("doc-del", "del.sql", "SELECT 'unique_content_hash_1';", "SRV", "DB", null, null, false,
                HistoryEntry.TYPE_EXECUTION, 10L, 0, null, null, 1048576, null, null, false);
        assertNotNull(res1);

        String hash = res1.contentHash();
        assertNotNull(store.getContent(hash));

        // Delete entry
        boolean deleted = store.deleteEntry(res1.entryId());
        assertTrue(deleted);

        // Content should be removed since RefCount reached 0
        assertNull(store.getContent(hash));
    }

    @Test
    void testRetentionPurge() {
        String docId = "doc-retention";
        long oldTime = System.currentTimeMillis() - (100L * 86400000L); // 100 days ago

        // Insert 5 old entries
        for (int i = 0; i < 5; i++) {
            store.recordExecution(docId, "old.sql", "SELECT " + i + ";", "SRV", "DB", null, null, false,
                    HistoryEntry.TYPE_EXECUTION, 10L, 0, null, oldTime + i, 1048576, null, null, false);
        }

        // Insert 1 old entry that is marked favorite
        var favRes = store.recordExecution(docId, "old.sql", "SELECT 'protected_fav';", "SRV", "DB", null, null, false,
                HistoryEntry.TYPE_EXECUTION, 10L, 0, null, oldTime + 10, 1048576, null, null, true);
        assertNotNull(favRes);

        // Run purge with 90 days max age
        var purgeRes = store.purgeOldEntries(90, 200, 500);
        assertTrue(purgeRes.purgedCount() >= 5);

        // Protected favorite entry should still exist!
        assertNotNull(store.getEntryDetail(favRes.entryId()));
    }

    @Test
    void testExportAndImportJson() throws IOException {
        String docId = "doc-export";
        String sql = "SELECT 100 AS result_val;";
        var res = store.recordExecution(docId, "export_test.sql", sql, "SRV9", "AnalyticsDb", "/var/export_test.sql", "sa", false,
                HistoryEntry.TYPE_EXECUTION, 88L, HistoryEntry.STATUS_OK, null, null, 1048576, "Test note", "Migration", true);
        assertNotNull(res);

        File jsonFile = tempDir.resolve("history_export.json").toFile();
        store.exportJson(jsonFile);
        assertTrue(jsonFile.exists());
        assertTrue(jsonFile.length() > 0);

        // Create new store and import
        Path newDb = tempDir.resolve("imported_history.db");
        SqliteHistoryStore newStore = new SqliteHistoryStore(newDb);
        int imported = newStore.importJson(jsonFile);
        assertTrue(imported >= 1);

        List<HistoryDocument> docs = newStore.getDocuments("export_test", false, false, null, null, null, null, 10);
        assertEquals(1, docs.size());
        assertEquals("export_test.sql", docs.get(0).getDisplayName());
    }
}
