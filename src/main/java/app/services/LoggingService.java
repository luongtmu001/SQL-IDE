package app.services;

import app.models.IdeSettings;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * S3 (2026-11-06): the "System &amp; Error Logs" settings act on logback at run time (LLTool app-logger):
 * <ul>
 *   <li>{@code logging.level}: level of the root logger (DEBUG / INFO / WARNING / ERROR).</li>
 *   <li>{@code logging.enabled}: a rolling file {@code sql-ide.log} in the app's logs folder
 *       ({@code %APPDATA%\LLTool-Desktop\logs}); before this there was no log file at all.</li>
 *   <li>{@code logging.maxFileSizeMb} / {@code logging.maxFiles}: size of one file and how many old files are kept.</li>
 * </ul>
 * Applied at start-up and every time Settings are saved; the console output stays as it is.
 */
public final class LoggingService {

    private static final String APPENDER = "SQLIDE_FILE";
    public static final String LOG_FILE = "sql-ide.log";

    private LoggingService() {
    }

    public static synchronized void apply(IdeSettings settings) {
        if (settings == null || !(LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx)) return;
        IdeSettings.LoggingSettings cfg = settings.getLogging() != null ? settings.getLogging() : new IdeSettings.LoggingSettings();
        Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(level(cfg.getLevel()));

        Appender<ILoggingEvent> old = root.getAppender(APPENDER);
        if (old != null) {
            root.detachAppender(old);
            old.stop();
        }
        if (!cfg.isEnabled() || isTestRun()) return;
        Path dir = logsDir();
        if (dir == null) return;
        try {
            Files.createDirectories(dir);
            String base = dir.toString().replace('\\', '/'); // logback patterns treat '\' as an escape

            RollingFileAppender<ILoggingEvent> file = new RollingFileAppender<>();
            file.setContext(ctx);
            file.setName(APPENDER);
            file.setFile(base + "/" + LOG_FILE);

            FixedWindowRollingPolicy rolling = new FixedWindowRollingPolicy();
            rolling.setContext(ctx);
            rolling.setParent(file);
            rolling.setFileNamePattern(base + "/sql-ide.%i.log");
            rolling.setMinIndex(1);
            rolling.setMaxIndex(Math.max(1, Math.min(20, cfg.getMaxFiles())));
            rolling.start();

            SizeBasedTriggeringPolicy<ILoggingEvent> trigger = new SizeBasedTriggeringPolicy<>();
            trigger.setContext(ctx);
            trigger.setMaxFileSize(FileSize.valueOf(Math.max(1, cfg.getMaxFileSizeMb()) + "MB"));
            trigger.start();

            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(ctx);
            encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n");
            encoder.setCharset(StandardCharsets.UTF_8);
            encoder.start();

            file.setRollingPolicy(rolling);
            file.setTriggeringPolicy(trigger);
            file.setEncoder(encoder);
            file.start();
            root.addAppender(file);
        } catch (Exception e) {
            LoggerFactory.getLogger(LoggingService.class).warn("File logging not started: {}", e.getMessage());
        }
    }

    /** Folder of the log files (also opened by Settings "Mở thư mục nhật ký"). */
    public static Path logsDir() {
        try {
            return app.framework.bootstrap.DesktopBootstrap.getInstance().getDirectoryManager().getLogsDir();
        } catch (Exception e) {
            return null;
        }
    }

    /** LLTool names (WARNING) and logback names (WARN); anything else = INFO. */
    static Level level(String name) {
        if (name == null) return Level.INFO;
        return switch (name.trim().toUpperCase(Locale.ROOT)) {
            case "DEBUG" -> Level.DEBUG;
            case "TRACE" -> Level.TRACE;
            case "WARNING", "WARN" -> Level.WARN;
            case "ERROR", "CRITICAL" -> Level.ERROR;
            default -> Level.INFO;
        };
    }

    private static boolean isTestRun() {
        return System.getProperty("surefire.test.class.path") != null;
    }
}
