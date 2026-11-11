package com.lltool.infrastructure.database.sqlserver;

import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;
import com.lltool.core.domain.tabledesigner.model.*;
import com.lltool.core.domain.tabledesigner.valueobject.ForeignKeyAction;
import com.lltool.core.domain.tabledesigner.valueobject.IndexType;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.metadata.TableDesignReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-fidelity TableDesign reader for Microsoft SQL Server.
 * Faithfully ports metadata queries from Python sqlserver.py:1557.
 */
public class SqlServerTableDesignReader implements TableDesignReader {

    private static final Logger log = LoggerFactory.getLogger(SqlServerTableDesignReader.class);
    private static final Pattern NEXT_VAL_REGEX = Pattern.compile("(?i)NEXT\\s+VALUE\\s+FOR\\s+([^\\s\\)]+)");

    private static final Set<String> NUMERIC_TYPES = Set.of(
            "int", "bigint", "smallint", "tinyint", "decimal", "numeric",
            "float", "real", "bit", "money", "smallmoney", "integer"
    );

    @Override
    public Result<TableDesign> readTableDesign(DatabaseConnection connection, String catalog, String schema, String table) {
        if (connection == null) {
            return Result.failure(AppError.validation("NULL_CONNECTION", "Database connection cannot be null"));
        }
        if (table == null || table.isBlank()) {
            return Result.failure(AppError.validation("EMPTY_TABLE_NAME", "Table name cannot be empty"));
        }

        String cleanSchema = (schema == null || schema.isBlank()) ? "dbo" : schema.trim().replaceAll("[\\[\\]]", "");
        String cleanTable = table.trim().replaceAll("[\\[\\]]", "");

        try {
            Connection raw = connection.getRawJdbcConnection();
            if (catalog != null && !catalog.isBlank()) {
                try {
                    raw.setCatalog(catalog.trim());
                } catch (Exception ex) {
                    log.debug("Could not switch catalog to {}: {}", catalog, ex.getMessage());
                }
            }

            // 1. Resolve table object_id and flags
            String objSql = """
                SELECT o.object_id,
                       OBJECTPROPERTY(o.object_id, 'TableHasPrimaryKey') AS has_pk,
                       OBJECTPROPERTY(o.object_id, 'TableHasUniqueCnst') AS has_uq,
                       OBJECTPROPERTY(o.object_id, 'TableHasForeignKey') AS has_fk,
                       OBJECTPROPERTY(o.object_id, 'TableHasCheckCnst') AS has_chk
                FROM sys.tables o
                JOIN sys.schemas s ON s.schema_id = o.schema_id
                WHERE (s.name = ? OR LOWER(s.name) = LOWER(?))
                  AND (o.name = ? OR LOWER(o.name) = LOWER(?))
            """;

            int objectId = 0;
            boolean hasPk = true;
            boolean hasUq = true;
            boolean hasFk = true;
            boolean hasChk = true;

            try (PreparedStatement ps = raw.prepareStatement(objSql)) {
                ps.setString(1, cleanSchema);
                ps.setString(2, cleanSchema);
                ps.setString(3, cleanTable);
                ps.setString(4, cleanTable);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        objectId = rs.getInt(1);
                        hasPk = rs.getInt(2) == 1;
                        hasUq = rs.getInt(3) == 1;
                        hasFk = rs.getInt(4) == 1;
                        hasChk = rs.getInt(5) == 1;
                    } else {
                        return Result.failure(AppError.resourceNotFound("TABLE_NOT_FOUND",
                                "Table not found: " + cleanSchema + "." + cleanTable));
                    }
                }
            }

            TableDesign design = new TableDesign("sqlserver", cleanSchema, cleanTable);
            design.setOriginalTable(cleanTable);
            design.setDatabase(catalog);

            // 2. Table Comment
            String commentSql = """
                SELECT CAST(value AS NVARCHAR(MAX))
                FROM sys.extended_properties
                WHERE major_id = ? AND minor_id = 0 AND name = 'MS_Description'
            """;
            try (PreparedStatement ps = raw.prepareStatement(commentSql)) {
                ps.setInt(1, objectId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        design.setComment(rs.getString(1));
                    }
                }
            } catch (Exception e) {
                log.debug("Error reading table description: {}", e.getMessage());
            }

            // 3. Columns
            String colSql = """
                SELECT 
                    c.column_id,
                    c.name,
                    TYPE_NAME(c.system_type_id) AS type_name,
                    c.max_length,
                    c.precision,
                    c.scale,
                    c.is_nullable,
                    c.is_identity,
                    CAST(ISNULL(ic.seed_value, 1) AS NUMERIC(38, 0)) AS seed_val,
                    CAST(ISNULL(ic.increment_value, 1) AS NUMERIC(38, 0)) AS inc_val,
                    CAST(OBJECT_DEFINITION(c.default_object_id) AS NVARCHAR(MAX)) AS default_val,
                    c.collation_name,
                    CASE WHEN pk_cols.column_id IS NOT NULL THEN 1 ELSE 0 END AS is_pk,
                    CAST(ep.value AS NVARCHAR(MAX)) AS description,
                    t.is_user_defined,
                    SCHEMA_NAME(t.schema_id) AS type_schema,
                    t.name AS type_raw_name,
                    c.is_computed,
                    pk_cols.pk_name,
                    OBJECT_NAME(c.default_object_id) AS default_constraint_name,
                    CAST(cc.definition AS NVARCHAR(MAX)) AS computed_definition,
                    cc.is_persisted
                FROM sys.columns c
                JOIN sys.types t ON t.user_type_id = c.user_type_id
                LEFT JOIN sys.computed_columns cc
                    ON cc.object_id = c.object_id AND cc.column_id = c.column_id
                LEFT JOIN sys.identity_columns ic 
                    ON ic.object_id = c.object_id AND ic.column_id = c.column_id
                LEFT JOIN (
                    SELECT ic.object_id, ic.column_id, kc.name AS pk_name
                    FROM sys.key_constraints kc
                    JOIN sys.index_columns ic 
                        ON ic.object_id = kc.parent_object_id AND ic.index_id = kc.unique_index_id
                    WHERE kc.parent_object_id = ? AND kc.type = 'PK'
                ) pk_cols ON pk_cols.object_id = c.object_id AND pk_cols.column_id = c.column_id
                LEFT JOIN sys.extended_properties ep 
                    ON ep.major_id = c.object_id AND ep.minor_id = c.column_id AND ep.name = 'MS_Description'
                WHERE c.object_id = ?
                ORDER BY c.column_id
            """;

            try (PreparedStatement ps = raw.prepareStatement(colSql)) {
                ps.setInt(1, objectId);
                ps.setInt(2, objectId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String colName = rs.getString("name");
                        boolean isUdt = rs.getBoolean("is_user_defined");
                        String typeSchema = rs.getString("type_schema");
                        String typeRawName = rs.getString("type_raw_name");
                        String sysTypeName = rs.getString("type_name");

                        String typeName;
                        if (isUdt && typeRawName != null && !typeRawName.isBlank()) {
                            typeName = "[" + typeSchema + "].[" + typeRawName + "]";
                        } else {
                            typeName = sysTypeName != null ? sysTypeName.toLowerCase() : "varchar";
                        }

                        int rawLen = rs.getInt("max_length");
                        int prec = rs.getInt("precision");
                        int scale = rs.getInt("scale");
                        boolean isNullable = rs.getBoolean("is_nullable");
                        boolean isIdentity = rs.getBoolean("is_identity");
                        long seedVal = rs.getLong("seed_val");
                        long incVal = rs.getLong("inc_val");
                        String rawDefault = rs.getString("default_val");
                        String collation = rs.getString("collation_name");
                        boolean isPk = rs.getInt("is_pk") == 1;
                        String colComment = rs.getString("description");
                        boolean isComputed = rs.getBoolean("is_computed");
                        String defConstraintName = rs.getString("default_constraint_name");
                        String computedDef = rs.getString("computed_definition");
                        boolean isPersisted = rs.getBoolean("is_persisted");

                        Integer length = null;
                        Integer colScale = null;

                        if (!isUdt) {
                            if ("nvarchar".equalsIgnoreCase(typeName) || "nchar".equalsIgnoreCase(typeName)) {
                                length = (rawLen == -1) ? -1 : (rawLen / 2);
                            } else if ("varchar".equalsIgnoreCase(typeName) || "char".equalsIgnoreCase(typeName)
                                    || "varbinary".equalsIgnoreCase(typeName) || "binary".equalsIgnoreCase(typeName)) {
                                length = (rawLen == -1) ? -1 : rawLen;
                            } else if ("decimal".equalsIgnoreCase(typeName) || "numeric".equalsIgnoreCase(typeName)) {
                                length = prec > 0 ? prec : null;
                                colScale = scale;
                            } else if ("time".equalsIgnoreCase(typeName) || "datetime2".equalsIgnoreCase(typeName)) {
                                colScale = scale;
                            }
                        }

                        // Clean default value: strip outer parentheses
                        String defVal = rawDefault != null ? rawDefault.trim() : null;
                        if (defVal != null) {
                            while (defVal.startsWith("(") && defVal.endsWith(")") && defVal.length() >= 2) {
                                defVal = defVal.substring(1, defVal.length() - 1).trim();
                            }
                            if (NUMERIC_TYPES.contains(typeName.toLowerCase())) {
                                while ((defVal.startsWith("'") && defVal.endsWith("'") && defVal.length() >= 2)
                                        || (defVal.startsWith("\"") && defVal.endsWith("\"") && defVal.length() >= 2)) {
                                    defVal = defVal.substring(1, defVal.length() - 1).trim();
                                }
                            }
                        }

                        // Sequence name
                        String seqName = null;
                        if (rawDefault != null) {
                            Matcher m = NEXT_VAL_REGEX.matcher(rawDefault);
                            if (m.find()) {
                                seqName = m.group(1).replaceAll("[\\[\\]]", "").trim();
                            }
                        }

                        ColumnDesign col = ColumnDesign.loadedFromDb(colName, typeName);
                        col.setLength(length);
                        col.setScale(colScale);
                        col.setNotNull(!isNullable);
                        col.setPrimaryKey(isPk);
                        col.setDefaultValue(defVal);
                        col.setDefaultConstraintName(defConstraintName);
                        col.setCollation(collation);
                        col.setIdentity(isIdentity);
                        col.setIdentitySeed(seedVal != 0 ? seedVal : 1L);
                        col.setIdentityIncrement(incVal != 0 ? incVal : 1L);
                        col.setSequenceName(seqName);
                        col.setComputedFormula(computedDef);
                        col.setComputedPersisted(isPersisted);
                        col.setComment(colComment);

                        design.addColumn(col);
                    }
                }
            }

            // 4. Indexes
            String idxSql = """
                SELECT 
                    i.name,
                    i.type_desc,
                    i.is_unique,
                    COL_NAME(ic.object_id, ic.column_id) AS col_name,
                    ic.is_descending_key
                FROM sys.indexes i
                JOIN sys.index_columns ic 
                    ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                WHERE i.object_id = ? AND i.type > 0 AND i.is_primary_key = 0 AND i.is_unique_constraint = 0
                ORDER BY i.index_id, ic.key_ordinal
            """;

            Map<String, IndexDesign> indexMap = new LinkedHashMap<>();
            try (PreparedStatement ps = raw.prepareStatement(idxSql)) {
                ps.setInt(1, objectId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String iname = rs.getString("name");
                        if (iname == null || iname.isBlank()) continue;

                        String typeDesc = rs.getString("type_desc");
                        boolean isUnique = rs.getBoolean("is_unique");
                        String colName = rs.getString("col_name");
                        boolean isDesc = rs.getBoolean("is_descending_key");

                        IndexDesign idx = indexMap.computeIfAbsent(iname, k -> {
                            IndexDesign newIdx = new IndexDesign(k);
                            newIdx.setUnique(isUnique);
                            newIdx.setIndexType("CLUSTERED".equalsIgnoreCase(typeDesc) ? IndexType.CLUSTERED : IndexType.NONCLUSTERED);
                            return newIdx;
                        });

                        idx.getColumns().add(new IndexColumn(colName, isDesc));
                    }
                }
            }
            design.setIndexes(new ArrayList<>(indexMap.values()));

            // 5. Foreign Keys
            if (hasFk) {
                String fkSql = """
                    SELECT 
                        fk.name AS fk_name,
                        COL_NAME(fkc.parent_object_id, fkc.parent_column_id) AS local_col,
                        OBJECT_SCHEMA_NAME(fkc.referenced_object_id) AS ref_schema,
                        OBJECT_NAME(fkc.referenced_object_id) AS ref_table,
                        COL_NAME(fkc.referenced_object_id, fkc.referenced_column_id) AS ref_col,
                        fk.delete_referential_action_desc AS on_delete,
                        fk.update_referential_action_desc AS on_update
                    FROM sys.foreign_keys fk
                    JOIN sys.foreign_key_columns fkc 
                        ON fkc.constraint_object_id = fk.object_id
                    WHERE fk.parent_object_id = ?
                    ORDER BY fk.name, fkc.constraint_column_id
                """;

                Map<String, ForeignKeyDesign> fkMap = new LinkedHashMap<>();
                try (PreparedStatement ps = raw.prepareStatement(fkSql)) {
                    ps.setInt(1, objectId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String fkName = rs.getString("fk_name");
                            String localCol = rs.getString("local_col");
                            String refSchema = rs.getString("ref_schema");
                            String refTable = rs.getString("ref_table");
                            String refCol = rs.getString("ref_col");
                            String onDeleteStr = rs.getString("on_delete");
                            String onUpdateStr = rs.getString("on_update");

                            ForeignKeyDesign fk = fkMap.computeIfAbsent(fkName, k -> {
                                ForeignKeyDesign newFk = new ForeignKeyDesign(k);
                                newFk.setTargetSchema(refSchema != null ? refSchema : "dbo");
                                newFk.setTargetTable(refTable != null ? refTable : "");
                                newFk.setOnDelete(ForeignKeyAction.fromString(onDeleteStr));
                                newFk.setOnUpdate(ForeignKeyAction.fromString(onUpdateStr));
                                return newFk;
                            });

                            fk.getColumnMappings().add(new ForeignKeyColumnMapping(localCol, refCol));
                        }
                    }
                }
                design.setForeignKeys(new ArrayList<>(fkMap.values()));
            }

            // 6. Unique Constraints
            if (hasUq) {
                String uqSql = """
                    SELECT 
                        kc.name AS constraint_name,
                        i.type_desc AS index_type,
                        COL_NAME(ic.object_id, ic.column_id) AS column_name
                    FROM sys.key_constraints kc
                    JOIN sys.indexes i 
                        ON i.object_id = kc.parent_object_id AND i.index_id = kc.unique_index_id
                    JOIN sys.index_columns ic 
                        ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                    WHERE kc.parent_object_id = ? AND kc.type = 'UQ'
                    ORDER BY kc.name, ic.key_ordinal
                """;

                Map<String, UniqueDesign> uqMap = new LinkedHashMap<>();
                try (PreparedStatement ps = raw.prepareStatement(uqSql)) {
                    ps.setInt(1, objectId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String uqName = rs.getString("constraint_name");
                            String indexType = rs.getString("index_type");
                            String colName = rs.getString("column_name");

                            UniqueDesign uq = uqMap.computeIfAbsent(uqName, k -> {
                                UniqueDesign newUq = new UniqueDesign(k);
                                newUq.setClustered("CLUSTERED".equalsIgnoreCase(indexType));
                                return newUq;
                            });

                            if (colName != null && !colName.isBlank()) {
                                uq.getColumns().add(colName);
                            }
                        }
                    }
                }
                design.setUniques(new ArrayList<>(uqMap.values()));
            }

            // 7. Check Constraints
            if (hasChk) {
                String chkSql = """
                    SELECT 
                        cc.name,
                        CAST(cc.definition AS NVARCHAR(MAX)) AS definition
                    FROM sys.check_constraints cc
                    WHERE cc.parent_object_id = ?
                    ORDER BY cc.name
                """;

                List<CheckDesign> checks = new ArrayList<>();
                try (PreparedStatement ps = raw.prepareStatement(chkSql)) {
                    ps.setInt(1, objectId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String chkName = rs.getString("name");
                            String rawClause = rs.getString("definition");

                            String clause = rawClause != null ? rawClause.trim() : "";
                            while (clause.startsWith("(") && clause.endsWith(")") && clause.length() >= 2) {
                                clause = clause.substring(1, clause.length() - 1).trim();
                            }

                            checks.add(new CheckDesign(chkName, clause));
                        }
                    }
                }
                design.setChecks(checks);
            }

            // 8. Triggers
            String trigSql = """
                SELECT 
                    tr.name,
                    tr.is_instead_of_trigger,
                    OBJECTPROPERTY(tr.object_id, 'ExecIsInsertTrigger') AS is_insert,
                    OBJECTPROPERTY(tr.object_id, 'ExecIsUpdateTrigger') AS is_update,
                    OBJECTPROPERTY(tr.object_id, 'ExecIsDeleteTrigger') AS is_delete,
                    CAST(sm.definition AS NVARCHAR(MAX)) AS definition
                FROM sys.triggers tr
                LEFT JOIN sys.sql_modules sm ON sm.object_id = tr.object_id
                WHERE tr.parent_id = ?
                ORDER BY tr.name
            """;

            List<TriggerDesign> triggers = new ArrayList<>();
            try (PreparedStatement ps = raw.prepareStatement(trigSql)) {
                ps.setInt(1, objectId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String trigName = rs.getString("name");
                        boolean isInsteadOf = rs.getBoolean("is_instead_of_trigger");
                        boolean isInsert = rs.getInt("is_insert") == 1;
                        boolean isUpdate = rs.getInt("is_update") == 1;
                        boolean isDelete = rs.getInt("is_delete") == 1;
                        String definition = rs.getString("definition");

                        TriggerDesign tr = new TriggerDesign(trigName, definition != null ? definition : "");
                        tr.setTiming(isInsteadOf ? "INSTEAD OF" : "AFTER");
                        List<String> events = new ArrayList<>();
                        if (isInsert) events.add("INSERT");
                        if (isUpdate) events.add("UPDATE");
                        if (isDelete) events.add("DELETE");
                        tr.setEvents(events);

                        triggers.add(tr);
                    }
                }
            }
            design.setTriggers(triggers);

            return Result.success(design);
        } catch (Exception e) {
            log.error("Failed to read table design for {}.{}: {}", cleanSchema, cleanTable, e.getMessage(), e);
            return Result.failure(AppError.system("Failed to read table design: " + e.getMessage(), e));
        }
    }
}

