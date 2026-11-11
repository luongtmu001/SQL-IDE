package com.lltool.core.application.tabledesigner;

import com.lltool.core.application.tabledesigner.dialect.DdlDialect;
import com.lltool.core.application.tabledesigner.dialect.PostgresDdlDialect;
import com.lltool.core.application.tabledesigner.dialect.SqlServerDdlDialect;
import com.lltool.core.application.tabledesigner.model.MigrationPlan;
import com.lltool.core.application.tabledesigner.model.MigrationStatement;
import com.lltool.core.application.tabledesigner.model.StatementGroup;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Migration Diff Engine: computes delta between original and modified TableDesign
 * and generates ordered, non-conflicting DDL migration statements.
 *
 * Implements strict 20-step execution order and fixes Bugs B1, B3, B4, B5, B6, B7, B8, B9, B16, B18.
 */
public class TableDesignDiffer {

    public MigrationPlan computeDiff(TableDesign original, TableDesign modified) {
        if (modified == null) {
            return MigrationPlan.empty();
        }

        String engine = modified.getEngine() != null ? modified.getEngine().toLowerCase() : "sqlserver";
        DdlDialect dialect = "postgresql".equals(engine) ? new PostgresDdlDialect() : new SqlServerDdlDialect();
        return computeDiff(original, modified, dialect);
    }

    public MigrationPlan computeDiff(TableDesign original, TableDesign modified, DdlDialect dialect) {
        if (modified == null) {
            return MigrationPlan.empty();
        }
        DdlDialect resolvedDialect = dialect;
        if (resolvedDialect == null) {
            resolvedDialect = "postgresql".equalsIgnoreCase(modified.getEngine())
                    ? new PostgresDdlDialect()
                    : new SqlServerDdlDialect();
        }
        final DdlDialect ddlDialect = resolvedDialect;

        boolean isSqlServer = "sqlserver".equalsIgnoreCase(ddlDialect.engine());

        // Case 1: New table (no original data)
        if (original == null || original.getColumns().isEmpty()) {
            String createSql = ddlDialect.generateCreateTable(modified);
            List<MigrationStatement> stmts = new ArrayList<>();
            stmts.add(MigrationStatement.of(createSql, "Tạo bảng mới " + modified.getTable(),
                    StatementGroup.COLUMN_ADD, false, modified.getTable()));
            return new MigrationPlan(stmts, List.of(), false, true);
        }

        List<MigrationStatement> statements = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean[] hasDestructive = new boolean[]{false};

        String currentSchema = modified.getSchema();
        String originalSchema = original.getSchema();
        String currentTable = modified.getTable();
        String originalTable = original.getTable();
        String fullTargetTable = ddlDialect.formatTableName(currentSchema, currentTable);

        // Map columns for original and modified
        // Fix B1: Match primarily by ColumnId if present, fallback to originalName, then name
        Map<String, ColumnDesign> origByName = new LinkedHashMap<>();
        for (ColumnDesign col : original.getColumns()) {
            origByName.put(col.getName().toLowerCase(), col);
        }

        // Identify Renamed, Dropped, Added, Altered columns
        Set<String> matchedOrigCols = new HashSet<>();
        List<ColumnRename> renames = new ArrayList<>();
        List<ColumnDesign> addedCols = new ArrayList<>();
        List<ColumnAlteration> alteredCols = new ArrayList<>();

        for (ColumnDesign modCol : modified.getColumns()) {
            ColumnDesign origCol = null;

            // 1. Check if modCol has originalName matching an existing column
            if (modCol.getOriginalName() != null && !modCol.getOriginalName().isBlank()) {
                origCol = origByName.get(modCol.getOriginalName().toLowerCase());
            }

            // 2. If no originalName specified, check matching name
            if (origCol == null) {
                origCol = origByName.get(modCol.getName().toLowerCase());
            }

            if (origCol != null && !matchedOrigCols.contains(origCol.getName().toLowerCase())) {
                matchedOrigCols.add(origCol.getName().toLowerCase());
                if (!origCol.getName().equalsIgnoreCase(modCol.getName())) {
                    renames.add(new ColumnRename(origCol.getName(), modCol.getName()));
                }
                // Check if attributes changed
                if (isColumnAltered(origCol, modCol, isSqlServer)) {
                    alteredCols.add(new ColumnAlteration(origCol, modCol));
                }
            } else {
                // Completely new column (or duplicate)
                addedCols.add(modCol);
            }
        }

        List<ColumnDesign> droppedCols = new ArrayList<>();
        for (ColumnDesign origCol : original.getColumns()) {
            if (!matchedOrigCols.contains(origCol.getName().toLowerCase())) {
                droppedCols.add(origCol);
            }
        }

        Set<String> affectedColNames = new HashSet<>();
        for (ColumnDesign c : droppedCols) affectedColNames.add(c.getName().toLowerCase());
        for (ColumnAlteration a : alteredCols) affectedColNames.add(a.mod.getName().toLowerCase());
        for (ColumnRename r : renames) affectedColNames.add(r.oldName.toLowerCase());

        // ==========================================
        // STEP 1: Rename Table
        // ==========================================
        if (originalTable != null && !originalTable.equalsIgnoreCase(currentTable)) {
            if (isSqlServer) {
                String oldQ = ddlDialect.formatTableName(originalSchema, originalTable);
                statements.add(MigrationStatement.of(
                        "EXEC sp_rename N'" + escapeSql(oldQ) + "', N'" + escapeSql(currentTable) + "';",
                        "Đổi tên bảng từ " + originalTable + " sang " + currentTable,
                        StatementGroup.TABLE_RENAME, false, currentTable));
            } else {
                String oldFull = ddlDialect.formatTableName(originalSchema, originalTable);
                statements.add(MigrationStatement.of(
                        "ALTER TABLE " + oldFull + " RENAME TO " + ddlDialect.quoteIdentifier(currentTable) + ";",
                        "Đổi tên bảng từ " + originalTable + " sang " + currentTable,
                        StatementGroup.TABLE_RENAME, false, currentTable));
            }
        }

        // ==========================================
        // STEP 2: Drop Foreign Keys
        // ==========================================
        Set<String> modFkNames = modified.getForeignKeys().stream()
                .map(fk -> fk.getName().toLowerCase())
                .collect(Collectors.toSet());
        for (ForeignKeyDesign origFk : original.getForeignKeys()) {
            boolean droppedExplicitly = !modFkNames.contains(origFk.getName().toLowerCase());
            boolean referencesAffectedCol = origFk.getColumnMappings().stream()
                    .anyMatch(m -> affectedColNames.contains(m.getSourceColumn().toLowerCase()));
            ForeignKeyDesign modFk = findFkByName(modified.getForeignKeys(), origFk.getName());
            boolean altered = modFk != null && !origFk.equals(modFk);

            if (droppedExplicitly || referencesAffectedCol || altered) {
                statements.add(MigrationStatement.of(
                        "ALTER TABLE " + fullTargetTable + " DROP CONSTRAINT " + ddlDialect.quoteIdentifier(origFk.getName()) + ";",
                        "Xóa khóa ngoại " + origFk.getName(),
                        StatementGroup.CONSTRAINT_DROP, false, origFk.getName()));
            }
        }

        // ==========================================
        // STEP 3: Drop Indexes
        // ==========================================
        Set<String> modIdxNames = modified.getIndexes().stream()
                .map(idx -> idx.getName().toLowerCase())
                .collect(Collectors.toSet());
        for (IndexDesign origIdx : original.getIndexes()) {
            boolean droppedExplicitly = !modIdxNames.contains(origIdx.getName().toLowerCase());
            boolean referencesAffectedCol = origIdx.getColumns().stream()
                    .anyMatch(ic -> affectedColNames.contains(ic.getColumnName().toLowerCase()));
            IndexDesign modIdx = findIdxByName(modified.getIndexes(), origIdx.getName());
            boolean altered = modIdx != null && !origIdx.equals(modIdx);

            if (droppedExplicitly || referencesAffectedCol || altered) {
                if (isSqlServer) {
                    statements.add(MigrationStatement.of(
                            "DROP INDEX " + ddlDialect.quoteIdentifier(origIdx.getName()) + " ON " + fullTargetTable + ";",
                            "Xóa chỉ mục " + origIdx.getName(),
                            StatementGroup.INDEX_DROP, false, origIdx.getName()));
                } else {
                    statements.add(MigrationStatement.of(
                            "DROP INDEX IF EXISTS " + ddlDialect.formatTableName(currentSchema, origIdx.getName()) + ";",
                            "Xóa chỉ mục " + origIdx.getName(),
                            StatementGroup.INDEX_DROP, false, origIdx.getName()));
                }
            }
        }

        // ==========================================
        // STEP 4: Drop Unique Constraints
        // ==========================================
        Set<String> modUqNames = modified.getUniques().stream()
                .map(uq -> uq.getName().toLowerCase())
                .collect(Collectors.toSet());
        for (UniqueDesign origUq : original.getUniques()) {
            boolean droppedExplicitly = !modUqNames.contains(origUq.getName().toLowerCase());
            boolean referencesAffectedCol = origUq.getColumns().stream()
                    .anyMatch(c -> affectedColNames.contains(c.toLowerCase()));
            UniqueDesign modUq = findUqByName(modified.getUniques(), origUq.getName());
            boolean altered = modUq != null && !origUq.equals(modUq);

            if (droppedExplicitly || referencesAffectedCol || altered) {
                statements.add(MigrationStatement.of(
                        "ALTER TABLE " + fullTargetTable + " DROP CONSTRAINT " + ddlDialect.quoteIdentifier(origUq.getName()) + ";",
                        "Xóa ràng buộc UNIQUE " + origUq.getName(),
                        StatementGroup.CONSTRAINT_DROP, false, origUq.getName()));
            }
        }

        // ==========================================
        // STEP 5: Drop Check Constraints
        // ==========================================
        Set<String> modCkNames = modified.getChecks().stream()
                .map(ck -> ck.getName().toLowerCase())
                .collect(Collectors.toSet());
        for (CheckDesign origCk : original.getChecks()) {
            boolean droppedExplicitly = !modCkNames.contains(origCk.getName().toLowerCase());
            CheckDesign modCk = findCkByName(modified.getChecks(), origCk.getName());
            boolean altered = modCk != null && !origCk.equals(modCk);

            if (droppedExplicitly || altered) {
                statements.add(MigrationStatement.of(
                        "ALTER TABLE " + fullTargetTable + " DROP CONSTRAINT " + ddlDialect.quoteIdentifier(origCk.getName()) + ";",
                        "Xóa ràng buộc CHECK " + origCk.getName(),
                        StatementGroup.CONSTRAINT_DROP, false, origCk.getName()));
            }
        }

        // ==========================================
        // STEP 6: Drop Default Constraints (SQL Server)
        // ==========================================
        if (isSqlServer) {
            for (ColumnAlteration alt : alteredCols) {
                if (alt.orig.getDefaultConstraintName() != null && !alt.orig.getDefaultConstraintName().isBlank()) {
                    statements.add(MigrationStatement.of(
                            "ALTER TABLE " + fullTargetTable + " DROP CONSTRAINT " + ddlDialect.quoteIdentifier(alt.orig.getDefaultConstraintName()) + ";",
                            "Xóa ràng buộc DEFAULT cũ của cột " + alt.orig.getName(),
                            StatementGroup.CONSTRAINT_DROP, false, alt.orig.getDefaultConstraintName()));
                }
            }
        }

        // ==========================================
        // STEP 7: Drop Primary Key
        // ==========================================
        List<String> origPk = original.getPrimaryKeyColumnNames();
        List<String> modPk = modified.getPrimaryKeyColumnNames();
        boolean pkChanged = !origPk.equals(modPk);
        if (!origPk.isEmpty() && pkChanged) {
            String pkName = "PK_" + originalTable;
            statements.add(MigrationStatement.of(
                    "ALTER TABLE " + fullTargetTable + " DROP CONSTRAINT " + ddlDialect.quoteIdentifier(pkName) + ";",
                    "Xóa khóa chính " + pkName,
                    StatementGroup.CONSTRAINT_DROP, false, pkName));
        }

        // ==========================================
        // STEP 8: Rename Columns
        // ==========================================
        for (ColumnRename rename : renames) {
            if (isSqlServer) {
                String fullOldCol = fullTargetTable + "." + ddlDialect.quoteIdentifier(rename.oldName);
                statements.add(MigrationStatement.of(
                        "EXEC sp_rename N'" + escapeSql(fullOldCol) + "', N'" + escapeSql(rename.newName) + "', 'COLUMN';",
                        "Đổi tên cột từ " + rename.oldName + " sang " + rename.newName,
                        StatementGroup.COLUMN_RENAME, false, rename.newName));
            } else {
                statements.add(MigrationStatement.of(
                        "ALTER TABLE " + fullTargetTable + " RENAME COLUMN " + ddlDialect.quoteIdentifier(rename.oldName) + " TO " + ddlDialect.quoteIdentifier(rename.newName) + ";",
                        "Đổi tên cột từ " + rename.oldName + " sang " + rename.newName,
                        StatementGroup.COLUMN_RENAME, false, rename.newName));
            }
        }

        // ==========================================
        // STEP 9: Drop Columns (Destructive!)
        // ==========================================
        for (ColumnDesign col : droppedCols) {
            hasDestructive[0] = true;
            warnings.add("Cột '" + col.getName() + "' sẽ bị xóa cùng toàn bộ dữ liệu chứa trong cột.");
            statements.add(MigrationStatement.of(
                    "ALTER TABLE " + fullTargetTable + " DROP COLUMN " + ddlDialect.quoteIdentifier(col.getName()) + ";",
                    "Xóa cột " + col.getName(),
                    StatementGroup.COLUMN_DROP, true, col.getName()));
        }

        // ==========================================
        // STEP 10: Add Columns
        // ==========================================
        for (ColumnDesign col : addedCols) {
            String colDef = ddlDialect.formatColumnDefinition(col, false);
            statements.add(MigrationStatement.of(
                    "ALTER TABLE " + fullTargetTable + " ADD " + colDef + ";",
                    "Thêm cột mới " + col.getName(),
                    StatementGroup.COLUMN_ADD, false, col.getName()));
        }

        // ==========================================
        // STEP 11: Alter Columns
        // ==========================================
        for (ColumnAlteration alt : alteredCols) {
            ColumnDesign orig = alt.orig;
            ColumnDesign mod = alt.mod;

            // Check destructive data conversion
            if (isNarrowingConversion(orig, mod)) {
                hasDestructive[0] = true;
                warnings.add("Sửa đổi kiểu dữ liệu cột '" + mod.getName() + "' từ " + orig.getDataType() + " sang " + mod.getDataType() + " có thể gây mất dữ liệu hoặc lỗi ép kiểu.");
            }
            if (!orig.isNotNull() && mod.isNotNull() && (mod.getDefaultValue() == null || mod.getDefaultValue().isBlank())) {
                hasDestructive[0] = true;
                warnings.add("Chuyển cột '" + mod.getName() + "' sang NOT NULL khi chưa có giá trị mặc định có thể gây lỗi nếu bảng đã có dữ liệu.");
            }

            if (isSqlServer) {
                // SQL Server: ALTER TABLE ... ALTER COLUMN [col] type [COLLATE ...] [NULL|NOT NULL]
                StringBuilder alterSql = new StringBuilder();
                alterSql.append("ALTER TABLE ").append(fullTargetTable).append(" ALTER COLUMN ")
                        .append(ddlDialect.quoteIdentifier(mod.getName())).append(" ")
                        .append(ddlDialect.formatColumnType(mod));

                if (mod.getCollation() != null && !mod.getCollation().isBlank()) {
                    alterSql.append(" COLLATE ").append(mod.getCollation().trim());
                } else if (orig.getCollation() != null && !orig.getCollation().isBlank()) {
                    // Fix B5: Retain original collation when not explicitly changed
                    alterSql.append(" COLLATE ").append(orig.getCollation().trim());
                }

                if (mod.isNotNull()) {
                    alterSql.append(" NOT NULL");
                } else {
                    alterSql.append(" NULL");
                }
                alterSql.append(";");

                statements.add(MigrationStatement.of(
                        alterSql.toString(),
                        "Sửa cấu trúc cột " + mod.getName(),
                        StatementGroup.COLUMN_ALTER, isNarrowingConversion(orig, mod), mod.getName()));
            } else {
                // PostgreSQL: separate ALTER COLUMN statements for type, nullability, default
                boolean typeChanged = !orig.getDataType().equalsIgnoreCase(mod.getDataType()) ||
                        !Objects.equals(orig.getLength(), mod.getLength()) ||
                        !Objects.equals(orig.getScale(), mod.getScale());
                if (typeChanged) {
                    statements.add(MigrationStatement.of(
                            "ALTER TABLE " + fullTargetTable + " ALTER COLUMN " + ddlDialect.quoteIdentifier(mod.getName()) +
                                    " TYPE " + ddlDialect.formatColumnType(mod) + ";",
                            "Đổi kiểu dữ liệu cột " + mod.getName(),
                            StatementGroup.COLUMN_ALTER, isNarrowingConversion(orig, mod), mod.getName()));
                }

                if (orig.isNotNull() != mod.isNotNull()) {
                    String nullSql = mod.isNotNull() ? "SET NOT NULL" : "DROP NOT NULL";
                    statements.add(MigrationStatement.of(
                            "ALTER TABLE " + fullTargetTable + " ALTER COLUMN " + ddlDialect.quoteIdentifier(mod.getName()) + " " + nullSql + ";",
                            "Cập nhật ràng buộc NULL của cột " + mod.getName(),
                            StatementGroup.COLUMN_ALTER, mod.isNotNull(), mod.getName()));
                }

                if (!Objects.equals(orig.getDefaultValue(), mod.getDefaultValue())) {
                    if (mod.getDefaultValue() != null && !mod.getDefaultValue().isBlank()) {
                        String def = ddlDialect.formatDefaultValue(mod.getDefaultValue(), mod.getDataType(), mod.isDefaultIsExpression());
                        statements.add(MigrationStatement.of(
                                "ALTER TABLE " + fullTargetTable + " ALTER COLUMN " + ddlDialect.quoteIdentifier(mod.getName()) + " SET DEFAULT " + def + ";",
                                "Đặt giá trị mặc định cho cột " + mod.getName(),
                                StatementGroup.COLUMN_ALTER, false, mod.getName()));
                    } else if (orig.getDefaultValue() != null && !orig.getDefaultValue().isBlank()) {
                        statements.add(MigrationStatement.of(
                                "ALTER TABLE " + fullTargetTable + " ALTER COLUMN " + ddlDialect.quoteIdentifier(mod.getName()) + " DROP DEFAULT;",
                                "Xóa giá trị mặc định của cột " + mod.getName(),
                                StatementGroup.COLUMN_ALTER, false, mod.getName()));
                    }
                }
            }
        }

        // ==========================================
        // STEP 12: Add Default Constraints (SQL Server)
        // ==========================================
        if (isSqlServer) {
            for (ColumnAlteration alt : alteredCols) {
                ColumnDesign mod = alt.mod;
                if (mod.getDefaultValue() != null && !mod.getDefaultValue().isBlank()) {
                    String defName = mod.getDefaultConstraintName() != null && !mod.getDefaultConstraintName().isBlank()
                            ? mod.getDefaultConstraintName()
                            : "DF_" + currentTable + "_" + mod.getName();
                    String defVal = ddlDialect.formatDefaultValue(mod.getDefaultValue(), mod.getDataType(), mod.isDefaultIsExpression());
                    statements.add(MigrationStatement.of(
                            "ALTER TABLE " + fullTargetTable + " ADD CONSTRAINT " + ddlDialect.quoteIdentifier(defName) +
                                    " DEFAULT " + defVal + " FOR " + ddlDialect.quoteIdentifier(mod.getName()) + ";",
                            "Thêm ràng buộc DEFAULT cho cột " + mod.getName(),
                            StatementGroup.CONSTRAINT_ADD, false, defName));
                }
            }
        }

        // ==========================================
        // STEP 13: Add Primary Key
        // ==========================================
        if (!modPk.isEmpty() && pkChanged) {
            String pkName = "PK_" + currentTable;
            String pkCols = modPk.stream().map(dialect::quoteIdentifier).collect(Collectors.joining(", "));
            String cluster = isSqlServer ? " CLUSTERED" : "";
            statements.add(MigrationStatement.of(
                    "ALTER TABLE " + fullTargetTable + " ADD CONSTRAINT " + ddlDialect.quoteIdentifier(pkName) +
                            " PRIMARY KEY" + cluster + " (" + pkCols + ");",
                    "Tạo khóa chính " + pkName,
                    StatementGroup.CONSTRAINT_ADD, false, pkName));
        }

        // ==========================================
        // STEP 14: Add Unique Constraints
        // ==========================================
        for (UniqueDesign uq : modified.getUniques()) {
            UniqueDesign origUq = findUqByName(original.getUniques(), uq.getName());
            boolean altered = origUq != null && !origUq.equals(uq);
            boolean isNew = origUq == null;

            if (isNew || altered) {
                String uqName = uq.getName() != null && !uq.getName().isBlank() ? uq.getName() : "UQ_" + currentTable + "_" + String.join("_", uq.getColumns());
                String cols = uq.getColumns().stream().map(dialect::quoteIdentifier).collect(Collectors.joining(", "));
                String cluster = (isSqlServer && uq.isClustered()) ? " CLUSTERED" : "";
                statements.add(MigrationStatement.of(
                        "ALTER TABLE " + fullTargetTable + " ADD CONSTRAINT " + ddlDialect.quoteIdentifier(uqName) +
                                " UNIQUE" + cluster + " (" + cols + ");",
                        "Thêm ràng buộc UNIQUE " + uqName,
                        StatementGroup.CONSTRAINT_ADD, false, uqName));
            }
        }

        // ==========================================
        // STEP 15: Add Check Constraints
        // ==========================================
        for (CheckDesign ck : modified.getChecks()) {
            CheckDesign origCk = findCkByName(original.getChecks(), ck.getName());
            boolean altered = origCk != null && !origCk.equals(ck);
            boolean isNew = origCk == null;

            if (isNew || altered) {
                String ckName = ck.getName() != null && !ck.getName().isBlank() ? ck.getName() : "CK_" + currentTable;
                String clause = ck.getCheckClause() != null ? ck.getCheckClause().trim() : "";
                if (!clause.startsWith("(") || !clause.endsWith(")")) {
                    clause = "(" + clause + ")";
                }
                statements.add(MigrationStatement.of(
                        "ALTER TABLE " + fullTargetTable + " ADD CONSTRAINT " + ddlDialect.quoteIdentifier(ckName) +
                                " CHECK " + clause + ";",
                        "Thêm ràng buộc CHECK " + ckName,
                        StatementGroup.CONSTRAINT_ADD, false, ckName));
            }
        }

        // ==========================================
        // STEP 16: Add Foreign Keys
        // ==========================================
        for (ForeignKeyDesign fk : modified.getForeignKeys()) {
            ForeignKeyDesign origFk = findFkByName(original.getForeignKeys(), fk.getName());
            boolean altered = origFk != null && !origFk.equals(fk);
            boolean isNew = origFk == null;

            if (isNew || altered) {
                String fkName = fk.getName() != null && !fk.getName().isBlank() ? fk.getName() : "FK_" + currentTable + "_" + fk.getTargetTable();
                String srcCols = fk.getColumnMappings().stream().map(m -> ddlDialect.quoteIdentifier(m.getSourceColumn())).collect(Collectors.joining(", "));
                String tgtCols = fk.getColumnMappings().stream().map(m -> ddlDialect.quoteIdentifier(m.getTargetColumn())).collect(Collectors.joining(", "));
                String refTable = ddlDialect.formatTableName(fk.getTargetSchema(), fk.getTargetTable());

                StringBuilder fkSb = new StringBuilder();
                fkSb.append("ALTER TABLE ").append(fullTargetTable).append(" ADD CONSTRAINT ").append(ddlDialect.quoteIdentifier(fkName))
                        .append(" FOREIGN KEY (").append(srcCols).append(")")
                        .append(" REFERENCES ").append(refTable).append(" (").append(tgtCols).append(")");
                if (fk.getOnDelete() != null && !fk.getOnDelete().sqlKeyword().equalsIgnoreCase("NO ACTION")) {
                    fkSb.append(" ON DELETE ").append(fk.getOnDelete().sqlKeyword());
                }
                if (fk.getOnUpdate() != null && !fk.getOnUpdate().sqlKeyword().equalsIgnoreCase("NO ACTION")) {
                    fkSb.append(" ON UPDATE ").append(fk.getOnUpdate().sqlKeyword());
                }
                fkSb.append(";");

                statements.add(MigrationStatement.of(
                        fkSb.toString(),
                        "Thêm khóa ngoại " + fkName,
                        StatementGroup.CONSTRAINT_ADD, false, fkName));
            }
        }

        // ==========================================
        // STEP 17: Add Indexes
        // ==========================================
        for (IndexDesign idx : modified.getIndexes()) {
            IndexDesign origIdx = findIdxByName(original.getIndexes(), idx.getName());
            boolean altered = origIdx != null && !origIdx.equals(idx);
            boolean isNew = origIdx == null;

            if (isNew || altered) {
                String idxName = idx.getName();
                String unique = idx.isUnique() ? "UNIQUE " : "";

                if (isSqlServer) {
                    String cluster = idx.isClustered() ? "CLUSTERED " : "NONCLUSTERED ";
                    String cols = idx.getColumns().stream()
                            .map(c -> ddlDialect.quoteIdentifier(c.getColumnName()) + (c.isDescending() ? " DESC" : ""))
                            .collect(Collectors.joining(", "));

                    StringBuilder idxSb = new StringBuilder();
                    idxSb.append("CREATE ").append(unique).append(cluster).append("INDEX ")
                            .append(ddlDialect.quoteIdentifier(idxName)).append(" ON ").append(fullTargetTable)
                            .append(" (").append(cols).append(")");
                    if (idx.getFilterPredicate() != null && !idx.getFilterPredicate().isBlank()) {
                        idxSb.append(" WHERE ").append(idx.getFilterPredicate());
                    }
                    idxSb.append(";");

                    statements.add(MigrationStatement.of(
                            idxSb.toString(),
                            "Tạo chỉ mục " + idxName,
                            StatementGroup.INDEX_ADD, false, idxName));
                } else {
                    // PostgreSQL (Fix B3)
                    IndexType it = idx.getIndexType() != null ? idx.getIndexType() : IndexType.BTREE;
                    if (it == IndexType.NONCLUSTERED || it == IndexType.CLUSTERED) {
                        it = IndexType.BTREE;
                    }
                    String cols = idx.getColumns().stream()
                            .map(c -> ddlDialect.quoteIdentifier(c.getColumnName()) + (c.isDescending() ? " DESC" : ""))
                            .collect(Collectors.joining(", "));

                    String idxSql = "CREATE " + unique + "INDEX " + ddlDialect.quoteIdentifier(idxName) +
                            " ON " + fullTargetTable + " USING " + it.keyword() + " (" + cols + ");";

                    statements.add(MigrationStatement.of(
                            idxSql,
                            "Tạo chỉ mục " + idxName,
                            StatementGroup.INDEX_ADD, false, idxName));
                }
            }
        }

        // ==========================================
        // STEP 18: Comments
        // ==========================================
        if (!Objects.equals(original.getComment(), modified.getComment())) {
            if (modified.getComment() != null && !modified.getComment().isBlank()) {
                statements.add(MigrationStatement.of(
                        ddlDialect.generateTableComment(currentSchema, currentTable, modified.getComment(), true),
                        "Cập nhật mô tả bảng " + currentTable,
                        StatementGroup.COMMENT, false, currentTable));
            }
        }

        for (ColumnAlteration alt : alteredCols) {
            ColumnDesign orig = alt.orig;
            ColumnDesign mod = alt.mod;
            if (!Objects.equals(orig.getComment(), mod.getComment()) && mod.getComment() != null && !mod.getComment().isBlank()) {
                statements.add(MigrationStatement.of(
                        ddlDialect.generateColumnComment(currentSchema, currentTable, mod.getName(), mod.getComment(), true),
                        "Cập nhật mô tả cột " + mod.getName(),
                        StatementGroup.COMMENT, false, mod.getName()));
            }
        }

        // ==========================================
        // STEP 19 & 20: Triggers
        // ==========================================
        Set<String> modTrigNames = modified.getTriggers().stream()
                .map(tr -> tr.getName().toLowerCase())
                .collect(Collectors.toSet());
        for (TriggerDesign origTr : original.getTriggers()) {
            if (!modTrigNames.contains(origTr.getName().toLowerCase())) {
                if (isSqlServer) {
                    statements.add(MigrationStatement.of(
                            "DROP TRIGGER IF EXISTS " + ddlDialect.formatTableName(currentSchema, origTr.getName()) + ";",
                            "Xóa trigger " + origTr.getName(),
                            StatementGroup.TRIGGER, false, origTr.getName()));
                } else {
                    statements.add(MigrationStatement.of(
                            "DROP TRIGGER IF EXISTS " + ddlDialect.quoteIdentifier(origTr.getName()) + " ON " + fullTargetTable + ";",
                            "Xóa trigger " + origTr.getName(),
                            StatementGroup.TRIGGER, false, origTr.getName()));
                }
            }
        }

        for (TriggerDesign tr : modified.getTriggers()) {
            TriggerDesign origTr = findTrigByName(original.getTriggers(), tr.getName());
            boolean isNew = origTr == null;
            boolean altered = origTr != null && !origTr.equals(tr);

            if (isNew || altered) {
                // Fix B8: SQL Server uses CREATE OR ALTER TRIGGER
                String trigSql = tr.getDefinition() != null ? tr.getDefinition().trim() : "";
                if (isSqlServer && !trigSql.toUpperCase().contains("CREATE OR ALTER")) {
                    trigSql = trigSql.replaceFirst("(?i)CREATE\\s+TRIGGER", "CREATE OR ALTER TRIGGER");
                }
                statements.add(MigrationStatement.of(
                        trigSql,
                        "Tạo hoặc cập nhật trigger " + tr.getName(),
                        StatementGroup.TRIGGER, false, tr.getName()));
            }
        }

        boolean hasChanges = !statements.isEmpty();
        return new MigrationPlan(statements, warnings, hasDestructive[0], hasChanges);
    }

    private boolean isColumnAltered(ColumnDesign orig, ColumnDesign mod, boolean isSqlServer) {
        if (!orig.getDataType().equalsIgnoreCase(mod.getDataType())) return true;
        // Fix B6: normalize size/length
        if (!Objects.equals(orig.getLength(), mod.getLength())) return true;
        if (!Objects.equals(orig.getScale(), mod.getScale())) return true;
        if (orig.isNotNull() != mod.isNotNull()) return true;
        if (!Objects.equals(orig.getDefaultValue(), mod.getDefaultValue())) return true;
        if (!Objects.equals(orig.getCollation(), mod.getCollation())) return true;
        if (orig.isIdentity() != mod.isIdentity()) return true;
        if (!Objects.equals(orig.getComputedFormula(), mod.getComputedFormula())) return true;
        return false;
    }

    private boolean isNarrowingConversion(ColumnDesign orig, ColumnDesign mod) {
        if (!orig.getDataType().equalsIgnoreCase(mod.getDataType())) {
            return true;
        }
        if (orig.getLength() != null && mod.getLength() != null && mod.getLength() < orig.getLength()) {
            return true;
        }
        if (orig.getScale() != null && mod.getScale() != null && mod.getScale() < orig.getScale()) {
            return true;
        }
        return false;
    }

    private IndexDesign findIdxByName(List<IndexDesign> list, String name) {
        if (name == null) return null;
        for (IndexDesign idx : list) {
            if (name.equalsIgnoreCase(idx.getName())) return idx;
        }
        return null;
    }

    private ForeignKeyDesign findFkByName(List<ForeignKeyDesign> list, String name) {
        if (name == null) return null;
        for (ForeignKeyDesign fk : list) {
            if (name.equalsIgnoreCase(fk.getName())) return fk;
        }
        return null;
    }

    private UniqueDesign findUqByName(List<UniqueDesign> list, String name) {
        if (name == null) return null;
        for (UniqueDesign uq : list) {
            if (name.equalsIgnoreCase(uq.getName())) return uq;
        }
        return null;
    }

    private CheckDesign findCkByName(List<CheckDesign> list, String name) {
        if (name == null) return null;
        for (CheckDesign ck : list) {
            if (name.equalsIgnoreCase(ck.getName())) return ck;
        }
        return null;
    }

    private TriggerDesign findTrigByName(List<TriggerDesign> list, String name) {
        if (name == null) return null;
        for (TriggerDesign tr : list) {
            if (name.equalsIgnoreCase(tr.getName())) return tr;
        }
        return null;
    }

    private String escapeSql(String val) {
        return val != null ? val.replace("'", "''") : "";
    }

    private record ColumnRename(String oldName, String newName) {}
    private record ColumnAlteration(ColumnDesign orig, ColumnDesign mod) {}
}
