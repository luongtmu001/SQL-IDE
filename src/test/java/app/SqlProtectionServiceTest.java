package app;

import app.framework.query.SqlProtectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SqlProtectionServiceTest {

    private final SqlProtectionService service = SqlProtectionService.getInstance();

    @Test
    @DisplayName("Detect dangerous DELETE without WHERE clause on permanent table")
    void testDetectDeleteWithoutWhereOnPermanentTable() {
        String sql = "DELETE FROM dbo.Customers;";
        var issues = service.detectDangerousActions(sql);
        assertEquals(1, issues.size());
        assertEquals("DELETE", issues.get(0).actionType());
        assertTrue(issues.get(0).message().contains("Customers"));
    }

    @Test
    @DisplayName("Detect dangerous UPDATE without WHERE clause on permanent table")
    void testDetectUpdateWithoutWhereOnPermanentTable() {
        String sql = "UPDATE Users SET isActive = 0, updatedAt = GETDATE();";
        var issues = service.detectDangerousActions(sql);
        assertEquals(1, issues.size());
        assertEquals("UPDATE", issues.get(0).actionType());
        assertTrue(issues.get(0).message().contains("Users"));
    }

    @Test
    @DisplayName("Detect dangerous TRUNCATE TABLE on permanent table")
    void testDetectTruncateOnPermanentTable() {
        String sql = "TRUNCATE TABLE Orders;";
        var issues = service.detectDangerousActions(sql);
        assertEquals(1, issues.size());
        assertEquals("TRUNCATE", issues.get(0).actionType());
        assertTrue(issues.get(0).message().contains("Orders"));
    }

    @Test
    @DisplayName("Safe queries with WHERE clause do NOT trigger warnings")
    void testSafeQueriesWithWhereDoNotTrigger() {
        String sql1 = "DELETE FROM Customers WHERE CustomerId = 100;";
        assertTrue(service.detectDangerousActions(sql1).isEmpty());

        String sql2 = "UPDATE Users SET isActive = 1 WHERE UserId IN (1, 2, 3);";
        assertTrue(service.detectDangerousActions(sql2).isEmpty());

        String sql3 = "SELECT * FROM Orders;";
        assertTrue(service.detectDangerousActions(sql3).isEmpty());
    }

    @Test
    @DisplayName("EXEMPTION: Temporary tables (#temp, ##temp, [#temp]) must NEVER trigger warnings")
    void testTemporaryTablesExemption() {
        // Local temporary table
        String sql1 = "DELETE FROM #temp_customers;";
        assertTrue(service.detectDangerousActions(sql1).isEmpty(), "DELETE on #temp should be exempt");

        String sql2 = "UPDATE #temp_orders SET total = 0;";
        assertTrue(service.detectDangerousActions(sql2).isEmpty(), "UPDATE on #temp should be exempt");

        String sql3 = "TRUNCATE TABLE #staging_data;";
        assertTrue(service.detectDangerousActions(sql3).isEmpty(), "TRUNCATE on #temp should be exempt");

        // Global temporary table ##
        String sql4 = "DELETE FROM ##global_users;";
        assertTrue(service.detectDangerousActions(sql4).isEmpty(), "DELETE on ##global_temp should be exempt");

        String sql5 = "UPDATE ##global_cache SET valid = 0;";
        assertTrue(service.detectDangerousActions(sql5).isEmpty(), "UPDATE on ##global_temp should be exempt");

        // Quoted temporary table [#temp]
        String sql6 = "DELETE FROM [#temp_items];";
        assertTrue(service.detectDangerousActions(sql6).isEmpty(), "DELETE on [#temp] should be exempt");

        // Schema qualified temp table dbo.#temp
        String sql7 = "DELETE FROM dbo.#temp_staging;";
        assertTrue(service.detectDangerousActions(sql7).isEmpty(), "DELETE on dbo.#temp should be exempt");
    }

    @Test
    @DisplayName("EXEMPTION: Table variables (@table, [@table]) must NEVER trigger warnings")
    void testTableVariablesExemption() {
        String sql1 = "DELETE FROM @myTableVar;";
        assertTrue(service.detectDangerousActions(sql1).isEmpty(), "DELETE on @tableVar should be exempt");

        String sql2 = "UPDATE @orderSummary SET status = 'DONE';";
        assertTrue(service.detectDangerousActions(sql2).isEmpty(), "UPDATE on @tableVar should be exempt");

        String sql3 = "DELETE FROM [@orderSummary];";
        assertTrue(service.detectDangerousActions(sql3).isEmpty(), "DELETE on [@orderSummary] should be exempt");
    }

    @Test
    @DisplayName("Comments and string literals do not trigger false positives")
    void testCommentsAndStringsDoNotTrigger() {
        String sqlWithComments = """
                -- DELETE FROM Customers;
                /* TRUNCATE TABLE Orders; */
                SELECT * FROM Invoices;
                """;
        assertTrue(service.detectDangerousActions(sqlWithComments).isEmpty());

        String sqlWithStrings = "SELECT 'DELETE FROM Customers', 'TRUNCATE TABLE Users' FROM Dummy;";
        assertTrue(service.detectDangerousActions(sqlWithStrings).isEmpty());
    }

    @Test
    @DisplayName("isTemporaryOrVariableTable helper method accuracy")
    void testIsTemporaryOrVariableTable() {
        assertTrue(service.isTemporaryOrVariableTable("#temp"));
        assertTrue(service.isTemporaryOrVariableTable("##globalTemp"));
        assertTrue(service.isTemporaryOrVariableTable("@tableVar"));
        assertTrue(service.isTemporaryOrVariableTable("[#temp]"));
        assertTrue(service.isTemporaryOrVariableTable("[@tableVar]"));
        assertTrue(service.isTemporaryOrVariableTable("dbo.#temp"));
        assertTrue(service.isTemporaryOrVariableTable("tempdb..#temp"));

        assertFalse(service.isTemporaryOrVariableTable("Customers"));
        assertFalse(service.isTemporaryOrVariableTable("dbo.Customers"));
        assertFalse(service.isTemporaryOrVariableTable("[Orders]"));
        assertFalse(service.isTemporaryOrVariableTable("db.schema.table"));
        assertFalse(service.isTemporaryOrVariableTable(null));
        assertFalse(service.isTemporaryOrVariableTable(""));
    }
}

