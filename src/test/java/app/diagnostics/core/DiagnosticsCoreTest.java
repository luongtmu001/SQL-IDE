package app.diagnostics.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsCoreTest {

    private DiagnosticsManager manager;

    @BeforeEach
    void setUp() {
        manager = DiagnosticsManager.getInstance();
        manager.initialize(DiagnosticsConfig.defaultConfig());
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    @Test
    @DisplayName("DiagnosticsLevel should have correct hierarchy and code mapping")
    void testLevelHierarchy() {
        assertEquals(0, DiagnosticsLevel.OFF.getCode());
        assertEquals(1, DiagnosticsLevel.BASIC.getCode());
        assertEquals(2, DiagnosticsLevel.BALANCED.getCode());
        assertEquals(3, DiagnosticsLevel.ADVANCED.getCode());

        assertTrue(DiagnosticsLevel.ADVANCED.isAtLeast(DiagnosticsLevel.BASIC));
        assertTrue(DiagnosticsLevel.BALANCED.isAtLeast(DiagnosticsLevel.BASIC));
        assertTrue(DiagnosticsLevel.BALANCED.isAtLeast(DiagnosticsLevel.BALANCED));
        assertFalse(DiagnosticsLevel.BASIC.isAtLeast(DiagnosticsLevel.BALANCED));
        assertFalse(DiagnosticsLevel.OFF.isAtLeast(DiagnosticsLevel.BASIC));
        assertFalse(DiagnosticsLevel.BASIC.isAtLeast(null));

        assertEquals(DiagnosticsLevel.ADVANCED, DiagnosticsLevel.fromCode(3));
        assertEquals(DiagnosticsLevel.BASIC, DiagnosticsLevel.fromCode(99)); // fallback
    }

    @Test
    @DisplayName("DiagnosticsConfig should support defaults and fluent builder")
    void testConfigDefaultAndBuilder() {
        DiagnosticsConfig defaultConfig = DiagnosticsConfig.defaultConfig();
        assertFalse(defaultConfig.isDebugModeEnabled());
        assertEquals(DiagnosticsLevel.BASIC, defaultConfig.getLevel());
        assertEquals(DiagnosticsConfig.DEFAULT_SAMPLING_INTERVAL_MS, defaultConfig.getSamplingIntervalMs());
        assertEquals(DiagnosticsConfig.DEFAULT_MAX_EVENTS_IN_MEMORY, defaultConfig.getMaxEventsInMemory());
        assertNotNull(defaultConfig.getExportDirectory());

        DiagnosticsConfig custom = DiagnosticsConfig.builder()
                .debugModeEnabled(true)
                .level(DiagnosticsLevel.ADVANCED)
                .samplingIntervalMs(500L)
                .maxEventsInMemory(250)
                .exportDirectory(Paths.get("target/test-diag"))
                .build();

        assertTrue(custom.isDebugModeEnabled());
        assertEquals(DiagnosticsLevel.ADVANCED, custom.getLevel());
        assertEquals(500L, custom.getSamplingIntervalMs());
        assertEquals(250, custom.getMaxEventsInMemory());
        assertEquals(Paths.get("target/test-diag"), custom.getExportDirectory());
    }

    @Test
    @DisplayName("DiagnosticsEvent factory methods and metadata immutability")
    void testEventCreationAndFactories() {
        DiagnosticsEvent infoEvt = DiagnosticsEvent.info("SYSTEM", "SysTest", "System check");
        assertEquals("SYSTEM", infoEvt.getCategory());
        assertEquals(DiagnosticsEvent.Severity.INFO, infoEvt.getSeverity());
        assertEquals("SysTest", infoEvt.getName());
        assertEquals("System check", infoEvt.getMessage());
        assertNotNull(infoEvt.getId());
        assertNotNull(infoEvt.getTimestamp());

        RuntimeException cause = new RuntimeException("DB Connection failed");
        DiagnosticsEvent errEvt = DiagnosticsEvent.error("DATABASE", "ConnErr", "Failed to connect", cause);
        assertEquals(DiagnosticsEvent.Severity.ERROR, errEvt.getSeverity());
        assertEquals("java.lang.RuntimeException", errEvt.getMetadata().get("exception_class"));
        assertEquals("DB Connection failed", errEvt.getMetadata().get("exception_message"));

        assertThrows(UnsupportedOperationException.class, () ->
                errEvt.getMetadata().put("illegal", "mutation"));
    }

    @Test
    @DisplayName("DiagnosticsSession should bound event memory and evict oldest events")
    void testSessionBoundedCapacity() {
        DiagnosticsConfig smallCapConfig = DiagnosticsConfig.builder()
                .maxEventsInMemory(15) // small bound for test
                .build();

        DiagnosticsSession session = new DiagnosticsSession(smallCapConfig, DiagnosticsLevel.BASIC);
        assertNotNull(session.getSessionId());
        assertFalse(session.isClosed());

        for (int i = 1; i <= 30; i++) {
            session.recordEvent(DiagnosticsEvent.info("TEST", "Evt_" + i, "Msg " + i));
        }

        List<DiagnosticsEvent> recorded = session.getEvents();
        // Since bound is 15, event count in memory should not exceed 15
        assertTrue(recorded.size() <= 15, "Recorded events count should not exceed capacity: " + recorded.size());
        assertEquals("Evt_30", recorded.get(recorded.size() - 1).getName());

        List<DiagnosticsEvent> recent5 = session.getRecentEvents(5);
        assertEquals(5, recent5.size());
        assertEquals("Evt_30", recent5.get(4).getName());
        assertEquals("Evt_26", recent5.get(0).getName());

        session.close();
        assertTrue(session.isClosed());
        assertNotNull(session.getEndTime());
    }

    @Test
    @DisplayName("DiagnosticsManager lifecycle, event dispatching and listener notifications")
    void testDiagnosticsManagerLifecycle() {
        assertTrue(manager.isInitialized());
        assertFalse(manager.isShutdown());
        assertNotNull(manager.getCurrentSession());

        List<DiagnosticsEvent> received = new ArrayList<>();
        manager.addEventListener(received::add);

        DiagnosticsEvent testEvt = DiagnosticsEvent.info("TEST", "LifecycleTest", "Hello Diagnostics");
        manager.publishEvent(testEvt);

        assertTrue(received.stream().anyMatch(e -> "LifecycleTest".equals(e.getName())));

        manager.setDebugMode(true, DiagnosticsLevel.BALANCED);
        assertTrue(manager.isDebugModeEnabled());
        assertEquals(DiagnosticsLevel.BALANCED, manager.getActiveLevel());

        manager.shutdown();
        assertTrue(manager.isShutdown());
        assertFalse(manager.isInitialized());
    }

    @Test
    @DisplayName("DiagnosticsManager should be 100% fail-safe against listener errors and null arguments")
    void testDiagnosticsManagerFaultTolerance() {
        AtomicBoolean badListenerCalled = new AtomicBoolean(false);

        // Add faulty listener that throws an unhandled error
        manager.addEventListener(event -> {
            badListenerCalled.set(true);
            throw new RuntimeException("Simulated listener crash!");
        });

        // Add healthy listener
        AtomicBoolean healthyListenerCalled = new AtomicBoolean(false);
        manager.addEventListener(event -> healthyListenerCalled.set(true));

        // Publish event: should NOT throw exception and healthy listener should still be invoked
        assertDoesNotThrow(() -> {
            manager.publishEvent(DiagnosticsEvent.warn("TEST", "FaultTolerance", "Testing crash resistance"));
        });

        assertTrue(badListenerCalled.get());
        assertTrue(healthyListenerCalled.get());

        // Null safety
        assertDoesNotThrow(() -> manager.publishEvent(null));
        assertDoesNotThrow(() -> manager.addEventListener(null));
        assertDoesNotThrow(() -> manager.removeEventListener(null));
        assertDoesNotThrow(() -> manager.updateConfig(null));
    }
}

