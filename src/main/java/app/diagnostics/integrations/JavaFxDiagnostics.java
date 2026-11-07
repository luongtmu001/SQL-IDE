package app.diagnostics.integrations;

import app.diagnostics.core.DiagnosticsEvent;
import app.diagnostics.core.DiagnosticsManager;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diagnostics for JavaFX Application Thread responsiveness and long-running UI operations.
 */
public final class JavaFxDiagnostics {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaFxDiagnostics.class);
    private static final JavaFxDiagnostics INSTANCE = new JavaFxDiagnostics();

    public static final long UI_FREEZE_THRESHOLD_MS = 50L; // 50ms causes noticeable frame drop (>3 frames at 60fps)
    private static final int MAX_UI_EVENTS = 100;

    public record UiLagRecord(String operationName, long durationMs, Instant timestamp, boolean isFxThread) {}

    private final ConcurrentLinkedDeque<UiLagRecord> lagRecords = new ConcurrentLinkedDeque<>();
    private final AtomicInteger lagCount = new AtomicInteger(0);

    private JavaFxDiagnostics() {}

    public static JavaFxDiagnostics getInstance() {
        return INSTANCE;
    }

    public boolean isFxApplicationThread() {
        try {
            return Platform.isFxApplicationThread();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Records a potentially long-running UI operation.
     */
    public void recordUiOperation(String operationName, long durationMs) {
        if (operationName == null) return;

        boolean isFxThread = isFxApplicationThread();
        if (durationMs >= UI_FREEZE_THRESHOLD_MS && isFxThread) {
            UiLagRecord record = new UiLagRecord(operationName, durationMs, Instant.now(), true);
            lagRecords.addLast(record);

            if (lagCount.incrementAndGet() > MAX_UI_EVENTS) {
                if (lagRecords.pollFirst() != null) {
                    lagCount.decrementAndGet();
                }
            }

            DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.warn("JAVAFX", "UiLagDetected",
                    String.format("UI thread blocked by '%s' for %d ms", operationName, durationMs)));
        }
    }

    public List<UiLagRecord> getLagRecords() {
        return Collections.unmodifiableList(new ArrayList<>(lagRecords));
    }

    public void clear() {
        lagRecords.clear();
        lagCount.set(0);
    }
}

