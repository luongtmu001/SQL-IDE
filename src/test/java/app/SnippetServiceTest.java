package app;

import app.models.SqlSnippet;
import app.services.SnippetService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SnippetServiceTest {

    private SnippetService service;

    @BeforeEach
    public void setUp() {
        service = SnippetService.getInstance();
    }

    @Test
    @Order(1)
    public void testSqlSnippetMonacoConversionSingleVariable() {
        // User's specific feature requirement:
        // ssf = SELECT * FROM $table$ -> SELECT * FROM ${1:table}
        SqlSnippet snip = new SqlSnippet("ssf", "SELECT * FROM fragment", "SELECT * FROM $table$");
        String monacoBody = snip.toMonacoSnippetBody();
        assertEquals("SELECT * FROM ${1:table}", monacoBody,
                "The $table$ placeholder must be converted to ${1:table} so Monaco automatically focuses and selects 'table'");
    }

    @Test
    @Order(2)
    public void testSqlSnippetMonacoConversionMultipleVariables() {
        SqlSnippet snip = new SqlSnippet("sfb", "SELECT WHERE", "SELECT $columns$ FROM $table$ WHERE $condition$");
        String monacoBody = snip.toMonacoSnippetBody();
        assertEquals("SELECT ${1:columns} FROM ${2:table} WHERE ${3:condition}", monacoBody);
    }

    @Test
    @Order(3)
    public void testSqlSnippetMonacoConversionLinkedMirroredVariables() {
        SqlSnippet snip = new SqlSnippet("join", "Self join", "SELECT * FROM $table$ a JOIN $table$ b ON a.id = b.id");
        String monacoBody = snip.toMonacoSnippetBody();
        assertEquals("SELECT * FROM ${1:table} a JOIN ${1:table} b ON a.id = b.id", monacoBody,
                "Duplicate $table$ variables must share the same tabstop index ${1:table} for linked editing");
    }

    @Test
    @Order(4)
    public void testSqlSnippetMonacoConversionPreserveExisting() {
        SqlSnippet snip = new SqlSnippet("custom", "Already monaco", "SELECT ${1:col} FROM ${2:tbl}");
        assertEquals("SELECT ${1:col} FROM ${2:tbl}", snip.toMonacoSnippetBody());
    }

    @Test
    @Order(5)
    public void testSnippetServiceLoadDefaultsAndFindSsf() {
        List<SqlSnippet> all = service.getAllSnippets();
        assertFalse(all.isEmpty(), "Snippets list should not be empty");

        Optional<SqlSnippet> ssf = service.getSnippetByPrefix("ssf");
        assertTrue(ssf.isPresent(), "Built-in ssf snippet must exist");
        assertEquals("SELECT * FROM $table$", ssf.get().getBody());
        assertEquals("SELECT * FROM ${1:table}", ssf.get().toMonacoSnippetBody());
    }

    @Test
    @Order(6)
    public void testSnippetServiceSaveAndRetrieve() {
        String testPrefix = "test_custom_" + System.currentTimeMillis();
        SqlSnippet custom = new SqlSnippet(testPrefix, "Custom unit test snippet", "UPDATE $table$ SET $col$ = $val$;", "sqlserver");
        custom.setPinned(true);
        custom.getTags().add("test");

        boolean saved = service.saveSnippet(custom);
        assertTrue(saved, "Snippet should be saved successfully");

        Optional<SqlSnippet> found = service.getSnippetByPrefix(testPrefix);
        assertTrue(found.isPresent());
        assertEquals(custom.getDescription(), found.get().getDescription());
        assertTrue(found.get().isPinned());
        assertEquals("UPDATE ${1:table} SET ${2:col} = ${3:val};", found.get().toMonacoSnippetBody());

        // Toggle pin
        service.togglePin(found.get().getId());
        Optional<SqlSnippet> toggled = service.getSnippetById(found.get().getId());
        assertTrue(toggled.isPresent());
        assertFalse(toggled.get().isPinned());

        // Clean up
        service.deleteSnippet(found.get().getId());
        assertFalse(service.getSnippetByPrefix(testPrefix).isPresent());
    }

    @Test
    @Order(7)
    public void testSnippetServiceJsonPayloadForMonaco() throws Exception {
        String json = service.getSnippetsAsJson();
        assertNotNull(json);
        assertTrue(json.startsWith("[") && json.endsWith("]"));

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> list = mapper.readValue(json, new TypeReference<>() {});
        assertFalse(list.isEmpty());

        Map<String, Object> ssf = list.stream()
                .filter(m -> "ssf".equals(m.get("prefix")))
                .findFirst()
                .orElse(null);
        assertNotNull(ssf, "ssf must be present in JSON payload for Monaco");
        assertEquals("SELECT * FROM ${1:table}", ssf.get("monacoBody"));
    }

    @Test
    @Order(8)
    public void testSnippetServiceExportAndImport() throws Exception {
        File tempFile = File.createTempFile("snippets_export_test", ".json");
        tempFile.deleteOnExit();

        boolean exported = service.exportSnippets(tempFile);
        assertTrue(exported);
        assertTrue(tempFile.length() > 0);

        boolean imported = service.importSnippets(tempFile);
        assertTrue(imported);

        Files.deleteIfExists(tempFile.toPath());
    }
}
