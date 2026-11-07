package app.diagnostics.core;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Encapsulates a diagnostics session with bounded in-memory event collection.
 */
public final class DiagnosticsSession {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());

    private final String sessionId;
    private final Instant startTime;
    private final AtomicReference<Instant> endTime = new AtomicReference<>(null);
    private final DiagnosticsConfig initialConfig;
    private final AtomicReference<DiagnosticsLevel> activeLevel;
    private final int maxEvents;

    private final ConcurrentLinkedDeque<DiagnosticsEvent> events = new ConcurrentLinkedDeque<>();
    private final AtomicInteger eventCount = new AtomicInteger(0);

    public DiagnosticsSession(DiagnosticsConfig config, DiagnosticsLevel level) {
        this.initialConfig = Objects.requireNonNull(config, "config must not be null");
        this.activeLevel = new AtomicReference<>(level != null ? level : config.getLevel());
        this.maxEvents = Math.max(10, config.getMaxEventsInMemory());
        this.startTime = Instant.now();
        this.sessionId = generateSessionId(startTime);
    }

    private static String generateSessionId(Instant timestamp) {
        String ts = TIMESTAMP_FORMAT.format(timestamp);
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        return "diag_session_" + ts + "_" + shortUuid;
    }

    public String getSessionId() {
        return sessionId;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime.get();
    }

    public boolean isClosed() {
        return endTime.get() != null;
    }

    public DiagnosticsLevel getActiveLevel() {
        return activeLevel.get();
    }

    public void setActiveLevel(DiagnosticsLevel newLevel) {
        if (newLevel != null) {
            this.activeLevel.set(newLevel);
        }
    }

    public DiagnosticsConfig getInitialConfig() {
        return initialConfig;
    }

    /**
     * Records a diagnostic event into the session, evicting the oldest event
     * if the maximum in-memory capacity is reached.
     */
    public void recordEvent(DiagnosticsEvent event) {
        if (event == null || isClosed()) {
            return;
        }

        events.addLast(event);
        if (eventCount.incrementAndGet() > maxEvents) {
            if (events.pollFirst() != null) {
                eventCount.decrementAndGet();
            }
        }
    }

    /**
     * Returns an unmodifiable snapshot of all recorded events in this session.
     */
    public List<DiagnosticsEvent> getEvents() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    /**
     * Returns the most recent N events in chronological order.
     */
    public List<DiagnosticsEvent> getRecentEvents(int maxCount) {
        if (maxCount <= 0) {
            return Collections.emptyList();
        }
        List<DiagnosticsEvent> all = new ArrayList<>(events);
        if (all.size() <= maxCount) {
            return Collections.unmodifiableList(all);
        }
        return Collections.unmodifiableList(all.subList(all.size() - maxCount, all.size()));
    }

    public int getRecordedEventCount() {
        return eventCount.get();
    }

    /**
     * Gracefully closes the session.
     */
    public void close() {
        endTime.compareAndSet(null, Instant.now());
    }

    @Override
    public String toString() {
        return "DiagnosticsSession{" +
                "sessionId='" + sessionId + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime.get() +
                ", activeLevel=" + activeLevel.get() +
                ", eventCount=" + eventCount.get() +
                '}';
    }
}

