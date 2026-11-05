package app.framework;

import app.framework.bootstrap.DesktopBootstrap;
import app.framework.error.DesktopErrorHandler;
import app.framework.lifecycle.DesktopLifecycleManager;
import app.framework.navigation.NavigationManager;
import app.framework.state.GlobalDesktopState;
import com.lltool.infrastructure.database.api.DatabaseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DesktopFrameworkTest {

    @Test
    @DisplayName("DesktopBootstrap correctly wires CorePlatform, Database Providers, and Repositories")
    void testDesktopBootstrapWiring() {
        DesktopBootstrap bootstrap = DesktopBootstrap.getInstance();

        assertNotNull(bootstrap.getDirectoryManager());
        assertNotNull(bootstrap.getConfigurationService());
        assertNotNull(bootstrap.getCredentialVault());
        assertNotNull(bootstrap.getCorePlatform());
        assertNotNull(bootstrap.getProjectRepository());

        // Verify all 3 database providers are registered
        assertTrue(bootstrap.getProviderRegistry().supports(DatabaseType.POSTGRESQL));
        assertTrue(bootstrap.getProviderRegistry().supports(DatabaseType.SQLITE));
        assertTrue(bootstrap.getProviderRegistry().supports(DatabaseType.SQLSERVER));
    }

    @Test
    @DisplayName("GlobalDesktopState observable properties update and notify listeners")
    void testGlobalDesktopState() {
        GlobalDesktopState state = GlobalDesktopState.getInstance();
        AtomicBoolean projectChanged = new AtomicBoolean(false);

        state.activeProjectProperty().addListener((obs, oldV, newV) -> projectChanged.set(true));
        state.setActiveProject("Cloud DW");

        assertEquals("Cloud DW", state.getActiveProject());
        assertTrue(projectChanged.get());

        state.setBusy(true);
        assertTrue(state.isBusy());
    }

    @Test
    @DisplayName("NavigationManager switches perspectives correctly")
    void testNavigationManager() {
        NavigationManager nav = NavigationManager.getInstance();
        nav.navigateTo(NavigationManager.Perspective.QUERY_CONSOLE);
        assertEquals(NavigationManager.Perspective.QUERY_CONSOLE, nav.getCurrentPerspective());

        nav.navigateTo(NavigationManager.Perspective.SETTINGS);
        assertEquals(NavigationManager.Perspective.SETTINGS, nav.getCurrentPerspective());
    }

    @Test
    @DisplayName("DesktopLifecycleManager executes registered shutdown hooks")
    void testLifecycleShutdownHooks() {
        DesktopLifecycleManager lifecycle = DesktopLifecycleManager.getInstance();
        AtomicBoolean hookExecuted = new AtomicBoolean(false);

        lifecycle.addShutdownHook(() -> hookExecuted.set(true));
        lifecycle.initiateShutdown();

        assertTrue(hookExecuted.get());
    }
}

