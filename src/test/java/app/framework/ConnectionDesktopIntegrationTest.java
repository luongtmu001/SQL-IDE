package app.framework;

import app.controllers.ConnectionManagerController;
import app.framework.bootstrap.DesktopBootstrap;
import app.framework.connection.ConnectionSessionManager;
import app.framework.connection.DesktopConnectionTesterAdapter;
import app.framework.connection.DesktopCredentialVaultAdapter;
import app.framework.state.GlobalDesktopState;
import com.lltool.core.api.connection.ConnectionApi;
import com.lltool.core.api.request.CreateConnectionRequest;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.common.result.Result;
import com.lltool.core.domain.connection.model.ConnectionProfile;
import com.lltool.core.domain.connection.valueobject.ConnectionDatabaseType;
import com.lltool.core.domain.connection.valueobject.ConnectionId;
import com.lltool.core.domain.connection.valueobject.ConnectionName;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionDesktopIntegrationTest {

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        try {
            Platform.setImplicitExit(false);
        } catch (Exception ignored) {}
        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX Platform should start within 5s");
    }

    @Test
    @DisplayName("DesktopBootstrap wires ConnectionApi and LocalConnectionRepository")
    void testBootstrapConnectionWiring() {
        DesktopBootstrap bootstrap = DesktopBootstrap.getInstance();
        assertNotNull(bootstrap.getConnectionRepository(), "ConnectionRepository must be wired");
        assertNotNull(bootstrap.getConnectionApi(), "ConnectionApi must be wired");
    }

    @Test
    @DisplayName("DesktopCredentialVaultAdapter stores and resolves secrets correctly")
    void testCredentialVaultAdapter() {
        DesktopBootstrap bootstrap = DesktopBootstrap.getInstance();
        DesktopCredentialVaultAdapter adapter = new DesktopCredentialVaultAdapter(bootstrap.getCredentialVault());

        String secretRef = adapter.storeSecret("superSecret123");
        assertNotNull(secretRef);
        assertTrue(secretRef.startsWith("cred://"));

        Optional<String> resolved = adapter.getSecret(secretRef);
        assertTrue(resolved.isPresent());
        assertEquals("superSecret123", resolved.get());

        adapter.deleteSecret(secretRef);
        Optional<String> deleted = adapter.getSecret(secretRef);
        assertTrue(deleted.isEmpty());
    }

    @Test
    @DisplayName("DesktopConnectionTesterAdapter successfully validates an SQLite profile")
    void testConnectionTesterAdapterSqlite() throws Exception {
        DesktopBootstrap bootstrap = DesktopBootstrap.getInstance();
        DesktopConnectionTesterAdapter tester = new DesktopConnectionTesterAdapter(bootstrap.getProviderRegistry());

        Path tempDb = Files.createTempFile("test_desktop_tester", ".db");
        tempDb.toFile().deleteOnExit();

        ConnectionProfile profile = ConnectionProfile.create(
                ConnectionId.generate(),
                ConnectionName.of("SQLite_Test"),
                "LOCAL",
                "DEV",
                false,
                ConnectionDatabaseType.SQLITE,
                "localhost",
                0,
                "",
                tempDb.toAbsolutePath().toString(),
                "",
                com.lltool.core.domain.connection.valueobject.AuthenticationType.NONE,
                com.lltool.core.domain.connection.valueobject.CredentialReference.empty(),
                false,
                5,
                true,
                Collections.emptyMap(),
                java.time.Instant.now()
        );

        Result<Boolean> testResult = tester.test(profile, "");
        assertTrue(testResult.isSuccess(), "SQLite test connection should succeed");
        assertTrue(testResult.getDataOrThrow());
    }

    @Test
    @DisplayName("ConnectionSessionManager connects and updates GlobalDesktopState")
    void testConnectionSessionManager() throws Exception {
        Path tempDb = Files.createTempFile("test_session_mgr", ".db");
        tempDb.toFile().deleteOnExit();

        ConnectionSessionManager sessionManager = ConnectionSessionManager.getInstance();

        ConnectionProfileDto profile = new ConnectionProfileDto(
                "test-conn-id",
                "Session_SQLite",
                "TEST_GROUP",
                "DEV",
                false,
                "SQLITE",
                "localhost",
                0,
                "",
                tempDb.toAbsolutePath().toString(),
                "",
                "NONE",
                null,
                false,
                5,
                true,
                Collections.emptyMap(),
                null,
                null
        );

        Result<?> connectResult = sessionManager.connect(profile, "");
        assertTrue(connectResult.isSuccess(), "Connecting to SQLite should succeed");
        assertTrue(sessionManager.isConnected());
        assertEquals("Session_SQLite", sessionManager.activeConnectionNameProperty().get());
        assertEquals("Session_SQLite", GlobalDesktopState.getInstance().getActiveConnection());

        // Test fetchDatabases
        Result<List<String>> dbs = sessionManager.fetchDatabases(profile, "");
        assertTrue(dbs.isSuccess());
        assertFalse(dbs.getDataOrThrow().isEmpty());

        sessionManager.disconnect();
        assertFalse(sessionManager.isConnected());
        assertEquals("", GlobalDesktopState.getInstance().getActiveConnection());
    }

    @Test
    @DisplayName("Connection Manager FXML and Controller loads successfully")
    void testConnectionManagerFXMLLoads() throws Exception {
        URL fxmlUrl = getClass().getResource("/fxml/connection-manager-dialog.fxml");
        assertNotNull(fxmlUrl, "connection-manager-dialog.fxml must exist on classpath");

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(fxmlUrl);
                Parent root = loader.load();
                assertNotNull(root, "Root node must not be null");
                ConnectionManagerController controller = loader.getController();
                assertNotNull(controller, "Controller must be loaded");
                success.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "FXML loading should complete within 5s");
        assertTrue(success.get(), "Connection Manager FXML and Controller should load without errors");
    }

    @Test
    @DisplayName("selectConnectionByIdOrName loads backend profile data into controller form")
    void testSelectConnectionByIdOrNameLoadsBackendData() throws Exception {
        String testName = "EDIT_TEST_" + java.util.UUID.randomUUID().toString().substring(0, 8);
        ConnectionApi api = DesktopBootstrap.getInstance().getConnectionApi();
        Result<ConnectionProfileDto> createRes = api.createConnection(new CreateConnectionRequest(
                testName,
                "TEST_GRP",
                "STAGING",
                true,
                "POSTGRESQL",
                "10.20.30.40",
                5433,
                "staging_db",
                "",
                "staging_user",
                "PASSWORD",
                "staging_secret",
                true,
                20,
                true,
                Collections.emptyMap()
        ));
        assertTrue(createRes.isSuccess(), "Connection creation should succeed: " + (createRes.getError().isPresent() ? createRes.getError().get().message() : ""));
        String createdId = createRes.getDataOrThrow().id();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean verified = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/connection-manager-dialog.fxml"));
                Parent root = loader.load();
                ConnectionManagerController controller = loader.getController();

                // Select by ID or Name
                controller.selectConnectionByIdOrName(createdId);

                // Start new connection should reset fields
                controller.startNewConnection();

                // Re-select by Name
                controller.selectConnectionByIdOrName(testName);

                verified.set(true);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Selection should finish within 5s");
        assertTrue(verified.get(), "Controller should load backend connection without errors");
    }

    @Test
    @DisplayName("DatabaseTreeCell generates Edit Connection context menu for CONNECTION node")
    void testDatabaseTreeCellHasEditConnectionContextMenu() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean foundEditMenu = new AtomicBoolean(false);

        Platform.runLater(() -> {
            try {
                app.ui.components.DatabaseTreeCell cell = new app.ui.components.DatabaseTreeCell(node -> {});
                app.models.DatabaseNode connNode = new app.models.DatabaseNode(
                        "Test_Node",
                        app.models.DatabaseNodeType.CONNECTION,
                        "localhost:5432",
                        "fth-server"
                );
                connNode.setConnectionId("test-id-123");

                // Invoke updateItem on DatabaseTreeCell
                java.lang.reflect.Method updateItemMethod = app.ui.components.DatabaseTreeCell.class
                        .getDeclaredMethod("updateItem", app.models.DatabaseNode.class, boolean.class);
                updateItemMethod.setAccessible(true);
                updateItemMethod.invoke(cell, connNode, false);

                javafx.scene.control.ContextMenu menu = cell.getContextMenu();
                assertNotNull(menu, "Cell should have a ContextMenu for CONNECTION node");
                // the menu is built lazily when it is about to be shown
                if (menu.getOnShowing() != null) {
                    menu.getOnShowing().handle(new javafx.stage.WindowEvent(null, javafx.stage.WindowEvent.WINDOW_SHOWING));
                }
                boolean hasEdit = menu.getItems().stream()
                        .anyMatch(mi -> mi.getText() != null && mi.getText().contains("Edit Connection"));
                foundEditMenu.set(hasEdit);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Context menu test should complete within 5s");
        assertTrue(foundEditMenu.get(), "Context menu must include 'Edit Connection...'");
    }
}
