package app.framework.async;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Async Task Manager for JavaFX Desktop Framework (Phase 4).
 * Executes intensive operations on a managed background thread pool
 * and safely delivers callbacks/results to the JavaFX Application Thread.
 */
public class AsyncTaskManager {

    private static final Logger log = LoggerFactory.getLogger(AsyncTaskManager.class);
    private static AsyncTaskManager instance;

    private final ExecutorService executor;

    public AsyncTaskManager() {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "lltool-async-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public static synchronized AsyncTaskManager getInstance() {
        if (instance == null || instance.executor.isShutdown()) {
            instance = new AsyncTaskManager();
        }
        return instance;
    }

    /**
     * Executes a background task and dispatches the result to onSuccess (on JavaFX Application Thread).
     */
    public <T> CompletableFuture<T> executeAsync(
            Supplier<T> backgroundTask,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure) {

        Objects.requireNonNull(backgroundTask, "backgroundTask cannot be null");
        Executor currentExecutor = (executor != null && !executor.isShutdown()) ? executor : ForkJoinPool.commonPool();

        return CompletableFuture.supplyAsync(backgroundTask, currentExecutor)
                .thenApply(result -> {
                    if (onSuccess != null) {
                        Platform.runLater(() -> {
                            try {
                                onSuccess.accept(result);
                            } catch (Throwable t) {
                                log.error("Error executing async onSuccess callback: {}", t.getMessage(), t);
                            }
                        });
                    }
                    return result;
                })
                .exceptionally(ex -> {
                    Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                    log.error("Background task failed: {}", cause.getMessage(), cause);
                    if (onFailure != null) {
                        Platform.runLater(() -> {
                            try {
                                onFailure.accept(cause);
                            } catch (Throwable t) {
                                log.error("Error executing async onFailure callback: {}", t.getMessage(), t);
                            }
                        });
                    }
                    return null;
                });
    }

    public void runInBackground(Runnable runnable) {
        if (runnable != null) {
            executor.submit(runnable);
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

