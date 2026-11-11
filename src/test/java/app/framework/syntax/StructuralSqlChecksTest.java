package app.framework.syntax;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructuralSqlChecksTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void testPostgresOfflineCorpus() throws Exception {
        InputStream in = getClass().getResourceAsStream("/corpus/syntax/postgres_offline.json");
        assertNotNull(in, "postgres_offline.json corpus resource must be present");

        List<Map<String, Object>> cases = json.readValue(in, new TypeReference<>() {});
        assertFalse(cases.isEmpty());

        for (var c : cases) {
            String id = (String) c.get("id");
            String sql = (String) c.get("sql");
            @SuppressWarnings("unchecked")
            Map<String, Object> expect = (Map<String, Object>) c.get("expect");

            int expectedErrorCount = ((Number) expect.get("errorCount")).intValue();

            List<SyntaxDiagnostic> diags = StructuralSqlChecks.checkPostgresStructuralBlocks(sql);

            if (expectedErrorCount == 0) {
                assertTrue(diags.isEmpty(), "Expected 0 errors for " + id + ", got: " + diags);
            } else {
                assertFalse(diags.isEmpty(), "Expected errors for " + id + ", got 0");
                if (expect.containsKey("token")) {
                    String expectedToken = (String) expect.get("token");
                    boolean tokenMatched = diags.stream().anyMatch(d -> expectedToken.equalsIgnoreCase(d.token()));
                    assertTrue(tokenMatched, "Expected token '" + expectedToken + "' in diags for " + id);
                }
            }
        }
    }

    @Test
    void testEmptySelectDetection() {
        String sql = "SELECT FROM dbo.Orders;";
        List<SyntaxDiagnostic> diags = StructuralSqlChecks.checkEmptySelect(sql);
        assertEquals(1, diags.size());
        assertEquals("SELECT", diags.get(0).token());
        assertEquals("EMPTY_SELECT", diags.get(0).code());
    }

    @Test
    void testSelectStarFormTypoDetection() {
        String sql = "-- Local / Bravo10Setup / dbo\nSELECT * FORM";
        List<SyntaxDiagnostic> diags = StructuralSqlChecks.check(sql, "sqlserver");
        assertFalse(diags.isEmpty(), "Should detect syntax error on misspelled FORM");
        boolean hasFormError = diags.stream().anyMatch(d ->
                "FORM".equals(d.token()) && d.line() == 2 && d.column() == 10 && d.severity() == SyntaxDiagnostic.Severity.ERROR);
        assertTrue(hasFormError, "Should pinpoint line 2, column 10, token 'FORM': " + diags);
    }

    @Test
    void testSelectStarWithoutFromDetection() {
        String sql = "SELECT *;";
        List<SyntaxDiagnostic> diags = StructuralSqlChecks.check(sql, "sqlserver");
        assertFalse(diags.isEmpty());
        boolean hasStarError = diags.stream().anyMatch(d ->
                "*".equals(d.token()) && d.severity() == SyntaxDiagnostic.Severity.ERROR);
        assertTrue(hasStarError, "Should catch SELECT * without FROM: " + diags);
    }

    @Test
    void testDanglingFromClauseDetection() {
        String sql = "SELECT col1 FROM;";
        List<SyntaxDiagnostic> diags = StructuralSqlChecks.check(sql, "sqlserver");
        assertFalse(diags.isEmpty());
        boolean hasFromError = diags.stream().anyMatch(d ->
                "FROM".equals(d.token()) && d.severity() == SyntaxDiagnostic.Severity.ERROR);
        assertTrue(hasFromError, "Should catch dangling FROM: " + diags);
    }
}

