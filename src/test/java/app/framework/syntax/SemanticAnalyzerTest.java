package app.framework.syntax;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SemanticAnalyzerTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void testInvalidCorpus() throws Exception {
        InputStream in = getClass().getResourceAsStream("/corpus/syntax/invalid.json");
        assertNotNull(in, "invalid.json corpus resource must be present");

        List<Map<String, Object>> cases = json.readValue(in, new TypeReference<>() {});
        assertFalse(cases.isEmpty());

        for (var c : cases) {
            String id = (String) c.get("id");
            String sql = (String) c.get("sql");
            String dialect = (String) c.getOrDefault("dialect", "sqlserver");
            @SuppressWarnings("unchecked")
            Map<String, Object> expect = (Map<String, Object>) c.get("expect");

            String expectedToken = (String) expect.get("token");

            List<SyntaxDiagnostic> diags = SemanticAnalyzer.analyze(sql, dialect, null);

            assertFalse(diags.isEmpty(), "Expected diagnostic for " + id + ", SQL: " + sql);
            if (expectedToken != null && !expectedToken.isEmpty()) {
                boolean tokenFound = diags.stream().anyMatch(d -> expectedToken.equalsIgnoreCase(d.token()));
                assertTrue(tokenFound, "Expected token '" + expectedToken + "' for " + id + ", got: " + diags);
            }
        }
    }

    @Test
    void testTempTableOpenStateAfterExec() {
        String sql = "CREATE TABLE #Kqt (Id INT);\n"
                + "EXECUTE usp_sys_CreateTable N'#Kqt', @_Kqt;\n"
                + "ALTER TABLE #Kqt ADD IsCheck NVARCHAR(1);\n"
                + "SELECT Account, ItemLevel, IsCheck FROM #Kqt;";

        List<SyntaxDiagnostic> diags = SemanticAnalyzer.analyze(sql, "sqlserver", null);
        assertTrue(diags.isEmpty(), "EXEC usp_... must mark temp table as open state without false errors: " + diags);
    }

    @Test
    void testTempTableAlterDropColumn() {
        String sql = "CREATE TABLE #T (Id INT, Old INT);\n"
                + "ALTER TABLE #T DROP COLUMN Old;\n"
                + "SELECT Old FROM #T;";

        List<SyntaxDiagnostic> diags = SemanticAnalyzer.analyze(sql, "sqlserver", null);
        assertEquals(1, diags.size(), "Dropped column Old must be flagged");
        assertEquals("Old", diags.get(0).token());
    }

    @Test
    void testTableVariableColumns() {
        String sql = "DECLARE @tbl TABLE (id INT, code VARCHAR(50));\n"
                + "SELECT id, bad_col FROM @tbl;";

        List<SyntaxDiagnostic> diags = SemanticAnalyzer.analyze(sql, "sqlserver", null);
        assertEquals(1, diags.size());
        assertEquals("bad_col", diags.get(0).token());
    }

    @Test
    void testCteColumns() {
        String sql = "WITH CteOrders AS (SELECT OrderID, Total FROM dbo.Orders)\n"
                + "SELECT OrderID, fake_cte_col FROM CteOrders;";

        List<SyntaxDiagnostic> diags = SemanticAnalyzer.analyze(sql, "sqlserver", null);
        assertEquals(1, diags.size());
        assertEquals("fake_cte_col", diags.get(0).token());
    }
}

