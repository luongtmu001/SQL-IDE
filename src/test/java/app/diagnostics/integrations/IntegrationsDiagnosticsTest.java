package app.diagnostics.integrations;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationsDiagnosticsTest {

    @Test
    @DisplayName("DatabaseDiagnostics should scrub passwords, usernames, and secret parameters from URLs")
    void testUrlSanitization() {
        String raw1 = "jdbc:postgresql://admin:secretPass123@localhost:5432/mydb";
        String clean1 = DatabaseDiagnostics.sanitizeUrl(raw1);
        assertFalse(clean1.contains("secretPass123"));
        assertFalse(clean1.contains("admin:"));
        assertTrue(clean1.contains("localhost:5432"));

        String raw2 = "jdbc:sqlserver://127.0.0.1;databaseName=test;user=sa;password=SuperSecretPassword!";
        String clean2 = DatabaseDiagnostics.sanitizeUrl(raw2);
        assertFalse(clean2.contains("SuperSecretPassword!"));
        assertTrue(clean2.contains("password=***"));
        assertTrue(clean2.contains("user=***"));
    }

    @Test
    @DisplayName("DatabaseDiagnostics should scrub literal string values and truncate long SQL queries")
    void testSqlSanitization() {
        String rawSql = "SELECT * FROM users WHERE email = 'john.doe@example.com' AND secret_token = 'xyz123'";
        String sanitized = DatabaseDiagnostics.sanitizeSql(rawSql, 150);
        assertFalse(sanitized.contains("john.doe@example.com"));
        assertFalse(sanitized.contains("xyz123"));
        assertTrue(sanitized.contains("SELECT * FROM users WHERE email = '?' AND secret_token = '?'"));

        String veryLongSql = "SELECT " + "a".repeat(200) + " FROM tbl";
        String truncated = DatabaseDiagnostics.sanitizeSql(veryLongSql, 50);
        assertTrue(truncated.endsWith("... [truncated]"));
        assertTrue(truncated.length() <= 80);
    }

    @Test
    @DisplayName("WebViewDiagnostics should record and retrieve JS errors and page load metrics")
    void testWebViewDiagnostics() {
        WebViewDiagnostics webView = WebViewDiagnostics.getInstance();
        webView.clear();

        webView.recordPageLoad("monaco/index.html", 120L, true, null);
        webView.recordJsError("Uncaught ReferenceError: foo is not defined", "editor.js", 42);

        assertEquals(1, webView.getLoadRecords().size());
        assertEquals(1, webView.getJsErrors().size());
        assertEquals("editor.js", webView.getJsErrors().get(0).sourceId());
        assertEquals(42, webView.getJsErrors().get(0).lineNumber());
    }
}

