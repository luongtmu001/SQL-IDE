package com.lltool.core.application.tableeditor;

import com.lltool.core.domain.tableeditor.model.DataChangeBatch;
import com.lltool.core.domain.tableeditor.model.DataRowChange;
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

    public List<String> buildDmlStatements(
            DataChangeBatch batch,
            TableEditorMetadata metadata,
            boolean optimistic) {

        List<String> statements = new ArrayList<>();
        if (batch == null || batch.isEmpty()) {
            return statements;
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
                colNames.add(quoteIdent(col, isPg));
                valLiterals.add(formatLiteral(entry.getValue()));
            }

            if (!colNames.isEmpty()) {
                statements.add("INSERT INTO " + qualifiedTable + " (" +
                        String.join(", ", colNames) + ") VALUES (" +
                        String.join(", ", valLiterals) + ");");
            } else {
                statements.add("INSERT INTO " + qualifiedTable + " DEFAULT VALUES;");
            }
        }

        if (hasIdentityInsert && !isPg) {
            statements.add(0, "SET IDENTITY_INSERT " + qualifiedTable + " ON;");
            statements.add("SET IDENTITY_INSERT " + qualifiedTable + " OFF;");
        }

        // 2. UPDATES
        for (DataRowChange update : batch.getUpdates()) {
            Map<String, Object> newVals = update.getNewValues();
            Map<String, Object> oldVals = update.getOldValues();
            Map<String, Object> pkVals = update.getPkValues();

            List<String> setClauses = new ArrayList<>();
            for (Map.Entry<String, Object> entry : newVals.entrySet()) {
                String col = entry.getKey();
                if (computedCols.contains(col.toLowerCase())) continue;
                setClauses.add(quoteIdent(col, isPg) + " = " + formatLiteral(entry.getValue()));
            }

            if (setClauses.isEmpty()) continue;

            List<String> whereClauses = new ArrayList<>();
            // PK match
            if (!pkVals.isEmpty()) {
                for (Map.Entry<String, Object> pk : pkVals.entrySet()) {
                    whereClauses.add(quoteIdent(pk.getKey(), isPg) + " = " + formatLiteral(pk.getValue()));
                }
            }

            // Optimistic concurrency on modified columns
            if (optimistic && oldVals != null) {
                for (Map.Entry<String, Object> entry : oldVals.entrySet()) {
                    String col = entry.getKey();
                    if (pkVals.containsKey(col)) continue;
                    Object oldVal = entry.getValue();
                    if (oldVal == null) {
                        whereClauses.add(quoteIdent(col, isPg) + " IS NULL");
                    } else {
                        whereClauses.add(quoteIdent(col, isPg) + " = " + formatLiteral(oldVal));
                    }
                }
            }

            if (!whereClauses.isEmpty()) {
                statements.add("UPDATE " + qualifiedTable + " SET " +
                        String.join(", ", setClauses) +
                        " WHERE " + String.join(" AND ", whereClauses) + ";");
            }
        }

        // 3. DELETES
        for (DataRowChange del : batch.getDeletes()) {
            Map<String, Object> pkVals = del.getPkValues();
            Map<String, Object> oldVals = del.getOldValues();

            List<String> whereClauses = new ArrayList<>();
            if (pkVals != null && !pkVals.isEmpty()) {
                for (Map.Entry<String, Object> pk : pkVals.entrySet()) {
                    whereClauses.add(quoteIdent(pk.getKey(), isPg) + " = " + formatLiteral(pk.getValue()));
                }
            } else if (oldVals != null && !oldVals.isEmpty()) {
                for (Map.Entry<String, Object> old : oldVals.entrySet()) {
                    Object val = old.getValue();
                    if (val == null) {
                        whereClauses.add(quoteIdent(old.getKey(), isPg) + " IS NULL");
                    } else {
                        whereClauses.add(quoteIdent(old.getKey(), isPg) + " = " + formatLiteral(val));
                    }
                }
            }

            if (!whereClauses.isEmpty()) {
                statements.add("DELETE FROM " + qualifiedTable + " WHERE " +
                        String.join(" AND ", whereClauses) + ";");
            }
        }

        return statements;
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

