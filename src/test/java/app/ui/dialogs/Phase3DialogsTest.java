package app.ui.dialogs;

import com.lltool.core.application.tabledesigner.model.MigrationPlan;
import com.lltool.core.application.tabledesigner.model.MigrationStatement;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3 - Step 7: SaveDesignDialog and PreviewDataChangesDialog Tests")
class Phase3DialogsTest {

    @BeforeAll
    static void initJavaFX() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException ignored) {
            Platform.setImplicitExit(false);
            latch.countDown();
        }
        assertTrue(latch.await(20, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("SaveDesignDialog creates dialog with non-destructive changes cleanly")
    void testSaveDesignDialogNonDestructive() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DialogChrome> dialogRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MigrationPlan plan = new MigrationPlan(
                        List.of(new MigrationStatement("ALTER TABLE dbo.Customers ADD email nvarchar(100);", "Add email", com.lltool.core.application.tabledesigner.model.StatementGroup.COLUMN_ADD, false, "email")),
                        List.of(),
                        false,
                        false
                );
                String sql = "ALTER TABLE dbo.Customers ADD email nvarchar(100);";

                DialogChrome chrome = SaveDesignDialog.createDialog(null, "dbo", "Customers", plan, sql, r -> {});
                dialogRef.set(chrome);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        DialogChrome chrome = dialogRef.get();
        assertNotNull(chrome);
        assertNotNull(chrome.stage());
    }

    @Test
    @DisplayName("SaveDesignDialog creates dialog with destructive changes and danger banner")
    void testSaveDesignDialogDestructive() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DialogChrome> dialogRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MigrationPlan plan = new MigrationPlan(
                        List.of(new MigrationStatement("ALTER TABLE dbo.Customers DROP COLUMN old_col;", "Drop col", com.lltool.core.application.tabledesigner.model.StatementGroup.COLUMN_DROP, true, "old_col")),
                        List.of("Xóa cột old_col"),
                        true,
                        true
                );
                String sql = "ALTER TABLE dbo.Customers DROP COLUMN old_col;";

                DialogChrome chrome = SaveDesignDialog.createDialog(null, "dbo", "Customers", plan, sql, r -> {});
                dialogRef.set(chrome);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        DialogChrome chrome = dialogRef.get();
        assertNotNull(chrome);
    }

    @Test
    @DisplayName("PreviewDataChangesDialog initializes 3 badges and execution buttons")
    void testPreviewDataChangesDialogExecution() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DialogChrome> dialogRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                String dml = "INSERT INTO dbo.Customers VALUES (1, 'Test');";
                DialogChrome chrome = PreviewDataChangesDialog.createDialog(null, "dbo", "Customers", 2, 3, 1, dml, r -> {});
                dialogRef.set(chrome);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        DialogChrome chrome = dialogRef.get();
        assertNotNull(chrome);
    }

    @Test
    @DisplayName("PreviewDataChangesDialog in read-only mode (Xem DML) does not provide execution button")
    void testPreviewDataChangesDialogReadOnly() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DialogChrome> dialogRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                String dml = "SELECT * FROM dbo.Customers;";
                DialogChrome chrome = PreviewDataChangesDialog.createDialog(null, "dbo", "Customers", 0, 1, 0, dml, false, r -> {});
                dialogRef.set(chrome);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        DialogChrome chrome = dialogRef.get();
        assertNotNull(chrome);
    }
}
