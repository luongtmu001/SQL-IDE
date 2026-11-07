package app.diagnostics.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Central management singleton for the Application Diagnostics system.
 * <p>
 * Strictly non-invasive: all public methods are fail-safe and guaranteed
 * never to throw unhandled exceptions to the caller.
 */
public final class DiagnosticsManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiagnosticsManager.class);
    private static final DiagnosticsManager INSTANCE = new DiagnosticsManager();

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    private final AtomicReference<DiagnosticsConfig> config =
            new AtomicReference<>(DiagnosticsConfig.defaultConfig());

    private final AtomicReference<DiagnosticsSession> currentSession =
            new AtomicReference<>(null);

    private final List<Consumer<DiagnosticsEvent>> eventListeners =
            new CopyOnWriteArrayList<>();

    private DiagnosticsManager() {
        // Private singleton constructor
    }

    public static DiagnosticsManager getInstance() {
        return INSTANCE;
    }

    /**
     * Initializes the Diagnostics subsystem with the given configuration.
     * Safe to call multiple times (idempotent).
     */
    public synchronized void initialize(DiagnosticsConfig customConfig) {
        try {
            if (shutdown.get()) {
                LOGGER.warn("DiagnosticsManager was shut down, re-initializing new session.");
                shutdown.set(false);
            }

            DiagnosticsConfig cfg = customConfig != null ? customConfig : DiagnosticsConfig.defaultConfig();
            this.config.set(cfg);

            // Automatically begin initial session
            DiagnosticsLevel initialLevel = cfg.isDebugModeEnabled() ? cfg.getLevel() : DiagnosticsLevel.BASIC;
            startSession(initialLevel);

            initialized.set(true);
            publishEvent(DiagnosticsEvent.info("SYSTEM", "DiagnosticsInitialized",
                    "Diagnostics Core initialized successfully with level: " + initialLevel));

            LOGGER.info("Diagnostics system initialized successfully with config: {}", cfg);
        } catch (Throwable t) {
            // Absolute fail-safe guard: never crash the host application
            LOGGER.error("Failed to initialize Diagnostics system safely: {}", t.getMessage(), t);
        }
    }

    /**
     * Gracefully shuts down the Diagnostics system and releases resources.
     */
    public synchronized void shutdown() {
        try {
            if (!initialized.get() || shutdown.get()) {
                return;
            }

            publishEvent(DiagnosticsEvent.info("SYSTEM", "DiagnosticsShutdown", "Shutting down Diagnostics system"));

            endCurrentSession();
            eventListeners.clear();
            shutdown.set(true);
            initialized.set(false);

            LOGGER.info("Diagnostics system shut down cleanly.");
        } catch (Throwable t) {
            LOGGER.error("Error during Diagnostics shutdown: {}", t.getMessage(), t);
        }
    }

    public boolean isInitialized() {
        return initialized.get();
    }

    public boolean isShutdown() {
        return shutdown.get();
    }

    public DiagnosticsConfig getConfig() {
        return config.get();
    }

    /**
     * Updates runtime configuration safely.
     */
    public void updateConfig(DiagnosticsConfig newConfig) {
        if (newConfig == null) {
            return;
        }
        try {
            this.config.set(newConfig);
            DiagnosticsSession session = currentSession.get();
            if (session != null && !session.isClosed()) {
                session.setActiveLevel(newConfig.getLevel());
            }
            publishEvent(DiagnosticsEvent.info("SYSTEM", "ConfigUpdated",
                    "Diagnostics configuration updated: " + newConfig));
        } catch (Throwable t) {
            LOGGER.error("Failed to update diagnostics config: {}", t.getMessage(), t);
        }
    }

    /**
     * Starts a new diagnostics session, closing any currently active session.
     */
    public synchronized DiagnosticsSession startSession(DiagnosticsLevel level) {
        try {
            endCurrentSession();

            DiagnosticsLevel sessionLevel = level != null ? level : config.get().getLevel();
            DiagnosticsSession session = new DiagnosticsSession(config.get(), sessionLevel);
            currentSession.set(session);

            return session;
        } catch (Throwable t) {
            LOGGER.error("Failed to start diagnostics session: {}", t.getMessage(), t);
            return null;
        }
    }

    /**
     * Ends the current active session if one exists.
     */
    public synchronized void endCurrentSession() {
        try {
            DiagnosticsSession session = currentSession.getAndSet(null);
            if (session != null && !session.isClosed()) {
                session.recordEvent(DiagnosticsEvent.info("SYSTEM", "SessionClosed",
                        "Diagnostics session closed: " + session.getSessionId()));
                session.close();
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to close diagnostics session safely: {}", t.getMessage(), t);
        }
    }

    public DiagnosticsSession getCurrentSession() {
        return currentSession.get();
    }

    public DiagnosticsLevel getActiveLevel() {
        DiagnosticsSession session = currentSession.get();
        if (session != null && !session.isClosed()) {
            return session.getActiveLevel();
        }
        return config.get().getLevel();
    }

    /**
     * Toggles on-demand Debug Mode and sets debug level at runtime.
     */
    public synchronized void setDebugMode(boolean enabled, DiagnosticsLevel level) {
        try {
            DiagnosticsLevel targetLevel = (enabled && level != null) ? level : (enabled ? DiagnosticsLevel.BASIC : DiagnosticsLevel.OFF);
            DiagnosticsConfig updated = config.get().toBuilder()
                    .debugModeEnabled(enabled)
                    .level(targetLevel)
                    .build();

            this.config.set(updated);

            DiagnosticsSession session = currentSession.get();
            if (session != null && !session.isClosed()) {
                session.setActiveLevel(targetLevel);
            }

            publishEvent(DiagnosticsEvent.info("SYSTEM", "DebugModeChanged",
                    "Debug mode " + (enabled ? "enabled (Level: " + targetLevel + ")" : "disabled")));
        } catch (Throwable t) {
            LOGGER.error("Failed to set debug mode: {}", t.getMessage(), t);
        }
    }

    public boolean isDebugModeEnabled() {
        return config.get().isDebugModeEnabled();
    }

    /**
     * Publishes an event to the diagnostics system.
     * Guaranteed thread-safe, non-blocking, and fail-safe.
     */
    public void publishEvent(DiagnosticsEvent event) {
        if (event == null || shutdown.get()) {
            return;
        }

        try {
            // Record to active session
            DiagnosticsSession session = currentSession.get();
            if (session != null && !session.isClosed()) {
                session.recordEvent(event);
            }

            // Dispatch to registered observers safely
            for (Consumer<DiagnosticsEvent> listener : eventListeners) {
                try {
                    listener.accept(event);
                } catch (Throwable observerError) {
                    LOGGER.warn("Diagnostics event listener threw an error: {}", observerError.getMessage());
                }
            }
        } catch (Throwable t) {
            // Absolute silence / swallow on failure to guarantee non-invasive contract
            LOGGER.warn("Failed to publish diagnostics event safely: {}", t.getMessage());
        }
    }

    /**
     * Registers an event listener to observe published diagnostic events.
     */
    public void addEventListener(Consumer<DiagnosticsEvent> listener) {
        if (listener != null) {
            eventListeners.add(listener);
        }
    }

    /**
     * Removes a previously registered event listener.
     */
    public void removeEventListener(Consumer<DiagnosticsEvent> listener) {
        if (listener != null) {
            eventListeners.remove(listener);
        }
    }
}

