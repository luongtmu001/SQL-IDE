package app.models;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ConsoleMessage {

    public enum Level {
        INFO("INFO", "badge-info"),
        SUCCESS("SUCCESS", "badge-success"),
        WARNING("WARN", "badge-warning"),
        ERROR("ERROR", "badge-danger");

        private final String label;
        private final String styleClass;

        Level(String label, String styleClass) {
            this.label = label;
            this.styleClass = styleClass;
        }

        public String getLabel() {
            return label;
        }

        public String getStyleClass() {
            return styleClass;
        }
    }

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final String timestamp;
    private final Level level;
    private final String message;
    private final String duration;

    public ConsoleMessage(Level level, String message, String duration) {
        this(LocalDateTime.now().format(TIME_FORMATTER), level, message, duration);
    }

    public ConsoleMessage(String timestamp, Level level, String message, String duration) {
        this.timestamp = timestamp;
        this.level = level;
        this.message = message;
        this.duration = duration != null ? duration : "";
    }

    public String getTimestamp() {
        return timestamp;
    }

    public Level getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public String getDuration() {
        return duration;
    }

    @Override
    public String toString() {
        return String.format("[%s] [%s] %s %s", timestamp, level.getLabel(), message, duration.isEmpty() ? "" : ("(" + duration + ")"));
    }
}

