package app.ui.search;

import com.lltool.core.application.objectsearch.ObjectSearchService;
import com.lltool.core.domain.objectsearch.model.ObjectSearchHit;
import com.lltool.core.domain.objectsearch.model.ObjectSearchQuery;
import com.lltool.core.domain.objectsearch.model.SearchMatchLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ObjectSearchService Unit Tests")
class ObjectSearchServiceTest {

    private ObjectSearchService service;

    @BeforeEach
    void setUp() {
        service = new ObjectSearchService();
    }

    @Test
    @DisplayName("compilePattern creates case-insensitive plain pattern by default")
    void testCompilePatternDefault() {
        Pattern p = service.compilePattern("SELECT", false, false, false);
        assertNotNull(p);
        assertTrue(p.matcher("select * from users").find());
        assertTrue(p.matcher("SELECT * from users").find());
        assertTrue(p.matcher("my_select_col").find());
    }

    @Test
    @DisplayName("compilePattern respects case-sensitivity")
    void testCompilePatternCaseSensitive() {
        Pattern p = service.compilePattern("Select", true, false, false);
        assertNotNull(p);
        assertFalse(p.matcher("select * from users").find());
        assertTrue(p.matcher("Select * from users").find());
    }

    @Test
    @DisplayName("compilePattern handles whole word boundary")
    void testCompilePatternWholeWord() {
        Pattern p = service.compilePattern("id", false, true, false);
        assertNotNull(p);
        assertTrue(p.matcher("select id from users").find());
        assertTrue(p.matcher("WHERE (id = 1)").find());
        assertFalse(p.matcher("select identity from users").find());
        assertFalse(p.matcher("valid_user").find());
    }

    @Test
    @DisplayName("compilePattern parses custom regular expressions")
    void testCompilePatternRegex() {
        Pattern p = service.compilePattern("dbo\\.[a-z_]+", false, false, true);
        assertNotNull(p);
        assertTrue(p.matcher("FROM dbo.customers c").find());
        assertTrue(p.matcher("JOIN dbo.order_items oi").find());
        assertFalse(p.matcher("FROM sales.customers c").find());
    }

    @Test
    @DisplayName("extractMatches returns 1-based line and column coordinates and snippets")
    void testExtractMatches() {
        String ddl = """
                CREATE PROCEDURE dbo.usp_test
                AS
                BEGIN
                    SELECT id, name FROM dbo.users;
                    -- Another query below
                    SELECT count(*) FROM dbo.orders;
                END;
                """;

        Pattern p = service.compilePattern("SELECT", false, false, false);
        List<SearchMatchLocation> matches = service.extractMatches(ddl, p);

        assertEquals(2, matches.size());

        // First match on line 4 (1-based)
        SearchMatchLocation m1 = matches.get(0);
        assertEquals(4, m1.line());
        assertEquals(5, m1.column()); // "    SELECT" -> column 5
        assertEquals(6, m1.length());
        assertTrue(m1.snippet().contains("SELECT id, name FROM dbo.users;"));

        // Second match on line 6 (1-based)
        SearchMatchLocation m2 = matches.get(1);
        assertEquals(6, m2.line());
        assertEquals(5, m2.column());
        assertEquals(6, m2.length());
        assertTrue(m2.snippet().contains("SELECT count(*) FROM dbo.orders;"));
    }

    @Test
    @DisplayName("search in mock mode returns matching objects sorted by type")
    void testMockSearch() throws Exception {
        ObjectSearchQuery query = new ObjectSearchQuery(
                "orders",
                "master",
                "dbo",
                Set.of("VIEW", "PROCEDURE", "FUNCTION", "TRIGGER"),
                false,
                false,
                false
        );

        List<ObjectSearchHit> hits = service.search(null, "sqlserver", query);

        assertNotNull(hits);
        assertFalse(hits.isEmpty());

        // Ensure order is VIEW -> PROCEDURE -> FUNCTION -> TRIGGER
        for (int i = 0; i < hits.size() - 1; i++) {
            ObjectSearchHit a = hits.get(i);
            ObjectSearchHit b = hits.get(i + 1);
            int rankA = typeRank(a.getType());
            int rankB = typeRank(b.getType());
            assertTrue(rankA <= rankB, "Expected rank of " + a.getType() + " <= " + b.getType());
        }

        // Check that each hit has matchCount > 0 and populated DDL
        for (ObjectSearchHit hit : hits) {
            assertTrue(hit.getMatchCount() > 0);
            assertNotNull(hit.getDdl());
            assertFalse(hit.getMatches().isEmpty());
        }
    }

    @Test
    @DisplayName("search filters by requested object types")
    void testSearchFilterTypes() throws Exception {
        ObjectSearchQuery query = new ObjectSearchQuery(
                "orders",
                "master",
                "dbo",
                Set.of("VIEW"),
                false,
                false,
                false
        );

        List<ObjectSearchHit> hits = service.search(null, "sqlserver", query);

        assertNotNull(hits);
        for (ObjectSearchHit hit : hits) {
            assertEquals("VIEW", hit.getType());
        }
    }

    @Test
    @DisplayName("OS-3: Catastrophic regex backtracking triggers timeout and does not hang")
    void testRegexTimeoutGuard() {
        // (a+)+$ on a string of 3,000 'a' characters followed by 'b' causes catastrophic backtracking
        String heavyInput = "a".repeat(2000) + "b";
        Pattern pattern = service.compilePattern("(a+)+$", false, false, true);
        assertNotNull(pattern);

        long start = System.currentTimeMillis();
        assertThrows(ObjectSearchService.MatchTimeoutException.class, () -> {
            service.extractMatches(heavyInput, pattern, 300L); // 300ms limit
        });
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 2000, "Regex timeout guard took longer than expected: " + elapsed + "ms");
    }

    @Test
    @DisplayName("OS-4 & OS-6: Search with warnings returns warnings, encrypted count, and truncated flag")
    void testSearchWithWarnings() throws Exception {
        ObjectSearchQuery query = new ObjectSearchQuery(
                "orders",
                "master",
                "dbo",
                Set.of("VIEW", "PROCEDURE"),
                false,
                false,
                false
        );

        ObjectSearchService.SearchResultWithWarnings result = service.searchWithWarnings(null, "sqlserver", query);
        assertNotNull(result);
        assertNotNull(result.hits());
        assertFalse(result.hits().isEmpty());
        assertFalse(result.truncated());
        assertEquals(0, result.encryptedObjectsSkipped());
    }

    private int typeRank(String type) {
        return switch (type.toUpperCase()) {
            case "VIEW" -> 1;
            case "PROCEDURE" -> 2;
            case "FUNCTION" -> 3;
            case "TRIGGER" -> 4;
            default -> 99;
        };
    }
}

