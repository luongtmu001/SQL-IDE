package com.lltool.core.application.tableeditor;

import com.lltool.core.domain.tableeditor.model.DataChangeBatch;
import com.lltool.core.domain.tableeditor.model.DataRowChange;
import com.lltool.core.domain.tableeditor.model.DmlCommand;
import com.lltool.core.domain.tableeditor.model.TableEditorMetadata;

import java.util.*;

/**
 * Service generating queries, DML statements, and managing transactions for Table Data Editor.
 */
public class TableDataEditorService {

    public String buildSelectQuery(
            String engine,
            String schema,
            String table,
            List<String> selectCols,
            String whereClause,
            String orderBy,
            int limit,
            int offset) {

        boolean isPg = "postgresql".equalsIgnoreCase(engine) || "postgres".equalsIgnoreCase(engine);
        String qualifiedTable = quoteQualified(schema, table, isPg);

        String cols = (selectCols == null || selectCols.isEmpty())
                ? "*"
                : String.join(", ", selectCols.stream().map(c -> quoteIdent(c, isPg)).toList());

        StringBuilder sb = new StringBuilder();

        if (!isPg && limit > 0 && offset <= 0) {
            sb.append("SELECT TOP ").append(limit).append(" ").append(cols);
        } else {
            sb.append("SELECT ").append(cols);
        }

        sb.append(" FROM ").append(qualifiedTable);

        if (whereClause != null && !whereClause.isBlank()) {
            String trimmed = whereClause.trim();
            if (!trimmed.toUpperCase().startsWith("WHERE")) {
                sb.append(" WHERE ");
            } else {
                sb.append(" ");
            }
            sb.append(trimmed);
        }

        if (orderBy != null && !orderBy.isBlank()) {
            String trimmed = orderBy.trim();
            if (!trimmed.toUpperCase().startsWith("ORDER BY")) {
                sb.append(" ORDER BY ");
            } else {
                sb.append(" ");
            }
            sb.append(trimmed);
        }

        if (isPg) {
            if (limit > 0) {
                sb.append(" LIMIT ").append(limit);
            }
            if (offset > 0) {
                sb.append(" OFFSET ").append(offset);
            }
        } else if (offset > 0) {
            if (orderBy == null || orderBy.isBlank()) {
                sb.append(" ORDER BY (SELECT NULL)");
            }
            sb.append(" OFFSET ").append(offset).append(" ROWS");
            if (limit > 0) {
                sb.append(" FETCH NEXT ").append(limit).append(" ROWS ONLY");
            }
        }

        return sb.toString();
    }

    public List<DmlCommand> buildDmlCommands(
            DataChangeBatch batch,
            TableEditorMetadata metadata,
            boolean optimistic) {

        List<DmlCommand> commands = new ArrayList<>();
        if (batch == null || batch.isEmpty()) {
            return commands;
        }

        boolean isPg = "postgresql".equalsIgnoreCase(batch.getEngine()) || "postgres".equalsIgnoreCase(batch.getEngine());
        String qualifiedTable = quoteQualified(batch.getSchema(), batch.getTable(), isPg);

        Set<String> computedCols = metadata != null ? metadata.getComputedNames() : Collections.emptySet();
        Set<String> identityCols = metadata != null ? metadata.getIdentityNames() : Collections.emptySet();

        boolean hasIdentityInsert = false;

        // 1. INSERTS
        for (DataRowChange insert : batch.getInserts()) {
            Map<String, Object> values = insert.getNewValues();
            List<String> colNames = new ArrayList<>();
            List<String> valLiterals = new ArrayList<>();
            List<DmlCommand.DmlParam> params = new ArrayList<>();
            List<String> placeholders = new ArrayList<>();

            for (Map.Entry<String, Object> entry : values.entrySet()) {
                String col = entry.getKey();
                if (computedCols.contains(col.toLowerCase())) {
                    continue; // Skip computed columns on INSERT
                }
                if (identityCols.contains(col.toLowerCase()) && entry.getValue() == null) {
                    continue; // Skip identity column if null
                }
                if (!isPg && identityCols.contains(col.toLowerCase()) && entry.getValue() != null) {
                    hasIdentityInsert = true;
                }
                TableEditorMetadata.ColumnMeta colMeta = metadata != null ? metadata.findColumn(col).orElse(null) : null;
                int sqlType = colMeta != null ? colMeta.sqlType() : java.sql.Types.OTHER;

                colNames.add(quoteIdent(col, isPg));
                placeholders.add("?");
                params.add(new DmlCommand.DmlParam(entry.getValue(), sqlType));
                valLiterals.add(formatDialectLiteral(entry.getValue(), colMeta, isPg));
            }

            if (!colNames.isEmpty()) {
                String sql = "INSERT INTO " + qualifiedTable + " (" +
                        String.join(", ", colNames) + ") VALUES (" +
                        String.join(", ", placeholders) + ");";
                String previewSql = "INSERT INTO " + qualifiedTable + " (" +
                        String.join(", ", colNames) + ") VALUES (" +
                        String.join(", ", valLiterals) + ");";
                commands.add(new DmlCommand(sql, params, previewSql, DataRowChange.Type.INSERT, insert.getRowId()));
            } else {
                String defaultSql = "INSERT INTO " + qualifiedTable + " DEFAULT VALUES;";
                commands.add(new DmlCommand(defaultSql, Collections.emptyList(), defaultSql, DataRowChange.Type.INSERT, insert.getRowId()));
            }
        }

        if (hasIdentityInsert && !isPg) {
            commands.add(0, new DmlCommand("SET IDENTITY_INSERT " + qualifiedTable + " ON;", Collections.emptyList(), "SET IDENTITY_INSERT " + qualifiedTable + " ON;"));
            commands.add(new DmlCommand("SET IDENTITY_INSERT " + qualifiedTable + " OFF;", Collections.emptyList(), "SET IDENTITY_INSERT " + qualifiedTable + " OFF;"));
        }

        // 2. UPDATES
        for (DataRowChange update : batch.getUpdates()) {
            Map<String, Object> newVals = update.getNewValues();
            Map<String, Object> oldVals = update.getOldValues();
            Map<String, Object> pkVals = update.getPkValues();

            List<String> setClauses = new ArrayList<>();
            List<String> setPreview = new ArrayList<>();
            List<DmlCommand.DmlParam> params = new ArrayList<>();

            for (Map.Entry<String, Object> entry : newVals.entrySet()) {
                String col = entry.getKey();
                if (computedCols.contains(col.toLowerCase())) continue;
                TableEditorMetadata.ColumnMeta colMeta = metadata != null ? metadata.findColumn(col).orElse(null) : null;
                int sqlType = colMeta != null ? colMeta.sqlType() : java.sql.Types.OTHER;

                setClauses.add(quoteIdent(col, isPg) + " = ?");
                setPreview.add(quoteIdent(col, isPg) + " = " + formatDialectLiteral(entry.getValue(), colMeta, isPg));
                params.add(new DmlCommand.DmlParam(entry.getValue(), sqlType));
            }

            if (setClauses.isEmpty()) continue;

            List<String> whereClauses = new ArrayList<>();
            List<String> wherePreview = new ArrayList<>();

            // PK match
            if (!pkVals.isEmpty()) {
                for (Map.Entry<String, Object> pk : pkVals.entrySet()) {
                    TableEditorMetadata.ColumnMeta colMeta = metadata != null ? metadata.findColumn(pk.getKey()).orElse(null) : null;
                    int sqlType = colMeta != null ? colMeta.sqlType() : java.sql.Types.OTHER;

                    whereClauses.add(quoteIdent(pk.getKey(), isPg) + " = ?");
                    wherePreview.add(quoteIdent(pk.getKey(), isPg) + " = " + formatDialectLiteral(pk.getValue(), colMeta, isPg));
                    params.add(new DmlCommand.DmlParam(pk.getValue(), sqlType));
                }

                // DE-4: Optimistic concurrency on modified columns only (excluding PK and non-comparable columns)
                if (optimistic && oldVals != null) {
                    for (String col : newVals.keySet()) {
                        if (pkVals.containsKey(col)) continue;
                        TableEditorMetadata.ColumnMeta colMeta = metadata != null ? metadata.findColumn(col).orElse(null) : null;
                        if (!isComparable(colMeta)) continue; // exclude LOB / XML / spatial / float
                        Object oldVal = oldVals.get(col);
                        if (oldVal == null) {
                            whereClauses.add(quoteIdent(col, isPg) + " IS NULL");
                            wherePreview.add(quoteIdent(col, isPg) + " IS NULL");
                        } else {
                            whereClauses.add(quoteIdent(col, isPg) + " = ?");
                            wherePreview.add(quoteIdent(col, isPg) + " = " + formatDialectLiteral(oldVal, colMeta, isPg));
                            params.add(new DmlCommand.DmlParam(oldVal, colMeta != null ? colMeta.sqlType() : java.sql.Types.OTHER));
                        }
                    }
                }
            } else if (oldVals != null && !oldVals.isEmpty()) {
                // Table without PK: match all comparable columns
                for (Map.Entry<String, Object> old : oldVals.entrySet()) {
                    String col = old.getKey();
                    TableEditorMetadata.ColumnMeta colMeta = metadata != null ? metadata.findColumn(col).orElse(null) : null;
                    if (!isComparable(colMeta)) continue; // exclude LOB / XML / spatial / float
                    Object oldVal = old.getValue();
                    if (oldVal == null) {
                        whereClauses.add(quoteIdent(col, isPg) + " IS NULL");
                        wherePreview.add(quoteIdent(col, isPg) + " IS NULL");
                    } else {
                        whereClauses.add(quoteIdent(col, isPg) + " = ?");
                        wherePreview.add(quoteIdent(col, isPg) + " = " + formatDialectLiteral(oldVal, colMeta, isPg));
                        params.add(new DmlCommand.DmlParam(oldVal, colMeta != null ? colMeta.sqlType() : java.sql.Types.OTHER));
                    }
                }
            }

            if (!whereClauses.isEmpty()) {
                String sql = "UPDATE " + qualifiedTable + " SET " +
                        String.join(", ", setClauses) +
                        " WHERE " + String.join(" AND ", whereClauses) + ";";
                String previewSql = "UPDATE " + qualifiedTable + " SET " +
                        String.join(", ", setPreview) +
                        " WHERE " + String.join(" AND ", wherePreview) + ";";
                commands.add(new DmlCommand(sql, params, previewSql, DataRowChange.Type.UPDATE, update.getRowId()));
            }
        }

        // 3. DELETES
        for (DataRowChange del : batch.getDeletes()) {
            Map<String, Object> pkVals = del.getPkValues();
            Map<String, Object> oldVals = del.getOldValues();

            List<String> whereClauses = new ArrayList<>();
            List<String> wherePreview = new ArrayList<>();
            List<DmlCommand.DmlParam> params = new ArrayList<>();

            if (pkVals != null && !pkVals.isEmpty()) {
                for (Map.Entry<String, Object> pk : pkVals.entrySet()) {
                    TableEditorMetadata.ColumnMeta colMeta = metadata != null ? metadata.findColumn(pk.getKey()).orElse(null) : null;
                    int sqlType = colMeta != null ? colMeta.sqlType() : java.sql.Types.OTHER;

                    whereClauses.add(quoteIdent(pk.getKey(), isPg) + " = ?");
                    wherePreview.add(quoteIdent(pk.getKey(), isPg) + " = " + formatDialectLiteral(pk.getValue(), colMeta, isPg));
                    params.add(new DmlCommand.DmlParam(pk.getValue(), sqlType));
                }
            } else if (oldVals != null && !oldVals.isEmpty()) {
                for (Map.Entry<String, Object> old : oldVals.entrySet()) {
                    String col = old.getKey();
                    TableEditorMetadata.ColumnMeta colMeta = metadata != null ? metadata.findColumn(col).orElse(null) : null;
                    if (!isComparable(colMeta)) continue;
                    Object val = old.getValue();
                    if (val == null) {
                        whereClauses.add(quoteIdent(col, isPg) + " IS NULL");
                        wherePreview.add(quoteIdent(col, isPg) + " IS NULL");
                    } else {
                        whereClauses.add(quoteIdent(col, isPg) + " = ?");
                        wherePreview.add(quoteIdent(col, isPg) + " = " + formatDialectLiteral(val, colMeta, isPg));
                        params.add(new DmlCommand.DmlParam(val, colMeta != null ? colMeta.sqlType() : java.sql.Types.OTHER));
                    }
                }
            }

            if (!whereClauses.isEmpty()) {
                String sql = "DELETE FROM " + qualifiedTable + " WHERE " +
                        String.join(" AND ", whereClauses) + ";";
                String previewSql = "DELETE FROM " + qualifiedTable + " WHERE " +
                        String.join(" AND ", wherePreview) + ";";
                commands.add(new DmlCommand(sql, params, previewSql, DataRowChange.Type.DELETE, del.getRowId()));
            }
        }

        return commands;
    }

    public List<String> buildDmlStatements(
            DataChangeBatch batch,
            TableEditorMetadata metadata,
            boolean optimistic) {
        return buildDmlCommands(batch, metadata, optimistic).stream()
                .map(DmlCommand::previewSql)
                .toList();
    }

    public static boolean isComparable(TableEditorMetadata.ColumnMeta col) {
        if (col == null) return true;
        int t = col.sqlType();
        if (t == java.sql.Types.BLOB || t == java.sql.Types.CLOB || t == java.sql.Types.NCLOB ||
            t == java.sql.Types.LONGVARBINARY || t == java.sql.Types.LONGVARCHAR || t == java.sql.Types.LONGNVARCHAR ||
            t == java.sql.Types.SQLXML || t == java.sql.Types.FLOAT || t == java.sql.Types.DOUBLE ||
            t == java.sql.Types.REAL || t == java.sql.Types.ARRAY || t == java.sql.Types.STRUCT) {
            return false;
        }
        String typeName = col.dataType() != null ? col.dataType().toLowerCase() : "";
        if (typeName.contains("text") || typeName.contains("image") || typeName.contains("xml") ||
            typeName.contains("geography") || typeName.contains("geometry") ||
            typeName.contains("float") || typeName.contains("real") || typeName.contains("double") ||
            typeName.contains("json") || typeName.contains("blob") || typeName.contains("clob")) {
            return false;
        }
        return true;
    }

    public static String formatDialectLiteral(Object value, TableEditorMetadata.ColumnMeta colMeta, boolean isPg) {
        if (value == null) return "NULL";
        if (value instanceof Boolean b) {
            if (isPg) return b ? "TRUE" : "FALSE";
            return b ? "1" : "0";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            if (isPg) {
                sb.append("E'\\\\x");
                for (byte b : bytes) sb.append(String.format("%02x", b));
                sb.append("'::bytea");
            } else {
                sb.append("0x");
                for (byte b : bytes) sb.append(String.format("%02X", b));
            }
            return sb.toString();
        }

        String str = value.toString();
        String escaped = str.replace("'", "''");

        if (!isPg) {
            boolean isNchar = false;
            if (colMeta != null) {
                int st = colMeta.sqlType();
                String dt = colMeta.dataType() != null ? colMeta.dataType().toLowerCase() : "";
                if (st == java.sql.Types.NVARCHAR || st == java.sql.Types.NCHAR || st == java.sql.Types.LONGNVARCHAR
                        || dt.contains("nvarchar") || dt.contains("nchar") || dt.contains("ntext")) {
                    isNchar = true;
                }
            }
            if (isNchar) {
                return "N'" + escaped + "'";
            }
        }
        return "'" + escaped + "'";
    }

    public static String quoteIdent(String name, boolean isPostgres) {
        if (name == null) return "";
        if (isPostgres) {
            return "\"" + name.replace("\"", "\"\"") + "\"";
        }
        return "[" + name.replace("]", "]]") + "]";
    }

    public static String quoteQualified(String schema, String table, boolean isPostgres) {
        String s = schema != null && !schema.isBlank() ? schema : (isPostgres ? "public" : "dbo");
        String t = table != null ? table : "";
        return quoteIdent(s, isPostgres) + "." + quoteIdent(t, isPostgres);
    }

    public static String formatLiteral(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        String str = String.valueOf(value);
        return "'" + str.replace("'", "''") + "'";
    }
}

