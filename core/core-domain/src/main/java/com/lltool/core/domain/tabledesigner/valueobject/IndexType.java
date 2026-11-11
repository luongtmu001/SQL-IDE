package com.lltool.core.domain.tabledesigner.valueobject;

/**
 * Dialect-aware index types supporting SQL Server and PostgreSQL.
 * Fixes Bug B3 (preventing PostgreSQL indexes from defaulting to NONCLUSTERED).
 */
public enum IndexType {
    CLUSTERED("CLUSTERED"),
    NONCLUSTERED("NONCLUSTERED"),
    BTREE("btree"),
    HASH("hash"),
    GIN("gin"),
    GIST("gist"),
    BRIN("brin");

    private final String keyword;

    IndexType(String keyword) {
        this.keyword = keyword;
    }

    public String keyword() {
        return keyword;
    }

    public static IndexType fromString(String raw, String engine) {
        if (raw == null || raw.isBlank()) {
            return "postgresql".equalsIgnoreCase(engine) ? BTREE : NONCLUSTERED;
        }
        String clean = raw.trim();
        for (IndexType type : values()) {
            if (type.name().equalsIgnoreCase(clean) || type.keyword.equalsIgnoreCase(clean)) {
                return type;
            }
        }
        return "postgresql".equalsIgnoreCase(engine) ? BTREE : NONCLUSTERED;
    }
}

