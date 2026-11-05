package app.framework.lifecycle;

import app.framework.async.AsyncTaskManager;
import app.framework.bootstrap.DesktopBootstrap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Desktop Application Lifecycle Coordinator (Phase 4).
 * Manages clean initialization, registered shutdown hooks, and connection cleanup.
 */
public class DesktopLifecycleManager {

    private static final Logger log = LoggerFactory.getLogger(DesktopLifecycleManager.class);
    private static DesktopLifecycleManager instance;

    private final List<Runnable> shutdownHooks = new ArrayList<>();
    private boolean shuttingDown = false;

    private DesktopLifecycleManager() {
        // Register default shutdown tasks
        addShutdownHook(() -> {
            log.info("Executing Desktop Framework shutdown tasks...");
            AsyncTaskManager.getInstance().shutdown();
        });
    }

    public static synchronized DesktopLifecycleManager getInstance() {
        if (instance == null) {
            instance = new DesktopLifecycleManager();
        }
        return instance;
    }

    public synchronized void addShutdownHook(Runnable hook) {
        if (hook != null) {
            shutdownHooks.add(hook);
        }
    }

    public synchronized void initiateShutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        log.info("Initiating graceful desktop shutdown sequence...");

        for (Runnable hook : shutdownHooks) {
            try {
                hook.run();
            } catch (Throwable t) {
                log.error("Error during shutdown hook execution: {}", t.getMessage(), t);
            }
        }
        log.info("Shutdown sequence finished.");
    }
}

