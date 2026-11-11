package app.ui.dataeditor;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CriteriaGridTest {

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
    @DisplayName("Should generate SQL WHERE and ORDER BY clauses correctly for SQL Server")
    void testBuildClausesSqlServer() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> whereRef = new AtomicReference<>();
        AtomicReference<String> orderRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                CriteriaGrid grid = new CriteriaGrid();
                grid.setAvailableColumns(List.of("Id", "Country", "City", "Balance"));

                CriteriaRow r1 = new CriteriaRow("Country", "=", "Vietnam", "Germany", "ASC", "1");
                CriteriaRow r2 = new CriteriaRow("City", "LIKE", "%Hanoi%", "", "None", "");
                CriteriaRow r3 = new CriteriaRow("Balance", "IS NOT NULL", "", "", "DESC", "2");

                grid.getCriteriaList().addAll(r1, r2, r3);

                whereRef.set(grid.buildWhereClause(false));
                orderRef.set(grid.buildOrderByClause(false));
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        String where = whereRef.get();
        assertNotNull(where);
        assertTrue(where.contains("([Country] = 'Vietnam' OR [Country] = 'Germany')"));
        assertTrue(where.contains("[City] LIKE '%Hanoi%'"));
        assertTrue(where.contains("[Balance] IS NOT NULL"));

        String orderBy = orderRef.get();
        assertNotNull(orderBy);
        assertEquals("[Country] ASC, [Balance] DESC", orderBy);
    }

    @Test
    @DisplayName("Should generate SQL WHERE and ORDER BY clauses correctly for PostgreSQL")
    void testBuildClausesPostgreSql() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> whereRef = new AtomicReference<>();
        AtomicReference<String> orderRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                CriteriaGrid grid = new CriteriaGrid();
                grid.setAvailableColumns(List.of("id", "status", "created_at"));

                CriteriaRow r1 = new CriteriaRow("status", "=", "ACTIVE", "", "ASC", "1");
                grid.getCriteriaList().add(r1);

                whereRef.set(grid.buildWhereClause(true));
                orderRef.set(grid.buildOrderByClause(true));
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertEquals("\"status\" = 'ACTIVE'", whereRef.get());
        assertEquals("\"status\" ASC", orderRef.get());
    }

    @Test
    @DisplayName("Should return null when criteria list is empty")
    void testEmptyCriteria() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> whereRef = new AtomicReference<>();
        AtomicReference<String> orderRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                CriteriaGrid grid = new CriteriaGrid();
                whereRef.set(grid.buildWhereClause(false));
                orderRef.set(grid.buildOrderByClause(false));
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));

        assertNull(whereRef.get());
        assertNull(orderRef.get());
    }

    @Test
    @DisplayName("DE-9: Should handle IN and NOT IN clauses without wrapping whole list in quotes")
    void testInOperatorHandling() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<CriteriaGrid.WhereClause> whereRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                CriteriaGrid grid = new CriteriaGrid();
                grid.setAvailableColumns(List.of("Id", "Code"));

                CriteriaRow r1 = new CriteriaRow("Id", "IN", "(10, 20, 30)", "", "None", "");
                CriteriaRow r2 = new CriteriaRow("Code", "NOT IN", "'A', 'B'", "", "None", "");
                grid.getCriteriaList().addAll(r1, r2);

                whereRef.set(grid.buildWhere(null, false));
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        CriteriaGrid.WhereClause wc = whereRef.get();
        assertNotNull(wc);
        assertEquals("[Id] IN (?, ?, ?) AND [Code] NOT IN (?, ?)", wc.sql());
        assertEquals(List.of(10L, 20L, 30L, "A", "B"), wc.params());
        assertEquals("[Id] IN (10, 20, 30) AND [Code] NOT IN ('A', 'B')", wc.previewSql());
    }

    @Test
    @DisplayName("DE-9: Should auto-wrap LIKE pattern with % if unescaped, and strictly parse numbers")
    void testLikeAndStrictNumeric() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<CriteriaGrid.WhereClause> whereRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                CriteriaGrid grid = new CriteriaGrid();
                grid.setAvailableColumns(List.of("Name", "Sku", "Price", "Col]Name"));

                CriteriaRow r1 = new CriteriaRow("Name", "LIKE", "Coffee", "", "None", "");
                CriteriaRow r2 = new CriteriaRow("Sku", "=", "123-ABC", "", "None", "");
                CriteriaRow r3 = new CriteriaRow("Price", ">=", "-15.5", "", "None", "");
                CriteriaRow r4 = new CriteriaRow("Col]Name", "=", "Test", "", "None", "");
                grid.getCriteriaList().addAll(r1, r2, r3, r4);

                whereRef.set(grid.buildWhere(null, false));
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        CriteriaGrid.WhereClause wc = whereRef.get();
        assertNotNull(wc);
        assertTrue(wc.sql().contains("[Name] LIKE ?"));
        assertTrue(wc.sql().contains("[Sku] = ?"));
        assertTrue(wc.sql().contains("[Price] >= ?"));
        assertTrue(wc.sql().contains("[Col]]Name] = ?"));

        assertTrue(wc.params().contains("%Coffee%"));
        assertTrue(wc.params().contains("123-ABC"));
        assertTrue(wc.params().contains(-15.5));
        assertTrue(wc.params().contains("Test"));

        assertTrue(wc.previewSql().contains("[Name] LIKE '%Coffee%'"));
        assertTrue(wc.previewSql().contains("[Sku] = '123-ABC'"));
        assertTrue(wc.previewSql().contains("[Price] >= -15.5"));
        assertTrue(wc.previewSql().contains("[Col]]Name] = 'Test'"));
    }
}
