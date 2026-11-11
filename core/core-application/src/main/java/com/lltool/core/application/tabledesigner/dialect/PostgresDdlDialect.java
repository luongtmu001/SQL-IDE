package com.lltool.core.application.tabledesigner.dialect;

import com.lltool.core.domain.tabledesigner.model.CheckDesign;
import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
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
 * PostgreSQL dialect implementation for DDL generation.
 */
public class PostgresDdlDialect implements DdlDialect {

    @Override
    public String engine() {
        return "postgresql";
    }

    @Override
    public String quoteIdentifier(String identifier) {
        if (identifier == null) return "\"\"";
        String clean = identifier.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1);
        }
        return "\"" + clean.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String formatTableName(String schema, String table) {
        String s = schema != null && !schema.isBlank() ? schema.trim() : "public";
        String t = table != null ? table.trim() : "";
        return quoteIdentifier(s) + "." + quoteIdentifier(t);
    }

    @Override
    public String formatColumnType(ColumnDesign column) {
        if (column == null) return "text";
        String raw = column.getDataType() != null ? column.getDataType().trim() : "text";
        String t = raw.toLowerCase();
        Integer len = column.getLength();
        Integer scale = column.getScale();

        if (t.equals("serial") || t.equals("bigserial") || t.equals("smallserial") ||
                t.equals("boolean") || t.equals("bytea") || t.equals("uuid") ||
                t.equals("json") || t.equals("jsonb") || t.equals("text")) {
            return t;
        }

        if (t.equals("varchar") || t.equals("char")) {
            if (len != null && len > 0) {
                return t + "(" + len + ")";
            }
            return t;
        }

        if (t.equals("numeric") || t.equals("decimal")) {
            if (len != null && scale != null && scale > 0) {
                return t + "(" + len + ", " + scale + ")";
            } else if (len != null) {
                return t + "(" + len + ")";
            }
            return t;
        }

        if ((t.equals("timestamp") || t.equals("timestamptz") || t.equals("time") || t.equals("timetz")) && scale != null) {
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
        if (t.equals("boolean") || t.equals("bool")) {
            String clean = val.replace("'", "").replace("\"", "").trim().toLowerCase();
            if (clean.equals("1") || clean.equals("true") || clean.equals("t")) {
                return "true";
            } else if (clean.equals("0") || clean.equals("false") || clean.equals("f")) {
                return "false";
            }
            return clean;
        }

        boolean isNumeric = t.equals("int") || t.equals("integer") || t.equals("bigint") ||
                t.equals("smallint") || t.equals("numeric") || t.equals("decimal") ||
                t.equals("real") || t.equals("double precision");
        if (isNumeric) {
            String cleaned = val.replace("'", "").replace("\"", "").replace("(", "").replace(")", "").trim();
            if (cleaned.contains("::")) {
                cleaned = cleaned.split("::")[0].trim();
            }
            return cleaned.isEmpty() ? "0" : cleaned;
        }

        boolean isChar = t.equals("varchar") || t.equals("char") || t.equals("text") || t.equals("uuid");
        if (isChar) {
            if (val.startsWith("'") && val.endsWith("'")) {
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
        sb.append(formatColumnType(column));

        if (column.getCollation() != null && !column.getCollation().isBlank()) {
            sb.append(" COLLATE ").append(quoteIdentifier(column.getCollation().trim()));
        }

        if (column.getDefaultValue() != null && !column.getDefaultValue().isBlank()) {
            String formattedDef = formatDefaultValue(column.getDefaultValue(), column.getDataType(), column.isDefaultIsExpression());
            if (!formattedDef.isBlank()) {
                sb.append(" DEFAULT ").append(formattedDef);
            }
        }

        if (column.isNotNull()) {
            sb.append(" NOT NULL");
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
            items.add("    CONSTRAINT " + quoteIdentifier(pkName) + " PRIMARY KEY (" + pkColsJoined + ")");
        }

        // Unique constraints
        for (UniqueDesign uq : design.getUniques()) {
            String uqName = uq.getName() != null && !uq.getName().isBlank() ? uq.getName() : "UQ_" + design.getTable() + "_" + String.join("_", uq.getColumns());
            String cols = uq.getColumns().stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
            items.add("    CONSTRAINT " + quoteIdentifier(uqName) + " UNIQUE (" + cols + ")");
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

        // Indexes (Fix B3: Using dialect-aware indexType like btree, hash, gin, gist, brin)
        for (IndexDesign idx : design.getIndexes()) {
            String idxName = idx.getName();
            String unique = idx.isUnique() ? "UNIQUE " : "";
            IndexType it = idx.getIndexType() != null ? idx.getIndexType() : IndexType.BTREE;
            if (it == IndexType.NONCLUSTERED || it == IndexType.CLUSTERED) {
                it = IndexType.BTREE; // normalize to PostgreSQL standard
            }
            String cols = idx.getColumns().stream()
                    .map(c -> quoteIdentifier(c.getColumnName()) + (c.isDescending() ? " DESC" : ""))
                    .collect(Collectors.joining(", "));

            sb.append("\nCREATE ").append(unique).append("INDEX ")
                    .append(quoteIdentifier(idxName)).append(" ON ").append(fullTableName)
                    .append(" USING ").append(it.keyword()).append(" (").append(cols).append(");");
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
                sb.append("\n\n").append(tr.getDefinition().trim()).append(";");
            }
        }

        return sb.toString().trim();
    }

    @Override
    public String generateTableComment(String schema, String table, String comment, boolean isDiff) {
        String fullTableName = formatTableName(schema, table);
        String val = escapeLiteral(comment != null ? comment.trim() : "");
        return "COMMENT ON TABLE " + fullTableName + " IS '" + val + "';";
    }

    @Override
    public String generateColumnComment(String schema, String table, String column, String comment, boolean isDiff) {
        String fullTableName = formatTableName(schema, table);
        String col = quoteIdentifier(column);
        String val = escapeLiteral(comment != null ? comment.trim() : "");
        return "COMMENT ON COLUMN " + fullTableName + "." + col + " IS '" + val + "';";
    }

    @Override
    public String escapeLiteral(String literal) {
        if (literal == null) return "";
        return literal.replace("'", "''");
    }
}

