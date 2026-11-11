package app.ui.dependency;

import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.domain.dependency.DependencyNodeType;
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

@DisplayName("Feature 05: DependencyGraphTab Component Tests")
class DependencyGraphTabTest {

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
    @DisplayName("DependencyGraphTab initializes controls, header toolbar, canvas and inspector cleanly")
    void testTabInitialization() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DependencyGraphTab> tabRef = new AtomicReference<>();

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

                DependencyGraphTab tab = new DependencyGraphTab(
                        profile,
                        "B10_PhucLoi",
                        "dbo",
                        "orders",
                        null,
                        DependencyNodeType.TABLE,
                        action -> {}
                );

                tabRef.set(tab);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(30, TimeUnit.SECONDS), "DependencyGraphTab creation timed out");
        DependencyGraphTab tab = tabRef.get();
        assertNotNull(tab, "DependencyGraphTab instance should not be null");

        // Verify title & custom key
        assertTrue(tab.getTabTitle().contains("dbo.orders"));
        assertTrue(tab.getCustomKey().contains("dep_graph|"));
        assertTrue(tab.getCustomKey().contains("dbo.orders"));
        assertNotNull(tab.getContent());
    }

    @Test
    @DisplayName("DG-2: RootObjectItem holds schema, name, and type without string-splitting issues")
    void testRootObjectItem() {
        RootObjectItem item1 = new RootObjectItem("sales", "order.details", DependencyNodeType.TABLE);
        assertEquals("sales", item1.schema());
        assertEquals("order.details", item1.name());
        assertEquals(DependencyNodeType.TABLE, item1.type());
        assertEquals("sales.order.details", item1.getDisplayName());

        RootObjectItem item2 = new RootObjectItem(null, "my proc with spaces", DependencyNodeType.PROCEDURE);
        assertEquals("dbo", item2.schema());
        assertEquals("my proc with spaces", item2.name());
        assertEquals(DependencyNodeType.PROCEDURE, item2.type());
        assertEquals("dbo.my proc with spaces", item2.getDisplayName());
    }

    @Test
    @DisplayName("DG-1 & DG-7: DependencyGraphLoader loads mock graph cleanly when profile is null or mock enabled")
    void testDependencyGraphLoaderMock() throws Exception {
        var model = app.framework.dependency.DependencyGraphLoader.load(
                null, "test_cat", "dbo", "orders", null,
                DependencyNodeType.TABLE,
                com.lltool.core.domain.dependency.GraphDirection.BOTH,
                2,
                msg -> {}
        );

        assertNotNull(model);
        assertEquals("dbo.orders", model.getRootNodeId());
        assertTrue(model.getNodeCount() >= 5);
        assertTrue(model.getEdgeCount() >= 4);
    }

    @Test
    @DisplayName("DG-1: BFS cycle resilience - mutual reference A <-> B terminates without infinite loop")
    void testCycleResilience() {
        com.lltool.core.domain.dependency.DependencyGraphModel model =
                new com.lltool.core.domain.dependency.DependencyGraphModel("dbo.a", com.lltool.core.domain.dependency.GraphDirection.BOTH, 5);

        com.lltool.core.domain.dependency.DependencyNode nodeA =
                com.lltool.core.domain.dependency.DependencyNode.of("dbo", "a", DependencyNodeType.TABLE, true);
        com.lltool.core.domain.dependency.DependencyNode nodeB =
                com.lltool.core.domain.dependency.DependencyNode.of("dbo", "b", DependencyNodeType.TABLE, false);

        model.addNode(nodeA);
        model.addNode(nodeB);

        // Cyclic foreign keys: A -> B and B -> A
        model.addEdge(com.lltool.core.domain.dependency.DependencyEdge.of(nodeA.getId(), nodeB.getId(), com.lltool.core.domain.dependency.RelationType.FOREIGN_KEY_TO, "FK_A_B"));
        model.addEdge(com.lltool.core.domain.dependency.DependencyEdge.of(nodeB.getId(), nodeA.getId(), com.lltool.core.domain.dependency.RelationType.FOREIGN_KEY_TO, "FK_B_A"));

        com.lltool.core.application.dependency.DependencyGraphService svc = new com.lltool.core.application.dependency.DependencyGraphService();
        assertDoesNotThrow(() -> svc.calculateHierarchicalLayout(model));
        assertDoesNotThrow(() -> svc.generateSuggestedSteps(model, nodeA.getId()));

        assertEquals(2, model.getNodeCount());
        assertEquals(2, model.getEdgeCount());
    }
}
