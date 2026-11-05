package app;

import app.framework.metadata.DesktopMetadataCache;
import app.framework.metadata.SqlScriptGenerator;
import app.framework.metadata.filter.DatabaseFilter;
import app.framework.metadata.filter.DesktopFilterManager;
import app.framework.metadata.filter.ObjectFilter;
import app.models.DatabaseNode;
import app.models.DatabaseNodeType;
import app.ui.components.DatabaseTreeCell;
import app.ui.components.LazyDatabaseTreeItem;
import com.lltool.core.application.connection.dto.ConnectionProfileDto;
import com.lltool.infrastructure.database.api.metadata.ColumnMetadata;
import com.lltool.infrastructure.database.api.metadata.TableMetadata;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseExplorerPhase63Test {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
        }
        try {
            Platform.setImplicitExit(false);
        } catch (Exception ignored) {
        }
    }

    @Test
    void testSqlScriptGeneratorPostgreSQL() {
        TableMetadata table = new TableMetadata("customers", "public", "test_db", "TABLE", 100L, null);
        List<ColumnMetadata> cols = List.of(
                new ColumnMetadata("id", "integer", 0, false, true, false, null, 1),
                new ColumnMetadata("name", "varchar", 255, false, false, false, null, 2),
                new ColumnMetadata("email", "varchar", 255, true, false, false, null, 3)
        );

        // SELECT
        String selectSql = SqlScriptGenerator.generateSelect("POSTGRESQL", table, cols);
        assertTrue(selectSql.contains("SELECT \"id\", \"name\", \"email\""), "Should select quoted columns: " + selectSql);
        assertTrue(selectSql.contains("FROM \"public\".\"customers\""), "Should have schema qualified table: " + selectSql);
        assertTrue(selectSql.contains("LIMIT 100;"), "Should use LIMIT 100 for Postgres: " + selectSql);

        // INSERT
        String insertSql = SqlScriptGenerator.generateInsert("POSTGRESQL", table, cols);
        assertTrue(insertSql.contains("INSERT INTO \"public\".\"customers\" (\"name\", \"email\")"), "Should insert non-PK cols: " + insertSql);
        assertTrue(insertSql.contains("VALUES (?, ?);"), "Should have 2 placeholders: " + insertSql);

        // UPDATE
        String updateSql = SqlScriptGenerator.generateUpdate("POSTGRESQL", table, cols);
        assertTrue(updateSql.contains("UPDATE \"public\".\"customers\""), "Should update table: " + updateSql);
        assertTrue(updateSql.contains("\"name\" = ?"), "Should set name: " + updateSql);
        assertTrue(updateSql.contains("WHERE \"id\" = ?;"), "Should condition on PK: " + updateSql);

        // DELETE
        String deleteSql = SqlScriptGenerator.generateDelete("POSTGRESQL", table, cols);
        assertEquals("DELETE FROM \"public\".\"customers\"\nWHERE \"id\" = ?;", deleteSql);

        // COUNT
        String countSql = SqlScriptGenerator.generateCount("POSTGRESQL", table);
        assertEquals("SELECT COUNT(*) AS total_count FROM \"public\".\"customers\";", countSql);

        // DDL
        String ddlSql = SqlScriptGenerator.generateDdl("POSTGRESQL", table, cols);
        assertTrue(ddlSql.contains("CREATE TABLE \"public\".\"customers\" ("));
        assertTrue(ddlSql.contains("\"id\" integer PRIMARY KEY,"));
        assertTrue(ddlSql.contains("\"name\" varchar(255) NOT NULL,"));

        // DROP TABLE
        String dropSql = SqlScriptGenerator.generateDropTable("POSTGRESQL", table);
        assertEquals("DROP TABLE \"public\".\"customers\";", dropSql);

        // ROUTINE CALL
        String procCall = SqlScriptGenerator.generateRoutineCall("POSTGRESQL", "public", "sync_users", true);
        assertEquals("CALL \"public\".\"sync_users\"();", procCall);

        String funcCall = SqlScriptGenerator.generateRoutineCall("POSTGRESQL", "public", "calculate_tax", false);
        assertEquals("SELECT \"public\".\"calculate_tax\"();", funcCall);
    }

    @Test
    void testSqlScriptGeneratorSqlServer() {
        TableMetadata table = new TableMetadata("Orders", "dbo", "SalesDB", "TABLE", 500L, null);
        List<ColumnMetadata> cols = List.of(
                new ColumnMetadata("OrderID", "int", 0, false, true, false, null, 1),
                new ColumnMetadata("TotalAmount", "decimal", 18, false, false, false, null, 2)
        );

        // SELECT with TOP (100) and square bracket identifiers
        String selectSql = SqlScriptGenerator.generateSelect("SQLSERVER", table, cols);
        assertTrue(selectSql.contains("SELECT TOP (100) [OrderID], [TotalAmount]"), "SQL Server TOP (100): " + selectSql);
        assertTrue(selectSql.contains("FROM [dbo].[Orders];"), "SQL Server bracketed table ref: " + selectSql);

        // Routine Call with EXEC
        String execCall = SqlScriptGenerator.generateRoutineCall("SQLSERVER", "dbo", "sp_ProcessOrder", true);
        assertEquals("EXEC [dbo].[sp_ProcessOrder];\nGO", execCall);
    }

    @Test
    void testDesktopMetadataCache() {
        DesktopMetadataCache cache = new DesktopMetadataCache();
        String connId = "test-conn-123";

        // Initial get is null
        assertNull(cache.get(connId, "TABLES", "public"));

        // Put and get
        List<String> tables = List.of("users", "orders");
        cache.put(connId, "TABLES", "public", tables);

        List<String> retrieved = cache.get(connId, "TABLES", "public");
        assertNotNull(retrieved);
        assertEquals(2, retrieved.size());
        assertEquals("users", retrieved.get(0));

        // Invalidate specific node
        cache.invalidateNode(connId, "public");
        assertNull(cache.get(connId, "TABLES", "public"));

        // Repopulate and invalidate entire connection
        cache.put(connId, "TABLES", "public", tables);
        cache.put(connId, "SCHEMAS", "", List.of("public", "audit"));
        assertNotNull(cache.get(connId, "SCHEMAS", ""));

        cache.invalidateConnection(connId);
        assertNull(cache.get(connId, "TABLES", "public"));
        assertNull(cache.get(connId, "SCHEMAS", ""));
    }

    @Test
    void testLazyDatabaseTreeItemLeafDetection() {
        DatabaseNode connNode = new DatabaseNode("My PG", DatabaseNodeType.CONNECTION);
        LazyDatabaseTreeItem connItem = new LazyDatabaseTreeItem(connNode);
        assertFalse(connItem.isLeaf(), "Connection must NOT be leaf");

        DatabaseNode dbNode = new DatabaseNode("mydb", DatabaseNodeType.DATABASE);
        LazyDatabaseTreeItem dbItem = new LazyDatabaseTreeItem(dbNode);
        assertFalse(dbItem.isLeaf(), "Database must NOT be leaf");

        DatabaseNode schemaNode = new DatabaseNode("public", DatabaseNodeType.SCHEMA);
        LazyDatabaseTreeItem schemaItem = new LazyDatabaseTreeItem(schemaNode);
        assertFalse(schemaItem.isLeaf(), "Schema must NOT be leaf");

        DatabaseNode tableNode = new DatabaseNode("users", DatabaseNodeType.TABLE);
        LazyDatabaseTreeItem tableItem = new LazyDatabaseTreeItem(tableNode);
        assertFalse(tableItem.isLeaf(), "Table must NOT be leaf (can have columns)");

        DatabaseNode colNode = new DatabaseNode("id", DatabaseNodeType.COLUMN);
        LazyDatabaseTreeItem colItem = new LazyDatabaseTreeItem(colNode);
        assertTrue(colItem.isLeaf(), "Column MUST be leaf");

        DatabaseNode idxNode = new DatabaseNode("idx_user_email", DatabaseNodeType.INDEX);
        LazyDatabaseTreeItem idxItem = new LazyDatabaseTreeItem(idxNode);
        assertTrue(idxItem.isLeaf(), "Index MUST be leaf");

        DatabaseNode procNode = new DatabaseNode("sp_clean", DatabaseNodeType.PROCEDURE);
        LazyDatabaseTreeItem procItem = new LazyDatabaseTreeItem(procNode);
        assertTrue(procItem.isLeaf(), "Procedure MUST be leaf");

        DatabaseNode funcNode = new DatabaseNode("fn_total", DatabaseNodeType.FUNCTION);
        LazyDatabaseTreeItem funcItem = new LazyDatabaseTreeItem(funcNode);
        assertTrue(funcItem.isLeaf(), "Function MUST be leaf");
    }

    @Test
    void testLazyDatabaseTreeItemInitialChildrenLoadingPlaceholder() {
        DatabaseNode tblNode = new DatabaseNode("products", DatabaseNodeType.TABLE);
        LazyDatabaseTreeItem tblItem = new LazyDatabaseTreeItem(tblNode);

        // Before expansion, calling getChildren() returns a dummy LOADING child so disclosure chevron is rendered
        assertFalse(tblItem.getChildren().isEmpty(), "Initial children must not be empty");
        assertEquals(1, tblItem.getChildren().size());
        assertEquals(DatabaseNodeType.LOADING, tblItem.getChildren().get(0).getValue().getType());
    }

    @Test
    void testLazyDatabaseTreeItemMockChildrenFallback() {
        DatabaseNode parent = new DatabaseNode("Mock Root", DatabaseNodeType.ROOT);
        DatabaseNode child1 = new DatabaseNode("Child 1", DatabaseNodeType.TABLE);
        DatabaseNode child2 = new DatabaseNode("Child 2", DatabaseNodeType.TABLE);
        parent.addChild(child1);
        parent.addChild(child2);

        LazyDatabaseTreeItem treeItem = new LazyDatabaseTreeItem(parent);
        // Expanding triggers fallback for mock children when profile is null
        treeItem.setExpanded(true);

        assertEquals(2, treeItem.getChildren().size());
        assertEquals("Child 1", treeItem.getChildren().get(0).getValue().getName());
        assertEquals("Child 2", treeItem.getChildren().get(1).getValue().getName());
        assertTrue(parent.isLoaded());
    }

    @Test
    void testContextMenuGenerationViaReflection() throws Exception {
        DatabaseTreeCell cell = new DatabaseTreeCell(node -> {});
        Method createMenuMethod = DatabaseTreeCell.class.getDeclaredMethod("createContextMenu", DatabaseNode.class);
        createMenuMethod.setAccessible(true);

        // Test Table Context Menu
        DatabaseNode tableNode = new DatabaseNode("orders", DatabaseNodeType.TABLE);
        ContextMenu tableMenu = (ContextMenu) createMenuMethod.invoke(cell, tableNode);
        assertNotNull(tableMenu);
        // SCR-MAIN-002 Q4 = a: LLTool context-menu.js structure
        assertTrue(tableMenu.getItems().stream().anyMatch(i -> i.getText() != null && i.getText().contains("Select Top 1000")), "Table menu must have Select Top 1000");
        assertTrue(tableMenu.getItems().stream().anyMatch(i -> i.getText() != null && i.getText().contains("Select Count(*)")), "Table menu must have Select Count(*)");
        assertTrue(tableMenu.getItems().stream().anyMatch(i -> i.getText() != null && i.getText().contains("Script Table as")), "Table menu must have Script Table as submenu");
        // Q3 = b: out-of-scope features stay visible but disabled
        assertTrue(tableMenu.getItems().stream().anyMatch(i -> i.getText() != null && i.getText().contains("Design Table") && i.isDisable()), "Design Table must be disabled (chưa hỗ trợ)");

        // Test Connection Context Menu
        DatabaseNode connNode = new DatabaseNode("Local PG", DatabaseNodeType.CONNECTION);
        connNode.setConnectionId("pg-local-1");
        ContextMenu connMenu = (ContextMenu) createMenuMethod.invoke(cell, connNode);
        assertNotNull(connMenu);
        assertTrue(connMenu.getItems().stream().anyMatch(i -> i.getText() != null && i.getText().contains("Edit Connection...")), "Conn menu must have Edit Connection...");
        assertTrue(connMenu.getItems().stream().anyMatch(i -> i.getText() != null && i.getText().contains("New Query Console")), "Conn menu must have New Query Console");
        assertTrue(connMenu.getItems().stream().anyMatch(i -> i.getText() != null && i.getText().contains("Remove Connection")), "Conn menu must have Remove Connection");

        // Test View Context Menu
        DatabaseNode viewNode = new DatabaseNode("v_active_users", DatabaseNodeType.VIEW);
        ContextMenu viewMenu = (ContextMenu) createMenuMethod.invoke(cell, viewNode);
        assertNotNull(viewMenu);
        assertTrue(viewMenu.getItems().stream().anyMatch(i -> i.getText() != null && i.getText().contains("Select Top 1000")));

        // Test Column Context Menu
        DatabaseNode colNode = new DatabaseNode("created_at", DatabaseNodeType.COLUMN);
        ContextMenu colMenu = (ContextMenu) createMenuMethod.invoke(cell, colNode);
        assertNotNull(colMenu);
        assertTrue(colMenu.getItems().stream().anyMatch(i -> i.getText() != null && i.getText().contains("Copy Column Name")));
        assertTrue(colMenu.getItems().stream().anyMatch(i -> i.getText() != null && i.getText().contains("Copy Qualified Column Name")));

        // Test Connection Filter Menu Item
        assertTrue(connMenu.getItems().stream().anyMatch(i -> i.getText() != null && i.getText().contains("Filter Databases...")),
                "Connection context menu must have 'Filter Databases...'");

        // Test Category Tables Filter Menu Item
        DatabaseNode tablesCat = new DatabaseNode("Tables", DatabaseNodeType.CATEGORY_TABLES);
        ContextMenu catMenu = (ContextMenu) createMenuMethod.invoke(cell, tablesCat);
        assertNotNull(catMenu);
        assertTrue(catMenu.getItems().stream().anyMatch(i -> i.getText() != null && i.getText().contains("Filter Tables...")),
                "Category Tables context menu must have 'Filter Tables...'");
    }

    @Test
    void testDatabaseFilterMatching() {
        DatabaseFilter emptyFilter = new DatabaseFilter();
        assertFalse(emptyFilter.isActive());
        assertTrue(emptyFilter.matches("postgres"));
        assertTrue(emptyFilter.matches("b10_phucloi"));

        DatabaseFilter activeFilter = new DatabaseFilter(Set.of("B10_PhucLoi", "B10_Casa"));
        assertTrue(activeFilter.isActive());
        assertEquals(2, activeFilter.getSelectedDatabases().size());
        assertTrue(activeFilter.matches("B10_PhucLoi"));
        assertTrue(activeFilter.matches("b10_casa"), "Case insensitive match should succeed");
        assertFalse(activeFilter.matches("postgres"), "Unselected database should NOT match");
    }

    @Test
    void testObjectFilterConditionsAndLogic() {
        // AND logic with Name contains
        ObjectFilter filterAnd = new ObjectFilter(
                List.of(
                        new ObjectFilter.Condition("Name", ObjectFilter.Operator.CONTAINS, "interest"),
                        new ObjectFilter.Condition("Name", ObjectFilter.Operator.STARTS_WITH, "b10")
                ),
                ObjectFilter.Logic.AND
        );

        assertTrue(filterAnd.isActive());
        assertTrue(filterAnd.matches("b10phucloiinterest", "dbo"));
        assertFalse(filterAnd.matches("other_interest", "dbo"), "Does not start with b10");
        assertFalse(filterAnd.matches("b10_users", "dbo"), "Does not contain interest");

        // OR logic with Schema and Operators
        ObjectFilter filterOr = new ObjectFilter(
                List.of(
                        new ObjectFilter.Condition("Schema", ObjectFilter.Operator.EQUALS, "dbo"),
                        new ObjectFilter.Condition("Name", ObjectFilter.Operator.ENDS_WITH, "_bak")
                ),
                ObjectFilter.Logic.OR
        );

        assertTrue(filterOr.matches("customers", "dbo"), "Matches schema dbo");
        assertTrue(filterOr.matches("users_bak", "archive"), "Matches ends with _bak");
        assertFalse(filterOr.matches("customers", "sales"), "Matches neither condition");

        // NOT_EQUALS operator
        ObjectFilter filterNotEquals = new ObjectFilter(
                List.of(new ObjectFilter.Condition("Name", ObjectFilter.Operator.NOT_EQUALS, "sysdiagrams")),
                ObjectFilter.Logic.AND
        );
        assertFalse(filterNotEquals.matches("sysdiagrams", "dbo"));
        assertTrue(filterNotEquals.matches("customers", "dbo"));
    }

    @Test
    void testDesktopFilterManager() {
        DesktopFilterManager manager = DesktopFilterManager.getInstance();
        String connId = "conn-filter-test-1";

        AtomicBoolean notified = new AtomicBoolean(false);
        manager.setOnFilterChanged(v -> notified.set(true));

        // Test database filter setting
        manager.setDatabaseFilter(connId, new DatabaseFilter(Set.of("db1", "db2")));
        assertTrue(manager.hasDatabaseFilter(connId));
        assertTrue(notified.get());

        // Test object filter setting
        notified.set(false);
        ObjectFilter objF = new ObjectFilter(List.of(new ObjectFilter.Condition("Name", ObjectFilter.Operator.CONTAINS, "user")), ObjectFilter.Logic.AND);
        manager.setObjectFilter(connId, "cat1", "sch1", "TABLES", objF);
        assertTrue(manager.hasObjectFilter(connId, "cat1", "sch1", "TABLES"));
        assertTrue(notified.get());

        // Clear filter
        notified.set(false);
        manager.clearObjectFilter(connId, "cat1", "sch1", "TABLES");
        assertFalse(manager.hasObjectFilter(connId, "cat1", "sch1", "TABLES"));
        assertTrue(notified.get());
    }

    @Test
    void testNewDatabaseNodeTypesAndIcons() {
        // Table subfolders must have folder icons
        TableMetadata meta = new TableMetadata("my_table", "dbo", "db", "TABLE", 0L, null);

        DatabaseNode colsCat = new DatabaseNode("Columns", DatabaseNodeType.CATEGORY_COLUMNS).setTableMetadata(meta);
        assertEquals("fth-folder", colsCat.getEffectiveIconLiteral());

        DatabaseNode keysCat = new DatabaseNode("Keys", DatabaseNodeType.CATEGORY_KEYS).setTableMetadata(meta);
        assertEquals("fth-folder", keysCat.getEffectiveIconLiteral());

        DatabaseNode constraintsCat = new DatabaseNode("Constraints", DatabaseNodeType.CATEGORY_CONSTRAINTS).setTableMetadata(meta);
        assertEquals("fth-folder", constraintsCat.getEffectiveIconLiteral());

        DatabaseNode triggersCat = new DatabaseNode("Triggers", DatabaseNodeType.CATEGORY_TRIGGERS).setTableMetadata(meta);
        assertEquals("fth-folder", triggersCat.getEffectiveIconLiteral(), "Triggers subfolder under table must be folder icon");

        DatabaseNode schemaTriggersCat = new DatabaseNode("Triggers", DatabaseNodeType.CATEGORY_TRIGGERS);
        assertEquals("fth-zap", schemaTriggersCat.getEffectiveIconLiteral(), "Schema-level triggers category must be zap icon");

        DatabaseNode mview = new DatabaseNode("mv_summary", DatabaseNodeType.MATERIALIZED_VIEW);
        assertEquals("mdi2l-layers-triple-outline", mview.getEffectiveIconLiteral());

        DatabaseNode udt = new DatabaseNode("udt_code", DatabaseNodeType.USER_TYPE);
        assertEquals("mdi2c-cube-outline", udt.getEffectiveIconLiteral());

        DatabaseNode roles = new DatabaseNode("Roles / Users", DatabaseNodeType.ROLES_USERS);
        assertEquals("mdi2a-account-group", roles.getEffectiveIconLiteral());
    }
}

