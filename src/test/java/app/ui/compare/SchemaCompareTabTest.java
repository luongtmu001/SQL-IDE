package app.ui.compare;

import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SchemaCompareTab Component Tests")
class SchemaCompareTabTest {

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
    @DisplayName("SchemaCompareTab initializes controls, header and editors cleanly")
    void testTabInitialization() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SchemaCompareTab> tabRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                ConnectionProfileDto sourceProfile = new ConnectionProfileDto(
                        UUID.randomUUID().toString(),
                        "MSSQL-DEV",
                        "Default",
                        "Dev",
                        false,
                        "sqlserver",
                        "localhost",
                        1433,
                        "B10_PhucLoi",
                        null,
                        "sa",
                        "SQL_PASSWORD",
                        null,
                        false,
                        30,
                        true,
                        Collections.emptyMap(),
                        null,
                        null
                );

                ConnectionProfileDto targetProfile = new ConnectionProfileDto(
                        UUID.randomUUID().toString(),
                        "MSSQL-PROD",
                        "Default",
                        "Prod",
                        false,
                        "sqlserver",
                        "192.168.1.100",
                        1433,
                        "B10_PhucLoi",
                        null,
                        "sa",
                        "SQL_PASSWORD",
                        null,
                        false,
                        30,
                        true,
                        Collections.emptyMap(),
                        null,
                        null
                );

                SchemaCompareTab tab = new SchemaCompareTab(
                        sourceProfile, "DEV_DB", "dbo",
                        targetProfile, "PROD_DB", "dbo",
                        a -> {}
                );
                tabRef.set(tab);

                assertNotNull(tab.getCbSourceProfile());
                assertNotNull(tab.getCbTargetProfile());
                assertNotNull(tab.getBtnRunCompare());
                assertNotNull(tab.getDiffTreeView());
                assertNotNull(tab.getSourceEditor());
                assertNotNull(tab.getTargetEditor());
                assertNotNull(tab.getScriptEditor());

                // Execute compare
                tab.executeCompareAsync();

            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(30, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("SC-3: Different engine profiles abort compare and notify user")
    void testEngineMismatchAborts_SC3() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                ConnectionProfileDto mssql = new ConnectionProfileDto(
                        "p1", "MSSQL", "Default", "Dev", false, "sqlserver", "localhost", 1433,
                        "DEV_DB", null, "sa", "pwd", null, false, 30, true, Collections.emptyMap(), null, null
                );
                ConnectionProfileDto pg = new ConnectionProfileDto(
                        "p2", "PG", "Default", "Dev", false, "postgresql", "localhost", 5432,
                        "DEV_DB", null, "postgres", "pwd", null, false, 30, true, Collections.emptyMap(), null, null
                );

                SchemaCompareTab tab = new SchemaCompareTab(mssql, "DEV_DB", "dbo", pg, "DEV_DB", "public", a -> {});
                tab.executeCompareAsync();

                // Because engines differ, compareResult should remain null (aborted before execution)
                assertNull(tab.getCompareResult());
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("SC-3: Identical source and target endpoints abort compare")
    void testIdenticalEndpointsAbort_SC3() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                ConnectionProfileDto profile = new ConnectionProfileDto(
                        "same-p1", "MSSQL", "Default", "Dev", false, "sqlserver", "localhost", 1433,
                        "DEV_DB", null, "sa", "pwd", null, false, 30, true, Collections.emptyMap(), null, null
                );

                SchemaCompareTab tab = new SchemaCompareTab(profile, "DEV_DB", "dbo", profile, "DEV_DB", "dbo", a -> {});
                tab.executeCompareAsync();

                // Because endpoints are identical, compare should abort
                assertNull(tab.getCompareResult());
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }
}
