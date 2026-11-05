package com.lltool.core.common.identity;

import java.util.UUID;

/**
 * Pluggable strategy for unique identity generation.
 */
@FunctionalInterface
public interface IdGenerator {

    String nextId();

    static IdGenerator uuid() {
        return () -> UUID.randomUUID().toString();
    }

    static IdGenerator prefixed(String prefix) {
        return () -> prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}

