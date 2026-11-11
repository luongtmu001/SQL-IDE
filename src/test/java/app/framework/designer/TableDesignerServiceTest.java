package app.framework.designer;

import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.core.application.tabledesigner.model.MigrationPlan;
import com.lltool.core.common.result.Result;
import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.model.TableDesign;
import com.lltool.core.domain.tabledesigner.valueobject.IndexType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TableDesignerServiceTest {

    private final TableDesignerService service = TableDesignerService.getInstance();

    private ConnectionProfileDto createTestProfile(String engine, boolean readOnly) {
        return new ConnectionProfileDto(
                "test-prof-1",
                "Test Server",
                "Default",
                "DEV",
                readOnly,
                engine,
                "localhost",
                1433,
                "master",
                null,
                "sa",
                "SQL_AUTH",
                "cred-1",
                false,
                15,
                false,
                Map.of(),
                "2026-11-09T00:00:00Z",
                "2026-11-09T00:00:00Z"
        );
    }

    @Test
    @DisplayName("Should create realistic mock TableDesign for SQL Server")
    void testMockTableDesignSqlServer() {
        TableDesign design = service.createMockTableDesign("sqlserver", "master", "dbo", "Customers");
        assertNotNull(design);
        assertEquals("sqlserver", design.getEngine());
        assertEquals("dbo", design.getSchema());
        assertEquals("Customers", design.getTable());
        assertEquals(8, design.getColumns().size());

        ColumnDesign pk = design.findColumnByName("Id");
        assertNotNull(pk);
        assertTrue(pk.isPrimaryKey());
        assertTrue(pk.isIdentity());

        assertNotNull(design.findColumnByName("CustomerCode"));
        assertNotNull(design.findColumnByName("FullName"));
        assertNotNull(design.findColumnByName("Email"));
        assertNotNull(design.findColumnByName("Balance"));
        assertEquals("0.00", design.findColumnByName("Balance").getDefaultValue());

        assertEquals(1, design.getIndexes().size());
        assertEquals(IndexType.NONCLUSTERED, design.getIndexes().get(0).getIndexType());
        assertEquals(1, design.getUniques().size());
        assertEquals(1, design.getChecks().size());
        assertEquals(1, design.getTriggers().size());
    }

    @Test
    @DisplayName("Should create realistic mock TableDesign for PostgreSQL")
    void testMockTableDesignPostgreSql() {
        TableDesign design = service.createMockTableDesign("postgresql", "postgres", "public", "customers");
        assertNotNull(design);
        assertEquals("postgresql", design.getEngine());
        assertEquals("public", design.getSchema());
        assertEquals("customers", design.getTable());

        ColumnDesign pk = design.findColumnByName("id");
        assertNotNull(pk);
        assertTrue(pk.isPrimaryKey());
        assertEquals("serial", pk.getDataType());

        assertEquals(1, design.getIndexes().size());
        assertEquals(IndexType.BTREE, design.getIndexes().get(0).getIndexType()); // Fix B3
    }

    @Test
    @DisplayName("Should generate migration plan when valid")
    void testGenerateMigrationPlanSuccess() {
        TableDesign orig = service.createMockTableDesign("sqlserver", "master", "dbo", "Customers");
        TableDesign mod = orig.deepCopy();

        // Add a new column
        ColumnDesign newCol = ColumnDesign.of("LoyaltyPoints", "int");
        newCol.setDefaultValue("0");
        mod.addColumn(newCol);

        Result<MigrationPlan> planRes = service.generateMigrationPlan(orig, mod);
        assertTrue(planRes.isSuccess());
        MigrationPlan plan = planRes.getDataOrThrow();
        assertFalse(plan.statements().isEmpty());

        boolean hasAddCol = plan.statements().stream()
                .anyMatch(s -> s.sql().contains("ADD [LoyaltyPoints] int"));
        assertTrue(hasAddCol, "Plan should contain ADD LoyaltyPoints statement");
    }

    @Test
    @DisplayName("Should reject migration plan when model violates validation (Bug B13)")
    void testGenerateMigrationPlanValidationError() {
        TableDesign orig = service.createMockTableDesign("sqlserver", "master", "dbo", "Customers");
        TableDesign mod = orig.deepCopy();

        // Duplicate column name
        ColumnDesign dup = ColumnDesign.of("Email", "varchar");
        mod.addColumn(dup);

        Result<MigrationPlan> planRes = service.generateMigrationPlan(orig, mod);
        assertTrue(planRes.isFailure());
        assertEquals("VALIDATION_ERROR", planRes.getError().get().code());
        assertTrue(planRes.getError().get().message().contains("Email"));
    }

    @Test
    @DisplayName("Should block migration apply when read-only guardrail is active")
    void testApplyMigrationReadOnlyGuardrail() {
        String prevProp = System.getProperty("sqlide.readOnly");
        try {
            System.setProperty("sqlide.readOnly", "true");
            ConnectionProfileDto profile = createTestProfile("SQLSERVER", false);
            TableDesign orig = service.createMockTableDesign("sqlserver", "master", "dbo", "Customers");
            TableDesign mod = orig.deepCopy();
            mod.addColumn(ColumnDesign.of("Notes", "nvarchar"));

            MigrationPlan plan = service.generateMigrationPlan(orig, mod).getDataOrThrow();

            Result<Void> applyRes = service.applyMigration(profile, "master", plan, null);
            assertTrue(applyRes.isFailure());
            assertEquals("READ_ONLY_GUARDRAIL", applyRes.getError().get().code());
        } finally {
            if (prevProp != null) System.setProperty("sqlide.readOnly", prevProp);
            else System.clearProperty("sqlide.readOnly");
        }
    }

    @Test
    @DisplayName("Should block migration apply when profile is explicitly read-only")
    void testApplyMigrationProfileReadOnly() {
        String prevProp = System.getProperty("sqlide.readOnly");
        try {
            System.setProperty("sqlide.readOnly", "false");
            ConnectionProfileDto profile = createTestProfile("SQLSERVER", true);

            TableDesign orig = service.createMockTableDesign("sqlserver", "master", "dbo", "Customers");
            TableDesign mod = orig.deepCopy();
            mod.addColumn(ColumnDesign.of("Notes", "nvarchar"));

            MigrationPlan plan = service.generateMigrationPlan(orig, mod).getDataOrThrow();

            Result<Void> applyRes = service.applyMigration(profile, "master", plan, null);
            assertTrue(applyRes.isFailure());
            assertEquals("READ_ONLY_PROFILE", applyRes.getError().get().code());
        } finally {
            if (prevProp != null) {
                System.setProperty("sqlide.readOnly", prevProp);
            } else {
                System.clearProperty("sqlide.readOnly");
            }
        }
    }

    @Test
    @DisplayName("Should clone TableDesign for copy with options via TableDesignerService (TD5)")
    void testCloneForCopyViaService() {
        TableDesign orig = service.createMockTableDesign("sqlserver", "master", "dbo", "Customers");
        com.lltool.core.domain.tabledesigner.model.CopyTableOptions opts =
                new com.lltool.core.domain.tabledesigner.model.CopyTableOptions(true, true, true, false, true, false);

        Result<TableDesign> copyRes = service.cloneForCopy(orig, "sales", "Customers_Backup", opts);
        assertTrue(copyRes.isSuccess());
        TableDesign copy = copyRes.getDataOrThrow();
        assertNotNull(copy);
        assertEquals("sales", copy.getSchema());
        assertEquals("Customers_Backup", copy.getTable());
        assertNull(copy.getOriginalTable());

        // FKs should be excluded because copyForeignKeys was false
        assertTrue(copy.getForeignKeys().isEmpty());
        // Indexes should be included with safe identifier suffix
        assertFalse(copy.getIndexes().isEmpty());
        assertTrue(copy.getIndexes().get(0).getName().endsWith("_Copy"));
    }
}
