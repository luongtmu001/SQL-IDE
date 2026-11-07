package app.diagnostics.level;

import app.diagnostics.core.DiagnosticsLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DebugLevelManagerTest {

    private DebugLevelManager levelManager;

    @BeforeEach
    void setUp() {
        levelManager = DebugLevelManager.getInstance();
        levelManager.disableDebugMode();
    }

    @AfterEach
    void tearDown() {
        levelManager.disableDebugMode();
    }

    @Test
    @DisplayName("DebugLevelManager toggle and level transition")
    void testToggleAndLevels() {
        assertFalse(levelManager.isDebugModeActive());
        assertEquals(DiagnosticsLevel.OFF, levelManager.getCurrentLevel());

        AtomicReference<DiagnosticsLevel> transitionTarget = new AtomicReference<>();
        levelManager.addLevelChangeListener((oldL, newL) -> transitionTarget.set(newL));

        levelManager.enableDebugMode(DiagnosticsLevel.BALANCED);
        assertTrue(levelManager.isDebugModeActive());
        assertEquals(DiagnosticsLevel.BALANCED, levelManager.getCurrentLevel());
        assertEquals(DiagnosticsLevel.BALANCED, transitionTarget.get());

        // Test feature gating
        assertTrue(levelManager.isFeatureActive(DiagnosticsFeature.CPU_MONITORING));
        assertTrue(levelManager.isFeatureActive(DiagnosticsFeature.TASK_TRACKING));
        assertFalse(levelManager.isFeatureActive(DiagnosticsFeature.FLIGHT_RECORDER));

        // Switch to Advanced
        levelManager.setLevel(DiagnosticsLevel.ADVANCED);
        assertTrue(levelManager.isFeatureActive(DiagnosticsFeature.FLIGHT_RECORDER));
        assertTrue(levelManager.isFeatureActive(DiagnosticsFeature.DEADLOCK_DETECTION));

        // Disable
        levelManager.disableDebugMode();
        assertFalse(levelManager.isDebugModeActive());
        assertEquals(DiagnosticsLevel.OFF, levelManager.getCurrentLevel());
        assertFalse(levelManager.isFeatureActive(DiagnosticsFeature.TASK_TRACKING));
    }
}

