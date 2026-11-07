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
import java.util.regex.Pattern;

/**
 * Diagnostics for database operations with strict credential sanitization and privacy guarantees.
 */
public final class DatabaseDiagnostics {

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseDiagnostics.class);
    private static final DatabaseDiagnostics INSTANCE = new DatabaseDiagnostics();
    private static final int MAX_DB_EVENTS = 100;

    // Regex to mask credentials in connection strings
    private static final Pattern PWD_PATTERN = Pattern.compile("(?i)(password|pwd|secret)=([^;&]+)");
    private static final Pattern USER_PATTERN = Pattern.compile("(?i)(user|username|uid)=([^;&]+)");
    private static final Pattern URI_AUTH_PATTERN = Pattern.compile("://([^:@]+):([^@]+)@");

    public record ConnectionMetric(
            String dbType,
            String sanitizedEndpoint,
            long durationMs,
            boolean success,
            String error,
            Instant timestamp
    ) {}

    public record QueryMetric(
            String sanitizedSql,
            long durationMs,
            int rowsAffected,
            boolean success,
            String error,
            Instant timestamp
    ) {}

    private final ConcurrentLinkedDeque<ConnectionMetric> connectionMetrics = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<QueryMetric> queryMetrics = new ConcurrentLinkedDeque<>();
    private final AtomicInteger connCount = new AtomicInteger(0);
    private final AtomicInteger queryCount = new AtomicInteger(0);

    private DatabaseDiagnostics() {}

    public static DatabaseDiagnostics getInstance() {
        return INSTANCE;
    }

    /**
     * Sanitizes a JDBC connection URL by scrubbing passwords, tokens, and credentials.
     */
    public static String sanitizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "jdbc:unknown";
        }
        String sanitized = URI_AUTH_PATTERN.matcher(rawUrl).replaceAll("://***:***@");
        sanitized = PWD_PATTERN.matcher(sanitized).replaceAll("$1=***");
        sanitized = USER_PATTERN.matcher(sanitized).replaceAll("$1=***");
        return sanitized;
    }

    /**
     * Sanitizes and bounds SQL statements by replacing literal strings and truncating length.
     */
    public static String sanitizeSql(String sql, int maxLength) {
        if (sql == null || sql.isBlank()) {
            return "<empty query>";
        }
        // Normalize whitespaces
        String cleaned = sql.replaceAll("\\s+", " ").trim();
        // Mask quoted literals
        cleaned = cleaned.replaceAll("'(?:[^'\\\\]|\\\\.)*'", "'?'");
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength) + "... [truncated]";
        }
        return cleaned;
    }

    public void recordConnection(String dbType, String rawUrl, long durationMs, boolean success, String error) {
        String sanitized = sanitizeUrl(rawUrl);
        ConnectionMetric metric = new ConnectionMetric(dbType, sanitized, durationMs, success, error, Instant.now());

        connectionMetrics.addLast(metric);
        if (connCount.incrementAndGet() > MAX_DB_EVENTS) {
            if (connectionMetrics.pollFirst() != null) {
                connCount.decrementAndGet();
            }
        }

        if (!success) {
            DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.warn("DATABASE", "ConnectionFailed",
                    String.format("Connection to %s (%s) failed after %d ms: %s", dbType, sanitized, durationMs, error)));
        }
    }

    public void recordQuery(String rawSql, long durationMs, int rowsAffected, boolean success, String error) {
        String sanitized = sanitizeSql(rawSql, 120);
        QueryMetric metric = new QueryMetric(sanitized, durationMs, rowsAffected, success, error, Instant.now());

        queryMetrics.addLast(metric);
        if (queryCount.incrementAndGet() > MAX_DB_EVENTS) {
            if (queryMetrics.pollFirst() != null) {
                queryCount.decrementAndGet();
            }
        }

        // Alert on slow queries (> 2000 ms)
        if (durationMs >= 2000L) {
            DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.warn("DATABASE", "SlowQuery",
                    String.format("Slow query detected (%d ms): %s", durationMs, sanitized)));
        }
    }

    public List<ConnectionMetric> getConnectionMetrics() {
        return Collections.unmodifiableList(new ArrayList<>(connectionMetrics));
    }

    public List<QueryMetric> getQueryMetrics() {
        return Collections.unmodifiableList(new ArrayList<>(queryMetrics));
    }

    public void clear() {
        connectionMetrics.clear();
        queryMetrics.clear();
        connCount.set(0);
        queryCount.set(0);
    }
}

