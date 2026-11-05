package app.framework.metadata;

import com.lltool.infrastructure.database.api.metadata.ColumnMetadata;
import com.lltool.infrastructure.database.api.metadata.TableMetadata;

import java.util.List;
import java.util.stream.Collectors;

/**
 * SQL Statement Generator for Database Explorer context actions (Phase 6.3).
 */
public final class SqlScriptGenerator {

    private SqlScriptGenerator() {}

    public static String generateSelect(String dialect, TableMetadata table, List<ColumnMetadata> columns) {
        String colList = (columns != null && !columns.isEmpty())
                ? columns.stream().map(c -> quoteIdentifier(dialect, c.name())).collect(Collectors.joining(", "))
                : "*";
        String tblRef = formatTableRef(dialect, table);

        if (dialect != null && dialect.toUpperCase().contains("SQLSERVER")) {
            return String.format("SELECT TOP (100) %s\nFROM %s;", colList, tblRef);
        }
        return String.format("SELECT %s\nFROM %s\nLIMIT 100;", colList, tblRef);
    }

    public static String generateInsert(String dialect, TableMetadata table, List<ColumnMetadata> columns) {
        String tblRef = formatTableRef(dialect, table);
        if (columns == null || columns.isEmpty()) {
            return String.format("INSERT INTO %s DEFAULT VALUES;", tblRef);
        }
        var nonGenerated = columns.stream().filter(c -> !c.primaryKey()).toList();
        var targetCols = nonGenerated.isEmpty() ? columns : nonGenerated;
        String colNames = targetCols.stream().map(c -> quoteIdentifier(dialect, c.name())).collect(Collectors.joining(", "));
        String placeholders = targetCols.stream().map(c -> "?").collect(Collectors.joining(", "));

        return String.format("INSERT INTO %s (%s)\nVALUES (%s);", tblRef, colNames, placeholders);
    }

    public static String generateUpdate(String dialect, TableMetadata table, List<ColumnMetadata> columns) {
        String tblRef = formatTableRef(dialect, table);
        if (columns == null || columns.isEmpty()) {
            return String.format("UPDATE %s\nSET column_name = ?\nWHERE id = ?;", tblRef);
        }
        var setClauses = columns.stream()
                .filter(c -> !c.primaryKey())
                .map(c -> quoteIdentifier(dialect, c.name()) + " = ?")
                .collect(Collectors.joining(",\n    "));

        var pk = columns.stream().filter(ColumnMetadata::primaryKey).findFirst();
        String whereClause = pk.map(c -> quoteIdentifier(dialect, c.name()) + " = ?").orElse("/* condition */");

        return String.format("UPDATE %s\nSET %s\nWHERE %s;", tblRef, setClauses.isEmpty() ? "column = ?" : setClauses, whereClause);
    }

    public static String generateDelete(String dialect, TableMetadata table, List<ColumnMetadata> columns) {
        String tblRef = formatTableRef(dialect, table);
        if (columns != null) {
            var pk = columns.stream().filter(ColumnMetadata::primaryKey).findFirst();
            if (pk.isPresent()) {
                return String.format("DELETE FROM %s\nWHERE %s = ?;", tblRef, quoteIdentifier(dialect, pk.get().name()));
            }
        }
        return String.format("DELETE FROM %s\nWHERE /* condition */;", tblRef);
    }

    public static String generateCount(String dialect, TableMetadata table) {
        String tblRef = formatTableRef(dialect, table);
        return String.format("SELECT COUNT(*) AS total_count FROM %s;", tblRef);
    }

    public static String generateDropTable(String dialect, TableMetadata table) {
        String tblRef = formatTableRef(dialect, table);
        return String.format("DROP TABLE %s;", tblRef);
    }

    public static String generateRoutineCall(String dialect, String schema, String routineName, boolean isProcedure) {
        String qualified = (schema != null && !schema.isBlank() && !"main".equalsIgnoreCase(schema))
                ? quoteIdentifier(dialect, schema) + "." + quoteIdentifier(dialect, routineName)
                : quoteIdentifier(dialect, routineName);

        if (dialect != null && dialect.toUpperCase().contains("SQLSERVER")) {
            return String.format("EXEC %s;\nGO", qualified);
        } else if (dialect != null && dialect.toUpperCase().contains("POSTGRE")) {
            if (isProcedure) {
                return String.format("CALL %s();", qualified);
            } else {
                return String.format("SELECT %s();", qualified);
            }
        }
        return String.format("SELECT %s();", qualified);
    }

    public static String generateDdl(String dialect, TableMetadata table, List<ColumnMetadata> columns) {
        String tblRef = formatTableRef(dialect, table);
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("CREATE TABLE %s (\n", tblRef));
        if (columns != null && !columns.isEmpty()) {
            for (int i = 0; i < columns.size(); i++) {
                ColumnMetadata col = columns.get(i);
                sb.append("    ").append(quoteIdentifier(dialect, col.name())).append(" ").append(col.dataType());
                if (col.columnSize() > 0 && !col.dataType().toUpperCase().contains("INT")) {
                    sb.append("(").append(col.columnSize()).append(")");
                }
                if (col.primaryKey()) {
                    sb.append(" PRIMARY KEY");
                } else if (!col.nullable()) {
                    sb.append(" NOT NULL");
                }
                if (i < columns.size() - 1) {
                    sb.append(",");
                }
                sb.append("\n");
            }
        } else {
            sb.append("    /* columns */\n");
        }
        sb.append(");");
        return sb.toString();
    }

    private static String formatTableRef(String dialect, TableMetadata table) {
        if (table == null) return "table_name";
        boolean hasSchema = table.schema() != null && !table.schema().isBlank() && !"main".equalsIgnoreCase(table.schema());
        if (hasSchema) {
            return quoteIdentifier(dialect, table.schema()) + "." + quoteIdentifier(dialect, table.name());
        }
        return quoteIdentifier(dialect, table.name());
    }

    private static String quoteIdentifier(String dialect, String id) {
        if (id == null) return "";
        if (dialect != null && dialect.toUpperCase().contains("SQLSERVER")) {
            return "[" + id + "]";
        }
        return "\"" + id + "\"";
    }
}
