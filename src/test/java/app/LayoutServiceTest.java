package app;

import app.services.LayoutService;
import javafx.application.Platform;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LayoutServiceTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Already started
        }
        try {
            Platform.setImplicitExit(false);
        } catch (Exception ignored) {
        }
    }

    @Test
    void testLayoutDefaults() {
        LayoutService layout = LayoutService.getInstance();
        assertNotNull(layout);

        layout.resetToDefault();

        assertTrue(layout.isSidebarVisible());
        assertFalse(layout.isSidebarCollapsed());
        assertTrue(layout.isResultsVisible());
        assertFalse(layout.isResultsMaximized());
        assertFalse(layout.isResultsFloating());
        assertEquals(LayoutService.DEFAULT_SIDEBAR_DIVIDER, layout.getSidebarDividerPosition(), 0.001);
        assertEquals(LayoutService.DEFAULT_RESULTS_DIVIDER, layout.getResultsDividerPosition(), 0.001);
    }

    @Test
    void testToggleSidebarCollapse() {
        LayoutService layout = LayoutService.getInstance();
        layout.resetToDefault();

        assertFalse(layout.isSidebarCollapsed());
        layout.toggleSidebarCollapse();
        assertTrue(layout.isSidebarCollapsed());
        layout.toggleSidebarCollapse();
        assertFalse(layout.isSidebarCollapsed());
    }

    @Test
    void testToggleSidebarAndResults() {
        LayoutService layout = LayoutService.getInstance();

        layout.setSidebarVisible(true);
        layout.toggleSidebar();
        assertFalse(layout.isSidebarVisible());
        layout.toggleSidebar();
        assertTrue(layout.isSidebarVisible());

        layout.setResultsVisible(true);
        layout.toggleResults();
        assertFalse(layout.isResultsVisible());
        layout.toggleResults();
        assertTrue(layout.isResultsVisible());
    }

    @Test
    void testToggleMaximizeResults() {
        LayoutService layout = LayoutService.getInstance();

        layout.resetToDefault();
        assertFalse(layout.isResultsMaximized());

        layout.toggleMaximizeResults();
        assertTrue(layout.isResultsMaximized());

        layout.toggleMaximizeResults();
        assertFalse(layout.isResultsMaximized());
    }

    @Test
    void testPresets() {
        LayoutService layout = LayoutService.getInstance();

        // 1. Editor Focused (Distraction-Free)
        layout.applyPreset(LayoutService.LayoutPreset.EDITOR_FOCUSED);
        assertEquals(LayoutService.LayoutPreset.EDITOR_FOCUSED, layout.getCurrentPreset());
        assertFalse(layout.isSidebarVisible());
        assertFalse(layout.isResultsVisible());

        // 2. Explorer Focused
        layout.applyPreset(LayoutService.LayoutPreset.DB_EXPLORER_FOCUSED);
        assertEquals(LayoutService.LayoutPreset.DB_EXPLORER_FOCUSED, layout.getCurrentPreset());
        assertTrue(layout.isSidebarVisible());
        assertTrue(layout.isResultsVisible());
        assertEquals(LayoutService.EXPLORER_FOCUSED_DIVIDER, layout.getSidebarDividerPosition(), 0.001);

        // 3. Results Maximized
        layout.applyPreset(LayoutService.LayoutPreset.RESULTS_MAXIMIZED);
        assertEquals(LayoutService.LayoutPreset.RESULTS_MAXIMIZED, layout.getCurrentPreset());
        assertTrue(layout.isSidebarVisible());
        assertTrue(layout.isResultsVisible());
        assertTrue(layout.isResultsMaximized());
        assertEquals(LayoutService.MAXIMIZED_RESULTS_DIVIDER, layout.getResultsDividerPosition(), 0.001);

        // 4. Reset to Default
        layout.resetToDefault();
        assertEquals(LayoutService.LayoutPreset.DEFAULT, layout.getCurrentPreset());
        assertTrue(layout.isSidebarVisible());
        assertTrue(layout.isResultsVisible());
        assertFalse(layout.isResultsMaximized());
    }

    @Test
    void testDividerPositionClamping() {
        LayoutService layout = LayoutService.getInstance();

        layout.setSidebarDividerPosition(0.35);
        assertEquals(0.35, layout.getSidebarDividerPosition(), 0.001);

        // Clamped: negative or >= 1.0 ignored
        layout.setSidebarDividerPosition(-0.1);
        assertEquals(0.35, layout.getSidebarDividerPosition(), 0.001);

        layout.setSidebarDividerPosition(1.5);
        assertEquals(0.35, layout.getSidebarDividerPosition(), 0.001);
    }

    @Test
    void testFloatingWindowLifecycle() throws InterruptedException {
        LayoutService layout = LayoutService.getInstance();
        layout.resetToDefault();
        assertFalse(layout.isResultsFloating());

        Label dummyResults = new Label("Dummy Results Grid");
        final boolean[] redocked = {false};
        CountDownLatch openLatch = new CountDownLatch(1);
        CountDownLatch closeLatch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                layout.openFloatingResultsWindow(dummyResults, () -> {
                    redocked[0] = true;
                    closeLatch.countDown();
                });
                openLatch.countDown();
            } catch (Exception e) {
                openLatch.countDown();
            }
        });

        assertTrue(openLatch.await(5, TimeUnit.SECONDS), "Floating window should open within 5s");
        assertTrue(layout.isResultsFloating());

        // Now re-dock on FX thread
        Platform.runLater(layout::reDockResults);

        assertTrue(closeLatch.await(5, TimeUnit.SECONDS), "Re-dock callback should be invoked within 5s");
        assertFalse(layout.isResultsFloating());
        assertTrue(redocked[0], "Redock callback should have been invoked");
    }

    @Test
    void testActiveTabsStateManagement() {
        LayoutService layout = LayoutService.getInstance();
        layout.setActiveEditorTabTitle("query_analytics.sql");
        assertEquals("query_analytics.sql", layout.getActiveEditorTabTitle());

        layout.setActiveResultTabIndex(2);
        assertEquals(2, layout.getActiveResultTabIndex());

        // Test persistence cycle
        layout.savePreferences();
        layout.loadPreferences();

        assertEquals("query_analytics.sql", layout.getActiveEditorTabTitle());
        assertEquals(2, layout.getActiveResultTabIndex());
    }
}
