package app.diagnostics.core;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable event representing a diagnostic observation, metric, or error.
 */
public final class DiagnosticsEvent {

    public enum Severity {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    private final String id;
    private final Instant timestamp;
    private final String category;
    private final Severity severity;
    private final String name;
    private final String message;
    private final Map<String, Object> metadata;

    private DiagnosticsEvent(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.timestamp = builder.timestamp != null ? builder.timestamp : Instant.now();
        this.category = builder.category != null ? builder.category : "GENERAL";
        this.severity = builder.severity != null ? builder.severity : Severity.INFO;
        this.name = builder.name != null ? builder.name : "";
        this.message = builder.message != null ? builder.message : "";
        this.metadata = builder.metadata != null
                ? Collections.unmodifiableMap(new HashMap<>(builder.metadata))
                : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DiagnosticsEvent info(String category, String name, String message) {
        return builder()
                .category(category)
                .severity(Severity.INFO)
                .name(name)
                .message(message)
                .build();
    }

    public static DiagnosticsEvent warn(String category, String name, String message) {
        return builder()
                .category(category)
                .severity(Severity.WARN)
                .name(name)
                .message(message)
                .build();
    }

    public static DiagnosticsEvent error(String category, String name, String message, Throwable throwable) {
        Builder b = builder()
                .category(category)
                .severity(Severity.ERROR)
                .name(name)
                .message(message);
        if (throwable != null) {
            b.metadata("exception_class", throwable.getClass().getName());
            b.metadata("exception_message", throwable.getMessage() != null ? throwable.getMessage() : "");
        }
        return b.build();
    }

    public static DiagnosticsEvent debug(String category, String name, String message) {
        return builder()
                .category(category)
                .severity(Severity.DEBUG)
                .name(name)
                .message(message)
                .build();
    }

    public String getId() {
        return id;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getCategory() {
        return category;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getName() {
        return name;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DiagnosticsEvent that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DiagnosticsEvent{" +
                "id='" + id + '\'' +
                ", timestamp=" + timestamp +
                ", category='" + category + '\'' +
                ", severity=" + severity +
                ", name='" + name + '\'' +
                ", message='" + message + '\'' +
                ", metadata=" + metadata +
                '}';
    }

    public static final class Builder {
        private String id;
        private Instant timestamp;
        private String category;
        private Severity severity;
        private String name;
        private String message;
        private Map<String, Object> metadata;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder severity(Severity severity) {
            this.severity = severity;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder metadata(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = new HashMap<>();
            }
            if (key != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            if (metadata != null) {
                if (this.metadata == null) {
                    this.metadata = new HashMap<>();
                }
                this.metadata.putAll(metadata);
            }
            return this;
        }

        public DiagnosticsEvent build() {
            return new DiagnosticsEvent(this);
        }
    }
}

