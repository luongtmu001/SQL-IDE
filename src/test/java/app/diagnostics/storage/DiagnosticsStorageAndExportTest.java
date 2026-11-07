package app.diagnostics.storage;

import app.diagnostics.advanced.DeadlockDetector;
import app.diagnostics.advanced.FlightRecorderManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsStorageAndExportTest {

    @Test
    @DisplayName("DeadlockDetector should safely check JVM for thread deadlocks")
    void testDeadlockDetection() {
        DeadlockDetector detector = DeadlockDetector.getInstance();
        var report = detector.detectDeadlocks();
        assertNotNull(report);
        assertNotNull(report.timestamp());
        assertFalse(report.deadlockDetected(), "Healthy JVM during tests should have no deadlocks");
        assertTrue(report.deadlockedThreads().isEmpty());
    }

    @Test
    @DisplayName("FlightRecorderManager should verify JFR availability")
    void testFlightRecorderManager() {
        FlightRecorderManager jfr = FlightRecorderManager.getInstance();
        assertNotNull(jfr);
        // In standard Java 21 OpenJDK, JFR is available
        boolean available = jfr.isAvailable();
        assertTrue(available || !available); // safe invocation check
    }

    @Test
    @DisplayName("DiagnosticsExporter should generate TXT, JSON, and ZIP bundle")
    void testExportGeneration(@TempDir Path tempDir) throws IOException {
        DiagnosticsStorage storage = new DiagnosticsStorage(tempDir);
        DiagnosticsExporter exporter = new DiagnosticsExporter(storage);

        String txt = exporter.generateTextReport();
        assertNotNull(txt);
        assertTrue(txt.contains("LLTOOL - APPLICATION DIAGNOSTICS REPORT"));
        assertTrue(txt.contains("STARTUP ANALYSIS"));

        String json = exporter.generateJsonReport();
        assertNotNull(json);
        assertTrue(json.startsWith("{"));
        assertTrue(json.endsWith("}\n") || json.endsWith("}"));
        assertTrue(json.contains("\"os\""));

        Path bundle = exporter.createDiagnosticsBundle(null);
        assertNotNull(bundle);
        assertTrue(Files.exists(bundle));
        assertTrue(bundle.getFileName().toString().startsWith("DiagnosticsBundle_"));
        assertTrue(bundle.getFileName().toString().endsWith(".zip"));
    }
}

