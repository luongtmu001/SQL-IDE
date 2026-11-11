package com.lltool.core.domain.tabledesigner.valueobject;

import com.lltool.core.common.identity.EntityId;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable unique identifier for a table column throughout its lifecycle in Table Designer.
 * Prevents mistaken column renames on duplicate/clone operations (Fixes Bug B1)
 * and enables robust relational dependency tracking (Fixes Bug B4).
 */
public record ColumnId(String value) implements EntityId<String> {

    public ColumnId {
        if (value == null || value.isBlank()) {
            value = UUID.randomUUID().toString();
        }
    }

    public static ColumnId generate() {
        return new ColumnId(UUID.randomUUID().toString());
    }

    public static ColumnId of(String value) {
        return new ColumnId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

