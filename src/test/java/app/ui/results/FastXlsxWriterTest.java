package app.ui.results;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

public class FastXlsxWriterTest {

    @Test
    public void testColRef() {
        assertEquals("A", FastXlsxWriter.colRef(1));
        assertEquals("B", FastXlsxWriter.colRef(2));
        assertEquals("Z", FastXlsxWriter.colRef(26));
        assertEquals("AA", FastXlsxWriter.colRef(27));
        assertEquals("AB", FastXlsxWriter.colRef(28));
        assertEquals("AZ", FastXlsxWriter.colRef(52));
        assertEquals("BA", FastXlsxWriter.colRef(53));
    }

    @Test
    public void testEscapeXml() {
        assertEquals("Tom &amp; Jerry", FastXlsxWriter.escapeXml("Tom & Jerry"));
        assertEquals("&lt;div class=&quot;sql&quot;&gt;", FastXlsxWriter.escapeXml("<div class=\"sql\">"));
        assertEquals("It&apos;s fine", FastXlsxWriter.escapeXml("It's fine"));
        assertEquals("", FastXlsxWriter.escapeXml(null));
        assertEquals("", FastXlsxWriter.escapeXml(""));
    }

    @Test
    public void testWriteValidXlsxArchive(@TempDir Path tempDir) throws Exception {
        File file = tempDir.resolve("test_export.xlsx").toFile();

        List<String> columns = List.of("ID", "Customer Name", "Total Amount", "Is Active", "Notes");
        List<List<Object>> rows = new ArrayList<>();
        rows.add(List.of(1, "Alice & Bob Corp", 1500.50, true, "VIP"));
        rows.add(List.of(2, "Charlie", 0, false, "Standard"));
        rows.add(List.of(3, "Null Customer", 42.0, true, ""));
        // Null value test
        List<Object> rowWithNull = new ArrayList<>();
        rowWithNull.add(4);
        rowWithNull.add("No Notes");
        rowWithNull.add(99.9);
        rowWithNull.add(false);
        rowWithNull.add(null);
        rows.add(rowWithNull);

        FastXlsxWriter.write(file, columns, rows);

        assertTrue(file.exists());
        assertTrue(file.length() > 0);

        // Verify ZIP contents
        try (ZipFile zip = new ZipFile(file)) {
            assertNotNull(zip.getEntry("[Content_Types].xml"));
            assertNotNull(zip.getEntry("_rels/.rels"));
            assertNotNull(zip.getEntry("xl/workbook.xml"));
            assertNotNull(zip.getEntry("xl/styles.xml"));
            ZipEntry sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml");
            assertNotNull(sheetEntry);

            // Read worksheet XML content
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(sheetEntry), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            String sheetXml = sb.toString();

            // Verify freeze panes A2
            assertTrue(sheetXml.contains("topLeftCell=\"A2\""));
            assertTrue(sheetXml.contains("state=\"frozen\""));

            // Verify auto filter
            assertTrue(sheetXml.contains("<autoFilter ref=\"A1:E5\"/>"));

            // Verify header values and escaping
            assertTrue(sheetXml.contains("Alice &amp; Bob Corp"));
            assertTrue(sheetXml.contains("Customer Name"));

            // Verify numeric, boolean, null cells
            assertTrue(sheetXml.contains("<v>1500.5</v>"));
            assertTrue(sheetXml.contains("<v>1</v>")); // boolean true
            assertTrue(sheetXml.contains("s=\"3\"><is><t>NULL</t></is>")); // NULL cell style 3
        }
    }
}

