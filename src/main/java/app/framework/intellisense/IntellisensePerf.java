package app.framework.intellisense;

import javafx.animation.AnimationTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Measuring only (Phase 0, docs/feature_09): a timeline of connecting and typing, written to target/perf when the
 * environment variable {@code SQLIDE_PERF_IS=1} is set (run-ui-perf.cmd). Off otherwise: every call returns at once.
 * Writes durations and counts, never SQL text or credentials.
 */
public final class IntellisensePerf {

    public static final boolean ENABLED = "1".equals(System.getenv("SQLIDE_PERF_IS"));

    private static final Logger log = LoggerFactory.getLogger(IntellisensePerf.class);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final long STALL_MS = 100;
    private static final long SAMPLE_AFTER_MS = 300;
    private static final int STACK_FRAMES = 14;
    private static volatile long lastFrame;
    private static Path file;
    private static boolean watching;

    private IntellisensePerf() {
    }

    public static void log(String line) {
        if (!ENABLED) return;
        String text = CLOCK.format(LocalTime.now()) + " [" + Thread.currentThread().getName() + "] " + line + System.lineSeparator();
        synchronized (IntellisensePerf.class) {
            try {
                if (file == null) {
                    Files.createDirectories(Path.of("target", "perf"));
                    file = Path.of("target", "perf",
                            "app-timeline-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + ".log");
                }
                Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                log.warn("Perf log not written: {}", e.getMessage());
            }
        }
    }

    /** Call on the FX thread: reports every time the thread could not draw a frame for a while. */
    public static void watchFxThread() {
        if (!ENABLED || watching) return;
        watching = true;
        lastFrame = System.currentTimeMillis();
        Thread fx = Thread.currentThread();
        new AnimationTimer() {
            private long last;

            @Override
            public void handle(long now) {
                lastFrame = System.currentTimeMillis();
                if (last != 0) {
                    long ms = (now - last) / 1_000_000;
                    if (ms >= STALL_MS) IntellisensePerf.log("fx-stall " + ms + " ms");
                }
                last = now;
            }
        }.start();
        Thread sampler = new Thread(() -> sampleStalls(fx), "intellisense-perf-sampler");
        sampler.setDaemon(true);
        sampler.start();
    }

    /** What the FX thread is doing while it draws nothing: at most two samples per stall, method names only. */
    private static void sampleStalls(Thread fx) {
        long sampledFrame = -1;
        int samples = 0;
        while (true) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                return;
            }
            long frame = lastFrame;
            long waited = System.currentTimeMillis() - frame;
            if (frame != sampledFrame) {
                sampledFrame = frame;
                samples = 0;
            }
            if (waited >= SAMPLE_AFTER_MS * (samples + 1) && samples < 2) {
                samples++;
                StringBuilder line = new StringBuilder("fx-stack after ").append(waited).append(" ms:");
                StackTraceElement[] stack = fx.getStackTrace();
                for (int i = 0; i < Math.min(STACK_FRAMES, stack.length); i++) {
                    line.append(" < ").append(stack[i].getClassName()).append('.').append(stack[i].getMethodName());
                }
                log(line.toString());
            }
        }
    }
}
