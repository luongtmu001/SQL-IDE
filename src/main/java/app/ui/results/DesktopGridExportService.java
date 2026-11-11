package app.ui.results;

import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * DesktopGridExportService — Handles opening grid data in Microsoft Excel (via temporary file)
 * and saving results via native OS FileChooser.
 * Complies with Chức năng 5 (Open in Excel) và Chức năng 6 (Save Result As) in .SKILL/result_sql.md.
 */
public class DesktopGridExportService {

    private static final Logger log = LoggerFactory.getLogger(DesktopGridExportService.class);
    private static DesktopGridExportService instance;

    private DesktopGridExportService() {}

    public static synchronized DesktopGridExportService getInstance() {
        if (instance == null) {
            instance = new DesktopGridExportService();
        }
        return instance;
    }

    public record ExportResult(boolean success, boolean cancelled, String filePath, String errorMessage) {}

    /**
     * Exports tabular results to a temporary file (with UTF-8 BOM for Excel Unicode compatibility),
     * sets it to Read-Only, and opens it using the OS default spreadsheet application (Excel).
     */
    public ExportResult openInExcel(List<String> columnNames, List<List<Object>> rows) {
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "SqlGridExports");
            if (!tempDir.exists()) {
                tempDir.mkdirs();
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String uniqueId = UUID.randomUUID().toString().substring(0, 6);
            String fileName = "Export_" + timestamp + "_" + uniqueId + ".xlsx";
            File tempFile = new File(tempDir, fileName);

            // Write real .xlsx format matching Python LLTool
            try {
                FastXlsxWriter.write(tempFile, columnNames, rows);
            } catch (Exception ex) {
                log.warn("FastXlsxWriter failed, falling back to CSV: {}", ex.getMessage());
                fileName = "Export_" + timestamp + "_" + uniqueId + ".csv";
                tempFile = new File(tempDir, fileName);
                try (FileOutputStream fos = new FileOutputStream(tempFile);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    writer.write('\uFEFF'); // UTF-8 BOM
                    writer.write(GridDataExportUtils.formatCsv(columnNames, rows, true));
                    writer.flush();
                }
            }

            // Set Read-Only attribute on the temporary file
            try {
                tempFile.setReadOnly();
            } catch (Exception e) {
                log.warn("Could not set Read-Only attribute on temp file: {}", e.getMessage());
            }

            // Open with system default application (Excel)
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(tempFile);
                log.info("Opened query results in Excel: {}", tempFile.getAbsolutePath());
                return new ExportResult(true, false, tempFile.getAbsolutePath(), null);
            } else {
                return new ExportResult(false, false, null, "Desktop OPEN action is not supported on this operating system.");
            }
        } catch (Exception e) {
            log.error("Failed to open query results in Excel", e);
            return new ExportResult(false, false, null, e.getMessage());
        }
    }

    /**
     * Prompts the user with a FileChooser to save results to XLSX / CSV / TSV / JSON.
     */
    public ExportResult saveResultsAs(Window ownerWindow, List<String> columnNames, List<List<Object>> rows, String defaultFileName) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Results As...");
            fileChooser.setInitialFileName(defaultFileName != null ? defaultFileName : "Query_Results.xlsx");

            fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Microsoft Excel Worksheet (*.xlsx)", "*.xlsx"),
                new FileChooser.ExtensionFilter("CSV (Comma delimited) (*.csv)", "*.csv"),
                new FileChooser.ExtensionFilter("TSV (Tab delimited) (*.txt)", "*.txt"),
                new FileChooser.ExtensionFilter("JSON (*.json)", "*.json"),
                new FileChooser.ExtensionFilter("Excel XML Spreadsheet (*.xml)", "*.xml"),
                new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
            );

            File selectedFile = fileChooser.showSaveDialog(ownerWindow);
            if (selectedFile == null) {
                return new ExportResult(false, true, null, "User cancelled file selection");
            }

            String path = selectedFile.getAbsolutePath();
            String lower = path.toLowerCase();

            if (lower.endsWith(".xlsx")) {
                FastXlsxWriter.write(selectedFile, columnNames, rows);
            } else {
                try (FileOutputStream fos = new FileOutputStream(selectedFile);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {

                    if (lower.endsWith(".json")) {
                        writer.write(GridDataExportUtils.formatJson(columnNames, rows, true));
                    } else if (lower.endsWith(".xml")) {
                        writer.write(GridDataExportUtils.formatXmlSpreadsheet(columnNames, rows));
                    } else if (lower.endsWith(".txt") || lower.endsWith(".tsv")) {
                        writer.write('\uFEFF');
                        writer.write(GridDataExportUtils.formatTsv(columnNames, rows, true));
                    } else {
                        // Default to CSV with BOM
                        writer.write('\uFEFF');
                        writer.write(GridDataExportUtils.formatCsv(columnNames, rows, true));
                    }
                    writer.flush();
                }
            }

            log.info("Saved query results to: {}", selectedFile.getAbsolutePath());
            return new ExportResult(true, false, selectedFile.getAbsolutePath(), null);
        } catch (Exception e) {
            log.error("Failed to save query results", e);
            return new ExportResult(false, false, null, e.getMessage());
        }
    }
}

