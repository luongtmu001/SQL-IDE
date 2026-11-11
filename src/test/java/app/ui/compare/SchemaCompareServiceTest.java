package app.ui.compare;

import com.lltool.core.application.schemacompare.SchemaCompareService;
import com.lltool.core.domain.schemacompare.model.*;
import com.lltool.core.domain.tabledesigner.model.*;
import com.lltool.core.domain.tabledesigner.valueobject.ForeignKeyAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SchemaCompareService Tests")
class SchemaCompareServiceTest {

    private SchemaCompareService compareService;

    @BeforeEach
    void setUp() {
        compareService = new SchemaCompareService();
    }

    @Test
    @DisplayName("Compare mock snapshots correctly detects additions, modifications and deletions")
    void testCompareMockSnapshots() {
        SchemaSnapshot source = compareService.createMockSnapshot("sqlserver", "DEV", "dbo", true);
        SchemaSnapshot target = compareService.createMockSnapshot("sqlserver", "PROD", "dbo", false);

        SchemaCompareResult result = compareService.compare(source, target);
        assertNotNull(result);

        SchemaCompareSummary summary = result.getSummary();
        assertNotNull(summary);

        // Verify summary
        assertTrue(summary.hasDestructiveChanges(), "Should detect destructive changes (dropping legacy_logs)");
        assertEquals(2, summary.differentCount(), "orders and v_customer_orders should be DIFFERENT");
        assertEquals(2, summary.missingInTargetCount(), "audit_events and usp_sync_stock should be MISSING_IN_TARGET");
        assertEquals(1, summary.extraInTargetCount(), "legacy_logs should be EXTRA_IN_TARGET");

        // Verify specific object diffs
        SchemaObjectDiff ordersDiff = result.getDiffs().stream()
                .filter(d -> "orders".equals(d.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(ordersDiff);
        assertEquals(DiffStatus.DIFFERENT, ordersDiff.getStatus());
        assertEquals(DiffAction.ALTER, ordersDiff.getAction());
        assertFalse(ordersDiff.getColumnDiffs().isEmpty(), "orders should have column differences");

        SchemaObjectDiff auditDiff = result.getDiffs().stream()
                .filter(d -> "audit_events".equals(d.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(auditDiff);
        assertEquals(DiffStatus.MISSING_IN_TARGET, auditDiff.getStatus());
        assertEquals(DiffAction.CREATE, auditDiff.getAction());

        SchemaObjectDiff legacyDiff = result.getDiffs().stream()
                .filter(d -> "legacy_logs".equals(d.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(legacyDiff);
        assertEquals(DiffStatus.EXTRA_IN_TARGET, legacyDiff.getStatus());
        assertEquals(DiffAction.DROP, legacyDiff.getAction());
        assertTrue(legacyDiff.isDestructive());

        SchemaObjectDiff viewDiff = result.getDiffs().stream()
                .filter(d -> "v_customer_orders".equals(d.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(viewDiff);
        assertEquals(DiffStatus.DIFFERENT, viewDiff.getStatus());
        assertEquals(DiffAction.ALTER, viewDiff.getAction());

        SchemaObjectDiff procDiff = result.getDiffs().stream()
                .filter(d -> "usp_sync_stock".equals(d.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(procDiff);
        assertEquals(DiffStatus.MISSING_IN_TARGET, procDiff.getStatus());
        assertEquals(DiffAction.CREATE, procDiff.getAction());
    }

    @Test
    @DisplayName("Generate migration script wraps in atomic transaction and excludes destructive drop by default")
    void testGenerateMigrationScript() {
        SchemaSnapshot source = compareService.createMockSnapshot("sqlserver", "DEV", "dbo", true);
        SchemaSnapshot target = compareService.createMockSnapshot("sqlserver", "PROD", "dbo", false);

        SchemaCompareResult result = compareService.compare(source, target);

        // Generate without drop
        MigrationScriptResult scriptResult = compareService.generateMigrationScript(
                result, null, false, true
        );

        assertNotNull(scriptResult);
        String sql = scriptResult.sql();

        assertTrue(sql.contains("BEGIN TRANSACTION"), "Should contain BEGIN TRANSACTION");
        assertTrue(sql.contains("COMMIT TRANSACTION"), "Should contain COMMIT TRANSACTION");
        assertTrue(sql.contains("SET XACT_ABORT ON"), "Should contain SET XACT_ABORT ON");

        // Should include audit_events (create) and orders (alter) and v_customer_orders and usp_sync_stock
        assertTrue(sql.contains("audit_events"), "Should migrate audit_events");
        assertTrue(sql.contains("orders"), "Should migrate orders");
        assertTrue(sql.contains("v_customer_orders"), "Should migrate view");
        assertTrue(sql.contains("usp_sync_stock"), "Should migrate proc");

        // Should NOT include drop table legacy_logs
        assertFalse(sql.contains("DROP TABLE [dbo].[legacy_logs]"), "Should NOT drop legacy_logs by default");
    }

    @Test
    @DisplayName("Generate migration script includes DROP statement when includeDrop is explicitly enabled")
    void testGenerateMigrationScriptWithDrop() {
        SchemaSnapshot source = compareService.createMockSnapshot("sqlserver", "DEV", "dbo", true);
        SchemaSnapshot target = compareService.createMockSnapshot("sqlserver", "PROD", "dbo", false);

        SchemaCompareResult result = compareService.compare(source, target);

        // Generate with drop
        MigrationScriptResult scriptResult = compareService.generateMigrationScript(
                result, null, true, true
        );

        assertNotNull(scriptResult);
        String sql = scriptResult.sql();

        assertTrue(sql.contains("DROP TABLE [dbo].[legacy_logs]"), "Should drop legacy_logs when includeDrop=true");
        assertTrue(scriptResult.hasDestructive());
    }

    @Test
    @DisplayName("Engine mismatch throws IllegalArgumentException")
    void testEngineMismatchThrows() {
        SchemaSnapshot source = compareService.createMockSnapshot("sqlserver", "DEV", "dbo", true);
        SchemaSnapshot target = compareService.createMockSnapshot("postgresql", "PROD", "public", false);

        assertThrows(IllegalArgumentException.class, () -> compareService.compare(source, target));
    }

    @Test
    @DisplayName("Selective migration script only generates DDL for specified object names")
    void testSelectiveMigration() {
        SchemaSnapshot source = compareService.createMockSnapshot("sqlserver", "DEV", "dbo", true);
        SchemaSnapshot target = compareService.createMockSnapshot("sqlserver", "PROD", "dbo", false);

        SchemaCompareResult result = compareService.compare(source, target);

        // Only select orders
        Set<String> selected = Set.of("dbo.orders");
        MigrationScriptResult scriptResult = compareService.generateMigrationScript(
                result, selected, false, false
        );

        assertEquals(1, scriptResult.objectCount());
        assertTrue(scriptResult.sql().contains("orders"));
        assertFalse(scriptResult.sql().contains("audit_events"));
        assertFalse(scriptResult.sql().contains("usp_sync_stock"));
    }

    @Test
    @DisplayName("SC-4: PostgreSQL specific DDL generation for trigger, function overload, and view alter")
    void testPostgresDdlGeneration_SC4() {
        SchemaSnapshot src = new SchemaSnapshot("postgresql", "app_db", "public");
        src.addFunction("calc_tax", "CREATE FUNCTION public.calc_tax(rate numeric) RETURNS numeric AS $$ SELECT $1 * 0.1 $$ LANGUAGE sql;", "rate numeric");
        src.addView("v_report", "CREATE VIEW public.v_report AS SELECT 1 AS id, 'NEW' AS status;");
        src.addTrigger("trg_audit", "CREATE TRIGGER trg_audit AFTER INSERT ON public.orders FOR EACH ROW EXECUTE PROCEDURE audit_log();", "orders");

        SchemaSnapshot tgt = new SchemaSnapshot("postgresql", "app_db", "public");
        tgt.addFunction("calc_tax", "CREATE FUNCTION public.calc_tax(rate numeric) RETURNS numeric AS $$ SELECT $1 * 0.05 $$ LANGUAGE sql;", "rate numeric");
        tgt.addView("v_report", "CREATE VIEW public.v_report AS SELECT 1 AS id;");
        tgt.addTrigger("trg_audit", "CREATE TRIGGER trg_audit AFTER UPDATE ON public.orders FOR EACH ROW EXECUTE PROCEDURE audit_log();", "orders");
        // Extra trigger to drop
        tgt.addTrigger("trg_old", "CREATE TRIGGER trg_old AFTER DELETE ON public.logs FOR EACH ROW EXECUTE PROCEDURE audit_log();", "logs");

        SchemaCompareResult result = compareService.compare(src, tgt);
        assertNotNull(result);

        // Check trg_old drop statement has ON "public"."logs"
        SchemaObjectDiff oldTrgDiff = result.getDiffs().stream()
                .filter(d -> "trg_old".equals(d.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(oldTrgDiff);
        assertEquals(DiffAction.DROP, oldTrgDiff.getAction());
        assertTrue(oldTrgDiff.getMigrationSql().contains("ON \"public\".\"logs\""),
                "Postgres DROP TRIGGER must include ON parent_table: " + oldTrgDiff.getMigrationSql());

        // Check v_report alter has DROP VIEW CASCADE before create
        SchemaObjectDiff viewDiff = result.getDiffs().stream()
                .filter(d -> "v_report".equals(d.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(viewDiff);
        assertEquals(DiffAction.ALTER, viewDiff.getAction());
        assertTrue(viewDiff.getMigrationSql().contains("DROP VIEW IF EXISTS \"public\".\"v_report\" CASCADE;"),
                "Postgres altered view must drop cascade before replace to allow column modifications: " + viewDiff.getMigrationSql());
    }

    @Test
    @DisplayName("SC-5: Code normalization preserves whitespace inside string literals, case, and handles function overloads")
    void testCodeNormalization_SC5() {
        // Test literal preservation
        String ddl1 = "SELECT 'Hello   World' FROM users;";
        String ddl2 = "SELECT 'Hello World' FROM users;";
        assertNotEquals(SchemaCompareService.normalizeCode(ddl1, true, false),
                        SchemaCompareService.normalizeCode(ddl2, true, false),
                        "Whitespace inside single-quoted string literals must be preserved verbatim");

        // Test case preservation
        String ddlCase1 = "SELECT 'active' AS status, Code FROM t WHERE val = 'A';";
        String ddlCase2 = "SELECT 'active' AS status, Code FROM t WHERE val = 'a';";
        assertNotEquals(SchemaCompareService.normalizeCode(ddlCase1, true, false),
                        SchemaCompareService.normalizeCode(ddlCase2, true, false),
                        "Case inside and outside strings must be preserved for exact matching");

        // Overloaded functions in PostgreSQL
        SchemaSnapshot src = new SchemaSnapshot("postgresql", "app_db", "public");
        src.addFunction("calc_discount", "CREATE FUNCTION public.calc_discount(p int) ...", "p int");
        src.addFunction("calc_discount", "CREATE FUNCTION public.calc_discount(p int, rate numeric) ...", "p int, rate numeric");

        SchemaSnapshot tgt = new SchemaSnapshot("postgresql", "app_db", "public");
        tgt.addFunction("calc_discount", "CREATE FUNCTION public.calc_discount(p int) ...", "p int");

        SchemaCompareResult res = compareService.compare(src, tgt);
        // Overload with (p int, rate numeric) should be MISSING_IN_TARGET and not overwrite (p int)
        assertEquals(2, res.getDiffs().size(), "Both function overloads must be preserved as separate entries");
        SchemaObjectDiff missingOverload = res.getDiffs().stream()
                .filter(d -> d.getName().contains("rate numeric"))
                .findFirst()
                .orElse(null);
        assertNotNull(missingOverload);
        assertEquals(DiffStatus.MISSING_IN_TARGET, missingOverload.getStatus());
    }

    @Test
    @DisplayName("SC-6: Migration script orders statements: Table creates -> Alters -> FK constraints -> Code objects; Drops first")
    void testMigrationScriptOrdering_SC6() {
        SchemaSnapshot src = new SchemaSnapshot("sqlserver", "DEV", "dbo");

        // Table B (referenced)
        com.lltool.core.domain.tabledesigner.model.TableDesign tableB = new com.lltool.core.domain.tabledesigner.model.TableDesign("sqlserver", "dbo", "categories");
        tableB.getColumns().add(com.lltool.core.domain.tabledesigner.model.ColumnDesign.of("id", "int"));
        src.addTable(tableB);

        // Table A (has FK to Table B)
        com.lltool.core.domain.tabledesigner.model.TableDesign tableA = new com.lltool.core.domain.tabledesigner.model.TableDesign("sqlserver", "dbo", "products");
        tableA.getColumns().add(com.lltool.core.domain.tabledesigner.model.ColumnDesign.of("id", "int"));
        tableA.getColumns().add(com.lltool.core.domain.tabledesigner.model.ColumnDesign.of("cat_id", "int"));
        ForeignKeyDesign fk = new ForeignKeyDesign(
                "FK_prod_cat", "dbo", "categories",
                List.of(new ForeignKeyColumnMapping("cat_id", "id"))
        );
        fk.setOnDelete(ForeignKeyAction.NO_ACTION);
        fk.setOnUpdate(ForeignKeyAction.NO_ACTION);
        tableA.getForeignKeys().add(fk);
        src.addTable(tableA);

        // Function and View
        src.addFunction("fn_get_price", "CREATE FUNCTION dbo.fn_get_price() RETURNS INT AS BEGIN RETURN 10; END;");
        src.addView("v_product_list", "CREATE VIEW dbo.v_product_list AS SELECT p.id, c.id AS cid FROM dbo.products p JOIN dbo.categories c ON p.cat_id = c.id;");

        SchemaSnapshot tgt = new SchemaSnapshot("sqlserver", "PROD", "dbo");
        // Target has old trigger to be dropped
        tgt.addTrigger("trg_deprecated", "CREATE TRIGGER dbo.trg_deprecated ON dbo.legacy FOR DELETE AS BEGIN SET NOCOUNT ON; END;");

        SchemaCompareResult compareResult = compareService.compare(src, tgt);
        MigrationScriptResult scriptResult = compareService.generateMigrationScript(compareResult, null, true, true);

        String sql = scriptResult.sql();
        assertNotNull(sql);

        // Phase 1 (Drop) must appear before Phase 2
        int dropIndex = sql.indexOf("PHASE 1: DROP STATEMENTS");
        int createTablesIndex = sql.indexOf("PHASE 2.1: CREATE TABLES");
        int fkIndex = sql.indexOf("PHASE 2.3: FOREIGN KEYS");
        int funcIndex = sql.indexOf("PHASE 2.4: FUNCTIONS");
        int viewIndex = sql.indexOf("PHASE 2.5: VIEWS");

        assertTrue(dropIndex >= 0, "Script should have Phase 1 DROP");
        assertTrue(createTablesIndex > dropIndex, "CREATE TABLES must appear after DROPs");
        assertTrue(fkIndex > createTablesIndex, "FOREIGN KEYS must appear after CREATE TABLES");
        assertTrue(funcIndex > fkIndex, "FUNCTIONS must appear after FOREIGN KEYS");
        assertTrue(viewIndex > funcIndex, "VIEWS must appear after FUNCTIONS");

        // Verify products create table doesn't inline FK constraint before categories is created
        int createProductsIdx = sql.indexOf("CREATE TABLE [dbo].[products]");
        int fkConstraintIdx = sql.indexOf("ALTER TABLE [dbo].[products] ADD CONSTRAINT [FK_prod_cat]");
        assertTrue(createProductsIdx >= 0 && fkConstraintIdx > createProductsIdx,
                "Foreign key constraint must be added after table creation");
    }

    @Test
    @DisplayName("SC-9: Destructive changes are not selected by default and include warning comments")
    void testDestructiveChangesDefault_SC9() {
        SchemaSnapshot src = compareService.createMockSnapshot("sqlserver", "DEV", "dbo", true);
        SchemaSnapshot tgt = compareService.createMockSnapshot("sqlserver", "PROD", "dbo", false);

        SchemaCompareResult result = compareService.compare(src, tgt);

        // Drop table legacy_logs is destructive -> isSelected() must be false
        SchemaObjectDiff dropTable = result.getDiffs().stream()
                .filter(d -> "legacy_logs".equals(d.getName()))
                .findFirst()
                .orElse(null);
        assertNotNull(dropTable);
        assertTrue(dropTable.isDestructive(), "legacy_logs drop should be marked destructive");
        assertFalse(dropTable.isSelected(), "Destructive changes must be unselected by default (SC-9)");

        // Generating script with includeDrop=true must output warning comment
        MigrationScriptResult scriptResult = compareService.generateMigrationScript(result, null, true, true);
        assertTrue(scriptResult.sql().contains("[CẢNH BÁO: THAO TÁC PHÁ HỦY / DESTRUCTIVE - CÓ THỂ MẤT DỮ LIỆU]"),
                "Script must include warning comment for destructive drop statements");
    }
}

