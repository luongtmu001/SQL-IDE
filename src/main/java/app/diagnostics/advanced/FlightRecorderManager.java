package app.diagnostics.advanced;

import app.diagnostics.core.DiagnosticsEvent;
import app.diagnostics.core.DiagnosticsManager;
import jdk.jfr.Configuration;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages Java Flight Recorder (JFR) profiling sessions using Java 21 standard JDK JFR APIs.
 */
public final class FlightRecorderManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(FlightRecorderManager.class);
    private static final FlightRecorderManager INSTANCE = new FlightRecorderManager();

    private final AtomicBoolean recordingActive = new AtomicBoolean(false);
    private final AtomicReference<Recording> currentRecording = new AtomicReference<>(null);

    private FlightRecorderManager() {}

    public static FlightRecorderManager getInstance() {
        return INSTANCE;
    }

    /**
     * Checks if Java Flight Recorder is available on this JVM runtime.
     */
    public boolean isAvailable() {
        try {
            return FlightRecorder.isAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isRecording() {
        return recordingActive.get();
    }

    /**
     * Starts a JFR recording session using the standard "default" configuration.
     */
    public synchronized boolean startRecording(String sessionName, Duration maxDuration) {
        if (!isAvailable()) {
            LOGGER.warn("Java Flight Recorder is not available on this JVM.");
            return false;
        }

        if (recordingActive.get()) {
            LOGGER.warn("A JFR recording session is already active.");
            return false;
        }

        try {
            Configuration config = Configuration.getConfiguration("default");
            Recording recording = new Recording(config);
            recording.setName(sessionName != null ? sessionName : "LLTool-Diagnostics-Recording");

            if (maxDuration != null && !maxDuration.isZero() && !maxDuration.isNegative()) {
                recording.setDuration(maxDuration);
            }

            recording.start();
            currentRecording.set(recording);
            recordingActive.set(true);

            DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.info("JFR", "RecordingStarted",
                    "Started JFR recording: " + recording.getName()));

            LOGGER.info("JFR recording started successfully: {}", recording.getName());
            return true;
        } catch (Throwable t) {
            LOGGER.error("Failed to start JFR recording: {}", t.getMessage(), t);
            return false;
        }
    }

    /**
     * Stops the active JFR recording and dumps the recording file to destination.
     */
    public synchronized boolean stopAndDump(Path destination) {
        if (!recordingActive.get() || currentRecording.get() == null) {
            LOGGER.warn("No active JFR recording to stop.");
            return false;
        }

        Recording recording = currentRecording.getAndSet(null);
        recordingActive.set(false);

        try {
            recording.stop();
            if (destination != null) {
                recording.dump(destination);
                LOGGER.info("Dumped JFR recording to: {}", destination.toAbsolutePath());
            }
            recording.close();

            DiagnosticsManager.getInstance().publishEvent(DiagnosticsEvent.info("JFR", "RecordingStopped",
                    "JFR recording saved to: " + (destination != null ? destination.getFileName() : "in-memory")));

            return true;
        } catch (Throwable t) {
            LOGGER.error("Failed to stop and dump JFR recording: {}", t.getMessage(), t);
            return false;
        }
    }
}

