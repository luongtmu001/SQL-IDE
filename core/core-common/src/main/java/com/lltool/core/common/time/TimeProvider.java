package com.lltool.core.common.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Clock abstraction to allow deterministic testing of time-dependent domain models.
 */
public interface TimeProvider {

    Instant now();

    default LocalDateTime nowLocalDateTime() {
        return LocalDateTime.ofInstant(now(), ZoneId.systemDefault());
    }

    default long currentTimeMillis() {
        return now().toEpochMilli();
    }

    static TimeProvider system() {
        return SystemTimeProvider.INSTANCE;
    }

    static TestTimeProvider fixed(Instant instant) {
        return new TestTimeProvider(instant);
    }
}

