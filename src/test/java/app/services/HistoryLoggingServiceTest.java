package app.services;

import app.models.HistoryDocument;
import app.models.HistoryEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class HistoryLoggingServiceTest {

    @TempDir
    Path tempDir;

    private HistoryLoggingService service;

    @BeforeEach
    void setUp() {
        Path dbFile = tempDir.resolve("test_service_history.db");
        service = new HistoryLoggingService(dbFile);
        HistoryLoggingService.setCustomInstance(service);
    }

    @AfterEach
    void tearDown() {
        HistoryLoggingService.setCustomInstance(null);
    }

    @Test
    void testExecutionLoggingFlow() throws InterruptedException {
        String docId = "test-tab-1";
        String sql = "SELECT customer_id, name FROM customers WHERE status = 'ACTIVE';";

        Long entryId = service.recordExecution(
                docId, "customers.sql", sql, "PROD-SRV", "CrmDb", "/data/customers.sql", "app_user", false
        );

        assertNotNull(entryId);
        assertTrue(entryId > 0);

        // Update result
        service.updateExecutionResult(entryId, 84L, HistoryEntry.STATUS_OK, null);

        // Allow async worker to process
        Thread.sleep(150);

        HistoryEntry detail = service.getEntryDetail(entryId);
        assertNotNull(detail);
        assertEquals(sql, detail.getSql());
        assertEquals("PROD-SRV", detail.getServer());
        assertEquals("CrmDb", detail.getDatabaseName());
        assertEquals(HistoryEntry.STATUS_OK, detail.getStatus());
        assertEquals(84L, detail.getDurationMs());

        List<HistoryDocument> docs = service.getDocuments(null, false, null, null, null, null);
        assertEquals(1, docs.size());
        assertEquals("customers.sql", docs.get(0).getDisplayName());
    }

    @Test
    void testSnapshotLoggingOnSave() throws InterruptedException {
        String docId = "test-tab-save";
        String filePath = tempDir.resolve("saved_script.sql").toString();
        String sql = "CREATE TABLE Demo (Id INT);";

        service.recordSnapshot(docId, "saved_script.sql", filePath, sql, "SRV", "DB");
        service.updateDocument(docId, "saved_script.sql", filePath);

        Thread.sleep(150);

        List<HistoryEntry> entries = service.getEntries(docId, null, null, null, null, null, null, null, null, false);
        assertEquals(1, entries.size());
        assertEquals(HistoryEntry.TYPE_SNAPSHOT, entries.get(0).getEntryType());
    }

    @Test
    void testTagsAndNotes() {
        String docId = "test-notes-doc";
        Long entryId = service.recordExecution(docId, "note.sql", "SELECT 1;", "SRV", "DB", null, null, false);
        assertNotNull(entryId);

        boolean noteOk = service.updateEntryNote(entryId, "Special note for auditing");
        assertTrue(noteOk);

        boolean tagOk = service.addEntryTag(entryId, "Audit");
        assertTrue(tagOk);

        HistoryEntry detail = service.getEntryDetail(entryId);
        assertEquals("Special note for auditing", detail.getNote());
        assertTrue(detail.getTags().contains("Audit"));
    }
}
