package app.ui.results;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * FastXlsxWriter — Ultra-lightweight, zero-dependency streaming Excel (.xlsx) generator.
 * <p>
 * Compliant with Phase 8 / Milestone D4:
 * - Writes pure OpenXML (.xlsx) without Apache POI dependencies.
 * - Streams directly into ZipOutputStream: constant O(1) heap memory, effortlessly handles 100,000+ rows.
 * - Styling matching Python LLTool:
 *   - Header: Segoe UI 10pt Bold, #E6E6E6 fill, thin #D4D4D4 border.
 *   - Data: Segoe UI 10pt Regular, thin #D4D4D4 border.
 *   - NULL: "NULL", Segoe UI 10pt Italic #888888, #FFFFE1 fill, thin #D4D4D4 border.
 *   - Frozen top row (freeze_panes A2).
 *   - AutoFilter on header range A1:<lastCol><lastRow>.
 *   - Auto-computed column widths.
 */
public final class FastXlsxWriter {

    private FastXlsxWriter() {}

    public static void write(File targetFile, List<String> columns, List<List<Object>> rows) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(targetFile);
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {

            // 1. [Content_Types].xml
            zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
            writeBytes(zos, """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                      <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>
                    """);
            zos.closeEntry();

            // 2. _rels/.rels
            zos.putNextEntry(new ZipEntry("_rels/.rels"));
            writeBytes(zos, """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>
                    """);
            zos.closeEntry();

            // 3. xl/_rels/workbook.xml.rels
            zos.putNextEntry(new ZipEntry("xl/_rels/workbook.xml.rels"));
            writeBytes(zos, """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                      <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>
                    """);
            zos.closeEntry();

            // 4. xl/workbook.xml
            zos.putNextEntry(new ZipEntry("xl/workbook.xml"));
            writeBytes(zos, """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets>
                        <sheet name="Query Results" sheetId="1" r:id="rId1"/>
                      </sheets>
                    </workbook>
                    """);
            zos.closeEntry();

            // 5. xl/styles.xml
            zos.putNextEntry(new ZipEntry("xl/styles.xml"));
            writeBytes(zos, """
                    <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                      <fonts count="3">
                        <font>
                          <sz val="10"/>
                          <name val="Segoe UI"/>
                          <color rgb="FF000000"/>
                        </font>
                        <font>
                          <b/>
                          <sz val="10"/>
                          <name val="Segoe UI"/>
                          <color rgb="FF1F1F1F"/>
                        </font>
                        <font>
                          <i/>
                          <sz val="10"/>
                          <name val="Segoe UI"/>
                          <color rgb="FF888888"/>
                        </font>
                      </fonts>
                      <fills count="4">
                        <fill><patternFill patternType="none"/></fill>
                        <fill><patternFill patternType="gray125"/></fill>
                        <fill>
                          <patternFill patternType="solid">
                            <fgColor rgb="FFE6E6E6"/>
                            <bgColor indexed="64"/>
                          </patternFill>
                        </fill>
                        <fill>
                          <patternFill patternType="solid">
                            <fgColor rgb="FFFFFFE1"/>
                            <bgColor indexed="64"/>
                          </patternFill>
                        </fill>
                      </fills>
                      <borders count="2">
                        <border>
                          <left/><right/><top/><bottom/><diagonal/>
                        </border>
                        <border>
                          <left style="thin"><color rgb="FFD4D4D4"/></left>
                          <right style="thin"><color rgb="FFD4D4D4"/></right>
                          <top style="thin"><color rgb="FFD4D4D4"/></top>
                          <bottom style="thin"><color rgb="FFD4D4D4"/></bottom>
                          <diagonal/>
                        </border>
                      </borders>
                      <cellStyleXfs count="1">
                        <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
                      </cellStyleXfs>
                      <cellXfs count="4">
                        <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                        <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1">
                          <alignment horizontal="left" vertical="center"/>
                        </xf>
                        <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1"/>
                        <xf numFmtId="0" fontId="2" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1"/>
                      </cellXfs>
                    </styleSheet>
                    """);
            zos.closeEntry();

            // 6. xl/worksheets/sheet1.xml (Streamed directly via BufferedWriter)
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(zos, StandardCharsets.UTF_8), 32768);

            bw.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
            bw.write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">\n");

            // Freeze panes: lock row 1 (A2)
            bw.write("  <sheetViews>\n");
            bw.write("    <sheetView tabSelected=\"1\" workbookViewId=\"0\">\n");
            bw.write("      <pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>\n");
            bw.write("    </sheetView>\n");
            bw.write("  </sheetViews>\n");

            int colCount = columns != null ? columns.size() : 0;
            int rowCount = rows != null ? rows.size() : 0;

            // Compute estimated column widths (sample first 100 rows)
            if (colCount > 0) {
                int[] maxLens = new int[colCount];
                for (int c = 0; c < colCount; c++) {
                    maxLens[c] = columns.get(c) != null ? columns.get(c).length() : 4;
                }
                int sampleLimit = Math.min(rowCount, 100);
                for (int r = 0; r < sampleLimit; r++) {
                    List<Object> row = rows.get(r);
                    for (int c = 0; c < colCount && c < row.size(); c++) {
                        Object val = row.get(c);
                        int len = val != null ? String.valueOf(val).length() : 4;
                        if (len > maxLens[c]) maxLens[c] = len;
                    }
                }
                bw.write("  <cols>\n");
                for (int c = 0; c < colCount; c++) {
                    int w = Math.min(Math.max(maxLens[c] + 4, 12), 50);
                    bw.write("    <col min=\"" + (c + 1) + "\" max=\"" + (c + 1) + "\" width=\"" + w + "\" customWidth=\"1\"/>\n");
                }
                bw.write("  </cols>\n");
            }

            // Sheet Data
            bw.write("  <sheetData>\n");

            // Row 1: Header (style index 1)
            if (colCount > 0) {
                bw.write("    <row r=\"1\">\n");
                for (int c = 0; c < colCount; c++) {
                    String ref = colRef(c + 1) + "1";
                    String name = columns.get(c) != null ? columns.get(c) : "";
                    bw.write("      <c r=\"" + ref + "\" t=\"inlineStr\" s=\"1\"><is><t>" + escapeXml(name) + "</t></is></c>\n");
                }
                bw.write("    </row>\n");
            }

            // Data Rows (starting from row 2)
            if (rows != null) {
                for (int r = 0; r < rowCount; r++) {
                    int rowNum = r + 2;
                    List<Object> row = rows.get(r);
                    bw.write("    <row r=\"" + rowNum + "\">\n");

                    for (int c = 0; c < colCount; c++) {
                        String ref = colRef(c + 1) + rowNum;
                        Object val = (c < row.size()) ? row.get(c) : null;

                        if (val == null) {
                            // NULL style (index 3)
                            bw.write("      <c r=\"" + ref + "\" t=\"inlineStr\" s=\"3\"><is><t>NULL</t></is></c>\n");
                        } else if (val instanceof Number n && !(val instanceof Double d && (d.isNaN() || d.isInfinite()))) {
                            // Numeric style (index 2)
                            bw.write("      <c r=\"" + ref + "\" s=\"2\"><v>" + n.toString() + "</v></c>\n");
                        } else if (val instanceof Boolean b) {
                            bw.write("      <c r=\"" + ref + "\" t=\"b\" s=\"2\"><v>" + (b ? "1" : "0") + "</v></c>\n");
                        } else {
                            // String inline style (index 2)
                            bw.write("      <c r=\"" + ref + "\" t=\"inlineStr\" s=\"2\"><is><t>" + escapeXml(val.toString()) + "</t></is></c>\n");
                        }
                    }
                    bw.write("    </row>\n");
                }
            }

            bw.write("  </sheetData>\n");

            // AutoFilter
            if (colCount > 0) {
                String lastCol = colRef(colCount);
                int lastRow = Math.max(rowCount + 1, 1);
                bw.write("  <autoFilter ref=\"A1:" + lastCol + lastRow + "\"/>\n");
            }

            bw.write("</worksheet>\n");
            bw.flush();
            zos.closeEntry();
        }
    }

    private static void writeBytes(ZipOutputStream zos, String str) throws IOException {
        zos.write(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Converts a 1-based column index to an Excel column reference (1 -> A, 26 -> Z, 27 -> AA, etc.).
     */
    public static String colRef(int colNum) {
        StringBuilder sb = new StringBuilder();
        int n = colNum;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    /**
     * Fast XML character escaping.
     */
    public static String escapeXml(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    // Strip control characters invalid in XML 1.0 except \t, \n, \r
                    if (c >= 0x20 || c == '\t' || c == '\n' || c == '\r') {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
