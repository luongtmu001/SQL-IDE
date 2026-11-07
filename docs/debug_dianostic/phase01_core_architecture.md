# Phase 01: Diagnostics Core Architecture & API Documentation

**Package:** `app.diagnostics.core`  
**Status:** Completed  
**Pattern:** Non-invasive, Decoupled Diagnostics Engine  

---

## 1. Overview & Architectural Guarantees

Phase 01 establishes the foundational infrastructure for the **Application Diagnostics & Debug Center**. In strict accordance with the non-invasive architectural principle, the Diagnostics Core operates as an independent, decoupled subsystem.

### Key Guarantees:
1. **Zero Business Logic Interference:** The core does not hook into or alter any database, SQL execution, or Monaco editor flows.
2. **Fail-Safe / Zero-Impact Execution:** All public APIs catch internal exceptions and guarantee that no diagnostics failures escape into the calling application.
3. **Memory Bounded:** Event collection in sessions employs automatic eviction with a configurable upper limit (`maxEventsInMemory`, default: 1,000) to prevent OOM.
4. **Idempotent Lifecycle:** The `DiagnosticsManager` can be safely initialized, re-configured, or shut down multiple times without deadlocks or thread leaks.

---

## 2. Component Structure

```text
src/main/java/app/diagnostics/core/
├── DiagnosticsLevel.java       # Debug severity & scope levels (OFF, BASIC, BALANCED, ADVANCED)
├── DiagnosticsConfig.java      # Immutable runtime configuration with builder support
├── DiagnosticsEvent.java       # Immutable event model with severity, category, and metadata
├── DiagnosticsSession.java     # Stateful session tracker with bounded memory and eviction
└── DiagnosticsManager.java     # Singleton facade, lifecycle manager, and safe event bus
```

---

## 3. Component Details & API Reference

### 3.1. `DiagnosticsLevel`
An enum defining the diagnostic and monitoring granularity:
* `OFF (0)`: Diagnostics disabled or background baseline only.
* `BASIC (1)`: Basic application diagnostics (Uptime, CPU, Memory, Threads, Logs).
* `BALANCED (2)`: Task tracking, JavaFX UI responsiveness, WebView, Database diagnostics.
* `ADVANCED (3)`: Java Flight Recorder (JFR), CPU profiling, Deadlock detection.

**Methods:**
* `isAtLeast(DiagnosticsLevel required)`: Returns `true` if current level meets or exceeds the required level.
* `fromCode(int code)`: Resolves an integer code to a `DiagnosticsLevel` (fallback: `BASIC`).

---

### 3.2. `DiagnosticsConfig`
Thread-safe, immutable configuration record.

**Default Values:**
* `samplingIntervalMs`: 2000 ms
* `maxEventsInMemory`: 1000 events
* `level`: `DiagnosticsLevel.BASIC`
* `debugModeEnabled`: `false`
* `exportDirectory`: `~/.sqlide/diagnostics`

**Creation:**
```java
DiagnosticsConfig config = DiagnosticsConfig.builder()
    .debugModeEnabled(true)
    .level(DiagnosticsLevel.BALANCED)
    .samplingIntervalMs(1000L)
    .maxEventsInMemory(2000)
    .build();
```

---

### 3.3. `DiagnosticsEvent`
An immutable snapshot of a system event or telemetry record.

**Severities:** `DEBUG`, `INFO`, `WARN`, `ERROR`

**Factories:**
* `DiagnosticsEvent.info(category, name, message)`
* `DiagnosticsEvent.warn(category, name, message)`
* `DiagnosticsEvent.error(category, name, message, throwable)`
* `DiagnosticsEvent.debug(category, name, message)`
* `DiagnosticsEvent.builder()` for custom attributes & arbitrary metadata maps.

---

### 3.4. `DiagnosticsSession`
Represents an individual diagnostics run (from application boot or session start).

**Memory Safety:**
* Employs `ConcurrentLinkedDeque<DiagnosticsEvent>` paired with atomic counters.
* When events exceed `maxEventsInMemory`, oldest events are discarded (`pollFirst()`), guaranteeing steady bounded memory usage.
* Snapshot methods: `getEvents()` and `getRecentEvents(int maxCount)`.

---

### 3.5. `DiagnosticsManager`
Central singleton coordinating lifecycle, configuration, and event distribution.

**Key APIs:**
* `DiagnosticsManager.getInstance()`: Singleton accessor.
* `initialize(DiagnosticsConfig config)`: Safe initialization; creates the initial session.
* `shutdown()`: Closes active session, clears observers, releases resources.
* `startSession(DiagnosticsLevel level)`: Creates a new tracking session.
* `endCurrentSession()`: Closes the active session gracefully.
* `setDebugMode(boolean enabled, DiagnosticsLevel level)`: Toggles debug mode at runtime.
* `publishEvent(DiagnosticsEvent event)`: Thread-safe, non-blocking event publishing. Wraps listener dispatch in try/catch to protect callers from faulty listeners.
* `addEventListener(Consumer<DiagnosticsEvent> listener)` / `removeEventListener(...)`: Observer management for UI views or exporters.

---

## 4. Verification & Testing

Unit test suite: `src/test/java/app/diagnostics/core/DiagnosticsCoreTest.java`

* Verified: Level hierarchy and code conversions.
* Verified: Configuration defaults and fluent builder.
* Verified: Event factory methods, exception payload capture, and metadata immutability.
* Verified: Memory bounds and oldest-event eviction in `DiagnosticsSession`.
* Verified: Fault-tolerance against throwing listeners and null parameters.
* Verified: Graceful shutdown and re-initialization.

