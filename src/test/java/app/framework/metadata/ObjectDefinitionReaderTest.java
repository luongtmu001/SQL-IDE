package app.framework.metadata;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ObjectDefinitionReaderTest {

    @Test
    void testMockDefinitionsFilteredByType() {
        ObjectDefinitionReader reader = new ObjectDefinitionReader();
        ObjectDefinitionReader.FilterOptions options = new ObjectDefinitionReader.FilterOptions("testdb", "dbo", Set.of("VIEW"));

        ObjectDefinitionReader.ObjectDefinitionResult result = reader.readDefinitions(null, "sqlserver", options);

        assertNotNull(result);
        assertTrue(result.warnings().isEmpty());
        assertFalse(result.definitions().isEmpty());
        for (ObjectDefinitionReader.ObjectDefinition def : result.definitions()) {
            assertEquals("VIEW", def.type());
            assertEquals("dbo", def.schema());
            assertNotNull(def.definition());
        }
    }

    @Test
    void testSqlExceptionGeneratesWarningInsteadOfCrashing() throws Exception {
        ObjectDefinitionReader reader = new ObjectDefinitionReader();
        ObjectDefinitionReader.FilterOptions options = new ObjectDefinitionReader.FilterOptions("testdb", "dbo", Set.of("VIEW", "TRIGGER"));

        // Use in-memory SQLite connection which does not have sys.sql_modules
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            ObjectDefinitionReader.ObjectDefinitionResult result = reader.readDefinitions(conn, "sqlserver", options);
            assertNotNull(result);
            assertTrue(result.definitions().isEmpty());
            // Should contain warnings for failed queries
            assertFalse(result.warnings().isEmpty());
            assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Không thể nạp")));
        }
    }

    @Test
    void testTriggerReturnsParentTableInMock() {
        ObjectDefinitionReader reader = new ObjectDefinitionReader();
        ObjectDefinitionReader.FilterOptions options = new ObjectDefinitionReader.FilterOptions("testdb", "dbo", Set.of("TRIGGER"));

        ObjectDefinitionReader.ObjectDefinitionResult result = reader.readDefinitions(null, "sqlserver", options);

        assertNotNull(result);
        assertEquals(1, result.definitions().size());
        ObjectDefinitionReader.ObjectDefinition triggerDef = result.definitions().get(0);
        assertEquals("TRIGGER", triggerDef.type());
        assertEquals("orders", triggerDef.parentTable());
    }
}

