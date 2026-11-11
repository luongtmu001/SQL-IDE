package com.lltool.core.application.tabledesigner.dialect;

import com.lltool.core.domain.tabledesigner.model.CheckDesign;
import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.model.ForeignKeyColumnMapping;
import com.lltool.core.domain.tabledesigner.model.ForeignKeyDesign;
import com.lltool.core.domain.tabledesigner.model.IndexColumn;
import com.lltool.core.domain.tabledesigner.model.IndexDesign;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.core.domain.tabledesigner.model.TriggerDesign;
import com.lltool.core.domain.tabledesigner.model.UniqueDesign;
import com.lltool.core.domain.tabledesigner.valueobject.IndexType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Microsoft SQL Server dialect implementation for DDL generation.
 */
public class SqlServerDdlDialect implements DdlDialect {

    @Override
    public String engine() {
        return "sqlserver";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null) return "[]";
        String clean = identifier.trim();
        if (clean.startsWith("[") && clean.endsWith("]") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return "[" + clean.replace("]", "]]") + "]";
    }

    @Override
    public String formatTableName(String schema, String table) {
        String s = schema != null && !schema.isBlank() ? schema.trim() : "dbo";
        String t = table != null ? table.trim() : "";
        return quoteIdentifier(s) + "." + quoteIdentifier(t);
    }

    @Override
    public String formatColumnType(ColumnDesign column) {
        if (column == null) return "varchar";
        String raw = column.getDataType() != null ? column.getDataType().trim() : "varchar";
        String t = raw.toLowerCase();
        Integer len = column.getLength();
        Integer scale = column.getScale();

        if (t.equals("varchar") || t.equals("nvarchar") || t.equals("char") ||
                t.equals("nchar") || t.equals("varbinary") || t.equals("binary")) {
            if (len != null) {
                if (len <= 0 || len == Integer.MAX_VALUE) {
                    return t + "(max)";
                }
                return t + "(" + len + ")";
            }
            return t;
        }

        if (t.equals("decimal") || t.equals("numeric")) {
            if (len != null && scale != null && scale > 0) {
                return t + "(" + len + ", " + scale + ")";
            } else if (len != null) {
                return t + "(" + len + ")";
            }
            return t;
        }

        if ((t.equals("datetime2") || t.equals("time")) && scale != null) {
            return t + "(" + scale + ")";
        }

        return raw;
    }

    @Override
    public String formatDefaultValue(String defaultValue, String dataType, boolean isExpression) {
        if (defaultValue == null || defaultValue.isBlank()) {
            return "";
        }
        String val = defaultValue.trim();
        if ("NULL".equalsIgnoreCase(val)) {
            return "NULL";
        }
        if (isExpression || (val.startsWith("(") && val.endsWith(")"))) {
            return val;
        }

        String t = dataType != null ? dataType.trim().toLowerCase() : "";
        if (t.equals("bit")) {
            String clean = val.replace("'", "").replace("\"", "").replace("(", "").replace(")", "").trim().toLowerCase();
            if (clean.equals("1") || clean.equals("true")) {
                return "((1))";
            } else if (clean.equals("0") || clean.equals("false")) {
                return "((0))";
            }
            return "((" + val + "))";
        }

        boolean isNumeric = t.equals("int") || t.equals("bigint") || t.equals("smallint") ||
                t.equals("tinyint") || t.equals("decimal") || t.equals("numeric") ||
                t.equals("float") || t.equals("real") || t.equals("money") || t.equals("smallmoney");
        if (isNumeric) {
            String cleaned = val.replace("'", "").replace("\"", "").replace("(", "").replace(")", "").trim();
            return cleaned.isEmpty() ? "0" : cleaned;
        }

        boolean isChar = t.equals("varchar") || t.equals("nvarchar") || t.equals("char") ||
                t.equals("nchar") || t.equals("text") || t.equals("ntext");
        if (isChar) {
            if ((val.startsWith("'") && val.endsWith("'")) || (val.startsWith("N'") && val.endsWith("'"))) {
                return val;
            }
            return "'" + escapeLiteral(val) + "'";
        }

        return val;
    }

    @Override
    public String formatColumnDefinition(ColumnDesign column, boolean isCreate) {
        if (column == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(quoteIdentifier(column.getName())).append(" ");

        if (column.isComputed()) {
            sb.append("AS (").append(column.getComputedFormula()).append(")");
            if (column.isComputedPersisted()) {
                sb.append(" PERSISTED");
            }
            if (column.isNotNull()) {
                sb.append(" NOT NULL");
            }
            return sb.toString();
        }

        sb.append(formatColumnType(column));

        if (column.getCollation() != null && !column.getCollation().isBlank()) {
            sb.append(" COLLATE ").append(column.getCollation().trim());
        }

        if (column.isIdentity()) {
            long seed = column.getIdentitySeed() != null ? column.getIdentitySeed() : 1L;
            long inc = column.getIdentityIncrement() != null ? column.getIdentityIncrement() : 1L;
            sb.append(" IDENTITY(").append(seed).append(",").append(inc).append(")");
        }

        if (column.getDefaultValue() != null && !column.getDefaultValue().isBlank()) {
            String formattedDef = formatDefaultValue(column.getDefaultValue(), column.getDataType(), column.isDefaultIsExpression());
            if (!formattedDef.isBlank()) {
                if (column.getDefaultConstraintName() != null && !column.getDefaultConstraintName().isBlank()) {
                    sb.append(" CONSTRAINT ").append(quoteIdentifier(column.getDefaultConstraintName()));
                }
                sb.append(" DEFAULT ").append(formattedDef);
            }
        }

        if (column.isNotNull()) {
            sb.append(" NOT NULL");
        } else {
            sb.append(" NULL");
        }

        return sb.toString();
    }

    @Override
    public String generateCreateTable(TableDesign design) {
        if (design == null) return "";
        StringBuilder sb = new StringBuilder();
        String fullTableName = formatTableName(design.getSchema(), design.getTable());

        sb.append("CREATE TABLE ").append(fullTableName).append(" (\n");

        List<String> items = new ArrayList<>();
        for (ColumnDesign col : design.getColumns()) {
            items.add("    " + formatColumnDefinition(col, true));
        }

        // Primary Key constraint
        List<String> pkCols = design.getPrimaryKeyColumnNames();
        if (!pkCols.isEmpty()) {
            String pkName = "PK_" + design.getTable();
            String pkColsJoined = pkCols.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
            items.add("    CONSTRAINT " + quoteIdentifier(pkName) + " PRIMARY KEY CLUSTERED (" + pkColsJoined + ")");
        }

        // Unique constraints
        for (UniqueDesign uq : design.getUniques()) {
            String uqName = uq.getName() != null && !uq.getName().isBlank() ? uq.getName() : "UQ_" + design.getTable() + "_" + String.join("_", uq.getColumns());
            String cols = uq.getColumns().stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
            String clusterType = uq.isClustered() ? "CLUSTERED " : "";
            items.add("    CONSTRAINT " + quoteIdentifier(uqName) + " UNIQUE " + clusterType + "(" + cols + ")");
        }

        // Check constraints
        for (CheckDesign ck : design.getChecks()) {
            String ckName = ck.getName() != null && !ck.getName().isBlank() ? ck.getName() : "CK_" + design.getTable();
            String clause = ck.getCheckClause() != null ? ck.getCheckClause().trim() : "";
            if (!clause.startsWith("(") || !clause.endsWith(")")) {
                clause = "(" + clause + ")";
            }
            items.add("    CONSTRAINT " + quoteIdentifier(ckName) + " CHECK " + clause);
        }

        // Foreign Key constraints
        for (ForeignKeyDesign fk : design.getForeignKeys()) {
            String fkName = fk.getName() != null && !fk.getName().isBlank() ? fk.getName() : "FK_" + design.getTable() + "_" + fk.getTargetTable();
            String srcCols = fk.getColumnMappings().stream().map(m -> quoteIdentifier(m.getSourceColumn())).collect(Collectors.joining(", "));
            String tgtCols = fk.getColumnMappings().stream().map(m -> quoteIdentifier(m.getTargetColumn())).collect(Collectors.joining(", "));
            String refTable = formatTableName(fk.getTargetSchema(), fk.getTargetTable());

            StringBuilder fkSb = new StringBuilder();
            fkSb.append("    CONSTRAINT ").append(quoteIdentifier(fkName))
                    .append(" FOREIGN KEY (").append(srcCols).append(")")
                    .append(" REFERENCES ").append(refTable).append(" (").append(tgtCols).append(")");
            if (fk.getOnDelete() != null && !fk.getOnDelete().sqlKeyword().equalsIgnoreCase("NO ACTION")) {
                fkSb.append(" ON DELETE ").append(fk.getOnDelete().sqlKeyword());
            }
            if (fk.getOnUpdate() != null && !fk.getOnUpdate().sqlKeyword().equalsIgnoreCase("NO ACTION")) {
                fkSb.append(" ON UPDATE ").append(fk.getOnUpdate().sqlKeyword());
            }
            items.add(fkSb.toString());
        }

        sb.append(String.join(",\n", items));
        sb.append("\n);\n");

        // Indexes
        for (IndexDesign idx : design.getIndexes()) {
            String idxName = idx.getName();
            String unique = idx.isUnique() ? "UNIQUE " : "";
            String clusterType = idx.isClustered() ? "CLUSTERED " : "NONCLUSTERED ";
            String cols = idx.getColumns().stream()
                    .map(c -> quoteIdentifier(c.getColumnName()) + (c.isDescending() ? " DESC" : ""))
                    .collect(Collectors.joining(", "));

            sb.append("\nCREATE ").append(unique).append(clusterType).append("INDEX ")
                    .append(quoteIdentifier(idxName)).append(" ON ").append(fullTableName)
                    .append(" (").append(cols).append(")");
            if (idx.getFilterPredicate() != null && !idx.getFilterPredicate().isBlank()) {
                sb.append(" WHERE ").append(idx.getFilterPredicate());
            }
            sb.append(";");
        }

        // Table comment
        if (design.getComment() != null && !design.getComment().isBlank()) {
            sb.append("\n\n").append(generateTableComment(design.getSchema(), design.getTable(), design.getComment(), false));
        }

        // Column comments
        for (ColumnDesign col : design.getColumns()) {
            if (col.getComment() != null && !col.getComment().isBlank()) {
                sb.append("\n\n").append(generateColumnComment(design.getSchema(), design.getTable(), col.getName(), col.getComment(), false));
            }
        }

        // Triggers
        for (TriggerDesign tr : design.getTriggers()) {
            if (tr.getDefinition() != null && !tr.getDefinition().isBlank()) {
                sb.append("\n\nGO\n").append(tr.getDefinition().trim()).append("\nGO");
            }
        }

        return sb.toString().trim();
    }

    @Override
    public String generateTableComment(String schema, String table, String comment, boolean isDiff) {
        String s = schema != null && !schema.isBlank() ? schema.trim() : "dbo";
        String t = table != null ? table.trim() : "";
        String cleanSchema = unquote(s);
        String cleanTable = unquote(t);
        String val = escapeLiteral(comment != null ? comment.trim() : "");

        if (isDiff) {
            return "IF NOT EXISTS (SELECT 1 FROM sys.extended_properties " +
                    "WHERE major_id = OBJECT_ID(N'" + cleanSchema + "." + cleanTable + "') AND minor_id = 0 AND name = N'MS_Description')\n" +
                    "    EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'" + val + "', " +
                    "@level0type = N'SCHEMA', @level0name = N'" + cleanSchema + "', @level1type = N'TABLE', @level1name = N'" + cleanTable + "';\n" +
                    "ELSE\n" +
                    "    EXEC sys.sp_updateextendedproperty @name = N'MS_Description', @value = N'" + val + "', " +
                    "@level0type = N'SCHEMA', @level0name = N'" + cleanSchema + "', @level1type = N'TABLE', @level1name = N'" + cleanTable + "';";
        } else {
            return "EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'" + val + "', " +
                    "@level0type = N'SCHEMA', @level0name = N'" + cleanSchema + "', @level1type = N'TABLE', @level1name = N'" + cleanTable + "';";
        }
    }

    @Override
    public String generateColumnComment(String schema, String table, String column, String comment, boolean isDiff) {
        String s = schema != null && !schema.isBlank() ? schema.trim() : "dbo";
        String t = table != null ? table.trim() : "";
        String c = column != null ? column.trim() : "";
        String cleanSchema = unquote(s);
        String cleanTable = unquote(t);
        String cleanCol = unquote(c);
        String val = escapeLiteral(comment != null ? comment.trim() : "");

        if (isDiff) {
            return "IF NOT EXISTS (SELECT 1 FROM sys.extended_properties ep " +
                    "JOIN sys.columns c ON ep.major_id = c.object_id AND ep.minor_id = c.column_id " +
                    "WHERE ep.major_id = OBJECT_ID(N'" + cleanSchema + "." + cleanTable + "') AND c.name = N'" + cleanCol + "' AND ep.name = N'MS_Description')\n" +
                    "    EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'" + val + "', " +
                    "@level0type = N'SCHEMA', @level0name = N'" + cleanSchema + "', @level1type = N'TABLE', @level1name = N'" + cleanTable + "', " +
                    "@level2type = N'COLUMN', @level2name = N'" + cleanCol + "';\n" +
                    "ELSE\n" +
                    "    EXEC sys.sp_updateextendedproperty @name = N'MS_Description', @value = N'" + val + "', " +
                    "@level0type = N'SCHEMA', @level0name = N'" + cleanSchema + "', @level1type = N'TABLE', @level1name = N'" + cleanTable + "', " +
                    "@level2type = N'COLUMN', @level2name = N'" + cleanCol + "';";
        } else {
            return "EXEC sys.sp_addextendedproperty @name = N'MS_Description', @value = N'" + val + "', " +
                    "@level0type = N'SCHEMA', @level0name = N'" + cleanSchema + "', @level1type = N'TABLE', @level1name = N'" + cleanTable + "', " +
                    "@level2type = N'COLUMN', @level2name = N'" + cleanCol + "';";
        }
    }

    @Override
    public String escapeLiteral(String literal) {
        if (literal == null) return "";
        return literal.replace("'", "''");
    }

    private String unquote(String identifier) {
        if (identifier == null) return "";
        String clean = identifier.trim();
        if (clean.startsWith("[") && clean.endsWith("]")) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return clean.replace("]]", "]");
    }
}

