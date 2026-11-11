package app.ui.visualizer;

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

@DisplayName("Feature 06: SqlVisualizerTab Component Tests")
class SqlVisualizerTabTest {

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
    @DisplayName("SqlVisualizerTab initializes controls, header toolbar, canvas and inspector cleanly")
    void testTabInitialization() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<SqlVisualizerTab> tabRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                ConnectionProfileDto profile = new ConnectionProfileDto(
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

                SqlVisualizerTab tab = new SqlVisualizerTab(
                        profile,
                        "B10_PhucLoi",
                        "dbo",
                        "CustomerRevenueQuery",
                        null,
                        action -> {}
                );

                tabRef.set(tab);
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        SqlVisualizerTab tab = tabRef.get();
        assertNotNull(tab);
        assertTrue(tab.getTabTitle().contains("CustomerRevenueQuery"));
        assertNotNull(tab.getCanvas());
        assertNotNull(tab.getInspector());
        assertNotNull(tab.getCustomKey());
        assertTrue(tab.getCustomKey().startsWith("sql-visualizer-"));
    }

    @Test
    @DisplayName("Canvas selectNode updates Inspector and selected state")
    void testCanvasNodeSelection() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                SqlVisualizerCanvas canvas = new SqlVisualizerCanvas();
                SqlVisualizerInspector inspector = new SqlVisualizerInspector();

                com.lltool.core.domain.visualizer.QueryFlowNode node = new com.lltool.core.domain.visualizer.QueryFlowNode(
                        "node_1",
                        com.lltool.core.domain.visualizer.QueryNodeType.SOURCE_TABLE,
                        "dbo.customers",
                        "Base Table"
                );
                node.setEstimatedCost("25%");
                node.setRowCount(5000L);
                node.setSqlFragment("FROM dbo.customers");

                inspector.inspectNode(node);
                assertEquals(node, inspector.getCurrentNode());

                canvas.setOnNodeSelectedListener(inspector::inspectNode);
                canvas.selectNode(node);
                assertEquals(node, canvas.getSelectedNode());
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }
}
