package app.diagnostics.core;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Immutable configuration settings for the Diagnostics system.
 */
public final class DiagnosticsConfig {

    public static final long DEFAULT_SAMPLING_INTERVAL_MS = 2000L;
    public static final int DEFAULT_MAX_EVENTS_IN_MEMORY = 1000;
    public static final DiagnosticsLevel DEFAULT_LEVEL = DiagnosticsLevel.BASIC;

    private final boolean debugModeEnabled;
    private final DiagnosticsLevel level;
    private final long samplingIntervalMs;
    private final int maxEventsInMemory;
    private final Path exportDirectory;

    private DiagnosticsConfig(Builder builder) {
        this.debugModeEnabled = builder.debugModeEnabled;
        this.level = builder.level != null ? builder.level : DEFAULT_LEVEL;
        this.samplingIntervalMs = builder.samplingIntervalMs > 0 ? builder.samplingIntervalMs : DEFAULT_SAMPLING_INTERVAL_MS;
        this.maxEventsInMemory = builder.maxEventsInMemory > 0 ? builder.maxEventsInMemory : DEFAULT_MAX_EVENTS_IN_MEMORY;
        this.exportDirectory = builder.exportDirectory != null
                ? builder.exportDirectory
                : Paths.get(System.getProperty("user.home"), ".sqlide", "diagnostics");
    }

    public static DiagnosticsConfig defaultConfig() {
        return new Builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isDebugModeEnabled() {
        return debugModeEnabled;
    }

    public DiagnosticsLevel getLevel() {
        return level;
    }

    public long getSamplingIntervalMs() {
        return samplingIntervalMs;
    }

    public int getMaxEventsInMemory() {
        return maxEventsInMemory;
    }

    public Path getExportDirectory() {
        return exportDirectory;
    }

    public Builder toBuilder() {
        return new Builder()
                .debugModeEnabled(this.debugModeEnabled)
                .level(this.level)
                .samplingIntervalMs(this.samplingIntervalMs)
                .maxEventsInMemory(this.maxEventsInMemory)
                .exportDirectory(this.exportDirectory);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiagnosticsConfig that)) return false;
        return debugModeEnabled == that.debugModeEnabled &&
                samplingIntervalMs == that.samplingIntervalMs &&
                maxEventsInMemory == that.maxEventsInMemory &&
                level == that.level &&
                Objects.equals(exportDirectory, that.exportDirectory);
    }

    @Override
    public int hashCode() {
        return Objects.hash(debugModeEnabled, level, samplingIntervalMs, maxEventsInMemory, exportDirectory);
    }

    @Override
    public String toString() {
        return "DiagnosticsConfig{" +
                "debugModeEnabled=" + debugModeEnabled +
                ", level=" + level +
                ", samplingIntervalMs=" + samplingIntervalMs +
                ", maxEventsInMemory=" + maxEventsInMemory +
                ", exportDirectory=" + exportDirectory +
                '}';
    }

    public static final class Builder {
        private boolean debugModeEnabled = false;
        private DiagnosticsLevel level = DiagnosticsLevel.BASIC;
        private long samplingIntervalMs = DEFAULT_SAMPLING_INTERVAL_MS;
        private int maxEventsInMemory = DEFAULT_MAX_EVENTS_IN_MEMORY;
        private Path exportDirectory;

        public Builder debugModeEnabled(boolean enabled) {
            this.debugModeEnabled = enabled;
            return this;
        }

        public Builder level(DiagnosticsLevel level) {
            this.level = level != null ? level : DiagnosticsLevel.BASIC;
            return this;
        }

        public Builder samplingIntervalMs(long intervalMs) {
            this.samplingIntervalMs = intervalMs;
            return this;
        }

        public Builder maxEventsInMemory(int maxEvents) {
            this.maxEventsInMemory = maxEvents;
            return this;
        }

        public Builder exportDirectory(Path exportDirectory) {
            this.exportDirectory = exportDirectory;
            return this;
        }

        public DiagnosticsConfig build() {
            return new DiagnosticsConfig(this);
        }
    }
}

