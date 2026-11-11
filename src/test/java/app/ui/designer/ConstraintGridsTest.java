package app.ui.designer;

import app.framework.designer.TableDesignerService;
import com.lltool.core.domain.tabledesigner.model.*;
import com.lltool.core.domain.tabledesigner.valueobject.ForeignKeyAction;
import com.lltool.core.domain.tabledesigner.valueobject.IndexType;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ConstraintGridsTest {

    @BeforeAll
    static void initJavaFX() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException ignored) {
            Platform.setImplicitExit(false);
            latch.countDown();
        }
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("IndexGrid loads items and converts to domain models properly")
    void testIndexGrid() {
        AtomicBoolean changed = new AtomicBoolean(false);
        IndexGrid grid = new IndexGrid("sqlserver", () -> changed.set(true));

        IndexDesign idx1 = new IndexDesign("IX_Users_Email");
        idx1.setUnique(true);
        idx1.setIndexType(IndexType.NONCLUSTERED);
        idx1.getColumns().add(new IndexColumn(null, "email", false));
        idx1.getColumns().add(new IndexColumn(null, "created_at", true));

        grid.setItems(List.of(idx1));

        List<IndexDesign> converted = grid.toDomainIndexes();
        assertEquals(1, converted.size());
        assertEquals("IX_Users_Email", converted.get(0).getName());
        assertTrue(converted.get(0).isUnique());
        assertEquals(2, converted.get(0).getColumns().size());
        assertEquals("email", converted.get(0).getColumns().get(0).getColumnName());
        assertFalse(converted.get(0).getColumns().get(0).isDescending());
        assertEquals("created_at", converted.get(0).getColumns().get(1).getColumnName());
        assertTrue(converted.get(0).getColumns().get(1).isDescending());
    }

    @Test
    @DisplayName("ForeignKeyGrid loads items and converts to domain models properly")
    void testForeignKeyGrid() {
        AtomicBoolean changed = new AtomicBoolean(false);
        ForeignKeyGrid grid = new ForeignKeyGrid("sqlserver", () -> changed.set(true));

        ForeignKeyDesign fk = new ForeignKeyDesign("FK_Orders_Users");
        fk.setTargetSchema("dbo");
        fk.setTargetTable("Users");
        fk.getColumnMappings().add(new ForeignKeyColumnMapping("user_id", "id"));
        fk.setOnDelete(ForeignKeyAction.CASCADE);

        grid.setItems(List.of(fk));

        List<ForeignKeyDesign> converted = grid.toDomainForeignKeys();
        assertEquals(1, converted.size());
        assertEquals("FK_Orders_Users", converted.get(0).getName());
        assertEquals("dbo", converted.get(0).getTargetSchema());
        assertEquals("Users", converted.get(0).getTargetTable());
        assertEquals(ForeignKeyAction.CASCADE, converted.get(0).getOnDelete());
        assertEquals(1, converted.get(0).getColumnMappings().size());
        assertEquals("user_id", converted.get(0).getColumnMappings().get(0).getSourceColumn());
        assertEquals("id", converted.get(0).getColumnMappings().get(0).getTargetColumn());
    }

    @Test
    @DisplayName("UniqueConstraintGrid loads items and converts to domain models properly")
    void testUniqueConstraintGrid() {
        AtomicBoolean changed = new AtomicBoolean(false);
        UniqueConstraintGrid grid = new UniqueConstraintGrid("sqlserver", () -> changed.set(true));

        UniqueDesign uq = new UniqueDesign("UQ_Users_Code", List.of("org_id", "user_code"), false);
        grid.setItems(List.of(uq));

        List<UniqueDesign> converted = grid.toDomainUniques();
        assertEquals(1, converted.size());
        assertEquals("UQ_Users_Code", converted.get(0).getName());
        assertEquals(2, converted.get(0).getColumns().size());
        assertEquals("org_id", converted.get(0).getColumns().get(0));
        assertEquals("user_code", converted.get(0).getColumns().get(1));
    }

    @Test
    @DisplayName("CheckConstraintGrid loads items and converts to domain models properly")
    void testCheckConstraintGrid() {
        AtomicBoolean changed = new AtomicBoolean(false);
        CheckConstraintGrid grid = new CheckConstraintGrid("sqlserver", () -> changed.set(true));

        CheckDesign ck = new CheckDesign("CK_Price", "[Price] > 0");
        grid.setItems(List.of(ck));

        List<CheckDesign> converted = grid.toDomainChecks();
        assertEquals(1, converted.size());
        assertEquals("CK_Price", converted.get(0).getName());
        assertEquals("[Price] > 0", converted.get(0).getCheckClause());
    }

    @Test
    @DisplayName("TableDesignerService returns supported types for Postgres and SQL Server")
    void testSupportedTypes() {
        TableDesignerService service = TableDesignerService.getInstance();

        List<String> pgTypes = service.getSupportedTypes("postgresql");
        assertNotNull(pgTypes);
        assertTrue(pgTypes.contains("integer"));
        assertTrue(pgTypes.contains("uuid"));
        assertTrue(pgTypes.contains("jsonb"));

        List<String> msTypes = service.getSupportedTypes("sqlserver");
        assertNotNull(msTypes);
        assertTrue(msTypes.contains("nvarchar"));
        assertTrue(msTypes.contains("datetime2"));
        assertTrue(msTypes.contains("uniqueidentifier"));
    }
}
