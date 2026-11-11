package app.ui.window;

import app.controllers.EditorController;
import app.ui.components.IconButton;
import app.ui.editor.SqlEditorTab;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.domain.dependency.DependencyNodeType;
import javafx.application.Platform;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Dual-mode Display (Tab & Window) Tests for 5 Core Features")
class FeatureDualModeDisplayTest {

    private static ConnectionProfileDto testProfile;

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

        testProfile = new ConnectionProfileDto(
                UUID.randomUUID().toString(),
                "Test Conn",
                "Default",
                "Dev",
                false,
                "SQLSERVER",
                "localhost",
                1433,
                "master",
                null,
                "sa",
                "pwd",
                null,
                false,
                30,
                true,
                Collections.emptyMap(),
                null,
                null
        );
    }

    @Test
    @DisplayName("1. Schema Compare: Toggle between Tab and Window modes")
    void testSchemaCompareDualMode() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TabPane tabPane = new TabPane();
                EditorController ec = new EditorController();
                ec.setPrimaryTabPane(tabPane);

                // Open as Tab (from Object Explorer flow)
                var tab = ec.openSchemaCompareTab(testProfile, "master", "dbo", testProfile, "master", "dbo");
                assertNotNull(tab);
                assertEquals(1, tabPane.getTabs().size());
                assertSame(tab, tabPane.getTabs().get(0));

                IconButton toggleBtn = tab.getToggleWindowButton();
                assertNotNull(toggleBtn);

                // Toggle to Window
                FeatureWindowManager.getInstance().toggleWindow(tab, tab.getContentRoot(), toggleBtn, "Schema Compare", "fth-git-commit");
                assertTrue(FeatureWindowManager.getInstance().isWindowMode(tab));
                assertEquals(0, tabPane.getTabs().size());

                Stage stage = FeatureWindowManager.getInstance().getStage(tab);
                assertNotNull(stage);
                assertTrue(stage.isShowing());

                // Toggle back to Tab
                FeatureWindowManager.getInstance().toggleWindow(tab, tab.getContentRoot(), toggleBtn, "Schema Compare", "fth-git-commit");
                assertFalse(FeatureWindowManager.getInstance().isWindowMode(tab));
                assertEquals(1, tabPane.getTabs().size());
                assertSame(tab, tabPane.getTabs().get(0));

                // Open from Menu flow -> Direct Window
                Stage menuStage = ec.openSchemaCompareWindow(testProfile, "master", "dbo", testProfile, "master", "dbo");
                assertNotNull(menuStage);
                assertTrue(FeatureWindowManager.getInstance().isWindowMode(tab));

                stage.close();
                menuStage.close();
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("2. Search Object: Toggle between Tab and Window modes")
    void testObjectSearchDualMode() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TabPane tabPane = new TabPane();
                EditorController ec = new EditorController();
                ec.setPrimaryTabPane(tabPane);

                // Open as Tab (from Object Explorer flow)
                var tab = ec.openObjectContentSearchTab(testProfile, "master", "customers");
                assertNotNull(tab);
                assertEquals(1, tabPane.getTabs().size());

                IconButton toggleBtn = tab.getToggleWindowButton();
                assertNotNull(toggleBtn);

                // Toggle to Window
                FeatureWindowManager.getInstance().toggleWindow(tab, tab.getContentRoot(), toggleBtn, "Search Objects", "fth-search");
                assertTrue(FeatureWindowManager.getInstance().isWindowMode(tab));
                assertEquals(0, tabPane.getTabs().size());

                Stage stage = FeatureWindowManager.getInstance().getStage(tab);
                assertNotNull(stage);

                // Toggle back to Tab
                FeatureWindowManager.getInstance().toggleWindow(tab, tab.getContentRoot(), toggleBtn, "Search Objects", "fth-search");
                assertFalse(FeatureWindowManager.getInstance().isWindowMode(tab));
                assertEquals(1, tabPane.getTabs().size());

                stage.close();
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("3. New Table (Table Designer): Toggle between Tab and Window modes")
    void testTableDesignerDualMode() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TabPane tabPane = new TabPane();
                EditorController ec = new EditorController();
                ec.setPrimaryTabPane(tabPane);

                // Open as Tab (from Object Explorer flow)
                var tab = ec.openTableDesignerTab(testProfile, "master", "dbo", "NewTable_1", true);
                assertNotNull(tab);
                assertEquals(1, tabPane.getTabs().size());

                IconButton toggleBtn = tab.getToggleWindowButton();
                assertNotNull(toggleBtn);

                // Toggle to Window
                FeatureWindowManager.getInstance().toggleWindow(tab, tab.getContentRoot(), toggleBtn, "New Table", "fth-edit-3");
                assertTrue(FeatureWindowManager.getInstance().isWindowMode(tab));
                assertEquals(0, tabPane.getTabs().size());

                Stage stage = FeatureWindowManager.getInstance().getStage(tab);
                assertNotNull(stage);

                // Toggle back to Tab
                FeatureWindowManager.getInstance().toggleWindow(tab, tab.getContentRoot(), toggleBtn, "New Table", "fth-edit-3");
                assertFalse(FeatureWindowManager.getInstance().isWindowMode(tab));
                assertEquals(1, tabPane.getTabs().size());

                stage.close();
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("4. Dependency Graph: Toggle between Tab and Window modes")
    void testDependencyGraphDualMode() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TabPane tabPane = new TabPane();
                EditorController ec = new EditorController();
                ec.setPrimaryTabPane(tabPane);

                // Open as Tab (from Object Explorer flow)
                var tab = ec.openDependencyGraphTab(testProfile, "master", "dbo", "orders", null, DependencyNodeType.TABLE);
                assertNotNull(tab);
                assertEquals(1, tabPane.getTabs().size());

                IconButton toggleBtn = tab.getToggleWindowButton();
                assertNotNull(toggleBtn);

                // Toggle to Window
                FeatureWindowManager.getInstance().toggleWindow(tab, tab.getContentRoot(), toggleBtn, "Dependency Graph", "fth-share-2");
                assertTrue(FeatureWindowManager.getInstance().isWindowMode(tab));
                assertEquals(0, tabPane.getTabs().size());

                Stage stage = FeatureWindowManager.getInstance().getStage(tab);
                assertNotNull(stage);

                // Toggle back to Tab
                FeatureWindowManager.getInstance().toggleWindow(tab, tab.getContentRoot(), toggleBtn, "Dependency Graph", "fth-share-2");
                assertFalse(FeatureWindowManager.getInstance().isWindowMode(tab));
                assertEquals(1, tabPane.getTabs().size());

                stage.close();
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("5. SQL Visualizer: Toggle between Tab and Window modes")
    void testSqlVisualizerDualMode() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                TabPane tabPane = new TabPane();
                EditorController ec = new EditorController();
                ec.setPrimaryTabPane(tabPane);

                // Open as Tab (from Object Explorer flow)
                var tab = ec.openSqlVisualizerTab(testProfile, "master", "dbo", "Query1", "SELECT * FROM orders");
                assertNotNull(tab);
                assertEquals(1, tabPane.getTabs().size());

                IconButton toggleBtn = tab.getToggleWindowButton();
                assertNotNull(toggleBtn);

                // Toggle to Window
                FeatureWindowManager.getInstance().toggleWindow(tab, tab.getContentRoot(), toggleBtn, "SQL Visualizer", "fth-cpu");
                assertTrue(FeatureWindowManager.getInstance().isWindowMode(tab));
                assertEquals(0, tabPane.getTabs().size());

                Stage stage = FeatureWindowManager.getInstance().getStage(tab);
                assertNotNull(stage);

                // Toggle back to Tab
                FeatureWindowManager.getInstance().toggleWindow(tab, tab.getContentRoot(), toggleBtn, "SQL Visualizer", "fth-cpu");
                assertFalse(FeatureWindowManager.getInstance().isWindowMode(tab));
                assertEquals(1, tabPane.getTabs().size());

                stage.close();
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(15, TimeUnit.SECONDS));
    }
}
