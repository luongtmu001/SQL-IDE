package app.diagnostics.errors;

import app.diagnostics.core.DiagnosticsEvent;
import app.diagnostics.core.DiagnosticsManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects, categorizes, and deduplicates runtime exceptions.
 */
public final class ExceptionCollector {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionCollector.class);
    private static final ExceptionCollector INSTANCE = new ExceptionCollector();
    private static final int DEFAULT_MAX_ERRORS = 150;

    private final Map<String, ErrorRecord> fingerprintMap = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<ErrorRecord> errorList = new ConcurrentLinkedDeque<>();
    private final AtomicInteger errorCount = new AtomicInteger(0);
    private final AtomicBoolean handlerInstalled = new AtomicBoolean(false);
    private final int maxErrors;

    private ExceptionCollector() {
        this(DEFAULT_MAX_ERRORS);
    }

    public ExceptionCollector(int maxErrors) {
        this.maxErrors = Math.max(10, maxErrors);
    }

    public static ExceptionCollector getInstance() {
        return INSTANCE;
    }

    /**
     * Records an exception, deduplicating recurring instances.
     */
    public ErrorRecord recordException(Throwable throwable, String contextCategory) {
        if (throwable == null) {
            return null;
        }

        try {
            String threadName = Thread.currentThread().getName();
            ErrorRecord newRecord = new ErrorRecord(throwable, contextCategory, threadName);
            String fingerprint = newRecord.getFingerprint();

            ErrorRecord existing = fingerprintMap.putIfAbsent(fingerprint, newRecord);
            if (existing != null) {
                existing.incrementOccurrence();
                return existing;
            }

            // New unique error
            errorList.addLast(newRecord);
            if (errorCount.incrementAndGet() > maxErrors) {
                ErrorRecord evicted = errorList.pollFirst();
                if (evicted != null) {
                    fingerprintMap.remove(evicted.getFingerprint());
                    errorCount.decrementAndGet();
                }
            }

            DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.error(
                    contextCategory != null ? contextCategory : "ERROR",
                    throwable.getClass().getSimpleName(),
                    throwable.getMessage() != null ? throwable.getMessage() : "Unknown exception",
                    throwable
            ));

            return newRecord;
        } catch (Throwable t) {
            LOGGER.warn("Failed to collect exception safely: {}", t.getMessage());
            return null;
        }
    }

    /**
     * Non-invasively attaches an uncaught exception handler, preserving any existing handlers.
     */
    public synchronized void installUncaughtExceptionHandler() {
        if (handlerInstalled.compareAndSet(false, true)) {
            Thread.UncaughtExceptionHandler existing = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                recordException(throwable, "UNCAUGHT");
                if (existing != null) {
                    existing.uncaughtException(thread, throwable);
                }
            });
            LOGGER.info("Diagnostics uncaught exception hook installed safely.");
        }
    }

    public List<ErrorRecord> getErrors() {
        return Collections.unmodifiableList(new ArrayList<>(errorList));
    }

    public int getTotalUniqueErrors() {
        return errorCount.get();
    }

    public void clear() {
        fingerprintMap.clear();
        errorList.clear();
        errorCount.set(0);
    }
}

