package app.diagnostics.errors;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Detailed error and exception telemetry record.
 */
public final class ErrorRecord {

    private final String errorId;
    private final Instant firstOccurrence;
    private volatile Instant lastOccurrence;
    private final String exceptionClass;
    private final String message;
    private final String stackTrace;
    private final String threadName;
    private final String contextCategory;
    private volatile int occurrenceCount;

    public ErrorRecord(Throwable throwable, String contextCategory, String threadName) {
        this.errorId = "err_" + UUID.randomUUID().toString().substring(0, 8);
        this.firstOccurrence = Instant.now();
        this.lastOccurrence = firstOccurrence;
        this.exceptionClass = throwable != null ? throwable.getClass().getName() : "UnknownException";
        this.message = throwable != null && throwable.getMessage() != null ? throwable.getMessage() : "";
        this.stackTrace = extractStackTrace(throwable);
        this.contextCategory = contextCategory != null ? contextCategory : "GENERAL";
        this.threadName = threadName != null ? threadName : Thread.currentThread().getName();
        this.occurrenceCount = 1;
    }

    public static String extractStackTrace(Throwable throwable) {
        if (throwable == null) return "";
        StringWriter sw = new StringWriter();
        throwable.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public void incrementOccurrence() {
        this.occurrenceCount++;
        this.lastOccurrence = Instant.now();
    }

    public String getErrorId() { return errorId; }
    public Instant getFirstOccurrence() { return firstOccurrence; }
    public Instant getLastOccurrence() { return lastOccurrence; }
    public String getExceptionClass() { return exceptionClass; }
    public String getMessage() { return message; }
    public String getStackTrace() { return stackTrace; }
    public String getThreadName() { return threadName; }
    public String getContextCategory() { return contextCategory; }
    public int getOccurrenceCount() { return occurrenceCount; }

    /**
     * Fingerprint used to deduplicate recurring identical exceptions.
     */
    public String getFingerprint() {
        return exceptionClass + "::" + message + "::" + (stackTrace.lines().findFirst().orElse(""));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ErrorRecord that)) return false;
        return Objects.equals(errorId, that.errorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(errorId);
    }
}

