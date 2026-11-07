package app.framework.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TabSnapshotStoreTest {

    @Test
    void roundTripKeepsUnsavedTextAndTabContext(@TempDir Path dir) throws Exception {
        TabSnapshotStore store = new TabSnapshotStore(dir.resolve("cfg").resolve("tab-snapshots.json"));
        var a = new TabSnapshotStore.Snap("console_1.sql", null, "select 1;\n-- tiếng Việt", true,
                "Prod", "sales", "dbo", true, false);
        var b = new TabSnapshotStore.Snap("x.sql", "C:\\q\\x.sql", null, false, null, null, null, false, true);
        store.writeJson(store.toJson(List.of(a, b)));

        List<TabSnapshotStore.Snap> back = store.read();
        assertEquals(List.of(a, b), back);
    }

    @Test
    void missingOrBrokenFileGivesEmptyList(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("tab-snapshots.json");
        TabSnapshotStore store = new TabSnapshotStore(f);
        assertTrue(store.read().isEmpty());
        java.nio.file.Files.writeString(f, "{ not json");
        assertTrue(store.read().isEmpty());
    }
}
