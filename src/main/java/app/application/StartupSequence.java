package app.application;

import app.ui.splash.SplashWindow;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * Start-up order: splash first, then the back end on a background thread, the main window (still hidden) on the FX
 * thread, and the switch once the IDE is ready. The splash goes away only when the IDE is ready and has been on
 * screen for at least {@link #MIN_SPLASH_MILLIS}.
 * <p>
 * The waits use their own timer, not JavaFX animations: those depend on FX pulses, which can stall while the FX
 * thread is busy or no FX window is on screen, and the main window is hidden during all of this.
 */
final class StartupSequence {

    private static final Logger log = LoggerFactory.getLogger(StartupSequence.class);

    static final long MIN_SPLASH_MILLIS = 1750;
    /** A safety net: an editor that never reports ready must not keep the user out of the IDE forever. */
    static final long READY_TIMEOUT_MILLIS = 30_000;
    /** The main window paints behind the splash before the splash fades out. */
    private static final long HANDOVER_MILLIS = 120;

    private static final String[] STEP_LABELS = {
            "Đang chuẩn bị thư mục ứng dụng",
            "Đang đọc cấu hình",
            "Đang mở kho thông tin xác thực",
            "Đang nạp driver cơ sở dữ liệu",
            "Đang dựng giao diện và workspace",
            "Đang khởi tạo trình soạn thảo"
    };
    private static final int STEP_UI = 5;
    private static final int STEP_EDITOR = 6;

    interface Backend {
        void load(IntConsumer onStep) throws Exception;
    }

    interface MainWindow {
        /** {@code callback} runs on the FX thread when the IDE can be used, at once if it already can. */
        void whenReady(Runnable callback);

        void show(boolean minimized);
    }

    interface MainWindowFactory {
        MainWindow build() throws Exception;
    }

    private final SplashWindow splash;
    private final Backend backend;
    private final MainWindowFactory factory;
    private final Runnable openLogs;
    private final ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sqlide-startup-timer");
        t.setDaemon(true);
        return t;
    });

    private long shownAtNanos;
    private int currentStep;
    private boolean revealStarted;
    private ScheduledFuture<?> readyTimeout;

    StartupSequence(SplashWindow splash, Backend backend, MainWindowFactory factory, Runnable openLogs) {
        this.splash = splash;
        this.backend = backend;
        this.factory = factory;
        this.openLogs = openLogs;
    }

    void begin() {
        splash.show();
        shownAtNanos = System.nanoTime();
        loadBackend();
    }

    private void loadBackend() {
        showStep(1);
        Thread worker = new Thread(() -> {
            try {
                backend.load(step -> Platform.runLater(() -> showStep(step)));
                Platform.runLater(this::buildMainWindow);
            } catch (Throwable t) {
                Platform.runLater(() -> fail(t));
            }
        }, "sqlide-startup");
        worker.setDaemon(true);
        worker.start();
    }

    private void buildMainWindow() {
        showStep(STEP_UI);
        MainWindow window;
        long start = System.nanoTime();
        try {
            window = factory.build();
        } catch (Throwable t) {
            fail(t);
            return;
        }
        log.info("[startup] main window built in {} ms", (System.nanoTime() - start) / 1_000_000);
        showStep(STEP_EDITOR);

        Runnable ready = () -> onIdeReady(window);
        readyTimeout = after(READY_TIMEOUT_MILLIS, () -> {
            log.warn("[startup] IDE did not report ready within {} ms: showing it anyway", READY_TIMEOUT_MILLIS);
            ready.run();
        });
        window.whenReady(ready);
    }

    private void onIdeReady(MainWindow window) {
        if (revealStarted) return;
        revealStarted = true;
        readyTimeout.cancel(false);
        log.info("[startup] IDE ready {} ms after the splash appeared", elapsedMillis());
        splash.showReady(STEP_LABELS.length);
        after(Math.max(0, MIN_SPLASH_MILLIS - elapsedMillis()), () -> reveal(window));
    }

    private void reveal(MainWindow window) {
        try {
            boolean minimized = splash.isMinimized();
            window.show(minimized);
            log.info("[startup] main window shown {} ms after the splash appeared (splash minimized: {})",
                    elapsedMillis(), minimized);
            timer.schedule(() -> {
                splash.closeWithFade();
                timer.shutdown();
            }, HANDOVER_MILLIS, TimeUnit.MILLISECONDS);
        } catch (Throwable t) {
            fail(t);
        }
    }

    private void fail(Throwable t) {
        log.error("[startup] failed at step {}/{}", currentStep, STEP_LABELS.length, t);
        if (readyTimeout != null) readyTimeout.cancel(false);
        revealStarted = false;
        splash.showError("Lỗi ở bước " + currentStep + "/" + STEP_LABELS.length + ": " + rootMessage(t),
                () -> Platform.runLater(this::loadBackend), openLogs, Platform::exit);
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        return message != null && !message.isBlank() ? message : root.getClass().getSimpleName();
    }

    private void showStep(int step) {
        currentStep = step;
        splash.showStep(step, STEP_LABELS.length, STEP_LABELS[step - 1]);
    }

    private long elapsedMillis() {
        return (System.nanoTime() - shownAtNanos) / 1_000_000;
    }

    /** Runs {@code action} on the FX thread after {@code millis}. */
    private ScheduledFuture<?> after(long millis, Runnable action) {
        return timer.schedule(() -> Platform.runLater(action), millis, TimeUnit.MILLISECONDS);
    }
}
