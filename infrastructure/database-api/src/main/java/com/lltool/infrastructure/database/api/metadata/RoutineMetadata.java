package com.lltool.infrastructure.database.api.metadata;

import java.io.Serializable;
import java.util.Objects;

/**
 * Metadata descriptor for a stored procedure or function (Phase 6.3).
 */
public record RoutineMetadata(
        String name,
        String schema,
        String catalog,
        String routineType, // "PROCEDURE" or "FUNCTION"
        String returnType
) implements Serializable {

    public RoutineMetadata {
        Objects.requireNonNull(name, "Routine name cannot be null");
        routineType = routineType != null ? routineType : "FUNCTION";
    }

    public boolean isProcedure() {
        return "PROCEDURE".equalsIgnoreCase(routineType);
    }
}

