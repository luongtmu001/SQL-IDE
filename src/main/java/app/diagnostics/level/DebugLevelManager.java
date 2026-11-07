package app.diagnostics.level;

import app.diagnostics.core.DiagnosticsEvent;
import app.diagnostics.core.DiagnosticsLevel;
import app.diagnostics.core.DiagnosticsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Manages runtime debug mode activation and capability gating across Levels 1, 2, and 3.
 */
public final class DebugLevelManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DebugLevelManager.class);
    private static final DebugLevelManager INSTANCE = new DebugLevelManager();

    private final AtomicBoolean debugModeActive = new AtomicBoolean(false);
    private final AtomicReference<DiagnosticsLevel> currentLevel = new AtomicReference<>(DiagnosticsLevel.OFF);
    private final List<BiConsumer<DiagnosticsLevel, DiagnosticsLevel>> levelChangeListeners = new CopyOnWriteArrayList<>();

    private DebugLevelManager() {
        // Private singleton constructor
    }

    public static DebugLevelManager getInstance() {
        return INSTANCE;
    }

    /**
     * Activates debug mode at the specified level (defaults to BASIC if null or OFF).
     */
    public synchronized void enableDebugMode(DiagnosticsLevel level) {
        DiagnosticsLevel target = (level != null && level != DiagnosticsLevel.OFF) ? level : DiagnosticsLevel.BASIC;
        DiagnosticsLevel previous = currentLevel.get();

        debugModeActive.set(true);
        currentLevel.set(target);

        DiagnosticsManager.getInstance().setDebugMode(true, target);

        LOGGER.info("Debug mode enabled. Level changed from {} to {}", previous, target);
        notifyLevelChange(previous, target);
    }

    /**
     * Disables on-demand debug mode and resets to OFF (background baseline only).
     */
    public synchronized void disableDebugMode() {
        if (!debugModeActive.get()) {
            return;
        }

        DiagnosticsLevel previous = currentLevel.get();
        debugModeActive.set(false);
        currentLevel.set(DiagnosticsLevel.OFF);

        DiagnosticsManager.getInstance().setDebugMode(false, DiagnosticsLevel.OFF);

        LOGGER.info("Debug mode disabled. Resetting level from {} to OFF", previous);
        notifyLevelChange(previous, DiagnosticsLevel.OFF);
    }

    /**
     * Updates debug level at runtime without restarting the application.
     */
    public synchronized void setLevel(DiagnosticsLevel newLevel) {
        if (newLevel == null || newLevel == DiagnosticsLevel.OFF) {
            disableDebugMode();
            return;
        }

        DiagnosticsLevel previous = currentLevel.get();
        if (previous == newLevel && debugModeActive.get()) {
            return;
        }

        debugModeActive.set(true);
        currentLevel.set(newLevel);
        DiagnosticsManager.getInstance().setDebugMode(true, newLevel);

        LOGGER.info("Debug level updated: {} -> {}", previous, newLevel);
        notifyLevelChange(previous, newLevel);
    }

    public boolean isDebugModeActive() {
        return debugModeActive.get();
    }

    public DiagnosticsLevel getCurrentLevel() {
        return currentLevel.get();
    }

    /**
     * Checks if a specific diagnostic feature is enabled under the current operating level.
     */
    public boolean isFeatureActive(DiagnosticsFeature feature) {
        if (feature == null) {
            return false;
        }
        if (!debugModeActive.get()) {
            // Background monitoring features remain active in background mode
            return feature.getMinimumLevel() == DiagnosticsLevel.BASIC;
        }
        return feature.isSupportedAt(currentLevel.get());
    }

    public void addLevelChangeListener(BiConsumer<DiagnosticsLevel, DiagnosticsLevel> listener) {
        if (listener != null) {
            levelChangeListeners.add(listener);
        }
    }

    public void removeLevelChangeListener(BiConsumer<DiagnosticsLevel, DiagnosticsLevel> listener) {
        if (listener != null) {
            levelChangeListeners.remove(listener);
        }
    }

    private void notifyLevelChange(DiagnosticsLevel oldLevel, DiagnosticsLevel newLevel) {
        DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.info("DEBUG_LEVEL", "LevelTransition",
                String.format("Level transitioned from %s to %s", oldLevel, newLevel)));

        for (var listener : levelChangeListeners) {
            try {
                listener.accept(oldLevel, newLevel);
            } catch (Throwable t) {
                LOGGER.warn("Level change listener error: {}", t.getMessage());
            }
        }
    }
}

