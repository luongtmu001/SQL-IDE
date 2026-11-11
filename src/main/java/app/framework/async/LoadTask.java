package app.framework.async;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Statement;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Coordinated background task wrapper for JavaFX UI tabs (BE-0.1).
 * Features:
 * - Generation tracking (cancels and discards results from older executions)
 * - Safe cancellation of active statements via Statement.cancel()
 * - JavaFX Application Thread dispatch for onSuccess, onError, and onCancelled callbacks
 */
public class LoadTask<T> {

    private static final Logger log = LoggerFactory.getLogger(LoadTask.class);

    private final AtomicLong currentGeneration = new AtomicLong(0);
    private volatile ActiveExecution<T> currentExecution;

    @FunctionalInterface
    public interface Worker<R> {
        R run(Context ctx) throws Exception;
    }

    public interface Context {
        void registerStatement(Statement stmt);
        void unregisterStatement(Statement stmt);
        boolean isCancelled();
        void checkCancelled() throws InterruptedException;
        long getGeneration();
    }

    public static class ContextImpl implements Context {
        private final long generation;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final List<Statement> activeStatements = new CopyOnWriteArrayList<>();

        public ContextImpl(long generation) {
            this.generation = generation;
        }

        @Override
        public void registerStatement(Statement stmt) {
            if (stmt == null) return;
            if (cancelled.get()) {
                try {
                    stmt.cancel();
                } catch (Exception ignored) {
                }
                return;
            }
            activeStatements.add(stmt);
        }

        @Override
        public void unregisterStatement(Statement stmt) {
            if (stmt != null) {
                activeStatements.remove(stmt);
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled.get() || Thread.currentThread().isInterrupted();
        }

        @Override
        public void checkCancelled() throws InterruptedException {
            if (isCancelled()) {
                throw new InterruptedException("Task cancelled at generation " + generation);
            }
        }

        @Override
        public long getGeneration() {
            return generation;
        }

        void cancel() {
            cancelled.set(true);
            for (Statement stmt : activeStatements) {
                try {
                    stmt.cancel();
                } catch (Exception e) {
                    log.debug("Error cancelling statement: {}", e.getMessage());
                }
            }
            activeStatements.clear();
        }
    }

    private static class ActiveExecution<R> {
        final long generation;
        final ContextImpl context;
        final Consumer<R> onSuccess;
        final Consumer<Throwable> onError;
        final Runnable onCancelled;
        final AtomicBoolean completed = new AtomicBoolean(false);

        ActiveExecution(long generation,
                        ContextImpl context,
                        Consumer<R> onSuccess,
                        Consumer<Throwable> onError,
                        Runnable onCancelled) {
            this.generation = generation;
            this.context = context;
            this.onSuccess = onSuccess;
            this.onError = onError;
            this.onCancelled = onCancelled;
        }
    }

    public void execute(Worker<T> worker, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        execute(worker, onSuccess, onError, null);
    }

    public synchronized void execute(Worker<T> worker,
                                     Consumer<T> onSuccess,
                                     Consumer<Throwable> onError,
                                     Runnable onCancelled) {
        Objects.requireNonNull(worker, "worker cannot be null");
        cancel();

        long gen = currentGeneration.incrementAndGet();
        ContextImpl ctx = new ContextImpl(gen);
        ActiveExecution<T> execution = new ActiveExecution<>(gen, ctx, onSuccess, onError, onCancelled);
        this.currentExecution = execution;

        AsyncTaskManager.getInstance().runInBackground(() -> {
            try {
                if (ctx.isCancelled() || gen != currentGeneration.get()) {
                    notifyCancelled(execution);
                    return;
                }
                T result = worker.run(ctx);
                if (ctx.isCancelled() || gen != currentGeneration.get()) {
                    notifyCancelled(execution);
                } else {
                    notifySuccess(execution, result);
                }
            } catch (Throwable t) {
                if (ctx.isCancelled() || gen != currentGeneration.get()) {
                    notifyCancelled(execution);
                } else {
                    notifyError(execution, t);
                }
            }
        });
    }

    public synchronized void cancel() {
        ActiveExecution<T> exec = currentExecution;
        if (exec != null && !exec.completed.get()) {
            exec.context.cancel();
            notifyCancelled(exec);
        }
    }

    public boolean isRunning() {
        ActiveExecution<T> exec = currentExecution;
        return exec != null && !exec.completed.get() && !exec.context.isCancelled();
    }

    public long getCurrentGeneration() {
        return currentGeneration.get();
    }

    private void notifySuccess(ActiveExecution<T> exec, T result) {
        if (exec.completed.compareAndSet(false, true)) {
            runOnFx(() -> {
                if (exec.generation == currentGeneration.get() && exec.onSuccess != null) {
                    exec.onSuccess.accept(result);
                }
            });
        }
    }

    private void notifyError(ActiveExecution<T> exec, Throwable err) {
        if (exec.completed.compareAndSet(false, true)) {
            runOnFx(() -> {
                if (exec.generation == currentGeneration.get() && exec.onError != null) {
                    exec.onError.accept(err);
                }
            });
        }
    }

    private void notifyCancelled(ActiveExecution<T> exec) {
        if (exec.completed.compareAndSet(false, true)) {
            runOnFx(() -> {
                if (exec.onCancelled != null) {
                    exec.onCancelled.run();
                }
            });
        }
    }

    private void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            try {
                Platform.runLater(action);
            } catch (IllegalStateException e) {
                action.run();
            }
        }
    }
}
