package com.lltool.core.common.time;

import java.time.Instant;

public enum SystemTimeProvider implements TimeProvider {
    INSTANCE;

    @Override
    public Instant now() {
        return Instant.now();
    }
}

