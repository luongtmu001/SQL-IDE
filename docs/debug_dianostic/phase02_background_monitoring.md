# Phase 02: Background Monitoring Architecture & Metrics Specification

**Package:** `app.diagnostics.monitoring`  
**Status:** Completed  
**Technology:** Java Management Extensions (JMX MXBeans), `java.lang.management`, `com.sun.management`  

---

## 1. Overview & Objectives

Phase 02 implements lightweight, continuous background monitoring for the JVM and host operating system. The subsystem operates unobtrusively, collecting health and telemetry metrics at configurable intervals without interfering with the JavaFX Application Thread or application responsiveness.

### Strict Principles:
* **Zero Thread Blocking:** All sampling runs asynchronously on a dedicated, lowest-priority **Daemon Thread** (`LLTool-Diagnostics-Monitor`).
* **Non-Invasive:** Does not hook into any database, SQL engine, or Monaco editor components.
* **Bounded In-Memory Footprint:** Keeps a sliding window of historical metrics snapshots (default: 120 samples, ~2-4 minutes) to prevent memory accumulation.
* **No Continuous File I/O:** Metrics stay in memory and are only exported when explicitly requested or during a diagnostics dump.

---

## 2. Component Structure

```text
src/main/java/app/diagnostics/monitoring/
├── SystemMetricsSnapshot.java    # Immutable record capturing all metrics at time T
├── CpuMonitor.java               # Process & System CPU load via OperatingSystemMXBean
├── MemoryMonitor.java            # Heap & Non-Heap usage via MemoryMXBean
├── ThreadMonitor.java            # Live, Daemon, & Peak thread counts via ThreadMXBean
├── JvmMonitor.java               # JVM uptime, VM identity, and GC pause statistics
└── SystemMonitor.java            # Central background scheduler & observer dispatcher
```

---

## 3. Metrics Specification

| Metric | Source | Unit | Description |
| :--- | :--- | :--- | :--- |
| **Process CPU Load** | `CpuMonitor` | Percentage (0.0 - 100%) | CPU usage consumed by the LLTool JVM process |
| **System CPU Load** | `CpuMonitor` | Percentage (0.0 - 100%) | Overall CPU load across the entire OS |
| **Available Cores** | `CpuMonitor` | Integer | Total logical CPU processors available to the runtime |
| **Heap Used** | `MemoryMonitor` | Bytes / MB | Current memory allocated to live objects |
| **Heap Committed** | `MemoryMonitor` | Bytes / MB | Memory guaranteed to be available for the JVM heap |
| **Heap Max** | `MemoryMonitor` | Bytes / MB | Maximum configured heap limit (`-Xmx`) |
| **Non-Heap Used** | `MemoryMonitor` | Bytes / MB | Metaspace and code cache memory usage |
| **Live Threads** | `ThreadMonitor` | Integer | Total active threads currently alive |
| **Daemon Threads** | `ThreadMonitor` | Integer | Background daemon threads currently alive |
| **Peak Threads** | `ThreadMonitor` | Integer | Peak thread count since JVM startup |
| **JVM Uptime** | `JvmMonitor` | Milliseconds | Total runtime of the Java Virtual Machine |
| **Total GC Count** | `JvmMonitor` | Integer | Aggregated count of Garbage Collector invocations |
| **Total GC Time** | `JvmMonitor` | Milliseconds | Aggregated stop-the-world and pause time spent in GC |

---

## 4. Lifecycle & Integration

```java
// Initialize with custom history buffer size (e.g., 120 samples)
SystemMonitor monitor = new SystemMonitor(120);

// Register observers (e.g. for UI graphs or alerts)
monitor.addSnapshotListener(snapshot -> {
    double cpu = snapshot.getProcessCpuPercentage();
    double heap = snapshot.getHeapUsagePercentage();
});

// Start background sampling every 2000ms
monitor.start(2000L);

// Capture immediate ad-hoc snapshot on demand
SystemMetricsSnapshot current = monitor.captureSnapshot();

// Graceful shutdown
monitor.stop();
```

---

## 5. Verification & Test Suite

Unit test suite: `src/test/java/app/diagnostics/monitoring/SystemMonitorTest.java`

* **`testCpuMonitor`:** Validates CPU load measurements and core count detection.
* **`testMemoryMonitor`:** Confirms positive heap/non-heap memory metrics.
* **`testThreadMonitor`:** Verifies active live and daemon thread counts.
* **`testJvmMonitor`:** Verifies uptime reporting, VM specification, and GC collection counters.
* **`testSnapshotCalculations`:** Validates accurate unit conversions (MB, percentages).
* **`testSystemMonitorLifecycle`:** Verifies periodic background sampling on a daemon thread, listener delivery, sliding-window bounding, and clean shutdown.

