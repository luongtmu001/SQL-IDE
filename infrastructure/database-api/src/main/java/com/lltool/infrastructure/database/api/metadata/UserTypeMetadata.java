package com.lltool.infrastructure.database.api.metadata;

/**
 * User Defined Type (UDT) metadata record.
 */
public record UserTypeMetadata(
        String name,
        String schema,
        String catalog,
        String baseType,
        String remarks
) {
    public UserTypeMetadata(String name, String schema, String catalog, String baseType) {
        this(name, schema, catalog, baseType, null);
    }
}

