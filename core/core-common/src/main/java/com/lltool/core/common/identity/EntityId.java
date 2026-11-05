package com.lltool.core.common.identity;

import java.io.Serializable;

/**
 * Strongly-typed Entity Identifier marker interface.
 * @param <T> Raw primitive or underlying type (e.g. String, UUID, Long)
 */
public interface EntityId<T> extends Serializable {

    T value();

    default String asString() {
        return value() != null ? value().toString() : "";
    }
}

