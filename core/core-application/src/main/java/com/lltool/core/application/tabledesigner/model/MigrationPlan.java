package com.lltool.core.application.tabledesigner.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Complete migration plan resulting from diffing original and modified table designs.
 */
public record MigrationPlan(
        List<MigrationStatement> statements,
        List<String> warnings,
        boolean hasDestructive,
        boolean hasChanges
) implements Serializable {

    public MigrationPlan {
        statements = statements != null ? Collections.unmodifiableList(new ArrayList<>(statements)) : Collections.emptyList();
        warnings = warnings != null ? Collections.unmodifiableList(new ArrayList<>(warnings)) : Collections.emptyList();
    }

    public static MigrationPlan empty() {
        return new MigrationPlan(Collections.emptyList(), Collections.emptyList(), false, false);
    }

    public String generateFullMigrationSql(String engine) {
        if (statements.isEmpty()) {
            return "";
        }
        boolean isSqlServer = "sqlserver".equalsIgnoreCase(engine);
        if (isSqlServer) {
            return statements.stream()
                    .map(s -> s.sql().endsWith(";") ? s.sql() : s.sql() + ";")
                    .collect(Collectors.joining("\nGO\n"));
        } else {
            return statements.stream()
                    .map(s -> s.sql().endsWith(";") ? s.sql() : s.sql() + ";")
                    .collect(Collectors.joining("\n\n"));
        }
    }
}

