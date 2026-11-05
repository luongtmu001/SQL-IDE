package app.framework.error;

import com.lltool.infrastructure.logging.SensitiveDataMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Global Desktop Error Handler (Phase 4).
 * Catches uncaught thread exceptions, masks sensitive tokens, and logs safely.
 */
public class DesktopErrorHandler implements Thread.UncaughtExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DesktopErrorHandler.class);
    private static DesktopErrorHandler instance;

    private DesktopErrorHandler() {}

    public static synchronized DesktopErrorHandler getInstance() {
        if (instance == null) {
            instance = new DesktopErrorHandler();
        }
        return instance;
    }

    public void install() {
        Thread.setDefaultUncaughtExceptionHandler(this);
        log.info("Installed global Desktop uncaught exception handler");
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        String rawMessage = e != null ? e.getMessage() : "Unknown error";
        String maskedMessage = SensitiveDataMasker.mask(rawMessage);
        log.error("Uncaught exception in thread [{}]: {}", t.getName(), maskedMessage, e);
    }
}

