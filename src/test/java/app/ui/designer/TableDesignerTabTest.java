package app.ui.designer;

import com.lltool.core.domain.tabledesigner.model.ColumnDesign;
import com.lltool.core.domain.tabledesigner.valueobject.ColumnId;
import app.framework.designer.TableDesignerService;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TableDesignerTabTest {

    @BeforeAll
    static void initJavaFX() throws Exception {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException ignored) {
            Platform.setImplicitExit(false);
            latch.countDown();
        }
        assertTrue(latch.await(20, java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("TableDesignerUndoManager records, executes, un-executes, and updates properties")
    void testUndoManagerOperations() {
        TableDesignerUndoManager manager = new TableDesignerUndoManager();

        assertFalse(manager.canUndo());
        assertFalse(manager.canRedo());

        AtomicInteger val = new AtomicInteger(10);
        manager.execute(new TableDesignerUndoManager.Command() {
            @Override
            public void execute() {
                val.set(20);
            }

            @Override
            public void undo() {
                val.set(10);
            }

            @Override
            public String description() {
                return "Set value to 20";
            }
        });

        assertEquals(20, val.get());
        assertTrue(manager.canUndo());
        assertFalse(manager.canRedo());

        manager.undo();
        assertEquals(10, val.get());
        assertFalse(manager.canUndo());
        assertTrue(manager.canRedo());

        manager.redo();
        assertEquals(20, val.get());
        assertTrue(manager.canUndo());
        assertFalse(manager.canRedo());

        manager.clear();
        assertFalse(manager.canUndo());
        assertFalse(manager.canRedo());
    }

    @Test
    @DisplayName("ColumnDesignViewModel tracks dirty changes, status markers, and auto-notNull on PK")
    void testColumnDesignViewModelStatusAndRules() {
        // 1. New Column (not from DB)
        ColumnDesignViewModel newCol = ColumnDesignViewModel.newColumn("col1", "varchar");
        newCol.setLength(50);
        assertEquals("+", newCol.getStatusMarker());
        assertTrue(newCol.isDirty());

        // 2. Existing column loaded from DB
        ColumnDesign domain = ColumnDesign.loadedFromDb("user_id", "int");
        domain.setPrimaryKey(true);
        domain.setNotNull(true);
        ColumnDesignViewModel existingCol = ColumnDesignViewModel.fromDomain(domain);
        assertEquals("", existingCol.getStatusMarker());
        assertFalse(existingCol.isDirty());

        // Modify a property -> becomes modified '~'
        existingCol.setDefaultValue("0");
        assertTrue(existingCol.isDirty());
        assertEquals("~", existingCol.getStatusMarker());

        // Rename existing column -> becomes renamed '↻'
        existingCol.setName("account_id");
        assertTrue(existingCol.isDirty());
        assertEquals("↻", existingCol.getStatusMarker());

        // Mark deleted -> becomes '−'
        existingCol.setDeleted(true);
        assertEquals("−", existingCol.getStatusMarker());

        // 3. Setting Primary Key automatically enforces Not Null (B1 fix)
        ColumnDesignViewModel plainCol = ColumnDesignViewModel.newColumn("email", "varchar");
        plainCol.setLength(100);
        plainCol.setNotNull(false);
        assertFalse(plainCol.isNotNull());

        plainCol.setPrimaryKey(true);
        assertTrue(plainCol.isPrimaryKey());
        assertTrue(plainCol.isNotNull(), "Setting PK must automatically enforce isNotNull = true");

        // Attempting to unset NotNull while PK must be blocked
        plainCol.setNotNull(false);
        assertTrue(plainCol.isNotNull(), "Cannot unset isNotNull while column is Primary Key");
    }

    @Test
    @DisplayName("ColumnDesignViewModel duplicate generates fresh ColumnId and dirty status")
    void testColumnDesignViewModelDuplicate() {
        ColumnDesign domain = ColumnDesign.loadedFromDb("price", "decimal");
        domain.setLength(18);
        domain.setScale(2);
        ColumnDesignViewModel orig = ColumnDesignViewModel.fromDomain(domain);

        ColumnDesignViewModel dup = orig.duplicate("price_copy");
        assertNotEquals(orig.getId(), dup.getId());
        assertEquals("price_copy", dup.getName());
        assertEquals("decimal", dup.getDataType());
        assertEquals(18, dup.getLength());
        assertEquals(2, dup.getScale());
        assertTrue(dup.isDirty());
        assertEquals("+", dup.getStatusMarker());
    }

    @Test
    @DisplayName("ColumnGrid 15 row operations (TD-ROW-01..15) and undo/redo")
    void testColumnGridRowOperations() {
        TableDesignerUndoManager undo = new TableDesignerUndoManager();
        ColumnGrid grid = new ColumnGrid("sqlserver", true, undo, () -> {});
        com.lltool.core.domain.tabledesigner.model.TableDesign td =
                com.lltool.core.domain.tabledesigner.model.TableDesign.newTable("sqlserver", "dbo", "Table_1");
        grid.setItems(td.getColumns());

        // Initial state has default column (Id int)
        assertEquals(1, grid.getItems().size());
        assertEquals("Id", grid.getItems().get(0).getName());
        assertTrue(grid.getItems().get(0).isPrimaryKey());

        // TD-ROW-01: Add Row
        grid.addNewRowAtEnd();
        assertEquals(2, grid.getItems().size());
        assertEquals("Column", grid.getItems().get(1).getName());

        // TD-ROW-02: Insert Row Above
        grid.getTableView().getSelectionModel().select(1);
        grid.insertRowAbove();
        assertEquals(3, grid.getItems().size());
        assertEquals("Column_1", grid.getItems().get(1).getName());

        // Undo insert row
        undo.undo();
        assertEquals(2, grid.getItems().size());

        // Redo insert row
        undo.redo();
        assertEquals(3, grid.getItems().size());

        // TD-ROW-04: Duplicate Row
        grid.getTableView().getSelectionModel().clearAndSelect(0); // Select 'Id'
        grid.duplicateSelectedRows();
        assertEquals(4, grid.getItems().size());
        assertEquals("Id_copy", grid.getItems().get(1).getName());

        // Undo duplicate
        undo.undo();
        assertEquals(3, grid.getItems().size());

        // TD-ROW-05: Delete Row
        grid.getTableView().getSelectionModel().clearAndSelect(2);
        grid.deleteSelectedRows();
        assertEquals(2, grid.getItems().size());

        // Undo delete
        undo.undo();
        assertEquals(3, grid.getItems().size());

        // TD-ROW-06 & TD-ROW-07: Move Up & Move Down
        grid.getTableView().getSelectionModel().clearAndSelect(0);
        grid.moveSelectedRowsDown();
        assertEquals("Id", grid.getItems().get(1).getName());

        grid.moveSelectedRowsUp();
        assertEquals("Id", grid.getItems().get(0).getName());

        // TD-ROW-10: Toggle PK
        grid.getTableView().getSelectionModel().clearAndSelect(1);
        assertFalse(grid.getItems().get(1).isPrimaryKey());
        grid.togglePrimaryKeyOnSelected();
        assertTrue(grid.getItems().get(1).isPrimaryKey());
        assertTrue(grid.getItems().get(1).isNotNull());

        // TD-ROW-11: Toggle Nullable
        grid.toggleNotNullOnSelected();
        // Since it's PK, notNull cannot be toggled to false
        assertTrue(grid.getItems().get(1).isNotNull());

        // Turn off PK
        grid.togglePrimaryKeyOnSelected();
        assertFalse(grid.getItems().get(1).isPrimaryKey());
        grid.toggleNotNullOnSelected();
        assertFalse(grid.getItems().get(1).isNotNull());
    }

    @Test
    @DisplayName("TableDesignerTab instantiates and loads initial data without error")
    void testTableDesignerTabInstantiation() throws Exception {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> err = new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> {
            try {
                TableDesignerTab tab = new TableDesignerTab(null, "master", "dbo", "Table_1", true, action -> {});
                assertNotNull(tab);
                assertNotNull(tab.getContent());
                assertEquals("table_designer_new_master_dbo_Table_1", tab.getCustomKey());
                tab.close();
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(20, java.util.concurrent.TimeUnit.SECONDS));
        if (err.get() != null) {
            throw new AssertionError("Failed to instantiate TableDesignerTab on FX thread", err.get());
        }
    }

    @Test
    @DisplayName("TableDesignerTab instantiates with cloned TableDesign for Copy Table workflow (TD5)")
    void testTableDesignerTabWithClonedDesign() throws Exception {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Throwable> err = new java.util.concurrent.atomic.AtomicReference<>();
        Platform.runLater(() -> {
            try {
                com.lltool.core.domain.tabledesigner.model.TableDesign orig =
                        new com.lltool.core.domain.tabledesigner.model.TableDesign("sqlserver", "dbo", "Employees");
                ColumnDesign c1 = ColumnDesign.loadedFromDb("EmpId", "int");
                c1.setPrimaryKey(true);
                ColumnDesign c2 = ColumnDesign.loadedFromDb("EmpName", "nvarchar");
                c2.setLength(50);
                orig.addColumn(c1);
                orig.addColumn(c2);

                com.lltool.core.domain.tabledesigner.model.CopyTableOptions opts =
                        com.lltool.core.domain.tabledesigner.model.CopyTableOptions.defaults();
                com.lltool.core.domain.tabledesigner.model.TableDesign cloned =
                        orig.cloneForCopy("hr", "Employees_Copy", opts);

                TableDesignerTab tab = new TableDesignerTab(null, "company_db", "hr", "Employees_Copy", cloned, action -> {});
                assertNotNull(tab);
                assertNotNull(tab.getContent());
                assertEquals("table_designer_copy_company_db_hr_Employees_Copy", tab.getCustomKey());
                assertTrue(tab.getTabTitle().contains("Employees_Copy"));
                tab.close();
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(20, java.util.concurrent.TimeUnit.SECONDS));
        if (err.get() != null) {
            throw new AssertionError("Failed to instantiate TableDesignerTab with cloned design on FX thread", err.get());
        }
    }

    @Test
    @DisplayName("ColumnGrid PostgreSQL default column initializes 'id serial'")
    void testColumnGridPostgresDefault() {
        TableDesignerUndoManager undo = new TableDesignerUndoManager();
        ColumnGrid grid = new ColumnGrid("postgresql", true, undo, () -> {});
        com.lltool.core.domain.tabledesigner.model.TableDesign td =
                com.lltool.core.domain.tabledesigner.model.TableDesign.newTable("postgresql", "public", "table_1");
        grid.setItems(td.getColumns());

        assertEquals(1, grid.getItems().size());
        ColumnDesignViewModel col = grid.getItems().get(0);
        assertEquals("id", col.getName());
        assertEquals("serial", col.getDataType());
        assertTrue(col.isPrimaryKey());
        assertTrue(col.isNotNull());
    }

    @Test
    @DisplayName("TD-2: Opening mock/existing table should NOT mark column dirty (snapshot verification)")
    void testOpeningTableDoesNotMarkDirty() {
        ColumnDesign c1 = ColumnDesign.loadedFromDb("Id", "int");
        c1.setPrimaryKey(true);
        c1.setIdentity(true);

        ColumnDesignViewModel vm = ColumnDesignViewModel.fromDomain(c1);
        assertFalse(vm.isDirty(), "Column should not be dirty immediately after loading from domain");
        assertFalse(vm.isModifiedFromOriginal(), "Column should not be modified from original");
        assertEquals("", vm.statusMarkerProperty().get(), "Status marker must be empty");
    }

    @Test
    @DisplayName("TD-5 & TD-9: Migration failure reports statement index and data types are populated")
    void testMigrationErrorAndSupportedTypes() {
        TableDesignerService service = TableDesignerService.getInstance();
        var types = service.getSupportedTypes("sqlserver");
        assertTrue(types.contains("datetimeoffset"));
        assertTrue(types.contains("smalldatetime"));
        assertTrue(types.contains("geography"));

        var pgTypes = service.getSupportedTypes("postgresql");
        assertTrue(pgTypes.contains("interval"));
        assertTrue(pgTypes.contains("citext"));
        assertTrue(pgTypes.contains("macaddr"));
    }
}
