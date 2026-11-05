package app.ui.results;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * GridDataExportUtils — Utility methods for formatting, scripting, copying, and calculating
 * data from the Results Data Grid.
 * Strictly adheres to specifications in .SKILL/result_sql.md and Python implementation in static/js/grid-result.js.
 */
public final class GridDataExportUtils {

    private GridDataExportUtils() {}

    public record GridAggregates(
        int totalCells,
        int numericCount,
        double sum,
        double average,
        double min,
        double max
    ) {
        public String getDisplaySum() {
            if (numericCount == 0) return "-";
            return Math.floor(sum) == sum ? String.format("%,d", (long) sum) : String.format("%,.4f", sum);
        }

        public String getDisplayAverage() {
            if (numericCount == 0) return "-";
            return String.format("%,.4f", average);
        }

        public String getDisplayMin() {
            if (numericCount == 0) return "-";
            return Math.floor(min) == min ? String.format("%,d", (long) min) : String.format("%,.4f", min);
        }

        public String getDisplayMax() {
            if (numericCount == 0) return "-";
            return Math.floor(max) == max ? String.format("%,d", (long) max) : String.format("%,.4f", max);
        }
    }

    /**
     * Formats rows as TSV according to RFC 4180 rules.
     */
    public static String formatTsv(List<String> columnNames, List<List<Object>> rows, boolean includeHeaders) {
        StringBuilder sb = new StringBuilder();

        if (includeHeaders && columnNames != null && !columnNames.isEmpty()) {
            for (int i = 0; i < columnNames.size(); i++) {
                if (i > 0) sb.append('\t');
                sb.append(escapeTsv(columnNames.get(i)));
            }
            sb.append("\r\n");
        }

        if (rows != null) {
            for (List<Object> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) sb.append('\t');
                    Object val = row.get(i);
                    sb.append(escapeTsv(val == null ? "" : val.toString()));
                }
                sb.append("\r\n");
            }
        }

        return sb.toString();
    }

    /**
     * Formats rows as CSV according to RFC 4180 rules.
     */
    public static String formatCsv(List<String> columnNames, List<List<Object>> rows, boolean includeHeaders) {
        StringBuilder sb = new StringBuilder();

        if (includeHeaders && columnNames != null && !columnNames.isEmpty()) {
            for (int i = 0; i < columnNames.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(escapeCsv(columnNames.get(i)));
            }
            sb.append("\r\n");
        }

        if (rows != null) {
            for (List<Object> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    if (i > 0) sb.append(',');
                    Object val = row.get(i);
                    sb.append(escapeCsv(val == null ? "" : val.toString()));
                }
                sb.append("\r\n");
            }
        }

        return sb.toString();
    }

    /**
     * Formats selected cells as SQL Values List:
     * - Single column: ('val1', 'val2', 'val3')
     * - Multiple columns:
     *     ('val1', 'val2'),
     *     ('val3', 'val4')
     */
    public static String formatSqlValuesList(List<String> columnNames, List<List<Object>> rows) {
        if (rows == null || rows.isEmpty()) return "";

        int colCount = columnNames != null && !columnNames.isEmpty() ? columnNames.size() : (rows.get(0) != null ? rows.get(0).size() : 1);

        if (colCount == 1) {
            List<String> items = new ArrayList<>();
            for (List<Object> row : rows) {
                if (row != null && !row.isEmpty()) {
                    items.add(formatSqlLiteral(row.get(0)));
                }
            }
            return "(" + String.join(", ", items) + ")";
        } else {
            List<String> rowStrings = new ArrayList<>();
            for (List<Object> row : rows) {
                List<String> cellLiterals = new ArrayList<>();
                for (Object val : row) {
                    cellLiterals.add(formatSqlLiteral(val));
                }
                rowStrings.add("    (" + String.join(", ", cellLiterals) + ")");
            }
            return String.join(",\r\n", rowStrings);
        }
    }

    /**
     * Formats selected rows as a complete SQL Script with #tabletmp creation and batch INSERT statements.
     * Complies with TC02, TC03, TC04 in .SKILL/result_sql.md.
     */
    public static String formatScriptAsInsert(String tableName, List<String> columnNames, List<List<Object>> rows) {
        if (rows == null || rows.isEmpty() || columnNames == null || columnNames.isEmpty()) {
            return "";
        }

        String table = (tableName != null && !tableName.isBlank()) ? tableName : "#tabletmp";

        // 1. DDL: CREATE TABLE
        List<String> colDefs = new ArrayList<>();
        for (int c = 0; c < columnNames.size(); c++) {
            String colName = columnNames.get(c);
            List<Object> sampleVals = new ArrayList<>();
            for (List<Object> r : rows) {
                if (c < r.size()) sampleVals.add(r.get(c));
            }
            String inferredType = inferSqlType(colName, sampleVals);
            colDefs.add("    [" + colName + "] " + inferredType + " NULL");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("-- 1. Tạo bảng tạm ").append(table).append("\r\n");
        sb.append("IF OBJECT_ID('tempdb..").append(table).append("') IS NOT NULL DROP TABLE ").append(table).append(";\r\n");
        sb.append("CREATE TABLE ").append(table).append(" (\r\n");
        sb.append(String.join(",\r\n", colDefs)).append("\r\n);\r\n\r\n");

        // 2. DML: INSERT INTO
        sb.append("-- 2. Chèn dữ liệu\r\n");
        List<String> colBracketList = new ArrayList<>();
        for (String c : columnNames) {
            colBracketList.add("[" + c + "]");
        }
        String colListStr = String.join(", ", colBracketList);

        final int BATCH_SIZE = 1000;
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, rows.size());
            List<List<Object>> batch = rows.subList(i, end);

            sb.append("INSERT INTO ").append(table).append(" (").append(colListStr).append(") VALUES\r\n");

            List<String> rowStrs = new ArrayList<>();
            for (List<Object> r : batch) {
                List<String> cellLiterals = new ArrayList<>();
                for (int c = 0; c < columnNames.size(); c++) {
                    Object val = c < r.size() ? r.get(c) : null;
                    cellLiterals.add(formatSqlLiteral(val));
                }
                rowStrs.add("    (" + String.join(", ", cellLiterals) + ")");
            }
            sb.append(String.join(",\r\n", rowStrs)).append(";\r\n\r\n");
        }

        return sb.toString().trim();
    }

    /**
     * Formats cell literal according to SQL standard rules:
     * - NULL -> NULL (no quotes)
     * - "" -> ''
     * - "   " -> '   ' (preserves whitespace, never trimmed)
     * - Unicode -> N'...'
     * - Numbers / Booleans -> literal representation
     */
    public static String formatSqlLiteral(Object val) {
        if (val == null) {
            return "NULL";
        }
        if (val instanceof Number) {
            return val.toString();
        }
        if (val instanceof Boolean b) {
            return b ? "1" : "0";
        }

        String str = val.toString();
        if (str.isEmpty()) {
            return "''";
        }

        // Escape single quote
        String escaped = str.replace("'", "''");

        // Check if string contains non-ASCII characters
        boolean isUnicode = false;
        for (int i = 0; i < str.length(); i++) {
            if (str.charAt(i) > 127) {
                isUnicode = true;
                break;
            }
        }

        return (isUnicode ? "N'" : "'") + escaped + "'";
    }

    /**
     * Formats selection as Excel XML Spreadsheet 2003 schema.
     * When copied to clipboard, pasting into MS Excel generates real formatted cells!
     */
    public static String formatXmlSpreadsheet(List<String> columnNames, List<List<Object>> rows) {
        StringBuilder xmlRows = new StringBuilder();

        // Header row
        if (columnNames != null && !columnNames.isEmpty()) {
            xmlRows.append("   <Row>\r\n");
            for (String col : columnNames) {
                xmlRows.append("    <Cell ss:StyleID=\"Header\"><Data ss:Type=\"String\">")
                       .append(escapeXml(col))
                       .append("</Data></Cell>\r\n");
            }
            xmlRows.append("   </Row>\r\n");
        }

        // Data rows
        if (rows != null) {
            for (List<Object> row : rows) {
                xmlRows.append("   <Row>\r\n");
                for (Object val : row) {
                    if (val == null) {
                        xmlRows.append("    <Cell><Data ss:Type=\"String\"></Data></Cell>\r\n");
                    } else if (val instanceof Number) {
                        xmlRows.append("    <Cell><Data ss:Type=\"Number\">")
                               .append(val)
                               .append("</Data></Cell>\r\n");
                    } else {
                        xmlRows.append("    <Cell><Data ss:Type=\"String\">")
                               .append(escapeXml(val.toString()))
                               .append("</Data></Cell>\r\n");
                    }
                }
                xmlRows.append("   </Row>\r\n");
            }
        }

        return "<?xml version=\"1.0\"?>\r\n" +
               "<?mso-application progid=\"Excel.Sheet\"?>\r\n" +
               "<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\r\n" +
               " xmlns:o=\"urn:schemas-microsoft-com:office:office\"\r\n" +
               " xmlns:x=\"urn:schemas-microsoft-com:office:excel\"\r\n" +
               " xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"\r\n" +
               " xmlns:html=\"http://www.w3.org/TR/REC-html40\">\r\n" +
               " <Styles>\r\n" +
               "  <Style ss:ID=\"Default\" ss:Name=\"Normal\">\r\n" +
               "   <Alignment ss:Vertical=\"Bottom\"/>\r\n" +
               "  </Style>\r\n" +
               "  <Style ss:ID=\"Header\">\r\n" +
               "   <Font ss:Bold=\"1\" ss:Color=\"#000000\"/>\r\n" +
               "   <Interior ss:Color=\"#E6E6E6\" ss:Pattern=\"Solid\"/>\r\n" +
               "  </Style>\r\n" +
               " </Styles>\r\n" +
               " <Worksheet ss:Name=\"Query Results\">\r\n" +
               "  <Table>\r\n" +
               xmlRows +
               "  </Table>\r\n" +
               " </Worksheet>\r\n" +
               "</Workbook>";
    }

    /**
     * Formats rows as JSON string array of objects.
     */
    public static String formatJson(List<String> columnNames, List<List<Object>> rows, boolean pretty) {
        StringBuilder sb = new StringBuilder();
        sb.append(pretty ? "[\n" : "[");

        if (rows != null && columnNames != null) {
            for (int r = 0; r < rows.size(); r++) {
                if (r > 0) sb.append(pretty ? ",\n" : ",");
                sb.append(pretty ? "  {" : "{");
                List<Object> row = rows.get(r);
                for (int c = 0; c < columnNames.size(); c++) {
                    if (c > 0) sb.append(", ");
                    sb.append("\"").append(escapeJson(columnNames.get(c))).append("\": ");
                    Object val = c < row.size() ? row.get(c) : null;
                    if (val == null) {
                        sb.append("null");
                    } else if (val instanceof Number || val instanceof Boolean) {
                        sb.append(val);
                    } else {
                        sb.append("\"").append(escapeJson(val.toString())).append("\"");
                    }
                }
                sb.append("}");
            }
        }

        sb.append(pretty ? "\n]" : "]");
        return sb.toString();
    }

    /**
     * Formats rows as a GitHub-flavored Markdown table.
     */
    public static String formatMarkdownTable(List<String> columnNames, List<List<Object>> rows) {
        if (columnNames == null || columnNames.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        // Header
        sb.append("| ").append(String.join(" | ", columnNames)).append(" |\n");
        // Separator
        sb.append("| ");
        for (int i = 0; i < columnNames.size(); i++) {
            sb.append("--- | ");
        }
        sb.append("\n");

        // Rows
        if (rows != null) {
            for (List<Object> row : rows) {
                sb.append("| ");
                for (int c = 0; c < columnNames.size(); c++) {
                    Object val = c < row.size() ? row.get(c) : null;
                    sb.append(val != null ? val.toString().replace("|", "\\|") : "").append(" | ");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Computes mathematical aggregates over selected cells (Count, Num Count, Sum, Avg, Min, Max).
     */
    public static GridAggregates calculateAggregates(List<List<Object>> rows) {
        int totalCells = 0;
        int numericCount = 0;
        double sum = 0.0;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        if (rows != null) {
            for (List<Object> row : rows) {
                if (row == null) continue;
                for (Object val : row) {
                    totalCells++;
                    if (val == null) continue;

                    Double num = null;
                    if (val instanceof Number n) {
                        num = n.doubleValue();
                    } else {
                        String s = val.toString().replace(",", "").trim();
                        if (!s.isEmpty()) {
                            try {
                                num = Double.parseDouble(s);
                            } catch (NumberFormatException ignored) {}
                        }
                    }

                    if (num != null) {
                        numericCount++;
                        sum += num;
                        if (num < min) min = num;
                        if (num > max) max = num;
                    }
                }
            }
        }

        double avg = numericCount > 0 ? (sum / numericCount) : 0.0;
        return new GridAggregates(
            totalCells,
            numericCount,
            numericCount > 0 ? sum : 0.0,
            avg,
            min == Double.POSITIVE_INFINITY ? 0.0 : min,
            max == Double.NEGATIVE_INFINITY ? 0.0 : max
        );
    }

    /**
     * Formats string into a classic Hex Dump:
     * 00000000: 48 65 6c 6c 6f 20 57 6f 72 6c 64 ... | Hello World
     */
    public static String formatHexDump(String text) {
        if (text == null || text.isEmpty()) return "";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

        StringBuilder sb = new StringBuilder();
        int offset = 0;

        while (offset < bytes.length) {
            sb.append(String.format("%08x: ", offset));

            int lineLen = Math.min(16, bytes.length - offset);
            for (int i = 0; i < 16; i++) {
                if (i < lineLen) {
                    sb.append(String.format("%02x ", bytes[offset + i]));
                } else {
                    sb.append("   ");
                }
                if (i == 7) sb.append(" ");
            }

            sb.append(" | ");
            for (int i = 0; i < lineLen; i++) {
                byte b = bytes[offset + i];
                char c = (b >= 32 && b <= 126) ? (char) b : '.';
                sb.append(c);
            }
            sb.append("\n");
            offset += 16;
        }

        return sb.toString();
    }

    /**
     * Quick O(1) detector for candidate JSON or XML strings.
     */
    public static String detectStructuredCandidate(Object val) {
        if (val == null) return null;
        String s = val.toString().trim();
        if (s.length() < 2) return null;

        if ((s.startsWith("{") && s.endsWith("}")) || (s.startsWith("[") && s.endsWith("]"))) {
            return "json";
        }
        if (s.startsWith("<") && s.endsWith(">")) {
            return "xml";
        }
        return null;
    }

    // Helper functions
    private static String escapeTsv(String s) {
        if (s == null) return "";
        if (s.contains("\t") || s.contains("\n") || s.contains("\r") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\n") || s.contains("\r") || s.contains("\"")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String inferSqlType(String colName, List<Object> values) {
        String lowerCol = colName.toLowerCase();
        if (lowerCol.endsWith("_id") || lowerCol.equals("id")) return "BIGINT";

        boolean allInt = true;
        boolean allDecimal = true;
        boolean allBool = true;
        boolean hasNonNull = false;

        for (Object v : values) {
            if (v == null) continue;
            hasNonNull = true;
            String s = v.toString().trim();
            if (s.isEmpty()) continue;

            if (allInt) {
                try { Long.parseLong(s); } catch (Exception e) { allInt = false; }
            }
            if (allDecimal) {
                try { new BigDecimal(s); } catch (Exception e) { allDecimal = false; }
            }
            if (allBool) {
                if (!s.equalsIgnoreCase("true") && !s.equalsIgnoreCase("false") && !s.equals("1") && !s.equals("0")) {
                    allBool = false;
                }
            }
        }

        if (!hasNonNull) return "NVARCHAR(MAX)";
        if (allBool) return "BIT";
        if (allInt) return "INT";
        if (allDecimal) return "DECIMAL(18, 4)";
        return "NVARCHAR(MAX)";
    }
}

