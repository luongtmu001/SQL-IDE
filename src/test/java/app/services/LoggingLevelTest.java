package app.services;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** S3: LLTool log level names map to logback levels. */
class LoggingLevelTest {

    @Test
    void lltoolLevelNames() {
        assertEquals(Level.DEBUG, LoggingService.level("DEBUG"));
        assertEquals(Level.INFO, LoggingService.level("INFO"));
        assertEquals(Level.WARN, LoggingService.level("WARNING"));
        assertEquals(Level.ERROR, LoggingService.level("error"));
        assertEquals(Level.INFO, LoggingService.level(null));
        assertEquals(Level.INFO, LoggingService.level("verbose"));
    }
}
