package com.lltool.core.application.schemacompare;

import com.lltool.core.application.tabledesigner.TableDesignDiffer;
import com.lltool.core.application.tabledesigner.dialect.DdlDialect;
import com.lltool.core.application.tabledesigner.dialect.PostgresDdlDialect;
import com.lltool.core.application.tabledesigner.dialect.SqlServerDdlDialect;
import com.lltool.core.application.tabledesigner.model.MigrationPlan;
import com.lltool.core.application.tabledesigner.model.MigrationStatement;
import com.lltool.core.domain.schemacompare.model.*;
import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service for snapshotting, comparing database schemas and generating safe, selectable migration DDL.
 */
public class SchemaCompareService {

    private static final Logger log = LoggerFactory.getLogger(SchemaCompareService.class);

    private final TableDesignDiffer tableDiffer = new TableDesignDiffer();
    private final SqlServerDdlDialect sqlServerDialect = new SqlServerDdlDialect();
    private final PostgresDdlDialect postgresDialect = new PostgresDdlDialect();

    public DdlDialect getDialect(String engine) {
        if ("postgresql".equalsIgnoreCase(engine) || "postgres".equalsIgnoreCase(engine)) {
            return postgresDialect;
        }
        return sqlServerDialect;
    }

    /**
     * Compares Source (A) against Target (B).
     * Forward Sync direction: generates statements to update Target B to match Source A.
     */
    public SchemaCompareResult compare(SchemaSnapshot source, SchemaSnapshot target) {
        return compare(source, target, true, false);
    }

    public SchemaCompareResult compare(SchemaSnapshot source, SchemaSnapshot target, boolean ignoreWhitespace, boolean ignoreComments) {
        if (source == null || target == null) {
            throw new IllegalArgumentException("Source and Target snapshots must not be null");
        }

        String srcEngine = source.getEngine() != null ? source.getEngine().toLowerCase() : "sqlserver";
        String tgtEngine = target.getEngine() != null ? target.getEngine().toLowerCase() : "sqlserver";

        if (!srcEngine.equals(tgtEngine)) {
            throw new IllegalArgumentException(String.format(
                    "So sánh schema yêu cầu 2 kết nối cùng loại hệ quản trị (Source: '%s', Target: '%s').",
                    srcEngine, tgtEngine));
        }

        DdlDialect dialect = getDialect(tgtEngine);
        List<SchemaObjectDiff> diffs = new ArrayList<>();

        // 1. Compare Tables
        diffs.addAll(compareTables(source, target, dialect));

        // 2. Compare Views
        diffs.addAll(compareCodeObjects(source, target, SchemaObjectType.VIEW, ignoreWhitespace, ignoreComments));

        // 3. Compare Stored Procedures
        diffs.addAll(compareCodeObjects(source, target, SchemaObjectType.PROCEDURE, ignoreWhitespace, ignoreComments));

        // 4. Compare Functions
        diffs.addAll(compareCodeObjects(source, target, SchemaObjectType.FUNCTION, ignoreWhitespace, ignoreComments));

        // 5. Compare Triggers
        diffs.addAll(compareCodeObjects(source, target, SchemaObjectType.TRIGGER, ignoreWhitespace, ignoreComments));

        // Sort diffs: DIFFERENT (0) -> MISSING_IN_TARGET (1) -> EXTRA_IN_TARGET (2) -> IDENTICAL (3)
        diffs.sort(Comparator.comparingInt((SchemaObjectDiff d) -> {
            return switch (d.getStatus()) {
                case DIFFERENT -> 0;
                case MISSING_IN_TARGET -> 1;
                case EXTRA_IN_TARGET -> 2;
                case IDENTICAL -> 3;
            };
        }).thenComparing(d -> d.getObjectType().ordinal())
          .thenComparing(SchemaObjectDiff::getName, String.CASE_INSENSITIVE_ORDER));

        // Compute summary counts
        int identical = 0, different = 0, missing = 0, extra = 0;
        int tables = 0, views = 0, procs = 0, funcs = 0, trigs = 0;
        boolean hasDestructive = false;

        for (SchemaObjectDiff d : diffs) {
            switch (d.getStatus()) {
                case IDENTICAL -> identical++;
                case DIFFERENT -> different++;
                case MISSING_IN_TARGET -> missing++;
                case EXTRA_IN_TARGET -> extra++;
            }
            switch (d.getObjectType()) {
                case TABLE -> tables++;
                case VIEW -> views++;
                case PROCEDURE -> procs++;
                case FUNCTION -> funcs++;
                case TRIGGER -> trigs++;
            }
            if (d.getStatus() != DiffStatus.IDENTICAL && d.isDestructive()) {
                hasDestructive = true;
            }
        }

        SchemaCompareSummary summary = new SchemaCompareSummary(
                diffs.size(), identical, different, missing, extra,
                tables, views, procs, funcs, trigs, hasDestructive
        );

        return new SchemaCompareResult(source, target, diffs, summary);
    }

    private List<SchemaObjectDiff> compareTables(SchemaSnapshot source, SchemaSnapshot target, DdlDialect dialect) {
        List<SchemaObjectDiff> result = new ArrayList<>();
        Map<String, TableDesign> srcTables = source.getTables();
        Map<String, TableDesign> tgtTables = target.getTables();

        Set<String> allTableNames = new LinkedHashSet<>();
        allTableNames.addAll(srcTables.keySet());
        allTableNames.addAll(tgtTables.keySet());

        for (String tableName : allTableNames) {
            TableDesign srcTable = srcTables.get(tableName);
            TableDesign tgtTable = tgtTables.get(tableName);

            if (srcTable != null && tgtTable == null) {
                // Table only in Source -> Missing in Target (CREATE)
                List<String> stmts = new ArrayList<>();
                List<ColumnDiff> colDiffs = new ArrayList<>();
                for (ColumnDesign c : srcTable.getColumns()) {
                    colDiffs.add(new ColumnDiff(c.getName(), formatColumnType(c), null, !c.isNotNull(), null, "add"));
                }

                // If table has foreign keys, emit CREATE TABLE without FKs first, then add FKs as separate ALTER statements
                if (!srcTable.getForeignKeys().isEmpty()) {
                    TableDesign noFkTable = srcTable.deepCopy();
                    noFkTable.getForeignKeys().clear();
                    String createNoFk = dialect.generateCreateTable(noFkTable);
                    stmts.add(createNoFk);

                    for (com.lltool.core.domain.tabledesigner.model.ForeignKeyDesign fk : srcTable.getForeignKeys()) {
                        String fkName = fk.getName() != null && !fk.getName().isBlank() ? fk.getName() : "FK_" + srcTable.getTable() + "_" + fk.getTargetTable();
                        String srcCols = fk.getColumnMappings().stream().map(m -> dialect.quoteIdentifier(m.getSourceColumn())).collect(Collectors.joining(", "));
                        String tgtCols = fk.getColumnMappings().stream().map(m -> dialect.quoteIdentifier(m.getTargetColumn())).collect(Collectors.joining(", "));
                        String fullTargetTable = dialect.formatTableName(srcTable.getSchema(), srcTable.getTable());
                        String refTable = dialect.formatTableName(fk.getTargetSchema(), fk.getTargetTable());

                        StringBuilder fkSb = new StringBuilder();
                        fkSb.append("ALTER TABLE ").append(fullTargetTable).append(" ADD CONSTRAINT ").append(dialect.quoteIdentifier(fkName))
                                .append(" FOREIGN KEY (").append(srcCols).append(")")
                                .append(" REFERENCES ").append(refTable).append(" (").append(tgtCols).append(")");
                        if (fk.getOnDelete() != null && !fk.getOnDelete().sqlKeyword().equalsIgnoreCase("NO ACTION")) {
                            fkSb.append(" ON DELETE ").append(fk.getOnDelete().sqlKeyword());
                        }
                        if (fk.getOnUpdate() != null && !fk.getOnUpdate().sqlKeyword().equalsIgnoreCase("NO ACTION")) {
                            fkSb.append(" ON UPDATE ").append(fk.getOnUpdate().sqlKeyword());
                        }
                        fkSb.append(";");
                        if ("sqlserver".equalsIgnoreCase(dialect.engine())) {
                            fkSb.append("\nGO\n");
                        } else {
                            fkSb.append("\n");
                        }
                        stmts.add(fkSb.toString());
                    }
                } else {
                    String createSql = dialect.generateCreateTable(srcTable);
                    stmts.add(createSql);
                }

                String fullMigSql = String.join("\n", stmts);
                result.add(new SchemaObjectDiff(
                        tableName,
                        target.getSchema(),
                        SchemaObjectType.TABLE,
                        DiffStatus.MISSING_IN_TARGET,
                        DiffAction.CREATE,
                        false,
                        dialect.generateCreateTable(srcTable),
                        "",
                        fullMigSql,
                        stmts,
                        colDiffs,
                        srcTable.getColumns().size(),
                        0
                ));
            } else if (srcTable == null && tgtTable != null) {
                // Table only in Target -> Extra in Target (DROP)
                boolean isSqlSrv = "sqlserver".equalsIgnoreCase(dialect.engine());
                String fullTable = dialect.formatTableName(tgtTable.getSchema(), tgtTable.getTable());
                String dropSql = isSqlSrv
                        ? "DROP TABLE " + fullTable + ";\nGO\n"
                        : "DROP TABLE IF EXISTS " + fullTable + ";\n";
                String tgtCreateSql = dialect.generateCreateTable(tgtTable);
                List<ColumnDiff> colDiffs = new ArrayList<>();
                for (ColumnDesign c : tgtTable.getColumns()) {
                    colDiffs.add(new ColumnDiff(c.getName(), null, formatColumnType(c), null, !c.isNotNull(), "drop"));
                }
                result.add(new SchemaObjectDiff(
                        tableName,
                        target.getSchema(),
                        SchemaObjectType.TABLE,
                        DiffStatus.EXTRA_IN_TARGET,
                        DiffAction.DROP,
                        true,
                        "",
                        tgtCreateSql,
                        dropSql,
                        List.of(dropSql),
                        colDiffs,
                        0,
                        tgtTable.getColumns().size()
                ));
            } else if (srcTable != null && tgtTable != null) {
                // Table in both -> Compute delta from Target (original) to Source (modified)
                MigrationPlan plan = tableDiffer.computeDiff(tgtTable, srcTable, dialect);
                List<String> stmts = plan.statements().stream().map(MigrationStatement::sql).toList();
                String migSql = plan.generateFullMigrationSql(dialect.engine());

                // Compute detailed column diffs
                List<ColumnDiff> colDiffs = computeColumnDiffs(srcTable, tgtTable);

                boolean isDifferent = plan.hasChanges() || !colDiffs.isEmpty();
                DiffStatus status = isDifferent ? DiffStatus.DIFFERENT : DiffStatus.IDENTICAL;
                DiffAction action = isDifferent ? DiffAction.ALTER : DiffAction.NONE;

                String srcDdl = dialect.generateCreateTable(srcTable);
                String tgtDdl = dialect.generateCreateTable(tgtTable);

                result.add(new SchemaObjectDiff(
                        tableName,
                        target.getSchema(),
                        SchemaObjectType.TABLE,
                        status,
                        action,
                        plan.hasDestructive(),
                        srcDdl,
                        tgtDdl,
                        migSql,
                        stmts,
                        colDiffs,
                        srcTable.getColumns().size(),
                        tgtTable.getColumns().size()
                ));
            }
        }

        return result;
    }

    private List<ColumnDiff> computeColumnDiffs(TableDesign src, TableDesign tgt) {
        List<ColumnDiff> diffs = new ArrayList<>();
        Map<String, ColumnDesign> srcCols = new LinkedHashMap<>();
        for (ColumnDesign c : src.getColumns()) {
            srcCols.put(c.getName().toLowerCase(), c);
        }
        Map<String, ColumnDesign> tgtCols = new LinkedHashMap<>();
        for (ColumnDesign c : tgt.getColumns()) {
            tgtCols.put(c.getName().toLowerCase(), c);
        }

        for (Map.Entry<String, ColumnDesign> entry : srcCols.entrySet()) {
            String lowerName = entry.getKey();
            ColumnDesign sc = entry.getValue();
            if (!tgtCols.containsKey(lowerName)) {
                diffs.add(new ColumnDiff(sc.getName(), formatColumnType(sc), null, !sc.isNotNull(), null, "add"));
            } else {
                ColumnDesign tc = tgtCols.get(lowerName);
                String st = formatColumnType(sc);
                String tt = formatColumnType(tc);
                boolean sn = !sc.isNotNull();
                boolean tn = !tc.isNotNull();
                boolean spk = sc.isPrimaryKey();
                boolean tpk = tc.isPrimaryKey();

                if (!st.equalsIgnoreCase(tt) || sn != tn || spk != tpk) {
                    diffs.add(new ColumnDiff(sc.getName(), st, tt, sn, tn, "modify"));
                }
            }
        }

        for (Map.Entry<String, ColumnDesign> entry : tgtCols.entrySet()) {
            String lowerName = entry.getKey();
            ColumnDesign tc = entry.getValue();
            if (!srcCols.containsKey(lowerName)) {
                diffs.add(new ColumnDiff(tc.getName(), null, formatColumnType(tc), null, !tc.isNotNull(), "drop"));
            }
        }

        return diffs;
    }

    private String formatColumnType(ColumnDesign c) {
        if (c == null) return "";
        String type = c.getDataType() != null ? c.getDataType() : "varchar";
        if (c.getLength() != null && c.getLength() > 0) {
            return type + "(" + c.getLength() + ")";
        }
        if (c.getScale() != null && c.getScale() > 0) {
            return type + "(18," + c.getScale() + ")";
        }
        return type;
    }

    private List<SchemaObjectDiff> compareCodeObjects(
            SchemaSnapshot source,
            SchemaSnapshot target,
            SchemaObjectType objType,
            boolean ignoreWhitespace,
            boolean ignoreComments) {

        String schema = target.getSchema();
        String engine = target.getEngine() != null ? target.getEngine().toLowerCase() : "sqlserver";
        boolean isPg = "postgresql".equals(engine) || "postgres".equals(engine);

        Map<String, String> srcMap = switch (objType) {
            case VIEW -> source.getViews();
            case PROCEDURE -> source.getProcedures();
            case FUNCTION -> source.getFunctions();
            case TRIGGER -> source.getTriggers();
            default -> Collections.emptyMap();
        };

        Map<String, String> tgtMap = switch (objType) {
            case VIEW -> target.getViews();
            case PROCEDURE -> target.getProcedures();
            case FUNCTION -> target.getFunctions();
            case TRIGGER -> target.getTriggers();
            default -> Collections.emptyMap();
        };

        List<SchemaObjectDiff> diffs = new ArrayList<>();
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(srcMap.keySet());
        allKeys.addAll(tgtMap.keySet());

        for (String name : allKeys) {
            String srcDdl = srcMap.get(name);
            String tgtDdl = tgtMap.get(name);

            if (srcDdl != null && tgtDdl == null) {
                // Missing in Target (CREATE)
                String migSql = srcDdl.trim();
                if (!isPg && !migSql.endsWith("GO")) {
                    migSql += "\nGO\n";
                } else {
                    migSql += "\n";
                }
                diffs.add(new SchemaObjectDiff(
                        name, schema, objType, DiffStatus.MISSING_IN_TARGET, DiffAction.CREATE,
                        false, srcDdl, "", migSql, List.of(migSql), List.of(), 0, 0
                ));
            } else if (srcDdl == null && tgtDdl != null) {
                // Extra in Target (DROP)
                String dropSql = formatDropCodeObject(name, objType, schema, engine, target);
                diffs.add(new SchemaObjectDiff(
                        name, schema, objType, DiffStatus.EXTRA_IN_TARGET, DiffAction.DROP,
                        true, "", tgtDdl, dropSql, List.of(dropSql), List.of(), 0, 0
                ));
            } else if (srcDdl != null && tgtDdl != null) {
                // Compare normalized code (case-sensitive outside and inside strings)
                String normSrc = normalizeCode(srcDdl, ignoreWhitespace, ignoreComments);
                String normTgt = normalizeCode(tgtDdl, ignoreWhitespace, ignoreComments);
                boolean isIdentical = normSrc.equals(normTgt);

                if (isIdentical) {
                    diffs.add(new SchemaObjectDiff(
                            name, schema, objType, DiffStatus.IDENTICAL, DiffAction.NONE,
                            false, srcDdl, tgtDdl, "", List.of(), List.of(), 0, 0
                    ));
                } else {
                    // Generate ALTER / REPLACE statement
                    String migSql = srcDdl.trim();
                    if (!isPg) {
                        migSql = Pattern.compile("(?i)\\bCREATE\\s+(PROCEDURE|PROC|VIEW|FUNCTION|TRIGGER)\\b")
                                .matcher(migSql).replaceFirst("CREATE OR ALTER $1");
                        if (!migSql.endsWith("GO")) migSql += "\nGO\n";
                    } else {
                        // PostgreSQL dialect specifics (SC-4)
                        if (objType == SchemaObjectType.VIEW) {
                            migSql = String.format("DROP VIEW IF EXISTS \"%s\".\"%s\" CASCADE;\n", schema, name) + migSql + "\n";
                        } else if (objType == SchemaObjectType.TRIGGER) {
                            String parentTable = source.getTriggerParentTables().get(name);
                            if (parentTable == null) parentTable = target.getTriggerParentTables().get(name);
                            String parent = parentTable != null ? ("\"" + schema + "\".\"" + parentTable + "\"") : ("\"" + schema + "\".\"unknown_table\"");
                            migSql = String.format("DROP TRIGGER IF EXISTS \"%s\" ON %s;\n", name, parent) + migSql + "\n";
                        } else if (objType == SchemaObjectType.PROCEDURE) {
                            migSql = Pattern.compile("(?i)\\bCREATE\\s+PROCEDURE\\b")
                                    .matcher(migSql).replaceFirst("CREATE OR REPLACE PROCEDURE");
                            migSql += "\n";
                        } else {
                            migSql = Pattern.compile("(?i)\\bCREATE\\s+FUNCTION\\b")
                                    .matcher(migSql).replaceFirst("CREATE OR REPLACE FUNCTION");
                            migSql += "\n";
                        }
                    }

                    diffs.add(new SchemaObjectDiff(
                            name, schema, objType, DiffStatus.DIFFERENT, DiffAction.ALTER,
                            false, srcDdl, tgtDdl, migSql, List.of(migSql), List.of(), 0, 0
                    ));
                }
            }
        }

        return diffs;
    }

    public static String normalizeCode(String sql, boolean ignoreWhitespace, boolean ignoreComments) {
        if (sql == null) return "";
        StringBuilder sb = new StringBuilder(sql.length());
        int len = sql.length();
        int i = 0;
        boolean lastWasSpace = false;

        while (i < len) {
            char c = sql.charAt(i);

            // Single line comment --
            if (c == '-' && i + 1 < len && sql.charAt(i + 1) == '-') {
                int end = sql.indexOf('\n', i + 2);
                if (end == -1) end = len;
                if (!ignoreComments) {
                    sb.append(sql, i, end);
                } else if (ignoreWhitespace && !lastWasSpace) {
                    sb.append(' ');
                    lastWasSpace = true;
                }
                i = end;
                continue;
            }

            // Multi line comment /* ... */
            if (c == '/' && i + 1 < len && sql.charAt(i + 1) == '*') {
                int end = sql.indexOf("*/", i + 2);
                if (end == -1) end = len;
                else end += 2;
                if (!ignoreComments) {
                    sb.append(sql, i, end);
                } else if (ignoreWhitespace && !lastWasSpace) {
                    sb.append(' ');
                    lastWasSpace = true;
                }
                i = end;
                continue;
            }

            // String literal '...' (preserve verbatim including whitespace and case)
            if (c == '\'') {
                sb.append('\'');
                i++;
                while (i < len) {
                    char sc = sql.charAt(i);
                    sb.append(sc);
                    if (sc == '\'') {
                        if (i + 1 < len && sql.charAt(i + 1) == '\'') {
                            sb.append('\'');
                            i += 2;
                        } else {
                            i++;
                            break;
                        }
                    } else {
                        i++;
                    }
                }
                lastWasSpace = false;
                continue;
            }

            // Outside literal / comment
            if (Character.isWhitespace(c)) {
                if (ignoreWhitespace) {
                    if (!lastWasSpace && sb.length() > 0) {
                        sb.append(' ');
                        lastWasSpace = true;
                    }
                } else {
                    sb.append(c);
                }
                i++;
            } else {
                sb.append(c);
                lastWasSpace = false;
                i++;
            }
        }
        return sb.toString().trim();
    }

    private String cleanCode(String code) {
        return normalizeCode(code, true, false);
    }

    private String formatDropCodeObject(String name, SchemaObjectType objType, String schema, String engine, SchemaSnapshot target) {
        boolean isPg = "postgresql".equalsIgnoreCase(engine) || "postgres".equalsIgnoreCase(engine);

        if (!isPg) {
            String typeKw = switch (objType) {
                case PROCEDURE -> "PROCEDURE";
                case VIEW -> "VIEW";
                case FUNCTION -> "FUNCTION";
                case TRIGGER -> "TRIGGER";
                default -> "OBJECT";
            };
            return String.format("DROP %s IF EXISTS [%s].[%s];\nGO\n", typeKw, schema, name);
        }

        // PostgreSQL dialect (SC-4)
        switch (objType) {
            case TRIGGER -> {
                String parentTable = target != null ? target.getTriggerParentTables().get(name) : null;
                String parent = (parentTable != null && !parentTable.isBlank()) ? ("\"" + schema + "\".\"" + parentTable + "\"") : ("\"" + schema + "\".\"unknown_table\"");
                return String.format("DROP TRIGGER IF EXISTS \"%s\" ON %s;\n", name, parent);
            }
            case FUNCTION -> {
                String sig = target != null ? target.getFunctionSignatures().get(name) : null;
                String fnName = name.contains("(") ? name.substring(0, name.indexOf('(')).trim() : name;
                String args = sig != null ? ("(" + sig + ")") : (name.contains("(") ? name.substring(name.indexOf('(')) : "");
                return String.format("DROP FUNCTION IF EXISTS \"%s\".\"%s\"%s;\n", schema, fnName, args);
            }
            case PROCEDURE -> {
                String sig = target != null ? target.getFunctionSignatures().get(name) : null;
                String prName = name.contains("(") ? name.substring(0, name.indexOf('(')).trim() : name;
                String args = sig != null ? ("(" + sig + ")") : (name.contains("(") ? name.substring(name.indexOf('(')) : "");
                return String.format("DROP PROCEDURE IF EXISTS \"%s\".\"%s\"%s;\n", schema, prName, args);
            }
            case VIEW -> {
                return String.format("DROP VIEW IF EXISTS \"%s\".\"%s\" CASCADE;\n", schema, name);
            }
            default -> {
                return String.format("DROP OBJECT IF EXISTS \"%s\".\"%s\";\n", schema, name);
            }
        }
    }

    /**
     * Generates a consolidated, executable DDL migration script with dependency ordering (SC-6).
     */
    public MigrationScriptResult generateMigrationScript(
            SchemaCompareResult compareResult,
            Set<String> selectedQualifiedNames,
            boolean includeDrop,
            boolean wrapTransaction) {

        if (compareResult == null) {
            return new MigrationScriptResult("-- Chưa có kết quả so sánh.\n", 0, false, 0);
        }

        List<SchemaObjectDiff> activeDiffs = new ArrayList<>();
        for (SchemaObjectDiff d : compareResult.getDiffs()) {
            if (d.getStatus() == DiffStatus.IDENTICAL) continue;
            String keyWithType = d.getObjectType().name() + ":" + d.getQualifiedName();
            if (selectedQualifiedNames != null && !selectedQualifiedNames.contains(keyWithType) && !selectedQualifiedNames.contains(d.getQualifiedName())) continue;
            if (d.getStatus() == DiffStatus.EXTRA_IN_TARGET && !includeDrop) continue;
            if (d.getMigrationSql() == null || d.getMigrationSql().isBlank()) continue;
            activeDiffs.add(d);
        }

        if (activeDiffs.isEmpty()) {
            return new MigrationScriptResult("-- Không có thay đổi nào được chọn để sinh script migration.\n", 0, false, 0);
        }

        String engine = compareResult.getTarget().getEngine();
        boolean isSqlServer = "sqlserver".equalsIgnoreCase(engine);

        StringBuilder sb = new StringBuilder();
        sb.append("-- ====================================================================\n");
        sb.append("-- SQL IDE - Schema Compare Migration Script\n");
        sb.append(String.format("-- Engine: %s\n", engine.toUpperCase()));
        sb.append(String.format("-- Source: [%s].[%s]\n",
                compareResult.getSource().getDatabase(), compareResult.getSource().getSchema()));
        sb.append(String.format("-- Target: [%s].[%s]\n",
                compareResult.getTarget().getDatabase(), compareResult.getTarget().getSchema()));
        sb.append(String.format("-- Generated Objects: %d\n", activeDiffs.size()));
        sb.append("-- ====================================================================\n\n");

        if (wrapTransaction) {
            if (isSqlServer) {
                sb.append("SET XACT_ABORT ON;\n");
                sb.append("BEGIN TRANSACTION;\n");
                sb.append("GO\n\n");
            } else {
                sb.append("BEGIN;\n\n");
            }
        }

        int stmtCount = 0;
        boolean hasDestructive = false;

        // ====================================================================
        // PHASE 1: DROPS (Reverse dependency order: Triggers -> Procs -> Views -> Funcs -> Tables)
        // ====================================================================
        List<SchemaObjectDiff> dropDiffs = activeDiffs.stream()
                .filter(d -> d.getStatus() == DiffStatus.EXTRA_IN_TARGET)
                .sorted(Comparator.comparingInt((SchemaObjectDiff d) -> switch (d.getObjectType()) {
                    case TRIGGER -> 0;
                    case PROCEDURE -> 1;
                    case VIEW -> 2;
                    case FUNCTION -> 3;
                    case TABLE -> 4;
                }).thenComparing(SchemaObjectDiff::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (!dropDiffs.isEmpty()) {
            sb.append("-- ────────────────────────────────────────────────────────────────────\n");
            sb.append("-- PHASE 1: DROP STATEMENTS (Reverse Dependency Order)\n");
            sb.append("-- ────────────────────────────────────────────────────────────────────\n\n");

            for (SchemaObjectDiff d : dropDiffs) {
                hasDestructive = true;
                stmtCount += d.getStatements().isEmpty() ? 1 : d.getStatements().size();
                sb.append("-- [CẢNH BÁO: THAO TÁC PHÁ HỦY / DESTRUCTIVE - CÓ THỂ MẤT DỮ LIỆU]\n");
                sb.append(String.format("-- Xóa %s: [%s]\n", d.getObjectType().getDisplayName(), d.getName()));
                sb.append(d.getMigrationSql().trim());
                sb.append("\n\n");
            }
        }

        // ====================================================================
        // PHASE 2: CREATES & ALTERS (Dependency order: Tables -> Alters -> FKs -> Funcs -> Views -> Procs -> Triggers)
        // ====================================================================
        List<SchemaObjectDiff> nonDropDiffs = activeDiffs.stream()
                .filter(d -> d.getStatus() != DiffStatus.EXTRA_IN_TARGET)
                .toList();

        if (!nonDropDiffs.isEmpty()) {
            // 2.1 Create Tables without FKs
            List<SchemaObjectDiff> newTables = nonDropDiffs.stream()
                    .filter(d -> d.getObjectType() == SchemaObjectType.TABLE && d.getStatus() == DiffStatus.MISSING_IN_TARGET)
                    .toList();

            if (!newTables.isEmpty()) {
                sb.append("-- ────────────────────────────────────────────────────────────────────\n");
                sb.append("-- PHASE 2.1: CREATE TABLES (Initial definitions)\n");
                sb.append("-- ────────────────────────────────────────────────────────────────────\n\n");

                for (SchemaObjectDiff d : newTables) {
                    if (d.isDestructive()) hasDestructive = true;
                    // First statement is CREATE TABLE
                    String createStmt = !d.getStatements().isEmpty() ? d.getStatements().get(0) : d.getMigrationSql();
                    stmtCount++;
                    sb.append(String.format("-- Tạo mới bảng: [%s]\n", d.getName()));
                    sb.append(createStmt.trim());
                    sb.append("\n\n");
                }
            }

            // 2.2 Alter Tables (columns, constraints, indexes except FK)
            List<SchemaObjectDiff> alteredTables = nonDropDiffs.stream()
                    .filter(d -> d.getObjectType() == SchemaObjectType.TABLE && d.getStatus() == DiffStatus.DIFFERENT)
                    .toList();

            if (!alteredTables.isEmpty()) {
                sb.append("-- ────────────────────────────────────────────────────────────────────\n");
                sb.append("-- PHASE 2.2: ALTER TABLES (Columns, Constraints, Indexes)\n");
                sb.append("-- ────────────────────────────────────────────────────────────────────\n\n");

                for (SchemaObjectDiff d : alteredTables) {
                    if (d.isDestructive()) {
                        hasDestructive = true;
                        sb.append("-- [CẢNH BÁO: BẢNG CÓ THAY ĐỔI PHÁ HỦY (XOÁ CỘT / THU HẸP KIỂU)]\n");
                    }
                    sb.append(String.format("-- Điều chỉnh cấu trúc bảng: [%s]\n", d.getName()));
                    for (String stmt : d.getStatements()) {
                        if (!stmt.toUpperCase().contains("ADD CONSTRAINT") || !stmt.toUpperCase().contains("FOREIGN KEY")) {
                            stmtCount++;
                            sb.append(stmt.trim()).append("\n");
                        }
                    }
                    sb.append("\n");
                }
            }

            // 2.3 Foreign Keys (from both new tables and altered tables)
            List<String> fkStatements = new ArrayList<>();
            for (SchemaObjectDiff d : nonDropDiffs) {
                if (d.getObjectType() == SchemaObjectType.TABLE) {
                    if (d.getStatus() == DiffStatus.MISSING_IN_TARGET && d.getStatements().size() > 1) {
                        for (int i = 1; i < d.getStatements().size(); i++) {
                            fkStatements.add(d.getStatements().get(i));
                        }
                    } else if (d.getStatus() == DiffStatus.DIFFERENT) {
                        for (String stmt : d.getStatements()) {
                            if (stmt.toUpperCase().contains("ADD CONSTRAINT") && stmt.toUpperCase().contains("FOREIGN KEY")) {
                                fkStatements.add(stmt);
                            }
                        }
                    }
                }
            }

            if (!fkStatements.isEmpty()) {
                sb.append("-- ────────────────────────────────────────────────────────────────────\n");
                sb.append("-- PHASE 2.3: FOREIGN KEYS (Applied after all tables exist)\n");
                sb.append("-- ────────────────────────────────────────────────────────────────────\n\n");

                for (String fkStmt : fkStatements) {
                    stmtCount++;
                    sb.append(fkStmt.trim()).append("\n\n");
                }
            }

            // 2.4 Functions
            List<SchemaObjectDiff> funcs = nonDropDiffs.stream()
                    .filter(d -> d.getObjectType() == SchemaObjectType.FUNCTION)
                    .sorted(Comparator.comparing(SchemaObjectDiff::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            if (!funcs.isEmpty()) {
                sb.append("-- ────────────────────────────────────────────────────────────────────\n");
                sb.append("-- PHASE 2.4: FUNCTIONS\n");
                sb.append("-- ────────────────────────────────────────────────────────────────────\n\n");

                for (SchemaObjectDiff d : funcs) {
                    stmtCount += d.getStatements().isEmpty() ? 1 : d.getStatements().size();
                    sb.append(String.format("-- %s hàm: [%s]\n", d.getStatus() == DiffStatus.MISSING_IN_TARGET ? "Tạo mới" : "Cập nhật", d.getName()));
                    sb.append(d.getMigrationSql().trim()).append("\n\n");
                }
            }

            // 2.5 Views (ordered: if View A references View B, B comes first)
            List<SchemaObjectDiff> views = new ArrayList<>(nonDropDiffs.stream()
                    .filter(d -> d.getObjectType() == SchemaObjectType.VIEW)
                    .toList());

            if (!views.isEmpty()) {
                // Dependency sorting among views
                views.sort((v1, v2) -> {
                    boolean v1UsesV2 = v1.getSourceDefinition().toLowerCase().contains(v2.getName().toLowerCase());
                    boolean v2UsesV1 = v2.getSourceDefinition().toLowerCase().contains(v1.getName().toLowerCase());
                    if (v1UsesV2 && !v2UsesV1) return 1;
                    if (v2UsesV1 && !v1UsesV2) return -1;
                    return v1.getName().compareToIgnoreCase(v2.getName());
                });

                sb.append("-- ────────────────────────────────────────────────────────────────────\n");
                sb.append("-- PHASE 2.5: VIEWS\n");
                sb.append("-- ────────────────────────────────────────────────────────────────────\n\n");

                for (SchemaObjectDiff d : views) {
                    stmtCount += d.getStatements().isEmpty() ? 1 : d.getStatements().size();
                    sb.append(String.format("-- %s View: [%s]\n", d.getStatus() == DiffStatus.MISSING_IN_TARGET ? "Tạo mới" : "Cập nhật", d.getName()));
                    sb.append(d.getMigrationSql().trim()).append("\n\n");
                }
            }

            // 2.6 Procedures
            List<SchemaObjectDiff> procs = nonDropDiffs.stream()
                    .filter(d -> d.getObjectType() == SchemaObjectType.PROCEDURE)
                    .sorted(Comparator.comparing(SchemaObjectDiff::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            if (!procs.isEmpty()) {
                sb.append("-- ────────────────────────────────────────────────────────────────────\n");
                sb.append("-- PHASE 2.6: PROCEDURES\n");
                sb.append("-- ────────────────────────────────────────────────────────────────────\n\n");

                for (SchemaObjectDiff d : procs) {
                    stmtCount += d.getStatements().isEmpty() ? 1 : d.getStatements().size();
                    sb.append(String.format("-- %s Stored Procedure: [%s]\n", d.getStatus() == DiffStatus.MISSING_IN_TARGET ? "Tạo mới" : "Cập nhật", d.getName()));
                    sb.append(d.getMigrationSql().trim()).append("\n\n");
                }
            }

            // 2.7 Triggers
            List<SchemaObjectDiff> triggers = nonDropDiffs.stream()
                    .filter(d -> d.getObjectType() == SchemaObjectType.TRIGGER)
                    .sorted(Comparator.comparing(SchemaObjectDiff::getName, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            if (!triggers.isEmpty()) {
                sb.append("-- ────────────────────────────────────────────────────────────────────\n");
                sb.append("-- PHASE 2.7: TRIGGERS\n");
                sb.append("-- ────────────────────────────────────────────────────────────────────\n\n");

                for (SchemaObjectDiff d : triggers) {
                    stmtCount += d.getStatements().isEmpty() ? 1 : d.getStatements().size();
                    sb.append(String.format("-- %s Trigger: [%s]\n", d.getStatus() == DiffStatus.MISSING_IN_TARGET ? "Tạo mới" : "Cập nhật", d.getName()));
                    sb.append(d.getMigrationSql().trim()).append("\n\n");
                }
            }
        }

        if (wrapTransaction) {
            if (isSqlServer) {
                sb.append("COMMIT TRANSACTION;\n");
                sb.append("GO\n");
            } else {
                sb.append("COMMIT;\n");
            }
        }

        return new MigrationScriptResult(sb.toString(), stmtCount, hasDestructive, activeDiffs.size());
    }

    /**
     * Builds mock snapshots for testing and demo modes without requiring live database connections.
     */
    public SchemaSnapshot createMockSnapshot(String engine, String dbName, String schemaName, boolean isSource) {
        SchemaSnapshot snap = new SchemaSnapshot(engine, dbName, schemaName);

        // Table 1: orders
        TableDesign orders = new TableDesign();
        orders.setEngine(engine);
        orders.setSchema(schemaName);
        orders.setTable("orders");

        ColumnDesign colId = ColumnDesign.of("id", "int");
        colId.setPrimaryKey(true);
        colId.setNotNull(true);
        orders.getColumns().add(colId);

        ColumnDesign colCust = ColumnDesign.of("customer_id", "int");
        colCust.setNotNull(true);
        orders.getColumns().add(colCust);

        if (isSource) {
            ColumnDesign colCode = ColumnDesign.of("order_code", "varchar");
            colCode.setLength(32);
            colCode.setNotNull(true);
            orders.getColumns().add(colCode);
        }

        ColumnDesign colAmt = ColumnDesign.of("amount", "numeric");
        colAmt.setScale(2);
        colAmt.setNotNull(true);
        orders.getColumns().add(colAmt);

        ColumnDesign colStatus = ColumnDesign.of("status", "varchar");
        colStatus.setLength(20);
        colStatus.setDefaultValue("'active'");
        colStatus.setNotNull(false);
        orders.getColumns().add(colStatus);

        if (isSource) {
            ColumnDesign colNotes = ColumnDesign.of("notes", "nvarchar");
            colNotes.setLength(500);
            colNotes.setNotNull(false);
            orders.getColumns().add(colNotes);
        }

        ColumnDesign colCreated = ColumnDesign.of("created_at", "datetime2");
        colCreated.setDefaultValue("GETDATE()");
        colCreated.setNotNull(false);
        orders.getColumns().add(colCreated);

        snap.addTable(orders);

        if (isSource) {
            // Table 2 (Source only): audit_events
            TableDesign audit = new TableDesign();
            audit.setEngine(engine);
            audit.setSchema(schemaName);
            audit.setTable("audit_events");

            ColumnDesign colEvtId = ColumnDesign.of("event_id", "bigint");
            colEvtId.setPrimaryKey(true);
            colEvtId.setNotNull(true);
            audit.getColumns().add(colEvtId);

            ColumnDesign colEvtName = ColumnDesign.of("event_name", "varchar");
            colEvtName.setLength(100);
            colEvtName.setNotNull(true);
            audit.getColumns().add(colEvtName);

            ColumnDesign colEvtDate = ColumnDesign.of("created_at", "datetime2");
            colEvtDate.setDefaultValue("GETDATE()");
            colEvtDate.setNotNull(false);
            audit.getColumns().add(colEvtDate);

            snap.addTable(audit);
        } else {
            // Table 3 (Target only): legacy_logs
            TableDesign legacy = new TableDesign();
            legacy.setEngine(engine);
            legacy.setSchema(schemaName);
            legacy.setTable("legacy_logs");

            ColumnDesign colLogId = ColumnDesign.of("log_id", "int");
            colLogId.setPrimaryKey(true);
            colLogId.setNotNull(true);
            legacy.getColumns().add(colLogId);

            ColumnDesign colMsg = ColumnDesign.of("message", "text");
            colMsg.setNotNull(false);
            legacy.getColumns().add(colMsg);

            snap.addTable(legacy);
        }

        // View: v_customer_orders
        if (isSource) {
            snap.addView("v_customer_orders",
                    "CREATE VIEW dbo.v_customer_orders AS\n" +
                    "SELECT c.id AS customer_id, c.name, o.id AS order_id, o.order_code, o.amount, o.status\n" +
                    "FROM dbo.customers c\n" +
                    "JOIN dbo.orders o ON o.customer_id = c.id;");
        } else {
            snap.addView("v_customer_orders",
                    "CREATE VIEW dbo.v_customer_orders AS\n" +
                    "SELECT c.id AS customer_id, c.name, o.id AS order_id, o.amount, o.status\n" +
                    "FROM dbo.customers c\n" +
                    "JOIN dbo.orders o ON o.customer_id = c.id;");
        }

        // Procedure: usp_sync_stock (Source only)
        if (isSource) {
            snap.addProcedure("usp_sync_stock",
                    "CREATE PROCEDURE dbo.usp_sync_stock\n" +
                    "    @WarehouseId INT\n" +
                    "AS\n" +
                    "BEGIN\n" +
                    "    SET NOCOUNT ON;\n" +
                    "    UPDATE dbo.stock SET last_synced = GETDATE() WHERE warehouse_id = @WarehouseId;\n" +
                    "END;");
        }

        return snap;
    }
}
