package com.lltool.infrastructure.database.postgresql;

import com.lltool.core.common.error.AppError;
import com.lltool.core.common.result.Result;
import com.lltool.core.domain.tabledesigner.model.*;
import com.lltool.core.domain.tabledesigner.valueobject.ForeignKeyAction;
import com.lltool.core.domain.tabledesigner.valueobject.IndexType;
import com.lltool.infrastructure.database.api.DatabaseConnection;
import com.lltool.infrastructure.database.api.metadata.TableDesignReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

/**
 * High-fidelity TableDesign reader for PostgreSQL.
 * Faithfully ports metadata queries from Python postgresql.py:1568.
 */
public class PostgresTableDesignReader implements TableDesignReader {

    private static final Logger log = LoggerFactory.getLogger(PostgresTableDesignReader.class);

    private static final Set<String> NUMERIC_TYPES = Set.of(
            "int", "integer", "bigint", "smallint", "numeric", "decimal", "real", "double precision"
    );

    @Override
    public Result<TableDesign> readTableDesign(DatabaseConnection connection, String catalog, String schema, String table) {
        if (connection == null) {
            return Result.failure(AppError.validation("NULL_CONNECTION", "Database connection cannot be null"));
        }
        if (table == null || table.isBlank()) {
            return Result.failure(AppError.validation("EMPTY_TABLE_NAME", "Table name cannot be empty"));
        }

        String cleanSchema = (schema == null || schema.isBlank()) ? "public" : schema.trim().replaceAll("[\"']", "");
        String cleanTable = table.trim().replaceAll("[\"']", "");

        try {
            Connection raw = connection.getRawJdbcConnection();

            // 1. Check if table exists
            String checkSql = """
                SELECT 1 FROM information_schema.tables 
                WHERE table_schema = ? AND table_name = ?
            """;
            try (PreparedStatement ps = raw.prepareStatement(checkSql)) {
                ps.setString(1, cleanSchema);
                ps.setString(2, cleanTable);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Result.failure(AppError.resourceNotFound("TABLE_NOT_FOUND",
                                "Table not found: " + cleanSchema + "." + cleanTable));
                    }
                }
            }

            TableDesign design = new TableDesign("postgresql", cleanSchema, cleanTable);
            design.setOriginalTable(cleanTable);
            design.setDatabase(catalog);

            // 2. Table comment
            String commentSql = """
                SELECT obj_description(c_tbl.oid, 'pg_class') AS table_comment
                FROM pg_class c_tbl
                JOIN pg_namespace n_tbl ON n_tbl.oid = c_tbl.relnamespace
                WHERE n_tbl.nspname = ? AND c_tbl.relname = ?
            """;
            try (PreparedStatement ps = raw.prepareStatement(commentSql)) {
                ps.setString(1, cleanSchema);
                ps.setString(2, cleanTable);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        design.setComment(rs.getString("table_comment"));
                    }
                }
            } catch (Exception e) {
                log.debug("Error reading table description: {}", e.getMessage());
            }

            // 3. Columns
            String colSql = """
                SELECT 
                    c.ordinal_position,
                    c.column_name,
                    c.data_type,
                    c.udt_name,
                    c.character_maximum_length,
                    c.numeric_precision,
                    c.numeric_scale,
                    c.is_nullable,
                    c.is_identity,
                    c.column_default,
                    c.collation_name,
                    CASE WHEN pk_cols.attname IS NOT NULL THEN 1 ELSE 0 END AS is_pk,
                    col_description(c_tbl.oid, c.ordinal_position) AS description,
                    pk_cols.pk_name,
                    c.generation_expression,
                    c.is_generated
                FROM information_schema.columns c
                JOIN pg_class c_tbl ON c_tbl.relname = c.table_name
                JOIN pg_namespace n_tbl ON n_tbl.oid = c_tbl.relnamespace AND n_tbl.nspname = c.table_schema
                LEFT JOIN (
                    SELECT a.attname, c_con.conname AS pk_name
                    FROM pg_index i
                    JOIN pg_class c_pk ON c_pk.oid = i.indrelid
                    JOIN pg_namespace n_pk ON n_pk.oid = c_pk.relnamespace
                    JOIN pg_attribute a ON a.attrelid = c_pk.oid AND a.attnum = ANY(i.indkey)
                    LEFT JOIN pg_constraint c_con ON c_con.conindid = i.indexrelid AND c_con.contype = 'p'
                    WHERE n_pk.nspname = ? AND c_pk.relname = ? AND i.indisprimary
                ) pk_cols ON pk_cols.attname = c.column_name
                WHERE c.table_schema = ? AND c.table_name = ?
                ORDER BY c.ordinal_position
            """;

            try (PreparedStatement ps = raw.prepareStatement(colSql)) {
                ps.setString(1, cleanSchema);
                ps.setString(2, cleanTable);
                ps.setString(3, cleanSchema);
                ps.setString(4, cleanTable);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String colName = rs.getString("column_name");
                        String rawType = rs.getString("data_type");
                        String udtName = rs.getString("udt_name");
                        int charLen = rs.getInt("character_maximum_length");
                        boolean charLenNull = rs.wasNull();
                        int numPrec = rs.getInt("numeric_precision");
                        boolean numPrecNull = rs.wasNull();
                        int numScale = rs.getInt("numeric_scale");
                        boolean numScaleNull = rs.wasNull();
                        boolean isNullable = "YES".equalsIgnoreCase(rs.getString("is_nullable"));
                        String rawDefault = rs.getString("column_default");
                        String collation = rs.getString("collation_name");
                        boolean isPk = rs.getInt("is_pk") == 1;
                        String colComment = rs.getString("description");
                        String genExpr = rs.getString("generation_expression");
                        String isGen = rs.getString("is_generated");

                        String normType = (rawType != null ? rawType.toLowerCase() : "");
                        if ("character varying".equals(normType)) {
                            normType = "varchar";
                        } else if ("character".equals(normType)) {
                            normType = "char";
                        } else if ("timestamp without time zone".equals(normType)) {
                            normType = "timestamp";
                        } else if ("timestamp with time zone".equals(normType)) {
                            normType = "timestamptz";
                        } else if ("time without time zone".equals(normType)) {
                            normType = "time";
                        } else if ("time with time zone".equals(normType)) {
                            normType = "timetz";
                        } else if ("user-defined".equals(normType) && udtName != null) {
                            normType = udtName.toLowerCase();
                        }

                        Integer length = null;
                        if (!charLenNull && charLen > 0) {
                            length = charLen;
                        } else if (!numPrecNull && "numeric".equals(normType)) {
                            length = numPrec;
                        }

                        Integer scale = null;
                        if (!numScaleNull && ("numeric".equals(normType) || "timestamp".equals(normType)
                                || "timestamptz".equals(normType) || "time".equals(normType) || "timetz".equals(normType))) {
                            scale = numScale;
                        }

                        String defVal = rawDefault != null ? rawDefault.trim() : null;
                        boolean isIdentity = "YES".equalsIgnoreCase(rs.getString("is_identity"))
                                || (defVal != null && defVal.contains("nextval("));

                        if (isIdentity && defVal != null && defVal.contains("nextval(")) {
                            if ("integer".equals(normType) || "int".equals(normType)) {
                                normType = "serial";
                            } else if ("bigint".equals(normType)) {
                                normType = "bigserial";
                            } else if ("smallint".equals(normType)) {
                                normType = "smallserial";
                            }
                            defVal = null; // Serial implies auto-increment default
                        } else if (defVal != null) {
                            // Strip Postgres cast e.g. "'-1'::integer" -> "-1", "0::numeric" -> "0"
                            if (defVal.contains("::")) {
                                defVal = defVal.split("::")[0].trim();
                            }
                            if (NUMERIC_TYPES.contains(normType)) {
                                while ((defVal.startsWith("'") && defVal.endsWith("'") && defVal.length() >= 2)
                                        || (defVal.startsWith("\"") && defVal.endsWith("\"") && defVal.length() >= 2)) {
                                    defVal = defVal.substring(1, defVal.length() - 1).trim();
                                }
                            }
                        }

                        boolean isComputed = "ALWAYS".equalsIgnoreCase(isGen) || (genExpr != null && !genExpr.isBlank());

                        ColumnDesign col = ColumnDesign.loadedFromDb(colName, normType);
                        col.setLength(length);
                        col.setScale(scale);
                        col.setNotNull(!isNullable);
                        col.setPrimaryKey(isPk);
                        col.setDefaultValue(defVal);
                        col.setCollation(collation);
                        col.setIdentity(isIdentity);
                        col.setComputedFormula(genExpr);
                        col.setComment(colComment);

                        design.addColumn(col);
                    }
                }
            }

            // 4. Indexes
            String idxSql = """
                SELECT 
                    i.relname AS index_name,
                    am.amname AS index_type,
                    ix.indisunique AS is_unique,
                    ARRAY(
                        SELECT a.attname
                        FROM pg_attribute a
                        WHERE a.attrelid = t.oid AND a.attnum = ANY(ix.indkey)
                    ) AS col_names
                FROM pg_index ix
                JOIN pg_class t ON t.oid = ix.indrelid
                JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN pg_class i ON i.oid = ix.indexrelid
                JOIN pg_am am ON am.oid = i.relam
                WHERE n.nspname = ? AND t.relname = ? 
                  AND NOT ix.indisprimary
                  AND NOT EXISTS (
                      SELECT 1 FROM pg_constraint c 
                      WHERE c.conindid = ix.indexrelid AND c.contype = 'u'
                  )
                ORDER BY i.relname
            """;

            List<IndexDesign> indexes = new ArrayList<>();
            try (PreparedStatement ps = raw.prepareStatement(idxSql)) {
                ps.setString(1, cleanSchema);
                ps.setString(2, cleanTable);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String iname = rs.getString("index_name");
                        String amType = rs.getString("index_type");
                        boolean isUnique = rs.getBoolean("is_unique");

                        List<String> colNames = new ArrayList<>();
                        try {
                            Array sqlArray = rs.getArray("col_names");
                            if (sqlArray != null) {
                                Object arrayObj = sqlArray.getArray();
                                if (arrayObj instanceof String[] arr) {
                                    colNames.addAll(Arrays.asList(arr));
                                } else if (arrayObj instanceof Object[] arr) {
                                    for (Object o : arr) {
                                        if (o != null) colNames.add(o.toString());
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            String rawArr = rs.getString("col_names");
                            if (rawArr != null) {
                                String clean = rawArr.replaceAll("[{}\"]", "");
                                for (String c : clean.split(",")) {
                                    if (!c.isBlank()) colNames.add(c.trim());
                                }
                            }
                        }

                        IndexDesign idx = new IndexDesign(iname);
                        idx.setUnique(isUnique);
                        idx.setIndexType(IndexType.fromString(amType, "postgresql")); // Fix B3: defaults to btree

                        for (String col : colNames) {
                            idx.getColumns().add(new IndexColumn(col, false));
                        }
                        indexes.add(idx);
                    }
                }
            }
            design.setIndexes(indexes);

            // 5. Foreign Keys
            String fkSql = """
                SELECT
                    tc.constraint_name,
                    kcu.column_name,
                    ccu.table_schema AS ref_schema,
                    ccu.table_name AS ref_table,
                    ccu.column_name AS ref_column,
                    rc.delete_rule,
                    rc.update_rule
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                    ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
                JOIN information_schema.constraint_column_usage ccu
                    ON ccu.constraint_name = tc.constraint_name AND ccu.table_schema = tc.table_schema
                JOIN information_schema.referential_constraints rc
                    ON rc.constraint_name = tc.constraint_name AND rc.constraint_schema = tc.table_schema
                WHERE tc.constraint_type = 'FOREIGN KEY'
                    AND tc.table_schema = ? AND tc.table_name = ?
                ORDER BY tc.constraint_name, kcu.ordinal_position
            """;

            Map<String, ForeignKeyDesign> fkMap = new LinkedHashMap<>();
            try (PreparedStatement ps = raw.prepareStatement(fkSql)) {
                ps.setString(1, cleanSchema);
                ps.setString(2, cleanTable);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String fkName = rs.getString("constraint_name");
                        String colName = rs.getString("column_name");
                        String refSchema = rs.getString("ref_schema");
                        String refTable = rs.getString("ref_table");
                        String refCol = rs.getString("ref_column");
                        String delRule = rs.getString("delete_rule");
                        String updRule = rs.getString("update_rule");

                        ForeignKeyDesign fk = fkMap.computeIfAbsent(fkName, k -> {
                            ForeignKeyDesign newFk = new ForeignKeyDesign(k);
                            newFk.setTargetSchema(refSchema != null ? refSchema : "public");
                            newFk.setTargetTable(refTable != null ? refTable : "");
                            newFk.setOnDelete(ForeignKeyAction.fromString(delRule));
                            newFk.setOnUpdate(ForeignKeyAction.fromString(updRule));
                            return newFk;
                        });

                        fk.getColumnMappings().add(new ForeignKeyColumnMapping(colName, refCol));
                    }
                }
            }
            design.setForeignKeys(new ArrayList<>(fkMap.values()));

            // 6. Unique Constraints
            String uqSql = """
                SELECT
                    tc.constraint_name,
                    kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                    ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema
                WHERE tc.constraint_type = 'UNIQUE'
                    AND tc.table_schema = ? AND tc.table_name = ?
                ORDER BY tc.constraint_name, kcu.ordinal_position
            """;

            Map<String, UniqueDesign> uqMap = new LinkedHashMap<>();
            try (PreparedStatement ps = raw.prepareStatement(uqSql)) {
                ps.setString(1, cleanSchema);
                ps.setString(2, cleanTable);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String uqName = rs.getString("constraint_name");
                        String colName = rs.getString("column_name");

                        UniqueDesign uq = uqMap.computeIfAbsent(uqName, UniqueDesign::new);
                        if (colName != null && !colName.isBlank()) {
                            uq.getColumns().add(colName);
                        }
                    }
                }
            }
            design.setUniques(new ArrayList<>(uqMap.values()));

            // 7. Check Constraints
            String chkSql = """
                SELECT
                    tc.constraint_name,
                    cc.check_clause
                FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc
                    ON cc.constraint_name = tc.constraint_name AND cc.constraint_schema = tc.table_schema
                WHERE tc.constraint_type = 'CHECK'
                    AND tc.table_schema = ? AND tc.table_name = ?
                    AND tc.constraint_name NOT LIKE '%_not_null'
                ORDER BY tc.constraint_name
            """;

            List<CheckDesign> checks = new ArrayList<>();
            try (PreparedStatement ps = raw.prepareStatement(chkSql)) {
                ps.setString(1, cleanSchema);
                ps.setString(2, cleanTable);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String chkName = rs.getString("constraint_name");
                        String clause = rs.getString("check_clause");
                        checks.add(new CheckDesign(chkName, clause != null ? clause : ""));
                    }
                }
            }
            design.setChecks(checks);

            // 8. Triggers
            String trigSql = """
                SELECT 
                    tr.trigger_name,
                    tr.action_timing,
                    tr.event_manipulation,
                    pg_get_triggerdef(pg_t.oid, true) as trigger_def
                FROM information_schema.triggers tr
                JOIN pg_class pg_c ON pg_c.relname = tr.event_object_table
                JOIN pg_namespace pg_n ON pg_n.oid = pg_c.relnamespace AND pg_n.nspname = tr.event_object_schema
                JOIN pg_trigger pg_t ON pg_t.tgrelid = pg_c.oid AND pg_t.tgname = tr.trigger_name
                WHERE tr.event_object_schema = ? AND tr.event_object_table = ?
                ORDER BY tr.trigger_name
            """;

            Map<String, TriggerDesign> trigMap = new LinkedHashMap<>();
            try (PreparedStatement ps = raw.prepareStatement(trigSql)) {
                ps.setString(1, cleanSchema);
                ps.setString(2, cleanTable);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String tname = rs.getString("trigger_name");
                        String timing = rs.getString("action_timing");
                        String event = rs.getString("event_manipulation");
                        String def = rs.getString("trigger_def");

                        TriggerDesign tr = trigMap.computeIfAbsent(tname, k -> {
                            TriggerDesign newTr = new TriggerDesign(k, def != null ? def : "");
                            newTr.setTiming(timing != null ? timing.toUpperCase() : "AFTER");
                            return newTr;
                        });

                        if (event != null && !tr.getEvents().contains(event.toUpperCase())) {
                            tr.getEvents().add(event.toUpperCase());
                        }
                    }
                }
            }
            design.setTriggers(new ArrayList<>(trigMap.values()));

            return Result.success(design);
        } catch (Exception e) {
            log.error("Failed to read table design for {}.{}: {}", cleanSchema, cleanTable, e.getMessage(), e);
            return Result.failure(AppError.system("Failed to read table design: " + e.getMessage(), e));
        }
    }
}

