package com.lltool.core.common.time;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Mutable, deterministic clock for unit and integration testing.
 */
public class TestTimeProvider implements TimeProvider {

    private Instant currentInstant;

    public TestTimeProvider(Instant initial) {
        this.currentInstant = Objects.requireNonNull(initial, "initial instant cannot be null");
    }

    @Override
    public Instant now() {
        return currentInstant;
    }

    public void set(Instant newInstant) {
        this.currentInstant = Objects.requireNonNull(newInstant);
    }

    public void advance(Duration duration) {
        this.currentInstant = this.currentInstant.plus(duration);
    }
}

