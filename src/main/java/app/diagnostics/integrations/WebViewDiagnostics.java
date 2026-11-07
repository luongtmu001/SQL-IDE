package app.diagnostics.integrations;

import app.diagnostics.core.DiagnosticsEvent;
import app.diagnostics.core.DiagnosticsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Diagnostics for Monaco Editor and JavaFX WebView.
 */
public final class WebViewDiagnostics {

    private static final Logger LOGGER = LoggerFactory.getLogger(WebViewDiagnostics.class);
    private static final WebViewDiagnostics INSTANCE = new WebViewDiagnostics();
    private static final int MAX_WEBVIEW_EVENTS = 100;

    public record JsErrorRecord(String message, String sourceId, int lineNumber, Instant timestamp) {}
    public record WebViewLoadRecord(String resource, long durationMs, boolean success, String error, Instant timestamp) {}

    private final ConcurrentLinkedDeque<JsErrorRecord> jsErrors = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<WebViewLoadRecord> loadRecords = new ConcurrentLinkedDeque<>();
    private final AtomicInteger jsErrorCount = new AtomicInteger(0);
    private final AtomicInteger loadRecordCount = new AtomicInteger(0);

    private WebViewDiagnostics() {}

    public static WebViewDiagnostics getInstance() {
        return INSTANCE;
    }

    public void recordWebViewInit(long durationMs, boolean success, String error) {
        DiagnosticsManager.getInstance().publishEvent(
                success ? DiagnosticsEvent.info("WEBVIEW", "WebViewInit", "WebView initialized in " + durationMs + " ms")
                        : DiagnosticsEvent.error("WEBVIEW", "WebViewInitFailed", "WebView init failed: " + error, null)
        );
    }

    public void recordPageLoad(String resource, long durationMs, boolean success, String error) {
        WebViewLoadRecord record = new WebViewLoadRecord(resource, durationMs, success, error, Instant.now());
        loadRecords.addLast(record);

        if (loadRecordCount.incrementAndGet() > MAX_WEBVIEW_EVENTS) {
            if (loadRecords.pollFirst() != null) {
                loadRecordCount.decrementAndGet();
            }
        }
    }

    public void recordJsError(String message, String sourceId, int lineNumber) {
        JsErrorRecord record = new JsErrorRecord(message, sourceId, lineNumber, Instant.now());
        jsErrors.addLast(record);

        if (jsErrorCount.incrementAndGet() > MAX_WEBVIEW_EVENTS) {
            if (jsErrors.pollFirst() != null) {
                jsErrorCount.decrementAndGet();
            }
        }

        DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.warn("WEBVIEW", "JavaScriptError",
                String.format("Monaco JS Error in %s (line %d): %s", sourceId, lineNumber, message)));
    }

    public List<JsErrorRecord> getJsErrors() {
        return Collections.unmodifiableList(new ArrayList<>(jsErrors));
    }

    public List<WebViewLoadRecord> getLoadRecords() {
        return Collections.unmodifiableList(new ArrayList<>(loadRecords));
    }

    public void clear() {
        jsErrors.clear();
        loadRecords.clear();
        jsErrorCount.set(0);
        loadRecordCount.set(0);
    }
}

